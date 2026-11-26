package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.EquipmentRules;
import java.util.List;
import java.util.ArrayList;
import java.io.StringReader;
import com.google.gson.stream.JsonReader;
import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.CharacterClass;
import cc.sighs.dndturn.domain.character.CharacterRace;
import cc.sighs.dndturn.domain.character.RulesCatalog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Loads creator-authored class/race JSON. A failed file rejects the whole revision, never a partial list. */
public final class CharacterContentLoader {
    private static final int MAX_FILES = RulesCatalog.MAX_ENTRIES;
    private static final int MAX_FILE_BYTES = 64 * 1024;
    private static final Set<String> CLASS_FIELDS = Set.of("id", "version", "name", "hit_die", "saves");
    private static final Set<String> RACE_FIELDS = Set.of("id", "version", "name", "speed", "size", "ability_boosts");

    private static final Map<String, EquipmentRules.Weapon> weapons = new LinkedHashMap<>();
    private static final Map<String, EquipmentRules.Armor> armor = new LinkedHashMap<>();
    private static boolean frozen;

    public static synchronized void registerEquipment(Map<String, EquipmentRules.Weapon> newWeapons,
                                                      Map<String, EquipmentRules.Armor> newArmor) {
        if (frozen) throw new IllegalStateException("equipment registration is closed");
        var validated = new RulesCatalog("registration", Map.of(), Map.of(), Map.of(), newWeapons, newArmor);
        if (weapons.size() + newWeapons.size() > RulesCatalog.MAX_ENTRIES
                || armor.size() + newArmor.size() > RulesCatalog.MAX_ENTRIES
                || !java.util.Collections.disjoint(weapons.keySet(), newWeapons.keySet())
                || !java.util.Collections.disjoint(armor.keySet(), newArmor.keySet()))
            throw new IllegalArgumentException("duplicate or excessive equipment profiles");
        weapons.putAll(validated.weapons());
        armor.putAll(validated.armor());
    }

    public static synchronized void freezeEquipment() { frozen = true; }

    private CharacterContentLoader() {}

    public static RulesCatalog load(Path root) {
        Map<String, CharacterClass> classes = new LinkedHashMap<>();
        Map<String, CharacterRace> races = new LinkedHashMap<>();
        Map<String, cc.sighs.dndturn.domain.mob.MobDefinition> mobs = new LinkedHashMap<>();
        try {
            loadInstalledCatalogs(classes, races, mobs);
            loadClasses(root.resolve("classes"), classes);
            loadRaces(root.resolve("races"), races);
            loadMobs(root.resolve("mobs"), mobs);
        } catch (IOException error) {
            throw new IllegalStateException("cannot load character content from " + root, error);
        }
        var catalog = new RulesCatalog(UUID.randomUUID().toString(), classes, races, mobs, weapons, armor);
        var page = UUID.randomUUID();
        CharacterOptions.project(catalog, page, page);
        catalog.classes().values().forEach(definition -> CharacterOptions.detail(definition, page));
        return catalog;
    }

    private static void loadInstalledCatalogs(Map<String, CharacterClass> classes, Map<String, CharacterRace> races,
                                              Map<String, cc.sighs.dndturn.domain.mob.MobDefinition> mobs)
            throws IOException {
        var seenClasses = new HashSet<String>();
        var seenRaces = new HashSet<String>();
        for (var mod : net.neoforged.fml.ModList.get().getMods()) {
            String resource = "data/" + mod.getModId() + "/dndturn/catalog.json";
            Path file = Path.of(resource);
            JsonObject catalog;
            try (var input = mod.getOwningFile().getFile().getContents().openFile(resource)) {
                if (input == null) continue;
                catalog = read(input, file);
            }
            if (catalog.has("classes"))
                for (var value : array(catalog, "classes", file))
                    loadClass(value.getAsJsonObject(), file, classes, seenClasses);
            if (catalog.has("races"))
                for (var value : array(catalog, "races", file))
                    loadRace(value.getAsJsonObject(), file, races, seenRaces);
            if (catalog.has("mobs"))
                for (var value : array(catalog, "mobs", file)) {
                    var mob = MobDefinitionLoader.read(value.getAsJsonObject(), file);
                    if (mobs.putIfAbsent(mob.id(), mob) != null)
                        throw new IllegalArgumentException("duplicate mob " + mob.id() + " in " + file);
                }
        }
    }

    private static void loadMobs(Path directory, Map<String, cc.sighs.dndturn.domain.mob.MobDefinition> target)
            throws IOException {        Set<String> seen = new HashSet<>();
        for (Path file : files(directory)) {
            var json = read(file);
            var mob = MobDefinitionLoader.read(json, file);
            if (!seen.add(mob.id())) throw new IllegalArgumentException("duplicate mob id: " + mob.id() + " in " + file);
            target.put(mob.id(), mob);
        }
    }

    private static void loadClasses(Path directory, Map<String, CharacterClass> target) throws IOException {
        Set<String> seen = new HashSet<>();
        for (Path file : files(directory)) {
            JsonObject json = read(file);
            loadClass(json, file, target, seen);
        }
    }

