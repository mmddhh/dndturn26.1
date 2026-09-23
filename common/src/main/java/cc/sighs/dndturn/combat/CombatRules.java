package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.Random;

/** Version-independent rules from docs/dndrule.txt. Balancing inputs belong to callers. */
public final class CombatRules {
    /** Captured with damage results; advance when the implemented rule semantics change. */
    public static final String RULES_REVISION = "0.9";
    public static final int ATTACK_BONUS = 5;
    public static final int SHIELD_AC_BONUS = 2;
    public static final int MELEE_RANGE = 1;
    public static final int RANGED_RANGE = 6;

    private CombatRules() {}

    public static int armorClass(double armor, boolean holdingShield) {
        return Math.max(0, (int) Math.floor(armor)) + (holdingShield ? SHIELD_AC_BONUS : 0);
    }

    public static int damageReduction(double toughness) {
        return Math.max(0, (int) Math.floor(toughness));
    }

    public static int damageAfterReduction(int weaponDamage, int reduction, boolean critical) {
        return damageAfterReduction((double) weaponDamage, reduction, critical);
    }

    /** Preserve the weapon attribute fraction until reduction and critical doubling finish. */
    public static int damageAfterReduction(double weaponDamage, int reduction, boolean critical) {
        if (!Double.isFinite(weaponDamage) || weaponDamage < 0 || reduction < 0)
            throw new IllegalArgumentException("invalid tactical damage input");
        double result = Math.max(0, weaponDamage - reduction) * (critical ? 2 : 1);
        if (result > Integer.MAX_VALUE) throw new ArithmeticException("tactical damage overflow");
        return (int) Math.floor(result);
    }

    public static AttackRoll rollAttack(Random random, RollMode mode, int targetAc) {
        Objects.requireNonNull(random);
        Objects.requireNonNull(mode);
        int first = 1 + random.nextInt(20);
        int second = mode == RollMode.NORMAL ? first : 1 + random.nextInt(20);
        int die = switch (mode) {
            case NORMAL -> first;
            case ADVANTAGE -> Math.max(first, second);
            case DISADVANTAGE -> Math.min(first, second);
        };
        boolean hit = die == 20 || (die != 1 && die + ATTACK_BONUS >= targetAc);
        return new AttackRoll(first, second, die, die + ATTACK_BONUS, hit, die == 20);
    }

    public static RollMode mode(boolean advantage, boolean disadvantage) {
        if (advantage == disadvantage) return RollMode.NORMAL;
        return advantage ? RollMode.ADVANTAGE : RollMode.DISADVANTAGE;
    }

    public enum RollMode { NORMAL, ADVANTAGE, DISADVANTAGE }
    public record AttackRoll(int first, int second, int die, int total, boolean hit, boolean critical) {}
}
