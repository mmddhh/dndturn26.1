package cc.sighs.dndturn.platform.mixin.server.control;

import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
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
        return ActorControlPolicy.autonomousDecision(AuthorityProjection.actor(wither)).allowed() ? wither.getAlternativeTarget(head) : 0;
    }
}
