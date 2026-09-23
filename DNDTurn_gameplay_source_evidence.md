# DNDTurn 当前实际游戏链路：源码证据摘录
审查对象：`DNDTurn-sources(20260925-135634).zip`。所有行号来自本次压缩包解压后的原文件，不是历史版本，也不是网页源码。
压缩包 SHA-256：`f72bb358c4629811d81f3b9cccdc6ca549f6ca76866d1e02671f7b210141fbf8`
这是源码检查证据，不是客户端运行记录。没有执行 Gradle build、JUnit、GameTest 或真实双客户端验收。未修改原始源文件。
本包没有 `docs/` 目录；AGENTS.md 引用的规则文档不在此包中，未将这些文档视为已读取。
## 证据目录
- **S01**：实际 target 与客户端注册
- **S02**：镜头从眼睛初始化；不是俯视焦点镜头
- **S03**：WASD 已接线，但前进向量含 pitch；Z/Shift 升降，右键转向，滚轮沿前向移动
- **S04**：世界选择只有左键释放后的成员 UUID；BlockHit 只参与遮挡
- **S05**：HUD 是独立行动按钮；背包是只读物品预览
- **S06**：攻击键/移动键与快捷栏冲突处理；MOVE 只是开关移动许可
- **S07**：协议只能携带 target UUID；没有世界目标/物品选择/目的地载荷
- **S08**：服务端为选定目标执行即时近战；限定 Player/Zombie；超距直接拒绝
- **S09**：动作资源模型及计费种类
- **S10**：客户端与服务端都拦截了原版世界行为；没有对应战术执行替代
- **S11**：调度不能直接嵌套 MOVE 与 ATTACK；动作在根操作开始时扣除
- **S12**：正常入口进入候选阶段；候选阶段移动被禁止
- **S13**：当前回归测试验证的是自由镜头与候选阶段禁止移动
- **S14**：移动按观察到的服务端位移结算；搜索权重不等于移动费用
- **S15**：现有物品整理权限不是物品行为能力；持续使用还受身体 tick 门控

## S01 — 实际 target 与客户端注册

### `targets/neoforge-26.1/gradle.properties`：L1–L3

```text
   1 | neoforge_261_minecraft_version=26.1
   2 | neoforge_261_version=26.1.2.84
   3 | neoforge_261_version_range=[26.1,)
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/DNDTurnNeoForge261Client.java`：L12–L29

```text
  12 | @Mod(value = DNDTurnNeoForge261.MOD_ID, dist = Dist.CLIENT)
  13 | public final class DNDTurnNeoForge261Client {
  14 |     public DNDTurnNeoForge261Client(IEventBus modBus) {
  15 |         modBus.addListener(DNDTurnNeoForge261Client::registerPayloads);
  16 |         modBus.addListener(CombatControls::registerKeys);
  17 |         modBus.addListener(CombatControls::registerHud);
  18 |         NeoForge.EVENT_BUS.addListener(ClientCombatState::onClientTick);
  19 |         NeoForge.EVENT_BUS.addListener(CombatControls::onClientTick);
  20 |         NeoForge.EVENT_BUS.addListener(CombatControls::onKeyInput);
  21 |     }
  22 | 
  23 |     private static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
  24 |         event.register(CombatNetwork.BodyState.TYPE, ClientCombatState::receive);
  25 |         event.register(CombatNetwork.EntitySimulation.TYPE, cc.sighs.dndturn.client.ClientEntitySimulation::receive);
  26 |         event.register(CombatNetwork.EncounterState.TYPE, ClientCombatState::receiveEncounter);
  27 |         event.register(CombatNetwork.ResultNotice.TYPE, ClientCombatState::receiveResult);
  28 |         event.register(CombatNetwork.IntentStatus.TYPE, ClientCombatState::receiveIntentStatus);
  29 |         event.register(CombatNetwork.ConsentState.TYPE, ClientCombatState::receiveConsent);
```

## S02 — 镜头从眼睛初始化；不是俯视焦点镜头

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L54–L85

```text
  54 |     public static void reconcile() {
  55 |         Minecraft mc = Minecraft.getInstance();
  56 |         boolean valid = ClientCombatState.encounter() != null && mc.player != null && mc.player.isAlive()
  57 |             && mc.level != null && mc.getConnection() != null;
  58 |         if (level != mc.level || player != mc.player) {
  59 |             reset(); level = mc.level; player = mc.player;
  60 |         }
  61 |         if (valid && !session) {
  62 |             session = true; mode = Mode.CAMERA;
  63 |             cameraOwner = mc.getCameraEntity();
  64 |             position = mc.player.getEyePosition(); yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
  65 |             restoreGrab = mc.mouseHandler.isMouseGrabbed();
  66 |             transition();
  67 |         } else if (!valid && session) {
  68 |             session = false; position = null; cameraOwner = null; transition();
  69 |         }
  70 |         Recipient next = !mc.isWindowActive() ? Recipient.UNFOCUSED : mc.screen != null ? Recipient.SCREEN
  71 |             : modal() ? Recipient.CONSENT : session ? mode == Mode.CAMERA ? Recipient.CAMERA : Recipient.CHARACTER
  72 |             : Recipient.GAME;
  73 |         if (next != recipient) {
  74 |             boolean controlled = session || modal() || recipient == Recipient.CAMERA
  75 |                 || recipient == Recipient.CHARACTER || recipient == Recipient.CONSENT;
  76 |             recipient = next;
  77 |             if (controlled) transition();
  78 |         }
  79 |         if (mc.player == null || mc.screen != null || !mc.isWindowActive()) return;
  80 |         if (next == Recipient.CONSENT || next == Recipient.CAMERA) {
  81 |             if (mc.mouseHandler.isMouseGrabbed()) { restoreGrab = true; mc.mouseHandler.releaseMouse(); }
  82 |         } else if ((next == Recipient.CHARACTER || next == Recipient.GAME && restoreGrab)
  83 |             && !mc.mouseHandler.isMouseGrabbed()) {
  84 |             mc.mouseHandler.grabMouse(); restoreGrab = false;
  85 |         }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalCameraMixin.java`：L12–L24

```text
  12 | @Mixin(Camera.class)
  13 | public abstract class TacticalCameraMixin {
  14 |     @Shadow protected abstract void setPosition(Vec3 position);
  15 |     @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);
  16 |     @Shadow private boolean detached;
  17 |     @Inject(method = "alignWithEntity", at = @At("TAIL"))
  18 |     private void dndturn$pose(float partialTick, CallbackInfo ci) {
  19 |         if (!ClientControl.camera((Camera)(Object)this)) return;
  20 |         ClientControl.frame();
  21 |         setPosition(ClientControl.position());
  22 |         setRotation(ClientControl.yaw(), ClientControl.pitch(), 0);
  23 |         detached = true;
  24 |     }
```

## S03 — WASD 已接线，但前进向量含 pitch；Z/Shift 升降，右键转向，滚轮沿前向移动

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L130–L153

