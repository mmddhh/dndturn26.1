package cc.sighs.dndturn.platform.server.character;

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
    private static final long MAX_FILE_BYTES = 64 * 1024;
    private static final Set<String> CLASS_FIELDS = Set.of("id", "version", "name", "hit_die", "saves");
    private static final Set<String> RACE_FIELDS = Set.of("id", "version", "name", "speed", "size", "ability_boosts");

    private CharacterContentLoader() {}

    public static RulesCatalog load(Path root) {
        Map<String, CharacterClass> classes = new LinkedHashMap<>();
        Map<String, CharacterRace> races = new LinkedHashMap<>();
        Map<String, cc.sighs.dndturn.domain.mob.MobDefinition> mobs = new LinkedHashMap<>();
        try {
            loadDefaults(classes, races);
            loadDefaultMobs(mobs);
            loadClasses(root.resolve("classes"), classes);
            loadRaces(root.resolve("races"), races);
            loadMobs(root.resolve("mobs"), mobs);
        } catch (IOException error) {
            throw new IllegalStateException("cannot load character content from " + root, error);
        }
        return new RulesCatalog(UUID.randomUUID().toString(), classes, races, mobs);
    }

    private static void loadDefaultMobs(Map<String, cc.sighs.dndturn.domain.mob.MobDefinition> target)
            throws IOException {
        try (var in = CharacterContentLoader.class.getResourceAsStream("/dndturn-content/default-mobs.json")) {
            if (in == null) return;
            var parsed = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("default mob content must be an object");
            var root = parsed.getAsJsonObject();
            for (JsonElement value : array(root, "mobs", Path.of("default-mobs.json"))) {
                var mob = MobDefinitionLoader.read(value.getAsJsonObject(), Path.of("default-mobs.json"));
                target.put(mob.id(), mob);
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

    private static void loadDefaults(Map<String, CharacterClass> classes, Map<String, CharacterRace> races)
            throws IOException {
        try (var in = CharacterContentLoader.class.getResourceAsStream("/dndturn-content/defaults.json")) {
            if (in == null) return;
            JsonElement parsed = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("default content must be an object");
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("classes")) {
                for (JsonElement value : array(root, "classes", Path.of("defaults.json")))
                    loadClass(value.getAsJsonObject(), Path.of("defaults.json"), classes, null);
            }
            if (root.has("races")) {
                for (JsonElement value : array(root, "races", Path.of("defaults.json")))
                    loadRace(value.getAsJsonObject(), Path.of("defaults.json"), races, null);
            }
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
        for (JsonElement value : array(json, "saves", file)) saves.add(Ability.byId(value.getAsString()));
        CharacterClass characterClass = new CharacterClass(id, version, name, hitDie, saves);
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
            boosts.put(Ability.byId(entry.getKey()), entry.getValue().getAsInt());
        }
        CharacterRace race = new CharacterRace(id, version, name, speed, size, boosts);
        if (seen != null && !seen.add(id))
            throw new IllegalArgumentException("duplicate race id " + id + " in " + file);
        target.put(id, race);
    }

    private static java.util.List<Path> files(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return java.util.List.of();
        try (Stream<Path> stream = Files.list(directory)) {
            var files = stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted()
                .toList();
            if (files.size() > MAX_FILES) throw new IllegalArgumentException("too many content files in " + directory);
            return files;
        }
    }

    private static JsonObject read(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) throw new IllegalArgumentException("content file too large: " + file);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("content file must be an object: " + file);
        return parsed.getAsJsonObject();
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
        return value.getAsInt();
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
