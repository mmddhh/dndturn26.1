package cc.sighs.dndturn.combat;

import java.util.UUID;
import net.minecraft.world.effect.MobEffectInstance;

/** Fixed .84 timer fields; no execution permission. */
public interface EffectTimerAccess {
    UUID dndturn$timerInstance();
    MobEffectInstance dndturn$hiddenEffect();
    void dndturn$duration(int ticks);
    boolean dndturn$promoteHidden();
}
