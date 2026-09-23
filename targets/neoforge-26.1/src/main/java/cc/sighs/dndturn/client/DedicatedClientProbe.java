package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.CombatNetwork;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Opt-in external client test driver. Only normal commands, controls and vanilla movement input. */
final class DedicatedClientProbe {
    private static final Gson JSON = new Gson();
    private static long lastCommand = -1;
    private static int ticks;
    private static boolean forward;
    private static boolean backward;
    private DedicatedClientProbe() {}

    static void tick() {
        String folder = System.getProperty("dndturn.clientProbe");
        if (folder == null) return;
        Minecraft game = Minecraft.getInstance();
        game.options.pauseOnLostFocus = false;
        Path root = Path.of(folder);
        try {
            Files.createDirectories(root);
            Path command = root.resolve("command.json");
            if (Files.exists(command) && game.player != null && game.getConnection() != null) {
                var input = JsonParser.parseString(Files.readString(command)).getAsJsonObject();
                long sequence = input.get("sequence").getAsLong();
                if (sequence > lastCommand) {
                    lastCommand = sequence;
                    switch (input.get("kind").getAsString()) {
                        case "command" -> game.player.connection.sendCommand(input.get("text").getAsString());
                        case "intent" -> CombatControls.requestFromUi(
                            CombatNetwork.IntentKind.valueOf(input.get("intent").getAsString()),
                            input.has("target") ? UUID.fromString(input.get("target").getAsString()) : null);
                        case "walk" -> {
                            forward = input.get("forward").getAsBoolean();
                            backward = input.has("backward") && input.get("backward").getAsBoolean();
                        }
                        case "look" -> { game.player.setYRot(input.get("yaw").getAsFloat()); game.player.setXRot(0); }
                        case "focus" -> GLFW.glfwFocusWindow(game.getWindow().handle());
                        case "inventory" -> game.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(game.player));
                        case "chat" -> game.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
                        case "pause" -> game.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
                        case "closeMenu" -> game.setScreen(null);
                        case "quit" -> game.stop();
                        default -> throw new IllegalArgumentException("unknown client probe command");
                    }
                }
            }
            game.options.keyUp.setDown(forward);
            game.options.keyDown.setDown(backward);
            if (++ticks % 2 != 0) return;
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("tick", ticks); state.put("command", lastCommand);
            state.put("connected", game.getConnection() != null);
            state.put("screen", game.screen == null ? null : game.screen.getClass().getSimpleName());
            state.put("focused", game.isWindowActive()); state.put("grabbed", game.mouseHandler.isMouseGrabbed());
            state.put("encounter", ClientCombatState.encounter());
            state.put("status", ClientCombatState.latestStatus());
            state.put("results", ClientCombatState.results());
            if (game.player != null) {
                state.put("player", game.player.getUUID()); state.put("name", game.player.getName().getString());
                state.put("x", game.player.getX()); state.put("y", game.player.getY()); state.put("z", game.player.getZ());
                state.put("health", game.player.getHealth());
            }
            int[] x = new int[1], y = new int[1];
            GLFW.glfwGetWindowPos(game.getWindow().handle(), x, y);
            double scale = Math.min(game.getWindow().getScreenWidth() / 960.0, game.getWindow().getScreenHeight() / 540.0);
            for (Document document : ApricityUI.getDocument("dndturn/consent.html")) if (document.isActive()) {
                for (String id : java.util.List.of("agree", "decline")) {
                    var rect = document.getElementById(id).getBoundingClientRect();
                    state.put(id, new int[] {x[0] + (int)((rect.x + rect.width / 2) * scale),
                        y[0] + (int)((rect.y + rect.height / 2) * scale)});
                }
                state.put("consent", document.getElementById("reason").getTextContent());
            }
            Files.writeString(root.resolve("state.json"), JSON.toJson(state));
        } catch (Exception failure) {
            ApricityUI.LOGGER.error("Dedicated client probe failed", failure);
            try { Files.writeString(root.resolve("error.txt"), failure.toString()); }
            catch (Exception ignored) { }
        }
    }
}
