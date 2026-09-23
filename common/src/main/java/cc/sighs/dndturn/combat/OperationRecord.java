package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.UUID;

/** Value-only capture and immutable result. No Minecraft object crosses this boundary. */
public final class OperationRecord {
    private OperationRecord() {}

    public enum Kind { START, JOIN, MERGE, MOVE, ATTACK, DASH, DODGE, DISENGAGE, HELP, END_TURN, DAMAGE, ENVIRONMENT, INTERRUPT,
        PLAN, PLACE, BREAK, USE_ITEM, USE_BLOCK }
    public enum Outcome {
        ACCEPTED(0), COMPLETED(1), PARTIAL(2), REJECTED(3), INTERRUPTED(4), UNKNOWN(5);
        private final int code;
        Outcome(int code) { this.code = code; }
        public int code() { return code; }
        public static Outcome fromCode(int code) {
            for (Outcome value : values()) if (value.code == code) return value;
            throw new IllegalArgumentException("unknown outcome code: " + code);
        }
    }

    public record Snapshot(UUID operationId, UUID parentId, UUID encounterId, UUID owner,
                           UUID source, UUID target, long serverTick, long encounterVersion,
                           GridCell sourceCell, GridCell targetCell, Kind kind, UUID observationEpoch,
                           TacticalIntent intent) {
        public Snapshot(UUID operationId, UUID parentId, UUID encounterId, UUID owner,
                        UUID source, UUID target, long serverTick, long encounterVersion,
                        GridCell sourceCell, GridCell targetCell, Kind kind, UUID observationEpoch) {
            this(operationId, parentId, encounterId, owner, source, target, serverTick, encounterVersion,
                sourceCell, targetCell, kind, observationEpoch, null);
        }
        /** Legacy/test input has an explicitly unknown clock; do not compare it with server observations. */
        public Snapshot(UUID operationId, UUID parentId, UUID encounterId, UUID owner,
                        UUID source, UUID target, long serverTick, long encounterVersion,
                        GridCell sourceCell, GridCell targetCell, Kind kind) {
            this(operationId, parentId, encounterId, owner, source, target, serverTick,
                encounterVersion, sourceCell, targetCell, kind, null);
        }
        public Snapshot {
            Objects.requireNonNull(operationId);
            Objects.requireNonNull(encounterId);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(kind);
            if ((kind == Kind.PLAN) != (intent != null) || kind == Kind.PLAN && parentId != null)
                throw new IllegalArgumentException("plan intent boundary");
            if (kind == Kind.PLAN && (!owner.equals(source) || !Objects.equals(target, intent.target().entity())
                || intent.target().cell() != null && !intent.target().cell().equals(targetCell)))
                throw new IllegalArgumentException("plan identity does not match intent");
            if (observationEpoch != null && serverTick < 0) throw new IllegalArgumentException("negative observation time");
        }
    }

    public record Result(Snapshot snapshot, int step, Outcome outcome, String reason,
                         int actualMovementTicks, float actualDamage, long publishedVersion, boolean terminal,
                         DamageTrace damageTrace) {
        public Result(Snapshot snapshot, int step, Outcome outcome, String reason,
                      int actualMovementTicks, float actualDamage, long publishedVersion, boolean terminal) {
            this(snapshot, step, outcome, reason, actualMovementTicks, actualDamage, publishedVersion, terminal, null);
        }

        public Result {
            Objects.requireNonNull(snapshot);
            Objects.requireNonNull(outcome);
            Objects.requireNonNull(reason);
            if (step < 0 || actualMovementTicks < 0 || !Float.isFinite(actualDamage) || actualDamage < 0) {
                throw new IllegalArgumentException("negative result field");
            }
            if (damageTrace != null && (!snapshot.operationId().equals(damageTrace.operationId())
                || snapshot.kind() != Kind.ATTACK || !Objects.equals(snapshot.target(), damageTrace.targetId())
                || Float.compare(actualDamage, damageTrace.healthLoss()) != 0
                || damageTrace.evidence() != null
                    && !Objects.equals(snapshot.source(), damageTrace.evidence().sourceId())))
                throw new IllegalArgumentException("damage trace does not match attack result");
        }
    }
}
