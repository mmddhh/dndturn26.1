package cc.sighs.dndturn.platform.server.ai;

import cc.sighs.dndturn.domain.ai.AiDecisionContext;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;

public final class AiTestAccess {
    private AiTestAccess() {}
    public static AiDecisionContext capture(LiveActorContext actor, EncounterAuthority.StateView state, OperationRecord.Result previous, EncounterAuthority authority) {
        return MobTurnStrategies.capture(actor, state, previous, authority);
    }
}
