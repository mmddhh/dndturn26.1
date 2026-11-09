package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.CharacterState;
import cc.sighs.dndturn.domain.resolution.AttackProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Turn-based d20 weapon profile for player attacks; class kits only, no real-time combat hooks. */
public final class PlayerWeaponRules {
    private PlayerWeaponRules() {}

    public static AttackProfile profile(ServerPlayer player, CharacterState character) {
        if (character == null || !character.created()) return null;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return null;
        return profileFor(character, BuiltInRegistries.ITEM.getKey(held.getItem()).toString(),
            player.getOffhandItem().isEmpty());
    }

    public static AttackProfile profileFor(CharacterState character, String itemId) {
        return profileFor(character, itemId, true);
    }

    public static AttackProfile profileFor(CharacterState character, String itemId, boolean offhandEmpty) {
        if (character == null || !character.created() || itemId == null || itemId.isBlank()) return null;
        var spec = cc.sighs.dndturn.domain.character.EquipmentRules.WEAPONS.get(itemId);
        if (spec == null) return null;
        Ability ability = spec.finesse()
            ? (character.modifier(Ability.DEX) > character.modifier(Ability.STR) ? Ability.DEX : Ability.STR)
            : spec.ability();
        int sides = spec.versatileSides() > 0 && offhandEmpty ? spec.versatileSides() : spec.sides();
        int attackBonus = character.progression().proficiencyBonus() + character.modifier(ability);
        int damageBonus = Math.max(0, character.modifier(ability));
        return new AttackProfile(attackBonus, spec.dice(), sides, damageBonus, spec.damageType());
    }
}
