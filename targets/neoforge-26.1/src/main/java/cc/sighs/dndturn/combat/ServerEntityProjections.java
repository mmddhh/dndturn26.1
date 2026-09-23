package cc.sighs.dndturn.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Tracking permission and last delivered display state; never owns simulation authority. */
public final class ServerEntityProjections {
    private final MinecraftServer server;
    private final UUID generation;
    private final Predicate<Entity> paused;
    private final Map<UUID, Map<UUID, Boolean>> tracked = new HashMap<>();
    private long sequence;

    public ServerEntityProjections(MinecraftServer server, UUID generation, Predicate<Entity> paused) {
        this.server = server;
        this.generation = generation;
        this.paused = paused;
    }

    public void start(ServerPlayer viewer, Entity entity) {
        tracked.computeIfAbsent(viewer.getUUID(), ignored -> new HashMap<>()).put(entity.getUUID(), null);
        send(viewer, entity, true);
    }

    public void stop(ServerPlayer viewer, Entity entity) {
        Map<UUID, Boolean> values = tracked.get(viewer.getUUID());
        if (values != null) values.remove(entity.getUUID());
        send(viewer, entity, false);
    }

    public void removeViewer(UUID viewer) { tracked.remove(viewer); }
    public void clear() { tracked.clear(); }

    public void sync() {
        for (var viewerEntry : tracked.entrySet()) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerEntry.getKey());
            if (viewer == null) continue;
            for (UUID id : java.util.Set.copyOf(viewerEntry.getValue().keySet())) {
                Entity entity = viewer.level().getEntity(id);
                if (entity == null) { viewerEntry.getValue().remove(id); continue; }
                send(viewer, entity, true);
            }
        }
        tracked.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private void send(ServerPlayer viewer, Entity entity, boolean active) {
        boolean hold = active && paused.test(entity);
        Map<UUID, Boolean> values = tracked.get(viewer.getUUID());
        if (active && values != null && Boolean.valueOf(hold).equals(values.get(entity.getUUID()))) return;
        long next = Math.addExact(sequence, 1);
        sequence = next;
        if (CombatNetwork.sendEntitySimulation(viewer, new CombatNetwork.EntitySimulation(generation, next,
            entity.getUUID(), entity.level().dimension().identifier().toString(), active, hold))
            && active && values != null) values.put(entity.getUUID(), hold);
    }
}
