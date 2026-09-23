package cc.sighs.dndturn.combat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Target-owned execution metadata saved beside the common rule snapshot. */
public record CombatPersistenceEnvelope(int schemaVersion, CombatStateSnapshot rules,
                                        long cumulativeServerTicks, long nextSessionSequence,
                                        Map<UUID, Long> sessionSequences,
                                        Map<UUID, CapturedSettings> capturedSettings,
                                        Map<UUID, ProjectileOrigin> projectileOrigins,
                                        Map<UUID, UUID> projectileDomains,
                                        Map<UUID, PendingProjectileAttack> pendingProjectileAttacks,
                                        List<CombatStateSnapshot.MergePlanState> pendingMerges,
                                        Map<UUID, StartReceipt> startReceipts,
                                        Map<UUID, ExitReceipt> exitReceipts,
                                        Map<UUID, MoveEndReceipt> moveEndReceipts,
                                        List<LeaseEvidence> leases,
                                        Map<UUID, QuarantinedProjectile> quarantinedProjectiles,
                                        List<ExitAuthorizations.Grant> exitAuthorizations,
                                        Map<UUID, AbilityCheckpoint> abilities,
                                        Map<UUID, MergeBinding> mergeBindings,
                                        Map<UUID, ProjectileAbility> projectileAbilities,
                                        Map<UUID, RecoveryAudit> recoveryAudits,
                                        Map<UUID, ParticipantEffectState.Owner> participantEffects,
                                        Map<UUID, ParticipantEffectState.Settlement> turnSettlements) {
    public static final int CURRENT_SCHEMA = 12;

    public CombatPersistenceEnvelope {
        if (schemaVersion != CURRENT_SCHEMA || cumulativeServerTicks < 0 || nextSessionSequence < 0)
            throw new IllegalArgumentException("unsupported execution save schema or clock");
        Objects.requireNonNull(rules);
        sessionSequences = Map.copyOf(sessionSequences);
        capturedSettings = Map.copyOf(capturedSettings);
        projectileOrigins = Map.copyOf(projectileOrigins);
        projectileDomains = Map.copyOf(projectileDomains);
        pendingProjectileAttacks = Map.copyOf(pendingProjectileAttacks);
        pendingMerges = List.copyOf(pendingMerges);
        startReceipts = Map.copyOf(startReceipts);
        exitReceipts = Map.copyOf(exitReceipts);
        moveEndReceipts = Map.copyOf(moveEndReceipts);
        leases = List.copyOf(leases);
        quarantinedProjectiles = Map.copyOf(quarantinedProjectiles);
        exitAuthorizations = List.copyOf(exitAuthorizations);
        abilities = Map.copyOf(abilities);
        mergeBindings = Map.copyOf(mergeBindings);
        projectileAbilities = Map.copyOf(projectileAbilities);
        recoveryAudits = Map.copyOf(recoveryAudits);
        participantEffects = Map.copyOf(participantEffects);
        turnSettlements = Map.copyOf(turnSettlements);
        if (exitAuthorizations.stream().map(ExitAuthorizations.Grant::owner).distinct().count() != exitAuthorizations.size())
            throw new IllegalArgumentException("duplicate exit authorization");
        for (long sequence : sessionSequences.values())
            if (sequence < 0 || sequence > nextSessionSequence)
                throw new IllegalArgumentException("invalid session sequence");
    }

    /** Values that a live encounter will still consult after the server restarts. */
    public record CapturedSettings(double regionRadius, int maxSampledChunks,
                                   int maxAnchors, boolean tacticalKnockbackEnabled) {
        public CapturedSettings {
            if (!Double.isFinite(regionRadius) || regionRadius <= 0
                || maxSampledChunks < 1 || maxSampledChunks > 4096
                || maxAnchors < 1 || maxAnchors > 4096)
                throw new IllegalArgumentException("invalid captured combat settings");
        }

        public static CapturedSettings from(ServerCombatConfig config) {
            return new CapturedSettings(config.regionRadius(), config.maxSampledChunks(),
                config.maxAnchors(), config.tacticalKnockbackEnabled());
        }
    }

    public record RecoveryAudit(UUID operation, UUID generation, long tick, ActionFailure.Code code, String reason) {
        public RecoveryAudit {
            Objects.requireNonNull(operation); Objects.requireNonNull(generation); Objects.requireNonNull(code); Objects.requireNonNull(reason);
            if (tick < 0 || reason.length() > 512) throw new IllegalArgumentException("recovery audit");
        }
    }
    public record ProjectileAbility(UUID root, TacticalIntent invocation) {
        public ProjectileAbility { Objects.requireNonNull(root); Objects.requireNonNull(invocation); }
    }
    public enum MergePhase { PREPARED, RULES_COMMITTED, BOUND }
    public record MergeBinding(UUID operation, UUID primary, Set<UUID> sources, MergePhase phase, long sequence) {
        public MergeBinding {
            Objects.requireNonNull(operation); Objects.requireNonNull(primary); Objects.requireNonNull(phase);
            sources = Set.copyOf(sources);
            if (!sources.contains(primary) || sources.size() < 2 || sequence < 0) throw new IllegalArgumentException("merge binding evidence");
        }
        public MergeBinding at(MergePhase next) { return new MergeBinding(operation, primary, sources, next, sequence); }
    }
    public record PendingProjectileAttack(UUID targetId, UUID sourceEncounterId) {}
    public record QuarantinedProjectile(UUID operationId, UUID encounterId, UUID targetId, String reason) {
        public QuarantinedProjectile {
            Objects.requireNonNull(operationId);
            Objects.requireNonNull(reason);
            if (reason.isBlank()) throw new IllegalArgumentException("quarantine requires evidence reason");
        }
    }
    public record StartReceipt(UUID owner, UUID encounterId) {}
    public record ExitReceipt(UUID owner, UUID encounterId, long expectedVersion) {}
    public record MoveEndReceipt(UUID owner, UUID encounterId, long expectedVersion) {}
    public record LeaseEvidence(UUID encounterId, UUID operationId, UUID ownerId,
                                String kind, int nextStep, int spentTicks) {}
}
