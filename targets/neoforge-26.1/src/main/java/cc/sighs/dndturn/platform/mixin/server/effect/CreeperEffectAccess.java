package cc.sighs.dndturn.platform.mixin.server.effect;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Fixed .84 bridge; execution and field ownership remain in the Creeper adapter. */
@Mixin(Creeper.class)
public interface CreeperEffectAccess {
    @Accessor("swell") int dndturn$swell();
    @Accessor("swell") void dndturn$swell(int value);
    @Accessor("oldSwell") void dndturn$oldSwell(int value);
    @Accessor("maxSwell") int dndturn$maxSwell();
    @Accessor("explosionRadius") int dndturn$explosionRadius();
    @Invoker("explodeCreeper") void dndturn$explode();
}
