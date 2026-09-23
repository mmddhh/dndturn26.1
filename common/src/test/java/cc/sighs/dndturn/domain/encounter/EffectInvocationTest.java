package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.application.actor.ActorCompilation;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.AbilityRegistry;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.actor.TriggeredAbilityInvocation;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EffectSnapshot;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EffectInvocationTest {
    private final UUID actor = UUID.randomUUID(), instance = UUID.randomUUID();
    private final AbilityRegistry registry = new AbilityRegistry();
    private final AbilityDefinition observer = new AbilityDefinition("test:observer", 1, "observer", ActionIntent.Capability.USE_ITEM,
            Set.of(ActionIntent.TargetKind.SELF), AbilityDefinition.Activation.TRIGGERED, ActionCost.FREE,
            new ReadContract(Set.of(RuleFacts.SOURCE_VALID)), "test:observer", AbilityDefinition.TargetPolicy.NATIVE_INTERACTION);
    EffectInvocationTest() { registry.register(observer); }
    private EffectInstance effect(EffectDefinition.Clock clock) {
        var definition = new EffectDefinition("test:effect", 1, clock, EffectDefinition.Stacking.STACK, 3, false, true,
                List.of(), List.of(new EffectDefinition.Grant(observer.id(), 1, 3, 1)));
        return new EffectInstance(UUID.randomUUID(), UUID.randomUUID(), definition, 3, 1, 1);
    }
    @Test void removedExpiredAndDownwardEventsRetainTheirOwnGrantWithoutPublishingIt() {
        for (int variant = 0; variant < 3; variant++) {
            var owner = new ActorStates(); var effect = effect(EffectDefinition.Clock.TURN_END);
            owner.apply(new ActorStates.Command(UUID.randomUUID(), actor, 0, List.of(new ActorStates.Apply(effect))));
            var command = new ActorStates.Command(UUID.randomUUID(), actor, 1, List.of(variant == 0 ? new ActorStates.Remove(effect.id())
                    : variant == 1 ? new ActorStates.Advance(EffectDefinition.Clock.TURN_END) : new ActorStates.ConsumeStacks(effect.id(), 1)));
            var after = owner.apply(command); var transition = owner.receipt(command.operation()).transitions().get(0);
            var kind = variant == 0 ? ActivationSpec.Event.REMOVED : variant == 1 ? ActivationSpec.Event.EXPIRED : ActivationSpec.Event.STACK_CHANGED;
            var event = new ActorActivations.Event(UUID.randomUUID(), command.operation(), actor, null, kind, null, 0, transition);
            var current = ActorCompilation.bindings(actor, instance, after, registry);
            assertTrue(current.isEmpty());
            var eligible = ActorCompilation.reactionBindings(actor, instance, current, event, registry);
            assertEquals(1, eligible.size()); assertEquals(effect.id(), eligible.get(0).source().grant());
            assertEquals(1, eligible.get(0).source().revision());
            assertTrue(ActorCompilation.bindings(actor, instance, after, registry).isEmpty());
        }
    }
    @Test void emissionAndStateCommitTogetherAndRecoverWithoutDuplicatingOutput() {
        var owner = new ActorStates(); var effect = effect(EffectDefinition.Clock.EXPLICIT);
        assertEquals(0, effect.remaining());
        var initial = owner.apply(new ActorStates.Command(UUID.randomUUID(), actor, 0, List.of(new ActorStates.Apply(effect))));
        var binding = ActorCompilation.bindings(actor, instance, initial, registry).get(0);
        var event = new ActorActivations.Event(UUID.randomUUID(), UUID.randomUUID(), actor, null, ActivationSpec.Event.TURN_END, null, 0);
        var root = ActorActivations.root(event);
        var output = new TriggeredExecutionRecord(UUID.randomUUID(), root, event, binding,
                new TriggeredAbilityInvocation("test:world", 1, new ActionIntent.Target(ActionIntent.TargetKind.SELF,
                        "minecraft:overworld", null, null, -1, 0, 0, 0), TriggeredAbilityInvocation.Timing.IMMEDIATE), TriggeredExecutionRecord.Status.PENDING, "");
        var bad = new ActorStates.Command(UUID.randomUUID(), actor, 1, List.of(new ActorStates.ConsumeStacks(effect.id(), 4)), root, 2, List.of(output));
        assertThrows(IllegalArgumentException.class, () -> owner.apply(bad));
        assertTrue(owner.invocations().isEmpty()); assertEquals(initial, owner.state(actor));
        var good = new ActorStates.Command(UUID.randomUUID(), actor, 1, List.of(new ActorStates.ConsumeStacks(effect.id(), 3)), root, 2, List.of(output));
        owner.apply(good); owner.apply(good); assertEquals(1, owner.invocations().size());
        var restored = new ActorStates(); restored.restore(owner.snapshot(), owner.receipts()); restored.restoreInvocations(owner.invocations());
        restored.apply(good); assertEquals(1, restored.invocations().size());
        restored.invocationStatus(output.operation(), TriggeredExecutionRecord.Status.STARTED, "started");
        restored.invocationStatus(output.operation(), TriggeredExecutionRecord.Status.UNKNOWN, "uncertain");
        assertThrows(IllegalStateException.class, () -> restored.invocationStatus(output.operation(), TriggeredExecutionRecord.Status.STARTED, "retry"));
        assertEquals(TriggeredExecutionRecord.Status.PENDING, output.status());
    }
    @Test void nativeRankIsOneBasedAndRejectsZero() {
        var effect = new EffectSnapshot.Vanilla("minecraft:strength", 1, -1, false, true);
        assertEquals(0, effect.amplifier()); assertEquals(1, effect.rank());
        assertThrows(IllegalArgumentException.class, () -> new EffectSnapshot.Vanilla("minecraft:strength", 0, 20, false, true));
    }
}
