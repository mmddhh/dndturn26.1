package cc.sighs.dndturn.combat;

import cc.sighs.dndturn.mixin.LevelTicksAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.ScheduledTick;

/** One in-memory owner for scheduled ticks temporarily removed from vanilla's loaded chunk queues. */
public final class RegionalScheduledTicks {
    private static final Map<LevelTicks<?>, RegionalScheduledTicks> OWNERS = new IdentityHashMap<>();
    interface RegionAccess {
        UUID encounterAtBlock(ServerLevel level, BlockPos pos);
        UUID activeEnvironmentStep(ServerLevel level);
        Set<Long> candidateRegionChunks(ServerLevel level);
        boolean isBlockSimulationPaused(ServerLevel level, BlockPos pos);
    }
    private final RegionAccess owner;
    private final Map<LevelTicks<?>, QueueState<?>> queues = new IdentityHashMap<>();
    private boolean transferring;

    private record TickKey(BlockPos pos, Object type) {
        @Override public boolean equals(Object other) {
            return other instanceof TickKey key && pos.equals(key.pos) && type == key.type;
        }
        @Override public int hashCode() { return 31 * pos.hashCode() + System.identityHashCode(type); }
    }

    private static final class QueueState<T> {
        final ServerLevel level;
        final LevelTicks<T> queue;
        final Map<TickKey, Held<T>> byKey = new HashMap<>();
        final Map<Long, LinkedHashMap<TickKey, Held<T>>> byChunk = new HashMap<>();
        QueueState(ServerLevel level, LevelTicks<T> queue) { this.level = level; this.queue = queue; }
        void add(Held<T> entry) {
            TickKey key = new TickKey(entry.original.pos(), entry.original.type());
            if (byKey.putIfAbsent(key, entry) == null)
                byChunk.computeIfAbsent(ChunkPos.pack(key.pos()), ignored -> new LinkedHashMap<>()).put(key, entry);
        }
        void remove(Held<T> entry) {
            TickKey key = new TickKey(entry.original.pos(), entry.original.type());
            byKey.remove(key);
            Map<TickKey, Held<T>> entries = byChunk.get(ChunkPos.pack(key.pos()));
            if (entries != null) {
                entries.remove(key);
                if (entries.isEmpty()) byChunk.remove(ChunkPos.pack(key.pos()));
            }
        }
        @SuppressWarnings("unchecked")
        void removeUnknown(Held<?> entry) { remove((Held<T>) entry); }
        List<Held<T>> inChunk(long chunk) {
            Map<TickKey, Held<T>> entries = byChunk.get(chunk);
            return entries == null ? List.of() : List.copyOf(entries.values());
        }
    }

    private static final class Held<T> {
        final ServerLevel level;
        final LevelTicks<T> queue;
        final ScheduledTick<T> original;
        final UUID encounterId;
        final UUID bornStep;
        long remaining;

        Held(ServerLevel level, LevelTicks<T> queue, ScheduledTick<T> original,
             UUID encounterId, long remaining, UUID bornStep) {
            this.level = level;
            this.queue = queue;
            this.original = original;
            this.encounterId = encounterId;
            this.remaining = remaining;
            this.bornStep = bornStep;
        }

        void release(long triggerTick) {
            queue.schedule(new ScheduledTick<>(original.type(), original.pos(), triggerTick,
                original.priority(), original.subTickOrder()));
        }
    }

    RegionalScheduledTicks(RegionAccess owner) { this.owner = owner; }

    public static boolean isHeld(LevelTicks<?> queue, BlockPos pos, Object type) {
        RegionalScheduledTicks scheduler = OWNERS.get(queue);
        return scheduler != null && scheduler.hasHeld(queue, pos, type);
    }

    public static <T> boolean holdScheduled(LevelTicks<T> queue, ScheduledTick<T> tick) {
        RegionalScheduledTicks scheduler = OWNERS.get(queue);
        return scheduler != null && scheduler.holdDirect(queue, tick);
    }

    public static int heldCount(LevelTicks<?> queue) {
        RegionalScheduledTicks scheduler = OWNERS.get(queue);
        QueueState<?> state = scheduler == null ? null : scheduler.queues.get(queue);
        return state == null ? 0 : state.byKey.size();
    }

    public static void clearHeld(LevelTicks<?> queue, BoundingBox area) {
        RegionalScheduledTicks scheduler = OWNERS.get(queue);
        if (scheduler != null) scheduler.removeArea(queue, area);
    }

