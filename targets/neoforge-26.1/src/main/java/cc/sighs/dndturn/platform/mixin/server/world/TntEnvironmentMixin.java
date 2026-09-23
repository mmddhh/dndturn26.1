package cc.sighs.dndturn.platform.mixin.server.world;

import cc.sighs.dndturn.platform.server.world.EnvironmentExplosion;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PrimedTnt.class)
public abstract class TntEnvironmentMixin {
    @Shadow protected abstract void explode();
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/item/PrimedTnt;explode()V"))
    private void dndturn$environmentExplosion(PrimedTnt tnt) {
        EnvironmentExplosion.run(tnt, this::explode);
    }
}
