package cc.sighs.dndturn.domain.actor;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.effect.EffectSnapshot;
import cc.sighs.dndturn.domain.fact.FactSlice;
import java.util.*;

/** Resolution read model, never a storage owner or an Encounter member. */
public record ActorSnapshot(UUID actor, UUID instance, ActorDefinition definition,
                            Revision revision, FactSlice facts, List<AbilityBinding> abilities, List<EffectSnapshot> effects) {
    public ActorSnapshot(UUID actor, UUID instance, ActorDefinition definition,
                         Revision revision, FactSlice facts, List<AbilityBinding> abilities) {
        this(actor, instance, definition, revision, facts, abilities, List.of());
    }
    /** Capture identity proves only this sample, not that native state remains unchanged. */
    public record Revision(UUID capture, long actorState, String ruleset) {
        public Revision {
            Objects.requireNonNull(capture); Objects.requireNonNull(ruleset);
            if (actorState < 0 || ruleset.isBlank() || ruleset.length() > 128) throw new IllegalArgumentException("actor revision");
        }
    }
    public ActorSnapshot {
        Objects.requireNonNull(actor); Objects.requireNonNull(instance); Objects.requireNonNull(definition);
        Objects.requireNonNull(revision); Objects.requireNonNull(facts); abilities = List.copyOf(abilities);
        effects = List.copyOf(effects);
        if (effects.size() > 512) throw new IllegalArgumentException("actor effect snapshot bound");
        var tacticalIds = new HashSet<UUID>();
        var nativeIds = new HashSet<String>();
        for (var effect : effects) {
            if (effect instanceof EffectSnapshot.Tactical tactical
                    && (!tacticalIds.add(tactical.instance()) || tactical.revision() > revision.actorState())
                    || effect instanceof EffectSnapshot.Vanilla nativeEffect && !nativeIds.add(nativeEffect.effect()))
                throw new IllegalArgumentException("duplicate or future effect observation");
        }
        if (abilities.size() > 1024 || new HashSet<>(abilities).size() != abilities.size()
                || abilities.stream().anyMatch(b -> !actor.equals(b.grant().actor())))
            throw new IllegalArgumentException("actor bindings");
    }
}
