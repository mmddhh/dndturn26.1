package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.CombatNetwork;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Modal presentation of a server-owned consent window. It never pauses or creates a world session. */
final class ConsentOverlay {
    private static Document document;
    private static long refresh = -1;
    private static UUID pendingReply;
    private static final Map<UUID, Element> rows = new HashMap<>();
    private static final Map<String, String> values = new HashMap<>();

    private ConsentOverlay() {}

    static boolean isOpen() { return ClientControl.modal(); }
    static Document document() { return document; }
    static void cancelGesture() {
        if (document == null) return;
        var pressed = document.getPressedElement();
        if (pressed != null && pressed.isScrollbarInteractionActive()) pressed.handleScrollbarMouseUp(null);
        document.setPressedElement(null); document.clearFocus();
    }

    static void accept(CombatNetwork.ConsentState next) {
        var state = ClientControl.consent();
        if (!next.active()) {
            if (state == null || state.requestId().equals(next.requestId())) close();
            return;
        }
        if (state == null || !state.requestId().equals(next.requestId()) || state.revision() != next.revision())
            pendingReply = null;
        ClientControl.consent(next);
        tick();
    }

    static void tick() {
        var state = ClientControl.consent();
        if (state == null) return;
        Minecraft game = Minecraft.getInstance();
        if (game.player == null || !game.player.isAlive()) { close(); return; }
        if (document == null || !document.isActive()) {
            ClientControl.documentClosed(document);
            document = ApricityUI.createDocument("dndturn/consent.html");
            if (document == null) return;
            document.setReloadPersistent(true);
            refresh = -1;
        }
        if (refresh != document.getRefreshGeneration()) {
            refresh = document.getRefreshGeneration(); rows.clear(); values.clear();
            document.getElementById("agree").addEventListener("click", event -> reply(true));
            document.getElementById("decline").addEventListener("click", event -> reply(false));
        }
        text("reason", state.reason());
        text("deadline", "服务端剩余 " + Math.max(0, (state.deadline() - state.serverTick() + 19) / 20) + " 秒");
        Element roster = document.getElementById("consent-roster");
        var ids = state.members().stream().map(CombatNetwork.ConsentMember::id).toList();
        for (UUID id : java.util.List.copyOf(rows.keySet())) if (!ids.contains(id)) {
            roster.removeChild(rows.remove(id)); values.remove(id.toString());
        }
        boolean agreed = false;
        for (var member : state.members()) {
            Element row = rows.computeIfAbsent(member.id(), id -> roster.appendChild(document.createElement("div")));
            String label = (member.agreed() ? "已同意  " : "待确认  ") + member.name();
            if (!label.equals(values.put(member.id().toString(), label))) row.setTextContent(label);
            if (member.id().equals(game.player.getUUID())) agreed = member.agreed();
        }
        boolean expired = state.serverTick() >= state.deadline();
        enabled("agree", !agreed && !expired && pendingReply == null);
        enabled("decline", !expired && pendingReply == null);
    }

    private static void reply(boolean agree) {
        var state = ClientControl.consent();
        if (state == null || pendingReply != null || state.serverTick() >= state.deadline()) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        pendingReply = UUID.randomUUID();
        ClientPacketDistributor.sendToServer(new CombatNetwork.ConsentReply(state.generation(), state.requestId(),
            pendingReply, player.getUUID(), state.revision(), agree));
        tick();
    }

    static void close() {
        cancelGesture(); ClientControl.documentClosed(document);
        if (document != null) document.remove();
        document = null; pendingReply = null; refresh = -1;
        ClientControl.consent(null);
        rows.clear(); values.clear();
    }

    private static void text(String id, String value) {
        if (!value.equals(values.put(id, value))) document.getElementById(id).setTextContent(value);
    }

    private static void enabled(String id, boolean enabled) {
        String value = Boolean.toString(enabled);
        if (value.equals(values.put(id, value))) return;
        if (enabled) document.getElementById(id).removeAttribute("disabled");
        else document.getElementById(id).setAttribute("disabled", "");
    }
}
