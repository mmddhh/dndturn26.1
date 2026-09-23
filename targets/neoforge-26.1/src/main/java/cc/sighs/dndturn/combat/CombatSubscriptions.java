package cc.sighs.dndturn.combat;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Owns connection projections and result delivery cursors, never membership or rule resources. */
public final class CombatSubscriptions {
    private record Body(boolean paused, boolean movement, Object connection, Object level, Object player) {}
    private final MinecraftServer server;
    private final CombatEngine engine;
    private final UUID generation;
    private final Map<UUID, Body> bodies = new HashMap<>();
    private final Map<UUID, Long> bodySequences = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> cursors = new HashMap<>();
    private final Map<UUID, CombatNetwork.EncounterState> pendingStates = new HashMap<>();

    public CombatSubscriptions(MinecraftServer server, CombatEngine engine, UUID generation) {
        this.server = server; this.engine = engine; this.generation = generation;
    }

    public void body(ServerPlayer player, boolean paused, boolean movement) {
        Body next = new Body(paused, movement, player.connection, player.level(), player);
        if (next.equals(bodies.get(player.getUUID()))) return;
        long sequence = bodySequences.merge(player.getUUID(), 1L, Math::addExact);
        try {
            if (CombatNetwork.sendBodyState(player, new CombatNetwork.BodyState(generation, sequence, paused, movement)))
                bodies.put(player.getUUID(), next);
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Body projection failed for {}; retrying next server tick", player.getUUID(), failure);
        }
    }

    public void retainOnline(Set<UUID> online) { bodies.keySet().retainAll(online); }

    public void publish(ServerPlayer player, CombatNetwork.EncounterState state) {
        pendingStates.put(player.getUUID(), state);
        try {
            if (CombatNetwork.sendEncounterState(player, state))
                pendingStates.remove(player.getUUID(), state);
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Encounter projection failed for {}; queued authoritative resync", player.getUUID(), failure);
        }
    }

    public void subscribe(ServerPlayer player, UUID encounter) {
        Map<UUID, Integer> positions = cursors.computeIfAbsent(encounter, ignored -> new HashMap<>());
        positions.putIfAbsent(player.getUUID(), 0);
        deliver(player, encounter, positions);
    }

    public void resync(ServerPlayer player, UUID encounter, int from) {
        authorize(player, encounter);
        if (from < 0 || from > engine.resultPage(encounter, 0, 1).total())
            throw new IllegalArgumentException("invalid result sequence");
        Map<UUID, Integer> positions = cursors.computeIfAbsent(encounter, ignored -> new HashMap<>());
        positions.put(player.getUUID(), from);
        deliver(player, encounter, positions);
    }

    private void authorize(ServerPlayer player, UUID encounter) {
        if (!encounter.equals(engine.encounterOf(player.getUUID())))
            throw new IllegalStateException("result subscription revoked");
    }

    private void deliver(ServerPlayer player, UUID encounter, Map<UUID, Integer> positions) {
        authorize(player, encounter);
        int from = positions.get(player.getUUID());
        try {
            var page = engine.resultPage(encounter, from, 32);
            for (var result : page.results()) {
                if (!CombatNetwork.sendResultNotice(player, new CombatNetwork.ResultNotice(generation, encounter,
                    from, result.snapshot().operationId(), result.outcome().code(), result.reason(),
                    result.actualMovementTicks(), result.actualDamage(), CombatNetwork.HitNotice.from(result.damageTrace())))) break;
                positions.put(player.getUUID(), ++from);
            }
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Result delivery failed for {}; retaining cursor {}", player.getUUID(), from, failure);
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
        for (var entry : cursors.entrySet()) {
            entry.getValue().keySet().removeIf(id -> !entry.getKey().equals(engine.encounterOf(id))
                || server.getPlayerList().getPlayer(id) == null);
            for (UUID id : Set.copyOf(entry.getValue().keySet()))
                deliver(server.getPlayerList().getPlayer(id), entry.getKey(), entry.getValue());
        }
    }

    public void leave(UUID encounter, UUID player) {
        Map<UUID, Integer> positions = cursors.get(encounter);
        if (positions != null) positions.remove(player);
        bodies.remove(player);
        pendingStates.remove(player);
    }
    public void end(UUID encounter) { cursors.remove(encounter); }
    public void clear() { cursors.clear(); bodies.clear(); bodySequences.clear(); pendingStates.clear(); }
}
