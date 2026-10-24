package cc.sighs.dndturn.platform.client.input;

import cc.sighs.dndturn.platform.client.action.ClientTacticalPlan;
import cc.sighs.dndturn.platform.client.state.ClientCombatState;
import cc.sighs.dndturn.platform.client.ui.ConsentOverlay;
import cc.sighs.dndturn.platform.client.ui.OverlayUi;
import cc.sighs.dndturn.platform.client.ui.TacticalOverlay;
import cc.sighs.dndturn.platform.mixin.client.ui.AuiPointerStateAccessor;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Client-thread owner of virtual pose, temporary recipient and gesture ownership.
 * Server projections remain in ClientCombatState; none of these values grant a permit. */
public final class ClientControl {
    public enum Recipient { GAME, SCREEN, UNFOCUSED, CONSENT, TACTICAL, CAMERA, BINDING }
    private record Gesture(Recipient owner, Document document, long revision, double x, double y) {}
    private static Recipient recipient = Recipient.GAME;
    private static boolean session;
    private static Object level, player;
    private static Entity cameraOwner;
    private static final cc.sighs.dndturn.platform.client.camera.CameraRig rig = new cc.sighs.dndturn.platform.client.camera.CameraRig();
    private static long frameTime, revision;
    private static boolean restoreGrab;
    private static final Set<KeyMapping> cameraKeys = new HashSet<>();
    private static final Set<InputConstants.Key> heldKeys = new HashSet<>();
    private static final Set<InputConstants.Key> suppressedKeys = new HashSet<>();
    private static final Map<Integer, Gesture> gestures = new HashMap<>();
    private static Gesture dispatch;
    private static double mouseX = Double.NaN, mouseY;
    private static EncounterProtocol.ConsentState consent;

    private ClientControl() {}
    public static EncounterProtocol.ConsentState consent() { return consent; }
    public static void consent(EncounterProtocol.ConsentState value) { consent = value; reconcile(); }
    public static boolean modal() { return consent != null; }
    public static boolean active() { return session; }

    public static void reconcile() {
        Minecraft mc = Minecraft.getInstance();
        boolean valid = ClientCombatState.encounter() != null && mc.player != null && mc.player.isAlive()
            && mc.level != null && mc.getConnection() != null;
        if (level != mc.level || player != mc.player) {
            reset(); level = mc.level; player = mc.player;
        }
        if (valid && !session) {
            session = true;
            cameraOwner = mc.getCameraEntity();
            rig.start(mc.player.position().add(0, 1, 0));
            restoreGrab = mc.mouseHandler.isMouseGrabbed();
            transition();
        } else if (!valid && session) {
            ClientTacticalPlan.reset();
            session = false; rig.reset(); cameraOwner = null; transition();
        }
        Recipient next = !mc.isWindowActive() ? Recipient.UNFOCUSED : mc.screen != null ? Recipient.SCREEN
            : modal() ? Recipient.CONSENT : session ? Recipient.CAMERA
            : Recipient.GAME;
        if (next != recipient) {
            boolean controlled = session || modal() || recipient == Recipient.CAMERA
                || recipient == Recipient.CONSENT;
            recipient = next;
            if (controlled) transition();
        }
        if (mc.player == null || mc.screen != null || !mc.isWindowActive()) return;
        if (next == Recipient.CONSENT || next == Recipient.CAMERA) {
            if (mc.mouseHandler.isMouseGrabbed()) { restoreGrab = true; mc.mouseHandler.releaseMouse(); }
        } else if (next == Recipient.GAME && restoreGrab
            && !mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.grabMouse(); restoreGrab = false;
        }
    }

    private static void transition() {
        revision++; frameTime = 0; mouseX = Double.NaN; cameraKeys.clear();
        suppressedKeys.addAll(heldKeys);
        gestures.replaceAll((button, gesture) -> new Gesture(gesture.owner, null, -1, gesture.x, gesture.y));
        clearKeys();
        TacticalOverlay.cancelGesture(); ConsentOverlay.cancelGesture();
        AuiPointerStateAccessor.dndturn$buttons(0);
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler instanceof MouseInputReset reset) reset.dndturn$resetInput();
        if (mc.player != null) {
            mc.player.input.keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
            if (mc.player.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(mc.player);
        }
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }

    public static void reset() {
        boolean controlled = session || consent != null || rig.position() != null;
        session = false; consent = null; rig.reset(); cameraOwner = null;
        ClientTacticalPlan.reset();
        recipient = Recipient.GAME; level = null; player = null;
        restoreGrab = false;
        if (controlled) transition();
        // Retain consumed gesture tombstones until release; never restore an old entity reference.
    }

