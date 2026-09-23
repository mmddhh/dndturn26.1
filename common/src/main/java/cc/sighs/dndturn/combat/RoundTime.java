package cc.sighs.dndturn.combat;

/** Captured conversion policy; neither wall time nor a second resource balance. */
public record RoundTime(int ticks) {
    public static final int DEFAULT_TICKS = 30;
    public RoundTime {
        if (ticks < 1 || ticks > 1_000_000) throw new IllegalArgumentException("roundTicks out of range");
    }
    public int durationRounds(int remainingTicks) {
        if (remainingTicks < 0) throw new IllegalArgumentException("negative duration");
        return remainingTicks / ticks + (remainingTicks % ticks == 0 ? 0 : 1);
    }
    public int completeChargeTicks(int elapsedTicks) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("negative charge");
        return elapsedTicks / ticks * ticks;
    }
    public int realtimeTicks(int remainingRounds) {
        if (remainingRounds < 0) throw new IllegalArgumentException("negative rounds");
        return Math.multiplyExact(remainingRounds, ticks);
    }
    public double periodicAmount(double nativeAmount, int nativePeriod) {
        if (!Double.isFinite(nativeAmount) || nativeAmount < 0 || nativePeriod < 1)
            throw new IllegalArgumentException("invalid periodic effect");
        double amount = nativeAmount * ((double) ticks / nativePeriod);
        if (!Double.isFinite(amount)) throw new ArithmeticException("periodic amount overflow");
        return amount;
    }
}
