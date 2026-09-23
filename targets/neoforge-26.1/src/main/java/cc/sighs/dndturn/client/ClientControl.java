package cc.sighs.dndturn.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import cc.sighs.dndturn.combat.CombatNetwork;
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
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Client-thread owner of mode, virtual pose, temporary recipient and gesture ownership.
 * Server projections remain in ClientCombatState; none of these values grant a permit. */
public final class ClientControl {
    public enum Mode { CAMERA, CHARACTER }
    public enum Recipient { GAME, SCREEN, UNFOCUSED, CONSENT, TACTICAL, CAMERA, CHARACTER, BINDING }
    private record Gesture(Recipient owner, Document document, long revision, double x, double y) {}
    private static Mode mode = Mode.CAMERA;
    private static Recipient recipient = Recipient.GAME;
    private static boolean session;
    private static Object level, player;
    private static Entity cameraOwner;
    private static Vec3 position, focus;
    private static double distance = 12;
    private static float yaw, pitch;
    private static long frameTime, revision;
    private static boolean restoreGrab;
    private static final Set<KeyMapping> cameraKeys = new HashSet<>();
    private static final Set<InputConstants.Key> heldKeys = new HashSet<>();
    private static final Set<InputConstants.Key> suppressedKeys = new HashSet<>();
    private static final Map<Integer, Gesture> gestures = new HashMap<>();
    private static Gesture dispatch;
    private static double mouseX = Double.NaN, mouseY;
    private static CombatNetwork.ConsentState consent;

