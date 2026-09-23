package cc.sighs.dndturn.client;

import cc.sighs.dndturn.diagnostics.DebugDiagnostics;

import cc.sighs.dndturn.combat.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Authoritative display baselines. Cache presence alone grants no field ownership. */
public final class ClientEntitySimulation {
    private static final Map<UUID, CombatNetwork.EntitySimulation> states = new HashMap<>();
    private static final Map<UUID, UUID> localInstances = new HashMap<>();
    private record SwingReceipt(UUID instance, UUID operation, long sequence) {}
    private static final Map<UUID, SwingReceipt> swings = new HashMap<>();
    static boolean consumeSwing(CombatNetwork.TacticalSwing event) {
        var previous = swings.get(event.entity());
        if (previous != null && previous.instance().equals(event.instance())
            && (previous.sequence() >= event.sequence() || previous.operation().equals(event.operation()))) return false;
        swings.put(event.entity(), new SwingReceipt(event.instance(), event.operation(), event.sequence()));
        return true;
    }
    private static UUID generation;
    private ClientEntitySimulation() {}
    public static void clear() { states.clear(); localInstances.clear(); swings.clear(); generation = null; ClientPresentation.clear(); }
    public static void leave(Entity entity) {
        var state = states.get(entity.getUUID());
        if (state != null && state.runtimeId() == entity.getId()) {
            // Keep an ordering tombstone until world/connection reset; a delayed old baseline cannot bind a reused ID.
            states.put(entity.getUUID(), new CombatNetwork.EntitySimulation(state.generation(), state.sequence(), state.entityId(),
                state.runtimeId(), state.instance(), state.dimension(), false, state.facts(), null));
            localInstances.remove(entity.getUUID()); swings.remove(entity.getUUID()); ClientPresentation.forget(entity.getUUID());
        }
    }
    public static void receive(CombatNetwork.EntitySimulation state, IPayloadContext context) {
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
    static void accept(CombatNetwork.EntitySimulation state) {
        if (generation != null && !generation.equals(state.generation())) return;
        generation = state.generation();
        var previous = states.get(state.entityId());
        if (previous != null && previous.sequence() >= state.sequence()) return;
        if (!state.tracked() && previous != null && !previous.instance().equals(state.instance())) return;
        if (!state.tracked() || previous != null && (!previous.instance().equals(state.instance()) || previous.runtimeId() != state.runtimeId())) {
            ClientPresentation.forget(state.entityId()); localInstances.remove(state.entityId());
        }
        if (previous == null || !previous.instance().equals(state.instance()) || previous.tracked() != state.tracked()
            || !Objects.equals(previous.facts().member(), state.facts().member())
            || !Objects.equals(previous.facts().controller(), state.facts().controller()) || previous.paused() != state.paused())
            DebugDiagnostics.log("client baseline generation={} entity={} instance={} sequence={} tracked={} member={} controller={} paused={}",
                state.generation(), state.entityId(), state.instance(), state.sequence(), state.tracked(), state.facts().member(), state.facts().controller(), state.paused());
        states.put(state.entityId(), state);
        var mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(state.runtimeId());
        if (entity != null && projection(entity) != null) ClientPresentation.project(entity, state);
    }
    public static CombatNetwork.EntitySimulation projection(Entity entity) {
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
}
