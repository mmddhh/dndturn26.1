# DNDTurn 161358 源码验收证据

审查对象：`DNDTurn-sources(20260925-161358).zip`；对照：`DNDTurn-sources(20260925-135634).zip`。
新版 SHA-256：`d4b9f2f77d9ea8f74e4e25f7cc1ec56d0093103f8f9a5b211920909e13b1b599`。
旧版 SHA-256：`f72bb358c4629811d81f3b9cccdc6ca549f6ca76866d1e02671f7b210141fbf8`。
差异：26 个新增文件、25 个修改文件、0 个删除文件；两个包均不含 AGENTS.md 引用的 docs/。
以下 L 行号均为解压后原文件行号，不是此证据文件的行号。原始项目源码未修改。
验证：18 个 common 主源码以 javac 21.0.11 --release 17 编译通过；6 项独立离线探针通过，并复现 1 项死亡清理顺序回归。探针不是 JUnit，也未加载 Minecraft。
限制：JUnit 依赖下载 DNS 失败，未运行项目 JUnit；本环境未安装 target 所需 JDK 25，未运行 target 构建、GameTest、专服或真实客户端。

<a id="e01"></a>
## E01 — 镜头已改为焦点 rig、水平平移、QE、中键与缩放

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L55–L89

```text
   55 |     public static void reconcile() {
   56 |         Minecraft mc = Minecraft.getInstance();
   57 |         boolean valid = ClientCombatState.encounter() != null && mc.player != null && mc.player.isAlive()
   58 |             && mc.level != null && mc.getConnection() != null;
   59 |         if (level != mc.level || player != mc.player) {
   60 |             reset(); level = mc.level; player = mc.player;
   61 |         }
   62 |         if (valid && !session) {
   63 |             session = true; mode = Mode.CAMERA;
   64 |             cameraOwner = mc.getCameraEntity();
   65 |             focus = mc.player.position().add(0, 1, 0);
   66 |             yaw = 0; pitch = 55; distance = 12;
   67 |             updateRig();
   68 |             restoreGrab = mc.mouseHandler.isMouseGrabbed();
   69 |             transition();
   70 |         } else if (!valid && session) {
   71 |             ClientTacticalPlan.reset();
   72 |             session = false; position = null; focus = null; cameraOwner = null; transition();
   73 |         }
   74 |         Recipient next = !mc.isWindowActive() ? Recipient.UNFOCUSED : mc.screen != null ? Recipient.SCREEN
   75 |             : modal() ? Recipient.CONSENT : session ? mode == Mode.CAMERA ? Recipient.CAMERA : Recipient.CHARACTER
   76 |             : Recipient.GAME;
   77 |         if (next != recipient) {
   78 |             boolean controlled = session || modal() || recipient == Recipient.CAMERA
   79 |                 || recipient == Recipient.CHARACTER || recipient == Recipient.CONSENT;
   80 |             recipient = next;
   81 |             if (controlled) transition();
   82 |         }
   83 |         if (mc.player == null || mc.screen != null || !mc.isWindowActive()) return;
   84 |         if (next == Recipient.CONSENT || next == Recipient.CAMERA) {
   85 |             if (mc.mouseHandler.isMouseGrabbed()) { restoreGrab = true; mc.mouseHandler.releaseMouse(); }
   86 |         } else if ((next == Recipient.CHARACTER || next == Recipient.GAME && restoreGrab)
   87 |             && !mc.mouseHandler.isMouseGrabbed()) {
   88 |             mc.mouseHandler.grabMouse(); restoreGrab = false;
   89 |         }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L246–L327

```text
  246 |     public static boolean scroll(double delta, boolean consumed) {
  247 |         reconcile();
  248 |         if (Minecraft.getInstance().screen != null) return consumed;
  249 |         if (!consumed && recipient == Recipient.CAMERA && !TacticalOverlay.hit(mousePosition())) {
  250 |             distance = Mth.clamp(distance - delta, 3, 32);
  251 |             updateRig();
  252 |         }
  253 |         return consumed || session || modal();
  254 |     }
  255 |     private static Position mousePosition() {
  256 |         var mc = Minecraft.getInstance();
  257 |         return new Position(mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow()));
  258 |     }
  259 |     public static void mouseMove(double x, double y) {
  260 |         reconcile();
  261 |         Gesture g = gestures.get(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
  262 |         if (!Double.isNaN(mouseX) && recipient == Recipient.CAMERA && g != null
  263 |             && g.owner == Recipient.CAMERA && g.revision == revision) {
  264 |             var o = Minecraft.getInstance().options;
  265 |             double s = Math.pow(o.sensitivity().get() * .6 + .2, 3) * 8 * .15;
  266 |             yaw += (float)((x - mouseX) * s * (o.invertMouseX().get() ? -1 : 1));
  267 |             pitch = Mth.clamp(pitch + (float)((y - mouseY) * s * (o.invertMouseY().get() ? -1 : 1)), 25, 80);
  268 |             updateRig();
  269 |         }
  270 |         mouseX = x; mouseY = y;
  271 |     }
  272 |     public static boolean camera(Camera camera) {
  273 |         reconcile();
  274 |         var mc = Minecraft.getInstance();
  275 |         return session && mode == Mode.CAMERA && position != null && camera == mc.gameRenderer.getMainCamera()
  276 |             && mc.getCameraEntity() == cameraOwner && cameraOwner == mc.player;
  277 |     }
  278 |     public static void frame() {
  279 |         long now = System.nanoTime();
  280 |         double seconds = frameTime == 0 ? 0 : Math.min(.05, (now - frameTime) / 1e9);
  281 |         frameTime = now;
  282 |         if (recipient != Recipient.CAMERA) return;
  283 |         var o = Minecraft.getInstance().options;
  284 |         double f = axis(o.keyUp, o.keyDown), l = axis(o.keyLeft, o.keyRight);
  285 |         yaw = Mth.wrapDegrees(yaw + (float)(axis(CombatControls.rotateRightKey(), CombatControls.rotateLeftKey()) * seconds * 90));
  286 |         Vec3 forward = Vec3.directionFromRotation(0, yaw);
  287 |         Vec3 left = new Vec3(forward.z, 0, -forward.x).normalize();
  288 |         Vec3 delta = forward.scale(f).add(left.scale(l));
  289 |         if (delta.lengthSqr() > 0) move(delta.normalize().scale(seconds * (cameraKeys.contains(o.keySprint) ? 12 : 6)));
  290 |         updateRig();
  291 |     }
  292 |     private static int axis(KeyMapping positive, KeyMapping negative) {
  293 |         return (cameraKeys.contains(positive) ? 1 : 0) - (cameraKeys.contains(negative) ? 1 : 0);
  294 |     }
  295 |     private static Vec3 forward() { return Vec3.directionFromRotation(pitch, yaw); }
  296 |     private static void move(Vec3 delta) {
  297 |         if (focus == null) return;
  298 |         var world = Minecraft.getInstance().level;
  299 |         if (world == null) return;
  300 |         int steps = Math.max(1, (int)Math.ceil(delta.length() * 2));
  301 |         for (int i = 1; i <= steps; i++)
  302 |             if (!world.hasChunkAt(BlockPos.containing(focus.add(delta.scale((double)i / steps))))) return;
  303 |         focus = focus.add(delta);
  304 |         updateRig();
  305 |     }
  306 |     private static void updateRig() {
  307 |         if (focus == null) return;
  308 |         var mc = Minecraft.getInstance();
  309 |         Vec3 desired = focus.subtract(forward().scale(distance));
  310 |         // Clip the visual boom without loading terrain or moving the camera entity.
  311 |         if (mc.level != null && mc.player != null) {
  312 |             Vec3 boom = desired.subtract(focus);
  313 |             for (int i = 1, count = Math.max(1, (int)Math.ceil(distance * 2)); i <= count; i++) {
  314 |                 Vec3 point = focus.add(boom.scale((double)i / count));
  315 |                 if (!mc.level.hasChunkAt(BlockPos.containing(point))) {
  316 |                     desired = focus.add(boom.scale((double)(i - 1) / count));
  317 |                     break;
  318 |                 }
  319 |             }
  320 |             var hit = mc.level.clip(new ClipContext(focus, desired, ClipContext.Block.VISUAL,
  321 |                 ClipContext.Fluid.NONE, mc.player));
  322 |             if (hit.getType() != HitResult.Type.MISS) {
  323 |                 Vec3 offset = hit.getLocation().subtract(focus);
  324 |                 desired = focus.add(offset.normalize().scale(Math.max(0, offset.length() - .2)));
  325 |             }
  326 |         }
  327 |         position = desired;
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java`：L22–L73

```text
   22 | /** Rebindable prototype controls. Packets carry intent, never authoritative costs or damage. */
   23 | public final class CombatControls {
   24 |     private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
   25 |         Identifier.fromNamespaceAndPath(DNDTurnNeoForge261.MOD_ID, "prototype"));
   26 |     private static final KeyMapping UI = key("ui", GLFW.GLFW_KEY_GRAVE_ACCENT);
   27 |     private static final KeyMapping START = new KeyMapping("key.dndturn.start", KeyConflictContext.IN_GAME,
   28 |         KeyModifier.SHIFT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
   29 |     private static final KeyMapping EXIT = key("exit", GLFW.GLFW_KEY_UNKNOWN);
   30 |     private static final KeyMapping MOVE = key("move", GLFW.GLFW_KEY_UNKNOWN);
   31 |     private static final KeyMapping ATTACK = key("attack", GLFW.GLFW_KEY_UNKNOWN);
   32 |     private static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_UNKNOWN);
   33 |     private static final KeyMapping DODGE = key("dodge", GLFW.GLFW_KEY_UNKNOWN);
   34 |     private static final KeyMapping DISENGAGE = key("disengage", GLFW.GLFW_KEY_UNKNOWN);
   35 |     private static final KeyMapping END_TURN = key("end_turn", GLFW.GLFW_KEY_SPACE);
   36 |     private static final KeyMapping INVENTORY = key("inventory", GLFW.GLFW_KEY_I);
   37 |     private static final KeyMapping ROTATE_LEFT = key("rotate_left", GLFW.GLFW_KEY_Q);
   38 |     private static final KeyMapping ROTATE_RIGHT = key("rotate_right", GLFW.GLFW_KEY_E);
   39 |     private static final KeyMapping JUMP = key("jump", GLFW.GLFW_KEY_Z);
   40 |     private enum MovePhase { IDLE, START_PENDING, MOVING, STOP_PENDING }
   41 |     private static MovePhase movePhase = MovePhase.IDLE;
   42 |     private static UUID movementOperationId;
   43 |     private static long movementStartVersion;
   44 |     private static long movementEndVersion = -1;
   45 |     private static boolean endTurnAfterMove;
   46 |     private static boolean exitRetryPending;
   47 |     private static boolean exitAfterStart;
   48 |     private static long lastExitVersion;
   49 |     private static UUID startOperationId;
   50 | 
   51 |     private CombatControls() {}
   52 | 
   53 |     private static KeyMapping key(String name, int code) {
   54 |         return new KeyMapping("key.dndturn." + name, KeyConflictContext.IN_GAME,
   55 |             InputConstants.Type.KEYSYM, code, CATEGORY);
   56 |     }
   57 | 
   58 |     public static void registerKeys(RegisterKeyMappingsEvent event) {
   59 |         event.registerCategory(CATEGORY);
   60 |         event.register(START);
   61 |         event.register(UI);
   62 |         event.register(EXIT);
   63 |         event.register(MOVE);
   64 |         event.register(ATTACK);
   65 |         event.register(DASH);
   66 |         event.register(DODGE);
   67 |         event.register(DISENGAGE);
   68 |         event.register(END_TURN);
   69 |         event.register(INVENTORY);
   70 |         event.register(JUMP);
   71 |         event.register(ROTATE_LEFT);
   72 |         event.register(ROTATE_RIGHT);
   73 |     }
```

<a id="e02"></a>
## E02 — 能力选择写入后没有被目标查询/执行读取；两套目标状态

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientTacticalPlan.java`：L14–L96

```text
   14 | /** Executes only the current server waypoint through the ordinary local player input/tick chain. */
   15 | public final class ClientTacticalPlan {
   16 |     private static TacticalNetwork.Projection projection;
   17 |     private static long sequence = -1;
   18 |     private static UUID requested;
   19 |     private static TacticalIntent.Capability capability = TacticalIntent.Capability.MOVE;
   20 |     private static boolean endAfterCancel;
   21 |     private static UUID queryId;
   22 |     private static TacticalNetwork.Options options;
   23 |     private static TacticalIntent.Hand hand = TacticalIntent.Hand.MAIN_HAND;
   24 |     public static java.util.List<TacticalNetwork.Offer> offers() { return options == null ? java.util.List.of() : options.offers(); }
   25 |     public static void toggleHand() { hand = hand == TacticalIntent.Hand.MAIN_HAND ? TacticalIntent.Hand.OFF_HAND : TacticalIntent.Hand.MAIN_HAND; options = null; }
   26 |     public static void receiveOptions(TacticalNetwork.Options value, IPayloadContext context) {
   27 |         var connection = context.connection(); var level = Minecraft.getInstance().level; var player = Minecraft.getInstance().player;
   28 |         context.enqueueWork(() -> {
   29 |             var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter();
   30 |             if (state == null || mc.level != level || mc.player != player || mc.getConnection() == null || mc.getConnection().getConnection() != connection
   31 |                 || !state.generation().equals(value.generation()) || !state.encounterId().equals(value.encounter()) || !value.query().equals(queryId)) return;
   32 |             options = value; reason = value.reason().isEmpty() ? "选择服务器提供的行为" : value.reason();
   33 |         });
   34 |     }
   35 |     public static void choose(TacticalNetwork.Offer offer) {
   36 |         var state = ClientCombatState.encounter();
   37 |         if (state == null || options == null || running() || !offer.reason().isEmpty() || !options.offers().contains(offer)) return;
   38 |         requested = UUID.randomUUID();
   39 |         ClientPacketDistributor.sendToServer(new TacticalNetwork.Request(state.generation(), state.encounterId(), requested, options.version(), offer.intent(), false));
   40 |         options = null; reason = "等待服务器验证";
   41 |     }
   42 | 
   43 |     private static String reason = "右键选择目的地";
   44 |     private ClientTacticalPlan() {}
   45 |     public static void reset() { queryId = null; options = null; hand = TacticalIntent.Hand.MAIN_HAND; projection = null; sequence = -1; requested = null; endAfterCancel = false; capability = TacticalIntent.Capability.MOVE; reason = "右键选择目的地"; }
   46 |     public static void select(TacticalIntent.Capability value) { capability = value; }
   47 |     public static TacticalIntent.Capability capability() { return capability; }
   48 |     public static String description() { return hand + " · " + reason; }
   49 |     public static void receive(TacticalNetwork.Projection state, IPayloadContext context) {
   50 |         var connection = context.connection();
   51 |         var level = Minecraft.getInstance().level;
   52 |         var player = Minecraft.getInstance().player;
   53 |         context.enqueueWork(() -> {
   54 |             var mc = Minecraft.getInstance();
   55 |             var encounter = ClientCombatState.encounter();
   56 |             if (mc.level != level || mc.player != player || mc.getConnection() == null
   57 |                 || mc.getConnection().getConnection() != connection || encounter == null
   58 |                 || !encounter.generation().equals(state.generation()) || !encounter.encounterId().equals(state.encounter())
   59 |                 || state.sequence() <= sequence || requested == null || !requested.equals(state.operation())) return;
   60 |             sequence = state.sequence(); projection = state; reason = state.reason();
   61 |             requested = state.running() ? state.operation() : null;
   62 |             if (!state.running() && endAfterCancel) {
   63 |                 endAfterCancel = false;
   64 |                 CombatControls.requestFromUi(CombatNetwork.IntentKind.END_TURN, null);
   65 |             }
   66 |         });
   67 |     }
   68 |     public static boolean running() { return requested != null; }
   69 |     public static void endTurn() {
   70 |         if (!running()) { CombatControls.requestFromUi(CombatNetwork.IntentKind.END_TURN, null); return; }
   71 |         endAfterCancel = true; cancel();
   72 |     }
   73 |     public static void cancel() {
   74 |         var state = ClientCombatState.encounter();
   75 |         if (requested != null && state != null)
   76 |             ClientPacketDistributor.sendToServer(new TacticalNetwork.Request(state.generation(), state.encounterId(), requested,
   77 |                 state.version(), null, true));
   78 |     }
   79 |     public static void target(UUID entity, net.minecraft.world.phys.BlockHitResult hit) {
   80 |         var mc = Minecraft.getInstance();
   81 |         var state = ClientCombatState.encounter();
   82 |         if (state == null || mc.player == null || running()) return;
   83 |         String dimension = mc.level.dimension().identifier().toString();
   84 |         TacticalIntent.Target target;
   85 |         if (entity != null) target = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, dimension, entity, null, -1, 0, 0, 0);
   86 |         else if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
   87 |             target = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF, dimension, null, null, -1, 0, 0, 0);
   88 |         else {
   89 |             BlockPos pos = hit.getBlockPos(); Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
   90 |             target = new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK, dimension, null, new GridCell(pos.getX(), pos.getY(), pos.getZ()),
   91 |                 hit.getDirection().ordinal(), Math.clamp(local.x, 0, 1), Math.clamp(local.y, 0, 1), Math.clamp(local.z, 0, 1));
   92 |         }
   93 |         queryId = UUID.randomUUID(); options = null;
   94 |         ClientPacketDistributor.sendToServer(new TacticalNetwork.Query(state.generation(), state.encounterId(), queryId, target, hand));
   95 |         reason = "查询可用行为";
   96 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalOverlay.java`：L127–L198

```text
  127 |     private static void bind() {
  128 |         refreshGeneration = document.getRefreshGeneration();
  129 |         renderedOffers = List.of(); behaviorButtons.clear();
  130 |         values.clear(); members.clear(); memberLabels.clear(); memberFaces.clear(); logs.clear(); memberOrder = List.of();
  131 |         java.util.Arrays.fill(inventory, null);
  132 |         ability("move", cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE, "移动按实际获准位移计费；再次点击停止。结束回合会先结算移动。");
  133 |         ability("attack", cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK, "消耗动作。d20＋5 对目标 AC；自然 1 未命中，自然 20 暴击。选择邻近目标后攻击。");
  134 |         action("dash", CombatNetwork.IntentKind.DASH, "消耗动作，增加本会话捕获的一份基础移动预算。");
  135 |         action("dodge", CombatNetwork.IntentKind.DODGE, "消耗动作，直到下次自身回合开始前，对你的命中检定有劣势。");
  136 |         action("disengage", CombatNetwork.IntentKind.DISENGAGE, "消耗动作，设置本回合撤离状态。借机攻击流程尚未开放。");
  137 |         action("end", CombatNetwork.IntentKind.END_TURN, "先结束并结算正在进行的移动，然后请求结束回合。");
  138 |         action("exit", CombatNetwork.IntentKind.EXIT, "先让角色中心离开场地再退出；场内请求会被拒绝。有其他玩家留场时只移除你，其余玩家继续战斗。");
  139 |         ability("place", cc.sighs.dndturn.combat.TacticalIntent.Capability.PLACE, "选择放置面；原版接受放置后消耗一次动作。");
  140 |         ability("use-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_BLOCK, "使用方块自身，不消耗动作；接近仍消耗移动。");
  141 |         ability("break-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.BREAK, "选择要破坏的方块。");
  142 |         ability("use-item", cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_ITEM, "使用当前物品。");
  143 |         document.getElementById("behavior-hand").addEventListener("click", event -> { ClientTacticalPlan.toggleHand(); ClientTacticalPlan.target(null, null); });
  144 |         document.getElementById("cancel-plan").addEventListener("click", event -> ClientTacticalPlan.cancel());
  145 |         document.getElementById("inventory-toggle").addEventListener("click", event -> toggleInventory());
  146 |         attribute("inventory-panel", "style", inventoryOpen ? "display: block" : "display: none");
  147 |         Element slots = document.getElementById("inventory");
  148 |         for (int i = 0; i < inventory.length; i++) {
  149 |             Element slot = document.createElement("slot");
  150 |             slot.setAttribute("class", "inventory-slot");
  151 |             final int selectedSlot = i;
  152 |             slot.addEventListener("click", event -> {
  153 |                 var player = Minecraft.getInstance().player;
  154 |                 if (player == null || selectedSlot > 8) return;
  155 |                 player.getInventory().setSelectedSlot(selectedSlot);
  156 |                 player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(selectedSlot));
  157 |                 ClientTacticalPlan.target(null, null);
  158 |             });
  159 |             Element item = document.createElement("item");
  160 |             item.setAttribute("id", "inventory-" + i);
  161 |             slot.appendChild(item);
  162 |             slots.appendChild(slot);
  163 |         }
  164 |         document.getElementById("pointer").addEventListener("click", event -> togglePointer());
  165 |     }
  166 | 
  167 |     private static List<cc.sighs.dndturn.combat.TacticalNetwork.Offer> renderedOffers = List.of();
  168 |     private static final java.util.List<Element> behaviorButtons = new java.util.ArrayList<>();
  169 |     private static void renderBehaviors() {
  170 |         var offers = ClientTacticalPlan.offers();
  171 |         if (offers.equals(renderedOffers)) return;
  172 |         var host = document.getElementById("behavior-options");
  173 |         for (var button : behaviorButtons) host.removeChild(button);
  174 |         behaviorButtons.clear();
  175 |         for (var offer : offers) {
  176 |             var button = document.createElement("button");
  177 |             button.setTextContent(offer.label() + (offer.reason().isEmpty() ? "" : " · " + offer.reason()));
  178 |             if (!offer.reason().isEmpty()) button.setAttribute("disabled", "");
  179 |             button.addEventListener("click", event -> ClientTacticalPlan.choose(offer));
  180 |             host.appendChild(button);
  181 |             behaviorButtons.add(button);
  182 |         }
  183 |         renderedOffers = offers;
  184 |     }
  185 |     private static void ability(String id, cc.sighs.dndturn.combat.TacticalIntent.Capability capability, String description) {
  186 |         var element = document.getElementById(id);
  187 |         element.addEventListener("click", event -> { if (!"false".equals(values.get(id + ":enabled"))) ClientTacticalPlan.select(capability); if (capability == cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_ITEM) ClientTacticalPlan.target(null, null); });
  188 |         element.addEventListener("mouseenter", event -> text("description", description));
  189 |     }
  190 | 
  191 |     private static void action(String id, CombatNetwork.IntentKind kind, String description) {
  192 |         Element element = document.getElementById(id);
  193 |         element.addEventListener("click", event -> {
  194 |             if (!"true".equals(values.get(id + ":enabled"))) return;
  195 |             CombatControls.requestFromUi(kind, kind == CombatNetwork.IntentKind.ATTACK ? selectedTarget : null);
  196 |         });
  197 |         element.addEventListener("mouseenter", event -> text("description", description));
  198 |         enabled(id, true);
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L333–L360

```text
  333 |     private static void select(boolean execute) {
  334 |         var mc = Minecraft.getInstance();
  335 |         Camera camera = mc.gameRenderer.getMainCamera();
  336 |         if (!camera(camera)) return;
  337 |         double x = mc.mouseHandler.xpos() / mc.getWindow().getScreenWidth() * 2 - 1;
  338 |         double y = 1 - mc.mouseHandler.ypos() / mc.getWindow().getScreenHeight() * 2;
  339 |         double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
  340 |         Vector3f ray = new Vector3f((float)(x * tan * mc.getWindow().getScreenWidth() / mc.getWindow().getScreenHeight()),
  341 |             (float)(y * tan), -1).rotate(camera.rotation()).normalize();
  342 |         Vec3 direction = new Vec3(ray.x, ray.y, ray.z);
  343 |         double range = 0;
  344 |         while (range < 64 && mc.level.hasChunkAt(BlockPos.containing(position.add(direction.scale(range + 1))))) range++;
  345 |         Vec3 end = position.add(direction.scale(range));
  346 |         var block = mc.level.clip(new ClipContext(position, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, mc.player));
  347 |         double nearest = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(position);
  348 |         java.util.UUID selected = null;
  349 |         for (var member : ClientCombatState.encounter().members()) {
  350 |             Entity entity = null;
  351 |             for (Entity candidate : mc.level.entitiesForRendering()) if (candidate.getUUID().equals(member.id())) { entity = candidate; break; }
  352 |             if (entity == null || entity == mc.player || !entity.isAlive()) continue;
  353 |             var hit = entity.getBoundingBox().inflate(.1).clip(position, end);
  354 |             if (hit.isPresent() && hit.get().distanceToSqr(position) < nearest) {
  355 |                 nearest = hit.get().distanceToSqr(position); selected = entity.getUUID();
  356 |             }
  357 |         }
  358 |         TacticalOverlay.select(selected);
  359 |         if (execute) ClientTacticalPlan.target(selected, block);
  360 |     }
```

<a id="e03"></a>
## E03 — 热栏键在原版消费槽位切换前查询，Query 不包含所选槽位

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java`：L109–L145

```text
  109 |     /** Called after vanilla KeyMapping.set/click, before the next client input tick. */
  110 |     public static void onKeyInput(InputEvent.Key event) {
  111 |         if (!ClientControl.key(event.getKeyEvent(), event.getAction())) return;
  112 |         Minecraft game = Minecraft.getInstance();
  113 |         if (event.getAction() == GLFW.GLFW_RELEASE && JUMP.matches(event.getKeyEvent())) releaseJump();
  114 |         if (game.player == null || game.screen != null) return;
  115 |         boolean tactical = ClientCombatState.encounter() != null;
  116 |         if (tactical && event.getAction() == GLFW.GLFW_PRESS) {
  117 |             for (int i = 0; i < game.options.keyHotbarSlots.length; i++)
  118 |                 if (game.options.keyHotbarSlots[i].matches(event.getKeyEvent()))
  119 |                     ClientTacticalPlan.target(null, null);
  120 |         }
  121 |         boolean toggle = START.matches(event.getKeyEvent()) && START.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()));
  122 |         if (!tactical && !toggle) return;
  123 |         boolean handled = toggle;
  124 |         if (tactical) for (KeyMapping mapping : new KeyMapping[]{UI, EXIT, MOVE, ATTACK, DASH, DODGE,
  125 |                 DISENGAGE, END_TURN, INVENTORY, JUMP, ROTATE_LEFT, ROTATE_RIGHT}) {
  126 |             if (mapping.matches(event.getKeyEvent())) handled = true;
  127 |         }
  128 |         if (!handled) return;
  129 |         KeyMapping winner = null;
  130 |         for (KeyMapping mapping : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
  131 |                 DISENGAGE, END_TURN, INVENTORY, JUMP, ROTATE_LEFT, ROTATE_RIGHT}) {
  132 |             if (!mapping.matches(event.getKeyEvent()) || !mapping.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()))) continue;
  133 |             if (winner == null) winner = mapping;
  134 |             else clear(mapping);
  135 |         }
  136 |         // Consume only overlapping vanilla bindings; preserve the user's saved key configuration.
  137 |         for (KeyMapping mapping : game.options.keyMappings)
  138 |             if (!isAction(mapping) && mapping.matches(event.getKeyEvent())) clear(mapping);
  139 |         if (toggle) clear(END_TURN);
  140 |         if (tactical && winner == JUMP && !ClientControl.blockMovement()) {
  141 |             game.options.keyJump.setDown(event.getAction() != GLFW.GLFW_RELEASE);
  142 |         }
  143 |         if (event.getAction() == GLFW.GLFW_REPEAT)
  144 |             for (KeyMapping mapping : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
  145 |                     DISENGAGE, END_TURN, INVENTORY}) while (mapping.consumeClick()) { }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalNetwork.java`：L12–L47

```text
   12 | /** Bounded value messages; no world objects, costs, damage or trusted actor from C2S. */
   13 | public final class TacticalNetwork {
   14 |     private static final Gson JSON = new Gson();
   15 |     private TacticalNetwork() {}
   16 |     private static <T> StreamCodec<ByteBuf, T> codec(Class<T> type) {
   17 |         var text = ByteBufCodecs.stringUtf8(32768);
   18 |         return StreamCodec.of((buffer, value) -> text.encode(buffer, JSON.toJson(value)),
   19 |             buffer -> Objects.requireNonNull(JSON.fromJson(text.decode(buffer), type)));
   20 |     }
   21 |     public record Query(UUID generation, UUID encounter, UUID query, TacticalIntent.Target target,
   22 |                         TacticalIntent.Hand hand) implements CustomPacketPayload {
   23 |         public Query { Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(query); Objects.requireNonNull(target); Objects.requireNonNull(hand); }
   24 |         public static final Type<Query> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "behavior_query"));
   25 |         public static final StreamCodec<ByteBuf, Query> CODEC = codec(Query.class);
   26 |         public Type<? extends CustomPacketPayload> type() { return TYPE; }
   27 |     }
   28 |     public record Offer(String label, TacticalIntent intent, String reason) {
   29 |         public Offer { Objects.requireNonNull(label); Objects.requireNonNull(intent); Objects.requireNonNull(reason); }
   30 |     }
   31 |     public record Options(UUID generation, UUID encounter, UUID query, long version, java.util.List<Offer> offers,
   32 |                           String reason) implements CustomPacketPayload {
   33 |         public Options { offers = java.util.List.copyOf(offers); Objects.requireNonNull(reason); }
   34 |         public static final Type<Options> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "behavior_options"));
   35 |         public static final StreamCodec<ByteBuf, Options> CODEC = codec(Options.class);
   36 |         public Type<? extends CustomPacketPayload> type() { return TYPE; }
   37 |     }
   38 |     public record Request(UUID generation, UUID encounter, UUID operation, long version,
   39 |                           TacticalIntent intent, boolean cancel) implements CustomPacketPayload {
   40 |         public Request {
   41 |             Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(operation);
   42 |             if (version < 0 || !cancel && intent == null || cancel && intent != null)
   43 |                 throw new IllegalArgumentException("invalid plan request");
   44 |         }
   45 |         public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "plan_request"));
   46 |         public static final StreamCodec<ByteBuf, Request> CODEC = codec(Request.class);
   47 |         @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java`：L51–L85

```text
   51 |     public TacticalNetwork.Options discover(ServerPlayer player, TacticalNetwork.Query query) {
   52 |         if (!server.isSameThread()) throw new IllegalStateException("server thread required");
   53 |         var offers = new ArrayList<TacticalNetwork.Offer>();
   54 |         long version = 0;
   55 |         try {
   56 |             if (!service.matchesGeneration(query.generation()) || !query.encounter().equals(service.encounterOf(player.getUUID()))
   57 |                 || !service.mayOrganizeInventory(player)) throw new IllegalStateException("not an interactive member turn");
   58 |             var state = engine.stateView(query.encounter()); version = state.version();
   59 |             var selected = query.target();
   60 |             for (var adapter : TacticalCapabilities.all()) {
   61 |                 TacticalIntent.Target target = selected;
   62 |                 if (adapter.targets().contains(TacticalIntent.TargetKind.SELF))
   63 |                     target = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF, selected.dimension(), null, null, -1, 0, 0, 0);
   64 |                 else if (selected.kind() == TacticalIntent.TargetKind.BLOCK && adapter.targets().contains(TacticalIntent.TargetKind.GROUND)) {
   65 |                     BlockPos adjacent = pos(selected.cell()).relative(Direction.values()[selected.face()]);
   66 |                     target = new TacticalIntent.Target(TacticalIntent.TargetKind.GROUND, selected.dimension(), null, cell(adjacent), -1, 0, 0, 0);
   67 |                 }
   68 |                 if (!adapter.targets().contains(target.kind())) continue;
   69 |                 var hand = InteractionHand.valueOf(query.hand().name());
   70 |                 var item = new TacticalIntent.ItemReference(hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40,
   71 |                     TacticalItems.revision(player, player.getItemInHand(hand)));
   72 |                 var intent = new TacticalIntent(adapter.id(), adapter.version(), query.hand(), adapter.cost(), target, item);
   73 |                 String reason = "";
   74 |                 try {
   75 |                     validate(player, intent, state);
   76 |                     if (intent.requiresAction() && !state.members().get(player.getUUID()).action()) reason = "action unavailable";
   77 |                     else if (!adapter.canExecute(player, intent, player.position()) && state.members().get(player.getUUID()).movementTicks() == 0) reason = "movement budget exhausted";
   78 |                 } catch (RuntimeException rejected) { reason = rejected.getMessage() == null ? "behavior unavailable" : rejected.getMessage(); }
   79 |                 offers.add(new TacticalNetwork.Offer(adapter.label(), intent, reason));
   80 |             }
   81 |             return new TacticalNetwork.Options(service.generation(), query.encounter(), query.query(), version, offers, "");
   82 |         } catch (RuntimeException failure) {
   83 |             return new TacticalNetwork.Options(service.generation(), query.encounter(), query.query(), version, List.of(),
   84 |                 failure.getMessage() == null ? "discovery unavailable" : failure.getMessage());
   85 |         }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalOverlay.java`：L147–L158

```text
  147 |         Element slots = document.getElementById("inventory");
  148 |         for (int i = 0; i < inventory.length; i++) {
  149 |             Element slot = document.createElement("slot");
  150 |             slot.setAttribute("class", "inventory-slot");
  151 |             final int selectedSlot = i;
  152 |             slot.addEventListener("click", event -> {
  153 |                 var player = Minecraft.getInstance().player;
  154 |                 if (player == null || selectedSlot > 8) return;
  155 |                 player.getInventory().setSelectedSlot(selectedSlot);
  156 |                 player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(selectedSlot));
  157 |                 ClientTacticalPlan.target(null, null);
  158 |             });
```

<a id="e04"></a>
## E04 — 取消只处理 requested；世界右键不是按状态取消；新会话重置遗漏计划状态

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientTacticalPlan.java`：L35–L96

```text
   35 |     public static void choose(TacticalNetwork.Offer offer) {
   36 |         var state = ClientCombatState.encounter();
   37 |         if (state == null || options == null || running() || !offer.reason().isEmpty() || !options.offers().contains(offer)) return;
   38 |         requested = UUID.randomUUID();
   39 |         ClientPacketDistributor.sendToServer(new TacticalNetwork.Request(state.generation(), state.encounterId(), requested, options.version(), offer.intent(), false));
   40 |         options = null; reason = "等待服务器验证";
   41 |     }
   42 | 
   43 |     private static String reason = "右键选择目的地";
   44 |     private ClientTacticalPlan() {}
   45 |     public static void reset() { queryId = null; options = null; hand = TacticalIntent.Hand.MAIN_HAND; projection = null; sequence = -1; requested = null; endAfterCancel = false; capability = TacticalIntent.Capability.MOVE; reason = "右键选择目的地"; }
   46 |     public static void select(TacticalIntent.Capability value) { capability = value; }
   47 |     public static TacticalIntent.Capability capability() { return capability; }
   48 |     public static String description() { return hand + " · " + reason; }
   49 |     public static void receive(TacticalNetwork.Projection state, IPayloadContext context) {
   50 |         var connection = context.connection();
   51 |         var level = Minecraft.getInstance().level;
   52 |         var player = Minecraft.getInstance().player;
   53 |         context.enqueueWork(() -> {
   54 |             var mc = Minecraft.getInstance();
   55 |             var encounter = ClientCombatState.encounter();
   56 |             if (mc.level != level || mc.player != player || mc.getConnection() == null
   57 |                 || mc.getConnection().getConnection() != connection || encounter == null
   58 |                 || !encounter.generation().equals(state.generation()) || !encounter.encounterId().equals(state.encounter())
   59 |                 || state.sequence() <= sequence || requested == null || !requested.equals(state.operation())) return;
   60 |             sequence = state.sequence(); projection = state; reason = state.reason();
   61 |             requested = state.running() ? state.operation() : null;
   62 |             if (!state.running() && endAfterCancel) {
   63 |                 endAfterCancel = false;
   64 |                 CombatControls.requestFromUi(CombatNetwork.IntentKind.END_TURN, null);
   65 |             }
   66 |         });
   67 |     }
   68 |     public static boolean running() { return requested != null; }
   69 |     public static void endTurn() {
   70 |         if (!running()) { CombatControls.requestFromUi(CombatNetwork.IntentKind.END_TURN, null); return; }
   71 |         endAfterCancel = true; cancel();
   72 |     }
   73 |     public static void cancel() {
   74 |         var state = ClientCombatState.encounter();
   75 |         if (requested != null && state != null)
   76 |             ClientPacketDistributor.sendToServer(new TacticalNetwork.Request(state.generation(), state.encounterId(), requested,
   77 |                 state.version(), null, true));
   78 |     }
   79 |     public static void target(UUID entity, net.minecraft.world.phys.BlockHitResult hit) {
   80 |         var mc = Minecraft.getInstance();
   81 |         var state = ClientCombatState.encounter();
   82 |         if (state == null || mc.player == null || running()) return;
   83 |         String dimension = mc.level.dimension().identifier().toString();
   84 |         TacticalIntent.Target target;
   85 |         if (entity != null) target = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, dimension, entity, null, -1, 0, 0, 0);
   86 |         else if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
   87 |             target = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF, dimension, null, null, -1, 0, 0, 0);
   88 |         else {
   89 |             BlockPos pos = hit.getBlockPos(); Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
   90 |             target = new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK, dimension, null, new GridCell(pos.getX(), pos.getY(), pos.getZ()),
   91 |                 hit.getDirection().ordinal(), Math.clamp(local.x, 0, 1), Math.clamp(local.y, 0, 1), Math.clamp(local.z, 0, 1));
   92 |         }
   93 |         queryId = UUID.randomUUID(); options = null;
   94 |         ClientPacketDistributor.sendToServer(new TacticalNetwork.Query(state.generation(), state.encounterId(), queryId, target, hand));
   95 |         reason = "查询可用行为";
   96 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L179–L214

