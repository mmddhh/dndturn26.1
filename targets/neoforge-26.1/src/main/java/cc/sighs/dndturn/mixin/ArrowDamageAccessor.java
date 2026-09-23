package cc.sighs.dndturn.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the fixed-version arrow damage value at launch, before owner or weapon changes. */
@Mixin(AbstractArrow.class)
public interface ArrowDamageAccessor {
    @Accessor("baseDamage")
    double dndturn$getBaseDamage();
}
