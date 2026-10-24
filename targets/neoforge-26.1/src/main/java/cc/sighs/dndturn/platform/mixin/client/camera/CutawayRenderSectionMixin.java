package cc.sighs.dndturn.platform.mixin.client.camera;

import cc.sighs.dndturn.platform.client.camera.TerrainCutaway;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class CutawayRenderSectionMixin {
    @Inject(method = "reset", at = @At("HEAD"))
    private void dndturn$forget(CallbackInfo ci) {
        TerrainCutaway.forget((SectionRenderDispatcher.RenderSection)(Object)this);
    }
    @WrapMethod(method = "setSectionMesh")
    private SectionMesh dndturn$install(SectionMesh mesh, Operation<SectionMesh> original) {
        return TerrainCutaway.install((SectionRenderDispatcher.RenderSection)(Object)this, mesh, () -> original.call(mesh));
    }
}
