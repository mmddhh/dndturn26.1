package cc.sighs.dndturn.domain.ai;

import java.util.*;

/** Encounter-side decision memory. No rule resources or platform objects. */
public record AiRuntimeState(UUID actor, UUID instance, long round, int count, UUID operation, AiMemory memory) {
    public AiRuntimeState(UUID actor, UUID instance, long round, int count, UUID operation) {
        this(actor, instance, round, count, operation, AiMemory.empty());
    }
    public record Waiting(long until, int total) {
        public Waiting { if (total < 1 || total > 100) throw new IllegalArgumentException("AI wait bound"); }
    }
    public AiRuntimeState {
        Objects.requireNonNull(actor); Objects.requireNonNull(instance);
        Objects.requireNonNull(memory);
        if (count < 0 || count > 8) throw new IllegalArgumentException("decision bound");
    }
    public AiRuntimeState submitted(UUID operation) {
        return new AiRuntimeState(actor, instance, round, count + 1, Objects.requireNonNull(operation), memory);
    }
}
