package cc.sighs.dndturn.platform.server.effect.vanilla;

import java.util.UUID;
import net.minecraft.world.effect.MobEffectInstance;

/** Native timer fields; no execution permission. */
public interface EffectTimerAccess {
    UUID dndturn$timerInstance();
    MobEffectInstance dndturn$hiddenEffect();
    void dndturn$duration(int ticks);
    boolean dndturn$promoteHidden();
    void dndturn$suppressLifecycle(boolean value);
}