    private static void loadClass(JsonObject json, Path file, Map<String, CharacterClass> target,
                                  Set<String> seen) {
        requireFields(json, CLASS_FIELDS, file);
        String id = string(json, "id", file);
        int version = integer(json, "version", file);
        String name = string(json, "name", file);
        int hitDie = integer(json, "hit_die", file);
        var saves = new HashSet<Ability>();
        for (JsonElement value : array(json, "saves", file))
            if (!saves.add(Ability.byId(value.getAsString()))) throw new IllegalArgumentException("duplicate save proficiency");
        var levels = new HashSet<Integer>();
        for (var value : array(json, "asi_levels", file))
            if (!levels.add(value.getAsBigDecimal().intValueExact())) throw new IllegalArgumentException("duplicate ASI level");
        var subclasses = new ArrayList<String>();
        for (var value : array(json, "subclasses", file)) subclasses.add(value.getAsString());
        var kits = new LinkedHashMap<String, List<CharacterClass.Stack>>();
        object(json, "equipment", file).entrySet().forEach(entry -> {
            var stacks = new ArrayList<CharacterClass.Stack>();
            for (var value : entry.getValue().getAsJsonArray()) {
                var item = value.getAsJsonObject();
                var stack = new CharacterClass.Stack(string(item, "item", file), integer(item, "count", file));
                CharacterEquipment.resolve(stack.item());
                stacks.add(stack);
            }
            kits.put(entry.getKey(), stacks);
        });
        CharacterClass characterClass = new CharacterClass(id, version, name, hitDie, saves, levels,
            integer(json, "subclass_level", file), subclasses, kits);
        if (seen != null && !seen.add(id))
            throw new IllegalArgumentException("duplicate class id " + id + " in " + file);
        target.put(id, characterClass);
    }

    private static void loadRaces(Path directory, Map<String, CharacterRace> target) throws IOException {
        Set<String> seen = new HashSet<>();
        for (Path file : files(directory)) {
            JsonObject json = read(file);
            loadRace(json, file, target, seen);
        }
    }

    private static void loadRace(JsonObject json, Path file, Map<String, CharacterRace> target,
                                 Set<String> seen) {
        requireFields(json, RACE_FIELDS, file);
        String id = string(json, "id", file);
        int version = integer(json, "version", file);
        String name = string(json, "name", file);
        int speed = integer(json, "speed", file);
        String size = string(json, "size", file);
        var boosts = new EnumMap<Ability, Integer>(Ability.class);
        JsonObject boostJson = object(json, "ability_boosts", file);
        for (var entry : boostJson.entrySet()) {
            boosts.put(Ability.byId(entry.getKey()), integer(boostJson, entry.getKey(), file));
        }
        var slots = new LinkedHashMap<String, List<Integer>>();
        object(json, "subraces", file).entrySet().forEach(entry -> {
            var values = new ArrayList<Integer>();
            for (var value : entry.getValue().getAsJsonArray()) values.add(value.getAsBigDecimal().intValueExact());
            slots.put(entry.getKey(), values);
        });
        CharacterRace race = new CharacterRace(id, version, name, speed, size, boosts, slots);
        if (seen != null && !seen.add(id))
            throw new IllegalArgumentException("duplicate race id " + id + " in " + file);
        target.put(id, race);
    }

    private static java.util.List<Path> files(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return java.util.List.of();
        try (Stream<Path> stream = Files.list(directory)) {
            var files = stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .limit(MAX_FILES + 1L)
                .sorted()
                .toList();
            if (files.size() > MAX_FILES) throw new IllegalArgumentException("too many content files in " + directory);
            return files;
        }
    }

    private static JsonObject read(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            return read(input, file);
        }
    }

    private static JsonObject read(java.io.InputStream input, Path file) throws IOException {
        byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("content file too large: " + file);
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        try (var reader = new JsonReader(new StringReader(text))) {
            checkKeys(reader, 0);
        }
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("content file must be an object: " + file);
        return parsed.getAsJsonObject();
    }

    private static void checkKeys(JsonReader reader, int depth) throws IOException {
        if (depth > 48) throw new IllegalArgumentException("content nesting limit");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var names = new HashSet<String>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (!names.add(key)) throw new IllegalArgumentException("duplicate content key: " + key);
                    checkKeys(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) checkKeys(reader, depth + 1);
                reader.endArray();
            }
            default -> reader.skipValue();
        }
    }

    static void requireFields(JsonObject json, Set<String> fields, Path file) {
        for (String field : fields) if (!json.has(field)) throw new IllegalArgumentException("missing " + field + " in " + file);
    }

    static String string(JsonObject json, String field, Path file) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(field + " must be a string in " + file);
        return value.getAsString();
    }

    static int integer(JsonObject json, String field, Path file) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(field + " must be a number in " + file);
        return value.getAsBigDecimal().intValueExact();
    }

    private static JsonObject object(JsonObject json, String field, Path file) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonObject())
            throw new IllegalArgumentException(field + " must be an object in " + file);
        return value.getAsJsonObject();
    }

    static Iterable<JsonElement> array(JsonObject json, String field, Path file) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonArray())
            throw new IllegalArgumentException(field + " must be an array in " + file);
        return value.getAsJsonArray();
    }
}
