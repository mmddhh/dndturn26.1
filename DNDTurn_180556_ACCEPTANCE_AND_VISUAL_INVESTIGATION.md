# DNDTurn 180556：修改验收与回合制视觉恢复调查

## 0. 结论与范围

**验收结论：上轮六项问题中的五项已有对应源码修复；取消状态机仍存在新查询等待路径的遗漏。不能宣布全部交互或实机验收通过。**

**视觉结论：不是“粒子引擎被整个暂停”这样一个开关问题。当前实体 tick 门控同时截断部分动画状态更新与粒子发生器；战术攻击缺少显式挥手生产入口；部分方块动画信号受服务器 block event 暂停影响；脱离身体的镜头还会离开以玩家为中心的环境效果采样区域。**

建议下一轮恢复的是客户端表现，不是无条件恢复实体模拟。本轮只审查、调查、记录；未修改项目源代码、未升级依赖、未修改资料库，也未合并修复。

### 精确基线

| 对象 | 本次实际读取的来源 |
|---|---|
| 上版 | `DNDTurn-sources(20260925-161358).zip` |
| 新版 | `DNDTurn-sources(20260925-180556).zip` |
| 用户要求的参考 | Library `/minecraft-vanilla/minecraft-patched-26.1.2.109-sources.jar` |
| 项目实际依赖声明 | `targets/neoforge-26.1/gradle.properties`：Minecraft `26.1`、NeoForge `26.1.2.84` |

资料库文件是 **.109 的 patched Minecraft 源码**，不是证明当前运行时已经为 .109 的构建记录。下面带 `.109` 的路径/行号只对参考 JAR 成立；项目路径/行号只对 180556 成立。固定版本兼容性与 Mixin 命中要在下一轮选定实际构建基线后验证，不暗中升级。

当前 ZIP 不含 `docs/`。资料库既有规则可用于确认“模拟与视觉分离”“实际全局冻结仍遵从原版”等约束；其旧键位章节早于本轮交互改动，不能拿来回退当前目标式交互。

配套文件：`DNDTurn_180556_SOURCE_EVIDENCE.md` 含直接代码摘录、原始路径/行号、归档哈希与完整差异清单；`DNDTurn_180556_VALIDATION.zip` 含可复跑的独立规则探针与原始输出。

## 1. 实际执行的验证

| 验证项 | 结果与限制 |
|---|---|
| 新旧源文件逐文件比较 | 新增 4、修改 69、删除 0 |
| current ZIP 全 Java 文件统计 | 111 个；其中 common 主源码 18 个 |
| common 主源码编译 | OpenJDK 21.0.11，`javac --release 17`：18 个全部通过 |
| 独立 common 规则探针 | 10 项正向通过；不依赖 JUnit，不依赖 Minecraft |
| 仓库 JUnit | 未执行；尝试下载 JUnit standalone 时 Maven 域名 DNS 解析失败 |
| Gradle target 构建、Mixin 注入、GameTest | 未执行；target 声明 JDK 25，环境只有 JDK 21 |
| 真实键鼠、渲染、实际致死攻击、专服双客户端 | 未执行 |

10 项探针覆盖候选接近/移动费用、错误步骤拒绝、放置提交前拒绝不扣动作、持续行为一次计费、免费方块交互、父子步骤恢复，以及修正后的攻击完成/目标离场/UNKNOWN/不可变结果边界。它们验证的是规则模型，不替代实际 Minecraft 调用链。

## 2. 上轮 R1–R6 的验收

