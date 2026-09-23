package cc.sighs.dndturn.combat;

import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

final class NativeProjectiles {
    static boolean supported(Entity entity) {
        return entity.getClass()==ThrownEgg.class || entity.getClass()==ThrownExperienceBottle.class
            || entity.getClass()==ThrownSplashPotion.class || entity.getClass()==net.minecraft.world.entity.projectile.FishingHook.class;
    }
    static boolean matches(Entity entity,TacticalIntent invocation) {
        if (invocation==null) return false;
        return switch(invocation.behaviorId()) {
            case "dndturn:egg" -> entity.getClass()==ThrownEgg.class;
            case "dndturn:experience_bottle" -> entity.getClass()==ThrownExperienceBottle.class;
            case "dndturn:splash_potion" -> entity.getClass()==ThrownSplashPotion.class;
            case "dndturn:fishing_rod" -> entity.getClass()==net.minecraft.world.entity.projectile.FishingHook.class;
            default -> false;
        };
    }
    static boolean inert(BlockState state) {
        return state.getFluidState().isEmpty() && (state.isAir() || Set.of(Block.class,RotatedPillarBlock.class,
            SlabBlock.class,StairBlock.class).contains(state.getBlock().getClass()));
    }
}
