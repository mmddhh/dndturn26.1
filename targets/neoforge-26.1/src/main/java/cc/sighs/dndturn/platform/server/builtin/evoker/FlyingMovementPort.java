package cc.sighs.dndturn.platform.server.builtin.evoker;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.ai.MovementPorts;
import cc.sighs.dndturn.platform.spatial.MinecraftCoordinates;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.phys.Vec3;

/** Direct 3D approach for small flying summons. The existing movement ledger still owns displacement. */
public final class FlyingMovementPort implements MovementPorts.Port {
    private static final int SEARCH_LIMIT = 32;

    @Override
    public boolean matches(Mob actor) {
        return actor instanceof Vex && actor.getNavigation() instanceof FlyingPathNavigation;
    }

    @Override
    public ActionIntent opportunity(LiveActorContext actor, StateView encounter, OperationRecord.Result previous) {
        if (previous != null || ((Mob) actor.body()).isNoAi() || actor.body().isPassenger()) return null;
        var member = encounter.members().get(actor.id());
        if (member == null || member.movementTicks() < 1) return null;
        var target = ((Mob) actor.body()).getTarget();
        if (target == null || !target.isAlive()) return null;
        var from = actor.body().position();
        var to = target.position();
        var delta = to.subtract(from);
        double distance = delta.length();
        if (distance <= 4 || distance > SEARCH_LIMIT) return null;
        var next = from.add(delta.normalize().scale(Math.min(4, distance - 3)));
        var cell = MinecraftCoordinates.cell(BlockPos.containing(next));
        return new ActionIntent("dndturn:move", 1, ActionIntent.Capability.MOVE,
            new ActionIntent.Target(ActionIntent.TargetKind.GROUND, encounter.region().dimension(),
                null, cell, -1, 0, 0, 0), GrantEvidence.basic());
    }

    @Override
    public List<GridCell> plan(LiveActorContext actor, StateView encounter, ActionIntent intent,
                               GridCell target, Runnable probeBudget) {
        var start = MinecraftCoordinates.cell(actor.body().blockPosition());
        var cells = new ArrayList<GridCell>();
        int dx = Integer.signum(target.x() - start.x());
        int dy = Integer.signum(target.y() - start.y());
        int dz = Integer.signum(target.z() - start.z());
        int x = start.x(), y = start.y(), z = start.z();
        int guard = 0;
        while ((x != target.x() || y != target.y() || z != target.z()) && guard++ < 4096) {
            probeBudget.run();
            if (x != target.x()) x += dx;
            else if (z != target.z()) z += dz;
            else if (y != target.y()) y += dy;
            cells.add(new GridCell(x, y, z));
        }
        return cells;
    }

    @Override
    public MovementPorts.Driver start(Mob actor, GridCell destination) {
        if (!matches(actor)) throw new IllegalStateException("flying navigation unsupported");
        Vec3 goal = new Vec3(destination.x() + .5, destination.y(), destination.z() + .5);
        actor.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 1.0);
        final boolean[] active = {true};
        return new MovementPorts.Driver() {
            public boolean owns(Mob mob) { return active[0] && mob == actor; }
            public boolean done(Mob mob) {
                return !owns(mob) || mob.isRemoved() || !mob.isAlive()
                    || mob.position().distanceTo(goal) < .75;
            }
            public int nextStepBudget(Mob mob) {
                if (!owns(mob)) return -1;
                double distance = mob.position().distanceTo(goal);
                return distance < .75 ? -1 : distance > 2 ? 2 : 1;
            }
            public void release(Mob mob) { active[0] = false; }
        };
    }
}
