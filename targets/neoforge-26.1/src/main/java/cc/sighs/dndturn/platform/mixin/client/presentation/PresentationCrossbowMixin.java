package cc.sighs.dndturn.platform.mixin.client.presentation;

import cc.sighs.dndturn.platform.client.presentation.PresentationUse;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.renderer.item.properties.numeric.CrossbowPull.class)
public abstract class PresentationCrossbowMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void dndturn$pull(ItemStack stack, ClientLevel level, ItemOwner owner, int seed, CallbackInfoReturnable<Float> ci) {
        var entity = owner == null ? null : owner.asLivingEntity();
        var use = PresentationUse.snapshot(entity);
        if (use != null) ci.setReturnValue(PresentationUse.matches(entity, stack) && !CrossbowItem.isCharged(stack)
            ? (float)use.used() / CrossbowItem.getChargeDuration(stack, entity) : 0F);
    }
}
