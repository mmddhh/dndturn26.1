package cc.sighs.dndturn.platform.server.builtin.creeper;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.ai.AiDecisionContext;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.domain.encounter.operation.WorldOutcomeObservation;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.server.action.ActionTestAccess;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import cc.sighs.dndturn.platform.server.ai.AiTestAccess;
import cc.sighs.dndturn.platform.server.persistence.ActorSavedData;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Production registrations, real PLAN charge, native explosion and native virtual damage entry. */
public final class CreeperEffectChecks {
    public static void run(GameTestHelper h) {
        var marker = h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("dndturn-trigger-restart.properties");
        boolean persistent = "dndturn:creeper_effect".equals(System.getenv("DNDTURN_RESTART_TEST"));
        if (persistent && Files.exists(marker)) { verifyRestart(h, marker); return; }
        var level = h.getLevel(); var base = h.absolutePos(new BlockPos(175001, 185, 1));
        var forced = new HashSet<Long>();
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++)
            for (int z = (base.getZ()-24)>>4; z <= (base.getZ()+24)>>4; z++) {
                level.getChunk(x,z); if (level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
            }
        level.waitForEntities(ChunkPos.containing(base), 1);
        for (var p : BlockPos.betweenClosed(base.offset(-6,-1,-6),base.offset(6,4,6)))
            level.setBlockAndUpdate(p,p.getY() < base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        var player = TestPlayers.survival(h);
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(100);
        player.setAbsorptionAmount(100);
        var creeper = EntityType.CREEPER.create(level,EntitySpawnReason.COMMAND);
        creeper.setPos(base.getX()+2.5,base.getY(),base.getZ()+.5); creeper.setNoAi(true); creeper.setPersistenceRequired(); level.addFreshEntity(creeper);
        var service = ServerRuntime.encounters(level.getServer());
        Runnable cleanup = () -> {
            var id = service.encounterOf(player.getUUID()); if (id != null) service.stop(id);
            creeper.discard(); player.discard();
            for (long key : forced) { var c = ChunkPos.unpack(key); level.setChunkForced(c.x(),c.z(),false); } forced.clear();
        };
        h.runAtTickTime(399,cleanup);
        h.startSequence().thenIdle(20).thenExecute(() -> service.requestStart(player,UUID.randomUUID())).thenWaitUntil(() -> {
            var id = service.encounterOf(creeper.getUUID()); h.assertTrue(id != null,"Creeper membership missing");
            var state = service.state(id);
            if (player.getUUID().equals(state.current())) service.endTurn(id,player,UUID.randomUUID(),state.version());
            state = service.state(id);
            h.assertTrue(creeper.getUUID().equals(state.current()) && state.members().get(creeper.getUUID()).action(),"waiting for Creeper turn");
        }).thenExecute(() -> {
            var actor = new LiveActorContext(creeper); var states = service.actorStates(); var id = service.encounterOf(actor.id());
            var state = states.state(actor.id());
            // Arrange two prior charges; the third must use the normal paid PLAN entry.
            var old = CreeperAbilities.fuse(state);
            if (old == null) states.command(actor,new ActorStates.Command(UUID.randomUUID(),actor.id(),state.revision(),List.of(
                    new ActorStates.Apply(new EffectInstance(UUID.randomUUID(),UUID.randomUUID(),CreeperAbilities.DEFINITION,2,0,state.revision()+1,1,actor.id(),CreeperAbilities.CHARGE)))));
            else if (old.stacks() < 2) states.command(actor,new ActorStates.Command(UUID.randomUUID(),actor.id(),state.revision(),List.of(new ActorStates.AddStacks(old.id(),2-old.stacks()))));
            var binding = MinecraftSnapshotCapture.capture(actor,service.state(id)).abilities().stream().filter(b -> b.id().equals(CreeperAbilities.CHARGE)).findFirst().orElseThrow();
            var ai = AiTestAccess.capture(actor,service.state(id),null,ActionTestAccess.authority(service.tacticalActions()));
            h.assertTrue(AiPlanner.decide(ai) instanceof AiPlanner.Propose proposal && proposal.intent().behaviorId().equals(CreeperAbilities.CHARGE),
                    "production AI did not propose charge from the effect snapshot");
            var intent = binding.invocation(new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,level.dimension().identifier().toString(),player.getUUID(),null,-1,0,0,0));
            UUID operation = UUID.randomUUID(); long version = service.state(id).version();
            service.tacticalActions().submit(actor,service.generation(),id,operation,version,intent,false);
            h.assertTrue(CreeperAbilities.fuse(states.state(actor.id())).stacks() == 3,"third charge did not commit");
            h.assertTrue(!service.state(id).members().get(actor.id()).action(),"charge did not consume one action");
            h.assertTrue(creeper.isAlive() && !creeper.isRemoved(),"threshold exploded before TURN_END");
            var fullSnapshot = MinecraftSnapshotCapture.capture(actor,service.state(id));
            var fullAi = new AiDecisionContext(fullSnapshot,ai.definition(),ai.phase(),false,ai.dimension(),ai.perception(),ai.hostile(),ai.options(),ai.movementOpportunity(),ai.runtime());
            h.assertTrue(AiPlanner.decide(fullAi) instanceof AiPlanner.EndTurn,"full Fuse did not request settlement");
            var invocation = states.invocations(actor.id()).stream().filter(i -> i.invocation().ability().equals(CreeperAbilities.EXPLODE)).toList();
            h.assertTrue(invocation.size() == 1 && invocation.getFirst().status() == TriggeredExecutionRecord.Status.PENDING,"threshold emission missing/duplicated: " + invocation);
            service.tacticalActions().submit(actor,service.generation(),id,operation,version,intent,false);
            h.assertTrue(states.invocations(actor.id()).size() == 1,"charge replay duplicated emission");
            float absorption = player.getAbsorptionAmount();
            var immune = EntityType.COW.create(level,EntitySpawnReason.COMMAND);
            immune.setPos(base.getX()+2.5,base.getY(),base.getZ()+2.5); immune.setNoAi(true); immune.setInvulnerable(true); level.addFreshEntity(immune);
            service.actionHost().joinGeneratedMob(player,immune,id);
            var cancelled = EntityType.COW.create(level,EntitySpawnReason.COMMAND);
            cancelled.setPos(base.getX()+3.5,base.getY(),base.getZ()+1.5); cancelled.setNoAi(true); level.addFreshEntity(cancelled);
            service.actionHost().joinGeneratedMob(player,cancelled,id);
            var outside = EntityType.COW.create(level,EntitySpawnReason.COMMAND);
            outside.setPos(base.getX()+2.5,base.getY(),base.getZ()-1.5); outside.setNoAi(true); level.addFreshEntity(outside);
            float outsideHealth = outside.getHealth(); var outsideMotion = outside.getDeltaMovement();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> cancel = e -> {
                if (e.getEntity() == cancelled) e.setCanceled(true);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
            creeper.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED,120));
            try { service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version()); }
            finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
            h.assertTrue(creeper.isRemoved(),"Creeper native explosion did not remove source: " + states.invocations(actor.id()));
            h.assertTrue(player.getAbsorptionAmount() < absorption,"native explosion damage/absorption not observed");
            h.assertTrue(states.invocations(actor.id()).getFirst().status() == TriggeredExecutionRecord.Status.COMPLETED,"trigger did not complete");
            var observation = states.invocations(actor.id()).getFirst().observation();
            h.assertTrue(observation != null && observation.sourceRemoved() && !observation.damage().isEmpty(),"native observations missing");
            for (var unaffected : List.of(immune,cancelled))
                h.assertTrue(observation.damage().stream().anyMatch(d -> d.target().equals(unaffected.getUUID()) && d.known()
                        && !d.accepted() && d.healthBefore() == d.healthAfter()),"immunity/cancellation lost native observation");
            h.assertTrue(outside.getHealth() == outsideHealth && outside.getDeltaMovement().equals(outsideMotion)
                    && observation.rejectedEntities() > 0,"out-of-domain explosion affected nonmember");
            immune.discard(); cancelled.discard(); outside.discard();
            var cloudId = observation.spawns().stream().filter(s -> s.type().equals("minecraft:area_effect_cloud")).map(WorldOutcomeObservation.Spawn::entity).findFirst().orElseThrow();
            var cloud = level.getEntity(cloudId);
            h.assertTrue(cloud != null && CreeperClouds.confirmed(cloud,service),"cloud causal evidence missing");
            String sourceDomain = cloud.getPersistentData().getStringOr("dndturn:creeper_domain", "");
            try {
                for (String invalidDomain : List.of("malformed", UUID.randomUUID().toString())) {
                    cloud.getPersistentData().putString("dndturn:creeper_domain", invalidDomain);
                    h.assertTrue(!CreeperClouds.confirmed(cloud,service) && service.isEntitySimulationPaused(cloud),
                        "cloud acquired simulation from invalid causal domain");
                    h.assertTrue(CreeperClouds.targets((net.minecraft.world.entity.AreaEffectCloud)cloud,List.of(player)).isEmpty(),
                        "cloud acquired targets from invalid causal domain");
                    h.assertTrue(cloud.getPersistentData().getStringOr("dndturn:creeper_domain", "").equals(invalidDomain),
                        "pure cloud query rewrote evidence");
                }
            } finally { cloud.getPersistentData().putString("dndturn:creeper_domain", sourceDomain); }
            h.assertTrue(CreeperClouds.targets((net.minecraft.world.entity.AreaEffectCloud)cloud,List.of(player)).isEmpty(),
                    "cloud applied effects outside its authorized environment step");
            if (persistent) {
                var properties = new Properties();
                properties.setProperty("generation",service.generation().toString());
                properties.setProperty("actor",actor.id().toString()); properties.setProperty("completed",invocation.getFirst().operation().toString());
                properties.setProperty("cloud",cloudId.toString());
                properties.setProperty("x",Integer.toString(base.getX())); properties.setProperty("y",Integer.toString(base.getY())); properties.setProperty("z",Integer.toString(base.getZ()));
                for (String window : List.of("pending","started")) {
                    var survivor = EntityType.CREEPER.create(level,EntitySpawnReason.COMMAND);
                    survivor.setPos(base.getX()+4.5,base.getY(),base.getZ()+4.5); survivor.setNoAi(true); survivor.setPersistenceRequired(); level.addFreshEntity(survivor);
                    var survivorActor = new LiveActorContext(survivor); var prior = states.state(survivor.getUUID());
                    states.command(survivorActor,new ActorStates.Command(UUID.randomUUID(),survivor.getUUID(),prior.revision(),List.of(new ActorStates.Apply(
                            new EffectInstance(UUID.randomUUID(),UUID.randomUUID(),CreeperAbilities.DEFINITION,3,0,prior.revision()+1,1,survivor.getUUID(),CreeperAbilities.CHARGE)))));
                    var emission = states.invocations(survivor.getUUID()).getFirst();
                    if (window.equals("started")) states.invocationStatus(emission.operation(),TriggeredExecutionRecord.Status.STARTED,"injected interruption after admission");
                    properties.setProperty(window+"Actor",survivor.getUUID().toString()); properties.setProperty(window,emission.operation().toString());
                }
                states.close();
                try (var output = Files.newOutputStream(marker)) { properties.store(output,"Trigger recovery boundaries; STARTED is an injected fault window"); }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                level.getServer().tickRateManager().setFrozen(true);
                System.out.println("DNDTURN_TRIGGER_RESTART_WRITE_READY completed=" + invocation.getFirst().operation());
                return;
            }
            states.close();
            var restored = level.getServer().getDataStorage().computeIfAbsent(ActorSavedData.TYPE).restore();
            h.assertTrue(restored.invocation(invocation.getFirst().operation()).status() == TriggeredExecutionRecord.Status.COMPLETED,
                    "trigger checkpoint lost completion evidence");
            h.assertTrue(CreeperAbilities.fuse(states.state(actor.id())) == null,"fuse not consumed");
            cleanup.run();
        }).thenSucceed();
    }
    private static void verifyRestart(GameTestHelper h, Path marker) {
        var properties = new Properties();
        try (var input = Files.newInputStream(marker)) { properties.load(input); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        var level = h.getLevel(); var service = ServerRuntime.encounters(level.getServer()); var states = service.actorStates();
        var base = new BlockPos(Integer.parseInt(properties.getProperty("x")),Integer.parseInt(properties.getProperty("y")),Integer.parseInt(properties.getProperty("z")));
        var chunk = ChunkPos.containing(base); level.getChunk(chunk.x(),chunk.z()); level.setChunkForced(chunk.x(),chunk.z(),true); level.waitForEntities(chunk,1);
        var loader = TestPlayers.survival(h); loader.setPos(base.getX()+.5,base.getY()+4,base.getZ()+.5);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getEntity(UUID.fromString(properties.getProperty("cloud"))) != null,"saved cloud not loaded"))
                .thenExecute(() -> {
                    h.assertFalse(service.generation().toString().equals(properties.getProperty("generation")),"server generation reused");
                    for (String window : List.of("pending","started","completed")) {
                        UUID actor = UUID.fromString(properties.getProperty(window.equals("completed") ? "actor" : window+"Actor"));
                        UUID operation = UUID.fromString(properties.getProperty(window));
                        var emission = states.invocations(actor).stream().filter(i -> i.operation().equals(operation)).findFirst().orElseThrow();
                        var expected = window.equals("pending") ? TriggeredExecutionRecord.Status.PENDING : window.equals("started") ? TriggeredExecutionRecord.Status.UNKNOWN : TriggeredExecutionRecord.Status.COMPLETED;
                        h.assertTrue(emission.status() == expected,"restart window " + window + " lost/replayed: " + emission.status());
                        if (window.equals("completed")) h.assertTrue(emission.observation() != null && emission.observation().sourceRemoved(),"native completion observation lost");
                    }
                    var cloud = level.getEntity(UUID.fromString(properties.getProperty("cloud")));
                    h.assertTrue(CreeperClouds.confirmed(cloud,service),"saved cloud lost its confirmed source");
                    h.assertTrue(level.getEntity(UUID.fromString(properties.getProperty("actor"))) == null,"exploded source returned after restart");
                    System.out.println("DNDTURN_TRIGGER_RESTART_VERIFY_PASS pending=PENDING started=UNKNOWN completed=COMPLETED");
                    loader.discard(); level.setChunkForced(chunk.x(),chunk.z(),false);
                }).thenSucceed();
    }
}
