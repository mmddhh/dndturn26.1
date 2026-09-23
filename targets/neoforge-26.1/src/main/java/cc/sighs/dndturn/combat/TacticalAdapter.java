package cc.sighs.dndturn.combat;

import java.util.Set;

/** External behaviors use scoped ports instead of acquiring the service or rule engine.
 * Callbacks are synchronous on the server thread; retained contexts fail after the callback.
 * Preparation and release grant no world-effect authority. Release can be retried after failure.
 */
public abstract class TacticalAdapter extends TacticalBehavior {
    protected TacticalAdapter(String id, int version, String label, TacticalIntent.Capability cost,
                              Set<TacticalIntent.TargetKind> targets) {
        super(id, version, label, cost, targets);
    }
    public void prepare(TacticalActor actor, TacticalExecution execution) {}
    public abstract void start(TacticalActor actor, TacticalExecution execution);
    public abstract void tick(TacticalActor actor, TacticalExecution execution);
    public void requestCancel(TacticalActor actor, TacticalExecution execution) {}
    public void release(TacticalActor actor, TacticalExecution execution) {}
    @Override public final void prepare(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {
        try (var context = new TacticalExecution(actions, actor, execution, false)) { prepare(actor, context); }
    }
    @Override public final void start(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {
        try (var context = new TacticalExecution(actions, actor, execution, false)) { start(actor, context); }
    }
    @Override public final void tick(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {
        try (var context = new TacticalExecution(actions, actor, execution, false)) { tick(actor, context); }
    }
    @Override public final void requestCancel(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {
        try (var context = new TacticalExecution(actions, actor, execution, true)) { requestCancel(actor, context); }
    }
    @Override public final void release(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {
        try (var context = new TacticalExecution(actions, actor, execution, true)) { release(actor, context); }
    }
}
