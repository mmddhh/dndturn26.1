package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.MovementPreviewBudget;
import cc.sighs.dndturn.combat.PreviewPathfinder;
import cc.sighs.dndturn.combat.TacticalNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

final class MovementPreviewChecks {
    private record Line(Vec3 from, Vec3 to, int color) {}
    private static TacticalNetwork.PreviewStep step(double x, double y, double z, String kind) {
        return new TacticalNetwork.PreviewStep(PreviewPathfinder.value(new Vec3(x,y,z)),kind);
    }
    static void run() {
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
        System.out.println("Movement preview geometry and budget checks passed");
    }
    private static boolean close(double a,double b) { return Math.abs(a-b)<1e-8; }
    private static void require(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
}
