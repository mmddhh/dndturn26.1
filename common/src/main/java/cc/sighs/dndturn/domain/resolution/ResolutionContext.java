package cc.sighs.dndturn.domain.resolution;

import cc.sighs.dndturn.domain.ability.AbilityInvocation;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.domain.fact.FactSlice;
import java.util.Objects;

/** Complete detached evaluation boundary. Native observation/permission checks happen before capture. */
public record ResolutionContext(CombatantSnapshot actor, EncounterInput encounter,
                                AbilityInvocation invocation, FactSlice world, FactSlice targetFacts, Stage stage,
                                TriggeredExecutionRecord trigger) {
    public ResolutionContext(CombatantSnapshot actor, EncounterInput encounter, AbilityInvocation invocation,
                             FactSlice world, FactSlice targetFacts, Stage stage) {
        this(actor, encounter, invocation, world, targetFacts, stage, null);
    }
    public ResolutionContext(CombatantSnapshot actor, EncounterAuthority.StateView encounter, AbilityInvocation invocation,
                             FactSlice world, FactSlice targetFacts, Stage stage) {
        this(actor, EncounterInput.capture(encounter, invocation), invocation, world, targetFacts, stage);
    }
    public enum Stage { PROPOSAL, CONTINUATION }
    public ResolutionContext {
        Objects.requireNonNull(actor); Objects.requireNonNull(encounter);
        Objects.requireNonNull(invocation); Objects.requireNonNull(world);
        Objects.requireNonNull(targetFacts);
        Objects.requireNonNull(stage);
        if (!actor.actor().actor().equals(invocation.actor())
                || !actor.actor().revision().equals(invocation.revision())
                || !Objects.equals(actor.participant(), encounter.members().get(invocation.actor())))
            throw new IllegalArgumentException("mixed resolution revisions");
        world = world.restrict(invocation.binding().definition().reads());
        targetFacts = targetFacts.restrict(invocation.binding().definition().reads().target());
        var snapshot = actor.actor();
        actor = new CombatantSnapshot(new ActorSnapshot(snapshot.actor(), snapshot.instance(), snapshot.definition(),
                snapshot.revision(), snapshot.facts().restrict(invocation.binding().definition().reads().actor()),
                java.util.List.of(invocation.binding()), snapshot.effects()), actor.participant());
        if (trigger != null && (trigger.status() != TriggeredExecutionRecord.Status.PENDING
                || !trigger.event().actor().equals(invocation.actor())
                || !trigger.invocation().ability().equals(invocation.binding().id())
                || trigger.invocation().version() != invocation.binding().version()
                || !trigger.invocation().target().equals(invocation.intent().target())
                || !trigger.source().source().equals(invocation.binding().source())))
            throw new IllegalArgumentException("trigger resolution evidence mismatch");
    }
}
