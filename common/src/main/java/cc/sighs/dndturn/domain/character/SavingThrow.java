package cc.sighs.dndturn.domain.character;

import cc.sighs.dndturn.domain.resolution.CombatRules;
import java.util.Objects;

/** Captured d20 check/save interpretation. Unlike attacks, a natural 1/20 does not auto-fail/pass a save. */
public final class SavingThrow {
    private SavingThrow() {}

    public record Result(int first, int second, int die, int total, boolean success) {}

    public static Result resolve(int first, int second, CombatRules.RollMode mode, int bonus, int dc) {
        Objects.requireNonNull(mode);
        if (first < 1 || first > 20 || second < 1 || second > 20 || dc < 0)
            throw new IllegalArgumentException("saving throw evidence");
        if (mode == CombatRules.RollMode.NORMAL && first != second)
            throw new IllegalArgumentException("normal roll evidence");
        int die = switch (mode) {
            case NORMAL -> first;
            case ADVANTAGE -> Math.max(first, second);
            case DISADVANTAGE -> Math.min(first, second);
        };
        int total = Math.addExact(die, bonus);
        return new Result(first, second, die, total, total >= dc);
    }
}
