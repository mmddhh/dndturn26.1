package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.TacticalOverlay;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class TacticalChatScreenMixin extends Screen {
    @Shadow protected EditBox input;
    @Shadow private CommandSuggestions commandSuggestions;

    protected TacticalChatScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("HEAD"))
    private void dndturn$chatHeight(CallbackInfo ci) {
        height = dndturn$availableHeight();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void dndturn$refreshChatHeight(CallbackInfo ci) {
        int available = dndturn$availableHeight();
        if (height == available) return;
        height = available;
        input.setY(height - 12);
        commandSuggestions.updateCommandInfo();
    }

    @Unique
    private int dndturn$availableHeight() {
        return minecraft.getWindow().getGuiScaledHeight() - TacticalOverlay.chatBottomInset();
    }
}
