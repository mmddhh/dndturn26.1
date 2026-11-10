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

/** Native equipment observations resolved against the encounter's immutable rules catalog. */
public final class PlayerArmorRules {
    private PlayerArmorRules() {}

    /** Returns -1 when the target is not a created player character and native AC must remain authoritative. */
    public static int armorClass(LivingEntity target, CharacterState character) {
        return armorClass(target,character,cc.sighs.dndturn.domain.character.RulesCatalog.empty());
    }
    public static int armorClass(LivingEntity target, CharacterState character, cc.sighs.dndturn.domain.character.RulesCatalog catalog) {
        if (!(target instanceof Player player) || character == null || !character.created()) return -1;
        var set = wornSet(player,catalog.armorBindings());
        int dex = character.modifier(cc.sighs.dndturn.domain.character.Ability.DEX);
        boolean shield = player.getOffhandItem().is(Items.SHIELD) || player.getMainHandItem().is(Items.SHIELD);
        return set == null
            ? CharacterRules.armorClass(ArmorCategory.NONE, 0, dex, shield)
            : CharacterRules.armorClass(set.rule().category(), set.rule().baseAc(), dex, shield);
    }

    private static cc.sighs.dndturn.domain.character.EquipmentRules.ArmorBinding wornSet(Player player, Map<String, cc.sighs.dndturn.domain.character.EquipmentRules.ArmorBinding> bindings) {
        cc.sighs.dndturn.domain.character.EquipmentRules.ArmorBinding found = null;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) return null;
            var profile = bindings.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (profile == null || !profile.slot().name().equals(slot.name())) return null;
            if (found == null) found = profile;
            else if (!found.setId().equals(profile.setId()) || !found.rule().equals(profile.rule())) return null;
        }
        return found;
    }

}
