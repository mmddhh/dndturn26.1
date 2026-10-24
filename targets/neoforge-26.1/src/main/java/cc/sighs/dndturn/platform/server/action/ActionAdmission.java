package cc.sighs.dndturn.platform.server.action;
import cc.sighs.dndturn.platform.server.budget.AbilityWorkBudget;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Injected admission capability for accepted action execution. */
public interface ActionAdmission {
    boolean maySubmitPlan(LiveActorContext actor);
    boolean mayOrganizeInventory(ServerPlayer actor);
    void requireAbilityWork(UUID actor, UUID encounter, AbilityWorkBudget.Work work);
    void resyncMember(ServerPlayer actor, UUID encounter);
    void authorizeImpact(ServerPlayer actor, net.minecraft.core.BlockPos pos);
}
