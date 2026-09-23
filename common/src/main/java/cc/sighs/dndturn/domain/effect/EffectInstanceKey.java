package cc.sighs.dndturn.domain.effect;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.UUID;

/** Actor-local merge identity, selected by definition policy rather than caller instance UUID. */
public record EffectInstanceKey(String definition, UUID sourceActor, String sourceAbility, UUID application) {
    public EffectInstanceKey {
        FactKey.requireId(definition);
        if (sourceAbility != null) FactKey.requireId(sourceAbility);
    }
}
