package mdh.dndturn.util;

import net.minecraft.util.RandomSource;

public final class Dice {

    private Dice() {
    }

    public static int roll(RandomSource random, int sides) {
        return 1 + random.nextInt(sides);
    }

    public static int d20(RandomSource random) {
        return roll(random, 20);
    }

    public static int rollDice(RandomSource random, int count, int sides) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += roll(random, sides);
        }
        return total;
    }
}
