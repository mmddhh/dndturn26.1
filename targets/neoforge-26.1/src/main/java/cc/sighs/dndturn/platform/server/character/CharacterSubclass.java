package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.character.CharacterState;
import cc.sighs.dndturn.platform.network.CharacterProtocol;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Subclass metadata copied from classes; only stores the choice until features are migrated. */
public final class CharacterSubclass {
    private record Entry(int level, List<String> ids) {}
    private static final Map<String, Entry> CLASSES = new LinkedHashMap<>();
    static {
        put("barbarian", 3, "berserker", "totem_warrior");
        put("bard", 3, "lore", "valor");
        put("cleric", 1, "knowledge", "life", "light", "nature", "tempest", "trickery", "war");
        put("druid", 2, "land", "moon");
        put("fighter", 3, "champion", "battle_master", "eldritch_knight");
        put("monk", 3, "open_hand", "shadow", "four_elements");
        put("paladin", 3, "devotion", "ancients", "vengeance");
        put("ranger", 3, "hunter", "beast_master");
        put("rogue", 3, "thief", "assassin", "arcane_trickster");
        put("sorcerer", 1, "draconic_bloodline", "wild_magic");
        put("warlock", 1, "archfey", "fiend", "great_old_one");
        put("wizard", 2, "abjuration", "conjuration", "divination", "enchantment", "evocation",
            "illusion", "necromancy", "transmutation");
    }

    private CharacterSubclass() {}

    public static void open(ServerPlayer player) {
        CharacterState character = character(player);
        if (!available(character)) throw new IllegalStateException("subclass is not selectable");
        var progression = character.progression();
        Entry entry = CLASSES.get(bare(progression.classId()));
        JsonArray options = new JsonArray();
        for (String id : entry.ids()) {
            JsonObject option = new JsonObject();
            option.addProperty("id", "dndturn:" + id);
            option.addProperty("name", "subclass.dndclasses." + id + ".name");
            options.add(option);
        }
        JsonObject root = new JsonObject();
        root.add("options", options);
        PacketDistributor.sendToPlayer(player, new CharacterProtocol.SubclassOpen(root.toString()));
    }

    public static void apply(ServerPlayer player, String choice) {
        CharacterState character = character(player);
        var progression = character.progression();
        Entry entry = CLASSES.get(bare(progression.classId()));
        if (entry == null) throw new IllegalStateException("class has no subclass list");
        if (progression.level() < entry.level()) throw new IllegalStateException("subclass level not reached");
        if (progression.subclassId() != null) throw new IllegalStateException("subclass already selected");
        String id = choice.replace("dndclasses:", "dndturn:");
        if (id.contains(":")) id = id.substring(id.indexOf(':') + 1);
        if (!entry.ids().contains(id)) throw new IllegalArgumentException("invalid subclass");
        var next = character.withProgression(progression.withSubclass("dndturn:" + id));
        var service = ServerRuntime.encounters(player.level().getServer());
        var current = service.actorStates().state(player.getUUID());
        service.actorStates().command(new LiveActorContext(player),
            new ActorStates.Command(UUID.randomUUID(), player.getUUID(), current.revision(),
                List.of(new ActorStates.Character(next)), null));
        PacketDistributor.sendToPlayer(player, new CharacterProtocol.Reply(true, "Subclass selected"));
    }

    private static CharacterState character(ServerPlayer player) {
        var service = ServerRuntime.encounters(player.level().getServer());
        CharacterState character = service.actorStates().state(player.getUUID()).persistent().character();
        if (!character.created()) throw new IllegalStateException("character has not been created");
        return character;
    }

    public static boolean available(CharacterState character) {
        if (character == null || !character.created() || character.progression().subclassId() != null) return false;
        var progression = character.progression();
        Entry entry = CLASSES.get(bare(progression.classId()));
        return entry != null && progression.level() >= entry.level();
    }

    private static void put(String classKey, int level, String... ids) {
        CLASSES.put(classKey, new Entry(level, List.of(ids)));
    }

    private static String bare(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
