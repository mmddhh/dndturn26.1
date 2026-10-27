package cc.sighs.dndturn.gametest;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

/** Packet-level regression; does not claim to exercise a real client render loop. */
final class CursorFacingChecks {
    private CursorFacingChecks() {}

    static void verify(GameTestHelper helper, ServerPlayer player) {
        var channel = (EmbeddedChannel)player.connection.getConnection().channel();
        drainCorrections(channel);
        Vec3 stationary = player.position();
        verifyRejectedPrediction(helper, player, channel);
        for (int i = 0; i < 3; i++) {
            player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(stationary, 35, 15, false, false));
            helper.assertTrue(drainCorrections(channel) == null, "unchanged position started a correction loop");
        }

        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(stationary.add(2, 1, 0), 40, 20, false, false));
        var correction = drainCorrections(channel);
        helper.assertTrue(correction != null, "rejected displacement did not send a correction");
        helper.assertTrue(player.position().equals(stationary), "position correction allowed movement");
        helper.assertTrue(player.getYRot() == 40 && player.getXRot() == 20, "relative correction changed server look");

        // The user aims again before the correction arrives. Apply the exact vanilla
        // packet calculation to current AND previous angles (the interpolation source).
        var latest = new PositionMoveRotation(stationary.add(2, 1, 0), new Vec3(1, 0, 0), 95, -35);
        var applied = PositionMoveRotation.calculateAbsolute(latest, correction.change(), correction.relatives());
        helper.assertTrue(applied.position().equals(stationary) && applied.deltaMovement().equals(Vec3.ZERO),
            "correction did not restore authoritative position and velocity");
        helper.assertTrue(applied.yRot() == 95 && applied.xRot() == -35, "delayed correction rewound newer aim");
        var previous = PositionMoveRotation.calculateAbsolute(latest.withRotation(80, -25), correction.change(), correction.relatives());
        helper.assertTrue(previous.yRot() == 80 && previous.xRot() == -25, "correction rewound interpolation angles");

        player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(correction.id()));
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(applied.position(), applied.yRot(), applied.xRot(), false, false));
        helper.assertTrue(drainCorrections(channel) == null, "teleport acknowledgement generated another correction");
        helper.assertTrue(player.getYRot() == 95 && player.getXRot() == -35, "acknowledgement lost newer aim");

        // The completed handshake must still correct a subsequent illegal displacement.
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(stationary.add(1, 0, 0), false, false));
        correction = drainCorrections(channel);
        helper.assertTrue(correction != null, "completed handshake disabled later movement protection");
        player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(correction.id()));
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(stationary, 95, -35, false, false));
        helper.assertTrue(drainCorrections(channel) == null, "second acknowledgement restarted correction loop");
    }

    private static void verifyRejectedPrediction(GameTestHelper helper, ServerPlayer player, EmbeddedChannel channel) {
        var boat = net.minecraft.world.entity.EntityType.OAK_BOAT.create(player.level(),
            net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        helper.assertTrue(boat != null, "vehicle fixture missing");
        Vec3 body = player.position();
        boat.setPos(body);
        player.level().addFreshEntity(boat);
        try {
            helper.assertTrue(player.startRiding(boat, true, false), "vehicle fixture could not mount");
            Vec3 vehicleBefore = boat.position();
            Vec3 riderBefore = player.position();
            drainCorrections(channel);
            player.connection.handleMoveVehicle(new net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket(
                vehicleBefore.add(3, 0, 0), 60, 0, true));
            helper.assertTrue(player.position().equals(riderBefore) && boat.position().equals(vehicleBefore),
                "rejected vehicle movement moved rider or vehicle");
            channel.runPendingTasks();
            boolean corrected = false;
            Object packet;
            while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket) corrected = true;
                io.netty.util.ReferenceCountUtil.release(packet);
            }
            helper.assertTrue(corrected, "rejected vehicle prediction received no correction");
            player.stopRiding();
            player.setPos(body);
            var dismount = drainCorrections(channel);
            if (dismount != null)
                player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(dismount.id()));
            player.connection.handleInteract(new net.minecraft.network.protocol.game.ServerboundInteractPacket(
                boat.getId(), net.minecraft.world.InteractionHand.MAIN_HAND, Vec3.ZERO, false));
            channel.runPendingTasks();
            boolean inventory = false;
            while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket) inventory = true;
                io.netty.util.ReferenceCountUtil.release(packet);
            }
            helper.assertTrue(inventory && !player.isPassenger(), "rejected interaction lost inventory correction or mounted vehicle");
        } finally {
            player.stopRiding();
            player.setPos(body);
            boat.discard();
            drainCorrections(channel);
        }
    }

    private static ClientboundPlayerPositionPacket drainCorrections(EmbeddedChannel channel) {
        channel.runPendingTasks();
        ClientboundPlayerPositionPacket correction = null;
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof ClientboundPlayerPositionPacket position) correction = position;
            io.netty.util.ReferenceCountUtil.release(packet);
        }
        return correction;
    }
}
