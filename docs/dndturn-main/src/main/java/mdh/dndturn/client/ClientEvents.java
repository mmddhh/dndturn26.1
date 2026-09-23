package mdh.dndturn.client;

import com.mojang.blaze3d.platform.InputConstants;
import mdh.dndturn.Config;
import mdh.dndturn.Dndturn;
import mdh.dndturn.client.camera.TacticalCamera;
import mdh.dndturn.network.DndTurnNetwork;
import mdh.dndturn.network.packet.AttackTargetC2S;
import mdh.dndturn.network.packet.EndTurnC2S;
import mdh.dndturn.network.packet.EnterCombatC2S;
import mdh.dndturn.network.packet.MoveToCellC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@Mod.EventBusSubscriber(modid = Dndturn.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

    private static boolean tacticalActive = false;
    private static boolean leftDown = false;
    private static boolean leftMoved = false;
    private static double leftAccumulated = 0.0D;
    private static boolean rightDown = false;
    private static double lastMouseX;
    private static double lastMouseY;
    private static long lastFrameNanos = System.nanoTime();

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (TacticalCamera.instance().shouldOverride()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        while (ClientSetup.ENTER_COMBAT.consumeClick()) {
            DndTurnNetwork.CHANNEL.sendToServer(new EnterCombatC2S());
        }
        while (ClientSetup.END_TURN.consumeClick()) {
            if (ClientCombatState.inCombat && ClientCombatState.myTurn) {
                DndTurnNetwork.CHANNEL.sendToServer(new EndTurnC2S());
            }
        }
        while (ClientSetup.ATTACK.consumeClick()) {
            if (ClientCombatState.inCombat && ClientCombatState.myTurn && ClientCombatState.myAction) {
                ClientCombatState.attackMode = !ClientCombatState.attackMode;
            }
        }

        TacticalCamera camera = TacticalCamera.instance();
        boolean wantTactical = ClientCombatState.inCombat;
        if (wantTactical && !tacticalActive) {
            tacticalActive = true;
            camera.activate();
            camera.focus((ClientCombatState.gridMinX + ClientCombatState.gridMaxX + 1) / 2.0D,
                    (ClientCombatState.gridMinZ + ClientCombatState.gridMaxZ + 1) / 2.0D);
            minecraft.mouseHandler.releaseMouse();
            lastFrameNanos = System.nanoTime();
        } else if (!wantTactical && tacticalActive) {
            tacticalActive = false;
            camera.deactivate();
            leftDown = false;
            rightDown = false;
            ClientCombatState.activePath.clear();
            minecraft.mouseHandler.grabMouse();
        }

        long now = System.nanoTime();
        double delta = (now - lastFrameNanos) / 1_000_000_000.0D;
        lastFrameNanos = now;
        if (delta < 0.0D || delta > 0.5D) {
            delta = 0.05D;
        }

        if (!tacticalActive || minecraft.screen != null) {
            return;
        }
        minecraft.mouseHandler.releaseMouse();
        long window = minecraft.getWindow().getWindow();

        double panSpeed = camera.getHeight() * 0.6D * Config.cameraPanSpeed * delta;
        double forward = 0.0D;
        double strafe = 0.0D;
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W)) {
            forward += 1.0D;
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S)) {
            forward -= 1.0D;
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D)) {
            strafe += 1.0D;
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A)) {
            strafe -= 1.0D;
        }
        if (forward != 0.0D) {
            camera.panForward(forward * panSpeed);
        }
        if (strafe != 0.0D) {
            camera.panStrafe(strafe * panSpeed);
        }

        float rotateStep = (float) (Config.cameraRotateSpeed * delta);
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_Q)) {
            camera.rotateYaw(-rotateStep);
        }
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_E)) {
            camera.rotateYaw(rotateStep);
        }

        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        double moveX = mouseX - lastMouseX;
        double moveY = mouseY - lastMouseY;
        if (leftDown) {
            leftAccumulated += Math.abs(moveX) + Math.abs(moveY);
            if (leftAccumulated > 4.0D) {
                leftMoved = true;
            }
            if (leftMoved) {
                camera.panScreen(moveX, moveY);
            }
        } else if (rightDown) {
            camera.rotateYaw((float) (moveX * 0.4D));
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientCombatState.inCombat) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.jumping = false;
        input.shiftKeyDown = false;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;

        List<BlockPos> path = ClientCombatState.activePath;
        if (path.isEmpty()) {
            return;
        }
        Player player = event.getEntity();
        BlockPos waypoint = path.get(0);
        double dx = (waypoint.getX() + 0.5D) - player.getX();
        double dz = (waypoint.getZ() + 0.5D) - player.getZ();
        if (dx * dx + dz * dz < 0.09D) {
            path.remove(0);
            if (path.isEmpty()) {
                return;
            }
            waypoint = path.get(0);
            dx = (waypoint.getX() + 0.5D) - player.getX();
            dz = (waypoint.getZ() + 0.5D) - player.getZ();
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        input.forwardImpulse = 1.0F;
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!TacticalCamera.instance().isActive()) {
            return;
        }
        TacticalCamera.instance().zoom(event.getScrollDelta());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientCombatState.inCombat || minecraft.screen != null) {
            return;
        }
        int button = event.getButton();
        int action = event.getAction();
        event.setCanceled(true);
        if (action == GLFW.GLFW_PRESS) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                leftDown = true;
                leftMoved = false;
                leftAccumulated = 0.0D;
                lastMouseX = minecraft.mouseHandler.xpos();
                lastMouseY = minecraft.mouseHandler.ypos();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rightDown = true;
                lastMouseX = minecraft.mouseHandler.xpos();
                lastMouseY = minecraft.mouseHandler.ypos();
            }
        } else if (action == GLFW.GLFW_RELEASE) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (leftDown && !leftMoved) {
                    performSelect();
                }
                leftDown = false;
                leftMoved = false;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rightDown = false;
            }
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!ClientCombatState.inCombat) {
            return;
        }
        if (event.isAttack() || event.isUseItem() || event.isPickBlock()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static void performSelect() {
        if (!ClientCombatState.myTurn || !ClientCombatState.activePath.isEmpty()) {
            return;
        }
        BlockPos hovered = TacticalCamera.instance().getHovered();
        if (ClientCombatState.attackMode) {
            int enemyId = findEnemyAt(hovered);
            if (enemyId >= 0) {
                DndTurnNetwork.CHANNEL.sendToServer(new AttackTargetC2S(enemyId));
                ClientCombatState.attackMode = false;
            }
            return;
        }
        BlockPos target = matchReachable(hovered);
        if (target != null) {
            DndTurnNetwork.CHANNEL.sendToServer(new MoveToCellC2S(target));
        }
    }

    private static BlockPos matchReachable(BlockPos hovered) {
        if (hovered == null) {
            return null;
        }
        for (BlockPos pos : ClientCombatState.reachable) {
            if (pos.getX() == hovered.getX() && pos.getZ() == hovered.getZ()) {
                return pos;
            }
        }
        return null;
    }

    private static int findEnemyAt(BlockPos hovered) {
        if (hovered == null || Minecraft.getInstance().level == null) {
            return -1;
        }
        for (mdh.dndturn.network.packet.CombatStateS2C.Entry entry : ClientCombatState.entries) {
            if (mdh.dndturn.core.Combatant.SIDE_PLAYERS.equals(entry.side())) {
                continue;
            }
            net.minecraft.world.entity.Entity entity = Minecraft.getInstance().level.getEntity(entry.networkId());
            if (entity == null) {
                continue;
            }
            BlockPos pos = entity.blockPosition();
            if (pos.getX() == hovered.getX() && pos.getZ() == hovered.getZ()) {
                return entry.networkId();
            }
        }
        return -1;
    }
}
