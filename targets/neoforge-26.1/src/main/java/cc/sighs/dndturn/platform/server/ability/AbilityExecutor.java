package cc.sighs.dndturn.platform.server.ability;

import cc.sighs.dndturn.domain.ability.ProcessDefinition;
import cc.sighs.dndturn.domain.encounter.operation.Intervention;
import cc.sighs.dndturn.domain.encounter.operation.NativeObservation;
import cc.sighs.dndturn.domain.encounter.operation.ObservationRegistry;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.server.action.ActionExecutionCoordinator;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.effect.TriggeredAbilities;
import java.util.*;

/** Native executor contract. Definitions and discovery belong to separate registries.
 * Hooks run on the server thread. Discovery/validation/reach must have no world side effects.
 * prepare runs after approach/revalidation; start/tick observe vanilla effects before publishing.
 * release must be idempotent, releasing only control acquired by this execution, without firing it.
 */
public abstract class AbilityExecutor {
    private final AbilityDefinition definition;
    protected AbilityExecutor(AbilityDefinition definition) {
        this.definition = Objects.requireNonNull(definition);
    }
    public final AbilityDefinition definition() { return definition; }
    public final String id() { return definition.id(); }
    public final int version() { return definition.version(); }
    public final String label() { return definition.label(); }
    public final ActionIntent.Capability kind() { return definition.kind(); }
    public final Set<ActionIntent.TargetKind> targets() { return definition.targets(); }
    public enum ExecutionClock { AUTHORIZED_EXECUTION_STEP }
    public enum Recovery { RECONCILE_WITHOUT_REPLAY }
    public ExecutionClock executionClock() { return ExecutionClock.AUTHORIZED_EXECUTION_STEP; }
    public Recovery recovery() { return Recovery.RECONCILE_WITHOUT_REPLAY; }
    public Set<ObservationRegistry.Key> requiredObservations() {
        return Set.of(NativeObservations.BODY);
    }
    /** Post-call evidence. Additional direct/indirect outcomes require an adapter's explicit capture. */
    public List<NativeObservation> observeNative(
            LiveActorContext actor, UUID operation, ActionIntent invocation) {
        return List.of(NativeObservations.body(operation, actor.body()));
    }
    public ProcessDefinition processDefinition() {
        return new ProcessDefinition(id(), version(),
                ProcessDefinition.Clock.AUTHORIZED_EXECUTION_STEP,
                ProcessDefinition.Recovery.FAIL_UNKNOWN, 1000000,
                Set.of(), "native execution; reconcile observations without callback replay");
    }
    /** Inventory evidence is an adapter concern, independent of actor species or ability ID. */
    public Set<Integer> observationSlots(LiveActorContext actor, ActionIntent intent) {
        return intent.source().kind() == GrantEvidence.Kind.EQUIPMENT
            ? Set.of(intent.source().item().slot()) : Set.of();
    }
    /** Exact direct observation scope. Additional effects need their own audited evidence. */
    public List<GridCell> observationFootprint(LiveActorContext actor, ActionIntent intent) {
        return intent.target().kind() == ActionIntent.TargetKind.BLOCK ? List.of(intent.target().cell()) : List.of();
    }
    /** Pure preparation of observation dependencies; never invokes a world action. */
    public List<GridCell> prepareObservation(LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
        return observationFootprint(actor, execution.snapshot().intent());
    }
    /** An audited melee driver declares these effects; source kind grants no effect policy. */
    public record MeleeEffects(double baseDamage, boolean knockback, boolean observeZeroDamage, boolean heldItemHooks) {
        public MeleeEffects {
            if (!Double.isFinite(baseDamage) || baseDamage < 0 || baseDamage > Integer.MAX_VALUE)
                throw new IllegalArgumentException("invalid melee damage");
        }
    }
    public MeleeEffects meleeEffects(LiveActorContext actor, boolean configuredKnockback) {
        throw new IllegalStateException("ability has no audited melee driver");
    }
    public void prepare(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {}
    public abstract void start(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution);
    public abstract void tick(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution);
    /** Synchronous triggered channel; implementations must explicitly opt in and remain bounded. */
    public void prepareTriggered(LiveActorContext actor, TriggeredExecutionRecord invocation) {
        throw new IllegalStateException("triggered executor unavailable");
    }
    public OperationRecord.Outcome executeTriggered(TriggeredAbilities.Context context) {
        throw new IllegalStateException("triggered executor unavailable");
    }
    public void releaseTriggered(TriggeredAbilities.Context context) {}
    /** Pure decision after a confirmed reaction. The caller still owns the original mutation. */
    public Intervention intervention(TriggeredAbilities.Context context) {
        return Intervention.pass(context.emission().event().causal());
    }
    public void requestCancel(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {}
    public void release(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {}
    public String recoveryReason() { return "behavior execution evidence uncertain; not replayed"; }
}
