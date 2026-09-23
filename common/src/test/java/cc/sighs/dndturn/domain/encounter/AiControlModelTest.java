package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.ability.AbilityGrant;
import cc.sighs.dndturn.domain.ability.BuiltinAbilities;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.ai.AiAbilitySemantics;
import cc.sighs.dndturn.domain.ai.AiAffordance;
import cc.sighs.dndturn.domain.ai.AiDecisionContext;
import cc.sighs.dndturn.domain.ai.AiDefinition;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.ai.AiRuntimeState;
import cc.sighs.dndturn.domain.ai.PerceptionSnapshot;
import cc.sighs.dndturn.domain.control.GateDecision;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.domain.spatial.GridCell;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiControlModelTest {
    private final UUID actor = UUID.randomUUID(), instance = UUID.randomUUID(), target = UUID.randomUUID();
    private AiDefinition definition(AiDefinition.TargetPreference preference, AiDefinition.Source source) {
        return new AiDefinition("test:ai", 1, "dndturn:shared_ground", Set.of(), preference,
            AiDefinition.Positioning.GROUND_OPPORTUNITY, AiDefinition.ResourcePreference.CONSERVATIVE, AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY,
            ActorFacts.DECISION, new AiDefinition.Provenance(source, "test:provider", 1, "test values"));
    }
    private AiDecisionContext context(AiDefinition d, boolean visible, boolean current, boolean hostile, boolean legal, Set<AiAffordance> tags) {
        var binding = new AbilityBinding(BuiltinAbilities.require("dndturn:intrinsic_melee"), AbilityGrant.nativeGrant(actor,
            GrantEvidence.intrinsic("test:melee", 1, actor, instance)));
        var snapshot = new ActorSnapshot(actor, instance, new ActorDefinition("test:actor", 1, Set.of(), new FactSlice(Map.of(), Map.of())),
            new ActorSnapshot.Revision(UUID.randomUUID(), 0, CombatRules.RULES_REVISION), new FactSlice(Map.of(), Map.of()), List.of(binding));
        return new AiDecisionContext(snapshot, d, EncounterPhase.ACTIVE, true, "minecraft:overworld",
            new PerceptionSnapshot(List.of(new PerceptionSnapshot.ObservedActor(target, visible, current, true, true, 4))),
            hostile ? Set.of(target) : Set.of(), List.of(new AiDecisionContext.Option(target, binding, legal, tags)), null,
            new AiRuntimeState(actor, instance, 1, 0, null));
    }
    private static final Set<AiAffordance> MELEE = Set.of(AiAffordance.DAMAGE, AiAffordance.MELEE);
    @Test void rangedAffordanceUsesTheSameBindingAndLegality() {
        var d = definition(AiDefinition.TargetPreference.VISIBLE_PLAYERS, AiDefinition.Source.AUDITED_NATIVE_FAMILY);
        var ranged = Set.of(AiAffordance.DAMAGE, AiAffordance.RANGED);
        assertInstanceOf(AiPlanner.Propose.class, AiPlanner.decide(context(d, true, false, false, true, ranged)));
        assertInstanceOf(AiPlanner.EndTurn.class, AiPlanner.decide(context(d, true, false, false, false, ranged)));
    }
    @Test void completedAttackDoesNotDiscardRemainingMovement() {
        var d = definition(AiDefinition.TargetPreference.VISIBLE_PLAYERS, AiDefinition.Source.AUDITED_NATIVE_FAMILY);
        var c = context(d, true, false, false, true, MELEE);
        var move = new ActionIntent("dndturn:move", 1, ActionIntent.Capability.MOVE,
                new ActionIntent.Target(ActionIntent.TargetKind.GROUND, "minecraft:overworld", null,
                        new GridCell(1, 0, 0), -1, 0, 0, 0), GrantEvidence.basic());
        var spent = new AiDecisionContext(c.actor(), c.definition(), c.phase(), false, c.dimension(), c.perception(),
                c.hostile(), c.options(), move, c.runtime());
        assertEquals(new AiPlanner.Propose(move), AiPlanner.decide(spent));
    }
    @Test void decisionVocabularyDoesNotCarryExecutableAuthority() {
        assertTrue(GateDecision.allow("READY").allowed());
        assertFalse(GateDecision.hold("WAIT").allowed());
        assertFalse(GateDecision.deny("INVALID").allowed());
        assertNotEquals(GateDecision.hold("WAIT"), GateDecision.deny("WAIT"));
        assertThrows(IllegalArgumentException.class, () -> GateDecision.allow("free text"));
        assertEquals(List.of(GateDecision.Disposition.class, String.class, GateDecision.Evidence.class),
            Arrays.stream(GateDecision.class.getRecordComponents()).map(c -> c.getType()).toList());
        // No execution API or authority object is obtainable from a captured decision.
        assertEquals(0, GateDecision.class.getInterfaces().length);
    }
    @Test void perceptionRelationPreferenceAndLegalityStayIndependent() {
        var d = definition(AiDefinition.TargetPreference.COMMITTED_OR_HOSTILE_PLAYERS, AiDefinition.Source.AUDITED_NATIVE_FAMILY);
        assertInstanceOf(AiPlanner.EndTurn.class, AiPlanner.decide(context(d, true, false, false, true, MELEE)));
        assertInstanceOf(AiPlanner.EndTurn.class, AiPlanner.decide(context(d, false, false, true, true, MELEE)));
        assertInstanceOf(AiPlanner.Propose.class, AiPlanner.decide(context(d, false, true, false, true, MELEE)));
        assertInstanceOf(AiPlanner.EndTurn.class, AiPlanner.decide(context(d, true, true, true, false, MELEE)));
    }
    @Test void affordanceDoesNotGrantLegalityAndPlannerIsDeterministic() {
        var d = definition(AiDefinition.TargetPreference.VISIBLE_PLAYERS, AiDefinition.Source.AUDITED_NATIVE_FAMILY);
        var c = context(d, true, false, false, true, MELEE);
        assertEquals(AiPlanner.decide(c), AiPlanner.decide(c));
        assertInstanceOf(AiPlanner.EndTurn.class, AiPlanner.decide(context(d, true, true, true, false, MELEE)));
        assertInstanceOf(AiPlanner.EndTurn.class, AiPlanner.decide(context(d, true, true, true, true, Set.of())));
        assertSame(d, context(d, false, true, true, false, MELEE).definition());
    }
    @Test void unknownAndStructuralEvidenceNeverBecomeZombie() {
        for (var source : List.of(AiDefinition.Source.UNSUPPORTED, AiDefinition.Source.STRUCTURAL_CANDIDATE)) {
            var decision = AiPlanner.decide(context(definition(AiDefinition.TargetPreference.VISIBLE_PLAYERS, source), true, true, true, true, MELEE));
            assertEquals(new AiPlanner.EndTurn("AI_UNSUPPORTED"), decision);
        }
        assertInstanceOf(AiPlanner.EndTurn.class, AiPlanner.decide(context(definition(AiDefinition.TargetPreference.NONE,
            AiDefinition.Source.GENERIC_FALLBACK), true, true, true, true, MELEE)));
    }
    @Test void semanticRegistryIsVersionedFrozenAndNotAnAbilityGrant() {
        var registry = new AiAbilitySemantics();
        var key = new AiAbilitySemantics.Key("test:ability", 1);
        registry.register(key, MELEE);
        assertThrows(IllegalArgumentException.class, () -> registry.register(key, Set.of()));
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.register(new AiAbilitySemantics.Key("test:ability", 2), MELEE));
        assertTrue(registry.get("test:ability", 2).isEmpty());
    }
    @Test void runtimeMemoryHasNoRuleBalanceAndHasBoundedContinuation() {
        var state = new AiRuntimeState(actor, instance, 1, 0, null);
        for (int i = 0; i < 8; i++) state = state.submitted(UUID.randomUUID());
        var exhausted = state;
        assertThrows(IllegalArgumentException.class, () -> exhausted.submitted(UUID.randomUUID()));
    }
}
