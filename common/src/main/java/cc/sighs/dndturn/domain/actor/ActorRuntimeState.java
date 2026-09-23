package cc.sighs.dndturn.domain.actor;

import cc.sighs.dndturn.domain.effect.EffectInstance;
import java.util.*;

public record ActorRuntimeState(Map<UUID, EffectInstance> effects) {
    public ActorRuntimeState {
        effects = Map.copyOf(effects);
        if (effects.size() > 256) throw new IllegalArgumentException("effect bound");
        effects.forEach((id, value) -> {
            if (!id.equals(value.id())) throw new IllegalArgumentException("effect identity");
        });
        if (effects.values().stream().map(EffectInstance::key).distinct().count() != effects.size())
            throw new IllegalArgumentException("duplicate effect merge key");
    }
    public static ActorRuntimeState empty() { return new ActorRuntimeState(Map.of()); }
}
