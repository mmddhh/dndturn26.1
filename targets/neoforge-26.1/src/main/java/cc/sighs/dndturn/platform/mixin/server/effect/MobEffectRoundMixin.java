package cc.sighs.dndturn.platform.mixin.server.effect;

import cc.sighs.dndturn.platform.server.effect.vanilla.EffectTimerAccess;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectRoundController;
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
    @Unique private final UUID dndturn$instance = UUID.randomUUID();
    public UUID dndturn$timerInstance() { return dndturn$instance; }
    public MobEffectInstance dndturn$hiddenEffect() { return hiddenEffect; }
    public void dndturn$duration(int ticks) { duration = ticks; }
    @Invoker("downgradeToHiddenEffect") public abstract boolean dndturn$promoteHidden();
    @Inject(method="tickServer", at=@At("HEAD"), cancellable=true)
    private void dndturn$ownerClock(ServerLevel level, LivingEntity owner, Runnable update, CallbackInfoReturnable<Boolean> ci) {
        if (VanillaEffectRoundController.holdEffect(owner, (MobEffectInstance)(Object)this)) ci.setReturnValue(true);
    }
}
