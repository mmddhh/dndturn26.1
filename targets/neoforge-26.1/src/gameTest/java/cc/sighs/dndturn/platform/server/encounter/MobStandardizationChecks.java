package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.compat.ArcheryExtension;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.ActionTestAccess;
import cc.sighs.dndturn.platform.server.action.EquippedRanged;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.ai.AiTestAccess;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Real native launch through shared discovery/AI/PLAN and the normal environment projectile owner. */
public final class MobStandardizationChecks {
    public static void run(GameTestHelper h) { run(h, false, false, false, false); }
    public static void held(GameTestHelper h) { run(h, true, false, false, false); }
    public static void external(GameTestHelper h) { run(h, false, true, false, false); }
    public static void miss(GameTestHelper h) { run(h, false, false, true, false); }
    public static void automatic(GameTestHelper h) { run(h, false, false, false, true); }
    private static void run(GameTestHelper h, boolean held, boolean external, boolean miss, boolean automatic) {
        var level = h.getLevel(); var base = new BlockPos(automatic ? 216001 : miss ? 215001 : external ? 212001 : held ? 211001 : 210001, 185, 1);
        var forced = new HashSet<Long>();
        for (int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++) for(int z=-2;z<=2;z++) {
            level.getChunk(x,z); if(level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
        }
        level.waitForEntities(ChunkPos.containing(base),1);
        for(var pos:BlockPos.betweenClosed(base.offset(-7,-1,-7),base.offset(7,4,7)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY() || pos.getY()==base.getY()+4 ? Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=TestPlayers.survival(h);
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        player.connection.resetPosition();
        Mob mob=external ? EntityType.COW.create(level,EntitySpawnReason.COMMAND) : EntityType.SKELETON.create(level,EntitySpawnReason.COMMAND);
        if(external) mob.addTag(ArcheryExtension.GROUP);
        mob.setPos(base.getX()+2.5,base.getY(),base.getZ()+.5); mob.setNoAi(true); mob.setPersistenceRequired();
        mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
        if(held) mob.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.ARROW,7));
        level.addFreshEntity(mob);
        var service=ServerRuntime.encounters(level.getServer());
        boolean mobTarget = !held && !external && !miss && !automatic;
        Mob opponent = mobTarget ? EntityType.HUSK.create(level, EntitySpawnReason.COMMAND) : null;
        if (opponent != null) {
            opponent.setPos(base.getX() + .5, base.getY(), base.getZ() + 2.5);
            opponent.setNoAi(true); opponent.setPersistenceRequired(); level.addFreshEntity(opponent);
            mob.setTarget(opponent);
            h.assertTrue(service.encounterOf(mob.getUUID()) == null && service.encounterOf(opponent.getUUID()) == null,
                "ordinary world Mob aggression created an encounter");
            mob.setTarget(null);
        }
        LivingEntity selectedTarget = opponent == null ? player : opponent;
        Runnable cleanup=()->{
            UUID id=service.encounterOf(player.getUUID()); if(id!=null) service.lifecycle().stop(id);
            mob.discard(); player.discard(); if (opponent != null) opponent.discard();
            for(long key:forced){var c=ChunkPos.unpack(key);level.setChunkForced(c.x(),c.z(),false);} forced.clear();
        };
        h.runAtTickTime(299,cleanup);
        UUID[] operation={null}, encounter={null};
        h.startSequence().thenIdle(20).thenExecute(()->{
            // The field admits a mob with an actual directed lock; presence alone is not enough.
            mob.setTarget(player);
            service.lifecycle().requestStart(player,UUID.randomUUID());
        }).thenWaitUntil(()->{
            UUID id=service.encounterOf(mob.getUUID());h.assertTrue(id!=null,"Skeleton not discovered");
            var state=service.state(id);
            if(player.getUUID().equals(state.current())) service.combat().endTurn(id,player,UUID.randomUUID(),state.version());
            state=service.state(id);
            h.assertTrue(mob.getUUID().equals(state.current()) && state.members().get(mob.getUUID()).action(),"waiting for Skeleton turn");
        }).thenExecute(()->{
            var actor=service.actorStates().context(mob);var id=service.encounterOf(actor.id()); encounter[0]=id;
            if (opponent != null) {
                java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent> cancel = event -> {
                    if (event.getEntity() == mob && event.getNewAboutToBeSetTarget() == opponent) event.setCanceled(true);
                };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
                try {
                    mob.setTarget(opponent);
                    h.assertTrue(mob.getTarget() == player && service.encounterOf(opponent.getUUID()) == null,
                        "canceled native target admitted a neutral mob");
                } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel); }
                mob.setTarget(opponent);
                h.assertTrue(id.equals(service.encounterOf(opponent.getUUID())), "committed Mob target did not join");
                long relationVersion = service.state(id).version();
                mob.setTarget(opponent);
                h.assertTrue(service.state(id).version() == relationVersion, "same native target changed rule revision");
                service.useAttackRandomForGameTest(id, new Random(1) { @Override public int nextInt(int bound) { return 14; } });
            }
            var state=service.state(id);var target=new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,
                    level.dimension().identifier().toString(),selectedTarget.getUUID(),null,-1,0,0,0);
            var before=service.actorStates().state(actor.id());
            h.assertTrue(before.runtime().effects().values().stream().anyMatch(e->e.definition().equals(external
                    ? ArcheryExtension.EFFECT : AbilityAdapterRegistry.effects().require("dndturn:native_archery", 1))),"group effect not installed");
            if(automatic) { mob.setNoAi(false); return; }
            var found=cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor,state,target).stream().filter(o->o.binding().id().equals(EquippedRanged.ID)).findFirst().orElseThrow();
            h.assertTrue(found.availability()==cc.sighs.dndturn.platform.server.ability.AbilityQueries.Availability.EXECUTABLE,"bow unavailable: "+found);
            h.assertTrue(before.equals(service.actorStates().state(actor.id())),"discovery mutated effect state");
            mob.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TIPPED_ARROW));
            var unsupported=cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor,state,target).stream().filter(o->o.binding().id().equals(EquippedRanged.ID)).findFirst().orElseThrow();
            h.assertTrue(unsupported.availability()==cc.sighs.dndturn.platform.server.ability.AbilityQueries.Availability.RULE_BLOCKED,"special ammunition silently authorized");
            mob.setItemSlot(EquipmentSlot.OFFHAND,held?new ItemStack(Items.ARROW,7):ItemStack.EMPTY);
            found=cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor,state,target).stream().filter(o->o.binding().id().equals(EquippedRanged.ID)).findFirst().orElseThrow();
            var intent=found.binding().invocation(target);
            h.assertTrue(intent.source().kind()==GrantEvidence.Kind.EQUIPMENT && intent.source().grant()!=null,"bow lost equipment/effect evidence");
            h.assertTrue(intent.source().dependencies().get("ammunition").startsWith(held?"OFF_HAND:":"native:"),"incorrect ammunition source");
            var ai=AiTestAccess.capture(actor,state,null,ActionTestAccess.authority(service.actions()), service.mobDecisionFacts());
            h.assertTrue(AiPlanner.decide(ai) instanceof AiPlanner.Propose p && p.intent().behaviorId().equals(EquippedRanged.ID)
                    && p.intent().target().entity().equals(selectedTarget.getUUID()),"shared planner did not choose the committed ranged target: " + AiPlanner.decide(ai) + " preference=" + ai.definition().targetPreference());
            // Source replacement is rejected before any action or native side effect.
            mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STICK));
            try {ActionTestAccess.validate(service.actions(), mob,intent,state);throw new AssertionError("stale bow accepted");}
            catch(IllegalStateException expected) {}
            h.assertTrue(service.state(id).members().get(actor.id()).action(),"source rejection consumed action");
            mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
            try {ActionTestAccess.validate(service.actions(), mob,intent,state);throw new AssertionError("equal replacement reused old equipment generation");}
            catch(IllegalStateException expected) {}
            intent=cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor,state,target).stream().filter(o->o.binding().id().equals(EquippedRanged.ID))
                    .findFirst().orElseThrow().binding().invocation(target);
            operation[0]=UUID.randomUUID();
            if(miss) service.useAttackRandomForGameTest(id,new Random(1){@Override public int nextInt(int bound){return 0;}});
            service.actions().submit(actor,service.generation(),id,operation[0],state.version(),intent,false);
            var results=service.lifecycle().results(id,0,128).results();
            var result=results.stream().filter(r->r.snapshot().operationId().equals(operation[0])).findFirst().orElseThrow();
            h.assertTrue(result.outcome()==OperationRecord.Outcome.COMPLETED,"native launch failed: "+results);
            var checkpoint=service.abilityCheckpoint(operation[0]);
            h.assertTrue(checkpoint.invocation().source().equals(intent.source()) && checkpoint.observed().spawned().size()==1,
                    "checkpoint lost policy/source/native spawn evidence");
            h.assertTrue(checkpoint.before().items().equals(checkpoint.observed().items()),"zero native item consumption not observed");
            h.assertTrue(!service.state(id).members().get(actor.id()).action(),"launch did not spend action");
            h.assertTrue(mob.getMainHandItem().getDamageValue()==0 && (!held || mob.getOffhandItem().getCount()==7),"native effect consumed equipment");
            var arrows=level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,mob.getBoundingBox().inflate(8));
            h.assertTrue(arrows.size()==1,"native launch did not create one arrow");
            int count=results.size();
            service.actions().submit(actor,service.generation(),id,operation[0],state.version(),intent,false);
            h.assertTrue(service.lifecycle().results(id,0,128).results().size()==count,"duplicate launch repeated");
            var effects=service.actorStates().state(actor.id());
            service.actorStates().command(actor,new ActorStates.Command(UUID.randomUUID(),actor.id(),effects.revision(),
                    List.of(new ActorStates.Remove(intent.source().grant()))));
            h.assertTrue(AbilityAdapterRegistry.facts(EquippedRanged.ID).checkedSources(actor).isEmpty(),"revoked effect still grants bow");
            service.combat().endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
        }).thenWaitUntil(()->{
            var id=encounter[0];var state=service.state(id);
            if(automatic && operation[0]==null) {
                var root=service.lifecycle().results(id,0,128).results().stream().filter(r->r.snapshot().intent()!=null
                        && r.snapshot().intent().behaviorId().equals(EquippedRanged.ID) && r.terminal()).findFirst();
                h.assertTrue(root.isPresent(),"waiting for automatic registered AI shot");
                h.assertTrue(root.get().outcome()==OperationRecord.Outcome.COMPLETED,"automatic AI shot failed: "+root);
                operation[0]=root.get().snapshot().operationId();
                h.assertTrue(!state.members().get(mob.getUUID()).action(),"automatic shot did not consume action");
            }
            var projectile=service.lifecycle().results(id,0,128).results().stream().filter(r->r.damageTrace()!=null
                    && r.snapshot().source()!=null && !r.snapshot().source().equals(mob.getUUID())).findFirst();
            if(projectile.isEmpty() && state.current()!=null && state.phase()!=EncounterPhase.ENVIRONMENT
                    && (!automatic || player.getUUID().equals(state.current())))
                service.combat().endCurrentTurn(id,UUID.randomUUID(),state.version());
            h.assertTrue(projectile.isPresent(),"waiting for native arrow contact and tactical result");
            h.assertTrue(projectile.get().snapshot().target().equals(selectedTarget.getUUID()), "projectile retargeted");
            if (opponent != null) h.assertTrue(projectile.get().actualDamage() > 0 && opponent.getHealth() < opponent.getMaxHealth(),
                "Mob arrow did not cause observed damage");
            if(miss) h.assertTrue(!projectile.get().damageTrace().hit() && projectile.get().actualDamage()==0,"natural one did not preserve miss");
            System.out.println("MOB_STANDARDIZATION held="+held+" outcome="+projectile.get().outcome()+" trace="+projectile.get().damageTrace());
        }).thenWaitUntil(() -> {
            if (opponent == null) return;
            var id = encounter[0]; var state = service.state(id);
            if (!opponent.getUUID().equals(state.current()) && state.phase() != EncounterPhase.ENVIRONMENT)
                service.combat().endCurrentTurn(id, UUID.randomUUID(), state.version());
            h.assertTrue(opponent.getUUID().equals(service.state(id).current()), "waiting for Mob retaliation turn");
        }).thenExecute(() -> {
            if (opponent == null) return;
            var id = encounter[0];
            opponent.setPos(mob.getX() - 1, mob.getY(), mob.getZ());
            opponent.setTarget(mob);
            var actor = service.actorStates().context(opponent);
            var target = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, level.dimension().identifier().toString(),
                mob.getUUID(), null, -1, 0, 0, 0);
            var state = service.state(id);
            var option = cc.sighs.dndturn.platform.server.ability.AbilityQueries.forActor(actor).discover(actor, state, target).stream()
                .filter(o -> o.binding().id().equals("dndturn:intrinsic_melee")).findFirst().orElseThrow();
            h.assertTrue(option.availability() == cc.sighs.dndturn.platform.server.ability.AbilityQueries.Availability.EXECUTABLE,
                "Mob melee retaliation unavailable: " + option);
            var intent = option.binding().invocation(target); UUID operationId = UUID.randomUUID();
            float health = mob.getHealth();
            service.actions().submit(actor, service.generation(), id, operationId, state.version(), intent, false);
            h.assertTrue(mob.getHealth() < health && !service.state(id).members().get(opponent.getUUID()).action(),
                "Mob melee failed to apply damage and consume one action");
            float after = mob.getHealth();
            service.actions().submit(actor, service.generation(), id, operationId, state.version(), intent, false);
            h.assertTrue(mob.getHealth() == after, "duplicate Mob melee applied damage twice");
        }).thenExecute(()->{
            service.lifecycle().stop(encounter[0]);
            h.assertTrue(service.actorStates().state(mob.getUUID()).runtime().effects().values().stream()
                    .noneMatch(e->e.definition().equals(external?ArcheryExtension.EFFECT:AbilityAdapterRegistry.effects().require("dndturn:native_archery", 1))),
                    "encounter exit retained group-owned archery effect");
            cleanup.run();
        }).thenSucceed();
    }
}