| 问题 | 本版修复 | 本轮判断 |
|---|---|---|
| R1 致死攻击使父 PLAN 变 UNKNOWN | `attackPlan` 用外层 `worldEffectDepth` 延后死亡清理；先发布父计划，再处理离场；终端投影从实际账本结果构建 | 源码修复成立；对应 common 顺序通过探针，实机致死路径未执行 |
| R2 能力按钮与实际请求不一致 | 服务器 ability/offer 绑定所选物品、behavior、目标；统一 `ClientTacticalPlan` 状态；上下文选项使用已绑定目标 | 主问题源码已改，保留运行验收 |
| R3 数字热栏先查旧物品 | 数字键与 UI 都进 `selectSlot`；Query 带显式 slot、revision、item reference；服务器验证后选槽并回送确认 | 原有时序入口已收束；网络延迟/改键实机未执行 |
| R4 取消与会话生命周期 | 新增 phase、查询 revision、CancelPending、遇新 Encounter reset；收投影检查连接/会话/操作身份 | **部分通过**：下述 Idle 地面查询未进入可取消状态 |
| R5 旧公开世界行为旁路 | 新 `ATTACK/MOVE_BEGIN/MOVE_END` 明确拒绝；已完成历史重试只返回已有结果 | 对上轮指定旁路源码修复成立；不等于内部 Mob 执行器被删除 |
| R6 实际放置范围未校验 | 新 `TacticalImpact` 在 invoke 前解析实际落点与门/床/双高位置，检查加载、场地、保护与碰撞 | 对列明类型有保守合同；不是所有方块或任意邻居连锁效果的通用兼容证明 |

源码定位：证据 E01–E05。R6 使用确切类型白名单，并拒绝未知 override、自定义 block entity 数据及部分自定义状态。这个限制应继续显式展示；不要通过放宽白名单假装已经支持所有 Minecraft/其他模组物品。

### 2.1 仍须修复：Idle 地面移动查询绕过 Esc 取消

180556 `ClientTacticalPlan.java:52–61`：空闲左键地面设置 `behavior=move` 和 `submitAfterQuery=true`，随后查询，但**没有改变 IDLE phase**。`target():165–183` 也只更新 query/revision，不改变 phase。

`ClientControl.escape():344–348` 只在 `phase != IDLE` 时调用 cancel。因而存在源码可达链：

```text
Idle 左键地面 → Query 在途，但 phase 仍 Idle
→ 按 Esc → 未 invalidate 这个查询，只关闭面板或打开游戏菜单
→ Options 迟到，仍匹配 query/revision
→ receiveOptions():80–84 自动 choose
→ 新的移动计划 Request 被发往服务器
```

角色在菜单打开时可能被输入维护逻辑暂时按住，但服务器计划可以已经创建；这不是成功取消。

下轮应让“未提交、仍在查询、会自动提交”的状态显式可取消。可新增 discovery/query phase，或统一用请求生命周期判定取消，不能只检查 requested/non-IDLE。新查询应清理旧 `submitAfterQuery`，避免它泄漏到右键菜单。测试必须使用延迟 Options：点击地面→立刻 Esc→注入旧回复，断言没有新 Request/移动许可，菜单也不复活。

这是静态时序发现，本轮没有伪称实机复现。证据 E03。

### 2.2 本版输入交付已经前进

Home、O、左 Ctrl 已注册；I 可以打开真实 `InventoryScreen`；数字热栏与物品 UI 已归一。客户端 probe 新增真实键盘回调、鼠标手势/菜单坐标路径，不再只有直接调用 `target()/choose()`。

这些是测试代码存在和源码接线事实，不是测试已运行。现有 raw 用例仍应补上述 Idle 查询取消时序。Alt/T/F10 不属于本次已验证完成项。证据 E06。

## 3. 视觉失常的实际分层

### 3.1 实体还在被渲染，但产生动画状态的 tick 被截断

当前 `ClientEntitySimulationMixin` 在 `ClientLevel.tickNonPassenger/tickPassenger` HEAD 取消暂停的非本地实体，仅运行 `setOldPosAndRot()`、收到网络位置后的插值收敛和乘客分流。

`.109 ClientLevel.tickNonPassenger:359–372` 在原版路径中还会递增 tickCount、触发 NeoForge tick 事件并调用 entity.tick。当前 HEAD 取消发生在这些工作之前。

`.109 EntityRenderer.extractRenderState:173–180` 的 `ageInTicks=entity.tickCount+partialTicks`，`LivingEntityRenderer` 的步态、受击红色覆盖与死亡时间也依赖实体内更新结果。因此可以出现位置在插值、模型却没有正常迈步，或者动画仅在一个很小插值区间反复而不能连续推进。

**本地玩家不完全相同：**它避开远端门控，却在 `TacticalLocalPlayerMixin` 的 LocalPlayer.tick 入口早退。其外层 tickCount 可能仍递增，内部挥手、使用、受击等状态却不推进。不能用“所有实体的 tickCount 都停止”解释一切。合法移动期间还有 `ClientMovementBodyMixin` 单独取消 `tickEffects/updateUsingItem`。

