package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Run twice in separate JVMs. Marker, chunk and SavedData must all survive normal server shutdown. */
public final class RestartHistoryChecks {
    public static void run(GameTestHelper helper) {
        var level=helper.getLevel();var server=level.getServer();
        var service=ServerRuntime.encounters(server);
        var pos=new BlockPos(89001,151,1);
        Set<Long> forced = new HashSet<>();
        for (int x=(pos.getX()-24)>>4; x<=(pos.getX()+24)>>4; x++)
            for (int z=(pos.getZ()-24)>>4; z<=(pos.getZ()+24)>>4; z++) {
                level.getChunk(x,z);
                if (level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
            }
        Runnable releaseChunks = () -> {
            for (long key : forced) {
                var chunk = net.minecraft.world.level.ChunkPos.unpack(key);
                level.setChunkForced(chunk.x(), chunk.z(), false);
            }
            forced.clear();
        };
        helper.runAtTickTime(99, releaseChunks);
        Path marker=server.getWorldPath(LevelResource.ROOT).resolve("dndturn-test-restart.properties");
        try {
            if (Files.exists(marker)) {
                var values=new Properties();try(var in=Files.newInputStream(marker)){values.load(in);}
                UUID encounter=UUID.fromString(values.getProperty("encounter"));
                UUID operation=UUID.fromString(values.getProperty("operation"));
                helper.assertTrue(!service.generation().toString().equals(values.getProperty("generation")),"restart retained server generation");
                level.getChunkAt(pos);
                helper.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_BLOCK),"saved world effect missing after process restart");
                var result=service.lifecycle().results(encounter,0,128).results().stream().filter(r->r.snapshot().operationId().equals(operation)).findFirst().orElseThrow();
                helper.assertTrue(result.terminal()&&result.outcome()==OperationRecord.Outcome.COMPLETED,
                    "historical terminal operation lost after restart");
                helper.assertTrue(!service.actions().running(result.snapshot().owner()),"historical action replayed after restart");
                UUID activeEncounter = UUID.fromString(values.getProperty("activeEncounter"));
                UUID activeOperation = UUID.fromString(values.getProperty("activeOperation"));
                var checkpoint = service.abilityCheckpoint(activeOperation);
                helper.assertTrue(checkpoint != null
                    && checkpoint.phase() == cc.sighs.dndturn.platform.server.checkpoint.AbilityCheckpoint.Phase.APPROACH
                    && checkpoint.release() == cc.sighs.dndturn.platform.server.checkpoint.AbilityCheckpoint.Release.RELEASED,
                    "shutdown lost the active phase or control release evidence");
                service.world().beforeLevelTick(level);
                var terminal = service.lifecycle().results(activeEncounter, 0, 128).results().stream()
                    .filter(r -> r.snapshot().operationId().equals(activeOperation) && r.terminal()).toList();
                helper.assertTrue(terminal.size() == 1 && terminal.getFirst().outcome() == OperationRecord.Outcome.INTERRUPTED,
                    "approach recovery did not produce one interrupted result: " + terminal);
                helper.assertTrue(!service.actions().running(checkpoint.owner())
                    && !service.movement().hasPlayerMoveLease(checkpoint.owner()), "restart restored an old control lease");
                System.out.println("DNDTURN_RESTART_VERIFY_PASS encounter="+encounter+" operation="+operation);
                releaseChunks.run();helper.succeed();return;
            }
            for(var p:BlockPos.betweenClosed(pos.offset(-3,-1,-3),pos.offset(3,3,3)))
                level.setBlockAndUpdate(p,p.getY()<pos.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
            var player=TestPlayers.survival(helper);player.setPos(pos.getX()-1.5,pos.getY(),pos.getZ()+.5);
            helper.runAtTickTime(30,()-> {
                service.lifecycle().requestStart(player,UUID.randomUUID());UUID encounter=service.encounterOf(player.getUUID());
                UUID operation=UUID.randomUUID();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_BLOCK,2));
                var target=new ActionIntent.Target(ActionIntent.TargetKind.BLOCK,level.dimension().identifier().toString(),null,
                    new GridCell(pos.getX(),pos.getY()-1,pos.getZ()),1,.5,1,.5);
                var options=service.actions().selectAndDiscover(player,new ActionProtocol.Query(service.generation(),encounter,UUID.randomUUID(),target,ActionIntent.Hand.MAIN_HAND));
                var offer=options.offers().stream().filter(o->o.intent().behaviorId().equals("dndturn:place")&&o.reason().isEmpty()).findFirst().orElseThrow();
                service.actions().request(player,new ActionProtocol.Request(service.generation(),encounter,operation,options.version(),offer.intent(),false));
                helper.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_BLOCK)&&player.getMainHandItem().getCount()==1,"restart setup did not execute real placement: "
                    + level.getBlockState(pos) + " stack=" + player.getMainHandItem() + " results=" + service.lifecycle().results(encounter,0,128).results());
                var completed = service.lifecycle().results(encounter, 0, 128).results().stream()
                    .filter(r -> r.snapshot().operationId().equals(operation) && r.terminal()).findFirst().orElseThrow();
                cc.sighs.dndturn.platform.server.action.ActionTestAccess.verifyObservationIdentity(
                    helper, service.actions(), player, completed.snapshot());
                service.lifecycle().stop(encounter);service.persistIfChanged();
                // Leave a genuine accepted movement stage alive for the normal stopping event.
                service.lifecycle().requestStart(player, UUID.randomUUID());
                UUID activeEncounter = service.encounterOf(player.getUUID());
                UUID activeOperation = UUID.randomUUID();
                var ground = new ActionIntent.Target(ActionIntent.TargetKind.GROUND, level.dimension().identifier().toString(),
                    null, new GridCell(pos.getX()-2, pos.getY(), pos.getZ()+2), -1, 0, 0, 0);
                var movementOptions = service.actions().selectAndDiscover(player, new ActionProtocol.Query(
                    service.generation(), activeEncounter, UUID.randomUUID(), ground, ActionIntent.Hand.MAIN_HAND));
                var move = movementOptions.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:move"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("restart movement discovery: " + movementOptions));
                service.actions().request(player, new ActionProtocol.Request(service.generation(), activeEncounter,
                    activeOperation, movementOptions.version(), move.intent(), false));
                helper.assertTrue(service.actions().running(player.getUUID()) && service.movement().hasPlayerMoveLease(player.getUUID()),
                    "restart fixture did not leave a live movement stage");
                var values=new Properties();
                values.setProperty("activeEncounter", activeEncounter.toString());
                values.setProperty("activeOperation", activeOperation.toString());
                values.setProperty("encounter",encounter.toString());values.setProperty("operation",operation.toString());
                values.setProperty("generation",service.generation().toString());
                try(var out=Files.newOutputStream(marker)){values.store(out,"Fixture identity; completion verified only by next JVM");}
                catch(java.io.IOException e){throw new IllegalStateException(e);}
                System.out.println("DNDTURN_RESTART_WRITE_READY encounter="+encounter+" operation="+operation);
                releaseChunks.run();
                helper.succeed();
            });
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
