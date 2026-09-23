package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.PresentationUse;
import cc.sighs.dndturn.client.ClientPresentation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.client.renderer.entity.player.AvatarRenderer.class)
public abstract class PresentationAvatarMixin {
    @Redirect(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Avatar;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"))
    private static InteractionHand dndturn$getUsedItemHandAvatar(net.minecraft.world.entity.Avatar entity) { return PresentationUse.hand(entity); }
    @Redirect(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Avatar;getUseItemRemainingTicks()I"))
    private static int dndturn$getUseItemRemainingTicksAvatar(net.minecraft.world.entity.Avatar entity) { return PresentationUse.remaining(entity); }
}
