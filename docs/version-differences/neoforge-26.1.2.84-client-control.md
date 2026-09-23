# NeoForge 26.1.2.84 / AUI 1.2.5 客户端控制契约

本轮在有 staged/unstaged/untracked 用户修改的工作区增量修改。指定 `DNDTurn-sources(20260925-124521).zip` 未找到；没有以较旧归档覆盖当前源码，也不宣称完成精确归档比较。

## 固定实现证据

- target `gradle.properties`：NeoForge `26.1.2.84`；Gradle `runtimeClasspath` 实际解析同版本，Minecraft patched 实际为 **26.1.2**。JDK 25.0.3。
- `minecraft-patched-26.1.2.84-sources.jar` SHA-256：`9e2e31c73929903c055704075690cf04809dc0e64950b53dcf551eec29ce8610`。
- AUI Maven `maven.modrinth:apricityui:wwrdhxiM`，实际 1.2.5 jar SHA-1：`d4311449e5b1cdc9a59e33a7f2b02435d7441546`。对实际 jar 用 JDK 25 `javap -c -p` 检查 `Client`、`Document`、`Operation`、`MouseEvent`；结合本地 `build/aui-reference` 的源码阅读。没有以在线最新文档或 .109 源码代替固定版本证据。
- 本地本轮提取源码/字节码位于被忽略的 `build/client-control-source/`；解析结果在 `build/client-control-build.log`。

## AUI 输入语义

`Client.mouseButton(InputEvent.MouseButton.Pre)` 调用 `Operation.onMouseDown/onMouseUp`，更新按钮位图后派发 `MouseEvent`。坐标由 **GLFW 实时游标**取得，不只读取 MouseHandler 缓存。`Client.scroll` 调用 `Operation.scroll`。AUI 消费后取消 NeoForge 事件；鼠标移动通过渲染帧轮询，不能只在 20 Hz tick 放开鼠标。

全局 `MouseEvent.tiggerEvent(event)` 按 Document 层级从前向后遍历；每个文档先将 GUI 坐标转换为视口坐标，再命中测试。`Document.interceptsMouseEventsAt` 等价于 intercept 开启且 hitTest 非空，并不是整个实际窗口拦截。全局循环还会再次检查 intercept，因此仅跳过下层文档的局部回调不足以保证模态获得事件。

命中按绘制逆序检查 display、visible、pointer-events，遵守 mask push/pop 裁剪；禁用按钮仍可能命中，但鼠标释放的 click/default activation 会拒绝 disabled 节点。释放会找按下与当前目标的最近公共祖先，清除 pressedElement；mousemove/mouseup 可重定向给原按下控件（含滚动条拖动）。按下文档关闭/切换时不能让释放进入另一个文档。

实现保留 tactical 根 `pointer-events:none`，面板和模式按钮显式可命中，空白交给世界选择/镜头。Consent 使用 browser viewport 和百分比全窗口背景，不再让固定 16:9 画布代表整个窗口。坐标测试使用 `documentToScreenPosition`，再按 GUI/窗口比例转换，避免把 CSS 尺寸直接当屏幕像素。

## 单一状态 owner

`ClientControl` 持有模式、虚拟位姿、临时接收者、镜头按键、按键交接抑制、按下手势与代次、同意状态。优先级：原版 Screen > 同意模态 > 战术 UI > 镜头/获准角色；失焦没有游戏接收者。模式只在用户明确切换或会话生命周期改变时改变。

`ClientCombatState` 仍是服务端会话、许可和结果的客户端投影。161358 修订由 `ClientTacticalPlan` 统一持有物品/行为/目标选择、菜单、运行计划和取消待确认状态；`CombatControls` 只路由键位与意图，旧 MovePhase 状态机移除。Overlay 从交互 owner 读取目标，不持有第二份实体选择。旧 `OverlayMouseControl` 移除。

模式切换清 click 队列、held、鼠标累计增量、原版鼠标按下/拖动状态和 AUI pressed/focus；旧手势只保留释放墓碑，不能激活新文档。键盘长按跨接收者的重复事件受抑制直到释放。实际 KeyMapping 匹配，保留 options；同键行动按 START、模式、EXIT、MOVE、ATTACK、DASH、DODGE、DISENGAGE、END、物品、跳跃的稳定顺序消解。

## .84 seam 表

| 类/方法 | 桥接位置与职责 | 取消语义 |
| --- | --- | --- |
| `MouseHandler.onButton(long, MouseButtonInfo, int)` | 包围 `ClientHooks.onMouseButtonPre`：先登记手势，原样执行 AUI/NeoForge，再合并消费结果 | 在原版 grabMouse、isLeft/RightPressed、KeyMapping.set/click 之前终止；不提前剥夺 AUI 点击 |
| `MouseHandler.onScroll(long,double,double)` | 包围 `ClientHooks.onMouseScroll` | UI 先消费；空白相机推拉；战术/模态不进入热栏分支 |
| `MouseHandler.onMove/turnPlayer` | 独立游标增量转向；镜头/模态拒绝真实玩家转向 | 原版仍清帧累计值；交接清第一次增量，避免恢复跳转 |
| AUI `MouseEvent.tiggerEvent(MouseEvent)` 及 `(MouseEvent,Document)` | 在 AUI 正常派发入口选择唯一目标文档，保留该文档自身 hitTest/default action/释放 | 不复制派发；Screen 时我们的 Overlay 不抢输入；模态不向底层 Document 派发 |
| `Camera.alignWithEntity(float)` | TAIL，`Camera.update` 的 FOV、视锥、投影矩阵之前应用虚拟位置/旋转 | 不改 camera entity；只在 main camera 和原玩家 ownership 仍成立时应用 |
| `KeyboardInput.tick()` | TAIL，移除不属于获准角色的 keyPresses/moveVector | 镜头按键独立，移动许可不会把镜头 WASD 转发给角色 |
| `Minecraft.handleKeybinds/startAttack/continueAttack/startUseItem/pickBlockOrEntity` | click 消费前和根动作 HEAD | 阻止攻击挥手、持续破坏/使用与选取副作用；背包/聊天/菜单仍可打开 |
| `MultiPlayerGameMode.interact(Player,Entity,EntityHitResult,InteractionHand)` 及已有 attack/use/destroy | HEAD，拒绝未授权客户端预测 | `.84 interact` 在本地交互/预测发包前拒绝；服务端授权保持不变 |
| `ClientLevel.tickNonPassenger/tickPassenger` | 远端暂停保留原实现，本机玩家不在这里跳过 | 使本机玩家进入自己的网络维护桥接 |
| `LocalPlayer.tick()` | 身体暂停时保留空 input 同步和 `sendPosition` | 不运行被暂停身体模拟；不把镜头位置当玩家位置；死亡仍走原版生命周期 |

