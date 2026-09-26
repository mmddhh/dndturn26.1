package cc.sighs.dndturn.client;

import cc.sighs.dndturn.mixin.client.ClientInputProbeAccessor;
import com.sighs.apricityui.layout.Position;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Explicit test driver on a real dedicated connection, using the normal tactical command.
 * Raw callback tests complement (not replace) the OS mouse tests in TacticalUiSmokeTest. */
public final class ClientControlRegression {
    private static final Map<String, Integer> packets = new ConcurrentHashMap<>();
    private static int ticks, age = -1, clicks;
    private static boolean requested, done;
    private static Vec3 actor, camera;
    private static float yaw, pitch;
    private static int budget, slot, resultCount;
    private static com.mojang.blaze3d.platform.InputConstants.Key savedForward;
    private static long startedAt = System.currentTimeMillis();
    private static Vec3 peerCamera, peerActor;
    private static int settled;
    private static int disabledClicks, wheelEvents;
    private static boolean fixtureReady;
    private static int activeStage, stageTicks;
    private static Object oldPlayer;
    private static net.minecraft.core.BlockPos placed;
    private static volatile Runnable replayOptions;
    private static Runnable replayRunning;
    private static volatile boolean holdOptions;
    private static int queryRequestCount;
    private static boolean cancelledSelection;
    private static volatile cc.sighs.dndturn.combat.TacticalNetwork.Request lastTacticalRequest;
    private static boolean attackReplayed;
    public static void receivedSwing(net.minecraft.world.entity.Entity entity) {
        if (Boolean.getBoolean("dndturn.controlProbe") && entity != null && entity == Minecraft.getInstance().player)
            packets.merge("RECEIVED_SELF_SWING", 1, Integer::sum);
    }
    static boolean captureOptions(Runnable replay) { replayOptions = replay; return holdOptions; }
    static void captureRunningProjection(Runnable replay) { replayRunning = replay; }
    private ClientControlRegression() {}
    public static void packet(Packet<?> packet) {
        if (!Boolean.getBoolean("dndturn.controlProbe") && !Boolean.getBoolean("dndturn.uiSmoke")) return;
        String name = packet.getClass().getSimpleName();
        if (packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket custom
            && custom.payload() instanceof cc.sighs.dndturn.combat.TacticalNetwork.Request request) {
            packets.merge("TACTICAL_REQUEST", 1, Integer::sum);
            if (Boolean.getBoolean("dndturn.controlProbe")) lastTacticalRequest = request;
        }
        if (name.startsWith("Serverbound") || packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket)
            packets.merge(name, 1, Integer::sum);
        if (actor != null && packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket move
            && (move.getX(actor.x) != actor.x || move.getY(actor.y) != actor.y || move.getZ(actor.z) != actor.z
                || move.getYRot(yaw) != yaw || move.getXRot(pitch) != pitch)) packets.merge("CHANGED_ACTOR_POSE", 1, Integer::sum);
    }
    static int worldPacketCount() {
        return java.util.List.of("ServerboundInteractPacket", "ServerboundUseItemPacket", "ServerboundUseItemOnPacket",
            "ServerboundSwingPacket", "ServerboundSetCarriedItemPacket", "ServerboundPlayerActionPacket")
            .stream().mapToInt(name -> packets.getOrDefault(name, 0)).sum();
    }
    public static void tick() {
        if (!Boolean.getBoolean("dndturn.controlProbe") || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (Boolean.getBoolean("dndturn.controlProbe.peer")) { peerTick(); return; }
            if (++ticks > 1800) throw new IllegalStateException("timeout screen=" + mc.screen + " status=" + ClientCombatState.latestStatus());
            if (!requested && mc.player != null && !mc.player.isAlive()) { mc.player.respawn(); return; }
            if (mc.player == null || mc.level == null || mc.screen != null) return;
            mc.options.pauseOnLostFocus = false;
            GLFW.glfwFocusWindow(mc.getWindow().handle());
            if (!fixtureReady && ticks > 100 && (Boolean.getBoolean("dndturn.controlProbe.activeCombat")
                || Boolean.getBoolean("dndturn.controlProbe.requirePeer"))) {
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer") && mc.level.players().stream()
                    .noneMatch(p -> p.getName().getString().equals("DNDCameraPeer"))) return;
                fixtureReady = true;
                for (String command : new String[]{"kill @e[type=minecraft:zombie,tag=dndturn_control_probe]",
                        "gamerule minecraft:spawn_mobs false", "kill @e[type=minecraft:spider,x=0,y=120,z=0,distance=..30]",
                        "attribute @s minecraft:max_health base set 20", "effect give @s minecraft:instant_health 1 10",
                        "effect give @a minecraft:resistance 120 4 true", "effect give @a minecraft:instant_health 1 5 true", "time set midnight", "fill -12 119 -12 12 119 12 minecraft:stone",
                        "fill -12 120 -12 12 125 12 minecraft:air", "item replace entity @s hotbar.0 with minecraft:dirt 8", "item replace entity @s hotbar.1 with minecraft:apple 2", "item replace entity @s hotbar.2 with minecraft:diamond_sword", "setblock 1 120 -2 minecraft:lever[face=floor]", "tp @s 0.5 120 0.5 0 0"}) mc.player.connection.sendCommand(command);
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) mc.player.connection.sendCommand("tp DNDCameraPeer -1.5 120 2.5");
                if (Boolean.getBoolean("dndturn.controlProbe.activeCombat"))
                    mc.player.connection.sendCommand("summon minecraft:zombie 5.5 120 0.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"dndturn_control_probe\"]}");
                if (Boolean.getBoolean("dndturn.controlProbe.visual"))
                    mc.player.connection.sendCommand("effect give @e[type=minecraft:zombie,tag=dndturn_control_probe] minecraft:glowing 120 0 false");
            }
            if (!requested && ticks > 160) {
                if (Boolean.getBoolean("dndturn.controlProbe.activeCombat")) {
                    boolean targetReady = false;
                    for (var entity : mc.level.entitiesForRendering()) if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie && entity.position().distanceToSqr(mc.player.position()) < 100) targetReady = true;
                    if (!targetReady) { if (ticks % 40 == 0) mc.player.connection.sendCommand("summon minecraft:zombie 5.5 120 0.5 {PersistenceRequired:1b,NoAI:1b,Tags:[\"dndturn_control_probe\"]}"); return; }
                }
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) {
                    var peer = mc.level.players().stream().filter(p -> p.getName().getString().equals("DNDCameraPeer")).findFirst();
                    if (peer.isEmpty()) return;
                    Vec3 offset = peer.get().position().subtract(mc.player.position());
                    if (offset.horizontalDistanceSqr() > 36 || Math.abs(offset.y) > 5) {
                        settled = 0;
                        return;
                    }
                    mc.options.keyUp.setDown(false); mc.options.keyJump.setDown(false);
                    if (++settled < 20) return;
                }
                requested = true; mc.player.connection.sendCommand("dndturn tactical start");
            }
            if (ClientCombatState.encounter() == null) return;
            ++age;
            if (Boolean.getBoolean("dndturn.controlProbe.visual") && age > 56) {
                if (ClientPresentationRegression.tick()) finish("PASS: physical .84 client protocol 17; finite main/off-hand clips and dedup; four-tick ownership release; independent encounter projections; hurt residue/new hurt; vanilla swing; Player/Zombie/Bat/item and Frog/Warden counterexamples; item-use matching/stopping and bow/crossbow properties; movement evidence dedup/attribution; global freeze; entity unload/ID reuse/generation rejection.");
                return;
            }
            if (Boolean.getBoolean("dndturn.controlProbe.activeCombat") && age > 56) { planTick(); return; }
            if (age == 0) {
                mc.options.guiScale().set(Integer.getInteger("dndturn.controlProbe.scale", 2));
                mc.resizeGui();
            }
            if (age == 10) {
                require(ClientControl.active() && ClientControl.mode() == ClientControl.Mode.CAMERA, "automatic virtual controller");
                require(!mc.mouseHandler.isMouseGrabbed(), "camera pointer");
                require(ClientControl.pitch() == 55, "fixed overhead entry pitch");
                actor = mc.player.position(); yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
                camera = ClientControl.position(); budget = ClientCombatState.encounter().movementTicks();
                slot = mc.player.getInventory().getSelectedSlot(); resultCount = ClientCombatState.encounter().resultCount();
                packets.clear();
                ClientControl.key(new KeyEvent(GLFW.GLFW_KEY_W, 0, 0), GLFW.GLFW_PRESS);
            }
            if (age == 25) {
                ClientControl.key(new KeyEvent(GLFW.GLFW_KEY_W, 0, 0), GLFW.GLFW_RELEASE);
                require(ClientControl.position().distanceToSqr(camera) > .01, "camera translation");
                require(Math.abs(ClientControl.position().y - camera.y) < .001, "horizontal camera pan");
                unchanged();
                float rigYaw = ClientControl.yaw(), rigPitch = ClientControl.pitch();
                point(0.42, .45); button(2, 1); point(.48, .50); button(2, 0);
                mouse().dndturn$scroll(mc.getWindow().handle(), 0, 1);
                require(ClientControl.yaw() != rigYaw || ClientControl.pitch() != rigPitch, "camera rotation");
                unchanged();
                var doc = TacticalOverlay.document();
                doc.getElementById("inventory-toggle").addEventListener("click", event -> clicks++);
                var rect = doc.getElementById("inventory-toggle").getBoundingClientRect();
                var p = doc.documentToScreenPosition(new Position(rect.x + rect.width / 2, rect.y + rect.height / 2));
                cursor(p.x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                    p.y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
                button(0, 1); button(0, 0);
                require(clicks == 1, "UI received exactly one click: count=" + clicks + " hit="
                    + doc.hitTest(doc.screenToDocumentPosition(com.sighs.apricityui.render.Operation.getMousePositionDirectly()))
                    + " pointer=" + com.sighs.apricityui.render.Operation.getMousePositionDirectly()
                    + " rect=" + rect + " grabbed=" + mc.mouseHandler.isMouseGrabbed());
                doc.getElementById("cancel-plan").setAttribute("disabled", "");
                doc.getElementById("cancel-plan").addEventListener("click", event -> disabledClicks++);
                element("cancel-plan"); button(0, 1); button(0, 0);
                require(disabledClicks == 0, "disabled control activated");
                doc.getElementById("cancel-plan").removeAttribute("disabled");
                doc.getElementById("log").addEventListener("wheel", event -> wheelEvents++);
                element("log"); mouse().dndturn$scroll(mc.getWindow().handle(), 0, -1);
                require(wheelEvents == 1, "log did not receive wheel");
                element("inventory-toggle"); button(0, 1); point(.4, .45); button(0, 0);
                require(clicks == 1, "release outside activated original control");
                element("inventory-toggle"); button(0, 1); TacticalOverlay.close(); TacticalOverlay.tick(); button(0, 0);
                require(clicks == 1, "removed document retained its gesture");
                mc.options.keyAttack.setDown(true); mc.options.keyUse.setDown(true);
                net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey());
                net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
                unchanged();
            }
            if (age == 30) {
                point(.4, .45); button(0, 1);
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                ClientControl.reconcile(); button(0, 0); mc.setScreen(null); ClientControl.reconcile();
                require(ClientControl.mode() == ClientControl.Mode.CAMERA && !mc.mouseHandler.isMouseGrabbed(), "screen preserves mode");
                require(!mc.options.keyAttack.isDown() && !mc.options.keyAttack.consumeClick(), "gesture release queue");
                require(!mc.options.keyUse.isDown() && !mc.options.keyUse.consumeClick(), "held use survived UI handoff");
                unchanged();
                ClientControl.toggleMode();
                require(ClientControl.mode() == ClientControl.Mode.CHARACTER && mc.mouseHandler.isMouseGrabbed(), "explicit character control");
                require(ClientControl.blockMovement(), "candidate starts without a movement permit");
                ClientControl.toggleMode();
            }
            if (age == 40) {
                savedForward = mc.options.keyUp.getKey();
                mc.options.keyUp.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H));
                net.minecraft.client.KeyMapping.resetMapping();
                camera = ClientControl.position();
                ClientControl.key(new KeyEvent(GLFW.GLFW_KEY_H, 0, 0), GLFW.GLFW_PRESS);
            }
            if (age == 48) {
                ClientControl.key(new KeyEvent(GLFW.GLFW_KEY_H, 0, 0), GLFW.GLFW_RELEASE);
                require(ClientControl.position().distanceToSqr(camera) > .01, "rebound keyboard camera movement");
                mc.options.keyUp.setKey(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(4));
                net.minecraft.client.KeyMapping.resetMapping();
                camera = ClientControl.position(); point(.4, .45); button(4, 1);
            }
            if (age == 56) {
                button(4, 0);
                require(ClientControl.position().distanceToSqr(camera) > .01, "rebound mouse camera movement");
                mc.options.keyUp.setKey(savedForward); savedForward = null; net.minecraft.client.KeyMapping.resetMapping();
                unchanged();
                for (String name : new String[]{"ServerboundInteractPacket", "ServerboundUseItemPacket", "ServerboundUseItemOnPacket",
                    "ServerboundSwingPacket", "ServerboundSetCarriedItemPacket", "ServerboundPlayerActionPacket", "CHANGED_ACTOR_POSE"})
                    require(packets.getOrDefault(name, 0) == 0, "unexpected world packet " + name + packets);
                require(packets.getOrDefault("Pos", 0) > 0, "player position heartbeat did not run");
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) {
                    var peer = com.google.gson.JsonParser.parseString(Files.readString(output().resolveSibling("peer-state.json"))).getAsJsonObject();
                    require(peer.get("active").getAsBoolean() && peer.get("independent").getAsBoolean(), "peer camera/actor was changed by primary");
                    require(System.currentTimeMillis() - peer.get("time").getAsLong() < 3000, "stale peer evidence");
                }
                if (!Boolean.getBoolean("dndturn.controlProbe.activeCombat"))
                    finish("PASS: dedicated normal START; automatic camera; translation/rotation/scroll preserve actor pose, budget, results and hotbar; UI callback once; no attack/use/interact/swing/hotbar/action packets; disabled button/log wheel/close-during-press; screen gesture ownership; explicit mode; candidate requires explicit movement plan; keyboard and mouse movement rebinding. Window=" + mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + " GUI=" + mc.options.guiScale().get() + " Packets=" + packets);
            }
        } catch (Throwable failure) { finish("FAIL: " + failure + " packets=" + packets); }
    }
    private static void rawKey(int code) {
        var mc = Minecraft.getInstance();
        var keyboard = (cc.sighs.dndturn.mixin.client.ClientKeyboardProbeAccessor)mc.keyboardHandler;
        keyboard.dndturn$key(mc.getWindow().handle(), GLFW.GLFW_PRESS, new KeyEvent(code,0,0));
        keyboard.dndturn$key(mc.getWindow().handle(), GLFW.GLFW_RELEASE, new KeyEvent(code,0,0));
    }
    private static void pointAt(Vec3 target) {
        var mc = Minecraft.getInstance(); var camera = mc.gameRenderer.getMainCamera();
        var delta = target.subtract(ClientControl.position());
        var local = new org.joml.Vector3f((float)delta.x,(float)delta.y,(float)delta.z)
            .rotate(new org.joml.Quaternionf(camera.rotation()).conjugate());
        double tan = Math.tan(Math.toRadians(mc.options.fov().get())/2);
        double aspect = (double)mc.getWindow().getScreenWidth()/mc.getWindow().getScreenHeight();
        point((local.x/(-local.z*tan*aspect)+1)/2, (1-local.y/(-local.z*tan))/2);
    }
    private static void planTick() throws Exception {
        var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter(); stageTicks++;
        require(stageTicks < 400,"raw input stage timeout " + activeStage + " " + ClientTacticalPlan.description()+" current="+state.current()+" self="+mc.player.getUUID()+" action="+state.action()+" status="+ClientCombatState.latestStatus());
        switch (activeStage) {
            case 0 -> {
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) Files.writeString(output().resolveSibling("active-ready.txt"), "ready");
                if (!mc.player.getUUID().equals(state.current())) return;
                TacticalOverlay.closePanel(); ClientTacticalPlan.cancel(); ClientControl.home();
                actor = mc.player.position(); budget = state.movementTicks();
                queryRequestCount = packets.getOrDefault("TACTICAL_REQUEST", 0);
                activeStage = -4; stageTicks = 0;
            }
            case -4 -> {
                if (stageTicks < 5) return;
                replayOptions = null; holdOptions = true;
                pointAt(actor.add(2,0,0)); button(0,1); button(0,0);
                require(ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.QUERYING,"ground query is not cancellable");
                rawKey(GLFW.GLFW_KEY_ESCAPE);
                require(mc.screen==null && ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.IDLE,"Esc did not consume ground query");
                nextStage();
            }
            case -3 -> {
                if (replayOptions == null) return;
                var delayed = replayOptions; holdOptions = false;
                delayed.run(); delayed.run(); nextStage();
            }
            case -2 -> {
                if (stageTicks < 10) return;
                require(ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.IDLE && ClientTacticalPlan.offers().isEmpty()
                    && ClientTacticalPlan.inspected()==null && mc.screen==null,"delayed ground Options revived selection/menu");
                require(!ClientTacticalPlan.running() && !state.moving() && actor.distanceToSqr(mc.player.position())<1e-8
                    && state.movementTicks()==budget && packets.getOrDefault("TACTICAL_REQUEST",0)==queryRequestCount,
                    "cancelled ground query sent a Request or gained movement");
                pointAt(actor.add(2,0,0)); button(0,1); button(0,0);
                button(1,1); button(1,0);
                require(ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.IDLE,"right click did not cancel ground query");
                button(1,1); button(1,0); nextStage();
            }
            case -1 -> {
                if (stageTicks < 10 || ClientTacticalPlan.offers().isEmpty()) return;
                require(ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.CONTEXT_MENU && !ClientTacticalPlan.running()
                    && packets.getOrDefault("TACTICAL_REQUEST",0)==queryRequestCount,
                    "context discovery inherited ground auto-submit");
                rawKey(GLFW.GLFW_KEY_ESCAPE);
                activeStage = 1; stageTicks = 0;
            }
            case 1 -> {
                if (stageTicks == 5) { pointAt(actor.add(2,0,0)); button(1,1); button(1,0); }
                if (stageTicks < 15) return;
                require(ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.CONTEXT_MENU && !ClientTacticalPlan.offers().isEmpty(),"right click did not open target menu");
                rawKey(GLFW.GLFW_KEY_ESCAPE);
                require(!ClientTacticalPlan.running() && ClientTacticalPlan.offers().isEmpty(),"menu cancellation submitted an action");
                pointAt(actor.add(2,0,0)); button(0,1); button(0,0); nextStage();
            }
            case 2 -> {
                if (stageTicks < 20 || ClientTacticalPlan.running()) return;
                require(mc.player.position().distanceToSqr(actor)>1 && state.movementTicks()<budget,"raw ground click did not move and charge");
                if (Boolean.getBoolean("dndturn.controlProbe.queryOnly")) {
                    finish("PASS: raw ground click/Esc before held Options; duplicate delayed replies cause no Request, permit, movement or revived UI; right-click cancellation and replacement menu discovery do not auto-submit; uncancelled ground click moves and charges.");
                    return;
                }
                rawKey(GLFW.GLFW_KEY_1); rawKey(GLFW.GLFW_KEY_2); rawKey(GLFW.GLFW_KEY_3); nextStage();
            }
            case 3 -> {
                if (stageTicks < 10) return;
                if (cancelledSelection) {
                    require(ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.IDLE && ClientTacticalPlan.offers().isEmpty(),"late duplicate Options revived cancelled selection");
                    rawKey(GLFW.GLFW_KEY_3); nextStage(); return;
                }
                if (ClientTacticalPlan.phase()!=ClientTacticalPlan.Phase.TARGETING) return;
                require(mc.player.getInventory().getSelectedSlot()==2,"rapid 1/2/3 selected wrong slot");
                rawKey(GLFW.GLFW_KEY_ESCAPE);
                require(ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.IDLE && ClientTacticalPlan.offers().isEmpty(),"Esc did not clear targeting");
                require(replayOptions!=null,"no Options captured for application reorder test");
                replayOptions.run(); replayOptions.run(); cancelledSelection=true;
            }
            case 4 -> {
                if (stageTicks < 10 || ClientTacticalPlan.phase()!=ClientTacticalPlan.Phase.TARGETING) return;
                var target = mc.level.entitiesForRendering().iterator();
                while (target.hasNext()) {
                    var entity = target.next();
                    if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie && state.members().stream().anyMatch(m -> m.id().equals(entity.getUUID()))) {
                        actor=mc.player.position(); pointAt(entity.getBoundingBox().getCenter()); button(0,1); button(0,0); nextStage(); return;
                    }
                }
                throw new IllegalStateException("raw target fixture missing; members="+state.members()+" actor="+mc.player.position());
            }
            case 5 -> {
                if (stageTicks<20 || ClientTacticalPlan.running()) return;
                require(state.phase()==cc.sighs.dndturn.combat.EncounterPhase.ACTIVE,"raw attack did not activate: "+ClientTacticalPlan.description());
                require(packets.getOrDefault("RECEIVED_SELF_SWING", 0) == 1, "attack/retry did not emit exactly one swing");
                if (!attackReplayed) {
                    require(lastTacticalRequest != null, "missing attack request for retry");
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(lastTacticalRequest);
                    attackReplayed = true; stageTicks = 0; return;
                }
                require(actor.distanceToSqr(mc.player.position())>.1,"raw attack did not approach");
                require(ClientControl.mode()==ClientControl.Mode.CAMERA,"plan changed camera mode");
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) {
                    var evidence=com.google.gson.JsonParser.parseString(Files.readString(output().resolveSibling("peer-state.json"))).getAsJsonObject();
                    require(evidence.get("independent").getAsBoolean(),"peer camera changed");
                    require(new Vec3(evidence.get("primaryX").getAsDouble(),evidence.get("primaryY").getAsDouble(),evidence.get("primaryZ").getAsDouble()).distanceToSqr(mc.player.position())<.01,"peer actor did not converge");
                }
                // First hostile execution rolls initiative; its owner need not act first.
                // Wait for the authoritative turn before testing the end-turn key.
                if (!mc.player.getUUID().equals(state.current())) return;
                rawKey(GLFW.GLFW_KEY_SPACE); nextStage();
            }
            case 6 -> {
                if (!mc.player.getUUID().equals(state.current()) || !state.action()) return;
                require(replayRunning!=null,"no running projection captured");
                replayRunning.run(); replayRunning.run();
                rawKey(GLFW.GLFW_KEY_1); ClientControl.home(); nextStage();
            }
            case 7 -> {
                require(!ClientTacticalPlan.running(),"late running projection revived terminal operation");
                if (stageTicks<10 || ClientTacticalPlan.phase()!=ClientTacticalPlan.Phase.TARGETING) return;
                placed=mc.player.blockPosition().offset(-1,0,1);
                require(mc.level.getBlockState(placed).isAir(),"placement fixture occupied");
                pointAt(Vec3.atLowerCornerOf(placed).add(.5,0,.5)); button(0,1); button(0,0); nextStage();
            }
            case 8 -> {
                if (stageTicks<20 || ClientTacticalPlan.running()) return;
                require(mc.level.getBlockState(placed).is(net.minecraft.world.level.block.Blocks.DIRT)
                    && mc.player.getMainHandItem().getCount()==7 && !state.action(),"raw placement did not place/charge once: "+ClientTacticalPlan.description());
                pointAt(new Vec3(1.5,120.2,-1.5)); button(1,1); button(1,0); nextStage();
            }
            case 9 -> {
                if (stageTicks<10 || ClientTacticalPlan.offers().isEmpty()) return;
                var offer=ClientTacticalPlan.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:block")).findFirst().orElseThrow();
                require(offer.reason().isEmpty(),"free block disabled after action spent: "+offer.reason());
                element("offer-dndturn-block"); button(0,1); button(0,0); nextStage();
            }
            case 10 -> {
                if (stageTicks<20 || ClientTacticalPlan.running()) return;
                var lever=mc.level.getBlockState(new net.minecraft.core.BlockPos(1,120,-2));
                require(lever.is(net.minecraft.world.level.block.Blocks.LEVER) && lever.getValue(net.minecraft.world.level.block.LeverBlock.POWERED)
                    && !state.action() && mc.player.getMainHandItem().getCount()==7,"menu free interaction failed or fell back to item: "+ClientTacticalPlan.description());
                finish("PASS: real keyboard 1/2/3, Esc, Space; target menu cancel and menu-item mouse activation; late duplicate Options and running projections ignored after cancellation/terminal; rendered-camera MoveTo, approach attack, one paid placement and free lever after action spent; camera isolation and peer convergence.");
            }
        }
    }
    private static void planMethodTick() throws Exception {
        var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter(); stageTicks++;
        require(stageTicks < 400, "plan stage timeout " + activeStage + " " + ClientTacticalPlan.description());
        switch (activeStage) {
            case 0 -> {
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) Files.writeString(output().resolveSibling("active-ready.txt"), "ready");
                if (!mc.player.getUUID().equals(state.current())) return;
                actor = mc.player.position(); budget = state.movementTicks();
                ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE);
                var ground = mc.player.blockPosition().offset(2, -1, 0);
                ClientTacticalPlan.target(null, new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, .5, 0), net.minecraft.core.Direction.UP, ground, false));
                nextStage();
            }
            case 1 -> {
                if (!ClientTacticalPlan.offers().isEmpty()) {
                    ClientTacticalPlan.choose(ClientTacticalPlan.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:move")).findFirst().orElseThrow());
                    return;
                }
                if (ClientTacticalPlan.running() || stageTicks < 15) return;
                require(mc.player.position().distanceToSqr(actor) > 1 && state.movementTicks() < budget,
                    "automatic MoveTo did not move and charge: " + ClientTacticalPlan.description());
                require(ClientControl.mode() == ClientControl.Mode.CAMERA, "MoveTo changed camera mode");
                for (var entity : mc.level.entitiesForRendering()) if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie
                    && state.members().stream().anyMatch(m -> m.id().equals(entity.getUUID()))
                    && entity.position().distanceToSqr(mc.player.position()) < 64) {
                    actor = mc.player.position();
                    ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK);
                    ClientTacticalPlan.target(entity.getUUID(), net.minecraft.world.phys.BlockHitResult.miss(entity.position(),
                        net.minecraft.core.Direction.UP, entity.blockPosition()));
                    nextStage(); return;
                }
                throw new IllegalStateException("plan fixture target missing");
            }
            case 2 -> {
                if (!ClientTacticalPlan.offers().isEmpty()) {
                    ClientTacticalPlan.choose(ClientTacticalPlan.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:melee")).findFirst().orElseThrow());
                    return;
                }
                if (ClientTacticalPlan.running() || stageTicks < 15) return;
                require(state.phase() == cc.sighs.dndturn.combat.EncounterPhase.ACTIVE,
                    "approach attack failed: " + ClientTacticalPlan.description());
                require(actor.distanceToSqr(mc.player.position()) > .1, "attack did not approach its target");
                require(ClientControl.mode() == ClientControl.Mode.CAMERA, "attack approach changed camera mode");
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) { nextStage(); return; }
                finish("PASS: real dedicated client overhead camera, UI/input isolation, candidate MoveTo through vanilla input, observed movement charge, automatic approach and one attack activation.");
            }
            case 3 -> {
                if (stageTicks < 20) return;
                var evidence = com.google.gson.JsonParser.parseString(Files.readString(output().resolveSibling("peer-state.json"))).getAsJsonObject();
                require(evidence.get("active").getAsBoolean() && evidence.get("independent").getAsBoolean(), "peer camera or actor moved");
                require(System.currentTimeMillis() - evidence.get("time").getAsLong() < 3000, "stale peer evidence");
                Vec3 remote = new Vec3(evidence.get("primaryX").getAsDouble(), evidence.get("primaryY").getAsDouble(), evidence.get("primaryZ").getAsDouble());
                require(remote.distanceToSqr(mc.player.position()) < .01, "plan movement did not converge on peer");
                finish("PASS: two real clients; independent tactical cameras; candidate MoveTo and approach attack through vanilla input; movement cost and remote actor synchronization.");
            }
            default -> throw new IllegalStateException("unexpected plan stage");
        }
    }
    private static void activeTick() throws Exception {
        var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter(); stageTicks++;
        require(stageTicks < 400, "active stage timeout " + activeStage + " status=" + ClientCombatState.latestStatus());
        switch (activeStage) {
            case 0 -> {
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) Files.writeString(output().resolveSibling("active-ready.txt"), "ready");
                if (!mc.player.getUUID().equals(state.current())) return;
                var zombie = mc.level.entitiesForRendering().iterator();
                while (zombie.hasNext()) {
                    var entity = zombie.next();
                    if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie && entity.position().distanceToSqr(mc.player.position()) < 9) {
                        int before = worldPacketCount();
                        require(mc.gameMode.interact(mc.player, entity, new net.minecraft.world.phys.EntityHitResult(entity),
                            net.minecraft.world.InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.PASS,
                            "entity interaction prediction accepted");
                        require(worldPacketCount() == before, "entity interaction emitted a world packet");
                        CombatControls.requestFromUi(cc.sighs.dndturn.combat.CombatNetwork.IntentKind.ATTACK, entity.getUUID());
                        nextStage(); return;
                    }
                }
                throw new IllegalStateException("fixture zombie missing");
            }
            case 1 -> {
                if (state.phase() != cc.sighs.dndturn.combat.EncounterPhase.ACTIVE || !mc.player.getUUID().equals(state.current())) return;
                CombatControls.requestFromUi(cc.sighs.dndturn.combat.CombatNetwork.IntentKind.MOVE_BEGIN, null); nextStage();
            }
            case 2 -> {
                if (!state.moving() || !ClientCombatState.movementAllowed()) return;
                actor = mc.player.position(); budget = state.movementTicks();
                mc.options.keyUp.setDown(true);
                ClientControl.key(new KeyEvent(GLFW.GLFW_KEY_W, 0, 0), GLFW.GLFW_PRESS); nextStage();
            }
            case 3 -> {
                if (stageTicks < 10) return;
                require(actor.distanceToSqr(mc.player.position()) < 1e-8 && state.movementTicks() == budget, "permit forwarded camera input");
                ClientControl.key(new KeyEvent(GLFW.GLFW_KEY_W, 0, 0), GLFW.GLFW_RELEASE);
                ClientControl.toggleMode();
                mc.options.keyUp.setDown(true); nextStage();
            }
            case 4 -> {
                mc.options.keyUp.setDown(true);
                if (stageTicks < 8) return;
                require(actor.distanceToSqr(mc.player.position()) > .01 && state.movementTicks() < budget, "authorized character movement/charge missing");
                ClientControl.toggleMode();
                require(ClientControl.blockMovement() && !mc.options.keyUp.isDown(), "camera switch retained character input");
                CombatControls.requestFromUi(cc.sighs.dndturn.combat.CombatNetwork.IntentKind.MOVE_BEGIN, null); nextStage();
            }
            case 5 -> {
                if (state.moving()) return;
                if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) {
                    if (stageTicks < 20) return;
                    var evidence = com.google.gson.JsonParser.parseString(Files.readString(output().resolveSibling("peer-state.json"))).getAsJsonObject();
                    require(evidence.get("active").getAsBoolean() && evidence.get("independent").getAsBoolean(), "peer lost independent control");
                    require(System.currentTimeMillis() - evidence.get("time").getAsLong() < 3000, "stale peer movement evidence");
                    Vec3 remote = new Vec3(evidence.get("primaryX").getAsDouble(), evidence.get("primaryY").getAsDouble(), evidence.get("primaryZ").getAsDouble());
                    require(remote.distanceToSqr(mc.player.position()) < .01 && remote.distanceToSqr(actor) > .01, "authorized movement did not synchronize to peer");
                    finish("PASS: two real clients in active combat; independent virtual cameras; authorized original character movement spends budget and synchronizes to peer; camera switch stops character input.");
                    return;
                }
                oldPlayer = mc.player;
                mc.player.connection.sendCommand("attribute @s minecraft:max_health base set 1");
                CombatControls.requestFromUi(cc.sighs.dndturn.combat.CombatNetwork.IntentKind.END_TURN, null); nextStage();
            }
            default -> { }
        }
    }
    private static void lifecycleTick() {
        var mc = Minecraft.getInstance(); stageTicks++;
        require(stageTicks < 400, "lifecycle timeout stage " + activeStage + " status=" + ClientCombatState.latestStatus());
        if (mc.player == null || mc.level == null) return;
        switch (activeStage) {
            case 6 -> {
                if (mc.player.isAlive()) {
                    var state = ClientCombatState.encounter();
                    if (stageTicks % 40 == 0 && state != null && mc.player.getUUID().equals(state.current()))
                        CombatControls.requestFromUi(cc.sighs.dndturn.combat.CombatNetwork.IntentKind.END_TURN, null);
                    return;
                }
                require(!ClientControl.active() && !TacticalOverlay.isOpen(), "death retained control");
                mc.player.respawn(); nextStage();
            }
            case 7 -> {
                if (!mc.player.isAlive() || mc.player == oldPlayer || mc.screen != null) return;
                require(!ClientControl.active() && ClientCombatState.encounter() == null, "respawn restored old session");
                mc.player.connection.sendCommand("attribute @s minecraft:max_health base set 20");
                mc.player.connection.sendCommand("effect give @s minecraft:instant_health 1 10");
                mc.player.connection.sendCommand("execute in minecraft:the_nether run tp @s 0 130 0"); nextStage();
            }
            case 8 -> {
                if (!mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER)) return;
                require(!ClientControl.active() && !TacticalOverlay.isOpen(), "dimension retained control");
                mc.player.connection.sendCommand("execute in minecraft:overworld run tp @s 0.5 120 0.5"); nextStage();
            }
            case 9 -> {
                if (!mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) || mc.screen != null) return;
                mc.player.connection.sendCommand("dndturn tactical start"); nextStage();
            }
            case 10 -> {
                if (ClientCombatState.encounter() == null || !ClientControl.active()) return;
                require(ClientControl.mode() == ClientControl.Mode.CAMERA, "new lifecycle did not auto-enter camera");
                require(ClientControl.position().distanceToSqr(mc.player.getEyePosition()) < .01, "new lifecycle restored stale virtual pose");
                mc.player.connection.sendCommand("tp @s 80 120 0"); nextStage();
            }
            case 11 -> {
                if (mc.player.getX() < 70) return;
                CombatControls.requestFromUi(cc.sighs.dndturn.combat.CombatNetwork.IntentKind.EXIT, null); nextStage();
            }
            case 12 -> {
                if (ClientCombatState.encounter() != null) return;
                require(!ClientControl.active() && !TacticalOverlay.isOpen() && TacticalOverlay.selectedTarget() == null, "legal EXIT retained control");
                finish("PASS: dedicated normal START and attack activation; active permit does not forward camera input; explicit character locomotion changes position and spends budget; camera switch clears input; death/respawn/player replacement/dimension/re-entry/center-outside EXIT clear control. Fixture teleports only prepare lifecycle scenarios; movement assertion uses original travel.");
            }
            default -> throw new IllegalStateException("unexpected lifecycle stage");
        }
    }
    private static void nextStage() { activeStage++; stageTicks = 0; }
    private static Path output() { return Path.of(System.getProperty("dndturn.controlProbe.output")); }
    private static void peerTick() throws Exception {
        var mc = Minecraft.getInstance();
        if (++ticks > 2400) throw new IllegalStateException("peer timeout");
        mc.options.pauseOnLostFocus = false;
        if (mc.player != null && mc.level != null && !ClientControl.active() && !ClientControl.modal()) {
            var primary = mc.level.players().stream().filter(p -> p.getName().getString().equals("DNDCameraProbe")).findFirst();
            if (primary.isPresent()) {
                Vec3 offset = primary.get().position().subtract(mc.player.position());
                boolean walk = offset.horizontalDistanceSqr() > 25 || Math.abs(offset.y) > 4;
                mc.player.setYRot((float)Math.toDegrees(Math.atan2(-offset.x, offset.z)));
                mc.options.keyUp.setDown(walk); mc.options.keyJump.setDown(false);
            }
        }
        if (ClientControl.modal() && ConsentOverlay.document() != null)
            ConsentOverlay.document().getElementById("agree").click();
        var state = ClientCombatState.encounter();
        if (Boolean.getBoolean("dndturn.controlProbe.activeCombat") && state != null && mc.player != null
            && Files.exists(output().resolveSibling("active-ready.txt"))
            && Files.getLastModifiedTime(output().resolveSibling("active-ready.txt")).toMillis() >= startedAt
            && mc.player.getUUID().equals(state.current()) && ticks % 40 == 0)
            CombatControls.requestFromUi(cc.sighs.dndturn.combat.CombatNetwork.IntentKind.END_TURN, null);
        if (ClientControl.active() && peerCamera == null) { peerCamera = ClientControl.position(); peerActor = mc.player.position(); }
        var evidence = new com.google.gson.JsonObject();
        evidence.addProperty("active", ClientControl.active());
        evidence.addProperty("independent", peerCamera != null && peerCamera.equals(ClientControl.position()) && peerActor.equals(mc.player.position()));
        evidence.addProperty("time", System.currentTimeMillis());
        if (mc.level != null) mc.level.players().stream().filter(p -> p.getName().getString().equals("DNDCameraProbe")).findFirst().ifPresent(primary -> {
            evidence.addProperty("primaryX", primary.getX()); evidence.addProperty("primaryY", primary.getY()); evidence.addProperty("primaryZ", primary.getZ());
        });
        Files.createDirectories(output().getParent());
        Files.writeString(output().resolveSibling("peer-state.json"), evidence.toString());
        if (Files.exists(output()) && Files.getLastModifiedTime(output()).toMillis() >= startedAt) {
            String primaryResult = Files.readString(output());
            if (primaryResult.startsWith("PASS:")) finish("PASS: second real client consent and independent stationary camera/actor while primary camera moves");
            else if (primaryResult.startsWith("FAIL:")) finish("FAIL: primary probe failed: " + primaryResult);
        }
    }
    private static void unchanged() {
        var mc = Minecraft.getInstance();
        require(actor.distanceToSqr(mc.player.position()) < 1e-8 && yaw == mc.player.getYRot() && pitch == mc.player.getXRot(), "actor pose changed");
        require(ClientCombatState.encounter().movementTicks() == budget, "camera spent movement");
        require(ClientCombatState.encounter().resultCount() == resultCount, "unexpected operation result");
        require(mc.player.getInventory().getSelectedSlot() == slot, "hotbar changed");
    }
    private static ClientInputProbeAccessor mouse() { return (ClientInputProbeAccessor)Minecraft.getInstance().mouseHandler; }
    private static void point(double x, double y) {
        var mc = Minecraft.getInstance(); cursor(x * mc.getWindow().getScreenWidth(), y * mc.getWindow().getScreenHeight());
    }
    private static void cursor(double x, double y) {
        var mc = Minecraft.getInstance(); GLFW.glfwSetCursorPos(mc.getWindow().handle(), x, y);
        mouse().dndturn$move(mc.getWindow().handle(), x, y);
    }
    private static void element(String id) {
        var mc = Minecraft.getInstance(); var doc = TacticalOverlay.document();
        var rect = doc.getElementById(id).getBoundingClientRect();
        var p = doc.documentToScreenPosition(new Position(rect.x + rect.width / 2, rect.y + rect.height / 2));
        cursor(p.x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
            p.y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
    }
    private static void button(int button, int action) {
        mouse().dndturn$button(Minecraft.getInstance().getWindow().handle(), new MouseButtonInfo(button, 0), action);
    }
    private static void require(boolean condition, String text) { if (!condition) throw new IllegalStateException(text); }
    private static void finish(String result) {
        done = true;
        replayOptions = null; replayRunning = null;
        holdOptions = false;
        if (savedForward != null) {
            Minecraft.getInstance().options.keyUp.setKey(savedForward);
            net.minecraft.client.KeyMapping.resetMapping(); savedForward = null;
        }
        try { Path file = output(); if (Boolean.getBoolean("dndturn.controlProbe.peer")) file = file.resolveSibling("peer-result.txt");
            Files.createDirectories(file.getParent()); Files.writeString(file, result); }
        catch (Exception failure) { throw new RuntimeException(failure); }
        Minecraft.getInstance().stop();
    }
}
