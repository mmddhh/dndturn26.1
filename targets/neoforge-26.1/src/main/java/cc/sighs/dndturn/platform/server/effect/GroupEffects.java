package cc.sighs.dndturn.platform.server.effect;

import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import java.util.*;

/** Group-owned effect installation is a lifecycle command, never part of discovery. */
public final class GroupEffects {
    private record Registration(String group, EffectDefinition effect) {}
    private record Binding(UUID instance, UUID encounter, Set<String> groups) {}
    private static final Map<String, Registration> registrations = new LinkedHashMap<>();
    private static boolean frozen;
    private final Map<UUID, Binding> attached = new HashMap<>();
    public static void register(String group, EffectDefinition effect) {
        FactKey.requireId(group); Objects.requireNonNull(effect);
        if (frozen || registrations.size() >= 128 || registrations.putIfAbsent(group, new Registration(group, effect)) != null)
            throw new IllegalStateException("group effect registration closed or duplicate");
    }
    public static void freeze() {
        var owned = new HashSet<String>();
        for (var registration : registrations.values()) {
            AbilityAdapterRegistry.effects().require(registration.effect());
            if (!owned.add(registration.effect().id())) throw new IllegalStateException("effect has competing group owners");
        }
        frozen = true;
    }
    public void capture(EncounterRuntime service, net.minecraft.world.entity.LivingEntity body) {
        if (!body.isAlive() || body.isRemoved()) return;
        UUID encounter = service.encounterOf(body.getUUID());
        if (encounter == null) return;
        var actor = new LiveActorContext(body);
        var binding = new Binding(actor.instance(), encounter, ActorDefinitions.capture(actor).groups());
        if (binding.equals(attached.get(actor.id()))) return;
        var state = service.actorStates().state(actor.id());
        var changes = new ArrayList<ActorStates.Change>();
        for (var registration : registrations.values()) {
            var owned = state.runtime().effects().values().stream().filter(e ->
                    registration.group().equals(e.sourceAbility()) && actor.id().equals(e.sourceActor())
                    && registration.effect().id().equals(e.definition().id())).toList();
            if (!binding.groups().contains(registration.group())) {
                owned.forEach(e -> changes.add(new ActorStates.Remove(e.id())));
            } else if (owned.isEmpty()) {
                UUID operation = UUID.randomUUID();
                changes.add(new ActorStates.Apply(new EffectInstance(operation, operation,
                        registration.effect(), 1, 0, state.revision() + 1, 1, actor.id(), registration.group())));
            } else if (owned.stream().anyMatch(e -> !e.definition().equals(registration.effect()))) {
                throw new IllegalStateException("group effect definition changed during recovery");
            }
        }
        if (!changes.isEmpty()) service.actorStates().command(actor,
                new ActorStates.Command(UUID.randomUUID(), actor.id(), state.revision(), changes));
        attached.put(actor.id(), binding);
    }
    public void release(EncounterRuntime service, net.minecraft.world.entity.LivingEntity body) {
        if (!body.isRemoved() && body.isAlive()) {
            var actor = new LiveActorContext(body);
            var state = service.actorStates().state(actor.id());
            var changes = new ArrayList<ActorStates.Change>();
            for (var effect : state.runtime().effects().values()) {
                var registration = registrations.get(effect.sourceAbility());
                if (registration != null && actor.id().equals(effect.sourceActor())
                        && registration.effect().id().equals(effect.definition().id())) changes.add(new ActorStates.Remove(effect.id()));
            }
            if (!changes.isEmpty()) service.actorStates().command(actor,
                    new ActorStates.Command(UUID.randomUUID(), actor.id(), state.revision(), changes));
        }
        forget(body.getUUID());
    }
    public void forget(UUID actor) { attached.remove(actor); }
}
