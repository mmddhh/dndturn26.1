package cc.sighs.dndturn.platform.server.character;

import net.minecraft.world.entity.item.ItemEntity;
import java.util.List;
import cc.sighs.dndturn.domain.character.CharacterClass;
import cc.sighs.dndturn.domain.actor.ActorStates;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Preflights the selected kit and records the observed inventory and world destinations. */
public final class CharacterEquipment {
    private CharacterEquipment() {}

    static Item resolve(String key) {
        var item = BuiltInRegistries.ITEM.get(Identifier.parse(key)).orElseThrow(
            () -> new IllegalArgumentException("unknown equipment item " + key)).value();
        if (item == Items.AIR) throw new IllegalArgumentException("empty equipment item");
        return item;
    }

    public static List<ItemStack> prepare(CharacterClass definition, String kit) {
        var selected = definition.equipment().get(kit);
        if (selected == null) throw new IllegalArgumentException("unknown starting kit");
        return selected.stream().map(entry -> new ItemStack(resolve(entry.item()), entry.count())).toList();
    }

    public static void grant(ServerPlayer player, List<ItemStack> stacks, List<ActorStates.GrantedStack> observations) {
        for (var stack : stacks) {
            String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            int requested = stack.getCount();
            var inventory = player.getInventory();
            var type = stack.getItem();
            int before = inventory.countItem(type);
            inventory.add(stack);
            int inserted = inventory.countItem(type) - before;
            if (inserted < 0 || inserted > requested) throw new IllegalStateException("inventory change unconfirmed");
            int remainder = requested - inserted;
            stack.setCount(remainder);
            ItemEntity drop = null;
            try {
                if (remainder > 0) {
                    var entity = player.drop(stack, false);
                    if (entity == null || player.level().getEntity(entity.getUUID()) != entity
                        || entity.getItem().getCount() != remainder || !BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).toString().equals(item))
                        throw new IllegalStateException("equipment drop unconfirmed");
                    drop = entity;
                }
            } finally {
                observations.add(new ActorStates.GrantedStack(item, requested, requested - remainder,
                    drop == null ? 0 : remainder, drop == null ? null : drop.getUUID()));
            }
        }
    }
}
