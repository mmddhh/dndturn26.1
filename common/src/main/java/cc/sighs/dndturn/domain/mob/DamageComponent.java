package cc.sighs.dndturn.domain.mob;

import java.util.Objects;

/** One independently resisted damage component. */
public record DamageComponent(int dice, int sides, int flatBonus, DamageType type) {
    public DamageComponent {
        if (dice < 0 || dice > 20 || sides < 0 || sides > 100
            || dice > 0 && sides < 2 || flatBonus < 0 || flatBonus > 10_000)
            throw new IllegalArgumentException("damage component");
        Objects.requireNonNull(type);
    }

    public int roll(java.util.Random random) {
        Objects.requireNonNull(random);
        int total = flatBonus;
        for (int index = 0; index < dice; index++) total = Math.addExact(total, 1 + random.nextInt(sides));
        return total;
    }
}
