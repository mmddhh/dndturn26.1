package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;

/** The original callback is invoked at most once by the existing impact execution chain. */
public final class ProjectileImpactHooks {
    private ProjectileImpactHooks() {}
    public static ProjectileDeflection impact(Projectile projectile, HitResult hit, Supplier<ProjectileDeflection> original) {
        if (projectile.level() instanceof ServerLevel level) {
            var runtime = ServerRuntime.existingEncounter(level.getServer());
            if (runtime != null) return runtime.itemProjectileImpact(projectile, hit, original);
        }
        return original.get();
    }
}