```text
  179 |     public static void beginMouse(int button, int action) {
  180 |         reconcile();
  181 |         if (action == GLFW.GLFW_PRESS) {
  182 |             Recipient who = recipient;
  183 |             Document doc = null;
  184 |             if (who == Recipient.CONSENT) doc = ConsentOverlay.document();
  185 |             else if (who == Recipient.CAMERA && TacticalOverlay.hit(mousePosition())) {
  186 |                 who = Recipient.TACTICAL; doc = TacticalOverlay.document();
  187 |             }
  188 |             else if ((who == Recipient.CAMERA || who == Recipient.CHARACTER) && CombatControls.hasMouseBinding(button))
  189 |                 who = Recipient.BINDING;
  190 |             gestures.put(button, new Gesture(who, doc, revision));
  191 |         }
  192 |         dispatch = gestures.get(button);
  193 |     }
  194 |     public static boolean endMouse(int button, int action, boolean consumed) {
  195 |         Gesture gesture = dispatch;
  196 |         dispatch = null;
  197 |         if (action == GLFW.GLFW_RELEASE) gestures.remove(button);
  198 |         if (gesture == null) return consumed || session && recipient != Recipient.SCREEN || modal();
  199 |         boolean ours = gesture.owner != Recipient.GAME && gesture.owner != Recipient.SCREEN;
  200 |         boolean binding = false;
  201 |         if (gesture.revision == revision && (gesture.owner == Recipient.CAMERA || gesture.owner == Recipient.CHARACTER || gesture.owner == Recipient.BINDING)
  202 |             && !consumed) {
  203 |             binding = CombatControls.mouseBinding(button, action);
  204 |             InputConstants.Key code = InputConstants.Type.MOUSE.getOrCreate(button);
  205 |             for (KeyMapping key : movementKeys()) if (key.getKey().equals(code)) {
  206 |                 if (action == GLFW.GLFW_RELEASE) cameraKeys.remove(key);
  207 |                 else if (gesture.owner == Recipient.CAMERA && !binding) cameraKeys.add(key);
  208 |                 binding = true;
  209 |             }
  210 |         }
  211 |         if (action == GLFW.GLFW_RELEASE && gesture.owner == Recipient.CAMERA && gesture.revision == revision
  212 |             && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
  213 |             && !consumed && !binding) select(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
  214 |         return consumed || ours;
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientCombatState.java`：L88–L110

