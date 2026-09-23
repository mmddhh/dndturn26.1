package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.action.ActionIntent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public final class MinecraftProjectiles {
    public static boolean supported(Entity entity) {
        return entity instanceof ThrownEgg || entity instanceof ThrownExperienceBottle
            || entity instanceof ThrownSplashPotion || entity instanceof net.minecraft.world.entity.projectile.FishingHook;
    }
    public static boolean matches(Entity entity,ActionIntent invocation) {
        if (invocation==null) return false;
        return switch(invocation.behaviorId()) {
            case "dndturn:egg" -> entity instanceof ThrownEgg;
            case "dndturn:experience_bottle" -> entity instanceof ThrownExperienceBottle;
            case "dndturn:splash_potion" -> entity instanceof ThrownSplashPotion;
            case "dndturn:fishing_rod" -> entity instanceof net.minecraft.world.entity.projectile.FishingHook;
            default -> false;
        };
    }
    public static boolean inert(BlockState state) {
        return state.getFluidState().isEmpty();
    }
}