```text
 130 |     public static boolean key(KeyEvent event, int action) {
 131 |         reconcile();
 132 |         InputConstants.Key code = InputConstants.getKey(event);
 133 |         boolean suppressed = suppressedKeys.contains(code);
 134 |         if (action == GLFW.GLFW_RELEASE) { heldKeys.remove(code); suppressedKeys.remove(code); }
 135 |         else heldKeys.add(code);
 136 |         if (suppressed) {
 137 |             for (KeyMapping key : Minecraft.getInstance().options.keyMappings) if (key.matches(event)) clear(key);
 138 |             return false;
 139 |         }
 140 |         for (KeyMapping mapping : movementKeys()) {
 141 |             if (!mapping.matches(event)) continue;
 142 |             if (action == GLFW.GLFW_RELEASE) cameraKeys.remove(mapping);
 143 |             if (action == GLFW.GLFW_PRESS && recipient == Recipient.CAMERA
 144 |                 && mapping.isActiveAndMatches(InputConstants.getKey(event))
 145 |                 && (mapping == CombatControls.cameraUpKey() || !CombatControls.matchesAction(event)))
 146 |                 cameraKeys.add(mapping);
 147 |         }
 148 |         return true;
 149 |     }
 150 |     private static KeyMapping[] movementKeys() {
 151 |         var o = Minecraft.getInstance().options;
 152 |         return new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, CombatControls.cameraUpKey(), o.keyShift, o.keySprint};
 153 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L238–L259

```text
 238 |     public static boolean scroll(double delta, boolean consumed) {
 239 |         reconcile();
 240 |         if (Minecraft.getInstance().screen != null) return consumed;
 241 |         if (!consumed && recipient == Recipient.CAMERA && !TacticalOverlay.hit(mousePosition())) move(forward().scale(delta));
 242 |         return consumed || session || modal();
 243 |     }
 244 |     private static Position mousePosition() {
 245 |         var mc = Minecraft.getInstance();
 246 |         return new Position(mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow()));
 247 |     }
 248 |     public static void mouseMove(double x, double y) {
 249 |         reconcile();
 250 |         Gesture g = gestures.get(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
 251 |         if (!Double.isNaN(mouseX) && recipient == Recipient.CAMERA && g != null
 252 |             && g.owner == Recipient.CAMERA && g.revision == revision) {
 253 |             var o = Minecraft.getInstance().options;
 254 |             double s = Math.pow(o.sensitivity().get() * .6 + .2, 3) * 8 * .15;
 255 |             yaw += (float)((x - mouseX) * s * (o.invertMouseX().get() ? -1 : 1));
 256 |             pitch = Mth.clamp(pitch + (float)((y - mouseY) * s * (o.invertMouseY().get() ? -1 : 1)), -89, 89);
 257 |         }
 258 |         mouseX = x; mouseY = y;
 259 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L260–L293

```text
 260 |     public static boolean camera(Camera camera) {
 261 |         reconcile();
 262 |         var mc = Minecraft.getInstance();
 263 |         return session && mode == Mode.CAMERA && position != null && camera == mc.gameRenderer.getMainCamera()
 264 |             && mc.getCameraEntity() == cameraOwner && cameraOwner == mc.player;
 265 |     }
 266 |     public static void frame() {
 267 |         long now = System.nanoTime();
 268 |         double seconds = frameTime == 0 ? 0 : Math.min(.05, (now - frameTime) / 1e9);
 269 |         frameTime = now;
 270 |         if (recipient != Recipient.CAMERA) return;
 271 |         var o = Minecraft.getInstance().options;
 272 |         double f = axis(o.keyUp, o.keyDown), l = axis(o.keyLeft, o.keyRight), u = axis(CombatControls.cameraUpKey(), o.keyShift);
 273 |         Vec3 forward = forward();
 274 |         Vec3 left = new Vec3(forward.z, 0, -forward.x).normalize();
 275 |         Vec3 delta = forward.scale(f).add(left.scale(l)).add(0, u, 0);
 276 |         if (delta.lengthSqr() > 0) move(delta.normalize().scale(seconds * (cameraKeys.contains(o.keySprint) ? 12 : 6)));
 277 |     }
 278 |     private static int axis(KeyMapping positive, KeyMapping negative) {
 279 |         return (cameraKeys.contains(positive) ? 1 : 0) - (cameraKeys.contains(negative) ? 1 : 0);
 280 |     }
 281 |     private static Vec3 forward() { return Vec3.directionFromRotation(pitch, yaw); }
 282 |     private static void move(Vec3 delta) {
 283 |         if (position == null) return;
 284 |         var world = Minecraft.getInstance().level;
 285 |         if (world == null) return;
 286 |         int steps = Math.max(1, (int)Math.ceil(delta.length() * 2));
 287 |         for (int i = 1; i <= steps; i++)
 288 |             if (!world.hasChunkAt(BlockPos.containing(position.add(delta.scale((double)i / steps))))) return;
 289 |         position = position.add(delta);
 290 |     }
 291 |     public static Vec3 position() { return position; }
 292 |     public static float yaw() { return yaw; }
 293 |     public static float pitch() { return pitch; }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java`：L24–L38

```text
  24 |     private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
  25 |         Identifier.fromNamespaceAndPath(DNDTurnNeoForge261.MOD_ID, "prototype"));
  26 |     private static final KeyMapping UI = key("ui", GLFW.GLFW_KEY_GRAVE_ACCENT);
  27 |     private static final KeyMapping START = new KeyMapping("key.dndturn.start", KeyConflictContext.IN_GAME,
  28 |         KeyModifier.SHIFT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
  29 |     private static final KeyMapping EXIT = key("exit", GLFW.GLFW_KEY_UNKNOWN);
  30 |     private static final KeyMapping MOVE = key("move", GLFW.GLFW_KEY_1);
  31 |     private static final KeyMapping ATTACK = key("attack", GLFW.GLFW_KEY_2);
  32 |     private static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_3);
  33 |     private static final KeyMapping DODGE = key("dodge", GLFW.GLFW_KEY_4);
  34 |     private static final KeyMapping DISENGAGE = key("disengage", GLFW.GLFW_KEY_5);
  35 |     private static final KeyMapping END_TURN = key("end_turn", GLFW.GLFW_KEY_SPACE);
  36 |     private static final KeyMapping INVENTORY = key("inventory", GLFW.GLFW_KEY_I);
  37 |     private static final KeyMapping JUMP = key("jump", GLFW.GLFW_KEY_Z);
  38 |     private enum MovePhase { IDLE, START_PENDING, MOVING, STOP_PENDING }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java`：L143–L155

```text
 143 |     static boolean matchesAction(net.minecraft.client.input.KeyEvent event) {
 144 |         for (KeyMapping key : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
 145 |                 DISENGAGE, END_TURN, INVENTORY, JUMP})
 146 |             if (key.matches(event) && key.isActiveAndMatches(InputConstants.getKey(event))) return true;
 147 |         return false;
 148 |     }
 149 |     private static boolean isAction(KeyMapping candidate) {
 150 |         for (KeyMapping key : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
 151 |                 DISENGAGE, END_TURN, INVENTORY, JUMP}) if (key == candidate) return true;
 152 |         return false;
 153 |     }
 154 | 
 155 |     static KeyMapping cameraUpKey() { return JUMP; }
```

## S04 — 世界选择只有左键释放后的成员 UUID；BlockHit 只参与遮挡

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L172–L207

```text
 172 |     public static void beginMouse(int button, int action) {
 173 |         reconcile();
 174 |         if (action == GLFW.GLFW_PRESS) {
 175 |             Recipient who = recipient;
 176 |             Document doc = null;
 177 |             if (who == Recipient.CONSENT) doc = ConsentOverlay.document();
 178 |             else if (who == Recipient.CAMERA && TacticalOverlay.hit(mousePosition())) {
 179 |                 who = Recipient.TACTICAL; doc = TacticalOverlay.document();
 180 |             }
 181 |             else if ((who == Recipient.CAMERA || who == Recipient.CHARACTER) && CombatControls.hasMouseBinding(button))
 182 |                 who = Recipient.BINDING;
 183 |             gestures.put(button, new Gesture(who, doc, revision));
 184 |         }
 185 |         dispatch = gestures.get(button);
 186 |     }
 187 |     public static boolean endMouse(int button, int action, boolean consumed) {
 188 |         Gesture gesture = dispatch;
 189 |         dispatch = null;
 190 |         if (action == GLFW.GLFW_RELEASE) gestures.remove(button);
 191 |         if (gesture == null) return consumed || session && recipient != Recipient.SCREEN || modal();
 192 |         boolean ours = gesture.owner != Recipient.GAME && gesture.owner != Recipient.SCREEN;
 193 |         boolean binding = false;
 194 |         if (gesture.revision == revision && (gesture.owner == Recipient.CAMERA || gesture.owner == Recipient.CHARACTER || gesture.owner == Recipient.BINDING)
 195 |             && !consumed) {
 196 |             binding = CombatControls.mouseBinding(button, action);
 197 |             InputConstants.Key code = InputConstants.Type.MOUSE.getOrCreate(button);
 198 |             for (KeyMapping key : movementKeys()) if (key.getKey().equals(code)) {
 199 |                 if (action == GLFW.GLFW_RELEASE) cameraKeys.remove(key);
 200 |                 else if (gesture.owner == Recipient.CAMERA && !binding) cameraKeys.add(key);
 201 |                 binding = true;
 202 |             }
 203 |         }
 204 |         if (action == GLFW.GLFW_RELEASE && gesture.owner == Recipient.CAMERA && gesture.revision == revision
 205 |             && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !consumed && !binding) select();
 206 |         return consumed || ours;
 207 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L295–L321

```text
 295 |     private static void select() {
 296 |         var mc = Minecraft.getInstance();
 297 |         Camera camera = mc.gameRenderer.getMainCamera();
 298 |         if (!camera(camera)) return;
 299 |         double x = mc.mouseHandler.xpos() / mc.getWindow().getScreenWidth() * 2 - 1;
 300 |         double y = 1 - mc.mouseHandler.ypos() / mc.getWindow().getScreenHeight() * 2;
 301 |         double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
 302 |         Vector3f ray = new Vector3f((float)(x * tan * mc.getWindow().getScreenWidth() / mc.getWindow().getScreenHeight()),
 303 |             (float)(y * tan), -1).rotate(camera.rotation()).normalize();
 304 |         Vec3 direction = new Vec3(ray.x, ray.y, ray.z);
 305 |         double range = 0;
 306 |         while (range < 64 && mc.level.hasChunkAt(BlockPos.containing(position.add(direction.scale(range + 1))))) range++;
 307 |         Vec3 end = position.add(direction.scale(range));
 308 |         var block = mc.level.clip(new ClipContext(position, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
 309 |         double nearest = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(position);
 310 |         java.util.UUID selected = null;
 311 |         for (var member : ClientCombatState.encounter().members()) {
 312 |             Entity entity = null;
 313 |             for (Entity candidate : mc.level.entitiesForRendering()) if (candidate.getUUID().equals(member.id())) { entity = candidate; break; }
 314 |             if (entity == null || entity == mc.player || !entity.isAlive()) continue;
 315 |             var hit = entity.getBoundingBox().inflate(.1).clip(position, end);
 316 |             if (hit.isPresent() && hit.get().distanceToSqr(position) < nearest) {
 317 |                 nearest = hit.get().distanceToSqr(position); selected = entity.getUUID();
 318 |             }
 319 |         }
 320 |         TacticalOverlay.select(selected);
 321 |     }
```

## S05 — HUD 是独立行动按钮；背包是只读物品预览

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalOverlay.java`：L100–L154

```text
 100 |         enabled("move", myTurn && state.phase() == cc.sighs.dndturn.combat.EncounterPhase.ACTIVE && (state.movementTicks() > 0 || state.moving()));
 101 |         text("move", (state.moving() ? "停止移动" : "移动") + "\n" + CombatControls.keyLabel("move"));
 102 |         for (String id : List.of("attack", "dash", "dodge", "disengage", "end", "exit", "inventory-toggle")) {
 103 |             String label = switch (id) {
 104 |                 case "attack" -> "攻击"; case "dash" -> "疾走"; case "dodge" -> "回避";
 105 |                 case "disengage" -> "撤离"; case "end" -> "结束回合"; case "exit" -> "退出";
 106 |                 default -> "物品";
 107 |             };
 108 |             text(id, label + "\n" + CombatControls.keyLabel(id));
 109 |         }
 110 |         for (String id : List.of("attack", "dash", "dodge", "disengage"))
 111 |             enabled(id, myTurn && (id.equals("attack") || state.phase() == cc.sighs.dndturn.combat.EncounterPhase.ACTIVE)
 112 |                 && state.action() && !state.moving()
 113 |                 && (!id.equals("attack") || selectedTarget != null));
 114 |         enabled("end", myTurn);
 115 |         enabled("exit", state.interactive());
 116 |         updateLog(results);
 117 |         text("status", status == null ? "选择单位查看状态；攻击前选择目标。"
 118 |             : (status.accepted() ? "" : "未执行：") + status.reason());
 119 |         attribute("status", "class", status != null && !status.accepted() ? "rejected" : "muted");
 120 |     }
 121 | 
 122 |     private static void bind() {
 123 |         refreshGeneration = document.getRefreshGeneration();
 124 |         values.clear(); members.clear(); memberLabels.clear(); memberFaces.clear(); logs.clear(); memberOrder = List.of();
 125 |         java.util.Arrays.fill(inventory, null);
 126 |         action("move", CombatNetwork.IntentKind.MOVE_BEGIN, "移动按实际获准位移计费；再次点击停止。结束回合会先结算移动。");
 127 |         action("attack", CombatNetwork.IntentKind.ATTACK, "消耗动作。d20＋5 对目标 AC；自然 1 未命中，自然 20 暴击。选择邻近目标后攻击。");
 128 |         action("dash", CombatNetwork.IntentKind.DASH, "消耗动作，增加本会话捕获的一份基础移动预算。");
 129 |         action("dodge", CombatNetwork.IntentKind.DODGE, "消耗动作，直到下次自身回合开始前，对你的命中检定有劣势。");
 130 |         action("disengage", CombatNetwork.IntentKind.DISENGAGE, "消耗动作，设置本回合撤离状态。借机攻击流程尚未开放。");
 131 |         action("end", CombatNetwork.IntentKind.END_TURN, "先结束并结算正在进行的移动，然后请求结束回合。");
 132 |         action("exit", CombatNetwork.IntentKind.EXIT, "先让角色中心离开场地再退出；场内请求会被拒绝。有其他玩家留场时只移除你，其余玩家继续战斗。");
 133 |         document.getElementById("inventory-toggle").addEventListener("click", event -> toggleInventory());
 134 |         attribute("inventory-panel", "style", inventoryOpen ? "display: block" : "display: none");
 135 |         Element slots = document.getElementById("inventory");
 136 |         for (int i = 0; i < inventory.length; i++) {
 137 |             Element slot = document.createElement("slot");
 138 |             slot.setAttribute("class", "inventory-slot");
 139 |             Element item = document.createElement("item");
 140 |             item.setAttribute("id", "inventory-" + i);
 141 |             slot.appendChild(item);
 142 |             slots.appendChild(slot);
 143 |         }
 144 |         document.getElementById("pointer").addEventListener("click", event -> togglePointer());
 145 |     }
 146 | 
 147 |     private static void action(String id, CombatNetwork.IntentKind kind, String description) {
 148 |         Element element = document.getElementById(id);
 149 |         element.addEventListener("click", event -> {
 150 |             if (!"true".equals(values.get(id + ":enabled"))) return;
 151 |             CombatControls.requestFromUi(kind, kind == CombatNetwork.IntentKind.ATTACK ? selectedTarget : null);
 152 |         });
 153 |         element.addEventListener("mouseenter", event -> text("description", description));
 154 |         enabled(id, true);
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalOverlay.java`：L234–L244

```text
 234 |     private static void updateInventory() {
 235 |         var player = Minecraft.getInstance().player;
 236 |         if (!inventoryOpen || player == null || !isOpen()) return;
 237 |         for (int i = 0; i < inventory.length; i++) {
 238 |             var stack = player.getInventory().getItem(i);
 239 |             if (inventory[i] != null && net.minecraft.world.item.ItemStack.matches(inventory[i], stack)) continue;
 240 |             inventory[i] = stack.copy();
 241 |             Element node = document.getElementById("inventory-" + i);
 242 |             if (node instanceof com.sighs.apricityui.element.Item item) item.setIngredientStack(stack);
 243 |         }
 244 |     }
```

### `targets/neoforge-26.1/src/main/resources/assets/apricityui/apricity/dndturn/tactical.html`：L16–L33

```text
  16 |   <section id="inventory-panel" class="combat-panel inventory-panel" style="display: none">
  17 |     <h2>随身物品</h2><div id="inventory"></div>
  18 |     <p class="muted">物品预览 · 战术消耗与武器选择尚未开放</p>
  19 |   </section>
  20 |   <footer class="combat-panel actions">
  21 |     <div class="resource-row"><span id="action"></span><span id="movement"></span><span id="reaction"></span><span id="environment" class="muted"></span><span id="target"></span></div>
  22 |     <div class="buttons">
  23 |       <button class="button" id="move">移动</button>
  24 |       <button class="button button-primary" id="attack">攻击</button>
  25 |       <button class="button" id="dash">疾走</button>
  26 |       <button class="button" id="dodge">回避</button>
  27 |       <button class="button" id="disengage">撤离</button>
  28 |       <button class="button" id="inventory-toggle">物品</button>
  29 |       <button class="button" id="end">结束回合</button>
  30 |       <button class="button" id="exit">退出</button>
  31 |     </div>
  32 |     <div id="description" class="muted">悬停动作查看规则说明。</div>
  33 |     <div id="status" class="muted"></div>
```

## S06 — 攻击键/移动键与快捷栏冲突处理；MOVE 只是开关移动许可

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java`：L90–L135

```text
  90 |         while (EXIT.consumeClick()) requestExit();
  91 |         while (MOVE.consumeClick()) requestMove();
  92 |         while (ATTACK.consumeClick()) {
  93 |             UUID target = TacticalOverlay.selectedTarget();
  94 |             if (target != null) send(CombatNetwork.IntentKind.ATTACK, target);
  95 |         }
  96 |         while (DASH.consumeClick()) send(CombatNetwork.IntentKind.DASH, null);
  97 |         while (DODGE.consumeClick()) send(CombatNetwork.IntentKind.DODGE, null);
  98 |         while (DISENGAGE.consumeClick()) send(CombatNetwork.IntentKind.DISENGAGE, null);
  99 |         while (END_TURN.consumeClick()) requestEndTurn();
 100 |         while (INVENTORY.consumeClick()) TacticalOverlay.toggleInventory();
 101 |         while (JUMP.consumeClick()) { /* Held state is forwarded by onKeyInput. */ }
 102 |     }
 103 | 
 104 |     /** Called after vanilla KeyMapping.set/click, before the next client input tick. */
 105 |     public static void onKeyInput(InputEvent.Key event) {
 106 |         if (!ClientControl.key(event.getKeyEvent(), event.getAction())) return;
 107 |         Minecraft game = Minecraft.getInstance();
 108 |         if (event.getAction() == GLFW.GLFW_RELEASE && JUMP.matches(event.getKeyEvent())) releaseJump();
 109 |         if (game.player == null || game.screen != null) return;
 110 |         boolean tactical = ClientCombatState.encounter() != null;
 111 |         boolean toggle = START.matches(event.getKeyEvent()) && START.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()));
 112 |         if (!tactical && !toggle) return;
 113 |         boolean handled = toggle;
 114 |         if (tactical) for (KeyMapping mapping : new KeyMapping[]{UI, EXIT, MOVE, ATTACK, DASH, DODGE,
 115 |                 DISENGAGE, END_TURN, INVENTORY, JUMP}) {
 116 |             if (mapping.matches(event.getKeyEvent())) handled = true;
 117 |         }
 118 |         if (!handled) return;
 119 |         KeyMapping winner = null;
 120 |         for (KeyMapping mapping : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
 121 |                 DISENGAGE, END_TURN, INVENTORY, JUMP}) {
 122 |             if (!mapping.matches(event.getKeyEvent()) || !mapping.isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()))) continue;
 123 |             if (winner == null) winner = mapping;
 124 |             else clear(mapping);
 125 |         }
 126 |         // Consume only overlapping vanilla bindings; preserve the user's saved key configuration.
 127 |         for (KeyMapping mapping : game.options.keyMappings)
 128 |             if (!isAction(mapping) && mapping.matches(event.getKeyEvent())) clear(mapping);
 129 |         if (toggle) clear(END_TURN);
 130 |         if (tactical && winner == JUMP && !ClientControl.blockMovement()) {
 131 |             game.options.keyJump.setDown(event.getAction() != GLFW.GLFW_RELEASE);
 132 |         }
 133 |         if (event.getAction() == GLFW.GLFW_REPEAT)
 134 |             for (KeyMapping mapping : new KeyMapping[]{START, UI, EXIT, MOVE, ATTACK, DASH, DODGE,
 135 |                     DISENGAGE, END_TURN, INVENTORY}) while (mapping.consumeClick()) { }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java`：L202–L224

```text
 202 |     private static void requestMove() {
 203 |             var state = ClientCombatState.encounter();
 204 |             if (state == null) return;
 205 |             switch (movePhase) {
 206 |                 case IDLE -> {
 207 |                     if (state.moving()) {
 208 |                         movementOperationId = state.movementOperationId();
 209 |                         movePhase = MovePhase.MOVING;
 210 |                         requestStop();
 211 |                     } else {
 212 |                         movementOperationId = UUID.randomUUID();
 213 |                         movementStartVersion = state.version();
 214 |                         movementEndVersion = -1;
 215 |                         movePhase = MovePhase.START_PENDING;
 216 |                         send(CombatNetwork.IntentKind.MOVE_BEGIN, null,
 217 |                             movementOperationId, movementStartVersion);
 218 |                     }
 219 |                 }
 220 |                 case START_PENDING -> send(CombatNetwork.IntentKind.MOVE_BEGIN, null,
 221 |                     movementOperationId, movementStartVersion);
 222 |                 case MOVING, STOP_PENDING -> requestStop();
 223 |             }
 224 |         }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java`：L240–L251

```text
 240 |     /** AUI and key bindings share the same pending movement/stop state machine. */
 241 |     public static void requestFromUi(CombatNetwork.IntentKind kind, UUID target) {
 242 |         Minecraft game = Minecraft.getInstance();
 243 |         if (game.player == null || game.getConnection() == null || game.screen != null) return;
 244 |         switch (kind) {
 245 |             case MOVE_BEGIN -> requestMove();
 246 |             case END_TURN -> requestEndTurn();
 247 |             case EXIT -> requestExit();
 248 |             case ATTACK -> send(kind, target);
 249 |             case DASH, DODGE, DISENGAGE -> send(kind, null);
 250 |             default -> throw new IllegalArgumentException("unsupported UI action");
 251 |         }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java`：L119–L127

```text
 119 |     public static boolean blockWorldActions() { return modal() || session || ClientCombatState.gameplayPaused(); }
 120 |     public static boolean blockMovement() {
 121 |         Minecraft mc = Minecraft.getInstance();
 122 |         return modal() || session && (mode != Mode.CHARACTER || mc.screen != null || !mc.isWindowActive()
 123 |             || mc.getCameraEntity() != cameraOwner || !ClientCombatState.movementAllowed());
 124 |     }
 125 |     public static boolean blockTurn() {
 126 |         Minecraft mc = Minecraft.getInstance();
 127 |         return modal() || session && (mode == Mode.CAMERA || mc.screen != null || !mc.isWindowActive());
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L836–L877

```text
 836 |     public OperationRecord.Result beginPlayerMove(ServerPlayer actor, UUID operationId,
 837 |                                                                  long expectedVersion) {
 838 |         requireThread();
 839 |         Objects.requireNonNull(operationId);
 840 |         UUID encounterId = engine.encounterOf(actor.getUUID());
 841 |         if (encounterId == null) throw new IllegalStateException("player is not a member");
 842 |         OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
 843 |         if (previous != null) {
 844 |             if (previous.snapshot().kind() != OperationRecord.Kind.MOVE
 845 |                 || !previous.snapshot().owner().equals(actor.getUUID())
 846 |                 || previous.snapshot().encounterVersion() != expectedVersion)
 847 |                 throw new IllegalStateException("operation ID payload conflict");
 848 |             return previous;
 849 |         }
 850 |         OperationRecord.Snapshot pending = engine.pendingOperation(encounterId, operationId);
 851 |         if (pending != null) {
 852 |             if (pending.kind() != OperationRecord.Kind.MOVE
 853 |                 || !pending.owner().equals(actor.getUUID())
 854 |                 || pending.encounterVersion() != expectedVersion)
 855 |                 throw new IllegalStateException("operation ID payload conflict");
 856 |             return null;
 857 |         }
 858 |         if (moveEndReceipts.containsKey(operationId))
 859 |             throw new IllegalStateException("operation ID payload conflict");
 860 |         CombatEngine.StateView state = engine.stateView(encounterId);
 861 |         if (state.version() != expectedVersion || state.phase() != EncounterPhase.ACTIVE
 862 |             || !actor.getUUID().equals(state.current()) || unsupportedPlayerMovement(actor)
 863 |             || !state.region().dimension().equals(actor.level().dimension().identifier().toString())
 864 |             || !state.region().containsPoint(pointOf(actor).x(), pointOf(actor).y(), pointOf(actor).z())
 865 |             || state.members().get(actor.getUUID()).movementTicks() < 1
 866 |             || playerMoves.containsKey(actor.getUUID()))
 867 |             throw new IllegalStateException("player movement is not authorized in this phase");
 868 |         BlockPos pos = actor.blockPosition();
 869 |         OperationRecord.Snapshot snapshot = operationSnapshot(operationId, null,
 870 |             encounterId, actor.getUUID(), actor.getUUID(), null, cumulativeServerTicks,
 871 |             expectedVersion, new GridCell(pos.getX(), pos.getY(), pos.getZ()), null,
 872 |             OperationRecord.Kind.MOVE);
 873 |         if (!engine.beginOperation(snapshot)) throw new IllegalStateException("movement operation rejected");
 874 |         playerMoves.put(actor.getUUID(), new PlayerMoveLease(encounterId, operationId, actor.getUUID()));
 875 |         sync(engine.stateView(encounterId));
 876 |         syncBodyStateTransitions();
 877 |         return null; // Accepted operation has no terminal result until observed movement is settled.
```

## S07 — 协议只能携带 target UUID；没有世界目标/物品选择/目的地载荷

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatNetwork.java`：L96–L153

```text
  96 |     public enum IntentKind {
  97 |         START(0), EXIT(1), MOVE_BEGIN(2), MOVE_END(3), ATTACK(4), END_TURN(5), RESULT_SYNC(6), DASH(7),
  98 |         DODGE(8), DISENGAGE(9);
  99 |         private final int code;
 100 |         IntentKind(int code) { this.code = code; }
 101 |         public int code() { return code; }
 102 |         public static IntentKind fromCode(int code) {
 103 |             for (IntentKind value : values()) if (value.code == code) return value;
 104 |             throw new IllegalArgumentException("unknown intent code");
 105 |         }
 106 |     }
 107 | 
 108 |     /** Client data is an untrusted intent; actor identity and target validation live on the server. */
 109 |     public record CombatIntent(UUID operationId, UUID generation, UUID encounterId,
 110 |                                   long expectedVersion, IntentKind kind, UUID targetId, int fromIndex)
 111 |         implements CustomPacketPayload {
 112 |         public CombatIntent(UUID operationId, UUID generation, UUID encounterId,
 113 |                             long expectedVersion, IntentKind kind, UUID targetId) {
 114 |             this(operationId, generation, encounterId, expectedVersion, kind, targetId, 0);
 115 |         }
 116 |         public CombatIntent {
 117 |             if (expectedVersion < 0 || fromIndex < 0 || kind != IntentKind.RESULT_SYNC && fromIndex != 0)
 118 |                 throw new IllegalArgumentException("invalid intent version or result cursor");
 119 |         }
 120 |         public static CombatIntent resultSync(UUID operationId, UUID generation, UUID encounterId,
 121 |                                                long expectedVersion, int fromIndex) {
 122 |             return new CombatIntent(operationId, generation, encounterId, expectedVersion,
 123 |                 IntentKind.RESULT_SYNC, null, fromIndex);
 124 |         }
 125 |         public static CombatIntent start(UUID operationId) {
 126 |             return new CombatIntent(operationId, null, null, 0, IntentKind.START, null);
 127 |         }
 128 |         public static final Type<CombatIntent> TYPE = new Type<>(
 129 |             Identifier.fromNamespaceAndPath(DNDTurnNeoForge261.MOD_ID, "combat_intent"));
 130 |         public static final StreamCodec<ByteBuf, CombatIntent> STREAM_CODEC = StreamCodec.of(
 131 |             (buffer, intent) -> {
 132 |                 writeUuid(buffer, intent.operationId());
 133 |                 buffer.writeBoolean(intent.generation() != null);
 134 |                 if (intent.generation() != null) writeUuid(buffer, intent.generation());
 135 |                 buffer.writeBoolean(intent.encounterId() != null);
 136 |                 if (intent.encounterId() != null) writeUuid(buffer, intent.encounterId());
 137 |                 buffer.writeLong(intent.expectedVersion());
 138 |                 buffer.writeInt(intent.fromIndex());
 139 |                 buffer.writeByte(intent.kind().code());
 140 |                 buffer.writeBoolean(intent.targetId() != null);
 141 |                 if (intent.targetId() != null) writeUuid(buffer, intent.targetId());
 142 |             }, buffer -> {
 143 |                 UUID operationId = readUuid(buffer);
 144 |                 UUID generation = buffer.readBoolean() ? readUuid(buffer) : null;
 145 |                 UUID encounterId = buffer.readBoolean() ? readUuid(buffer) : null;
 146 |                 long version = buffer.readLong();
 147 |                 int fromIndex = buffer.readInt();
 148 |                 int kindIndex = buffer.readUnsignedByte();
 149 |                 UUID target = buffer.readBoolean() ? readUuid(buffer) : null;
 150 |                 return new CombatIntent(operationId, generation, encounterId, version,
 151 |                     IntentKind.fromCode(kindIndex), target, fromIndex);
 152 |             });
 153 |         @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatIntentHandler.java`：L89–L105

```text
  89 |                 throw new IllegalStateException("combat session is no longer active");
  90 |             switch (intent.kind()) {
  91 |                 case EXIT -> {
  92 |                     service.exitEncounter(player, intent.encounterId(), intent.operationId(),
  93 |                         intent.expectedVersion());
  94 |                 }
  95 |                 case MOVE_BEGIN -> service.beginPlayerMove(player, intent.operationId(),
  96 |                     intent.expectedVersion());
  97 |                 case MOVE_END -> {
  98 |                     var result = service.finishPlayerMove(player, intent.operationId(),
  99 |                         intent.expectedVersion());
 100 |                     player.sendOverlayMessage(Component.literal("DNDTurn: " + result.reason()));
 101 |                 }
 102 |                 case ATTACK -> {
 103 |                     if (intent.targetId() == null) throw new IllegalStateException("no target under crosshair");
 104 |                     var result = service.attack(player, intent.targetId(), intent.operationId(),
 105 |                         intent.expectedVersion());
```

## S08 — 服务端为选定目标执行即时近战；限定 Player/Zombie；超距直接拒绝

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L1208–L1215

```text
1208 |     private static boolean inMeleeReach(LivingEntity actor, LivingEntity target) {
1209 |         BlockPos from = actor.blockPosition();
1210 |         BlockPos to = target.blockPosition();
1211 |         return from.distManhattan(to) <= 3
1212 |             && new GridCell(from.getX(), from.getY(), from.getZ()).chebyshev(
1213 |                 new GridCell(to.getX(), to.getY(), to.getZ())) <= CombatRules.MELEE_RANGE
1214 |             && actor.hasLineOfSight(target);
1215 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L1816–L1875

```text
1816 |     public OperationRecord.Result attack(LivingEntity actor, UUID targetId,
1817 |                                                                   UUID operationId, long expectedVersion) {
1818 |         requireThread();
1819 |         Objects.requireNonNull(targetId);
1820 |         Objects.requireNonNull(operationId);
1821 |         UUID encounterId = engine.encounterOf(actor.getUUID());
1822 |         if (encounterId == null) throw new IllegalStateException("attacker is not a member");
1823 |         OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
1824 |         if (previous != null) {
1825 |             OperationRecord.Snapshot snapshot = previous.snapshot();
1826 |             if (snapshot.kind() != OperationRecord.Kind.ATTACK || !snapshot.owner().equals(actor.getUUID())
1827 |                 || !targetId.equals(snapshot.target()) || snapshot.encounterVersion() != expectedVersion)
1828 |                 throw new IllegalStateException("operation ID payload conflict");
1829 |             return previous;
1830 |         }
1831 |         CombatEngine.StateView state = engine.stateView(encounterId);
1832 |         if (state.version() != expectedVersion) throw new IllegalStateException("stale encounter version");
1833 |         if (state.phase() != EncounterPhase.CANDIDATE && state.phase() != EncounterPhase.ACTIVE)
1834 |             throw new IllegalStateException("not a member attack phase");
1835 |         if (!actor.getUUID().equals(state.current()) || !state.members().containsKey(targetId))
1836 |             throw new IllegalStateException("attacker or target not authorized");
1837 |         if (!(actor.level() instanceof ServerLevel level) || actor.isDeadOrDying())
1838 |             throw new IllegalStateException("attacker is not a loaded living member");
1839 |         Entity found = level.getEntity(targetId);
1840 |         if (!(found instanceof LivingEntity target)
1841 |             || !(actor instanceof ServerPlayer && found.getType() == EntityType.ZOMBIE
1842 |                 || actor instanceof Zombie && found instanceof ServerPlayer)
1843 |             || target.isDeadOrDying())
1844 |             throw new IllegalStateException("only Player/Zombie melee is supported");
1845 |         Vec3 actorCenter = actor.getBoundingBox().getCenter();
1846 |         Vec3 targetCenter = target.getBoundingBox().getCenter();
1847 |         if (!state.region().dimension().equals(level.dimension().identifier().toString())
1848 |             || !state.region().containsPoint(actorCenter.x, actorCenter.y, actorCenter.z)
1849 |             || !state.region().containsPoint(targetCenter.x, targetCenter.y, targetCenter.z)
1850 |             || !inMeleeReach(actor, target)) throw new IllegalStateException("target is outside supported melee reach");
1851 |         boolean openingAdvantage = actor instanceof ServerPlayer && target instanceof Zombie zombie
1852 |             && !engine.hasAttemptedAttack(encounterId, actor.getUUID())
1853 |             && zombie.getTarget() != actor;
1854 |         double weaponAttribute = actor instanceof ServerPlayer
1855 |             ? actor.getMainHandItem().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
1856 |                 .compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND)
1857 |             : actor.getAttributeValue(Attributes.ATTACK_DAMAGE);
1858 |         double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
1859 |         if (!Double.isFinite(weaponAttribute) || !Double.isFinite(toughness)
1860 |             || weaponAttribute > Integer.MAX_VALUE || toughness > Integer.MAX_VALUE)
1861 |             throw new IllegalStateException("unsupported weapon or toughness attribute");
1862 |         double weaponDamage = Math.max(0, weaponAttribute);
1863 |         int reduction = CombatRules.damageReduction(toughness);
1864 |         CombatRules.damageAfterReduction(weaponDamage, reduction, true);
1865 |         boolean holdingShield = target.getMainHandItem().is(Items.SHIELD)
1866 |             || target.getOffhandItem().is(Items.SHIELD);
1867 |         int ac = CombatRules.armorClass(target.getArmorValue(), holdingShield);
1868 |         var origin = actor.blockPosition();
1869 |         var destination = target.blockPosition();
1870 |         OperationRecord.Snapshot root = operationSnapshot(operationId, null,
1871 |             encounterId, actor.getUUID(), actor.getUUID(), targetId, cumulativeServerTicks, expectedVersion,
1872 |             new GridCell(origin.getX(), origin.getY(), origin.getZ()),
1873 |             new GridCell(destination.getX(), destination.getY(), destination.getZ()), OperationRecord.Kind.ATTACK);
1874 |         if (!engine.beginOperation(root)) throw new IllegalStateException("attack already executing or unauthorized");
1875 |         UUID permitId = null;
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L1881–L1900

```text
1881 |         CombatRules.AttackRoll roll = CombatRules.rollAttack(
1882 |             gameTestAttackRandom.getOrDefault(encounterId, attackRandom), rollMode, ac);
1883 |         if (!roll.hit()) {
1884 |             DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
1885 |                 weaponDamage, reduction, 0, false, 0, 0,
1886 |                 damageEvidence(actor, state, DamageTrace.Stage.MISS, null, List.of()));
1887 |             OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
1888 |                 OperationRecord.Outcome.COMPLETED, "miss: die=" + roll.die() + " AC=" + ac, 0, 0, true, trace);
1889 |             sync(engine.stateView(encounterId));
1890 |             return result;
1891 |         }
1892 |         int tacticalDamage = CombatRules.damageAfterReduction(weaponDamage, reduction, roll.critical());
1893 |         if (tacticalDamage == 0) {
1894 |             DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
1895 |                 weaponDamage, reduction, 0, false, 0, 0,
1896 |                 damageEvidence(actor, state, DamageTrace.Stage.ZERO_DAMAGE, null, List.of()));
1897 |             OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
1898 |                 OperationRecord.Outcome.COMPLETED, "hit with zero tactical damage", 0, 0, true, trace);
1899 |             sync(engine.stateView(encounterId));
1900 |             return result;
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L1902–L1940

```text
1902 |         permitId = UUID.randomUUID();
1903 |         engine.issueEffectPermit(new CombatEngine.EffectPermit(permitId, encounterId, operationId,
1904 |             actor.getUUID(), Set.of(targetId), Set.of(EncounterPhase.ACTIVE), 1,
1905 |             engine.stateView(encounterId).round()));
1906 |         damageId = UUID.randomUUID();
1907 |         OperationRecord.Snapshot child = operationSnapshot(damageId, operationId,
1908 |             encounterId, actor.getUUID(), actor.getUUID(), targetId, cumulativeServerTicks,
1909 |             engine.stateView(encounterId).version(), root.sourceCell(), root.targetCell(),
1910 |             OperationRecord.Kind.DAMAGE);
1911 |         if (!engine.beginOperation(child, permitId)) throw new IllegalStateException("damage permit rejected");
1912 |         DamageSource source = level.damageSources().source(TacticalDamageContext.DAMAGE_TYPE, actor);
1913 |         float healthBefore = target.getHealth();
1914 |         float absorptionBefore = target.getAbsorptionAmount();
1915 |         Map<EquipmentKey, EquipmentValue> equipmentBefore = equipmentSnapshot(actor, target);
1916 |         effectStarted = true;
1917 |         worldEffectDepth++;
1918 |         try {
1919 |             var observed = TacticalDamageContext.hurtObserved(level, target, source, tacticalDamage,
1920 |                 settingsFor(encounterId).tacticalKnockbackEnabled(), damageId);
1921 |             boolean accepted = observed.accepted();
1922 |             if (accepted && actor instanceof ServerPlayer player) {
1923 |                 ItemStack weapon = player.getMainHandItem();
1924 |                 if (weapon.hurtEnemy(target, player)) weapon.postHurtEnemy(target, player);
1925 |             }
1926 |             float healthLoss = Math.max(0, healthBefore - target.getHealth());
1927 |             float absorptionLoss = Math.max(0, absorptionBefore - target.getAbsorptionAmount());
1928 |             OperationRecord.Outcome effectOutcome = accepted ? OperationRecord.Outcome.COMPLETED
1929 |                 : healthLoss > 0 || absorptionLoss > 0 ? OperationRecord.Outcome.PARTIAL
1930 |                 : OperationRecord.Outcome.REJECTED;
1931 |             DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
1932 |                 weaponDamage, reduction, tacticalDamage, accepted, absorptionLoss, healthLoss,
1933 |                 damageEvidence(actor, state, accepted ? DamageTrace.Stage.VANILLA_ACCEPTED
1934 |                     : DamageTrace.Stage.VANILLA_REJECTED, observed,
1935 |                     equipmentChanges(equipmentBefore, equipmentSnapshot(actor, target))));
1936 |             engine.publish(encounterId, damageId, 0, effectOutcome,
1937 |                 "vanilla hurtServer=" + accepted + " absorptionLoss=" + absorptionLoss
1938 |                     + " healthLoss=" + healthLoss, 0, healthLoss, true);
1939 |             return engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.COMPLETED,
1940 |                 "attack hit: die=" + roll.die() + " effect=" + effectOutcome, 0, healthLoss, true, trace);
```

### `common/src/main/java/cc/sighs/dndturn/combat/CombatRules.java`：L8–L13

```text
   8 |     /** Captured with damage results; advance when the implemented rule semantics change. */
   9 |     public static final String RULES_REVISION = "0.9";
  10 |     public static final int ATTACK_BONUS = 5;
  11 |     public static final int SHIELD_AC_BONUS = 2;
  12 |     public static final int MELEE_RANGE = 1;
  13 |     public static final int RANGED_RANGE = 6;
```

## S09 — 动作资源模型及计费种类

### `common/src/main/java/cc/sighs/dndturn/combat/ActionEconomy.java`：L3–L43

```text
   3 | /** Resources are spent at one operation boundary, never by a client callback. */
   4 | public final class ActionEconomy {
   5 |     private int movementTicks;
   6 |     private boolean action = true;
   7 |     private boolean reaction = true;
   8 | 
   9 |     public ActionEconomy(int movementTicks) { reset(movementTicks); }
  10 | 
  11 |     public void reset(int ticks) {
  12 |         if (ticks < 0) throw new IllegalArgumentException("negative movement");
  13 |         movementTicks = ticks;
  14 |         action = true;
  15 |         reaction = true;
  16 |     }
  17 | 
  18 |     public int movementTicks() { return movementTicks; }
  19 |     public boolean hasAction() { return action; }
  20 |     public boolean hasReaction() { return reaction; }
  21 | 
  22 |     public boolean spendMovement(int actualTicks) {
  23 |         if (actualTicks < 0 || actualTicks > movementTicks) return false;
  24 |         movementTicks -= actualTicks;
  25 |         return true;
  26 |     }
  27 | 
  28 |     public void addMovement(int ticks) {
  29 |         if (ticks < 0) throw new IllegalArgumentException("negative movement");
  30 |         movementTicks = Math.addExact(movementTicks, ticks);
  31 |     }
  32 | 
  33 |     public boolean spendAction() {
  34 |         if (!action) return false;
  35 |         action = false;
  36 |         return true;
  37 |     }
  38 | 
  39 |     public boolean spendReaction() {
  40 |         if (!reaction) return false;
  41 |         reaction = false;
  42 |         return true;
  43 |     }
```

### `common/src/main/java/cc/sighs/dndturn/combat/OperationRecord.java`：L6–L24

```text
   6 | /** Value-only capture and immutable result. No Minecraft object crosses this boundary. */
   7 | public final class OperationRecord {
   8 |     private OperationRecord() {}
   9 | 
  10 |     public enum Kind { START, JOIN, MERGE, MOVE, ATTACK, DASH, DODGE, DISENGAGE, HELP, END_TURN, DAMAGE, ENVIRONMENT, INTERRUPT }
  11 |     public enum Outcome {
  12 |         ACCEPTED(0), COMPLETED(1), PARTIAL(2), REJECTED(3), INTERRUPTED(4), UNKNOWN(5);
  13 |         private final int code;
  14 |         Outcome(int code) { this.code = code; }
  15 |         public int code() { return code; }
  16 |         public static Outcome fromCode(int code) {
  17 |             for (Outcome value : values()) if (value.code == code) return value;
  18 |             throw new IllegalArgumentException("unknown outcome code: " + code);
  19 |         }
  20 |     }
  21 | 
  22 |     public record Snapshot(UUID operationId, UUID parentId, UUID encounterId, UUID owner,
  23 |                            UUID source, UUID target, long serverTick, long encounterVersion,
  24 |                            GridCell sourceCell, GridCell targetCell, Kind kind, UUID observationEpoch) {
```

### `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`：L1331–L1333

```text
1331 |     private static boolean requiresAction(OperationRecord.Kind kind) {
1332 |         return switch (kind) { case ATTACK, DASH, DODGE, DISENGAGE, HELP -> true; default -> false; };
1333 |     }
```

## S10 — 客户端与服务端都拦截了原版世界行为；没有对应战术执行替代

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/ClientGameplayInputMixin.java`：L12–L29

```text
  12 | /** Suppress prediction at its actual entrypoints; packet authorization remains server-owned. */
  13 | @Mixin(MultiPlayerGameMode.class)
  14 | public abstract class ClientGameplayInputMixin {
  15 |     @Inject(method = {"startDestroyBlock", "continueDestroyBlock", "destroyBlock"},
  16 |         at = @At("HEAD"), cancellable = true)
  17 |     private void dndturn$destroy(CallbackInfoReturnable<Boolean> ci) {
  18 |         if (ClientControl.blockWorldActions()) ci.setReturnValue(false);
  19 |     }
  20 | 
  21 |     @Inject(method = {"useItem", "useItemOn", "interact"}, at = @At("HEAD"), cancellable = true)
  22 |     private void dndturn$use(CallbackInfoReturnable<InteractionResult> ci) {
  23 |         if (ClientControl.blockWorldActions()) ci.setReturnValue(InteractionResult.PASS);
  24 |     }
  25 | 
  26 |     @Inject(method = {"attack", "piercingAttack"}, at = @At("HEAD"), cancellable = true)
  27 |     private void dndturn$attack(CallbackInfo ci) {
  28 |         if (ClientControl.blockWorldActions()) ci.cancel();
  29 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalMinecraftInputMixin.java`：L11–L20

```text
  11 | @Mixin(Minecraft.class)
  12 | public abstract class TacticalMinecraftInputMixin {
  13 |     @Inject(method = "handleKeybinds", at = @At("HEAD"))
  14 |     private void dndturn$queues(CallbackInfo ci) { ClientControl.beforeKeybinds(); }
  15 |     @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
  16 |     private void dndturn$attack(CallbackInfoReturnable<Boolean> ci) {
  17 |         if (ClientControl.blockWorldActions()) ci.setReturnValue(false);
  18 |     }
  19 |     @Inject(method = {"continueAttack", "startUseItem", "pickBlockOrEntity"}, at = @At("HEAD"), cancellable = true)
  20 |     private void dndturn$world(CallbackInfo ci) { if (ClientControl.blockWorldActions()) ci.cancel(); }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaInputPolicy.java`：L14–L23

```text
  14 |     public static boolean controlled(ServerPlayer player) {
  15 |         ServerCombatService service = ServerCombatService.existing(player.level().getServer());
  16 |         return MinecraftCombatRuntime.isGameplayInputPaused(player)
  17 |             || service != null && service.isMember(player.getUUID());
  18 |     }
  19 | 
  20 |     public static boolean mayOrganize(ServerPlayer player) {
  21 |         ServerCombatService service = ServerCombatService.existing(player.level().getServer());
  22 |         return service != null && service.mayOrganizeInventory(player);
  23 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/ServerGamePacketListenerMixin.java`：L90–L124

```text
  90 |     @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
  91 |     private void dndturn$playerAction(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
  92 |         if (!player.level().getServer().isSameThread() || !VanillaInputPolicy.controlled(player)) return;
  93 |         if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
  94 |             && VanillaInputPolicy.mayOrganize(player)) return;
  95 |         switch (packet.getAction()) {
  96 |             case START_DESTROY_BLOCK, STOP_DESTROY_BLOCK, ABORT_DESTROY_BLOCK ->
  97 |                 VanillaInputPolicy.rejectPrediction(player, packet.getSequence(), packet.getPos());
  98 |             default -> VanillaInputPolicy.correctInventory(player);
  99 |         }
 100 |         callback.cancel();
 101 |     }
 102 | 
 103 |     @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
 104 |     private void dndturn$useItemOn(ServerboundUseItemOnPacket packet, CallbackInfo callback) {
 105 |         if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)) {
 106 |             VanillaInputPolicy.rejectPrediction(player, packet.getSequence(), packet.getHitResult().getBlockPos());
 107 |             VanillaInputPolicy.rejectPrediction(player, packet.getSequence(),
 108 |                 packet.getHitResult().getBlockPos().relative(packet.getHitResult().getDirection()));
 109 |             callback.cancel();
 110 |         }
 111 |     }
 112 | 
 113 |     @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
 114 |     private void dndturn$useItem(ServerboundUseItemPacket packet, CallbackInfo callback) {
 115 |         if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)) {
 116 |             VanillaInputPolicy.rejectPrediction(player, packet.getSequence(), null);
 117 |             callback.cancel();
 118 |         }
 119 |     }
 120 | 
 121 |     @Inject(method = "handleInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
 122 |     private void dndturn$interact(ServerboundInteractPacket packet, CallbackInfo callback) {
 123 |         if (dndturn$pauseOtherInput()) callback.cancel();
 124 |     }
```

## S11 — 调度不能直接嵌套 MOVE 与 ATTACK；动作在根操作开始时扣除

### `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`：L976–L1061

```text
 976 |     /** Effect operations must present a server-issued capability. */
 977 |     public boolean beginOperation(OperationRecord.Snapshot snapshot, UUID permitId) {
 978 |         Objects.requireNonNull(snapshot);
 979 |         if (closedEncounters.containsKey(snapshot.encounterId())) {
 980 |             OperationRecord.Result previousResult = resultFor(snapshot.encounterId(), snapshot.operationId());
 981 |             if (previousResult == null) throw new IllegalStateException("closed encounter has no such operation");
 982 |             if (!previousResult.snapshot().equals(snapshot)) throw new IllegalStateException("operation payload conflict");
 983 |             return false;
 984 |         }
 985 |         Encounter encounter = require(snapshot.encounterId());
 986 |         OperationRecord.Snapshot previous = encounter.causes.get(snapshot.operationId());
 987 |         if (previous != null) {
 988 |             if (!previous.equals(snapshot)) throw new IllegalStateException("operation payload conflict");
 989 |             return false;
 990 |         }
 991 |         if (snapshot.encounterVersion() != encounter.version) return false;
 992 |         if (snapshot.parentId() != null) {
 993 |             OperationRecord.Snapshot parent = encounter.causes.get(snapshot.parentId());
 994 |             UUID rootId = parent == null ? null : rootOf(encounter, parent);
 995 |             PermitState permit = encounter.permits.get(permitId);
 996 |             boolean damage = parent != null && permit != null && snapshot.kind() == OperationRecord.Kind.DAMAGE
 997 |                 && (parent.kind() == OperationRecord.Kind.ATTACK || parent.kind() == OperationRecord.Kind.DAMAGE
 998 |                     || parent.kind() == OperationRecord.Kind.INTERRUPT)
 999 |                 && snapshot.owner().equals(parent.owner())
1000 |                 && permit.permit.parentId().equals(snapshot.parentId())
1001 |                 && permit.permit.source().equals(snapshot.source())
1002 |                 && permit.permit.targets().contains(snapshot.target())
1003 |                 && permit.permit.phases().contains(encounter.phase)
1004 |                 && encounter.round <= permit.effectiveValidThroughRound
1005 |                 && permit.used < permit.permit.maxUses()
1006 |                 && encounter.members.containsKey(snapshot.target());
1007 |             boolean reaction = parent != null && snapshot.kind() == OperationRecord.Kind.INTERRUPT
1008 |                 && encounter.phase == EncounterPhase.ACTIVE && encounter.pending.containsKey(parent.operationId())
1009 |                 && (parent.kind() == OperationRecord.Kind.ATTACK || parent.kind() == OperationRecord.Kind.DAMAGE)
1010 |                 && snapshot.owner().equals(parent.target()) && Objects.equals(snapshot.target(), parent.owner())
1011 |                 && encounter.members.containsKey(snapshot.owner())
1012 |                 && snapshot.owner().equals(snapshot.source())
1013 |                 && encounter.members.get(snapshot.owner()).economy.hasReaction();
1014 |             if ((!damage && !reaction) || (reaction && permitId != null)
1015 |                 || childDepth(encounter, parent) >= 8 || rootId == null
1016 |                 || encounter.childrenByRoot.getOrDefault(rootId, 0) >= 64) return false;
1017 |             long nextVersion = Math.addExact(encounter.version, 1);
1018 |             if (reaction) encounter.members.get(snapshot.owner()).economy.spendReaction();
1019 |             if (damage) permit.used++;
1020 |             encounter.childrenByRoot.merge(rootId, 1, Integer::sum);
1021 |             encounter.pending.put(snapshot.operationId(), snapshot);
1022 |             encounter.causes.put(snapshot.operationId(), snapshot);
1023 |             encounter.version = nextVersion;
1024 |             return true;
1025 |         }
1026 |         if (permitId != null || !encounter.members.containsKey(snapshot.owner())) return false;
1027 |         if (encounter.phase != EncounterPhase.ACTIVE && encounter.phase != EncounterPhase.CANDIDATE) return false;
1028 |         if (encounter.phase == EncounterPhase.CANDIDATE && snapshot.kind() != OperationRecord.Kind.ATTACK
1029 |             && snapshot.kind() != OperationRecord.Kind.END_TURN) return false;
1030 |         if (!encounter.pending.isEmpty() || snapshot.source() == null || !snapshot.source().equals(snapshot.owner())
1031 |             || snapshot.kind() == OperationRecord.Kind.DAMAGE || snapshot.kind() == OperationRecord.Kind.INTERRUPT
1032 |             || snapshot.kind() == OperationRecord.Kind.ENVIRONMENT || snapshot.kind() == OperationRecord.Kind.MERGE
1033 |             || snapshot.kind() == OperationRecord.Kind.START || snapshot.kind() == OperationRecord.Kind.JOIN) return false;
1034 |         UUID current = encounter.order.get(encounter.cursor);
1035 |         if (!snapshot.owner().equals(current)) return false;
1036 |         Member member = encounter.members.get(current);
1037 |         if (snapshot.kind() == OperationRecord.Kind.ATTACK
1038 |             && (snapshot.target() == null || !encounter.members.containsKey(snapshot.target())
1039 |                 || snapshot.target().equals(snapshot.owner()))) return false;
1040 |         if (requiresAction(snapshot.kind()) && !member.economy.hasAction()) return false;
1041 |         if (snapshot.kind() == OperationRecord.Kind.DASH)
1042 |             Math.addExact(member.economy.movementTicks(), encounter.movementTicksPerTurn);
1043 |         long nextVersion = Math.addExact(encounter.version, 1);
1044 |         Map<UUID, InitiativeRoll> initiativeRolls = encounter.phase == EncounterPhase.CANDIDATE
1045 |             && snapshot.kind() == OperationRecord.Kind.ATTACK ? sampleInitiatives(encounter) : Map.of();
1046 |         if (requiresAction(snapshot.kind())) member.economy.spendAction();
1047 |         if (snapshot.kind() == OperationRecord.Kind.DODGE) member.dodging = true;
1048 |         if (snapshot.kind() == OperationRecord.Kind.DISENGAGE) member.disengaged = true;
1049 |         if (snapshot.kind() == OperationRecord.Kind.DASH) member.economy.addMovement(encounter.movementTicksPerTurn);
1050 |         if (encounter.phase == EncounterPhase.CANDIDATE && snapshot.kind() == OperationRecord.Kind.ATTACK) {
1051 |             encounter.hostile.add(new Relation(snapshot.owner(), snapshot.target()));
1052 |             activate(encounter, initiativeRolls);
1053 |             if (!snapshot.owner().equals(currentId(encounter)))
1054 |                 member.preserveSpentActionOnNextTurn = true;
1055 |         }
1056 |         encounter.pending.put(snapshot.operationId(), snapshot);
1057 |         encounter.causes.put(snapshot.operationId(), snapshot);
1058 |         if (snapshot.kind() == OperationRecord.Kind.ATTACK)
1059 |             encounter.attackersWithRegisteredAttempt.add(snapshot.owner());
1060 |         encounter.version = nextVersion;
1061 |         return true;
```

## S12 — 正常入口进入候选阶段；候选阶段移动被禁止

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L1476–L1510

```text
1476 |         AABB search = new AABB(discovery.minX(), discovery.minY(), discovery.minZ(),
1477 |             discovery.maxX(), discovery.maxY(), discovery.maxZ());
1478 |         Entity target = level.getEntities((Entity) null, search, entity -> entity.getType() == EntityType.ZOMBIE)
1479 |             .stream()
1480 |             .filter(entity -> discovery.contains(pointOf(entity)))
1481 |             .filter(entity -> engine.encounterOf(entity.getUUID()) == null)
1482 |             .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(initiator)))
1483 |             .orElse(null);
1484 |         Set<UUID> members = new HashSet<>();
1485 |         for (UUID playerId : approvedPlayers) {
1486 |             ServerPlayer player = server.getPlayerList().getPlayer(playerId);
1487 |             if (player == null || !player.isAlive() || player.level() != level
1488 |                 || MinecraftCombatRuntime.isLocalMember(server, playerId))
1489 |                 throw new IllegalStateException("consenting player became unavailable");
1490 |             var point = pointOf(player);
1491 |             if (!region.containsPoint(point.x(), point.y(), point.z()))
1492 |                 throw new IllegalStateException("consenting player left the proposed region");
1493 |             // Already participating players consent to the new proposal but remain in their
1494 |             // authoritative encounter until the shared safe merge boundary commits.
1495 |             if (!isMember(playerId)) members.add(playerId);
1496 |         }
1497 |         if (target != null) members.add(target.getUUID());
1498 |         UUID encounterId = UUID.randomUUID();
1499 |         long nextSequence = Math.addExact(nextSessionSequence, 1);
1500 |         var captured = CombatPersistenceEnvelope.CapturedSettings.from(config);
1501 |         Set<Long> chunks = exactRegionChunks(region);
1502 |         engine.beginCandidate(encounterId, region, members,
1503 |             config.movementTicks(), config.environmentTicks());
1504 |         capturedSettings.put(encounterId, captured);
1505 |         sessionSequences.put(encounterId, nextSequence);
1506 |         nextSessionSequence = nextSequence;
1507 |         regions.put(encounterId, region);
1508 |         regionChunks.put(encounterId, chunks);
1509 |         // Merge planning runs at beforeLevelTick. Queue capture follows the receipt commit.
1510 |         return engine.stateView(encounterId);
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L860–L867

```text
 860 |         CombatEngine.StateView state = engine.stateView(encounterId);
 861 |         if (state.version() != expectedVersion || state.phase() != EncounterPhase.ACTIVE
 862 |             || !actor.getUUID().equals(state.current()) || unsupportedPlayerMovement(actor)
 863 |             || !state.region().dimension().equals(actor.level().dimension().identifier().toString())
 864 |             || !state.region().containsPoint(pointOf(actor).x(), pointOf(actor).y(), pointOf(actor).z())
 865 |             || state.members().get(actor.getUUID()).movementTicks() < 1
 866 |             || playerMoves.containsKey(actor.getUUID()))
 867 |             throw new IllegalStateException("player movement is not authorized in this phase");
```

### `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`：L1026–L1055

```text
1026 |         if (permitId != null || !encounter.members.containsKey(snapshot.owner())) return false;
1027 |         if (encounter.phase != EncounterPhase.ACTIVE && encounter.phase != EncounterPhase.CANDIDATE) return false;
1028 |         if (encounter.phase == EncounterPhase.CANDIDATE && snapshot.kind() != OperationRecord.Kind.ATTACK
1029 |             && snapshot.kind() != OperationRecord.Kind.END_TURN) return false;
1030 |         if (!encounter.pending.isEmpty() || snapshot.source() == null || !snapshot.source().equals(snapshot.owner())
1031 |             || snapshot.kind() == OperationRecord.Kind.DAMAGE || snapshot.kind() == OperationRecord.Kind.INTERRUPT
1032 |             || snapshot.kind() == OperationRecord.Kind.ENVIRONMENT || snapshot.kind() == OperationRecord.Kind.MERGE
1033 |             || snapshot.kind() == OperationRecord.Kind.START || snapshot.kind() == OperationRecord.Kind.JOIN) return false;
1034 |         UUID current = encounter.order.get(encounter.cursor);
1035 |         if (!snapshot.owner().equals(current)) return false;
1036 |         Member member = encounter.members.get(current);
1037 |         if (snapshot.kind() == OperationRecord.Kind.ATTACK
1038 |             && (snapshot.target() == null || !encounter.members.containsKey(snapshot.target())
1039 |                 || snapshot.target().equals(snapshot.owner()))) return false;
1040 |         if (requiresAction(snapshot.kind()) && !member.economy.hasAction()) return false;
1041 |         if (snapshot.kind() == OperationRecord.Kind.DASH)
1042 |             Math.addExact(member.economy.movementTicks(), encounter.movementTicksPerTurn);
1043 |         long nextVersion = Math.addExact(encounter.version, 1);
1044 |         Map<UUID, InitiativeRoll> initiativeRolls = encounter.phase == EncounterPhase.CANDIDATE
1045 |             && snapshot.kind() == OperationRecord.Kind.ATTACK ? sampleInitiatives(encounter) : Map.of();
1046 |         if (requiresAction(snapshot.kind())) member.economy.spendAction();
1047 |         if (snapshot.kind() == OperationRecord.Kind.DODGE) member.dodging = true;
1048 |         if (snapshot.kind() == OperationRecord.Kind.DISENGAGE) member.disengaged = true;
1049 |         if (snapshot.kind() == OperationRecord.Kind.DASH) member.economy.addMovement(encounter.movementTicksPerTurn);
1050 |         if (encounter.phase == EncounterPhase.CANDIDATE && snapshot.kind() == OperationRecord.Kind.ATTACK) {
1051 |             encounter.hostile.add(new Relation(snapshot.owner(), snapshot.target()));
1052 |             activate(encounter, initiativeRolls);
1053 |             if (!snapshot.owner().equals(currentId(encounter)))
1054 |                 member.preserveSpentActionOnNextTurn = true;
1055 |         }
```

## S13 — 当前回归测试验证的是自由镜头与候选阶段禁止移动

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControlRegression.java`：L94–L110

```text
  94 |             if (age == 10) {
  95 |                 require(ClientControl.active() && ClientControl.mode() == ClientControl.Mode.CAMERA, "automatic virtual controller");
  96 |                 require(!mc.mouseHandler.isMouseGrabbed(), "camera pointer");
  97 |                 actor = mc.player.position(); yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
  98 |                 camera = ClientControl.position(); budget = ClientCombatState.encounter().movementTicks();
  99 |                 slot = mc.player.getInventory().getSelectedSlot(); resultCount = ClientCombatState.encounter().resultCount();
 100 |                 packets.clear();
 101 |                 ClientControl.key(new KeyEvent(GLFW.GLFW_KEY_W, 0, 0), GLFW.GLFW_PRESS);
 102 |             }
 103 |             if (age == 25) {
 104 |                 ClientControl.key(new KeyEvent(GLFW.GLFW_KEY_W, 0, 0), GLFW.GLFW_RELEASE);
 105 |                 require(ClientControl.position().distanceToSqr(camera) > .01, "camera translation");
 106 |                 unchanged();
 107 |                 point(0.42, .45); button(1, 1); point(.48, .50); button(1, 0);
 108 |                 mouse().dndturn$scroll(mc.getWindow().handle(), 0, 1);
 109 |                 require(ClientControl.yaw() != yaw || ClientControl.pitch() != pitch, "camera rotation");
 110 |                 unchanged();
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControlRegression.java`：L137–L148

```text
 137 |             if (age == 30) {
 138 |                 point(.4, .45); button(0, 1);
 139 |                 mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
 140 |                 ClientControl.reconcile(); button(0, 0); mc.setScreen(null); ClientControl.reconcile();
 141 |                 require(ClientControl.mode() == ClientControl.Mode.CAMERA && !mc.mouseHandler.isMouseGrabbed(), "screen preserves mode");
 142 |                 require(!mc.options.keyAttack.isDown() && !mc.options.keyAttack.consumeClick(), "gesture release queue");
 143 |                 require(!mc.options.keyUse.isDown() && !mc.options.keyUse.consumeClick(), "held use survived UI handoff");
 144 |                 unchanged();
 145 |                 ClientControl.toggleMode();
 146 |                 require(ClientControl.mode() == ClientControl.Mode.CHARACTER && mc.mouseHandler.isMouseGrabbed(), "explicit character control");
 147 |                 require(ClientControl.blockMovement(), "candidate has no movement permit");
 148 |                 ClientControl.toggleMode();
```

## S14 — 移动按观察到的服务端位移结算；搜索权重不等于移动费用

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L880–L920

```text
 880 |     /** Check the proposed packet before vanilla movement can consume a lease budget. */
 881 |     public boolean preparePlayerMovePacket(ServerPlayer actor, ServerboundMovePlayerPacket packet,
 882 |                                            boolean correctionPending) {
 883 |         requireThread();
 884 |         PlayerMoveLease lease = playerMoves.get(actor.getUUID());
 885 |         if (lease == null || !packet.hasPosition()) return true;
 886 |         if (unsupportedPlayerMovement(actor)) {
 887 |             if (lease.moved && lease.observedTick == cumulativeServerTicks)
 888 |                 lease.mixedTick = true;
 889 |             closePlayerMove(lease, "special movement mode has no active movement permit");
 890 |             return correctionPending || actor.isChangingDimension();
 891 |         }
 892 |         long tick = cumulativeServerTicks;
 893 |         if (lease.moved && lease.observedTick != tick) settleMoveTick(lease);
 894 |         if (playerMoves.get(actor.getUUID()) != lease) return false;
 895 |         Vec3 before = actor.position();
 896 |         double wantedX = packet.getX(before.x), wantedY = packet.getY(before.y), wantedZ = packet.getZ(before.z);
 897 |         if (!Double.isFinite(wantedX) || !Double.isFinite(wantedY) || !Double.isFinite(wantedZ))
 898 |             return true; // Vanilla rejects invalid packet coordinates before any world movement.
 899 |         boolean horizontal = Math.abs(wantedX - before.x) > 1.0E-5
 900 |                 || Math.abs(wantedZ - before.z) > 1.0E-5;
 901 |         boolean rising = actor.onGround() && wantedY > before.y + 0.05;
 902 |         boolean verticalWater = (actor.isInWater() || actor.getFluidHeight(FluidTags.WATER) > 0)
 903 |             && Math.abs(wantedY - before.y) > 1.0E-5;
 904 |         if (correctionPending || actor.isChangingDimension()
 905 |             || availableForce(actor.getUUID(), tick) != null
 906 |             || (!horizontal && !rising && !verticalWater)) return true;
 907 |         AABB proposedBody = actor.getBoundingBox().move(
 908 |             wantedX - before.x, wantedY - before.y, wantedZ - before.z);
 909 |         int proposedWater = waterInLoadedBody(actor.level(), proposedBody);
 910 |         if (proposedWater < 0) {
 911 |             closePlayerMove(lease);
 912 |             return false;
 913 |         }
 914 |         boolean water = actor.isInWater() || proposedWater > 0;
 915 |         int required = Math.max(lease.baseCost, water ? 2 : 1)
 916 |             + (lease.jumped || rising ? 1 : 0);
 917 |         if (engine.stateView(lease.encounterId).members().get(lease.playerId).movementTicks() >= required)
 918 |             return true;
 919 |         closePlayerMove(lease);
 920 |         return false;
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L1355–L1379

```text
1355 | 
1356 |     private void settleMoveTick(PlayerMoveLease lease) {
1357 |         if (!lease.moved || playerMoves.get(lease.playerId) != lease) return;
1358 |         CombatEngine.StateView state = engine.stateView(lease.encounterId);
1359 |         int remaining = state.members().get(lease.playerId).movementTicks();
1360 |         int cost = lease.mixedTick ? 0 : lease.baseCost + (lease.jumped ? 1 : 0);
1361 |         if (cost > remaining) throw new IllegalStateException("observed movement exceeded authorized budget");
1362 |         boolean terminal = remaining == cost;
1363 |         engine.publish(lease.encounterId, lease.operationId, lease.nextStep,
1364 |             terminal ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.ACCEPTED,
1365 |             (lease.mixedTick ? "mixed active/forced displacement, contribution unproven: "
1366 |                 + lease.forcedCause + " at server tick "
1367 |                 : "vanilla player displacement at server tick ") + lease.observedTick,
1368 |             cost, 0, terminal);
1369 |         lease.nextStep++;
1370 |         lease.observedSteps++;
1371 |         lease.spentTicks += cost;
1372 |         lease.moved = false;
1373 |         lease.mixedTick = false;
1374 |         lease.forcedCause = null;
1375 |         lease.baseCost = 0;
1376 |         lease.jumped = false;
1377 |         if (terminal) playerMoves.remove(lease.playerId);
1378 |         sync(engine.stateView(lease.encounterId));
1379 |         if (terminal) syncBodyStateTransitions();
```

### `common/src/main/java/cc/sighs/dndturn/combat/TacticalPlanner.java`：L12–L32

```text
  12 | /** Plans from probe measurements. A proposal must be checked again before movement. */
  13 | public final class TacticalPlanner {
  14 |     private TacticalPlanner() {}
  15 | 
  16 |     public interface CellProbe {
  17 |         /** True only when the entire footprint can occupy the cell. */
  18 |         boolean canOccupy(GridCell cell);
  19 |         /** Search weight only; zero or negative means the edge is closed. It is never a movement charge. */
  20 |         int traversalCost(GridCell from, GridCell to);
  21 |     }
  22 | 
  23 |     public record Proposal(List<GridCell> cells, int cost, long probeVersion) {
  24 |         public Proposal { cells = List.copyOf(cells); }
  25 |     }
  26 | 
  27 |     public static Proposal propose(GridCell start, GridCell goal, int maxCost, int maxNodes,
  28 |                                    long probeVersion, CellProbe probe) {
  29 |         Objects.requireNonNull(start);
  30 |         Objects.requireNonNull(goal);
  31 |         Objects.requireNonNull(probe);
  32 |         if (maxCost < 0 || maxNodes < 1) throw new IllegalArgumentException("planner budget");
```

## S15 — 现有物品整理权限不是物品行为能力；持续使用还受身体 tick 门控

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`：L1398–L1408

```text
1398 |     public boolean mayOrganizeInventory(ServerPlayer player) {
1399 |         requireThread();
1400 |         UUID id = engine.encounterOf(player.getUUID());
1401 |         if (closing || recoveryFailure || id == null || recoveryPending.contains(id)
1402 |             || worldEffectDepth != 0 || server.tickRateManager().isFrozen()
1403 |             || MinecraftCombatRuntime.isLocalMember(server, player.getUUID())) return false;
1404 |         CombatEngine.StateView state = engine.stateView(id);
1405 |         return player.getUUID().equals(state.current())
1406 |             && (state.phase() == EncounterPhase.ACTIVE || state.phase() == EncounterPhase.CANDIDATE)
1407 |             && isEntityInsidePausedRegion(player);
1408 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/ServerGamePacketListenerMixin.java`：L126–L141

```text
 126 |     @Inject(method = "handleSetCarriedItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
 127 |     private void dndturn$hotbar(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
 128 |         if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)
 129 |             && !VanillaInputPolicy.mayOrganize(player)) {
 130 |             VanillaInputPolicy.correctInventory(player);
 131 |             ci.cancel();
 132 |         }
 133 |     }
 134 | 
 135 |     @Inject(method = "handleContainerClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true)
 136 |     private void dndturn$container(ServerboundContainerClickPacket packet, CallbackInfo ci) {
 137 |         if (player.level().getServer().isSameThread() && VanillaInputPolicy.controlled(player)
 138 |             && !VanillaInputPolicy.mayClick(player, packet)) {
 139 |             VanillaInputPolicy.correctInventory(player);
 140 |             ci.cancel();
 141 |         }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/ClientMovementBodyMixin.java`：L12–L29

```text
  12 | /** Local movement prediction keeps running while body effects and active item use remain paused. */
  13 | @Mixin(LivingEntity.class)
  14 | public abstract class ClientMovementBodyMixin {
  15 |     private boolean dndturn$holdBody() {
  16 |         return (Object) this == Minecraft.getInstance().player
  17 |             && ClientCombatState.holdBodySubsystemsDuringMovement();
  18 |     }
  19 | 
  20 |     @Inject(method = "tickEffects", at = @At("HEAD"), cancellable = true)
  21 |     private void dndturn$holdEffects(CallbackInfo callback) {
  22 |         if (dndturn$holdBody()) callback.cancel();
  23 |     }
  24 | 
  25 |     @Inject(method = "updateUsingItem(Lnet/minecraft/world/item/ItemStack;)V",
  26 |         at = @At("HEAD"), cancellable = true)
  27 |     private void dndturn$holdItemUse(ItemStack stack, CallbackInfo callback) {
  28 |         if (dndturn$holdBody()) callback.cancel();
  29 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/ServerPlayerTickMixin.java`：L10–L18

```text
  10 | /** ServerPlayer#doTick runs from the connection, outside EntityTickEvent.Pre. */
  11 | @Mixin(ServerPlayer.class)
  12 | public abstract class ServerPlayerTickMixin {
  13 |     @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
  14 |     private void dndturn$pauseBodyTick(CallbackInfo callback) {
  15 |         if (MinecraftCombatRuntime.isBodyPaused((ServerPlayer) (Object) this)) {
  16 |             callback.cancel();
  17 |         }
  18 |     }
```

## S16 — 对本包全部 Java 源文件的调用点扫描

下面是 `TacticalPlanner.propose`、`TacticalPlanner.revalidate` 与 `new MinecraftCellProbe` 的全部字面引用。扫描覆盖本次解压目录中的全部 92 个 Java 文件；不涉及依赖 jar 或未提供的代码。共同说明：本包中的规划器只在测试场景调用，没有接入玩家的正常指令链。Zombie 自己的 Navigation 是另一条路径，不能据此称整个项目完全没有寻路。

```text
common/src/test/java/cc/sighs/dndturn/combat/CombatEngineReviewRegressionTest.java:665: assertThrows(IllegalStateException.class, () -> TacticalPlanner.propose(
common/src/test/java/cc/sighs/dndturn/combat/CombatEngineTest.java:245: var proposal = TacticalPlanner.propose(start, target, 3, 32, 7, probe);
common/src/test/java/cc/sighs/dndturn/combat/CombatEngineTest.java:247: assertTrue(TacticalPlanner.revalidate(start, proposal, 7, probe));
common/src/test/java/cc/sighs/dndturn/combat/CombatEngineTest.java:248: assertFalse(TacticalPlanner.revalidate(start, proposal, 8, probe));
targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/gametest/LocalTimeGameTests.java:2651: MinecraftCellProbe probe = new MinecraftCellProbe(helper.getLevel(), mover, region);
targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/gametest/LocalTimeGameTests.java:2652: var proposal = TacticalPlanner.propose(start, goal, 2, 32, 1, probe);
targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/gametest/LocalTimeGameTests.java:2655: helper.assertFalse(TacticalPlanner.revalidate(start, proposal, 1, probe),
```
