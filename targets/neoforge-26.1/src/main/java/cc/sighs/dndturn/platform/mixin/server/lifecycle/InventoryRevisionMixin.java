package cc.sighs.dndturn.platform.mixin.server.lifecycle;

import cc.sighs.dndturn.platform.server.actor.EquipmentRevisions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Local write evidence only; no inventory contents or rule authority are mirrored. */
@Mixin(Inventory.class)
public abstract class InventoryRevisionMixin implements EquipmentRevisions {
    @Shadow private int selected;
    @Unique private final long[] dndturn$writes = new long[36];
    @Unique private long dndturn$selection;
    public long dndturn$equipmentRevision(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) throw new IllegalArgumentException("inventory main hand only");
        return Math.addExact(dndturn$selection, dndturn$writes[selected]);
    }
    @Unique private void dndturn$write(int slot) {
        if (slot >= 0 && slot < dndturn$writes.length) dndturn$writes[slot] = Math.incrementExact(dndturn$writes[slot]);
    }
    @Inject(method="setItem", at=@At("RETURN"), require=1)
    private void dndturn$set(int slot, ItemStack stack, CallbackInfo ci) { dndturn$write(slot); }
    @Inject(method="setSelectedItem", at=@At("RETURN"), require=1)
    private void dndturn$selected(ItemStack stack, CallbackInfoReturnable<ItemStack> ci) { dndturn$write(selected); }
    @Inject(method="setSelectedSlot", at=@At("HEAD"), require=1)
    private void dndturn$select(int slot, CallbackInfo ci) {
        if (slot != selected && Inventory.isHotbarSlot(slot)) dndturn$selection = Math.incrementExact(dndturn$selection);
    }
    @Inject(method="removeItem(II)Lnet/minecraft/world/item/ItemStack;", at=@At("RETURN"), require=1)
    private void dndturn$remove(int slot, int count, CallbackInfoReturnable<ItemStack> ci) {
        if (!ci.getReturnValue().isEmpty()) dndturn$write(slot);
    }
    @Inject(method="removeItemNoUpdate", at=@At("RETURN"), require=1)
    private void dndturn$removeQuiet(int slot, CallbackInfoReturnable<ItemStack> ci) { dndturn$write(slot); }
    @Inject(method={"clearContent", "load"}, at=@At("RETURN"), require=1)
    private void dndturn$reset(CallbackInfo ci) { dndturn$selection = Math.incrementExact(dndturn$selection); }
}
