package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.CombatNetwork;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Explicit dev JVM probe in a fresh flat world; never creates a combat session. */
final class TacticalUiSmokeTest {
    private static final UUID VIEWER = new UUID(0, 1), TARGET = new UUID(0, 2), SESSION = new UUID(0, 3);
    private static final CombatNetwork.EncounterState STATE = new CombatNetwork.EncounterState(
        SESSION, SESSION, 1, true, 7, 1, 2, VIEWER, 1, 24, true, true, 20, false, null,
        List.of(new CombatNetwork.MemberNotice(VIEWER, "冒险者", 18, 0, false, false, 24, true, true),
            new CombatNetwork.MemberNotice(TARGET, "僵尸", 13, 0, true, false, 28, true, true)), 1, true);
    private static final List<CombatNetwork.ResultNotice> RESULTS = List.of(new CombatNetwork.ResultNotice(
        SESSION, SESSION, 0, new UUID(0, 4), 1, "箭矢命中 · 吸收 2 · 生命损失 4", 0, 4,
        new CombatNetwork.HitNotice(16, 8, 16, 21, 12, 6, 2, 4, "ADVANTAGE", "VANILLA_ACCEPTED")));
    private static int ticks;
    private static int opened;
    private static boolean done;
    private static boolean worldRequested;
    private static Element firstRow;
    private static Process mouse;
    private static java.io.PrintWriter mouseInput;
    private static java.io.BufferedReader mouseOutput;
    private static int nativeClicks;
    private static int lastModalStep = -1;
    private static int worldPackets, hotbarSlot;
    private static long worldTime;

    private TacticalUiSmokeTest() {}

