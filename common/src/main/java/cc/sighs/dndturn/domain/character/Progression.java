package cc.sighs.dndturn.domain.character;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Player-only progression. Class, subclass and race ids are content keys, not native entity types. */
public record Progression(String raceId, String subraceId, String classId, String subclassId,
                          int level, int experience, int pendingAsi, Set<Integer> asiLevels) {
    public Progression {
        requireId(raceId, "race");
        if (subraceId != null && !subraceId.isBlank()) requireId(subraceId, "subrace");
        requireId(classId, "class");
        if (subclassId != null && !subclassId.isBlank()) requireId(subclassId, "subclass");
        if (level < 1 || level > DndLeveling.MAX_LEVEL) throw new IllegalArgumentException("character level");
        if (experience < 0 || pendingAsi < 0 || pendingAsi > DndLeveling.MAX_LEVEL)
            throw new IllegalArgumentException("character experience or pending ASI");
        asiLevels = Set.copyOf(asiLevels);
        for (int asiLevel : asiLevels)
            if (asiLevel < 1 || asiLevel > DndLeveling.MAX_LEVEL)
                throw new IllegalArgumentException("ASI level");
        if (pendingAsi > asiLevels.size()) throw new IllegalArgumentException("pending ASI exceeds granted levels");
    }

    public Progression(String raceId, String classId, int level, int experience) {
        this(raceId, null, classId, null, level, experience, 0, Set.of());
    }

    public int proficiencyBonus() { return DndLeveling.proficiencyBonus(level); }

    public Progression withExperience(int value) {
        return new Progression(raceId, subraceId, classId, subclassId, level, value, pendingAsi, asiLevels);
    }

    public Progression withLevel(int value) {
        return new Progression(raceId, subraceId, classId, subclassId, value, experience, pendingAsi, asiLevels);
    }

    public Progression withSubclass(String value) {
        return new Progression(raceId, subraceId, classId, value, level, experience, pendingAsi, asiLevels);
    }

    /** Adds one ASI level at most once; the caller still has to spend the pending choice. */
    public Progression grantAsiLevel(int asiLevel) {
        if (asiLevels.contains(asiLevel)) return this;
        var levels = new TreeSet<>(asiLevels);
        levels.add(asiLevel);
        return new Progression(raceId, subraceId, classId, subclassId, level, experience,
                Math.addExact(pendingAsi, 1), levels);
    }

    public Progression spendAsi(int count) {
        if (count < 1 || count > pendingAsi) throw new IllegalArgumentException("no pending ASI");
        return new Progression(raceId, subraceId, classId, subclassId, level, experience,
                pendingAsi - count, asiLevels);
    }

    private static void requireId(String value, String label) {
        Objects.requireNonNull(value, label + " id");
        if (value.isBlank() || value.length() > 128 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("invalid " + label + " id");
    }
}
