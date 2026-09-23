package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;

/** Exercises actual ticker, event queue and live boat contact, including no historical replay. */
public final class EnvironmentClassificationChecks {
    public static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        var base = new BlockPos(79001, 151, 1);
        Set<Long> forced = new HashSet<>();
        List<Entity> spawned = new ArrayList<>();
        var service = ServerRuntime.encounters(level.getServer());
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++)
            for (int z = (base.getZ()-24)>>4; z <= (base.getZ()+24)>>4; z++) {
                level.getChunk(x,z);
                if (level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
            }
        level.waitForEntities(net.minecraft.world.level.ChunkPos.containing(base), 1);
        for (var pos : BlockPos.betweenClosed(base.offset(-7,-1,-7),base.offset(7,3,7)))
            level.setBlockAndUpdate(pos, pos.getY() < base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        Runnable cleanup = () -> {
            UUID encounter = service.encounterOf(player.getUUID());
            if (encounter != null) service.stop(encounter);
            spawned.forEach(Entity::discard);
            for (long key : forced) { var chunk = ChunkPos.unpack(key); level.setChunkForced(chunk.x(),chunk.z(),false); }
            forced.clear();
        };
        helper.runAtTickTime(30, () -> {
            service.requestStart(player, UUID.randomUUID());
            UUID encounter = service.encounterOf(player.getUUID());
            helper.assertTrue(encounter != null, "classification START failed");
            var signPos = base.north(3);
            level.setBlockAndUpdate(signPos, Blocks.OAK_SIGN.defaultBlockState());
            var sign = (SignBlockEntity) level.getBlockEntity(signPos);
            sign.setAllowedPlayerEditor(UUID.randomUUID());
            var potPos = base.south(3);
            level.setBlockAndUpdate(potPos, Blocks.DECORATED_POT.defaultBlockState());
            var pot = (DecoratedPotBlockEntity) level.getBlockEntity(potPos);
            level.blockEvent(potPos, Blocks.DECORATED_POT, 1, 0);
            var present = base.west(4);
            var departed = base.east(4);
            for (var pos : List.of(present, departed)) {
                level.setBlockAndUpdate(pos.below(), Blocks.WATER.defaultBlockState());
                level.setBlockAndUpdate(pos, Blocks.LILY_PAD.defaultBlockState());
                var boat = EntityType.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
                helper.assertTrue(boat != null, "boat creation failed");
                boat.setPos(pos.getX()+.5,pos.getY()+.01,pos.getZ()+.5);
                boat.setNoGravity(true);
                level.addFreshEntity(boat);
                spawned.add(boat);
            }
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(sign.getPlayerWhoMayEdit() == null, "sign editor maintenance froze with production tickers");
                helper.assertTrue(pot.lastWobbleStyle == DecoratedPotBlockEntity.WobbleStyle.POSITIVE,
                    "exact pot presentation event was held");
                helper.assertTrue(level.getBlockState(present).is(Blocks.LILY_PAD)
                    && level.getBlockState(departed).is(Blocks.LILY_PAD), "boat destroyed lily pad during thinking");
                spawned.get(1).discard();
                service.endCurrentTurn(encounter, UUID.randomUUID(), service.state(encounter).version());
                helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.getBlockState(present).isAir(),
                        "current boat contact did not destroy lily pad in environment"))
                    .thenExecute(() -> helper.assertTrue(level.getBlockState(departed).is(Blocks.LILY_PAD),
                        "departed boat contact was replayed"))
                    .thenExecute(cleanup).thenSucceed();
            });
        });
        helper.runAtTickTime(99, cleanup);
    }
}
