package cc.sighs.dndturn.platform.client.camera;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/** Immutable render-only cells, including air so newly placed terrain uses the same cut. */
public record CutawayMask(Object worldIdentity, Map<Long, Set<Long>> sections) {
    private static final double ENTER_RADIUS = 2, LEAVE_RADIUS = 2.75;
    public CutawayMask {
        var copy = new HashMap<Long, Set<Long>>();
        sections.forEach((key, cells) -> copy.put(key, Set.copyOf(cells)));
        sections = Map.copyOf(copy);
    }

    public boolean hides(BlockPos pos) { return cells(SectionPos.asLong(pos)).contains(pos.asLong()); }
    public Set<Long> cells(long section) { return sections.getOrDefault(section, Set.of()); }

    /** A build reads a 3x3x3 section region, including AO/model neighbors. */
    public boolean matches(CutawayMask other, long section) {
        if (worldIdentity != other.worldIdentity) return false;
        if (this == other) return true;
        for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
            long neighbor = SectionPos.offset(section, x, y, z);
            if (!cells(neighbor).equals(other.cells(neighbor))) return false;
        }
        return true;
    }

    public CutawayMask move(Vec3 camera, Vec3 focus, Predicate<BlockPos> loaded) {
        Vec3 boom = focus.subtract(camera);
        double length = boom.length();
        if (!Double.isFinite(length) || length < 1 || length > 33) return new CutawayMask(worldIdentity, Map.of());
        Vec3 direction = boom.scale(1 / length);
        var next = new HashMap<Long, Set<Long>>();
        // Camera distance is bounded by CameraRig; at most 40^3 cell-center tests.
        BlockPos min = BlockPos.containing(Math.min(camera.x, focus.x) - LEAVE_RADIUS,
            Math.min(camera.y, focus.y) - LEAVE_RADIUS, Math.min(camera.z, focus.z) - LEAVE_RADIUS);
        BlockPos max = BlockPos.containing(Math.max(camera.x, focus.x) + LEAVE_RADIUS,
            Math.max(camera.y, focus.y) + LEAVE_RADIUS, Math.max(camera.z, focus.z) + LEAVE_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            Vec3 relative = Vec3.atCenterOf(pos).subtract(camera);
            double depth = relative.dot(direction);
            boolean wasHidden = hides(pos);
            // Stop before the focus, preserving its floor and terrain behind it.
            if (depth > length - (wasHidden ? .5 : 1)) continue;
            double radius = wasHidden ? LEAVE_RADIUS : ENTER_RADIUS;
            double along = Math.clamp(depth, 0, length);
            if (relative.subtract(direction.scale(along)).lengthSqr() > radius * radius || !loaded.test(pos)) continue;
            next.computeIfAbsent(SectionPos.asLong(pos), key -> new HashSet<>()).add(pos.asLong());
        }
        return sections.equals(next) ? this : new CutawayMask(worldIdentity, next);
    }

    /** Boundary cells invalidate adjacent sections too: faces, fluid edges and AO. */
    public Set<Long> changedSections(CutawayMask next) {
        var changed = new HashSet<Long>();
        var keys = new HashSet<>(sections.keySet());
        keys.addAll(next.sections.keySet());
        for (long section : keys) {
            Set<Long> before = cells(section), after = next.cells(section);
            if (before.equals(after)) continue;
            var difference = new HashSet<>(before);
            for (long pos : after) if (!difference.add(pos)) difference.remove(pos);
            for (long packed : difference) {
                BlockPos pos = BlockPos.of(packed);
                for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++)
                    changed.add(SectionPos.asLong(pos.offset(x, y, z)));
            }
        }
        return Set.copyOf(changed);
    }
}
