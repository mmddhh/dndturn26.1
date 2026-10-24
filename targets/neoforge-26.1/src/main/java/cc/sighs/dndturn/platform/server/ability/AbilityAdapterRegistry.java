package cc.sighs.dndturn.platform.server.ability;

import cc.sighs.dndturn.application.inspection.InspectionPolicy;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.AbilityRegistry;
import cc.sighs.dndturn.domain.ability.AbilityRule;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.ability.ContinuousAbilities;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.effect.EffectRegistry;
import cc.sighs.dndturn.domain.effect.EquipmentEffects;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import java.util.*;

/** Independent definition, capture and execution registration. No resource authority lives here. */
public final class AbilityAdapterRegistry {
    private static final AbilityRegistry DEFINITIONS = new AbilityRegistry();
    private static final InspectionPolicy INSPECTION = new InspectionPolicy();
    public static InspectionPolicy inspection() { return INSPECTION; }
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
        if (existing == null) DEFINITIONS.register(definition);
        ACTIVATIONS.register(registration);
    }
    public static synchronized boolean hasNativeFacts(String id) { return FACTS.containsKey(id); }
    private static boolean frozen;

    private AbilityAdapterRegistry() {}
    public static synchronized void register(AbilityDefinition definition, MinecraftAbilityFacts facts, AbilityRule rule) {
        Objects.requireNonNull(definition); Objects.requireNonNull(facts); Objects.requireNonNull(rule);
        if (frozen) throw new IllegalStateException("ability registration frozen");
        DEFINITIONS.register(definition, rule); FACTS.put(definition.id(), facts);
    }
    public static synchronized void freeze() { DEFINITIONS.freeze(); frozen = true; }
    public static synchronized boolean frozen() { return frozen; }
    public static AbilityRegistry definitions() { return DEFINITIONS; }
    public static synchronized MinecraftAbilityFacts facts(String id) {
        var provider = FACTS.get(id);
        if (provider == null) throw new ActionFailure(ActionFailure.Code.ABILITY_UNAVAILABLE, ActionFailure.Retry.NONE, "native fact provider missing");
        return provider;
    }
    public static MinecraftAbilityFacts facts(ActionIntent intent) { DEFINITIONS.require(intent); return facts(intent.behaviorId()); }
}
