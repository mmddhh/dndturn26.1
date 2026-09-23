package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.PresentationUse;
import cc.sighs.dndturn.client.ClientPresentation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.client.renderer.entity.HumanoidMobRenderer.class)
public abstract class PresentationHumanoidMixin {
    @Redirect(method = "extractHumanoidRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getUseItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack dndturn$getUseItemLivingEntity(net.minecraft.world.entity.LivingEntity entity) { return PresentationUse.stack(entity); }
    @Redirect(method = "extractHumanoidRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"))
    private static InteractionHand dndturn$getUsedItemHandLivingEntity(net.minecraft.world.entity.LivingEntity entity) { return PresentationUse.hand(entity); }
    @Redirect(method = "extractHumanoidRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isUsingItem()Z"))
    private static boolean dndturn$isUsingItemLivingEntity(net.minecraft.world.entity.LivingEntity entity) { return PresentationUse.using(entity); }
    @Redirect(method = "extractHumanoidRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getTicksUsingItem(F)F"))
    private static float dndturn$getTicksUsingItemLivingEntity(net.minecraft.world.entity.LivingEntity entity, float partial) { return PresentationUse.used(entity, partial); }
}
