package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Synchronous item invocation evidence. Never grants a later entity tick or another input. */
public final class ItemUseEffects implements AutoCloseable {
    private static final ThreadLocal<ItemUseEffects> CURRENT = new ThreadLocal<>();
    private final ServerPlayer player;
    private final TacticalActions.Execution execution;
    private final ItemUseEffects previous;
    private final Set<Entity> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Entity> accepted = Collections.newSetFromMap(new IdentityHashMap<>());
    ItemUseEffects(ServerPlayer player, TacticalActions.Execution execution) {
        this.player=player; this.execution=execution; previous=CURRENT.get(); CURRENT.set(this);
    }
    static void joining(Entity entity) {
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
                    ServerCombatService.forServer(player.level().getServer()).joinGeneratedMob(player, mob, execution.root.encounterId());
            }
        } finally { if (previous==null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
