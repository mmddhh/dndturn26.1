package cc.sighs.dndturn.gametest;

import cc.sighs.dndturn.combat.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Regression evidence for rejected recovery candidates and normal START terminal receipts. */
public final class RepairGameTests {
    private RepairGameTests() {}

    public static void inventorySwap(GameTestHelper helper, ServerPlayer player) {
        var inventory = player.getInventory();
        var original = new java.util.ArrayList<ItemStack>();
        for (int i = 0; i < inventory.getContainerSize(); i++) original.add(inventory.getItem(i).copy());
        ItemStack carried = player.inventoryMenu.getCarried().copy();
        boolean creative = player.getAbilities().instabuild;
        var bounds = player.getBoundingBox().inflate(8);
        int drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds).size();
        try {
            helper.assertTrue(VanillaInputPolicy.mayOrganize(player), "fixture lacks inventory capability");
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            for (int source : new int[] {0, 40}) {
                for (boolean damaged : new boolean[] {false, true}) {
                    for (boolean infinite : new boolean[] {false, true}) {
                        player.getAbilities().instabuild = infinite;
                        fillInventory(player);
                        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
                        if (damaged) helmet.setDamageValue(7);
                        inventory.setItem(39, helmet.copy());
                        inventory.setItem(source, new ItemStack(Items.CARVED_PUMPKIN, 64));
                        swap(player, 5, source);
                        helper.assertTrue(ItemStack.matches(inventory.getItem(39), helmet)
                            && inventory.getItem(source).getCount() == 64,
                            "full inventory SWAP lost equipment or consumed source");
                        inventory.setItem(9, ItemStack.EMPTY);
                        swap(player, 5, source);
                        helper.assertTrue(inventory.getItem(39).is(Items.CARVED_PUMPKIN)
                            && inventory.getItem(39).getCount() == 1 && inventory.getItem(source).getCount() == 63
                            && ItemStack.matches(inventory.getItem(9), helmet),
                            "empty main slot did not receive displaced equipment");
                    }
                }
                player.getAbilities().instabuild = false;
                fillInventory(player);
                inventory.setItem(39, new ItemStack(Items.CARVED_PUMPKIN));
                inventory.setItem(source, new ItemStack(Items.CARVED_PUMPKIN, 64));
                swap(player, 5, source);
                helper.assertTrue(inventory.getItem(source).getCount() == 64
                    && inventory.getItem(39).is(Items.CARVED_PUMPKIN), "split source capacity was ignored");

                fillInventory(player);
                inventory.setItem(39, new ItemStack(Items.IRON_HELMET));
                inventory.setItem(source, new ItemStack(Items.DIAMOND_HELMET));
                swap(player, 5, source);
                helper.assertTrue(inventory.getItem(39).is(Items.DIAMOND_HELMET)
                    && inventory.getItem(source).is(Items.IRON_HELMET), "normal full-inventory swap was rejected");
            }
            // Existing matching offhand capacity counts, but its empty slot is not getFreeSlot.
            fillInventory(player);
            inventory.setItem(0, new ItemStack(Items.CARVED_PUMPKIN, 64));
            inventory.setItem(39, new ItemStack(Items.IRON_HELMET));
            inventory.setItem(40, ItemStack.EMPTY);
            swap(player, 5, 0);
            helper.assertTrue(inventory.getItem(39).is(Items.IRON_HELMET), "empty offhand was treated as insertion space");
            inventory.setItem(39, new ItemStack(Items.CARVED_PUMPKIN));
            inventory.setItem(0, new ItemStack(Items.STONE, 64));
            // Equip a stackable helmet source with different components to force merging into offhand.
            ItemStack named = new ItemStack(Items.CARVED_PUMPKIN, 64);
            named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("source"));
            inventory.setItem(0, named);
            inventory.setItem(40, new ItemStack(Items.CARVED_PUMPKIN, 63));
            swap(player, 5, 0);
            helper.assertTrue(inventory.getItem(0).getCount() == 63 && inventory.getItem(40).getCount() == 64,
                "matching offhand stack was not used for overflow");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bounds).size() == drops,
                "inventory organization spawned an ItemEntity");
        } finally {
            for (int i = 0; i < original.size(); i++) inventory.setItem(i, original.get(i));
            player.inventoryMenu.setCarried(carried);
            player.getAbilities().instabuild = creative;
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void fillInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            player.getInventory().setItem(i, ItemStack.EMPTY);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    }

    private static void swap(ServerPlayer player, int slot, int source) {
        player.connection.handleContainerClick(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(
            player.inventoryMenu.containerId, player.inventoryMenu.getStateId(), (short) slot, (byte) source,
            net.minecraft.world.inventory.ContainerInput.SWAP, it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(),
            net.minecraft.network.HashedStack.EMPTY));
    }

    public static void values(GameTestHelper helper) {
        UUID member = UUID.randomUUID(), encounter = UUID.randomUUID(), arrow = UUID.randomUUID();
        CombatEngine rules = new CombatEngine(new Random(1), 28, 20);
        rules.beginCandidate(encounter, EncounterRegion.generate("minecraft:overworld",
            new EncounterRegion.Discovery(0, 0, 0, 4, 4, 4),
            List.of(new EncounterRegion.Anchor(member, new EncounterRegion.Point(2, 2, 2))), 2, 1), Set.of(member));
        var snapshot = rules.exportSnapshot();
        var quarantine = new CombatPersistenceEnvelope.QuarantinedProjectile(UUID.randomUUID(), encounter, member,
            "restart cannot prove collision outcome");
        CombatSavedData saved = new CombatSavedData();
        saved.update(new CombatPersistenceEnvelope(CombatPersistenceEnvelope.CURRENT_SCHEMA, snapshot,
            20, 1, Map.of(encounter, 1L), Map.of(), Map.of(), Map.of(), Map.of(), List.of(),
            Map.of(), Map.of(), Map.of(), List.of(), Map.of(arrow, quarantine)));
        String original = saved.json();
        var server = helper.getLevel().getServer();
        var live = ServerCombatService.forServer(server);
        var rejected = ServerCombatService.restoreForGameTest(server, saved);
        helper.assertTrue(rejected.encounterOf(member) == null, "bad metadata installed a member");
        boolean refused = false;
        try { rejected.requestStart(helper.makeMockServerPlayerInLevel(), UUID.randomUUID()); }
        catch (IllegalStateException expected) { refused = true; }
        rejected.persistIfChanged();
        helper.assertTrue(refused && original.equals(saved.json()), "bad recovery allowed START or overwrote original JSON");
        helper.assertTrue(saved.envelope().quarantinedProjectiles().get(arrow).equals(quarantine),
            "quarantine evidence did not survive serialization");
        helper.assertTrue(ServerCombatService.existing(server) == live && live.encounterOf(member) == null,
            "isolated recovery replaced or changed live service");
        var settings = Map.of(encounter, new CombatPersistenceEnvelope.CapturedSettings(2, 16, 64, false));
        saved.update(new CombatPersistenceEnvelope(CombatPersistenceEnvelope.CURRENT_SCHEMA, snapshot,
            40, 1, Map.of(encounter, 1L), settings, Map.of(), Map.of(), Map.of(), List.of(),
            Map.of(), Map.of(), Map.of(), List.of(), Map.of(arrow, quarantine)));
        var restored = ServerCombatService.restoreForGameTest(server, saved);
        helper.assertTrue(encounter.equals(restored.encounterOf(member))
            && restored.state(encounter).members().containsKey(member), "valid recovery did not install member");
        helper.assertTrue(restored.isProjectileQuarantined(arrow), "production recovery lost quarantine evidence");
        helper.assertTrue(ServerCombatService.existing(server) == live, "valid recovery replaced live service");
        helper.assertTrue(saved.envelope().cumulativeServerTicks() == 40, "clock-only save retained an old clock");
        var tickRate = helper.getLevel().tickRateManager();
        boolean frozen = tickRate.isFrozen();
        int steps = tickRate.frozenTicksToRun();
        var beforeAudit = restored.state(encounter);
        try {
            tickRate.setFrozen(true);
            tickRate.setFrozenTicksToRun(0);
            tickRate.tick();
            restored.beforeLevelTick(helper.getLevel());
            helper.assertTrue(beforeAudit.equals(restored.state(encounter)),
                "frozen recovery settled or released an unaudited encounter");
            tickRate.setFrozen(false);
            tickRate.tick();
            // This member deliberately has no world entity. Exercise the production audit,
            // including its null-target UNKNOWN and subsequent release, not just decoding.
            restored.beforeLevelTick(helper.getLevel());
            helper.assertTrue(restored.encounterOf(member) == null,
                "missing restored member retained control");
            restored.persistIfChanged();
            var audited = saved.envelope();
            helper.assertTrue(audited.rules().encounters().isEmpty()
                && audited.rules().closed().size() == 1,
                "failed recovery was not archived");
            var results = audited.rules().closed().getFirst().results();
            helper.assertTrue(results.stream().anyMatch(result -> result.outcome() == OperationRecord.Outcome.UNKNOWN
                && result.snapshot().target() == null), "recovery lost its encounter-wide UNKNOWN evidence");
            helper.assertTrue(restored.isProjectileQuarantined(arrow), "release removed projectile quarantine");
            restored.beforeLevelTick(helper.getLevel());
            restored.persistIfChanged();
            helper.assertTrue(audited.equals(saved.envelope()), "repeated audit changed terminal evidence");
            var reloaded = ServerCombatService.restoreForGameTest(server, saved);
            reloaded.beforeLevelTick(helper.getLevel());
            helper.assertTrue(reloaded.encounterOf(member) == null && reloaded.isProjectileQuarantined(arrow),
                "reloading recovery results resurrected control or lost quarantine");
        } finally {
            tickRate.setFrozen(frozen);
            tickRate.setFrozenTicksToRun(steps);
            tickRate.tick();
        }
        var sync = CombatNetwork.CombatIntent.resultSync(UUID.randomUUID(), UUID.randomUUID(), encounter, 73, 19);
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            CombatNetwork.CombatIntent.STREAM_CODEC.encode(buffer, sync);
            var decoded = CombatNetwork.CombatIntent.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.equals(sync) && decoded.expectedVersion() == 73 && decoded.fromIndex() == 19
                && decoded.kind() == CombatNetwork.IntentKind.RESULT_SYNC && !buffer.isReadable(),
                "result sync cursor and rule version did not round trip independently");
        } finally { buffer.release(); }
        helper.succeed();
    }

    public static void playerOnlyStart(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos position = helper.absolutePos(new BlockPos(41001, 121, 1));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        player.setNoGravity(true);
        Set<Long> forcedChunks = new java.util.HashSet<>();
        for (int x = (position.getX() - 24) >> 4; x <= (position.getX() + 24) >> 4; x++)
            for (int z = (position.getZ() - 24) >> 4; z <= (position.getZ() + 24) >> 4; z++) {
                helper.getLevel().getChunk(x, z);
                if (helper.getLevel().setChunkForced(x, z, true))
                    forcedChunks.add(net.minecraft.world.level.ChunkPos.pack(x, z));
            }
        var service = ServerCombatService.forServer(helper.getLevel().getServer());
        Runnable cleanup = () -> {
            service.leave(player.getUUID());
            for (long packed : forcedChunks) {
                var chunk = net.minecraft.world.level.ChunkPos.unpack(packed);
                helper.getLevel().setChunkForced(chunk.x(), chunk.z(), false);
            }
            forcedChunks.clear();
        };
        helper.runAtTickTime(99, cleanup);
        UUID request = UUID.randomUUID();
        var intent = CombatNetwork.CombatIntent.start(request);
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            CombatNetwork.CombatIntent.STREAM_CODEC.encode(buffer, intent);
            var decoded = CombatNetwork.CombatIntent.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.equals(intent) && decoded.expectedVersion() == 0
                && decoded.encounterId() == null && decoded.generation() == null && decoded.fromIndex() == 0,
                "START factory did not produce a valid sessionless intent");
            CombatIntentHandler.handleIntent(player, decoded);
        } finally { buffer.release(); }
        UUID encounter = service.encounterOf(player.getUUID());
        helper.assertTrue(encounter != null, "START without enemies did not create an encounter");
        var first = service.state(encounter);
        helper.assertTrue(first.phase() == EncounterPhase.CANDIDATE
            && first.members().keySet().equals(Set.of(player.getUUID()))
            && player.getUUID().equals(first.current()), "player-only candidate has incorrect membership or turn");
        CombatIntentHandler.handleIntent(player, intent);
        helper.assertTrue(service.state(encounter).equals(first), "START retry changed the existing session");
        service.endCurrentTurn(encounter, UUID.randomUUID(), first.version());
        helper.assertTrue(service.state(encounter).phase() == EncounterPhase.ENVIRONMENT,
            "player-only end turn skipped the environment");
        helper.startSequence().thenWaitUntil(() -> {
            var next = service.state(encounter);
            helper.assertTrue(next.phase() == EncounterPhase.CANDIDATE && next.round() == first.round() + 1
                && player.getUUID().equals(next.current()), "environment did not return control to the player");
        }).thenExecute(() -> {
            var next = service.state(encounter);
            helper.assertTrue(next.members().get(player.getUUID()).action()
                && next.members().get(player.getUUID()).movementTicks() == first.members().get(player.getUUID()).movementTicks(),
                "player-only next turn did not refresh resources");
            service.endCurrentTurn(encounter, UUID.randomUUID(), next.version());
        }).thenWaitUntil(() -> {
            var next = service.state(encounter);
            helper.assertTrue(next.phase() == EncounterPhase.CANDIDATE && next.round() == first.round() + 2,
                "player/environment loop stopped on the second cycle");
        }).thenExecute(cleanup).thenSucceed();
    }
}
