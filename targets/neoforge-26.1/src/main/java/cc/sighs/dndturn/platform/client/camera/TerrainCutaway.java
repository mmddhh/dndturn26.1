package cc.sighs.dndturn.platform.client.camera;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Owns only visual cells. Workers receive immutable values, never the live camera/world. */
public final class TerrainCutaway {
    private static volatile CutawayMask requested = new CutawayMask(new Object(), Map.of());
    private static final Map<Long, CutawayMask.Cells> displayed = new ConcurrentHashMap<>();
    private static ClientLevel level;
    private static long lastUpdate;
    private static final CutawayMask.Updates updates = new CutawayMask.Updates();
    private TerrainCutaway() {}

    public static CutawayStampAccess.Stamp capture(int x, int y, int z) {
        CutawayMask mask = requested;
        long section = SectionPos.asLong(x, y, z);
        return new CutawayStampAccess.Stamp(mask.local(section), section);
    }

    public static synchronized void forget(SectionRenderDispatcher.RenderSection section) {
        if (section.getSectionMesh() instanceof CutawayStampAccess access && access.dndturn$cutawayStamp() != null) {
            var stamp = access.dndturn$cutawayStamp();
            if (stamp.mask().worldIdentity() == requested.worldIdentity()) displayed.remove(stamp.section());
        }
    }
    public static boolean hidden(BlockPos pos) {
        return !displayed.isEmpty() && displayed.getOrDefault(SectionPos.asLong(pos), CutawayMask.Cells.EMPTY).contains(pos);
    }

    public static void filterBreaking(net.minecraft.client.renderer.state.level.LevelRenderState state) {
        state.blockBreakingRenderStates.removeIf(block -> hidden(block.blockPos()));
    }

    private static void reconcileWorld() {
        ClientLevel now = Minecraft.getInstance().level;
        if (level == now) return;
        level = now;
        requested = new CutawayMask(new Object(), Map.of());
        displayed.clear();
        lastUpdate = 0;
        updates.reset();
    }

    public static synchronized void update(Vec3 camera, Vec3 focus) {
        reconcileWorld();
        if (level == null || camera == null || focus == null) return;
        long now = System.nanoTime();
        if (now - lastUpdate < 100_000_000L) return;
        lastUpdate = now;
        publish(updates.update(requested, camera, focus, level::hasChunkAt,
            pos -> level.isInsideBuildHeight(pos) && level.hasChunkAt(pos)));
    }

    public static synchronized void clear() {
        reconcileWorld();
        if (!requested.sections().isEmpty()) publish(new CutawayMask(requested.worldIdentity(), Map.of()));
        lastUpdate = 0;
        updates.reset();
    }

    private static void publish(CutawayMask next) {
        if (requested == next) return;
        var changed = requested.changedSections(next);
        requested = next;
        if (level == null) return;
        var renderer = Minecraft.getInstance().levelRenderer;
        for (long section : changed)
            renderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
    }

    /** The caller releases the returned mesh. A stale upload releases itself, not the visible mesh. */
    public static synchronized SectionMesh install(SectionRenderDispatcher.RenderSection section, SectionMesh mesh,
                                                   Supplier<SectionMesh> install) {
        if (mesh instanceof CutawayStampAccess access && access.dndturn$cutawayStamp() != null) {
            var stamp = access.dndturn$cutawayStamp();
            if (stamp.section() != section.getSectionNode() || !stamp.mask().matches(requested, stamp.section())) {
                section.setDirty(false);
                return mesh;
            }
            SectionMesh old = install.get();
            CutawayMask.Cells cells = stamp.mask().cells(stamp.section());
            if (cells.isEmpty()) displayed.remove(stamp.section());
            else displayed.put(stamp.section(), cells);
            return old;
        }
        return install.get();
    }

    /** Display picking skips only cells whose cut mesh has actually been installed. No rule LOS changes. */
    public static BlockHitResult clip(ClientLevel world, ClipContext context) {
        return new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos pos) {
                return hidden(pos) ? Blocks.AIR.defaultBlockState() : world.getBlockState(pos);
            }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return hidden(pos) ? null : world.getBlockEntity(pos); }
            @Override public int getHeight() { return world.getHeight(); }
            @Override public int getMinY() { return world.getMinY(); }
        }.clip(context);
    }
}
