package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;

/** A real periodic BE uses domain time even when world gameTime has a different phase. */
public final class EnvironmentClockChecks {
    public static void run(GameTestHelper helper) {
        var level=helper.getLevel();var base=new BlockPos(85001,151,1);Set<Long> forced=new HashSet<>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++)
            for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++){
                level.getChunk(x,z);if(level.setChunkForced(x,z,true))forced.add(ChunkPos.pack(x,z));
            }
        level.waitForEntities(net.minecraft.world.level.ChunkPos.containing(base), 1);
        var player=helper.makeMockServerPlayerInLevel();player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        level.setBlockAndUpdate(base.below(),Blocks.OBSIDIAN.defaultBlockState());
        var service=ServerRuntime.encounters(level.getServer());
        Runnable cleanup=()->{
            UUID id=service.encounterOf(player.getUUID());if(id!=null)service.stop(id);
            for(long key:forced){var chunk=ChunkPos.unpack(key);level.setChunkForced(chunk.x(),chunk.z(),false);}forced.clear();
        };
        helper.runAtTickTime(30,()->{
            service.requestStart(player,UUID.randomUUID());UUID id=service.encounterOf(player.getUUID());
            var pos=base.east(4).above(2);
            for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++)
                level.setBlockAndUpdate(pos.offset(x,y,z),Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))==2
                    ?Blocks.PRISMARINE.defaultBlockState():Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(pos,Blocks.CONDUIT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true));
            var conduit=(ConduitBlockEntity)level.getBlockEntity(pos);
            helper.runAfterDelay(13,()->{
                helper.assertTrue(!conduit.isActive() && service.environmentTimeAt(level,pos)==0,"waiting consumed periodic phase");
                service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
                helper.runAfterDelay(32,()->{
                    helper.assertTrue(!conduit.isActive() && service.environmentTimeAt(level,pos)==30,"conduit fired before forty effective steps");
                    helper.runAfterDelay(11,()->{
                        helper.assertTrue(!conduit.isActive() && service.environmentTimeAt(level,pos)==30,"world time advanced conduit phase");
                        service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
                        helper.runAfterDelay(32,()->{
                            helper.assertTrue(conduit.isActive() && service.environmentTimeAt(level,pos)==60,"conduit missed effective periodic deadline");
                            cleanup.run();helper.succeed();
                        });
                    });
                });
            });
        });
        helper.runAtTickTime(149,cleanup);
    }
}
