package cc.sighs.dndturn.platform.mixin.server.control;

import cc.sighs.dndturn.platform.server.control.InputPolicy;
import cc.sighs.dndturn.platform.server.control.MovementInputHooks;
import cc.sighs.dndturn.platform.server.control.VanillaInputPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep network and chat alive while suppressing body input in a local encounter. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow public ServerPlayer player;
    @Shadow private Vec3 awaitingPositionFromClient;
    @Unique private Vec3 dndturn$moveBefore;
    @Unique private boolean dndturn$groundBefore;
    @Unique private boolean dndturn$waterBefore;
    @Unique private boolean dndturn$correctionPending;

    private boolean dndturn$pauseInput() {
        // Vanilla schedules these handlers from the network thread onto the server thread.
        // Do not read encounter state until that handoff has happened.
        return player.level().getServer().isSameThread() && !InputPolicy.movement(AuthorityProjection.input(player)).allowed();
    }

    private boolean dndturn$pauseOtherInput() {
        return player.level().getServer().isSameThread()
            && !InputPolicy.gameplay(AuthorityProjection.input(player)).allowed();
    }
    @Inject(method = "handlePlayerInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$activeInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (dndturn$pauseInput()) {
            player.setLastClientInput(net.minecraft.world.entity.player.Input.EMPTY);
            player.setShiftKeyDown(false);
            ci.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$movePlayer(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        dndturn$moveBefore = null;
        if (dndturn$pauseInput()) {
            // Leave malformed values to vanilla's disconnect path. Rotation never grants movement.
            if (Double.isNaN(packet.getX(0)) || Double.isNaN(packet.getY(0))
                || Double.isNaN(packet.getZ(0)) || !Float.isFinite(packet.getYRot(0))
                || !Float.isFinite(packet.getXRot(0))) return;
            if (packet.hasRotation() && player.connection.hasClientLoaded() && !player.wonGame
                && player.isAlive() && !player.isSleeping()) {
                player.absSnapRotationTo(net.minecraft.util.Mth.wrapDegrees(packet.getYRot(player.getYRot())),
                    net.minecraft.util.Mth.wrapDegrees(packet.getXRot(player.getXRot())));
            }
            // A teleport acknowledgement is followed by PosRot; an unchanged position
            // (also sent by vanilla's periodic reminder) must not start another teleport.
            if (packet.hasPosition() && (packet.getX(player.getX()) != player.getX()
                || packet.getY(player.getY()) != player.getY()
                || packet.getZ(player.getZ()) != player.getZ())) dndturn$correctMovement();
            callback.cancel();
        }
        else if (player.level().getServer().isSameThread()) {
            dndturn$correctionPending = awaitingPositionFromClient != null;
            if (!MovementInputHooks.prepare(player, packet,
                dndturn$correctionPending)) {
                dndturn$correctMovement();
                callback.cancel();
                return;
            }
            dndturn$moveBefore = player.position();
            dndturn$groundBefore = player.onGround();
            dndturn$waterBefore = player.isInWater();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void dndturn$observedMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        Vec3 before = dndturn$moveBefore;
        dndturn$moveBefore = null;
        if (before == null || !player.level().getServer().isSameThread()) return;
        MovementInputHooks.observe(player, before, packet.hasPosition(),
            dndturn$groundBefore, dndturn$waterBefore, dndturn$correctionPending);
    }

    @Inject(method = "handleMoveVehicle", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$moveVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo callback) {
        if (dndturn$pauseOtherInput()) callback.cancel();
    }

    @Inject(method = "handlePlayerCommand", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$specialMoveCommand(ServerboundPlayerCommandPacket packet, CallbackInfo callback) {
        if (dndturn$pauseOtherInput() && (packet.getAction() == ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
            || packet.getAction() == ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP
            || packet.getAction() == ServerboundPlayerCommandPacket.Action.STOP_RIDING_JUMP))
            callback.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$playerAction(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        if (!dndturn$pauseOtherInput()) return;
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
            && InputPolicy.organize(AuthorityProjection.input(player)).allowed()) return;
        switch (packet.getAction()) {
            case START_DESTROY_BLOCK, STOP_DESTROY_BLOCK, ABORT_DESTROY_BLOCK ->
                VanillaInputPolicy.rejectPrediction(player, packet.getSequence(), packet.getPos());
            default -> VanillaInputPolicy.correctInventory(player);
        }
        callback.cancel();
    }

    @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$useItemOn(ServerboundUseItemOnPacket packet, CallbackInfo callback) {
        if (dndturn$pauseOtherInput()) {
            VanillaInputPolicy.rejectPrediction(player, packet.getSequence(), packet.getHitResult().getBlockPos());
            VanillaInputPolicy.rejectPrediction(player, packet.getSequence(),
                packet.getHitResult().getBlockPos().relative(packet.getHitResult().getDirection()));
            callback.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$useItem(ServerboundUseItemPacket packet, CallbackInfo callback) {
        if (dndturn$pauseOtherInput()) {
            VanillaInputPolicy.rejectPrediction(player, packet.getSequence(), null);
            callback.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$interact(ServerboundInteractPacket packet, CallbackInfo callback) {
        if (dndturn$pauseOtherInput()) callback.cancel();
    }

    @Inject(method = "handleSetCarriedItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$hotbar(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (player.level().getServer().isSameThread() && !InputPolicy.carriedSlot(AuthorityProjection.input(player)).allowed()) {
            VanillaInputPolicy.correctInventory(player);
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$container(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (dndturn$pauseOtherInput()
            && !VanillaInputPolicy.mayClick(player, packet)) {
            VanillaInputPolicy.correctInventory(player);
            ci.cancel();
        }
    }

    @Inject(method = {"handlePlaceRecipe", "handleContainerButtonClick", "handleSetCreativeModeSlot"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$unsupportedInventory(CallbackInfo ci) {
        if (dndturn$pauseOtherInput()) {
            VanillaInputPolicy.correctInventory(player);
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAbilities", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$abilities(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (dndturn$pauseOtherInput()) {
            player.onUpdateAbilities();
            ci.cancel();
        }
    }

    @Unique private void dndturn$correctMovement() {
        if (awaitingPositionFromClient == null)
            // Correct only position/velocity. Absolute angles would rewind newer local
            // look input when this packet arrives; vanilla relative zero keeps it intact.
            player.connection.teleport(new net.minecraft.world.entity.PositionMoveRotation(
                player.position(), Vec3.ZERO, 0, 0), java.util.Set.of(
                    net.minecraft.world.entity.Relative.Y_ROT, net.minecraft.world.entity.Relative.X_ROT));
    }
}
