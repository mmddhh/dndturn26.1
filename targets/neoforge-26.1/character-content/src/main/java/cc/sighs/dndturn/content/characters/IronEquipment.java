package cc.sighs.dndturn.content.characters;

import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.ArmorCategory;
import cc.sighs.dndturn.domain.character.EquipmentRules.*;
import java.util.LinkedHashMap;
import java.util.Map;

final class IronEquipment {
    static Map<String, Armor> armorProfiles() {
        var combined = new LinkedHashMap<>(ARMOR);
        combined.put("minecraft:leather",new Armor(ArmorCategory.LIGHT,11,0,false));
        combined.put("minecraft:chainmail",new Armor(ArmorCategory.HEAVY,16,0,false));
        combined.put("minecraft:iron",new Armor(ArmorCategory.HEAVY,16,0,false));
        combined.put("minecraft:golden",new Armor(ArmorCategory.MEDIUM,14,0,false));
        combined.put("minecraft:diamond",new Armor(ArmorCategory.HEAVY,18,0,false));
        combined.put("minecraft:netherite",new Armor(ArmorCategory.HEAVY,18,0,false));
        return Map.copyOf(combined);
    }

    public static final Map<String, Weapon> WEAPONS;
    public static final Map<String, Armor> ARMOR;

    static {
        var weapons = new LinkedHashMap<String, Weapon>();
        var armor = new LinkedHashMap<String, Armor>();
        weapon(weapons, "club", 1, 4, 0, "bludgeoning", Ability.STR, false, Property.LIGHT);
        weapon(weapons, "dagger", 1, 4, 0, "piercing", Ability.DEX, false, Property.FINESSE, Property.LIGHT, Property.THROWN);
        weapon(weapons, "greatclub", 1, 8, 0, "bludgeoning", Ability.STR, false, Property.TWO_HANDED);
        weapon(weapons, "handaxe", 1, 6, 0, "slashing", Ability.STR, false, Property.LIGHT, Property.THROWN);
        weapon(weapons, "javelin", 1, 6, 0, "piercing", Ability.STR, false, Property.THROWN);
        weapon(weapons, "light_hammer", 1, 4, 0, "bludgeoning", Ability.STR, false, Property.LIGHT, Property.THROWN);
        weapon(weapons, "mace", 1, 6, 0, "bludgeoning", Ability.STR, false);
        weapon(weapons, "quarterstaff", 1, 6, 8, "bludgeoning", Ability.STR, false, Property.VERSATILE);
        weapon(weapons, "sickle", 1, 4, 0, "slashing", Ability.STR, false, Property.LIGHT);
        weapon(weapons, "spear", 1, 6, 8, "piercing", Ability.STR, false, Property.THROWN, Property.VERSATILE);
        weapon(weapons, "light_crossbow", 1, 8, 0, "piercing", Ability.DEX, true, Property.AMMUNITION, Property.LOADING, Property.TWO_HANDED);
        weapon(weapons, "dart", 1, 4, 0, "piercing", Ability.DEX, false, Property.FINESSE, Property.THROWN);
        weapon(weapons, "shortbow", 1, 6, 0, "piercing", Ability.DEX, true, Property.AMMUNITION, Property.TWO_HANDED);
        weapon(weapons, "sling", 1, 4, 0, "bludgeoning", Ability.DEX, true, Property.AMMUNITION);
        weapon(weapons, "battleaxe", 1, 8, 10, "slashing", Ability.STR, false, Property.VERSATILE);
        weapon(weapons, "flail", 1, 8, 0, "bludgeoning", Ability.STR, false);
        weapon(weapons, "glaive", 1, 10, 0, "slashing", Ability.STR, false, Property.HEAVY, Property.REACH, Property.TWO_HANDED);
        weapon(weapons, "greataxe", 1, 12, 0, "slashing", Ability.STR, false, Property.HEAVY, Property.TWO_HANDED);
        weapon(weapons, "greatsword", 2, 6, 0, "slashing", Ability.STR, false, Property.HEAVY, Property.TWO_HANDED);
        weapon(weapons, "halberd", 1, 10, 0, "slashing", Ability.STR, false, Property.HEAVY, Property.REACH, Property.TWO_HANDED);
        weapon(weapons, "lance", 1, 12, 0, "piercing", Ability.STR, false, Property.REACH, Property.SPECIAL);
        weapon(weapons, "longsword", 1, 8, 10, "slashing", Ability.STR, false, Property.VERSATILE);
        weapon(weapons, "maul", 2, 6, 0, "bludgeoning", Ability.STR, false, Property.HEAVY, Property.TWO_HANDED);
        weapon(weapons, "morningstar", 1, 8, 0, "piercing", Ability.STR, false);
        weapon(weapons, "pike", 1, 10, 0, "piercing", Ability.STR, false, Property.HEAVY, Property.REACH, Property.TWO_HANDED);
        weapon(weapons, "rapier", 1, 8, 0, "piercing", Ability.DEX, false, Property.FINESSE);
        weapon(weapons, "scimitar", 1, 6, 0, "slashing", Ability.DEX, false, Property.FINESSE, Property.LIGHT);
        weapon(weapons, "shortsword", 1, 6, 0, "piercing", Ability.DEX, false, Property.FINESSE, Property.LIGHT);
        weapon(weapons, "trident", 1, 6, 8, "piercing", Ability.STR, false, Property.THROWN, Property.VERSATILE);
        weapon(weapons, "war_pick", 1, 8, 0, "piercing", Ability.STR, false);
        weapon(weapons, "warhammer", 1, 8, 10, "bludgeoning", Ability.STR, false, Property.VERSATILE);
        weapon(weapons, "whip", 1, 4, 0, "slashing", Ability.DEX, false, Property.FINESSE, Property.REACH);
        weapon(weapons, "blowgun", 1, 1, 0, "piercing", Ability.DEX, true, Property.AMMUNITION, Property.LOADING);
        weapon(weapons, "hand_crossbow", 1, 6, 0, "piercing", Ability.DEX, true, Property.AMMUNITION, Property.LIGHT, Property.LOADING);
        weapon(weapons, "heavy_crossbow", 1, 10, 0, "piercing", Ability.DEX, true, Property.AMMUNITION, Property.HEAVY, Property.LOADING, Property.TWO_HANDED);
        weapon(weapons, "longbow", 1, 8, 0, "piercing", Ability.DEX, true, Property.AMMUNITION, Property.HEAVY, Property.TWO_HANDED);
        weapon(weapons, "net", 0, 0, 0, "bludgeoning", Ability.DEX, false, Property.SPECIAL, Property.THROWN);

        armor(armor, "padded", ArmorCategory.LIGHT, 11, 0, true);
        armor(armor, "leather", ArmorCategory.LIGHT, 11, 0, false);
        armor(armor, "studded_leather", ArmorCategory.LIGHT, 12, 0, false);
        armor(armor, "hide", ArmorCategory.MEDIUM, 12, 0, false);
        armor(armor, "chain_shirt", ArmorCategory.MEDIUM, 13, 0, false);
        armor(armor, "scale_mail", ArmorCategory.MEDIUM, 14, 0, true);
        armor(armor, "breastplate", ArmorCategory.MEDIUM, 14, 0, false);
        armor(armor, "half_plate", ArmorCategory.MEDIUM, 15, 0, true);
        armor(armor, "ring_mail", ArmorCategory.HEAVY, 14, 0, true);
        armor(armor, "chain_mail", ArmorCategory.HEAVY, 16, 13, true);
        armor(armor, "splint", ArmorCategory.HEAVY, 17, 15, true);
        armor(armor, "plate", ArmorCategory.HEAVY, 18, 15, true);
        WEAPONS = java.util.Collections.unmodifiableMap(weapons);
        ARMOR = java.util.Collections.unmodifiableMap(armor);
    }

    private IronEquipment() {}

    private static void weapon(Map<String, Weapon> weapons, String id, int dice, int sides, int versatile, String type,
                               Ability ability, boolean ranged, Property... properties) {
        weapons.put("dndturn:iron_" + id, new Weapon(dice, sides, versatile, cc.sighs.dndturn.domain.mob.DamageType.byId(type), ability, ranged, java.util.Set.of(properties)));
    }

    private static void armor(Map<String, Armor> armor, String id, ArmorCategory category, int ac, int strength, boolean stealth) {
        armor.put("dndturn:iron_" + id, new Armor(category, ac, strength, stealth));
    }
}
