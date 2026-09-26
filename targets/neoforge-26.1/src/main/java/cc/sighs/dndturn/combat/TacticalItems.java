package cc.sighs.dndturn.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Complete stack value comparison, including count and all serialized components. */
public final class TacticalItems {
    private TacticalItems() {}
    public static String revision(net.minecraft.world.entity.LivingEntity player, ItemStack stack) {
        if (stack.isEmpty()) return "empty";
        JsonElement value = ItemStack.CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack)
            .getOrThrow();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical(value).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key -> result.add(key, canonical(value.getAsJsonObject().get(key))));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new com.google.gson.JsonArray();
            value.getAsJsonArray().forEach(element -> result.add(canonical(element)));
            return result;
        }
        return value;
    }
}
