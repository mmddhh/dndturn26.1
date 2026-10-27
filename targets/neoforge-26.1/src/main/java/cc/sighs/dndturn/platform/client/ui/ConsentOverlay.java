package cc.sighs.dndturn.platform.client.ui;

import cc.sighs.dndturn.platform.client.input.ClientControl;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Modal presentation of a server-owned consent window. It never pauses or creates a world session. */
public final class ConsentOverlay {
    private static Document document;
    private static long refresh = -1;
    private static UUID pendingReply;
    private static final java.util.List<Element> rowPool = new java.util.ArrayList<>();
    private static EncounterProtocol.ConsentState renderedState;
    private static UUID renderedPending;
    private static double layoutWidth = -1, layoutHeight = -1;
    private static final Map<String, String> values = new HashMap<>();

    private ConsentOverlay() {}

    public static boolean isOpen() { return ClientControl.modal(); }
    public static Document document() { return document; }
    public static boolean visible() {
        var game = Minecraft.getInstance();
        return isOpen() && game.level != null && game.player != null && game.player.isAlive() && OverlayUi.unobstructed();
    }
    static void ensureDocument() {
        if (document == null || !document.isActive()) {
            if (com.sighs.apricityui.parser.HTML.getTemple("dndturn/consent.html") == null) return;
            ClientControl.documentClosed(document);
            document = ApricityUI.createDocument("dndturn/consent.html");
            if (document == null) return;
            refresh = -1;
        }
        if (refresh != document.getRefreshGeneration()) {
            refresh = document.getRefreshGeneration(); rowPool.clear(); values.clear();
            renderedState = null; renderedPending = null; layoutWidth = layoutHeight = -1;
            document.getElementById("agree").addEventListener("click", event -> reply(true));
            document.getElementById("decline").addEventListener("click", event -> reply(false));
            for (int i = 0; i < 8; i++) row(i);
        }
    }
    private static Element row(int index) {
        while (rowPool.size() <= index) {
            Element row = document.getElementById("consent-roster").appendChild(document.createElement("div"));
            TacticalButton.style(row, "display: none"); rowPool.add(row);
        }
        return rowPool.get(index);
    }
    static void prepareFrame() {
        tick();
        updateLayout();
    }
    private static void updateLayout() {
        document.applyViewport(false);
        var viewport = document.getViewportSize();
        if (layoutWidth == viewport.width() && layoutHeight == viewport.height()) return;
        layoutWidth = viewport.width(); layoutHeight = viewport.height();
        double width = Math.min(480, Math.max(1, viewport.width() - 16));
        double height = Math.min(390, Math.max(1, viewport.height() - 16));
        TacticalButton.style(document.getElementById("consent-dialog"), "left: " + (viewport.width() - width) / 2
            + "px; top: " + (viewport.height() - height) / 2 + "px; width: " + width + "px; height: " + height + "px");
    }
    public static void cancelGesture() {
        if (document == null) return;
        var pressed = document.getPressedElement();
        if (pressed != null && pressed.isScrollbarInteractionActive()) pressed.handleScrollbarMouseUp(null);
        document.setPressedElement(null); document.clearFocus();
    }

    public static void accept(EncounterProtocol.ConsentState next) {
        var state = ClientControl.consent();
        if (!next.active()) {
            if (state == null || state.requestId().equals(next.requestId())) close();
            return;
        }
        if (state == null || !state.requestId().equals(next.requestId()) || state.revision() != next.revision()) {
            pendingReply = null;
            OverlayUi.resetSession(document);
        }
        ClientControl.consent(next);
        tick();
    }

    public static void tick() {
        var state = ClientControl.consent();
        if (state == null) return;
        Minecraft game = Minecraft.getInstance();
        if (game.player == null || !game.player.isAlive()) { close(); return; }
        if (!visible()) return;
        ensureDocument();
        if (document == null || !document.isActive()) return;
        if (renderedState == state && java.util.Objects.equals(renderedPending, pendingReply)) return;
        renderedState = state; renderedPending = pendingReply;
        text("reason", state.reason());
        text("deadline", "服务端剩余 " + Math.max(0, (state.deadline() - state.serverTick() + 19) / 20) + " 秒");
        int index = 0;
        boolean agreed = false;
        for (var member : state.members()) {
            Element row = row(index++);
            TacticalButton.style(row, "display: block");
            String label = (member.agreed() ? "已同意  " : "待确认  ") + member.name();
            OverlayUi.text(row, label);
            if (member.id().equals(game.player.getUUID())) agreed = member.agreed();
        }
        for (int i = index; i < rowPool.size(); i++) {
            TacticalButton.style(rowPool.get(i), "display: none"); OverlayUi.text(rowPool.get(i), "");
        }
        boolean expired = state.serverTick() >= state.deadline();
        enabled("agree", !agreed && !expired && pendingReply == null);
        enabled("decline", !expired && pendingReply == null);
        updateLayout();
    }

    private static void reply(boolean agree) {
        var state = ClientControl.consent();
        if (!visible() || !ClientControl.allowDocument(document) || state == null || pendingReply != null || state.serverTick() >= state.deadline()) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        pendingReply = UUID.randomUUID();
        ClientPacketDistributor.sendToServer(new EncounterProtocol.ConsentReply(state.generation(), state.requestId(),
            pendingReply, player.getUUID(), state.revision(), agree));
        tick();
    }

    public static void close() {
        if (ClientControl.consent() == null && renderedState == null && pendingReply == null) return;
        cancelGesture(); ClientControl.documentClosed(document);
        pendingReply = null; renderedState = null; renderedPending = null;
        ClientControl.consent(null);
        for (var row : rowPool) { TacticalButton.style(row, "display: none"); OverlayUi.text(row, ""); }
        if (document != null && document.isActive()) {
            text("reason", ""); text("deadline", ""); enabled("agree", false); enabled("decline", false);
        }
        OverlayUi.resetSession(document);
    }

    private static void text(String id, String value) {
        if (!value.equals(values.put(id, value))) OverlayUi.text(document.getElementById(id), value);
    }

    private static void enabled(String id, boolean enabled) {
        String value = Boolean.toString(enabled);
        if (value.equals(values.put(id, value))) return;
        if (enabled) document.getElementById(id).removeAttribute("disabled");
        else document.getElementById(id).setAttribute("disabled", "");
    }
}
