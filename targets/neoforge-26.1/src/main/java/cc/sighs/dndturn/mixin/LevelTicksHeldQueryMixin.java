package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.RegionalScheduledTicks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelTicks.class)
public abstract class LevelTicksHeldQueryMixin<T> {
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void dndturn$deduplicateOrHold(ScheduledTick<T> tick, CallbackInfo callback) {
        if (RegionalScheduledTicks.holdScheduled((LevelTicks<T>) (Object) this, tick)) callback.cancel();
    }

    @Inject(method = "hasScheduledTick", at = @At("RETURN"), cancellable = true)
    private void dndturn$heldScheduledTick(BlockPos pos, T type, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValue() && RegionalScheduledTicks.isHeld((LevelTicks<?>) (Object) this, pos, type))
            result.setReturnValue(true);
    }

    @Inject(method = "clearArea", at = @At("HEAD"))
    private void dndturn$clearHeld(BoundingBox area, CallbackInfo callback) {
        RegionalScheduledTicks.clearHeld((LevelTicks<?>) (Object) this, area);
    }

    @Inject(method = "copyAreaFrom", at = @At("HEAD"), cancellable = true)
    private void dndturn$copyHeld(LevelTicks<T> source, BoundingBox area, Vec3i offset, CallbackInfo callback) {
        if (RegionalScheduledTicks.copyAreaSnapshot((LevelTicks<T>) (Object) this, source, area, offset))
            callback.cancel();
    }

    @Inject(method = "count", at = @At("RETURN"), cancellable = true)
    private void dndturn$countHeld(CallbackInfoReturnable<Integer> result) {
        result.setReturnValue(result.getReturnValue() + RegionalScheduledTicks.heldCount((LevelTicks<?>) (Object) this));
    }
}
