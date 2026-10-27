package cc.sighs.dndturn.platform.server.encounter;
import cc.sighs.dndturn.platform.server.runtime.EncounterRuntime;

import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.platform.network.ActionProtocol;
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
    private static final net.neoforged.neoforge.registries.DeferredRegister<MobEffect> EFFECTS =
        net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT, "effect_extension");
    private static final java.util.function.Supplier<MobEffect> BASIC = EFFECTS.register("basic", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 1) {});
    private static int unknownCalls;
    private static final java.util.function.Supplier<MobEffect> UNKNOWN = EFFECTS.register("special", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 2) {
        @Override public boolean applyEffectTick(net.minecraft.server.level.ServerLevel level, LivingEntity actor, int amplifier) { unknownCalls++; return true; }
        @Override public boolean shouldApplyEffectTickThisTick(int ticks, int amplifier) { return true; }
    });
    public static void register(net.neoforged.bus.api.IEventBus bus) { EFFECTS.register(bus); }

    private static float exhaustion(ServerPlayer player) {
        try {
            var field=net.minecraft.world.food.FoodData.class.getDeclaredField("exhaustionLevel");
            field.setAccessible(true); return field.getFloat(player.getFoodData());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
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
        Runnable cleanup=()->{var id=service.encounterOf(player.getUUID());if(id!=null)service.lifecycle().stop(id);
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
        a.service.combat().endTurn(id,a.player,UUID.randomUUID(),a.service.state(id).version());
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
        h.runAtTickTime(599,cleanup);
        int[] bodyTicks={0};
        ActionProtocol.Request[] drink={null};
        h.startSequence().thenIdle(30).thenExecute(()->{
            service.lifecycle().requestStart(player,UUID.randomUUID());ready(h,a);
            player.setHealth(10);player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,31));
            unknownCalls=0;
            player.addEffect(new MobEffectInstance(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(BASIC.get()), 91));
            player.addEffect(new MobEffectInstance(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(UNKNOWN.get()), 91));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER,31));
            try {
                cc.sighs.dndturn.platform.server.effect.vanilla.EffectAdapters.register(new cc.sighs.dndturn.platform.server.effect.vanilla.EffectAdapters.Adapter(
                    "effect_extension:late",1,e->false,Set.of(),cc.sighs.dndturn.platform.server.effect.vanilla.EffectAdapters.Tick.NONE));
                throw new AssertionError("late effect registration accepted");
            } catch (IllegalStateException expected) { }

            player.addEffect(new MobEffectInstance(MobEffects.SPEED,91,0));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED,31,1));bodyTicks[0]=player.tickCount;
        }).thenIdle(37).thenExecute(()->{
            h.assertTrue(player.getHealth()==10 && player.getEffect(MobEffects.REGENERATION).getDuration()==60,
                "real waiting consumed effect duration or regenerated health");
            h.assertTrue(player.tickCount>bodyTicks[0],"effect pause froze ordinary player body");
            var basic=player.getEffect(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(BASIC.get()));
            var unknown=player.getEffect(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(UNKNOWN.get()));
            h.assertTrue(unknownCalls==0 && basic.getDuration()==120 && unknown.getDuration()==120,"extension effects used wall time");
            var projected=service.vanillaEffects().describe(player,unknown);
            h.assertTrue(projected.support()==cc.sighs.dndturn.domain.effect.EffectSnapshot.NativeSupport.NEEDS_ADAPTER && projected.remainingRounds()==4,
                "unadapted effect support not projected");
            h.assertTrue(service.vanillaEffects().describe(player,basic).support()==cc.sighs.dndturn.domain.effect.EffectSnapshot.NativeSupport.MANAGED,
                "non-minecraft basic effect rejected");
            service.persistIfChanged();var saved=player.level().getServer().getDataStorage().computeIfAbsent(CombatSavedData.TYPE).envelope();
            var duration=saved.participantEffects().get(player.getUUID()).effects().get("minecraft:regeneration").getFirst().duration();
            h.assertTrue(duration.rounds()==2 && duration.time().ticks()==30,"checkpoint lost captured effect rounds");
            var isolated=EncounterRuntime.restoreForGameTest(player.level().getServer(),PersistenceTestAccess.saved(player.level().getServer().getDataStorage().computeIfAbsent(CombatSavedData.TYPE).json()));
            h.assertTrue(isolated.vanillaEffects().reconcile(player),"unchanged native effect failed restart reconciliation");
            var originalOwner=saved.participantEffects().get(player.getUUID());
            var mismatched=new HashMap<>(originalOwner.effects());
            var timer=mismatched.get("minecraft:regeneration").getFirst();
            mismatched.put("minecraft:regeneration",List.of(new cc.sighs.dndturn.platform.server.checkpoint.VanillaEffectSettlementState.Timer(
                timer.instance(),timer.amplifier(),timer.expectedNativeTicks(),timer.duration(),timer.adapter(),timer.semanticVersion()+1)));
            isolated.vanillaEffects().restore(Map.of(player.getUUID(),new cc.sighs.dndturn.platform.server.checkpoint.VanillaEffectSettlementState.Owner(mismatched,originalOwner.fire())),Map.of());
            h.assertTrue(!isolated.vanillaEffects().reconcile(player) && player.getHealth()==10,"semantic mismatch restored or replayed native effect");
            var id=service.encounterOf(player.getUUID());long version=service.state(id).version();var operation=UUID.randomUUID();
            float exhaustion=exhaustion(player);
            var result=service.combat().endTurn(id,player,operation,version);
            h.assertTrue(Math.abs(exhaustion(player)-exhaustion-.15f)<1e-5,
                "hunger did not consume captured effective ticks");
            h.assertTrue(unknownCalls==0 && unknown.getDuration()==90 && basic.getDuration()==90,"extension round duration or callback isolation lost");
            h.assertTrue(Math.abs(player.getHealth()-10.6)<1e-5 && player.getEffect(MobEffects.REGENERATION).getDuration()==30,"first owner turn did not settle 0.6 and one round");
            h.assertTrue(service.combat().endTurn(id,player,operation,version).equals(result) && Math.abs(player.getHealth()-10.6)<1e-5,"end-turn retry repeated status effects");
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
            player.setHealth(10);
            player.getInventory().setSelectedSlot(0);
            var potion=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
            potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.HEALING));
            player.getInventory().setItem(0,potion);
            var id=service.encounterOf(player.getUUID());
            var self=new ActionIntent.Target(ActionIntent.TargetKind.SELF,player.level().dimension().identifier().toString(),null,null,-1,0,0,0);
            var options=service.actions().selectAndDiscover(player,new ActionProtocol.Query(service.generation(),id,UUID.randomUUID(),self,ActionIntent.Hand.MAIN_HAND));
            var offer=options.offers().stream().filter(o->o.intent().behaviorId().equals("dndturn:consume")).findFirst().orElseThrow();
            h.assertTrue(offer.reason().isEmpty(),"healing potion unavailable: "+offer.reason());
            drink[0]=new ActionProtocol.Request(service.generation(),id,UUID.randomUUID(),options.version(),offer.intent(),false);
            service.actions().request(player,drink[0]);
        }).thenWaitUntil(()->{
            var id=drink[0].encounter();
            var result=service.lifecycle().results(id,0,256).results().stream().filter(v->v.snapshot().operationId().equals(drink[0].operation())).findFirst().orElse(null);
            h.assertTrue(result!=null,"drinking has not finished");
            h.assertTrue(result.outcome()==cc.sighs.dndturn.domain.encounter.operation.OperationRecord.Outcome.COMPLETED,"drink failed: "+result);
            h.assertTrue(player.getHealth()==14 && player.getMainHandItem().is(net.minecraft.world.item.Items.GLASS_BOTTLE),"instant healing or bottle consumption lost");
            service.actions().request(player,drink[0]);
            h.assertTrue(player.getHealth()==14 && !service.state(id).members().get(player.getUUID()).action(),"instant retry repeated healing or refunded action");
        }).thenExecute(()->end(a)).thenWaitUntil(()->ready(h,a)).thenExecute(()->{
            player.setHealth(10); player.invulnerableTime=0;
            var potion=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
            potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.HARMING));
            player.getInventory().setItem(0,potion);
            var id=service.encounterOf(player.getUUID());
            var self=new ActionIntent.Target(ActionIntent.TargetKind.SELF,player.level().dimension().identifier().toString(),null,null,-1,0,0,0);
            var options=service.actions().selectAndDiscover(player,new ActionProtocol.Query(service.generation(),id,UUID.randomUUID(),self,ActionIntent.Hand.MAIN_HAND));
            var offer=options.offers().stream().filter(o->o.intent().behaviorId().equals("dndturn:consume")).findFirst().orElseThrow();
            drink[0]=new ActionProtocol.Request(service.generation(),id,UUID.randomUUID(),options.version(),offer.intent(),false);
            service.actions().request(player,drink[0]);
        }).thenWaitUntil(()->{
            var result=service.lifecycle().results(drink[0].encounter(),0,256).results().stream().filter(v->v.snapshot().operationId().equals(drink[0].operation())).findFirst().orElse(null);
            h.assertTrue(result!=null,"harm potion has not finished");
            h.assertTrue(result.outcome()==cc.sighs.dndturn.domain.encounter.operation.OperationRecord.Outcome.COMPLETED,"harm potion failed: "+result);
            h.assertTrue(player.getHealth()==4,"instant harm did not apply native six damage");
            service.actions().request(player,drink[0]);
            h.assertTrue(player.getHealth()==4,"instant harm retry repeated damage");
        }).thenExecute(()->end(a)).thenWaitUntil(()->ready(h,a)).thenExecute(()->{
            player.setHealth(.5f);player.addEffect(new MobEffectInstance(MobEffects.WITHER,1));cancelDeath[0]=true;
            end(a);cancelDeath[0]=false;
            h.assertTrue(player.isAlive() && service.isMember(player.getUUID()),"canceled player death removed membership");
        }).thenWaitUntil(()->ready(h,a)).thenExecute(()->{
            player.setHealth(.5f);player.addEffect(new MobEffectInstance(MobEffects.WITHER,1));
            var id=service.encounterOf(player.getUUID());var operation=UUID.randomUUID();long version=service.state(id).version();
            var result=service.combat().endTurn(id,player,operation,version);
            h.assertTrue(!player.isAlive() && !service.isMember(player.getUUID()),"lethal end-turn effect did not clean membership");
            h.assertTrue(service.combat().endTurn(id,player,operation,version).equals(result),"dead participant's boundary retry did not return canonical result");
            cleanup.run();
        }).thenSucceed();
    }
    public static void fire(GameTestHelper h) {
        var a=arena(h,127001);var level=h.getLevel();var service=a.service;
        for (var pos:BlockPos.betweenClosed(a.base.offset(-3,4,-3),a.base.offset(4,4,3)))
            level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState()); // Fire timing requires a dry fixture.
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
            cow.setTarget(a.player); // Explicit hostility; neutral animals do not auto-enroll.
            service.lifecycle().requestStart(a.player,UUID.randomUUID());
            var id=service.encounterOf(a.player.getUUID());
            h.assertTrue(id.equals(service.encounterOf(cow.getUUID())),"fire fixture cow missing");
        }).thenWaitUntil(()->{
            var id=service.encounterOf(cow.getUUID());
            h.assertTrue(id!=null,"waiting for fire encounter");
            if (service.state(id).phase()!=EncounterPhase.ACTIVE) {
                if (a.player.getUUID().equals(service.state(id).current()))
                    service.combat().attack(a.player,cow.getUUID(),UUID.randomUUID(),service.state(id).version());
                else if (cow.getUUID().equals(service.state(id).current()))
                    service.combat().endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
            }
            h.assertTrue(service.state(id).phase()==EncounterPhase.ACTIVE,"waiting for committed hostile activation");
        }).thenWaitUntil(()->{
            var id=service.encounterOf(cow.getUUID());
            h.assertTrue(id!=null,"waiting for fire encounter");
            if (a.player.getUUID().equals(service.state(id).current()))
                service.combat().endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
            h.assertTrue(cow.getUUID().equals(service.state(id).current()),"waiting for fire owner turn");
        }).thenExecute(()->{
            var id=service.encounterOf(cow.getUUID());
            cow.setHealth(initialHealth); cow.invulnerableTime=0;
            cow.setRemainingFireTicks(31);
            service.combat().endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
            h.assertTrue(Math.abs(cow.getHealth()-(initialHealth-1.5))<1e-5 && cow.getRemainingFireTicks()==30,"Mob fire did not settle fractional 1.5 on first turn");
            ticks[0]=cow.tickCount;
        }).thenIdle(11).thenExecute(()->{
            h.assertTrue(Math.abs(cow.getHealth()-(initialHealth-1.5))<1e-5 && cow.getRemainingFireTicks()==30 && cow.tickCount>ticks[0],"thinking advanced fire or froze body: health="+cow.getHealth()+" fire="+cow.getRemainingFireTicks()+" ticks="+cow.tickCount+" initial="+ticks[0]+" state="+service.state(service.encounterOf(cow.getUUID())));
            var id=service.encounterOf(cow.getUUID());service.lifecycle().stop(id);
            h.assertTrue(cow.getRemainingFireTicks()==30,"exit lost remaining round conversion");
        }).thenIdle(4).thenExecute(()->{
            h.assertTrue(cow.getRemainingFireTicks()<30 && cow.getRemainingFireTicks()>0,"exit did not resume native fire time");
            cleanup.run();
        }).thenSucceed();
    }
}
