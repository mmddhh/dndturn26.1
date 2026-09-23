package cc.sighs.dndturn.platform.mixin.client.ui;

import com.sighs.apricityui.render.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Operation.class, remap = false)
public interface AuiPointerStateAccessor {
    @Accessor("mouseButtons") static void dndturn$buttons(int buttons) { throw new AssertionError("mixin"); }
}