```text
   88 |     public static void receiveEncounter(CombatNetwork.EncounterState state, IPayloadContext context) {
   89 |         PacketOrigin origin = captureOrigin(context);
   90 |         context.enqueueWork(() -> {
   91 |             refreshSession();
   92 |             if (!acceptsOrigin(origin)) return;
   93 |             var update = EncounterProjectionOrder.advance(
   94 |                 lastEncounter == null ? null : stamp(lastEncounter),
   95 |                 nextResultIndex, resyncPending, stamp(state));
   96 |             var decision = update.decision();
   97 |             if (decision == EncounterProjectionOrder.Decision.IGNORE) return;
   98 |             nextResultIndex = update.nextResultIndex();
   99 |             resyncPending = update.resyncPending();
  100 |             if (decision == EncounterProjectionOrder.Decision.ACCEPT_RESET_RESULTS) {
  101 |                 latestResult = null;
  102 |                 results.clear();
  103 |                 latestStatus = null;
  104 |                 CombatControls.resetForNewEncounter();
  105 |             }
  106 |             expectedResultCount = state.resultCount();
  107 |             lastEncounter = state;
  108 |             encounter = state.active() ? state : null;
  109 |             CombatControls.onEncounterState(state);
  110 |         });
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java`：L267–L285

```text
  267 |     public static void reset() {
  268 |         releaseJump();
  269 |         movePhase = MovePhase.IDLE;
  270 |         movementOperationId = null;
  271 |         movementEndVersion = -1;
  272 |         endTurnAfterMove = false;
  273 |         exitRetryPending = false;
  274 |         exitAfterStart = false;
  275 |         startOperationId = null;
  276 |     }
  277 | 
  278 |     public static void resetForNewEncounter() {
  279 |         boolean exitRequested = exitAfterStart;
  280 |         reset();
  281 |         exitAfterStart = exitRequested;
  282 |     }
  283 | 
  284 |     public static void onEncounterState(CombatNetwork.EncounterState state) {
  285 |         if (!state.active()) { reset(); return; }
```

