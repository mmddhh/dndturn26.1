package cc.sighs.dndturn.platform.client.state;

import cc.sighs.dndturn.platform.client.input.ClientControl;
import cc.sighs.dndturn.platform.network.InspectionProtocol;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Owns only the panel request and a disposable filtered projection, independently of action selection. */
public final class ClientInspection {
    private static InspectionProtocol.Query pending;
    private static InspectionProtocol.Reply reply;
    private static net.minecraft.world.entity.Entity targetInstance;
    private static int waiting;
    private static boolean timedOut;
    private ClientInspection() {}
    public static boolean open() { return pending != null; }
    public static boolean close() {
        boolean wasOpen = open();
        pending = null; reply = null; targetInstance = null; waiting = 0; timedOut = false;
        return wasOpen;
    }
    public static void request(UUID target) {
        var game = Minecraft.getInstance(); var state = ClientCombatState.encounter();
        if (target == null || state == null || game.player == null || !game.player.isAlive()
                || game.level == null || game.screen != null || ClientControl.modal()) return;
        close();
        int examined = 0;
        for (var entity : game.level.entitiesForRendering()) {
            if (++examined > 4096) break;
            if (entity.getUUID().equals(target)) { targetInstance = entity; break; }
        }
        pending = new InspectionProtocol.Query(state.generation(), state.encounterId(), UUID.randomUUID(), target);
        ClientPacketDistributor.sendToServer(pending);
    }
    public static void refresh() { if (pending != null) request(pending.target()); }
    public static void tick() {
        if (pending == null) return;
        var game = Minecraft.getInstance(); var state = ClientCombatState.encounter();
        if (state == null || game.level == null || game.player == null || !game.player.isAlive()
                || !pending.generation().equals(state.generation()) || !pending.encounter().equals(state.encounterId())
                || targetInstance != null && (targetInstance.isRemoved() || !targetInstance.isAlive()
                    || game.level.getEntity(targetInstance.getId()) != targetInstance)
                || state.members().stream().noneMatch(m -> m.id().equals(pending.target()))) { close(); return; }
        if (reply == null && ++waiting >= 100) timedOut = true;
    }
    public static void receive(InspectionProtocol.Reply value, IPayloadContext context) {
        var connection = context.connection();
        var level = Minecraft.getInstance().level; var player = Minecraft.getInstance().player;
        context.enqueueWork(() -> {
            var game = Minecraft.getInstance(); var state = ClientCombatState.encounter();
            if (pending == null || timedOut || !pending.equals(value.query()) || state == null
                    || game.level != level || game.player != player || player == null || !player.isAlive()
                    || game.getConnection() == null || game.getConnection().getConnection() != connection
                    || !state.generation().equals(pending.generation()) || !state.encounterId().equals(pending.encounter())
                    || level == null || targetInstance != null && (targetInstance.isRemoved()
                        || level.getEntity(targetInstance.getId()) != targetInstance)) return;
            if (value.status() == InspectionProtocol.Status.OK && targetInstance == null) {
                reply = new InspectionProtocol.Reply(pending, InspectionProtocol.Status.UNAVAILABLE, null);
                return;
            }
            reply = value;
        });
    }
    public static String label(String key) { return Component.translatable("dndturn.inspection." + key).getString(); }
    public static String content() {
        if (pending == null) return "";
        if (targetInstance == null) return label("unavailable");
        if (timedOut) return label("timeout");
        if (reply == null) return label("loading");
        if (reply.status() != InspectionProtocol.Status.OK) return label(reply.status().name().toLowerCase(java.util.Locale.ROOT));
        var out = new StringBuilder(label("sample")).append("\n");
        for (var field : reply.view().fields()) {
            String name = switch (field.id()) {
                case "dndturn:public_name" -> label("name");
                case "dndturn:public_type" -> label("type");
                case "dndturn:public_dodging" -> label("dodging");
                case "dndturn:public_disengaged" -> label("disengaged");
                default -> Component.translatable("dndturn.inspection.field." + field.id().replace(':', '.')).getString();
            };
            if (field.id().equals("dndturn:public_dodging") || field.id().equals("dndturn:public_disengaged"))
                out.append(name).append("\n");
            else {
                String value = field.value();
                if (field.id().equals("dndturn:public_type"))
                    value = Component.translatable("entity." + value.replace(':', '.')).getString();
                out.append(name).append(": ").append(value).append("\n");
            }
        }
        return out.append("\n").append(label("private")).toString();
    }
}
