package cc.sighs.dndturn.platform.client.ui;

import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.domain.character.PointBuy;
import cc.sighs.dndturn.platform.network.CharacterProtocol;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Modal creator UI. Every submitted value is revalidated by PlayerCharacterService on the server. */
public final class CharacterOverlay {
    private static final String[] ABILITIES = {"str", "dex", "con", "int", "wis", "cha"};
    private static Document document;
    private static CharacterProtocol.Options options;
    private static String selectedClass, selectedRace;
    private static final int[] scores = {15, 15, 14, 10, 8, 8};
    private static boolean open, pending;
    private static String message = "";
    private static long refresh = -1;
    private static final Map<String, String> values = new HashMap<>();

    private CharacterOverlay() {}

    public static boolean isOpen() { return open && document != null && document.isActive(); }
    public static Document document() { return document; }
    public static boolean visible() {
        var game = Minecraft.getInstance();
        return isOpen() && game.level != null && game.player != null && game.player.isAlive()
            && OverlayUi.unobstructed();
    }

    public static void receiveOptions(CharacterProtocol.Options payload, IPayloadContext context) {
        context.enqueueWork(() -> open(payload));
    }

    public static void receiveReply(CharacterProtocol.Reply payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.success()) close();
            else { pending = false; message = payload.message(); tick(); }
        });
    }

    private static void open(CharacterProtocol.Options payload) {
        options = payload;
        selectedClass = payload.classes().isEmpty() ? null : payload.classes().getFirst().id();
        selectedRace = payload.races().isEmpty() ? null : payload.races().getFirst().id();
        for (int index = 0; index < scores.length; index++) scores[index] = new int[] {15, 15, 14, 10, 8, 8}[index];
        pending = false;
        message = "";
        open = true;
        ensureDocument();
        rebuildLists();
        tick();
    }

    static void ensureDocument() {
        if (document == null || !document.isActive()) {
            if (com.sighs.apricityui.parser.HTML.getTemple("dndturn/character.html") == null) return;
            document = ApricityUI.createDocument("dndturn/character.html");
            if (document == null) return;
            refresh = -1;
        }
        if (refresh != document.getRefreshGeneration()) {
            refresh = document.getRefreshGeneration();
            values.clear(); rebuildLists();
            document.getElementById("character-submit").addEventListener("click", event -> submit());
            document.getElementById("character-close").addEventListener("click", event -> close());
        }
    }

    static void prepareFrame() {
        tick();
        if (document == null) return;
        document.applyViewport(false);
    }

    private static void rebuildLists() {
        if (document == null || !document.isActive() || options == null) return;
        values.clear();
        rebuild("character-classes", options.classes().stream()
            .map(value -> new Option(value.id(), value.name() + "  d" + value.hitDie())).toList(), true);
        rebuild("character-races", options.races().stream()
            .map(value -> new Option(value.id(), value.name())).toList(), false);
        buildAbilities();
    }

    private static void rebuild(String hostId, List<Option> entries, boolean characterClass) {
        Element host = document.getElementById(hostId);
        if (host == null) return;
        host.setTextContent("");
        for (Option option : entries) {
            Element button = host.appendChild(document.createElement("button"));
            button.setAttribute("class", "character-option");
            button.setAttribute("data-id", option.id());
            button.setAttribute("type", "button");
            button.addEventListener("click", event -> {
                if (characterClass) selectedClass = option.id(); else selectedRace = option.id();
                tick();
            });
            OverlayUi.text(button, option.label());
        }
    }

    private static void buildAbilities() {
        Element host = document.getElementById("character-abilities");
        if (host == null) return;
        host.setTextContent("");
        for (String ability : ABILITIES) {
            Element row = host.appendChild(document.createElement("div"));
            row.setAttribute("class", "ability-row");
            Element name = row.appendChild(document.createElement("span"));
            name.setAttribute("class", "ability-name");
            OverlayUi.text(name, ability.toUpperCase());
            Element minus = row.appendChild(document.createElement("button"));
            minus.setAttribute("class", "ability-button");
            minus.setAttribute("id", "character-" + ability + "-minus");
            minus.setAttribute("type", "button");
            OverlayUi.text(minus, "−");
            minus.addEventListener("click", event -> adjust(index(ability), -1));
            Element value = row.appendChild(document.createElement("span"));
            value.setAttribute("class", "ability-score");
            value.setAttribute("id", "character-" + ability + "-value");
            Element plus = row.appendChild(document.createElement("button"));
            plus.setAttribute("class", "ability-button");
            plus.setAttribute("id", "character-" + ability + "-plus");
            plus.setAttribute("type", "button");
            OverlayUi.text(plus, "+");
            plus.addEventListener("click", event -> adjust(index(ability), 1));
            Element modifier = row.appendChild(document.createElement("span"));
            modifier.setAttribute("class", "ability-mod");
            modifier.setAttribute("id", "character-" + ability + "-mod");
        }
    }

    private static void adjust(int index, int delta) {
        if (index < 0 || pending) return;
        scores[index] = Math.max(8, Math.min(15, scores[index] + delta));
        tick();
    }

    private static void submit() {
        if (!isOpen() || pending || selectedClass == null || selectedRace == null) return;
        var abilities = new AbilityScores(scores[0], scores[1], scores[2], scores[3], scores[4], scores[5]);
        if (!PointBuy.isValid(abilities)) { message = "27 点购点必须恰好花完"; tick(); return; }
        pending = true; message = "提交中……"; tick();
        ClientPacketDistributor.sendToServer(new CharacterProtocol.Create(selectedClass, selectedRace,
            scores[0], scores[1], scores[2], scores[3], scores[4], scores[5]));
    }

    static void tick() {
        if (!open) return;
        ensureDocument();
        if (document == null || !document.isActive()) return;
        if (options == null) return;
        for (String ability : ABILITIES) {
            int index = index(ability);
            int score = scores[index];
            text("character-" + ability + "-value", Integer.toString(score));
            text("character-" + ability + "-mod", modifier(score));
            enabled("character-" + ability + "-minus", !pending && score > 8);
            enabled("character-" + ability + "-plus", !pending && score < 15);
        }
        int spent = PointBuy.totalCost(new AbilityScores(scores[0], scores[1], scores[2], scores[3], scores[4], scores[5]));
        text("character-points", "已用 " + spent + " / 27 点");
        text("character-status", pending ? "提交中……" : message.isEmpty()
            ? "选择职业、种族并分配属性" : message);
        enabled("character-submit", !pending && selectedClass != null && selectedRace != null && spent == 27);
        enabled("character-close", !pending);
        for (Element button : document.getElementById("character-classes").getChildren()) {
            String id = button.getAttribute("data-id");
            button.setAttribute("class", id != null && id.equals(selectedClass)
                ? "character-option selected" : "character-option");
        }
        for (Element button : document.getElementById("character-races").getChildren()) {
            String id = button.getAttribute("data-id");
            button.setAttribute("class", id != null && id.equals(selectedRace)
                ? "character-option selected" : "character-option");
        }
    }

    private static String modifier(int score) {
        int value = Math.floorDiv(score - 10, 2);
        return (value >= 0 ? "+" : "") + value;
    }

    public static void close() {
        if (!open && document == null) return;
        open = false; pending = false; message = "";
        cancelGesture();
        if (document != null && document.isActive()) {
            text("character-status", ""); text("character-points", "");
            OverlayUi.resetSession(document);
        }
    }

    public static void cancelGesture() {
        if (document == null) return;
        var pressed = document.getPressedElement();
        if (pressed != null && pressed.isScrollbarInteractionActive()) pressed.handleScrollbarMouseUp(null);
        document.setPressedElement(null); document.clearFocus();
    }

    private static void text(String id, String value) {
        Element element = document == null ? null : document.getElementById(id);
        if (element != null && !value.equals(values.put(id, value))) OverlayUi.text(element, value);
    }

    private static void enabled(String id, boolean enabled) {
        Element element = document == null ? null : document.getElementById(id);
        if (element == null || Boolean.toString(enabled).equals(values.put(id, Boolean.toString(enabled)))) return;
        if (enabled) element.removeAttribute("disabled"); else element.setAttribute("disabled", "");
    }

    private static int index(String ability) {
        return switch (ability) { case "str" -> 0; case "dex" -> 1; case "con" -> 2; case "int" -> 3; case "wis" -> 4; default -> 5; };
    }

    private record Option(String id, String label) {}
}
