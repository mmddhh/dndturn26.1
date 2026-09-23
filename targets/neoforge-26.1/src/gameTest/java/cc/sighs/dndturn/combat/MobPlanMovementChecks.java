package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;

/** Focused movement-child fixture; direct admission does not substitute for the final AI test. */
public final class MobPlanMovementChecks {
    public static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        var base = new BlockPos(69001,151,1);
        Set<Long> forced = new HashSet<>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++)
            for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++) {
                level.getChunk(x,z);
                if(level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
            }
        for(var pos:BlockPos.betweenClosed(base.offset(-6,-1,-6),base.offset(6,4,6)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=helper.makeMockServerPlayerInLevel();
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+3.5); player.setNoGravity(true);
        var mob=EntityType.COW.create(level,EntitySpawnReason.COMMAND);
        mob.setPos(base.getX()+.5,base.getY(),base.getZ()+.5); mob.setNoAi(true);
        level.addFreshEntity(mob);
        var service=ServerCombatService.forServer(level.getServer());
        Runnable cleanup=()-> {
            service.leave(player.getUUID()); service.leave(mob.getUUID()); mob.discard();
            for(long value:forced) { var c=net.minecraft.world.level.ChunkPos.unpack(value); level.setChunkForced(c.x(),c.z(),false); }
            forced.clear();
        };
        helper.runAtTickTime(199,cleanup);
        helper.runAtTickTime(40,()-> {
            try {
                helper.assertTrue(level.getEntity(mob.getUUID())==mob,"Cow fixture not registered");
                // Normal START explicitly enrolls every living Mob in discovery, including Cow.
                // See ServerCombatService.commitConsentedEncounter and neutralMobTurn regression.
                service.requestStart(player,UUID.randomUUID());
                UUID id=service.encounterOf(player.getUUID());
                helper.assertTrue(id!=null && id.equals(service.encounterOf(mob.getUUID()))
                    && service.state(id).members().containsKey(mob.getUUID()),"normal START did not admit Cow");
                for(int n=0;n<3 && !mob.getUUID().equals(service.state(id).current());n++)
                    service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
                helper.assertTrue(mob.getUUID().equals(service.state(id).current()),"Cow turn not reached");
                mob.setNoAi(false); mob.setOnGround(true);
                var state=service.state(id);
                var goal=new GridCell(base.getX()+3,base.getY(),base.getZ());
                var intent=new TacticalIntent("dndturn:move", 1, TacticalIntent.Capability.MOVE, new TacticalIntent.Target(TacticalIntent.TargetKind.GROUND,state.region().dimension(),null,goal,-1,0,0,0), cc.sighs.dndturn.combat.AbilitySource.basic());
                var root=new OperationRecord.Snapshot(UUID.randomUUID(),null,id,mob.getUUID(),mob.getUUID(),null,
                    service.planClock(),state.version(),TacticalActions.cell(mob.blockPosition()),goal,OperationRecord.Kind.PLAN,service.generation(),intent);
                helper.assertTrue(service.admitPlan(root),"movement PLAN not admitted");
                UUID movement=UUID.randomUUID(); service.beginMobPlanMovement(mob,root,movement,goal);
                var before=mob.position();
                helper.startSequence().thenWaitUntil(()->helper.assertTrue(mob.position().distanceToSqr(before)>.2,"navigation has not displaced Cow"))
                    .thenExecute(()-> {
                        service.finishPlanMovement(mob.getUUID(),movement);
                        var results=service.results(id,0,128).results();
                        helper.assertTrue(results.stream().anyMatch(r->r.snapshot().operationId().equals(movement)
                            && root.operationId().equals(r.snapshot().parentId()) && r.actualMovementTicks()>0),"missing paid MOVE child");
                        helper.assertTrue(service.state(id).current().equals(mob.getUUID())
                            && service.state(id).members().get(mob.getUUID()).action(),"movement driver ended turn or spent action");
                        helper.assertTrue(!service.hasMobMoveLease(mob.getUUID()) && mob.getNavigation().isDone(),"navigation lease not released");
                        cleanup.run();
                    }).thenSucceed();
            } catch(Throwable error) { cleanup.run(); throw error; }
        });
    }
}
