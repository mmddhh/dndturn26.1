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
        helper.assertTrue(CharacterContentService.lastFailure().isEmpty() && content.mobs().containsKey("minecraft:zombie")
            && content.mobs().containsKey("dndturn:ghast"), "default monster catalog was rejected: " + CharacterContentService.lastFailure());
        helper.assertTrue(content.classes().containsKey("dndturn:fighter")
            && content.races().containsKey("dndturn:human"), "default character content missing");
        var resolution = new cc.sighs.dndturn.platform.server.damage.DamageResolution();
        var encounter = java.util.UUID.randomUUID();
        int[] samples = {0};
        resolution.seed(encounter, new java.util.Random() {
            @Override public int nextInt(int bound) { samples[0]++; return bound - 1; }
        });
        var profile = new cc.sighs.dndturn.domain.resolution.AttackProfile(3,2,6,1,cc.sighs.dndturn.domain.mob.DamageType.FIRE);
        var mode = cc.sighs.dndturn.domain.resolution.CombatRules.RollMode.NORMAL;
        var launched = resolution.resolveAttack(encounter, 10, 0, 0, mode, null, 3, profile);
        var trace = cc.sighs.dndturn.platform.server.damage.DamageResolution.attackTrace(java.util.UUID.randomUUID(),player.getUUID(),
            mode,launched.roll(),10,launched.input().baseDamage(),0,launched.damage(),false,0,0,null);
        int capturedSamples = samples[0];
        var impact = resolution.resolveAttack(encounter,10,0,999,mode,trace,3,profile);
        helper.assertTrue(impact.equals(launched) && samples[0] == capturedSamples,
            "captured ranged damage or attack dice were rolled again at impact");
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
        var changedArmor = new java.util.HashMap<>(content.armor());
        changedArmor.put("dndturn:iron_plate",new cc.sighs.dndturn.domain.character.EquipmentRules.Armor(
            cc.sighs.dndturn.domain.character.ArmorCategory.HEAVY,20,15,true));
        var changedWeapons = new java.util.HashMap<>(content.weapons());
        changedWeapons.remove("dndturn:iron_longsword");
        var different = new cc.sighs.dndturn.domain.character.RulesCatalog("different-equipment",content.classes(),content.races(),content.mobs(),changedWeapons,changedArmor);
        helper.assertTrue(PlayerArmorRules.armorClass(player,state,content)==18 && PlayerArmorRules.armorClass(player,state,different)==20,
            "armor execution ignored the supplied immutable catalog");
        helper.assertTrue(PlayerWeaponRules.profileFor(state,"dndturn:iron_longsword",true,content)!=null
            && PlayerWeaponRules.profileFor(state,"dndturn:iron_longsword",true,different)==null,
            "weapon execution read a static/latest definition outside its catalog");
        player.setItemSlot(armorSlots[0], new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
        helper.assertTrue(PlayerArmorRules.armorClass(player, state) == 13,
            "equal-valued armor from a different set must not complete the set");
        for (var slot : armorSlots) player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);

        var pinnedClass = new cc.sighs.dndturn.domain.character.CharacterClass(state.progression().classId(), 1,
            "Pinned fighter", 10, java.util.Set.of());
        var pinnedClasses = new java.util.HashMap<>(content.classes());
        pinnedClasses.put(pinnedClass.id(), pinnedClass);
        var pinnedCatalog = new cc.sighs.dndturn.domain.character.RulesCatalog("pinned-saves", pinnedClasses,
            content.races(), content.mobs(), content.weapons(), content.armor());
        var participant = new ParticipantContent(player.getUUID(), "dndturn:player", 1, state,
            pinnedClass, content.requireRace(state.progression().raceId()), null, pinnedCatalog.revision());
        var pinned = new EncounterContent(java.util.UUID.randomUUID().toString(), pinnedCatalog,
            java.util.Map.of(player.getUUID(), participant));
        boolean rejectedBinding = false;
        try {
            var mismatched = new ParticipantContent(player.getUUID(), "dndturn:player", 1, state,
                content.requireClass(state.progression().classId()), participant.characterRace(), null, pinnedCatalog.revision());
            new EncounterContent(pinned.encounterId(), pinnedCatalog, java.util.Map.of(player.getUUID(), mismatched));
        } catch (IllegalArgumentException expected) { rejectedBinding = true; }
        helper.assertTrue(rejectedBinding, "restored participant accepted class semantics from another catalog");
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
