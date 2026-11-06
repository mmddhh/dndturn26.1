package cc.sighs.dndturn.domain.character;

/** PHB 27-point ability buy. Character creation must spend the whole budget. */
public final class PointBuy {
    public static final int BUDGET = 27;
    public static final int MIN_SCORE = 8;
    public static final int MAX_SCORE = 15;

    private PointBuy() {}

    public static int cost(int score) {
        if (score < MIN_SCORE || score > MAX_SCORE) return -1;
        return switch (score) {
            case 8 -> 0;
            case 9 -> 1;
            case 10 -> 2;
            case 11 -> 3;
            case 12 -> 4;
            case 13 -> 5;
            case 14 -> 7;
            case 15 -> 9;
            default -> -1;
        };
    }

    public static int totalCost(AbilityScores scores) {
        int total = 0;
        for (Ability ability : Ability.values()) {
            int cost = cost(scores.score(ability));
            if (cost < 0) throw new IllegalArgumentException("score outside point buy range");
            total = Math.addExact(total, cost);
        }
        return total;
    }

    public static boolean isValid(AbilityScores scores) {
        try {
            return totalCost(scores) == BUDGET;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
