package cc.sighs.dndturn.domain.resolution;

import java.util.Objects;
import java.util.Random;

/** A native-mob attack profile: hit bonus, damage dice and an informational damage type. */
public record AttackProfile(int attackBonus, int diceCount, int diceSides, int flatBonus, String damageType) {
    public AttackProfile {
        if (attackBonus < -20 || attackBonus > 50) throw new IllegalArgumentException("attack bonus");
        if (diceCount < 0 || diceCount > 20 || diceSides < 0 || diceSides > 100
            || diceCount > 0 && diceSides < 2 || flatBonus < 0 || flatBonus > 10_000)
            throw new IllegalArgumentException("attack damage dice");
        Objects.requireNonNull(damageType);
    }

    public int roll(Random random) {
        Objects.requireNonNull(random);
        int total = flatBonus;
        for (int i = 0; i < diceCount; i++) total = Math.addExact(total, 1 + random.nextInt(diceSides));
        return total;
    }
}
