package cc.sighs.dndturn.platform.mixin.server.world;

import cc.sighs.dndturn.platform.server.world.WorldSimulationHooks;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSimulationStepMixin {
    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void dndturn$beforeWorldSimulation(BooleanSupplier haveTime, CallbackInfo callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        WorldSimulationHooks.beforeLevelTick(level);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V", ordinal = 0))
    private void dndturn$beforeBlockQueue(BooleanSupplier haveTime, CallbackInfo callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        WorldSimulationHooks.beforeBlockQueue(level);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V", ordinal = 1))
    private void dndturn$beforeFluidQueue(BooleanSupplier haveTime, CallbackInfo callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        WorldSimulationHooks.beforeFluidQueue(level);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void dndturn$afterWorldSimulation(BooleanSupplier haveTime, CallbackInfo callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        WorldSimulationHooks.afterLevelTick(level);
    }
}
