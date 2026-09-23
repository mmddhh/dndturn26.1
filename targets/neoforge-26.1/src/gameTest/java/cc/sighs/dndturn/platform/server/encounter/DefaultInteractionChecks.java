package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.server.action.MinecraftCoordinates;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** Native item/block execution through selection, discovery and submission. */
public final class DefaultInteractionChecks {
    public static void run(GameTestHelper h) {
        var level=h.getLevel(); var base=new BlockPos(100001,151,1);
        Set<Long> forced=new HashSet<>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++) for(int z=-2;z<=2;z++) {
            level.getChunk(x,z);
            if(level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
        }
        level.waitForEntities(net.minecraft.world.level.ChunkPos.containing(base),1);
        for(var pos:BlockPos.betweenClosed(base.offset(-5,-1,-5),base.offset(5,4,5)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=TestPlayers.survival(h);
        for(int n=0;n<8;n++) player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(n));
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5); player.connection.resetPosition();
        var service=ServerRuntime.encounters(level.getServer());
        Runnable cleanup=()->{
            var id=service.encounterOf(player.getUUID()); if(id!=null) service.stop(id);
            for(long key:forced) { var c=net.minecraft.world.level.ChunkPos.unpack(key); level.setChunkForced(c.x(),c.z(),false); }
            forced.clear();
        };
        h.runAtTickTime(99,cleanup);
        h.runAtTickTime(30,()->{
            try {
                var clicked=base.offset(1,-1,0); var destination=clicked.above();
                for(int scenario=0;scenario<3;scenario++) {
                    level.setBlockAndUpdate(destination,scenario==2?Blocks.LEVER.defaultBlockState():Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(clicked,scenario==1?Blocks.DIRT.defaultBlockState():Blocks.STONE.defaultBlockState());
                    var item=scenario==0?Items.OAK_FENCE:scenario==1?Items.POTION:Items.REDSTONE;
                    player.getInventory().setSelectedSlot(0); player.getInventory().setItem(0,new ItemStack(item,8));
                    if(scenario==1) player.getMainHandItem().set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.WATER));
                    service.requestStart(player,UUID.randomUUID()); var encounter=service.encounterOf(player.getUUID());
                    for(int n=0;n<service.state(encounter).members().size() && !player.getUUID().equals(service.state(encounter).current());n++)
                        service.endCurrentTurn(encounter,UUID.randomUUID(),service.state(encounter).version());
                    var pos=scenario==2?destination:clicked;
                    var target=new ActionIntent.Target(ActionIntent.TargetKind.BLOCK,level.dimension().identifier().toString(),null,
                        MinecraftCoordinates.cell(pos),Direction.UP.ordinal(),.5,1,.5);
                    var options=service.tacticalActions().selectAndDiscover(player,new ActionProtocol.Query(service.generation(),encounter,UUID.randomUUID(),target,ActionIntent.Hand.MAIN_HAND));
                    String behavior=scenario==0?"dndturn:place":scenario==1?"dndturn:tool":"dndturn:block";
                    var offer=options.offers().stream().filter(o->o.intent().behaviorId().equals(behavior)).findFirst().orElseThrow(()->new IllegalStateException("missing "+behavior+": "+options));
                    h.assertTrue(offer.reason().isEmpty(),behavior+" unavailable: "+offer.reason());
                    h.assertTrue(player.getMainHandItem().getCount()==8,"query consumed item");
                    var operation=UUID.randomUUID();
                    service.tacticalActions().request(player,new ActionProtocol.Request(service.generation(),encounter,operation,options.version(),offer.intent(),false));
                    var results=service.results(encounter,0,128).results();
                    var result=results.stream().filter(r->r.snapshot().operationId().equals(operation)).findFirst().orElseThrow();
                    h.assertTrue(result.outcome()==OperationRecord.Outcome.COMPLETED,behavior+" failed: "+results);
                    if(scenario<2) {
                        h.assertTrue(level.getBlockState(scenario==0?destination:clicked).is(scenario==0?Blocks.OAK_FENCE:Blocks.MUD),"native placement missing");
                        h.assertTrue(player.getMainHandItem().getCount()==7,"wrong item consumption");
                        h.assertTrue(!service.state(encounter).members().get(player.getUUID()).action(),"paid use did not spend action");
                    } else {
                        h.assertTrue(level.getBlockState(destination).getValue(LeverBlock.POWERED),"native block callback missing");
                        h.assertTrue(player.getMainHandItem().getCount()==8,"free block interaction consumed held item");
                        h.assertTrue(service.state(encounter).members().get(player.getUUID()).action(),"free block interaction spent action");
                    }
                    System.out.println("DEFAULT_INTERACTION behavior="+behavior+" passed");
                    service.stop(encounter);
                }
                var cow=net.minecraft.world.entity.EntityType.COW.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                cow.setPos(base.getX()+.5,base.getY(),base.getZ()+1.5); cow.setNoAi(true); level.addFreshEntity(cow);
                try {
                    player.getInventory().setItem(0,new ItemStack(Items.BUCKET));
                    service.requestStart(player,UUID.randomUUID()); var encounter=service.encounterOf(player.getUUID());
                    for(int n=0;n<service.state(encounter).members().size() && !player.getUUID().equals(service.state(encounter).current());n++)
                        service.endCurrentTurn(encounter,UUID.randomUUID(),service.state(encounter).version());
                    var target=new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,level.dimension().identifier().toString(),cow.getUUID(),null,-1,0,0,0);
                    var options=service.tacticalActions().selectAndDiscover(player,new ActionProtocol.Query(service.generation(),encounter,UUID.randomUUID(),target,ActionIntent.Hand.MAIN_HAND));
                    var offer=options.offers().stream().filter(o->o.intent().behaviorId().equals("dndturn:entity_item")).findFirst().orElseThrow();
                    h.assertTrue(offer.reason().isEmpty(),"cow item interaction unavailable: "+offer.reason());
                    h.assertTrue(player.getMainHandItem().is(Items.BUCKET),"discovery milked cow");
                    var operation=UUID.randomUUID();
                    service.tacticalActions().request(player,new ActionProtocol.Request(service.generation(),encounter,operation,options.version(),offer.intent(),false));
                    var results=service.results(encounter,0,128).results();
                    h.assertTrue(results.stream().anyMatch(r->r.snapshot().operationId().equals(operation) && r.outcome()==OperationRecord.Outcome.COMPLETED),"cow interaction failed: "+results);
                    h.assertTrue(player.getMainHandItem().is(Items.MILK_BUCKET),"native milking callback missing");
                    System.out.println("DEFAULT_INTERACTION behavior=dndturn:entity_item cow passed");
                } finally { cow.discard(); }
                h.succeed();
            } finally { cleanup.run(); }
        });
    }
}
