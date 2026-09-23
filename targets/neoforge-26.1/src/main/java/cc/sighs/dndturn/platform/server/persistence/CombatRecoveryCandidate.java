package cc.sighs.dndturn.platform.server.persistence;

import cc.sighs.dndturn.domain.ability.ProcessAuthority;
import cc.sighs.dndturn.domain.ability.ProcessState;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import java.util.Objects;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Side-effect-free recovery preflight. Rejected candidates are never installed in a running service. */
public final class CombatRecoveryCandidate {
    private CombatRecoveryCandidate() {}
    public static EncounterAuthority validate(CombatPersistenceEnvelope saved, Random random) {
        EncounterAuthority candidate = EncounterAuthority.restoreSnapshot(saved.rules(), random);
        if (!saved.capturedSettings().keySet().containsAll(candidate.encounterIds())
            || !saved.sessionSequences().keySet().containsAll(candidate.encounterIds()))
            throw new IllegalArgumentException("active encounter lacks captured settings or session sequence");
        saved.pendingMerges().forEach(plan -> plan.restore());
        saved.participantEffects().forEach((owner, effects) -> {
            if(candidate.encounterOf(owner)==null) throw new IllegalArgumentException("effect owner is not a member");
        });
        saved.turnSettlements().forEach((id, evidence) -> {
            var result=candidate.resultFor(evidence.encounter(),id);
            var root=result==null?candidate.pendingOperation(evidence.encounter(),id):result.snapshot();
            if(!id.equals(evidence.operation()) || root==null || root.kind()!=OperationRecord.Kind.END_TURN
                || !root.owner().equals(evidence.owner())) throw new IllegalArgumentException("settlement lacks turn identity");
        });
        saved.pendingProjectileAttacks().forEach((id, pending) -> {
            Objects.requireNonNull(pending.targetId()); Objects.requireNonNull(pending.sourceEncounterId());
        });
        saved.startReceipts().values().forEach(receipt -> {
            Objects.requireNonNull(receipt.owner()); Objects.requireNonNull(receipt.encounterId());
        });
        saved.exitReceipts().values().forEach(receipt -> {
            Objects.requireNonNull(receipt.owner()); Objects.requireNonNull(receipt.encounterId());
            if (receipt.expectedVersion() < 0) throw new IllegalArgumentException("negative exit version");
        });
        saved.exitAuthorizations().forEach(grant -> {
            var domain = candidate.canonicalEncounterId(grant.encounter());
            if (candidate.encounterIds().contains(domain)
                && !candidate.stateView(domain).region().dimension().equals(grant.dimension()))
                throw new IllegalArgumentException("exit authorization dimension mismatch");
            // Active members never inherit an old exit grant. Reconciliation revokes it on install.
        });
        saved.moveEndReceipts().values().forEach(receipt -> {
            Objects.requireNonNull(receipt.owner()); Objects.requireNonNull(receipt.encounterId());
            if (receipt.expectedVersion() < 0) throw new IllegalArgumentException("negative movement version");
        });
        saved.leases().forEach(lease -> {
            Objects.requireNonNull(lease.encounterId()); Objects.requireNonNull(lease.operationId());
            Objects.requireNonNull(lease.ownerId()); Objects.requireNonNull(lease.kind());
            if (lease.nextStep() < 0 || lease.spentTicks() < 0) throw new IllegalArgumentException("invalid lease evidence");
        });
        saved.abilities().forEach((id, evidence) -> {
            if (!id.equals(evidence.operation())) throw new IllegalArgumentException("checkpoint operation mismatch");
            var result = candidate.resultFor(evidence.encounter(), id);
            var root = result == null ? candidate.pendingOperation(evidence.encounter(), id) : result.snapshot();
            if (root == null || !root.owner().equals(evidence.owner()) || !evidence.invocation().equals(root.intent()))
                throw new IllegalArgumentException("checkpoint lacks matching canonical operation");
        });
        var processes = new ProcessAuthority();
        processes.restore(saved.processes());
        // Build once: a checkpoint may retain many processes from the same closed encounter.
        var causes = new HashMap<UUID, Map<UUID, OperationRecord.Snapshot>>();
        saved.rules().encounters().forEach(encounter -> causes.put(encounter.id(), encounter.causes()));
        saved.rules().closed().forEach(encounter -> {
            var history = new HashMap<UUID, OperationRecord.Snapshot>();
            encounter.results().forEach(result -> history.put(result.snapshot().operationId(), result.snapshot()));
            causes.put(encounter.id(), history);
        });
        saved.processes().forEach((id, process) -> {
            var history = causes.getOrDefault(process.encounter(), Map.of());
            var operation = history.get(process.cause());
            if (operation == null || !root(history, operation).equals(process.root())
                    || process.owner().kind() == ProcessState.Ownership.ACTOR
                        && !operation.owner().equals(process.owner().id()))
                throw new IllegalArgumentException("process lacks canonical cause or ownership");
        });
        saved.mergeBindings().forEach((id, evidence) -> {
            if (!id.equals(evidence.primary())) throw new IllegalArgumentException("merge binding identity mismatch");
            if (evidence.phase() != CombatPersistenceEnvelope.MergePhase.PREPARED
                && evidence.sources().stream().anyMatch(source -> !candidate.canonicalEncounterId(source).equals(candidate.canonicalEncounterId(id))))
                throw new IllegalArgumentException("committed merge lacks rule aliases");
        });
        saved.projectileAbilities().forEach((projectile, ability) -> {
            var origin = saved.projectileOrigins().get(projectile);
            if (origin == null || origin.ownerId() == null || origin.sourceEncounterId() == null) throw new IllegalArgumentException("projectile ability lacks origin");
            var result = candidate.resultFor(origin.sourceEncounterId(), ability.root());
            var root = result == null ? candidate.pendingOperation(origin.sourceEncounterId(), ability.root()) : result.snapshot();
            if (root == null || !origin.ownerId().equals(root.owner()) || !ability.invocation().equals(root.intent()))
                throw new IllegalArgumentException("projectile ability lacks canonical root");
        });
        return candidate;
    }

    private static UUID root(Map<UUID, OperationRecord.Snapshot> history, OperationRecord.Snapshot operation) {
        for (int depth = 0; depth <= 64; depth++) {
            if (operation.parentId() == null) return operation.operationId();
            operation = history.get(operation.parentId());
            if (operation == null) throw new IllegalArgumentException("process lacks causal ancestor");
        }
        throw new IllegalArgumentException("process causal depth exceeded");
    }
}
