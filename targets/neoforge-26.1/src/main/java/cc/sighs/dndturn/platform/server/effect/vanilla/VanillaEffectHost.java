package cc.sighs.dndturn.platform.server.effect.vanilla;
import cc.sighs.dndturn.platform.server.actor.ActorLifecycleHost;
import java.util.UUID;
public interface VanillaEffectHost extends ActorLifecycleHost {
    UUID generation();
    long clock();
    void changed();
    void completeWorldOutcome(Runnable completion);
}
