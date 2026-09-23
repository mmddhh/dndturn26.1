package cc.sighs.dndturn.domain.ai;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import java.util.*;

/** One bounded pure-value planning input, separated by evidence owner. */
public record AiDecisionContext(ActorSnapshot actor, AiDefinition definition, EncounterPhase phase,
        boolean actionAvailable, String dimension, PerceptionSnapshot perception, Set<UUID> hostile,
        List<Option> options, ActionIntent movementOpportunity, AiRuntimeState runtime) {
    public record Option(UUID target, AbilityBinding binding, boolean available, Set<AiAffordance> semantics) {
        public Option { Objects.requireNonNull(target); Objects.requireNonNull(binding); semantics = Set.copyOf(semantics); }
    }
    public AiDecisionContext {
        Objects.requireNonNull(actor); Objects.requireNonNull(definition); Objects.requireNonNull(phase);
        Objects.requireNonNull(runtime);
        if (!actor.actor().equals(runtime.actor()) || !actor.instance().equals(runtime.instance()))
            throw new IllegalArgumentException("AI runtime instance");
        Objects.requireNonNull(dimension); Objects.requireNonNull(perception);
        hostile = Set.copyOf(hostile); options = List.copyOf(options);
        if (options.size() > 65536) throw new IllegalArgumentException("AI option bound");
        if (options.stream().anyMatch(o -> !actor.abilities().contains(o.binding()))) throw new IllegalArgumentException("unbound AI option");
        if (movementOpportunity != null && movementOpportunity.capability() != ActionIntent.Capability.MOVE)
            throw new IllegalArgumentException("movement opportunity");
    }
}
