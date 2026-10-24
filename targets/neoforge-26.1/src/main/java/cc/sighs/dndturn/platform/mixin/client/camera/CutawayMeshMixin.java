package cc.sighs.dndturn.platform.mixin.client.camera;

import cc.sighs.dndturn.platform.client.camera.CutawayStampAccess;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
public abstract class CutawayMeshMixin implements CutawayStampAccess {
    @Unique private Stamp dndturn$stamp;
    @Override public Stamp dndturn$cutawayStamp() { return dndturn$stamp; }
    @Override public void dndturn$cutawayStamp(Stamp stamp) { dndturn$stamp = stamp; }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void dndturn$stamp(TranslucencyPointOfView view, SectionCompiler.Results results, CallbackInfo ci) {
        dndturn$stamp = ((CutawayStampAccess)(Object)results).dndturn$cutawayStamp();
    }
}
