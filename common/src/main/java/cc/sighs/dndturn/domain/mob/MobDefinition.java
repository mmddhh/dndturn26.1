package cc.sighs.dndturn.domain.mob;

import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.AbilityScores;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Authoritative monster design values except HP/AC: those remain native Minecraft values
 * (1:1 max health and armor). Speed values are captured tick budgets, not feet.
 */
public record MobDefinition(String id, int version, AbilityScores abilities,
                            Map<Ability, Integer> saveBonuses,
                            Map<MovementMode, Integer> speedTicks,
                            Set<DamageType> resistances, Set<DamageType> immunities,
                            Set<DamageType> vulnerabilities, Set<String> conditionImmunities,
                            List<String> traits, List<MobAction> actions,
                            int multiattack, int rechargeMin, int legendaryResistance) {
    public MobDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(abilities);
        saveBonuses = Map.copyOf(saveBonuses);
        speedTicks = Map.copyOf(speedTicks);
        resistances = Set.copyOf(resistances);
        immunities = Set.copyOf(immunities);
        vulnerabilities = Set.copyOf(vulnerabilities);
        conditionImmunities = Set.copyOf(conditionImmunities);
        traits = List.copyOf(traits);
        actions = List.copyOf(actions);
        if (id.isBlank() || id.length() > 128 || version < 1 || multiattack < 0 || multiattack > 20
            || rechargeMin < 0 || rechargeMin > 6 || legendaryResistance < 0 || legendaryResistance > 10
            || traits.size() > 64 || actions.size() > 64)
            throw new IllegalArgumentException("mob definition bounds");
        for (var entry : speedTicks.entrySet())
            if (entry.getValue() < 0 || entry.getValue() > 10_000)
                throw new IllegalArgumentException("mob speed ticks");
    }

    public int speed(MovementMode mode) {
        return speedTicks.getOrDefault(mode, 0);
    }

    /** Unknown third-party mobs still participate: 10s in every ability, 30-tick ground speed, 10 bludgeoning. */
    public static MobDefinition fallback(String entityId) {
        return new MobDefinition(entityId, 1,
            new AbilityScores(10, 10, 10, 10, 10, 10), Map.of(),
            Map.of(MovementMode.GROUND, 30), Set.of(), Set.of(), Set.of(), Set.of(),
            List.of(), List.of(new MobAction("melee", MobAction.Kind.ATTACK, 5, 3, 0,
                List.of(new DamageComponent(0, 0, 10, DamageType.BLUDGEONING)),
                null, 0, false, null, 0, 0, 0, null, 0)), 1, 0, 0);
    }
}
