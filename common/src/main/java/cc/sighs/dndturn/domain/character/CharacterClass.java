package cc.sighs.dndturn.domain.character;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Content definition of a player class. JSON content resolves to this immutable value. */
public record CharacterClass(String id, int version, String name, int hitDie, Set<Ability> saveProficiencies,
                             Set<Integer> asiLevels, int subclassLevel, List<String> subclasses,
                             Map<String, List<Stack>> equipment) {
    public record Stack(String item, int count) {
        public Stack {
            requireId(item);
            if (count < 1 || count > 64) throw new IllegalArgumentException("equipment count");
        }
    }
    public CharacterClass {
        requireId(id);
        asiLevels = Set.copyOf(asiLevels);
        subclasses = List.copyOf(subclasses);
        if (subclassLevel < 1 || subclassLevel > 20 || subclasses.size() > 64
            || Set.copyOf(subclasses).size() != subclasses.size()
            || asiLevels.stream().anyMatch(level -> level < 1 || level > 20) || equipment.size() > 64)
            throw new IllegalArgumentException("class creation options");
        subclasses.forEach(CharacterClass::requireId);
        var kits = new LinkedHashMap<String,List<Stack>>();
        equipment.forEach((key, stacks) -> {
            if (!key.matches("[a-z0-9_.-]{1,64}") || stacks.size() > 64)
                throw new IllegalArgumentException("equipment option");
            kits.put(key, List.copyOf(stacks));
        });
        equipment = Map.copyOf(kits);
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
