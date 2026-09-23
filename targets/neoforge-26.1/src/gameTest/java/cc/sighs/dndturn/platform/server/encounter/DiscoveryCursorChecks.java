package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterRegion;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.time.RoundTime;
import cc.sighs.dndturn.platform.server.action.AbilityWorkBudget;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.ai.MobTurnStrategies;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** The real discovery loop exceeds one actor's tick allowance and resumes through the same Decisions owner. */
public final class DiscoveryCursorChecks {
    public static void run(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(99001, 151, 1);
        var forced = new HashSet<Long>(); var entities = new ArrayList<Entity>();
        for (int x=(base.getX()-24)>>4; x<=(base.getX()+24)>>4; x++)
            for (int z=(base.getZ()-24)>>4; z<=(base.getZ()+24)>>4; z++) {
                level.getChunk(x,z); if(level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
            }
        // Do not insert the 81 actors into sections whose asynchronous entity load and
        // tracking transition has not completed. A fixed 30-tick delay is not I/O evidence.
        level.waitForEntities(ChunkPos.containing(base), 1);
        for (var pos : BlockPos.betweenClosed(base.offset(-8,-1,-8),base.offset(18,-1,18)))
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        var actor = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        actor.setNoAi(true); actor.setPos(base.getX()+.5,base.getY(),base.getZ()-3.5);
        actor.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40);
        level.addFreshEntity(actor); entities.add(actor);
        for (int i=0;i<80;i++) {
            var target = EntityType.COW.create(level,EntitySpawnReason.COMMAND);
            target.setNoAi(true); target.setPos(base.getX()+(i%10)*1.5,base.getY(),base.getZ()+(i/10)*1.5);
            level.addFreshEntity(target); entities.add(target);
        }
        Runnable cleanup = () -> {
            entities.forEach(Entity::discard);
            for(var key:forced){var c=ChunkPos.unpack(key);level.setChunkForced(c.x(),c.z(),false);} forced.clear();
        };
        h.runAtTickTime(99,cleanup);
        h.runAtTickTime(30, () -> {
            h.assertTrue(entities.stream().allMatch(entity -> level.getEntity(entity.getUUID()) == entity),
                "discovery fixture lost a current native entity before scanning");
            var engine = new EncounterAuthority(new Random(391),30,30);
            var id = UUID.randomUUID();
            var region = EncounterRegion.generate(level.dimension().identifier().toString(),
                new EncounterRegion.Discovery(base.getX()-24,140,-24,base.getX()+24,170,24),
                List.of(new EncounterRegion.Anchor(actor.getUUID(),new EncounterRegion.Point(base.getX(),151,0))),24,1);
            engine.beginCandidate(id,region,entities.stream().map(Entity::getUUID).collect(java.util.stream.Collectors.toSet()),new RoundTime(30));
            var decisions = new MobTurnStrategies.Decisions();
            var service = ServerRuntime.encounters(level.getServer());
            long before = service.abilityWorkCounts().get(AbilityWorkBudget.Work.CANDIDATE).consumed();
            // Source capture is now once per decision, so 80 targets no longer waste >1024 work.
            // Model earlier work by the same actor to retain the real cross-tick exhaustion test.
            for (int i=0;i<1000;i++) service.requireAbilityWork(actor.getUUID(), id,
                AbilityWorkBudget.Work.CANDIDATE);
            class Scan {
                int deferrals;
                void resume() {
                    try {
                        decisions.next(new LiveActorContext(actor),engine.stateView(id),engine);
                        long work = service.abilityWorkCounts().get(AbilityWorkBudget.Work.CANDIDATE).consumed()-before;
                        h.assertTrue(deferrals > 0 && work > 1024 && deferrals < 10,
                            "large discovery did not resume to completion: deferrals="+deferrals+" work="+work);
                        h.assertTrue(work < 1250, "source capture repeated per target after deferral: work="+work);
                        decisions.retain(Set.of()); cleanup.run(); h.succeed();
                    } catch (ActionFailure deferred) {
                        if (deferred.details().code() != ActionFailure.Code.EVALUATION_DEFERRED) throw deferred;
                        h.assertTrue(++deferrals < 10, "discovery repeatedly restarted instead of completing");
                        h.runAfterDelay(1,this::resume);
                    }
                }
            }
            new Scan().resume();
        });
    }
}
