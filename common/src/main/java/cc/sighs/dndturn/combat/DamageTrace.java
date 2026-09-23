package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

/** Captured rule decision and observed vanilla damage effects for one root attack. */
public record DamageTrace(UUID operationId, UUID targetId, CombatRules.RollMode rollMode,
                          int firstDie, int secondDie, int selectedDie, int rollTotal,
                          int targetArmorClass, boolean hit, boolean critical,
                          double weaponDamage, int toughnessReduction, int tacticalDamage,
                          boolean vanillaAccepted, float absorptionLoss, float healthLoss,
                          DamageEvidence evidence) {
    public enum Stage { MISS, ZERO_DAMAGE, VANILLA_ACCEPTED, VANILLA_REJECTED, UNKNOWN }

    /** Value-only observations made after the authorized vanilla call. */
    public record DamageEvidence(UUID sourceId, String rulesRevision, long regionVersion,
                                 boolean knockbackEnabled, Stage stage,
                                 float shieldBlockedContribution, boolean shieldWearCalled,
                                 boolean knockbackEventObserved, boolean knockbackMoved,
                                 List<EquipmentChange> equipmentChanges,
                                 UUID historicalOwnerId, UUID sourceEncounterId, UUID rootOperationId) {
        public DamageEvidence(UUID sourceId, String rulesRevision, long regionVersion,
                              boolean knockbackEnabled, Stage stage,
                              float shieldBlockedContribution, boolean shieldWearCalled,
                              boolean knockbackEventObserved, boolean knockbackMoved,
                              List<EquipmentChange> equipmentChanges) {
            this(sourceId, rulesRevision, regionVersion, knockbackEnabled, stage,
                shieldBlockedContribution, shieldWearCalled, knockbackEventObserved,
                knockbackMoved, equipmentChanges, null, null, null);
        }
        public DamageEvidence {
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(rulesRevision);
            Objects.requireNonNull(stage);
            equipmentChanges = List.copyOf(equipmentChanges);
            if (rulesRevision.isBlank() || regionVersion < 0
                || !Float.isFinite(shieldBlockedContribution) || shieldBlockedContribution < 0)
                throw new IllegalArgumentException("invalid damage evidence");
        }
    }

    public record EquipmentChange(UUID ownerId, String slot, String beforeItem, String afterItem,
                                  int beforeCount, int afterCount, int beforeDamage, int afterDamage) {
        public EquipmentChange {
            Objects.requireNonNull(ownerId);
            Objects.requireNonNull(slot);
            Objects.requireNonNull(beforeItem);
            Objects.requireNonNull(afterItem);
            if (slot.isBlank() || beforeItem.isBlank() || afterItem.isBlank()
                || beforeCount < 0 || afterCount < 0 || beforeDamage < 0 || afterDamage < 0)
                throw new IllegalArgumentException("invalid equipment change");
        }
    }

    public DamageTrace(UUID operationId, UUID targetId, CombatRules.RollMode rollMode,
                       int firstDie, int secondDie, int selectedDie, int rollTotal,
                       int targetArmorClass, boolean hit, boolean critical,
                       double weaponDamage, int toughnessReduction, int tacticalDamage,
                       boolean vanillaAccepted, float absorptionLoss, float healthLoss) {
        this(operationId, targetId, rollMode, firstDie, secondDie, selectedDie, rollTotal,
            targetArmorClass, hit, critical, weaponDamage, toughnessReduction, tacticalDamage,
            vanillaAccepted, absorptionLoss, healthLoss, null);
    }

    public DamageTrace {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(rollMode);
        if (firstDie < 1 || firstDie > 20 || secondDie < 1 || secondDie > 20
            || selectedDie < 1 || selectedDie > 20 || targetArmorClass < 0
            || !Double.isFinite(weaponDamage) || weaponDamage < 0
            || toughnessReduction < 0 || tacticalDamage < 0
            || !Float.isFinite(absorptionLoss) || absorptionLoss < 0
            || !Float.isFinite(healthLoss) || healthLoss < 0
            || (!hit && (tacticalDamage != 0 || vanillaAccepted || absorptionLoss != 0 || healthLoss != 0)))
            throw new IllegalArgumentException("invalid damage trace");
        if (evidence != null && (evidence.stage() == Stage.MISS && hit
            || evidence.stage() == Stage.ZERO_DAMAGE && (!hit || tacticalDamage != 0)
            || evidence.stage() == Stage.VANILLA_ACCEPTED && !vanillaAccepted
            || evidence.stage() == Stage.VANILLA_REJECTED && vanillaAccepted))
            throw new IllegalArgumentException("damage stage conflicts with observed result");
    }
}
