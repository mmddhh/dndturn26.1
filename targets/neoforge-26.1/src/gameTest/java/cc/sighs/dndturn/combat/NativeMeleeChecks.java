package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

public final class NativeMeleeChecks {
    public static void run(GameTestHelper h) {
        var level = h.getLevel(); var base = new BlockPos(101001,151,1);
        var forced = new HashSet<Long>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++)
            for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++) {
                level.getChunk(x,z);if(level.setChunkForced(x,z,true))forced.add(ChunkPos.pack(x,z));
            }
        for(var pos:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(4,4,3)))
            level.setBlockAndUpdate(pos,pos.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=cc.sighs.dndturn.gametest.TestPlayers.survival(h);
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var mob=EntityType.ENDERMAN.create(level,EntitySpawnReason.COMMAND);
        mob.setNoAi(true);mob.setPersistenceRequired();mob.setPos(base.getX()+1.5,base.getY(),base.getZ()+.5);
        level.addFreshEntity(mob);
        var service=ServerCombatService.forServer(level.getServer());
        Runnable cleanup=()->{
            var id=service.encounterOf(player.getUUID());if(id!=null)service.stop(id);mob.discard();
            for(var key:forced){var c=ChunkPos.unpack(key);level.setChunkForced(c.x(),c.z(),false);}forced.clear();
        };
        h.runAtTickTime(119,cleanup);
        h.runAtTickTime(30,()->{
            try {
                service.requestStart(player,UUID.randomUUID());var id=service.encounterOf(player.getUUID());
                h.assertTrue(id!=null && id.equals(service.encounterOf(mob.getUUID())),"Enderman missing from normal admission");
                var state=service.state(id);
                if(!player.getUUID().equals(state.current()))service.endCurrentTurn(id,UUID.randomUUID(),state.version());
                service.useAttackRandomForGameTest(id,new Random(391){public int nextInt(int bound){return 9;}});
                var playerActor=new TacticalActor(player);var melee=new IntrinsicMelee();
                var opener=new TacticalIntent(melee.id(),melee.version(),melee.cost(),new TacticalIntent.Target(
                    TacticalIntent.TargetKind.ENTITY,state.region().dimension(),mob.getUUID(),null,-1,0,0,0),melee.source(playerActor,null));
                var openerId=UUID.randomUUID();
                service.tacticalActions().submit(playerActor,service.generation(),id,openerId,service.state(id).version(),opener,false);
                h.assertTrue(service.state(id).phase()==EncounterPhase.ACTIVE,"shared player melee failed to activate against Enderman");
                if(!mob.getUUID().equals(service.state(id).current()))service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
                var actor=new TacticalActor(mob);
                var modifier=Identifier.fromNamespaceAndPath("test","native_damage");
                mob.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(new AttributeModifier(modifier,3,AttributeModifier.Operation.ADD_VALUE));
                var facts=NativeFacts.capture(actor);
                h.assertTrue(facts.attributes().get("attack_damage").effective()==10
                    && facts.attributes().get("attack_damage").base()==7,"effective damage lost or double-counted modifier");
                var position=mob.position();var previousTarget=mob.getTarget();
                melee.checkedSources(actor);NativeFacts.capture(actor);
                h.assertTrue(mob.position().equals(position)&&mob.getTarget()==previousTarget,"query changed native state");
                var intent=new TacticalIntent(melee.id(),melee.version(),melee.cost(),new TacticalIntent.Target(
                    TacticalIntent.TargetKind.ENTITY,state.region().dimension(),player.getUUID(),null,-1,0,0,0),melee.source(actor,null));
                long version=service.state(id).version();var operation=UUID.randomUUID();float health=player.getHealth();
                service.tacticalActions().submit(actor,service.generation(),id,operation,version,intent,false);
                h.assertTrue(player.getHealth()==health-10 && !service.state(id).members().get(mob.getUUID()).action(),
                    "Enderman did not use shared PLAN with one effective native damage input and one action");
                service.tacticalActions().submit(actor,service.generation(),id,operation,version,intent,false);
                h.assertTrue(player.getHealth()==health-10,"retry replayed Enderman damage");
                h.assertTrue(service.results(id,0,64).results().stream().filter(r->r.terminal()&&r.snapshot().kind()==OperationRecord.Kind.PLAN).count()==2,
                    "native/player roots did not share terminal ledger");
                cleanup.run();h.succeed();
            } catch(RuntimeException|Error failure){cleanup.run();throw failure;}
        });
    }
}
