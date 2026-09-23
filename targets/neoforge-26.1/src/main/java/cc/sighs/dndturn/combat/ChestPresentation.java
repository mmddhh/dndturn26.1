package cc.sighs.dndturn.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Audited normal chest lid signal; trapped/modded chests and simulation events stay queued. */
public final class ChestPresentation {
    private ChestPresentation() {}
    public static boolean accepts(ServerLevel level, BlockPos pos, Block block, int event) {
        return event == 1 && block == Blocks.CHEST && level.hasChunkAt(pos)
            && level.getBlockState(pos).is(Blocks.CHEST)
            && level.getBlockEntity(pos) != null && level.getBlockEntity(pos).getClass() == ChestBlockEntity.class
            && level.tickRateManager().runsNormally()
            && MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos);
    }
    public static void send(ServerLevel level, BlockPos pos, Block block, int count) {
        level.getServer().getPlayerList().broadcast(null, pos.getX(), pos.getY(), pos.getZ(), 64,
            level.dimension(), new ClientboundBlockEventPacket(pos, block, 1, count));
    }
}
