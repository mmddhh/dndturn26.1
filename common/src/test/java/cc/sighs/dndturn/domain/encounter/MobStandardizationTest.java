package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.ability.AbilityGrant;
import cc.sighs.dndturn.domain.ability.AbilityInvocation;
import cc.sighs.dndturn.domain.ability.BuiltinAbilities;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.resolution.CombatantSnapshot;
import cc.sighs.dndturn.domain.resolution.EncounterInput;
import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import cc.sighs.dndturn.domain.ability.AbilityRegistry;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EquipmentEffects;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.domain.spatial.GridCell;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobStandardizationTest {
    @Test void sharedMeleeRequiresHostilityForMobsAndNeverPermitsPvp() {
        UUID actor = UUID.randomUUID(), target = UUID.randomUUID(), instance = UUID.randomUUID();
        var binding = new AbilityBinding(BuiltinAbilities.require("dndturn:intrinsic_melee"),
            AbilityGrant.nativeGrant(actor, GrantEvidence.intrinsic("test:melee", 1, actor, instance)));
        var empty = new FactSlice(Map.of(), Map.of());
        var snapshot = new ActorSnapshot(actor, instance, new ActorDefinition("test:actor", 1, Set.of(), empty),
            new ActorSnapshot.Revision(UUID.randomUUID(), 0, CombatRules.RULES_REVISION), empty, List.of(binding));
        var member = new MemberView(actor, 10, 0, 30, true, true, false, false, 0);
        var other = new MemberView(target, 9, 0, 30, true, true, false, false, 0);
        var encounter = new EncounterInput(UUID.randomUUID(), 0, EncounterPhase.ACTIVE, 1, actor, Map.of(actor, member, target, other));
        for (boolean player : List.of(false, true)) for (boolean targetPlayer : List.of(false, true))
            for (boolean hostile : List.of(false, true)) {
                var aim = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, "minecraft:overworld", target, null, -1, 0, 0, 0);
                var intent = binding.invocation(aim);
                var world = new FactSlice(Map.of(RuleFacts.SOURCE_VALID, true, RuleFacts.NATIVE_REJECTION, "",
                    RuleFacts.IN_REACH, true, RuleFacts.ACTOR_PLAYER, player, RuleFacts.TARGET_PLAYER, targetPlayer,
                    RuleFacts.TARGET_LIVING, true, RuleFacts.DIMENSION_MATCH, true, RuleFacts.TARGET_LOADED, true,
                    RuleFacts.TARGET_DOMAIN, true, RuleFacts.TARGET_HOSTILE, hostile), Map.of());
                var context = new ResolutionContext(new CombatantSnapshot(snapshot, member), encounter,
                    new AbilityInvocation(actor, binding, intent, snapshot.revision()), world, empty, ResolutionContext.Stage.PROPOSAL);
                assertEquals(player != targetPlayer || !player && hostile, RuleResolver.resolve(context).accepted());
            }
    }

    private static EffectDefinition effect(String id) {
        return new EffectDefinition(id, 1, EffectDefinition.Clock.EXPLICIT, EffectDefinition.Stacking.REJECT,
                1, false, true, List.of(), List.of());
    }
    @Test void typedContributionRequiresLiveEffectAndRejectsAmbiguity() {
        var registry = new EquipmentEffects(); var definition = effect("test:archery");
        var policy = new EquipmentEffects.Policy("test:arrows", 1, EquipmentEffects.Ammunition.HELD_OR_NATIVE_ORDINARY_ARROW,
                EquipmentEffects.Consumption.NATIVE_NO_ITEM_COST);
        registry.register(definition, policy);
        assertThrows(IllegalStateException.class, () -> registry.register(definition, policy));
        var effect = new EffectInstance(UUID.randomUUID(), UUID.randomUUID(), definition, 1, 0, 1);
        assertEquals(policy, registry.resolve(List.of(effect)).orElseThrow().policy());
        assertTrue(registry.resolve(List.of()).isEmpty());
        assertTrue(registry.resolve(List.of(new EffectInstance(UUID.randomUUID(), UUID.randomUUID(),
                effect("test:unknown"), 1, 0, 1))).isEmpty());
        var other = new EffectInstance(UUID.randomUUID(), UUID.randomUUID(), definition, 1, 0, 1);
        assertThrows(IllegalStateException.class, () -> registry.resolve(List.of(effect, other)));
        registry.freeze(); assertThrows(IllegalStateException.class, () -> registry.register(effect("test:new"), policy));
    }
    @Test void equipmentEvidenceBindsEffectInstanceAndNativeDependencies() {
        UUID actor = UUID.randomUUID(), instance = UUID.randomUUID(), effect = UUID.randomUUID();
        var source = GrantEvidence.equipment(ActionIntent.Hand.MAIN_HAND, new ActionIntent.ItemReference(0, "bow"),
                "test:policy", 2, actor, instance, effect, 3).withDependencies(Map.of("ammunition", "native", "driver", "test:driver@1"));
        var changed = source.withItemRevision("damaged");
        assertEquals(source.dependencies(), changed.dependencies());
        assertEquals(source.grant(), changed.grant()); assertEquals(source.instance(), changed.instance());
        assertNotEquals(source, changed);
        assertNotEquals(source, source.withDependencies(Map.of("ammunition", "held")));
        assertThrows(UnsupportedOperationException.class, () -> source.dependencies().put("injected", "value"));
    }
    @Test void definitionOwnsTargetCostExecutionAndBoundedParameters() {
        var definition = new AbilityDefinition("test:area", 1, "Area", ActionIntent.Capability.ATTACK,
                Set.of(ActionIntent.TargetKind.GROUND), AbilityDefinition.Activation.MANUAL, ActionCost.ATTACK,
                RuleFacts.AVAILABILITY, "test:area", AbilityDefinition.TargetPolicy.NATIVE_INTERACTION,
                OperationRecord.Kind.USE_ITEM, Map.of("shape", Set.of("small", "wide")));
        var registry = new AbilityRegistry(); registry.register(definition); registry.freeze();
        var source = GrantEvidence.intrinsic("test:source", 1, UUID.randomUUID(), UUID.randomUUID());
        var target = new ActionIntent.Target(ActionIntent.TargetKind.GROUND, "minecraft:overworld", null,
                new GridCell(0, 0, 0), -1, 0, 0, 0);
        var intent = new ActionIntent("test:area", 1, null, ActionIntent.Capability.ATTACK, target, null, source,
                null, CombatRules.RULES_REVISION, Map.of("shape", "small"));
        assertSame(definition, registry.require(intent));
        assertEquals(OperationRecord.Kind.USE_ITEM, registry.require(intent).executionKind());
        assertEquals(ActionCost.ATTACK, registry.require(intent).cost());
        assertThrows(IllegalArgumentException.class, () -> definition.validateParameters(Map.of("shape", "unbounded")));
        assertThrows(IllegalArgumentException.class, () -> definition.validateParameters(Map.of("damage", "9999")));
        assertNotEquals(intent, new ActionIntent("test:area", 1, null, ActionIntent.Capability.ATTACK, target, null, source,
                null, CombatRules.RULES_REVISION, Map.of("shape", "wide")));
    }
}
