package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.damage.RangedAdapters;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A single callback's bounded access to its existing plan. Never a transferable permit.
 * Adapter values belong to the separately persisted semantic process and remain historical after release.
 * Recovery reconciles observations without replaying adapter callbacks or restoring this object.
 */
public final class AbilityExecutionContext implements AutoCloseable {
    private final ActionExecutionCoordinator actions;
    private final LiveActorContext actor;
    private final ActionExecutionCoordinator.Execution execution;
    private final boolean release;
    private boolean open = true;

    AbilityExecutionContext(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution, boolean release) {
        this.actions = actions; this.actor = actor; this.execution = execution; this.release = release;
    }
    private void check() {
        if (!open) throw new IllegalStateException("execution callback has ended");
        actions.verifyContext(actor, execution, release);
    }
    public OperationRecord.Snapshot snapshot() { check(); return execution.snapshot(); }
    public long executionSteps() { check(); return execution.executionSteps; }
    public Map<String, String> state() { check(); return actions.service.processes().require(execution.root.operationId()).values(); }
    public long random() { check(); return actions.service.processes().random(execution.root.operationId()); }
    public void put(String key, String value) {
        check(); Objects.requireNonNull(key); Objects.requireNonNull(value);
        actions.service.processes().put(execution.root.operationId(), key, value);
    }
    public void remove(String key) { check(); actions.service.processes().put(execution.root.operationId(), Objects.requireNonNull(key), null); }

    /** The audited shared melee family. Target, source, policy and cost come from the bound plan.
     * No caller-supplied damage, target, permit or operation can broaden its effect.
     */
    public void melee() {
        check();
        if (release || execution.phase != AbilityCheckpoint.Phase.EXECUTE
            || execution.root.intent().capability() != ActionIntent.Capability.ATTACK
            || execution.root.intent().target().kind() != ActionIntent.TargetKind.ENTITY)
            throw new IllegalStateException("plan has no melee execution authority");
        if (execution.effectInvoked) throw new IllegalStateException("plan effect already invoked");
        // Mark before entering native code: an exception with uncertain effects must never replay.
        execution.effectInvoked = true;
        actions.service.actionHost().attackPlan(actor, execution.root.target(), UUID.randomUUID(), execution.root.operationId(),
            result -> actions.finish(actor.body(), execution, result.outcome(), result.reason()));
    }
    /** Executes only the registered driver of the bound equipment ability. No caller-supplied target or cost. */
    public void ranged() {
        check();
        if (release || execution.phase != AbilityCheckpoint.Phase.EXECUTE || execution.effectInvoked
                || !(execution.behavior instanceof EquippedRanged))
            throw new IllegalStateException("plan has no ranged execution authority");
        var root = execution.root;
        var state = actions.engine.stateView(root.encounterId());
        actions.validate(actor.body(), root.intent(), state);
        if (!execution.behavior.definition().cost().equals(ActionCost.ATTACK)
                || !AbilityAdapterRegistry.facts(root.intent()).canExecute(actor, root.intent(), actor.body().position()))
            throw new IllegalStateException("ranged execution invalidated");
        var driver = RangedAdapters.find(actor);
        var equipmentEffect = EquippedRanged.contribution(actor);
        var target = actor.level().getEntity(root.target());
        if (driver == null || equipmentEffect == null || !(target instanceof net.minecraft.world.entity.LivingEntity living))
            throw new IllegalStateException("ranged dependencies unavailable");
        actions.beginStep(actor.body(), execution);
        execution.effectInvoked = true;
        OperationRecord.Outcome outcome = OperationRecord.Outcome.COMPLETED;
        String reason = "native ranged launch observed";
        try {
            execution.launchTrace = actions.service.actionHost().rangedTrace(actor.body(), root.target(), execution.action, false);
            try (var launch = new TacticalLaunchContext(actor.id(), root.encounterId(), execution.action,
                    root.target(), execution.launchTrace, root.operationId(), root.intent())) {
                try { driver.driver().shoot(actor, living); }
                finally { execution.spawned.addAll(launch.spawned()); }
                if (launch.spawned().size() != 1) throw new IllegalStateException("native driver did not produce exactly one arrow");
            }
            switch (equipmentEffect.policy().consumption()) {
                case NATIVE_NO_ITEM_COST -> {
                    if (!root.intent().source().equals(AbilityAdapterRegistry.facts(root.intent()).source(actor, root.intent().hand())))
                        throw new IllegalStateException("native equipment consumption contradicted captured effect policy");
                }
            }
        } catch (RuntimeException failure) {
            outcome = OperationRecord.Outcome.UNKNOWN;
            reason = "native ranged launch uncertain; not replayed";
        }
        actions.finishAction(actor.body(), execution, outcome, reason);
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
