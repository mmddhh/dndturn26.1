package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Server extension contract. Instances are immutable; execution state contains values only.
 * Hooks run on the server thread. Discovery/validation/reach must have no world side effects.
 * prepare runs after approach/revalidation; start/tick observe vanilla effects before publishing.
 * release must be idempotent, releasing only control acquired by this execution, without firing it.
 */
public abstract class TacticalBehavior {
    public enum CommitPoint { LEGAL_ATTACK, ACCEPTED_EFFECT, MOVEMENT_OBSERVATION, FREE }
    private final String id, label;
    private final int version;
    private final TacticalIntent.Capability cost;
    private final Set<TacticalIntent.TargetKind> targets;
    protected TacticalBehavior(String id, String label, TacticalIntent.Capability cost, Set<TacticalIntent.TargetKind> targets) {
        this(id, 1, label, cost, targets);
    }
    protected TacticalBehavior(String id, int version, String label, TacticalIntent.Capability cost, Set<TacticalIntent.TargetKind> targets) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 128 || version < 1 || label.isBlank() || label.length() > 128)
            throw new IllegalArgumentException("behavior metadata");
        this.id = id; this.version = version; this.label = label; this.cost = Objects.requireNonNull(cost);
        this.targets = Set.copyOf(targets);
        if (this.targets.isEmpty()) throw new IllegalArgumentException("target kinds required");
    }
    public final String id() { return id; }
    public final int version() { return version; }
    public final String label() { return label; }
    public final TacticalIntent.Capability cost() { return cost; }
    public final Set<TacticalIntent.TargetKind> targets() { return targets; }
    public final CommitPoint commitPoint() {
        return switch (cost) {
            case ATTACK -> CommitPoint.LEGAL_ATTACK; case MOVE -> CommitPoint.MOVEMENT_OBSERVATION;
            case USE_BLOCK, EQUIP -> CommitPoint.FREE; default -> CommitPoint.ACCEPTED_EFFECT;
        };
    }
    /** Pure source ownership query; null means this actor does not own this ability. */
    public abstract AbilitySource source(TacticalActor actor, TacticalIntent.Hand hand);
    /** Shared, bounded enumeration for UI and AI. Override for multiple independent status grants. */
    public List<AbilitySource> sources(TacticalActor actor) {
        var values = new LinkedHashSet<AbilitySource>();
        var natural = source(actor, null);
        if (natural != null) values.add(natural);
        for (var hand : TacticalIntent.Hand.values()) {
            var value = source(actor, hand);
            if (value != null) values.add(value);
        }
        return List.copyOf(values);
    }
    public final List<AbilitySource> checkedSources(TacticalActor actor) {
        var values = List.copyOf(sources(actor));
        if (values.size() > 16 || new HashSet<>(values).size() != values.size())
            throw new IllegalStateException("ability source enumeration exceeds contract");
        return values;
    }
    public abstract String unavailable(TacticalActor actor, TacticalIntent intent, CombatEngine.StateView state);
    public final void validate(TacticalActor actor, TacticalIntent intent, CombatEngine.StateView state) {
        actor.verifyCurrent();
        if (!targets.contains(intent.target().kind())) throw new IllegalStateException("unsupported target kind");
        if (checkedSources(actor).stream().noneMatch(owned -> sameSource(owned, intent.source())))
            throw ActionFailure.source("ability source revoked or replaced");
        String reason = unavailable(actor, intent, state);
        if (reason != null) throw new ActionFailure(ActionFailure.Code.ABILITY_UNAVAILABLE, ActionFailure.Retry.NONE, reason);
    }
    private static boolean sameSource(AbilitySource owned, AbilitySource captured) {
        if (owned.kind() == AbilitySource.Kind.EQUIPMENT && captured.kind() == owned.kind())
            return owned.hand() == captured.hand() && owned.item().slot() == captured.item().slot();
        return owned.equals(captured);
    }
    public int approachRadius() { return 4; }
    public enum ExecutionClock { AUTHORIZED_EXECUTION_STEP }
    public enum Recovery { RECONCILE_WITHOUT_REPLAY }
    public ExecutionClock executionClock() { return ExecutionClock.AUTHORIZED_EXECUTION_STEP; }
    public Recovery recovery() { return Recovery.RECONCILE_WITHOUT_REPLAY; }
    /** Inventory evidence is an adapter concern, independent of actor species or ability ID. */
    public Set<Integer> observationSlots(TacticalActor actor, TacticalIntent intent) {
        return intent.source().kind() == AbilitySource.Kind.EQUIPMENT
            ? Set.of(intent.source().item().slot()) : Set.of();
    }
    /** Exact direct observation scope. Additional effects need their own audited evidence. */
    public List<GridCell> observationFootprint(TacticalActor actor, TacticalIntent intent) {
        return intent.target().kind() == TacticalIntent.TargetKind.BLOCK ? List.of(intent.target().cell()) : List.of();
    }
    /** Pure preparation of observation dependencies; never invokes a world action. */
    public List<GridCell> prepareObservation(TacticalActor actor, TacticalActions.Execution execution) {
        return observationFootprint(actor, execution.snapshot().intent());
    }
    /** An audited melee driver declares these effects; source kind grants no effect policy. */
    public record MeleeEffects(double baseDamage, boolean knockback, boolean observeZeroDamage, boolean heldItemHooks) {
        public MeleeEffects {
            if (!Double.isFinite(baseDamage) || baseDamage < 0 || baseDamage > Integer.MAX_VALUE)
                throw new IllegalArgumentException("invalid melee damage");
        }
    }
    public MeleeEffects meleeEffects(TacticalActor actor, boolean configuredKnockback) {
        throw new IllegalStateException("ability has no audited melee driver");
    }
    public abstract boolean canExecute(TacticalActor actor, TacticalIntent intent, Vec3 feet);
    public void prepare(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {}
    public abstract void start(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution);
    public abstract void tick(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution);
    public void requestCancel(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {}
    public void release(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) {}
    public String recoveryReason() { return "behavior execution evidence uncertain; not replayed"; }
}
