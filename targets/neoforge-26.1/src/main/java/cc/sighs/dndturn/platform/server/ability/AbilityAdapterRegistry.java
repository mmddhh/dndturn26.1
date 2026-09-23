package cc.sighs.dndturn.platform.server.ability;

import cc.sighs.dndturn.application.inspection.InspectionPolicy;
import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.AbilityRegistry;
import cc.sighs.dndturn.domain.ability.AbilityRule;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.ability.ContinuousAbilities;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.effect.EffectRegistry;
import cc.sighs.dndturn.domain.effect.EquipmentEffects;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.actor.ActorLifecycleAdapters;
import cc.sighs.dndturn.platform.server.actor.MinecraftFactProviders;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import cc.sighs.dndturn.platform.server.damage.RangedAdapters;
import cc.sighs.dndturn.platform.server.effect.GroupEffects;
import cc.sighs.dndturn.platform.server.world.CloudOrigins;
import cc.sighs.dndturn.platform.server.world.WorldOutcomeHooks;
import java.util.*;

/** Independent definition, capture and execution registration. No resource authority lives here. */
public final class AbilityAdapterRegistry {
    private static final AbilityRegistry DEFINITIONS = new AbilityRegistry();
    private static final InspectionPolicy INSPECTION = new InspectionPolicy();
    public static InspectionPolicy inspection() { return INSPECTION; }
    private static final Map<String, AbilityExecutor> EXECUTORS = new LinkedHashMap<>();
    private static final Map<String, MinecraftAbilityFacts> FACTS = new LinkedHashMap<>();
    private static final ActorActivations ACTIVATIONS = new ActorActivations();
    private static final EffectRegistry EFFECTS = new EffectRegistry();
    private static final ContinuousAbilities CONTINUOUS = new ContinuousAbilities();
    private static final EquipmentEffects EQUIPMENT_EFFECTS = new EquipmentEffects();
    public static EquipmentEffects equipmentEffects() { return EQUIPMENT_EFFECTS; }
    public static ContinuousAbilities continuous() { return CONTINUOUS; }
    public static synchronized void registerContinuous(AbilityDefinition definition, ContinuousAbilities.Contribution contribution) {
        if (frozen || definition.activation() != AbilityDefinition.Activation.CONTINUOUS || !definition.cost().equals(ActionCost.FREE))
            throw new IllegalStateException("continuous registration contract");
        DEFINITIONS.register(definition); CONTINUOUS.register(definition, contribution);
    }
    public static EffectRegistry effects() { return EFFECTS; }
    public static ActorActivations activations() { return ACTIVATIONS; }
    public static synchronized void registerActorEffect(AbilityDefinition definition, ActivationSpec activation, ActorActivations.Rule rule) {
        registerActorEffect(definition, activation, definition.reads(), rule);
    }
    public static synchronized void registerActorEffect(AbilityDefinition definition, ActivationSpec activation,
            cc.sighs.dndturn.domain.fact.ReadContract reads, ActorActivations.Rule rule) {
        var registration = new ActorActivations.Registration(definition, activation, reads, rule);
        var existing = DEFINITIONS.find(definition.id(), definition.version());
        if (frozen || existing != null && !existing.equals(definition)) throw new IllegalStateException("activation registration closed or definition conflict");
        if (!definition.cost().equals(ActionCost.FREE) && !EXECUTORS.containsKey(definition.id()))
            throw new IllegalStateException("register the paid ability executor before its activation");
        if (existing == null) DEFINITIONS.register(definition);
        ACTIVATIONS.register(registration);
    }
    public static synchronized boolean hasNativeFacts(String id) { return FACTS.containsKey(id); }
    private static boolean frozen;

