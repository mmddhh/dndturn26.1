package cc.sighs.dndturn.platform.mixin.client.camera;

import cc.sighs.dndturn.platform.client.camera.CutawayStampAccess;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public abstract class CutawayCompilerMixin {
    @Inject(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;", at = @At("RETURN"))
    private void dndturn$stamp(SectionPos pos, RenderSectionRegion region, VertexSorting sorting,
                              SectionBufferBuilderPack builders, List<AddSectionGeometryEvent.AdditionalSectionRenderer> extra,
                              CallbackInfoReturnable<SectionCompiler.Results> cir) {
        ((CutawayStampAccess)(Object)cir.getReturnValue()).dndturn$cutawayStamp(((CutawayStampAccess)region).dndturn$cutawayStamp());
    }
}
