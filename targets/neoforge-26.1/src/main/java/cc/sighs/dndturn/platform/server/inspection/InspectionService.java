package cc.sighs.dndturn.platform.server.inspection;

import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.platform.network.InspectionProtocol;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Server-lifetime read service; budgets contain values only, never connection or entity references. */
public final class InspectionService {
    private record Budget(long start, int used) {}
    private final EncounterRuntime service;
    private final Map<UUID, Budget> budgets = new HashMap<>();
    private long tick = -1;
    private int captures;
    public InspectionService(EncounterRuntime service) { this.service = service; }

    public InspectionProtocol.Reply query(ServerPlayer observer, InspectionProtocol.Query query) {
        var server = observer.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("inspection server thread required");
        if (server.getPlayerList().getPlayer(observer.getUUID()) != observer || observer.hasDisconnected()
                || !observer.isAlive() || observer.isSpectator()) return reject(query, InspectionProtocol.Status.UNAVAILABLE);
        long now = service.actionHost().planClock();
        if (tick != now) {
            tick = now; captures = 0;
            budgets.entrySet().removeIf(e -> now - e.getValue().start() >= 20);
        }
        var budget = budgets.getOrDefault(observer.getUUID(), new Budget(now, 0));
        if (budget.used() >= 4 || captures >= 32) return reject(query, InspectionProtocol.Status.RATE_LIMITED);
        budgets.put(observer.getUUID(), new Budget(budget.start(), budget.used() + 1));
        captures++;
        if (!service.matchesGeneration(query.generation()) || !query.encounter().equals(service.encounterOf(observer.getUUID())))
            return reject(query, InspectionProtocol.Status.STALE_SESSION);
        if (!query.encounter().equals(service.encounterOf(query.target()))) return reject(query, InspectionProtocol.Status.UNAVAILABLE);
        try {
            var state = service.state(query.encounter());
            if (state.phase() == EncounterPhase.ENDED) return reject(query, InspectionProtocol.Status.STALE_SESSION);
            var target = observer.level().getEntity(query.target());
            if (!(target instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()
                    || !state.members().containsKey(query.target())) return reject(query, InspectionProtocol.Status.UNAVAILABLE);
            if (living != observer && (living.isInvisibleTo(observer) || !loadedSight(observer, living)
                    || !observer.hasLineOfSight(living))) return reject(query, InspectionProtocol.Status.UNAVAILABLE);
            var view = service.actorStates().inspect(new LiveActorContext(observer), new LiveActorContext(living), state);
            return new InspectionProtocol.Reply(query, InspectionProtocol.Status.OK, view);
        } catch (RuntimeException unavailable) {
            // Never forward provider exceptions, hidden fact names or rule quarantine details.
            return reject(query, InspectionProtocol.Status.UNAVAILABLE);
        }
    }
    private static InspectionProtocol.Reply reject(InspectionProtocol.Query query, InspectionProtocol.Status status) {
        return new InspectionProtocol.Reply(query, status, null);
    }
    /** Vanilla sight is bounded to 128 blocks; preflight its chunk envelope without loading anything. */
    private static boolean loadedSight(ServerPlayer observer, LivingEntity target) {
        if (observer.getEyePosition().distanceToSqr(target.getEyePosition()) > 128 * 128) return false;
        // BlockGetter expands ray endpoints slightly; include their boundary neighbors.
        int minX = (int)Math.floor(Math.min(observer.getX(), target.getX()) - 0.001) >> 4;
        int maxX = (int)Math.floor(Math.max(observer.getX(), target.getX()) + 0.001) >> 4;
        int minZ = (int)Math.floor(Math.min(observer.getZ(), target.getZ()) - 0.001) >> 4;
        int maxZ = (int)Math.floor(Math.max(observer.getZ(), target.getZ()) + 0.001) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (observer.level().getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
}
