package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** Selection -> discovery -> request -> native melee. Embedded connection, not real client input. */
public final class GateMeleeChecks {
    public static void run(GameTestHelper h) { run(h, false, false); }
    public static void approach(GameTestHelper h) { run(h, true, false); }
    public static void cow(GameTestHelper h) { run(h, false, true); }
    private static void run(GameTestHelper h, boolean approach, boolean cow) {
        var level = h.getLevel();
        var base = new BlockPos(cow ? 99001 : approach ? 98001 : 97001, 151, 1);
        Set<Long> forced = new HashSet<>();
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++)
            for (int z = -2; z <= 2; z++) {
                level.getChunk(x,z);
                if (level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
            }
        level.waitForEntities(net.minecraft.world.level.ChunkPos.containing(base), 1);
        for (var pos : BlockPos.betweenClosed(base.offset(-5,-1,-5),base.offset(5,4,5)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player = TestPlayers.survival(h);
        for (int id=0;id<8;id++) player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id));
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        player.connection.resetPosition();
        var mob = (cow ? EntityType.COW : EntityType.CREEPER).create(level,EntitySpawnReason.COMMAND);
        mob.setPos(base.getX()+(approach ? 4.5 : 1.5),base.getY(),base.getZ()+.5);
        mob.setNoAi(true); mob.setPersistenceRequired(); level.addFreshEntity(mob);
        var service = ServerRuntime.encounters(level.getServer());
        Runnable cleanup = () -> {
            UUID id = service.encounterOf(player.getUUID()); if (id != null) service.stop(id);
            mob.discard();
            for (long key : forced) { var c = net.minecraft.world.level.ChunkPos.unpack(key); level.setChunkForced(c.x(),c.z(),false); }
            forced.clear();
        };
        h.runAtTickTime(99,cleanup);
        h.runAtTickTime(30, () -> {
            if (approach) {
                approach(h, service, player, mob, base, cleanup);
                return;
            }
            try {
                mob.getRandom().setSeed(731);
                for (Item item : List.of(Items.IRON_AXE, Items.IRON_SWORD, Items.STONE)) {
                    mob.setHealth(mob.getMaxHealth());
                    player.getInventory().setSelectedSlot(0);
                    player.getInventory().setItem(0, ItemStack.EMPTY);
                    player.getInventory().setItem(3, new ItemStack(item));
                    service.requestStart(player,UUID.randomUUID());
                    UUID encounter = service.encounterOf(player.getUUID());
                    h.assertTrue(encounter != null && service.state(encounter).members().containsKey(mob.getUUID()), "fixture target not discovered");
                    for (int n=0;n<service.state(encounter).members().size() && !player.getUUID().equals(service.state(encounter).current());n++)
                        service.endCurrentTurn(encounter,UUID.randomUUID(),service.state(encounter).version());
                    // The GUI first discovers on SELF when selecting a slot, before it has an attack target.
                    var self = new ActionIntent.Target(ActionIntent.TargetKind.SELF,level.dimension().identifier().toString(),null,null,-1,0,0,0);
                    var selection = service.tacticalActions().selectAndDiscover(player,new ActionProtocol.Query(
                        service.generation(),encounter,UUID.randomUUID(),self,ActionIntent.Hand.MAIN_HAND,3,1,null));
                    h.assertTrue(selection.item() != null && selection.reason().isEmpty(), "slot confirmation failed: " + selection.reason());
                    h.assertTrue(player.getInventory().getSelectedSlot() == 3 && selection.item().slot() == 3, "selection confirmed the wrong slot");
                    h.assertTrue(selection.abilities().stream().anyMatch(a -> a.id().equals("dndturn:melee")), "SELF discovery omitted weapon action row");
                    h.assertTrue(service.state(encounter).members().get(player.getUUID()).action(), "selection spent action");
                    var target = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,level.dimension().identifier().toString(),mob.getUUID(),null,-1,0,0,0);
                    var options = service.tacticalActions().selectAndDiscover(player,new ActionProtocol.Query(
                        service.generation(),encounter,UUID.randomUUID(),target,ActionIntent.Hand.MAIN_HAND,3,2,selection.item()));
                    var offer = options.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:melee")).findFirst().orElseThrow();
                    h.assertTrue(offer.reason().isEmpty(),item + " discovery rejected: " + offer.reason());
                    UUID op = UUID.randomUUID();
                    var request = new ActionProtocol.Request(service.generation(),encounter,op,options.version(),offer.intent(),false);
                    service.tacticalActions().request(player,request);
                    var results = service.results(encounter,0,128).results();
                    var result = results.stream().filter(r -> r.snapshot().operationId().equals(op)).findFirst().orElseThrow();
                    h.assertTrue(result.outcome()==OperationRecord.Outcome.COMPLETED,item+" attack failed: "+results);
                    var trace = results.stream().filter(r -> r.snapshot().kind()==OperationRecord.Kind.ATTACK)
                        .map(OperationRecord.Result::damageTrace).filter(Objects::nonNull).findFirst().orElseThrow();
                    h.assertTrue(item == Items.STONE ? trace.weaponDamage()==0 : trace.weaponDamage()>0,"wrong item damage input");
                    if (trace.hit() && trace.tacticalDamage()>0)
                        h.assertTrue(trace.vanillaAccepted() && trace.healthLoss()>0,"legal hit never reached native damage");
                    System.out.println("GATE_MELEE item="+item+" hit="+trace.hit()+" healthLoss="+trace.healthLoss());
                    h.assertTrue(!service.state(encounter).members().get(player.getUUID()).action(),"attack did not spend action");
                    int count = results.size();
                    service.tacticalActions().request(player,request);
                    h.assertTrue(service.results(encounter,0,128).results().size()==count,"retry duplicated attack");
                    service.stop(encounter);
                }
                h.succeed();
            } finally { cleanup.run(); }
        });
    }
    private static void approach(GameTestHelper h, EncounterRuntime service,
            net.minecraft.server.level.ServerPlayer player, net.minecraft.world.entity.Mob target,
            BlockPos base, Runnable cleanup) {
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_AXE));
        player.connection.resetPosition();
        service.requestStart(player,UUID.randomUUID());
        UUID encounter=service.encounterOf(player.getUUID());
        h.assertTrue(service.state(encounter).members().containsKey(target.getUUID()),"approach target not discovered");
        for (int n=0;n<service.state(encounter).members().size() && !player.getUUID().equals(service.state(encounter).current());n++)
            service.endCurrentTurn(encounter,UUID.randomUUID(),service.state(encounter).version());
        var targetValue=new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,player.level().dimension().identifier().toString(),target.getUUID(),null,-1,0,0,0);
        var options=service.tacticalActions().selectAndDiscover(player,new ActionProtocol.Query(service.generation(),encounter,UUID.randomUUID(),targetValue,ActionIntent.Hand.MAIN_HAND));
        var offer=options.offers().stream().filter(o->o.intent().behaviorId().equals("dndturn:melee")).findFirst().orElseThrow();
        var endpoint=new ActionIntent.Point(base.getX()+3.5,base.getY(),base.getZ()+.5);
        var intent=offer.intent().withApproach(new ActionIntent.Approach(UUID.randomUUID(),0,endpoint));
        UUID operation=UUID.randomUUID();
        int movement=service.state(encounter).members().get(player.getUUID()).movementTicks();
        service.tacticalActions().request(player,new ActionProtocol.Request(service.generation(),encounter,operation,options.version(),intent,false));
        h.assertTrue(service.hasPlayerMoveLease(player.getUUID()),"approach did not acquire movement: "+service.results(encounter,0,128).results());
        h.assertTrue(service.state(encounter).members().get(player.getUUID()).action(),"approach charged attack before execution");
        var start=player.position();
        class Driver {
            int ticks;
            boolean observedCharge;
            void step() {
                if (service.hasPlayerMoveLease(player.getUUID())) {
                    observedCharge |= service.state(encounter).members().get(player.getUUID()).movementTicks()<movement;
                    player.connection.handlePlayerInput(new net.minecraft.network.protocol.game.ServerboundPlayerInputPacket(
                        new net.minecraft.world.entity.player.Input(true,false,false,false,false,false,false)));
                    player.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(
                        Math.min(endpoint.x(),player.getX()+.1),player.getY(),player.getZ(),-90,0,true,false));
                }
                if (++ticks<48) h.runAfterDelay(1,this::step);
            }
        }
        var driver = new Driver();
        h.runAfterDelay(1,driver::step);
        h.runAtTickTime(80,()-> {
            try {
                var results=service.results(encounter,0,128).results();
                var result=results.stream().filter(r->r.snapshot().operationId().equals(operation)).findFirst().orElseThrow();
                h.assertTrue(result.outcome()==OperationRecord.Outcome.COMPLETED,"approach attack failed: "+results);
                h.assertTrue(player.position().distanceToSqr(start)>4,"movement packets did not move player");
                h.assertTrue(driver.observedCharge,"approach did not charge movement while its lease was active");
                h.assertTrue(results.stream().anyMatch(r -> r.snapshot().kind()==OperationRecord.Kind.MOVE
                    && r.actualMovementTicks()>0),"approach has no charged movement receipt: "+results);
                h.assertTrue(!service.hasPlayerMoveLease(player.getUUID()) && !service.tacticalActions().running(player.getUUID()),"completed attack retained control");
                h.succeed();
            } finally { cleanup.run(); }
        });
    }
}
