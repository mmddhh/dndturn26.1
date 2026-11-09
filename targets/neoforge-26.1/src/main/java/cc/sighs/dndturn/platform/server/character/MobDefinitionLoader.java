package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.domain.mob.DamageComponent;
import cc.sighs.dndturn.domain.mob.DamageType;
import cc.sighs.dndturn.domain.mob.MobAction;
import cc.sighs.dndturn.domain.mob.MobDefinition;
import cc.sighs.dndturn.domain.mob.MovementMode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses the normalized monster JSON. HP/AC fields are deliberately rejected as runtime authority. */
final class MobDefinitionLoader {
    private static final Set<String> FIELDS = Set.of("id", "version", "abilities", "saves", "speeds",
        "resistances", "immunities", "vulnerabilities", "condition_immunities", "traits", "actions",
        "multiattack", "recharge_min", "legendary_resistance");

    private MobDefinitionLoader() {}

    static MobDefinition read(JsonObject json, Path file) {
        CharacterContentLoader.requireFields(json, FIELDS, file);
        String id = CharacterContentLoader.string(json, "id", file);
        int version = CharacterContentLoader.integer(json, "version", file);
        JsonObject scores = json.getAsJsonObject("abilities");
        AbilityScores abilities = new AbilityScores(
            CharacterContentLoader.integer(scores, "str", file), CharacterContentLoader.integer(scores, "dex", file),
            CharacterContentLoader.integer(scores, "con", file), CharacterContentLoader.integer(scores, "int", file),
            CharacterContentLoader.integer(scores, "wis", file), CharacterContentLoader.integer(scores, "cha", file));
        Map<Ability, Integer> saves = new EnumMap<>(Ability.class);
        if (json.has("saves")) for (var entry : json.getAsJsonObject("saves").entrySet())
            saves.put(Ability.byId(entry.getKey()), entry.getValue().getAsInt());
        Map<MovementMode, Integer> speeds = new EnumMap<>(MovementMode.class);
        for (var entry : json.getAsJsonObject("speeds").entrySet())
            speeds.put(MovementMode.valueOf(entry.getKey().toUpperCase(java.util.Locale.ROOT)), entry.getValue().getAsInt());
        List<String> traits = strings(json, "traits", file);
        List<MobAction> actions = new ArrayList<>();
        for (JsonElement value : CharacterContentLoader.array(json, "actions", file))
            actions.add(action(value.getAsJsonObject(), file));
        return new MobDefinition(id, version, abilities, saves, speeds,
            damageTypes(json, "resistances"), damageTypes(json, "immunities"), damageTypes(json, "vulnerabilities"),
            new HashSet<>(strings(json, "condition_immunities", file)), traits, actions,
            json.has("multiattack") ? json.get("multiattack").getAsInt() : 1,
            json.has("recharge_min") ? json.get("recharge_min").getAsInt() : 0,
            json.has("legendary_resistance") ? json.get("legendary_resistance").getAsInt() : 0);
    }

    private static MobAction action(JsonObject json, Path file) {
        String id = CharacterContentLoader.string(json, "id", file);
        String kind = CharacterContentLoader.string(json, "kind", file);
        List<DamageComponent> damage = new ArrayList<>();
        if (json.has("damage")) for (JsonElement value : json.getAsJsonArray("damage")) {
            JsonObject part = value.getAsJsonObject();
            damage.add(new DamageComponent(part.has("dice") ? part.get("dice").getAsInt() : 0,
                part.has("sides") ? part.get("sides").getAsInt() : 0,
                part.has("flat") ? part.get("flat").getAsInt() : 0,
                DamageType.byId(CharacterContentLoader.string(part, "type", file))));
        }
        return switch (kind) {
            case "ATTACK" -> {
                CharacterContentLoader.requireFields(json, Set.of("id", "kind", "attack_bonus", "reach_ticks", "range_ticks", "damage"), file);
                yield new MobAction.Attack(id, json.get("attack_bonus").getAsInt(),
                    json.has("reach_ticks") ? json.get("reach_ticks").getAsInt() : 0,
                    json.has("range_ticks") ? json.get("range_ticks").getAsInt() : 0, damage);
            }
            case "SAVE_AREA" -> {
                CharacterContentLoader.requireFields(json, Set.of("id", "kind", "save_ability", "save_dc", "half_on_save", "radius_ticks", "range_ticks", "damage"), file);
                yield new MobAction.SaveArea(id, Ability.byId(json.get("save_ability").getAsString()), json.get("save_dc").getAsInt(),
                    json.get("half_on_save").getAsBoolean(), json.get("radius_ticks").getAsInt(),
                    json.has("range_ticks") ? json.get("range_ticks").getAsInt() : 0, damage);
            }
            default -> throw new IllegalArgumentException("Unsupported mob action executor: " + kind);
        };
    }

    private static Set<DamageType> damageTypes(JsonObject json, String key) {
        Set<DamageType> out = new HashSet<>();
        if (json.has(key)) for (JsonElement value : json.getAsJsonArray(key))
            out.add(DamageType.byId(value.getAsString()));
        return out;
    }

    private static List<String> strings(JsonObject json, String key, Path file) {
        List<String> out = new ArrayList<>();
        if (json.has(key)) for (JsonElement value : CharacterContentLoader.array(json, key, file))
            out.add(value.getAsString());
        return out;
    }
}
