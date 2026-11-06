package cc.sighs.dndturn.domain.mob;

import java.util.Set;

/** Per-actor damage defenses from the monster design document. */
public record DefenseProfile(Set<DamageType> resistances, Set<DamageType> immunities,
                             Set<DamageType> vulnerabilities, boolean magical) {
    public DefenseProfile {
        resistances = Set.copyOf(resistances);
        immunities = Set.copyOf(immunities);
        vulnerabilities = Set.copyOf(vulnerabilities);
    }

    public int apply(DamageType type, int amount) {
        if (amount < 0) throw new IllegalArgumentException("negative damage");
        if (immunities.contains(type)) return 0;
        int result = amount;
        if (resistances.contains(type) && vulnerabilities.contains(type)) result = result / 2 * 2;
        else if (resistances.contains(type)) result /= 2;
        else if (vulnerabilities.contains(type)) result = Math.multiplyExact(result, 2);
        return result;
    }
}