<a id="e05"></a>
## E05 — 新计划后端的授权、接近、执行和玩家输入接入

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java`：L87–L169

```text
   87 |     public void request(ServerPlayer player, TacticalNetwork.Request request) {
   88 |         if (!server.isSameThread()) throw new IllegalStateException("server thread required");
   89 |         boolean created = false;
   90 |         boolean mayRecordRejection = false;
   91 |         try {
   92 |             if (!service.matchesGeneration(request.generation())) throw new IllegalStateException("expired session");
   93 |             var completed = engine.resultFor(request.encounter(), request.operation());
   94 |             var root = completed == null ? engine.pendingOperation(request.encounter(), request.operation()) : completed.snapshot();
   95 |             if (root != null) {
   96 |                 if (root.kind() != OperationRecord.Kind.PLAN || !root.owner().equals(player.getUUID())
   97 |                     || !request.cancel() && (!Objects.equals(root.intent(), request.intent()) || root.encounterVersion() != request.version()))
   98 |                     throw new IllegalStateException("operation payload conflict");
   99 |                 if (completed != null) { send(player, root, null, false, completed.reason()); return; }
  100 |                 if (request.cancel()) { cancel(player.getUUID(), "cancelled by player"); return; }
  101 |                 Execution existing = executions.get(player.getUUID());
  102 |                 send(player, root, existing == null ? null : waypoint(existing), true, "already accepted");
  103 |                 return;
  104 |             }
  105 |             if (request.cancel()) throw new IllegalStateException("unknown plan");
  106 |             mayRecordRejection = request.encounter().equals(service.encounterOf(player.getUUID()));
  107 |             if (!request.encounter().equals(service.encounterOf(player.getUUID())) || !service.mayOrganizeInventory(player))
  108 |                 throw new IllegalStateException("not an interactive member turn");
  109 |             var state = engine.stateView(request.encounter());
  110 |             if (state.version() != request.version()) throw new IllegalStateException("stale encounter version");
  111 |             TacticalIntent intent = request.intent();
  112 |             validate(player, intent, state);
  113 |             GridCell target = targetCell(player, intent);
  114 |             List<GridCell> path = path(player, intent, state, target);
  115 |             if (!path.isEmpty() && state.members().get(player.getUUID()).movementTicks() < 1)
  116 |                 throw new IllegalStateException("movement budget exhausted");
  117 |             root = new OperationRecord.Snapshot(request.operation(), null, state.id(), player.getUUID(), player.getUUID(),
  118 |                 intent.target().entity(), service.planClock(), state.version(), cell(player.blockPosition()), target,
  119 |                 OperationRecord.Kind.PLAN, service.generation(), intent);
  120 |             if (!engine.beginOperation(root)) throw new IllegalStateException("action unavailable or another plan pending");
  121 |             Execution execution = new Execution(root, path, target,
  122 |                 intent.target().kind() == TacticalIntent.TargetKind.BLOCK ? player.level().getBlockState(pos(target)).toString() : "",
  123 |                 player.position());
  124 |             executions.put(player.getUUID(), execution);
  125 |             created = true;
  126 |             if (!path.isEmpty()) {
  127 |                 execution.movement = UUID.randomUUID();
  128 |                 service.beginPlanMovement(player, root.operationId(), execution.movement);
  129 |                 send(player, root, waypoint(execution), true, "approaching");
  130 |             } else execute(player, execution);
  131 |             service.resyncMember(player, state.id());
  132 |         } catch (RuntimeException failure) {
  133 |             com.mojang.logging.LogUtils.getLogger().debug("Tactical request rejected: {}", failure.toString());
  134 |             Execution execution = executions.get(player.getUUID());
  135 |             if (created && execution != null && execution.root.operationId().equals(request.operation())) {
  136 |                 if (execution.action != null) finishAction(player, execution, OperationRecord.Outcome.UNKNOWN, "behavior start outcome uncertain");
  137 |                 else cancel(player.getUUID(), failure.getMessage());
  138 |             }
  139 |             else {
  140 |                 String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  141 |                 if (mayRecordRejection) {
  142 |                     var intent = request.intent();
  143 |                     var rejected = new OperationRecord.Snapshot(request.operation(), null, request.encounter(), player.getUUID(), player.getUUID(),
  144 |                         intent.target().entity(), service.planClock(), request.version(), cell(player.blockPosition()), intent.target().cell(),
  145 |                         OperationRecord.Kind.PLAN, service.generation(), intent);
  146 |                     engine.rejectPlan(rejected, reason);
  147 |                     service.resyncMember(player, request.encounter());
  148 |                 }
  149 |                 send(player, request.encounter(), request.operation(), null, false, reason);
  150 |             }
  151 |         }
  152 |     }
  153 |     private void validate(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
  154 |         if (!intent.target().dimension().equals(player.level().dimension().identifier().toString()))
  155 |             throw new IllegalStateException("target dimension changed");
  156 |         if (intent.item() != null) {
  157 |             int slot = intent.item().slot();
  158 |             if (slot != (intent.hand() == TacticalIntent.Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40)) throw new IllegalStateException("equip selected item first");
  159 |             Execution active = executions.get(player.getUUID());
  160 |             String expected = active != null && active.runningItemRevision != null ? active.runningItemRevision : intent.item().revision();
  161 |             if (!expected.equals(TacticalItems.revision(player, TacticalBehavior.stack(player, intent))))
  162 |                 throw new IllegalStateException("item stack changed");
  163 |         }
  164 |         TacticalCapabilities.resolve(intent).validate(player, intent, state);
  165 |         GridCell target = targetCell(player, intent);
  166 |         if (target != null && (!player.level().hasChunkAt(pos(target))
  167 |             || !state.region().containsPoint(target.x() + .5, target.y() + .5, target.z() + .5)))
  168 |             throw new IllegalStateException("target outside loaded encounter");
  169 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java`：L170–L288

```text
  170 |     private List<GridCell> path(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state, GridCell target) {
  171 |         if (canExecute(player, intent, player.position())) return List.of();
  172 |         if (target == null) throw new IllegalStateException("target required");
  173 |         GridCell start = cell(player.blockPosition());
  174 |         MinecraftCellProbe probe = new MinecraftCellProbe(player.level(), player, state.region());
  175 |         List<GridCell> goals = new ArrayList<>();
  176 |         int radius = TacticalCapabilities.resolve(intent).approachRadius();
  177 |         for (int x = -radius; x <= radius; x++) for (int y = -1; y <= 1; y++) for (int z = -radius; z <= radius; z++) {
  178 |             if (radius == 0 && y != 0) continue;
  179 |             GridCell goal = new GridCell(target.x() + x, target.y() + y, target.z() + z);
  180 |             if (probe.canOccupy(goal) && canExecute(player, intent, point(goal))) goals.add(goal);
  181 |         }
  182 |         var proposal = TacticalPlanner.propose(start, Set.copyOf(goals), 128, 4096, state.region().version(), probe);
  183 |         if (proposal.cost() != Integer.MAX_VALUE && TacticalPlanner.revalidate(start, proposal, state.region().version(), probe))
  184 |             return proposal.cells();
  185 |         throw new IllegalStateException("no supported path to execution position");
  186 |     }
  187 |     private boolean canExecute(ServerPlayer player, TacticalIntent intent, Vec3 feet) {
  188 |         return TacticalCapabilities.resolve(intent).canExecute(player, intent, feet);
  189 |     }
  190 |     public boolean allowsMove(ServerPlayer player, Vec3 wanted) {
  191 |         Execution execution = executions.get(player.getUUID());
  192 |         if (execution == null) return true;
  193 |         GridCell next = waypoint(execution);
  194 |         if (next == null) return false;
  195 |         Vec3 end = point(next), start = execution.cursor == 0 ? point(execution.root.sourceCell()) : point(execution.path.get(execution.cursor - 1));
  196 |         Vec3 edge = end.subtract(start);
  197 |         double t = Math.max(0, Math.min(1, wanted.subtract(start).dot(edge) / Math.max(edge.lengthSqr(), .0001)));
  198 |         return wanted.distanceToSqr(start.add(edge.scale(t))) <= .81;
  199 |     }
  200 |     public void tick() {
  201 |         if (server.tickRateManager().isFrozen()) return;
  202 |         for (UUID owner : List.copyOf(containers.keySet())) {
  203 |             ServerPlayer viewer = server.getPlayerList().getPlayer(owner);
  204 |             if (viewer == null || !mayUseContainer(viewer)) {
  205 |                 var permit = containers.remove(owner);
  206 |                 if (viewer != null && viewer.containerMenu.containerId == permit.menu()) viewer.closeContainer();
  207 |             }
  208 |         }
  209 |         for (Execution execution : List.copyOf(executions.values())) {
  210 |             ServerPlayer player = server.getPlayerList().getPlayer(execution.root.owner());
  211 |             if (player == null) for (var level : server.getAllLevels()) {
  212 |                 if (level.getEntity(execution.root.owner()) instanceof ServerPlayer found) { player = found; break; }
  213 |             }
  214 |             if (player == null) { cancel(execution.root.owner(), "player unavailable"); continue; }
  215 |             try {
  216 |                 if (!execution.root.encounterId().equals(service.encounterOf(player.getUUID())) || !player.isAlive())
  217 |                     throw new IllegalStateException("plan owner left");
  218 |                 if (engine.pendingOperation(execution.root.encounterId(), execution.root.operationId()) == null)
  219 |                     throw new IllegalStateException("plan no longer pending");
  220 |                 if (execution.action != null) { tickAction(player, execution); continue; }
  221 |                 if (execution.movement == null) continue;
  222 |                 var intent = execution.root.intent();
  223 |                 validate(player, intent, engine.stateView(execution.root.encounterId()));
  224 |                 if (!Objects.equals(execution.targetCell, targetCell(player, intent))) throw new IllegalStateException("target moved");
  225 |                 GridCell next = waypoint(execution);
  226 |                 if (next != null && player.position().distanceToSqr(point(next)) < .16) {
  227 |                     execution.cursor++;
  228 |                     next = waypoint(execution);
  229 |                     send(player, execution.root, next, true, "approaching");
  230 |                 }
  231 |                 if (next == null) {
  232 |                     service.finishPlanMovement(player);
  233 |                     execution.movement = null;
  234 |                     execute(player, execution);
  235 |                 } else {
  236 |                     if (!service.hasPlayerMoveLease(player.getUUID())) throw new IllegalStateException("movement budget exhausted");
  237 |                     var probe = new MinecraftCellProbe(player.level(), player, engine.stateView(execution.root.encounterId()).region());
  238 |                     GridCell current = cell(player.blockPosition());
  239 |                     if (!probe.canOccupy(next) || !current.equals(next) && probe.traversalCost(current, next) <= 0)
  240 |                         throw new IllegalStateException("path obstructed");
  241 |                     execution.stalled = player.position().distanceToSqr(execution.previous) < .0001 ? execution.stalled + 1 : 0;
  242 |                     execution.previous = player.position();
  243 |                     if (execution.stalled >= 100) throw new IllegalStateException("movement stalled");
  244 |                 }
  245 |             } catch (RuntimeException error) {
  246 |                 if (execution.action != null) finishAction(player, execution, OperationRecord.Outcome.UNKNOWN, "behavior step outcome uncertain: " + error.getMessage());
  247 |                 else cancel(execution.root.owner(), error.getMessage());
  248 |             }
  249 |         }
  250 |     }
  251 |     private void execute(ServerPlayer player, Execution execution) {
  252 |         var root = execution.root;
  253 |         var intent = root.intent();
  254 |         validate(player, intent, engine.stateView(root.encounterId()));
  255 |         if (!canExecute(player, intent, player.position())) throw new IllegalStateException("execution position invalidated");
  256 |         if (!execution.blockState.isEmpty() && !execution.blockState.equals(player.level().getBlockState(pos(execution.targetCell)).toString()))
  257 |             throw new IllegalStateException("target block changed");
  258 |         TacticalCapabilities.resolve(intent).prepare(this, player, execution);
  259 |         TacticalCapabilities.resolve(intent).start(this, player, execution);
  260 |     }
  261 |     private void tickAction(ServerPlayer player, Execution execution) {
  262 |         var intent = execution.root.intent();
  263 |         validate(player, intent, engine.stateView(execution.root.encounterId()));
  264 |         if (!canExecute(player, intent, player.position())) throw new IllegalStateException("execution target out of reach");
  265 |         TacticalCapabilities.resolve(intent).tick(this, player, execution);
  266 |     }
  267 |     public void beginStep(ServerPlayer player, Execution execution) {
  268 |         var root = execution.root;
  269 |         var snapshot = new OperationRecord.Snapshot(UUID.randomUUID(), root.operationId(), root.encounterId(), player.getUUID(), player.getUUID(),
  270 |             root.target(), service.planClock(), engine.stateView(root.encounterId()).version(), cell(player.blockPosition()), execution.targetCell,
  271 |             root.intent().executionKind(), service.generation());
  272 |         if (!engine.beginPlanStep(snapshot)) throw new IllegalStateException("execution step rejected");
  273 |         execution.action = snapshot.operationId();
  274 |     }
  275 |     public void accept(ServerPlayer player, Execution execution, String reason) {
  276 |         engine.publish(execution.root.encounterId(), execution.action, execution.actionStep++, OperationRecord.Outcome.ACCEPTED, reason, 0, 0, false);
  277 |         send(player, execution.root, null, true, reason);
  278 |     }
  279 |     public void finishAction(ServerPlayer player, Execution execution, OperationRecord.Outcome outcome, String reason) {
  280 |         try { TacticalCapabilities.resolve(execution.root.intent()).cancel(this, player, execution); }
  281 |         catch (RuntimeException failure) {
  282 |             player.stopUsingItem(); outcome = OperationRecord.Outcome.UNKNOWN;
  283 |             reason = "behavior cleanup failed: " + failure.getClass().getSimpleName();
  284 |         }
  285 |         engine.publish(execution.root.encounterId(), execution.action, execution.actionStep++, outcome, reason, 0, 0, true);
  286 |         execution.action = null;
  287 |         VanillaInputPolicy.correctInventory(player);
  288 |         finish(player, execution, outcome, reason);
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientTacticalPlan.java`：L97–L115

```text
   97 |     public record Steering(Input keys, Vec2 vector) {}
   98 |     public static Steering steering() {
   99 |         var mc = Minecraft.getInstance();
  100 |         var state = ClientCombatState.encounter();
  101 |         if (projection == null || !projection.running() || projection.waypoint() == null || mc.player == null
  102 |             || state == null || !state.encounterId().equals(projection.encounter()) || !ClientCombatState.movementAllowed()) return null;
  103 |         if (mc.screen != null || !mc.isWindowActive() || ClientControl.modal()) return new Steering(Input.EMPTY, Vec2.ZERO);
  104 |         GridCell cell = projection.waypoint();
  105 |         Vec3 delta = new Vec3(cell.x() + .5, cell.y(), cell.z() + .5).subtract(mc.player.position());
  106 |         if (delta.lengthSqr() < .16) return new Steering(Input.EMPTY, Vec2.ZERO);
  107 |         double yaw = Math.toRadians(mc.player.getYRot());
  108 |         double forward = -Math.sin(yaw) * delta.x + Math.cos(yaw) * delta.z;
  109 |         double left = Math.cos(yaw) * delta.x + Math.sin(yaw) * delta.z;
  110 |         double length = Math.max(.001, Math.hypot(forward, left));
  111 |         float scale = (float)Math.min(1, delta.horizontalDistance() * 3);
  112 |         boolean jump = delta.y > .5 && mc.player.onGround();
  113 |         return new Steering(new Input(forward > .01, forward < -.01, left > .01, left < -.01, jump, false, false),
  114 |             new Vec2((float)(left / length) * scale, (float)(forward / length) * scale));
  115 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalKeyboardInputMixin.java`：L1–L21

```text
    1 | package cc.sighs.dndturn.mixin.client;
    2 | 
    3 | import cc.sighs.dndturn.client.ClientControl;
    4 | import net.minecraft.client.player.ClientInput;
    5 | import net.minecraft.client.player.KeyboardInput;
    6 | import net.minecraft.world.entity.player.Input;
    7 | import net.minecraft.world.phys.Vec2;
    8 | import org.spongepowered.asm.mixin.Mixin;
    9 | import org.spongepowered.asm.mixin.injection.At;
   10 | import org.spongepowered.asm.mixin.injection.Inject;
   11 | import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
   12 | 
   13 | @Mixin(KeyboardInput.class)
   14 | public abstract class TacticalKeyboardInputMixin extends ClientInput {
   15 |     @Inject(method = "tick", at = @At("TAIL"))
   16 |     private void dndturn$input(CallbackInfo ci) {
   17 |         var steering = cc.sighs.dndturn.client.ClientTacticalPlan.steering();
   18 |         if (steering != null) { keyPresses = steering.keys(); moveVector = steering.vector(); return; }
   19 |         if (ClientControl.blockMovement()) { keyPresses = Input.EMPTY; moveVector = Vec2.ZERO; }
   20 |     }
   21 | }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L76–L92

```text
   76 |     private TacticalActions tacticalActions;
   77 |     public TacticalActions tacticalActions() {
   78 |         requireThread();
   79 |         if (tacticalActions == null) tacticalActions = new TacticalActions(this, server, engine);
   80 |         return tacticalActions;
   81 |     }
   82 |     long planClock() { return cumulativeServerTicks; }
   83 |     void finishPlanMovement(ServerPlayer player) { finishPlayerMove(player); }
   84 |     void finishPlanMovement(UUID owner) { var lease = playerMoves.get(owner); if (lease != null) closePlayerMove(lease); }
   85 |     void beginPlanMovement(ServerPlayer player, UUID parent, UUID operation) {
   86 |         UUID id = engine.encounterOf(player.getUUID());
   87 |         var snapshot = new OperationRecord.Snapshot(operation, parent, id, player.getUUID(), player.getUUID(), null,
   88 |             cumulativeServerTicks, engine.stateView(id).version(), null, null, OperationRecord.Kind.MOVE, generation());
   89 |         if (unsupportedPlayerMovement(player) || !engine.beginPlanStep(snapshot)) throw new IllegalStateException("plan movement rejected");
   90 |         playerMoves.put(player.getUUID(), new PlayerMoveLease(id, operation, player.getUUID()));
   91 |         sync(engine.stateView(id)); syncBodyStateTransitions();
   92 |     }
```

<a id="e06"></a>
## E06 — 根计划死亡结算顺序回归

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java`：L41–L58

```text
   41 |     static final class Melee extends TacticalBehavior {
   42 |         Melee() { super("dndturn:melee", "近战攻击", TacticalIntent.Capability.ATTACK, Set.of(TacticalIntent.TargetKind.ENTITY)); }
   43 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
   44 |             if (i.hand() != TacticalIntent.Hand.MAIN_HAND) return "melee requires main hand";
   45 |             var stack = stack(p, i);
   46 |             if (stack.getItem() instanceof ProjectileWeaponItem || stack.is(Items.SNOWBALL)) return "item has a ranged adapter; no melee fallback";
   47 |             // Generic attack contract permits ordinary equipment; custom side effects need explicit registration.
   48 |             if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")) return "custom attack effects require an adapter";
   49 |             return attackTarget(p, i, s);
   50 |         }
   51 |         public boolean canExecute(ServerPlayer p, TacticalIntent i, Vec3 feet) {
   52 |             return cell(BlockPos.containing(feet)).chebyshev(targetCell(p, i)) <= 1 && super.canExecute(p, i, feet);
   53 |         }
   54 |         public void start(TacticalActions a, ServerPlayer p, Execution e) {
   55 |             var result = a.service.attackPlan(p, e.root.target(), UUID.randomUUID(), e.root.operationId());
   56 |             a.finish(p, e, result.outcome(), result.reason());
   57 |         }
   58 |         public void tick(TacticalActions a, ServerPlayer p, Execution e) { throw new IllegalStateException("melee already terminal"); }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L423–L436

```text
  423 |     public void noteDeath(UUID entityId) {
  424 |         requireThread();
  425 |         if (engine.encounterOf(entityId) != null) pendingDeaths.add(entityId);
  426 |     }
  427 | 
  428 |     public void confirmPendingDeaths() {
  429 |         requireThread();
  430 |         if (worldEffectDepth != 0) return;
  431 |         for (UUID entityId : Set.copyOf(pendingDeaths)) {
  432 |             pendingDeaths.remove(entityId);
  433 |             Entity entity = resolve(entityId);
  434 |             if (entity instanceof LivingEntity living
  435 |                 && ((LivingEntityDeathAccessor) living).dndturn$isDead()) leave(entityId);
  436 |         }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L1969–L2008

```text
 1969 |             engine.publish(encounterId, damageId, 0, effectOutcome,
 1970 |                 "vanilla hurtServer=" + accepted + " absorptionLoss=" + absorptionLoss
 1971 |                     + " healthLoss=" + healthLoss, 0, healthLoss, true);
 1972 |             return engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
 1973 |                 "attack hit: die=" + roll.die() + " effect=" + effectOutcome, 0, healthLoss, true, trace);
 1974 |         } catch (RuntimeException error) {
 1975 |             if (engine.resultFor(encounterId, damageId) == null)
 1976 |                 engine.publish(encounterId, damageId, 0, OperationRecord.Outcome.UNKNOWN,
 1977 |                     "world damage outcome unknown: " + error.getClass().getSimpleName(), 0, 0, true);
 1978 |             if (engine.resultFor(encounterId, operationId) == null)
 1979 |                 return engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.UNKNOWN,
 1980 |                     "world damage outcome unknown", 0, 0, true);
 1981 |             return engine.resultFor(encounterId, operationId);
 1982 |         } finally {
 1983 |             worldEffectDepth--;
 1984 |             confirmPendingDeaths();
 1985 |             for (UUID departed : Set.copyOf(departuresDuringWorldEffect)) {
 1986 |                 departuresDuringWorldEffect.remove(departed);
 1987 |                 leave(departed);
 1988 |             }
 1989 |             if (engine.encounterIds().contains(encounterId)) sync(engine.stateView(encounterId));
 1990 |         }
 1991 |         } catch (RuntimeException error) {
 1992 |             OperationRecord.Result known = engine.resultFor(encounterId, operationId);
 1993 |             if (known != null) return known;
 1994 |             if (!engine.encounterIds().contains(encounterId)) throw error;
 1995 |             OperationRecord.Outcome outcome = effectStarted
 1996 |                 ? OperationRecord.Outcome.UNKNOWN : OperationRecord.Outcome.REJECTED;
 1997 |             if (damageId != null && engine.pendingOperation(encounterId, damageId) != null)
 1998 |                 engine.publish(encounterId, damageId, 0, outcome,
 1999 |                     effectStarted ? "damage effect outcome unknown" : "damage preparation failed", 0, 0, true);
 2000 |             if (permitId != null && !effectStarted) engine.revokeEffectPermit(encounterId, permitId);
 2001 |             if (engine.pendingOperation(encounterId, operationId) != null) {
 2002 |                 OperationRecord.Result failed = engine.publish(encounterId, operationId, 0, outcome,
 2003 |                     (effectStarted ? "attack effect outcome unknown: " : "attack preparation failed: ")
 2004 |                         + error.getClass().getSimpleName(), 0, 0, true);
 2005 |                 sync(engine.stateView(encounterId));
 2006 |                 return failed;
 2007 |             }
 2008 |             throw error;
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L2112–L2147

```text
 2112 |     public void leave(UUID entityId) {
 2113 |         requireThread();
 2114 |         if (worldEffectDepth > 0) {
 2115 |             if (engine.encounterOf(entityId) != null) departuresDuringWorldEffect.add(entityId);
 2116 |             return;
 2117 |         }
 2118 |         UUID encounterId = engine.encounterOf(entityId);
 2119 |         if (encounterId == null) return;
 2120 |         if (tacticalActions != null) tacticalActions.cancel(entityId, "member left");
 2121 |         subscriptions.leave(encounterId, entityId);
 2122 |         PlayerMoveLease playerLease = playerMoves.get(entityId);
 2123 |         if (playerLease != null) closePlayerMove(playerLease);
 2124 |         ServerPlayer departingPlayer = server.getPlayerList().getPlayer(entityId);
 2125 |         MobMoveLease mobLease = mobMoves.get(entityId);
 2126 |         if (mobLease != null) closeMobMove(mobLease, "member left after observed movement");
 2127 |         engine.leave(encounterId, entityId);
 2128 |         revokeEndedProjectileDomains();
 2129 |         forcedMovements.remove(entityId);
 2130 |         CombatEngine.View closed = engine.closedView(encounterId);
 2131 |         if (closed != null) {
 2132 |             regions.remove(encounterId);
 2133 |             capturedSettings.remove(encounterId);
 2134 |             regionChunks.remove(encounterId);
 2135 |             subscriptions.end(encounterId);
 2136 |                     discardMergesContaining(encounterId);
 2137 |             mergeFailures.remove(encounterId);
 2138 |             gameTestAttackRandom.remove(encounterId);
 2139 |             scheduledTicks.releaseEncounter(encounterId);
 2140 |             if (departingPlayer != null) clear(departingPlayer, encounterId, closed.version());
 2141 |             for (UUID member : closed.members().keySet()) clear(member, encounterId, closed.version());
 2142 |         } else {
 2143 |             CombatEngine.StateView state = engine.stateView(encounterId);
 2144 |             clear(entityId, encounterId, state.version());
 2145 |             sync(state);
 2146 |         }
 2147 |         syncBodyStateTransitions();
```

### `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`：L500–L538

```text
  500 |     public boolean leave(UUID encounterId, UUID memberId) {
  501 |         Objects.requireNonNull(memberId);
  502 |         Encounter encounter = require(encounterId);
  503 |         if (!encounter.members.containsKey(memberId)) return false;
  504 |         Math.addExact(encounter.version, (long) encounter.pending.size() + 1);
  505 |         long nextStructuralRevision = Math.addExact(encounter.structuralRevision, 1);
  506 |         for (OperationRecord.Snapshot snapshot : pendingChildrenFirst(encounter)) {
  507 |             OperationRecord.Snapshot root = encounter.causes.get(rootOf(encounter, snapshot));
  508 |             if (!memberId.equals(snapshot.owner()) && !memberId.equals(snapshot.target())
  509 |                 && (root == null || !memberId.equals(root.target()))) continue;
  510 |             int step = encounter.stepResults.getOrDefault(snapshot.operationId(), Map.of()).size();
  511 |             publish(encounterId, snapshot.operationId(), step, OperationRecord.Outcome.UNKNOWN,
  512 |                 "member left before world outcome was confirmed", 0, 0, true);
  513 |         }
  514 |         int index = encounter.order.indexOf(memberId);
  515 |         boolean wasCurrent = index == encounter.cursor && encounter.phase != EncounterPhase.ENVIRONMENT;
  516 |         encounter.members.remove(memberId);
  517 |         membership.remove(memberId);
  518 |         encounter.structuralRevision = nextStructuralRevision;
  519 |         encounter.hostile.removeIf(relation -> relation.source().equals(memberId) || relation.target().equals(memberId));
  520 |         // A later join with the same UUID is a new membership, not the old target scope.
  521 |         encounter.permits.values().removeIf(state -> state.permit.targets().contains(memberId));
  522 |         if (index >= 0) {
  523 |             encounter.order.remove(index);
  524 |             if (index < encounter.cursor) encounter.cursor--;
  525 |         }
  526 |         if (encounter.members.isEmpty()) {
  527 |             end(encounterId);
  528 |         } else if (encounter.phase == EncounterPhase.ENVIRONMENT) {
  529 |             // Roster changes do not start a new simulation cycle.
  530 |             encounter.version++;
  531 |         } else if (encounter.order.isEmpty() || encounter.cursor >= encounter.order.size()) {
  532 |             enterEnvironment(encounter);
  533 |             encounter.version++;
  534 |         } else {
  535 |             if (wasCurrent) beginMemberTurn(encounter, encounter.members.get(encounter.order.get(encounter.cursor)));
  536 |             encounter.version++;
  537 |         }
  538 |         return true;
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java`：L316–L328

```text
  316 |     void finish(ServerPlayer player, Execution execution, OperationRecord.Outcome outcome, String reason) {
  317 |         var root = execution.root;
  318 |         if (engine.encounterIds().contains(root.encounterId()) && engine.pendingOperation(root.encounterId(), root.operationId()) != null)
  319 |             engine.publish(root.encounterId(), root.operationId(), 0, outcome, reason, 0, 0, true);
  320 |         executions.remove(root.owner());
  321 |         if (player != null) { service.resyncMember(player, root.encounterId()); send(player, root, null, false, reason); }
  322 |     }
  323 |     void send(ServerPlayer player, OperationRecord.Snapshot root, GridCell point, boolean running, String reason) {
  324 |         send(player, root.encounterId(), root.operationId(), point, running, reason);
  325 |     }
  326 |     private void send(ServerPlayer player, UUID encounter, UUID operation, GridCell point, boolean running, String reason) {
  327 |         if (NetworkRegistry.hasChannel(player.connection, TacticalNetwork.Projection.TYPE.id()))
  328 |             PacketDistributor.sendToPlayer(player, new TacticalNetwork.Projection(service.generation(), encounter, operation, ++sequence, point, running, reason));
```

<a id="e07"></a>
## E07 — 点击目标授权不等于最终放置影响范围授权

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java`：L153–L168

```text
  153 |     private void validate(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
  154 |         if (!intent.target().dimension().equals(player.level().dimension().identifier().toString()))
  155 |             throw new IllegalStateException("target dimension changed");
  156 |         if (intent.item() != null) {
  157 |             int slot = intent.item().slot();
  158 |             if (slot != (intent.hand() == TacticalIntent.Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40)) throw new IllegalStateException("equip selected item first");
  159 |             Execution active = executions.get(player.getUUID());
  160 |             String expected = active != null && active.runningItemRevision != null ? active.runningItemRevision : intent.item().revision();
  161 |             if (!expected.equals(TacticalItems.revision(player, TacticalBehavior.stack(player, intent))))
  162 |                 throw new IllegalStateException("item stack changed");
  163 |         }
  164 |         TacticalCapabilities.resolve(intent).validate(player, intent, state);
  165 |         GridCell target = targetCell(player, intent);
  166 |         if (target != null && (!player.level().hasChunkAt(pos(target))
  167 |             || !state.region().containsPoint(target.x() + .5, target.y() + .5, target.z() + .5)))
  168 |             throw new IllegalStateException("target outside loaded encounter");
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalBehavior.java`：L51–L59

```text
   51 |     public void prepare(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution) {
   52 |         var intent = execution.root.intent();
   53 |         if (intent.target().cell() != null) {
   54 |             BlockPos pos = TacticalActions.pos(intent.target().cell());
   55 |             if (player.level().getServer().isUnderSpawnProtection(player.level(), pos, player) || !player.level().mayInteract(player, pos))
   56 |                 throw new IllegalStateException("protected block");
   57 |         }
   58 |         aim(player, intent);
   59 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java`：L85–L105

```text
   85 |     static final class BlockUse extends Interaction {
   86 |         BlockUse() { super("dndturn:block", "方块自身交互", TacticalIntent.Capability.USE_BLOCK, TacticalIntent.TargetKind.BLOCK); }
   87 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
   88 |             return i.hand() == TacticalIntent.Hand.MAIN_HAND ? null : "block-only vanilla branch requires main hand";
   89 |         }
   90 |         protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
   91 |             try (var context = new TacticalUseContext(p, pos(i.target().cell()), true)) {
   92 |                 return p.gameMode.useItemOn(p, p.level(), stack(p, i), hand(i), hit(i));
   93 |             }
   94 |         }
   95 |     }
   96 |     static final class ItemOnBlock extends Interaction {
   97 |         private final Predicate<ItemStack> supports;
   98 |         ItemOnBlock(String id, String label, TacticalIntent.Capability cost, Predicate<ItemStack> supports) {
   99 |             super(id, label, cost, TacticalIntent.TargetKind.BLOCK); this.supports = supports;
  100 |         }
  101 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) { return supports.test(stack(p, i)) ? null : "item-on-block contract unavailable"; }
  102 |         protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
  103 |             try (var context = new TacticalUseContext(p, pos(i.target().cell()), false)) {
  104 |                 return p.gameMode.useItemOn(p, p.level(), stack(p, i), hand(i), hit(i));
  105 |             }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java`：L115–L133

```text
  115 |     static final class Bucket extends Interaction {
  116 |         Bucket() { super("dndturn:bucket", "桶 / 流体", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.BLOCK); }
  117 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
  118 |             var stack = stack(p, i);
  119 |             if (!stack.is(Items.BUCKET) && !stack.is(Items.WATER_BUCKET) && !stack.is(Items.LAVA_BUCKET)) return "ordinary fluid bucket required; entity buckets need an adapter";
  120 |             BlockPos adjacent = pos(i.target().cell()).relative(Direction.values()[i.target().face()]);
  121 |             if (!p.level().hasChunkAt(adjacent) || !s.region().containsPoint(adjacent.getX()+.5, adjacent.getY()+.5, adjacent.getZ()+.5)) return "bucket effect outside loaded encounter";
  122 |             if (p.level().getServer().isUnderSpawnProtection(p.level(), adjacent, p) || !p.level().mayInteract(p, adjacent)) return "protected fluid destination";
  123 |             return null;
  124 |         }
  125 |         protected ClipContext.Fluid fluids(ServerPlayer p, TacticalIntent i) { return stack(p, i).is(Items.BUCKET) ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE; }
  126 |         public void prepare(TacticalActions a, ServerPlayer p, Execution e) {
  127 |             super.prepare(a, p, e);
  128 |             var i = e.root.intent(); var eye = p.getEyePosition();
  129 |             var actual = p.level().clip(new ClipContext(eye, eye.add(p.calculateViewVector(p.getXRot(), p.getYRot()).scale(p.blockInteractionRange())), ClipContext.Block.OUTLINE, fluids(p, i), p));
  130 |             if (actual.getType() != HitResult.Type.BLOCK || !actual.getBlockPos().equals(pos(i.target().cell())) || actual.getDirection().ordinal() != i.target().face())
  131 |                 throw new IllegalStateException("bucket vanilla ray no longer matches target and face: " + actual.getType() + " " + actual.getBlockPos() + " " + actual.getDirection() + " eye=" + eye + " view=" + p.getViewVector(1));
  132 |         }
  133 |         protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) { return p.gameMode.useItem(p, p.level(), stack(p, i), hand(i)); }
