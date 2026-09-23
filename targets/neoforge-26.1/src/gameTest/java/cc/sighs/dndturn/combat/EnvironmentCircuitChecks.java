package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

/** Native contact -> held plant ticks, plus block-event/BE piston and fluid phases. */
public final class EnvironmentCircuitChecks {
    public static void run(GameTestHelper helper) {
        var level=helper.getLevel();var base=new BlockPos(91001,151,1);Set<Long> forced=new HashSet<>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++)
            for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++){
                level.getChunk(x,z);if(level.setChunkForced(x,z,true))forced.add(ChunkPos.pack(x,z));
            }
        for(var pos:BlockPos.betweenClosed(base.offset(-7,-1,-7),base.offset(7,4,7)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=helper.makeMockServerPlayerInLevel();player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var service=ServerCombatService.forServer(level.getServer());List<Entity> entities=new ArrayList<>();
        Runnable cleanup=()->{
            UUID id=service.encounterOf(player.getUUID());if(id!=null)service.stop(id);
            entities.forEach(Entity::discard);
            for(long key:forced){var chunk=ChunkPos.unpack(key);level.setChunkForced(chunk.x(),chunk.z(),false);}forced.clear();
        };
        helper.runAtTickTime(30,()->{
            service.requestStart(player,UUID.randomUUID());UUID id=service.encounterOf(player.getUUID());
            var leaf=base.north(4);var piston=base.south(4);var water=base.west(4);
            level.setBlockAndUpdate(leaf.below(),Blocks.CLAY.defaultBlockState());
            level.setBlockAndUpdate(leaf,Blocks.BIG_DRIPLEAF.defaultBlockState());
            var cow=EntityType.COW.create(level,EntitySpawnReason.COMMAND);
            cow.setPos(leaf.getX()+.5,leaf.getY()+1,leaf.getZ()+.5);level.addFreshEntity(cow);entities.add(cow);
            level.setBlockAndUpdate(piston,Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.EAST));
            level.setBlockAndUpdate(piston.west(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(water,Blocks.WATER.defaultBlockState());
            helper.runAfterDelay(13,()->{
                helper.assertTrue(level.getBlockState(leaf).getValue(BlockStateProperties.TILT)==Tilt.UNSTABLE,
                    "direct dripleaf contact must become UNSTABLE without consuming its delay: "
                        +level.getBlockState(leaf)+" cow="+cow.position()+" grounded="+cow.onGround());
                helper.assertTrue(!level.getBlockState(piston).getValue(BlockStateProperties.EXTENDED),"piston event ran during thinking");
                helper.assertTrue(level.getFluidState(water.west()).isEmpty(),"fluid propagated during thinking");
                service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
                helper.runAfterDelay(12,()->helper.assertTrue(level.getBlockState(leaf).getValue(BlockStateProperties.TILT)==Tilt.PARTIAL,
                    "dripleaf did not retain its ten-step intermediate phase"));
                helper.runAfterDelay(32,()->{
                    helper.assertTrue(level.getBlockState(leaf).getValue(BlockStateProperties.TILT)==Tilt.FULL,"dripleaf did not reach FULL after twenty effective steps");
                    helper.assertTrue(level.getBlockState(piston).getValue(BlockStateProperties.EXTENDED)
                        && level.getBlockState(piston.east()).is(Blocks.PISTON_HEAD),"piston event and moving BE did not complete together");
                    helper.assertTrue(!level.getFluidState(water.west()).isEmpty(),"environment did not release fluid propagation");
                    class ResetRounds {
                        int completed;
                        void advance() {
                            service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
                            helper.runAfterDelay(32,()->{
                                completed++;
                                Tilt expected=completed==3?Tilt.NONE:Tilt.FULL;
                                helper.assertTrue(level.getBlockState(leaf).getValue(BlockStateProperties.TILT)==expected,
                                    "dripleaf reset did not preserve its hundred-step delay");
                                if(completed==3){cleanup.run();helper.succeed();}else advance();
                            });
                        }
                    }
                    new ResetRounds().advance();
                });
            });
        });
        helper.runAtTickTime(219,cleanup);
    }
}
