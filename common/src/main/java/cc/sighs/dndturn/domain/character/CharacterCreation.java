package cc.sighs.dndturn.domain.character;

import java.util.Objects;

/** Pure creation rule shared by the server command and the future character-creation screen. */
public final class CharacterCreation {
    private CharacterCreation() {}

    /** Validates the 27-point buy, applies racial boosts and creates a level-1 character. */
    public static CharacterState create(CharacterClass characterClass, CharacterRace race,
                                        AbilityScores boughtScores, String subclassId) {
        Objects.requireNonNull(characterClass);
        Objects.requireNonNull(race);
        Objects.requireNonNull(boughtScores);
        if (!PointBuy.isValid(boughtScores)) throw new IllegalArgumentException("point buy must spend exactly 27 points");
        if (subclassId != null && !subclassId.isBlank()
                && (!subclassId.startsWith("dndturn:") || subclassId.length() > 128))
            throw new IllegalArgumentException("subclass id");
        AbilityScores finalScores = race.apply(boughtScores);
        Progression progression = new Progression(race.id(), null, characterClass.id(),
                subclassId == null ? null : subclassId, 1, 0, 0, java.util.Set.of());
        return new CharacterState(progression, finalScores);
    }

    /** Creation from the classes page: the page already selected the complete racial bonus multiset. */
    public static CharacterState createFromScreen(CharacterClass characterClass, CharacterRace race,
                                                  AbilityScores boughtScores, java.util.Map<Ability, Integer> racialBonuses,
                                                  String subraceId, String subclassId) {
        Objects.requireNonNull(characterClass);
        Objects.requireNonNull(race);
        Objects.requireNonNull(boughtScores);
        Objects.requireNonNull(racialBonuses);
        if (!PointBuy.isValid(boughtScores)) throw new IllegalArgumentException("point buy must spend exactly 27 points");
        if (subraceId != null && !subraceId.isBlank()
                && (!subraceId.startsWith("dndturn:") || subraceId.length() > 128))
            throw new IllegalArgumentException("subrace id");
        if (subclassId != null && !subclassId.isBlank()
                && (!subclassId.startsWith("dndturn:") || subclassId.length() > 128))
            throw new IllegalArgumentException("subclass id");
        AbilityScores finalScores = boughtScores;
        for (var entry : racialBonuses.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 3)
                throw new IllegalArgumentException("racial bonus");
            finalScores = finalScores.plus(entry.getKey(), entry.getValue());
            if (finalScores.score(entry.getKey()) > 20) throw new IllegalArgumentException("racial bonus cap");
        }
        Progression progression = new Progression(race.id(), subraceId == null || subraceId.isBlank() ? null : subraceId,
                characterClass.id(), subclassId == null || subclassId.isBlank() ? null : subclassId,
                1, 0, 0, java.util.Set.of());
        return new CharacterState(progression, finalScores);
    }

    public static int maxHp(CharacterClass characterClass, CharacterState character) {
        Objects.requireNonNull(characterClass);
        Objects.requireNonNull(character);
        return CharacterRules.expectedMaxHp(characterClass.hitDie(),
                character.modifier(Ability.CON), character.progression().level());
    }
}
