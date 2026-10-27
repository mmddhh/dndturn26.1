package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.application.actor.ActorCompilation;
import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EffectSnapshot;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ResourceKey;
import cc.sighs.dndturn.domain.fact.StatKey;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TacticalEffectStateTest {
    private final UUID actor = UUID.randomUUID(), source = UUID.randomUUID();
    private EffectDefinition definition(EffectDefinition.InstancePolicy policy, EffectDefinition.Overflow overflow) {
        return new EffectDefinition("test:effect", 1, EffectDefinition.Clock.TURN_END,
                EffectDefinition.Stacking.STACK, 3, false, true,
                List.of(new EffectDefinition.Modifier(new StatKey("test:constant"), EffectDefinition.Operator.ADD, 2, EffectDefinition.Scaling.CONSTANT),
                        new EffectDefinition.Modifier("test:scaled", EffectDefinition.Operator.ADD, -1)), List.of(), Set.of("test:debuff"),
                policy, overflow, EffectDefinition.Refresh.MAXIMUM);
    }
    private EffectInstance effect(EffectDefinition d, UUID operation, UUID sourceActor, int stacks, int rank, long revision) {
        return new EffectInstance(UUID.randomUUID(), operation, d, stacks, 4, revision, rank, sourceActor, "test:ability");
    }
    private ActorStates.State apply(ActorStates owner, ActorStates.Change... changes) {
        return owner.apply(new ActorStates.Command(UUID.randomUUID(), actor, owner.state(actor).revision(), List.of(changes)));
    }
    @Test void learnedBindingsFollowPreparationRevocationAndInstanceWithoutMutatingSnapshots() {
        var registry = new cc.sighs.dndturn.domain.ability.AbilityRegistry();
        var ability = new cc.sighs.dndturn.domain.ability.AbilityDefinition("test:learned", 1, "Learned",
                cc.sighs.dndturn.domain.action.ActionIntent.Capability.USE_ITEM,
                Set.of(cc.sighs.dndturn.domain.action.ActionIntent.TargetKind.SELF),
                cc.sighs.dndturn.domain.ability.AbilityDefinition.Activation.CONTINUOUS,
                cc.sighs.dndturn.domain.ability.ActionCost.FREE,
                new cc.sighs.dndturn.domain.fact.ReadContract(Set.of(cc.sighs.dndturn.domain.fact.RuleFacts.SOURCE_VALID)), "test:learned",
                cc.sighs.dndturn.domain.ability.AbilityDefinition.TargetPolicy.NATIVE_INTERACTION,
                cc.sighs.dndturn.domain.ability.AbilityDefinition.Learning.PREPARED);
        registry.register(ability); registry.freeze();
        assertThrows(IllegalArgumentException.class, () -> ability.validateLearning(false));
        var owner = new ActorStates(); var grant = UUID.randomUUID(); var instance = UUID.randomUUID();
        apply(owner, new ActorStates.Learn(new cc.sighs.dndturn.domain.actor.ActorPersistentState.Learned(grant, ability.id(), 1, "test:admin", true, 1)));
        assertTrue(ActorCompilation.bindings(actor, instance, owner.state(actor), registry).isEmpty());
        apply(owner, new ActorStates.Prepare(Set.of(grant)));
        var bound = ActorCompilation.bindings(actor, instance, owner.state(actor), registry);
        apply(owner, new ActorStates.Prepare(Set.of()));
        assertTrue(ActorCompilation.bindings(actor, instance, owner.state(actor), registry).isEmpty());
        apply(owner, new ActorStates.Prepare(Set.of(grant)));
        assertNotEquals(bound, ActorCompilation.bindings(actor, instance, owner.state(actor), registry));
        assertNotEquals(bound, ActorCompilation.bindings(actor, UUID.randomUUID(), owner.state(actor), registry));
        apply(owner, new ActorStates.Forget(grant));
        assertTrue(ActorCompilation.bindings(actor, instance, owner.state(actor), registry).isEmpty());
        assertEquals(1, bound.size());
        var missing = new FactSlice(Map.of(), Map.of(new cc.sighs.dndturn.domain.fact.FactKey<>("test:missing", Double.class), FactSlice.Missing.UNSUPPORTED));
        assertEquals(missing, ActorCompilation.apply(missing, owner.state(actor),
                List.of(new EffectDefinition.Modifier("test:missing", EffectDefinition.Operator.ADD, 3))));
    }
    @Test void policyMergesDifferentCallerIdsAndPreservesRank() {
        var owner = new ActorStates(); var d = definition(EffectDefinition.InstancePolicy.SINGLE_PER_TARGET, EffectDefinition.Overflow.CLAMP);
        var first = effect(d, UUID.randomUUID(), source, 1, 2, 1);
        var before = apply(owner, new ActorStates.Apply(first));
        var after = apply(owner, new ActorStates.Apply(effect(d, UUID.randomUUID(), source, 3, 2, 2)));
        assertEquals(1, after.runtime().effects().size());
        var result = after.runtime().effects().get(first.id());
        assertEquals(3, result.stacks()); assertEquals(2, result.rank());
        assertEquals(1, before.runtime().effects().get(first.id()).stacks());
        assertThrows(UnsupportedOperationException.class, () -> before.runtime().effects().clear());
        assertEquals(12, ActorCompilation.stat(after, "test:constant", 10));
        assertEquals(7, ActorCompilation.stat(after, "test:scaled", 10));
        var explanation = ActorCompilation.explain(after, "test:scaled", 10,
                new cc.sighs.dndturn.domain.ability.ContinuousAbilities.Expanded(List.of(), List.of()));
        assertEquals(ActorCompilation.stat(after, "test:scaled", 10), explanation.value());
        assertEquals(first.id(), explanation.contributions().get(0).effect());
        assertEquals(3, explanation.contributions().get(0).scale());
        var ability = new cc.sighs.dndturn.domain.ability.AbilityDefinition("test:passive", 1, "Passive",
                cc.sighs.dndturn.domain.action.ActionIntent.Capability.USE_ITEM, Set.of(cc.sighs.dndturn.domain.action.ActionIntent.TargetKind.SELF),
                cc.sighs.dndturn.domain.ability.AbilityDefinition.Activation.CONTINUOUS, cc.sighs.dndturn.domain.ability.ActionCost.FREE,
                cc.sighs.dndturn.domain.fact.RuleFacts.AVAILABILITY, "test:passive", cc.sighs.dndturn.domain.ability.AbilityDefinition.TargetPolicy.NATIVE_INTERACTION, cc.sighs.dndturn.domain.ability.AbilityDefinition.Learning.UNAVAILABLE);
        var registry = new cc.sighs.dndturn.domain.ability.AbilityRegistry(); registry.register(ability);
        var binding = new cc.sighs.dndturn.domain.ability.AbilityBinding(ability, cc.sighs.dndturn.domain.ability.AbilityGrant.nativeGrant(actor,
                cc.sighs.dndturn.domain.ability.GrantEvidence.intrinsic("test:source", 1, actor, UUID.randomUUID())));
        var continuous = new cc.sighs.dndturn.domain.ability.ContinuousAbilities();
        continuous.register(ability, new cc.sighs.dndturn.domain.ability.ContinuousAbilities.Contribution(
                List.of(new EffectDefinition.Modifier("test:scaled", EffectDefinition.Operator.MULTIPLY, 1.5)), List.of()));
        var expanded = continuous.expand(List.of(binding), registry);
        var sourced = ActorCompilation.explain(after, "test:scaled", 10, expanded);
        assertEquals(10.5, sourced.value());
        assertEquals(ActorCompilation.stat(after, "test:scaled", 10, expanded.modifiers()), sourced.value());
        assertEquals(binding.source(), sourced.contributions().get(1).grant());
        assertTrue(continuous.expand(List.of(), registry).sources().isEmpty());
        assertEquals(expanded, continuous.expand(List.of(binding, binding), registry));
    }
    @Test void sourcePoliciesAndUniqueApplicationsDoNotMergeUnrelatedSources() {
        for (var policy : EffectDefinition.InstancePolicy.values()) {
            var owner = new ActorStates(); var d = definition(policy, EffectDefinition.Overflow.REJECT);
            apply(owner, new ActorStates.Apply(effect(d, UUID.randomUUID(), source, 1, 1, 1)));
            var after = apply(owner, new ActorStates.Apply(effect(d, UUID.randomUUID(), UUID.randomUUID(), 1, 1, 2)));
            assertEquals(policy == EffectDefinition.InstancePolicy.SINGLE_PER_TARGET ? 1 : 2, after.runtime().effects().size());
        }
    }
    @Test void overflowAndOverConsumptionHaveNoPartialCommitOrReceipt() {
        var owner = new ActorStates(); var d = definition(EffectDefinition.InstancePolicy.SINGLE_PER_TARGET, EffectDefinition.Overflow.REJECT);
        var effect = effect(d, UUID.randomUUID(), source, 2, 1, 1);
        var before = apply(owner, new ActorStates.Apply(effect));
        for (ActorStates.Change invalid : List.of(new ActorStates.AddStacks(effect.id(), 2), new ActorStates.ConsumeStacks(effect.id(), 3))) {
            var command = new ActorStates.Command(UUID.randomUUID(), actor, 1,
                    List.of(new ActorStates.Resource(new ResourceKey("test:mana"), 1, true), invalid));
            assertThrows(IllegalArgumentException.class, () -> owner.apply(command));
            assertSame(before, owner.state(actor)); assertNull(owner.receipt(command.operation()));
        }
        assertThrows(IllegalArgumentException.class, () -> new ActorStates.AddStacks(effect.id(), -1));
    }
    @Test void consumeRefreshClockDeathAndRetryUseSingleOwner() {
        var owner = new ActorStates(); var d = definition(EffectDefinition.InstancePolicy.SINGLE_PER_TARGET, EffectDefinition.Overflow.REJECT);
        var effect = effect(d, UUID.randomUUID(), source, 2, 3, 1);
        apply(owner, new ActorStates.Apply(effect));
        var consumed = apply(owner, new ActorStates.ConsumeStacks(effect.id(), 1));
        var refreshed = apply(owner, new ActorStates.RefreshDuration(effect.id(), 6));
        assertEquals(consumed.runtime().effects().get(effect.id()).grantRevision(), refreshed.runtime().effects().get(effect.id()).grantRevision());
        assertTrue(consumed.runtime().effects().get(effect.id()).revision() < refreshed.runtime().effects().get(effect.id()).revision());
        owner.lifecycle(actor, new ActorStates.Advance(EffectDefinition.Clock.TURN_END));
        assertEquals(5, owner.state(actor).runtime().effects().get(effect.id()).remaining());
        assertEquals(3, owner.state(actor).runtime().effects().get(effect.id()).rank());
        assertEquals(refreshed.runtime().effects().get(effect.id()).grantRevision(), owner.state(actor).runtime().effects().get(effect.id()).grantRevision());
        assertTrue(refreshed.runtime().effects().get(effect.id()).revision() < owner.state(actor).runtime().effects().get(effect.id()).revision());
        var command = new ActorStates.Command(UUID.randomUUID(), actor, owner.state(actor).revision(), List.of(new ActorStates.ConsumeStacks(effect.id(), 1)));
        var result = owner.apply(command);
        assertTrue(result.runtime().effects().isEmpty()); assertSame(result, owner.apply(command));
        var restored = new ActorStates(); restored.restore(owner.snapshot(), owner.receipts());
        assertEquals(result, restored.apply(command));
        assertThrows(IllegalArgumentException.class, () -> restored.apply(new ActorStates.Command(command.operation(), actor, command.expectedRevision(), List.of())));
        apply(owner, new ActorStates.Apply(effect(d, UUID.randomUUID(), source, 1, 1, owner.state(actor).revision() + 1)));
        owner.lifecycle(actor, new ActorStates.Death()); assertTrue(owner.state(actor).runtime().effects().isEmpty());
    }
    @Test void expiryAndRankConflict() {
        var owner = new ActorStates(); var d = definition(EffectDefinition.InstancePolicy.SINGLE_PER_TARGET, EffectDefinition.Overflow.REJECT);
        apply(owner, new ActorStates.Apply(effect(d, UUID.randomUUID(), source, 1, 2, 1)));
        assertThrows(IllegalArgumentException.class, () -> apply(owner, new ActorStates.Apply(effect(d, UUID.randomUUID(), source, 1, 1, 2))));
        for (int i = 0; i < 4; i++) owner.lifecycle(actor, new ActorStates.Advance(EffectDefinition.Clock.TURN_END));
        assertTrue(owner.state(actor).runtime().effects().isEmpty());
    }
    @Test void differentRequirementsCannotDuplicateTheSameGrant() {
        assertThrows(IllegalArgumentException.class, () -> new EffectDefinition("test:effect", 1,
                EffectDefinition.Clock.EXPLICIT, EffectDefinition.Stacking.STACK, 3, false, true, List.of(),
                List.of(new EffectDefinition.Grant("dndturn:move", 1, 1, 1),
                        new EffectDefinition.Grant("dndturn:move", 1, 2, 1))));
    }
    @Test void effectProjectionSeparatesNativeRankFromStacksAndKeepsOldValues() {
        var owner = new ActorStates();
        var d = definition(EffectDefinition.InstancePolicy.SINGLE_PER_TARGET, EffectDefinition.Overflow.CLAMP);
        var value = effect(d, UUID.randomUUID(), source, 2, 3, 1);
        apply(owner, new ActorStates.Apply(value));
        var projected = EffectSnapshot.Tactical.capture(value);
        var nativeEffect = new EffectSnapshot.Vanilla("minecraft:strength", 4, -1, false, true, -2, EffectSnapshot.NativeSupport.NATIVE);
        var effects = new ArrayList<EffectSnapshot>(List.of(projected, nativeEffect));
        var facts = new FactSlice(Map.of(), Map.of());
        var actorDefinition = new ActorDefinition("test:actor", 1, Set.of(), facts, List.of(), ActorDefinition.Classification.unspecified(), Map.of());
        var snapshot = new ActorSnapshot(actor, UUID.randomUUID(), actorDefinition,
                new ActorSnapshot.Revision(UUID.randomUUID(), 1, "test:rules"), facts, List.of(), effects);
        effects.clear();
        owner.lifecycle(actor, new ActorStates.Advance(EffectDefinition.Clock.TURN_END));
        assertEquals(2, snapshot.effects().size());
        assertEquals(4, projected.remaining());
        assertEquals(3, owner.state(actor).runtime().effects().get(value.id()).remaining());
        assertEquals(4, nativeEffect.rank());
        assertEquals(1, nativeEffect.stacks());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.effects().clear());
        assertThrows(IllegalArgumentException.class, () -> new ActorSnapshot(actor, UUID.randomUUID(), actorDefinition,
                snapshot.revision(), facts, List.of(), List.of(nativeEffect, nativeEffect)));
        assertThrows(IllegalArgumentException.class, () -> new EffectInstance(value.id(), value.sourceOperation(),
                d, 2, 4, 1, 3, source, "test:ability", 2));
    }
}
