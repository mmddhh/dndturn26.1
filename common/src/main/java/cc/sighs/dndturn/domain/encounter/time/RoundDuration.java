package cc.sighs.dndturn.domain.encounter.time;

import java.util.Objects;

/** A process keeps its own conversion base across encounter merges. -1 denotes infinity. */
public record RoundDuration(RoundTime time, int rounds) {
    public RoundDuration {
        Objects.requireNonNull(time);
        if (rounds < -1) throw new IllegalArgumentException("invalid remaining rounds");
    }
    public static RoundDuration capture(RoundTime time, int ticks) {
        if (ticks < -1) throw new IllegalArgumentException("invalid native duration");
        return new RoundDuration(time, ticks == -1 ? -1 : time.durationRounds(ticks));
    }
    public boolean active() { return rounds != 0; }
    public RoundDuration endTurn() { return new RoundDuration(time, rounds > 0 ? rounds - 1 : rounds); }
    public long remainingTicks() { return rounds == -1 ? -1 : (long) rounds * time.ticks(); }
    /** Native timers are signed ints. Only the representation saturates; the round value does not. */
    public int nativeTicks() { return (int) Math.min(Integer.MAX_VALUE, remainingTicks()); }
}
