package cc.sighs.dndturn.platform.client.render;

import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.spatial.MovementPreviewBudget;
import cc.sighs.dndturn.platform.spatial.PreviewPathfinder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class MovementPreviewChecks {
    private record Line(Vec3 from, Vec3 to, int color) {}
    private static ActionProtocol.PreviewStep step(double x, double y, double z, String kind) {
        return new ActionProtocol.PreviewStep(PreviewPathfinder.value(new Vec3(x,y,z)),kind);
    }
    public static void run() {
        var lines = new ArrayList<Line>();
        var straight = List.of(step(.3,0,0,"WALK"), step(2,0,0,"WALK"));
        MovementPathLines.emit(Vec3.ZERO, straight, 1.25, (a,b,c) -> lines.add(new Line(a,b,c)));
        require(lines.stream().filter(l -> l.color()==MovementPathLines.GRAY).allMatch(l -> l.to().x<=1.25), "gray exceeds stop");
        require(lines.stream().filter(l -> l.color()==MovementPathLines.RED).allMatch(l -> l.from().x>=1.25), "red precedes stop");
        require(close(lines.stream().mapToDouble(l -> l.from().distanceTo(l.to())).sum(),1.4), "dash rhythm restarted at waypoint");
        require(lines.stream().anyMatch(l -> close(l.from().x,1.25) && close(l.to().x,1.5) && l.color()==MovementPathLines.RED), "budget boundary did not split long dash");
        lines.clear();
        MovementPathLines.emit(Vec3.ZERO,List.of(step(.7,0,0,"WALK"),step(.7,0,1.3,"WALK")),10,
            (a,b,c) -> lines.add(new Line(a,b,c)));
        require(close(lines.stream().mapToDouble(l -> l.from().distanceTo(l.to())).sum(),1.4), "turn restarted metre rhythm");
        require(lines.stream().allMatch(l -> l.color()==MovementPathLines.GRAY), "affordable path not gray");
        require(lines.stream().allMatch(l -> close(l.from().z,0) && close(l.to().z,0)
            || close(l.from().x,.7) && close(l.to().x,.7)), "dash cut across a corner");

        var route=List.of(step(2,0,0,"WALK"),step(2,0,8,"WALK"));
        var limited=MovementPreviewBudget.limit(Vec3.ZERO,route,30,.1);
        require(limited.exceeded() && limited.stop().x==2 && limited.stop().z>0 && limited.stop().z<8,"budget ignored route length or corner");
        require(close(limited.distance(),2+limited.stop().z),"color boundary and submitted endpoint disagree");
        require(PreviewPathfinder.vector(limited.route().getLast().feet()).equals(limited.stop()),"truncated route lost stop");
        require(route.size()==2 && route.getLast().feet().z()==8,"budget mutated original preview");
        var zero=MovementPreviewBudget.limit(Vec3.ZERO,route,0,.1);
        require(zero.exceeded() && zero.route().isEmpty() && zero.stop().equals(Vec3.ZERO),"zero budget offered movement");
        require(!MovementPreviewBudget.limit(Vec3.ZERO,route,100,.1).exceeded(),"affordable route truncated");
        require(MovementPreviewBudget.limit(Vec3.ZERO,route,30,.2).distance()>limited.distance(),"speed not reflected");
        require(MovementPreviewBudget.limit(Vec3.ZERO,List.of(step(10,0,0,"SWIM")),30,.1).distance()<limited.distance(),"swim cost ignored");
        require(MovementPreviewBudget.limit(Vec3.ZERO,List.of(step(1,1,0,"JUMP")),2,.1).route().isEmpty(),"stop inside jump");
        require(MovementPreviewBudget.limit(Vec3.ZERO,route,30,Double.NaN).route().isEmpty(),"invalid speed offered movement");
        // Render masks must remain bounded, hysteretic, immutable and world-instance scoped.
        // Existing path checks cannot detect stale terrain uploads or boundary-face invalidation.
        Object world = new Object();
        var empty = new cc.sighs.dndturn.platform.client.camera.CutawayMask(world, java.util.Map.of());
        Vec3 eye = new Vec3(.5, 8.5, .5), focus = new Vec3(.5, .5, .5);
        var mask = empty.move(eye, focus, p -> true);
        var edge = new net.minecraft.core.BlockPos(2, 4, 0);
        require(mask.hides(edge), "cutaway omitted cell center at entry radius");
        var shiftedEye = eye.add(-.1, 0, 0);
        var shiftedFocus = focus.add(-.1, 0, 0);
        require(mask.move(shiftedEye, shiftedFocus, p -> true).hides(edge), "cutaway flickered within hysteresis band");
        require(!empty.move(shiftedEye, shiftedFocus, p -> true).hides(edge), "new cell entered through exit threshold");
        require(!mask.move(eye.add(-1, 0, 0), focus.add(-1, 0, 0), p -> true).hides(edge), "cutaway did not restore outside exit radius");
        require(!mask.hides(new net.minecraft.core.BlockPos(0, -1, 0)), "cutaway removed floor behind focus");
        require(mask.move(eye, focus, p -> false).sections().isEmpty(), "cutaway retained unloaded cells");
        require(empty.sections().isEmpty() && mask.hides(edge), "published cutaway snapshot was mutated");
        require(!mask.matches(new cc.sighs.dndturn.platform.client.camera.CutawayMask(new Object(), mask.sections()), 0), "old-world mesh accepted");
        var boundary = new cc.sighs.dndturn.platform.client.camera.CutawayMask(world, java.util.Map.of(0L, singleCell(15, 15, 15)));
        require(empty.changedSections(boundary).contains(net.minecraft.core.SectionPos.asLong(1, 1, 1)), "cutaway failed to invalidate diagonal AO neighbor");
        require(!empty.matches(boundary, net.minecraft.core.SectionPos.asLong(1, 0, 0)), "stale neighboring mesh accepted");
        require(empty.matches(boundary, net.minecraft.core.SectionPos.asLong(4, 0, 0)), "unrelated mask update invalidated remote mesh");
        // Snapshot storage and precise invalidation are independent of the renderer / GPU.
        long[] words = new long[64]; words[0] = 1;
        var immutable = new cc.sighs.dndturn.platform.client.camera.CutawayMask.Cells(words);
        words[0] = 0;
        require(immutable.contains(net.minecraft.core.BlockPos.ZERO), "bitmap retained writable caller storage");
        for (int x : new int[] {0, 8, 15}) for (int y : new int[] {0, 8, 15}) for (int z : new int[] {0, 8, 15}) {
            long section = net.minecraft.core.SectionPos.asLong(-2, -1, 3);
            var one = new cc.sighs.dndturn.platform.client.camera.CutawayMask(world, java.util.Map.of(section, singleCell(x, y, z)));
            var expected = new java.util.HashSet<Long>();
            var pos = new net.minecraft.core.BlockPos(-32 + x, -16 + y, 48 + z);
            for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) for (int oz = -1; oz <= 1; oz++)
                expected.add(net.minecraft.core.SectionPos.asLong(pos.offset(ox, oy, oz)));
            require(empty.changedSections(one).equals(expected) && one.changedSections(empty).equals(expected), "bitmap changed boundary propagation");
            require(one.local(section).hides(pos) && one.local(section).matches(one, section), "local bitmap lookup changed cells");
        }
        var updates = new cc.sighs.dndturn.platform.client.camera.CutawayMask.Updates();
        int[] probes = {0};
        java.util.function.Predicate<net.minecraft.core.BlockPos> loaded = p -> { probes[0]++; return true; };
        var first = updates.update(empty, eye, focus, p -> true, loaded);
        int count = probes[0];
        require(updates.update(first, eye, focus, p -> true, loaded) == first && probes[0] == count, "stationary mask rescanned cells");
        var unloaded = updates.update(first, eye, focus, p -> false, p -> false);
        require(unloaded.sections().isEmpty(), "stationary unload retained hidden cells");
        var reloaded = updates.update(unloaded, eye, focus, p -> true, loaded);
        require(reloaded.hides(edge) && probes[0] > count, "stationary reload failed to rebuild mask");
        count = probes[0]; updates.reset();
        updates.update(reloaded, eye, focus, p -> true, loaded);
        require(probes[0] > count, "reset retained update cache");
        var newWorld = new cc.sighs.dndturn.platform.client.camera.CutawayMask(new Object(), java.util.Map.of());
        require(updates.update(newWorld, eye, focus, p -> true, loaded).worldIdentity() == newWorld.worldIdentity(), "cache crossed world identity");
        System.out.println("Movement preview geometry and budget checks passed");
    }
    private static cc.sighs.dndturn.platform.client.camera.CutawayMask.Cells singleCell(int x, int y, int z) {
        long[] words = new long[64];
        int bit = (y << 8) | (z << 4) | x;
        words[bit >>> 6] = 1L << (bit & 63);
        return new cc.sighs.dndturn.platform.client.camera.CutawayMask.Cells(words);
    }
    private static boolean close(double a,double b) { return Math.abs(a-b)<1e-8; }
    private static void require(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
}
