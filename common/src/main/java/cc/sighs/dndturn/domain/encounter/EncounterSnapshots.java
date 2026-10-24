package cc.sighs.dndturn.domain.encounter;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority.*;
/** Internal checkpoint translation; restoration is validated before installation. */
final class EncounterSnapshots {
 private EncounterSnapshots() {}
 record Restored(Map<UUID, Encounter> encounters, Map<UUID, View> closedEncounters, Map<UUID, UUID> membership, Map<UUID, MergeReceipt> committedMerges, Map<UUID, UUID> mergedInto) {}
    static EncounterStateSnapshot exportSnapshot(int movementTicksPerTurn, int environmentTicks,
            Map<UUID, Encounter> encounters, Map<UUID, View> closedEncounters,
            Map<UUID, MergeReceipt> committedMerges, Map<UUID, UUID> mergedInto) {
        List<EncounterStateSnapshot.EncounterState> active = new ArrayList<>();
        for (Encounter encounter : encounters.values()) {
            List<EncounterStateSnapshot.MemberState> members = new ArrayList<>();
            for (Member member : encounter.members.values()) members.add(new EncounterStateSnapshot.MemberState(
                member.id, member.initiative, member.tieBreak, member.eligibleRound,
                member.economy.movementTicks(), member.economy.hasAction(),
                member.economy.hasReaction(), member.dodging, member.disengaged,
                member.preserveSpentActionOnNextTurn));
            List<EncounterStateSnapshot.Hostility> hostile = encounter.hostile.stream()
                .map(relation -> new EncounterStateSnapshot.Hostility(relation.source(), relation.target()))
                .toList();
            List<EncounterStateSnapshot.PermitState> permits = encounter.permits.values().stream()
                .map(permit -> new EncounterStateSnapshot.PermitState(permit.permit, permit.used,
                    permit.effectiveValidThroughRound)).toList();
            active.add(new EncounterStateSnapshot.EncounterState(encounter.id, null,
                EncounterStateSnapshot.RegionState.capture(encounter.region), encounter.phase,
                encounter.version, encounter.structuralRevision, encounter.round, encounter.cursor,
                encounter.environmentRemaining, encounter.authorizedEnvironmentStep,
                encounter.environmentStepAuthorized,
                encounter.boundaryPriority == null ? null : encounter.boundaryPriority.initiative(),
                encounter.boundaryPriority == null ? null : encounter.boundaryPriority.tieBreak(),
                members, hostile, encounter.order, encounter.operations.history, encounter.operations.pending,
                encounter.operations.causes, encounter.attackersWithRegisteredAttempt, permits,
                encounter.operations.childrenByRoot, encounter.completedEnvironmentSteps,
                encounter.causalMergeNeighbors, encounter.movementTicksPerTurn, encounter.environmentTicks, encounter.environmentTime));
        }
        List<EncounterStateSnapshot.ClosedState> closed = new ArrayList<>();
        for (View view : closedEncounters.values()) closed.add(new EncounterStateSnapshot.ClosedState(
            view.id(), view.bounds(), EncounterStateSnapshot.RegionState.capture(view.region()),
            view.version(), view.round(), view.order(), view.members(), view.results()));
        List<EncounterStateSnapshot.MergeReceiptState> receipts = new ArrayList<>();
        for (var entry : committedMerges.entrySet()) {
            MergeReceipt receipt = entry.getValue();
            receipts.add(new EncounterStateSnapshot.MergeReceiptState(entry.getKey(),
                EncounterStateSnapshot.MergePlanState.capture(receipt.plan()),
                EncounterStateSnapshot.RegionState.capture(receipt.region()),
                receipt.serverTick(), receipt.primary()));
        }
        return new EncounterStateSnapshot(movementTicksPerTurn, environmentTicks, active, closed, mergedInto, receipts);
    }
    static Restored restoreSnapshot(EncounterStateSnapshot saved) {
        Objects.requireNonNull(saved);
        Restored engine = new Restored(new LinkedHashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        engine.mergedInto.putAll(saved.mergedInto());
        for (EncounterStateSnapshot.EncounterState state : saved.encounters()) {
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
            encounter.environmentTime = state.environmentTime();
            encounter.authorizedEnvironmentStep = state.authorizedEnvironmentStep();
            encounter.environmentStepAuthorized = state.environmentStepAuthorized();
            if (state.boundaryInitiative() != null)
                encounter.boundaryPriority = new InitiativeRoll(state.boundaryInitiative(),
                    state.boundaryTieBreak());
            for (EncounterStateSnapshot.MemberState value : state.members()) {
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
            eligible.add(encounter.environment.id());
            if (encounter.members.isEmpty()
                || state.order().isEmpty() || !state.order().get(state.order().size() - 1).equals(encounter.environment.id())
                || encounter.members.containsKey(encounter.environment.id())
                || (environment ? state.cursor() != state.order().size() - 1
                    : state.cursor() < 0 || state.cursor() >= state.order().size() - 1)
                || !eligible.equals(new HashSet<>(state.order()))
                || new HashSet<>(state.order()).size() != state.order().size())
                throw new IllegalArgumentException("invalid turn order snapshot");
            encounter.order.addAll(state.order());
            for (EncounterStateSnapshot.Hostility relation : state.hostile()) {
                if (!encounter.members.containsKey(relation.source())
                    || !encounter.members.containsKey(relation.target())
                    || relation.source().equals(relation.target()))
                    throw new IllegalArgumentException("invalid hostility snapshot");
                encounter.hostile.add(new Relation(relation.source(), relation.target()));
            }
            encounter.operations.history.addAll(state.history());
            for (OperationRecord.Result result : state.history()) {
                UUID operationId = result.snapshot().operationId();
                if (encounter.operations.stepResults.computeIfAbsent(operationId, ignored -> new HashMap<>())
                    .putIfAbsent(result.step(), result) != null)
                    throw new IllegalArgumentException("duplicate result step snapshot");
                if (result.terminal() && encounter.operations.finalResults.putIfAbsent(operationId, result) != null)
                    throw new IllegalArgumentException("duplicate terminal result snapshot");
            }
            encounter.operations.pending.putAll(state.pending());
            encounter.operations.causes.putAll(state.causes());
            for (var entry : encounter.operations.pending.entrySet())
                if (!entry.getKey().equals(entry.getValue().operationId())
                    || !entry.getValue().equals(encounter.operations.causes.get(entry.getKey()))
                    || encounter.operations.finalResults.containsKey(entry.getKey()))
                    throw new IllegalArgumentException("invalid pending operation snapshot");
            encounter.attackersWithRegisteredAttempt.addAll(state.attackersWithRegisteredAttempt());
            for (EncounterStateSnapshot.PermitState value : state.permits()) {
                PermitState permit = new PermitState(value.permit());
                permit.used = value.used();
                permit.effectiveValidThroughRound = value.effectiveValidThroughRound();
                if (encounter.permits.putIfAbsent(value.permit().id(), permit) != null)
                    throw new IllegalArgumentException("duplicate permit snapshot");
            }
            encounter.operations.childrenByRoot.putAll(state.childrenByRoot());
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
        for (EncounterStateSnapshot.ClosedState state : saved.closed()) {
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
        for (EncounterStateSnapshot.MergeReceiptState value : saved.mergeReceipts()) {
            MergeReceipt receipt = new MergeReceipt(value.plan().restore(), value.region().restore(),
                value.serverTick(), value.primary());
            if (engine.committedMerges.putIfAbsent(value.operationId(), receipt) != null)
                throw new IllegalArgumentException("duplicate merge receipt snapshot");
        }
        return engine;
    }
}
