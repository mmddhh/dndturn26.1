package cc.sighs.dndturn.platform.server.persistence;

import cc.sighs.dndturn.domain.ability.ProcessState;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterStateSnapshot;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.operation.ProjectileOrigin;
import cc.sighs.dndturn.platform.server.action.AbilityCheckpoint;
import cc.sighs.dndturn.platform.server.control.ExitAuthorizations;
import cc.sighs.dndturn.platform.server.effect.vanilla.VanillaEffectSettlementState;
import cc.sighs.dndturn.platform.server.encounter.ServerCombatConfig;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Target-owned execution metadata saved beside the common rule snapshot. */
public record CombatPersistenceEnvelope(EncounterStateSnapshot rules,
                                        long cumulativeServerTicks, long nextSessionSequence,
                                        Map<UUID, Long> sessionSequences,
                                        Map<UUID, CapturedSettings> capturedSettings,
                                        Map<UUID, ProjectileOrigin> projectileOrigins,
                                        Map<UUID, UUID> projectileDomains,
                                        Map<UUID, PendingProjectileAttack> pendingProjectileAttacks,
                                        List<EncounterStateSnapshot.MergePlanState> pendingMerges,
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
                                        Map<UUID, VanillaEffectSettlementState.Owner> participantEffects,
                                        Map<UUID, VanillaEffectSettlementState.Settlement> turnSettlements,
                                        Map<UUID, ProcessState> processes) {

    public CombatPersistenceEnvelope(EncounterStateSnapshot rules,
                                        long cumulativeServerTicks, long nextSessionSequence,
                                        Map<UUID, Long> sessionSequences,
                                        Map<UUID, CapturedSettings> capturedSettings,
                                        Map<UUID, ProjectileOrigin> projectileOrigins,
                                        Map<UUID, UUID> projectileDomains,
                                        Map<UUID, PendingProjectileAttack> pendingProjectileAttacks,
                                        List<EncounterStateSnapshot.MergePlanState> pendingMerges,
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
                                        Map<UUID, VanillaEffectSettlementState.Owner> participantEffects,
                                        Map<UUID, VanillaEffectSettlementState.Settlement> turnSettlements) {
        this(rules, cumulativeServerTicks, nextSessionSequence, sessionSequences, capturedSettings, projectileOrigins,
                projectileDomains, pendingProjectileAttacks, pendingMerges, startReceipts, exitReceipts, moveEndReceipts,
                leases, quarantinedProjectiles, exitAuthorizations, abilities, mergeBindings, projectileAbilities,
                recoveryAudits, participantEffects, turnSettlements, Map.of());
    }

    public CombatPersistenceEnvelope {
        if (cumulativeServerTicks < 0 || nextSessionSequence < 0)
            throw new IllegalArgumentException("invalid execution save clock");
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
        processes = Map.copyOf(processes);
        if (processes.size() > 16384 || processes.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().id())))
            throw new IllegalArgumentException("process checkpoint bounds");
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
    public record ProjectileAbility(UUID root, ActionIntent invocation) {
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
