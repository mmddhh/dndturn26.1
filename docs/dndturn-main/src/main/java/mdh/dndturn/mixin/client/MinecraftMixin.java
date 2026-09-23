package mdh.dndturn.mixin.client;

import mdh.dndturn.client.camera.TacticalCamera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void dndturn$blockVanillaKeys(CallbackInfo ci) {
        if (!TacticalCamera.instance().isActive()) {
            return;
        }
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.screen != null) {
            return;
        }
        minecraft.options.keyDrop.consumeClick();
        minecraft.options.keyInventory.consumeClick();
        minecraft.options.keySwapOffhand.consumeClick();
        minecraft.options.keyTogglePerspective.consumeClick();
    }
}
