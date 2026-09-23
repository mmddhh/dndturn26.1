package mdh.dndturn.client.camera;

import mdh.dndturn.client.ClientCombatState;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TacticalCamera {

    private static final TacticalCamera INSTANCE = new TacticalCamera();
    private static final float TRANSITION_SECONDS = 0.8F;

    private boolean active;
    private float blend;
    private long lastNanos;
    private double centerX;
    private double centerZ;
    private double height = 30.0D;
    private float yaw = 0.0F;
    private float pitch = 45.0F;
    private BlockPos hovered;
    private double hoverY;

    private TacticalCamera() {
    }

    public static TacticalCamera instance() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    public boolean shouldOverride() {
        return blend > 0.002F;
    }

    public float getBlend() {
        return blend;
    }

    public void activate() {
        active = true;
        hovered = null;
        lastNanos = System.nanoTime();
    }

    public void deactivate() {
        active = false;
        hovered = null;
        lastNanos = System.nanoTime();
    }

    public void updateBlend() {
        long now = System.nanoTime();
        float delta = (now - lastNanos) / 1_000_000_000.0F;
        lastNanos = now;
        if (delta < 0.0F || delta > 0.5F) {
            delta = 0.016F;
        }
        float target = active ? 1.0F : 0.0F;
        float step = delta / TRANSITION_SECONDS;
        if (blend < target) {
            blend = Math.min(target, blend + step);
        } else if (blend > target) {
            blend = Math.max(target, blend - step);
        }
    }

    public void focus(double x, double z) {
        this.centerX = x;
        this.centerZ = z;
    }

    public void rotateYaw(float degrees) {
        this.yaw = (float) ((this.yaw + degrees) % 360.0D);
        if (this.yaw < 0.0F) {
            this.yaw += 360.0F;
        }
    }

    public void panForward(double amount) {
        centerX += forwardX() * amount;
        centerZ += forwardZ() * amount;
        clampToGrid();
    }

    public void panStrafe(double amount) {
        centerX += rightX() * amount;
        centerZ += rightZ() * amount;
        clampToGrid();
    }

    public void panScreen(double dx, double dy) {
        double scale = height * 0.0022D;
        double horizontal = dx * scale;
        double vertical = dy * scale;
        centerX -= rightX() * horizontal;
        centerZ -= rightZ() * horizontal;
        centerX += forwardX() * vertical;
        centerZ += forwardZ() * vertical;
        clampToGrid();
    }

    private double forwardX() {
        return -Math.sin(Math.toRadians(yaw));
    }

    private double forwardZ() {
        return Math.cos(Math.toRadians(yaw));
    }

    private double rightX() {
        return -Math.cos(Math.toRadians(yaw));
    }

    private double rightZ() {
        return -Math.sin(Math.toRadians(yaw));
    }

    public void zoom(double scrollDelta) {
        height *= Math.exp(-scrollDelta * 0.15D);
        if (height < 8.0D) {
            height = 8.0D;
        }
        if (height > 80.0D) {
            height = 80.0D;
        }
    }

    private void clampToGrid() {
        if (ClientCombatState.inCombat) {
            if (centerX < ClientCombatState.gridMinX) {
                centerX = ClientCombatState.gridMinX;
            }
            if (centerX > ClientCombatState.gridMaxX + 1) {
                centerX = ClientCombatState.gridMaxX + 1;
            }
            if (centerZ < ClientCombatState.gridMinZ) {
                centerZ = ClientCombatState.gridMinZ;
            }
            if (centerZ > ClientCombatState.gridMaxZ + 1) {
                centerZ = ClientCombatState.gridMaxZ + 1;
            }
        }
    }

    public double getHeight() {
        return height;
    }

    public double getCameraX() {
        return centerX - lookX() * distanceToFloor();
    }

    public double getCameraY() {
        return ClientCombatState.floorY + height;
    }

    public double getCameraZ() {
        return centerZ - lookZ() * distanceToFloor();
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    private double distanceToFloor() {
        double sin = Math.sin(Math.toRadians(pitch));
        if (sin < 1.0E-4D) {
            return 0.0D;
        }
        return height / sin;
    }

    private double lookX() {
        return -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
    }

    private double lookZ() {
        return Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
    }

    public BlockPos getHovered() {
        return hovered;
    }

    public void setHovered(BlockPos hovered) {
        this.hovered = hovered;
    }

    public double getHoverY() {
        return hoverY;
    }

    public void setHoverY(double hoverY) {
        this.hoverY = hoverY;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }
}
