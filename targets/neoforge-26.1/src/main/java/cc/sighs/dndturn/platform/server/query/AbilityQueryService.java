package cc.sighs.dndturn.platform.server.query;

import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import java.util.*;

/** One synchronous read-only discovery operation. Never store this service across ticks,
 * world writes or execution boundaries. It owns temporary evidence, not rule state. */
public final class AbilityQueryService implements AutoCloseable {
    private MinecraftSnapshotCapture snapshots;
    private final Map<TargetIdentity, FactSlice> targets = new HashMap<>();
    private record TargetIdentity(UUID actor, UUID instance, long revision) {}

    public AbilityQueryService(MinecraftSnapshotCapture snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots);
    }
    public RuleResolver.Resolution evaluate(LiveActorContext actor, ActorSnapshot snapshot,
                                           StateView state, ActionIntent intent) {
        if (snapshots == null) throw new IllegalStateException("query closed");
        return AbilityAdapterRegistry.definitions().resolve(snapshots.capture(actor, snapshot, intent,
                state, intent.source(), ResolutionContext.Stage.PROPOSAL, this::targetFacts));
    }
    private FactSlice targetFacts(LiveActorContext target, ReadContract reads) {
        target.verifyCurrent();
        if (reads.keys().isEmpty()) return new FactSlice(Map.of(), Map.of());
        var key = new TargetIdentity(target.id(), target.instance(), target.state().revision());
        var known = targets.getOrDefault(key, new FactSlice(Map.of(), Map.of()));
        var missing = new HashSet<>(reads.keys());
        missing.removeAll(known.keys());
        if (missing.isEmpty()) return known.restrict(reads);
        var captured = snapshots.captureFacts(target, new ReadContract(missing));
        var reused = new HashSet<>(reads.keys());
        reused.removeAll(missing);
        var result = known.restrict(new ReadContract(reused)).merge(captured);
        // The cache is an optimization, not a new rejection condition for valid queries.
        if (targets.containsKey(key) || targets.size() < 128) {
            var combined = new HashSet<>(known.keys()); combined.addAll(missing);
            targets.put(key, combined.size() <= 128 && combined.stream().map(k -> k.id()).distinct().count() == combined.size()
                    ? known.merge(captured) : result);
        }
        return result;
    }
    @Override public void close() { targets.clear(); snapshots = null; }
}
