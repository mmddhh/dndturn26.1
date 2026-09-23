package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/** Actual ticker ordering and transfer effects, without manually ticking a body or block entity. */
public final class EnvironmentBoundaryTimeChecks {
    public static void run(GameTestHelper h) {
        var level = h.getLevel();
        var base = h.absolutePos(new BlockPos(97001, 210, 1));
        var forced = new HashSet<Long>();
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++)
            for (int z = (base.getZ()-24)>>4; z <= (base.getZ()+24)>>4; z++) {
                level.getChunk(x,z); if (level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
            }
        // The accelerated GameTest clock is not an I/O completion budget. Vanilla's
        // managed wait drains entity-load completions for the already requested core
        // chunks; it does not tick entities or simulate the encounter ahead of time.
        level.waitForEntities(ChunkPos.containing(base), 1);
        var player = TestPlayers.survival(h);
        player.teleportTo(base.getX()+.5, base.getY(), base.getZ()+.5);
        level.setBlockAndUpdate(base.below(), Blocks.OBSIDIAN.defaultBlockState());
        var service = ServerRuntime.encounters(level.getServer());
        Runnable cleanup = () -> {
            var id = service.encounterOf(player.getUUID()); if (id != null) service.stop(id);
            for (var key : forced) { var c = ChunkPos.unpack(key); level.setChunkForced(c.x(), c.z(), false); }
            forced.clear();
        };
        h.runAtTickTime(399, () -> {
            System.out.println("DNDTURN_ENVIRONMENT_TIMEOUT time=" + service.presentationFacts(player).environmentTime()
                + " chunks=" + forced.stream().map(key -> {
                    var cp = ChunkPos.unpack(key); var chunk = level.getChunkSource().getChunkNow(cp.x(), cp.z());
                    return cp + ":" + (chunk == null ? "missing" : chunk.getFullStatus())
                        + ":entities=" + level.areEntitiesLoaded(key) + ":range=" + level.shouldTickBlocksAt(key);
                }).toList());
            try { h.assertTrue(false, "environment fixture timed out; native readiness recorded before cleanup"); }
            finally { cleanup.run(); }
        });
        h.startSequence().thenWaitUntil(() -> {
            // Forced loading includes an outer maintenance margin, not just the encounter.
            // Wait for the actor here; the production scheduler independently checks every
            // actual region chunk before spending any environment step.
            h.assertTrue(level.isPositionEntityTicking(base) && level.areEntitiesLoaded(ChunkPos.pack(base)),
                "environment fixture actor chunk not natively ready");
        }).thenExecute(() -> {
            service.requestStart(player, UUID.randomUUID());
            var id = service.encounterOf(player.getUUID());
            h.assertTrue(service.state(id).members().size() == 1 && player.getUUID().equals(service.state(id).current()),
                "isolated boundary fixture has unexpected members/current: " + service.state(id));
            BlockPos inside = null, outside = null;
            for (int offset = 1; offset <= 16; offset++) {
                var candidate = base.east(offset);
                if (service.isBlockSimulationPaused(level, candidate.west()) && !service.isBlockSimulationPaused(level, candidate)) {
                    inside = candidate.west(); outside = candidate; break;
                }
            }
            h.assertTrue(inside != null, "fixture did not straddle exact domain");
            final var chestPos = inside; final var hopperPos = outside;
            level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
            var hopper = (HopperBlockEntity)level.getBlockEntity(hopperPos);
            var chest = (ChestBlockEntity)level.getBlockEntity(chestPos);
            hopper.clearContent(); chest.clearContent();
            hopper.setItem(0, new ItemStack(Items.DIAMOND));
            var detector = base.north(4);
            int wrongPower = (expectedPower(level, detector) + 7) % 16;
            level.setBlockAndUpdate(detector, Blocks.DAYLIGHT_DETECTOR.defaultBlockState().setValue(DaylightDetectorBlock.POWER, wrongPower));
            h.runAfterDelay(23, () -> {
                h.assertTrue(chest.isEmpty() && hopper.getItem(0).getCount() == 1,
                    "outside hopper mutated paused destination: chest=" + chest.getItem(0) + " hopper=" + hopper.getItem(0)
                        + " phase=" + service.state(id).phase() + " player=" + player.position()
                        + " chestPaused=" + service.isBlockSimulationPaused(level,chestPos)
                        + " source=" + hopperPos + " destination=" + chestPos
                        + " time=" + service.environmentTimeAt(level,chestPos));
                h.assertTrue(level.getBlockState(detector).getValue(DaylightDetectorBlock.POWER) == wrongPower, "thinking advanced daylight sampling");
                service.endCurrentTurn(id, UUID.randomUUID(), service.state(id).version());
                int[] sampledPower = {-1};
                // GameTestInfo iterates a FastUtil map while running these callbacks. Adding 31
                // delayed callbacks here rehashes that live iterator and may rerun this end-turn.
                // Sequences have their own copy-on-write lifecycle and do not mutate that map.
                h.startSequence().thenWaitUntil(() -> h.assertTrue(service.presentationFacts(player).environmentTime() == 20,
                    "waiting for committed environment step 20: actual=" + service.presentationFacts(player).environmentTime()
                        + " forced=" + forced.stream().map(key -> {
                            var chunkPos = ChunkPos.unpack(key);
                            var chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
                            return chunkPos + ":" + (chunk == null ? "missing" : chunk.getFullStatus())
                                + ":entities=" + level.areEntitiesLoaded(key) + ":range=" + level.shouldTickBlocksAt(key);
                        }).toList())).thenExecute(() -> {
                        sampledPower[0] = expectedPower(level, detector);
                        h.assertTrue(level.getBlockState(detector).getValue(DaylightDetectorBlock.POWER) == sampledPower[0],
                            "daylight at effective step 20: actual=" + level.getBlockState(detector)
                                + " expected=" + sampledPower[0] + " initial=" + wrongPower + " world=" + level.getGameTime()
                                + " local=" + service.environmentTimeAt(level, detector)
                                + " ticking=" + level.shouldTickBlocksAt(detector)
                                + " entitiesLoaded=" + level.areEntitiesLoaded(ChunkPos.pack(detector))
                                + " fullStatus=" + level.getChunkAt(detector).getFullStatus()
                                + " paused=" + service.isBlockSimulationPaused(level, detector)
                                + " blockEntity=" + level.getBlockEntity(detector)
                                + " phase=" + service.state(id).phase());
                }).thenIdle(12).thenExecute(() -> {
                    h.assertTrue(hopper.isEmpty() && chest.getItem(0).is(Items.DIAMOND),
                        "authorized destination never accepted native hopper transfer: hopper=" + hopper.getItem(0)
                            + " chest=" + chest.getItem(0) + " block=" + level.getBlockState(hopperPos)
                            + " sourceTicking=" + level.shouldTickBlocksAt(hopperPos)
                            + " currentHopper=" + (level.getBlockEntity(hopperPos)==hopper)
                            + " currentChest=" + (level.getBlockEntity(chestPos)==chest)
                            + " chestAbove=" + level.getBlockState(chestPos.above()));
                    h.assertTrue(sampledPower[0] >= 0 && level.getBlockState(detector).getValue(DaylightDetectorBlock.POWER) == sampledPower[0],
                        "daylight changed between its effective sample boundaries");
                    var facts = service.presentationFacts(player);
                    h.assertTrue(facts.environmentTime() == 30 && facts.phase().equals("CANDIDATE"), "confirmed environment time lost after permission window closed");
                    cleanup.run();
                }).thenSucceed();
            });
        });
    }
    private static int expectedPower(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        int brightness = level.getEffectiveSkyBrightness(pos);
        float angle = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE, pos) * (float)(Math.PI / 180.0);
        angle += ((angle < Math.PI ? 0 : (float)(Math.PI * 2)) - angle) * .2F;
        return net.minecraft.util.Mth.clamp(Math.round(brightness * net.minecraft.util.Mth.cos(angle)), 0, 15);
    }
}