证据 V01–V03。

### 3.2 部分战术攻击根本没有产生挥手事件

当前战术攻击链自行做命中检定、子步骤和 `hurtObserved`，不走原版普通玩家鼠标攻击入口；原版入口又被输入 Mixin 阻断。全 111 个 Java 文件未找到显式 `.swing(...)` 或 `ClientboundAnimatePacket` 调用/引用。

所以仅恢复动画时间，也不能播放一个没有启动的挥手。下一轮应在**实际被授权的攻击尝试开始**时产生一次演出提示；未命中也挥手，纯查询或被拒请求不挥手。同操作网络重试不能再播放一次。

`.109 LivingEntity.swing(hand,true):2073–2087` 可向追踪者及自身发送原版动画包，但包含物品 `onEntitySwing` 钩子及实体字段变化，不能当作完全无副作用的发送函数。优先明确“原版包桥接”与“独立计划演出事件”二选一或明确定义去重关系，不能两套同时播放。

受击本身已有原版伤害事件路径；不能每次收账本结果再额外播一遍受击。对 critical/miss 等特殊效果使用真实已确认结果，不能从伤害值猜。

证据 V04、S01。

### 3.3 粒子要区分：生成入口、既有粒子更新、显示采样位置

`.109 Minecraft.tick:1957–1965` 在未真正 pause 且 vanilla TickRateManager 正常时，继续执行 `ClientLevel.animateTick` 和 `ParticleEngine.tick()`。当前 DNDTurn 的局部会话门控没有取消这两处顶层调用。

| 通道 | 调查结果 | 恢复方向 |
|---|---|---|
| 已生成粒子的运动/寿命 | ParticleEngine 仍 tick group；Particle 自己 age/remove/move | 保留原版单次更新；**不要额外补跑 particleEngine.tick** |
| 已创建的 TrackingEmitter | ParticleEngine 独立 tick，有限寿命 | 保留原版，不按每渲染帧重复创建 |
| 服务端粒子包/伤害事件 | ClientPacketListener 有独立包入口 | 不因主体暂停而丢弃合法演出；检查触发与去重 |
| 挂在实体 tick 的持续粒子发生器 | 主体被取消就不再进入 | 提取已审计的客户端发射逻辑 |
| 状态效果粒子 | tickEffects 的客户端分支既递减效果 duration 又发粒子 | 只恢复发射部分，不恢复整段效果 tick |
| 环境方块/流体粒子 | animateTick 顶层仍运行，但中心为实际玩家 | 评估受预算和加载边界约束的战术观察区域采样 |

`.109 LivingEntity.tickEffects:868–882` 先遍历 `effect.tickClient()` 再从同步的效果粒子数据发射；`MobEffectInstance.tickClient:235–241` 会递减 duration、切换隐藏效果并更新混合状态。恢复整个方法会让客户端状态倒计时与被冻结的权威状态脱节。

纯视觉发射器应读取服务器已确认的效果/手持物品/使用状态，使用独立表现随机源；不推进药水持续时间、不调用 item.onUseTick/finishUsingItem、不补做伤害或消耗。

证据 V05–V06。

### 3.4 虚拟镜头离开玩家后，环境效果生成区域没有跟着移动

`.109 Minecraft.java:1959` 实参是 `player.getBlockX/Y/Z()`，不是独立镜头位置。`ClientLevel.animateTick` 在该中心附近做 16/32 的随机采样。

所以镜头移动去看远处时，可能看见已渲染地形却看不到相应新环境效果；这不等于 particle engine 停止。

下一轮只能考虑在已加载、可观察范围内调整/分配一个统一视觉采样预算，不要同时对玩家和镜头各跑一套无上限 animateTick，也不要移动真实玩家、扩展服务端模拟区或强制加载区块来制造粒子。原版粒子选项、距离裁剪和网络追踪限制仍应保留。

证据 V07。此项是环境表现采样问题，与仅记录的相机地形高度缺陷分开。

### 3.5 箱盖可能收不到动画目标，不是不会 tick

当前 `LevelBlockEntityGateMixin` 只暂停服务器侧方块实体模拟；客户端箱盖 `ChestLidController.tickLid()` 没被这条门控取消。

