package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class ClientAmbientSamplingMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;animateTick(III)V"))
    private void dndturn$sampleCamera(ClientLevel level, int x, int y, int z) {
        if (ClientControl.active() && ClientControl.mode() == ClientControl.Mode.CAMERA && ClientControl.position() != null) {
            BlockPos center = BlockPos.containing(ClientControl.position());
            if (level.hasChunkAt(center)) { x = center.getX(); y = center.getY(); z = center.getZ(); }
        }
        // Replace the center of the existing call, never add a second sampling pass.
        level.animateTick(x, y, z);
    }
}
