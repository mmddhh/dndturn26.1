package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.platform.server.ability.MinecraftAbilityAdapter;

/** External behaviors use scoped ports instead of acquiring the service or rule engine.
 * Callbacks are synchronous on the server thread; retained contexts fail after the callback.
 * Preparation and release grant no world-effect authority. Release can be retried after failure.
 */
public abstract class AbilityAdapter extends MinecraftAbilityAdapter {
    protected AbilityAdapter(AbilityDefinition definition) {
        super(definition);
    }
    public void prepare(LiveActorContext actor, AbilityExecutionContext execution) {}
    public abstract void start(LiveActorContext actor, AbilityExecutionContext execution);
    public abstract void tick(LiveActorContext actor, AbilityExecutionContext execution);
    public void requestCancel(LiveActorContext actor, AbilityExecutionContext execution) {}
    public void release(LiveActorContext actor, AbilityExecutionContext execution) {}
    @Override public final void prepare(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
        try (var context = new AbilityExecutionContext(actions, actor, execution, false)) { prepare(actor, context); }
    }
    @Override public final void start(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
        try (var context = new AbilityExecutionContext(actions, actor, execution, false)) { start(actor, context); }
    }
    @Override public final void tick(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
        try (var context = new AbilityExecutionContext(actions, actor, execution, false)) { tick(actor, context); }
    }
    @Override public final void requestCancel(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
        try (var context = new AbilityExecutionContext(actions, actor, execution, true)) { requestCancel(actor, context); }
    }
    @Override public final void release(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
        try (var context = new AbilityExecutionContext(actions, actor, execution, true)) { release(actor, context); }
    }
}
