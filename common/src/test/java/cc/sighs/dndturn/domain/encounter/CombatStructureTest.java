package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.domain.ability.*;
import cc.sighs.dndturn.domain.action.*;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.actor.TriggeredAbilityInvocation;
import cc.sighs.dndturn.domain.encounter.operation.*;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatStructureTest {
    private static UUID id(long n) { return new UUID(0, n); }
    private static ProcessState process(long id, Set<String> controls) {
        return new ProcessState(id(id), new ProcessDefinition("test:channel", 1,
                ProcessDefinition.Clock.AUTHORIZED_EXECUTION_STEP, ProcessDefinition.Recovery.FAIL_UNKNOWN,
                2, controls, "test"), new ProcessState.Owner(ProcessState.Ownership.ACTOR, id(1)),
                id(id), id(id), id(3), id(4), 30, 0, "EXECUTE", Map.of(), 0, 0,
                new ProcessState.Entropy(9, 0), false, false, null, ProcessState.Release.HELD, "");
    }
    @Test void processPreflightIsPureAndConflictsDoNotPartiallyInstall() {
        var owner = new ProcessAuthority(); var first = process(10, Set.of("test:look"));
        owner.validateOpen(first); assertTrue(owner.snapshot().isEmpty());
        owner.open(first); var snapshot = owner.snapshot();
        assertThrows(IllegalStateException.class, () -> owner.validateOpen(process(11, Set.of("test:look"))));
        assertEquals(snapshot, owner.snapshot());
        owner.open(process(12, Set.of("test:attack")));
        assertEquals(2, owner.snapshot().size());
        assertEquals(1, snapshot.size());
    }
    @Test void failedReleaseRetainsOwnedControlAndSurvivesRestore() {
        var owner = new ProcessAuthority(); var first = owner.open(process(10, Set.of("test:look")));
        owner.revise(first.id(), "TERMINAL", Map.of(), 0, 0, first.entropy(), false, false,
                OperationRecord.Outcome.COMPLETED, ProcessState.Release.FAILED, "control fault");
        var restored = new ProcessAuthority(); restored.restore(owner.snapshot());
        assertThrows(IllegalStateException.class, () -> restored.open(process(11, Set.of("test:look"))));
        assertThrows(IllegalStateException.class, () -> restored.revise(first.id(), "TERMINAL", Map.of(), 0, 0,
                first.entropy(), false, false, OperationRecord.Outcome.COMPLETED, ProcessState.Release.RELEASED, "rewritten"));
    }
    @Test void processBoundsAndCancellationAreCheckedWithoutChangingPriorState() {
        var owner = new ProcessAuthority(); var first = owner.open(process(10, Set.of()));
        owner.revise(first.id(), "EXECUTE", Map.of("warmup", "1"), 1, 2, first.entropy(), true, false,
                null, ProcessState.Release.HELD, "cancel requested");
        var snapshot = owner.snapshot();
        assertThrows(IllegalStateException.class, () -> owner.revise(first.id(), "EXECUTE", Map.of(), 1, 1,
                first.entropy(), true, false, null, ProcessState.Release.HELD, "backward clock"));
        assertThrows(IllegalStateException.class, () -> owner.revise(first.id(), "EXECUTE", Map.of(), 1, 2,
                first.entropy(), false, false, null, ProcessState.Release.HELD, "lost cancel"));
        assertThrows(IllegalArgumentException.class, () -> owner.revise(first.id(), "EXECUTE", Map.of(), 3, 3,
                first.entropy(), true, false, null, ProcessState.Release.HELD, "overflow"));
        assertEquals(snapshot, owner.snapshot());
    }
    @Test void attemptCannotMasqueradeAsConfirmedDamageAndDecisionsStayScoped() {
        var event = new CausalEvent(id(1), id(2), id(3), id(4), 0, id(5), id(6),
                CausalEvent.Phase.ATTEMPT, new CausalEvent.Damage(4, true, id(7)));
        assertThrows(IllegalArgumentException.class, () -> new ActorActivations.Event(event.id(), event.operation(),
                event.subject(), event.source(), ActivationSpec.Event.DAMAGED, null, 0, null, event));
        var cancel = new Intervention(event.id(), Intervention.Disposition.CANCEL, "dodge");
        assertEquals(cancel, Intervention.pass(event).combine(cancel).combine(Intervention.pass(event)));
        assertThrows(IllegalArgumentException.class, () -> cancel.combine(new Intervention(id(99), Intervention.Disposition.PASS, "")));
    }
    @Test void bodyInstanceParticipatesInCanonicalTargetIdentity() {
        var first = new ActionIntent.BodyFacet("test:parts", 1, "head", id(1), id(2));
        var replaced = new ActionIntent.BodyFacet("test:parts", 1, "head", id(1), id(3));
        assertNotEquals(new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, "overworld", id(4), null, -1, 0, 0, 0, first),
                new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, "overworld", id(4), null, -1, 0, 0, 0, replaced));
    }

    private static AbilityDefinition ability(String suffix, ActionCost cost) {
        return new AbilityDefinition("test:" + suffix, 1, suffix, ActionIntent.Capability.USE_ITEM,
                Set.of(ActionIntent.TargetKind.SELF), AbilityDefinition.Activation.TRIGGERED, cost,
                RuleFacts.AVAILABILITY, "test:" + suffix, AbilityDefinition.TargetPolicy.NATIVE_INTERACTION);
    }
    private static final class Fixture {
        final AbilityRegistry definitions = new AbilityRegistry();
        final AbilityDefinition paid = ability("paid", ActionCost.REACTION);
        final AbilityDefinition deferred = ability("deferred", new ActionCost(ActionCost.Resource.REACTION, ActionCost.CommitPoint.ACCEPTED_EFFECT));
        final AbilityDefinition free = ability("free", ActionCost.FREE);
        final EncounterAuthority engine = new EncounterAuthority(new Random(3), 30, 30);
        final UUID encounter = id(20), actor, attacker;
        final OperationRecord.Snapshot parent;
        Fixture() {
            definitions.register(paid); definitions.register(deferred); definitions.register(free);
            engine.bindAbilityDefinitions(definitions);
            var region = EncounterRegion.generate("overworld", new EncounterRegion.Discovery(-10, -10, -10, 10, 10, 10),
                    List.of(new EncounterRegion.Anchor(id(1), new EncounterRegion.Point(0, 0, 0))), 2, 1);
            engine.beginCandidate(encounter, region, Set.of(id(1), id(2)));
            attacker = engine.view(encounter).current(); actor = attacker.equals(id(1)) ? id(2) : id(1);
            parent = new OperationRecord.Snapshot(id(30), null, encounter, attacker, attacker, actor, 1,
                    engine.view(encounter).version(), null, null, OperationRecord.Kind.ATTACK);
            assertTrue(engine.beginOperation(parent));
        }
        OperationRecord.Snapshot operation(long n, AbilityDefinition definition) {
            var intent = new ActionIntent(definition.id(), 1, ActionIntent.Capability.USE_ITEM,
                    new ActionIntent.Target(ActionIntent.TargetKind.SELF, "overworld", null, null, -1, 0, 0, 0),
                    GrantEvidence.intrinsic("test:source", 1, actor, id(9)));
            return new OperationRecord.Snapshot(id(n), parent.operationId(), encounter, actor, actor, null, 1,
                    engine.view(encounter).version(), null, null, OperationRecord.Kind.TRIGGERED, null, intent);
        }
        ExecutionRequest request(OperationRecord.Snapshot operation, AbilityDefinition definition) {
            return new ExecutionRequest(operation.operationId(), actor, id(9), encounter, operation.encounterVersion(),
                    definition.executor(), 1, operation.intent().ruleset(),
                    new ActorSnapshot.Revision(id(50), 0, operation.intent().ruleset()), operation.intent().source(), operation.intent(), definition.cost());
        }
        boolean admit(OperationRecord.Snapshot operation, AbilityDefinition definition) {
            return engine.beginTriggeredOperation(operation, request(operation, definition));
        }
    }
    @Test void freeAndPaidReactionsShareAdmissionWithoutSharingCost() {
        var f = new Fixture();
        assertTrue(f.admit(f.operation(40, f.free), f.free));
        assertTrue(f.engine.stateView(f.encounter).members().get(f.actor).reaction());
        var paid = f.operation(41, f.paid); var request = f.request(paid, f.paid);
        assertTrue(f.engine.beginTriggeredOperation(paid, request));
        assertFalse(f.engine.stateView(f.encounter).members().get(f.actor).reaction());
        var snapshot = f.engine.exportSnapshot();
        assertFalse(f.engine.beginTriggeredOperation(paid, request));
        assertFalse(f.admit(f.operation(42, f.paid), f.paid));
        assertEquals(snapshot, f.engine.exportSnapshot());
        var restored = EncounterAuthority.restoreSnapshot(snapshot, new Random(4));
        restored.bindAbilityDefinitions(f.definitions);
        assertFalse(restored.beginTriggeredOperation(paid, request));
        assertFalse(restored.stateView(f.encounter).members().get(f.actor).reaction());
    }
    @Test void pendingAcceptanceClaimsReactionUntilItSettles() {
        var f = new Fixture(); var deferred = f.operation(40, f.deferred);
        assertTrue(f.admit(deferred, f.deferred));
        assertTrue(f.engine.stateView(f.encounter).members().get(f.actor).reaction());
        assertFalse(f.admit(f.operation(41, f.paid), f.paid));
        assertFalse(f.admit(f.operation(42, f.deferred), f.deferred));
        assertTrue(f.admit(f.operation(43, f.free), f.free));
        f.engine.publish(f.encounter, deferred.operationId(), 0, OperationRecord.Outcome.REJECTED, "before effect", 0, 0, true);
        assertTrue(f.admit(f.operation(44, f.paid), f.paid));
    }
    @Test void acceptedEffectChargesExactlyOnce() {
        var f = new Fixture(); var deferred = f.operation(40, f.deferred);
        assertTrue(f.admit(deferred, f.deferred));
        var result = f.engine.publish(f.encounter, deferred.operationId(), 0, OperationRecord.Outcome.COMPLETED, "accepted", 0, 0, true);
        assertFalse(f.engine.stateView(f.encounter).members().get(f.actor).reaction());
        assertEquals(result, f.engine.publish(f.encounter, deferred.operationId(), 0, OperationRecord.Outcome.COMPLETED, "accepted", 0, 0, true));
        assertFalse(f.admit(f.operation(41, f.paid), f.paid));
    }
    @Test void rootCannotSettleWhileReactionIsPending() {
        var f = new Fixture(); var operation = f.operation(40, f.free);
        assertTrue(f.admit(operation, f.free));
        var before = f.engine.exportSnapshot();
        assertThrows(IllegalStateException.class, () -> f.engine.publish(f.encounter, f.parent.operationId(), 0,
                OperationRecord.Outcome.COMPLETED, "premature", 0, 0, true));
        assertEquals(before, f.engine.exportSnapshot());
        f.engine.publish(f.encounter, operation.operationId(), 0, OperationRecord.Outcome.COMPLETED, "child", 0, 0, true);
        f.engine.publish(f.encounter, f.parent.operationId(), 0, OperationRecord.Outcome.COMPLETED, "parent", 0, 0, true);
    }
    @Test void mismatchedExecutorCannotSpendReaction() {
        var f = new Fixture(); var op = f.operation(40, f.paid); var request = f.request(op, f.paid);
        var forged = new ExecutionRequest(request.operation(), request.actor(), request.instance(), request.encounter(),
                request.encounterVersion(), "test:wrong", request.definitionVersion(), request.ruleset(),
                request.actorRevision(), request.evidence(), request.intent(), request.cost());
        var before = f.engine.exportSnapshot();
        assertFalse(f.engine.beginTriggeredOperation(op, forged));
        assertEquals(before, f.engine.exportSnapshot());
    }
    @Test void activationReadsAreIndependentOfExecutionAvailability() {
        var f = new Fixture(); var op = f.operation(40, f.paid);
        var binding = new AbilityBinding(f.paid, AbilityGrant.nativeGrant(f.actor, op.intent().source()));
        var invocation = new TriggeredAbilityInvocation(f.paid.id(), 1, op.intent().target(), TriggeredAbilityInvocation.Timing.IMMEDIATE);
        var activation = new ActivationSpec(AbilityDefinition.Activation.TRIGGERED, ActivationSpec.Event.DAMAGED, null, 0);
        var registration = new ActorActivations.Registration(f.paid, activation,
                new ReadContract(Set.of(RuleFacts.SOURCE_VALID)), input -> List.of(invocation));
        var event = new ActorActivations.Event(id(60), f.parent.operationId(), f.actor, f.attacker, ActivationSpec.Event.DAMAGED, null, 0);
        var empty = new FactSlice(Map.of(), Map.of());
        var input = new ActorActivations.Input(event, binding, new FactSlice(Map.of(RuleFacts.SOURCE_VALID, true), Map.of()), empty, empty, 0);
        assertEquals(List.of(invocation), new ActorActivations().resolve(registration, input));
        var missing = new ActorActivations.Input(event, binding, empty, empty, empty, 0);
        assertThrows(FactSlice.MissingFact.class, () -> new ActorActivations().resolve(registration, missing));
        assertEquals(RuleFacts.AVAILABILITY, f.paid.reads());
    }
}