    private AbilityAdapterRegistry() {}
    public static synchronized void register(AbilityDefinition definition, MinecraftAbilityFacts facts, AbilityExecutor executor) {
        register(definition, facts, executor, RuleResolver::resolve);
    }
    public static synchronized void register(AbilityDefinition definition, MinecraftAbilityFacts facts,
                                             AbilityExecutor executor, AbilityRule rule) {
        Objects.requireNonNull(definition); Objects.requireNonNull(facts); Objects.requireNonNull(executor);
        if (frozen) throw new IllegalStateException("ability registration frozen");
        if (!definition.equals(executor.definition()) || !definition.executor().equals(executor.id()))
            throw new IllegalArgumentException("executor contract mismatch");
        DEFINITIONS.register(definition, rule);
        EXECUTORS.put(definition.id(), executor); FACTS.put(definition.id(), facts);
    }
    public static synchronized void freeze() {
        WorldOutcomeHooks.freeze();
        CloudOrigins.freeze();
        var modifiers = new ArrayList<>(CONTINUOUS.registeredModifiers());
        EFFECTS.definitions().forEach(value -> modifiers.addAll(value.modifiers()));
        for (var modifier : modifiers) if (MinecraftFactProviders.owns(modifier.stat().fact()))
            throw new IllegalArgumentException("rule modifiers cannot overwrite native-owned facts");
        CONTINUOUS.freeze(DEFINITIONS); EFFECTS.freeze(DEFINITIONS); DEFINITIONS.freeze();
        INSPECTION.freeze(); EQUIPMENT_EFFECTS.freeze(); GroupEffects.freeze(); RangedAdapters.freeze();
        MinecraftFactProviders.freeze(); ActorDefinitions.freeze(); ActorLifecycleAdapters.freeze(); ACTIVATIONS.freeze(); frozen = true;
    }
    public static synchronized boolean frozen() { return frozen; }
    public static AbilityRegistry definitions() { return DEFINITIONS; }
    public static synchronized MinecraftAbilityFacts facts(String id) {
        var provider = FACTS.get(id);
        if (provider == null) throw new ActionFailure(ActionFailure.Code.ABILITY_UNAVAILABLE, ActionFailure.Retry.NONE, "native fact provider missing");
        return provider;
    }
    public static MinecraftAbilityFacts facts(ActionIntent intent) { DEFINITIONS.require(intent); return facts(intent.behaviorId()); }
    public static synchronized AbilityExecutor resolve(ActionIntent intent) {
        DEFINITIONS.require(intent);
        var executor = EXECUTORS.get(intent.behaviorId());
        if (executor == null) throw new ActionFailure(ActionFailure.Code.ABILITY_UNAVAILABLE, ActionFailure.Retry.NONE, "native executor missing");
        return executor;
    }
    public static String recoveryReason(ActionIntent intent) {
        try { return resolve(intent).recoveryReason(); }
        catch (IllegalStateException unavailable) { return unavailable.getMessage() + "; not replayed"; }
    }
    public static synchronized List<AbilityExecutor> all() { return List.copyOf(EXECUTORS.values()); }
    public enum Availability { EXECUTABLE, APPROACH_REQUIRED, TARGET_REQUIRED, RULE_BLOCKED, ADAPTER_MISSING, EVALUATION_DEFERRED }
    public record Discovered(AbilityBinding binding, Availability availability, ActionFailure.Details failure) {}
    public static RuleResolver.Resolution evaluate(LiveActorContext actor, ActionIntent intent,
            EncounterAuthority.StateView state, GrantEvidence expected, ResolutionContext.Stage stage) {
        var snapshot = MinecraftSnapshotCapture.capture(actor, DEFINITIONS.require(intent));
        return DEFINITIONS.resolve(MinecraftSnapshotCapture.capture(actor, snapshot, intent, state, expected, stage));
    }
    public static void requireAccepted(RuleResolver.Resolution resolution) {
        if (resolution.accepted()) return;
        throw new ActionFailure(resolution.status() == RuleResolver.Status.DEFERRED
                ? ActionFailure.Code.EVALUATION_DEFERRED : ActionFailure.Code.ABILITY_UNAVAILABLE,
                resolution.status() == RuleResolver.Status.DEFERRED ? ActionFailure.Retry.REFRESH_AND_REPROPOSE : ActionFailure.Retry.NONE,
                resolution.reason());
    }
    public static List<Discovered> discover(LiveActorContext actor, EncounterAuthority.StateView state, ActionIntent.Target target) {
        var snapshot = MinecraftSnapshotCapture.capture(actor, state);
        return discover(actor, snapshot, state, target);
    }
    /** Reuse one decision-boundary source capture across bounded target evaluation. Not an authority cache. */
    public static List<Discovered> discover(LiveActorContext actor, ActorSnapshot snapshot, EncounterAuthority.StateView state, ActionIntent.Target target) {
        var found = new ArrayList<Discovered>();
        for (var binding : snapshot.abilities()) {
            if (binding.definition().activation() != AbilityDefinition.Activation.MANUAL) continue;
            if (target == null || !binding.targets().contains(target.kind())) {
                found.add(new Discovered(binding, Availability.TARGET_REQUIRED, ActionFailure.Details.NONE)); continue;
            }
            try {
                var intent = binding.invocation(target);
                var result = DEFINITIONS.resolve(MinecraftSnapshotCapture.capture(actor, snapshot, intent, state,
                        binding.source(), ResolutionContext.Stage.PROPOSAL));
                Availability availability = switch (result.status()) {
                    case ALLOWED -> Availability.EXECUTABLE;
                    case REQUIRES_APPROACH -> Availability.APPROACH_REQUIRED;
                    case REJECTED -> Availability.RULE_BLOCKED;
                    case UNSUPPORTED -> Availability.ADAPTER_MISSING;
                    case DEFERRED -> Availability.EVALUATION_DEFERRED;
                };
                found.add(new Discovered(binding, availability, result.accepted() ? ActionFailure.Details.NONE : ActionFailure.Details.REJECTED));
            } catch (RuntimeException rejected) {
                var failure = ActionFailure.classify(rejected);
                found.add(new Discovered(binding, failure.code() == ActionFailure.Code.EVALUATION_DEFERRED
                        ? Availability.EVALUATION_DEFERRED : Availability.RULE_BLOCKED, failure));
            }
        }
        return List.copyOf(found);
    }
}
