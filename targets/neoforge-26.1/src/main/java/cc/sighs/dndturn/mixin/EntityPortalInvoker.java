package cc.sighs.dndturn.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityPortalInvoker {
    @Invoker("handlePortal")
    void dndturn$handlePortal();
}
