package cc.sighs.dndturn.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable continuous encounter area built from one fixed discovery snapshot. */
public final class EncounterRegion {
    public static final double VERTICAL_MARGIN = 16.0;

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("non-finite point");
        }
    }

    public record Discovery(double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ) {
        public Discovery {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ
                || !Double.isFinite(maxX - minX) || !Double.isFinite(maxZ - minZ))
                throw new IllegalArgumentException("invalid discovery bounds");
        }

        public boolean contains(Point point) {
            return point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY
                && point.z >= minZ && point.z <= maxZ;
        }
    }

    public record Anchor(UUID entityId, Point center) {
        public Anchor {
            Objects.requireNonNull(entityId);
            Objects.requireNonNull(center);
        }
    }

    private record Horizontal(double x, double z) {}

    private final String dimension;
    private final Discovery discovery;
    private final List<Anchor> anchors;
    private final List<Horizontal> hull;
    private final double radius;
    private final double minY;
    private final double maxY;
    private final long version;

    private EncounterRegion(String dimension, Discovery discovery, List<Anchor> anchors,
                            List<Horizontal> hull, double radius, double minY, double maxY, long version) {
        this.dimension = dimension;
        this.discovery = discovery;
        this.anchors = anchors;
        this.hull = hull;
        this.radius = radius;
        this.minY = minY;
        this.maxY = maxY;
        this.version = version;
    }

    public static EncounterRegion generate(String dimension, Discovery discovery,
                                           List<Anchor> anchors, double radius, long version) {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(discovery);
        Objects.requireNonNull(anchors);
        if (dimension.isBlank() || !Double.isFinite(radius) || radius <= 0 || version < 0 || anchors.isEmpty())
            throw new IllegalArgumentException("invalid region parameters");
        List<Anchor> captured = List.copyOf(anchors);
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        List<Horizontal> points = new ArrayList<>(captured.size());
        Set<UUID> identities = new HashSet<>();
        for (Anchor anchor : captured) {
            Objects.requireNonNull(anchor);
            if (!identities.add(anchor.entityId())) throw new IllegalArgumentException("duplicate anchor identity");
            if (!discovery.contains(anchor.center())) throw new IllegalArgumentException("anchor outside discovery");
            lowest = Math.min(lowest, anchor.center().y());
            highest = Math.max(highest, anchor.center().y());
            points.add(new Horizontal(anchor.center().x(), anchor.center().z()));
        }
        double minY = lowest - VERTICAL_MARGIN;
        double maxY = highest + VERTICAL_MARGIN;
        if (!Double.isFinite(minY) || !Double.isFinite(maxY)) throw new IllegalArgumentException("region height overflow");
        return new EncounterRegion(dimension, discovery, captured, convexHull(points), radius, minY, maxY, version);
    }

    public String dimension() { return dimension; }
    public Discovery discovery() { return discovery; }
    public List<Anchor> anchors() { return anchors; }
    public double radius() { return radius; }
    public double minY() { return minY; }
    public double maxY() { return maxY; }
    public long version() { return version; }

    public boolean containsPoint(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || y < minY || y > maxY)
            return false;
        if (hull.size() == 1) return distance(x, z, hull.get(0)) <= radius;
        if (hull.size() >= 3 && insideHull(x, z)) return true;
        int edges = hull.size() == 2 ? 1 : hull.size();
        for (int i = 0; i < edges; i++) {
            Horizontal a = hull.get(i);
            Horizontal b = hull.get((i + 1) % hull.size());
            if (segmentDistance(x, z, a, b) <= radius) return true;
        }
        return false;
    }

    public boolean containsBlock(int blockX, int blockY, int blockZ) {
        return containsPoint(blockX + 0.5, blockY + 0.5, blockZ + 0.5);
    }

    /** Exact continuous crossing test for one movement segment, including a fast pass-through. */
    public boolean intersectsSegment(Point from, Point to) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        double first = 0.0, last = 1.0;
        double dy = to.y - from.y;
        if (dy == 0.0) {
            if (from.y < minY || from.y > maxY) return false;
        } else {
            double low = (minY - from.y) / dy;
            double high = (maxY - from.y) / dy;
            first = Math.max(first, Math.min(low, high));
            last = Math.min(last, Math.max(low, high));
            if (first > last) return false;
        }
        Horizontal a = new Horizontal(from.x + (to.x - from.x) * first,
            from.z + (to.z - from.z) * first);
        Horizontal b = new Horizontal(from.x + (to.x - from.x) * last,
            from.z + (to.z - from.z) * last);
        if (hull.size() >= 3 && (insideHull(a.x, a.z) || insideHull(b.x, b.z))) return true;
        for (int i = 0; i < edgeCount(); i++) {
            Horizontal c = hull.get(i), d = edgeEnd(i);
            if (segmentsIntersect(a, b, c, d)
                || segmentDistance(a.x, a.z, c, d) <= radius
                || segmentDistance(b.x, b.z, c, d) <= radius
                || segmentDistance(c.x, c.z, a, b) <= radius
                || segmentDistance(d.x, d.z, a, b) <= radius) return true;
        }
        return false;
    }

    /** First contact along a segment; infinity means it never enters this area. */
    public double firstIntersectionFraction(Point from, Point to) {
        if (!intersectsSegment(from, to)) return Double.POSITIVE_INFINITY;
        if (containsPoint(from.x, from.y, from.z)) return 0.0;
        double low = 0.0, high = 1.0;
        for (int i = 0; i < 48; i++) {
            double middle = (low + high) * 0.5;
            Point prefixEnd = new Point(from.x + (to.x - from.x) * middle,
                from.y + (to.y - from.y) * middle, from.z + (to.z - from.z) * middle);
            if (intersectsSegment(from, prefixEnd)) high = middle;
            else low = middle;
        }
        return high;
    }

    public boolean overlaps(EncounterRegion other) {
        Objects.requireNonNull(other);
        if (!dimension.equals(other.dimension) || maxY < other.minY || other.maxY < minY) return false;
        if (hull.size() >= 3)
            for (Horizontal point : other.hull) if (insideHull(point.x, point.z)) return true;
        if (other.hull.size() >= 3)
            for (Horizontal point : hull) if (other.insideHull(point.x, point.z)) return true;
        double reach = radius + other.radius;
        for (int i = 0; i < edgeCount(); i++) {
            Horizontal a = hull.get(i);
            Horizontal b = edgeEnd(i);
            for (int j = 0; j < other.edgeCount(); j++) {
                Horizontal c = other.hull.get(j);
                Horizontal d = other.edgeEnd(j);
                if (segmentsIntersect(a, b, c, d) || segmentDistance(a.x, a.z, c, d) <= reach
                    || segmentDistance(b.x, b.z, c, d) <= reach
                    || segmentDistance(c.x, c.z, a, b) <= reach
                    || segmentDistance(d.x, d.z, a, b) <= reach) return true;
            }
        }
        return false;
    }

    private int edgeCount() { return hull.size() < 3 ? 1 : hull.size(); }

    private Horizontal edgeEnd(int index) {
        return hull.size() == 1 ? hull.get(0) : hull.get((index + 1) % hull.size());
    }

    private static boolean segmentsIntersect(Horizontal a, Horizontal b, Horizontal c, Horizontal d) {
        if (a.equals(b) || c.equals(d)) return false;
        if (Math.max(Math.min(a.x, b.x), Math.min(c.x, d.x))
            > Math.min(Math.max(a.x, b.x), Math.max(c.x, d.x))
            || Math.max(Math.min(a.z, b.z), Math.min(c.z, d.z))
            > Math.min(Math.max(a.z, b.z), Math.max(c.z, d.z))) return false;
        double abC = cross(a, b, c), abD = cross(a, b, d);
        double cdA = cross(c, d, a), cdB = cross(c, d, b);
        return (abC <= 0 && abD >= 0 || abC >= 0 && abD <= 0)
            && (cdA <= 0 && cdB >= 0 || cdA >= 0 && cdB <= 0);
    }

    private boolean insideHull(double x, double z) {
        for (int i = 0; i < hull.size(); i++) {
            Horizontal a = hull.get(i);
            Horizontal b = hull.get((i + 1) % hull.size());
            if (cross(a, b, new Horizontal(x, z)) < 0) return false;
        }
        return true;
    }

    private static double distance(double x, double z, Horizontal point) {
        return Math.hypot(x - point.x, z - point.z);
    }

    private static double segmentDistance(double x, double z, Horizontal a, Horizontal b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double length = Math.hypot(dx, dz);
        if (length == 0) return distance(x, z, a);
        double projection = (x - a.x) * (dx / length) + (z - a.z) * (dz / length);
        double t = Math.max(0, Math.min(1, projection / length));
        double distanceX = x - (a.x + t * dx);
        double distanceZ = z - (a.z + t * dz);
        return Math.hypot(distanceX, distanceZ);
    }

    private static double cross(Horizontal a, Horizontal b, Horizontal c) {
        return (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x);
    }

    private static List<Horizontal> convexHull(List<Horizontal> input) {
        List<Horizontal> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingDouble(Horizontal::x).thenComparingDouble(Horizontal::z));
        List<Horizontal> distinct = new ArrayList<>(sorted.size());
        for (Horizontal point : sorted) {
            if (distinct.isEmpty() || !distinct.get(distinct.size() - 1).equals(point)) distinct.add(point);
        }
        if (distinct.size() <= 2) return List.copyOf(distinct);
        List<Horizontal> lower = new ArrayList<>();
        for (Horizontal point : distinct) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= 0)
                lower.remove(lower.size() - 1);
            lower.add(point);
        }
        List<Horizontal> upper = new ArrayList<>();
        for (int i = distinct.size() - 1; i >= 0; i--) {
            Horizontal point = distinct.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= 0)
                upper.remove(upper.size() - 1);
            upper.add(point);
        }
        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return List.copyOf(lower);
    }
}
