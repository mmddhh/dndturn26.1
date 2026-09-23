package cc.sighs.dndturn.platform.mixin.server.control;

import cc.sighs.dndturn.platform.server.actor.ActorLifecycleHooks;
import cc.sighs.dndturn.platform.server.runtime.MinecraftCombatRuntime;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ServerPlayer#doTick runs from the connection, outside EntityTickEvent.Pre. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTickMixin {
    @Inject(method = "doTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;tick()V", shift = At.Shift.AFTER))
    private void dndturn$observeActorStep(CallbackInfo callback) {
        var player = (ServerPlayer) (Object) this;
        ActorLifecycleHooks.simulated(player);
    }
    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void dndturn$pauseBodyTick(CallbackInfo callback) {
        if (MinecraftCombatRuntime.isBodyPaused((ServerPlayer) (Object) this)) {
            callback.cancel();
        }
    }
}
