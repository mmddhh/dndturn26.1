package cc.sighs.dndturn.platform.server.character;

import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.List;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Platform seam: creator content, actor persistence and native max-health binding agree. */
public final class PlayerCharacterChecks {
    private PlayerCharacterChecks() {}

    public static void run(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.creative(helper);
        var content = ServerRuntime.content(player.level().getServer()).current();
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
        helper.assertTrue(Math.abs(player.getMaxHealth() - 12.0F) < 0.001F, "class hit-die max health not applied");
        var persisted = ServerRuntime.encounters(player.level().getServer()).actorStates()
            .state(player.getUUID()).persistent().character();
        helper.assertTrue(persisted.equals(state), "character state did not reach the actor owner");
        helper.assertTrue(PlayerWeaponRules.profileFor(state, "dndturn:iron_longsword", true, content) != null,
            "registered iron weapon lost its tactical profile");
        helper.assertTrue(PlayerWeaponRules.profileFor(state, "other:iron_longsword", true, content) == null
            && PlayerWeaponRules.profileFor(state, "minecraft:iron_longsword", true, content) == null
            && PlayerWeaponRules.profileFor(state, "iron_longsword", true, content) == null,
            "weapon binding must preserve the full registry identity");
        var armorSlots = new net.minecraft.world.entity.EquipmentSlot[] {
            net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        var armorNames = new String[] {"helmet", "chestplate", "leggings", "boots"};
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
        for (int i = 0; i < armorSlots.length; i++) player.setItemSlot(armorSlots[i],
            new net.minecraft.world.item.ItemStack(CharacterEquipment.resolve("dndturn:iron_plate_" + armorNames[i])));
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
        helper.assertTrue(PlayerArmorRules.armorClass(player, state, content) == 13,
            "equal-valued armor from a different set must not complete the set");
        for (var slot : armorSlots) player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);

        var pinnedClass = new cc.sighs.dndturn.domain.character.CharacterClass(state.progression().classId(), 1,
            "Pinned fighter", 10, Set.of(), Set.of(), 3, List.of(), Map.of());
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
        ServerRuntime.content(player.level().getServer()).reload();
        helper.assertTrue(SaveService.bonus(player, cc.sighs.dndturn.domain.character.Ability.CON, pinned) == beforeReload,
            "saving throw changed after content reload");
        var levelled = PlayerCharacterService.addExperience(player, 300);
        helper.assertTrue(levelled.progression().level() == 2, "XP threshold did not raise the level");
        helper.assertTrue(Math.abs(player.getMaxHealth() - 20.0F) < 0.001F, "level-up max health expectation mismatch");
        checkCreationDelivery(helper);
        interruptedKit(helper);
        worldClassGrowth(helper);
        helper.succeed();
    }
    private static void worldClassGrowth(GameTestHelper helper) {
        var player = TestPlayers.creative(helper);
        var server = player.level().getServer();
        var catalogs = ServerRuntime.content(server);
        var directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("dndturn-content/classes");
        String definition = """
            {"id":"test:fighter","version":1,"name":"Fixture","hit_die":8,"saves":["con"],
             "asi_levels":[2],"subclass_level":1,"subclasses":["test:sample"],"equipment":{"base":[]}}
            """;
        try {
            java.nio.file.Files.createDirectories(directory);
            var file = java.nio.file.Files.createTempFile(directory, "dndturn-fixture-", ".json");
            try {
                java.nio.file.Files.writeString(file, definition);
                var accepted = catalogs.reload();
                PlayerCharacterService.create(player, "test:fighter", "human", new AbilityScores(15,15,14,10,8,8));
                var levelled = PlayerCharacterService.addExperience(player, 300);
                helper.assertTrue(levelled.progression().pendingAsi() == 1
                    && levelled.progression().classId().equals("test:fighter"), "world class lost its own ASI schedule");
                java.nio.file.Files.writeString(file, definition.replace("\"base\":[]",
                    "\"base\":[{\"item\":\"test:missing\",\"count\":1}]"));
                boolean rejected = false;
                try { catalogs.reload(); } catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected && catalogs.current() == accepted, "invalid kit replaced the live catalog");
            } finally {
                java.nio.file.Files.deleteIfExists(file);
                catalogs.reload();
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("world class fixture", error);
        }
    }

