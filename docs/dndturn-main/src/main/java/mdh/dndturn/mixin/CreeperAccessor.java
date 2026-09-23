package mdh.dndturn.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Creeper.class)
public interface CreeperAccessor {

    @Accessor("swell")
    void dndturn$setSwell(int value);

    @Accessor("maxSwell")
    int dndturn$getMaxSwell();

    @Invoker("explodeCreeper")
    void dndturn$explode();
}
