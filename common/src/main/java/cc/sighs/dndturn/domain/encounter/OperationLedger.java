package cc.sighs.dndturn.domain.encounter;
import java.util.*;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
/** Owned by one Encounter; writes only occur along authority commits or validated restore. */
final class OperationLedger {
    final Map<UUID, OperationRecord.Result> finalResults = new HashMap<>();
    final Map<UUID, Map<Integer, OperationRecord.Result>> stepResults = new HashMap<>();
    final Map<UUID, OperationRecord.Snapshot> pending = new HashMap<>();
    final Map<UUID, OperationRecord.Snapshot> causes = new HashMap<>();
    final Map<UUID, Integer> childrenByRoot = new HashMap<>();
    final List<OperationRecord.Result> history = new ArrayList<>();
}