    private static void checkCreationDelivery(GameTestHelper helper) {
        var player = TestPlayers.survival(helper);
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.teleportTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        var server = player.level().getServer();
        var arrows = CharacterEquipment.resolve("dndturn:iron_arrow");
        var selection = new ActorStates.Creation(
            ServerRuntime.content(server).current().revision(), "dndturn:ranger", "dndturn:elf",
            "dndturn:high_elf", null, new AbilityScores(15,15,14,10,8,8),
            List.of(Ability.CON, Ability.INT), "0");
        var inventory = player.getInventory();
        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.STONE, 64));
        inventory.setItem(0, new ItemStack(arrows, 61));
        var box = player.getBoundingBox().inflate(3);
        var existingDrops = Set.copyOf(player.level().getEntitiesOfClass(ItemEntity.class, box));
        var operation = UUID.randomUUID();
        try {
            var invalid = new ActorStates.Creation(selection.revision(),
                selection.characterClass(), selection.race(), selection.subrace(), null, selection.bought(), selection.targets(), "missing");
            try {
                PlayerCharacterService.submit(player, operation, invalid);
                helper.fail("invalid kit created character");
            } catch (IllegalArgumentException expected) {
                helper.assertTrue(!ServerRuntime.encounters(server).actorStates().state(player.getUUID()).persistent().character().created()
                    && inventory.getItem(0).getCount() == 61, "rejected kit changed player");
            }
            var result = PlayerCharacterService.submit(player, operation, selection);
            var drops = player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(3), e -> e.getItem().is(arrows));
            helper.assertTrue(!result.unknown() && result.grants().stream().anyMatch(g -> g.inventory() == 3 && g.dropped() == 17
                && drops.stream().anyMatch(e -> e.getUUID().equals(g.dropEntity()))) && inventory.getItem(0).getCount() == 64
                && drops.size() == 1 && drops.getFirst().getItem().getCount() == 17, "partial inventory grant: " + result + " inventory=" + inventory.getItem(0) + " drops=" + drops.size());
            ServerRuntime.content(server).reload();
            helper.assertTrue(PlayerCharacterService.submit(player, operation, selection).equals(result)
                && drops.getFirst().getItem().getCount() == 17, "retry lost original delivery");
            try {
                PlayerCharacterService.submit(player, UUID.randomUUID(), selection);
                helper.fail("expired catalog was accepted");
            } catch (IllegalStateException expected) { helper.assertTrue(expected.getMessage().contains("catalog changed"), "wrong stale reason"); }
            var changed = new ActorStates.Creation(selection.revision(),
                selection.characterClass(), selection.race(), selection.subrace(), null, selection.bought(), selection.targets(), "1");
            try {
                PlayerCharacterService.submit(player, operation, changed);
                helper.fail("same operation accepted another kit");
            } catch (IllegalStateException expected) { helper.assertTrue(expected.getMessage().contains("payload mismatch"), "wrong retry refusal"); }
        } finally {
            player.level().getEntitiesOfClass(ItemEntity.class,
                box, e -> !existingDrops.contains(e)).forEach(Entity::discard);
            server.getPlayerList().remove(player);
        }
        var reconnected = TestPlayers.survival(helper, player.getUUID());
        try {
            var result = PlayerCharacterService.submit(reconnected, operation, selection);
            helper.assertTrue(!result.unknown() && result.grants().stream().anyMatch(g -> g.dropped() == 17)
                && ServerRuntime.encounters(server).actorStates().state(reconnected.getUUID()).persistent().character().created(),
                "reconnected creator lost the authoritative receipt");
        } finally { server.getPlayerList().remove(reconnected); }
    }
    private static void interruptedKit(GameTestHelper helper) {
        var player = TestPlayers.survival(helper);
        var level = player.level();
        var pos = helper.absolutePos(new BlockPos(2, 3, 2));
        player.teleportTo(pos.getX(), pos.getY(), pos.getZ());
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i,
            new ItemStack(Items.STONE, 64));
        var selection = new ActorStates.Creation(ServerRuntime.content(level.getServer()).current().revision(),
            "dndturn:barbarian", "dndturn:dwarf", "dndturn:hill_dwarf", null, new AbilityScores(15,15,14,10,8,8),
            List.of(Ability.DEX, Ability.INT), "0");
        var operation = UUID.randomUUID();
        int[] attempts = {0};
        Consumer<ItemTossEvent> cancel = event -> {
            if (event.getPlayer() == player && ++attempts[0] == 2) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            var receipt = PlayerCharacterService.submit(player, operation, selection);
            helper.assertTrue(receipt.unknown() && receipt.grants().size() == 2
                && receipt.grants().getFirst().dropped() == 1 && level.getEntity(receipt.grants().getFirst().dropEntity()) != null
                && receipt.grants().getLast().dropped() == 0 && receipt.maxHealth() == player.getMaxHealth(),
                "post-commit cancellation lost health, confirmed drop or unknown result");
            helper.assertTrue(PlayerCharacterService.submit(player, operation, selection).equals(receipt) && attempts[0] == 2,
                "interrupted kit replayed on retry");
            level.getEntity(receipt.grants().getFirst().dropEntity()).discard();
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
            level.getServer().getPlayerList().remove(player);
        }
    }

}
