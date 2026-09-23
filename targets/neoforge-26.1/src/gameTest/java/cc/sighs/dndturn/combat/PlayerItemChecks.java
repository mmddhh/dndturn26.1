package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

public final class PlayerItemChecks {
    public static void projectiles(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(158001,151,1));
        Set<Long> forced=new HashSet<>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++)for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++) {
            level.getChunk(x,z);if(level.setChunkForced(x,z,true))forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
        }
        for(var pos:BlockPos.betweenClosed(base.offset(-12,-1,-12),base.offset(12,6,12)))
            level.setBlockAndUpdate(pos,pos.getY()==base.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var p=cc.sighs.dndturn.gametest.TestPlayers.survival(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);p.setOnGround(true);
        var service=ServerCombatService.forServer(level.getServer());
        Runnable cleanup=() -> {
            var id=service.encounterOf(p.getUUID());if(id!=null)service.stop(id);
            for(var entity:level.getEntities(p,p.getBoundingBox().inflate(16)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
            for(long k:forced){var c=net.minecraft.world.level.ChunkPos.unpack(k);level.setChunkForced(c.x(),c.z(),false);}forced.clear();
        };
        h.runAtTickTime(299,cleanup);
        h.runAfterDelay(4,() -> {try {
            var request=submit(p,service,"dndturn:experience_bottle",new ItemStack(Items.EXPERIENCE_BOTTLE,2),base.offset(3,-1,0));
            var evidence=service.abilityCheckpoint(request.operation());
            h.assertTrue(evidence!=null && evidence.observed()!=null && evidence.observed().spawned().size()==1,"throw did not capture projectile: "+service.results(request.encounter(),0,16).results());
            UUID generated=evidence.observed().spawned().getFirst().entity();
            h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getEntity(generated)!=null,"waiting for projectile entity lookup"))
                .thenExecute(() -> {
            var projectile=level.getEntity(generated);var start=projectile.position();
            h.runAfterDelay(100,() -> {
                if (!projectile.isRemoved()) h.fail("bottle did not impact within environment: position="+projectile.position()
                    +" velocity="+projectile.getDeltaMovement()+" quarantined="+service.isProjectileQuarantined(projectile.getUUID()));
            });
            h.assertTrue(service.isEntitySimulationPaused(projectile),"new projectile has no environment ownership");
            h.startSequence().thenIdle(5).thenExecute(() -> {
                h.assertTrue(projectile.position().equals(start),"projectile advanced during member turn");
                service.endCurrentTurn(request.encounter(),UUID.randomUUID(),service.state(request.encounter()).version());
            }).thenWaitUntil(() -> h.assertTrue(projectile.isRemoved(),"experience bottle did not complete native collision"))
              .thenExecute(() -> {
                var results=service.results(request.encounter(),0,64).results();
                h.assertTrue(results.stream().anyMatch(r -> r.snapshot().source()!=null && r.snapshot().source().equals(projectile.getUUID())
                    && r.snapshot().kind()==OperationRecord.Kind.ENVIRONMENT && r.outcome()==OperationRecord.Outcome.COMPLETED),"missing causal native impact: "+results);
                h.assertTrue(!level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,p.getBoundingBox().inflate(12)).isEmpty(),"native XP effect missing");
                service.tacticalActions().request(p,request);
                h.assertTrue(p.getMainHandItem().getCount()==1,"launch retry consumed another bottle");
                restart(p,service);
                var cast=submit(p,service,"dndturn:fishing_rod",new ItemStack(Items.FISHING_ROD),base.offset(3,-1,0));
                h.assertTrue(p.fishing!=null,"native fishing cast failed: "+service.results(cast.encounter(),0,16).results());
                var hook=p.fishing;
                h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getEntity(hook.getUUID())==hook,"waiting for hook lookup"))
                    .thenExecute(() -> service.endCurrentTurn(cast.encounter(),UUID.randomUUID(),service.state(cast.encounter()).version()))
                    .thenWaitUntil(() -> h.assertTrue(p.getUUID().equals(service.state(cast.encounter()).current())
                        && service.state(cast.encounter()).members().get(p.getUUID()).action(),"waiting for next fishing action"))
                    .thenExecute(() -> {
                        var target=new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY,p.level().dimension().identifier().toString(),hook.getUUID(),null,-1,0,0,0);
                        submit(p,service,"dndturn:reel",p.getMainHandItem(),TacticalIntent.Capability.USE_ITEM,target);
                        h.assertTrue(p.fishing==null && hook.isRemoved(),"reel did not remove native hook");
                        h.assertTrue(p.getUUID().equals(service.state(cast.encounter()).current()),"reel ended player turn");
                        cleanup.run();
                    }).thenSucceed();
              });
                });
        }catch(RuntimeException|AssertionError failure){cleanup.run();throw failure;} });
    }
    public static void run(GameTestHelper h) {
        var level=h.getLevel(); var base=h.absolutePos(new BlockPos(156001,151,1));
        Set<Long> forced=new HashSet<>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++) for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++) {
            level.getChunk(x,z); if(level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
        }
        for(var pos:BlockPos.betweenClosed(base.offset(-8,-1,-8),base.offset(8,5,8)))
            level.setBlockAndUpdate(pos,pos.getY()==base.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var p=cc.sighs.dndturn.gametest.TestPlayers.survival(h); p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);p.setOnGround(true);
        var service=ServerCombatService.forServer(level.getServer());
        Runnable cleanup=() -> {
            var id=service.encounterOf(p.getUUID());if(id!=null)service.stop(id);
            for(var e:level.getEntities(p,p.getBoundingBox().inflate(12))) if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
            for(long packed:forced) { var c=net.minecraft.world.level.ChunkPos.unpack(packed);level.setChunkForced(c.x(),c.z(),false); }forced.clear();
        };
        h.runAtTickTime(399,cleanup);
        h.runAfterDelay(4,() -> { try {
            var target=base.offset(2,0,0);
            level.setBlockAndUpdate(target,Blocks.COPPER_BLOCK.defaultBlockState());
            var wax=submit(p,service,"dndturn:tool",new ItemStack(Items.HONEYCOMB,2),target);
            h.assertTrue(level.getBlockState(target).is(Blocks.WAXED_COPPER_BLOCK) && p.getMainHandItem().getCount()==1,"waxing failed");
            h.assertTrue(!service.state(wax.encounter()).members().get(p.getUUID()).action(),"wax did not charge once");
            service.tacticalActions().request(p,wax);
            h.assertTrue(p.getMainHandItem().getCount()==1,"wax retry repeated effect");
            var self=new TacticalIntent.Target(TacticalIntent.TargetKind.SELF,p.level().dimension().identifier().toString(),null,null,-1,0,0,0);
            submit(p,service,"dndturn:equip",new ItemStack(Items.IRON_HELMET),TacticalIntent.Capability.EQUIP,self);
            h.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET),"free equip unavailable after action spent");
            h.assertTrue(!service.state(wax.encounter()).members().get(p.getUUID()).action(),"equip changed action balance");
            restart(p,service);
            level.setBlockAndUpdate(target.below(),Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(target,Blocks.WHEAT.defaultBlockState());
            submit(p,service,"dndturn:tool",new ItemStack(Items.BONE_MEAL,2),target);
            h.assertTrue(level.getBlockState(target).getValue(net.minecraft.world.level.block.CropBlock.AGE)>0,"crop did not grow");
            restart(p,service);
            level.setBlockAndUpdate(target,Blocks.STONE.defaultBlockState());
            var stand=submit(p,service,"dndturn:spawn_item",new ItemStack(Items.ARMOR_STAND,2),target);
            var evidence=service.abilityCheckpoint(stand.operation());
            h.assertTrue(evidence!=null && evidence.observed()!=null && evidence.observed().spawned().size()==1,"missing confirmed spawn identity: "+evidence);
            var generated=evidence.observed().spawned().getFirst();
            h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getEntity(generated.entity()) instanceof net.minecraft.world.entity.decoration.ArmorStand,"waiting for inserted armor stand to enter entity lookup"))
                .thenExecute(() -> {
            level.getEntity(generated.entity()).discard();
            restart(p,service);
            level.setBlockAndUpdate(target,Blocks.SUSPICIOUS_SAND.defaultBlockState());
            var brush=submit(p,service,"dndturn:brush",new ItemStack(Items.BRUSH),target);
            var round=service.state(brush.encounter()).round();
            int movement=service.state(brush.encounter()).members().get(p.getUUID()).movementTicks();
            h.assertTrue(p.isUsingItem(),"brush did not begin: "+service.results(brush.encounter(),0,16).results());
            h.startSequence().thenWaitUntil(() -> h.assertTrue(!service.tacticalActions().running(p.getUUID()),"brush still running"))
                .thenExecute(() -> {
                    h.assertTrue(level.getBlockState(target).is(Blocks.SAND),"brush did not finish native archaeology: "+service.results(brush.encounter(),0,128).results());
                    h.assertTrue(service.state(brush.encounter()).round()==round && service.state(brush.encounter()).current().equals(p.getUUID()),"brushing advanced the turn");
                    h.assertTrue(service.state(brush.encounter()).members().get(p.getUUID()).movementTicks()==movement,"brushing spent movement");
                    cleanup.run();
                }).thenSucceed();
                });
        }catch(RuntimeException|AssertionError ex){cleanup.run();throw ex;} });
    }
    static void restart(ServerPlayer p,ServerCombatService service) {
        var id=service.encounterOf(p.getUUID());if(id!=null)service.stop(id);
        service.requestStart(p,UUID.randomUUID());
    }
    static TacticalNetwork.Request submit(ServerPlayer p,ServerCombatService service,String behavior,ItemStack stack,BlockPos target) {
        var t=new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK,p.level().dimension().identifier().toString(),null,
            TacticalActions.cell(target),Direction.UP.ordinal(),.5,1,.5);
        return submit(p,service,behavior,stack,TacticalIntent.Capability.USE_ITEM,t);
    }
    static TacticalNetwork.Request submit(ServerPlayer p,ServerCombatService service,String behavior,ItemStack stack,TacticalIntent.Capability capability,TacticalIntent.Target t) {
        if(service.encounterOf(p.getUUID())==null)service.requestStart(p,UUID.randomUUID());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        var id=service.encounterOf(p.getUUID());
        var source=AbilitySource.equipment(TacticalIntent.Hand.MAIN_HAND,new TacticalIntent.ItemReference(p.getInventory().getSelectedSlot(),TacticalItems.revision(p,stack)));
        var intent=new TacticalIntent(behavior,1,capability,t,source);
        var request=new TacticalNetwork.Request(service.generation(),id,UUID.randomUUID(),service.state(id).version(),intent,false);
        service.tacticalActions().request(p,request);return request;
    }
}
