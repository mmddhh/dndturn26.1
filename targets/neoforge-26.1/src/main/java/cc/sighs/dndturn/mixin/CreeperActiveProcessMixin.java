package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ActiveBodyControl;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The outer tick fuse is an active skill and is not covered by Goal/Brain suppression. */
@Mixin(Creeper.class)
public abstract class CreeperActiveProcessMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/monster/Creeper;isAlive()Z"))
    private boolean dndturn$skill(Creeper entity) { return entity.isAlive() && !ActiveBodyControl.controlled(entity); }
}
