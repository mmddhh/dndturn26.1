package cc.sighs.dndturn.platform.client.camera;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/** Immutable render-only cells, including air so newly placed terrain uses the same cut. */
public final class CutawayMask {
    private static final double ENTER_RADIUS = 2, LEAVE_RADIUS = 2.75;
    private final Object worldIdentity;
    private final Map<Long, Cells> sections;
    private final Cells[] local;
    private final int localX, localY, localZ;

    public CutawayMask(Object worldIdentity, Map<Long, Cells> sections) {
        this(worldIdentity, sections, null, 0, 0, 0);
    }

    private CutawayMask(Object worldIdentity, Map<Long, Cells> sections, Cells[] local, int x, int y, int z) {
        this.worldIdentity = worldIdentity;
        this.sections = Map.copyOf(sections);
        this.local = local;
        localX = x; localY = y; localZ = z;
    }

    public Object worldIdentity() { return worldIdentity; }
    public Map<Long, Cells> sections() { return sections; }
    public boolean hides(BlockPos pos) {
        return !sections.isEmpty() && cells(SectionPos.asLong(pos)).contains(pos);
    }
    public Cells cells(long section) {
        if (local == null) return sections.getOrDefault(section, Cells.EMPTY);
        int x = SectionPos.x(section) - localX + 1, y = SectionPos.y(section) - localY + 1,
            z = SectionPos.z(section) - localZ + 1;
        return x < 0 || x > 2 || y < 0 || y > 2 || z < 0 || z > 2
            ? Cells.EMPTY : local[(z * 3 + y) * 3 + x];
    }

    /** Fixed lookup slots for the compiler's 3x3x3 region; immutable cells are shared. */
    public CutawayMask local(long section) {
        if (sections.isEmpty()) return this;
        Cells[] slots = new Cells[27];
        var selected = new HashMap<Long, Cells>();
        int index = 0;
        for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
            long neighbor = SectionPos.offset(section, x, y, z);
            Cells cells = cells(neighbor);
            slots[index++] = cells;
            if (!cells.isEmpty()) selected.put(neighbor, cells);
        }
        return new CutawayMask(worldIdentity, selected, slots, SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
    }

