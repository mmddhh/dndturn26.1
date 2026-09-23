package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.MinecraftCombatRuntime;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ServerPlayer#doTick runs from the connection, outside EntityTickEvent.Pre. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTickMixin {
    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void dndturn$pauseBodyTick(CallbackInfo callback) {
        if (MinecraftCombatRuntime.isBodyPaused((ServerPlayer) (Object) this)) {
            callback.cancel();
        }
    }
}
