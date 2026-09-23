package cc.sighs.dndturn.platform.server.ai;

import cc.sighs.dndturn.application.planning.MovementPlanner;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.action.MinecraftCellProbe;
import cc.sighs.dndturn.platform.server.action.MinecraftCoordinates;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fixed .84 ground translation; path weights are estimates, never movement charges. */
public final class GroundMovementPort implements MovementPorts.Port {
    public boolean matches(Mob actor) { return actor instanceof PathfinderMob && actor.getNavigation() instanceof GroundPathNavigation; }
    public ActionIntent opportunity(LiveActorContext actor, EncounterAuthority.StateView encounter, OperationRecord.Result previous) {
        return MinecraftMovementOpportunities.movement(actor, encounter, previous);
    }
    public List<GridCell> plan(LiveActorContext actor, EncounterAuthority.StateView state, ActionIntent intent,
                               GridCell target, Runnable budget) {
        var world = new MinecraftCellProbe(actor.level(), actor.body(), state.region());
        MovementPlanner.CellProbe probe = new MovementPlanner.CellProbe() {
            public boolean canOccupy(GridCell cell) { budget.run(); return world.canOccupy(cell); }
            public int traversalCost(GridCell from, GridCell to) { budget.run(); return world.traversalCost(from, to); }
        };
        var goals = new HashSet<GridCell>();
        int radius = AbilityAdapterRegistry.facts(intent).approachRadius();
        if (radius < 0 || radius > 16) throw new IllegalStateException("approach radius bound");
        for (int x = -radius; x <= radius; x++) for (int y = -1; y <= 1; y++) for (int z = -radius; z <= radius; z++) {
            if (radius == 0 && y != 0) continue;
            var goal = new GridCell(target.x() + x, target.y() + y, target.z() + z);
            if (probe.canOccupy(goal) && AbilityAdapterRegistry.facts(intent).canPlanExecutionFrom(actor, intent,
                    new Vec3(goal.x() + .5, goal.y(), goal.z() + .5))) goals.add(goal);
        }
        var start = MinecraftCoordinates.cell(actor.body().blockPosition());
        var proposal = MovementPlanner.propose(start, goals, 128, 4096, state.region().version(), probe);
        if (proposal.cost() == Integer.MAX_VALUE || !MovementPlanner.revalidate(start, proposal, state.region().version(), probe))
            throw new IllegalStateException("no supported path to execution position");
        return proposal.cells();
    }
    public MovementPorts.Driver start(Mob actor, GridCell goal) {
        if (!matches(actor) || !actor.getNavigation().moveTo(goal.x() + .5, goal.y(), goal.z() + .5, 0, 1.0))
            throw new IllegalStateException("vanilla navigation found no path");
        Path path = Objects.requireNonNull(actor.getNavigation().getPath());
        return new MovementPorts.Driver() {
            public boolean owns(Mob mob) { return mob.getNavigation().getPath() == path; }
            public boolean done(Mob mob) { return !owns(mob) || mob.getNavigation().isDone(); }
            public int nextStepBudget(Mob mob) { return owns(mob) ? stepBudget(mob, path) : -1; }
            public void release(Mob mob) { if (owns(mob)) mob.getNavigation().stop(); }
        };
    }
    private static int stepBudget(Mob mob, Path path) {
        if (path.isDone()) return -1;
        int index = path.getNextNodeIndex(), last = Math.min(path.getNodeCount() - 1, index + 1);
        if (index < 0 || last < index) return -1;
        int cost = mob.isInWater() ? 2 : 1;
        boolean jump = mob.onGround() && (mob.isJumping() || mob.horizontalCollision
                || mob.getMoveControl().hasWanted() && mob.getMoveControl().getWantedY() > mob.getY() + .25);
        for (int node = index; node <= last; node++) {
            Vec3 delta = path.getEntityPosAtNode(mob, node).subtract(mob.position());
            if (delta.lengthSqr() > 9) return -1;
            if (delta.y > .25) jump = true;
            if (!mob.level().noCollision(mob, mob.getBoundingBox().expandTowards(delta.x, Math.max(0, delta.y), delta.z))) jump = true;
            int samples = Math.max(1, (int)Math.ceil(delta.length() * 4));
            for (int i = 0; i <= samples; i++) {
                int water = water((ServerLevel)mob.level(), mob.getBoundingBox().move(delta.scale((double)i / samples)));
                if (water < 0) return -1;
                if (water > 0) cost = 2;
            }
        }
        return cost + (jump ? 1 : 0);
    }
    private static int water(ServerLevel level, AABB body) {
        var min = BlockPos.containing(body.minX, body.minY, body.minZ);
        var max = BlockPos.containing(body.maxX - 1E-7, body.maxY - 1E-7, body.maxZ - 1E-7);
        long x = (long)max.getX() - min.getX() + 1, y = (long)max.getY() - min.getY() + 1, z = (long)max.getZ() - min.getZ() + 1;
        if (x < 1 || x > 4 || y < 1 || y > 4 || z < 1 || z > 4 || x * y * z > 32) return -1;
        boolean water = false;
        for (var pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) return -1;
            water |= level.getFluidState(pos).is(FluidTags.WATER);
        }
        return water ? 1 : 0;
    }
}
