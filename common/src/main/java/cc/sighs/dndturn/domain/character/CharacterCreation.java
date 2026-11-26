package cc.sighs.dndturn.domain.character;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.EnumMap;
import java.util.Objects;

/** Pure creation rule shared by the server command and the character-creation screen. */
public final class CharacterCreation {
    private CharacterCreation() {}

    /** Validates the 27-point buy, applies racial boosts and creates a level-1 character. */
    public static CharacterState create(CharacterClass characterClass, CharacterRace race,
                                        AbilityScores boughtScores, String subclassId) {
        return resolve(characterClass, race, boughtScores, race.abilityBoosts(), null, subclassId);
    }

    public static CharacterState createFromScreen(CharacterClass characterClass, CharacterRace race,
            AbilityScores boughtScores, List<Ability> targets, String subraceId, String subclassId) {
        var slots = race.subraces().get(subraceId);
        if (slots == null || targets.size() != slots.size())
            throw new IllegalArgumentException("invalid subrace allocation");
        var boosts = new EnumMap<Ability,Integer>(Ability.class);
        for (int i = 0; i < targets.size(); i++)
            if (boosts.put(targets.get(i), slots.get(i)) != null)
                throw new IllegalArgumentException("duplicate allocation target");
        return resolve(characterClass, race, boughtScores, boosts, subraceId, subclassId);
    }

    private static CharacterState resolve(CharacterClass definition, CharacterRace race, AbilityScores bought,
            Map<Ability,Integer> boosts, String subrace, String subclass) {
        if (!PointBuy.isValid(bought)) throw new IllegalArgumentException("point buy must spend exactly 27 points");
        if (subclass != null && (definition.subclassLevel() > 1 || !definition.subclasses().contains(subclass)))
            throw new IllegalArgumentException("invalid starting subclass");
        var scores = subrace == null ? race.apply(bought) : bought;
        if (subrace != null) for (var boost : boosts.entrySet()) {
            scores = scores.plus(boost.getKey(), boost.getValue());
            if (scores.score(boost.getKey()) > 20) throw new IllegalArgumentException("ability cap");
        }
        return new CharacterState(new Progression(race.id(), subrace, definition.id(), subclass,
            1, 0, 0, Set.of()), scores);
    }

    public static int maxHp(CharacterClass characterClass, CharacterState character) {
        Objects.requireNonNull(characterClass);
        Objects.requireNonNull(character);
        return CharacterRules.expectedMaxHp(characterClass.hitDie(),
                character.modifier(Ability.CON), character.progression().level());
    }
}
