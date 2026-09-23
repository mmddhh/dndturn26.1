package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Complete server behaviors; no default attack fallback or predicate-only registration. */
public final class TacticalCapabilities {
    private static final Map<String, TacticalBehavior> ADAPTERS = new LinkedHashMap<>();
    private static boolean frozen;
    static { VanillaBehaviors.register(); }
    private TacticalCapabilities() {}
    public static synchronized void register(TacticalBehavior adapter) {
        Objects.requireNonNull(adapter);
        if (frozen) throw new IllegalStateException("behavior registry is frozen");
        if (ADAPTERS.size() >= 64) throw new IllegalStateException("behavior registry capacity exceeded");
        if (ADAPTERS.putIfAbsent(adapter.id(), adapter) != null) throw new IllegalArgumentException("duplicate behavior " + adapter.id());
    }
    /** Register during mod construction; exact loader 11.0.15 completes all constructors before common setup. */
    public static synchronized void freeze() { frozen = true; }
    public static synchronized boolean frozen() { return frozen; }
    public static synchronized TacticalBehavior resolve(TacticalIntent intent) {
        var adapter = ADAPTERS.get(intent.behaviorId());
        if (adapter == null) throw new ActionFailure(ActionFailure.Code.ABILITY_UNAVAILABLE, ActionFailure.Retry.NONE, "behavior adapter missing: " + intent.behaviorId());
        if (adapter.version() != intent.behaviorVersion()) throw new ActionFailure(ActionFailure.Code.ABILITY_UNAVAILABLE, ActionFailure.Retry.REFRESH_AND_REPROPOSE, "behavior contract version changed");
        if (adapter.cost() != intent.capability()) throw new IllegalStateException("behavior cost classification conflict");
        return adapter;
    }
    public static String recoveryReason(TacticalIntent intent) {
        try {
            var behavior = resolve(intent);
            return switch (behavior.recovery()) {
                case RECONCILE_WITHOUT_REPLAY -> behavior.recoveryReason();
            };
        }
        catch (IllegalStateException unavailable) { return unavailable.getMessage() + "; not replayed"; }
    }
    public static synchronized List<TacticalBehavior> all() { return List.copyOf(ADAPTERS.values()); }
    public enum Availability { EXECUTABLE, APPROACH_REQUIRED, TARGET_REQUIRED, RULE_BLOCKED, ADAPTER_MISSING, EVALUATION_DEFERRED }
    public record Binding(String id, int version, TacticalIntent.Capability cost,
                          Set<TacticalIntent.TargetKind> targets, AbilitySource source) {
        public Binding { targets = Set.copyOf(targets); }
        public TacticalIntent invocation(TacticalIntent.Target target) {
            return new TacticalIntent(id, version, cost, target, source);
        }
    }
    public record Discovered(Binding binding, Availability availability, ActionFailure.Details failure) {}
    /** Actor-level pure discovery. No selection, path search, random sampling or world writes. */
    public static List<Discovered> discover(TacticalActor actor, CombatEngine.StateView state, TacticalIntent.Target target) {
        actor.verifyCurrent();
        var found = new ArrayList<Discovered>();
        for (var behavior : all()) {
            var service = ServerCombatService.forServer(actor.level().getServer());
            service.abilityWork.require(service.planClock(), actor.id(), state.id(), AbilityWorkBudget.Work.CANDIDATE);
            for (var source : behavior.checkedSources(actor)) {
                var binding = new Binding(behavior.id(), behavior.version(), behavior.cost(), behavior.targets(), source);
                if (target == null || !behavior.targets().contains(target.kind())) {
                    found.add(new Discovered(binding, Availability.TARGET_REQUIRED, ActionFailure.Details.NONE));
                    continue;
                }
                try {
                    var intent = binding.invocation(target);
                    behavior.validate(actor, intent, state);
                    var member = state.members().get(actor.id());
                    if (member == null || !actor.id().equals(state.current()) || intent.requiresAction() && !member.action())
                        found.add(new Discovered(binding, Availability.RULE_BLOCKED, ActionFailure.Details.REJECTED));
                    else found.add(new Discovered(binding, behavior.canExecute(actor, intent, actor.body().position())
                        ? Availability.EXECUTABLE : Availability.APPROACH_REQUIRED, ActionFailure.Details.NONE));
                } catch (RuntimeException rejected) {
                    var failure = ActionFailure.classify(rejected);
                    found.add(new Discovered(binding, failure.code() == ActionFailure.Code.EVALUATION_DEFERRED
                        ? Availability.EVALUATION_DEFERRED : Availability.RULE_BLOCKED, failure));
                }
            }
        }
        return List.copyOf(found);
    }
}