```

<a id="e08"></a>
## E08 — 旧公开攻击/移动意图仍可直达服务，绕过新行为解析链

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatIntentHandler.java`：L97–L128

```text
   97 |             if (!intent.encounterId().equals(service.encounterOf(player.getUUID())))
   98 |                 throw new IllegalStateException("combat session is no longer active");
   99 |             switch (intent.kind()) {
  100 |                 case EXIT -> {
  101 |                     service.exitEncounter(player, intent.encounterId(), intent.operationId(),
  102 |                         intent.expectedVersion());
  103 |                 }
  104 |                 case MOVE_BEGIN -> service.beginPlayerMove(player, intent.operationId(),
  105 |                     intent.expectedVersion());
  106 |                 case MOVE_END -> {
  107 |                     var result = service.finishPlayerMove(player, intent.operationId(),
  108 |                         intent.expectedVersion());
  109 |                     player.sendOverlayMessage(Component.literal("DNDTurn: " + result.reason()));
  110 |                 }
  111 |                 case ATTACK -> {
  112 |                     if (intent.targetId() == null) throw new IllegalStateException("no target under crosshair");
  113 |                     var result = service.attack(player, intent.targetId(), intent.operationId(),
  114 |                         intent.expectedVersion());
  115 |                     player.sendOverlayMessage(Component.literal("DNDTurn: " + result.reason()));
  116 |                 }
  117 |                 case END_TURN -> service.endTurn(intent.encounterId(), player, intent.operationId(),
  118 |                     intent.expectedVersion());
  119 |                 case DASH -> {
  120 |                     if (intent.targetId() != null) throw new IllegalStateException("DASH does not accept a target");
  121 |                     var result = service.dash(player, intent.operationId(), intent.expectedVersion());
  122 |                     player.sendOverlayMessage(Component.literal("DNDTurn: " + result.reason()));
  123 |                 }
  124 |                 case DODGE, DISENGAGE -> {
  125 |                     if (intent.targetId() != null) throw new IllegalStateException(intent.kind() + " does not accept a target");
  126 |                     OperationRecord.Kind kind = intent.kind() == IntentKind.DODGE
  127 |                         ? OperationRecord.Kind.DODGE : OperationRecord.Kind.DISENGAGE;
  128 |                     var result = service.defensiveAction(player, intent.operationId(),
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L1842–L1907

```text
 1842 |     public OperationRecord.Result attack(LivingEntity actor, UUID targetId,
 1843 |                                                                   UUID operationId, long expectedVersion) {
 1844 |         return attack(actor, targetId, operationId, expectedVersion, null);
 1845 |     }
 1846 |     OperationRecord.Result attackPlan(ServerPlayer actor, UUID target, UUID operation, UUID parent) {
 1847 |         return attack(actor, target, operation, engine.stateView(engine.encounterOf(actor.getUUID())).version(), parent);
 1848 |     }
 1849 |     private OperationRecord.Result attack(LivingEntity actor, UUID targetId,
 1850 |                                           UUID operationId, long expectedVersion, UUID planParent) {
 1851 |         requireThread();
 1852 |         Objects.requireNonNull(targetId);
 1853 |         Objects.requireNonNull(operationId);
 1854 |         UUID encounterId = engine.encounterOf(actor.getUUID());
 1855 |         if (encounterId == null) throw new IllegalStateException("attacker is not a member");
 1856 |         OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
 1857 |         if (previous != null) {
 1858 |             OperationRecord.Snapshot snapshot = previous.snapshot();
 1859 |             if (snapshot.kind() != OperationRecord.Kind.ATTACK || !snapshot.owner().equals(actor.getUUID())
 1860 |                 || !targetId.equals(snapshot.target()) || snapshot.encounterVersion() != expectedVersion)
 1861 |                 throw new IllegalStateException("operation ID payload conflict");
 1862 |             return previous;
 1863 |         }
 1864 |         CombatEngine.StateView state = engine.stateView(encounterId);
 1865 |         if (state.version() != expectedVersion) throw new IllegalStateException("stale encounter version");
 1866 |         if (state.phase() != EncounterPhase.CANDIDATE && state.phase() != EncounterPhase.ACTIVE)
 1867 |             throw new IllegalStateException("not a member attack phase");
 1868 |         if (!actor.getUUID().equals(state.current()) || !state.members().containsKey(targetId))
 1869 |             throw new IllegalStateException("attacker or target not authorized");
 1870 |         if (!(actor.level() instanceof ServerLevel level) || actor.isDeadOrDying())
 1871 |             throw new IllegalStateException("attacker is not a loaded living member");
 1872 |         Entity found = level.getEntity(targetId);
 1873 |         if (!(found instanceof LivingEntity target)
 1874 |             || !(actor instanceof ServerPlayer && found.getType() == EntityType.ZOMBIE
 1875 |                 || actor instanceof Zombie && found instanceof ServerPlayer)
 1876 |             || target.isDeadOrDying())
 1877 |             throw new IllegalStateException("only Player/Zombie melee is supported");
 1878 |         Vec3 actorCenter = actor.getBoundingBox().getCenter();
 1879 |         Vec3 targetCenter = target.getBoundingBox().getCenter();
 1880 |         if (!state.region().dimension().equals(level.dimension().identifier().toString())
 1881 |             || !state.region().containsPoint(actorCenter.x, actorCenter.y, actorCenter.z)
 1882 |             || !state.region().containsPoint(targetCenter.x, targetCenter.y, targetCenter.z)
 1883 |             || !inMeleeReach(actor, target)) throw new IllegalStateException("target is outside supported melee reach");
 1884 |         boolean openingAdvantage = actor instanceof ServerPlayer && target instanceof Zombie zombie
 1885 |             && !engine.hasAttemptedAttack(encounterId, actor.getUUID())
 1886 |             && zombie.getTarget() != actor;
 1887 |         double weaponAttribute = actor instanceof ServerPlayer
 1888 |             ? actor.getMainHandItem().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
 1889 |                 .compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND)
 1890 |             : actor.getAttributeValue(Attributes.ATTACK_DAMAGE);
 1891 |         double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
 1892 |         if (!Double.isFinite(weaponAttribute) || !Double.isFinite(toughness)
 1893 |             || weaponAttribute > Integer.MAX_VALUE || toughness > Integer.MAX_VALUE)
 1894 |             throw new IllegalStateException("unsupported weapon or toughness attribute");
 1895 |         double weaponDamage = Math.max(0, weaponAttribute);
 1896 |         int reduction = CombatRules.damageReduction(toughness);
 1897 |         CombatRules.damageAfterReduction(weaponDamage, reduction, true);
 1898 |         boolean holdingShield = target.getMainHandItem().is(Items.SHIELD)
 1899 |             || target.getOffhandItem().is(Items.SHIELD);
 1900 |         int ac = CombatRules.armorClass(target.getArmorValue(), holdingShield);
 1901 |         var origin = actor.blockPosition();
 1902 |         var destination = target.blockPosition();
 1903 |         OperationRecord.Snapshot root = operationSnapshot(operationId, planParent,
 1904 |             encounterId, actor.getUUID(), actor.getUUID(), targetId, cumulativeServerTicks, expectedVersion,
 1905 |             new GridCell(origin.getX(), origin.getY(), origin.getZ()),
 1906 |             new GridCell(destination.getX(), destination.getY(), destination.getZ()), OperationRecord.Kind.ATTACK);
 1907 |         if (!(planParent == null ? engine.beginOperation(root) : engine.beginPlanStep(root))) throw new IllegalStateException("attack already executing or unauthorized");
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java`：L253–L265

```text
  253 |     /** AUI and key bindings share the same pending movement/stop state machine. */
  254 |     public static void requestFromUi(CombatNetwork.IntentKind kind, UUID target) {
  255 |         Minecraft game = Minecraft.getInstance();
  256 |         if (game.player == null || game.getConnection() == null || game.screen != null) return;
  257 |         switch (kind) {
  258 |             case MOVE_BEGIN -> requestMove();
  259 |             case END_TURN -> requestEndTurn();
  260 |             case EXIT -> requestExit();
  261 |             case ATTACK -> send(kind, target);
  262 |             case DASH, DODGE, DISENGAGE -> send(kind, null);
  263 |             default -> throw new IllegalArgumentException("unsupported UI action");
  264 |         }
  265 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java`：L41–L49

```text
   41 |     static final class Melee extends TacticalBehavior {
   42 |         Melee() { super("dndturn:melee", "近战攻击", TacticalIntent.Capability.ATTACK, Set.of(TacticalIntent.TargetKind.ENTITY)); }
   43 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
   44 |             if (i.hand() != TacticalIntent.Hand.MAIN_HAND) return "melee requires main hand";
   45 |             var stack = stack(p, i);
   46 |             if (stack.getItem() instanceof ProjectileWeaponItem || stack.is(Items.SNOWBALL)) return "item has a ranged adapter; no melee fallback";
   47 |             // Generic attack contract permits ordinary equipment; custom side effects need explicit registration.
   48 |             if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")) return "custom attack effects require an adapter";
   49 |             return attackTarget(p, i, s);
```

<a id="e09"></a>
## E09 — 动作提交与恢复：根计划不扣动作，执行子步骤按提交点计费

### `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`：L985–L1099

```text
  985 |     }
  986 | 
  987 |     /** Effect operations must present a server-issued capability. */
  988 |     public boolean beginOperation(OperationRecord.Snapshot snapshot, UUID permitId) {
  989 |         return beginOperation(snapshot, permitId, false);
  990 |     }
  991 | 
  992 |     /** A plan may run one movement step, then its exact selected capability, sequentially. */
  993 |     public boolean beginPlanStep(OperationRecord.Snapshot snapshot) {
  994 |         return beginOperation(snapshot, null, true);
  995 |     }
  996 | 
  997 |     private boolean beginOperation(OperationRecord.Snapshot snapshot, UUID permitId, boolean planStep) {
  998 |         Objects.requireNonNull(snapshot);
  999 |         if (closedEncounters.containsKey(snapshot.encounterId())) {
 1000 |             OperationRecord.Result previousResult = resultFor(snapshot.encounterId(), snapshot.operationId());
 1001 |             if (previousResult == null) throw new IllegalStateException("closed encounter has no such operation");
 1002 |             if (!previousResult.snapshot().equals(snapshot)) throw new IllegalStateException("operation payload conflict");
 1003 |             return false;
 1004 |         }
 1005 |         Encounter encounter = require(snapshot.encounterId());
 1006 |         OperationRecord.Snapshot previous = encounter.causes.get(snapshot.operationId());
 1007 |         if (previous != null) {
 1008 |             if (!previous.equals(snapshot)) throw new IllegalStateException("operation payload conflict");
 1009 |             return false;
 1010 |         }
 1011 |         if (snapshot.encounterVersion() != encounter.version) return false;
 1012 |         OperationRecord.Snapshot plan = planStep ? encounter.pending.get(snapshot.parentId()) : null;
 1013 |         if (planStep) {
 1014 |             if (plan == null || plan.kind() != OperationRecord.Kind.PLAN || permitId != null
 1015 |                 || !plan.owner().equals(snapshot.owner()) || !plan.source().equals(snapshot.source())
 1016 |                 || encounter.pending.size() != 1
 1017 |                 || snapshot.kind() != OperationRecord.Kind.MOVE && snapshot.kind() != plan.intent().executionKind()
 1018 |                 || !Objects.equals(snapshot.target(), snapshot.kind() == OperationRecord.Kind.MOVE ? null : plan.target())
 1019 |                 || snapshot.kind() != OperationRecord.Kind.MOVE && !Objects.equals(snapshot.targetCell(), plan.targetCell()))
 1020 |                 return false;
 1021 |             for (OperationRecord.Snapshot cause : encounter.causes.values()) {
 1022 |                 if (!plan.operationId().equals(cause.parentId())) continue;
 1023 |                 if (cause.kind() != OperationRecord.Kind.MOVE || snapshot.kind() == OperationRecord.Kind.MOVE)
 1024 |                     return false;
 1025 |             }
 1026 |         }
 1027 |         if (snapshot.parentId() != null && !planStep) {
 1028 |             OperationRecord.Snapshot parent = encounter.causes.get(snapshot.parentId());
 1029 |             UUID rootId = parent == null ? null : rootOf(encounter, parent);
 1030 |             PermitState permit = encounter.permits.get(permitId);
 1031 |             boolean damage = parent != null && permit != null && snapshot.kind() == OperationRecord.Kind.DAMAGE
 1032 |                 && (parent.kind() == OperationRecord.Kind.ATTACK || parent.kind() == OperationRecord.Kind.DAMAGE
 1033 |                     || parent.kind() == OperationRecord.Kind.INTERRUPT)
 1034 |                 && snapshot.owner().equals(parent.owner())
 1035 |                 && permit.permit.parentId().equals(snapshot.parentId())
 1036 |                 && permit.permit.source().equals(snapshot.source())
 1037 |                 && permit.permit.targets().contains(snapshot.target())
 1038 |                 && permit.permit.phases().contains(encounter.phase)
 1039 |                 && encounter.round <= permit.effectiveValidThroughRound
 1040 |                 && permit.used < permit.permit.maxUses()
 1041 |                 && encounter.members.containsKey(snapshot.target());
 1042 |             boolean reaction = parent != null && snapshot.kind() == OperationRecord.Kind.INTERRUPT
 1043 |                 && encounter.phase == EncounterPhase.ACTIVE && encounter.pending.containsKey(parent.operationId())
 1044 |                 && (parent.kind() == OperationRecord.Kind.ATTACK || parent.kind() == OperationRecord.Kind.DAMAGE)
 1045 |                 && snapshot.owner().equals(parent.target()) && Objects.equals(snapshot.target(), parent.owner())
 1046 |                 && encounter.members.containsKey(snapshot.owner())
 1047 |                 && snapshot.owner().equals(snapshot.source())
 1048 |                 && encounter.members.get(snapshot.owner()).economy.hasReaction();
 1049 |             if ((!damage && !reaction) || (reaction && permitId != null)
 1050 |                 || childDepth(encounter, parent) >= 8 || rootId == null
 1051 |                 || encounter.childrenByRoot.getOrDefault(rootId, 0) >= 64) return false;
 1052 |             long nextVersion = Math.addExact(encounter.version, 1);
 1053 |             if (reaction) encounter.members.get(snapshot.owner()).economy.spendReaction();
 1054 |             if (damage) permit.used++;
 1055 |             encounter.childrenByRoot.merge(rootId, 1, Integer::sum);
 1056 |             encounter.pending.put(snapshot.operationId(), snapshot);
 1057 |             encounter.causes.put(snapshot.operationId(), snapshot);
 1058 |             encounter.version = nextVersion;
 1059 |             return true;
 1060 |         }
 1061 |         if (permitId != null || !encounter.members.containsKey(snapshot.owner())) return false;
 1062 |         if (encounter.phase != EncounterPhase.ACTIVE && encounter.phase != EncounterPhase.CANDIDATE) return false;
 1063 |         if (encounter.phase == EncounterPhase.CANDIDATE && snapshot.kind() != OperationRecord.Kind.ATTACK
 1064 |             && snapshot.kind() != OperationRecord.Kind.END_TURN && snapshot.kind() != OperationRecord.Kind.MOVE
 1065 |             && snapshot.kind() != OperationRecord.Kind.PLAN && !planStep) return false;
 1066 |         if ((!planStep && !encounter.pending.isEmpty()) || snapshot.source() == null || !snapshot.source().equals(snapshot.owner())
 1067 |             || snapshot.kind() == OperationRecord.Kind.DAMAGE || snapshot.kind() == OperationRecord.Kind.INTERRUPT
 1068 |             || snapshot.kind() == OperationRecord.Kind.ENVIRONMENT || snapshot.kind() == OperationRecord.Kind.MERGE
 1069 |             || snapshot.kind() == OperationRecord.Kind.START || snapshot.kind() == OperationRecord.Kind.JOIN) return false;
 1070 |         if (!planStep && (snapshot.kind() == OperationRecord.Kind.PLACE || snapshot.kind() == OperationRecord.Kind.BREAK
 1071 |             || snapshot.kind() == OperationRecord.Kind.USE_ITEM || snapshot.kind() == OperationRecord.Kind.USE_BLOCK)) return false;
 1072 |         UUID current = encounter.order.get(encounter.cursor);
 1073 |         if (!snapshot.owner().equals(current)) return false;
 1074 |         Member member = encounter.members.get(current);
 1075 |         if (snapshot.kind() == OperationRecord.Kind.ATTACK
 1076 |             && (snapshot.target() == null || !encounter.members.containsKey(snapshot.target())
 1077 |                 || snapshot.target().equals(snapshot.owner()))) return false;
 1078 |         if (requiresAction(snapshot.kind()) && !member.economy.hasAction()) return false;
 1079 |         if (snapshot.kind() == OperationRecord.Kind.PLAN && snapshot.intent().requiresAction()
 1080 |             && !member.economy.hasAction()) return false;
 1081 |         if (snapshot.kind() == OperationRecord.Kind.DASH)
 1082 |             Math.addExact(member.economy.movementTicks(), encounter.movementTicksPerTurn);
 1083 |         long nextVersion = Math.addExact(encounter.version, 1);
 1084 |         Map<UUID, InitiativeRoll> initiativeRolls = encounter.phase == EncounterPhase.CANDIDATE
 1085 |             && snapshot.kind() == OperationRecord.Kind.ATTACK ? sampleInitiatives(encounter) : Map.of();
 1086 |         if (requiresAction(snapshot.kind()) && !deferredAction(snapshot.kind())) member.economy.spendAction();
 1087 |         if (snapshot.kind() == OperationRecord.Kind.DODGE) member.dodging = true;
 1088 |         if (snapshot.kind() == OperationRecord.Kind.DISENGAGE) member.disengaged = true;
 1089 |         if (snapshot.kind() == OperationRecord.Kind.DASH) member.economy.addMovement(encounter.movementTicksPerTurn);
 1090 |         if (encounter.phase == EncounterPhase.CANDIDATE && snapshot.kind() == OperationRecord.Kind.ATTACK) {
 1091 |             encounter.hostile.add(new Relation(snapshot.owner(), snapshot.target()));
 1092 |             activate(encounter, initiativeRolls);
 1093 |             if (!snapshot.owner().equals(currentId(encounter)))
 1094 |                 member.preserveSpentActionOnNextTurn = true;
 1095 |         }
 1096 |         encounter.pending.put(snapshot.operationId(), snapshot);
 1097 |         encounter.causes.put(snapshot.operationId(), snapshot);
 1098 |         if (snapshot.kind() == OperationRecord.Kind.ATTACK)
 1099 |             encounter.attackersWithRegisteredAttempt.add(snapshot.owner());
```

### `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`：L1134–L1182

```text
 1134 |     public OperationRecord.Result publish(UUID encounterId, UUID operationId, int step,
 1135 |                                           OperationRecord.Outcome outcome, String reason, int movementTicks, float damage,
 1136 |                                           boolean terminal, DamageTrace trace) {
 1137 |         if (closedEncounters.containsKey(encounterId)) {
 1138 |             for (OperationRecord.Result previous : closedEncounters.get(encounterId).results()) {
 1139 |                 if (previous.snapshot().operationId().equals(operationId) && previous.step() == step)
 1140 |                     return matchingResult(previous, outcome, reason, movementTicks, damage, terminal, trace);
 1141 |             }
 1142 |             throw new IllegalStateException("closed encounter has no such step");
 1143 |         }
 1144 |         Encounter encounter = require(encounterId);
 1145 |         OperationRecord.Result existingStep = encounter.stepResults.getOrDefault(operationId, Map.of()).get(step);
 1146 |         if (existingStep != null) return matchingResult(existingStep, outcome, reason, movementTicks, damage, terminal, trace);
 1147 |         if (encounter.finalResults.containsKey(operationId)) throw new IllegalStateException("operation already terminal");
 1148 |         if (step != encounter.stepResults.getOrDefault(operationId, Map.of()).size())
 1149 |             throw new IllegalStateException("out-of-order operation step");
 1150 |         OperationRecord.Snapshot snapshot = encounter.pending.get(operationId);
 1151 |         if (snapshot == null) throw new IllegalStateException("unregistered operation");
 1152 |         Member member = encounter.members.get(snapshot.owner());
 1153 |         if (movementTicks < 0 || movementTicks > (member == null ? 0 : member.economy.movementTicks()))
 1154 |             throw new IllegalStateException("movement budget exceeded");
 1155 |         boolean finishTurn = terminal && outcome == OperationRecord.Outcome.COMPLETED
 1156 |             && snapshot.kind() == OperationRecord.Kind.END_TURN;
 1157 |         if (finishTurn && (!snapshot.owner().equals(encounter.order.get(encounter.cursor))
 1158 |             || encounter.pending.size() != 1)) throw new IllegalStateException("turn changed during operation");
 1159 |         long nextVersion = Math.addExact(encounter.version, 1);
 1160 |         Map<UUID, InitiativeRoll> rolls = finishTurn && encounter.phase == EncounterPhase.CANDIDATE
 1161 |             && !encounter.hostile.isEmpty() ? sampleInitiatives(encounter) : Map.of();
 1162 |         OperationRecord.Result result = new OperationRecord.Result(snapshot, step, outcome, reason,
 1163 |             movementTicks, damage, nextVersion, terminal, trace);
 1164 |         boolean commitAction = deferredAction(snapshot.kind()) && step == 0
 1165 |             && (outcome == OperationRecord.Outcome.ACCEPTED || outcome == OperationRecord.Outcome.COMPLETED
 1166 |                 || outcome == OperationRecord.Outcome.PARTIAL || outcome == OperationRecord.Outcome.UNKNOWN);
 1167 |         if (commitAction && (member == null || !member.economy.hasAction()))
 1168 |             throw new IllegalStateException("reserved action unavailable");
 1169 |         if (terminal && snapshot.kind() == OperationRecord.Kind.PLAN
 1170 |             && encounter.pending.values().stream().anyMatch(child -> operationId.equals(child.parentId())))
 1171 |             throw new IllegalStateException("plan step still executing");
 1172 |         if (commitAction) member.economy.spendAction();
 1173 |         if (member != null) member.economy.spendMovement(movementTicks);
 1174 |         encounter.version = nextVersion;
 1175 |         encounter.history.add(result);
 1176 |         encounter.stepResults.computeIfAbsent(operationId, ignored -> new HashMap<>()).put(step, result);
 1177 |         if (terminal) {
 1178 |             encounter.pending.remove(operationId);
 1179 |             encounter.finalResults.put(operationId, result);
 1180 |             if (finishTurn) finishMemberTurnState(encounter, rolls);
 1181 |         }
 1182 |         return result;
```

### `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`：L1403–L1425

```text
 1403 |     }
 1404 |     private static List<OperationRecord.Snapshot> pendingChildrenFirst(Encounter encounter) {
 1405 |         return encounter.pending.values().stream()
 1406 |             .sorted(Comparator.comparingInt((OperationRecord.Snapshot value) -> childDepth(encounter, value)).reversed())
 1407 |             .toList();
 1408 |     }
 1409 | 
 1410 |     private static boolean requiresAction(OperationRecord.Kind kind) {
 1411 |         return switch (kind) {
 1412 |             case ATTACK, DASH, DODGE, DISENGAGE, HELP, PLACE, BREAK, USE_ITEM -> true;
 1413 |             case START, JOIN, MERGE, MOVE, END_TURN, DAMAGE, ENVIRONMENT, INTERRUPT, PLAN, USE_BLOCK -> false;
 1414 |         };
 1415 |     }
 1416 |     private static boolean deferredAction(OperationRecord.Kind kind) {
 1417 |         return kind == OperationRecord.Kind.PLACE || kind == OperationRecord.Kind.BREAK
 1418 |             || kind == OperationRecord.Kind.USE_ITEM;
 1419 |     }
 1420 | 
 1421 |     private static int childDepth(Encounter encounter, OperationRecord.Snapshot parent) {
 1422 |         int depth = 1;
 1423 |         while (parent.parentId() != null) {
 1424 |             parent = encounter.causes.get(parent.parentId());
 1425 |             if (parent == null) return Integer.MAX_VALUE;
```

### `common/src/main/java/cc/sighs/dndturn/combat/TacticalIntent.java`：L17–L73

```text
   17 |     public enum Capability { MOVE, ATTACK, PLACE, BREAK, USE_ITEM, USE_BLOCK }
   18 |     public enum TargetKind { ENTITY, BLOCK, GROUND, SELF }
   19 |     public record Target(TargetKind kind, String dimension, UUID entity, GridCell cell,
   20 |                          int face, double x, double y, double z) {
   21 |         public Target {
   22 |             Objects.requireNonNull(kind);
   23 |             Objects.requireNonNull(dimension);
   24 |             if (dimension.isBlank() || dimension.length() > 256 || !Double.isFinite(x)
   25 |                 || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("target values");
   26 |             if ((kind == TargetKind.ENTITY) != (entity != null)
   27 |                 || ((kind == TargetKind.BLOCK || kind == TargetKind.GROUND) != (cell != null))
   28 |                 || (kind == TargetKind.BLOCK ? face < 0 || face > 5 : face != -1))
   29 |                 throw new IllegalArgumentException("target shape");
   30 |             if (kind == TargetKind.BLOCK && (x < 0 || x > 1 || y < 0 || y > 1 || z < 0 || z > 1))
   31 |                 throw new IllegalArgumentException("block hit outside face");
   32 |         }
   33 |     }
   34 |     /** Fingerprint verified against the authoritative revision of the complete stack, not just its item type. */
   35 |     public record ItemReference(int slot, String revision) {
   36 |         public ItemReference {
   37 |             Objects.requireNonNull(revision);
   38 |             if (slot < 0 || slot > 40 || revision.isBlank() || revision.length() > 128)
   39 |                 throw new IllegalArgumentException("item reference");
   40 |         }
   41 |     }
   42 |     public TacticalIntent {
   43 |         Objects.requireNonNull(behaviorId);
   44 |         Objects.requireNonNull(hand);
   45 |         if (!behaviorId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || behaviorId.length() > 128 || behaviorVersion < 1)
   46 |             throw new IllegalArgumentException("behavior contract");
   47 |         Objects.requireNonNull(capability);
   48 |         Objects.requireNonNull(target);
   49 |         boolean valid = switch (capability) {
   50 |             case MOVE -> target.kind() == TargetKind.GROUND;
   51 |             case ATTACK -> target.kind() == TargetKind.ENTITY;
   52 |             case PLACE, BREAK, USE_BLOCK -> target.kind() == TargetKind.BLOCK;
   53 |             case USE_ITEM -> true;
   54 |         };
   55 |         if (!valid || (capability != Capability.MOVE && capability != Capability.USE_BLOCK && item == null))
   56 |             throw new IllegalArgumentException("capability target or item mismatch");
   57 |     }
   58 |     public boolean requiresAction() {
   59 |         return switch (capability) {
   60 |             case MOVE, USE_BLOCK -> false;
   61 |             case ATTACK, PLACE, BREAK, USE_ITEM -> true;
   62 |         };
   63 |     }
   64 |     public OperationRecord.Kind executionKind() {
   65 |         return switch (capability) {
   66 |             case MOVE -> OperationRecord.Kind.MOVE;
   67 |             case ATTACK -> OperationRecord.Kind.ATTACK;
   68 |             case PLACE -> OperationRecord.Kind.PLACE;
   69 |             case BREAK -> OperationRecord.Kind.BREAK;
   70 |             case USE_ITEM -> OperationRecord.Kind.USE_ITEM;
   71 |             case USE_BLOCK -> OperationRecord.Kind.USE_BLOCK;
   72 |         };
   73 |     }
```

<a id="e10"></a>
## E10 — 免费方块交互与物品使用分支已隔离，但支持范围有限

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/TacticalBlockUseMixin.java`：L17–L36

```text
   17 | /** Keep .84 protection/event handling while restricting the selected capability's branches. */
   18 | @Mixin(ServerPlayerGameMode.class)
   19 | public abstract class TacticalBlockUseMixin {
   20 |     @Redirect(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onItemUseFirst(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
   21 |     private InteractionResult dndturn$first(ItemStack stack, UseOnContext context) {
   22 |         return Boolean.TRUE.equals(TacticalUseContext.free(context.getPlayer(), context.getClickedPos()))
   23 |             ? InteractionResult.PASS : stack.onItemUseFirst(context);
   24 |     }
   25 |     @Redirect(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
   26 |     private InteractionResult dndturn$block(BlockState state, ItemStack stack, Level level, Player player,
   27 |                                            InteractionHand hand, BlockHitResult hit) {
   28 |         Boolean free = TacticalUseContext.free(player, hit.getBlockPos());
   29 |         if (free != null) return free ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
   30 |         return state.useItemOn(stack, level, player, hand, hit);
   31 |     }
   32 |     @Redirect(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
   33 |     private InteractionResult dndturn$item(ItemStack stack, UseOnContext context) {
   34 |         return Boolean.TRUE.equals(TacticalUseContext.free(context.getPlayer(), context.getClickedPos()))
   35 |             ? InteractionResult.PASS : stack.useOn(context);
   36 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalUseContext.java`：L7–L26

```text
    7 | /** Scoped capability selecting the block-only or item-only branch of the original use chain. */
    8 | public final class TacticalUseContext implements AutoCloseable {
    9 |     private static final ThreadLocal<TacticalUseContext> CURRENT = new ThreadLocal<>();
   10 |     private final TacticalUseContext previous;
   11 |     private final UUID player;
   12 |     private final BlockPos target;
   13 |     private final boolean free;
   14 |     public TacticalUseContext(Player player, BlockPos target, boolean free) {
   15 |         this.player = player.getUUID(); this.target = target.immutable(); this.free = free;
   16 |         previous = CURRENT.get(); CURRENT.set(this);
   17 |     }
   18 |     public static Boolean free(Player player, BlockPos target) {
   19 |         var context = CURRENT.get();
   20 |         return context != null && player != null && context.player.equals(player.getUUID())
   21 |             && context.target.equals(target) ? context.free : null;
   22 |     }
   23 |     @Override public void close() {
   24 |         if (CURRENT.get() != this) throw new IllegalStateException("unbalanced use scope");
   25 |         if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
   26 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java`：L17–L30

```text
   17 |     static void register() {
   18 |         TacticalCapabilities.register(new Move());
   19 |         TacticalCapabilities.register(new Melee());
   20 |         TacticalCapabilities.register(new BlockUse());
   21 |         TacticalCapabilities.register(new ItemOnBlock("dndturn:place", "放置", TacticalIntent.Capability.PLACE, s -> s.getItem() instanceof BlockItem));
   22 |         TacticalCapabilities.register(new ItemOnBlock("dndturn:tool", "物品对方块使用", TacticalIntent.Capability.USE_ITEM,
   23 |             s -> s.getItem() instanceof AxeItem || s.getItem() instanceof HoeItem || s.getItem() instanceof ShovelItem || s.getItem() instanceof FlintAndSteelItem));
   24 |         TacticalCapabilities.register(new Break());
   25 |         TacticalCapabilities.register(new Consume());
   26 |         TacticalCapabilities.register(new Bucket());
   27 |         TacticalCapabilities.register(new EntityItem());
   28 |         TacticalCapabilities.register(new RangedBehavior("dndturn:bow", Items.BOW));
   29 |         TacticalCapabilities.register(new RangedBehavior("dndturn:crossbow", Items.CROSSBOW));
   30 |         TacticalCapabilities.register(new RangedBehavior("dndturn:snowball", Items.SNOWBALL));
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java`：L108–L145

```text
  108 |     static final class Consume extends Interaction {
  109 |         Consume() { super("dndturn:consume", "自身使用 / 食饮", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.SELF); }
  110 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
  111 |             return stack(p, i).has(DataComponents.CONSUMABLE) ? null : "consumable component required";
  112 |         }
  113 |         protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) { return p.gameMode.useItem(p, p.level(), stack(p, i), hand(i)); }
  114 |     }
  115 |     static final class Bucket extends Interaction {
  116 |         Bucket() { super("dndturn:bucket", "桶 / 流体", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.BLOCK); }
  117 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
  118 |             var stack = stack(p, i);
  119 |             if (!stack.is(Items.BUCKET) && !stack.is(Items.WATER_BUCKET) && !stack.is(Items.LAVA_BUCKET)) return "ordinary fluid bucket required; entity buckets need an adapter";
  120 |             BlockPos adjacent = pos(i.target().cell()).relative(Direction.values()[i.target().face()]);
  121 |             if (!p.level().hasChunkAt(adjacent) || !s.region().containsPoint(adjacent.getX()+.5, adjacent.getY()+.5, adjacent.getZ()+.5)) return "bucket effect outside loaded encounter";
  122 |             if (p.level().getServer().isUnderSpawnProtection(p.level(), adjacent, p) || !p.level().mayInteract(p, adjacent)) return "protected fluid destination";
  123 |             return null;
  124 |         }
  125 |         protected ClipContext.Fluid fluids(ServerPlayer p, TacticalIntent i) { return stack(p, i).is(Items.BUCKET) ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE; }
  126 |         public void prepare(TacticalActions a, ServerPlayer p, Execution e) {
  127 |             super.prepare(a, p, e);
  128 |             var i = e.root.intent(); var eye = p.getEyePosition();
  129 |             var actual = p.level().clip(new ClipContext(eye, eye.add(p.calculateViewVector(p.getXRot(), p.getYRot()).scale(p.blockInteractionRange())), ClipContext.Block.OUTLINE, fluids(p, i), p));
  130 |             if (actual.getType() != HitResult.Type.BLOCK || !actual.getBlockPos().equals(pos(i.target().cell())) || actual.getDirection().ordinal() != i.target().face())
  131 |                 throw new IllegalStateException("bucket vanilla ray no longer matches target and face: " + actual.getType() + " " + actual.getBlockPos() + " " + actual.getDirection() + " eye=" + eye + " view=" + p.getViewVector(1));
  132 |         }
  133 |         protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) { return p.gameMode.useItem(p, p.level(), stack(p, i), hand(i)); }
  134 |     }
  135 |     static final class EntityItem extends Interaction {
  136 |         EntityItem() { super("dndturn:entity_item", "物品对实体使用", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.ENTITY); }
  137 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
  138 |             if (!stack(p, i).is(Items.NAME_TAG)) return "entity item effects require a registered contract";
  139 |             return attackTarget(p, i, s);
  140 |         }
  141 |         protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
  142 |             var target = p.level().getEntity(i.target().entity());
  143 |             return p.interactOn(target, hand(i), target.getBoundingBox().getCenter().subtract(target.position()));
  144 |         }
  145 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalBehavior.java`：L102–L107

```text
  102 |     protected static String attackTarget(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
  103 |         var target = player.level().getEntity(intent.target().entity());
  104 |         if (target instanceof net.minecraft.world.entity.player.Player) return "PvP unsupported";
  105 |         if (!(target instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive() || !state.members().containsKey(target.getUUID())) return "target not a living encounter member: present=" + (target != null) + " alive=" + (target != null && target.isAlive()) + " member=" + state.members().containsKey(intent.target().entity());
  106 |         return target.getType() == net.minecraft.world.entity.EntityType.ZOMBIE ? null : "target combat adapter unavailable";
  107 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/RangedBehavior.java`：L15–L32

```text
   15 |     @Override public String unavailable(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
   16 |         if (!stack(player, intent).is(weapon)) return "selected ranged weapon required";
   17 |         try { validateAmmo(player, intent); } catch (IllegalStateException e) { return e.getMessage(); }
   18 |         return attackTarget(player, intent, state);
   19 |     }
   20 |     private void validateAmmo(ServerPlayer player, TacticalIntent intent) {
   21 |             ItemStack weapon = stack(player, intent);
   22 |             if (!weapon.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
   23 |                 net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()) throw new IllegalStateException("ranged enchantments unsupported");
   24 |             if (!weapon.is(net.minecraft.world.item.Items.SNOWBALL)) {
   25 |                 var charged = weapon.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
   26 |                 var ammo = charged != null && !charged.isEmpty() ? charged.itemCopies() : List.of(player.getProjectile(weapon));
   27 |                 if (ammo.size() != 1 || !ammo.getFirst().is(net.minecraft.world.item.Items.ARROW)
   28 |                     || ammo.getFirst().has(net.minecraft.core.component.DataComponents.POTION_CONTENTS))
   29 |                     throw new IllegalStateException("ordinary arrows required");
   30 |             }
   31 |     }
   32 |     @Override protected double entityRange() { return 6; }
```

<a id="e11"></a>
## E11 — 新增测试绕过物品选择和真实鼠标拾取：不能替代交互验收

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControlRegression.java`：L184–L246

```text
  184 |         } catch (Throwable failure) { finish("FAIL: " + failure + " packets=" + packets); }
  185 |     }
  186 |     private static void planTick() throws Exception {
  187 |         var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter(); stageTicks++;
  188 |         require(stageTicks < 400, "plan stage timeout " + activeStage + " " + ClientTacticalPlan.description());
  189 |         switch (activeStage) {
  190 |             case 0 -> {
  191 |                 if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) Files.writeString(output().resolveSibling("active-ready.txt"), "ready");
  192 |                 if (!mc.player.getUUID().equals(state.current())) return;
  193 |                 actor = mc.player.position(); budget = state.movementTicks();
  194 |                 ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE);
  195 |                 var ground = mc.player.blockPosition().offset(2, -1, 0);
  196 |                 ClientTacticalPlan.target(null, new net.minecraft.world.phys.BlockHitResult(
  197 |                     net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, .5, 0), net.minecraft.core.Direction.UP, ground, false));
  198 |                 nextStage();
  199 |             }
  200 |             case 1 -> {
  201 |                 if (!ClientTacticalPlan.offers().isEmpty()) {
  202 |                     ClientTacticalPlan.choose(ClientTacticalPlan.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:move")).findFirst().orElseThrow());
  203 |                     return;
  204 |                 }
  205 |                 if (ClientTacticalPlan.running() || stageTicks < 15) return;
  206 |                 require(mc.player.position().distanceToSqr(actor) > 1 && state.movementTicks() < budget,
  207 |                     "automatic MoveTo did not move and charge: " + ClientTacticalPlan.description());
  208 |                 require(ClientControl.mode() == ClientControl.Mode.CAMERA, "MoveTo changed camera mode");
  209 |                 for (var entity : mc.level.entitiesForRendering()) if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie
  210 |                     && state.members().stream().anyMatch(m -> m.id().equals(entity.getUUID()))
  211 |                     && entity.position().distanceToSqr(mc.player.position()) < 64) {
  212 |                     actor = mc.player.position();
  213 |                     ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK);
  214 |                     ClientTacticalPlan.target(entity.getUUID(), net.minecraft.world.phys.BlockHitResult.miss(entity.position(),
  215 |                         net.minecraft.core.Direction.UP, entity.blockPosition()));
  216 |                     nextStage(); return;
  217 |                 }
  218 |                 throw new IllegalStateException("plan fixture target missing");
  219 |             }
  220 |             case 2 -> {
  221 |                 if (!ClientTacticalPlan.offers().isEmpty()) {
  222 |                     ClientTacticalPlan.choose(ClientTacticalPlan.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:melee")).findFirst().orElseThrow());
  223 |                     return;
  224 |                 }
  225 |                 if (ClientTacticalPlan.running() || stageTicks < 15) return;
  226 |                 require(state.phase() == cc.sighs.dndturn.combat.EncounterPhase.ACTIVE,
  227 |                     "approach attack failed: " + ClientTacticalPlan.description());
  228 |                 require(actor.distanceToSqr(mc.player.position()) > .1, "attack did not approach its target");
  229 |                 require(ClientControl.mode() == ClientControl.Mode.CAMERA, "attack approach changed camera mode");
  230 |                 if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) { nextStage(); return; }
  231 |                 finish("PASS: real dedicated client overhead camera, UI/input isolation, candidate MoveTo through vanilla input, observed movement charge, automatic approach and one attack activation.");
  232 |             }
  233 |             case 3 -> {
  234 |                 if (stageTicks < 20) return;
  235 |                 var evidence = com.google.gson.JsonParser.parseString(Files.readString(output().resolveSibling("peer-state.json"))).getAsJsonObject();
  236 |                 require(evidence.get("active").getAsBoolean() && evidence.get("independent").getAsBoolean(), "peer camera or actor moved");
  237 |                 require(System.currentTimeMillis() - evidence.get("time").getAsLong() < 3000, "stale peer evidence");
  238 |                 Vec3 remote = new Vec3(evidence.get("primaryX").getAsDouble(), evidence.get("primaryY").getAsDouble(), evidence.get("primaryZ").getAsDouble());
  239 |                 require(remote.distanceToSqr(mc.player.position()) < .01, "plan movement did not converge on peer");
  240 |                 finish("PASS: two real clients; independent tactical cameras; candidate MoveTo and approach attack through vanilla input; movement cost and remote actor synchronization.");
  241 |             }
  242 |             default -> throw new IllegalStateException("unexpected plan stage");
  243 |         }
  244 |     }
  245 |     private static void activeTick() throws Exception {
  246 |         var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter(); stageTicks++;
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/gametest/TacticalPlanGameTests.java`：L14–L91

```text
   14 |     public static void interactions(GameTestHelper helper) {
   15 |         ServerPlayer player = helper.makeMockServerPlayerInLevel();
   16 |         BlockPos base = helper.absolutePos(new BlockPos(45001, 121, 1));
   17 |         var level = helper.getLevel();
   18 |         Set<Long> forced = new HashSet<>();
   19 |         for (int x = (base.getX() - 24) >> 4; x <= (base.getX() + 24) >> 4; x++)
   20 |             for (int z = (base.getZ() - 24) >> 4; z <= (base.getZ() + 24) >> 4; z++) {
   21 |                 level.getChunk(x, z);
   22 |                 if (level.setChunkForced(x, z, true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x, z));
   23 |             }
   24 |         for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, -1, -5), base.offset(5, -1, 5))) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
   25 |         for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, 0, -5), base.offset(5, 5, 5))) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
   26 |         player.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
   27 |         player.setNoGravity(true);
   28 |         player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
   29 |         var service = ServerCombatService.forServer(level.getServer());
   30 |         Runnable cleanup = () -> {
   31 |             service.leave(player.getUUID());
   32 |             for (long packed : forced) {
   33 |                 var c = net.minecraft.world.level.ChunkPos.unpack(packed); level.setChunkForced(c.x(), c.z(), false);
   34 |             }
   35 |             forced.clear();
   36 |         };
   37 |         helper.runAtTickTime(199, cleanup);
   38 |         try {
   39 |             service.requestStart(player, UUID.randomUUID());
   40 |             UUID id = service.encounterOf(player.getUUID());
   41 |             BlockPos target = base.offset(1, 0, 0);
   42 |             level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
   43 |             player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.DIRT, 8));
   44 |             level.setBlockAndUpdate(target, Blocks.LEVER.defaultBlockState());
   45 |             var free = request(player, service, id, TacticalIntent.Capability.USE_BLOCK, target);
   46 |             service.tacticalActions().request(player, free);
   47 |             helper.assertTrue(level.getBlockState(target.above()).isAir() && player.getMainHandItem().getCount() == 8,
   48 |                 "free block interaction fell back to held block placement");
   49 |             helper.assertTrue(service.state(id).members().get(player.getUUID()).action(), "unhandled free interaction spent action");
   50 |             level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
   51 |             var place = request(player, service, id, TacticalIntent.Capability.PLACE, target);
   52 |             service.tacticalActions().request(player, place);
   53 |             helper.assertTrue(level.getBlockState(target.above()).is(Blocks.DIRT) && player.getMainHandItem().getCount() == 7,
   54 |                 "paid placement did not execute through vanilla: " + service.results(id, 0, 64).results());
   55 |             helper.assertTrue(!service.state(id).members().get(player.getUUID()).action(), "accepted placement did not spend action");
   56 |             service.tacticalActions().request(player, place);
   57 |             helper.assertTrue(player.getMainHandItem().getCount() == 7, "duplicate plan repeated placement");
   58 |             BlockPos chest = base.offset(-1, 0, 0);
   59 |             level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
   60 |             service.tacticalActions().request(player, request(player, service, id, TacticalIntent.Capability.USE_BLOCK, chest));
   61 |             helper.assertTrue(service.tacticalActions().mayUseContainer(player), "free chest did not grant bounded container access after action spent");
   62 |             player.closeContainer();
   63 |             service.stop(id);
   64 |             service.requestStart(player, UUID.randomUUID());
   65 |             id = service.encounterOf(player.getUUID());
   66 |             player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.APPLE, 2));
   67 |             player.getFoodData().setFoodLevel(10);
   68 |             var self = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF, level.dimension().identifier().toString(), null, null, -1, 0, 0, 0);
   69 |             var eat = new TacticalIntent(TacticalIntent.Capability.USE_ITEM, self, item(player));
   70 |             service.tacticalActions().request(player, new TacticalNetwork.Request(service.generation(), id, UUID.randomUUID(), service.state(id).version(), eat, false));
   71 |             helper.assertTrue(player.isUsingItem() && !service.state(id).members().get(player.getUUID()).action(), "accepted food did not start and charge once");
   72 |             UUID eatingEncounter = id;
   73 |             helper.startSequence().thenWaitUntil(() -> helper.assertTrue(!player.isUsingItem(), "food still using"))
   74 |                 .thenExecute(() -> {
   75 |                     helper.assertTrue(player.getMainHandItem().getCount() == 1 && player.getFoodData().getFoodLevel() > 10,
   76 |                         "food did not complete while body paused");
   77 |                     helper.assertTrue(!service.state(eatingEncounter).members().get(player.getUUID()).action(), "food refunded action");
   78 |                     service.stop(eatingEncounter);
   79 |                     service.requestStart(player, UUID.randomUUID());
   80 |                     UUID mining = service.encounterOf(player.getUUID());
   81 |                     player.getInventory().setItem(player.getInventory().getSelectedSlot(), new ItemStack(Items.IRON_PICKAXE));
   82 |                     BlockPos ore = base.offset(0, 0, 1);
   83 |                     level.setBlockAndUpdate(ore, Blocks.STONE.defaultBlockState());
   84 |                     service.tacticalActions().request(player, request(player, service, mining, TacticalIntent.Capability.BREAK, ore));
   85 |                     helper.assertTrue(!service.state(mining).members().get(player.getUUID()).action(), "mining did not charge on accepted start");
   86 |                 }).thenWaitUntil(() -> helper.assertTrue(level.getBlockState(base.offset(0, 0, 1)).isAir(), "mining still pending"))
   87 |                 .thenExecute(() -> {
   88 |                     UUID mining = service.encounterOf(player.getUUID());
   89 |                     helper.assertTrue(!service.state(mining).members().get(player.getUUID()).action(), "mining refunded action");
   90 |                     cleanup.run();
   91 |                 }).thenSucceed();
