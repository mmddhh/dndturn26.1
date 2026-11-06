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
    private record Profile(ArmorCategory category, int baseAc) {}
    private static final Map<String, Profile> PROFILES = Map.ofEntries(
        Map.entry("leather", new Profile(ArmorCategory.LIGHT, 11)),
        Map.entry("chainmail", new Profile(ArmorCategory.HEAVY, 16)),
        Map.entry("iron", new Profile(ArmorCategory.HEAVY, 16)),
        Map.entry("golden", new Profile(ArmorCategory.MEDIUM, 14)),
        Map.entry("diamond", new Profile(ArmorCategory.HEAVY, 18)),
        Map.entry("netherite", new Profile(ArmorCategory.HEAVY, 18)),
        Map.entry("iron_leather", new Profile(ArmorCategory.LIGHT, 11)),
        Map.entry("iron_padded", new Profile(ArmorCategory.LIGHT, 11)),
        Map.entry("iron_studded_leather", new Profile(ArmorCategory.LIGHT, 12)),
        Map.entry("iron_hide", new Profile(ArmorCategory.MEDIUM, 12)),
        Map.entry("iron_chain_shirt", new Profile(ArmorCategory.MEDIUM, 13)),
        Map.entry("iron_scale_mail", new Profile(ArmorCategory.MEDIUM, 14)),
        Map.entry("iron_breastplate", new Profile(ArmorCategory.MEDIUM, 14)),
        Map.entry("iron_half_plate", new Profile(ArmorCategory.MEDIUM, 15)),
        Map.entry("iron_ring_mail", new Profile(ArmorCategory.HEAVY, 14)),
        Map.entry("iron_chain_mail", new Profile(ArmorCategory.HEAVY, 16)),
        Map.entry("iron_splint", new Profile(ArmorCategory.HEAVY, 17)),
        Map.entry("iron_plate", new Profile(ArmorCategory.HEAVY, 18))
    );

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
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (String suffix : new String[] {"_helmet", "_chestplate", "_leggings", "_boots"})
            if (path.endsWith(suffix)) return PROFILES.get(path.substring(0, path.length() - suffix.length()));
        return null;
    }
}
