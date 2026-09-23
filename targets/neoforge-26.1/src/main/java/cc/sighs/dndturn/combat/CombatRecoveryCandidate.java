package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.Random;

/** Side-effect-free recovery preflight. Rejected candidates are never installed in a running service. */
public final class CombatRecoveryCandidate {
    private CombatRecoveryCandidate() {}
    public static CombatEngine validate(CombatPersistenceEnvelope saved, Random random) {
        CombatEngine candidate = CombatEngine.restoreSnapshot(saved.rules(), random);
        if (!saved.capturedSettings().keySet().containsAll(candidate.encounterIds())
            || !saved.sessionSequences().keySet().containsAll(candidate.encounterIds()))
            throw new IllegalArgumentException("active encounter lacks captured settings or session sequence");
        saved.pendingMerges().forEach(plan -> plan.restore());
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
        saved.moveEndReceipts().values().forEach(receipt -> {
            Objects.requireNonNull(receipt.owner()); Objects.requireNonNull(receipt.encounterId());
            if (receipt.expectedVersion() < 0) throw new IllegalArgumentException("negative movement version");
        });
        saved.leases().forEach(lease -> {
            Objects.requireNonNull(lease.encounterId()); Objects.requireNonNull(lease.operationId());
            Objects.requireNonNull(lease.ownerId()); Objects.requireNonNull(lease.kind());
            if (lease.nextStep() < 0 || lease.spentTicks() < 0) throw new IllegalArgumentException("invalid lease evidence");
        });
        return candidate;
    }
}
