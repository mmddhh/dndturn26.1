package cc.sighs.dndturn.platform.mixin.client.presentation;

import cc.sighs.dndturn.platform.client.presentation.PresentationUse;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UseDuration.class)
public abstract class PresentationUseDurationMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void dndturn$duration(ItemStack stack, ClientLevel level, ItemOwner owner, int seed, CallbackInfoReturnable<Float> ci) {
        var entity = owner == null ? null : owner.asLivingEntity();
        var use = PresentationUse.snapshot(entity);
        if (use != null) ci.setReturnValue(PresentationUse.matches(entity, stack)
            ? (float)(((UseDuration)(Object)this).remaining() ? use.remaining() : use.used()) : 0F);
    }
    @Inject(method = "useDuration", at = @At("HEAD"), cancellable = true)
    private static void dndturn$used(ItemStack stack, net.minecraft.world.entity.LivingEntity entity, CallbackInfoReturnable<Integer> ci) {
        var use = PresentationUse.snapshot(entity);
        if (use != null) ci.setReturnValue(PresentationUse.matches(entity, stack) ? use.used() : 0);
    }
}
