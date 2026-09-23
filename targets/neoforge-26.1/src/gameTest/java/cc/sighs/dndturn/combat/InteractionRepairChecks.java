package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Actual native placement/insertion and autonomous candidate-turn regression coverage. */
public final class InteractionRepairChecks {
    public static void run(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(168001, 151, 1);
        var forced = new HashSet<Long>();
        for (int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++) for(int z=-2;z<=2;z++) {
            level.getChunk(x,z);
            if(level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
        }
        for(var pos:BlockPos.betweenClosed(base.offset(-8,-1,-8),base.offset(8,5,8)))
            level.setBlockAndUpdate(pos, pos.getY()==base.getY()-1 || pos.getY()==base.getY()+5
                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        var p=cc.sighs.dndturn.gametest.TestPlayers.survival(h);
        p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5); p.setOnGround(true);
        var service=ServerCombatService.forServer(level.getServer());
        Runnable cleanup=() -> {
            var id=service.encounterOf(p.getUUID()); if(id!=null) service.stop(id);
            for(var entity:level.getEntities(p,p.getBoundingBox().inflate(16)))
                if(!(entity instanceof net.minecraft.world.entity.player.Player)) entity.discard();
            for(long packed:forced) { var c=net.minecraft.world.level.ChunkPos.unpack(packed); level.setChunkForced(c.x(),c.z(),false); }
            forced.clear();
        };
        h.runAtTickTime(299,cleanup);
        h.runAfterDelay(4,() -> { try {
            var melee=new VanillaBehaviors.Melee();
            for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)
                if(item!=Items.AIR) h.assertTrue(melee.supportsItem(new ItemStack(item)),"item melee gated: "+item);
            h.assertTrue(!melee.supportsItem(ItemStack.EMPTY),"empty item fabricated equipment source");
            var first=base.offset(2,0,0);
            var target=new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK,level.dimension().identifier().toString(),null,
                TacticalActions.cell(first.below()),Direction.UP.ordinal(),.5,1,.5);
            var request=PlayerItemChecks.submit(p,service,"dndturn:place",new ItemStack(Items.CHEST,2),TacticalIntent.Capability.PLACE,target);
            h.assertTrue(level.getBlockState(first).is(Blocks.CHEST),"single chest failed: "+service.results(request.encounter(),0,16).results());
            h.assertTrue(p.getMainHandItem().getCount()==1,"chest did not consume exactly once");
            service.tacticalActions().request(p,request);
            h.assertTrue(p.getMainHandItem().getCount()==1,"retry repeated chest placement");
            PlayerItemChecks.restart(p,service);
            var second=first.south();
            var secondTarget=new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK,level.dimension().identifier().toString(),null,
                TacticalActions.cell(second.below()),Direction.UP.ordinal(),.5,1,.5);
            var pair=PlayerItemChecks.submit(p,service,"dndturn:place",new ItemStack(Items.CHEST),TacticalIntent.Capability.PLACE,secondTarget);
            h.assertTrue(level.getBlockState(second).is(Blocks.CHEST)
                && level.getBlockState(first).getValue(ChestBlock.TYPE)!=ChestType.SINGLE
                && level.getBlockState(second).getValue(ChestBlock.TYPE)!=ChestType.SINGLE,
                "native double chest failed: "+service.results(pair.encounter(),0,16).results());
            h.assertTrue(service.abilityCheckpoint(pair.operation()).observed().blocks().stream().anyMatch(b -> b.cell().equals(TacticalActions.cell(first))),
                "double chest partner missing from effect footprint");
            level.setBlockAndUpdate(first,Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(second,Blocks.AIR.defaultBlockState());
            PlayerItemChecks.restart(p,service);
            var egg=PlayerItemChecks.submit(p,service,"dndturn:spawn_item",new ItemStack(Items.ZOMBIE_SPAWN_EGG,2),base.offset(1,-1,0));
            var evidence=service.abilityCheckpoint(egg.operation());
            h.assertTrue(evidence!=null && evidence.observed()!=null && evidence.observed().spawned().size()==1,
                "egg missing insertion evidence: "+service.results(egg.encounter(),0,16).results());
            var mobId=evidence.observed().spawned().getFirst().entity();
            var before=service.state(egg.encounter());
            h.assertTrue(egg.encounter().equals(service.encounterOf(mobId)),"spawned mob was not enrolled");
            h.assertTrue(before.members().get(mobId).eligibleRound()==before.round()+1,"spawned mob obtained immediate turn eligibility");
            service.tacticalActions().request(p,egg);
            h.assertTrue(p.getMainHandItem().getCount()==1 && service.state(egg.encounter()).members().size()==2,"egg retry duplicated spawn/member");
            h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getEntity(mobId)!=null,"waiting for inserted Mob lookup"))
                .thenExecute(() -> {
                    var zombie=(net.minecraft.world.entity.monster.zombie.Zombie)level.getEntity(mobId);
                    zombie.setTarget(null);
                    h.assertTrue(!zombie.isNoAi(),"egg spawned disabled AI");
                    service.useAttackRandomForGameTest(egg.encounter(),new Random(71){ public int nextInt(int bound){return 9;} });
                    service.endCurrentTurn(egg.encounter(),UUID.randomUUID(),service.state(egg.encounter()).version());
                }).thenWaitUntil(() -> {
                    var results=service.results(egg.encounter(),0,128).results();
                    boolean attacked=results.stream().anyMatch(r -> r.snapshot().owner().equals(mobId)
                        && r.snapshot().kind()==OperationRecord.Kind.ATTACK && r.terminal());
                    var state=service.state(egg.encounter());
                    if(!attacked && p.getUUID().equals(state.current()) && !service.tacticalActions().running(p.getUUID()))
                        service.endCurrentTurn(egg.encounter(),UUID.randomUUID(),state.version());
                    h.assertTrue(attacked,"candidate zombie did not autonomously acquire/attack survival player");
                }).thenWaitUntil(() -> h.assertTrue(p.getUUID().equals(service.state(egg.encounter()).current()),
                    "waiting for player turn after autonomous attack"))
                .thenExecute(() -> {
                    h.assertTrue(service.state(egg.encounter()).phase()==EncounterPhase.ACTIVE,"zombie attack did not activate candidate encounter");
                    var targetMob=(net.minecraft.world.entity.LivingEntity)level.getEntity(mobId);
                    float health=targetMob.getHealth();
                    var attackTarget=new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY,level.dimension().identifier().toString(),mobId,null,-1,0,0,0);
                    var attack=PlayerItemChecks.submit(p,service,"dndturn:melee",new ItemStack(Items.STONE),TacticalIntent.Capability.ATTACK,attackTarget);
                    h.assertTrue(service.results(egg.encounter(),0,128).results().stream().anyMatch(r -> r.terminal()
                        && r.snapshot().operationId().equals(attack.operation()) && r.outcome()==OperationRecord.Outcome.COMPLETED),
                        "ordinary block item attack was rejected");
                    h.assertTrue(!service.state(egg.encounter()).members().get(p.getUUID()).action(),"ordinary item attack did not charge once");
                    h.assertTrue(targetMob.getHealth()==health,"ordinary zero-attribute item changed the approved damage formula");
                    cleanup.run();
                }).thenSucceed();
        } catch(RuntimeException|Error failure) {cleanup.run();throw failure;} });
    }
}
