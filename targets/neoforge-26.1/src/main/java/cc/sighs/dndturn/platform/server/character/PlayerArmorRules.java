package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.ArmorCategory;
import cc.sighs.dndturn.domain.character.CharacterRules;
import cc.sighs.dndturn.domain.character.CharacterState;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Interim player armor mapping until the dndclasses armor set is registered.
 * Only ready-made 5e-like vanilla sets count; mixed or partial sets are unarmored.
 */
public final class PlayerArmorRules {
    private record Profile(String setId, ArmorCategory category, int baseAc) {}
    private static final Map<String, Profile> PROFILES = profiles();

    private static Map<String, Profile> profiles() {
        var profiles = new java.util.LinkedHashMap<String, Profile>();
        addSet(profiles, "minecraft:leather", ArmorCategory.LIGHT, 11);
        addSet(profiles, "minecraft:chainmail", ArmorCategory.HEAVY, 16);
        addSet(profiles, "minecraft:iron", ArmorCategory.HEAVY, 16);
        addSet(profiles, "minecraft:golden", ArmorCategory.MEDIUM, 14);
        addSet(profiles, "minecraft:diamond", ArmorCategory.HEAVY, 18);
        addSet(profiles, "minecraft:netherite", ArmorCategory.HEAVY, 18);
        cc.sighs.dndturn.domain.character.EquipmentRules.ARMOR.forEach((id, armor) ->
            addSet(profiles, id, armor.category(), armor.baseAc()));
        return Map.copyOf(profiles);
    }

    private static void addSet(Map<String, Profile> profiles, String id, ArmorCategory category, int ac) {
        var profile = new Profile(id, category, ac);
        for (String slot : new String[] {"helmet", "chestplate", "leggings", "boots"})
            profiles.put(id + "_" + slot, profile);
    }

    private PlayerArmorRules() {}

    /** Returns -1 when the target is not a created player character and native AC must remain authoritative. */
    public static int armorClass(LivingEntity target, CharacterState character) {
        if (!(target instanceof Player player) || character == null || !character.created()) return -1;
        Profile set = wornSet(player);
        int dex = character.modifier(cc.sighs.dndturn.domain.character.Ability.DEX);
        boolean shield = player.getOffhandItem().is(Items.SHIELD) || player.getMainHandItem().is(Items.SHIELD);
        return set == null
            ? CharacterRules.armorClass(ArmorCategory.NONE, 0, dex, shield)
            : CharacterRules.armorClass(set.category(), set.baseAc(), dex, shield);
    }

    private static Profile wornSet(Player player) {
        Profile found = null;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) return null;
            Profile profile = profile(stack);
            if (profile == null) return null;
            if (found == null) found = profile;
            else if (!found.equals(profile)) return null;
        }
        return found;
    }

    private static Profile profile(ItemStack stack) {
        return PROFILES.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
