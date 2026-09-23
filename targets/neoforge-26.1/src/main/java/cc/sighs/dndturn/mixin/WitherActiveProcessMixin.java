package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ActiveBodyControl;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** .84 aiStep pursues an existing target before the common AI gate. No target field is rewritten. */
@Mixin(WitherBoss.class)
public abstract class WitherActiveProcessMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;getAlternativeTarget(I)I"))
    private int dndturn$target(WitherBoss wither, int head) {
        return ActiveBodyControl.controlled(wither) ? 0 : wither.getAlternativeTarget(head);
    }
}
