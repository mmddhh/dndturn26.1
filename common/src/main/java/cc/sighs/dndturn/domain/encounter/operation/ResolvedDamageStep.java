package cc.sighs.dndturn.domain.encounter.operation;

import java.util.*;

/** Captured numeric decision and detached application evidence for one bounded child. */
public record ResolvedDamageStep(UUID operation, UUID target, UUID instance, String stage,
        String nativeType, String channel, Area area, List<Integer> dice, int bonus, int threshold, boolean success,
        float rolled, float afterSave, float afterDefense, OperationRecord.Outcome outcome,
        float healthLoss, float absorptionLoss, double shieldLoss, boolean submitted, String reason) {
    public record Area(double x, double y, double z, double radius) {
        public Area {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(radius) || radius < 0 || radius > 512)
                throw new IllegalArgumentException("damage scope");
        }
        public boolean contains(double px, double py, double pz) {
            double dx=px-x, dy=py-y, dz=pz-z; return dx*dx+dy*dy+dz*dz <= radius*radius;
        }
    }
    public ResolvedDamageStep {
        Objects.requireNonNull(operation); Objects.requireNonNull(target); Objects.requireNonNull(instance);
        Objects.requireNonNull(area); Objects.requireNonNull(stage); Objects.requireNonNull(nativeType); Objects.requireNonNull(channel);
        Objects.requireNonNull(outcome); Objects.requireNonNull(reason); dice = List.copyOf(dice);
        if (stage.isBlank() || stage.length() > 128 || dice.size() > 64 || reason.length() > 512
            || !Float.isFinite(rolled) || rolled < 0 || !Float.isFinite(afterSave) || afterSave < 0
            || !Float.isFinite(afterDefense) || afterDefense < 0 || !Float.isFinite(healthLoss) || healthLoss < 0
            || !Float.isFinite(absorptionLoss) || absorptionLoss < 0 || !Double.isFinite(shieldLoss) || shieldLoss < 0)
            throw new IllegalArgumentException("damage step evidence");
    }
    public ResolvedDamageStep application(OperationRecord.Outcome result, float health, float absorption,
            double shields, boolean submitted, String reason) {
        return new ResolvedDamageStep(operation, target, instance, stage, nativeType, channel, area, dice, bonus,
            threshold, success, rolled, afterSave, afterDefense, result, health, absorption, shields, submitted, reason);
    }
}
