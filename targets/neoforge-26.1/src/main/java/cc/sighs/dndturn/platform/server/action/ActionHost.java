package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.encounter.operation.DamageTrace;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.spatial.GridCell;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Bounded cross-owner ActionHost commands; implementations retain thread and identity validation. */
public interface ActionHost {
    void recordReleaseReconciliation(UUID operation, boolean retired);
    void recordAbilityCheckpoint(AbilityCheckpoint evidence);
    long planClock();
    void drainImmediateTriggers(LiveActorContext actor, UUID encounter, UUID parent);
    void finishPlanMovement(ServerPlayer player);
    void finishPlanMovement(UUID owner);
    void finishPlanMovement(UUID owner, UUID operation);
    void beginMobPlanMovement(Mob mob, OperationRecord.Snapshot root, UUID operation, GridCell goal);
    void observeMobPlanMovement(Mob mob, UUID operation);
    void beginPlanMovement(ServerPlayer player, UUID parent, UUID operation);
    void joinGeneratedMob(ServerPlayer owner, Mob mob, UUID encounter);
    boolean maySubmitPlan(LiveActorContext actor);
    OperationRecord.Result attackPlan(LiveActorContext context, UUID target, UUID operation, UUID parent,
                                     java.util.function.Consumer<OperationRecord.Result> complete);
    DamageTrace rangedTrace(LivingEntity player, UUID targetId, UUID operation, boolean opening);
    boolean ownsItemProjectile(ServerPlayer player,net.minecraft.world.entity.projectile.Projectile projectile);
}
