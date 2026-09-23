package example.compat.dndturn;

import cc.sighs.dndturn.combat.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Compiled in its own source set against public production API only. */
public final class ExternalMeleeChecks {
    private static final String TAG = "dndturn_external_melee";
    private static final String ID = "compat:bounded_melee";
    private static TacticalExecution retained;
    private static int releases;
    public static void register() {
        MeleeAdapters.server().register(new MeleeAdapters.Attacker("compat:sheep", 1,
            e -> e.getType() == EntityType.SHEEP && e.entityTags().contains(TAG), e -> 2));
        TacticalCapabilities.register(new TacticalAdapter(ID, 1, "External melee", TacticalIntent.Capability.ATTACK,
            Set.of(TacticalIntent.TargetKind.ENTITY)) {
            public AbilitySource source(TacticalActor actor, TacticalIntent.Hand hand) {
                return actor.body().getType() == EntityType.SHEEP && actor.body().entityTags().contains(TAG)
                    ? AbilitySource.intrinsic("compat:sheep", 1, actor.id(), actor.instance()) : null;
            }
            public String unavailable(TacticalActor actor, TacticalIntent intent, CombatEngine.StateView state) {
                return actor.level().getEntity(intent.target().entity()) instanceof ServerPlayer
                    && state.members().containsKey(intent.target().entity()) ? null : "requires member player";
            }
            public boolean canExecute(TacticalActor actor, TacticalIntent intent, Vec3 feet) {
                var target = actor.level().getEntity(intent.target().entity());
                return target != null && feet.distanceToSqr(target.position()) <= 2;
            }
            public MeleeEffects meleeEffects(TacticalActor actor, boolean knockback) {
                return new MeleeEffects(2, false, true, false);
            }
            public void prepare(TacticalActor actor, TacticalExecution execution) {
                mustReject(execution::melee, "prepare acquired world effect authority");
                if (!execution.state().isEmpty()) throw new AssertionError("new plan inherited hosted state");
                execution.put("prepared", "yes");
            }
            public void start(TacticalActor actor, TacticalExecution execution) {
                if (!"yes".equals(execution.state().get("prepared"))) throw new AssertionError("hosted state lost between callbacks");
                retained = execution;
                execution.put("effect", "invoked");
                execution.melee();
                mustReject(execution::melee, "terminal context repeated world effect");
            }
            public void tick(TacticalActor actor, TacticalExecution execution) { throw new AssertionError("synchronous effect ticked twice"); }
            public void release(TacticalActor actor, TacticalExecution execution) {
                mustReject(execution::melee, "release acquired world effect authority");
                if (!"invoked".equals(execution.state().get("effect"))) throw new AssertionError("release lost hosted state");
                releases++;
                if (!execution.state().containsKey("release_attempted")) {
                    execution.put("release_attempted", "yes");
                    throw new IllegalStateException("injected first release failure");
                }
            }
        });
    }
    private static void mustReject(Runnable action, String message) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
    /** Player creation and encounter request belong to the harness, never to the external adapter. */
    public static void run(GameTestHelper h, ServerPlayer player) {
        var level = h.getLevel(); var base = h.absolutePos(new BlockPos(113001, 181, 1));
        var service = ServerCombatService.forServer(level.getServer());
        var forced = new HashSet<Long>();
        for (int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++)
            for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++) {
                level.getChunk(x,z); if(level.setChunkForced(x,z,true)) forced.add(new net.minecraft.world.level.ChunkPos(x,z).pack());
            }
        for(var pos:BlockPos.betweenClosed(base.offset(-2,-1,-2),base.offset(3,3,2)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var mob=EntityType.SHEEP.create(level,EntitySpawnReason.COMMAND);
        mob.addTag(TAG); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.setPos(base.getX()+1.5,base.getY(),base.getZ()+.5); level.addFreshEntity(mob);
        Runnable cleanup=()->{
            var id=service.encounterOf(player.getUUID()); if(id!=null) service.stop(id); mob.discard();
            for(var key:forced){var chunk=net.minecraft.world.level.ChunkPos.unpack(key);level.setChunkForced(chunk.x(),chunk.z(),false);}
            forced.clear(); retained=null;
        };
        h.runAtTickTime(119,cleanup);
        h.runAtTickTime(30,()->{
            try {
                releases=0; service.requestStart(player,UUID.randomUUID()); var id=service.encounterOf(player.getUUID());
                h.assertTrue(id!=null && id.equals(service.encounterOf(mob.getUUID())),"external fixture membership");
                if(!mob.getUUID().equals(service.state(id).current())) service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
                service.useAttackRandomForGameTest(id,new Random(517){ public int nextInt(int bound){return 9;} });
                var actor=new TacticalActor(mob); var behavior=TacticalCapabilities.all().stream().filter(b->b.id().equals(ID)).findFirst().orElseThrow();
                var intent=new TacticalIntent(ID,1,TacticalIntent.Capability.ATTACK,new TacticalIntent.Target(
                    TacticalIntent.TargetKind.ENTITY,service.state(id).region().dimension(),player.getUUID(),null,-1,0,0,0),behavior.source(actor,null));
                var operation=UUID.randomUUID(); long version=service.state(id).version(); float health=player.getHealth();
                service.tacticalActions().submit(actor,service.generation(),id,operation,version,intent,false);
                h.assertTrue(player.getHealth()==health-2 && releases==1,"external port did not damage/release exactly once");
                h.assertTrue(!service.state(id).members().get(mob.getUUID()).action(),"external port bypassed action cost");
                h.assertTrue(service.tacticalActions().controlFault(mob.getUUID()),"release failure did not isolate adapter control");
                mustReject(()->retained.state(),"retained callback remained active");
                service.tacticalActions().submit(actor,service.generation(),id,operation,version,intent,false);
                h.assertTrue(player.getHealth()==health-2 && releases==1,"retry reran external effect or release");
                h.assertTrue(service.results(id,0,64).results().stream().anyMatch(r->r.snapshot().operationId().equals(operation)
                    && r.terminal() && r.outcome()==OperationRecord.Outcome.COMPLETED),"external root missing canonical terminal");
                var terminal = service.results(id,0,64).results().stream().filter(r->r.snapshot().operationId().equals(operation)).findFirst().orElseThrow();
                h.assertTrue(terminal.conclusion().release()==ExecutionConclusion.Release.FAILED,"release failure erased confirmed effect evidence");
                h.startSequence().thenWaitUntil(()->h.assertTrue(!service.tacticalActions().controlFault(mob.getUUID()),
                    "waiting for bounded control cleanup retry")).thenExecute(()->{
                    try {
                        h.assertTrue(releases==2 && player.getHealth()==health-2,"cleanup retried the world effect or lost hosted state");
                        h.assertTrue(service.tacticalActions().releaseReconciliation(operation)!=null,"successful cleanup has no reconciliation evidence");
                        h.assertTrue(service.results(id,0,64).results().contains(terminal),"cleanup rewrote the original canonical result");
                    } finally { cleanup.run(); }
                }).thenSucceed();
            } catch(RuntimeException|Error failure){cleanup.run();throw failure;}
        });
    }
}
