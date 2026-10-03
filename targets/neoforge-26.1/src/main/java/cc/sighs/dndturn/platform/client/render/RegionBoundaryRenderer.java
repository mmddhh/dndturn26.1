package cc.sighs.dndturn.platform.client.render;

import cc.sighs.dndturn.platform.network.RegionBoundaryProtocol;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * DNDTURN-TEMP-BOUNDARY-VIZ: TEMPORARY developer aid that draws the encounter field so boundary
 * rejections are visible. Remove together with the other DNDTURN-TEMP-BOUNDARY-VIZ sites.
 */
public final class RegionBoundaryRenderer {
    private static final int SINGLE = 0xFFFFAA33;
    private static final int MERGED = 0xFF33DDDD;
    private static final int TOP = 0xFF66FF66;
    private static final int BOTTOM = 0xFFFF5555;
    private static volatile RegionBoundaryProtocol.Boundary boundary;

    private RegionBoundaryRenderer() {}

    public static void receive(RegionBoundaryProtocol.Boundary value, IPayloadContext context) {
        context.enqueueWork(() -> boundary = value);
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        RegionBoundaryProtocol.Boundary current = boundary;
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null || current.anchors().isEmpty() || minecraft.level == null
            || !current.dimension().equals(minecraft.level.dimension().identifier().toString())) {
            return;
        }
        // A merge re-samples the region and bumps its version above the initial 1.
        boolean merged = current.version() > 1;
        int color = merged ? MERGED : SINGLE;
        float radius = (float) current.radius();
        GizmoStyle side = GizmoStyle.stroke(color, 2);
        GizmoStyle top = GizmoStyle.stroke(TOP, 2);
        GizmoStyle bottom = GizmoStyle.stroke(BOTTOM, 2);

        double midY = 0;
        for (RegionBoundaryProtocol.Point point : current.anchors()) midY += point.y();
        midY /= current.anchors().size();
        List<RegionBoundaryProtocol.Point> hull = hull(current.anchors());

        try (var collector = event.getLevelRenderer().collectPerFrameGizmos()) {
            int edges = hull.size() == 2 ? 1 : hull.size();
            for (int i = 0; i < edges; i++) {
                RegionBoundaryProtocol.Point a = hull.get(i);
                RegionBoundaryProtocol.Point b = hull.get((i + 1) % hull.size());
                Gizmos.line(new Vec3(a.x(), midY + 0.05, a.z()), new Vec3(b.x(), midY + 0.05, b.z()), color, 3);
                if (hull.size() == 2) {
                    // Capsule: the two radius-offset rails along the single hull edge.
                    double dx = b.x() - a.x(), dz = b.z() - a.z();
                    double length = Math.hypot(dx, dz);
                    if (length > 1.0E-6) {
                        double nx = -dz / length * radius, nz = dx / length * radius;
                        Gizmos.line(new Vec3(a.x() + nx, midY, a.z() + nz), new Vec3(b.x() + nx, midY, b.z() + nz), color, 2);
                        Gizmos.line(new Vec3(a.x() - nx, midY, a.z() - nz), new Vec3(b.x() - nx, midY, b.z() - nz), color, 2);
                    }
                }
            }
            for (RegionBoundaryProtocol.Point point : hull) {
                Gizmos.circle(new Vec3(point.x(), point.y(), point.z()), radius, side);
                Gizmos.circle(new Vec3(point.x(), current.maxY() - 0.05, point.z()), radius, top);
                Gizmos.circle(new Vec3(point.x(), current.minY() + 0.05, point.z()), radius, bottom);
                Gizmos.line(new Vec3(point.x() + radius, current.minY(), point.z()),
                    new Vec3(point.x() + radius, current.maxY(), point.z()), color, 2);
                Gizmos.line(new Vec3(point.x() - radius, current.minY(), point.z()),
                    new Vec3(point.x() - radius, current.maxY(), point.z()), color, 2);
                Gizmos.line(new Vec3(point.x(), current.minY(), point.z() + radius),
                    new Vec3(point.x(), current.maxY(), point.z() + radius), color, 2);
                Gizmos.line(new Vec3(point.x(), current.minY(), point.z() - radius),
                    new Vec3(point.x(), current.maxY(), point.z() - radius), color, 2);
            }
            double cx = 0, cz = 0;
            for (RegionBoundaryProtocol.Point point : current.anchors()) { cx += point.x(); cz += point.z(); }
            cx /= current.anchors().size();
            cz /= current.anchors().size();
            Gizmos.billboardTextOverBlock((merged ? "MERGED" : "FIELD") + " anchors=" + current.anchors().size()
                    + " v=" + current.version(),
                new BlockPos((int) Math.floor(cx), (int) Math.floor(current.maxY() + 1.0), (int) Math.floor(cz)),
                0, color, 1.5F);
        }
    }

    /** Monotone-chain convex hull on the XZ plane; matches the region's own silhouette. */
    private static List<RegionBoundaryProtocol.Point> hull(List<RegionBoundaryProtocol.Point> points) {
        List<RegionBoundaryProtocol.Point> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(RegionBoundaryProtocol.Point::x)
            .thenComparingDouble(RegionBoundaryProtocol.Point::z));
        List<RegionBoundaryProtocol.Point> distinct = new ArrayList<>(sorted.size());
        for (RegionBoundaryProtocol.Point point : sorted) {
            RegionBoundaryProtocol.Point last = distinct.isEmpty() ? null : distinct.get(distinct.size() - 1);
            if (last == null || last.x() != point.x() || last.z() != point.z()) distinct.add(point);
        }
        if (distinct.size() <= 2) return distinct;
        List<RegionBoundaryProtocol.Point> lower = new ArrayList<>();
        for (RegionBoundaryProtocol.Point point : distinct) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= 0)
                lower.remove(lower.size() - 1);
            lower.add(point);
        }
        List<RegionBoundaryProtocol.Point> upper = new ArrayList<>();
        for (int i = distinct.size() - 1; i >= 0; i--) {
            RegionBoundaryProtocol.Point point = distinct.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= 0)
                upper.remove(upper.size() - 1);
            upper.add(point);
        }
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower;
    }

    private static double cross(RegionBoundaryProtocol.Point a, RegionBoundaryProtocol.Point b, RegionBoundaryProtocol.Point c) {
        return (b.x() - a.x()) * (c.z() - a.z()) - (b.z() - a.z()) * (c.x() - a.x());
    }
}
