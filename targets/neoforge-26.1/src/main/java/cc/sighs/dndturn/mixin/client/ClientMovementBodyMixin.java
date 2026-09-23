package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientCombatState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local movement prediction keeps running while body effects and active item use remain paused. */
@Mixin(LivingEntity.class)
public abstract class ClientMovementBodyMixin {
    private boolean dndturn$holdBody() {
        return (Object) this == Minecraft.getInstance().player
            && ClientCombatState.holdBodySubsystemsDuringMovement();
    }

    @Inject(method = "tickEffects", at = @At("HEAD"), cancellable = true)
    private void dndturn$holdEffects(CallbackInfo callback) {
        if (dndturn$holdBody()) callback.cancel();
    }

    @Inject(method = "updateUsingItem(Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("HEAD"), cancellable = true)
    private void dndturn$holdItemUse(ItemStack stack, CallbackInfo callback) {
        if (dndturn$holdBody()) callback.cancel();
    }
}
