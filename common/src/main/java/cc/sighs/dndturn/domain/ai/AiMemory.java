package cc.sighs.dndturn.domain.ai;

import java.util.*;

/** Bounded semantic knowledge, aged in encounter rounds, never in real waiting time. */
public record AiMemory(Map<UUID, Entry> observations) {
    public record Entry(PerceptionSnapshot.ObservedActor observation, long observedRound, long expiresRound,
                        long encounterRevision) {
        public Entry {
            Objects.requireNonNull(observation);
            if (!observation.visible() || observation.evidence().lastKnownPosition() == null || observedRound < 0
                    || expiresRound < observedRound || expiresRound - observedRound > 32 || encounterRevision < 0)
                throw new IllegalArgumentException("memory provenance or expiry");
        }
    }
    public AiMemory {
        observations = Map.copyOf(observations);
        if (observations.size() > 128 || observations.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().observation().id())))
            throw new IllegalArgumentException("semantic memory bounds");
    }
    public static AiMemory empty() { return new AiMemory(Map.of()); }
    public AiMemory remember(PerceptionSnapshot perception, long round, long revision) {
        var next = new TreeMap<UUID, Entry>();
        observations.forEach((id, entry) -> { if (round >= entry.observedRound() && round <= entry.expiresRound()) next.put(id, entry); });
        for (var actor : perception.actors()) if (actor.visible() && actor.evidence().lastKnownPosition() != null) {
            if (!next.containsKey(actor.id()) && next.size() == 128) {
                var oldest = next.entrySet().stream().min(Comparator.<Map.Entry<UUID, Entry>>comparingLong(e -> e.getValue().observedRound())
                        .thenComparing(Map.Entry::getKey)).orElseThrow();
                next.remove(oldest.getKey());
            }
            next.put(actor.id(), new Entry(actor, round, Math.addExact(round, 2), revision));
        }
        return new AiMemory(next);
    }
    public Entry known(UUID subject, long round) {
        var value = observations.get(subject);
        return value != null && round >= value.observedRound() && round <= value.expiresRound() ? value : null;
    }
}