```

### `common/src/test/java/cc/sighs/dndturn/combat/TacticalPlanTest.java`：L10–L109

```text
   10 | class TacticalPlanTest {
   11 |     final UUID encounter = UUID.randomUUID(), actor = UUID.randomUUID();
   12 |     CombatEngine engine = new CombatEngine(new Random(4), 28, 20);
   13 |     TacticalPlanTest() {
   14 |         engine.beginCandidate(encounter, EncounterRegion.generate("overworld",
   15 |             new EncounterRegion.Discovery(0, 0, 0, 10, 10, 10),
   16 |             List.of(new EncounterRegion.Anchor(actor, new EncounterRegion.Point(5, 5, 5))), 5, 1), Set.of(actor));
   17 |     }
   18 |     OperationRecord.Snapshot plan() {
   19 |         var target = new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK, "overworld", null,
   20 |             new GridCell(5, 4, 5), 1, .5, 1, .5);
   21 |         var intent = new TacticalIntent(TacticalIntent.Capability.PLACE, target,
   22 |             new TacticalIntent.ItemReference(0, "stack-version-1"));
   23 |         return new OperationRecord.Snapshot(UUID.randomUUID(), null, encounter, actor, actor, null,
   24 |             0, engine.stateView(encounter).version(), null, target.cell(), OperationRecord.Kind.PLAN, null, intent);
   25 |     }
   26 |     OperationRecord.Snapshot child(OperationRecord.Snapshot plan, OperationRecord.Kind kind) {
   27 |         return new OperationRecord.Snapshot(UUID.randomUUID(), plan.operationId(), encounter, actor, actor, null,
   28 |             1, engine.stateView(encounter).version(), null, kind == OperationRecord.Kind.MOVE ? null : plan.targetCell(), kind);
   29 |     }
   30 |     @Test void rejectionSurvivesRestoreAndDoesNotGrantPermissionOrSpendResources() {
   31 |         var request = plan(); long version = engine.stateView(encounter).version();
   32 |         assertThrows(NullPointerException.class, () -> engine.rejectPlan(request, null));
   33 |         assertEquals(version, engine.stateView(encounter).version());
   34 |         var rejection = engine.rejectPlan(request, "adapter unavailable");
   35 |         assertEquals(OperationRecord.Outcome.REJECTED, rejection.outcome());
   36 |         assertTrue(engine.stateView(encounter).members().get(actor).action());
   37 |         assertNull(engine.pendingOperation(encounter, request.operationId()));
   38 |         engine = CombatEngine.restoreSnapshot(engine.exportSnapshot(), new Random(9));
   39 |         assertEquals(rejection, engine.resultFor(encounter, request.operationId()));
   40 |         assertEquals(rejection, engine.rejectPlan(request, "adapter unavailable"));
   41 |         var old = request.intent();
   42 |         var changed = new TacticalIntent("extension:other", 2, TacticalIntent.Hand.OFF_HAND, old.capability(), old.target(), old.item());
   43 |         var conflict = new OperationRecord.Snapshot(request.operationId(), null, encounter, actor, actor, null,
   44 |             request.serverTick(), request.encounterVersion(), request.sourceCell(), request.targetCell(), OperationRecord.Kind.PLAN, null, changed);
   45 |         assertThrows(IllegalStateException.class, () -> engine.rejectPlan(conflict, "adapter unavailable"));
   46 |     }
   47 |     @Test void behaviorIdentityIsPartOfImmutableRequest() {
   48 |         var original = plan().intent();
   49 |         assertNotEquals(original, new TacticalIntent("extension:place", 1, original.hand(), original.capability(), original.target(), original.item()));
   50 |         assertThrows(IllegalArgumentException.class, () -> new TacticalIntent("bad id", 1, original.hand(), original.capability(), original.target(), original.item()));
   51 |         assertThrows(IllegalArgumentException.class, () -> new TacticalIntent(original.behaviorId(), 0, original.hand(), original.capability(), original.target(), original.item()));
   52 |     }
   53 |     @Test void approachReservesActionAndUsesCurrentVersionWithoutActivatingCombat() {
   54 |         var plan = plan(); assertTrue(engine.beginOperation(plan));
   55 |         var move = child(plan, OperationRecord.Kind.MOVE);
   56 |         assertFalse(engine.beginOperation(move));
   57 |         assertTrue(engine.beginPlanStep(move));
   58 |         engine.publish(encounter, move.operationId(), 0, OperationRecord.Outcome.COMPLETED, "moved", 4, 0, true);
   59 |         assertEquals(24, engine.stateView(encounter).members().get(actor).movementTicks());
   60 |         assertTrue(engine.stateView(encounter).members().get(actor).action());
   61 |         assertEquals(EncounterPhase.CANDIDATE, engine.stateView(encounter).phase());
   62 |         assertFalse(engine.beginPlanStep(child(plan, OperationRecord.Kind.USE_ITEM)));
   63 |         var use = child(plan, OperationRecord.Kind.PLACE);
   64 |         assertTrue(engine.beginPlanStep(use));
   65 |         engine.publish(encounter, use.operationId(), 0, OperationRecord.Outcome.REJECTED, "protected", 0, 0, true);
   66 |         engine.publish(encounter, plan.operationId(), 0, OperationRecord.Outcome.REJECTED, "protected", 0, 0, true);
   67 |         assertTrue(engine.stateView(encounter).members().get(actor).action());
   68 |         assertFalse(engine.beginOperation(plan));
   69 |     }
   70 |     @Test void acceptedUseChargesOnceAndInvalidPublicationDoesNotPartiallyCommit() {
   71 |         var plan = plan(); assertTrue(engine.beginOperation(plan));
   72 |         var use = child(plan, OperationRecord.Kind.PLACE); assertTrue(engine.beginPlanStep(use));
   73 |         long version = engine.stateView(encounter).version();
   74 |         assertThrows(IllegalArgumentException.class, () -> engine.publish(encounter, use.operationId(), 0,
   75 |             OperationRecord.Outcome.ACCEPTED, "invalid", 0, Float.NaN, false));
   76 |         assertEquals(version, engine.stateView(encounter).version());
   77 |         assertTrue(engine.stateView(encounter).members().get(actor).action());
   78 |         var first = engine.publish(encounter, use.operationId(), 0, OperationRecord.Outcome.ACCEPTED, "started", 0, 0, false);
   79 |         assertSame(first, engine.publish(encounter, use.operationId(), 0, OperationRecord.Outcome.ACCEPTED, "started", 0, 0, false));
   80 |         assertFalse(engine.stateView(encounter).members().get(actor).action());
   81 |         engine.publish(encounter, use.operationId(), 1, OperationRecord.Outcome.INTERRUPTED, "cancelled", 0, 0, true);
   82 |         engine.publish(encounter, plan.operationId(), 0, OperationRecord.Outcome.INTERRUPTED, "cancelled", 0, 0, true);
   83 |         assertFalse(engine.stateView(encounter).members().get(actor).action());
   84 |     }
   85 |     @Test void recoveryAndExitCloseChildrenBeforeRootWithoutReplay() {
   86 |         var plan = plan(); assertTrue(engine.beginOperation(plan));
   87 |         var move = child(plan, OperationRecord.Kind.MOVE); assertTrue(engine.beginPlanStep(move));
   88 |         engine = CombatEngine.restoreSnapshot(engine.exportSnapshot(), new Random(9));
   89 |         assertEquals(2, engine.failRestoredWork(encounter, 20, "unconfirmed"));
   90 |         assertEquals(OperationRecord.Outcome.UNKNOWN, engine.resultFor(encounter, plan.operationId()).outcome());
   91 |         assertTrue(engine.stateView(encounter).members().get(actor).action());
   92 |         var second = plan(); assertTrue(engine.beginOperation(second));
   93 |         assertTrue(engine.beginPlanStep(child(second, OperationRecord.Kind.MOVE)));
   94 |         assertTrue(engine.leave(encounter, actor));
   95 |     }
   96 |     @Test void planRejectsDifferentTargetAndRootCannotFinishBeforeChild() {
   97 |         var plan = plan(); assertTrue(engine.beginOperation(plan));
   98 |         var wrong = new OperationRecord.Snapshot(UUID.randomUUID(), plan.operationId(), encounter, actor, actor,
   99 |             null, 1, engine.stateView(encounter).version(), null, new GridCell(8, 4, 5), OperationRecord.Kind.PLACE);
  100 |         long version = engine.stateView(encounter).version();
  101 |         assertFalse(engine.beginPlanStep(wrong));
  102 |         assertEquals(version, engine.stateView(encounter).version());
  103 |         var move = child(plan, OperationRecord.Kind.MOVE); assertTrue(engine.beginPlanStep(move));
  104 |         version = engine.stateView(encounter).version();
  105 |         assertThrows(IllegalStateException.class, () -> engine.publish(encounter, plan.operationId(), 0,
  106 |             OperationRecord.Outcome.COMPLETED, "too early", 0, 0, true));
  107 |         assertEquals(version, engine.stateView(encounter).version());
  108 |         assertNotNull(engine.pendingOperation(encounter, move.operationId()));
  109 |     }
