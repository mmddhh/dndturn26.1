package cc.sighs.dndturn.platform.server.character;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Grants the chosen classes starting kit; unknown ids are logged rather than silently invented. */
public final class CharacterEquipment {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("dndturn-character");

    private CharacterEquipment() {}

    public static void grant(ServerPlayer player, JsonObject root) {
        if (!root.has("equipment") || !root.get("equipment").isJsonArray()) {
            LOGGER.warn("starting equipment payload missing player={}", player.getUUID());
            return;
        }
        int granted = 0;
        for (var element : root.getAsJsonArray("equipment")) {
            String raw;
            int count = 1;
            if (element.isJsonPrimitive()) {
                raw = element.getAsString();
            } else if (element.isJsonObject()) {
                JsonObject entry = element.getAsJsonObject();
                raw = entry.get("id").getAsString();
                count = entry.has("count") ? Math.max(1, Math.min(64, entry.get("count").getAsInt())) : 1;
            } else {
                continue;
            }
            Item item = resolve(raw);
            if (item == Items.AIR) {
                LOGGER.warn("unknown starting equipment id={} player={}", raw, player.getUUID());
                continue;
            }
            if (count == 1 && (raw.endsWith("_arrow") || raw.endsWith("_bolt") || raw.endsWith("_needle")))
                count = 20;
            ItemStack stack = new ItemStack(item, count);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            granted++;
        }
        LOGGER.info("starting equipment granted player={} stacks={}", player.getUUID(), granted);
    }

    private static Item resolve(String raw) {
        if (raw == null || raw.isBlank()) return Items.AIR;
        Identifier id = Identifier.tryParse(raw.contains(":") ? raw.replace("dndclasses:", "dndturn:") : "dndturn:" + raw);
        if (id == null) return Items.AIR;
        return BuiltInRegistries.ITEM.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(Items.AIR);
    }
}
