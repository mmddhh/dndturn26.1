package cc.sighs.dndturn.platform.mixin.server.action;

import cc.sighs.dndturn.platform.server.action.ProjectileImpactHooks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Projectile.class)
public abstract class ItemProjectileImpactMixin {
    @WrapMethod(method="onHit")
    private void dndturn$fishingImpact(HitResult hit,Operation<Void> original) {
        var projectile=(Projectile)(Object)this;
        if (projectile instanceof net.minecraft.world.entity.projectile.FishingHook && projectile.level() instanceof ServerLevel level) {
            {
                ProjectileImpactHooks.impact(projectile,hit,() -> { original.call(hit); return ProjectileDeflection.NONE; });
                return;
            }
        }
        original.call(hit);
    }
    @WrapMethod(method="hitTargetOrDeflectSelf")
    private ProjectileDeflection dndturn$itemImpact(HitResult hit,Operation<ProjectileDeflection> original) {
        var projectile=(Projectile)(Object)this;
        if (projectile.level() instanceof ServerLevel level) {
            return ProjectileImpactHooks.impact(projectile,hit,() -> original.call(hit));
        }
        return original.call(hit);
    }
}
