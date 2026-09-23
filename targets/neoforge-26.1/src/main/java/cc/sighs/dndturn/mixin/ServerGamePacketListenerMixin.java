package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.MinecraftCombatRuntime;
import cc.sighs.dndturn.combat.ServerCombatService;
import cc.sighs.dndturn.combat.VanillaInputPolicy;
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
        return player.level().getServer().isSameThread() && MinecraftCombatRuntime.isPlayerMovementPaused(player);
    }

    private boolean dndturn$pauseOtherInput() {
        return player.level().getServer().isSameThread()
            && VanillaInputPolicy.controlled(player);
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$movePlayer(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        dndturn$moveBefore = null;
        if (dndturn$pauseInput()) {
            dndturn$correctMovement();
            callback.cancel();
        }
        else if (player.level().getServer().isSameThread()) {
            dndturn$correctionPending = awaitingPositionFromClient != null;
            ServerCombatService service = ServerCombatService.existing(player.level().getServer());
            if (service != null && !service.preparePlayerMovePacket(player, packet,
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
        ServerCombatService service = ServerCombatService.existing(player.level().getServer());
        if (service != null) service.observePlayerMovePacket(player, before, packet.hasPosition(),
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
        if (!player.level().getServer().isSameThread() || !VanillaInputPolicy.controlled(player)) return;
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
            && VanillaInputPolicy.mayOrganize(player)) return;
        switch (packet.getAction()) {
            case START_DESTROY_BLOCK, STOP_DESTROY_BLOCK, ABORT_DESTROY_BLOCK ->
                VanillaInputPolicy.rejectPrediction(player, packet.getSequence(), packet.getPos());
            default -> VanillaInputPolicy.correctInventory(player);
        }
        callback.cancel();
    }

    @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$useItemOn(ServerboundUseItemOnPacket packet, CallbackInfo callback) {
        if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)) {
            VanillaInputPolicy.rejectPrediction(player, packet.getSequence(), packet.getHitResult().getBlockPos());
            VanillaInputPolicy.rejectPrediction(player, packet.getSequence(),
                packet.getHitResult().getBlockPos().relative(packet.getHitResult().getDirection()));
            callback.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$useItem(ServerboundUseItemPacket packet, CallbackInfo callback) {
        if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)) {
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
        if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)
            && (ServerCombatService.existing(player.level().getServer()) != null
                && ServerCombatService.existing(player.level().getServer()).isMember(player.getUUID())
                || !VanillaInputPolicy.mayOrganize(player))) {
            VanillaInputPolicy.correctInventory(player);
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$container(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)
            && !VanillaInputPolicy.mayClick(player, packet)) {
            VanillaInputPolicy.correctInventory(player);
            ci.cancel();
        }
    }

    @Inject(method = {"handlePlaceRecipe", "handleContainerButtonClick", "handleSetCreativeModeSlot"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$unsupportedInventory(CallbackInfo ci) {
        if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)) {
            VanillaInputPolicy.correctInventory(player);
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAbilities", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
    private void dndturn$abilities(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)) {
            player.onUpdateAbilities();
            ci.cancel();
        }
    }

    @Unique private void dndturn$correctMovement() {
        if (awaitingPositionFromClient == null)
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }
}
