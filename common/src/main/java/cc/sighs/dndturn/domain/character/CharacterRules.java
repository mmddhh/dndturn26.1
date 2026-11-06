package cc.sighs.dndturn.domain.character;

/** Pure player-only rules. Native HP/AC remain authoritative for every non-player body. */
public final class CharacterRules {
    public static final int SHIELD_AC_BONUS = 2;

    private CharacterRules() {}

    /**
     * 5e hit-point expectation without rolling at level-up:
     * level 1 takes the die maximum, later levels take (dieSize + 1) / 2.
     * The CON modifier applies to every level and the sum is rounded once.
     */
    public static int expectedMaxHp(int hitDie, int constitutionModifier, int level) {
        if (hitDie < 2 || hitDie > 100) throw new IllegalArgumentException("hit die");
        if (level < 1 || level > DndLeveling.MAX_LEVEL) throw new IllegalArgumentException("level");
        double total = hitDie + constitutionModifier;
        total += (level - 1) * ((hitDie + 1) / 2.0 + constitutionModifier);
        return Math.max(1, (int) Math.round(total));
    }

    /** Player AC: unarmored 10 + DEX, light full DEX, medium max +2 DEX, heavy no DEX, shield +2. */
    public static int armorClass(ArmorCategory category, int baseArmorClass, int dexterityModifier, boolean shield) {
        if (category == null) throw new IllegalArgumentException("armor category");
        if (baseArmorClass < 0) throw new IllegalArgumentException("base armor class");
        int value = switch (category) {
            case NONE -> 10 + dexterityModifier;
            case LIGHT -> baseArmorClass + dexterityModifier;
            case MEDIUM -> baseArmorClass + Math.min(dexterityModifier, 2);
            case HEAVY -> baseArmorClass;
        };
        return Math.max(0, value + (shield ? SHIELD_AC_BONUS : 0));
    }

    public static int savingThrowBonus(CharacterClass characterClass, CharacterState character, Ability ability) {
        if (characterClass == null || character == null || ability == null)
            throw new IllegalArgumentException("saving throw source");
        return character.modifier(ability) + (characterClass.proficientInSave(ability)
            ? character.proficiencyBonus() : 0);
    }

    public static int spellSaveDc(CharacterClass characterClass, CharacterState character, Ability ability) {
        if (characterClass == null || character == null || ability == null)
            throw new IllegalArgumentException("spell save source");
        return 8 + character.proficiencyBonus() + character.modifier(ability);
    }
}
