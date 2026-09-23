package cc.sighs.dndturn.platform.mixin.client.simulation;

import cc.sighs.dndturn.platform.client.simulation.ClientEntitySimulation;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Managed TNT receives its fuse from entity data; client wall ticks cannot expire it early. */
@Mixin(PrimedTnt.class)
public abstract class TntAuthoritativeFuseMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/item/PrimedTnt;getFuse()I"))
    private int dndturn$authoritativeFuse(PrimedTnt tnt) {
        int fuse = tnt.getFuse();
        if (!tnt.level().isClientSide()) return fuse;
        var projection = ClientEntitySimulation.projection(tnt);
        // tick subtracts one immediately after this read. Preserve the received value,
        // including across delayed environment baselines; removal remains a server fact.
        if (projection == null || projection.facts().controller() == null) return fuse;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, fuse) + 1L);
    }
}
