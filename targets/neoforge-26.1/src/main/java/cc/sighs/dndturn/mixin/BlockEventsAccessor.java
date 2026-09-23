package cc.sighs.dndturn.mixin;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
public interface BlockEventsAccessor {
    @Accessor("blockEvents") ObjectLinkedOpenHashSet<BlockEventData> dndturn$blockEvents();
}
