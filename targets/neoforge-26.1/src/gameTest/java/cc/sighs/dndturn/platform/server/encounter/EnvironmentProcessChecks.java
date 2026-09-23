package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.encounter.TurnParticipant;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;

/** Real queues/tickers: synchronous TNT creation, paused fuse and hopper contact, then environment progress. */
public final class EnvironmentProcessChecks {
    private static void afterServerTicks(net.minecraft.server.MinecraftServer server, int delay, Runnable action) {
        var listener = new java.util.function.Consumer<net.neoforged.neoforge.event.tick.ServerTickEvent.Post>() {
            int remaining = delay;
            public void accept(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
                if (event.getServer() != server || --remaining > 0) return;
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);
                action.run();
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
    }
    public static void run(GameTestHelper helper) {
        var level = helper.getLevel(); var base = new BlockPos(77001,151,1); Set<Long> forced = new HashSet<>();
        for (int x=(base.getX()-24)>>4;x<=(base.getX()+24)>>4;x++)
            for(int z=(base.getZ()-24)>>4;z<=(base.getZ()+24)>>4;z++) {
                level.getChunk(x,z); if(level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
            }
        level.waitForEntities(net.minecraft.world.level.ChunkPos.containing(base), 1);
        for(var p:BlockPos.betweenClosed(base.offset(-6,-1,-6),base.offset(6,3,6)))
            level.setBlockAndUpdate(p,p.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var player=helper.makeMockServerPlayerInLevel(); player.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        var service=ServerRuntime.encounters(level.getServer());
        BlockPos tntPos=base.east(2), hopperPos=base.south(2), delayed=base.north(3);
        helper.runAtTickTime(30,()-> {
            service.requestStart(player,UUID.randomUUID()); UUID id=service.encounterOf(player.getUUID());
            helper.assertTrue(id!=null,"normal player-only START failed");
            level.setBlockAndUpdate(tntPos,Blocks.TNT.defaultBlockState());
            level.setBlockAndUpdate(tntPos.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            var primed=level.getEntitiesOfClass(PrimedTnt.class,new net.minecraft.world.phys.AABB(tntPos).inflate(2));
            helper.assertTrue(primed.size()==1 && level.getBlockState(tntPos).isAir(),"synchronous power did not create TNT");
            var tnt=primed.getFirst(); int fuse=tnt.getFuse();
            level.setBlockAndUpdate(delayed,Blocks.TNT.defaultBlockState());
            level.setBlockAndUpdate(delayed.west(),Blocks.REPEATER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,Direction.WEST)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DELAY,4));
            level.setBlockAndUpdate(delayed.west(2),Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(hopperPos,Blocks.HOPPER.defaultBlockState());
            var hopper=(HopperBlockEntity)level.getBlockEntity(hopperPos);
            var item=new ItemEntity(level,hopperPos.getX()+.5,hopperPos.getY()+1,hopperPos.getZ()+.5,new ItemStack(Items.COBBLESTONE,3));
            item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            item.setNeverPickUp(); // This fixture measures hopper suction, not nearby player pickup.
            level.addFreshEntity(item);
            HopperBlockEntity.entityInside(level,hopperPos,level.getBlockState(hopperPos),item,hopper);
            helper.assertTrue(hopper.isEmpty(),"contact suction bypassed environment time");
            helper.runAfterDelay(12,()-> {
                helper.assertTrue(tnt.getFuse()==fuse && hopper.isEmpty(),"thinking time advanced managed process");
                helper.assertTrue(level.getBlockState(delayed).is(Blocks.TNT),"repeater delay advanced during thinking");
                level.getServer().tickRateManager().setFrozen(true);
                service.endCurrentTurn(id,UUID.randomUUID(),service.state(id).version());
                int remaining=service.state(id).environmentRemaining();
                afterServerTicks(level.getServer(), 3, ()-> {
                    boolean unchanged = service.state(id).environmentRemaining()==remaining && tnt.getFuse()==fuse;
                    level.getServer().tickRateManager().setFrozen(false);
                    helper.runAfterDelay(1, () -> helper.assertTrue(unchanged, "global freeze consumed environment work"));
                    helper.startSequence().thenWaitUntil(()->helper.assertTrue(tnt.getFuse()<fuse && !hopper.isEmpty() && level.getBlockState(delayed).isAir(),
                        "environment processes: fuse="+tnt.getFuse()+" hopper="+hopper.isEmpty()+" item="+item.position()+":"+item.getRemovalReason()+" delayed="+level.getBlockState(delayed)+" repeater="+level.getBlockState(delayed.west())))
                        .thenExecute(()-> {
                            helper.assertTrue(service.state(id).participants().stream().filter(TurnParticipant::environment).count()==1,
                                "managed processes duplicated environment seat");
                            service.stop(id); tnt.discard(); item.discard();
                            level.getEntitiesOfClass(PrimedTnt.class,new net.minecraft.world.phys.AABB(delayed).inflate(3)).forEach(Entity::discard);
                            for(long key:forced) {var c=net.minecraft.world.level.ChunkPos.unpack(key);level.setChunkForced(c.x(),c.z(),false);}
                            forced.clear();
                        }).thenSucceed();
                });
            });
        });
        helper.runAtTickTime(99,()-> {
            helper.assertTrue(level.getBlockState(delayed).isAir(), "delayed TNT not primed: "+level.getBlockState(delayed.west())+" queued="+level.getBlockTicks().hasScheduledTick(delayed.west(),Blocks.REPEATER)+" ticking="+level.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.pack(delayed.west())));
            level.getServer().tickRateManager().setFrozen(false);
            UUID id=service.encounterOf(player.getUUID()); if(id!=null)service.stop(id);
            for(long key:forced) {var c=net.minecraft.world.level.ChunkPos.unpack(key);level.setChunkForced(c.x(),c.z(),false);}
            forced.clear();
        });
    }
}
