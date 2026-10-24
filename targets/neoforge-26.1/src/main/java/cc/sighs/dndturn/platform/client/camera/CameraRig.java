package cc.sighs.dndturn.platform.client.camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
/** Visual pose and loaded-world bounds. Never moves an entity or loads a chunk. */
public final class CameraRig {
    private Vec3 position, focus;
    private double distance = 12;
    private float yaw, pitch;
    public Vec3 position() { return position; }
    public Vec3 focus() { return focus; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public void reset() { position = null; focus = null; }
    public void start(Vec3 focus) { this.focus = focus; yaw = 0; pitch = 55; distance = 12; updateRig(); }
    public void focus(Vec3 value) { focus = value; updateRig(); }
    public void preset() { pitch = pitch < 65 ? 78 : 55; updateRig(); }
    public void zoom(double delta) { distance = Mth.clamp(distance - delta, 3, 32); updateRig(); }
    public void rotate(float horizontal, float vertical) {
        yaw = Mth.wrapDegrees(yaw + horizontal); pitch = Mth.clamp(pitch + vertical, 25, 80); updateRig();
    }
    public void pan(double forwardAxis, double leftAxis, double verticalAxis, double seconds, boolean sprint, double rotation) {
        yaw = Mth.wrapDegrees(yaw + (float)(rotation * seconds * 90));
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 left = new Vec3(forward.z, 0, -forward.x).normalize();
        Vec3 delta = forward.scale(forwardAxis).add(left.scale(leftAxis)).add(0, verticalAxis, 0);
        if (delta.lengthSqr() > 0) move(delta.normalize().scale(seconds * (sprint ? 12 : 6)));
        updateRig();
    }
    private Vec3 forward() { return Vec3.directionFromRotation(pitch, yaw); }
    private void move(Vec3 delta) {
        if (focus == null) return;
        var world = Minecraft.getInstance().level;
        if (world == null) return;
        int steps = Math.max(1, (int)Math.ceil(delta.length() * 2));
        for (int i = 1; i <= steps; i++)
            if (!world.hasChunkAt(BlockPos.containing(focus.add(delta.scale((double)i / steps))))) return;
        focus = focus.add(delta);
        updateRig();
    }
    private void updateRig() {
        if (focus == null) return;
        var mc = Minecraft.getInstance();
        Vec3 desired = focus.subtract(forward().scale(distance));
        // Terrain is cut away by the renderer. Only the loaded-world boundary limits the boom.
        if (mc.level != null && mc.player != null) {
            Vec3 boom = desired.subtract(focus);
            for (int i = 1, count = Math.max(1, (int)Math.ceil(distance * 2)); i <= count; i++) {
                Vec3 point = focus.add(boom.scale((double)i / count));
                if (!mc.level.hasChunkAt(BlockPos.containing(point))) {
                    desired = focus.add(boom.scale((double)(i - 1) / count));
                    break;
                }
            }
        }
        position = desired;
    }
}
