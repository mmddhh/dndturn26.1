package cc.sighs.dndturn.platform.mixin.server.control;

import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The final PLAN waypoint uses the planner's arrival tolerance, still driven by vanilla controls. */
@Mixin(PathNavigation.class)
public abstract class TacticalNavigationArrivalMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected Path path;
    @Shadow protected float maxDistanceToWaypoint;
    @Inject(method = "followThePath", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;maxDistanceToWaypoint:F",
        opcode = 181, shift = At.Shift.AFTER))
    private void dndturn$finalArrival(CallbackInfo ci) {
        if (!(mob.level() instanceof ServerLevel level) || path == null
            || path.getNextNodeIndex() != path.getNodeCount() - 1) return;
        if (ActorControlPolicy.leasedMovement(AuthorityProjection.actor(mob)).allowed())
            maxDistanceToWaypoint = Math.min(maxDistanceToWaypoint, .2F);
    }
}
