package cc.sighs.dndturn.content.mobs.mixin;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AreaEffectCloud.class)
public interface CreeperCloudAccess {
    @Accessor("potionContents") PotionContents dndturn$potionContents();
}
