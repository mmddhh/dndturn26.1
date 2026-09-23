package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
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
