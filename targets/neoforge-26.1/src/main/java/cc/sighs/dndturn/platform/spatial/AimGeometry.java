package cc.sighs.dndturn.platform.spatial;

import net.minecraft.world.phys.Vec3;

public final class AimGeometry {
    private AimGeometry() {}
    public record Aim(float yaw, float pitch) {}
    public static Aim plannedAim(Vec3 eye, Vec3 destination) {
        Vec3 delta = destination.subtract(eye);
        return new Aim((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)),
            (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }
}
