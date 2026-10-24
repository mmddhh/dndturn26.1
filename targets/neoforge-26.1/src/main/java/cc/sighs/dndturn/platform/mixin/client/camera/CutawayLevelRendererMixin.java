package cc.sighs.dndturn.platform.mixin.client.camera;

import cc.sighs.dndturn.platform.client.camera.TerrainCutaway;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class CutawayLevelRendererMixin {
    @Inject(method = "extractBlockDestroyAnimation", at = @At("RETURN"))
    private void dndturn$breaking(Camera camera, LevelRenderState state, CallbackInfo ci) {
        TerrainCutaway.filterBreaking(state);
    }
}
