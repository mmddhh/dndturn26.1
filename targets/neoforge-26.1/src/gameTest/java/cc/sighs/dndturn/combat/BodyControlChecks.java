package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Ordinary body force and lifecycle must run while active Goal/navigation/outer skills remain gated. */
public final class BodyControlChecks {
    private static void verifyImpulse(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player,
                                      net.minecraft.world.entity.LivingEntity target) {
        var level=helper.getLevel();
        var momentum=new Vec3(.12,0,.04);target.setDeltaMovement(momentum);
        var source=level.damageSources().source(TacticalDamageContext.DAMAGE_TYPE,player);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        for(UUID operation:java.util.List.of(first,second)) {
            var before=target.position();
            var result=TacticalDamageContext.hurtObserved(level,target,source,1,true,operation);
            var evidence=result.displacement();
            helper.assertTrue(result.accepted()&&evidence!=null,"missing passive response evidence");
            helper.assertTrue(evidence.operationId().equals(operation)&&evidence.settlementTick()==level.getServer().getTickCount(),
                "passive response lost operation/tick");
            helper.assertTrue(target.position().subtract(before).distanceToSqr(new Vec3(evidence.actualX(),evidence.actualY(),evidence.actualZ()))<1E-12,
                "collision response differed from recorded displacement");
            helper.assertTrue(target.getDeltaMovement().distanceToSqr(momentum)<1E-12,
                "immediate response replayed impulse or replaced existing momentum");
        }
    }
    public static void run(GameTestHelper helper) {
        var level=helper.getLevel(); var base=new BlockPos(81001,151,1); Set<Long> forced=new HashSet<>();
        for(int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++)
            for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++) {
                level.getChunk(x,z);if(level.setChunkForced(x,z,true))forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
            }
        for(var p:BlockPos.betweenClosed(base.offset(-6,-1,-6),base.offset(6,4,6)))
            level.setBlockAndUpdate(p,p.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=cc.sighs.dndturn.gametest.TestPlayers.survival(helper);player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var mob=EntityType.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        mob.setPos(base.getX()+2.5,base.getY(),base.getZ()+.5);mob.setNoAi(true);mob.setPersistenceRequired();level.addFreshEntity(mob);
        var creeper=EntityType.CREEPER.create(level,EntitySpawnReason.COMMAND);
        creeper.setPos(base.getX()+.5,base.getY(),base.getZ()+2.5);creeper.setNoAi(true);level.addFreshEntity(creeper);
        var snow=EntityType.SNOW_GOLEM.create(level,EntitySpawnReason.COMMAND);
        snow.setPos(base.getX()-2.5,base.getY(),base.getZ()+2.5);snow.setNoAi(true);level.addFreshEntity(snow);
        var wither=EntityType.WITHER.create(level,EntitySpawnReason.COMMAND);
        wither.setPos(base.getX()-4.5,base.getY()+2,base.getZ()+.5);wither.setNoAi(true);level.addFreshEntity(wither);
        var service=ServerCombatService.forServer(level.getServer());
        Runnable cleanup=()-> {
            UUID id=service.encounterOf(player.getUUID());if(id!=null)service.stop(id);mob.discard();creeper.discard();snow.discard();wither.discard();
            for(long key:forced){var c=net.minecraft.world.level.ChunkPos.unpack(key);level.setChunkForced(c.x(),c.z(),false);}forced.clear();
        };
        helper.runAtTickTime(99,cleanup);
        helper.runAtTickTime(30,()-> {
            service.requestStart(player,UUID.randomUUID()); UUID id=service.encounterOf(player.getUUID());
            for(int i=0;i<service.state(id).members().size()&&!player.getUUID().equals(service.state(id).current());i++)
                service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
            helper.assertTrue(player.getUUID().equals(service.state(id).current()),"fixture player turn unavailable");
            snow.setNoAi(false);wither.setNoAi(false);wither.setAlternativeTarget(0,player.getId());
            for(var p:BlockPos.betweenClosed(snow.blockPosition().offset(-1,0,-1),snow.blockPosition().offset(1,0,1)))
                level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
            Vec3 witherBefore=wither.position();
            mob.setNoAi(false);creeper.setNoAi(false);creeper.ignite();mob.setTarget(player);
            mob.getNavigation().moveTo(player,1); // Pre-existing autonomous path must not obtain a lease.
            var view=MobTurnStrategies.capture(new TacticalActor(mob),service.state(id),null,service.tacticalActions().engine);
            var seen=view.targets().stream().filter(t->t.id().equals(player.getUUID())).findFirst().orElseThrow();
            helper.assertTrue(seen.visible()&&seen.currentTarget()&&!seen.hostile(),"perception conflated vanilla commitment and directed hostility: "
                + seen + " difficulty=" + level.getDifficulty() + " player=" + player.position());
            int ticks=mob.tickCount;Vec3 before=mob.position();float health=player.getHealth();
            helper.runAfterDelay(12,()-> {
                helper.assertTrue(mob.tickCount>ticks&&!service.isEntitySimulationPaused(mob),"body lifecycle remained frozen");
                helper.assertTrue(mob.position().distanceToSqr(before)<.01&&!service.hasMobMoveLease(mob.getUUID())
                    && player.getHealth()==health,"unlicensed AI or navigation leaked");
                helper.assertTrue(wither.position().subtract(witherBefore).horizontalDistanceSqr()<.01,"outer Wither pursuit leaked");
                helper.assertTrue(level.getBlockState(snow.blockPosition()).isAir(),"outer snow trail leaked");
                mob.setDeltaMovement(new Vec3(.22,.15,0));Vec3 impulseStart=mob.position();
                int movement=service.state(id).members().get(mob.getUUID()).movementTicks();
                helper.runAfterDelay(4,()-> {
                    helper.assertTrue(mob.position().distanceToSqr(impulseStart)>.01,"ordinary body suppressed external impulse");
                    helper.assertTrue(service.state(id).members().get(mob.getUUID()).movementTicks()==movement,
                        "passive force spent active movement");
                    helper.assertTrue(creeper.isAlive()&&creeper.getSwelling(1)==0,"outer Creeper skill advanced without ability permission");
                    verifyImpulse(helper, player, mob);
                    cleanup.run();helper.succeed();
                });
            });
        });
    }
}
