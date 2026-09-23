package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Owns connection projections, never membership or rule resources. */
public final class CombatSubscriptions {
    private record Body(boolean paused, boolean movement, boolean controlled, Object connection, Object level, Object player) {}
    private final MinecraftServer server;
    private final EncounterAuthority engine;
    private final UUID generation;
    private final Map<UUID, Body> bodies = new HashMap<>();
    private final Map<UUID, Long> bodySequences = new HashMap<>();
    private final Map<UUID, EncounterProtocol.EncounterState> pendingStates = new HashMap<>();

    public CombatSubscriptions(MinecraftServer server, EncounterAuthority engine, UUID generation) {
        this.server = server; this.engine = engine; this.generation = generation;
    }

    public void body(ServerPlayer player, boolean paused, boolean movement) {
        boolean controlled = !ActorControlPolicy.autonomousDecision(AuthorityProjection.actor(player)).allowed();
        Body next = new Body(paused, movement, controlled, player.connection, player.level(), player);
        if (next.equals(bodies.get(player.getUUID()))) return;
        long sequence = bodySequences.merge(player.getUUID(), 1L, Math::addExact);
        try {
            if (EncounterProtocol.sendBodyState(player, new EncounterProtocol.BodyState(generation, sequence, paused, movement, controlled)))
            {
                bodies.put(player.getUUID(), next);
            }
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Body projection failed for {}; retrying next server tick", player.getUUID(), failure);
        }
    }

    public void retainOnline(Set<UUID> online) { bodies.keySet().retainAll(online); }

    public void publish(ServerPlayer player, EncounterProtocol.EncounterState state) {
        pendingStates.put(player.getUUID(), state);
        try {
            if (EncounterProtocol.sendEncounterState(player, state))
                pendingStates.remove(player.getUUID(), state);
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Encounter projection failed for {}; queued authoritative resync", player.getUUID(), failure);
        }
    }

    public void flush() {
        for (var entry : Map.copyOf(pendingStates).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            var state = entry.getValue();
            if (player == null || state.active() && !state.encounterId().equals(engine.encounterOf(entry.getKey())))
                pendingStates.remove(entry.getKey());
            else publish(player, state);
        }
    }

    public void leave(UUID player) {
        bodies.remove(player);
        pendingStates.remove(player);
    }
    public void clear() { bodies.clear(); bodySequences.clear(); pendingStates.clear(); }
}
