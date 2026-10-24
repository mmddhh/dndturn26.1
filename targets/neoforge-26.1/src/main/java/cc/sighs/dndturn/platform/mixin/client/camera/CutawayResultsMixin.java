package cc.sighs.dndturn.platform.mixin.client.camera;

import cc.sighs.dndturn.platform.client.camera.CutawayStampAccess;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SectionCompiler.Results.class)
public abstract class CutawayResultsMixin implements CutawayStampAccess {
    @Unique private Stamp dndturn$stamp;
    @Override public Stamp dndturn$cutawayStamp() { return dndturn$stamp; }
    @Override public void dndturn$cutawayStamp(Stamp stamp) { dndturn$stamp = stamp; }
}
