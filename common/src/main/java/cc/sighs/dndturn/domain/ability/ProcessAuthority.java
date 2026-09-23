package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import java.util.*;

/** Single writer of semantic process state. It cannot admit an encounter operation or spend a resource. */
public final class ProcessAuthority {
    private final Map<UUID, ProcessState> states = new LinkedHashMap<>();
    public Map<UUID, ProcessState> snapshot() { return Map.copyOf(states); }
    public ProcessState find(UUID id) { return states.get(id); }
    public ProcessState require(UUID id) { return Objects.requireNonNull(states.get(id), "unknown process"); }
    public ProcessState open(ProcessState state) {
        validateOpen(state);
        var previous = states.get(state.id());
        if (previous != null) return previous;
        states.put(state.id(), state); return state;
    }
    /** Pure admission check, also used before the encounter commits a paid activation. */
    public void validateOpen(ProcessState state) {
        Objects.requireNonNull(state);
        var previous = states.get(state.id());
        if (previous != null) {
            if (!previous.equals(state)) throw new IllegalStateException("process identity conflict");
            return;
        }
        if (states.size() >= 16384 || state.revision() != 0 || state.steps() != 0 || state.nativePending()
                || state.terminal() != null || state.release() != ProcessState.Release.HELD)
            throw new IllegalArgumentException("process admission");
        checkControls(state, states);
    }
    private static void checkControls(ProcessState candidate, Map<UUID, ProcessState> states) {
        int active = 0;
        for (var state : states.values()) {
            if (state.id().equals(candidate.id()) || !state.owner().equals(candidate.owner())
                    || state.release() == ProcessState.Release.RELEASED) continue;
            if (++active >= 32 || !Collections.disjoint(state.definition().controls(), candidate.definition().controls()))
                throw new IllegalStateException("PROCESS_CONTROL_CONFLICT");
        }
    }
    /** All validation precedes installation; immutable snapshots and terminal gameplay facts never change. */
    public ProcessState update(ProcessState next, long expectedRevision) {
        var previous = require(next.id());
        if (previous.revision() != expectedRevision || next.revision() != Math.addExact(expectedRevision, 1)
                || !previous.definition().equals(next.definition()) || !previous.owner().equals(next.owner())
                || !previous.cause().equals(next.cause()) || !previous.root().equals(next.root())
                || !previous.encounter().equals(next.encounter()) || !previous.instance().equals(next.instance())
                || previous.roundTicks() != next.roundTicks() || next.steps() < previous.steps()
                || next.steps() > previous.steps() + 1 || next.entropy().seed() != previous.entropy().seed()
                || next.nextBoundary() < previous.nextBoundary()
                || next.entropy().cursor() < previous.entropy().cursor()
                || !previous.phase().equals(next.phase()) && !previous.definition().transitions().get(previous.phase()).contains(next.phase())
                || previous.cancellationRequested() && !next.cancellationRequested()
                || previous.terminal() != null
                || previous.release() == ProcessState.Release.FAILED && next.release() == ProcessState.Release.HELD
                || previous.release() == ProcessState.Release.RELEASED
                    && (next.steps() != previous.steps() || !next.values().equals(previous.values())
                        || !next.entropy().equals(previous.entropy()) || next.nativePending())
                || previous.release() == ProcessState.Release.RELEASED && next.release() != ProcessState.Release.RELEASED)
            throw new IllegalStateException("invalid process transition");
        states.put(next.id(), next); return next;
    }
    public void restore(Map<UUID, ProcessState> saved) {
        if (!states.isEmpty() || saved.size() > 16384) throw new IllegalStateException("process restore bound");
        var candidate = new LinkedHashMap<UUID, ProcessState>();
        saved.forEach((id, state) -> {
            if (!id.equals(state.id())) throw new IllegalArgumentException("process restore identity");
            if (state.release() != ProcessState.Release.RELEASED) checkControls(state, candidate);
            candidate.put(id, state);
        });
        states.putAll(candidate);
    }
    public ProcessState revise(UUID id, String phase, Map<String, String> values, long steps, long next,
            ProcessState.Entropy entropy, boolean cancel, boolean nativePending,
            OperationRecord.Outcome terminal, ProcessState.Release release, String reason) {
        var old = require(id);
        return update(new ProcessState(id, old.definition(), old.owner(), old.cause(), old.root(), old.encounter(),
                old.instance(), old.roundTicks(), Math.addExact(old.revision(), 1), phase, values, steps, next,
                entropy, cancel, nativePending, terminal, release, reason), old.revision());
    }
}
