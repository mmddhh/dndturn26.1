package cc.sighs.dndturn.platform.mixin.server.effect;

import cc.sighs.dndturn.platform.server.builtin.creeper.CreeperClouds;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.List;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AreaEffectCloud.class)
public abstract class CreeperCloudTargetsMixin {
    @ModifyExpressionValue(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<LivingEntity> dndturn$targets(List<LivingEntity> candidates) {
        return CreeperClouds.targets((AreaEffectCloud)(Object)this, candidates);
    }
}
