package cc.sighs.dndturn.domain.effect;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;

/** Detached, immutable instance of a standardized actor effect. */
public record EffectInstance(UUID id, UUID sourceOperation, EffectDefinition definition, int stacks,
                        long remaining, long revision, int rank, UUID sourceActor, String sourceAbility,
                        long grantRevision) {
    public EffectInstance(UUID id, UUID sourceOperation, EffectDefinition definition, int stacks,
                     long remaining, long revision, int rank, UUID sourceActor, String sourceAbility) {
        this(id, sourceOperation, definition, stacks, remaining, revision, rank, sourceActor, sourceAbility, revision);
    }
    public EffectInstance(UUID id, UUID sourceOperation, EffectDefinition definition, int stacks, long remaining, long revision) {
        this(id, sourceOperation, definition, stacks, remaining, revision, 1, null, null);
    }
    public EffectInstance {
        Objects.requireNonNull(id); Objects.requireNonNull(sourceOperation); Objects.requireNonNull(definition);
        if (remaining < 0) throw new IllegalArgumentException("negative effect lifetime");
        // Explicit removal is the lifetime. Zero is canonical and never decremented.
        if (definition.clock() == EffectDefinition.Clock.EXPLICIT) remaining = 0;
        if (sourceAbility != null) FactKey.requireId(sourceAbility);
        if (definition.instancePolicy() == EffectDefinition.InstancePolicy.PER_SOURCE_ACTOR && sourceActor == null
                || definition.instancePolicy() == EffectDefinition.InstancePolicy.PER_SOURCE_ABILITY && (sourceActor == null || sourceAbility == null))
            throw new IllegalArgumentException("effect source identity required");
        if (stacks < 1 || stacks > definition.maxStacks()
                || (definition.clock() == EffectDefinition.Clock.EXPLICIT ? remaining != 0 : remaining < 1) || revision < 1 || rank < 1
                || grantRevision < 1 || grantRevision > revision)
            throw new IllegalArgumentException("effect instance");
    }
    public EffectInstanceKey key() {
        return switch (definition.instancePolicy()) {
            case SINGLE_PER_TARGET -> new EffectInstanceKey(definition.id(), null, null, null);
            case PER_SOURCE_ACTOR -> new EffectInstanceKey(definition.id(), sourceActor, null, null);
            case PER_SOURCE_ABILITY -> new EffectInstanceKey(definition.id(), sourceActor, sourceAbility, null);
            case UNIQUE_APPLICATION -> new EffectInstanceKey(definition.id(), null, null, sourceOperation);
        };
    }
    public EffectInstance withValues(int count, long duration, long nextRevision) {
        return new EffectInstance(id, sourceOperation, definition, count, duration, nextRevision, rank, sourceActor, sourceAbility,
                count == stacks ? grantRevision : nextRevision);
    }
}
