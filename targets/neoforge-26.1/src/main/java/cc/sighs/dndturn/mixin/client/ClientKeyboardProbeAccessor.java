package cc.sighs.dndturn.mixin.client;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(KeyboardHandler.class)
public interface ClientKeyboardProbeAccessor {
    @Invoker("keyPress") void dndturn$key(long window, int action, KeyEvent event);
}
