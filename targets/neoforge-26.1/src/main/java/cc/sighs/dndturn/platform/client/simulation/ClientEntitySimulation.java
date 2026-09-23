package cc.sighs.dndturn.platform.client.simulation;

import cc.sighs.dndturn.platform.client.presentation.ClientPresentation;
import cc.sighs.dndturn.platform.client.state.ClientCombatState;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.observation.VanillaEffectTypes;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Authoritative display baselines. Cache presence alone grants no field ownership. */
public final class ClientEntitySimulation {
    private static final Map<UUID, EncounterProtocol.EntitySimulation> states = new HashMap<>();
    private static final Map<UUID, UUID> localInstances = new HashMap<>();
    private static final Map<UUID, Long> environmentVisuals = new HashMap<>();
    private record SwingReceipt(UUID instance, UUID operation, long sequence) {}
    private static final Map<UUID, SwingReceipt> swings = new HashMap<>();
    public static boolean consumeSwing(EncounterProtocol.TacticalSwing event) {
        var previous = swings.get(event.entity());
        if (previous != null && previous.instance().equals(event.instance())
            && (previous.sequence() >= event.sequence() || previous.operation().equals(event.operation()))) return false;
        swings.put(event.entity(), new SwingReceipt(event.instance(), event.operation(), event.sequence()));
        return true;
    }
    private static UUID generation;
    private ClientEntitySimulation() {}
    public static void clear() { states.clear(); localInstances.clear(); swings.clear(); environmentVisuals.clear(); generation = null; ClientPresentation.clear(); }
    public static void leave(Entity entity) {
        var state = states.get(entity.getUUID());
        if (state != null && state.runtimeId() == entity.getId()) {
            // Keep an ordering tombstone until world/connection reset; a delayed old baseline cannot bind a reused ID.
            states.put(entity.getUUID(), new EncounterProtocol.EntitySimulation(state.generation(), state.sequence(), state.entityId(),
                state.runtimeId(), state.instance(), state.dimension(), false, state.facts(), null));
            localInstances.remove(entity.getUUID()); swings.remove(entity.getUUID()); ClientPresentation.forget(entity.getUUID());
            environmentVisuals.remove(entity.getUUID());
        }
    }
    public static void receive(EncounterProtocol.EntitySimulation state, IPayloadContext context) {
        var origin = context.connection();
        var world = Minecraft.getInstance().level;
        context.enqueueWork(() -> {
            ClientCombatState.refreshSession();
            var mc = Minecraft.getInstance();
            if (mc.getConnection() == null || mc.getConnection().getConnection() != origin || world == null || mc.level != world
                || !world.dimension().identifier().toString().equals(state.dimension())) return;
            accept(state);
        });
    }
    static void accept(EncounterProtocol.EntitySimulation state) {
        if (generation != null && !generation.equals(state.generation())) return;
        generation = state.generation();
        var previous = states.get(state.entityId());
        if (previous != null && previous.sequence() >= state.sequence()) return;
        if (!state.tracked() && previous != null && !previous.instance().equals(state.instance())) return;
        if (!state.tracked() || previous != null && (!previous.instance().equals(state.instance()) || previous.runtimeId() != state.runtimeId())) {
            ClientPresentation.forget(state.entityId()); localInstances.remove(state.entityId());
        }
        states.put(state.entityId(), state);
        if (previous == null || !previous.tracked() || !state.tracked()
            || !previous.instance().equals(state.instance())
            || !Objects.equals(previous.facts().controller(), state.facts().controller()))
            environmentVisuals.put(state.entityId(), state.facts().environmentTime());
        var mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(state.runtimeId());
        if (entity != null && projection(entity) != null) ClientPresentation.project(entity, state);
    }
    public static EncounterProtocol.EntitySimulation projection(Entity entity) {
        if (entity == null) return null;
        var state = states.get(entity.getUUID());
        if (state == null || !state.tracked() || state.runtimeId() != entity.getId()
            || !state.dimension().equals(entity.level().dimension().identifier().toString())) return null;
        UUID instance = ((PresentationIdentity)entity).dndturn$presentationInstance();
        UUID bound = localInstances.putIfAbsent(entity.getUUID(), instance);
        return bound == null || bound.equals(instance) ? state : null;
    }
    public static boolean paused(Entity entity) {
        if (entity == Minecraft.getInstance().player) return ClientCombatState.bodySimulationPaused();
        var state = projection(entity);
        return state != null && state.paused();
    }
    /** Remaining effect duration is maintained by authoritative participant packets. */
    public static boolean holdsEffect(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.effect.MobEffectInstance effect) {
        var state = projection(entity);
        return state != null && state.facts().member() != null && VanillaEffectTypes.supported(effect);
    }
    /** Coalesce confirmed progress into one visual update. Tracking starts from a baseline, never history. */
    public static boolean consumeEnvironmentVisual(Entity entity) {
        var state = projection(entity);
        if (state == null || state.facts().controller() == null) return false;
        long time = state.facts().environmentTime();
        Long old = environmentVisuals.put(entity.getUUID(), time);
        return old != null && time > old;
    }
}
