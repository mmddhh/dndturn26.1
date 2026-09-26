package cc.sighs.dndturn.mixin.client;

import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Used only by the opt-in physical-client regression, restored in a finally block. */
@Mixin(EntityRenderDispatcher.class)
public interface PresentationRendererProbeAccessor {
    @Accessor("renderers") Map<EntityType<?>, EntityRenderer<?, ?>> dndturn$renderers();
    @Accessor("renderers") void dndturn$renderers(Map<EntityType<?>, EntityRenderer<?, ?>> renderers);
}
