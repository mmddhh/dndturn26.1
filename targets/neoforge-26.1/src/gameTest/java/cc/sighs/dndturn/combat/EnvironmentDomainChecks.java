package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** A's real explosion cannot grant B an entity effect or an environment step. */
public final class EnvironmentDomainChecks {
    public static void run(GameTestHelper helper) {
        var level=helper.getLevel();var base=new BlockPos(87001,151,1);Set<Long> forced=new HashSet<>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+44)>>4;x++)
            for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++){
                level.getChunk(x,z);if(level.setChunkForced(x,z,true))forced.add(ChunkPos.pack(x,z));
            }
        for(var pos:BlockPos.betweenClosed(base.offset(-7,-1,-4),base.offset(27,3,4)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()?Blocks.OBSIDIAN.defaultBlockState():Blocks.AIR.defaultBlockState());
        var first=helper.makeMockServerPlayerInLevel();first.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var second=helper.makeMockServerPlayerInLevel();second.setPos(base.getX()+20.5,base.getY(),base.getZ()+.5);
        var service=ServerCombatService.forServer(level.getServer());List<Entity> entities=new ArrayList<>();
        Runnable cleanup=()->{
            for(var player:List.of(first,second)){UUID id=service.encounterOf(player.getUUID());if(id!=null)service.stop(id);}
            entities.forEach(Entity::discard);
            for(long key:forced){var chunk=ChunkPos.unpack(key);level.setChunkForced(chunk.x(),chunk.z(),false);}forced.clear();
        };
        helper.runAtTickTime(30,()->{
            service.requestStart(first,UUID.randomUUID());service.requestStart(second,UUID.randomUUID());
            UUID a=service.encounterOf(first.getUUID()),b=service.encounterOf(second.getUUID());
            helper.assertTrue(a!=null && b!=null && !a.equals(b),"independent domains not established");
            var exposed=EntityType.COW.create(level,EntitySpawnReason.COMMAND);
            var protectedCow=EntityType.COW.create(level,EntitySpawnReason.COMMAND);
            exposed.setPos(base.getX()+2.5,base.getY(),base.getZ()+.5);
            protectedCow.setPos(base.getX()+12.6,base.getY(),base.getZ()+.5);
            for(var cow:List.of(exposed,protectedCow)){cow.setNoAi(true);level.addFreshEntity(cow);entities.add(cow);}
            float health=protectedCow.getHealth();var position=protectedCow.position();
            var tnt=new PrimedTnt(level,base.getX()+7.5,base.getY(),base.getZ()+.5,null);
            tnt.setFuse(1);tnt.setNoGravity(true);tnt.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.addFreshEntity(tnt);entities.add(tnt);
            service.endCurrentTurn(a,UUID.randomUUID(),service.state(a).version());
            helper.runAfterDelay(5,()->{
                helper.assertTrue(tnt.isRemoved() && exposed.getHealth()<health,"fixture did not execute an effective TNT explosion");
                helper.assertTrue(protectedCow.getHealth()==health && protectedCow.position().distanceToSqr(position)<.001,
                    "A explosion damaged or pushed B's paused domain");
                helper.assertTrue(service.environmentTimeAt(level,second.blockPosition())==0,"A's step advanced B's clock");
                cleanup.run();helper.succeed();
            });
        });
        helper.runAtTickTime(99,cleanup);
    }
}
