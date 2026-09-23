package cc.sighs.dndturn.platform.mixin.server.control;

import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingActiveProcessMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"))
    private void dndturn$singleMovementDriver(LivingEntity entity, net.minecraft.world.phys.Vec3 input) {
        // Authorized player motion already arrives through the vanilla movement packet driver.
        // Idle controlled players use ordinary server body physics with cleared active input.
        if (!ActorControlPolicy.bodyTravel(AuthorityProjection.actor(entity),
            entity instanceof net.minecraft.server.level.ServerPlayer).allowed()) return;
        entity.travel(input);
    }
    @Inject(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;applyInput()V", shift = At.Shift.AFTER))
    private void dndturn$input(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (!ActorControlPolicy.nativeMovement(AuthorityProjection.actor(entity)).allowed()) {
            entity.xxa = 0; entity.yya = 0; entity.zza = 0; entity.setJumping(false);
        }
    }
    @Inject(method = "updatingUsingItem", at = @At("HEAD"), cancellable = true)
    private void dndturn$use(CallbackInfo ci) {
        if (!ActorControlPolicy.activeUse(AuthorityProjection.actor((LivingEntity)(Object)this)).allowed()) ci.cancel();
    }
}