```

<a id="e12"></a>
## E12 — 版本、构建约束及现有 UI 文案

### `targets/neoforge-26.1/gradle.properties`：L1–L3

```text
    1 | neoforge_261_minecraft_version=26.1
    2 | neoforge_261_version=26.1.2.84
    3 | neoforge_261_version_range=[26.1,)
```

### `targets/neoforge-26.1/build.gradle`：L1–L58

```text
    1 | plugins {
    2 |     id 'java-library'
    3 |     id 'net.neoforged.moddev' version '2.0.141'
    4 |     id 'me.modmuss50.mod-publish-plugin' version '2.1.1'
    5 | }
    6 | 
    7 | apply from: file('../../gradle/target-conventions/properties.gradle')
    8 | 
    9 | group = mod_group_id
   10 | version = mod_version
   11 | 
   12 | base {
   13 |     archivesName = "${mod_name}-neoforge-26.1"
   14 | }
   15 | 
   16 | java {
   17 |     toolchain.languageVersion = JavaLanguageVersion.of(25)
   18 | }
   19 | 
   20 | sourceSets.main.output.dir(
   21 |         project(':common').layout.buildDirectory.dir('classes/java/main'),
   22 |         builtBy: ':common:classes'
   23 | )
   24 | 
   25 | neoForge {
   26 |     version = neoforge_261_version
   27 | 
   28 |     runs {
   29 |         client {
   30 |             client()
   31 |             if (project.hasProperty('dndturnControlProbe')) {
   32 |                 systemProperty 'dndturn.controlProbe', 'true'
   33 |                 systemProperty 'dndturn.controlProbe.output', layout.buildDirectory.file('control-probe/result.txt').get().asFile.absolutePath
   34 |                 systemProperty 'dndturn.controlProbe.scale', project.findProperty('controlProbeScale') ?: '2'
   35 |                 systemProperty 'dndturn.controlProbe.peer', project.hasProperty('controlProbePeer').toString()
   36 |                 systemProperty 'dndturn.controlProbe.requirePeer', project.hasProperty('controlProbeRequirePeer').toString()
   37 |                 systemProperty 'dndturn.controlProbe.activeCombat', project.hasProperty('controlProbeActive').toString()
   38 |                 programArguments.addAll('--username', project.hasProperty('controlProbePeer') ? 'DNDCameraPeer' : 'DNDCameraProbe',
   39 |                     '--quickPlayMultiplayer', '127.0.0.1:25565')
   40 |                 programArguments.addAll('--width', project.findProperty('controlProbeWidth') ?: '1280',
   41 |                     '--height', project.findProperty('controlProbeHeight') ?: '720')
   42 |             }
   43 |             if (project.hasProperty('dndturnRepairClient')) {
   44 |                 gameDirectory = layout.buildDirectory.dir('repair-client')
   45 |             }
   46 |             if (project.hasProperty('dndturnUiSmoke')) {
   47 |                 systemProperty 'dndturn.uiSmoke', 'true'
   48 |                 systemProperty 'dndturn.uiSmoke.output', layout.buildDirectory.dir('ui-smoke').get().asFile.absolutePath
   49 |             }
   50 |         }
   51 |         server {
   52 |             server()
   53 |             programArgument '--nogui'
   54 |             if (project.hasProperty('dndturnRepairServer')) {
   55 |                 gameDirectory = layout.buildDirectory.dir('repair-server')
   56 |             }
   57 |         }
   58 |         gameTestServer {
```

### `targets/neoforge-26.1/src/main/resources/assets/apricityui/apricity/dndturn/tactical.html`：L16–L38

```text
   16 |   <section id="inventory-panel" class="combat-panel inventory-panel" style="display: none">
   17 |     <h2>随身物品</h2><div id="inventory"></div>
   18 |     <p class="muted">点击热栏物品选择能力；右键世界目标执行。背包中其他物品请先整理至热栏。</p>
   19 |   </section>
   20 |   <footer class="combat-panel actions">
   21 |     <div class="resource-row"><span id="action"></span><span id="movement"></span><span id="reaction"></span><span id="environment" class="muted"></span><span id="target"></span></div>
   22 |     <div class="buttons">
   23 |       <button class="button" id="move">移动</button>
   24 |       <button class="button button-primary" id="attack">攻击</button>
   25 |       <button class="button" id="place">放置</button>
   26 |       <button class="button" id="use-block">使用方块</button>
   27 |       <button class="button" id="break-block">破坏</button>
   28 |       <button class="button" id="use-item">使用物品</button>
   29 |       <div id="behavior-options"></div><button class="button" id="behavior-hand">切换主 / 副手</button><button class="button" id="cancel-plan">取消</button>
   30 |       <button class="button" id="dash">疾走</button>
   31 |       <button class="button" id="dodge">回避</button>
   32 |       <button class="button" id="disengage">撤离</button>
   33 |       <button class="button" id="inventory-toggle">物品</button>
   34 |       <button class="button" id="end">结束回合</button>
   35 |       <button class="button" id="exit">退出</button>
   36 |     </div>
   37 |     <div id="description" class="muted">悬停动作查看规则说明。</div>
   38 |     <div id="status" class="muted"></div>
```

## 全包扫描说明

扫描全部 108 个 Java 文件：`ClientTacticalPlan.capability()` 无调用点；`ClientTacticalPlan.select()` 的写入出现在 UI、键绑定和回归脚本中，而目标查询只携带 target/hand。
战术 KeyMapping 的完整注册见 E01：未注册 Home、O、Esc 分层取消、左 Ctrl 攻击准备、Alt 标签、T 提示固定、F10 战术 HUD 等新增绑定。这里只陈述本模组实现，不推断其他模组或原版界面的快捷键。
生产 Java 扫描 setBlock / EntityPlace / BlockEvent / mayInteract / isUnderSpawnProtection / mutation：未发现针对此次放置计划的通用影响范围校验。E07 将此记为授权缺口；没有在 Minecraft 中运行越界放置复现。
客户端回归新增 planTick 确实接入真实客户端运行框架，但直接调用 target()/choose()，未验证数字键→正确物品→鼠标拾取→菜单确认的完整入口。

## 离线探针输出

```text
PASS candidate approach preserves action, spends observed movement; cancellation does not refund movement
PASS wrong plan step rejected without version mutation
PASS pre-effect placement rejection leaves action available
PASS accepted continuous action charges once; cancellation does not refund action
PASS block-only interaction allowed after action exhaustion; next paid action denied
PASS root cannot finish before child; recovery closes pending movement then root without action charge
REPRODUCED lethal-ordering regression: ATTACK child=COMPLETED; target leave before PLAN finish sets PLAN=UNKNOWN; reason=member left before world outcome was confirmed
SUMMARY: 6 positive audit probes passed; 1 regression reproduced. No Minecraft runtime or JUnit execution.
```