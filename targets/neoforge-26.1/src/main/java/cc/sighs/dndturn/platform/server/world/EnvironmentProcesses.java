package cc.sighs.dndturn.platform.server.world;

import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;

/** Process classification is independent of the player who originally caused an update. */
public final class EnvironmentProcesses {
    public enum Policy { DIRECT, ENVIRONMENT, PASS_THROUGH }
    public enum Channel { SYNCHRONOUS, SCHEDULED_BLOCK, SCHEDULED_FLUID, RANDOM,
        BLOCK_ENTITY, BLOCK_EVENT, HOPPER_TRANSFER, LILY_PAD_CONTACT }

    private EnvironmentProcesses() {}

    /** Only registered callbacks use this projection; vanilla world time is never replaced. */
    public static long time(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return level.getGameTime();
        EncounterRuntime service = ServerRuntime.existingEncounter(serverLevel.getServer());
        return service == null ? level.getGameTime() : service.environmentTimeAt(serverLevel, pos);
    }

    public static Policy policy(boolean client, Channel channel) {
        if (client) return Policy.PASS_THROUGH;
        return channel == Channel.SYNCHRONOUS ? Policy.DIRECT : Policy.ENVIRONMENT;
    }

    public static Policy tickerPolicy(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        Block block = level.getBlockState(pos).getBlock();
        // Native sign maintenance also applies to subclasses.
        if (entity != null && ((entity instanceof SignBlockEntity
                && (block instanceof StandingSignBlock || block instanceof WallSignBlock))
            || (entity instanceof HangingSignBlockEntity
                && (block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock))))
            return Policy.PASS_THROUGH;
        return policy(false, Channel.BLOCK_ENTITY);
    }

    public static Policy eventPolicy(ServerLevel level, BlockEventData event) {
        if (event.block() instanceof net.minecraft.world.level.block.DecoratedPotBlock && event.paramA() == 1
            && event.paramB() >= 0 && event.paramB() < DecoratedPotBlockEntity.WobbleStyle.values().length
            && level.getBlockEntity(event.pos()) instanceof DecoratedPotBlockEntity)
            return Policy.PASS_THROUGH;
        return policy(false, Channel.BLOCK_EVENT);
    }
}
