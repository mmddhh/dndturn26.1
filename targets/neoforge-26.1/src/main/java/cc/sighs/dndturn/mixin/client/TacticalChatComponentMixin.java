package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.TacticalOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class TacticalChatComponentMixin {
    // .84 private overload: (ChatGraphicsAccess graphics, int screenHeight, int ticks, DisplayMode mode).
    // Instance local slots: this=0, graphics=1, screenHeight=2, ticks=3, mode=4.
    // Both public rendering and captureClickableText delegate here, before the scale transform.
    @ModifyVariable(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
        at = @At("HEAD"), argsOnly = true, index = 2)
    private int dndturn$chatBottom(int screenHeight) {
        return screenHeight - TacticalOverlay.chatBottomInset();
    }
}
