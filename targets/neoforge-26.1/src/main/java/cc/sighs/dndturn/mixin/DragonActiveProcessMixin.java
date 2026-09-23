package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ActiveBodyControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dragon phases bypass Mob.serverAiStep. Gate phase decisions and direct skills, preserving outer maintenance. */
@Mixin(EnderDragon.class)
public abstract class DragonActiveProcessMixin {
    private boolean dndturn$controlled() { return ActiveBodyControl.controlled((EnderDragon)(Object)this); }
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;doServerTick(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void dndturn$phase(DragonPhaseInstance phase, ServerLevel level) {
        if (!dndturn$controlled()) phase.doServerTick(level);
    }
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;getFlyTargetLocation()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 dndturn$target(DragonPhaseInstance phase) {
        return dndturn$controlled() ? null : phase.getFlyTargetLocation();
    }
    @Inject(method = {"knockBack", "hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V"}, at = @At("HEAD"), cancellable = true)
    private void dndturn$contact(ServerLevel level, java.util.List<net.minecraft.world.entity.Entity> entities, CallbackInfo ci) {
        if (dndturn$controlled()) ci.cancel();
    }
    @Inject(method = "checkWalls", at = @At("HEAD"), cancellable = true)
    private void dndturn$walls(ServerLevel level, net.minecraft.world.phys.AABB box, CallbackInfoReturnable<Boolean> cir) {
        if (dndturn$controlled()) cir.setReturnValue(false);
    }
}
