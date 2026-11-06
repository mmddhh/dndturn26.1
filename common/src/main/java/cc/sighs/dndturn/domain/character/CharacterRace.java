package cc.sighs.dndturn.domain.character;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Content definition of a player race or subrace. Boosts are applied once at creation. */
public record CharacterRace(String id, int version, String name, int speed, String size,
                            Map<Ability, Integer> abilityBoosts) {
    public CharacterRace {
        requireId(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(size);
        abilityBoosts = Map.copyOf(abilityBoosts);
        if (version < 1 || name.isBlank() || name.length() > 128 || speed < 0 || speed > 1_000
                || size.isBlank() || size.length() > 32 || abilityBoosts.size() > Ability.values().length)
            throw new IllegalArgumentException("character race");
        abilityBoosts.forEach((ability, amount) -> {
            Objects.requireNonNull(ability);
            if (amount < -10 || amount > 10) throw new IllegalArgumentException("racial ability boost");
        });
    }

    public AbilityScores apply(AbilityScores base) {
        Objects.requireNonNull(base);
        var values = new EnumMap<Ability, Integer>(Ability.class);
        for (Ability ability : Ability.values()) values.put(ability, base.score(ability));
        abilityBoosts.forEach((ability, amount) -> values.put(ability,
                Math.max(AbilityScores.MIN_SCORE, Math.min(20, values.get(ability) + amount))));
        return new AbilityScores(values.get(Ability.STR), values.get(Ability.DEX), values.get(Ability.CON),
                values.get(Ability.INT), values.get(Ability.WIS), values.get(Ability.CHA));
    }

    private static void requireId(String id) {
        Objects.requireNonNull(id);
        if (id.isBlank() || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("race id");
    }
}
