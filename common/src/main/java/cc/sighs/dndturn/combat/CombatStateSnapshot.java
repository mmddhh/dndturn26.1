package cc.sighs.dndturn.combat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Versioned, platform-free save boundary. Every collection is detached from live rule state. */
public record CombatStateSnapshot(int schemaVersion, int movementTicksPerTurn, int environmentTicks,
                                  List<EncounterState> encounters, List<ClosedState> closed,
                                  Map<UUID, UUID> mergedInto, List<MergeReceiptState> mergeReceipts) {
    public static final int CURRENT_SCHEMA = 5;

    public CombatStateSnapshot {
        if (schemaVersion != CURRENT_SCHEMA || movementTicksPerTurn < 1 || environmentTicks < 1)
            throw new IllegalArgumentException("unsupported combat snapshot schema or budget");
        encounters = List.copyOf(encounters);
        closed = List.copyOf(closed);
        mergedInto = Map.copyOf(mergedInto);
        mergeReceipts = List.copyOf(mergeReceipts);
    }

    public record RegionState(String dimension, EncounterRegion.Discovery discovery,
                              List<EncounterRegion.Anchor> anchors, double radius, long version) {
        public RegionState {
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(discovery);
            anchors = List.copyOf(anchors);
        }
        public static RegionState capture(EncounterRegion region) {
            return region == null ? null : new RegionState(region.dimension(), region.discovery(),
                region.anchors(), region.radius(), region.version());
        }
        public EncounterRegion restore() {
            return EncounterRegion.generate(dimension, discovery, anchors, radius, version);
        }
    }

    public record MemberState(UUID id, int initiative, int tieBreak, long eligibleRound,
                              int movementTicks, boolean action, boolean reaction,
                              boolean dodging, boolean disengaged,
                              boolean preserveSpentActionOnNextTurn) {}

    public record Hostility(UUID source, UUID target) {}

    public record PermitState(CombatEngine.EffectPermit permit, int used,
                              long effectiveValidThroughRound) {
        public PermitState {
            Objects.requireNonNull(permit);
            if (used < 0 || used > permit.maxUses()) throw new IllegalArgumentException("permit usage");
        }
    }

    public record EncounterState(UUID id, CombatEngine.Bounds bounds, RegionState region,
                                 EncounterPhase phase, long version, long structuralRevision,
                                 long round, int cursor, int environmentRemaining,
                                 UUID authorizedEnvironmentStep, boolean environmentStepAuthorized,
                                 Integer boundaryInitiative, Integer boundaryTieBreak,
                                 List<MemberState> members, List<Hostility> hostile,
                                 List<UUID> order, List<OperationRecord.Result> history,
                                 Map<UUID, OperationRecord.Snapshot> pending,
                                 Map<UUID, OperationRecord.Snapshot> causes,
                                 Set<UUID> attackersWithRegisteredAttempt,
                                 List<PermitState> permits, Map<UUID, Integer> childrenByRoot,
                                 Set<UUID> completedEnvironmentSteps,
                                 Set<UUID> causalMergeNeighbors,
                                 int movementTicksPerTurn, int environmentTicks) {
        public EncounterState {
            Objects.requireNonNull(id);
            Objects.requireNonNull(phase);
            if (movementTicksPerTurn < 1 || environmentTicks < 1)
                throw new IllegalArgumentException("invalid captured encounter budgets");
            members = List.copyOf(members);
            hostile = List.copyOf(hostile);
            order = List.copyOf(order);
            history = List.copyOf(history);
            pending = Map.copyOf(pending);
            causes = Map.copyOf(causes);
            attackersWithRegisteredAttempt = Set.copyOf(attackersWithRegisteredAttempt);
            permits = List.copyOf(permits);
            childrenByRoot = Map.copyOf(childrenByRoot);
            completedEnvironmentSteps = Set.copyOf(completedEnvironmentSteps);
            causalMergeNeighbors = Set.copyOf(causalMergeNeighbors);
        }
    }

    public record ClosedState(UUID id, CombatEngine.Bounds bounds, RegionState region,
                              long version, long round, List<UUID> order,
                              Map<UUID, CombatEngine.MemberView> members,
                              List<OperationRecord.Result> results) {
        public ClosedState {
            Objects.requireNonNull(id);
            order = List.copyOf(order);
            members = Map.copyOf(members);
            results = List.copyOf(results);
        }
    }

    public record MergePlanState(Set<UUID> encounters, Map<UUID, Long> versions,
                                 Map<UUID, Long> structuralRevisions, UUID primary,
                                 long targetEnvironmentRound, RegionState projectedRegion) {
        public MergePlanState {
            encounters = Set.copyOf(encounters);
            versions = Map.copyOf(versions);
            structuralRevisions = Map.copyOf(structuralRevisions);
        }
        public static MergePlanState capture(CombatEngine.MergePlan plan) {
            return new MergePlanState(plan.encounters(), plan.versions(),
                plan.structuralRevisions(), plan.primary(), plan.targetEnvironmentRound(),
                RegionState.capture(plan.projectedRegion()));
        }
        public CombatEngine.MergePlan restore() {
            return new CombatEngine.MergePlan(encounters, versions, structuralRevisions,
                primary, targetEnvironmentRound,
                projectedRegion == null ? null : projectedRegion.restore());
        }
    }

    public record MergeReceiptState(UUID operationId, MergePlanState plan, RegionState region,
                                    long serverTick, UUID primary) {}
}