    public boolean matches(CutawayMask other, long section) {
        if (worldIdentity != other.worldIdentity) return false;
        if (this == other) return true;
        if (sections.isEmpty() && other.sections.isEmpty()) return true;
        for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
            long neighbor = SectionPos.offset(section, x, y, z);
            if (!cells(neighbor).equals(other.cells(neighbor))) return false;
        }
        return true;
    }

    public CutawayMask move(Vec3 camera, Vec3 focus, Predicate<BlockPos> loaded) {
        double dx = focus.x - camera.x, dy = focus.y - camera.y, dz = focus.z - camera.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length) || length < 1 || length > 33) return sections.isEmpty() ? this : new CutawayMask(worldIdentity, Map.of());
        dx /= length; dy /= length; dz /= length;
        var builders = new HashMap<Long, long[]>();
        BlockPos min = minimum(camera, focus), max = maximum(camera, focus);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            double rx = pos.getX() + .5 - camera.x, ry = pos.getY() + .5 - camera.y, rz = pos.getZ() + .5 - camera.z;
            double depth = rx * dx + ry * dy + rz * dz;
            boolean wasHidden = hides(pos);
            if (depth > length - (wasHidden ? .5 : 1)) continue;
            double radius = wasHidden ? LEAVE_RADIUS : ENTER_RADIUS;
            double along = Math.clamp(depth, 0, length);
            rx -= dx * along; ry -= dy * along; rz -= dz * along;
            if (rx * rx + ry * ry + rz * rz > radius * radius || !loaded.test(pos)) continue;
            long[] words = builders.computeIfAbsent(SectionPos.asLong(pos), key -> new long[64]);
            int bit = Cells.index(pos);
            words[bit >>> 6] |= 1L << (bit & 63);
        }
        var next = new HashMap<Long, Cells>();
        builders.forEach((section, words) -> {
            Cells before = cells(section);
            next.put(section, Arrays.equals(before.words, words) ? before : new Cells(words));
        });
        return sections.equals(next) ? this : new CutawayMask(worldIdentity, next);
    }

    private static BlockPos minimum(Vec3 a, Vec3 b) {
        return BlockPos.containing(Math.min(a.x, b.x) - LEAVE_RADIUS, Math.min(a.y, b.y) - LEAVE_RADIUS, Math.min(a.z, b.z) - LEAVE_RADIUS);
    }
    private static BlockPos maximum(Vec3 a, Vec3 b) {
        return BlockPos.containing(Math.max(a.x, b.x) + LEAVE_RADIUS, Math.max(a.y, b.y) + LEAVE_RADIUS, Math.max(a.z, b.z) + LEAVE_RADIUS);
    }

    /** Only boundary cells can affect another section, including diagonal AO neighbors. */
    public Set<Long> changedSections(CutawayMask next) {
        if (this == next) return Set.of();
        var changed = new HashSet<Long>();
        var keys = new HashSet<>(sections.keySet());
        keys.addAll(next.sections.keySet());
        for (long section : keys) {
            Cells before = cells(section), after = next.cells(section);
            if (before == after) continue;
            for (int word = 0; word < 64; word++) {
                long difference = before.words[word] ^ after.words[word];
                while (difference != 0) {
                    int bit = word * 64 + Long.numberOfTrailingZeros(difference);
                    difference &= difference - 1;
                    int x = bit & 15, z = (bit >>> 4) & 15, y = bit >>> 8;
                    for (int oz = z == 0 ? -1 : 0; oz <= (z == 15 ? 1 : 0); oz++)
                        for (int oy = y == 0 ? -1 : 0; oy <= (y == 15 ? 1 : 0); oy++)
                            for (int ox = x == 0 ? -1 : 0; ox <= (x == 15 ? 1 : 0); ox++)
                                changed.add(SectionPos.offset(section, ox, oy, oz));
                }
            }
        }
        return Set.copyOf(changed);
    }

    /** No mutable storage escapes the snapshot. */
    public static final class Cells {
        public static final Cells EMPTY = new Cells(new long[64]);
        private final long[] words;
        private final boolean empty;
        public Cells(long[] words) {
            if (words.length != 64) throw new IllegalArgumentException("section needs 4096 bits");
            this.words = words.clone();
            boolean empty = true;
            for (long word : this.words) if (word != 0) { empty = false; break; }
            this.empty = empty;
        }
        private static int index(BlockPos pos) { return ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15); }
        public boolean contains(BlockPos pos) { int bit = index(pos); return (words[bit >>> 6] & (1L << (bit & 63))) != 0; }
        public boolean isEmpty() { return empty; }
        @Override public boolean equals(Object other) { return this == other || other instanceof Cells cells && Arrays.equals(words, cells.words); }
        @Override public int hashCode() { return Arrays.hashCode(words); }
    }

    /** Render-thread cache; loading is checked by column even when the pose is stationary. */
    public static final class Updates {
        private Vec3 camera, focus;
        private CutawayMask result;
        private long columns;
        public CutawayMask update(CutawayMask current, Vec3 camera, Vec3 focus,
                                  Predicate<BlockPos> columnLoaded, Predicate<BlockPos> cellLoaded) {
            double length = camera.distanceTo(focus);
            if (!Double.isFinite(length) || length < 1 || length > 33) {
                reset();
                return current.move(camera, focus, cellLoaded);
            }
            BlockPos min = minimum(camera, focus), max = maximum(camera, focus);
            long columns = 0;
            int bit = 0;
            // A <=33-block boom plus margins spans at most four columns per axis.
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++)
                for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
                    if (columnLoaded.test(new BlockPos(x << 4, 0, z << 4))) columns |= 1L << bit;
                    bit++;
                }
            if (current == result && camera.equals(this.camera) && focus.equals(this.focus) && columns == this.columns) return current;
            CutawayMask next = current.move(camera, focus, cellLoaded);
            this.camera = camera; this.focus = focus; this.columns = columns; result = next;
            return next;
        }
        public void reset() { camera = null; focus = null; result = null; columns = 0; }
    }
}
