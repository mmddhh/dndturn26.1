package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.ActorPersistentState;
import cc.sighs.dndturn.domain.actor.ActorRuntimeState;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.fact.ResourceKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorHistoryTest {
    private final UUID actor = UUID.randomUUID();
    private ActorStates owner(long clock) {
        var owner = new ActorStates();
        owner.restore(Map.of(actor, new ActorStates.State(1, ActorPersistentState.empty(), ActorRuntimeState.empty(),
                Map.of(EffectDefinition.Clock.SIMULATION_STEP, clock))), List.of());
        return owner;
    }
    private ActorStates.ReactionDelivery delivery(long sequence, ActorStates.DeliveryStatus status) {
        UUID id = UUID.nameUUIDFromBytes((actor + ":SIMULATION_STEP:" + sequence).getBytes(StandardCharsets.UTF_8));
        return new ActorStates.ReactionDelivery(id, actor, List.of(new ActorActivations.Event(id, null, actor, null,
                null, EffectDefinition.Clock.SIMULATION_STEP, sequence)), status);
    }
    private ActorStates restore(ActorStates source) {
        var restored = new ActorStates(); restored.restore(source.snapshot(), source.receipts());
        restored.restoreFaults(source.faults()); restored.restoreDeliveries(source.deliveries());
        restored.restoreInvocations(source.invocations()); restored.restoreSimulationArchive(source.simulationArchive());
        restored.restoreCapacityRecoveries(source.capacityCandidates(), source.capacityRecoveries());
        return restored;
    }
    @Test void twentyThousandCompletedStepsStayBoundedAndReplayAfterRestore() {
        var owner = owner(20000);
        for (int i = 1; i <= 20000; i++) {
            var event = delivery(i, ActorStates.DeliveryStatus.PENDING);
            owner.registerReactions(event.root(), actor, event.events());
            owner.finishReactions(event.root(), actor, ActorStates.DeliveryStatus.COMPLETED);
        }
        assertTrue(owner.deliveries().isEmpty());
        assertEquals(List.of(new ActorStates.SimulationRange(1, 20000)), owner.simulationArchive().get(actor));
        var restored = restore(owner);
        var old = delivery(1, ActorStates.DeliveryStatus.COMPLETED);
        assertEquals(old, restored.registerReactions(old.root(), actor, old.events()));
        var original = old.events().get(0);
        var changed = new ActorActivations.Event(original.id(), UUID.randomUUID(), actor, null, null,
                original.clock(), original.sequence());
        assertThrows(IllegalArgumentException.class, () -> restored.registerReactions(old.root(), actor, List.of(changed)));
        assertTrue(restored.deliveries().isEmpty());
    }
    @Test void gapsPendingFailuresAndRuleReceiptsAreNeverDiscarded() {
        var owner = owner(5);
        owner.restoreDeliveries(List.of(delivery(1, ActorStates.DeliveryStatus.COMPLETED),
                delivery(2, ActorStates.DeliveryStatus.PENDING), delivery(3, ActorStates.DeliveryStatus.FAULTED),
                delivery(4, ActorStates.DeliveryStatus.COMPLETED), delivery(5, ActorStates.DeliveryStatus.COMPLETED)));
        var referenced = delivery(4, ActorStates.DeliveryStatus.COMPLETED);
        owner.apply(new ActorStates.Command(UUID.randomUUID(), actor, 1, List.of(), referenced.root()));
        assertEquals(2, owner.compactSimulationHistory());
        assertEquals(3, owner.deliveries().size());
        assertEquals(List.of(new ActorStates.SimulationRange(1, 1), new ActorStates.SimulationRange(5, 5)), owner.simulationArchive().get(actor));
        var restored = restore(owner);
        assertEquals(owner.pendingReactions(actor), restored.pendingReactions(actor));
        assertEquals(owner.receipts(), restored.receipts());
    }
    @Test void saturatedLegacyFaultRequiresInertStateAndRetainsReconciliationEvidence() {
        var owner = owner(16384);
        owner.restoreDeliveries(java.util.stream.LongStream.rangeClosed(1, 16384)
                .mapToObj(n -> delivery(n, ActorStates.DeliveryStatus.COMPLETED)).toList());
        owner.fault(actor, "ACTIVATION_FAILED:IllegalStateException");
        assertEquals(16384, owner.prepareCapacityRecovery());
        assertNotNull(owner.faultReason(actor)); // Compaction alone cannot release control.
        assertEquals(1, owner.capacityCandidates().size());
        var restored = restore(owner);
        var state = restored.state(actor);
        assertTrue(restored.reconcileCapacityFault(actor));
        assertSame(state, restored.state(actor));
        assertNull(restored.faultReason(actor));
        assertFalse(restored.reconcileCapacityFault(actor));
        var finalOwner = restore(restored);
        assertEquals("ACTIVATION_FAILED:IllegalStateException", finalOwner.capacityRecoveries().get(actor).originalFault());
        assertTrue(finalOwner.capacityCandidates().isEmpty());
    }
    @Test void unrelatedOrUncertainFaultIsNotRecovered() {
        for (boolean pending : List.of(false, true)) {
            var owner = owner(16384);
            owner.restoreDeliveries(java.util.stream.LongStream.rangeClosed(1, 16384)
                    .mapToObj(n -> delivery(n, pending && n == 16384 ? ActorStates.DeliveryStatus.PENDING : ActorStates.DeliveryStatus.COMPLETED)).toList());
            owner.fault(actor, pending ? "ACTIVATION_FAILED:IllegalStateException" : "EFFECT_DEPTH_LIMIT");
            owner.prepareCapacityRecovery();
            assertFalse(owner.reconcileCapacityFault(actor));
            assertNotNull(owner.faultReason(actor));
        }
        var small = owner(1);
        small.restoreDeliveries(List.of(delivery(1, ActorStates.DeliveryStatus.COMPLETED)));
        small.fault(actor, "ACTIVATION_FAILED:IllegalStateException");
        small.prepareCapacityRecovery();
        assertFalse(small.reconcileCapacityFault(actor));
    }
    @Test void malformedArchiveCannotPartiallyInstall() {
        var owner = owner(2);
        assertThrows(IllegalArgumentException.class, () -> owner.restoreSimulationArchive(Map.of(actor,
                List.of(new ActorStates.SimulationRange(1, 3)))));
        assertTrue(owner.simulationArchive().isEmpty());
    }
    @Test void legacyActorWithRuleMutationsKeepsItsFault() {
        var owner = owner(16384);
        owner.apply(new ActorStates.Command(UUID.randomUUID(), actor, 1,
                List.of(new ActorStates.Resource(new ResourceKey("test:resource"), 1, true))));
        owner.restoreDeliveries(java.util.stream.LongStream.rangeClosed(1, 16384)
                .mapToObj(n -> delivery(n, ActorStates.DeliveryStatus.COMPLETED)).toList());
        owner.fault(actor, "ACTIVATION_FAILED:IllegalStateException");
        owner.prepareCapacityRecovery();
        assertFalse(owner.reconcileCapacityFault(actor));
        assertEquals(1.0, owner.state(actor).persistent().resources().get("test:resource"));
        assertNotNull(owner.faultReason(actor));
    }
}
