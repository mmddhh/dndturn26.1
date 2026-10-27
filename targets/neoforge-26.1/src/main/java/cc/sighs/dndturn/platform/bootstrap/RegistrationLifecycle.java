package cc.sighs.dndturn.platform.bootstrap;
import cc.sighs.dndturn.platform.server.action.AbilityExecutors;

import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.actor.ActorLifecycleAdapters;
import cc.sighs.dndturn.platform.server.fact.MinecraftFactProviders;
import cc.sighs.dndturn.platform.server.effect.GroupEffects;
import cc.sighs.dndturn.platform.server.damage.RangedAdapters;
import cc.sighs.dndturn.platform.server.world.WorldOutcomeHooks;
import cc.sighs.dndturn.platform.server.world.CloudOrigins;
import java.util.ArrayList;

/**
 * Register builtins and extensions during mod construction or common setup (including its queued work).
 * Load-complete queued work closes all registration before any server starts. Do not register at load complete.
 * Executors are bound to definition IDs; their executor ID is checked metadata, not a shared lookup key.
 */
public final class RegistrationLifecycle {
    private RegistrationLifecycle() {}
    public static synchronized void freeze() {
        AbilityExecutors.validate();
        WorldOutcomeHooks.freeze();
        cc.sighs.dndturn.platform.server.effect.vanilla.EffectAdapters.freeze();
        CloudOrigins.freeze();
        var modifiers = new ArrayList<>(AbilityAdapterRegistry.continuous().registeredModifiers());
        AbilityAdapterRegistry.effects().definitions().forEach(value -> modifiers.addAll(value.modifiers()));
        for (var modifier : modifiers) if (MinecraftFactProviders.owns(modifier.stat().fact()))
            throw new IllegalArgumentException("rule modifiers cannot overwrite native-owned facts");
        AbilityAdapterRegistry.continuous().freeze(AbilityAdapterRegistry.definitions()); AbilityAdapterRegistry.effects().freeze(AbilityAdapterRegistry.definitions()); 
        AbilityAdapterRegistry.inspection().freeze(); AbilityAdapterRegistry.equipmentEffects().freeze(); GroupEffects.freeze(); RangedAdapters.freeze();
        MinecraftFactProviders.freeze(); ActorDefinitions.freeze(); ActorLifecycleAdapters.freeze(); AbilityAdapterRegistry.activations().freeze();
        AbilityAdapterRegistry.freeze();
        cc.sighs.dndturn.platform.server.action.AbilityExecutors.freeze();
        cc.sighs.dndturn.platform.server.ai.MobTurnStrategies.freeze();
        cc.sighs.dndturn.platform.server.damage.MeleeAdapters.server().freeze();
        cc.sighs.dndturn.platform.server.damage.BodyTargets.freeze();
        cc.sighs.dndturn.platform.server.ability.NativeObservations.REGISTRY.freeze();
    }
}
