package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Full vanilla fuse across three thirty-step budgets, with real member damage and post-death removal. */
public final class EnvironmentTntChecks {
    public static void run(GameTestHelper helper) {
        var level = helper.getLevel(); var base = new BlockPos(83001,151,1);
        Set<Long> forced = new HashSet<>();
        for (int x=(base.getX()-24)>>4; x<=(base.getX()+24)>>4; x++)
            for (int z=(base.getZ()-24)>>4; z<=(base.getZ()+24)>>4; z++) {
                level.getChunk(x,z); if(level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
            }
        level.waitForEntities(net.minecraft.world.level.ChunkPos.containing(base), 1);
        for (var pos:BlockPos.betweenClosed(base.offset(-7,-1,-7),base.offset(7,4,7)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()?Blocks.OBSIDIAN.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=TestPlayers.survival(helper); player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        // The player-centered field only admits entities with an actual hostile lock; a passive cow
        // no longer joins. A husk that targets the player gives the TNT a real member victim, and
        // unlike a zombie it does not burn down to its 1 HP before the fixture starts.
        var victim=EntityType.HUSK.create(level,EntitySpawnReason.COMMAND);
        victim.setPos(base.getX()+3.5,base.getY(),base.getZ()+.5);victim.setHealth(1);
        level.addFreshEntity(victim);victim.setNoAi(true);victim.setNoGravity(true);
        var service=ServerRuntime.encounters(level.getServer());
        List<PrimedTnt> tnts=new ArrayList<>();
        Runnable cleanup=()-> {
            UUID id=service.encounterOf(player.getUUID());if(id!=null)service.stop(id);
            victim.discard();tnts.forEach(Entity::discard);
            for(long key:forced){var chunk=ChunkPos.unpack(key);level.setChunkForced(chunk.x(),chunk.z(),false);}forced.clear();
        };
        helper.runAtTickTime(30,()-> {
            // Re-establish the hostile lock in the same tick as the start, so the field admission
            // sees an actual target rather than a tick-old AI decision.
            victim.setTarget(player);
            service.requestStart(player,UUID.randomUUID()); UUID id=service.encounterOf(player.getUUID());
            helper.assertTrue(id!=null && service.isMember(victim.getUUID()),
                "TNT victim must be a real member; target=" + victim.getTarget()
                    + " members=" + (id == null ? null : service.state(id).members().keySet()));
            var pos=base.east(2);
            level.setBlockAndUpdate(pos,Blocks.TNT.defaultBlockState());
            level.setBlockAndUpdate(pos.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            tnts.addAll(level.getEntitiesOfClass(PrimedTnt.class,new net.minecraft.world.phys.AABB(pos).inflate(2)));
            helper.assertTrue(tnts.size()==1 && tnts.getFirst().getFuse()==80,"vanilla initial fuse changed");
            var tnt=tnts.getFirst();
            class Rounds {
                int completed;
                void waitThenAdvance() {
                    int fuse=tnt.getFuse(); var position=tnt.position();
                    helper.runAfterDelay(completed%2==0?3:17,()-> {
                        helper.assertTrue(tnt.getFuse()==fuse && tnt.position().equals(position),"thinking advanced TNT fuse or body");
                        for(int i=0;i<service.state(id).members().size()
                            && service.state(id).phase()!=EncounterPhase.ENVIRONMENT;i++)
                            service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
                        helper.runAfterDelay(32,()-> {
                            completed++;
                            if(completed<3) {
                                helper.assertTrue(!tnt.isRemoved() && tnt.getFuse()==80-completed*30,
                                    "environment window did not consume exactly its TNT steps: "+tnt.getFuse());
                                waitThenAdvance();
                            } else {
                                helper.assertTrue(tnt.isRemoved() && !victim.isAlive(),"vanilla explosion did not damage member");
                                helper.runAfterDelay(22,()-> {
                                    helper.assertTrue(victim.isRemoved(),"dead member lifecycle stopped outside its turn");
                                    cleanup.run();helper.succeed();
                                });
                            }
                        });
                    });
                }
            }
            new Rounds().waitThenAdvance();
        });
        helper.runAtTickTime(219,cleanup);
    }
}
