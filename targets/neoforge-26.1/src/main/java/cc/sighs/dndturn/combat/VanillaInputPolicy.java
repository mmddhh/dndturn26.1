package cc.sighs.dndturn.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-thread capability checks and authoritative correction, shared by vanilla packet seams. */
public final class VanillaInputPolicy {
    private VanillaInputPolicy() {}

    public static boolean controlled(ServerPlayer player) {
        ServerCombatService service = ServerCombatService.existing(player.level().getServer());
        return MinecraftCombatRuntime.isGameplayInputPaused(player)
            || service != null && service.isMember(player.getUUID());
    }

    public static boolean mayOrganize(ServerPlayer player) {
        ServerCombatService service = ServerCombatService.existing(player.level().getServer());
        return service != null && !service.tacticalActions().running(player.getUUID()) && service.mayOrganizeInventory(player);
    }

    public static boolean mayClick(ServerPlayer player, ServerboundContainerClickPacket packet) {
        var service = ServerCombatService.existing(player.level().getServer());
        if (service != null && service.tacticalActions().mayUseContainer(player)) {
            if (packet.containerId() != player.containerMenu.containerId || packet.stateId() != player.containerMenu.getStateId()
                || packet.slotNum() < 0 || packet.slotNum() >= player.containerMenu.slots.size()) return false;
            return switch (packet.containerInput()) {
                case PICKUP, QUICK_MOVE -> packet.buttonNum() == 0 || packet.buttonNum() == 1;
                default -> false;
            };
        }
        if (!mayOrganize(player) || player.containerMenu != player.inventoryMenu
            || packet.containerId() != player.inventoryMenu.containerId
            || packet.stateId() != player.inventoryMenu.getStateId()
            || packet.slotNum() < 5 || packet.slotNum() > 45) return false;
        // Cross-slot drag/collect and recipe slots need their own capability; never infer it
        // from client changedSlots. These modes can affect slots other than slotNum.
        return switch (packet.containerInput()) {
            case PICKUP, QUICK_MOVE -> packet.buttonNum() == 0 || packet.buttonNum() == 1;
            case SWAP -> (packet.buttonNum() >= 0 && packet.buttonNum() <= 8 || packet.buttonNum() == 40)
                && swapFitsInventory(player, packet.slotNum(), packet.buttonNum());
            default -> false;
        };
    }

    private static boolean swapFitsInventory(ServerPlayer player, int slotIndex, int sourceIndex) {
        var inventory = player.getInventory();
        var target = player.inventoryMenu.getSlot(slotIndex);
        ItemStack source = inventory.getItem(sourceIndex);
        ItemStack displaced = target.getItem();
        if (source.isEmpty() || displaced.isEmpty() || !target.mayPickup(player)
            || !target.mayPlace(source)) return true;
        int limit = target.getMaxStackSize(source);
        if (source.getCount() <= limit) return true;

        // .84 SWAP splits the source before Inventory.add(displaced). Only main inventory
        // empty slots and matching main/offhand stacks are insertion destinations.
        ItemStack remainder = source.copyWithCount(source.getCount() - limit);
        ItemStack replacement = source.copyWithCount(limit);
        long capacity = 0;
        for (int index = 0; index <= 40; index++) {
            if (index >= 36 && index != 40) continue;
            ItemStack after = index == sourceIndex ? remainder : inventory.getItem(index);
            if (target.container == inventory && target.getSlotIndex() == index) after = replacement;
            if (after.isEmpty() && index < 36) {
                // Damaged items use getFreeSlot and copyAndClear, never stack merging.
                if (displaced.isDamaged()) return true;
                capacity += inventory.getMaxStackSize(displaced);
            } else if (!displaced.isDamaged() && after.isStackable()
                && ItemStack.isSameItemSameComponents(after, displaced)) {
                capacity += Math.max(0, inventory.getMaxStackSize(after) - after.getCount());
            }
        }
        return !displaced.isDamaged() && capacity >= displaced.getCount();
    }

    public static void correctInventory(ServerPlayer player) {
        player.containerMenu.sendAllDataToRemote();
        player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
    }

    public static void rejectPrediction(ServerPlayer player, int sequence, BlockPos pos) {
        player.connection.ackBlockChangesUpTo(sequence); // validates negative sequences
        if (pos != null && player.isWithinBlockInteractionRange(pos, 1.0)
            && player.level().hasChunkAt(pos))
            player.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
        correctInventory(player);
    }
}
