package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.MinecraftCombatRuntime;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bridge the fixed-version pre-impact block-effect call to the server's domain owner. */
@Mixin(AbstractArrow.class)
public abstract class ArrowBlockEffectsGateMixin {
    @Redirect(method = "stepMoveAndHit(Lnet/minecraft/world/phys/BlockHitResult;)V",
        at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;applyEffectsFromBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V"))
    private void dndturn$gateBlockEffects(AbstractArrow arrow, Vec3 from, Vec3 to) {
        if (MinecraftCombatRuntime.allowArrowBlockEffects(arrow, from, to))
            arrow.applyEffectsFromBlocks(from, to);
    }

    @Redirect(method = "stepMoveAndHit(Lnet/minecraft/world/phys/BlockHitResult;)V",
        at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;handlePortal()V"))
    private void dndturn$gatePortalAfterRejectedTransit(AbstractArrow arrow) {
        if (arrow.isAlive()) ((EntityPortalInvoker) arrow).dndturn$handlePortal();
    }
}
