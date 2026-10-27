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
    private Vec3 geometryFocus, desiredPosition;
    private double geometryDistance;
    private float geometryYaw, geometryPitch;
    private BlockPos[] boomSamples = new BlockPos[0];
    private Vec3[] boomPositions = new Vec3[0];
    public Vec3 position() { return position; }
    public Vec3 focus() { return focus; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public void reset() { position = null; focus = null; geometryFocus = null; desiredPosition = null;
        boomSamples = new BlockPos[0]; boomPositions = new Vec3[0]; }
    public void start(Vec3 focus) { this.focus = focus; yaw = 0; pitch = 55; distance = 12; updateRig(); }
    public void focus(Vec3 value) { focus = value; updateRig(); }
    public void preset() { pitch = pitch < 65 ? 78 : 55; updateRig(); }
    public void zoom(double delta) { distance = Mth.clamp(distance - delta, 3, 32); updateRig(); }
    public void rotate(float horizontal, float vertical) {
        yaw = Mth.wrapDegrees(yaw + horizontal); pitch = Mth.clamp(pitch + vertical, 25, 80); updateRig();
    }
    public void pan(double forwardAxis, double leftAxis, double verticalAxis, double seconds, boolean sprint, double rotation) {
        yaw = Mth.wrapDegrees(yaw + (float)(rotation * seconds * 90));
        if (forwardAxis == 0 && leftAxis == 0 && verticalAxis == 0) { updateRig(); return; }
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
    }
    private void updateRig() {
        if (focus == null) return;
        var mc = Minecraft.getInstance();
        if (!focus.equals(geometryFocus) || distance != geometryDistance || yaw != geometryYaw || pitch != geometryPitch) {
            geometryFocus = focus; geometryDistance = distance; geometryYaw = yaw; geometryPitch = pitch;
            desiredPosition = focus.subtract(forward().scale(distance));
            Vec3 boom = desiredPosition.subtract(focus);
            int count = Math.max(1, (int)Math.ceil(distance * 2));
            boomPositions = new Vec3[count + 1];
            boomSamples = new BlockPos[count + 1];
            boomPositions[0] = focus;
            for (int i = 1; i <= count; i++) {
                boomPositions[i] = focus.add(boom.scale((double)i / count));
                boomSamples[i] = BlockPos.containing(boomPositions[i]);
            }
        }
        Vec3 desired = desiredPosition;
        // Recheck loading without reallocating geometry; unloaded columns can recover while stationary.
        if (mc.level != null && mc.player != null) {
            int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
            for (int i = 1; i < boomSamples.length; i++) {
                BlockPos point = boomSamples[i];
                int x = point.getX() >> 4, z = point.getZ() >> 4;
                if (x == lastX && z == lastZ) continue;
                if (!mc.level.hasChunkAt(point)) { desired = boomPositions[i - 1]; break; }
                lastX = x; lastZ = z;
            }
        }
        position = desired;
    }
}
