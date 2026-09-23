package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Submission-only planning. A client's selected position is an intent, never a cached permit. */
final class SelectedPositionPlanner {
    private final TacticalActions actions;
    SelectedPositionPlanner(TacticalActions actions) { this.actions = actions; }
    static String posture(ServerPlayer p) {
        return PreviewPathfinder.posture(p);
    }
    List<TacticalNetwork.PreviewStep> accept(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
        var budget = (Runnable)() -> actions.service.abilityWork.require(actions.service.planClock(), player.getUUID(), state.id(), AbilityWorkBudget.Work.PROBE);
        var finder = new PreviewPathfinder(player,state.region(),budget);
        Vec3 feet = PreviewPathfinder.vector(intent.approach().feet());
        var cell = PreviewPathfinder.key(feet);
        Vec3 actual = finder.surfaceAt(feet);
        if (actual == null || actual.distanceToSqr(feet) > 1e-8) throw ActionFailure.source("selected surface changed");
        var behavior = TacticalCapabilities.resolve(intent);
        if (!behavior.canExecute(new TacticalActor(player),intent,feet)) throw ActionFailure.source("selected position cannot execute ability");
        if (intent.capability() == TacticalIntent.Capability.MOVE && !cell.equals(intent.target().cell()))
            throw ActionFailure.source("selected position differs from movement target");
        if (intent.target().entity()!=null) {
            var target=player.level().getEntity(intent.target().entity());
            if (target==null || finder.dimensions.makeBoundingBox(feet.x,feet.y,feet.z).intersects(target.getBoundingBox()))
                throw ActionFailure.source("selected position overlaps or lost target");
        }
        var direct=finder.directTo(feet);
        if (direct!=null) return direct;
        finder.destination(feet);
        while (!finder.advance(64)) { /* bounded by the planner's 1024-node limit */ }
        var route=finder.finishAt(feet);
        if (route==null) throw ActionFailure.source("no supported path to selected position");
        return route;
    }
}
