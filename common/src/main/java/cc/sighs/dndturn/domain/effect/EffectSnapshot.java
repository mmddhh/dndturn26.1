package cc.sighs.dndturn.domain.effect;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.Objects;
import java.util.UUID;

/** Immutable observations. Native effects never become actor-owned tactical instances. */
public sealed interface EffectSnapshot permits EffectSnapshot.Tactical, EffectSnapshot.Vanilla {
    String effect();
    int rank();
    int stacks();
    long remaining();

    record Tactical(UUID instance, String effect, int version, int rank, int stacks, long remaining,
                    EffectDefinition.Clock clock, long revision, long grantRevision,
                    UUID sourceOperation, UUID sourceActor, String sourceAbility) implements EffectSnapshot {
        public Tactical {
            Objects.requireNonNull(instance); FactKey.requireId(effect); Objects.requireNonNull(clock);
            Objects.requireNonNull(sourceOperation);
            if (sourceAbility != null) FactKey.requireId(sourceAbility);
            if (version < 1 || rank < 1 || stacks < 1 || stacks > 64
                    || (clock == EffectDefinition.Clock.EXPLICIT ? remaining != 0 : remaining < 1)
                    || revision < 1 || grantRevision < 1 || grantRevision > revision)
                throw new IllegalArgumentException("tactical effect snapshot");
        }
        public static Tactical capture(EffectInstance effect) {
            return new Tactical(effect.id(), effect.definition().id(), effect.definition().version(),
                    effect.rank(), effect.stacks(), effect.remaining(), effect.definition().clock(),
                    effect.revision(), effect.grantRevision(), effect.sourceOperation(),
                    effect.sourceActor(), effect.sourceAbility());
        }
    }

    /** rank is one-based; remaining is native ticks, -1 means infinite.
     * Only the active effect is projected. A hidden replacement chain is not a stack chain. */
    record Vanilla(String effect, int rank, long remaining, boolean ambient, boolean visible) implements EffectSnapshot {
        public Vanilla {
            FactKey.requireId(effect);
            if (rank < 1 || remaining < -1) throw new IllegalArgumentException("native effect snapshot");
        }
        public int amplifier() { return rank - 1; }
        @Override public int stacks() { return 1; }
    }
}
