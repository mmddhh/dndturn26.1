package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.PresentationUse;
import cc.sighs.dndturn.client.ClientPresentation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
public abstract class PresentationHandsMixin {
    // Vanilla's use formula is usedTicks + partial - 1. A captured snapshot is already at its observation time.
    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float dndturn$usePartial(float partial) {
        var use = PresentationUse.snapshot(net.minecraft.client.Minecraft.getInstance().player);
        return use != null && use.active() ? 1 : partial;
    }
    @Redirect(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F"))
    private float dndturn$attack(net.minecraft.client.player.LocalPlayer player, float partial) {
        return ClientPresentation.attack(player, partial, player.getAttackAnim(partial));
    }
    @Redirect(method = "renderHandsWithItems", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;swingingArm:Lnet/minecraft/world/InteractionHand;"))
    private InteractionHand dndturn$hand(net.minecraft.client.player.LocalPlayer player) {
        return ClientPresentation.attackHand(player, player.swingingArm);
    }
    @Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getSwingAnimation()Lnet/minecraft/world/item/component/SwingAnimation;"))
    private SwingAnimation dndturn$animation(ItemStack stack, net.minecraft.client.player.AbstractClientPlayer player,
        float partial, float xRot, InteractionHand hand, float attack, ItemStack rendered, float height,
        com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        return ClientPresentation.swingAnimation(player, hand, stack.getSwingAnimation());
    }
    @Redirect(method = "evaluateWhichHandsToRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private static boolean dndturn$isUsingItemLocalPlayer(net.minecraft.client.player.LocalPlayer entity) { return PresentationUse.using(entity); }
    @Redirect(method = "selectionUsingItemWhileHoldingBowLike", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getUseItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack dndturn$getUseItemLocalPlayer(net.minecraft.client.player.LocalPlayer entity) { return PresentationUse.stack(entity); }
    @Redirect(method = "selectionUsingItemWhileHoldingBowLike", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"))
    private static InteractionHand dndturn$getUsedItemHandLocalPlayer(net.minecraft.client.player.LocalPlayer entity) { return PresentationUse.hand(entity); }
    @Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isUsingItem()Z"))
    private boolean dndturn$isUsingItemAbstractClientPlayer(net.minecraft.client.player.AbstractClientPlayer entity, net.minecraft.client.player.AbstractClientPlayer player, float partial, float xRot,
        InteractionHand hand, float attack, ItemStack stack, float height, com.mojang.blaze3d.vertex.PoseStack pose,
        net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        var use = PresentationUse.snapshot(entity);
        return use == null ? entity.isUsingItem() : PresentationUse.matches(entity, stack);
    }
    @Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUseItemRemainingTicks()I"))
    private int dndturn$getUseItemRemainingTicksAbstractClientPlayer(net.minecraft.client.player.AbstractClientPlayer entity, net.minecraft.client.player.AbstractClientPlayer player, float partial, float xRot,
        InteractionHand hand, float attack, ItemStack stack, float height, com.mojang.blaze3d.vertex.PoseStack pose,
        net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        var use = PresentationUse.snapshot(entity);
        return use == null ? entity.getUseItemRemainingTicks() : PresentationUse.matches(entity, stack) ? use.remaining() : 0;
    }
    @Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"))
    private InteractionHand dndturn$getUsedItemHandAbstractClientPlayer(net.minecraft.client.player.AbstractClientPlayer entity) { return PresentationUse.hand(entity); }
    @Redirect(method = {"applyEatTransform", "applyBrushTransform"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getUseItemRemainingTicks()I"))
    private int dndturn$getUseItemRemainingTicksPlayer(net.minecraft.world.entity.player.Player entity) { return PresentationUse.remaining(entity); }
}
