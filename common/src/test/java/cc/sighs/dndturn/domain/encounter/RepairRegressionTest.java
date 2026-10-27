package cc.sighs.dndturn.domain.encounter;

import cc.sighs.dndturn.application.projection.EncounterProjectionOrder;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import cc.sighs.dndturn.application.inspection.ActorViews;
import cc.sighs.dndturn.application.inspection.InspectionPolicy;
import cc.sighs.dndturn.domain.actor.*;
import cc.sighs.dndturn.application.inspection.CharacterProjection;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactSlice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepairRegressionTest {
    @Test void nativeDefenseDetailsRemainPrivateBoundedAndDetached() {
        UUID self = UUID.randomUUID(), other = UUID.randomUUID(), instance = UUID.randomUUID(), capture = UUID.randomUUID();
        var policy = new InspectionPolicy(); policy.freeze();
        var facts = new FactSlice(Map.of(ActorFacts.ARMOR_EFFECTIVE, 10.0, ActorFacts.ARMOR_CLASS, 12.0), Map.of());
        var own = policy.project(new InspectionPolicy.Observer(self, self), instance, capture, facts);
        var foreign = policy.project(new InspectionPolicy.Observer(other, self), instance, capture, facts);
        assertEquals(Set.of(ActorFacts.ARMOR_EFFECTIVE.id(), ActorFacts.ARMOR_CLASS.id()),
                own.fields().stream().map(InspectionPolicy.Field::id).collect(java.util.stream.Collectors.toSet()));
        assertTrue(foreign.fields().isEmpty());
        var modifiers = IntStream.range(0, 64).mapToObj(i -> new NativeAttributeObservation.Modifier(
                "test:modifier_" + i, NativeAttributeObservation.Operation.ADD_VALUE, 1, null, null)).toList();
        var trace = new NativeAttributeObservation(2, 10, modifiers, true);
        var definition = new ActorDefinition("test:actor", 1, Set.of(), new FactSlice(Map.of(), Map.of()),
                List.of(), ActorDefinition.Classification.unspecified(), Map.of());
        var snapshot = new ActorSnapshot(self, instance, definition, new ActorSnapshot.Revision(capture, 0, "test:rules"), facts, List.of());
        var nativeState = new NativeActorObservation(self, instance, capture, "test:actor", "Actor",
                new NativeActorObservation.Transform("test:world", 1, 2, 3, 0, 0),
                new NativeActorObservation.Vitals(10, 20, 2, true), new NativeActorObservation.Body(1, 2, 1.5, "STANDING", true, false, false),
                NativeActorObservation.Controller.PLAYER_CONNECTION, List.of(), List.of(), NativeActorObservation.Coverage.COMPLETE,
                0, Map.of("minecraft:armor", trace, "minecraft:armor_toughness", new NativeAttributeObservation(0, 2, List.of(), false)),
                NativeActorObservation.Coverage.COMPLETE);
        var sheet = new ActorViews.CharacterSheet(snapshot, ActorPersistentState.empty(), nativeState, Map.of(), null);
        var context = new CharacterProjection.CombatContext(null, List.of(), List.of(), false, 0, 0);
        assertThrows(SecurityException.class, () -> CharacterProjection.project(sheet, other, context, CharacterProjection.Section.ATTRIBUTES, 0));
        var first = CharacterProjection.project(sheet, self, context, CharacterProjection.Section.ATTRIBUTES, 0);
        assertTrue(first.pages() > 1);
        var all = new java.util.ArrayList<InspectionPolicy.Field>();
        for (int page = 0; page < first.pages(); page++) {
            var view = CharacterProjection.project(sheet, self, context, CharacterProjection.Section.ATTRIBUTES, page);
            assertTrue(view.fields().size() <= CharacterProjection.PAGE_SIZE);
            assertEquals(capture, view.capture()); assertEquals(instance, view.instance());
            all.addAll(view.fields());
        }
        assertTrue(all.stream().anyMatch(f -> f.id().contains("/unknown_source/")));
        assertTrue(all.stream().anyMatch(f -> f.id().contains("/limited/")));
        assertEquals(all.size(), all.stream().map(InspectionPolicy.Field::id).distinct().count());
        assertThrows(IllegalArgumentException.class, () -> CharacterProjection.project(sheet, self, context, CharacterProjection.Section.ATTRIBUTES, first.pages()));
        assertEquals(2, own.fields().size());
        assertEquals(10.0, nativeState.attributes().get("minecraft:armor").effective());
    }
    @Test void readinessRevisionPreservesResultsAndRejectsStalePackets() {
        UUID generation = UUID.randomUUID(), encounter = UUID.randomUUID();
        var pending = new EncounterProjectionOrder.Stamp(generation, encounter, 4, 7, true, 1);
        var ready = new EncounterProjectionOrder.Stamp(generation, encounter, 4, 7, true, 2);
        assertEquals(new EncounterProjectionOrder.Update(
            EncounterProjectionOrder.Decision.ACCEPT_KEEP_RESULTS, 13, true),
            EncounterProjectionOrder.advance(pending, 13, true, ready));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE, EncounterProjectionOrder.decide(ready, pending));
        assertEquals(EncounterProjectionOrder.Decision.IGNORE, EncounterProjectionOrder.decide(ready, ready));
    }

    @Test void damageOverloadsRejectTheSameInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damageAfterReduction(-1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damageAfterReduction(-1.0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damageAfterReduction(1, -1, false));
        assertThrows(IllegalArgumentException.class, () -> CombatRules.damageAfterReduction(1.0, -1, false));
        assertEquals(CombatRules.damageAfterReduction(7.0, 2, true), CombatRules.damageAfterReduction(7, 2, true));
    }
}
