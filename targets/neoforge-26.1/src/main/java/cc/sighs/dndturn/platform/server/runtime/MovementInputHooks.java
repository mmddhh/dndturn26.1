package cc.sighs.dndturn.platform.server.runtime;

import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Network movement seam; caller must be after the vanilla server-thread handoff. */
public final class MovementInputHooks {
    private MovementInputHooks() {}
    /** Preserve vanilla observer maintenance even when the proposed body movement is rejected. */
    public static void refreshTracking(ServerPlayer player) {
        if (player.connection.hasClientLoaded() && !player.wonGame)
            player.level().getChunkSource().move(player);
    }
    public static void rejectVehicleMove(ServerPlayer player) {
        if (!player.connection.hasClientLoaded()) return;
        var vehicle = player.getRootVehicle();
        if (vehicle != player && vehicle.getControllingPassenger() == player)
            player.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(vehicle));
        refreshTracking(player);
    }
    public static boolean prepare(ServerPlayer player, ServerboundMovePlayerPacket packet, boolean correctionPending) {
        var runtime = ServerRuntime.existingEncounter(player.level().getServer());
        return runtime == null || runtime.movement().preparePlayerMovePacket(player, packet, correctionPending);
    }
    public static void observe(ServerPlayer player, Vec3 before, boolean hasPosition, boolean groundBefore,
                               boolean waterBefore, boolean correctionPending) {
        var runtime = ServerRuntime.existingEncounter(player.level().getServer());
        if (runtime != null) runtime.movement().observePlayerMovePacket(player, before, hasPosition, groundBefore, waterBefore, correctionPending);
    }
}