    /** Snapshot both vanilla and held source entries before scheduling any destination entry. */
    public static <T> boolean copyAreaSnapshot(LevelTicks<T> destination, LevelTicks<T> source,
                                               BoundingBox area, Vec3i offset) {
        RegionalScheduledTicks scheduler = OWNERS.get(source);
        if (scheduler == null) return false;
        @SuppressWarnings("unchecked") QueueState<T> sourceState = (QueueState<T>) scheduler.queues.get(source);
        if (sourceState == null) return false;
        List<Held<T>> heldSources = scheduler.entriesInArea(sourceState, area);
        if (heldSources.isEmpty()) return false;
        RegionalScheduledTicks destinationOwner = OWNERS.get(destination);
        @SuppressWarnings("unchecked") QueueState<T> destinationState = destinationOwner == null ? null
            : (QueueState<T>) destinationOwner.queues.get(destination);
        long destinationTime = destinationState == null ? sourceState.level.getGameTime()
            : destinationState.level.getGameTime();
        Map<TickKey, ScheduledTick<T>> snapshot = new LinkedHashMap<>();
        LevelTicksAccessor<T> accessor = (LevelTicksAccessor<T>) source;
        for (ScheduledTick<T> tick : accessor.dndturn$alreadyRunThisTick())
            addCopySource(snapshot, area, tick);
        for (ScheduledTick<T> tick : accessor.dndturn$toRunThisTick())
            addCopySource(snapshot, area, tick);
        for (var container : accessor.dndturn$containers().values())
            container.getAll().forEach(tick -> addCopySource(snapshot, area, tick));
        for (Held<T> held : heldSources)
            addCopySource(snapshot, area, new ScheduledTick<>(held.original.type(), held.original.pos(),
                destinationTime + held.remaining, held.original.priority(), held.original.subTickOrder()));
        List<ScheduledTick<T>> copies = new ArrayList<>(snapshot.values());
        copies.sort(ScheduledTick.DRAIN_ORDER);
        if (!copies.isEmpty()) {
            long minOrder = copies.stream().mapToLong(ScheduledTick::subTickOrder).min().orElseThrow();
            long maxOrder = copies.stream().mapToLong(ScheduledTick::subTickOrder).max().orElseThrow();
            for (ScheduledTick<T> tick : copies) destination.schedule(new ScheduledTick<>(tick.type(),
                tick.pos().offset(offset), tick.triggerTick(), tick.priority(),
                tick.subTickOrder() - minOrder + maxOrder + 1L));
        }
        return true;
    }

    private static <T> void addCopySource(Map<TickKey, ScheduledTick<T>> snapshot,
                                          BoundingBox area, ScheduledTick<T> tick) {
        if (area.isInside(tick.pos()))
            snapshot.putIfAbsent(new TickKey(tick.pos(), tick.type()), tick);
    }

    private <T> QueueState<T> state(ServerLevel level, LevelTicks<T> queue) {
        @SuppressWarnings("unchecked") QueueState<T> existing = (QueueState<T>) queues.get(queue);
        if (existing != null) return existing;
        QueueState<T> created = new QueueState<>(level, queue);
        queues.put(queue, created);
        OWNERS.put(queue, this);
        return created;
    }

    void captureAll(ServerLevel level) {
        capture(level, level.getBlockTicks(), level.getGameTime());
        capture(level, level.getFluidTicks(), level.getGameTime());
    }

    private <T> boolean holdDirect(LevelTicks<T> queue, ScheduledTick<T> tick) {
        if (transferring) return false;
        @SuppressWarnings("unchecked") QueueState<T> state = (QueueState<T>) queues.get(queue);
        if (state == null) return false;
        LevelChunkTicks<T> container = ((LevelTicksAccessor<T>) queue).dndturn$containers()
            .get(ChunkPos.pack(tick.pos()));
        if (container == null) return false;
        if (container.hasScheduledTick(tick.pos(), tick.type())) return true;
        UUID encounterId = owner.encounterAtBlock(state.level, tick.pos());
        if (encounterId == null) return state.byKey.containsKey(new TickKey(tick.pos(), tick.type()));
        TickKey key = new TickKey(tick.pos(), tick.type());
        if (!state.byKey.containsKey(key)) {
            if (!markChunkUnsaved(state.level, tick.pos())) return false;
            state.add(new Held<>(state.level, queue, tick,
                encounterId, Math.max(0, tick.triggerTick() - state.level.getGameTime()),
                owner.activeEnvironmentStep(state.level)));
        }
        return true;
    }

