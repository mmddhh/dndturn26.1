package cc.sighs.dndturn.platform.mixin.server.lifecycle;

import cc.sighs.dndturn.platform.server.actor.EquipmentRevisions;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(EntityEquipment.class)
public abstract class EntityEquipmentRevisionMixin implements EquipmentRevisions {
    @Unique private final long[] dndturn$revisions = new long[EquipmentSlot.values().length];
    public long dndturn$equipmentRevision(EquipmentSlot slot) { return dndturn$revisions[slot.ordinal()]; }
    @Inject(method="set", at=@At("RETURN"), require=1)
    private void dndturn$set(EquipmentSlot slot, ItemStack stack, CallbackInfoReturnable<ItemStack> ci) {
        dndturn$revisions[slot.ordinal()] = Math.incrementExact(dndturn$revisions[slot.ordinal()]);
    }
    @Inject(method="setAll", at=@At("RETURN"), require=1)
    private void dndturn$setAll(EntityEquipment source, CallbackInfo ci) { dndturn$advanceAll(); }
    @Inject(method="clear", at=@At("RETURN"), require=1)
    private void dndturn$clear(CallbackInfo ci) { dndturn$advanceAll(); }
    @Unique private void dndturn$advanceAll() {
        for (int i=0;i<dndturn$revisions.length;i++) dndturn$revisions[i]=Math.incrementExact(dndturn$revisions[i]);
    }
}
