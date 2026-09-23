package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.EquippedRanged;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

public final class MobRangedApproachChecks {
    public static void run(GameTestHelper h) { run(h,false); }
    public static void revoked(GameTestHelper h) { run(h,true); }
    private static void run(GameTestHelper h, boolean revoke) {
        var level=h.getLevel();var base=new BlockPos(revoke?214001:213001,185,1);var forced=new HashSet<Long>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++)for(int z=-2;z<=2;z++) {
            level.getChunk(x,z);if(level.setChunkForced(x,z,true))forced.add(ChunkPos.pack(x,z));
        }
        level.waitForEntities(ChunkPos.containing(base),1);
        for(var pos:BlockPos.betweenClosed(base.offset(-3,-1,-5),base.offset(12,4,5)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()||pos.getY()==base.getY()+4?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=TestPlayers.survival(h);
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);player.connection.resetPosition();
        var mob=EntityType.SKELETON.create(level,EntitySpawnReason.COMMAND);
        mob.setPos(base.getX()+8.5,base.getY(),base.getZ()+.5);mob.setNoAi(true);mob.setPersistenceRequired();
        mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));level.addFreshEntity(mob);
        var service=ServerRuntime.encounters(level.getServer());
        UUID[] id={null},operation={null};int[] movement={0};var start=mob.position();
        Runnable cleanup=()->{
            if(id[0]!=null && service.encounterOf(player.getUUID())!=null)service.stop(id[0]);
            mob.discard();player.discard();
            for(long key:forced){var c=ChunkPos.unpack(key);level.setChunkForced(c.x(),c.z(),false);}forced.clear();
        };
        h.runAtTickTime(299,cleanup);
        h.startSequence().thenIdle(20).thenExecute(()->service.requestStart(player,UUID.randomUUID())).thenWaitUntil(()->{
            id[0]=service.encounterOf(mob.getUUID());h.assertTrue(id[0]!=null,"approach actor not discovered");
            var state=service.state(id[0]);if(player.getUUID().equals(state.current()))service.endTurn(id[0],player,UUID.randomUUID(),state.version());
            state=service.state(id[0]);h.assertTrue(mob.getUUID().equals(state.current())&&state.members().get(mob.getUUID()).action(),"waiting for approach turn");
        }).thenExecute(()->{
            var actor=new LiveActorContext(mob);var state=service.state(id[0]);
            var target=new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,level.dimension().identifier().toString(),player.getUUID(),null,-1,0,0,0);
            var found=AbilityAdapterRegistry.discover(actor,state,target).stream().filter(o->o.binding().id().equals(EquippedRanged.ID)).findFirst().orElseThrow();
            h.assertTrue(found.availability()==AbilityAdapterRegistry.Availability.APPROACH_REQUIRED,"out of range bow did not request approach");
            movement[0]=state.members().get(actor.id()).movementTicks();operation[0]=UUID.randomUUID();mob.setNoAi(false);mob.setOnGround(true);
            service.tacticalActions().submit(actor,service.generation(),id[0],operation[0],state.version(),found.binding().invocation(target),false);
            h.assertTrue(service.hasMobMoveLease(actor.id()),"ranged approach did not acquire native navigation: "+service.results(id[0],0,128).results());
            h.assertTrue(service.state(id[0]).members().get(actor.id()).action(),"approach spent attack action early");
            if(revoke) {
                var effects=service.actorStates().state(actor.id());
                service.actorStates().command(actor,new ActorStates.Command(UUID.randomUUID(),actor.id(),effects.revision(),
                        List.of(new ActorStates.Remove(found.binding().source().grant()))));
            }
        }).thenWaitUntil(()->{
            h.assertTrue(service.encounterOf(mob.getUUID())!=null,"approach encounter ended before terminal verification");
            h.assertTrue(service.results(id[0],0,128).results().stream().anyMatch(r->r.snapshot().operationId().equals(operation[0])),"waiting for approach terminal");
        }).thenExecute(()->{
            var result=service.results(id[0],0,128).results().stream().filter(r->r.snapshot().operationId().equals(operation[0])).findFirst();
            h.assertTrue(result.isPresent(),"waiting for ranged approach outcome");
            var state=service.state(id[0]);
            if(revoke) {
                h.assertTrue(result.get().outcome()!=OperationRecord.Outcome.COMPLETED,"revoked source still fired");
                h.assertTrue(state.members().get(mob.getUUID()).action(),"revoked approach consumed action");
                h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,mob.getBoundingBox().inflate(16)).isEmpty(),"revoked source spawned arrow");
            } else {
                h.assertTrue(result.get().outcome()==OperationRecord.Outcome.COMPLETED,"approach failed: "+result);
                h.assertTrue(mob.position().distanceToSqr(start)>.25,"native navigation did not move");
                h.assertTrue(state.members().get(mob.getUUID()).movementTicks()<movement[0],"native approach did not charge movement");
                h.assertTrue(!state.members().get(mob.getUUID()).action(),"arrived shot did not spend action");
            }
            var leasedOperation=service.controlFacts().controlEvidence(mob).operation();
            var completedChildren=service.results(id[0],0,128).results().stream()
                    .filter(r->operation[0].equals(r.snapshot().parentId())).map(r->r.snapshot().operationId()).toList();
            h.assertTrue(leasedOperation==null || !operation[0].equals(leasedOperation) && !completedChildren.contains(leasedOperation),
                    "terminal approach retained its navigation lease");
            System.out.println("MOB_RANGED_APPROACH revoke="+revoke+" outcome="+result.get().outcome());
        }).thenExecute(cleanup).thenSucceed();
    }
}
