package cc.sighs.dndturn.platform.client.render;

import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.spatial.PreviewPathfinder;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** One metre = .50 long + .15 gap + .20 short + .15 gap, across waypoint boundaries. */
final class MovementPathLines {
    static final int GRAY = 0xFFAAAAAA;
    static final int RED = 0xFFFF5544;
    interface Sink { void line(Vec3 from, Vec3 to, int color); }
    private MovementPathLines() {}

    static void emit(Vec3 origin, List<ActionProtocol.PreviewStep> route, double reachable, Sink sink) {
        Vec3 previous = origin;
        double distance = 0;
        for (var step : route) {
            Vec3 next = PreviewPathfinder.vector(step.feet());
            double length = previous.distanceTo(next);
            if (length < 1e-8) continue;
            double end = distance + length;
            for (int metre = (int)Math.floor(distance); metre < Math.ceil(end); metre++) {
                dash(previous, next, distance, length, metre, metre + .50, reachable, sink);
                dash(previous, next, distance, length, metre + .65, metre + .85, reachable, sink);
            }
            distance = end;
            previous = next;
        }
    }

    private static void dash(Vec3 from, Vec3 to, double offset, double length,
                             double start, double end, double reachable, Sink sink) {
        double a = Math.max(offset, start), b = Math.min(offset + length, end);
        if (b - a < 1e-8) return;
        double split = Math.clamp(reachable, a, b);
        if (split > a) sink.line(from.lerp(to, (a-offset)/length), from.lerp(to, (split-offset)/length), GRAY);
        if (b > split) sink.line(from.lerp(to, (split-offset)/length), from.lerp(to, (b-offset)/length), RED);
    }
}