但标准 Chest 的开合人数由 `ChestBlockEntity.signalOpenCount()` 发送 `blockEvent(pos,block,1,current)`。`.109 ServerLevel.runBlockEvents` 只有在位置允许 tick、事件实际执行后才广播 `ClientboundBlockEventPacket`。

当前 `ServerLevelBlockEventGateMixin` 对暂停区域的这条位置条件返回 false，于是开合信号会排队等待环境推进。客户端动画即使每 tick 正常运行，也没有新的 shouldBeOpen 目标。

下轮应只桥接已确认交互产生的、**已分类为纯展示**的方块状态/事件；同时处理以后释放的旧排队事件，避免旧“开箱”反过来覆盖新的“关箱”。不能把所有 block event 或“ID=1 的事件”视为纯视觉：活塞、红石、模组方块等并不共享同一语义。也不能为开箱恢复方块实体的整个服务器 ticker。

证据 V08。这是 `.109` 源码与项目门控交叉分析得到的可达链，仍需用实际构建版本验证。

## 4. 适合下一轮的实现切入点

### 4.1 保留模拟时钟，增加客户端表现状态

建议最小职责分解（名称为设计建议，当前还不存在这些类）：

```text
现有服务端：操作许可 → 实际原版执行 → 权威观察/结果
                                ↓ 已确认事件/状态
客户端表现管理：连续视觉时钟 + 有限演出片段 + 只读观测快照
                                ↓
RenderState bridge / 经审计的粒子发生器 / 展示信号桥接
                                ↓
原版 Renderer / Model / ParticleEngine
```

不得用 `Entity.tick()`、`LivingEntity.aiStep()`、`travel()` 或人为补身体 tick 来实现表现。也不要通过修改真实位置/速度/tickCount/血量/效果持续时间来让模型看起来在动。资料库的既定约束“镜头和攻击演出不改变实体真实位置”应继续保留。

表现更新需要一份按客户端会话和世界生命周期管理的状态，每个客户端更新周期最多推进一次，渲染只读。不要在 shadow pass、outline、头像和正常场景各提取一次 render state 时就各推进一次动画。

局部回合暂停不停止纯视觉；真实 Minecraft pause 或用户明确的全局 tick freeze 仍遵守原有暂停策略。异常长帧限制 dt，不能把服务器暂停期间积累的时间一次补进模拟。恢复原版 tick 时处理相位交接，避免出现动画跳回、旧 hurtTime 又把红色覆盖激活、步态双重推进。

### 4.2 使用 .109 已存在的 render-state 修改边界

精确切入点为：

```text
EntityRenderer.createRenderState(entity, partialTicks)
→ extractRenderState（包括具体子类）
→ finalizeRenderState
→ RenderStateExtensions.onUpdateEntityRenderState
```

NeoForge 官方 26.1 文档提供 `RegisterRenderStateModifiersEvent` / `registerEntityModifier`；玩家使用 Avatar 路径，还提供 `registerAvatarEntityModifier`。可利用这个最终提取后的边界覆盖**表现字段**，而不是替换实体的模拟行为。

优先检查并适配：

| 表现 | .109 对应提取/状态 |
|---|---|
| 待机/周期摆动 | `EntityRenderState.ageInTicks` |
| 行走摆动 | `LivingEntityRenderState.walkAnimationPos / walkAnimationSpeed` |
| 挥手 | `ArmedEntityRenderState.attackTime / attackArm / swingAnimationType` |
| 受击颜色与死亡姿态 | `LivingEntityRenderState.hasRedOverlay / deathTime` |
| 使用物品姿态 | Humanoid/Avatar 的 `isUsingItem / ticksUsingItem` 等已确认使用状态 |
| 掉落物旋转/上下浮动 | ItemEntityRenderer 对渲染 age 的使用；不推进真实 ItemEntity 寿命或重力 |
| 有独立动画状态的实体 | 例如 Bat 的 fly/rest `AnimationState` 副本；不能仅改 ageInTicks |

`.109 Bat.tick` 同时改位置/速度并启动 AnimationState，所以不能“只为扇翅调用 Bat.tick”。应在表现副本中按权威 resting/flying 状态初始化/停止对应动画；开始时间必须使用同一表现时基。未知自定义模型明确降级到未经修改的显示或注册适配，不通过运行任意实体 tick 换取兼容。

