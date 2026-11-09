package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Platform seam: creator content, actor persistence and native max-health binding agree. */
public final class PlayerCharacterChecks {
    private PlayerCharacterChecks() {}

    public static void run(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var content = CharacterContentService.current();
        helper.assertTrue(content.classes().containsKey("dndturn:fighter")
            && content.races().containsKey("dndturn:human"), "default character content missing");
        var state = PlayerCharacterService.create(player, "fighter", "human",
            new AbilityScores(15, 15, 14, 10, 8, 8));
        helper.assertTrue(state.created() && state.progression().level() == 1, "level-1 character not created");
        helper.assertTrue(state.abilities().strength() == 16 && state.abilities().intelligence() == 11,
            "racial ability boosts were not applied");
        helper.assertTrue(state.abilities().constitution() == 15, "human constitution must be 14 + 1");
        helper.assertTrue(Math.abs(player.getMaxHealth() - 12.0F) < 0.001F, "class hit-die max health not applied");
        var persisted = ServerRuntime.encounters(player.level().getServer()).actorStates()
            .state(player.getUUID()).persistent().character();
        helper.assertTrue(persisted.equals(state), "character state did not reach the actor owner");
        helper.assertTrue(PlayerWeaponRules.profileFor(state, "dndturn:iron_longsword") != null,
            "registered iron weapon lost its tactical profile");
        helper.assertTrue(PlayerWeaponRules.profileFor(state, "other:iron_longsword") == null
            && PlayerWeaponRules.profileFor(state, "minecraft:iron_longsword") == null
            && PlayerWeaponRules.profileFor(state, "iron_longsword") == null,
            "weapon binding must preserve the full registry identity");
        var armorSlots = new net.minecraft.world.entity.EquipmentSlot[] {
            net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        var armorNames = new String[] {"helmet", "chestplate", "leggings", "boots"};
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
        for (int i = 0; i < armorSlots.length; i++) player.setItemSlot(armorSlots[i],
            new net.minecraft.world.item.ItemStack(DndEquipmentItems.get("dndturn:iron_plate_" + armorNames[i])));
        helper.assertTrue(PlayerArmorRules.armorClass(player, state) == 18, "full iron plate AC changed");
        player.setItemSlot(armorSlots[0], new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
        helper.assertTrue(PlayerArmorRules.armorClass(player, state) == 13,
            "equal-valued armor from a different set must not complete the set");
        for (var slot : armorSlots) player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);

        var participant = new ParticipantContent(player.getUUID(), "dndturn:player", 1, state,
            new cc.sighs.dndturn.domain.character.CharacterClass(state.progression().classId(), 1,
                "Pinned fighter", 10, java.util.Set.of()), content.requireRace(state.progression().raceId()), null);
        var pinned = new EncounterContent(java.util.UUID.randomUUID().toString(), content,
            java.util.Map.of(player.getUUID(), participant));
        int beforeReload = SaveService.bonus(player, cc.sighs.dndturn.domain.character.Ability.CON, pinned);
        helper.assertTrue(beforeReload == state.modifier(cc.sighs.dndturn.domain.character.Ability.CON),
            "saving throw ignored pinned class and used the current proficient class");
        CharacterContentService.reload();
        helper.assertTrue(SaveService.bonus(player, cc.sighs.dndturn.domain.character.Ability.CON, pinned) == beforeReload,
            "saving throw changed after content reload");
        var levelled = PlayerCharacterService.addExperience(player, 300);
        helper.assertTrue(levelled.progression().level() == 2, "XP threshold did not raise the level");
        helper.assertTrue(Math.abs(player.getMaxHealth() - 20.0F) < 0.001F, "level-up max health expectation mismatch");
        helper.succeed();
    }
}
