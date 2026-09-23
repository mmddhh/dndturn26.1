package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EffectTransition;
import cc.sighs.dndturn.domain.effect.EffectWave;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectWaveTest {
    private final UUID actor = UUID.randomUUID(), instance = UUID.randomUUID();
    private ActorStates.State state(int stacks, long duration) {
        var definition = new EffectDefinition("test:fuse", 1, EffectDefinition.Clock.TURN_END,
                EffectDefinition.Stacking.STACK, 3, false, true, List.of(), List.of());
        return ActorStates.reduce(ActorStates.State.empty(), List.of(new ActorStates.Apply(
                new EffectInstance(instance, UUID.randomUUID(), definition, stacks, duration, 1))));
    }
    private EffectWave.Proposal proposal(String id, ActorStates.Change... changes) {
        return new EffectWave.Proposal(0, instance, id, List.of(changes));
    }
    @Test void commutingAddsAreMergedAndOrderedIndependentlyOfRegistration() {
        var before = state(1, 3);
        var a = proposal("test:a", new ActorStates.AddStacks(instance, 1));
        var b = proposal("test:b", new ActorStates.AddStacks(instance, 1));
        var forward = EffectWave.prepare(before, List.of(a, b));
        var reverse = EffectWave.prepare(before, List.of(b, a));
        assertEquals(forward, reverse);
        assertEquals(3, forward.reduction().state().runtime().effects().get(instance).stacks());
        assertEquals(1, before.runtime().effects().get(instance).stacks());
        var edge = forward.reduction().transitions().get(0);
        assertTrue(edge.crosses(EffectTransition.Edge.CROSS_UP, 3));
        assertFalse(edge.crosses(EffectTransition.Edge.CROSS_DOWN, 3));
        assertEquals(1, edge.beforeRevision()); assertEquals(2, edge.afterRevision());
        var refresh = ActorStates.preview(forward.reduction().state(), List.of(new ActorStates.RefreshDuration(instance, 4)));
        assertFalse(refresh.transitions().get(0).crosses(EffectTransition.Edge.CROSS_UP, 3));
    }
    @Test void allEdgesAndExpiryAreDistinct() {
        var before = state(3, 1);
        var reduced = ActorStates.preview(before, List.of(new ActorStates.ConsumeStacks(instance, 2)));
        assertTrue(reduced.transitions().get(0).crosses(EffectTransition.Edge.CROSS_DOWN, 3));
        var expired = ActorStates.preview(before, List.of(new ActorStates.Advance(EffectDefinition.Clock.TURN_END))).transitions().get(0);
        assertTrue(expired.events().contains(ActivationSpec.Event.EXPIRED));
        assertTrue(expired.events().contains(ActivationSpec.Event.REMOVED));
        assertTrue(expired.crosses(EffectTransition.Edge.BECAME_ZERO, 1));
        var removed = ActorStates.preview(before, List.of(new ActorStates.Remove(instance))).transitions().get(0);
        assertFalse(removed.events().contains(ActivationSpec.Event.EXPIRED));
        var applied = ActorStates.preview(ActorStates.State.empty(), List.of(new ActorStates.Apply(before.runtime().effects().get(instance))));
        assertTrue(applied.transitions().get(0).crosses(EffectTransition.Edge.BECAME_NONZERO, 1));
        assertTrue(applied.transitions().get(0).events().contains(ActivationSpec.Event.APPLIED));
    }
    @Test void conflictOrOverConsumptionRejectsWholeWaveWithoutOwnerMutation() {
        var before = state(2, 3);
        var owner = new ActorStates(); owner.restore(Map.of(actor, before), List.of());
        for (var proposals : List.of(
                List.of(proposal("test:a", new ActorStates.Remove(instance)), proposal("test:b", new ActorStates.AddStacks(instance, 1))),
                List.of(proposal("test:a", new ActorStates.ConsumeStacks(instance, 2)), proposal("test:b", new ActorStates.ConsumeStacks(instance, 1))),
                List.of(proposal("test:a", new ActorStates.AddStacks(instance, 1)), proposal("test:b", new ActorStates.AddStacks(instance, 1))))) {
            assertThrows(EffectWave.Fault.class, () -> EffectWave.prepare(owner.state(actor), proposals));
            assertSame(before, owner.state(actor)); assertTrue(owner.receipts().isEmpty());
        }
    }
    @Test void mutationBudgetAndLifecycleOwnershipAreEnforced() {
        var before = state(1, 3);
        assertEquals("EFFECT_LIFECYCLE_WRITE_DENIED", assertThrows(EffectWave.Fault.class,
                () -> EffectWave.prepare(before, List.of(proposal("test:a", new ActorStates.Death())))).code());
        var many = new ArrayList<EffectWave.Proposal>();
        for (int i = 0; i <= EffectWave.MAX_MUTATIONS; i++) many.add(proposal("test:r" + i, new ActorStates.AddStacks(instance, 1)));
        assertEquals("EFFECT_MUTATION_LIMIT", assertThrows(EffectWave.Fault.class, () -> EffectWave.prepare(before, many)).code());
    }
    @Test void deliveryRecoveryRetainsPayloadAndTerminalDeduplication() {
        var owner = new ActorStates();
        var event = new ActorActivations.Event(UUID.randomUUID(), null, actor, null, ActivationSpec.Event.TURN_END, null, 0);
        var root = ActorActivations.root(event);
        owner.registerReactions(root, actor, List.of(event));
        var restored = new ActorStates(); restored.restore(owner.snapshot(), owner.receipts());
        restored.restoreDeliveries(owner.deliveries());
        assertEquals(1, restored.pendingReactions(actor).size());
        restored.finishReactions(root, actor, ActorStates.DeliveryStatus.COMPLETED);
        assertTrue(restored.pendingReactions(actor).isEmpty());
        assertEquals(ActorStates.DeliveryStatus.COMPLETED, restored.registerReactions(root, actor, List.of(event)).status());
        assertThrows(IllegalArgumentException.class, () -> restored.registerReactions(root, actor, List.of(
                new ActorActivations.Event(event.id(), null, actor, UUID.randomUUID(), ActivationSpec.Event.TURN_END, null, 0))));
        assertThrows(IllegalStateException.class, () -> restored.finishReactions(root, actor, ActorStates.DeliveryStatus.FAULTED));
    }
    @Test void mergedWaveRetainsOriginalMutationBudgetAcrossRestore() {
        var before = state(1, 3);
        var wave = EffectWave.prepare(before, List.of(
                proposal("test:a", new ActorStates.AddStacks(instance, 1)),
                proposal("test:b", new ActorStates.AddStacks(instance, 1))));
        assertEquals(1, wave.changes().size());
        assertEquals(2, wave.proposedMutations());
        var owner = new ActorStates(); owner.restore(Map.of(actor, before), List.of());
        var command = new ActorStates.Command(UUID.randomUUID(), actor, before.revision(), wave.changes(),
                UUID.randomUUID(), wave.proposedMutations());
        owner.apply(command);
        var restored = new ActorStates(); restored.restore(owner.snapshot(), owner.receipts());
        assertEquals(2, restored.receipt(command.operation()).command().proposedMutations());
        assertEquals(owner.state(actor), restored.apply(command));
        assertThrows(IllegalArgumentException.class, () -> restored.apply(new ActorStates.Command(command.operation(),
                actor, before.revision(), wave.changes(), command.cause(), 1)));
        assertThrows(IllegalArgumentException.class, () -> new ActorStates.Command(UUID.randomUUID(), actor,
                before.revision(), wave.changes(), command.cause(), 0));
        assertThrows(IllegalArgumentException.class, () -> new ActorStates.Command(UUID.randomUUID(), actor,
                before.revision(), wave.changes(), command.cause(), EffectWave.MAX_MUTATIONS + 1));
    }
}
