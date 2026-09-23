package cc.sighs.dndturn.platform.server.control;

import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Network movement seam; caller must be after the vanilla server-thread handoff. */
public final class MovementInputHooks {
    private MovementInputHooks() {}
    public static boolean prepare(ServerPlayer player, ServerboundMovePlayerPacket packet, boolean correctionPending) {
        var runtime = ServerRuntime.existingEncounter(player.level().getServer());
        return runtime == null || runtime.preparePlayerMovePacket(player, packet, correctionPending);
    }
    public static void observe(ServerPlayer player, Vec3 before, boolean hasPosition, boolean groundBefore,
                               boolean waterBefore, boolean correctionPending) {
        var runtime = ServerRuntime.existingEncounter(player.level().getServer());
        if (runtime != null) runtime.observePlayerMovePacket(player, before, hasPosition, groundBefore, waterBefore, correctionPending);
    }
}
