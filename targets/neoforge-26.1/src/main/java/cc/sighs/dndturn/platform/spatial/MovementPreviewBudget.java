package cc.sighs.dndturn.platform.spatial;

import cc.sighs.dndturn.platform.network.ActionProtocol;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Nominal planning only; actual movement ticks remain the sole source of resource charges. */
public final class MovementPreviewBudget {
    private MovementPreviewBudget() {}
    public record Limit(List<ActionProtocol.PreviewStep> route, Vec3 stop, double distance, boolean exceeded) {}

    public static Limit limit(Vec3 origin, List<ActionProtocol.PreviewStep> route, int ticks, double movementSpeed) {
        // Normal ground travel: input damping .98, ground drag .6 * .91.
        // Allow for acceleration and the existing final-waypoint steering slowdown.
        double available = Math.max(0, ticks - Math.min(8, Math.max(0, ticks) * .5));
        double walking = movementSpeed * .98 / (1 - .6 * .91);
        if (!Double.isFinite(walking) || walking <= 0) {
            return new Limit(List.of(), origin, 0, !route.isEmpty());
        }
        var accepted = new ArrayList<ActionProtocol.PreviewStep>();
        Vec3 from = origin;
        double distance = 0;
        for (var step : route) {
            Vec3 to = PreviewPathfinder.vector(step.feet());
            double length = from.distanceTo(to);
            if (length < 1e-8) continue;
            boolean swim = step.kind().equals("SWIM");
            double speed = swim ? Math.min(walking, .02 * .98 / (1 - .8)) : walking;
            double cost = length / speed * (swim ? 2 : 1) + (step.kind().equals("JUMP") ? 1 : 0);
            if (available + 1e-8 < cost || available <= 0) {
                // Never propose stopping in the middle of a jump, step or fall.
                boolean levelSegment = Math.abs(from.y - to.y) < 1e-6;
                double fraction = levelSegment && (step.kind().equals("WALK") || swim)
                    ? Math.clamp(available / cost, 0, 1) : 0;
                Vec3 stop = from.lerp(to, fraction);
                if (fraction > 1e-8) accepted.add(new ActionProtocol.PreviewStep(PreviewPathfinder.value(stop), step.kind()));
                return new Limit(List.copyOf(accepted), stop, distance + length * fraction, true);
            }
            accepted.add(step); available -= cost; distance += length; from = to;
        }
        return new Limit(List.copyOf(accepted), from, distance, false);
    }
}
