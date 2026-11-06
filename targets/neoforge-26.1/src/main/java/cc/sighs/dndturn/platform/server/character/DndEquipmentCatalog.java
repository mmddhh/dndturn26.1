package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.Ability;
import java.util.LinkedHashMap;
import java.util.Map;

/** Data-only iron equipment catalog copied from the classes 5e tables (no real-time hooks). */
public final class DndEquipmentCatalog {
    public record Weapon(int dice, int sides, int versatileSides, String damageType, Ability ability,
                         boolean finesse, boolean ranged, String tags) {}
    public record Armor(String category, int baseAc, int strength, boolean stealth) {}

    public static final Map<String, Weapon> WEAPONS = new LinkedHashMap<>();
    public static final Map<String, Armor> ARMOR = new LinkedHashMap<>();

    static {
        weapon("club", 1, 4, 0, "bludgeoning", Ability.STR, false, false, "light");
        weapon("dagger", 1, 4, 0, "piercing", Ability.DEX, true, false, "finesse, light, thrown");
        weapon("greatclub", 1, 8, 0, "bludgeoning", Ability.STR, false, false, "two-handed");
        weapon("handaxe", 1, 6, 0, "slashing", Ability.STR, false, false, "light, thrown");
        weapon("javelin", 1, 6, 0, "piercing", Ability.STR, false, false, "thrown");
        weapon("light_hammer", 1, 4, 0, "bludgeoning", Ability.STR, false, false, "light, thrown");
        weapon("mace", 1, 6, 0, "bludgeoning", Ability.STR, false, false, "");
        weapon("quarterstaff", 1, 6, 8, "bludgeoning", Ability.STR, false, false, "versatile");
        weapon("sickle", 1, 4, 0, "slashing", Ability.STR, false, false, "light");
        weapon("spear", 1, 6, 8, "piercing", Ability.STR, false, false, "thrown, versatile");
        weapon("light_crossbow", 1, 8, 0, "piercing", Ability.DEX, false, true, "ammunition, loading, two-handed");
        weapon("dart", 1, 4, 0, "piercing", Ability.DEX, true, false, "finesse, thrown");
        weapon("shortbow", 1, 6, 0, "piercing", Ability.DEX, false, true, "ammunition, two-handed");
        weapon("sling", 1, 4, 0, "bludgeoning", Ability.DEX, false, true, "ammunition");
        weapon("battleaxe", 1, 8, 10, "slashing", Ability.STR, false, false, "versatile");
        weapon("flail", 1, 8, 0, "bludgeoning", Ability.STR, false, false, "");
        weapon("glaive", 1, 10, 0, "slashing", Ability.STR, false, false, "heavy, reach, two-handed");
        weapon("greataxe", 1, 12, 0, "slashing", Ability.STR, false, false, "heavy, two-handed");
        weapon("greatsword", 2, 6, 0, "slashing", Ability.STR, false, false, "heavy, two-handed");
        weapon("halberd", 1, 10, 0, "slashing", Ability.STR, false, false, "heavy, reach, two-handed");
        weapon("lance", 1, 12, 0, "piercing", Ability.STR, false, false, "reach, special");
        weapon("longsword", 1, 8, 10, "slashing", Ability.STR, false, false, "versatile");
        weapon("maul", 2, 6, 0, "bludgeoning", Ability.STR, false, false, "heavy, two-handed");
        weapon("morningstar", 1, 8, 0, "piercing", Ability.STR, false, false, "");
        weapon("pike", 1, 10, 0, "piercing", Ability.STR, false, false, "heavy, reach, two-handed");
        weapon("rapier", 1, 8, 0, "piercing", Ability.DEX, true, false, "finesse");
        weapon("scimitar", 1, 6, 0, "slashing", Ability.DEX, true, false, "finesse, light");
        weapon("shortsword", 1, 6, 0, "piercing", Ability.DEX, true, false, "finesse, light");
        weapon("trident", 1, 6, 8, "piercing", Ability.STR, false, false, "thrown, versatile");
        weapon("war_pick", 1, 8, 0, "piercing", Ability.STR, false, false, "");
        weapon("warhammer", 1, 8, 10, "bludgeoning", Ability.STR, false, false, "versatile");
        weapon("whip", 1, 4, 0, "slashing", Ability.DEX, true, false, "finesse, reach");
        weapon("blowgun", 1, 1, 0, "piercing", Ability.DEX, false, true, "ammunition, loading");
        weapon("hand_crossbow", 1, 6, 0, "piercing", Ability.DEX, false, true, "ammunition, light, loading");
        weapon("heavy_crossbow", 1, 10, 0, "piercing", Ability.DEX, false, true, "ammunition, heavy, loading, two-handed");
        weapon("longbow", 1, 8, 0, "piercing", Ability.DEX, false, true, "ammunition, heavy, two-handed");
        weapon("net", 0, 0, 0, "bludgeoning", Ability.DEX, false, false, "special, thrown");

        armor("padded", "light", 11, 0, true);
        armor("leather", "light", 11, 0, false);
        armor("studded_leather", "light", 12, 0, false);
        armor("hide", "medium", 12, 0, false);
        armor("chain_shirt", "medium", 13, 0, false);
        armor("scale_mail", "medium", 14, 0, true);
        armor("breastplate", "medium", 14, 0, false);
        armor("half_plate", "medium", 15, 0, true);
        armor("ring_mail", "heavy", 14, 0, true);
        armor("chain_mail", "heavy", 16, 13, true);
        armor("splint", "heavy", 17, 15, true);
        armor("plate", "heavy", 18, 15, true);
    }

    private DndEquipmentCatalog() {}

    private static void weapon(String id, int dice, int sides, int versatile, String type,
                               Ability ability, boolean finesse, boolean ranged, String tags) {
        WEAPONS.put(id, new Weapon(dice, sides, versatile, type, ability, finesse, ranged, tags));
    }

    private static void armor(String id, String category, int ac, int strength, boolean stealth) {
        ARMOR.put(id, new Armor(category, ac, strength, stealth));
    }
}
