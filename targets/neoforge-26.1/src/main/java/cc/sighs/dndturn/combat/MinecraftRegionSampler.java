package cc.sighs.dndturn.combat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Captures loaded entity centers on the server thread, then builds a value-only region. */
public final class MinecraftRegionSampler {
    private MinecraftRegionSampler() {}

    public static EncounterRegion capture(ServerLevel level, Entity initiator,
                                          EncounterRegion.Discovery discovery,
                                          double radius, long regionVersion, int maxSampledChunks,
                                          int maxAnchors) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("region capture requires server thread");
        if (initiator.level() != level) throw new IllegalArgumentException("initiator dimension");
        if (maxSampledChunks < 1) throw new IllegalArgumentException("chunk limit");
        if (maxAnchors < 1) throw new IllegalArgumentException("anchor limit");
        int minChunkX = chunkOf(discovery.minX());
        int maxChunkX = chunkOf(discovery.maxX());
        int minChunkZ = chunkOf(discovery.minZ());
        int maxChunkZ = chunkOf(discovery.maxZ());
        long chunkCount = ((long) maxChunkX - minChunkX + 1) * ((long) maxChunkZ - minChunkZ + 1);
        if (chunkCount > maxSampledChunks) throw new IllegalArgumentException("discovery exceeds chunk limit");
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!level.hasChunkAt(new BlockPos(x << 4, 0, z << 4)))
                    throw new IllegalStateException("discovery includes unloaded chunk");
            }
        }
        AABB search = new AABB(discovery.minX(), discovery.minY(), discovery.minZ(),
            discovery.maxX(), discovery.maxY(), discovery.maxZ());
        Map<UUID, EncounterRegion.Anchor> anchors = new LinkedHashMap<>();
        for (Entity entity : level.getEntities((Entity) null, search, ignored -> true)) {
            EncounterRegion.Anchor anchor = centerOf(entity);
            if (discovery.contains(anchor.center())) {
                anchors.putIfAbsent(anchor.entityId(), anchor);
                if (anchors.size() > maxAnchors) throw new IllegalStateException("discovery exceeds anchor limit");
            }
        }
        EncounterRegion.Anchor first = centerOf(initiator);
        if (!discovery.contains(first.center())) throw new IllegalArgumentException("initiator outside discovery");
        anchors.put(first.entityId(), first);
        if (anchors.size() > maxAnchors) throw new IllegalStateException("discovery exceeds anchor limit");
        List<EncounterRegion.Anchor> ordered = new ArrayList<>(anchors.values());
        ordered.sort((left, right) -> left.entityId().compareTo(right.entityId()));
        return EncounterRegion.generate(level.dimension().identifier().toString(), discovery,
            ordered, radius, regionVersion);
    }

    private static EncounterRegion.Anchor centerOf(Entity entity) {
        Vec3 center = entity.getBoundingBox().getCenter();
        return new EncounterRegion.Anchor(entity.getUUID(),
            new EncounterRegion.Point(center.x, center.y, center.z));
    }

    private static int chunkOf(double coordinate) {
        double chunk = Math.floor(coordinate / 16.0);
        if (chunk < Integer.MIN_VALUE / 16.0 || chunk > Integer.MAX_VALUE / 16.0)
            throw new IllegalArgumentException("discovery exceeds block coordinate range");
        return (int) chunk;
    }
}
