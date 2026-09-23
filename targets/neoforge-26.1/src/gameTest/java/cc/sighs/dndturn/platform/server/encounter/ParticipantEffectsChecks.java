package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.server.persistence.CombatSavedData;
import cc.sighs.dndturn.platform.server.persistence.PersistenceTestAccess;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

public final class ParticipantEffectsChecks {
    private record Arena(ServerPlayer player,EncounterRuntime service,BlockPos base,Runnable cleanup) {}
    private static Arena arena(GameTestHelper h,int offset) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(offset,190,1));var forced=new HashSet<Long>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++) for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++) {
            level.getChunk(x,z);if(level.setChunkForced(x,z,true))forced.add(ChunkPos.pack(x,z));
        }
        level.waitForEntities(net.minecraft.world.level.ChunkPos.containing(base), 1);
        for(var p:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(4,4,3)))
            level.setBlockAndUpdate(p,p.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=TestPlayers.survival(h);
        player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        player.getFoodData().setFoodLevel(10);player.getFoodData().setSaturation(0);
        var service=ServerRuntime.encounters(level.getServer());
        Runnable cleanup=()->{var id=service.encounterOf(player.getUUID());if(id!=null)service.stop(id);
            for(long key:forced){var c=ChunkPos.unpack(key);level.setChunkForced(c.x(),c.z(),false);}forced.clear();};
        return new Arena(player,service,base,cleanup);
    }
    private static void ready(GameTestHelper h,Arena a) {
        var id=a.service.encounterOf(a.player.getUUID());
        h.assertTrue(id!=null && a.service.state(id).phase()==EncounterPhase.CANDIDATE
            && a.player.getUUID().equals(a.service.state(id).current()),"waiting for next owner turn");
    }
    private static void end(Arena a) {
        var id=a.service.encounterOf(a.player.getUUID());
        a.service.endTurn(id,a.player,UUID.randomUUID(),a.service.state(id).version());
    }
    public static void effects(GameTestHelper h) {
        var a=arena(h,125001);var player=a.player;var service=a.service;
        boolean[] cancelExpiry={false},cancelDamage={false},cancelDeath={false},cancelHealing={false};
        Consumer<MobEffectEvent.Expired> expiration=e->{if(e.getEntity()==player && e.getEffectInstance().getEffect().equals(MobEffects.REGENERATION)&&cancelExpiry[0])e.setCanceled(true);};
        Consumer<LivingIncomingDamageEvent> damage=e->{if(e.getEntity()==player && e.getSource().is(DamageTypes.WITHER)&&cancelDamage[0])e.setCanceled(true);};
        Consumer<LivingDeathEvent> death=e->{if(e.getEntity()==player && cancelDeath[0]){player.setHealth(1);e.setCanceled(true);}};
        Consumer<LivingHealEvent> healing=e->{if(e.getEntity()==player && cancelHealing[0])e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(expiration);NeoForge.EVENT_BUS.addListener(damage);NeoForge.EVENT_BUS.addListener(death);NeoForge.EVENT_BUS.addListener(healing);
        Runnable cleanup=()->{NeoForge.EVENT_BUS.unregister(expiration);NeoForge.EVENT_BUS.unregister(damage);NeoForge.EVENT_BUS.unregister(death);NeoForge.EVENT_BUS.unregister(healing);a.cleanup.run();};
        h.runAtTickTime(359,cleanup);
        int[] bodyTicks={0};
        h.startSequence().thenIdle(30).thenExecute(()->{
            service.requestStart(player,UUID.randomUUID());ready(h,a);
            player.setHealth(10);player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,31));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED,91,0));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED,31,1));bodyTicks[0]=player.tickCount;
        }).thenIdle(37).thenExecute(()->{
            h.assertTrue(player.getHealth()==10 && player.getEffect(MobEffects.REGENERATION).getDuration()==60,
                "real waiting consumed effect duration or regenerated health");
            h.assertTrue(player.tickCount>bodyTicks[0],"effect pause froze ordinary player body");
            service.persistIfChanged();var saved=player.level().getServer().getDataStorage().computeIfAbsent(CombatSavedData.TYPE).envelope();
            var duration=saved.participantEffects().get(player.getUUID()).effects().get("minecraft:regeneration").getFirst().duration();
            h.assertTrue(duration.rounds()==2 && duration.time().ticks()==30,"checkpoint lost captured effect rounds");
            var isolated=EncounterRuntime.restoreForGameTest(player.level().getServer(),PersistenceTestAccess.saved(player.level().getServer().getDataStorage().computeIfAbsent(CombatSavedData.TYPE).json()));
            h.assertTrue(isolated.participantEffects().reconcile(player),"unchanged native effect failed restart reconciliation");
            var id=service.encounterOf(player.getUUID());long version=service.state(id).version();var operation=UUID.randomUUID();
            var result=service.endTurn(id,player,operation,version);
            h.assertTrue(Math.abs(player.getHealth()-10.6)<1e-5 && player.getEffect(MobEffects.REGENERATION).getDuration()==30,"first owner turn did not settle 0.6 and one round");
            h.assertTrue(service.endTurn(id,player,operation,version).equals(result) && Math.abs(player.getHealth()-10.6)<1e-5,"end-turn retry repeated status effects");
        }).thenWaitUntil(()->ready(h,a)).thenIdle(7).thenExecute(()->{
            cancelExpiry[0]=true;end(a);
            h.assertTrue(Math.abs(player.getHealth()-11.2)<1e-5 && player.getEffect(MobEffects.REGENERATION).getDuration()==0,
                "full final-round regeneration or cancellable expiration lost");
            var speed=player.getEffect(MobEffects.SPEED);
            h.assertTrue(speed.getAmplifier()==0 && speed.getDuration()==60,"hidden effect did not age/promote with native attribute refresh");
            cancelExpiry[0]=false;
        }).thenWaitUntil(()->ready(h,a)).thenExecute(()->{
            player.setHealth(1.1f);player.addEffect(new MobEffectInstance(MobEffects.POISON,1));end(a);
            h.assertTrue(Math.abs(player.getHealth()-1)<1e-5 && !player.hasEffect(MobEffects.POISON)
                && !player.hasEffect(MobEffects.REGENERATION),"poison floor, same-turn expiration or canceled-expiry retry failed");
        }).thenWaitUntil(()->ready(h,a)).thenExecute(()->{
            player.setHealth(10);player.invulnerableTime=17;player.addEffect(new MobEffectInstance(MobEffects.WITHER,1));cancelDamage[0]=true;
            end(a);cancelDamage[0]=false;
            h.assertTrue(player.getHealth()==10 && player.invulnerableTime==17 && !player.hasEffect(MobEffects.WITHER),"native damage cancellation/ownership lost");
        }).thenWaitUntil(()->ready(h,a)).thenExecute(()->{
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,1));cancelHealing[0]=true;
            end(a);cancelHealing[0]=false;
            h.assertTrue(player.getHealth()==10 && !player.hasEffect(MobEffects.REGENERATION),"native healing cancellation lost");
        }).thenWaitUntil(()->ready(h,a)).thenExecute(()->{
            player.setHealth(player.getMaxHealth()-.1f);player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,1));end(a);
            h.assertTrue(player.getHealth()==player.getMaxHealth(),"fractional regeneration exceeded native maximum health");
        }).thenWaitUntil(()->ready(h,a)).thenExecute(()->{
            player.setHealth(.5f);player.addEffect(new MobEffectInstance(MobEffects.WITHER,1));cancelDeath[0]=true;
            end(a);cancelDeath[0]=false;
            h.assertTrue(player.isAlive() && service.isMember(player.getUUID()),"canceled player death removed membership");
        }).thenWaitUntil(()->ready(h,a)).thenExecute(()->{
            player.setHealth(.5f);player.addEffect(new MobEffectInstance(MobEffects.WITHER,1));
            var id=service.encounterOf(player.getUUID());var operation=UUID.randomUUID();long version=service.state(id).version();
            var result=service.endTurn(id,player,operation,version);
            h.assertTrue(!player.isAlive() && !service.isMember(player.getUUID()),"lethal end-turn effect did not clean membership");
            h.assertTrue(service.endTurn(id,player,operation,version).equals(result),"dead participant's boundary retry did not return canonical result");
            cleanup.run();
        }).thenSucceed();
    }
    public static void fire(GameTestHelper h) {
        var a=arena(h,127001);var level=h.getLevel();var service=a.service;
        var cow=EntityType.COW.create(level,EntitySpawnReason.COMMAND);cow.setNoAi(true);cow.setPersistenceRequired();
        cow.setPos(a.base.getX()+1.5,a.base.getY(),a.base.getZ()+.5);level.addFreshEntity(cow);
        Runnable cleanup=()->{a.cleanup.run();cow.discard();};h.runAtTickTime(119,cleanup);
        int[] ticks={0};
        float initialHealth=cow.getHealth();
        h.startSequence().thenIdle(30).thenWaitUntil(()->{
            h.assertTrue(level.getEntity(cow.getUUID())==cow
                && level.getEntitiesOfClass(Mob.class,a.player.getBoundingBox().inflate(4)).contains(cow),
                "fire fixture cow is not yet visible in native discovery: "+cow.position()+" player="+a.player.position());
        }).thenExecute(()->{
            service.requestStart(a.player,UUID.randomUUID());var id=service.encounterOf(a.player.getUUID());
            h.assertTrue(id.equals(service.encounterOf(cow.getUUID())),"fire fixture cow missing");
            cow.setRemainingFireTicks(31);
            if(!service.state(id).current().equals(cow.getUUID()))service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
            service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
            h.assertTrue(Math.abs(cow.getHealth()-(initialHealth-1.5))<1e-5 && cow.getRemainingFireTicks()==30,"Mob fire did not settle fractional 1.5 on first turn");
            ticks[0]=cow.tickCount;
        }).thenIdle(11).thenExecute(()->{
            h.assertTrue(Math.abs(cow.getHealth()-(initialHealth-1.5))<1e-5 && cow.getRemainingFireTicks()==30 && cow.tickCount>ticks[0],"thinking advanced fire or froze body");
            var id=service.encounterOf(cow.getUUID());service.stop(id);
            h.assertTrue(cow.getRemainingFireTicks()==30,"exit lost remaining round conversion");
        }).thenIdle(4).thenExecute(()->{
            h.assertTrue(cow.getRemainingFireTicks()<30 && cow.getRemainingFireTicks()>0,"exit did not resume native fire time");
            cleanup.run();
        }).thenSucceed();
    }
}
