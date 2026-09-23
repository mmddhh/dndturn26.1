package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ServerCombatService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The loader's cancellable death event precedes this confirmed ServerPlayer death boundary. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {
    @Inject(method = "die", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;markClientUnloadedAfterDeath()V",
        shift = At.Shift.AFTER))
    private void dndturn$confirmPlayerDeath(DamageSource source, CallbackInfo callback) {
        var player = (ServerPlayer) (Object) this;
        var service = ServerCombatService.existing(player.level().getServer());
        if (service != null) service.noteCompletedPlayerDeath(player);
    }
}
