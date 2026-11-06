package cc.sighs.dndturn.domain.character;

import java.util.Objects;

/** Player-owned ability scores. Values outside the supported creation/advancement range are rejected. */
public record AbilityScores(int strength, int dexterity, int constitution,
                            int intelligence, int wisdom, int charisma) {
    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 30;

    public AbilityScores {
        if (strength < MIN_SCORE || strength > MAX_SCORE || dexterity < MIN_SCORE || dexterity > MAX_SCORE
                || constitution < MIN_SCORE || constitution > MAX_SCORE || intelligence < MIN_SCORE
                || intelligence > MAX_SCORE || wisdom < MIN_SCORE || wisdom > MAX_SCORE
                || charisma < MIN_SCORE || charisma > MAX_SCORE)
            throw new IllegalArgumentException("ability score range");
    }

    public int score(Ability ability) {
        Objects.requireNonNull(ability);
        return switch (ability) {
            case STR -> strength;
            case DEX -> dexterity;
            case CON -> constitution;
            case INT -> intelligence;
            case WIS -> wisdom;
            case CHA -> charisma;
        };
    }

    /** D&amp;D modifier: floor((score - 10) / 2). */
    public int modifier(Ability ability) {
        return Math.floorDiv(score(ability) - 10, 2);
    }

    public AbilityScores with(Ability ability, int value) {
        Objects.requireNonNull(ability);
        return switch (ability) {
            case STR -> new AbilityScores(value, dexterity, constitution, intelligence, wisdom, charisma);
            case DEX -> new AbilityScores(strength, value, constitution, intelligence, wisdom, charisma);
            case CON -> new AbilityScores(strength, dexterity, value, intelligence, wisdom, charisma);
            case INT -> new AbilityScores(strength, dexterity, constitution, value, wisdom, charisma);
            case WIS -> new AbilityScores(strength, dexterity, constitution, intelligence, value, charisma);
            case CHA -> new AbilityScores(strength, dexterity, constitution, intelligence, wisdom, value);
        };
    }

    public AbilityScores plus(Ability ability, int amount) {
        return with(ability, Math.addExact(score(ability), amount));
    }
}
