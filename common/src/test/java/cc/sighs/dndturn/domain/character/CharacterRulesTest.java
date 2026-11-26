package cc.sighs.dndturn.domain.character;

import java.util.Set;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterRulesTest {
    @Test
    void pointBuyRequiresExactlyTwentySevenPoints() {
        var valid = new AbilityScores(15, 15, 14, 10, 8, 8);
        assertTrue(PointBuy.isValid(valid));
        assertEquals(27, PointBuy.totalCost(valid));
        assertFalse(PointBuy.isValid(new AbilityScores(15, 15, 14, 12, 8, 8)));
        assertFalse(PointBuy.isValid(new AbilityScores(15, 15, 14, 8, 8, 8)));
        assertFalse(PointBuy.isValid(new AbilityScores(8, 8, 8, 8, 8, 8)));
    }

    @Test
    void creationAppliesRacialBoostsAfterPointBuy() {
        var fighter = new CharacterClass("dndturn:fighter", 1, "Fighter", 10,
                Set.of(Ability.STR, Ability.CON), Set.of(), 3, List.of(), Map.of());
        var mountainDwarf = new CharacterRace("dndturn:mountain_dwarf", 1, "Mountain Dwarf", 25, "medium",
                Map.of(Ability.CON, 2, Ability.STR, 2), Map.of());
        var state = CharacterCreation.create(fighter, mountainDwarf, new AbilityScores(15, 15, 14, 10, 8, 8), null);
        assertEquals(17, state.abilities().strength());
        assertEquals(16, state.abilities().constitution());
        assertEquals(15, state.abilities().dexterity());
        assertEquals(1, state.progression().level());
        assertEquals(0, state.progression().experience());
        assertEquals(13, CharacterCreation.maxHp(fighter, state));
    }

    @Test
    void pageAllocationUsesSubraceSlotsAndExactClassOwnership() {
        var definition = new CharacterClass("example:cleric", 1, "Cleric", 8, Set.of(),
            Set.of(3), 1, List.of("example:life"), Map.of());
        var race = new CharacterRace("example:elf", 1, "Elf", 30, "medium", Map.of(Ability.DEX, 2),
            Map.of("example:high", List.of(2, 1)));
        var bought = new AbilityScores(15, 15, 14, 10, 8, 8);
        var targets = List.of(Ability.CON, Ability.INT);
        var page = CharacterCreation.createFromScreen(definition, race, bought, targets, "example:high", "example:life");
        assertEquals(16, page.abilities().constitution());
        assertEquals(11, page.abilities().intelligence());
        assertEquals(17, CharacterCreation.create(definition, race, bought, null).abilities().dexterity());
        assertThrows(IllegalArgumentException.class, () -> CharacterCreation.createFromScreen(definition, race,
            bought, targets, "example:high", "dndturn:life"));
        assertThrows(IllegalArgumentException.class, () -> CharacterCreation.createFromScreen(definition, race,
            bought, List.of(Ability.INT, Ability.INT), "example:high", null));
    }

    @Test
    void savingThrowAddsProficiencyAndDoesNotAutoPassOnNaturalTwenty() {
        var fighter = new CharacterClass("dndturn:fighter", 1, "Fighter", 10,
                Set.of(Ability.STR, Ability.CON), Set.of(), 3, List.of(), Map.of());
        var race = new CharacterRace("dndturn:human", 1, "Human", 30, "medium", Map.of(), Map.of());
        var state = CharacterCreation.create(fighter, race, new AbilityScores(15, 15, 14, 10, 8, 8), null);
        int bonus = CharacterRules.savingThrowBonus(fighter, state, Ability.CON);
        assertEquals(4, bonus); // +2 CON, +2 proficiency
        var low = SavingThrow.resolve(1, 1, cc.sighs.dndturn.domain.resolution.CombatRules.RollMode.NORMAL, bonus, 10);
        assertFalse(low.success());
        assertEquals(5, low.total());
        var high = SavingThrow.resolve(20, 20, cc.sighs.dndturn.domain.resolution.CombatRules.RollMode.NORMAL, bonus, 25);
        assertFalse(high.success()); // 24 < 25; a natural 20 is not an automatic save success
        assertEquals(24, high.total());
        assertEquals(8 + 2 + 2, CharacterRules.spellSaveDc(fighter, state, Ability.CON));
    }
}
