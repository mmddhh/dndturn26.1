package cc.sighs.dndturn.domain.character;

import java.util.Objects;

/** Player-only character state. Non-players keep an empty state; creation is explicit. */
public record CharacterState(Progression progression, AbilityScores abilities) {
    public CharacterState {
        if ((progression == null) != (abilities == null))
            throw new IllegalArgumentException("incomplete character");
    }

    public static CharacterState empty() { return new CharacterState(null, null); }

    public boolean created() { return progression != null; }

    public int modifier(Ability ability) {
        return requireAbilities().modifier(Objects.requireNonNull(ability));
    }

    public int proficiencyBonus() { return requireProgression().proficiencyBonus(); }

    public CharacterState withProgression(Progression value) {
        return new CharacterState(Objects.requireNonNull(value), requireAbilities());
    }

    public CharacterState withAbilities(AbilityScores value) {
        return new CharacterState(requireProgression(), Objects.requireNonNull(value));
    }

    private Progression requireProgression() {
        if (progression == null) throw new IllegalStateException("character has not been created");
        return progression;
    }

    private AbilityScores requireAbilities() {
        if (abilities == null) throw new IllegalStateException("character has not been created");
        return abilities;
    }
}
