package cc.sighs.dndturn.domain.character;

/** Pure 5e level, experience and proficiency rules. */
public final class DndLeveling {
    public static final int MAX_LEVEL = 20;
    private static final int[] XP_THRESHOLDS = {
            0, 300, 900, 2_700, 6_500, 14_000, 23_000, 34_000, 48_000, 64_000,
            85_000, 100_000, 120_000, 140_000, 165_000, 195_000, 225_000, 265_000, 305_000, 355_000
    };

    private DndLeveling() {}

    public static int clampLevel(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    public static int clampExperience(int experience) {
        return Math.max(0, experience);
    }

    public static int addExperience(int current, int gained) {
        if (gained <= 0) return clampExperience(current);
        long total = (long) clampExperience(current) + gained;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    public static int levelForExperience(int experience) {
        int normalized = clampExperience(experience);
        int level = 1;
        for (int index = 0; index < XP_THRESHOLDS.length; index++) {
            if (normalized < XP_THRESHOLDS[index]) break;
            level = index + 1;
        }
        return level;
    }

    public static int experienceForLevel(int level) {
        return XP_THRESHOLDS[clampLevel(level) - 1];
    }

    public static int proficiencyBonus(int level) {
        return 2 + (clampLevel(level) - 1) / 4;
    }
}
