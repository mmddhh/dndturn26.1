package cc.sighs.dndturn.platform.server.action;
import cc.sighs.dndturn.platform.server.checkpoint.AbilityCheckpoint;
import java.util.UUID;

/** Injected evidence capability for accepted action execution. */
public interface ActionEvidence {
    long planClock();
    long executionDeadlineTicks();
    void recordAbilityCheckpoint(AbilityCheckpoint evidence);
    void recordReleaseFailure(UUID operation, String reason);
    void recordReleaseReconciliation(UUID operation, boolean retired);
}