    void beforeBlockQueue(ServerLevel level, UUID activeEncounter) {
        LevelTicks<net.minecraft.world.level.block.Block> queue = level.getBlockTicks();
        capture(level, queue, level.getGameTime());
        if (activeEncounter != null) releaseDue(level, queue, level.getGameTime(), activeEncounter);
    }

    void beforeFluidQueue(ServerLevel level, UUID activeEncounter) {
        LevelTicks<net.minecraft.world.level.material.Fluid> queue = level.getFluidTicks();
        capture(level, queue, level.getGameTime());
        if (activeEncounter != null) releaseDue(level, queue, level.getGameTime(), activeEncounter);
    }

    private <T> void capture(ServerLevel level, LevelTicks<T> queue, long now) {
        QueueState<T> state = state(level, queue);
        var containers = ((LevelTicksAccessor<T>) queue).dndturn$containers();
        for (long key : owner.candidateRegionChunks(level)) {
            LevelChunkTicks<T> container = containers.get(key);
            if (container == null) continue;
            net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(
                ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk == null) continue;
            List<ScheduledTick<T>> captured = container.getAll().filter(tick ->
                owner.encounterAtBlock(level, tick.pos()) != null).toList();
            if (captured.isEmpty()) continue;
            chunk.markUnsaved();
            Set<ScheduledTick<T>> selected = Set.copyOf(captured);
            container.removeIf(selected::contains);
            for (ScheduledTick<T> tick : captured) {
                state.add(new Held<>(level, queue, tick, owner.encounterAtBlock(level, tick.pos()),
                    Math.max(0, tick.triggerTick() - now), owner.activeEnvironmentStep(level)));
            }
        }
    }

    private <T> void releaseDue(ServerLevel level, LevelTicks<T> queue, long now, UUID activeEncounter) {
        @SuppressWarnings("unchecked") QueueState<T> state = (QueueState<T>) queues.get(queue);
        if (state == null) return;
        UUID stepId = owner.activeEnvironmentStep(level);
        for (Held<T> entry : List.copyOf(state.byKey.values())) {
            if (!entry.encounterId.equals(activeEncounter)) continue;
            if (owner.isBlockSimulationPaused(level, entry.original.pos())) continue;
            if (entry.remaining > 0 && !java.util.Objects.equals(stepId, entry.bornStep)) {
                entry.remaining--;
                markChunkUnsaved(level, entry.original.pos());
            }
            if (entry.remaining == 0) {
                transfer(state, entry, now);
            }
        }
    }

    void releaseEncounter(UUID encounterId) {
        for (QueueState<?> state : List.copyOf(queues.values()))
            releaseMatching(state, entry -> entry.encounterId.equals(encounterId));
    }

    /** Validate all transfers before the rule owner commits the corresponding merge. */
    boolean canRebindMerge(Set<UUID> sources, EncounterRegion region) {
        for (QueueState<?> state : queues.values()) {
            for (Held<?> entry : state.byKey.values()) {
                if (!sources.contains(entry.encounterId)) continue;
                if (!entry.level.dimension().identifier().toString().equals(region.dimension())) return false;
                if (!((LevelTicksAccessor<?>) state.queue).dndturn$containers()
                    .containsKey(ChunkPos.pack(entry.original.pos()))
                    || entry.level.getChunkSource().getChunkNow(
                        entry.original.pos().getX() >> 4, entry.original.pos().getZ() >> 4) == null)
                    return false;
            }
        }
        return true;
    }

