package cc.sighs.dndturn.platform.server.action;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.ability.MinecraftAbilityFacts;
import cc.sighs.dndturn.platform.server.action.AbilityExecutor;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.AbilityRule;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import java.util.*;

/** Execution registration, independent from ability definition/fact ownership. */
public final class AbilityExecutors {
    private static final Map<String, AbilityExecutor> EXECUTORS = new LinkedHashMap<>();
    private static boolean frozen;
    private AbilityExecutors() {}
    public static synchronized void register(AbilityDefinition definition, MinecraftAbilityFacts facts, AbilityExecutor executor) {
        register(definition, facts, executor, RuleResolver::resolve);
    }
    public static synchronized void register(AbilityDefinition definition, MinecraftAbilityFacts facts, AbilityExecutor executor, AbilityRule rule) {
        Objects.requireNonNull(executor);
        if (frozen) throw new IllegalStateException("executor registration frozen");
        if (!definition.equals(executor.definition()) || !definition.executor().equals(executor.id()))
            throw new IllegalArgumentException("executor contract mismatch");
        AbilityAdapterRegistry.register(definition, facts, rule); EXECUTORS.put(definition.id(), executor);
    }
    public static synchronized void freeze() { frozen = true; }
    public static synchronized AbilityExecutor resolve(ActionIntent intent) {
        AbilityAdapterRegistry.definitions().require(intent);
        var executor = EXECUTORS.get(intent.behaviorId());
        if (executor == null) throw new ActionFailure(ActionFailure.Code.ABILITY_UNAVAILABLE, ActionFailure.Retry.NONE, "native executor missing");
        return executor;
    }
    public static String recoveryReason(ActionIntent intent) {
        try { return resolve(intent).recoveryReason(); }
        catch (IllegalStateException unavailable) { return unavailable.getMessage() + "; not replayed"; }
    }

    public static synchronized List<AbilityExecutor> all() { return List.copyOf(EXECUTORS.values()); }
    public static synchronized void validate() {
        for (var definition : AbilityAdapterRegistry.definitions().all()) {
            boolean executable = EXECUTORS.containsKey(definition.id());
            boolean activation = AbilityAdapterRegistry.activations().supports(definition);
            boolean paid = !definition.cost().equals(cc.sighs.dndturn.domain.ability.ActionCost.FREE);
            switch (definition.activation()) {
                case MANUAL -> require(executable, "manual ability executor missing", definition);
                // Triggered executions may be emitted by another registered activation (e.g. threshold -> explosion).
                case TRIGGERED -> require(executable || activation, "triggered ability driver missing", definition);
                case PERIODIC -> require(activation, "periodic activation missing", definition);
                case CONTINUOUS -> require(!paid && !executable, "continuous ability execution conflict", definition);
            }
            if (paid) require(executable, "paid ability executor missing", definition);
            if (executable) require(AbilityAdapterRegistry.hasNativeFacts(definition.id()), "native facts missing", definition);
        }
    }
    private static void require(boolean valid, String message, AbilityDefinition definition) {
        if (!valid) throw new IllegalStateException(message + ": " + definition.id());
    }

}
