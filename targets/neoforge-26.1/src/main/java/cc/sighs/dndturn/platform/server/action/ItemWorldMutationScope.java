package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import cc.sighs.dndturn.platform.server.world.WorldOutcomeHooks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Synchronous item invocation evidence. Never grants a later entity tick or another input. */
public final class ItemWorldMutationScope implements AutoCloseable {
    private static final ThreadLocal<ItemWorldMutationScope> CURRENT = new ThreadLocal<>();
    private final ServerPlayer player;
    private final ActionExecutionCoordinator.Execution execution;
    private final ItemWorldMutationScope previous;
    private final Set<Entity> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Entity> accepted = Collections.newSetFromMap(new IdentityHashMap<>());
    ItemWorldMutationScope(ServerPlayer player, ActionExecutionCoordinator.Execution execution) {
        this.player=player; this.execution=execution; previous=CURRENT.get(); CURRENT.set(this);
    }
    /** Validate each actual synchronous native write; permission is not inferred from a class name. */
    public static void beforeBlockWrite(net.minecraft.world.level.Level level, BlockPos pos) {
        var scope = CURRENT.get();
        if (scope == null) return;
        if (level != scope.player.level() || !scope.player.level().getServer().isSameThread())
            throw new IllegalStateException("item write outside execution level/thread");
        TacticalImpact.authorize(scope.player, pos);
        var e = scope.execution;
        var cell = MinecraftCoordinates.cell(pos);
        if (e.footprint.contains(cell)) return;
        if (e.footprint.size() >= AbilityCheckpoint.MAX_OBSERVED_BLOCKS)
            throw new IllegalStateException("item block observation budget exhausted");
        var footprint = new ArrayList<>(e.footprint); footprint.add(cell); e.footprint = List.copyOf(footprint);
        var before = e.before;
        var blocks = new ArrayList<>(before.blocks());
        blocks.add(new AbilityCheckpoint.Block(cell, level.getBlockState(pos).toString()));
        e.before = new AbilityCheckpoint.Sample(before.items(), blocks, before.bodies(), before.spawned());
    }
    public static void joining(Entity entity) {
        var scope=CURRENT.get();
        if (scope==null || entity.level()!=scope.player.level()) return;
        if (scope.candidates.size()+scope.execution.spawned.size()>=32)
            throw new IllegalStateException("item entity generation budget exhausted");
        // Validate actual geometry before insertion, in addition to each behavior's preflight.
        var box=entity.getBoundingBox();
        if (box.getXsize()>16 || box.getYsize()>16 || box.getZsize()>16)
            throw new IllegalStateException("item generated entity exceeds geometry contract");
        for (var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),
            BlockPos.containing(box.maxX,box.maxY,box.maxZ))) TacticalImpact.authorize(scope.player,pos);
        scope.candidates.add(entity);
    }
    /** Called only after the entity manager accepted this insertion (including untracked sections). */
    public static void inserted(Entity entity) {
        WorldOutcomeHooks.inserted(entity);
        var scope=CURRENT.get(); if(scope!=null && scope.candidates.contains(entity)) scope.accepted.add(entity);
    }
    public static boolean brushing(net.minecraft.world.entity.player.Player player) {
        var scope=CURRENT.get();return scope!=null && scope.player==player && scope.execution.behavior.id().equals("dndturn:brush");
    }
    public void close() {
        try {
            for (Entity entity:accepted) {
                execution.spawned.add(new AbilityCheckpoint.Spawn(entity.getUUID(),
                    ((PresentationIdentity)entity).dndturn$presentationInstance(),
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                    entity.getX(),entity.getY(),entity.getZ()));
                if (execution.behavior.id().equals("dndturn:spawn_item") && entity instanceof net.minecraft.world.entity.Mob mob)
                    ServerRuntime.encounters(player.level().getServer()).actionHost().joinGeneratedMob(player, mob, execution.root.encounterId());
            }
        } finally { if (previous==null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