    private ClientControl() {}
    public static Mode mode() { return mode; }
    public static CombatNetwork.ConsentState consent() { return consent; }
    public static void consent(CombatNetwork.ConsentState value) { consent = value; reconcile(); }
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
            session = true; mode = Mode.CAMERA;
            cameraOwner = mc.getCameraEntity();
            focus = mc.player.position().add(0, 1, 0);
            yaw = 0; pitch = 55; distance = 12;
            updateRig();
            restoreGrab = mc.mouseHandler.isMouseGrabbed();
            transition();
        } else if (!valid && session) {
            ClientTacticalPlan.reset();
            session = false; position = null; focus = null; cameraOwner = null; transition();
        }
        Recipient next = !mc.isWindowActive() ? Recipient.UNFOCUSED : mc.screen != null ? Recipient.SCREEN
            : modal() ? Recipient.CONSENT : session ? mode == Mode.CAMERA ? Recipient.CAMERA : Recipient.CHARACTER
            : Recipient.GAME;
        if (next != recipient) {
            boolean controlled = session || modal() || recipient == Recipient.CAMERA
                || recipient == Recipient.CHARACTER || recipient == Recipient.CONSENT;
            recipient = next;
            if (controlled) transition();
        }
        if (mc.player == null || mc.screen != null || !mc.isWindowActive()) return;
        if (next == Recipient.CONSENT || next == Recipient.CAMERA) {
            if (mc.mouseHandler.isMouseGrabbed()) { restoreGrab = true; mc.mouseHandler.releaseMouse(); }
        } else if ((next == Recipient.CHARACTER || next == Recipient.GAME && restoreGrab)
            && !mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.grabMouse(); restoreGrab = false;
        }
    }

    public static void toggleMode() {
        if (!session || modal()) return;
        mode = mode == Mode.CAMERA ? Mode.CHARACTER : Mode.CAMERA;
        transition(); reconcile();
    }

    private static void transition() {
        revision++; frameTime = 0; mouseX = Double.NaN; cameraKeys.clear();
        suppressedKeys.addAll(heldKeys);
        gestures.replaceAll((button, gesture) -> new Gesture(gesture.owner, null, -1, gesture.x, gesture.y));
        clearKeys();
        TacticalOverlay.cancelGesture(); ConsentOverlay.cancelGesture();
        cc.sighs.dndturn.mixin.client.AuiPointerStateAccessor.dndturn$buttons(0);
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler instanceof MouseInputReset reset) reset.dndturn$resetInput();
        if (mc.player != null) {
            mc.player.input.keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
            if (mc.player.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(mc.player);
        }
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
    }

    public static void reset() {
        boolean controlled = session || consent != null || position != null;
        session = false; consent = null; position = null; focus = null; cameraOwner = null;
        ClientTacticalPlan.reset();
        mode = Mode.CAMERA; recipient = Recipient.GAME; level = null; player = null;
        restoreGrab = false;
        if (controlled) transition();
        // Retain consumed gesture tombstones until release; never restore an old entity reference.
    }

    public static boolean blockWorldActions() { return modal() || session || ClientCombatState.gameplayPaused(); }
    public static boolean blockMovement() {
        Minecraft mc = Minecraft.getInstance();
        return modal() || ClientTacticalPlan.running() || session && (mode != Mode.CHARACTER || mc.screen != null || !mc.isWindowActive()
            || mc.getCameraEntity() != cameraOwner || !ClientCombatState.movementAllowed());
    }
    public static boolean blockTurn() {
        Minecraft mc = Minecraft.getInstance();
        return modal() || session && (mode == Mode.CAMERA || mc.screen != null || !mc.isWindowActive());
    }

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
                && (mapping == CombatControls.cameraUpKey() || mapping == CombatControls.rotateLeftKey()
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
            else if ((who == Recipient.CAMERA || who == Recipient.CHARACTER) && CombatControls.hasMouseBinding(button))
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
        if (gesture.revision == revision && (gesture.owner == Recipient.CAMERA || gesture.owner == Recipient.CHARACTER || gesture.owner == Recipient.BINDING)
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
        Minecraft mc = Minecraft.getInstance();
        if (doc == TacticalOverlay.document() && !TacticalOverlay.hudVisible()) return false;
        if (mc.screen != null || !mc.isWindowActive())
            return doc != TacticalOverlay.document() && doc != ConsentOverlay.document();
        if (dispatch != null && dispatch.owner != Recipient.GAME && dispatch.owner != Recipient.SCREEN)
            return dispatch.revision == revision && dispatch.document == doc;
        if (modal()) return doc == ConsentOverlay.document();
        if (session) {
            for (Gesture g : gestures.values()) if (g.revision == revision && g.document != null) return g.document == doc;
            return mode == Mode.CAMERA && doc == TacticalOverlay.document();
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
        return session && mode == Mode.CAMERA ? TacticalOverlay.document() : null;
    }
    static void documentClosed(Document document) {
        if (document == null) return;
        gestures.replaceAll((button, gesture) -> gesture.document == document ? new Gesture(gesture.owner, null, -1, gesture.x, gesture.y) : gesture);
        if (dispatch != null && dispatch.document == document) dispatch = new Gesture(dispatch.owner, null, -1, dispatch.x, dispatch.y);
    }
    public static boolean scroll(double delta, boolean consumed) {
        reconcile();
        if (Minecraft.getInstance().screen != null) return consumed;
        if (!consumed && recipient == Recipient.CAMERA && !TacticalOverlay.hit(mousePosition())) {
            distance = Mth.clamp(distance - delta, 3, 32);
            updateRig();
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
            yaw += (float)((x - mouseX) * s * (o.invertMouseX().get() ? -1 : 1));
            pitch = Mth.clamp(pitch + (float)((y - mouseY) * s * (o.invertMouseY().get() ? -1 : 1)), 25, 80);
            updateRig();
        }
        mouseX = x; mouseY = y;
    }
    public static boolean camera(Camera camera) {
        reconcile();
        var mc = Minecraft.getInstance();
        return session && mode == Mode.CAMERA && position != null && camera == mc.gameRenderer.getMainCamera()
            && mc.getCameraEntity() == cameraOwner && cameraOwner == mc.player;
    }
    public static void frame() {
        long now = System.nanoTime();
        double seconds = frameTime == 0 ? 0 : Math.min(.05, (now - frameTime) / 1e9);
        frameTime = now;
        if (recipient != Recipient.CAMERA) return;
        var o = Minecraft.getInstance().options;
        double f = axis(o.keyUp, o.keyDown), l = axis(o.keyLeft, o.keyRight);
        yaw = Mth.wrapDegrees(yaw + (float)(axis(CombatControls.rotateRightKey(), CombatControls.rotateLeftKey()) * seconds * 90));
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 left = new Vec3(forward.z, 0, -forward.x).normalize();
        Vec3 delta = forward.scale(f).add(left.scale(l));
        if (delta.lengthSqr() > 0) move(delta.normalize().scale(seconds * (cameraKeys.contains(o.keySprint) ? 12 : 6)));
        updateRig();
    }
    private static int axis(KeyMapping positive, KeyMapping negative) {
        return (cameraKeys.contains(positive) ? 1 : 0) - (cameraKeys.contains(negative) ? 1 : 0);
    }
    private static Vec3 forward() { return Vec3.directionFromRotation(pitch, yaw); }
    private static void move(Vec3 delta) {
        if (focus == null) return;
        var world = Minecraft.getInstance().level;
        if (world == null) return;
        int steps = Math.max(1, (int)Math.ceil(delta.length() * 2));
        for (int i = 1; i <= steps; i++)
            if (!world.hasChunkAt(BlockPos.containing(focus.add(delta.scale((double)i / steps))))) return;
        focus = focus.add(delta);
        updateRig();
    }
    private static void updateRig() {
        if (focus == null) return;
        var mc = Minecraft.getInstance();
        Vec3 desired = focus.subtract(forward().scale(distance));
        // Clip the visual boom without loading terrain or moving the camera entity.
        if (mc.level != null && mc.player != null) {
            Vec3 boom = desired.subtract(focus);
            for (int i = 1, count = Math.max(1, (int)Math.ceil(distance * 2)); i <= count; i++) {
                Vec3 point = focus.add(boom.scale((double)i / count));
                if (!mc.level.hasChunkAt(BlockPos.containing(point))) {
                    desired = focus.add(boom.scale((double)(i - 1) / count));
                    break;
                }
            }
            var hit = mc.level.clip(new ClipContext(focus, desired, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 offset = hit.getLocation().subtract(focus);
                desired = focus.add(offset.normalize().scale(Math.max(0, offset.length() - .2)));
            }
        }
        position = desired;
    }
    public static void home() {
        var mc = Minecraft.getInstance();
        if (session && mc.player != null) { focus = mc.player.position().add(0,1,0); updateRig(); }
    }
    public static void preset() { if (session) { pitch = pitch < 65 ? 78 : 55; updateRig(); } }
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
    public static Vec3 position() { return position; }
    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    private static void select(boolean execute) {
        var mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera(camera)) return;
        double x = mc.mouseHandler.xpos() / mc.getWindow().getScreenWidth() * 2 - 1;
        double y = 1 - mc.mouseHandler.ypos() / mc.getWindow().getScreenHeight() * 2;
        double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
        Vector3f ray = new Vector3f((float)(x * tan * mc.getWindow().getScreenWidth() / mc.getWindow().getScreenHeight()),
            (float)(y * tan), -1).rotate(camera.rotation()).normalize();
        Vec3 direction = new Vec3(ray.x, ray.y, ray.z);
        double range = 0;
        while (range < 64 && mc.level.hasChunkAt(BlockPos.containing(position.add(direction.scale(range + 1))))) range++;
        Vec3 end = position.add(direction.scale(range));
        var block = mc.level.clip(new ClipContext(position, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, mc.player));
        double nearest = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(position);
        java.util.UUID selected = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || entity == mc.player || !entity.isAlive()) continue;
            var hit = entity.getBoundingBox().inflate(.1).clip(position, end);
            if (hit.isPresent() && hit.get().distanceToSqr(position) < nearest) {
                nearest = hit.get().distanceToSqr(position); selected = entity.getUUID();
            }
        }
        ClientTacticalPlan.worldClick(execute, selected, block);
    }
}
