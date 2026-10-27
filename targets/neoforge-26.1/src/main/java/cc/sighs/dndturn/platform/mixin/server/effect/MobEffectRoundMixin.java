package cc.sighs.dndturn.platform.mixin.server.effect;

import cc.sighs.dndturn.platform.server.effect.vanilla.EffectTimerAccess;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectInstance.class)
public abstract class MobEffectRoundMixin implements EffectTimerAccess {
    @Shadow private int duration;
    @Shadow private MobEffectInstance hiddenEffect;
    @Unique private boolean dndturn$suppressLifecycle;
    public void dndturn$suppressLifecycle(boolean value) { dndturn$suppressLifecycle=value; }
    @Unique private final UUID dndturn$instance = UUID.randomUUID();
    public UUID dndturn$timerInstance() { return dndturn$instance; }
    public MobEffectInstance dndturn$hiddenEffect() { return hiddenEffect; }
    public void dndturn$duration(int ticks) { duration = ticks; }
    @Invoker("downgradeToHiddenEffect") public abstract boolean dndturn$promoteHidden();
    @Inject(method="onMobHurt", at=@At("HEAD"), cancellable=true)
    private void dndturn$hurt(ServerLevel level, LivingEntity owner, net.minecraft.world.damagesource.DamageSource source,
            float damage, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (dndturn$suppressLifecycle || cc.sighs.dndturn.platform.server.runtime.VanillaEffectHooks.suppressSpecial(owner, (MobEffectInstance)(Object)this)) {
            dndturn$suppressLifecycle=true; ci.cancel();
        }
    }
    @Inject(method="onMobRemoved", at=@At("HEAD"), cancellable=true)
    private void dndturn$removed(ServerLevel level, LivingEntity owner, net.minecraft.world.entity.Entity.RemovalReason reason,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (dndturn$suppressLifecycle || cc.sighs.dndturn.platform.server.runtime.VanillaEffectHooks.suppressSpecial(owner, (MobEffectInstance)(Object)this)) {
            dndturn$suppressLifecycle=true; ci.cancel();
        }
    }
    @Inject(method={"onEffectStarted", "onEffectAdded"}, at=@At("HEAD"), cancellable=true)
    private void dndturn$started(LivingEntity owner, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (dndturn$suppressLifecycle || cc.sighs.dndturn.platform.server.runtime.VanillaEffectHooks.suppressSpecial(owner, (MobEffectInstance)(Object)this)) {
            dndturn$suppressLifecycle=true; ci.cancel();
        }
    }
    @Inject(method="tickServer", at=@At("HEAD"), cancellable=true)
    private void dndturn$ownerClock(ServerLevel level, LivingEntity owner, Runnable update, CallbackInfoReturnable<Boolean> ci) {
        if (cc.sighs.dndturn.platform.server.runtime.VanillaEffectHooks.holdEffect(owner, (MobEffectInstance)(Object)this)) ci.setReturnValue(true);
    }
}
