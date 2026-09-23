package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** In the normal suite, validates deferred delivery. Selected persistent runs verify a separate JVM. */
public final class EffectReactionRestartChecks {
    public static void run(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var service = ServerRuntime.encounters(server);
        var actors = service.actorStates();
        if (!"dndturn:effect_reaction_restart".equals(System.getenv("DNDTURN_RESTART_TEST"))) {
            var body = helper.spawn(EntityType.COW, 1, 2, 1);
            var actor = new LiveActorContext(body);
            var event = EffectReactionChecks.prepare(actors, actor);
            var before = actors.state(actor.id());
            actors.deferEvent(actor, event);
            helper.assertTrue(actors.state(actor.id()).equals(before), "deferred event executed before dispatch");
            actors.dispatch(actor, event);
            EffectReactionChecks.verify(helper, actors, actor);
            body.discard(); helper.succeed(); return;
        }
        var level = helper.getLevel();
        var pos = new BlockPos(91001, 151, 1);
        var forced = new ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x = (pos.getX() - 24) >> 4; x <= (pos.getX() + 24) >> 4; x++)
            for (int z = (pos.getZ() - 24) >> 4; z <= (pos.getZ() + 24) >> 4; z++) {
                level.getChunk(x, z);
                if (level.setChunkForced(x, z, true)) forced.add(new net.minecraft.world.level.ChunkPos(x, z));
            }
        var loader = TestPlayers.survival(helper);
        loader.teleportTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + .5);
        Path marker = server.getWorldPath(LevelResource.ROOT).resolve("dndturn-effect-restart.properties");
        var properties = new Properties();
        try {
            if (Files.exists(marker)) try (var input = Files.newInputStream(marker)) { properties.load(input); }
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(pos),
                "persistent effect fixture chunk is not entity-ticking")).thenExecute(() -> {
            if (properties.isEmpty()) {
                for (var floor : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, -1, 3)))
                    level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                var body = helper.spawn(EntityType.COW, 1, 2, 1);
                body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                var actor = new LiveActorContext(body);
                var event = EffectReactionChecks.prepare(actors, actor);
                actors.deferEvent(actor, event);
                actors.close(); // Capture the pending inbox; only normal server shutdown supplies disk evidence.
                properties.setProperty("actor", actor.id().toString());
                properties.setProperty("event", event.id().toString());
                properties.setProperty("generation", service.generation().toString());
                try (var output = Files.newOutputStream(marker)) { properties.store(output, "Pending pure effect delivery; verify in next JVM"); }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                server.tickRateManager().setFrozen(true); // Keep the accepted pure event pending until normal shutdown.
                System.out.println("DNDTURN_EFFECT_RESTART_WRITE_READY actor=" + actor.id() + " root=" + ActorActivations.root(event));
                helper.succeed();
            } else {
                UUID actorId = UUID.fromString(properties.getProperty("actor"));
                UUID eventId = UUID.fromString(properties.getProperty("event"));
                helper.assertFalse(service.generation().toString().equals(properties.getProperty("generation")), "server generation survived restart");
                helper.startSequence().thenWaitUntil(() -> {
                    helper.assertTrue(level.getEntity(actorId) instanceof LivingEntity, "saved actor not loaded after restart");
                    var actor = new LiveActorContext((LivingEntity)level.getEntity(actorId));
                    EffectReactionChecks.verify(helper, actors, actor);
                }).thenExecute(() -> {
                    var actor = new LiveActorContext((LivingEntity)level.getEntity(actorId));
                    var before = actors.state(actorId);
                    actors.dispatch(actor, new ActorActivations.Event(eventId, null, actorId, null, ActivationSpec.Event.ABILITY_USED, null, 0));
                    helper.assertTrue(actors.state(actorId).equals(before), "completed delivery replayed after restart");
                    System.out.println("DNDTURN_EFFECT_RESTART_VERIFY_PASS actor=" + actorId);
                    for (var chunk : forced) level.setChunkForced(chunk.x(), chunk.z(), false);
                    helper.succeed();
                });
            }
        });
    }
}
