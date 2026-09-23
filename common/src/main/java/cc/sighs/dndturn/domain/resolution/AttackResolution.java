package cc.sighs.dndturn.domain.resolution;

import cc.sighs.dndturn.domain.fact.FactSlice;
import java.util.Objects;

/** Pure damage plan. Dice are captured by the operation owner only after admission. */
public final class AttackResolution {
    private AttackResolution() {}
    public record Dice(int first, int second) {
        public Dice {
            if (first < 1 || first > 20 || second < 1 || second > 20) throw new IllegalArgumentException("d20 evidence");
        }
    }
    public record Defense(int armorClass, int reduction) {
        public Defense { if (armorClass < 0 || reduction < 0) throw new IllegalArgumentException("defense values"); }
    }
    public record Input(String ruleset, Defense defense, double baseDamage, CombatRules.RollMode mode, Dice dice) {
        public Input {
            if (!CombatRules.RULES_REVISION.equals(ruleset)) throw new IllegalArgumentException("attack ruleset unavailable");
            Objects.requireNonNull(defense); Objects.requireNonNull(mode); Objects.requireNonNull(dice);
            validateDamage(baseDamage, defense);
            if (mode == CombatRules.RollMode.NORMAL && dice.first() != dice.second()) throw new IllegalArgumentException("normal roll evidence");
        }
    }
    public record Plan(Input input, CombatRules.AttackRoll roll, int damage) {}
    public static Defense defense(FactSlice facts) { return new Defense(RuleResolver.armorClass(facts), RuleResolver.reduction(facts)); }
    public static void validateDamage(double baseDamage, Defense defense) {
        CombatRules.damageAfterReduction(baseDamage, defense.reduction(), true);
    }
    public static Plan resolve(Input input) {
        int first = input.dice().first(), second = input.dice().second();
        var roll = CombatRules.rollAttack(first, second, input.mode(), input.defense().armorClass());
        return new Plan(input, roll, roll.hit() ? CombatRules.damageAfterReduction(input.baseDamage(), input.defense().reduction(), roll.critical()) : 0);
    }
}
