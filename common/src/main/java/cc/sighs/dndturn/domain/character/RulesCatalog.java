package cc.sighs.dndturn.domain.character;

import cc.sighs.dndturn.domain.mob.MobDefinition;
import java.util.Map;
import java.util.Objects;

/** Immutable player/mob-content revision. A running encounter keeps the revision it started with. */
public record RulesCatalog(String revision, Map<String, CharacterClass> classes,
                              Map<String, CharacterRace> races, Map<String, MobDefinition> mobs,
                           Map<String, EquipmentRules.Weapon> weapons, Map<String, EquipmentRules.Armor> armor) {
    public static final int MAX_ENTRIES = 512;

    public RulesCatalog {
        Objects.requireNonNull(revision);
        classes = Map.copyOf(classes);
        weapons = Map.copyOf(weapons); armor = Map.copyOf(armor);
        races = Map.copyOf(races);
        mobs = mobs == null ? Map.of() : Map.copyOf(mobs);
        if (revision.isBlank() || revision.length() > 128
                || weapons.size() > MAX_ENTRIES || armor.size() > MAX_ENTRIES
                || classes.size() > MAX_ENTRIES || races.size() > MAX_ENTRIES || mobs.size() > MAX_ENTRIES)
            throw new IllegalArgumentException("content snapshot");
        weapons.keySet().forEach(cc.sighs.dndturn.domain.fact.FactKey::requireId);
        armor.keySet().forEach(cc.sighs.dndturn.domain.fact.FactKey::requireId);
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

    public RulesCatalog(String revision, Map<String, CharacterClass> classes, Map<String, CharacterRace> races, Map<String, MobDefinition> mobs) {
        this(revision, classes, races, mobs, EquipmentRules.WEAPONS, EquipmentRules.ARMOR);
    }

    public RulesCatalog(String revision, Map<String, CharacterClass> classes, Map<String, CharacterRace> races) {
        this(revision, classes, races, Map.of());
    }

    public Map<String, EquipmentRules.ArmorBinding> armorBindings() { return EquipmentRules.armorBindings(armor); }

    public static RulesCatalog empty() { return new RulesCatalog("empty", Map.of(), Map.of(), Map.of()); }

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

    /** Only the supported single-component ordinary attack enters the ordinary attack executor. */
    public cc.sighs.dndturn.domain.resolution.AttackProfile attackProfile(String id) {
        var definition = mobOrFallback(id);
        for (var action : definition.actions()) {
            if (!(action instanceof cc.sighs.dndturn.domain.mob.MobAction.Attack attack) || action.damage().size() != 1) continue;
            var damage = action.damage().get(0);
            return new cc.sighs.dndturn.domain.resolution.AttackProfile(attack.attackBonus(), damage.dice(),
                damage.sides(), damage.flatBonus(), damage.type());
        }
        return new cc.sighs.dndturn.domain.resolution.AttackProfile(5, 0, 0, 10, cc.sighs.dndturn.domain.mob.DamageType.BLUDGEONING);
    }

    public MobDefinition mobOrFallback(String id) {
        MobDefinition value = mobs.get(id);
        return value == null ? MobDefinition.fallback(id) : value;
    }
}
