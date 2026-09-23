package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EffectSnapshot;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.StatKey;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import cc.sighs.dndturn.platform.server.persistence.ActorSavedData;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;

/** Test-only registration; exercises the production state, snapshot and checkpoint ports. */
public final class TacticalEffectChecks {
    private static final EffectDefinition EFFECT = new EffectDefinition("dndturn:test_tactical_effect", 1,
            EffectDefinition.Clock.TURN_END, EffectDefinition.Stacking.STACK, 3, false, true,
            List.of(new EffectDefinition.Modifier(new StatKey(ActorFacts.ARMOR_CLASS.id()), EffectDefinition.Operator.ADD, 2)),
            List.of(new EffectDefinition.Grant("dndturn:move", 1, 2, 2)), Set.of("dndturn:test"),
            EffectDefinition.InstancePolicy.PER_SOURCE_ACTOR, EffectDefinition.Overflow.REJECT, EffectDefinition.Refresh.MAXIMUM);
    public static void register() { AbilityAdapterRegistry.effects().register(EFFECT); }
    public static void state(GameTestHelper h) {
        var body = h.spawn(EntityType.COW, 1, 2, 1);
        var actor = new LiveActorContext(body);
        var service = ServerRuntime.encounters(h.getLevel().getServer()).actorStates();
        var reads = new ReadContract(Set.of(ActorFacts.ARMOR_CLASS));
        double base = MinecraftSnapshotCapture.captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS);
        var instance = UUID.randomUUID();
        var application = new ActorStates.Command(UUID.randomUUID(), actor.id(), service.state(actor.id()).revision(),
                List.of(new ActorStates.Apply(new EffectInstance(instance, UUID.randomUUID(), EFFECT, 1, 2,
                        service.state(actor.id()).revision() + 1, 2, actor.id(), "dndturn:move"))));
        service.command(actor, application);
        h.assertTrue(MinecraftSnapshotCapture.captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS) == base + 2,
                "effect contribution did not reach native actor snapshot");
        h.assertTrue(MinecraftSnapshotCapture.captureActor(actor).abilities().stream().noneMatch(b -> instance.equals(b.source().grant())),
                "effect grant became available below stack threshold");
        var add = new ActorStates.Command(UUID.randomUUID(), actor.id(), service.state(actor.id()).revision(),
                List.of(new ActorStates.AddStacks(instance, 1), new ActorStates.RefreshDuration(instance, 3)));
        service.command(actor, add);
        var snapshot = MinecraftSnapshotCapture.captureActor(actor);
        h.assertTrue(snapshot.abilities().stream().anyMatch(b -> instance.equals(b.source().grant())), "effect grant missing at threshold");
        h.assertTrue(MinecraftSnapshotCapture.captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS) == base + 4,
                "stack contribution missing");
        var binding = snapshot.abilities().stream().filter(b -> instance.equals(b.source().grant())).findFirst().orElseThrow();
        var refresh = new ActorStates.Command(UUID.randomUUID(), actor.id(), service.state(actor.id()).revision(),
                List.of(new ActorStates.RefreshDuration(instance, 4)));
        service.command(actor, refresh);
        var refreshed = MinecraftSnapshotCapture.captureActor(actor);
        h.assertTrue(refreshed.abilities().contains(binding), "duration refresh revoked the effect grant");
        var oldEffect = (EffectSnapshot.Tactical) snapshot.effects().stream()
                .filter(e -> e instanceof EffectSnapshot.Tactical t && t.instance().equals(instance)).findFirst().orElseThrow();
        var nextEffect = (EffectSnapshot.Tactical) refreshed.effects().stream()
                .filter(e -> e instanceof EffectSnapshot.Tactical t && t.instance().equals(instance)).findFirst().orElseThrow();
        h.assertTrue(nextEffect.revision() > oldEffect.revision() && nextEffect.grantRevision() == oldEffect.grantRevision()
                && nextEffect.remaining() == 4 && oldEffect.remaining() == 3, "effect snapshot revisions or immutability");
        body.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 80, 2));
        var nativeProjection = MinecraftSnapshotCapture.captureActor(actor).effects().stream()
                .filter(e -> e instanceof EffectSnapshot.Vanilla).findFirst().orElseThrow();
        h.assertTrue(nativeProjection.rank() == 3 && ((EffectSnapshot.Vanilla)nativeProjection).amplifier() == 2 && nativeProjection.stacks() == 1 && nativeProjection.remaining() == 80,
                "native amplifier was converted into tactical stacks or duration changed by capture");
        var local = new ActorStates();
        local.apply(application); local.apply(add); local.apply(refresh);
        var checkpoint = new ActorSavedData(); checkpoint.update(local);
        var restored = checkpoint.restore();
        h.assertTrue(restored.state(actor.id()).equals(local.state(actor.id())), "effect checkpoint lost rank, identity or policies");
        h.assertTrue(restored.apply(refresh).equals(local.state(actor.id())), "restored command executed twice");
        var beforeRetry = restored.state(actor.id());
        restored.apply(add);
        h.assertTrue(restored.state(actor.id()).equals(beforeRetry), "historical retry rewound current state");
        var weighted = new ActorStates.Command(UUID.randomUUID(), actor.id(), local.state(actor.id()).revision(),
                List.of(new ActorStates.RefreshDuration(instance, 4)), UUID.randomUUID(), 2);
        local.apply(weighted); checkpoint.update(local); restored = checkpoint.restore();
        h.assertTrue(restored.receipt(weighted.operation()).command().proposedMutations() == 2,
                "checkpoint reduced the pre-merge reaction budget");
        var consume = new ActorStates.Command(UUID.randomUUID(), actor.id(), service.state(actor.id()).revision(),
                List.of(new ActorStates.ConsumeStacks(instance, 1)));
        service.command(actor, consume);
        local.apply(new ActorStates.Command(consume.operation(), actor.id(), local.state(actor.id()).revision(), consume.changes()));
        checkpoint.update(local); restored = checkpoint.restore();
        h.assertTrue(restored.apply(restored.receipt(consume.operation()).command()).equals(local.state(actor.id())), "consume receipt failed round trip");
        h.assertTrue(MinecraftSnapshotCapture.captureActor(actor).abilities().stream().noneMatch(b -> instance.equals(b.source().grant())),
                "consumed stacks retained conditional grant");
        service.advance(actor.id(), EffectDefinition.Clock.TURN_END);
        service.advance(actor.id(), EffectDefinition.Clock.TURN_END);
        service.advance(actor.id(), EffectDefinition.Clock.TURN_END);
        service.advance(actor.id(), EffectDefinition.Clock.TURN_END);
        h.assertTrue(service.state(actor.id()).runtime().effects().isEmpty(), "effect did not expire");
        h.assertTrue(MinecraftSnapshotCapture.captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS) == base,
                "expired effect retained stat contribution");
        body.discard(); h.succeed();
    }
}