官方参考：
- NeoForged 26.1, Entity Renderers, “Render State Modifications”：https://docs.neoforged.net/docs/entities/renderer/
- NeoForged 26.1, Client Particles：https://docs.neoforged.net/docs/rendering/particles/

上述文档补充 API 用途；具体调用顺序以已读取的 .109 patched JAR 为准，不把文档当成 .84 编译通过的证据。

### 4.3 不同视觉应使用不同推进依据

待机呼吸可持续按表现时钟播放；走路要依据真实获准移动/收到的位姿收敛，而不是镜头移动或凭空沿规划路径走；挥手和受击是一次性事件片段；用物品应跟随已确认执行进度，未授权时不能把拉弓/食饮显示为已经完成；箭的轨迹、碰撞、落地和寿命继续跟随环境模拟，不能只因“需要视觉流畅”让冻结箭自行飞完。

重复的渲染帧可以平滑已有数据，不能生造下一次世界状态。跨会话、掉线、卸载、重生、实体 ID 复用必须清理或拒绝旧表现状态。一次性事件按 generation/encounter/entity 身份/operation/event ordinal 去重；账本重发、补收和重连不能重新播放全部历史攻击。

死亡演出尤其不能复活逻辑实体。若服务器移除时机短于演出，确有需要时仅保留有界纯渲染快照；本轮不提前要求引入完整残影系统。

## 5. 下一轮合并前的验收矩阵

本表是待运行场景，不是本轮通过清单。

| 场景 | 需要同时观察的视觉与模拟不变量 |
|---|---|
| 空闲点地面→立即 Esc→延迟 Options | 不发送新计划；没有迟到移动；选择和菜单不复活 |
| 本地/远端玩家与普通 Mob 等待回合 | 适用的待机视觉连续；坐标、速度、AI、效果时长不因视觉更新前进 |
| 合法接近后攻击：命中/未命中/致死 | 双客户端各一次挥手；受击/critical 与结果一致；父子终态正确，不重复伤害/扣费 |
| 移动后立即暂停 | 位姿收敛平滑，步态收尾停止；不继续沿路径模拟行走 |
| 药水粒子与食饮/拉弓 | 粒子可生成消亡；效果剩余时间、物品数量和耐久不被纯视觉推进 |
| 已有粒子/跟随发射器 | 正常衰减，开启视觉层后速度/寿命不翻倍；不无限增殖 |
| 镜头平移至远处已加载观察区域 | 环境效果在预算内显示；不强制加载，不移动玩家/扩大模拟区 |
| 已授权开箱/关箱，无环境推进 | 客户端 lid target 及时正确；释放旧排队事件不倒放；不放行活塞等模拟事件 |
| 掉落物、冻结箭、特殊模型 | 允许 item bob/spin，但真实寿命/重力不变；箭不凭空继续飞；未适配实体不被偷偷 tick |
| 30/60/144 FPS，多渲染通道 | 演出持续时间一致；每实体视觉只推进一次，不因头像/轮廓渲染加速 |
| 真正暂停、全局冻结、换世界、重连、ID复用 | 保持原暂停策略；无旧 clip、旧粒子发生器、旧查询或旧操作残留 |

建议实施顺序：先修未提交查询取消，再建立最小表现时钟与 render-state adapter；接入权威攻击演出和有限发射器；分类处理方块展示消息与镜头观察区采样；最后统一运行当前固定版本的编译/注入/专服双客户端验证。不要在本轮提前修改/合并。

## 6. 相机问题：仅记录

**CAM-01：虚拟相机高度不随地形变化，用户报告相机进入方块后出现进入地下现象。**

状态：待下一轮合并；本轮未修改，未实机复现。用户对“碰撞检测导致”的解释保留为待验证假设，不冒充已确认根因。证据 C01 保存当前水平焦点平移与视线裁剪代码作为后续基线；本轮不展开实现方案、不擅自改变高度或碰撞策略。

## 7. 最终边界

这轮可以确认源码修复方向、一个剩余取消时序缺口、以及恢复视觉所需的多个具体接入位置；不能确认实际客户端的动画、粒子、相机、网络手感已经正常。

核心原则是：**让表现继续更新，并不意味着允许世界继续模拟；恢复发生器也不意味着重复更新已有粒子。**
