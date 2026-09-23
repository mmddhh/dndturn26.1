package cc.sighs.dndturn.combat;

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
        ServerCombatService service = ServerCombatService.existing(serverLevel.getServer());
        return service == null ? level.getGameTime() : service.environmentTimeAt(serverLevel, pos);
    }

    public static Policy policy(boolean client, Channel channel) {
        if (client) return Policy.PASS_THROUGH;
        return channel == Channel.SYNCHRONOUS ? Policy.DIRECT : Policy.ENVIRONMENT;
    }

    public static boolean allowed(ServerLevel level, BlockPos pos, Policy policy) {
        return policy != Policy.ENVIRONMENT || level.tickRateManager().runsNormally()
            && !MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos);
    }

    public static Policy tickerPolicy(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        Block block = level.getBlockState(pos).getBlock();
        // Exact .84 vanilla maintenance callbacks, not a whole inheritance hierarchy.
        if (entity != null && ((entity.getClass() == SignBlockEntity.class
                && (block.getClass() == StandingSignBlock.class || block.getClass() == WallSignBlock.class))
            || (entity.getClass() == HangingSignBlockEntity.class
                && (block.getClass() == CeilingHangingSignBlock.class || block.getClass() == WallHangingSignBlock.class))))
            return Policy.PASS_THROUGH;
        return policy(false, Channel.BLOCK_ENTITY);
    }

    public static Policy eventPolicy(ServerLevel level, BlockEventData event) {
        if (event.block() == Blocks.DECORATED_POT && event.paramA() == 1
            && event.paramB() >= 0 && event.paramB() < DecoratedPotBlockEntity.WobbleStyle.values().length
            && level.getBlockEntity(event.pos()) instanceof DecoratedPotBlockEntity pot
            && pot.getClass() == DecoratedPotBlockEntity.class)
            return Policy.PASS_THROUGH;
        return policy(false, Channel.BLOCK_EVENT);
    }
}
