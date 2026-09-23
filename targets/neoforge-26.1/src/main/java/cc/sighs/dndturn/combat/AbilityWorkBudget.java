package cc.sighs.dndturn.combat;

import java.util.*;

/** Server-thread work accounting. These counters never represent gameplay resources. */
public final class AbilityWorkBudget {
    public enum Work {
        QUERY(16, 64, 256), CANDIDATE(1024, 4096, 16384),
        PROBE(262144, 524288, 2097152), AI(1, 1, 32);
        final int actorLimit, sessionLimit, totalLimit;
        Work(int actor, int session, int total) { actorLimit = actor; sessionLimit = session; totalLimit = total; }
    }
    private long tick = Long.MIN_VALUE;
    private final Map<UUID, int[]> actors = new HashMap<>(), sessions = new HashMap<>();
    private final int[] total = new int[Work.values().length];
    private final long[] consumed = new long[Work.values().length], deferred = new long[Work.values().length];
    public boolean take(long now, UUID actor, UUID session, Work work) {
        if (now < tick) throw new IllegalArgumentException("work clock moved backwards");
        if (now != tick) { tick = now; actors.clear(); sessions.clear(); Arrays.fill(total, 0); }
        int k = work.ordinal();
        // Do not allocate an entry for rejected high-cardinality requests.
        int[] a = actors.get(actor), s = sessions.get(session);
        if (total[k] >= work.totalLimit || a != null && a[k] >= work.actorLimit
            || s != null && s[k] >= work.sessionLimit) { deferred[k]++; return false; }
        actors.computeIfAbsent(actor, ignored -> new int[total.length])[k]++;
        sessions.computeIfAbsent(session, ignored -> new int[total.length])[k]++;
        total[k]++; consumed[k]++; return true;
    }
    public void require(long now, UUID actor, UUID session, Work work) {
        if (!take(now, actor, session, work)) throw new ActionFailure(ActionFailure.Code.EVALUATION_DEFERRED,
            ActionFailure.Retry.REFRESH_AND_REPROPOSE, "ability evaluation budget exhausted; retry after a server tick");
    }
    public record Counts(long consumed, long deferred) {}
    public Map<Work, Counts> counters() {
        var result = new EnumMap<Work, Counts>(Work.class);
        for (var work : Work.values()) result.put(work, new Counts(consumed[work.ordinal()], deferred[work.ordinal()]));
        return Map.copyOf(result);
    }
}
