package cc.sighs.dndturn.combat;

import cc.sighs.dndturn.diagnostics.DebugDiagnostics;

import java.util.*;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Only observer lifetimes and last delivered values. Facts come from ServerCombatService. */
public final class ServerEntityProjections {
    private final MinecraftServer server;
    private final UUID generation;
    private final Function<Entity, PresentationState.Facts> facts;
    private record Display(UUID instance, int runtimeId, String dimension, PresentationState.Facts facts) {}
    private record Observer(UUID instance, String dimension) {}
    private final Map<UUID, Map<UUID, Display>> tracked = new HashMap<>();
    private final Map<UUID, Observer> observers = new HashMap<>();
    private long sequence;

    public ServerEntityProjections(MinecraftServer server, UUID generation, Function<Entity, PresentationState.Facts> facts) {
        this.server = server; this.generation = generation; this.facts = facts;
    }
    private static UUID instance(Entity entity) { return ((PresentationIdentity)entity).dndturn$presentationInstance(); }
    private static String dimension(Entity entity) { return entity.level().dimension().identifier().toString(); }
    private Map<UUID, Display> viewer(ServerPlayer viewer) {
        var identity = new Observer(instance(viewer), dimension(viewer));
        if (!identity.equals(observers.put(viewer.getUUID(), identity))) tracked.remove(viewer.getUUID());
        return tracked.computeIfAbsent(viewer.getUUID(), ignored -> new HashMap<>());
    }
    public void start(ServerPlayer viewer, Entity entity) {
        DebugDiagnostics.log("tracking start generation={} observer={} entity={} instance={} dimension={}", generation, viewer.getUUID(), entity.getUUID(), instance(entity), dimension(entity));
        viewer(viewer).put(entity.getUUID(), null);
        send(viewer, entity, true, null);
    }
    public void stop(ServerPlayer viewer, Entity entity) {
        var values = viewer(viewer);
        var previous = values.get(entity.getUUID());
        if (previous != null && !previous.instance().equals(instance(entity))) return;
        DebugDiagnostics.log("tracking stop generation={} observer={} entity={} instance={}", generation, viewer.getUUID(), entity.getUUID(), instance(entity));
        values.remove(entity.getUUID());
        send(viewer, entity, false, null);
    }
    public void removeViewer(UUID viewer) { tracked.remove(viewer); observers.remove(viewer); }
    public void clear() { tracked.clear(); observers.clear(); }
    public void sync() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) viewer(player).putIfAbsent(player.getUUID(), null);
        for (var entry : tracked.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            for (UUID id : Set.copyOf(entry.getValue().keySet())) {
                Entity entity = player.level().getEntity(id);
                if (entity == null) { entry.getValue().remove(id); continue; }
                send(player, entity, true, null);
            }
        }
        tracked.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        observers.keySet().retainAll(tracked.keySet());
    }
    public void movement(Entity entity, PresentationState.Movement evidence) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (player == entity || viewer(player).containsKey(entity.getUUID())) send(player, entity, true, evidence);
    }
    public void swing(LivingEntity entity, UUID encounter, UUID operation, InteractionHand hand) {
        // No event history: admission/ledger controls emission; tracking/reconnect sends only a baseline.
        DebugDiagnostics.log("swing admitted generation={} encounter={} entity={} instance={} operation={} hand={}", generation, encounter, entity.getUUID(), instance(entity), operation, hand);
        var animation = entity.getItemInHand(hand).getSwingAnimation();
        var event = new CombatNetwork.TacticalSwing(generation, encounter, entity.getUUID(), entity.getId(),
            instance(entity), operation, ++sequence, hand, animation);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != entity && !viewer(player).containsKey(entity.getUUID())) continue;
            if (!dimension(player).equals(dimension(entity))) continue;
            send(player, entity, true, null);
            if (NetworkRegistry.hasChannel(player.connection, CombatNetwork.TacticalSwing.TYPE.id()))
                PacketDistributor.sendToPlayer(player, event);
        }
    }
    private void send(ServerPlayer player, Entity entity, boolean active, PresentationState.Movement movement) {
        if (!dimension(player).equals(dimension(entity))) return;
        var values = viewer(player);
        var display = new Display(instance(entity), entity.getId(), dimension(entity), facts.apply(entity));
        if (active && movement == null && display.equals(values.get(entity.getUUID()))) return;
        if (CombatNetwork.sendEntitySimulation(player, new CombatNetwork.EntitySimulation(generation, ++sequence,
            entity.getUUID(), entity.getId(), display.instance(), display.dimension(), active, display.facts(), movement))
            && active) values.put(entity.getUUID(), display);
    }
}
