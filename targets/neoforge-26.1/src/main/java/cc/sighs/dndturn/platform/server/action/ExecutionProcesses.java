package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.ability.ProcessAuthority;
import cc.sighs.dndturn.domain.ability.ProcessDefinition;
import cc.sighs.dndturn.domain.ability.ProcessState;

import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import java.util.*;

/** Runtime's semantic process owner. Platform callbacks and native control stay in the execution driver. */
public final class ExecutionProcesses {
    private final ProcessAuthority authority = new ProcessAuthority();
    private final Runnable thread;
    private final Runnable changed;
    public ExecutionProcesses(Runnable thread, Runnable changed, Map<UUID, ProcessState> restored) {
        this.thread = Objects.requireNonNull(thread); this.changed = Objects.requireNonNull(changed);
        authority.restore(restored);
    }
    public Map<UUID, ProcessState> snapshot() { thread.run(); return authority.snapshot(); }
    public ProcessState find(UUID id) { thread.run(); return authority.find(id); }
    public ProcessState require(UUID id) { thread.run(); return authority.require(id); }
    public void open(UUID id, ProcessDefinition definition, ProcessState.Owner owner, UUID cause, UUID root,
                     UUID encounter, UUID instance, int roundTicks, String phase) {
        thread.run();
        authority.open(candidate(id, definition, owner, cause, root, encounter, instance, roundTicks, phase));
        changed.run();
    }
    public void validateOpen(UUID id, ProcessDefinition definition, ProcessState.Owner owner, UUID cause, UUID root,
                             UUID encounter, UUID instance, int roundTicks, String phase) {
        thread.run();
        authority.validateOpen(candidate(id, definition, owner, cause, root, encounter, instance, roundTicks, phase));
    }
    private static ProcessState candidate(UUID id, ProcessDefinition definition, ProcessState.Owner owner,
            UUID cause, UUID root, UUID encounter, UUID instance, int roundTicks, String phase) {
        if (definition.clock() != ProcessDefinition.Clock.AUTHORIZED_EXECUTION_STEP
                || definition.recovery() != ProcessDefinition.Recovery.FAIL_UNKNOWN)
            throw new IllegalStateException("PROCESS_DRIVER_UNSUPPORTED: clock or recovery requires an installed staged driver");
        long seed = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17);
        return new ProcessState(id, definition, owner, cause, root, encounter, instance, roundTicks,
                0, phase, Map.of(), 0, 0, new ProcessState.Entropy(seed, 0), false, false, null,
                ProcessState.Release.HELD, "");
    }
    public void observe(UUID id, String phase, long steps, boolean pending, ProcessState.Release release,
                        OperationRecord.Outcome terminal, String reason) {
        var old = require(id);
        if (old.terminal() != null) return;
        authority.revise(id, phase, old.values(), steps, Math.max(steps, old.nextBoundary()), old.entropy(),
                old.cancellationRequested(), pending, terminal, release, reason);
        changed.run();
    }
    public void put(UUID id, String key, String value) {
        var old = require(id); var values = new LinkedHashMap<>(old.values());
        if (value == null) values.remove(key); else values.put(key, value);
        authority.revise(id, old.phase(), values, old.steps(), old.nextBoundary(), old.entropy(),
                old.cancellationRequested(), old.nativePending(), null, old.release(), old.reason());
        changed.run();
    }
    public long random(UUID id) {
        var old = require(id); var draw = old.entropy().draw();
        authority.revise(id, old.phase(), old.values(), old.steps(), old.nextBoundary(), draw.next(),
                old.cancellationRequested(), old.nativePending(), null, old.release(), old.reason());
        changed.run(); return draw.value();
    }
    public void requestCancel(UUID id) {
        var old = require(id);
        if (old.terminal() != null || old.cancellationRequested()) return;
        authority.revise(id, old.phase(), old.values(), old.steps(), old.nextBoundary(), old.entropy(),
                true, old.nativePending(), null, old.release(), old.reason());
        changed.run();
    }
}
