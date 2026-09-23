package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.TacticalIntent;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;

/** Extracts immutable query results into vanilla's depth-tested per-frame geometry collector. */
public final class ActionPreviewRenderer {
    private ActionPreviewRenderer() {}
    private static Vec3 vector(TacticalIntent.Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    public static void extract(ExtractLevelRenderStateEvent event) {
        var mc = Minecraft.getInstance();
        if (!TacticalOverlay.hudVisible() || mc.screen != null || !mc.isWindowActive() || ClientControl.modal()) return;
        var preview = ClientTacticalPlan.preview();
        var route = ClientTacticalPlan.displayRoute();
        Vec3 previous = ClientTacticalPlan.previewOrigin();
        if (previous == null) return;
        try (var collector = event.getLevelRenderer().collectPerFrameGizmos()) {
            if (preview != null && !ClientTacticalPlan.running()) {
                var ring = ClientTacticalPlan.approachRing();
                if (ring != null) {
                    Gizmos.circle(ring.center(), ring.radius(), GizmoStyle.strokeAndFill(0xFFD9A441, 2, 0x1833AADD));
                    // Only the current route endpoint is highlighted; no per-block circles.
                    if (ClientTacticalPlan.trajectoryReady() && preview.intent()!=null)
                        Gizmos.point(vector(preview.intent().approach().feet()).add(0, .06, 0), 0xFFFFFF66, 6);
                }
            }
            double reachable = ClientTacticalPlan.running() || preview == null
                ? Double.POSITIVE_INFINITY : preview.reachableDistance();
            MovementPathLines.emit(previous, route, reachable,
                (from, to, color) -> Gizmos.line(from.add(0, .06, 0), to.add(0, .06, 0), color, 2));
            // Keep the stop visible even when it lies inside a dash gap.
            if (!ClientTacticalPlan.running() && preview != null && preview.intent() != null
                && preview.intent().capability() == TacticalIntent.Capability.MOVE)
                Gizmos.point(vector(preview.intent().approach().feet()).add(0, .06, 0), MovementPathLines.GRAY, 4);
            if (ClientTacticalPlan.trajectoryReady()) {
                var trajectory = preview.trajectory();
                for (int i = 1; i < trajectory.size(); i++)
                    Gizmos.line(vector(trajectory.get(i - 1)), vector(trajectory.get(i)), 0xCCFFDD66, 2);
                if (preview.contact() != null) {
                    Vec3 p = vector(preview.contact());
                    int color = preview.status().equals("TARGET_CONTACT") ? 0xFF66EE99 : 0xFFFF5544;
                    Gizmos.point(p, color, 8);
                    Gizmos.line(p.add(-.16, -.16, 0), p.add(.16, .16, 0), color, 3);
                    Gizmos.line(p.add(-.16, .16, 0), p.add(.16, -.16, 0), color, 3);
                }
            }
        }
    }
}
