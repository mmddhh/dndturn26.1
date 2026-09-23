package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.ActorPersistentState;
import cc.sighs.dndturn.domain.actor.ActorRuntimeState;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.server.persistence.ActorSavedData;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Isolated codec/capacity fixture plus actual player selection queries after loaded-state recovery. */
public final class ActorHistoryChecks {
    public static void run(GameTestHelper h) {
        codec(h);
        var level = h.getLevel();
        var server = level.getServer();
        var base = new BlockPos(99001, 151, 1);
        var forced = new ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++) for (int z=-2; z<=2; z++) {
            level.getChunk(x,z);
            if (level.setChunkForced(x,z,true)) forced.add(new net.minecraft.world.level.ChunkPos(x,z));
        }
        level.waitForEntities(net.minecraft.world.level.ChunkPos.containing(base), 1);
        for (var pos : BlockPos.betweenClosed(base.offset(-5,-1,-5),base.offset(5,4,5)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        String source = System.getenv("DNDTURN_CAPACITY_ACTOR");
        var player = TestPlayers.survival(h, source == null ? UUID.randomUUID() : UUID.fromString(source));
        for (int id=0;id<8;id++) player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id));
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5); player.connection.resetPosition();
        var mob = EntityType.CREEPER.create(level, EntitySpawnReason.COMMAND);
        mob.setPos(base.getX()+1.5,base.getY(),base.getZ()+.5); mob.setNoAi(true); mob.setPersistenceRequired(); level.addFreshEntity(mob);
        var service = ServerRuntime.encounters(server);
        Runnable cleanup = () -> {
            UUID encounter = service.encounterOf(player.getUUID()); if (encounter != null) service.stop(encounter);
            mob.discard();
            for (var chunk : forced) level.setChunkForced(chunk.x(),chunk.z(),false);
            forced.clear();
        };
        h.runAtTickTime(190, cleanup);
        h.runAtTickTime(30, () -> {
            try {
                h.assertTrue(service.actorStates().fault(player.getUUID()) == null, "player still quarantined");
                service.actorStates().close();
                var saved = server.getDataStorage().computeIfAbsent(ActorSavedData.TYPE).restore();
                if (source != null) {
                    h.assertTrue(saved.capacityRecoveries().containsKey(player.getUUID()), "missing capacity reconciliation evidence");
                    h.assertTrue(saved.deliveries().size() < 100, "empty simulation history still full");
                }
                player.getInventory().setItem(3, new ItemStack(Items.IRON_SWORD));
                service.requestStart(player, UUID.randomUUID());
                UUID encounter = service.encounterOf(player.getUUID());
                h.assertTrue(encounter != null && service.state(encounter).members().containsKey(mob.getUUID()), "target not discovered");
                for (int n=0;n<service.state(encounter).members().size() && !player.getUUID().equals(service.state(encounter).current());n++)
                    service.endCurrentTurn(encounter,UUID.randomUUID(),service.state(encounter).version());
                var self = new ActionIntent.Target(ActionIntent.TargetKind.SELF,level.dimension().identifier().toString(),null,null,-1,0,0,0);
                var selected = service.tacticalActions().selectAndDiscover(player, new ActionProtocol.Query(
                        service.generation(), encounter, UUID.randomUUID(), self, ActionIntent.Hand.MAIN_HAND,3,1,null));
                h.assertTrue(selected.reason().isEmpty() && selected.abilities().stream().anyMatch(a -> a.id().equals("dndturn:melee")),
                        "action row missing after recovery: " + selected.reason());
                var target = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,level.dimension().identifier().toString(),mob.getUUID(),null,-1,0,0,0);
                var options = service.tacticalActions().selectAndDiscover(player,new ActionProtocol.Query(
                        service.generation(),encounter,UUID.randomUUID(),target,ActionIntent.Hand.MAIN_HAND,3,2,selected.item()));
                h.assertTrue(options.offers().stream().anyMatch(o -> o.intent().behaviorId().equals("dndturn:melee") && o.reason().isEmpty()),
                        "context menu missing after recovery: " + options.reason());
                h.assertTrue(service.state(encounter).members().get(player.getUUID()).action(), "queries charged action");
                System.out.println("ACTOR_HISTORY_QUERY_PASS restored=" + (source != null) + " deliveries=" + saved.deliveries().size()
                        + " ranges=" + saved.simulationArchive().size() + " recovery=" + saved.capacityRecoveries().get(player.getUUID()));
                h.succeed();
            } finally { cleanup.run(); }
        });
    }
    private static void codec(GameTestHelper h) {
        UUID actor = UUID.randomUUID(); var owner = new ActorStates();
        owner.restore(Map.of(actor, new ActorStates.State(1, ActorPersistentState.empty(), ActorRuntimeState.empty(),
                Map.of(EffectDefinition.Clock.SIMULATION_STEP,16384L))), List.of());
        var events = new ArrayList<ActorStates.ReactionDelivery>();
        for (int n=1;n<=16384;n++) {
            UUID id = UUID.nameUUIDFromBytes((actor + ":SIMULATION_STEP:" + n).getBytes(StandardCharsets.UTF_8));
            events.add(new ActorStates.ReactionDelivery(id,actor,List.of(new ActorActivations.Event(id,null,actor,null,null,
                    EffectDefinition.Clock.SIMULATION_STEP,n)),ActorStates.DeliveryStatus.COMPLETED));
        }
        owner.restoreDeliveries(events); owner.fault(actor,"ACTIVATION_FAILED:IllegalStateException");
        var data = new ActorSavedData(); data.update(owner); var restored = data.restore();
        h.assertTrue(restored.prepareCapacityRecovery()==16384,"completed clock history not compacted");
        data.update(restored); restored = data.restore();
        h.assertTrue(restored.reconcileCapacityFault(actor),"eligible inert actor not reconciled");
        data.update(restored); restored = data.restore();
        var first = events.getFirst();
        h.assertTrue(restored.registerReactions(first.root(),actor,first.events()).status()==ActorStates.DeliveryStatus.COMPLETED,
                "archived event replayed after codec roundtrip");
        h.assertTrue(restored.capacityRecoveries().containsKey(actor) && restored.faultReason(actor)==null,"recovery audit lost");
    }
}
