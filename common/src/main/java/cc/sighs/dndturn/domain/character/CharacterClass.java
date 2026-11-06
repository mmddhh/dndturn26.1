package cc.sighs.dndturn.domain.character;

import java.util.Objects;
import java.util.Set;

/** Content definition of a player class. JSON content resolves to this immutable value. */
public record CharacterClass(String id, int version, String name, int hitDie, Set<Ability> saveProficiencies) {
    public CharacterClass {
        requireId(id);
        Objects.requireNonNull(name);
        saveProficiencies = Set.copyOf(saveProficiencies);
        if (version < 1 || name.isBlank() || name.length() > 128 || hitDie < 2 || hitDie > 100
                || saveProficiencies.size() > Ability.values().length)
            throw new IllegalArgumentException("character class");
    }

    public int proficiencyBonus(int level) { return DndLeveling.proficiencyBonus(level); }

    public boolean proficientInSave(Ability ability) { return saveProficiencies.contains(ability); }

    private static void requireId(String id) {
        Objects.requireNonNull(id);
        if (id.isBlank() || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("class id");
    }
}
