package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.TurnParticipant;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.network.RegionBoundaryProtocol; // DNDTURN-TEMP-BOUNDARY-VIZ
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Owns publication sequence, session sequence and connection subscriptions for one server generation. */
public final class ProjectionPublisher {
    private final MinecraftServer server;
    private final EncounterAuthority engine;
    private final UUID generation;
    private final java.util.function.Function<UUID, Entity> resolve;
    private final CombatSubscriptions subscriptions;
    private final Map<UUID, Long> sessionSequences = new HashMap<>();
    private final Map<UUID, Long> projectionRevisions = new HashMap<>();
    private long nextSessionSequence;
    public ProjectionPublisher(MinecraftServer server, EncounterAuthority engine, UUID generation,
                               java.util.function.Function<UUID, Entity> resolve) {
        this.server = server; this.engine = engine; this.generation = generation; this.resolve = resolve;
        subscriptions = new CombatSubscriptions(server, engine, generation);
    }
    public void restore(long next, Map<UUID, Long> sessions) {
        if (!sessionSequences.isEmpty() || nextSessionSequence != 0) throw new IllegalStateException("projection already initialized");
        nextSessionSequence = next; sessionSequences.putAll(sessions);
    }
    public long nextSequence() { return Math.addExact(nextSessionSequence, 1); }
    public long lastSequence() { return nextSessionSequence; }
    public Map<UUID, Long> sessions() { return Map.copyOf(sessionSequences); }
    public Long sequence(UUID encounter) { return sessionSequences.get(encounter); }
    public void bind(UUID encounter, long sequence) {
        sessionSequences.put(encounter, Math.max(sessionSequences.getOrDefault(encounter, 0L), sequence));
        nextSessionSequence = Math.max(nextSessionSequence, sequence);
    }
    public long nextRevision(UUID encounter) { return Math.addExact(projectionRevisions.getOrDefault(encounter, 0L), 1); }
    public void flush() { subscriptions.flush(); }
    public void leave(UUID actor) { subscriptions.leave(actor); }
    public void retainOnline(Set<UUID> actors) { subscriptions.retainOnline(actors); }
    public void body(ServerPlayer player, boolean paused, boolean movement) { subscriptions.body(player, paused, movement); }
    public void close() { subscriptions.clear(); sessionSequences.clear(); projectionRevisions.clear(); }
    public void publish(EncounterAuthority.StateView state, java.util.function.Function<UUID, UUID> movementOperation, boolean ready) {
        long projectionRevision = Math.addExact(projectionRevisions.getOrDefault(state.id(), 0L), 1);
        List<EncounterProtocol.MemberNotice> roster = new ArrayList<>(state.members().values().stream()
            .sorted(java.util.Comparator.comparingInt(EncounterAuthority.MemberView::initiative).reversed()
                .thenComparingInt(EncounterAuthority.MemberView::tieBreak)
                .thenComparing(EncounterAuthority.MemberView::id))
            .map(member -> {
                Entity entity = resolve.apply(member.id());
                return new EncounterProtocol.MemberNotice(member.id(),
                    entity == null ? member.id().toString() : entity.getName().getString(),
                    member.initiative(), member.eligibleRound(), member.dodging(), member.disengaged(),
                    member.movementTicks(), member.action(), member.reaction());
            }).toList());
        roster.add(new EncounterProtocol.MemberNotice(state.environment().id(), TurnParticipant.Kind.ENVIRONMENT,
            "环境", -1, 0, false, false, state.environmentRemaining(), false, false));
        projectionRevisions.put(state.id(), projectionRevision);
        for (var entry : state.members().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            EncounterAuthority.MemberView member = entry.getValue();
            subscriptions.publish(player, new EncounterProtocol.EncounterState(generation,
                state.id(), sessionSequences.getOrDefault(state.id(), 0L), true,
                state.version(), state.phase(), state.round(),
                state.current(), state.region().version(), member.movementTicks(),
                member.action(), member.reaction(), state.environmentRemaining(),
                movementOperation.apply(player.getUUID()) != null,
                movementOperation.apply(player.getUUID()) != null
                    ? movementOperation.apply(player.getUUID()) : null, roster,
                engine.resultPage(state.id(), 0, 1).total(), ready, projectionRevision,
                engine.movementTicksPerTurn(state.id())));
            // DNDTURN-TEMP-BOUNDARY-VIZ: expose the encounter field to the client for the debug overlay.
            RegionBoundaryProtocol.send(player, state.region());
        }
    }

    public void clear(ServerPlayer player, UUID encounterId, long version) {
        long revision = projectionRevisions.merge(encounterId, 1L, Math::addExact);
        subscriptions.publish(player, new EncounterProtocol.EncounterState(
            generation, encounterId, sessionSequences.getOrDefault(encounterId, 0L), false,
            version, EncounterPhase.ENDED,
            0, null, 0, 0, false, false, 0, false, null, List.of(), 0, false, revision));
        RegionBoundaryProtocol.clear(player); // DNDTURN-TEMP-BOUNDARY-VIZ
    }
}
