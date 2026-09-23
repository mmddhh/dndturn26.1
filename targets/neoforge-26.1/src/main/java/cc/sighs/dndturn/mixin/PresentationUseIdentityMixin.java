package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.PresentationUseIdentity;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class PresentationUseIdentityMixin implements PresentationUseIdentity {
    @Unique private UUID dndturn$useIdentity;
    @Override public UUID dndturn$useIdentity() { return dndturn$useIdentity; }
    // After the loader's cancellation return, only when a new use actually writes useItem.
    @Inject(method = "startUsingItem", at = @At(value = "FIELD", opcode = 181,
        target = "Lnet/minecraft/world/entity/LivingEntity;useItem:Lnet/minecraft/world/item/ItemStack;", shift = At.Shift.AFTER))
    private void dndturn$start(InteractionHand hand, CallbackInfo ci) { dndturn$useIdentity = UUID.randomUUID(); }
    @Inject(method = "stopUsingItem", at = @At("TAIL"))
    private void dndturn$stop(CallbackInfo ci) { dndturn$useIdentity = null; }
}