    /** Called on the server thread after the engine and region projection switch together. */
    void rebindMerge(Set<UUID> sources, UUID primary, EncounterRegion region) {
        for (QueueState<?> state : List.copyOf(queues.values())) {
            for (Held<?> entry : List.copyOf(state.byKey.values())) {
                if (!sources.contains(entry.encounterId)) continue;
                if (!region.containsBlock(entry.original.pos().getX(), entry.original.pos().getY(),
                    entry.original.pos().getZ())) {
                    transferUnknown(state, entry, entry.level.getGameTime() + entry.remaining);
                } else if (!entry.encounterId.equals(primary)) {
                    rebindUnknown(state, entry, primary);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void rebindUnknown(QueueState<?> state, Held<?> entry, UUID primary) {
        rebind((QueueState) state, (Held) entry, primary);
    }

    private <T> void rebind(QueueState<T> state, Held<T> entry, UUID primary) {
        state.remove(entry);
        state.add(new Held<>(entry.level, entry.queue, entry.original,
            primary, entry.remaining, entry.bornStep));
        markChunkUnsaved(entry.level, entry.original.pos());
    }

    void releaseChunk(ServerLevel level, ChunkPos chunk) {
        for (QueueState<?> state : List.copyOf(queues.values())) {
            if (state.level != level) continue;
            for (Held<?> entry : state.inChunk(chunk.pack())) transferUnknown(state, entry,
                level.getGameTime() + entry.remaining);
        }
    }

    void releaseAll() {
        RuntimeException releaseFailure = null;
        try {
            for (QueueState<?> state : List.copyOf(queues.values())) {
                for (Held<?> entry : List.copyOf(state.byKey.values())) {
                    try { transferUnknown(state, entry, entry.level.getGameTime() + entry.remaining); }
                    catch (RuntimeException failure) {
                        if (releaseFailure == null) releaseFailure = failure;
                        else releaseFailure.addSuppressed(failure);
                    }
                }
            }
        } finally {
            // Shutdown persists held evidence first. Failed transfers must not retain a dead server.
            OWNERS.entrySet().removeIf(entry -> entry.getValue() == this);
            queues.clear();
        }
        if (releaseFailure != null) throw releaseFailure;
    }

    private void releaseMatching(QueueState<?> state, Predicate<Held<?>> predicate) {
        for (Held<?> entry : List.copyOf(state.byKey.values())) {
            if (!predicate.test(entry)) continue;
            transferUnknown(state, entry, entry.level.getGameTime() + entry.remaining);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void transferUnknown(QueueState<?> state, Held<?> entry, long when) {
        transfer((QueueState) state, (Held) entry, when);
    }

    private <T> void transfer(QueueState<T> state, Held<T> entry, long when) {
        if (!((LevelTicksAccessor<T>) state.queue).dndturn$containers()
            .containsKey(ChunkPos.pack(entry.original.pos())))
            throw new IllegalStateException("held tick chunk container is not loaded");
        transferring = true;
        try {
            entry.release(when);
            state.remove(entry);
            markChunkUnsaved(entry.level, entry.original.pos());
        } finally {
            transferring = false;
        }
    }

    private static boolean markChunkUnsaved(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(
            pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        chunk.markUnsaved();
        return true;
    }

    boolean hasHeld(LevelTicks<?> queue, BlockPos pos, Object type) {
        QueueState<?> state = queues.get(queue);
        return state != null && state.byKey.containsKey(new TickKey(pos, type));
    }

    <T> List<SavedTick<T>> savedInChunk(LevelTicks<T> queue, ChunkPos chunk) {
        @SuppressWarnings("unchecked") QueueState<T> state = (QueueState<T>) queues.get(queue);
        if (state == null) return List.of();
        return state.inChunk(chunk.pack()).stream()
            .sorted((first, second) -> ScheduledTick.DRAIN_ORDER.compare(first.original, second.original))
            .map(entry -> new SavedTick<>(entry.original.type(),
            entry.original.pos(), Math.toIntExact(entry.remaining), entry.original.priority())).toList();
    }

    private void removeArea(LevelTicks<?> queue, BoundingBox area) {
        QueueState<?> state = queues.get(queue);
        if (state == null) return;
        for (Held<?> entry : entriesInArea(state, area)) {
            state.removeUnknown(entry);
            markChunkUnsaved(entry.level, entry.original.pos());
        }
    }

    private <T> List<Held<T>> entriesInArea(QueueState<T> state, BoundingBox area) {
        List<Held<T>> result = new ArrayList<>();
        for (int x = Math.floorDiv(area.minX(), 16); x <= Math.floorDiv(area.maxX(), 16); x++)
            for (int z = Math.floorDiv(area.minZ(), 16); z <= Math.floorDiv(area.maxZ(), 16); z++)
                for (Held<T> entry : state.inChunk(ChunkPos.pack(x, z)))
                    if (area.isInside(entry.original.pos())) result.add(entry);
        return result;
    }
}
