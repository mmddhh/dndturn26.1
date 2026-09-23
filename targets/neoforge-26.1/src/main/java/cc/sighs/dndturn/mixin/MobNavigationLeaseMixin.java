package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ServerCombatService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** During a tactical navigation lease, keep vanilla navigation and controls but stop autonomous goals. */
@Mixin(Mob.class)
public abstract class MobNavigationLeaseMixin {
    @Shadow protected abstract void customServerAiStep(ServerLevel level);
    private boolean dndturn$hasLease() {
        Mob mob = (Mob) (Object) this;
        if (!(mob.level() instanceof ServerLevel level)) return false;
        ServerCombatService service = ServerCombatService.existing(level.getServer());
        return service != null && service.hasMobMoveLease(mob.getUUID());
    }

    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
    private void dndturn$pauseGoalDecisions(GoalSelector selector) {
        if (!dndturn$hasLease()) selector.tick();
    }

    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"))
    private void dndturn$pauseRunningGoals(GoalSelector selector, boolean full) {
        if (!dndturn$hasLease()) selector.tickRunningGoals(full);
    }

    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Mob;customServerAiStep(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void dndturn$pauseCustomDecision(Mob mob, ServerLevel level) {
        if (!dndturn$hasLease()) customServerAiStep(level);
    }
}
