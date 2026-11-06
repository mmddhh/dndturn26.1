package cc.sighs.dndturn.domain.character;

import cc.sighs.dndturn.domain.mob.MobDefinition;
import java.util.Map;
import java.util.Objects;

/** Immutable player/mob-content revision. A running encounter keeps the revision it started with. */
public record ContentSnapshot(String revision, Map<String, CharacterClass> classes,
                              Map<String, CharacterRace> races, Map<String, MobDefinition> mobs) {
    public static final int MAX_ENTRIES = 512;

    public ContentSnapshot {
        Objects.requireNonNull(revision);
        classes = Map.copyOf(classes);
        races = Map.copyOf(races);
        mobs = mobs == null ? Map.of() : Map.copyOf(mobs);
        if (revision.isBlank() || revision.length() > 128
                || classes.size() > MAX_ENTRIES || races.size() > MAX_ENTRIES || mobs.size() > MAX_ENTRIES)
            throw new IllegalArgumentException("content snapshot");
        classes.forEach((id, value) -> {
            if (!id.equals(value.id())) throw new IllegalArgumentException("class key mismatch");
        });
        races.forEach((id, value) -> {
            if (!id.equals(value.id())) throw new IllegalArgumentException("race key mismatch");
        });
        mobs.forEach((id, value) -> {
            if (!id.equals(value.id())) throw new IllegalArgumentException("mob key mismatch");
        });
    }

    public ContentSnapshot(String revision, Map<String, CharacterClass> classes, Map<String, CharacterRace> races) {
        this(revision, classes, races, Map.of());
    }

    public static ContentSnapshot empty() { return new ContentSnapshot("empty", Map.of(), Map.of(), Map.of()); }

    public CharacterClass requireClass(String id) {
        CharacterClass value = classes.get(id);
        if (value == null) throw new IllegalArgumentException("unknown class: " + id);
        return value;
    }

    public CharacterRace requireRace(String id) {
        CharacterRace value = races.get(id);
        if (value == null) throw new IllegalArgumentException("unknown race: " + id);
        return value;
    }

    public MobDefinition requireMob(String id) {
        MobDefinition value = mobs.get(id);
        if (value == null) throw new IllegalArgumentException("unknown mob: " + id);
        return value;
    }

    public MobDefinition mobOrFallback(String id) {
        MobDefinition value = mobs.get(id);
        return value == null ? MobDefinition.fallback(id) : value;
    }
}
