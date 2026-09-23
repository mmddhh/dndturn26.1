package cc.sighs.dndturn.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Single server-thread owner of encounter rules and immutable operation results. */
public final class CombatEngine {
    public enum DomainKind { WORLD, ENCOUNTER }
    public record Domain(DomainKind kind, UUID encounterId) {
        public Domain {
            Objects.requireNonNull(kind);
            if ((kind == DomainKind.ENCOUNTER) != (encounterId != null)) throw new IllegalArgumentException("domain id");
        }
        public static Domain world() { return new Domain(DomainKind.WORLD, null); }
        public static Domain encounter(UUID id) { return new Domain(DomainKind.ENCOUNTER, id); }
    }
    /** Search envelope for the unfinished tactical prototype, not the final encounter area. */
    public record Bounds(String dimension, GridCell min, GridCell max) {
        public Bounds {
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(min);
            Objects.requireNonNull(max);
            if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) throw new IllegalArgumentException("bounds");
        }
        public boolean contains(GridCell cell) {
            return cell.x() >= min.x() && cell.x() <= max.x()
                && cell.y() >= min.y() && cell.y() <= max.y()
                && cell.z() >= min.z() && cell.z() <= max.z();
        }
    }
    public record MemberView(UUID id, int initiative, int tieBreak, int movementTicks, boolean action,
                             boolean reaction, boolean dodging, boolean disengaged, long eligibleRound) {}
    public record View(UUID id, EncounterPhase phase, long version, Bounds bounds, List<UUID> order,
                       UUID current, Map<UUID, MemberView> members, List<OperationRecord.Result> results,
                       EncounterRegion region, long round) {
        public View { order = List.copyOf(order); members = Map.copyOf(members); results = List.copyOf(results); }
    }
    /** Turn state without copying the ever-growing result history. */
    public record StateView(UUID id, EncounterPhase phase, long version, List<UUID> order, UUID current,
                            Map<UUID, MemberView> members, long round, int environmentRemaining,
                            EncounterRegion region) {
        public StateView { order = List.copyOf(order); members = Map.copyOf(members); }
    }
    public record ResultPage(int fromIndex, int nextIndex, int total, List<OperationRecord.Result> results) {
        public ResultPage { results = List.copyOf(results); }
    }
    /** Captured choice for a connected overlap component; never recompute it after turns advance. */
    public record MergePlan(Set<UUID> encounters, Map<UUID, Long> versions,
                            Map<UUID, Long> structuralRevisions, UUID primary,
                            long targetEnvironmentRound, EncounterRegion projectedRegion) {
        public MergePlan {
            encounters = Set.copyOf(encounters);
            versions = Map.copyOf(versions);
            structuralRevisions = Map.copyOf(structuralRevisions);
            Objects.requireNonNull(primary);
            if (encounters.size() < 2 || !encounters.equals(versions.keySet())
                || !encounters.equals(structuralRevisions.keySet()) || !encounters.contains(primary)
                || targetEnvironmentRound < 0)
                throw new IllegalArgumentException("merge plan participants");
        }
    }
    private static final class Member {
        final UUID id;
        final ActionEconomy economy;
        int initiative;
        int tieBreak;
        long eligibleRound;
        boolean dodging;
        boolean disengaged;
        boolean preserveSpentActionOnNextTurn;
        Member(UUID id, int movementTicks) { this.id = id; economy = new ActionEconomy(movementTicks); }
        MemberView view() { return new MemberView(id, initiative, tieBreak, economy.movementTicks(),
            economy.hasAction(), economy.hasReaction(), dodging, disengaged, eligibleRound); }
    }
    private static final class Encounter {
        final UUID id;
        final int movementTicksPerTurn;
        final int environmentTicks;
        EncounterRegion region;
        EncounterPhase phase = EncounterPhase.CANDIDATE;
        long version;
        long structuralRevision;
        long round;
        int cursor;
        int environmentRemaining;
        UUID authorizedEnvironmentStep;
        /** Once any simulation step is authorized, this entry can no longer accept a merge. */
        boolean environmentStepAuthorized;
        InitiativeRoll boundaryPriority;
        final Map<UUID, Member> members = new LinkedHashMap<>();
        final Set<Relation> hostile = new HashSet<>();
        /** A projectile attack can connect sessions before their fixed regions overlap. */
        final Set<UUID> causalMergeNeighbors = new HashSet<>();
        final List<UUID> order = new ArrayList<>();
        final Map<UUID, OperationRecord.Result> finalResults = new HashMap<>();
        final Map<UUID, Map<Integer, OperationRecord.Result>> stepResults = new HashMap<>();
        final Map<UUID, OperationRecord.Snapshot> pending = new HashMap<>();
        /** Accepted identities survive completion so delayed effects keep their original cause. */
        final Map<UUID, OperationRecord.Snapshot> causes = new HashMap<>();
        /** The first accepted attack consumes this Encounter-scoped entitlement, even on a miss. */
        final Set<UUID> attackersWithRegisteredAttempt = new HashSet<>();
        final Map<UUID, PermitState> permits = new HashMap<>();
        final Map<UUID, Integer> childrenByRoot = new HashMap<>();
        final Set<UUID> completedEnvironmentSteps = new HashSet<>();
        final List<OperationRecord.Result> history = new ArrayList<>();
        Encounter(UUID id, EncounterRegion region, int movementTicksPerTurn, int environmentTicks) {
            this.id = id; this.region = Objects.requireNonNull(region, "active encounter requires an exact region");
            if (movementTicksPerTurn < 1 || environmentTicks < 1) throw new IllegalArgumentException("tick budget");
            this.movementTicksPerTurn = movementTicksPerTurn;
            this.environmentTicks = environmentTicks;
        }
    }
    private record Relation(UUID source, UUID target) {}
    private record InitiativeRoll(int initiative, int tieBreak) {}
    private static final class PermitState {
        final EffectPermit permit;
        int used;
        long effectiveValidThroughRound;
        PermitState(EffectPermit permit) {
            this.permit = permit;
            this.effectiveValidThroughRound = permit.validThroughRound();
        }
    }

    /** A server-issued capability for a particular causal effect, never a client intent. */
    public record EffectPermit(UUID id, UUID encounterId, UUID parentId, UUID source,
                               Set<UUID> targets, Set<EncounterPhase> phases, int maxUses, long validThroughRound) {
        public EffectPermit {
            Objects.requireNonNull(id);
            Objects.requireNonNull(encounterId);
            Objects.requireNonNull(parentId);
            Objects.requireNonNull(source);
            targets = Set.copyOf(targets);
            phases = Set.copyOf(phases);
            if (targets.isEmpty() || phases.isEmpty() || maxUses < 1 || validThroughRound < 0
                || phases.contains(EncounterPhase.ENDED)) throw new IllegalArgumentException("effect permit scope");
        }
    }
    private final Map<UUID, Encounter> encounters = new LinkedHashMap<>();
    private final Map<UUID, View> closedEncounters = new HashMap<>();
    private final Map<UUID, UUID> membership = new HashMap<>();
    private record MergeReceipt(MergePlan plan, EncounterRegion region, long serverTick, UUID primary) {}
    private final Map<UUID, MergeReceipt> committedMerges = new HashMap<>();
    private final Map<UUID, UUID> mergedInto = new HashMap<>();
    private final Random random;
    private UUID observationEpoch;

    /** Bind this live engine to one server session; previously published snapshots stay unchanged. */
    public void bindObservationEpoch(UUID epoch) {
        Objects.requireNonNull(epoch);
        if (observationEpoch != null) throw new IllegalStateException("observation epoch already bound");
        observationEpoch = epoch;
    }
    private final int movementTicksPerTurn;
    private final int environmentTicks;

    public CombatEngine(Random random, int movementTicksPerTurn, int environmentTicks) {
        this.random = Objects.requireNonNull(random);
        if (movementTicksPerTurn < 1 || environmentTicks < 1) throw new IllegalArgumentException("tick budget");
        this.movementTicksPerTurn = movementTicksPerTurn;
        this.environmentTicks = environmentTicks;
    }

    public int movementTicksPerTurn() { return movementTicksPerTurn; }
    public int environmentTicks() { return environmentTicks; }
    public int movementTicksPerTurn(UUID encounterId) { return require(encounterId).movementTicksPerTurn; }
    public int environmentTicks(UUID encounterId) { return require(encounterId).environmentTicks; }
    public UUID encounterOf(UUID member) { return membership.get(member); }
    public UUID canonicalEncounterId(UUID id) {
        Objects.requireNonNull(id);
        UUID current = id;
        while (mergedInto.containsKey(current)) current = mergedInto.get(current);
        return current;
    }

    public Set<UUID> encounterIds() { return Set.copyOf(encounters.keySet()); }
    public View closedView(UUID id) { return closedEncounters.get(id); }

    public record Revision(Map<UUID, Long> versions, Map<UUID, Long> structures,
                           int closedCount, int aliasCount, int receiptCount) {}

    public Revision revision() {
        Map<UUID, Long> versions = new HashMap<>(), structures = new HashMap<>();
        for (Encounter encounter : encounters.values()) {
            versions.put(encounter.id, encounter.version);
            structures.put(encounter.id, encounter.structuralRevision);
        }
        return new Revision(Map.copyOf(versions), Map.copyOf(structures),
            closedEncounters.size(), mergedInto.size(), committedMerges.size());
    }

    public CombatStateSnapshot exportSnapshot() {
        List<CombatStateSnapshot.EncounterState> active = new ArrayList<>();
        for (Encounter encounter : encounters.values()) {
            List<CombatStateSnapshot.MemberState> members = new ArrayList<>();
            for (Member member : encounter.members.values()) members.add(new CombatStateSnapshot.MemberState(
                member.id, member.initiative, member.tieBreak, member.eligibleRound,
                member.economy.movementTicks(), member.economy.hasAction(),
                member.economy.hasReaction(), member.dodging, member.disengaged,
                member.preserveSpentActionOnNextTurn));
            List<CombatStateSnapshot.Hostility> hostile = encounter.hostile.stream()
                .map(relation -> new CombatStateSnapshot.Hostility(relation.source(), relation.target()))
                .toList();
            List<CombatStateSnapshot.PermitState> permits = encounter.permits.values().stream()
                .map(permit -> new CombatStateSnapshot.PermitState(permit.permit, permit.used,
                    permit.effectiveValidThroughRound)).toList();
            active.add(new CombatStateSnapshot.EncounterState(encounter.id, null,
                CombatStateSnapshot.RegionState.capture(encounter.region), encounter.phase,
                encounter.version, encounter.structuralRevision, encounter.round, encounter.cursor,
                encounter.environmentRemaining, encounter.authorizedEnvironmentStep,
                encounter.environmentStepAuthorized,
                encounter.boundaryPriority == null ? null : encounter.boundaryPriority.initiative(),
                encounter.boundaryPriority == null ? null : encounter.boundaryPriority.tieBreak(),
                members, hostile, encounter.order, encounter.history, encounter.pending,
                encounter.causes, encounter.attackersWithRegisteredAttempt, permits,
                encounter.childrenByRoot, encounter.completedEnvironmentSteps,
                encounter.causalMergeNeighbors, encounter.movementTicksPerTurn, encounter.environmentTicks));
        }
        List<CombatStateSnapshot.ClosedState> closed = new ArrayList<>();
        for (View view : closedEncounters.values()) closed.add(new CombatStateSnapshot.ClosedState(
            view.id(), view.bounds(), CombatStateSnapshot.RegionState.capture(view.region()),
            view.version(), view.round(), view.order(), view.members(), view.results()));
        List<CombatStateSnapshot.MergeReceiptState> receipts = new ArrayList<>();
        for (var entry : committedMerges.entrySet()) {
            MergeReceipt receipt = entry.getValue();
            receipts.add(new CombatStateSnapshot.MergeReceiptState(entry.getKey(),
                CombatStateSnapshot.MergePlanState.capture(receipt.plan()),
                CombatStateSnapshot.RegionState.capture(receipt.region()),
                receipt.serverTick(), receipt.primary()));
        }
        return new CombatStateSnapshot(CombatStateSnapshot.CURRENT_SCHEMA,
            movementTicksPerTurn, environmentTicks, active, closed, mergedInto, receipts);
    }

    /** Rebuild indexes from detached values; no platform action or pending effect is replayed. */
    public static CombatEngine restoreSnapshot(CombatStateSnapshot saved, Random random) {
        Objects.requireNonNull(saved);
        CombatEngine engine = new CombatEngine(random, saved.movementTicksPerTurn(), saved.environmentTicks());
        engine.mergedInto.putAll(saved.mergedInto());
        for (CombatStateSnapshot.EncounterState state : saved.encounters()) {
            if (state.phase() == EncounterPhase.ENDED || state.version() < 0 || state.round() < 0
                || state.environmentRemaining() < 0 || state.structuralRevision() < 0
                || (state.boundaryInitiative() == null) != (state.boundaryTieBreak() == null))
                throw new IllegalArgumentException("invalid active encounter snapshot");
            Encounter encounter = new Encounter(state.id(),
                state.region() == null ? null : state.region().restore(),
                state.movementTicksPerTurn(), state.environmentTicks());
            if (engine.encounters.putIfAbsent(state.id(), encounter) != null)
                throw new IllegalArgumentException("duplicate encounter snapshot");
            encounter.phase = state.phase();
            encounter.version = state.version();
            encounter.structuralRevision = state.structuralRevision();
            encounter.round = state.round();
            encounter.cursor = state.cursor();
            encounter.environmentRemaining = state.environmentRemaining();
            encounter.authorizedEnvironmentStep = state.authorizedEnvironmentStep();
            encounter.environmentStepAuthorized = state.environmentStepAuthorized();
            if (state.boundaryInitiative() != null)
                encounter.boundaryPriority = new InitiativeRoll(state.boundaryInitiative(),
                    state.boundaryTieBreak());
            for (CombatStateSnapshot.MemberState value : state.members()) {
                if (value.id() == null || value.eligibleRound() < 0 || value.movementTicks() < 0
                    || value.eligibleRound() > state.round() && value.eligibleRound() - state.round() != 1)
                    throw new IllegalArgumentException("invalid member snapshot");
                Member member = new Member(value.id(), value.movementTicks());
                member.initiative = value.initiative();
                member.tieBreak = value.tieBreak();
                member.eligibleRound = value.eligibleRound();
                if (!value.action()) member.economy.spendAction();
                if (!value.reaction()) member.economy.spendReaction();
                member.dodging = value.dodging();
                member.disengaged = value.disengaged();
                member.preserveSpentActionOnNextTurn = value.preserveSpentActionOnNextTurn();
                if (encounter.members.putIfAbsent(member.id, member) != null
                    || engine.membership.putIfAbsent(member.id, encounter.id) != null)
                    throw new IllegalArgumentException("duplicate member snapshot");
            }
            Set<UUID> eligible = new HashSet<>();
            for (Member member : encounter.members.values())
                if (member.eligibleRound <= encounter.round) eligible.add(member.id);
            boolean environment = encounter.phase == EncounterPhase.ENVIRONMENT;
            if (encounter.members.isEmpty()
                || (environment ? state.cursor() != 0
                    : state.order().isEmpty() || state.cursor() < 0 || state.cursor() >= state.order().size())
                || !eligible.equals(new HashSet<>(state.order()))
                || !encounter.members.keySet().containsAll(state.order())
                || new HashSet<>(state.order()).size() != state.order().size())
                throw new IllegalArgumentException("invalid turn order snapshot");
            encounter.order.addAll(state.order());
            for (CombatStateSnapshot.Hostility relation : state.hostile()) {
                if (!encounter.members.containsKey(relation.source())
                    || !encounter.members.containsKey(relation.target())
                    || relation.source().equals(relation.target()))
                    throw new IllegalArgumentException("invalid hostility snapshot");
                encounter.hostile.add(new Relation(relation.source(), relation.target()));
            }
            encounter.history.addAll(state.history());
            for (OperationRecord.Result result : state.history()) {
                UUID operationId = result.snapshot().operationId();
                if (encounter.stepResults.computeIfAbsent(operationId, ignored -> new HashMap<>())
                    .putIfAbsent(result.step(), result) != null)
                    throw new IllegalArgumentException("duplicate result step snapshot");
                if (result.terminal() && encounter.finalResults.putIfAbsent(operationId, result) != null)
                    throw new IllegalArgumentException("duplicate terminal result snapshot");
            }
            encounter.pending.putAll(state.pending());
            encounter.causes.putAll(state.causes());
            for (var entry : encounter.pending.entrySet())
                if (!entry.getKey().equals(entry.getValue().operationId())
                    || !entry.getValue().equals(encounter.causes.get(entry.getKey()))
                    || encounter.finalResults.containsKey(entry.getKey()))
                    throw new IllegalArgumentException("invalid pending operation snapshot");
            encounter.attackersWithRegisteredAttempt.addAll(state.attackersWithRegisteredAttempt());
            for (CombatStateSnapshot.PermitState value : state.permits()) {
                PermitState permit = new PermitState(value.permit());
                permit.used = value.used();
                permit.effectiveValidThroughRound = value.effectiveValidThroughRound();
                if (encounter.permits.putIfAbsent(value.permit().id(), permit) != null)
                    throw new IllegalArgumentException("duplicate permit snapshot");
            }
            encounter.childrenByRoot.putAll(state.childrenByRoot());
            encounter.completedEnvironmentSteps.addAll(state.completedEnvironmentSteps());
            encounter.causalMergeNeighbors.addAll(state.causalMergeNeighbors());
        }
        for (Encounter encounter : engine.encounters.values()) {
            for (UUID neighborId : encounter.causalMergeNeighbors) {
                Encounter neighbor = engine.encounters.get(neighborId);
                if (neighbor == null || neighbor == encounter
                    || !neighbor.causalMergeNeighbors.contains(encounter.id))
                    throw new IllegalArgumentException("invalid causal merge link snapshot");
            }
        }
        for (CombatStateSnapshot.ClosedState state : saved.closed()) {
            if (engine.encounters.containsKey(state.id()) || state.version() < 0 || state.round() < 0)
                throw new IllegalArgumentException("invalid closed encounter snapshot");
            View view = new View(state.id(), EncounterPhase.ENDED, state.version(), state.bounds(),
                state.order(), null, state.members(), state.results(),
                state.region() == null ? null : state.region().restore(), state.round());
            if (engine.closedEncounters.putIfAbsent(state.id(), view) != null)
                throw new IllegalArgumentException("duplicate closed encounter snapshot");
        }
        for (var entry : engine.mergedInto.entrySet()) {
            Set<UUID> visited = new HashSet<>();
            UUID destination = entry.getValue();
            while (engine.mergedInto.containsKey(destination) && visited.add(destination))
                destination = engine.mergedInto.get(destination);
            if (!engine.closedEncounters.containsKey(entry.getKey())
                || entry.getKey().equals(entry.getValue()) || !visited.add(destination)
                || !engine.encounters.containsKey(destination)
                    && !engine.closedEncounters.containsKey(destination))
                throw new IllegalArgumentException("invalid encounter alias snapshot");
        }
        for (CombatStateSnapshot.MergeReceiptState value : saved.mergeReceipts()) {
            MergeReceipt receipt = new MergeReceipt(value.plan().restore(), value.region().restore(),
                value.serverTick(), value.primary());
            if (engine.committedMerges.putIfAbsent(value.operationId(), receipt) != null)
                throw new IllegalArgumentException("duplicate merge receipt snapshot");
        }
        return engine;
    }

    public OperationRecord.Result resultFor(UUID encounterId, UUID operationId) {
        Objects.requireNonNull(encounterId);
        Objects.requireNonNull(operationId);
        Encounter active = encounters.get(encounterId);
        if (active != null) return active.finalResults.get(operationId);
        View closed = closedEncounters.get(encounterId);
        if (closed == null) return null;
        List<OperationRecord.Result> results = closed.results();
        for (int i = results.size() - 1; i >= 0; i--) {
            OperationRecord.Result result = results.get(i);
            if (result.snapshot().operationId().equals(operationId)) return result;
        }
        return null;
    }

    /** Read-only status for an executing cross-tick operation; does not authorize a retry. */
    public OperationRecord.Snapshot pendingOperation(UUID encounterId, UUID operationId) {
        Objects.requireNonNull(operationId);
        Encounter encounter = require(encounterId);
        return encounter.pending.get(operationId);
    }

    /** Old epoch capabilities cannot authorize a newly started server process. */
    public int revokeRestoredPermits(UUID encounterId) {
        Encounter encounter = require(encounterId);
        int count = encounter.permits.size();
        if (count > 0) {
            encounter.version = Math.addExact(encounter.version, 1);
            encounter.permits.clear();
        }
        return count;
    }

    /** Settle work whose world effects cannot be proven after restart, without replaying it. */
    public int failRestoredWork(UUID encounterId, long serverTick, String reason) {
        Objects.requireNonNull(reason);
        Encounter encounter = require(encounterId);
        int count = 0;
        for (UUID operationId : pendingChildrenFirst(encounter).stream().map(OperationRecord.Snapshot::operationId).toList()) {
            int step = encounter.stepResults.getOrDefault(operationId, Map.of()).size();
            publish(encounterId, operationId, step, OperationRecord.Outcome.UNKNOWN,
                reason, 0, 0, true);
            count++;
        }
        if (encounter.authorizedEnvironmentStep != null) {
            failEnvironmentStep(encounterId, encounter.authorizedEnvironmentStep, serverTick, reason);
            count++;
        }
        return count;
    }

    /** Publish unverified recovery evidence. A null target denotes encounter-wide uncertainty,
     * not an attack authorization; a supplied target must still belong to the encounter. */
    public OperationRecord.Result recordRestoredUnknown(UUID encounterId, UUID operationId,
                                                        UUID ownerId, UUID sourceId, UUID targetId,
                                                        long serverTick, String reason) {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(reason);
        Encounter encounter = require(canonicalEncounterId(encounterId));
        OperationRecord.Result previous = encounter.finalResults.get(operationId);
        if (previous != null) {
            OperationRecord.Snapshot old = previous.snapshot();
            if (previous.outcome() != OperationRecord.Outcome.UNKNOWN
                || !previous.reason().equals(reason) || !old.owner().equals(ownerId)
                || !sourceId.equals(old.source()) || !Objects.equals(targetId, old.target())
                || old.serverTick() != serverTick)
                throw new IllegalStateException("restored effect operation ID payload conflict");
            return previous;
        }
        if (encounter.causes.containsKey(operationId)
            || targetId != null && !encounter.members.containsKey(targetId))
            throw new IllegalStateException("lost effect identity or target conflicts with save");
        long nextVersion = Math.addExact(encounter.version, 1);
        OperationRecord.Snapshot snapshot = new OperationRecord.Snapshot(operationId, null,
            encounterId, ownerId, sourceId, targetId, serverTick, encounter.version,
            null, null, OperationRecord.Kind.ATTACK, observationEpoch);
        OperationRecord.Result result = new OperationRecord.Result(snapshot, 0,
            OperationRecord.Outcome.UNKNOWN, reason, 0, 0, nextVersion, true);
        encounter.causes.put(operationId, snapshot);
        encounter.finalResults.put(operationId, result);
        encounter.stepResults.put(operationId, Map.of(0, result));
        encounter.history.add(result);
        encounter.version = nextVersion;
        return result;
    }

    public UUID beginCandidate(UUID id, EncounterRegion region, Set<UUID> members) {
        Objects.requireNonNull(region);
        return beginCandidate(id, region, members, movementTicksPerTurn, environmentTicks);
    }

    public UUID beginCandidate(UUID id, EncounterRegion region, Set<UUID> members,
                               int movementBudget, int environmentBudget) {
        Objects.requireNonNull(region);
        return createCandidate(id, region, members, movementBudget, environmentBudget);
    }

    private UUID createCandidate(UUID id, EncounterRegion region, Set<UUID> members,
                                 int movementBudget, int environmentBudget) {
        Objects.requireNonNull(id); Objects.requireNonNull(members);
        if (members.isEmpty() || encounters.containsKey(id) || closedEncounters.containsKey(id))
            throw new IllegalArgumentException("candidate id/members");
        for (UUID member : members) if (member == null || membership.containsKey(member)) throw new IllegalStateException("member occupied");
        Encounter encounter = new Encounter(id, region, movementBudget, environmentBudget);
        members.stream().sorted().forEach(member -> encounter.members.put(member,
            new Member(member, encounter.movementTicksPerTurn)));
        reroll(encounter);
        encounters.put(id, encounter);
        for (UUID member : members) membership.put(member, id);
        return id;
    }

    public boolean join(UUID encounterId, UUID member) {
        Objects.requireNonNull(member);
        Encounter encounter = require(encounterId);
        if (membership.containsKey(member)) return false;
        long eligibleRound = Math.addExact(encounter.round, 1);
        long nextVersion = Math.addExact(encounter.version, 1);
        long nextStructuralRevision = Math.addExact(encounter.structuralRevision, 1);
        Member joined = new Member(member, encounter.movementTicksPerTurn);
        joined.eligibleRound = eligibleRound;
        applyRoll(joined, sampleRoll());
        encounter.members.put(member, joined);
        membership.put(member, encounterId);
        encounter.version = nextVersion;
        encounter.structuralRevision = nextStructuralRevision;
        return true;
    }

    public boolean leave(UUID encounterId, UUID memberId) {
        Objects.requireNonNull(memberId);
        Encounter encounter = require(encounterId);
        if (!encounter.members.containsKey(memberId)) return false;
        Math.addExact(encounter.version, (long) encounter.pending.size() + 1);
        long nextStructuralRevision = Math.addExact(encounter.structuralRevision, 1);
        for (OperationRecord.Snapshot snapshot : pendingChildrenFirst(encounter)) {
            OperationRecord.Snapshot root = encounter.causes.get(rootOf(encounter, snapshot));
            if (!memberId.equals(snapshot.owner()) && !memberId.equals(snapshot.target())
                && (root == null || !memberId.equals(root.target()))) continue;
            int step = encounter.stepResults.getOrDefault(snapshot.operationId(), Map.of()).size();
            boolean unstartedPlan = snapshot.kind() == OperationRecord.Kind.PLAN
                && encounter.causes.values().stream().noneMatch(child -> snapshot.operationId().equals(child.parentId())
                    && child.kind() != OperationRecord.Kind.MOVE);
            publish(encounterId, snapshot.operationId(), step,
                unstartedPlan ? OperationRecord.Outcome.INTERRUPTED : OperationRecord.Outcome.UNKNOWN,
                unstartedPlan ? "member left before behavior execution" : "member left before world outcome was confirmed", 0, 0, true);
        }
        int index = encounter.order.indexOf(memberId);
        boolean wasCurrent = index == encounter.cursor && encounter.phase != EncounterPhase.ENVIRONMENT;
        encounter.members.remove(memberId);
        membership.remove(memberId);
        encounter.structuralRevision = nextStructuralRevision;
        encounter.hostile.removeIf(relation -> relation.source().equals(memberId) || relation.target().equals(memberId));
        // A later join with the same UUID is a new membership, not the old target scope.
        encounter.permits.values().removeIf(state -> state.permit.targets().contains(memberId));
        if (index >= 0) {
            encounter.order.remove(index);
            if (index < encounter.cursor) encounter.cursor--;
        }
        if (encounter.members.isEmpty()) {
            end(encounterId);
        } else if (encounter.phase == EncounterPhase.ENVIRONMENT) {
            // Roster changes do not start a new simulation cycle.
            encounter.version++;
        } else if (encounter.order.isEmpty() || encounter.cursor >= encounter.order.size()) {
            enterEnvironment(encounter);
            encounter.version++;
        } else {
            if (wasCurrent) beginMemberTurn(encounter, encounter.members.get(encounter.order.get(encounter.cursor)));
            encounter.version++;
        }
        return true;
    }

    public void setHostile(UUID encounterId, UUID source, UUID target, boolean hostile) {
        Encounter encounter = require(encounterId);
        if (!encounter.members.containsKey(source) || !encounter.members.containsKey(target) || source.equals(target))
            throw new IllegalArgumentException("relation members");
        Relation relation = new Relation(source, target);
        boolean changed = hostile != encounter.hostile.contains(relation);
        if (changed) {
            long nextVersion = Math.addExact(encounter.version, 1);
            if (hostile) encounter.hostile.add(relation);
            else encounter.hostile.remove(relation);
            encounter.version = nextVersion;
        }
    }

    public boolean isHostile(UUID encounterId, UUID source, UUID target) {
        return require(encounterId).hostile.contains(new Relation(source, target));
    }

    public boolean hasAttemptedAttack(UUID encounterId, UUID attacker) {
        Encounter encounter = require(encounterId);
        if (!encounter.members.containsKey(attacker)) throw new IllegalArgumentException("attacker is not a member");
        return encounter.attackersWithRegisteredAttempt.contains(attacker);
    }

    public View view(UUID encounterId) {
        Encounter encounter = require(encounterId);
        Map<UUID, MemberView> members = memberViews(encounter);
        return new View(encounter.id, encounter.phase, encounter.version, null,
            encounter.order, currentId(encounter), members, encounter.history, encounter.region, encounter.round);
    }

    public StateView stateView(UUID encounterId) {
        Encounter encounter = require(encounterId);
        return new StateView(encounter.id, encounter.phase, encounter.version, encounter.order,
            currentId(encounter), memberViews(encounter), encounter.round, encounter.environmentRemaining, encounter.region);
    }

    private static Map<UUID, MemberView> memberViews(Encounter encounter) {
        Map<UUID, MemberView> members = new LinkedHashMap<>();
        encounter.members.forEach((id, member) -> members.put(id, member.view()));
        return members;
    }

    private static UUID currentId(Encounter encounter) {
        return encounter.phase == EncounterPhase.ENVIRONMENT || encounter.phase == EncounterPhase.ENDED
            || encounter.order.isEmpty() ? null : encounter.order.get(encounter.cursor);
    }

    /** Page immutable results by sequence index for ordered network or debug consumers. */
    public ResultPage resultPage(UUID encounterId, int fromIndex, int limit) {
        if (fromIndex < 0 || limit < 1) throw new IllegalArgumentException("result page");
        Encounter active = encounters.get(encounterId);
        List<OperationRecord.Result> history = active == null
            ? (closedEncounters.containsKey(encounterId) ? closedEncounters.get(encounterId).results() : null)
            : active.history;
        if (history == null) throw new IllegalArgumentException("unknown encounter " + encounterId);
        if (fromIndex > history.size()) throw new IllegalArgumentException("result cursor beyond history");
        int nextIndex = fromIndex + Math.min(limit, history.size() - fromIndex);
        return new ResultPage(fromIndex, nextIndex, history.size(), history.subList(fromIndex, nextIndex));
    }

    public boolean containsPoint(UUID encounterId, double x, double y, double z) {
        EncounterRegion region = require(encounterId).region;
        if (region == null) throw new IllegalStateException("legacy encounter has no continuous region");
        return region.containsPoint(x, y, z);
    }

    /** Chooses the canonical domain for two presently overlapping region snapshots. */
    public UUID primaryDomain(UUID firstId, UUID secondId) {
        Encounter first = require(firstId);
        Encounter second = require(secondId);
        if (first == second) return firstId;
        if (first.region == null || second.region == null || !first.region.overlaps(second.region))
            throw new IllegalArgumentException("encounters do not have overlapping continuous regions");
        return comparePriority(first, second) >= 0 ? firstId : secondId;
    }

    /** Finds the whole connected component before choosing a primary from one snapshot. */
    public MergePlan planMerge(UUID seedId) {
        return planMergeWithProjection(seedId, null);
    }

    public MergePlan requestCausalMerge(UUID sourceId, UUID targetId) {
        Encounter source = require(canonicalEncounterId(sourceId));
        Encounter target = require(canonicalEncounterId(targetId));
        if (source == target || source.region == null || target.region == null
            || !source.region.dimension().equals(target.region.dimension()))
            throw new IllegalArgumentException("causal merge requires two encounters in one dimension");
        if (!source.causalMergeNeighbors.contains(target.id)) {
            long sourceRevision = Math.addExact(source.structuralRevision, 1);
            long targetRevision = Math.addExact(target.structuralRevision, 1);
            source.causalMergeNeighbors.add(target.id);
            target.causalMergeNeighbors.add(source.id);
            source.structuralRevision = sourceRevision;
            target.structuralRevision = targetRevision;
        }
        return planMerge(source.id);
    }

    /** Detect additional sessions reached by a newly sampled, still provisional region. */
    public MergePlan planMergeProjected(UUID seedId, EncounterRegion projectedRegion) {
        Objects.requireNonNull(projectedRegion);
        return planMergeWithProjection(seedId, projectedRegion);
    }

    private MergePlan planMergeWithProjection(UUID seedId, EncounterRegion projectedRegion) {
        Encounter seed = require(seedId);
        if (seed.region == null) throw new IllegalArgumentException("legacy encounter has no continuous region");
        if (projectedRegion != null && !seed.region.dimension().equals(projectedRegion.dimension()))
            throw new IllegalArgumentException("projected region dimension differs from seed");
        Set<UUID> component = new HashSet<>();
        component.add(seedId);
        boolean changed;
        do {
            changed = false;
            for (Encounter candidate : encounters.values()) {
                if (candidate.region == null || component.contains(candidate.id)) continue;
                boolean adjacent = false;
                for (UUID memberId : component) {
                    EncounterRegion memberRegion = memberId.equals(seedId) && projectedRegion != null
                        ? projectedRegion : encounters.get(memberId).region;
                    if (memberRegion.overlaps(candidate.region)
                        || encounters.get(memberId).causalMergeNeighbors.contains(candidate.id)) {
                        adjacent = true;
                        break;
                    }
                }
                if (adjacent) {
                    component.add(candidate.id);
                    changed = true;
                }
            }
        } while (changed);
        if (component.size() < 2) throw new IllegalStateException("no overlapping encounter");
        Map<UUID, Long> versions = new HashMap<>();
        Map<UUID, Long> structuralRevisions = new HashMap<>();
        UUID primary = null;
        for (UUID id : component) {
            Encounter candidate = encounters.get(id);
            versions.put(id, candidate.version);
            structuralRevisions.put(id, candidate.structuralRevision);
            if (primary == null || comparePriority(candidate, encounters.get(primary)) > 0) primary = id;
        }
        Encounter primaryEncounter = encounters.get(primary);
        long targetRound = primaryEncounter.phase == EncounterPhase.ENVIRONMENT
            && primaryEncounter.environmentStepAuthorized
            ? Math.addExact(primaryEncounter.round, 1) : primaryEncounter.round;
        return new MergePlan(component, versions, structuralRevisions, primary, targetRound,
            projectedRegion);
    }

    /** A canceled reservation also closes the captured entry: the world may already have run. */
    public boolean mergeWindowExpired(MergePlan plan) {
        Objects.requireNonNull(plan);
        Encounter primary = require(plan.primary());
        return primary.round > plan.targetEnvironmentRound()
            || primary.round == plan.targetEnvironmentRound() && primary.environmentStepAuthorized;
    }

    /** Retain the originally captured primary when only its environment entry was missed. */
    public MergePlan renewMergePlan(MergePlan plan) {
        if (!mergeWindowExpired(plan)) throw new IllegalStateException("merge window has not expired");
        if (!planMergeWithProjection(plan.primary(), plan.projectedRegion())
            .encounters().equals(plan.encounters()))
            throw new IllegalStateException("merge component changed");
        Map<UUID, Long> versions = new HashMap<>();
        for (UUID id : plan.encounters()) {
            Encounter participant = require(id);
            if (!Objects.equals(plan.structuralRevisions().get(id), participant.structuralRevision))
                throw new IllegalStateException("merge participant changed");
            versions.put(id, participant.version);
        }
        Encounter primary = require(plan.primary());
        long nextEntry = primary.phase == EncounterPhase.ENVIRONMENT && primary.environmentStepAuthorized
            ? Math.addExact(primary.round, 1) : primary.round;
        return new MergePlan(plan.encounters(), versions, plan.structuralRevisions(),
            plan.primary(), nextEntry, plan.projectedRegion());
    }

    /** Commit a captured connected component at its primary environment boundary. */
    public OperationRecord.Result commitMerge(MergePlan plan, EncounterRegion sampledRegion,
                                              UUID operationId, long serverTick) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(sampledRegion);
        Objects.requireNonNull(operationId);
        MergeReceipt previous = committedMerges.get(operationId);
        if (previous != null) {
            if (!previous.plan().equals(plan) || !sameRegion(previous.region(), sampledRegion)
                || previous.serverTick() != serverTick)
                throw new IllegalStateException("merge operation ID payload conflict");
            return resultFor(previous.primary(), operationId);
        }
        Encounter primary = require(plan.primary());
        if (!planMergeProjected(plan.primary(), sampledRegion).encounters().equals(plan.encounters())
            || primary.phase != EncounterPhase.ENVIRONMENT || primary.environmentRemaining < 1
            || primary.round != plan.targetEnvironmentRound() || primary.environmentStepAuthorized)
            throw new IllegalStateException("merge is not at the captured environment boundary");
        long nextVersion = Math.addExact(primary.version, 1);
        long nextStructuralRevision = Math.addExact(primary.structuralRevision, 1);
        long migratedEligibleRound = Math.addExact(primary.round, 1);
        Map<UUID, Member> members = new LinkedHashMap<>(primary.members);
        Set<Relation> hostile = new HashSet<>(primary.hostile);
        Set<UUID> firstAttempts = new HashSet<>(primary.attackersWithRegisteredAttempt);
        Map<UUID, OperationRecord.Result> finalResults = new HashMap<>(primary.finalResults);
        Map<UUID, Map<Integer, OperationRecord.Result>> stepResults = new HashMap<>(primary.stepResults);
        Map<UUID, OperationRecord.Snapshot> causes = new HashMap<>(primary.causes);
        Map<UUID, PermitState> permits = new HashMap<>(primary.permits);
        Map<UUID, Integer> childrenByRoot = new HashMap<>(primary.childrenByRoot);
        Set<UUID> completedSteps = new HashSet<>(primary.completedEnvironmentSteps);
        List<OperationRecord.Result> history = new ArrayList<>(primary.history);
        List<Encounter> sources = plan.encounters().stream().filter(id -> !id.equals(primary.id))
            .sorted().map(this::require).toList();
        long greatestRegionVersion = primary.region.version();
        for (UUID id : plan.encounters()) {
            Encounter participant = require(id);
            if (!Objects.equals(plan.structuralRevisions().get(id), participant.structuralRevision)
                || participant.region == null || !participant.region.dimension().equals(primary.region.dimension())
                || participant.authorizedEnvironmentStep != null || !participant.pending.isEmpty())
                throw new IllegalStateException("merge participant changed or has executing work");
            greatestRegionVersion = Math.max(greatestRegionVersion, participant.region.version());
        }
        if (!sampledRegion.dimension().equals(primary.region.dimension())
            || sampledRegion.version() <= greatestRegionVersion)
            throw new IllegalArgumentException("merge requires a new region version in the same dimension");
        if (causes.containsKey(operationId) || finalResults.containsKey(operationId)
            || completedSteps.contains(operationId) || permits.containsKey(operationId))
            throw new IllegalStateException("merge operation ID already used");
        Map<UUID, Long> sourceVersions = new HashMap<>();
        for (Encounter source : sources) {
            sourceVersions.put(source.id, Math.addExact(source.version, 1));
            for (var entry : source.members.entrySet())
                if (members.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                    throw new IllegalStateException("member belongs to two merge participants");
            hostile.addAll(source.hostile);
            firstAttempts.addAll(source.attackersWithRegisteredAttempt);
            mergeUnique(finalResults, source.finalResults);
            mergeUnique(stepResults, source.stepResults);
            mergeUnique(causes, source.causes);
            Map<UUID, PermitState> translatedPermits = new HashMap<>();
            for (var entry : source.permits.entrySet()) {
                PermitState original = entry.getValue();
                PermitState translated = new PermitState(original.permit);
                translated.used = original.used;
                translated.effectiveValidThroughRound = original.effectiveValidThroughRound < source.round
                    ? -1 : Math.addExact(primary.round, original.effectiveValidThroughRound - source.round);
                translatedPermits.put(entry.getKey(), translated);
            }
            mergeUnique(permits, translatedPermits);
            mergeUnique(childrenByRoot, source.childrenByRoot);
            for (UUID step : source.completedEnvironmentSteps)
                if (!completedSteps.add(step)) throw new IllegalStateException("duplicate environment step ID");
            history.addAll(source.history);
        }
        if (causes.containsKey(operationId) || finalResults.containsKey(operationId)
            || completedSteps.contains(operationId) || permits.containsKey(operationId))
            throw new IllegalStateException("merge operation ID already used");
        OperationRecord.Snapshot snapshot = new OperationRecord.Snapshot(operationId, null,
            primary.id, primary.id, primary.id, null, serverTick, primary.version,
            null, null, OperationRecord.Kind.MERGE, observationEpoch);
        OperationRecord.Result result = new OperationRecord.Result(snapshot, 0,
            OperationRecord.Outcome.COMPLETED, "connected encounters merged", 0, 0, nextVersion, true);
        for (Encounter source : sources) {
            View closed = new View(source.id, EncounterPhase.ENDED,
                sourceVersions.get(source.id), null, source.order, null,
                memberViews(source), source.history, source.region, source.round);
            for (Member member : source.members.values()) member.eligibleRound = migratedEligibleRound;
            closedEncounters.put(source.id, closed);
            encounters.remove(source.id);
            mergedInto.put(source.id, primary.id);
            for (UUID member : source.members.keySet()) membership.put(member, primary.id);
        }
        for (Encounter participant : encounters.values())
            participant.causalMergeNeighbors.removeAll(plan.encounters());
        primary.causalMergeNeighbors.clear();
        primary.members.clear(); primary.members.putAll(members);
        primary.hostile.clear(); primary.hostile.addAll(hostile);
        primary.attackersWithRegisteredAttempt.clear();
        primary.attackersWithRegisteredAttempt.addAll(firstAttempts);
        primary.finalResults.clear(); primary.finalResults.putAll(finalResults);
        primary.stepResults.clear(); primary.stepResults.putAll(stepResults);
        primary.causes.clear(); primary.causes.putAll(causes);
        primary.permits.clear(); primary.permits.putAll(permits);
        primary.childrenByRoot.clear(); primary.childrenByRoot.putAll(childrenByRoot);
        primary.completedEnvironmentSteps.clear(); primary.completedEnvironmentSteps.addAll(completedSteps);
        primary.history.clear(); primary.history.addAll(history);
        primary.causes.put(operationId, snapshot);
        primary.finalResults.put(operationId, result);
        primary.stepResults.put(operationId, Map.of(0, result));
        primary.history.add(result);
        primary.region = sampledRegion;
        primary.version = nextVersion;
        primary.structuralRevision = nextStructuralRevision;
        committedMerges.put(operationId, new MergeReceipt(plan, sampledRegion, serverTick, primary.id));
        return result;
    }

    private static <T> void mergeUnique(Map<UUID, T> destination, Map<UUID, T> source) {
        for (var entry : source.entrySet())
            if (destination.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                throw new IllegalStateException("duplicate operation identity in merge participants");
    }

    private static boolean sameRegion(EncounterRegion first, EncounterRegion second) {
        return first.dimension().equals(second.dimension()) && first.discovery().equals(second.discovery())
            && first.anchors().equals(second.anchors()) && first.radius() == second.radius()
            && first.version() == second.version();
    }

    private int comparePriority(Encounter first, Encounter second) {
        InitiativeRoll firstCurrent = priorityOf(first);
        InitiativeRoll secondCurrent = priorityOf(second);
        if (firstCurrent == null || secondCurrent == null) {
            if (firstCurrent != null) return 1;
            if (secondCurrent != null) return -1;
        } else {
            int initiative = Integer.compare(firstCurrent.initiative(), secondCurrent.initiative());
            if (initiative != 0) return initiative;
            int tieBreak = Integer.compare(secondCurrent.tieBreak(), firstCurrent.tieBreak());
            if (tieBreak != 0) return tieBreak;
        }
        return second.id.compareTo(first.id);
    }

    private Member currentMember(Encounter encounter) {
        if (encounter.phase == EncounterPhase.ENVIRONMENT || encounter.order.isEmpty()) return null;
        return encounter.members.get(encounter.order.get(encounter.cursor));
    }

    private InitiativeRoll priorityOf(Encounter encounter) {
        Member current = currentMember(encounter);
        return current == null ? encounter.boundaryPriority
            : new InitiativeRoll(current.initiative, current.tieBreak);
    }

    /** Issue a bounded damage capability while its causal operation is executing. */
    public void issueEffectPermit(EffectPermit permit) {
        Objects.requireNonNull(permit);
        UUID canonicalId = canonicalEncounterId(permit.encounterId());
        Encounter encounter = require(canonicalId);
        PermitState previous = encounter.permits.get(permit.id());
        if (previous != null) {
            if (!previous.permit.equals(permit)) throw new IllegalStateException("permit payload conflict");
            return;
        }
        if (!canonicalId.equals(permit.encounterId()))
            throw new IllegalStateException("merged source cannot issue a new permit");
        OperationRecord.Snapshot parent = encounter.pending.get(permit.parentId());
        if (parent == null || (parent.kind() != OperationRecord.Kind.ATTACK
            && parent.kind() != OperationRecord.Kind.DAMAGE
            && parent.kind() != OperationRecord.Kind.INTERRUPT))
            throw new IllegalStateException("permit requires an executing cause");
        if (!encounter.members.keySet().containsAll(permit.targets()))
            throw new IllegalArgumentException("permit target is not a member");
        long nextVersion = Math.addExact(encounter.version, 1);
        encounter.permits.put(permit.id(), new PermitState(permit));
        encounter.version = nextVersion;
    }

    public boolean revokeEffectPermit(UUID encounterId, UUID permitId) {
        Encounter encounter = require(canonicalEncounterId(encounterId));
        if (!encounter.permits.containsKey(permitId)) return false;
        long nextVersion = Math.addExact(encounter.version, 1);
        encounter.permits.remove(permitId);
        encounter.version = nextVersion;
        return true;
    }

    /** Capture before world execution. Duplicate IDs never authorize a second execution. */
    public boolean beginOperation(OperationRecord.Snapshot snapshot) {
        return beginOperation(snapshot, null);
    }

    /** Register a projectile's first causal attack during an authorized environment step. */
    public boolean beginCausalProjectileAttack(ProjectileOrigin origin, OperationRecord.Snapshot snapshot) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(snapshot);
        Encounter encounter = require(snapshot.encounterId());
        OperationRecord.Snapshot previous = encounter.causes.get(snapshot.operationId());
        if (previous != null) {
            if (!previous.equals(snapshot)) throw new IllegalStateException("projectile operation payload conflict");
            return false;
        }
        UUID principal = origin.ownerId() == null ? origin.projectileId() : origin.ownerId();
        if (encounter.phase != EncounterPhase.ENVIRONMENT || encounter.authorizedEnvironmentStep == null
            || snapshot.encounterVersion() != encounter.version || snapshot.parentId() != null
            || snapshot.kind() != OperationRecord.Kind.ATTACK
            || !principal.equals(snapshot.owner()) || !origin.projectileId().equals(snapshot.source())
            || snapshot.target() == null || !encounter.members.containsKey(snapshot.target())
            || snapshot.target().equals(principal) || !encounter.pending.isEmpty()) return false;
        long nextVersion = Math.addExact(encounter.version, 1);
        encounter.pending.put(snapshot.operationId(), snapshot);
        encounter.causes.put(snapshot.operationId(), snapshot);
        if (encounter.members.containsKey(principal))
            encounter.attackersWithRegisteredAttempt.add(principal);
        encounter.version = nextVersion;
        return true;
    }

    /** Record an arrow collision that cannot be authorized, before vanilla applies its impact. */
    public OperationRecord.Result rejectCausalProjectileEffect(UUID encounterId, UUID operationId,
                                                                UUID ownerId, UUID projectileId,
                                                                UUID targetId, long serverTick,
                                                                String reason) {
        return recordCausalProjectileContact(encounterId, operationId, ownerId, projectileId, targetId, serverTick,
            OperationRecord.Outcome.REJECTED, reason);
    }

    /** A contact with no permitted world mutation still has an explicit observed outcome. */
    public OperationRecord.Result recordCausalProjectileContact(UUID encounterId, UUID operationId,
            UUID ownerId, UUID projectileId, UUID targetId, long serverTick, OperationRecord.Outcome outcome, String reason) {
        if (outcome != OperationRecord.Outcome.COMPLETED && outcome != OperationRecord.Outcome.REJECTED)
            throw new IllegalArgumentException("contact outcome");
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(projectileId);
        Objects.requireNonNull(reason);
        if (reason.isBlank()) throw new IllegalArgumentException("empty rejection reason");
        Encounter encounter = require(encounterId);
        OperationRecord.Result previous = encounter.finalResults.get(operationId);
        if (previous != null) {
            OperationRecord.Snapshot prior = previous.snapshot();
            if (prior.kind() != OperationRecord.Kind.ENVIRONMENT
                || !prior.owner().equals(ownerId) || !projectileId.equals(prior.source())
                || !Objects.equals(targetId, prior.target())
                || previous.outcome() != outcome
                || !previous.reason().equals(reason))
                throw new IllegalStateException("projectile effect ID payload conflict");
            return previous;
        }
        if (encounter.phase != EncounterPhase.ENVIRONMENT || encounter.authorizedEnvironmentStep == null
            || !encounter.pending.isEmpty() || encounter.causes.containsKey(operationId))
            throw new IllegalStateException("projectile rejection requires an environment step");
        long nextVersion = Math.addExact(encounter.version, 1);
        OperationRecord.Snapshot snapshot = new OperationRecord.Snapshot(operationId, null,
            encounterId, ownerId, projectileId, targetId, serverTick, encounter.version,
            null, null, OperationRecord.Kind.ENVIRONMENT, observationEpoch);
        OperationRecord.Result result = new OperationRecord.Result(snapshot, 0,
            outcome, reason, 0, 0, nextVersion, true);
        encounter.causes.put(operationId, snapshot);
        encounter.finalResults.put(operationId, result);
        encounter.stepResults.put(operationId, Map.of(0, result));
        encounter.history.add(result);
        encounter.version = nextVersion;
        return result;
    }

    /** Effect operations must present a server-issued capability. */
    public boolean beginOperation(OperationRecord.Snapshot snapshot, UUID permitId) {
        return beginOperation(snapshot, permitId, false);
    }

    /** A plan may run one movement step, then its exact selected capability, sequentially. */
    public boolean beginPlanStep(OperationRecord.Snapshot snapshot) {
        return beginOperation(snapshot, null, true);
    }

    private boolean beginOperation(OperationRecord.Snapshot snapshot, UUID permitId, boolean planStep) {
        Objects.requireNonNull(snapshot);
        if (closedEncounters.containsKey(snapshot.encounterId())) {
            OperationRecord.Result previousResult = resultFor(snapshot.encounterId(), snapshot.operationId());
            if (previousResult == null) throw new IllegalStateException("closed encounter has no such operation");
            if (!previousResult.snapshot().equals(snapshot)) throw new IllegalStateException("operation payload conflict");
            return false;
        }
        Encounter encounter = require(snapshot.encounterId());
        OperationRecord.Snapshot previous = encounter.causes.get(snapshot.operationId());
        if (previous != null) {
            if (!previous.equals(snapshot)) throw new IllegalStateException("operation payload conflict");
            return false;
        }
        if (snapshot.encounterVersion() != encounter.version) return false;
        OperationRecord.Snapshot plan = planStep ? encounter.pending.get(snapshot.parentId()) : null;
        if (planStep) {
            if (plan == null || plan.kind() != OperationRecord.Kind.PLAN || permitId != null
                || !plan.owner().equals(snapshot.owner()) || !plan.source().equals(snapshot.source())
                || encounter.pending.size() != 1
                || snapshot.kind() != OperationRecord.Kind.MOVE && snapshot.kind() != plan.intent().executionKind()
                || !Objects.equals(snapshot.target(), snapshot.kind() == OperationRecord.Kind.MOVE ? null : plan.target())
                || snapshot.kind() != OperationRecord.Kind.MOVE && !Objects.equals(snapshot.targetCell(), plan.targetCell()))
                return false;
            for (OperationRecord.Snapshot cause : encounter.causes.values()) {
                if (!plan.operationId().equals(cause.parentId())) continue;
                if (cause.kind() != OperationRecord.Kind.MOVE || snapshot.kind() == OperationRecord.Kind.MOVE)
                    return false;
            }
        }
        if (snapshot.parentId() != null && !planStep) {
            OperationRecord.Snapshot parent = encounter.causes.get(snapshot.parentId());
            UUID rootId = parent == null ? null : rootOf(encounter, parent);
            PermitState permit = encounter.permits.get(permitId);
            boolean damage = parent != null && permit != null && snapshot.kind() == OperationRecord.Kind.DAMAGE
                && (parent.kind() == OperationRecord.Kind.ATTACK || parent.kind() == OperationRecord.Kind.DAMAGE
                    || parent.kind() == OperationRecord.Kind.INTERRUPT)
                && snapshot.owner().equals(parent.owner())
                && permit.permit.parentId().equals(snapshot.parentId())
                && permit.permit.source().equals(snapshot.source())
                && permit.permit.targets().contains(snapshot.target())
                && permit.permit.phases().contains(encounter.phase)
                && encounter.round <= permit.effectiveValidThroughRound
                && permit.used < permit.permit.maxUses()
                && encounter.members.containsKey(snapshot.target());
            boolean reaction = parent != null && snapshot.kind() == OperationRecord.Kind.INTERRUPT
                && encounter.phase == EncounterPhase.ACTIVE && encounter.pending.containsKey(parent.operationId())
                && (parent.kind() == OperationRecord.Kind.ATTACK || parent.kind() == OperationRecord.Kind.DAMAGE)
                && snapshot.owner().equals(parent.target()) && Objects.equals(snapshot.target(), parent.owner())
                && encounter.members.containsKey(snapshot.owner())
                && snapshot.owner().equals(snapshot.source())
                && encounter.members.get(snapshot.owner()).economy.hasReaction();
            if ((!damage && !reaction) || (reaction && permitId != null)
                || childDepth(encounter, parent) >= 8 || rootId == null
                || encounter.childrenByRoot.getOrDefault(rootId, 0) >= 64) return false;
            long nextVersion = Math.addExact(encounter.version, 1);
            if (reaction) encounter.members.get(snapshot.owner()).economy.spendReaction();
            if (damage) permit.used++;
            encounter.childrenByRoot.merge(rootId, 1, Integer::sum);
            encounter.pending.put(snapshot.operationId(), snapshot);
            encounter.causes.put(snapshot.operationId(), snapshot);
            encounter.version = nextVersion;
            return true;
        }
        if (permitId != null || !encounter.members.containsKey(snapshot.owner())) return false;
        if (encounter.phase != EncounterPhase.ACTIVE && encounter.phase != EncounterPhase.CANDIDATE) return false;
        if (encounter.phase == EncounterPhase.CANDIDATE && snapshot.kind() != OperationRecord.Kind.ATTACK
            && snapshot.kind() != OperationRecord.Kind.END_TURN && snapshot.kind() != OperationRecord.Kind.MOVE
            && snapshot.kind() != OperationRecord.Kind.PLAN && !planStep) return false;
        if ((!planStep && !encounter.pending.isEmpty()) || snapshot.source() == null || !snapshot.source().equals(snapshot.owner())
            || snapshot.kind() == OperationRecord.Kind.DAMAGE || snapshot.kind() == OperationRecord.Kind.INTERRUPT
            || snapshot.kind() == OperationRecord.Kind.ENVIRONMENT || snapshot.kind() == OperationRecord.Kind.MERGE
            || snapshot.kind() == OperationRecord.Kind.START || snapshot.kind() == OperationRecord.Kind.JOIN) return false;
        if (!planStep && (snapshot.kind() == OperationRecord.Kind.PLACE || snapshot.kind() == OperationRecord.Kind.BREAK
            || snapshot.kind() == OperationRecord.Kind.USE_ITEM || snapshot.kind() == OperationRecord.Kind.USE_BLOCK)) return false;
        UUID current = encounter.order.get(encounter.cursor);
        if (!snapshot.owner().equals(current)) return false;
        Member member = encounter.members.get(current);
        if (snapshot.kind() == OperationRecord.Kind.ATTACK
            && (snapshot.target() == null || !encounter.members.containsKey(snapshot.target())
                || snapshot.target().equals(snapshot.owner()))) return false;
        if (requiresAction(snapshot.kind()) && !member.economy.hasAction()) return false;
        if (snapshot.kind() == OperationRecord.Kind.PLAN && snapshot.intent().requiresAction()
            && !member.economy.hasAction()) return false;
        if (snapshot.kind() == OperationRecord.Kind.DASH)
            Math.addExact(member.economy.movementTicks(), encounter.movementTicksPerTurn);
        long nextVersion = Math.addExact(encounter.version, 1);
        Map<UUID, InitiativeRoll> initiativeRolls = encounter.phase == EncounterPhase.CANDIDATE
            && snapshot.kind() == OperationRecord.Kind.ATTACK ? sampleInitiatives(encounter) : Map.of();
        if (requiresAction(snapshot.kind()) && !deferredAction(snapshot.kind())) member.economy.spendAction();
        if (snapshot.kind() == OperationRecord.Kind.DODGE) member.dodging = true;
        if (snapshot.kind() == OperationRecord.Kind.DISENGAGE) member.disengaged = true;
        if (snapshot.kind() == OperationRecord.Kind.DASH) member.economy.addMovement(encounter.movementTicksPerTurn);
        if (encounter.phase == EncounterPhase.CANDIDATE && snapshot.kind() == OperationRecord.Kind.ATTACK) {
            encounter.hostile.add(new Relation(snapshot.owner(), snapshot.target()));
            activate(encounter, initiativeRolls);
            if (!snapshot.owner().equals(currentId(encounter)))
                member.preserveSpentActionOnNextTurn = true;
        }
        encounter.pending.put(snapshot.operationId(), snapshot);
        encounter.causes.put(snapshot.operationId(), snapshot);
        if (snapshot.kind() == OperationRecord.Kind.ATTACK)
            encounter.attackersWithRegisteredAttempt.add(snapshot.owner());
        encounter.version = nextVersion;
        return true;
    }

    /** Durable pre-start rejection. It grants no operation permission and consumes no resource. */
    public OperationRecord.Result rejectPlan(OperationRecord.Snapshot snapshot, String reason) {
        Objects.requireNonNull(snapshot);
        if (snapshot.kind() != OperationRecord.Kind.PLAN || snapshot.parentId() != null)
            throw new IllegalArgumentException("root plan required");
        Encounter encounter = require(snapshot.encounterId());
        if (!encounter.members.containsKey(snapshot.owner()) || !snapshot.owner().equals(snapshot.source()))
            throw new IllegalArgumentException("member identity required");
        var existing = encounter.finalResults.get(snapshot.operationId());
        if (existing != null) {
            if (!existing.snapshot().equals(snapshot)) throw new IllegalStateException("operation payload conflict");
            return existing;
        }
        if (encounter.causes.containsKey(snapshot.operationId())) throw new IllegalStateException("operation already registered");
        long version = Math.addExact(encounter.version, 1);
        var result = new OperationRecord.Result(snapshot, 0, OperationRecord.Outcome.REJECTED, reason, 0, 0, version, true, null);
        encounter.causes.put(snapshot.operationId(), snapshot);
        encounter.history.add(result);
        encounter.stepResults.put(snapshot.operationId(), new HashMap<>(Map.of(0, result)));
        encounter.finalResults.put(snapshot.operationId(), result);
        encounter.version = version;
        return result;
    }

    public OperationRecord.Result publish(UUID encounterId, UUID operationId, int step,
                                          OperationRecord.Outcome outcome, String reason, int movementTicks, float damage,
                                          boolean terminal) {
        return publish(encounterId, operationId, step, outcome, reason, movementTicks, damage, terminal, null);
    }

    public OperationRecord.Result publish(UUID encounterId, UUID operationId, int step,
                                          OperationRecord.Outcome outcome, String reason, int movementTicks, float damage,
                                          boolean terminal, DamageTrace trace) {
        if (closedEncounters.containsKey(encounterId)) {
            for (OperationRecord.Result previous : closedEncounters.get(encounterId).results()) {
                if (previous.snapshot().operationId().equals(operationId) && previous.step() == step)
                    return matchingResult(previous, outcome, reason, movementTicks, damage, terminal, trace);
            }
            throw new IllegalStateException("closed encounter has no such step");
        }
        Encounter encounter = require(encounterId);
        OperationRecord.Result existingStep = encounter.stepResults.getOrDefault(operationId, Map.of()).get(step);
        if (existingStep != null) return matchingResult(existingStep, outcome, reason, movementTicks, damage, terminal, trace);
        if (encounter.finalResults.containsKey(operationId)) throw new IllegalStateException("operation already terminal");
        if (step != encounter.stepResults.getOrDefault(operationId, Map.of()).size())
            throw new IllegalStateException("out-of-order operation step");
        OperationRecord.Snapshot snapshot = encounter.pending.get(operationId);
        if (snapshot == null) throw new IllegalStateException("unregistered operation");
        Member member = encounter.members.get(snapshot.owner());
        if (movementTicks < 0 || movementTicks > (member == null ? 0 : member.economy.movementTicks()))
            throw new IllegalStateException("movement budget exceeded");
        boolean finishTurn = terminal && outcome == OperationRecord.Outcome.COMPLETED
            && snapshot.kind() == OperationRecord.Kind.END_TURN;
        if (finishTurn && (!snapshot.owner().equals(encounter.order.get(encounter.cursor))
            || encounter.pending.size() != 1)) throw new IllegalStateException("turn changed during operation");
        long nextVersion = Math.addExact(encounter.version, 1);
        Map<UUID, InitiativeRoll> rolls = finishTurn && encounter.phase == EncounterPhase.CANDIDATE
            && !encounter.hostile.isEmpty() ? sampleInitiatives(encounter) : Map.of();
        OperationRecord.Result result = new OperationRecord.Result(snapshot, step, outcome, reason,
            movementTicks, damage, nextVersion, terminal, trace);
        boolean commitAction = deferredAction(snapshot.kind()) && step == 0
            && (outcome == OperationRecord.Outcome.ACCEPTED || outcome == OperationRecord.Outcome.COMPLETED
                || outcome == OperationRecord.Outcome.PARTIAL || outcome == OperationRecord.Outcome.UNKNOWN);
        if (commitAction && (member == null || !member.economy.hasAction()))
            throw new IllegalStateException("reserved action unavailable");
        if (terminal && snapshot.kind() == OperationRecord.Kind.PLAN
            && encounter.pending.values().stream().anyMatch(child -> operationId.equals(child.parentId())))
            throw new IllegalStateException("plan step still executing");
        if (commitAction) member.economy.spendAction();
        if (member != null) member.economy.spendMovement(movementTicks);
        encounter.version = nextVersion;
        encounter.history.add(result);
        encounter.stepResults.computeIfAbsent(operationId, ignored -> new HashMap<>()).put(step, result);
        if (terminal) {
            encounter.pending.remove(operationId);
            encounter.finalResults.put(operationId, result);
            if (finishTurn) finishMemberTurnState(encounter, rolls);
        }
        return result;
    }

    private static OperationRecord.Result matchingResult(OperationRecord.Result previous,
                                                          OperationRecord.Outcome outcome, String reason,
                                                          int movementTicks, float damage, boolean terminal,
                                                          DamageTrace trace) {
        if (previous.outcome() != outcome || !previous.reason().equals(reason)
            || previous.actualMovementTicks() != movementTicks
            || Float.compare(previous.actualDamage(), damage) != 0 || previous.terminal() != terminal
            || !Objects.equals(previous.damageTrace(), trace))
            throw new IllegalStateException("operation step payload conflict");
        return previous;
    }

    /** Completes one member turn, including candidate hostility review; returns true on activation. */
    boolean endTurn(UUID encounterId, UUID owner) {
        Encounter encounter = require(encounterId);
        if (encounter.phase != EncounterPhase.CANDIDATE && encounter.phase != EncounterPhase.ACTIVE)
            throw new IllegalStateException("not a member turn");
        if (!owner.equals(encounter.order.get(encounter.cursor))) throw new IllegalStateException("not owner");
        if (!encounter.pending.isEmpty()) throw new IllegalStateException("action still executing");
        long nextVersion = Math.addExact(encounter.version, 1);
        Map<UUID, InitiativeRoll> rolls = encounter.phase == EncounterPhase.CANDIDATE
            && !encounter.hostile.isEmpty() ? sampleInitiatives(encounter) : Map.of();
        boolean activated = finishMemberTurnState(encounter, rolls);
        encounter.version = nextVersion;
        return activated;
    }

    /** Reserve one named simulation step; only one may execute at a time. */
    public boolean authorizeEnvironmentStep(UUID encounterId, UUID stepId) {
        Objects.requireNonNull(stepId);
        Encounter encounter = require(encounterId);
        if (encounter.completedEnvironmentSteps.contains(stepId)) return false;
        if (encounter.phase != EncounterPhase.ENVIRONMENT || encounter.environmentRemaining < 1
            || !encounter.pending.isEmpty()) throw new IllegalStateException("not ready for environment simulation");
        if (encounter.authorizedEnvironmentStep != null) {
            if (encounter.authorizedEnvironmentStep.equals(stepId)) return false;
            throw new IllegalStateException("another environment step is executing");
        }
        long nextVersion = Math.addExact(encounter.version, 1);
        encounter.authorizedEnvironmentStep = stepId;
        encounter.environmentStepAuthorized = true;
        encounter.version = nextVersion;
        return true;
    }

    /** Abort a reserved step when no simulation was performed. */
    public boolean cancelEnvironmentStep(UUID encounterId, UUID stepId) {
        Objects.requireNonNull(stepId);
        Encounter encounter = require(encounterId);
        if (!Objects.equals(encounter.authorizedEnvironmentStep, stepId)) return false;
        long nextVersion = Math.addExact(encounter.version, 1);
        encounter.authorizedEnvironmentStep = null;
        encounter.version = nextVersion;
        return true;
    }

    /** A world tick may already have changed the world when it fails. Keep that uncertainty
     *  under the reserved step ID without charging an environment budget. */
    public OperationRecord.Result failEnvironmentStep(UUID encounterId, UUID stepId,
                                                      long serverTick, String reason) {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(reason);
        Encounter encounter = require(encounterId);
        OperationRecord.Result previous = encounter.finalResults.get(stepId);
        if (previous != null) {
            if (previous.outcome() != OperationRecord.Outcome.UNKNOWN || !previous.reason().equals(reason))
                throw new IllegalStateException("environment step result conflict");
            return previous;
        }
        if (!stepId.equals(encounter.authorizedEnvironmentStep)
            || encounter.completedEnvironmentSteps.contains(stepId))
            throw new IllegalStateException("environment step was not reserved");
        long nextVersion = Math.addExact(encounter.version, 1);
        // The encounter ID is the stable environment principal; no member owned this step.
        OperationRecord.Snapshot snapshot = new OperationRecord.Snapshot(stepId, null,
            encounterId, encounterId, null, null, serverTick, encounter.version,
            null, null, OperationRecord.Kind.ENVIRONMENT, observationEpoch);
        OperationRecord.Result result = new OperationRecord.Result(snapshot, 0,
            OperationRecord.Outcome.UNKNOWN, reason, 0, 0, nextVersion, true);
        encounter.authorizedEnvironmentStep = null;
        encounter.completedEnvironmentSteps.add(stepId);
        encounter.causes.put(stepId, snapshot);
        encounter.finalResults.put(stepId, result);
        encounter.stepResults.computeIfAbsent(stepId, ignored -> new HashMap<>()).put(0, result);
        encounter.history.add(result);
        encounter.version = nextVersion;
        return result;
    }

    /** Caller supplies a stable ID for the simulation step it actually completed. */
    public boolean commitEnvironmentStep(UUID encounterId, UUID stepId) {
        Objects.requireNonNull(stepId);
        Encounter encounter = require(encounterId);
        if (encounter.completedEnvironmentSteps.contains(stepId)) return false;
        if (encounter.phase != EncounterPhase.ENVIRONMENT || encounter.environmentRemaining < 1)
            throw new IllegalStateException("not an environment turn");
        if (!stepId.equals(encounter.authorizedEnvironmentStep) || !encounter.pending.isEmpty())
            throw new IllegalStateException("environment step was not authorized or is still executing");
        long nextVersion = Math.addExact(encounter.version, 1);
        long nextRound = encounter.environmentRemaining == 1 ? Math.addExact(encounter.round, 1) : encounter.round;
        List<UUID> nextOrder = null;
        if (encounter.environmentRemaining == 1) {
            nextOrder = new ArrayList<>();
            for (Member member : encounter.members.values())
                if (member.eligibleRound <= nextRound) nextOrder.add(member.id);
            nextOrder.sort(Comparator.<UUID>comparingInt(id -> encounter.members.get(id).initiative).reversed()
                .thenComparingInt(id -> encounter.members.get(id).tieBreak).thenComparing(UUID::compareTo));
            if (nextOrder.isEmpty()) throw new IllegalStateException("no eligible member for next round");
        }
        encounter.completedEnvironmentSteps.add(stepId);
        encounter.authorizedEnvironmentStep = null;
        if (--encounter.environmentRemaining == 0) {
            encounter.phase = encounter.hostile.isEmpty() ? EncounterPhase.CANDIDATE : EncounterPhase.ACTIVE;
            encounter.round = nextRound;
            encounter.order.clear();
            encounter.order.addAll(nextOrder);
            encounter.cursor = 0;
            beginMemberTurn(encounter, encounter.members.get(encounter.order.get(encounter.cursor)));
        }
        encounter.version = nextVersion;
        return true;
    }

    public Set<UUID> end(UUID encounterId) {
        Encounter encounter = encounters.get(encounterId);
        if (encounter == null) return Set.of();
        Math.addExact(encounter.version, (long) encounter.pending.size() + 1);
        for (UUID operationId : pendingChildrenFirst(encounter).stream().map(OperationRecord.Snapshot::operationId).toList()) {
            int step = encounter.stepResults.getOrDefault(operationId, Map.of()).size();
            publish(encounterId, operationId, step, OperationRecord.Outcome.UNKNOWN,
                "encounter ended before world outcome was confirmed", 0, 0, true);
        }
        encounter.phase = EncounterPhase.ENDED;
        encounter.version++;
        closedEncounters.put(encounterId, view(encounterId));
        encounters.remove(encounterId);
        for (Encounter remaining : encounters.values())
            if (remaining.causalMergeNeighbors.remove(encounterId))
                remaining.structuralRevision = Math.addExact(remaining.structuralRevision, 1);
        for (UUID member : encounter.members.keySet()) membership.remove(member);
        return Set.copyOf(encounter.members.keySet());
    }

    private Encounter require(UUID id) {
        Encounter encounter = encounters.get(id);
        if (encounter == null) throw new IllegalArgumentException("unknown encounter " + id);
        return encounter;
    }
    private void activate(Encounter encounter, Map<UUID, InitiativeRoll> rolls) {
        encounter.phase = EncounterPhase.ACTIVE;
        for (Member member : encounter.members.values()) applyRoll(member, rolls.get(member.id));
        buildOrder(encounter);
    }
    private InitiativeRoll sampleRoll() { return new InitiativeRoll(1 + random.nextInt(20), random.nextInt()); }
    private Map<UUID, InitiativeRoll> sampleInitiatives(Encounter encounter) {
        Map<UUID, InitiativeRoll> rolls = new HashMap<>();
        for (Member member : encounter.members.values()) rolls.put(member.id, sampleRoll());
        return rolls;
    }
    private void applyRoll(Member member, InitiativeRoll roll) {
        member.initiative = roll.initiative();
        member.tieBreak = roll.tieBreak();
    }
    private void reroll(Encounter encounter) {
        Map<UUID, InitiativeRoll> rolls = sampleInitiatives(encounter);
        encounter.order.clear();
        for (Member member : encounter.members.values()) {
            applyRoll(member, rolls.get(member.id));
            encounter.order.add(member.id);
        }
        sortOrder(encounter);
        encounter.cursor = 0;
    }
    private void buildOrder(Encounter encounter) {
        encounter.order.clear();
        for (Member member : encounter.members.values())
            if (member.eligibleRound <= encounter.round) encounter.order.add(member.id);
        sortOrder(encounter);
        encounter.cursor = 0;
    }
    private void sortOrder(Encounter encounter) {
        encounter.order.sort(Comparator.<UUID>comparingInt(id -> encounter.members.get(id).initiative).reversed()
            .thenComparingInt(id -> encounter.members.get(id).tieBreak).thenComparing(UUID::compareTo));
    }
    private boolean finishMemberTurnState(Encounter encounter, Map<UUID, InitiativeRoll> rolls) {
        if (encounter.phase == EncounterPhase.CANDIDATE && !encounter.hostile.isEmpty()) {
            activate(encounter, rolls);
            return true;
        }
        advanceCursorState(encounter);
        return false;
    }
    private void advanceCursorState(Encounter encounter) {
        Member finishing = encounter.members.get(encounter.order.get(encounter.cursor));
        if (finishing != null) finishing.disengaged = false;
        encounter.cursor++;
        if (encounter.cursor >= encounter.order.size()) {
            enterEnvironment(encounter);
        } else beginMemberTurn(encounter, encounter.members.get(encounter.order.get(encounter.cursor)));
    }
    private void enterEnvironment(Encounter encounter) {
        if (!encounter.order.isEmpty()) {
            Member previous = encounter.members.get(encounter.order.get(encounter.order.size() - 1));
            if (previous != null) encounter.boundaryPriority = new InitiativeRoll(previous.initiative, previous.tieBreak);
        }
        encounter.cursor = 0;
        encounter.phase = EncounterPhase.ENVIRONMENT;
        encounter.environmentRemaining = encounter.environmentTicks;
        encounter.environmentStepAuthorized = false;
    }
    private void beginMemberTurn(Encounter encounter, Member member) {
        member.economy.reset(encounter.movementTicksPerTurn);
        if (member.preserveSpentActionOnNextTurn) {
            member.economy.spendAction();
            member.preserveSpentActionOnNextTurn = false;
        }
        member.dodging = false;
        member.disengaged = false;
    }
    private static List<OperationRecord.Snapshot> pendingChildrenFirst(Encounter encounter) {
        return encounter.pending.values().stream()
            .sorted(Comparator.comparingInt((OperationRecord.Snapshot value) -> childDepth(encounter, value)).reversed())
            .toList();
    }

    private static boolean requiresAction(OperationRecord.Kind kind) {
        return switch (kind) {
            case ATTACK, DASH, DODGE, DISENGAGE, HELP, PLACE, BREAK, USE_ITEM -> true;
            case START, JOIN, MERGE, MOVE, END_TURN, DAMAGE, ENVIRONMENT, INTERRUPT, PLAN, USE_BLOCK -> false;
        };
    }
    private static boolean deferredAction(OperationRecord.Kind kind) {
        return kind == OperationRecord.Kind.PLACE || kind == OperationRecord.Kind.BREAK
            || kind == OperationRecord.Kind.USE_ITEM;
    }

    private static int childDepth(Encounter encounter, OperationRecord.Snapshot parent) {
        int depth = 1;
        while (parent.parentId() != null) {
            parent = encounter.causes.get(parent.parentId());
            if (parent == null) return Integer.MAX_VALUE;
            depth++;
        }
        return depth;
    }

    private static UUID rootOf(Encounter encounter, OperationRecord.Snapshot parent) {
        while (parent.parentId() != null) {
            parent = encounter.causes.get(parent.parentId());
            if (parent == null) return null;
        }
        return parent.operationId();
    }
}
