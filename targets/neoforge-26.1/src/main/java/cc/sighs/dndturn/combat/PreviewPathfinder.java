package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One bounded, read-only surface search. All live reads are on the server thread. */
public final class PreviewPathfinder {
    private static final int MAX_STEPS = 64;
    final Player player;
    final EncounterRegion region;
    final Vec3 start;
    final EntityDimensions dimensions;
    final float stepHeight;
    private final Runnable budget;
    private final Map<GridCell, Vec3> surfaces = new HashMap<>();
    private final Set<GridCell> blocked = new HashSet<>();
    private final Map<GridCell, GridCell> parents = new HashMap<>();
    private final Map<GridCell, Double> distances = new HashMap<>();
    private final GridCell root;
    boolean truncated;
    private final Map<Vec3, Boolean> standing = new HashMap<>();
    private record Edge(Vec3 from, Vec3 to) {}
    private final Map<Edge, Boolean> edges = new HashMap<>();
    private record Open(GridCell cell, double distance, double score, int steps, long order) {}
    private final PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingDouble(Open::score)
        .thenComparing(Comparator.comparingDouble(Open::distance).reversed()).thenComparingLong(Open::order));
    private final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> observed = new LinkedHashMap<>();
    private Iterator<Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState>> observations;
    private Set<GridCell> goals = Set.of();
    private GridCell reached;
    private long order;
    private boolean finished;

    public PreviewPathfinder(Player player, EncounterRegion region, Runnable budget) {
        if (!player.level().isClientSide() && !player.level().getServer().isSameThread()) throw new IllegalStateException("server thread required");
        this.player = player; this.region = region; this.budget = budget;
        start = player.position(); dimensions = player.getDimensions(player.getPose()); stepHeight = player.maxUpStep();
        root = key(start); surfaces.put(root, start);
    }
    public static GridCell key(Vec3 p) { return new GridCell((int)Math.floor(p.x), (int)Math.ceil(p.y - 1e-5), (int)Math.floor(p.z)); }
    public static TacticalIntent.Point value(Vec3 p) { return new TacticalIntent.Point(p.x, p.y, p.z); }
    public static Vec3 vector(TacticalIntent.Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    public static String posture(Player p) {
        return p.getPose()+":"+p.getDimensions(p.getPose())+":"+p.maxUpStep()+":"
            +p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)+":"
            +p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY)+":"
            +p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
    }
    public Vec3 surface(GridCell cell) {
        if (surfaces.containsKey(cell)) return surfaces.get(cell);
        if (blocked.contains(cell)) return null;
        budget.run();
        BlockPos below = new BlockPos(cell.x(), cell.y() - 1, cell.z());
        if (!player.level().hasChunkAt(below)) { blocked.add(cell); return null; }
        var shape = player.level().getBlockState(below).getCollisionShape(player.level(), below);
        boolean water = player.level().getFluidState(below.above()).is(FluidTags.WATER);
        double y = water ? cell.y() : shape.isEmpty() ? Double.NaN : below.getY() + shape.max(Direction.Axis.Y);
        Vec3 point = new Vec3(cell.x() + .5, y, cell.z() + .5);
        if (!Double.isFinite(y) || !occupiable(point)) { blocked.add(cell); return null; }
        surfaces.put(cell, point); return point;
    }
    /** Resolve support at the requested X/Z rather than snapping to a cell centre. */
    public Vec3 surfaceAt(Vec3 desired) {
        budget.run();
        var cell=key(desired); var below=new BlockPos(cell.x(),cell.y()-1,cell.z());
        if (!player.level().hasChunkAt(below)) return null;
        double y=Double.NaN;
        if (player.level().getFluidState(below.above()).is(FluidTags.WATER)) y=cell.y();
        else for (var box:player.level().getBlockState(below).getCollisionShape(player.level(),below).toAabbs()) {
            double x=desired.x-below.getX(), z=desired.z-below.getZ();
            if (x>=box.minX-1e-6 && x<=box.maxX+1e-6 && z>=box.minZ-1e-6 && z<=box.maxZ+1e-6)
                y=Double.isNaN(y) ? below.getY()+box.maxY : Math.max(y,below.getY()+box.maxY);
        }
        Vec3 p=new Vec3(desired.x,y,desired.z);
        return Double.isFinite(y) && Math.abs(y-desired.y)<.02 && occupiable(p) ? p : null;
    }
    /** Install the exact terminal position as a graph node; same-cell movement retains its edge. */
    public void destination(Vec3 feet) {
        var cell=key(feet);
        if (!cell.equals(root)) { surfaces.put(cell,feet); blocked.remove(cell); }
        begin(Set.of(cell));
    }
    public List<TacticalNetwork.PreviewStep> finishAt(Vec3 feet) {
        var route=result();
        if (route==null) return null;
        Vec3 last=route.isEmpty()?start:vector(route.getLast().feet());
        if (last.distanceToSqr(feet)<1e-10) return route;
        if (!edge(last,feet)) return null;
        var extended=new ArrayList<>(route); extended.add(new TacticalNetwork.PreviewStep(value(feet),kind(last,feet)));
        return List.copyOf(extended);
    }
    public List<TacticalNetwork.PreviewStep> directTo(Vec3 feet) {
        if (start.distanceToSqr(feet)<1e-10) return List.of();
        if (!Double.isFinite(feet.x) || !Double.isFinite(feet.y) || !Double.isFinite(feet.z)
            || Math.abs(feet.y-start.y)>.001) return null;
        int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(feet.x-start.x),Math.abs(feet.z-start.z))));
        // Bound total work separately from the short, collision-checked edges.
        if (steps>MAX_STEPS) return null;
        var route=new ArrayList<TacticalNetwork.PreviewStep>(); Vec3 from=start;
        for (int i=1;i<=steps;i++) {
            Vec3 to=start.lerp(feet,(double)i/steps);
            if (!edge(from,to)) return null;
            route.add(new TacticalNetwork.PreviewStep(value(to),kind(from,to))); from=to;
        }
        return List.copyOf(route);
    }
    public List<TacticalNetwork.PreviewStep> retarget(List<TacticalNetwork.PreviewStep> route, Vec3 feet) {
        if (route.isEmpty()) return directTo(feet);
        Vec3 before=route.size()<2?start:vector(route.get(route.size()-2).feet());
        if (!edge(before,feet)) return null;
        var result=new ArrayList<>(route.subList(0,route.size()-1));
        result.add(new TacticalNetwork.PreviewStep(value(feet),kind(before,feet)));
        return List.copyOf(result);
    }
    public boolean checkSegment(Vec3 from, Vec3 to) { return edge(from,to); }
    boolean loaded(AABB box) {
        if (box.getXsize() > 8 || box.getYsize() > 8 || box.getZsize() > 8) return false;
        for (int x = (int)Math.floor(box.minX); x <= (int)Math.floor(box.maxX); x++)
            for (int z = (int)Math.floor(box.minZ); z <= (int)Math.floor(box.maxZ); z++)
                if (!player.level().hasChunkAt(new BlockPos(x, (int)box.minY, z))) return false;
        if (player.level().isClientSide()) {
            for (var p:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY-.02,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
                if (!observed.containsKey(p)) { budget.run(); observed.put(p.immutable(),player.level().getBlockState(p)); observations=null; }
            }
        }
        return true;
    }
    public boolean dependenciesChanged(int limit) {
        if (observations==null) observations=observed.entrySet().iterator();
        while (limit-->0 && observations.hasNext()) {
            var e=observations.next();
            if (!player.level().hasChunkAt(e.getKey()) || player.level().getBlockState(e.getKey())!=e.getValue()) return true;
        }
        if (!observations.hasNext()) observations=null;
        return false;
    }
    boolean occupiable(Vec3 p) {
        return standing.computeIfAbsent(p, this::checkStanding);
    }
    private boolean checkStanding(Vec3 p) {
        AABB body = dimensions.makeBoundingBox(p.x, p.y, p.z);
        Vec3 center = body.getCenter();
        return loaded(body) && (region == null || region.containsPoint(center.x, center.y, center.z))
            && player.level().noCollision(player, body.deflate(1e-6))
            && (water(p) || !player.level().noCollision(player,
                new AABB(body.minX, body.minY - .02, body.minZ, body.maxX, body.minY, body.maxZ)));
    }
    boolean water(Vec3 p) { return player.level().getFluidState(BlockPos.containing(p)).is(FluidTags.WATER); }
    String kind(Vec3 a, Vec3 b) {
        if (water(a) || water(b)) return "SWIM";
        if (b.y - a.y > stepHeight + .001) return "JUMP";
        if (b.y > a.y + .001) return "STEP";
        return b.y < a.y - .001 ? "DROP" : "WALK";
    }
    boolean edge(Vec3 a, Vec3 b) {
        return edges.computeIfAbsent(new Edge(a,b), ignored -> checkEdge(a,b));
    }
    private boolean checkEdge(Vec3 a, Vec3 b) {
        budget.run();
        double dy = b.y - a.y;
        if (Math.abs(dy) > 1.01 || a.distanceToSqr(b) > 6 || !occupiable(b)) return false;
        AABB box = dimensions.makeBoundingBox(a.x, a.y, a.z);
        double dx = b.x - a.x, dz = b.z - a.z;
        boolean jump = dy > stepHeight + .001;
        if (jump && (player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST)
            || Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH) - .42) > 1e-6
            || player.level().getBlockState(BlockPos.containing(a).below()).getBlock().getJumpFactor() != 1
            || player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY) != .08)) return false;
        // Conservative envelope: never smooth across a corner or through a ceiling.
        double lift = jump ? 1.3 : Math.max(0, dy);
        double centerY = (box.minY + box.maxY) / 2 + lift;
        if (region != null && (!region.containsPoint(a.x, centerY, a.z) || !region.containsPoint(b.x, centerY, b.z))) return false;
        AABB rising = box.expandTowards(0, lift, 0).deflate(1e-6);
        AABB across = box.move(0, lift, 0).expandTowards(dx, 0, dz).deflate(1e-6);
        AABB falling = dimensions.makeBoundingBox(b.x, b.y, b.z).expandTowards(0, lift - dy, 0).deflate(1e-6);
        if (!loaded(rising) || !loaded(across) || !loaded(falling)
            || lift>0 && !player.level().noCollision(player, rising) || !player.level().noCollision(player, across)
            || lift-dy>0 && !player.level().noCollision(player, falling)) return false;
        if (!jump && Math.abs(dy) < .001 && !water(a) && !water(b)) {
            for (int i = 1; i < 4; i++) if (!occupiable(a.lerp(b, i / 4.0))) return false;
        }
        return true;
    }
    public List<TacticalNetwork.PreviewStep> route(GridCell goal) {
        if (!distances.containsKey(goal)) return null;
        var direct = directTo(surfaces.get(goal));
        if (direct != null) return direct;
        var reverse = new ArrayDeque<GridCell>();
        for (var c = goal; !c.equals(root); c = parents.get(c)) reverse.addFirst(c);
        var route = new ArrayList<TacticalNetwork.PreviewStep>(); Vec3 from = start;
        for (var c : reverse) { Vec3 to = surfaces.get(c); route.add(new TacticalNetwork.PreviewStep(value(to), kind(from, to))); from = to; }
        return List.copyOf(route);
    }
    /** Incremental multi-goal A*: stop at the first valid goal, never await every candidate. */
    public void begin(Set<GridCell> targets) {
        goals = Set.copyOf(targets); finished = goals.isEmpty(); reached = null;
        open.clear(); distances.clear(); parents.clear(); order = 0;
        if (!finished) { distances.put(root, 0.0); open.add(new Open(root,0,heuristic(root),0,order++)); }
    }
    private double heuristic(GridCell cell) {
        Vec3 from = surfaces.get(cell);
        // Unresolved surfaces may have fractional heights. Horizontal distance remains
        // a lower bound, including the exact fractional start and selected endpoint.
        return goals.stream().mapToDouble(goal -> {
            Vec3 to = surfaces.get(goal);
            return Math.hypot(from.x - (to == null ? goal.x() + .5 : to.x),
                from.z - (to == null ? goal.z() + .5 : to.z));
        }).min().orElse(0);
    }
    public boolean advance(int expansions) {
        while (!finished && expansions-- > 0 && !open.isEmpty()) {
            Open item = open.remove(); var from = item.cell();
            if (distances.get(from) != item.distance()) continue;
            if (goals.contains(from)) { reached = from; finished = true; break; }
            if (item.steps() >= MAX_STEPS) continue;
            Vec3 a = surfaces.get(from);
            for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++) {
                if (dx==0 && dz==0) continue;
                for (int dy=-1;dy<=1;dy++) {
                    var next = new GridCell(from.x()+dx,from.y()+dy,from.z()+dz);
                    Vec3 b=surface(next);
                    if (b==null) continue;
                    // Geometric search weight, never a resource charge: diagonals are longer.
                    double distance = item.distance()+a.distanceTo(b);
                    if (distances.getOrDefault(next,Double.POSITIVE_INFINITY)<=distance || !edge(a,b)) continue;
                    if (distances.size()>=1024 && !distances.containsKey(next)) { truncated=true; finished=true; return true; }
                    distances.put(next,distance); parents.put(next,from);
                    open.add(new Open(next,distance,distance+heuristic(next),item.steps()+1,order++));
                }
            }
        }
        if (open.isEmpty()) finished=true;
        return finished;
    }
    public List<TacticalNetwork.PreviewStep> result() { return reached == null ? null : route(reached); }
    boolean revalidate(List<TacticalNetwork.PreviewStep> route) {
        Vec3 from = start;
        for (var step : route) { Vec3 to = vector(step.feet()); if (!edge(from, to)) return false; from = to; }
        return true;
    }
}
