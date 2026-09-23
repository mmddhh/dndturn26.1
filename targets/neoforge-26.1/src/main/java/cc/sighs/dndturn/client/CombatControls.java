package cc.sighs.dndturn.client;

import cc.sighs.dndturn.DNDTurnNeoForge261;
import cc.sighs.dndturn.combat.CombatNetwork;
import cc.sighs.dndturn.combat.OperationRecord;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.client.event.InputEvent;

/** Rebindable prototype controls. Packets carry intent, never authoritative costs or damage. */
public final class CombatControls {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath(DNDTurnNeoForge261.MOD_ID, "prototype"));
    private static final KeyMapping UI = key("ui", GLFW.GLFW_KEY_GRAVE_ACCENT);
    private static final KeyMapping START = new KeyMapping("key.dndturn.start", KeyConflictContext.IN_GAME,
        KeyModifier.SHIFT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
    private static final KeyMapping EXIT = key("exit", GLFW.GLFW_KEY_UNKNOWN);
    private static final KeyMapping MOVE = key("move", GLFW.GLFW_KEY_UNKNOWN);
    private static final KeyMapping ATTACK = key("attack", GLFW.GLFW_KEY_LEFT_CONTROL);
    private static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_UNKNOWN);
    private static final KeyMapping DODGE = key("dodge", GLFW.GLFW_KEY_UNKNOWN);
    private static final KeyMapping DISENGAGE = key("disengage", GLFW.GLFW_KEY_UNKNOWN);
    private static final KeyMapping END_TURN = key("end_turn", GLFW.GLFW_KEY_SPACE);
    private static final KeyMapping INVENTORY = key("inventory", GLFW.GLFW_KEY_I);
    private static final KeyMapping ROTATE_LEFT = key("rotate_left", GLFW.GLFW_KEY_Q);
    private static final KeyMapping ROTATE_RIGHT = key("rotate_right", GLFW.GLFW_KEY_E);
    private static final KeyMapping HOME = key("camera_home", GLFW.GLFW_KEY_HOME);
    private static final KeyMapping PRESET = key("camera_preset", GLFW.GLFW_KEY_O);
    private static final KeyMapping JUMP = key("jump", GLFW.GLFW_KEY_UNKNOWN);
    private static boolean exitRetryPending;
    private static boolean exitAfterStart;
    private static long lastExitVersion;
    private static UUID startOperationId;

    private CombatControls() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.dndturn." + name, KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(START);
        event.register(UI);
        event.register(EXIT);
        event.register(MOVE);
        event.register(ATTACK);
        event.register(DASH);
        event.register(DODGE);
        event.register(DISENGAGE);
        event.register(END_TURN);
        event.register(INVENTORY);
        event.register(JUMP);
        event.register(HOME); event.register(PRESET);
        event.register(ROTATE_LEFT);
        event.register(ROTATE_RIGHT);
    }

    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(DNDTurnNeoForge261.MOD_ID, "combat_hud"),
            CombatControls::renderHud);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft game = Minecraft.getInstance();
        if (game.player == null || game.screen != null || !game.isWindowActive() || game.getConnection() == null || ConsentOverlay.isOpen()) {
            for (KeyMapping mapping : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
                    DISENGAGE, END_TURN, INVENTORY, HOME, PRESET, JUMP, ROTATE_LEFT, ROTATE_RIGHT}) clear(mapping);
            releaseJump();
            return;
        }
        while (UI.consumeClick()) TacticalOverlay.togglePointer();
        while (START.consumeClick()) {
            if (ClientCombatState.encounter() != null) { requestExit(); continue; }
            if (startOperationId == null) startOperationId = UUID.randomUUID();
            send(CombatNetwork.IntentKind.START, null, startOperationId);
        }
        while (EXIT.consumeClick()) requestExit();
        while (MOVE.consumeClick()) ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE);
        while (ATTACK.consumeClick()) {
            ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK);
        }
        while (DASH.consumeClick()) send(CombatNetwork.IntentKind.DASH, null);
        while (DODGE.consumeClick()) send(CombatNetwork.IntentKind.DODGE, null);
        while (DISENGAGE.consumeClick()) send(CombatNetwork.IntentKind.DISENGAGE, null);
        while (END_TURN.consumeClick()) requestEndTurn();
        while (INVENTORY.consumeClick()) game.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(game.player));
        while (HOME.consumeClick()) ClientControl.home();
        while (PRESET.consumeClick()) ClientControl.preset();
        while (ROTATE_LEFT.consumeClick()) { }
        while (ROTATE_RIGHT.consumeClick()) { }
        while (JUMP.consumeClick()) { /* Held state is forwarded by onKeyInput. */ }
    }

    /** Called after vanilla KeyMapping.set/click, before the next client input tick. */
    public static void onKeyInput(InputEvent.Key event) {
        if (!ClientControl.key(event.getKeyEvent(), event.getAction())) return;
        Minecraft game = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_RELEASE && JUMP.matches(event.getKeyEvent())) releaseJump();
        if (game.player == null || game.screen != null || !game.isWindowActive() || ClientControl.modal()) return;
        boolean tactical = ClientCombatState.encounter() != null;
        if (tactical && event.getAction() == GLFW.GLFW_PRESS) {
            for (int i = 0; i < game.options.keyHotbarSlots.length; i++)
                if (game.options.keyHotbarSlots[i].matches(event.getKeyEvent())
                    && game.options.keyHotbarSlots[i].isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()))
                    && !matchesAction(event.getKeyEvent())) {
                    ClientTacticalPlan.selectSlot(i); clear(game.options.keyHotbarSlots[i]); return;
                }
        }
        boolean toggle = START.matches(event.getKeyEvent()) && START.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()));
        if (!tactical && !toggle) return;
        boolean handled = toggle;
        if (tactical) for (KeyMapping mapping : new KeyMapping[]{UI, EXIT, MOVE, ATTACK, DASH, DODGE,
                DISENGAGE, END_TURN, INVENTORY, HOME, PRESET, JUMP, ROTATE_LEFT, ROTATE_RIGHT}) {
            if (mapping.matches(event.getKeyEvent())) handled = true;
        }
        if (!handled) return;
        KeyMapping winner = null;
        for (KeyMapping mapping : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
                DISENGAGE, END_TURN, INVENTORY, HOME, PRESET, JUMP, ROTATE_LEFT, ROTATE_RIGHT}) {
            if (!mapping.matches(event.getKeyEvent()) || !mapping.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()))) continue;
            if (winner == null) winner = mapping;
            else clear(mapping);
        }
        // Consume only overlapping vanilla bindings; preserve the user's saved key configuration.
        for (KeyMapping mapping : game.options.keyMappings)
            if (!isAction(mapping) && mapping.matches(event.getKeyEvent())) clear(mapping);
        if (toggle) clear(END_TURN);
        if (tactical && winner == JUMP && !ClientControl.blockMovement()) {
            game.options.keyJump.setDown(event.getAction() != GLFW.GLFW_RELEASE);
        }
        if (event.getAction() == GLFW.GLFW_REPEAT)
            for (KeyMapping mapping : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
                    DISENGAGE, END_TURN, INVENTORY}) while (mapping.consumeClick()) { }
    }

    private static void clear(KeyMapping mapping) {
        mapping.setDown(false);
        while (mapping.consumeClick()) { }
    }

    static boolean matchesAction(net.minecraft.client.input.KeyEvent event) {
        for (KeyMapping key : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
                DISENGAGE, END_TURN, INVENTORY, HOME, PRESET, JUMP, ROTATE_LEFT, ROTATE_RIGHT})
            if (key.matches(event) && key.isActiveAndMatches(InputConstants.getKey(event))) return true;
        return false;
    }
    private static boolean isAction(KeyMapping candidate) {
        for (KeyMapping key : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
                DISENGAGE, END_TURN, INVENTORY, HOME, PRESET, JUMP, ROTATE_LEFT, ROTATE_RIGHT}) if (key == candidate) return true;
        return false;
    }

    static KeyMapping rotateLeftKey() { return ROTATE_LEFT; }
    static KeyMapping rotateRightKey() { return ROTATE_RIGHT; }
    static KeyMapping cameraUpKey() { return JUMP; }

    static boolean hasMouseBinding(int button) {
        InputConstants.Key code = InputConstants.Type.MOUSE.getOrCreate(button);
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings)
            if (isAction(mapping) && mapping.isActiveAndMatches(code)) return true;
        if (ClientControl.active()) for (KeyMapping mapping : Minecraft.getInstance().options.keyHotbarSlots)
            if (mapping.isActiveAndMatches(code)) return true;
        return false;
    }

    static boolean mouseBinding(int button, int action) {
        InputConstants.Key code = InputConstants.Type.MOUSE.getOrCreate(button);
        if (action == GLFW.GLFW_RELEASE) {
            boolean matched = false;
            for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings)
                if (isAction(mapping) && mapping.getKey().equals(code)) { mapping.setDown(false); matched = true; }
            return matched;
        }
        if (!hasMouseBinding(button)) return false;
        boolean actionMatch = false;
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings)
            if (isAction(mapping) && mapping.isActiveAndMatches(code)) actionMatch = true;
        if (!actionMatch && ClientControl.active()) {
            var hotbar = Minecraft.getInstance().options.keyHotbarSlots;
            for (int i=0;i<hotbar.length;i++) if (hotbar[i].isActiveAndMatches(code)) {
                if (action == GLFW.GLFW_PRESS) ClientTacticalPlan.selectSlot(i);
                clear(hotbar[i]); return true;
            }
        }
        KeyMapping.set(code, action != GLFW.GLFW_RELEASE);
        if (action == GLFW.GLFW_PRESS) KeyMapping.click(code);
        boolean winner = false;
        for (KeyMapping mapping : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
                DISENGAGE, END_TURN, INVENTORY, HOME, PRESET, JUMP, ROTATE_LEFT, ROTATE_RIGHT}) if (mapping.isActiveAndMatches(code)) {
            if (!winner) winner = true;
            else clear(mapping);
        }
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings)
            if (!isAction(mapping) && mapping.getKey().equals(code)) clear(mapping);
        return true;
    }

    private static void releaseJump() {
        Minecraft.getInstance().options.keyJump.setDown(false);
    }

    public static String keyLabel(String id) {
        KeyMapping mapping = switch (id) {
            case "move" -> MOVE; case "attack" -> ATTACK; case "dash" -> DASH;
            case "dodge" -> DODGE; case "disengage" -> DISENGAGE; case "end" -> END_TURN;
            case "exit" -> EXIT.isUnbound() ? START : EXIT; case "inventory-toggle" -> INVENTORY;
            default -> UI;
        };
        return mapping.getTranslatedKeyMessage().getString();
    }

    public static String uiKeyLabel() { return UI.getTranslatedKeyMessage().getString(); }

    private static void requestEndTurn() {
        if (ClientTacticalPlan.running()) { ClientTacticalPlan.endTurn(); return; }
        ClientTacticalPlan.cancel();
        send(CombatNetwork.IntentKind.END_TURN, null);
    }

    /** AUI and key bindings share the same pending movement/stop state machine. */
    public static void requestFromUi(CombatNetwork.IntentKind kind, UUID target) {
        Minecraft game = Minecraft.getInstance();
        if (game.player == null || game.getConnection() == null || game.screen != null) return;
        switch (kind) {
            case MOVE_BEGIN -> ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE);
            case END_TURN -> requestEndTurn();
            case EXIT -> requestExit();
            case ATTACK -> ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK);
            case DASH, DODGE, DISENGAGE -> send(kind, null);
            default -> throw new IllegalArgumentException("unsupported UI action");
        }
    }

    public static void reset() {
        releaseJump();
        exitRetryPending = false;
        exitAfterStart = false;
        startOperationId = null;
    }

    public static void resetForNewEncounter() {
        boolean exitRequested = exitAfterStart;
        reset();
        exitAfterStart = exitRequested;
    }

    public static void onEncounterState(CombatNetwork.EncounterState state) {
        if (!state.active()) { reset(); return; }
        if (exitAfterStart) {
            exitAfterStart = false;
            requestExit();
        }
        if (exitRetryPending && state.version() != lastExitVersion) {
            exitRetryPending = false;
            requestExit();
        }
    }

    public static void onIntentStatus(CombatNetwork.IntentStatus status) {
        if (status.kind() == CombatNetwork.IntentKind.START) {
            if (status.operationId().equals(startOperationId)) {
                startOperationId = null;
                if (!status.accepted()) exitAfterStart = false;
            }
            return;
        }
        if (status.kind() == CombatNetwork.IntentKind.EXIT && !status.accepted()
            && status.reason().contains("stale encounter version")) {
            exitRetryPending = true;
            var state = ClientCombatState.encounter();
            if (state != null && state.version() != lastExitVersion) {
                exitRetryPending = false;
                requestExit();
            }
        }
    }

    private static void requestExit() {
        var state = ClientCombatState.encounter();
        if (state == null) {
            if (startOperationId != null) exitAfterStart = true;
            return;
        }
        lastExitVersion = state.version();
        send(CombatNetwork.IntentKind.EXIT, null);
    }

    private static void send(CombatNetwork.IntentKind kind, UUID target) {
        send(kind, target, UUID.randomUUID());
    }

    private static void send(CombatNetwork.IntentKind kind, UUID target, UUID operationId) {
        var state = ClientCombatState.encounter();
        if (kind == CombatNetwork.IntentKind.START) {
            ClientPacketDistributor.sendToServer(CombatNetwork.CombatIntent.start(operationId));
            return;
        }
        send(kind, target, operationId, state == null ? -1 : state.version());
    }

    private static void send(CombatNetwork.IntentKind kind, UUID target, UUID operationId,
                             long expectedVersion) {
        var state = ClientCombatState.encounter();
        if (kind != CombatNetwork.IntentKind.START && state == null) return;
        ClientPacketDistributor.sendToServer(new CombatNetwork.CombatIntent(operationId,
            state == null ? null : state.generation(), state == null ? null : state.encounterId(),
            expectedVersion, kind, target));
    }

    private static void renderHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        var state = ClientCombatState.encounter();
        Minecraft game = Minecraft.getInstance();
        if (state == null || game.player == null || TacticalOverlay.isOpen()) return;
        String phase = switch (state.phase()) {
            case CANDIDATE -> "Candidate";
            case ACTIVE -> "Active";
            case ENVIRONMENT -> "Environment";
            case ENDED -> "Ended";
        };
        boolean myTurn = game.player.getUUID().equals(state.current());
        graphics.text(game.font, "DNDTurn " + phase + "  round " + state.round()
            + (myTurn ? "  YOUR TURN" : ""), 8, 8, 0xFFFFFF);
        graphics.text(game.font, "Action " + (state.action() ? "1" : "0")
            + "  Move " + state.movementTicks() + "  Reaction " + (state.reaction() ? "1" : "0")
            + "  Env " + state.environmentRemaining(), 8, 20, 0xFFFFFF);
        graphics.text(game.font, START.getTranslatedKeyMessage().getString() + " start  "
            + EXIT.getTranslatedKeyMessage().getString() + " exit  "
            + MOVE.getTranslatedKeyMessage().getString() + " " + ClientTacticalPlan.phase() + "  "
            + ATTACK.getTranslatedKeyMessage().getString() + " attack  "
            + DASH.getTranslatedKeyMessage().getString() + " dash  "
            + END_TURN.getTranslatedKeyMessage().getString() + " end", 8, 32, 0xDDDDDD);
        graphics.text(game.font, DODGE.getTranslatedKeyMessage().getString() + "  "
            + DISENGAGE.getTranslatedKeyMessage().getString(), 8, 44, 0xDDDDDD);
        var result = ClientCombatState.latestResult();
        if (result != null) graphics.text(game.font,
            "#" + result.index() + " " + result.reason(), 8, 56, 0xFFFFAA);
        var status = ClientCombatState.latestStatus();
        if (status != null) {
            String detail = status.replay() ? " [" + OperationRecord.Outcome.values()[status.outcome()]
                + " move=" + status.actualMovementTicks() + " damage=" + status.actualDamage() + "]" : "";
            graphics.text(game.font, status.kind() + ": " + status.reason() + detail,
                8, 68, status.accepted() ? 0xDDDDDD : 0xFF7777);
        }
    }
}
