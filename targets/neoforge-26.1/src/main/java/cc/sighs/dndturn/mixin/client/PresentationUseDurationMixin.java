package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.PresentationUse;
import cc.sighs.dndturn.client.ClientPresentation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.ItemOwner;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
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
