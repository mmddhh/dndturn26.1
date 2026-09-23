package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ItemUseEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
public abstract class ItemEntityInsertionMixin {
    @WrapMethod(method="addEntity")
    private boolean dndturn$inserted(Entity entity,Operation<Boolean> original) {
        boolean accepted=original.call(entity);
        if(accepted) ItemUseEffects.inserted(entity);
        return accepted;
    }
}
