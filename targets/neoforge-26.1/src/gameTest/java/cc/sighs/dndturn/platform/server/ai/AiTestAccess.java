package cc.sighs.dndturn.platform.server.ai;
import cc.sighs.dndturn.domain.encounter.StateView;

import cc.sighs.dndturn.domain.ai.AiDecisionContext;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;

public final class AiTestAccess {
    private AiTestAccess() {}
    public static AiDecisionContext capture(LiveActorContext actor, StateView state, OperationRecord.Result previous, EncounterAuthority authority, MobTurnStrategies.Facts facts) {
        return MobTurnStrategies.capture(actor, state, previous, authority, facts);
    }
}
