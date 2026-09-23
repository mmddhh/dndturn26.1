package cc.sighs.dndturn.platform.mixin.server.control;

import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Independent active AI gates. Body/lifecycle eligibility never grants an AI decision or skill. */
@Mixin(Mob.class)
public abstract class MobNavigationLeaseMixin {
    @Shadow protected abstract void customServerAiStep(ServerLevel level);
    private boolean dndturn$driveAllowed() {
        return ActorControlPolicy.nativeMovement(AuthorityProjection.actor((Mob)(Object)this)).allowed();
    }
    private boolean dndturn$autonomyAllowed() {
        return ActorControlPolicy.autonomousDecision(AuthorityProjection.actor((Mob)(Object)this)).allowed();
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void dndturn$clearUnlicensedInput(CallbackInfo ci) {
        if (!dndturn$driveAllowed()) {
            Mob mob = (Mob) (Object) this;
            mob.xxa = 0; mob.yya = 0; mob.zza = 0;
            mob.setJumping(false);
        }
    }

    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
    private void dndturn$pauseGoalDecisions(GoalSelector selector) {
        if (dndturn$autonomyAllowed()) selector.tick();
    }

    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"))
    private void dndturn$pauseRunningGoals(GoalSelector selector, boolean full) {
        if (dndturn$autonomyAllowed()) selector.tickRunningGoals(full);
    }

    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Mob;customServerAiStep(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void dndturn$pauseCustomDecision(Mob mob, ServerLevel level) {
        if (dndturn$autonomyAllowed()) customServerAiStep(level);
    }

    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V"))
    private void dndturn$navigation(PathNavigation navigation) {
        if (dndturn$driveAllowed()) navigation.tick();
    }
    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"))
    private void dndturn$move(MoveControl control) { if (dndturn$driveAllowed()) control.tick(); }
    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/control/LookControl;tick()V"))
    private void dndturn$look(LookControl control) { if (dndturn$driveAllowed()) control.tick(); }
    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/control/JumpControl;tick()V"))
    private void dndturn$jump(JumpControl control) { if (dndturn$driveAllowed()) control.tick(); }
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Mob;canPickUpLoot()Z"))
    private boolean dndturn$pickup(Mob mob) { return dndturn$autonomyAllowed() && mob.canPickUpLoot(); }
}
