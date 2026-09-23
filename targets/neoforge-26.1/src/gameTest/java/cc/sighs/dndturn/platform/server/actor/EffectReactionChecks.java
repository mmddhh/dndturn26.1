package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EffectTransition;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.ResourceKey;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;

/** Test registrations exercise the same state owner and iterative dispatcher as production. */
public final class EffectReactionChecks {
    private static final String PREFIX = "dndturn:test_reaction_";
    private static final EffectDefinition COUNTER = new EffectDefinition(PREFIX + "counter", 1,
            EffectDefinition.Clock.EXPLICIT, EffectDefinition.Stacking.STACK, 3, false, true, List.of(), List.of());
    private static final EffectDefinition OBSERVER = new EffectDefinition(PREFIX + "observer", 1,
            EffectDefinition.Clock.EXPLICIT, EffectDefinition.Stacking.REJECT, 1, false, true, List.of(),
            List.of(new EffectDefinition.Grant(PREFIX + "a", 1), new EffectDefinition.Grant(PREFIX + "b", 1),
                    new EffectDefinition.Grant(PREFIX + "edge", 1)));
    private static final EffectDefinition CYCLE = observer("cycle_observer", "cycle");
    private static final EffectDefinition CONFLICT = observer("conflict_observer", "a", "remove");
    private static EffectDefinition observer(String id, String... abilities) {
        return new EffectDefinition(PREFIX + id, 1, EffectDefinition.Clock.EXPLICIT,
                EffectDefinition.Stacking.REJECT, 1, false, true, List.of(),
                Arrays.stream(abilities).map(value -> new EffectDefinition.Grant(PREFIX + value, 1)).toList());
    }
    private static UUID counter(UUID actor) { return UUID.nameUUIDFromBytes((actor + ":reaction-counter").getBytes(StandardCharsets.UTF_8)); }
    public static void register() {
        for (var kind : List.of(ActivationSpec.Event.REMOVED, ActivationSpec.Event.EXPIRED, ActivationSpec.Event.STACK_CHANGED)) {
            String suffix = "self_" + kind.name().toLowerCase(java.util.Locale.ROOT);
            AbilityAdapterRegistry.registerActorEffect(ability(suffix), new ActivationSpec(AbilityDefinition.Activation.TRIGGERED, kind, null, 0), input -> {
                var transition = input.event().transition();
                return transition != null && transition.crosses(EffectTransition.Edge.CROSS_DOWN, 3)
                        ? List.of(new ActorStates.Resource(new ResourceKey(PREFIX + suffix), 1, true)) : List.of();
            });
            AbilityAdapterRegistry.effects().register(self(kind));
        }
        for (String suffix : List.of("a", "b")) {
            var definition = ability(suffix);
            AbilityAdapterRegistry.registerActorEffect(definition, new ActivationSpec(AbilityDefinition.Activation.TRIGGERED,
                    ActivationSpec.Event.ABILITY_USED, null, 0), input -> List.of(
                    new ActorStates.AddStacks(counter(input.event().actor()), 1),
                    new ActorStates.Resource(new ResourceKey(PREFIX + suffix), input.actorRevision(), true)));
        }
        AbilityAdapterRegistry.registerActorEffect(ability("edge"), new ActivationSpec(AbilityDefinition.Activation.TRIGGERED,
                ActivationSpec.Event.STACK_CHANGED, null, 0), input -> {
            var transition = input.event().transition();
            return transition != null && transition.instance().equals(counter(input.event().actor()))
                    && transition.crosses(EffectTransition.Edge.CROSS_UP, 3)
                    ? List.of(new ActorStates.Resource(new ResourceKey(PREFIX + "edges"), 1, false)) : List.of();
        });
        AbilityAdapterRegistry.effects().register(COUNTER);
        AbilityAdapterRegistry.effects().register(OBSERVER);
        AbilityAdapterRegistry.registerActorEffect(ability("remove"), new ActivationSpec(AbilityDefinition.Activation.TRIGGERED,
                ActivationSpec.Event.ABILITY_USED, null, 0), input -> List.of(new ActorStates.Remove(counter(input.event().actor()))));
        AbilityAdapterRegistry.registerActorEffect(ability("cycle"), new ActivationSpec(AbilityDefinition.Activation.TRIGGERED,
                ActivationSpec.Event.STACK_CHANGED, null, 0), input -> {
            var transition = input.event().transition();
            if (transition == null || !transition.instance().equals(counter(input.event().actor())) || transition.after() == null) return List.of();
            return transition.after().stacks() == 1 ? List.of(new ActorStates.AddStacks(transition.instance(), 1))
                    : List.of(new ActorStates.ConsumeStacks(transition.instance(), 1));
        });
        AbilityAdapterRegistry.effects().register(CYCLE);
        AbilityAdapterRegistry.effects().register(CONFLICT);
    }
    private static EffectDefinition self(ActivationSpec.Event kind) {
        String suffix = "self_" + kind.name().toLowerCase(java.util.Locale.ROOT);
        return new EffectDefinition(PREFIX + suffix, 1, EffectDefinition.Clock.TURN_END,
                EffectDefinition.Stacking.STACK, 3, false, true, List.of(),
                List.of(new EffectDefinition.Grant(PREFIX + suffix, 1, 3, 1)));
    }
    private static void selfLifecycle(GameTestHelper helper) {
        for (var kind : List.of(ActivationSpec.Event.REMOVED, ActivationSpec.Event.EXPIRED, ActivationSpec.Event.STACK_CHANGED)) {
            var body = helper.spawn(EntityType.COW, 2, 2, 1); var actor = new LiveActorContext(body);
            var service = ServerRuntime.encounters(helper.getLevel().getServer()).actorStates();
            long revision = service.state(actor.id()).revision(); UUID effect = UUID.randomUUID();
            service.command(actor, new ActorStates.Command(UUID.randomUUID(), actor.id(), revision, List.of(new ActorStates.Apply(
                    new EffectInstance(effect, UUID.randomUUID(), self(kind), 3, 1, revision + 1)))));
            service.command(actor, new ActorStates.Command(UUID.randomUUID(), actor.id(), service.state(actor.id()).revision(),
                    List.of(kind == ActivationSpec.Event.REMOVED ? new ActorStates.Remove(effect)
                            : kind == ActivationSpec.Event.EXPIRED ? new ActorStates.Advance(EffectDefinition.Clock.TURN_END)
                            : new ActorStates.ConsumeStacks(effect, 1))));
            helper.assertTrue(service.fault(actor.id()) == null && Double.valueOf(1).equals(service.state(actor.id()).persistent().resources()
                    .get(PREFIX + "self_" + kind.name().toLowerCase(java.util.Locale.ROOT))), "self lifecycle reaction missing: " + kind);
            body.discard();
        }
    }
    private static AbilityDefinition ability(String suffix) {
        return new AbilityDefinition(PREFIX + suffix, 1, "test reaction " + suffix, ActionIntent.Capability.USE_ITEM,
                Set.of(ActionIntent.TargetKind.SELF), AbilityDefinition.Activation.TRIGGERED, ActionCost.FREE,
                new ReadContract(Set.of(RuleFacts.SOURCE_VALID)), PREFIX + suffix, AbilityDefinition.TargetPolicy.NATIVE_INTERACTION);
    }
    public static void run(GameTestHelper helper) {
        selfLifecycle(helper);
        var body = helper.spawn(EntityType.COW, 1, 2, 1);
        var actor = new LiveActorContext(body);
        var service = ServerRuntime.encounters(helper.getLevel().getServer()).actorStates();
        var event = prepare(service, actor);
        long frozenRevision = service.state(actor.id()).revision();
        service.dispatch(actor, event);
        var after = service.state(actor.id());
        verify(helper, service, actor);
        helper.assertTrue(after.persistent().resources().get(PREFIX + "a") == frozenRevision
                && after.persistent().resources().get(PREFIX + "b") == frozenRevision, "callbacks observed different wave revisions");
        service.dispatch(actor, event);
        helper.assertTrue(service.state(actor.id()).equals(after), "duplicate event re-executed callbacks");
        service.command(actor, new ActorStates.Command(UUID.randomUUID(), actor.id(), after.revision(),
                List.of(new ActorStates.RefreshDuration(counter(actor.id()), 2))));
        helper.assertTrue(service.state(actor.id()).persistent().resources().get(PREFIX + "edges") == 1,
                "remaining above threshold retriggered edge");
        body.discard();
        fault(helper, CONFLICT, "EFFECT_WRITE_CONFLICT");
        fault(helper, CYCLE, "EFFECT_DEPTH_LIMIT");
        helper.succeed();
    }
    static ActorActivations.Event prepare(ActorStateAuthority service, LiveActorContext actor) {
        long revision = service.state(actor.id()).revision();
        service.command(actor, new ActorStates.Command(UUID.randomUUID(), actor.id(), revision, List.of(
                new ActorStates.Apply(new EffectInstance(counter(actor.id()), UUID.randomUUID(), COUNTER, 1, 1, revision + 1)),
                new ActorStates.Apply(new EffectInstance(UUID.randomUUID(), UUID.randomUUID(), OBSERVER, 1, 1, revision + 1)),
                new ActorStates.Resource(new ResourceKey(PREFIX + "edges"), 0, true))));
        return new ActorActivations.Event(UUID.randomUUID(), null, actor.id(), null, ActivationSpec.Event.ABILITY_USED, null, 0);
    }
    static void verify(GameTestHelper helper, ActorStateAuthority service, LiveActorContext actor) {
        var after = service.state(actor.id());
        helper.assertTrue(service.fault(actor.id()) == null, "reaction fault: " + service.fault(actor.id()));
        helper.assertTrue(after.runtime().effects().get(counter(actor.id())).stacks() == 3, "commuting adds did not merge");
        helper.assertTrue(after.persistent().resources().get(PREFIX + "a").equals(after.persistent().resources().get(PREFIX + "b")),
                "recovered callbacks observed different wave revisions");
        helper.assertTrue(after.persistent().resources().get(PREFIX + "edges") == 1, "threshold did not fire exactly once");
    }
    private static void fault(GameTestHelper helper, EffectDefinition observer, String expected) {
        var body = helper.spawn(EntityType.COW, 2, 2, 1);
        var actor = new LiveActorContext(body);
        var service = ServerRuntime.encounters(helper.getLevel().getServer()).actorStates();
        long revision = service.state(actor.id()).revision();
        service.command(actor, new ActorStates.Command(UUID.randomUUID(), actor.id(), revision, List.of(
                new ActorStates.Apply(new EffectInstance(counter(actor.id()), UUID.randomUUID(), COUNTER, 1, 1, revision + 1)),
                new ActorStates.Apply(new EffectInstance(UUID.randomUUID(), UUID.randomUUID(), observer, 1, 1, revision + 1)))));
        var before = service.state(actor.id());
        if (observer == CONFLICT) {
            service.dispatch(actor, new ActorActivations.Event(UUID.randomUUID(), null, actor.id(), null, ActivationSpec.Event.ABILITY_USED, null, 0));
            helper.assertTrue(service.state(actor.id()).equals(before), "conflicting wave partially committed");
        }
        helper.assertTrue(expected.equals(service.fault(actor.id())), "expected " + expected + ", got " + service.fault(actor.id()));
        helper.assertTrue(service.state(actor.id()).runtime().effects().containsKey(counter(actor.id())), "fault erased committed effects");
        body.discard();
    }
}
