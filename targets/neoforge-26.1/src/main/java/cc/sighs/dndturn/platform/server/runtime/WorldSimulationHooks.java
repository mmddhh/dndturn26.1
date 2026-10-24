package cc.sighs.dndturn.platform.server.runtime;

import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Fixed-version world boundaries. Hooks never create a server runtime. */
public final class WorldSimulationHooks {

    /** Only registered callbacks use this projection; vanilla world time is never replaced. */
    public static long environmentTimeAt(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return level.getGameTime();
        var service = ServerRuntime.existingEncounter(serverLevel.getServer());
        return service == null ? level.getGameTime() : service.world().environmentTimeAt(serverLevel, pos);
    }
    private WorldSimulationHooks() {}
    public static void beforeLevelTick(ServerLevel level) {
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        if (runtime != null) runtime.world().beforeLevelTick(level);
    }
    public static void beforeBlockQueue(ServerLevel level) {
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        if (runtime != null) runtime.world().beforeBlockQueue(level);
    }
    public static void beforeFluidQueue(ServerLevel level) {
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        if (runtime != null) runtime.world().beforeFluidQueue(level);
    }
    public static void afterLevelTick(ServerLevel level) {
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        if (runtime != null) runtime.world().afterLevelTick(level);
    }
    public static void abortLevelTick(ServerLevel level, Throwable failure) {
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        if (runtime != null) runtime.world().abortLevelTick(level, failure);
    }
    public static void beforeChunkUnload(ServerLevel level, ChunkPos chunk) {
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        if (runtime != null) runtime.world().beforeChunkUnload(level, chunk);
    }
    public static ChunkAccess.PackedTicks savedTicks(ServerLevel level, ChunkPos chunk, ChunkAccess.PackedTicks vanilla) {
        var runtime = ServerRuntime.existingEncounter(level.getServer());
        return runtime == null ? vanilla : runtime.world().savedTicksForChunk(level, chunk, vanilla);
    }
    public static UUID domainAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        var runtime = ServerRuntime.existingEncounter(server.getServer());
        return runtime == null ? null : runtime.world().encounterAtBlock(server, pos);
    }
}
