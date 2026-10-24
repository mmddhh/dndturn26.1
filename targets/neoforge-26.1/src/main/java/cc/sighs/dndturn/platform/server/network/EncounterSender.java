package cc.sighs.dndturn.platform.server.network;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import static cc.sighs.dndturn.platform.network.EncounterProtocol.*;
public final class EncounterSender {
 private EncounterSender() {}
    public static boolean sendBodyState(ServerPlayer player, BodyState state) {
        // GameTest players use an embedded connection without mod payload negotiation.
        if (NetworkRegistry.hasChannel(player.connection, BodyState.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, state);
            return true;
        }
        return false;
    }
    public static boolean sendEncounterState(ServerPlayer player, EncounterState state) {
        if (NetworkRegistry.hasChannel(player.connection, EncounterState.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, state);
            return true;
        }
        return false;
    }
    public static boolean sendEntitySimulation(ServerPlayer player, EntitySimulation state) {
        if (!NetworkRegistry.hasChannel(player.connection, EntitySimulation.TYPE.id())) return false;
        PacketDistributor.sendToPlayer(player, state);
        return true;
    }
    public static void sendConsentState(ServerPlayer player, ConsentState state) {
        if (NetworkRegistry.hasChannel(player.connection, ConsentState.TYPE.id()))
            PacketDistributor.sendToPlayer(player, state);
    }
    public static void sendIntentStatus(ServerPlayer player, IntentStatus status) {
        DebugDiagnostics.log("CONTROL_RESULT", () -> "actor=" + player.getUUID() + " result=" + status);
        if (NetworkRegistry.hasChannel(player.connection, IntentStatus.TYPE.id()))
            PacketDistributor.sendToPlayer(player, status);
    }
}
