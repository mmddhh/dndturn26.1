package cc.sighs.dndturn.mixin.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Raw callback bridge used by explicit runtime regression only. */
@Mixin(MouseHandler.class)
public interface ClientInputProbeAccessor {
    @Invoker("onButton") void dndturn$button(long window, MouseButtonInfo button, int action);
    @Invoker("onMove") void dndturn$move(long window, double x, double y);
    @Invoker("onScroll") void dndturn$scroll(long window, double x, double y);
}