所有注入 `defaultRequire=1`；没有 optional 匹配。Mixins 只桥接，状态与策略在 target 客户端。common 未引入客户端/Minecraft 依赖。

虚拟镜头保留原 camera entity，所以 `LocalPlayer.isControlledCamera()`、`sendPosition()` 的含义不变。视图在渲染帧推进，不依赖被暂停身体 tick。移动逐段检查客户端已加载 chunk，不请求服务端加载。选择使用当前虚拟位姿、窗口坐标的新射线，先按加载边界和方块遮挡裁剪，再选本会话实体；不用旧 `Minecraft.hitResult`。服务端仍从真实行动者复验距离/视线/许可。

生命周期不调用 setCameraEntity，不存待恢复的旧实体；退出停止应用虚拟位姿，由原版下一帧恢复。若其他系统改变 camera entity，则 ownership 检查失败，不覆盖它。世界、玩家实例、死亡、会话结束撤销位姿与输入；Screen/模态/失焦保留模式但暂停接收。

## 本轮命令与运行证据

工作目录 `targets/neoforge-26.1`，`JAVA_HOME=C:/Program Files/Java/jdk-25.0.3`，`-I ../../scripts/client-control-validation.gradle` 将构建产物和世界隔离到根 `build/client-control-validation/`，避免 clean 删除用户运行实例的文件。

```powershell
.\gradlew.bat -I ../../scripts/client-control-validation.gradle clean build :common:test dependencies --configuration runtimeClasspath
.\gradlew.bat -I ../../scripts/client-control-validation.gradle runGameTestServer
.\gradlew.bat -I ../../scripts/client-control-validation.gradle runServer
.\gradlew.bat -I ../../scripts/client-control-validation.gradle runClient -PdndturnUiSmoke
.\gradlew.bat -I ../../scripts/client-control-validation.gradle runClient -PdndturnControlProbe
```

- 独立 clean build 成功，common **48 项**成功，必需 GameTest **41 项**成功。包括原有 SWAP、START 版本 0、RESULT_SYNC 游标、恢复生产构造路径与 UNKNOWN 等回归；本轮未改这些服务端规则。
- 普通 dedicated server 成功启动，实际客户端连接并通过正常 `/dndturn tactical start` 创建候选会话。
- 单客户端回归同时断言：自动镜头、平移/旋转/滚轮改变虚拟位姿、真实玩家位姿和预算/结果数/热栏不变、UI 回调一次、无攻击/交互/use/swing/热栏/action 包；Screen 手势交接与模式保留；无许可的候选角色输入仍拒绝。修正本机实体门禁后，额外验证位置心跳存在、所有包内玩家位姿不随镜头改变。服务端暂停期间的纠正确认包保留，不把正常维护包误报成镜头位移。
- 原生 Windows 鼠标经过 GLFW → MouseHandler → NeoForge → AUI 的同意/拒绝测试通过，包含背包、聊天、暂停菜单、最小化/恢复及关闭。该夹具不创建正式战术会话；不替代上述联网测试。
- 联网单客户端分别通过 1280×720 / GUI 2 与 1000×800 / GUI 3，包含将镜头前进重绑定到 H、鼠标侧键并恢复原绑定的实际运行断言。命令为前述 `runClient -PdndturnControlProbe`，后者追加 `-PcontrolProbeWidth=1000 -PcontrolProbeHeight=800 -PcontrolProbeScale=3`；日志分别为 `build/client-control-probe-16x9.log`、`build/client-control-probe-5x4.log`。
- 两客户端已实际连接，但首轮出生点不在共同发现范围；第二轮普通行走夹具被地形阻挡而超时。这些失败没有记为双客户端镜头/角色同步通过，不能用两个进程存在代替同一会话验收。
- 首轮隔离 init 脚本 rootDir 错误、首次启动引导页、旧夹具重复注册计数回调、使用缓存坐标而非 GLFW 实时坐标的回归失败均已修正并重跑；失败记录不写成通过。

临时管理员战斗夹具的命令被自动审批拒绝，原因是测试账号 level 4 权限及范围未获明确授权；已询问用户，仅相应依赖测试等待授权。活动缺口及完整 A–J 未验证范围统一见 [G33](../02_GAPS_AND_CONFLICTS.md#g33--客户端控制重构运行矩阵)，不以这些局部通过宣布完整闭环验收。
