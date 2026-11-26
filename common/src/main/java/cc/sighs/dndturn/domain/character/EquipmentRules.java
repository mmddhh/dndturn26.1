package cc.sighs.dndturn.domain.character;

import java.util.LinkedHashMap;
import java.util.Map;

/** Equipment profile values and slot bindings for a captured catalog. */
public final class EquipmentRules {
    public record Weapon(int dice, int sides, int versatileSides, cc.sighs.dndturn.domain.mob.DamageType damageType, Ability ability,
                         boolean ranged, java.util.Set<Property> properties) {
        public Weapon { properties = java.util.Set.copyOf(properties); }
        public boolean finesse() { return properties.contains(Property.FINESSE); }
    }
    public enum Property { LIGHT, FINESSE, THROWN, TWO_HANDED, VERSATILE, AMMUNITION, LOADING, HEAVY, REACH, SPECIAL }
    public record Armor(ArmorCategory category, int baseAc, int strength, boolean stealth) {}

    public enum ArmorSlot { HEAD, CHEST, LEGS, FEET }
    public record ArmorBinding(String setId, ArmorSlot slot, Armor rule) {}

    /** Explicit full-ID bindings; a slot cannot borrow another set's numerically equal rule. */
    public static Map<String, ArmorBinding> armorBindings(Map<String, Armor> armor) {
        var bindings = new LinkedHashMap<String, ArmorBinding>();
        armor.forEach((id, rule) -> bindArmor(bindings,id,rule));
        return Map.copyOf(bindings);
    }
    private static void bindArmor(Map<String, ArmorBinding> bindings, String id, Armor rule) {
        bindings.put(id+"_helmet",new ArmorBinding(id,ArmorSlot.HEAD,rule));
        bindings.put(id+"_chestplate",new ArmorBinding(id,ArmorSlot.CHEST,rule));
        bindings.put(id+"_leggings",new ArmorBinding(id,ArmorSlot.LEGS,rule));
        bindings.put(id+"_boots",new ArmorBinding(id,ArmorSlot.FEET,rule));
    }

    private EquipmentRules() {}
}
