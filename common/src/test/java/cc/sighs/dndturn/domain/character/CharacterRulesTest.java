package cc.sighs.dndturn.domain.character;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterRulesTest {
    @Test
    void expectedHitPointsUseDieMaximumAtOneAndExpectationAfter() {
        assertEquals(12, CharacterRules.expectedMaxHp(10, 2, 1));
        // 12 + (5.5 + 2) at level 2 = 19.5 -> 20
        assertEquals(20, CharacterRules.expectedMaxHp(10, 2, 2));
        // 12 + 3 * 7.5 = 34.5 -> 35
        assertEquals(35, CharacterRules.expectedMaxHp(10, 2, 4));
        // 9 + 2 * (5.5 - 1) = 18
        assertEquals(18, CharacterRules.expectedMaxHp(10, -1, 3));
    }

    @Test
    void armorClassUsesCategoryDexCapAndShield() {
        assertEquals(15, CharacterRules.armorClass(ArmorCategory.NONE, 0, 5, false));
        assertEquals(17, CharacterRules.armorClass(ArmorCategory.NONE, 0, 5, true));
        assertEquals(17, CharacterRules.armorClass(ArmorCategory.LIGHT, 12, 5, false));
        assertEquals(16, CharacterRules.armorClass(ArmorCategory.MEDIUM, 14, 5, false));
        assertEquals(18, CharacterRules.armorClass(ArmorCategory.HEAVY, 18, 5, false));
        assertEquals(20, CharacterRules.armorClass(ArmorCategory.HEAVY, 18, 5, true));
    }

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
    void levelingUsesFifthEditionThresholdsAndProficiency() {
        assertEquals(1, DndLeveling.levelForExperience(0));
        assertEquals(1, DndLeveling.levelForExperience(299));
        assertEquals(2, DndLeveling.levelForExperience(300));
        assertEquals(20, DndLeveling.levelForExperience(355_000));
        assertEquals(2, DndLeveling.proficiencyBonus(1));
        assertEquals(3, DndLeveling.proficiencyBonus(5));
        assertEquals(6, DndLeveling.proficiencyBonus(17));
    }

    @Test
    void creationAppliesRacialBoostsAfterPointBuy() {
        var fighter = new CharacterClass("dndturn:fighter", 1, "Fighter", 10,
                java.util.Set.of(Ability.STR, Ability.CON));
        var mountainDwarf = new CharacterRace("dndturn:mountain_dwarf", 1, "Mountain Dwarf", 25, "medium",
                java.util.Map.of(Ability.CON, 2, Ability.STR, 2));
        var state = CharacterCreation.create(fighter, mountainDwarf, new AbilityScores(15, 15, 14, 10, 8, 8), null);
        assertEquals(17, state.abilities().strength());
        assertEquals(16, state.abilities().constitution());
        assertEquals(15, state.abilities().dexterity());
        assertEquals(1, state.progression().level());
        assertEquals(0, state.progression().experience());
        assertEquals(13, CharacterCreation.maxHp(fighter, state));
    }

    @Test
    void savingThrowAddsProficiencyAndDoesNotAutoPassOnNaturalTwenty() {
        var fighter = new CharacterClass("dndturn:fighter", 1, "Fighter", 10,
                java.util.Set.of(Ability.STR, Ability.CON));
        var race = new CharacterRace("dndturn:human", 1, "Human", 30, "medium", java.util.Map.of());
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
