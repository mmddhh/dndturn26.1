package cc.sighs.dndturn.platform.server.action;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.operation.DamageTrace;

/** Injected combat capability for accepted action execution. */
public interface ActionCombat {
    OperationRecord.Result attackPlan(LiveActorContext context, UUID target, UUID operation, UUID parent,
        java.util.function.Consumer<OperationRecord.Result> complete);
    DamageTrace rangedTrace(LivingEntity actor, UUID target, UUID operation, boolean opening);
}
