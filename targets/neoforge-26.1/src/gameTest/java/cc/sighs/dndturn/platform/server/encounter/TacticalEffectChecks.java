package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EffectSnapshot;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.StatKey;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
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
    private static final String LEARNED = "dndturn:test_learned_defense";
    public static void register() {
        AbilityAdapterRegistry.effects().register(EFFECT);
        var definition = new cc.sighs.dndturn.domain.ability.AbilityDefinition(LEARNED, 1, "Learned defense",
                cc.sighs.dndturn.domain.action.ActionIntent.Capability.USE_ITEM,
                Set.of(cc.sighs.dndturn.domain.action.ActionIntent.TargetKind.SELF),
                cc.sighs.dndturn.domain.ability.AbilityDefinition.Activation.CONTINUOUS,
                cc.sighs.dndturn.domain.ability.ActionCost.FREE,
                new ReadContract(Set.of(cc.sighs.dndturn.domain.fact.RuleFacts.SOURCE_VALID)), LEARNED,
                cc.sighs.dndturn.domain.ability.AbilityDefinition.TargetPolicy.NATIVE_INTERACTION,
                cc.sighs.dndturn.domain.ability.AbilityDefinition.Learning.PREPARED);
        AbilityAdapterRegistry.registerContinuous(definition, new cc.sighs.dndturn.domain.ability.ContinuousAbilities.Contribution(
                List.of(new EffectDefinition.Modifier(new StatKey(ActorFacts.ARMOR_CLASS.id()), EffectDefinition.Operator.ADD, 3)), List.of()));
    }
    private static void learnedAndEquipment(GameTestHelper h) {
        var runtime = ServerRuntime.encounters(h.getLevel().getServer());
        var service = runtime.actorStates();
        var player = cc.sighs.dndturn.gametest.TestPlayers.survival(h);
        var cow = h.spawn(EntityType.COW, 2, 2, 2);
        try {
            var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
            var console = h.getLevel().getServer().createCommandSourceStack();
            for (var body : List.<net.minecraft.world.entity.LivingEntity>of(player, cow)) {
                var actor = service.context(body);
                String prefix = "dndturn actor ability ";
                String target = body.getUUID().toString();
                var reads = new ReadContract(Set.of(ActorFacts.ARMOR_CLASS));
                double base = service.snapshots().captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS);
                h.assertTrue(dispatcher.execute(prefix + "grant " + target + " dndturn:bow 1", console) == 0,
                        "equipment ability unexpectedly learnable");
                h.assertTrue(dispatcher.execute(prefix + "grant " + target + " " + LEARNED + " 1", console) == 1, "grant command rejected");
                var learned = service.state(actor.id()).persistent().grants().values().stream().filter(g -> g.ability().equals(LEARNED)).findFirst().orElseThrow();
                long revision = service.state(actor.id()).revision();
                dispatcher.execute(prefix + "grant " + target + " " + LEARNED + " 1", console);
                h.assertTrue(service.state(actor.id()).revision() == revision, "duplicate grant changed owner");
                h.assertTrue(service.snapshots().captureActor(actor).abilities().stream().noneMatch(b -> b.id().equals(LEARNED)), "unprepared grant bound");
                h.assertTrue(dispatcher.execute(prefix + "prepare " + target + " " + learned.id(), console) == 1, "prepare command rejected");
                var frozen = service.snapshots().captureActor(actor);
                var binding = frozen.abilities().stream().filter(b -> b.id().equals(LEARNED)).findFirst().orElseThrow();
                h.assertTrue(service.snapshots().captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS) == base + 3,
                        "learned continuous ability did not reach actual defense path");
                var defense = service.snapshots().defense(body);
                var defenseReads = new ReadContract(Set.of(cc.sighs.dndturn.domain.fact.RuleFacts.RESOLVED_AC));
                h.assertTrue(defense.read(defenseReads, cc.sighs.dndturn.domain.fact.RuleFacts.RESOLVED_AC) == base + 3, "execution lost learned defense");
                dispatcher.execute(prefix + "unprepare " + target + " " + learned.id(), console);
                dispatcher.execute(prefix + "prepare " + target + " " + learned.id(), console);
                h.assertTrue(!service.snapshots().captureActor(actor).abilities().contains(binding), "reprepared grant reused old evidence");
                dispatcher.execute(prefix + "revoke " + target + " " + learned.id(), console);
                h.assertTrue(service.snapshots().captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS) == base,
                        "revoked contribution survived");
                h.assertTrue(frozen.abilities().contains(binding), "published snapshot was mutated");
                dispatcher.execute(prefix + "grant " + target + " " + LEARNED + " 1", console);
                var renewed = service.state(actor.id()).persistent().grants().values().stream().filter(g -> g.ability().equals(LEARNED)).findFirst().orElseThrow();
                h.assertTrue(!renewed.id().equals(learned.id()), "regrant reused revoked identity");
                dispatcher.execute(prefix + "revoke " + target + " " + renewed.id(), console);
                body.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
                var hand = cc.sighs.dndturn.domain.action.ActionIntent.Hand.MAIN_HAND;
                var source = cc.sighs.dndturn.platform.server.actor.ActorEquipment.bind(actor,
                        cc.sighs.dndturn.domain.ability.GrantEvidence.equipment(hand, cc.sighs.dndturn.platform.server.actor.ActorEquipment.reference(actor, hand)));
                body.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, body.getMainHandItem().copy());
                boolean rejected = false;
                try { cc.sighs.dndturn.platform.server.actor.ActorEquipment.validate(actor, source, source.item().revision()); }
                catch (cc.sighs.dndturn.domain.encounter.operation.ActionFailure expected) { rejected = true; }
                h.assertTrue(rejected, "same-value replacement retained old equipment identity");
                var current = cc.sighs.dndturn.platform.server.actor.ActorEquipment.bind(actor,
                        cc.sighs.dndturn.domain.ability.GrantEvidence.equipment(hand, cc.sighs.dndturn.platform.server.actor.ActorEquipment.reference(actor, hand)));
                body.getMainHandItem().setDamageValue(1);
                rejected = false;
                try { cc.sighs.dndturn.platform.server.actor.ActorEquipment.validate(actor, current, current.item().revision()); }
                catch (cc.sighs.dndturn.domain.encounter.operation.ActionFailure expected) { rejected = true; }
                h.assertTrue(rejected, "in-place component mutation retained old equipment evidence");
            }
            boolean denied = false;
            try { dispatcher.execute("dndturn actor ability list " + player.getUUID(), player.createCommandSourceStack()); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            h.assertTrue(denied, "non-admin accessed actor management");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) { throw new IllegalStateException(failure); }
        finally { h.getLevel().getServer().getPlayerList().remove(player); player.discard(); cow.discard(); }
    }
    public static void state(GameTestHelper h) {
        learnedAndEquipment(h);
        var body = h.spawn(EntityType.COW, 1, 2, 1);
        var actor = new LiveActorContext(body);
        var service = ServerRuntime.encounters(h.getLevel().getServer()).actorStates();
        var reads = new ReadContract(Set.of(ActorFacts.ARMOR_CLASS));
        double base = service.snapshots().captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS);
        var instance = UUID.randomUUID();
        var application = new ActorStates.Command(UUID.randomUUID(), actor.id(), service.state(actor.id()).revision(),
                List.of(new ActorStates.Apply(new EffectInstance(instance, UUID.randomUUID(), EFFECT, 1, 2,
                        service.state(actor.id()).revision() + 1, 2, actor.id(), "dndturn:move"))));
        service.command(actor, application);
        h.assertTrue(service.snapshots().captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS) == base + 2,
                "effect contribution did not reach native actor snapshot");
        h.assertTrue(service.snapshots().captureActor(actor).abilities().stream().noneMatch(b -> instance.equals(b.source().grant())),
                "effect grant became available below stack threshold");
        var add = new ActorStates.Command(UUID.randomUUID(), actor.id(), service.state(actor.id()).revision(),
                List.of(new ActorStates.AddStacks(instance, 1), new ActorStates.RefreshDuration(instance, 3)));
        service.command(actor, add);
        var snapshot = service.snapshots().captureActor(actor);
        h.assertTrue(snapshot.abilities().stream().anyMatch(b -> instance.equals(b.source().grant())), "effect grant missing at threshold");
        h.assertTrue(service.snapshots().captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS) == base + 4,
                "stack contribution missing");
        var defenseReads = new ReadContract(Set.of(cc.sighs.dndturn.domain.fact.RuleFacts.RESOLVED_AC));
        h.assertTrue(service.snapshots().defense(body).read(defenseReads, cc.sighs.dndturn.domain.fact.RuleFacts.RESOLVED_AC) == base + 4,
                "execution defense diverged from actor effect compilation");
        var sheetWithEffect = service.character(actor);
        var explainedEffect = sheetWithEffect.derivations().get(ActorFacts.ARMOR_CLASS.id());
        h.assertTrue(explainedEffect.value() == base + 4 && explainedEffect.contributions().stream().anyMatch(c -> instance.equals(c.effect()) && c.scale() == 2),
                "character derivation lost the effect source or disagrees with execution");
        // Partial reads must retain native/derived ownership and effect semantics without
        // needing to discover grants or project unrelated effects.
        var partialReads = new ReadContract(Set.of(ActorFacts.ARMOR_CLASS, ActorFacts.DAMAGE_REDUCTION,
                ActorFacts.ARMOR_EFFECTIVE, ActorFacts.SPEED_EFFECTIVE, ActorFacts.POSE));
        h.assertTrue(service.snapshots().captureFacts(actor, partialReads).equals(snapshot.facts().restrict(partialReads)),
                "partial fact capture differs from the full actor snapshot");
        var binding = snapshot.abilities().stream().filter(b -> instance.equals(b.source().grant())).findFirst().orElseThrow();
        var refresh = new ActorStates.Command(UUID.randomUUID(), actor.id(), service.state(actor.id()).revision(),
                List.of(new ActorStates.RefreshDuration(instance, 4)));
        service.command(actor, refresh);
        var refreshed = service.snapshots().captureActor(actor);
        h.assertTrue(refreshed.abilities().contains(binding), "duration refresh revoked the effect grant");
        var oldEffect = (EffectSnapshot.Tactical) snapshot.effects().stream()
                .filter(e -> e instanceof EffectSnapshot.Tactical t && t.instance().equals(instance)).findFirst().orElseThrow();
        var nextEffect = (EffectSnapshot.Tactical) refreshed.effects().stream()
                .filter(e -> e instanceof EffectSnapshot.Tactical t && t.instance().equals(instance)).findFirst().orElseThrow();
        h.assertTrue(nextEffect.revision() > oldEffect.revision() && nextEffect.grantRevision() == oldEffect.grantRevision()
                && nextEffect.remaining() == 4 && oldEffect.remaining() == 3, "effect snapshot revisions or immutability");
        body.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 80, 2));
        var nativeProjection = service.snapshots().captureActor(actor).effects().stream()
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
        h.assertTrue(service.snapshots().captureActor(actor).abilities().stream().noneMatch(b -> instance.equals(b.source().grant())),
                "consumed stacks retained conditional grant");
        h.runAfterDelay(1, () -> {
        service.advance(actor.id(), EffectDefinition.Clock.TURN_END);
        service.advance(actor.id(), EffectDefinition.Clock.TURN_END);
        service.advance(actor.id(), EffectDefinition.Clock.TURN_END);
        service.advance(actor.id(), EffectDefinition.Clock.TURN_END);
        h.assertTrue(service.state(actor.id()).runtime().effects().isEmpty(), "effect did not expire");
        h.assertTrue(service.snapshots().captureFacts(actor, reads).read(reads, ActorFacts.ARMOR_CLASS) == base,
                "expired effect retained stat contribution");
        nativeDefenses(h, body, service);
        });
    }
    /** Native equipment application and unknown modifier attribution cannot be proved by scalar rule tests. */
    private static void nativeDefenses(GameTestHelper h, net.minecraft.world.entity.LivingEntity body,
                                       cc.sighs.dndturn.platform.server.actor.ActorStateAuthority service) {
        var armor = body.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        armor.setBaseValue(2.75);
        var unknown = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("dndturn", "test_unknown_armor"),
                0.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        armor.addTransientModifier(unknown);
        body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
        body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
        var reads = new ReadContract(Set.of(ActorFacts.ARMOR_BASE, ActorFacts.ARMOR_EFFECTIVE,
                ActorFacts.TOUGHNESS_EFFECTIVE, ActorFacts.ARMOR_CLASS, ActorFacts.DAMAGE_REDUCTION));
        // Wait for ordinary native equipment application in this fixture; queries themselves never advance it.
        h.runAfterDelay(2, () -> {
            try {
                var actor = service.context(body);
                var nativeBefore = Set.copyOf(armor.getModifiers());
                var captured = service.character(actor);
                h.assertTrue(captured.nativeState().inventoryCoverage() == cc.sighs.dndturn.domain.actor.NativeActorObservation.Coverage.UNSUPPORTED
                        && captured.nativeState().controller() == cc.sighs.dndturn.domain.actor.NativeActorObservation.Controller.MOB_AI
                        && captured.behavior() != null, "mob identity or unsupported inventory was fabricated as player state");
                h.assertTrue(captured.nativeState().attributes().get("minecraft:movement_speed").modifiers().stream()
                        .anyMatch(m -> "minecraft:speed".equals(m.nativeEffect())), "native potion contribution lost its source");
                verifyCharacterPages(h, captured);
                var trace = captured.nativeState().attributes().get("minecraft:armor");
                h.assertTrue(trace.base() == 2.75 && trace.effective() == armor.getValue()
                        && trace.effective() == captured.actor().facts().read(reads, ActorFacts.ARMOR_EFFECTIVE), "mixed native attribute sample");
                h.assertTrue(trace.modifiers().stream().anyMatch(m -> m.equipment() != null
                        && m.equipment().slot().equals("chest") && m.equipment().item().equals("minecraft:diamond_chestplate")),
                        "live chest armor contribution was not associated with its current declaration");
                h.assertTrue(trace.modifiers().stream().anyMatch(m -> m.id().equals(unknown.id().toString())
                        && m.equipment() == null && m.operation() == cc.sighs.dndturn.domain.actor.NativeAttributeObservation.Operation.ADD_MULTIPLIED_BASE),
                        "unknown native multiplier was fabricated as an equipment source");
                h.assertTrue(nativeBefore.equals(armor.getModifiers()), "inspection mutated native modifiers");
                double ac = captured.actor().facts().read(reads, ActorFacts.ARMOR_CLASS);
                h.assertTrue(ac == Math.floor(trace.effective()) + 2, "equipment was counted again in tactical AC");
                var defenseReads = new ReadContract(Set.of(cc.sighs.dndturn.domain.fact.RuleFacts.RESOLVED_AC));
                h.assertTrue(service.snapshots().defense(body).read(defenseReads, cc.sighs.dndturn.domain.fact.RuleFacts.RESOLVED_AC) == ac,
                        "inspection and execution use different defense calculations");
                body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                h.runAfterDelay(2, () -> {
                    try {
                        var next = service.character(actor);
                        h.assertTrue(next.actor().facts().read(reads, ActorFacts.ARMOR_CLASS) < ac
                                && captured.actor().facts().read(reads, ActorFacts.ARMOR_CLASS) == ac,
                                "equipment change failed to refresh or rewrote the prior snapshot");
                        h.assertTrue(next.nativeState().attributes().get("minecraft:armor").modifiers().stream()
                                .noneMatch(m -> m.equipment() != null && m.equipment().item().equals("minecraft:diamond_chestplate")),
                                "old chest source survived equipment replacement");
                        for (int i = 0; i < 65; i++) armor.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("dndturn", "test_armor_budget_" + i),
                                0.01, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                        var limited = service.character(actor);
                        h.assertTrue(!limited.nativeState().attributes().get("minecraft:armor").complete()
                                && limited.nativeState().attributes().get("minecraft:armor").effective() == armor.getValue()
                                && limited.actor().facts().read(reads, ActorFacts.ARMOR_CLASS) == Math.floor(armor.getValue()) + 2,
                                "explanation budget changed the native or tactical value");
                        var player = h.makeMockServerPlayerInLevel();
                        try {
                            player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 3));
                            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(8);
                            player.setHealth(13); player.setAbsorptionAmount(2);
                            var playerSheet = service.character(service.context(player));
                            h.assertTrue(playerSheet.nativeState().vitals().health() == 13 && playerSheet.nativeState().vitals().absorption() == 2
                                    && playerSheet.nativeState().inventory().stream().anyMatch(i -> i.address().equals("0") && i.count() == 3 && i.id().equals("minecraft:apple")),
                                    "player native vitals or inventory were not captured");
                            player.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY); player.setHealth(10);
                            h.assertTrue(playerSheet.nativeState().vitals().health() == 13 && playerSheet.nativeState().inventory().getFirst().count() == 3,
                                    "native writes changed the published character observation");
                            verifyCharacterPages(h, playerSheet);
                        } finally { h.getLevel().getServer().getPlayerList().remove(player); player.discard(); }
                        h.succeed();
                    } finally { body.discard(); }
                });
            } catch (RuntimeException | Error failure) { body.discard(); throw failure; }
        });
    }
    private static void verifyCharacterPages(GameTestHelper h, cc.sighs.dndturn.application.inspection.ActorViews.CharacterSheet sheet) {
        var context = new cc.sighs.dndturn.application.inspection.CharacterProjection.CombatContext(null, List.of(), List.of(), false, 0, 0);
        for (var section : cc.sighs.dndturn.application.inspection.CharacterProjection.Section.values()) {
            int pages = cc.sighs.dndturn.application.inspection.CharacterProjection.project(sheet, sheet.actor().actor(), context, section, 0).pages();
            for (int page = 0; page < pages; page++) {
                var view = cc.sighs.dndturn.application.inspection.CharacterProjection.project(sheet, sheet.actor().actor(), context, section, page);
                var query = new cc.sighs.dndturn.platform.network.InspectionProtocol.Query(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sheet.actor().actor(), section, page);
                var reply = new cc.sighs.dndturn.platform.network.InspectionProtocol.Reply(query, cc.sighs.dndturn.platform.network.InspectionProtocol.Status.OK, view);
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
                try {
                    cc.sighs.dndturn.platform.network.InspectionProtocol.Reply.CODEC.encode(buffer, reply);
                    h.assertTrue(buffer.readableBytes() <= 16384 && reply.equals(cc.sighs.dndturn.platform.network.InspectionProtocol.Reply.CODEC.decode(buffer)),
                            "character page protocol lost identity, fields or page bounds");
                } finally { buffer.release(); }
            }
        }
    }
}
