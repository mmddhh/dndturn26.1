package cc.sighs.dndturn.platform.client.input;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.client.input.ClientControl;
import cc.sighs.dndturn.platform.client.state.ClientCombatState;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
public final class PlanSteering {
 private PlanSteering() {}
 private static int steeringCursor;
 private static UUID steeringOperation;
 public static void reset() { steeringCursor = 0; steeringOperation = null; }
 public record Steering(Input keys, Vec2 vector) {}
 private static Vec3 vector(ActionIntent.Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    public static Steering steering(UUID requested, ActionProtocol.Projection projection, boolean cancelling) {
        var mc = Minecraft.getInstance();
        var state = ClientCombatState.encounter();
        if (cancelling) return new Steering(Input.EMPTY, Vec2.ZERO);
        if (requested == null || projection == null || !requested.equals(projection.operation()) || !projection.running() || projection.waypoint() == null || mc.player == null
            || state == null || !state.generation().equals(projection.generation()) || !state.encounterId().equals(projection.encounter()) || !ClientCombatState.movementAllowed()) return null;
        if (mc.screen != null || !mc.isWindowActive() || ClientControl.modal()) return new Steering(Input.EMPTY, Vec2.ZERO);
        GridCell cell = projection.waypoint();
        if (!projection.operation().equals(steeringOperation)) { steeringOperation=projection.operation(); steeringCursor=projection.cursor(); }
        steeringCursor=Math.max(steeringCursor,projection.cursor());
        var path=projection.route();
        if (!path.isEmpty()) {
            while (steeringCursor<path.size()-1
                && mc.player.position().distanceToSqr(vector(path.get(steeringCursor).feet()))<.09) steeringCursor++;
        }
        Vec3 waypoint = projection.route().isEmpty() ? new Vec3(cell.x() + .5, cell.y(), cell.z() + .5)
            : vector(path.get(steeringCursor).feet());
        Vec3 delta = waypoint.subtract(mc.player.position());
        boolean finalPoint=path.isEmpty() || steeringCursor==path.size()-1;
        if (delta.lengthSqr() < (path.isEmpty() ? .16 : finalPoint?.0064:.09)) return new Steering(Input.EMPTY, Vec2.ZERO);
        double yaw = Math.toRadians(mc.player.getYRot());
        double forward = -Math.sin(yaw) * delta.x + Math.cos(yaw) * delta.z;
        double left = Math.cos(yaw) * delta.x + Math.sin(yaw) * delta.z;
        double length = Math.max(.001, Math.hypot(forward, left));
        float scale = finalPoint ? (float)Math.min(1, delta.horizontalDistance() * 3) : 1;
        boolean jump = delta.y > .5 && mc.player.onGround();
        return new Steering(new Input(forward > .01, forward < -.01, left > .01, left < -.01, jump, false, false),
            new Vec2((float)(left / length) * scale, (float)(forward / length) * scale));
    }
}
