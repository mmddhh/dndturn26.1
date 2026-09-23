package cc.sighs.dndturn.combat;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A single callback's bounded access to its existing plan. Never a transferable permit.
 * Stored adapter data is limited to values and is discarded after successful release.
 * Recovery reconciles observations without replaying adapter callbacks or restoring this object.
 */
public final class TacticalExecution implements AutoCloseable {
    private final TacticalActions actions;
    private final TacticalActor actor;
    private final TacticalActions.Execution execution;
    private final boolean release;
    private boolean open = true;

    TacticalExecution(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution, boolean release) {
        this.actions = actions; this.actor = actor; this.execution = execution; this.release = release;
    }
    private void check() {
        if (!open) throw new IllegalStateException("execution callback has ended");
        actions.verifyContext(actor, execution, release);
    }
    public OperationRecord.Snapshot snapshot() { check(); return execution.snapshot(); }
    public long executionSteps() { check(); return execution.executionSteps; }
    public Map<String, String> state() { check(); return Map.copyOf(execution.adapterState); }
    public void put(String key, String value) {
        check(); Objects.requireNonNull(key); Objects.requireNonNull(value);
        if (key.isBlank() || key.length() > 64 || value.length() > 512
            || !execution.adapterState.containsKey(key) && execution.adapterState.size() >= 32)
            throw new IllegalArgumentException("adapter state exceeds value budget");
        execution.adapterState.put(key, value);
    }
    public void remove(String key) { check(); execution.adapterState.remove(key); }

    /** The audited shared melee family. Target, source, policy and cost come from the bound plan.
     * No caller-supplied damage, target, permit or operation can broaden its effect.
     */
    public void melee() {
        check();
        if (release || execution.phase != AbilityCheckpoint.Phase.EXECUTE
            || execution.root.intent().capability() != TacticalIntent.Capability.ATTACK
            || execution.root.intent().target().kind() != TacticalIntent.TargetKind.ENTITY)
            throw new IllegalStateException("plan has no melee execution authority");
        if (execution.effectInvoked) throw new IllegalStateException("plan effect already invoked");
        // Mark before entering native code: an exception with uncertain effects must never replay.
        execution.effectInvoked = true;
        actions.service.attackPlan(actor, execution.root.target(), UUID.randomUUID(), execution.root.operationId(),
            result -> actions.finish(actor.body(), execution, result.outcome(), result.reason()));
    }
    /** End without a world effect. Arbitrary adapters cannot report invented damage or resource commits. */
    public void reject(String reason) {
        check();
        if (release || execution.effectInvoked || execution.phase != AbilityCheckpoint.Phase.EXECUTE)
            throw new IllegalStateException("cannot reject outside uneffected execution");
        if (reason == null || reason.isBlank() || reason.length() > 512) throw new IllegalArgumentException("reason");
        actions.finish(actor.body(), execution, OperationRecord.Outcome.REJECTED, reason);
    }
    @Override public void close() { open = false; }
}