    public static boolean blockWorldActions() { return modal() || session || ClientCombatState.gameplayPaused(); }
    public static boolean blockMovement() {
        return modal() || session || ClientTacticalPlan.running()
            || ClientCombatState.gameplayPaused() && !ClientCombatState.movementAllowed();
    }
    public static boolean blockTurn() { return modal() || session; }

    public static boolean key(KeyEvent event, int action) {
        reconcile();
        InputConstants.Key code = InputConstants.getKey(event);
        boolean suppressed = suppressedKeys.contains(code);
        if (action == GLFW.GLFW_RELEASE) { heldKeys.remove(code); suppressedKeys.remove(code); }
        else heldKeys.add(code);
        if (suppressed) {
            for (KeyMapping key : Minecraft.getInstance().options.keyMappings) if (key.matches(event)) clear(key);
            return false;
        }
        for (KeyMapping mapping : movementKeys()) {
            if (!mapping.matches(event)) continue;
            if (action == GLFW.GLFW_RELEASE) cameraKeys.remove(mapping);
            if (action == GLFW.GLFW_PRESS && recipient == Recipient.CAMERA
                && mapping.isActiveAndMatches(InputConstants.getKey(event))
                && (mapping == CombatControls.rotateLeftKey()
                    || mapping == CombatControls.rotateRightKey() || !CombatControls.matchesAction(event)))
                cameraKeys.add(mapping);
        }
        return true;
    }
    private static KeyMapping[] movementKeys() {
        var o = Minecraft.getInstance().options;
        return new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight,
            CombatControls.rotateLeftKey(), CombatControls.rotateRightKey(), o.keySprint};
    }
    static void clear(KeyMapping key) { key.setDown(false); while (key.consumeClick()) {} }
    private static void clearKeys() {
        var mc = Minecraft.getInstance();
        if (mc.options == null) return;
        for (KeyMapping key : mc.options.keyMappings) clear(key);
    }
    /** Before vanilla consumes click queues. Screens keep inventory/chat/menu behavior. */
    public static void beforeKeybinds() {
        reconcile();
        if (!blockWorldActions()) return;
        var o = Minecraft.getInstance().options;
        for (KeyMapping key : new KeyMapping[]{o.keyAttack, o.keyUse, o.keyPickItem, o.keyDrop}) clear(key);
        if (session || modal()) {
            for (KeyMapping key : o.keyHotbarSlots) clear(key);
            clear(o.keySwapOffhand);
        }
    }

    public static void beginMouse(int button, int action) {
        reconcile();
        if (action == GLFW.GLFW_PRESS) {
            Recipient who = recipient;
            Document doc = null;
            if (who == Recipient.CONSENT) doc = ConsentOverlay.document();
            else if (who == Recipient.CAMERA && TacticalOverlay.hit(mousePosition())) {
                who = Recipient.TACTICAL; doc = TacticalOverlay.document();
            }
            else if (who == Recipient.CAMERA && CombatControls.hasMouseBinding(button))
                who = Recipient.BINDING;
            gestures.put(button, new Gesture(who, doc, revision, Minecraft.getInstance().mouseHandler.xpos(), Minecraft.getInstance().mouseHandler.ypos()));
        }
        dispatch = gestures.get(button);
    }
    public static boolean endMouse(int button, int action, boolean consumed) {
        Gesture gesture = dispatch;
        dispatch = null;
        if (action == GLFW.GLFW_RELEASE) gestures.remove(button);
        if (gesture == null) return consumed || session && recipient != Recipient.SCREEN || modal();
        boolean ours = gesture.owner != Recipient.GAME && gesture.owner != Recipient.SCREEN;
        boolean binding = false;
        if (gesture.revision == revision && (gesture.owner == Recipient.CAMERA || gesture.owner == Recipient.BINDING)
            && !consumed) {
            binding = CombatControls.mouseBinding(button, action);
            InputConstants.Key code = InputConstants.Type.MOUSE.getOrCreate(button);
            for (KeyMapping key : movementKeys()) if (key.getKey().equals(code)) {
                if (action == GLFW.GLFW_RELEASE) cameraKeys.remove(key);
                else if (gesture.owner == Recipient.CAMERA && !binding) cameraKeys.add(key);
                binding = true;
            }
        }
        if (action == GLFW.GLFW_RELEASE && gesture.owner == Recipient.CAMERA && gesture.revision == revision
            && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            && Math.hypot(Minecraft.getInstance().mouseHandler.xpos()-gesture.x, Minecraft.getInstance().mouseHandler.ypos()-gesture.y) < 5
            && !consumed && !binding) select(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        return consumed || ours;
    }
    /** Invoked at AUI's per-document dispatch seam: no duplicate dispatch, no lower document leakage. */
    public static boolean allowDocument(Document doc) {
        if (!OverlayUi.stylesReady(doc)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (doc == TacticalOverlay.document() && !TacticalOverlay.hudVisible()) return false;
        if (mc.screen != null || !mc.isWindowActive())
            return doc != TacticalOverlay.document() && doc != ConsentOverlay.document();
        if (dispatch != null && dispatch.owner != Recipient.GAME && dispatch.owner != Recipient.SCREEN)
            return dispatch.revision == revision && dispatch.document == doc;
        if (modal()) return doc == ConsentOverlay.document();
        if (session) {
            for (Gesture g : gestures.values()) if (g.revision == revision && g.document != null) return g.document == doc;
            return doc == TacticalOverlay.document();
        }
        return true;
    }
    public static boolean routeAui() {
        return Minecraft.getInstance().screen == null && (session || modal()
            || dispatch != null && dispatch.owner != Recipient.GAME && dispatch.owner != Recipient.SCREEN);
    }
    public static Document mouseDocument() {
        if (!Minecraft.getInstance().isWindowActive()) return null;
        if (dispatch != null) return dispatch.revision == revision && dispatch.document != null && dispatch.document.isActive() ? dispatch.document : null;
        if (modal()) return ConsentOverlay.document();
        for (Gesture gesture : gestures.values()) return gesture.revision == revision ? gesture.document : null;
        return session ? TacticalOverlay.document() : null;
    }
    public static void documentClosed(Document document) {
        if (document == null) return;
        gestures.replaceAll((button, gesture) -> gesture.document == document ? new Gesture(gesture.owner, null, -1, gesture.x, gesture.y) : gesture);
        if (dispatch != null && dispatch.document == document) dispatch = new Gesture(dispatch.owner, null, -1, dispatch.x, dispatch.y);
    }
    public static boolean scroll(double delta, boolean consumed) {
        reconcile();
        if (Minecraft.getInstance().screen != null) return consumed;
        if (!consumed && recipient == Recipient.CAMERA && !TacticalOverlay.hit(mousePosition())) {
            rig.zoom(delta);
        }
        return consumed || session || modal();
    }
    private static Position mousePosition() {
        var mc = Minecraft.getInstance();
        return new Position(mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }
    public static void mouseMove(double x, double y) {
        reconcile();
        Gesture g = gestures.get(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        if (!Double.isNaN(mouseX) && recipient == Recipient.CAMERA && g != null
            && g.owner == Recipient.CAMERA && g.revision == revision) {
            var o = Minecraft.getInstance().options;
            double s = Math.pow(o.sensitivity().get() * .6 + .2, 3) * 8 * .15;
            rig.rotate((float)((x - mouseX) * s * (o.invertMouseX().get() ? -1 : 1)),
                (float)((y - mouseY) * s * (o.invertMouseY().get() ? -1 : 1)));
        }
        mouseX = x; mouseY = y;
    }
    public static boolean camera(Camera camera) {
        reconcile();
        var mc = Minecraft.getInstance();
        return session && rig.position() != null && camera == mc.gameRenderer.getMainCamera()
            && mc.getCameraEntity() == cameraOwner && cameraOwner == mc.player;
    }
    public static void frame() {
        long now = System.nanoTime();
        double seconds = frameTime == 0 ? 0 : Math.min(.05, (now - frameTime) / 1e9);
        frameTime = now;
        if (recipient != Recipient.CAMERA) return;
        var o = Minecraft.getInstance().options;
        double f = axis(o.keyUp, o.keyDown), l = axis(o.keyLeft, o.keyRight);
        rig.pan(f, l, seconds, cameraKeys.contains(o.keySprint),
            axis(CombatControls.rotateRightKey(), CombatControls.rotateLeftKey()));
    }
    private static int axis(KeyMapping positive, KeyMapping negative) {
        return (cameraKeys.contains(positive) ? 1 : 0) - (cameraKeys.contains(negative) ? 1 : 0);
    }



    public static void home() {
        var mc = Minecraft.getInstance();
        if (session && mc.player != null) { rig.focus(mc.player.position().add(0,1,0)); }
    }
    public static void preset() { if (session) { rig.preset(); } }
    private static boolean escapeHeld;
    public static boolean escape(int action, net.minecraft.client.input.KeyEvent event) {
        var mc = Minecraft.getInstance();
        if (event.key() != GLFW.GLFW_KEY_ESCAPE) return false;
        if (action == GLFW.GLFW_RELEASE) { boolean consumed = escapeHeld; escapeHeld = false; return consumed; }
        if (escapeHeld) return true;
        if (action != GLFW.GLFW_PRESS || mc.screen != null || !mc.isWindowActive()) return false;
        if (modal()) { escapeHeld = true; return true; }
        if (!session) return false;
        if (ClientTacticalPlan.phase() != ClientTacticalPlan.Phase.IDLE) {
            ClientTacticalPlan.cancel(); escapeHeld = true; return true;
        }
        if (TacticalOverlay.closePanel()) { escapeHeld = true; return true; }
        return false;
    }
    public static Vec3 position() { return rig.position(); }
    public static float yaw() { return rig.yaw(); }
    public static float pitch() { return rig.pitch(); }

    private static void select(boolean execute) {
        var pick = previewPick();
        if (pick == null) return;
        if (!execute && ClientTacticalPlan.previewClick(pick)) return;
        ClientTacticalPlan.worldClick(execute, pick.entity(), pick.block());
    }
    /** Input rotation, independent of the camera pose and of action target selection. */
    public static void updateFacing(net.minecraft.client.player.LocalPlayer actor) {
        var mc = Minecraft.getInstance();
        if (actor != mc.player || !actor.isAlive() || actor.isSleeping()
            || ClientCombatState.bodySimulationPaused()) return;
        var pick = previewPick();
        if (pick == null || pick.contact() == null) return;
        Vec3 delta = pick.contact().subtract(actor.getEyePosition());
        double horizontal = Math.hypot(delta.x, delta.z);
        if (!Double.isFinite(horizontal) || !Double.isFinite(delta.y) || delta.lengthSqr() < 1.0E-10) return;
        if (horizontal > 1.0E-5) {
            float targetYaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
            actor.setYRot(actor.getYRot() + Mth.wrapDegrees(targetYaw - actor.getYRot()));
        }
        actor.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, horizontal)));
        // Player.aiStep/tickHeadTurn own head/body follow; sendPosition owns network updates.
    }

    public record WorldPick(Vec3 origin, Vec3 end, java.util.UUID entity,
                            net.minecraft.world.phys.BlockHitResult block, Vec3 contact) {}
    /** Own vanilla's display pick while the tactical camera owns input. No action authorization. */
    public static boolean updateCrosshairPick() {
        var mc = Minecraft.getInstance();
        if (!camera(mc.gameRenderer.getMainCamera())) return false;
        var pick = previewPick();
        mc.hitResult = null;
        mc.crosshairPickEntity = null;
        if (pick == null) return true;
        if (pick.entity() == null) mc.hitResult = pick.block();
        else for (Entity entity : mc.level.entitiesForRendering()) {
            if (!entity.getUUID().equals(pick.entity())) continue;
            mc.crosshairPickEntity = entity;
            mc.hitResult = new net.minecraft.world.phys.EntityHitResult(entity, pick.contact());
            break;
        }
        return true;
    }

    public static WorldPick previewPick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || !mc.isWindowActive() || modal()
            || TacticalOverlay.hit(mousePosition())) return null;
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera(camera)) return null;
        double x = mc.mouseHandler.xpos() / mc.getWindow().getScreenWidth() * 2 - 1;
        double y = 1 - mc.mouseHandler.ypos() / mc.getWindow().getScreenHeight() * 2;
        double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
        // Read both origin and orientation from this rig. Camera.rotation() is only
        // refreshed during render update and can still describe the previous frame.
        Quaternionf rotation = new Quaternionf().rotationYXZ((float)Math.PI - rig.yaw() * Mth.DEG_TO_RAD,
            -rig.pitch() * Mth.DEG_TO_RAD, 0);
        Vector3f ray = new Vector3f((float)(x * tan * mc.getWindow().getScreenWidth() / mc.getWindow().getScreenHeight()),
            (float)(y * tan), -1).rotate(rotation).normalize();
        Vec3 direction = new Vec3(ray.x, ray.y, ray.z);
        double range = 0;
        while (range < 64 && mc.level.hasChunkAt(BlockPos.containing(rig.position().add(direction.scale(range + 1))))) range++;
        Vec3 end = rig.position().add(direction.scale(range));
        var block = mc.level.clip(new ClipContext(rig.position(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        double nearest = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(rig.position());
        java.util.UUID selected = null;
        Vec3 contact = block.getType() == HitResult.Type.MISS ? null : block.getLocation();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || entity == mc.player || !entity.isAlive()) continue;
            var hit = entity.getBoundingBox().inflate(.1).clip(rig.position(), end);
            if (hit.isPresent() && hit.get().distanceToSqr(rig.position()) < nearest) {
                nearest = hit.get().distanceToSqr(rig.position()); selected = entity.getUUID();
                contact = hit.get();
            }
        }
        return new WorldPick(rig.position(), end, selected, block, contact);
    }
}