    static void tick() {
        if (done) return;
        ticks++;
        try {
            Minecraft game = Minecraft.getInstance();
            if (opened != 0 && ticks - opened > 60) { modalProbe(ticks - opened); return; }
            if (!worldRequested && game.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                worldRequested = true;
                game.createWorldOpenFlows().createFreshLevel("dndturn-ui-smoke-" + UUID.randomUUID(),
                    new net.minecraft.world.level.LevelSettings("DNDTurn UI smoke",
                        net.minecraft.world.level.GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(1L, false, false),
                    provider -> provider.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)
                        .value().createWorldDimensions(), game.screen);
                return;
            }
            if (game.player == null || game.level == null || game.screen != null) {
                if (ticks > 1200) throw new IllegalStateException("test world did not become ready");
                return;
            }
            if (HTML.getTemple("dndturn/tactical.html") == null) {
                if (ticks > 600) throw new IllegalStateException("AUI template unavailable");
                return;
            }
            if (opened == 0) opened = ticks;
            int age = ticks - opened;
            if (age > 60) { modalProbe(age); return; }
            TacticalOverlay.renderProjection(STATE, VIEWER, RESULTS, null);
            Document doc = ApricityUI.getDocument("dndturn/tactical.html").getFirst();
            doc.setReloadPersistent(true);
            if (age == 5) {
                samplePortraits(doc);
                firstRow = doc.querySelector(".member");
                require(firstRow != null, "initiative member not constructed");
                firstRow.click();
                doc.getElementById("inventory-toggle").click();
                Element node = doc.getElementById("inventory-0");
                require(node instanceof com.sighs.apricityui.element.Item, "AUI item renderer not registered");
                ((com.sighs.apricityui.element.Item) node).setIngredientStack(new ItemStack(Items.DIAMOND_SWORD));
            }
            if (age == 20) {
                assertSeparate(doc.querySelector(".journal"), doc.querySelector(".actions"));
                assertInside(doc.getElementById("phase"), doc.querySelector(".heading"));
                require(firstRow == doc.querySelector(".member"), "unchanged roster recreated its nodes");
                require(doc.getElementById("target").getTextContent().contains("冒险者"), "target selection not dispatched");
                require(doc.getElementById("attack").getAttribute("disabled") == null, "selected attack stayed disabled");
                doc.refresh();
            }
            if (age == 25) {
                samplePortraits(doc);
                require(firstRow != doc.querySelector(".member"), "refresh retained stale node");
                require(doc.querySelectorAll(".member").size() == 2, "refresh duplicated roster");
                require(doc.querySelectorAll(".log-entry").size() == 1, "refresh duplicated ordered result");
                ((com.sighs.apricityui.element.Item) doc.getElementById("inventory-0"))
                    .setIngredientStack(new ItemStack(Items.DIAMOND_SWORD));
            }
            if (age == 30) org.lwjgl.glfw.GLFW.glfwSetWindowSize(game.getWindow().handle(), 1280, 720);
            if (age == 40) {
                assertSeparate(doc.getElementById("inventory-panel"), doc.querySelector(".actions"));
                assertSeparate(doc.getElementById("inventory-panel"), doc.querySelector(".journal"));
                assertInside(doc.getElementById("inventory-35"), doc.getElementById("inventory-panel"));
                Path folder = Path.of(System.getProperty("dndturn.uiSmoke.output"));
                Files.createDirectories(folder);
                Screenshot.takeScreenshot(game.getMainRenderTarget(), image -> {
                    try { image.writeToFile(folder.resolve("inventory.png")); }
                    catch (Exception failure) { fail(failure); }
                    finally { image.close(); }
                });
            }
            if (age == 45) doc.getElementById("inventory-toggle").click();
            if (age == 60) {
                for (String id : List.of("phase", "initiative", "attack", "end", "log", "hit")) {
                    var rect = doc.getElementById(id).getBoundingClientRect();
                    require(rect.width > 0 && rect.height > 0, "invisible layout node " + id);
                }
                require(doc.getElementById("hit").getTextContent().contains("AC 12"), "hit evidence missing");
                assertSeparate(doc.querySelector(".journal"), doc.querySelector(".actions"));
                assertSeparate(doc.querySelector(".heading"), doc.getElementById("initiative"));
                for (String id : List.of("move", "attack", "dash", "dodge", "disengage", "inventory-toggle", "end", "exit", "status"))
                    assertInside(doc.getElementById(id), doc.querySelector(".actions"));
                for (Element label : doc.querySelectorAll(".member span")) {
                    var rect = label.getBoundingClientRect();
                    require(rect.width > 40 && rect.height > 15 && !label.getTextContent().isBlank(),
                        "member label is not laid out: " + rect.width + "x" + rect.height);
                }
                Path folder = Path.of(System.getProperty("dndturn.uiSmoke.output"));
                Files.createDirectories(folder);
                Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget(), image -> {
                    try {
                        image.writeToFile(folder.resolve("tactical.png"));
                        TacticalOverlay.close();
                        require(!doc.isActive(), "overlay remained active after close");

                    } catch (Exception failure) { fail(failure); }
                    finally { image.close(); }
                });
            }
        } catch (Exception failure) { fail(failure); }
    }

    private static void modalProbe(int age) throws Exception {
        ConsentOverlay.tick();
        if (age >= 65) age = 65 + (age - 65) / 3;
        if (age == lastModalStep) return;
        lastModalStep = age;
        Minecraft game = Minecraft.getInstance();
        if (age == 65) {
            worldPackets = ClientControlRegression.worldPacketCount();
            hotbarSlot = game.player.getInventory().getSelectedSlot();
            worldTime = game.level.getGameTime();
            mouse = new ProcessBuilder("python", "-u", "-c", """
                import ctypes, sys
                api = ctypes.windll.user32
                for line in sys.stdin:
                    args = line.split()
                    if args[0] == 'move': api.SetCursorPos(int(args[1]), int(args[2]))
                    elif args[0] == 'down': api.mouse_event(2, 0, 0, 0, 0)
                    elif args[0] == 'up': api.mouse_event(4, 0, 0, 0, 0)
                    print('ok', flush=True)
                """).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            mouseInput = new java.io.PrintWriter(mouse.getOutputStream(), true);
            mouseOutput = new java.io.BufferedReader(new java.io.InputStreamReader(mouse.getInputStream()));
            game.options.pauseOnLostFocus = false;
            org.lwjgl.glfw.GLFW.glfwFocusWindow(game.getWindow().handle());
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre event) ->
                    ApricityUI.LOGGER.info("Native input event: button={} action={} grabbed={}",
                        event.getButton(), event.getAction(), game.mouseHandler.isMouseGrabbed()));
            showConsent(1);
            Document doc = ApricityUI.getDocument("dndturn/consent.html").stream().filter(Document::isActive).findFirst().orElseThrow();
            for (String id : List.of("agree", "decline"))
                doc.getElementById(id).addEventListener("click", event -> nativeClicks++);
        }
        ConsentOverlay.tick();
        if (age < 65) return;
        Document doc = ApricityUI.getDocument("dndturn/consent.html").stream().filter(Document::isActive).findFirst().orElseThrow();
        if (age == 70) {
            var rect = doc.getElementById("consent-dialog").getBoundingClientRect();
            require(rect.width >= 400 && rect.height > 200, "modal did not lay out");
            require(doc.getElementById("deadline").getTextContent().contains("15"), "modal lost server deadline");
            require(doc.querySelectorAll("#consent-roster div").size() == 2, "modal roster missing");
        }
        if (age == 75) game.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(game.player));
        if (age == 95) game.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
        if (age == 115) game.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
        if (age == 80 || age == 100 || age == 120) {
            game.setScreen(null); // Vanilla grabs the mouse here, after the Screen closes.
            require(game.mouseHandler.isMouseGrabbed(), "menu closure did not exercise vanilla mouse re-grab");
            showConsent(age);
        }
        if (age == 135) org.lwjgl.glfw.GLFW.glfwIconifyWindow(game.getWindow().handle());
        if (age == 140) {
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(game.getWindow().handle());
            org.lwjgl.glfw.GLFW.glfwFocusWindow(game.getWindow().handle());
            game.setScreen(null);
            showConsent(age);
        }
        if (age == 155) {
            TacticalOverlay.renderProjection(STATE, VIEWER, RESULTS, null);
            // A display-only fixture must not acquire a tactical controller without a server session.
            require(!ClientControl.active(), "display fixture acquired a tactical session");
            showConsent(age);
            doc = ApricityUI.getDocument("dndturn/consent.html").stream().filter(Document::isActive).findFirst().orElseThrow();
            TacticalOverlay.close();
            require(!game.mouseHandler.isMouseGrabbed(), "closing tactical overlay stole the modal pointer");
        }
        if (List.of(85, 105, 125, 145, 165).contains(age)) {
            require(game.screen == null && game.isWindowActive() && !game.mouseHandler.isMouseGrabbed(),
                "modal did not regain mouse after menu/focus transition " + age);
            moveNativeMouse(doc, age == 105 || age == 145 ? "decline" : "agree");
        }
        if (List.of(86, 106, 126, 146, 166).contains(age)) {
            require(game.isWindowActive(), "test window lost focus before native press");
            ApricityUI.LOGGER.info("Native press: scaled={},{} AUI={} grabbed={}",
                game.mouseHandler.getScaledXPos(game.getWindow()), game.mouseHandler.getScaledYPos(game.getWindow()),
                com.sighs.apricityui.render.Operation.getMousePositionDirectly(), game.mouseHandler.isMouseGrabbed());
            moveNativeMouse(doc, age == 106 || age == 146 ? "decline" : "agree");
            nativeMouse("down");
        }
        if (List.of(87, 107, 127, 147, 167).contains(age)) {
            moveNativeMouse(doc, age == 107 || age == 147 ? "decline" : "agree");
            nativeMouse("up");
        }
        if (List.of(90, 110, 130, 150, 170).contains(age))
            require(nativeClicks == (age - 90) / 20 + 1, "native modal click not delivered after transition " + age
                + ": count=" + nativeClicks);
        if (age != 170) return;
        require(ClientControlRegression.worldPacketCount() == worldPackets, "modal leaked world input packets");
        require(game.player.getInventory().getSelectedSlot() == hotbarSlot, "modal changed hotbar");
        require(game.level.getGameTime() > worldTime, "consent paused world simulation");
        done = true;
        Path folder = Path.of(System.getProperty("dndturn.uiSmoke.output"));
        Document finalDoc = doc;
        Screenshot.takeScreenshot(game.getMainRenderTarget(), image -> {
            try {
                image.writeToFile(folder.resolve("consent.png"));
                ConsentOverlay.accept(new CombatNetwork.ConsentState(SESSION, 200, SESSION, 200,
                    400, 400, false, List.of(), "expired"));
                require(!finalDoc.isActive(), "closed consent retained its modal");
                require(game.mouseHandler.isMouseGrabbed(), "final modal close did not restore mouse");
                Files.writeString(folder.resolve("result.txt"),
                    "PASS: layout, target click, node reuse, refresh, log, item renderer, portraits, consent modal, "
                    + "server deadline, native agree/decline after inventory/chat/pause/focus, shared mouse ownership, disposal\n");
            } catch (Exception failure) { fail(failure); }
            finally { image.close(); stopNativeMouse(); game.stop(); }
        });
    }

    private static void showConsent(long revision) {
        var player = Minecraft.getInstance().player;
        ConsentOverlay.accept(new CombatNetwork.ConsentState(SESSION, revision, SESSION, revision,
            400, 100, true, List.of(new CombatNetwork.ConsentMember(player.getUUID(), "你", false),
                new CombatNetwork.ConsentMember(TARGET, "队友", true)), "等待全员同意，世界继续运行"));
    }

    /** OS mouse input traverses GLFW -> MouseHandler -> NeoForge -> AUI hit testing; no DOM click shortcut. */
    private static void moveNativeMouse(Document doc, String id) throws Exception {
        Minecraft game = Minecraft.getInstance();
        int[] x = new int[1], y = new int[1];
        org.lwjgl.glfw.GLFW.glfwGetWindowPos(game.getWindow().handle(), x, y);
        var rect = doc.getElementById(id).getBoundingClientRect();
        var point = doc.documentToScreenPosition(new com.sighs.apricityui.layout.Position(
            rect.x + rect.width / 2, rect.y + rect.height / 2));
        double scale = (double)game.getWindow().getScreenWidth() / game.getWindow().getGuiScaledWidth();
        ApricityUI.LOGGER.info("Native target {} rect={},{},{}x{} window={},{} size={}x{} scale={}", id,
            rect.x, rect.y, rect.width, rect.height, x[0], y[0], game.getWindow().getScreenWidth(), game.getWindow().getScreenHeight(), scale);
        nativeMouse("move " + (x[0] + (int)(point.x * scale))
            + " " + (y[0] + (int)(point.y * game.getWindow().getScreenHeight() / game.getWindow().getGuiScaledHeight())));
    }

    private static void nativeMouse(String command) throws Exception {
        mouseInput.println(command);
        require("ok".equals(mouseOutput.readLine()), "native mouse helper failed");
    }

    private static void stopNativeMouse() {
        if (mouseInput != null) { mouseInput.println("up"); mouseInput.close(); }
        // EOF lets the helper release any pressed button before it exits.
        mouse = null; mouseInput = null; mouseOutput = null;
    }

    private static void samplePortraits(Document doc) {
        var faces = doc.querySelectorAll(".portrait texture");
        faces.getFirst().setAttribute("src", Minecraft.getInstance().player.getSkin().body().texturePath().toString());
        faces.getFirst().setAttribute("style", "display: block");
        require(faces.getLast().getAttribute("src").isEmpty(), "mob portrait must remain empty");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void assertSeparate(Element first, Element second) {
        var a = first.getBoundingClientRect();
        var b = second.getBoundingClientRect();
        require(a.x + a.width <= b.x || b.x + b.width <= a.x
            || a.y + a.height <= b.y || b.y + b.height <= a.y, "panels overlap: " + first + " / " + second);
    }

    private static void assertInside(Element child, Element parent) {
        var a = child.getBoundingClientRect();
        var b = parent.getBoundingClientRect();
        require(a.x >= b.x && a.y >= b.y && a.x + a.width <= b.x + b.width + 1
            && a.y + a.height <= b.y + b.height + 1, "layout escapes panel: " + child);
    }

    private static void fail(Exception failure) {
        done = true;
        stopNativeMouse();
        ApricityUI.LOGGER.error("DNDTurn UI smoke failed", failure);
        try {
            Path folder = Path.of(System.getProperty("dndturn.uiSmoke.output"));
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("result.txt"), "FAIL: " + failure + "\n");
        } catch (Exception ignored) { /* The startup log still contains the failure. */ }
        Minecraft.getInstance().stop();
    }
}
