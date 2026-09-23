# DNDTurn：161358 版本修复与目标式交互重构

你正在修改 DNDTurn 仓库。请直接检查、修改源码并验证，不要只输出设计方案，不要仅更换 UI 文案。当前审查基线为 `DNDTurn-sources(20260925-161358).zip`，不是先前的 135634 版本。先用实际工作树确认下列问题是否仍存在；用户已有改动必须保留，不执行 reset/回滚覆盖。

## 0. 阅读范围与工程约束

先阅读根 AGENTS.md，以及实际存在的 docs/03_PLAYER_RULES.md、docs/02_GAPS_AND_CONFLICTS.md、相关 version-differences 和维护约定。审查用 ZIP 不含 docs/，不表示完整工作树也没有；读不到时如实记录，不能声称读过或编造旧决策。用户本次明确玩法要求优先，规则变更写入规定文档。

当前主要 target 为 `targets/neoforge-26.1`，包内声明 Minecraft 26.1、NeoForge 26.1.2.84；target 需要 JDK 25，common 保持 Java 17 兼容且无 Minecraft/loader/Mixin/UI 依赖。最终以工作树和解析后的固定版本依赖为准，不借重构升级版本。关键接入先读该版本 patched 源码/字节码，不猜测其他版本 API。

必须阅读的链路：
- client/ClientControl、CombatControls、ClientTacticalPlan、ClientCombatState、TacticalOverlay、ClientControlRegression。
- combat/TacticalIntent（common）、TacticalNetwork、TacticalActions、TacticalBehavior、TacticalCapabilities、TacticalItems、VanillaBehaviors、RangedBehavior。
- CombatEngine、OperationRecord、CombatStateSnapshot、TacticalPlanner、MinecraftCellProbe。
- ServerCombatService、CombatIntentHandler、VanillaInputPolicy；相关客户端输入、ServerPlayerGameMode、方块使用/破坏、使用 tick 和伤害 Mixin。
- tactical.html/css、语言资源、common 测试、TacticalPlanGameTests、target build.gradle。

保留本版已有价值：focus/yaw/pitch/distance 镜头、服务端行为注册、完整 ItemStack 指纹、typed target、PLAN→MOVE→执行步骤、原版玩家输入驱动与实际位移计费、免费方块/付费物品分支隔离、去重、未知副作用不重放、投射物归属和现有反应/伤害许可。不要建立第二套 Encounter 权威或另起并行执行后端。

## 1. 先修复可定位的问题

### 1.1 致死攻击的父计划终态错误（先补失败测试）

当前调用顺序：
`VanillaBehaviors.Melee.start → ServerCombatService.attackPlan/attack → 发布 ATTACK 子操作 COMPLETED → attack 的 finally 调 confirmPendingDeaths → leave(死亡目标) → CombatEngine.leave 将仍 pending 且指向该目标的 PLAN 发布为 UNKNOWN → 返回 Melee.start → TacticalActions.finish`。

这不是假定：离线执行原 common 类，按“PLAN 开始→ATTACK 子步骤完成→目标 leave→父计划收尾”的顺序，可以得到 ATTACK=COMPLETED、PLAN=UNKNOWN，原因是 `member left before world outcome was confirmed`。目标死亡正是本次已确认行为的结果，不能被误判为副作用未确认。

要求：明确执行完成与死亡/离场清理的提交边界。优先通过一致的调度或按已确认子结果聚合来关闭根计划，不能重写已发布不可变结果，也不能一律将 UNKNOWN 改为 COMPLETED。执行前目标消失、执行中异常、真实结果不确定仍须有不同结局。保留伤害 trace、动作消费、死亡生命周期和恰好一个根终态；投射物发射完成不等于命中完成。

测试至少覆盖：致死近战、非致死/未命中、执行前目标离场、执行中异常、攻击者死亡、重复原请求、存档恢复。必须检查账本根终态与 S2C 终态一致，而不是只检查目标血量。

### 1.2 能力选择是假状态，目标有两个持有者

`ClientTacticalPlan.select()` 只写 capability，`capability()` 无调用点；`target()` 不使用它，只查询所有行为。TacticalOverlay 的 move/attack/place/break/use 按钮大多只改该字段，真正执行的是另一个 behavior-options 列表的 `choose(offer)`。左键又只更新 TacticalOverlay.selectedTarget，可能留下指向旧目标的 options。

不要仅把 capability 字段塞入 Query 作为修复。用统一的选择状态表达“哪件物品/哪只手/哪个明确 behavior/哪个世界目标”，HUD、键盘与鼠标消费同一个状态。Capability 可继续作为费用/行为家族，不应伪装成完整 UI 选择。消除失效按钮、重复目标状态和旧的“选择邻近目标后攻击”文案。

### 1.3 热栏切换与能力查询没有绑定同一件物品

`CombatControls.onKeyInput` 命中热栏键时立即调用 `ClientTacticalPlan.target(null,null)`；注释明确该入口在原版消费点击队列前。Query 仅携带 target/hand，服务器 discover 使用其收到时的当前 selectedSlot。这存在先查旧槽位、后同步新槽位的时序路径。点击 UI 热栏则手动发 SetCarriedItem 再查询，是另一条实现。

将数字键和 UI 热栏统一到一个选择入口。查询必须关联明确槽位、手别、选择 revision/nonce 和服务端确认的物品值身份；服务端仍验证是否允许换装。可以采用有序选槽确认，或统一的服务端选槽/发现协议，但不能信任客户端物品指纹、仅延迟一 tick 掩盖竞态、靠重查当前槽位猜测原意图。快速 1→2→3、延迟响应、换槽被拒绝、物品数量/组件变化、主副手切换都不得让旧响应覆盖新选择。

选武器默认准备该武器的攻击行为；选方块物品准备放置；多能力物品支持显式切换。仅选择和预览不扣动作。正在执行时的换槽必须明确拒绝或先取消旧计划并等待结果，不能让旧意图按新物品执行。I 必须提供实际可用的背包操作或明确整理入口，不能只提示“其他物品先整理至热栏”却没有可达的整理流程。

### 1.4 取消、会话切换、过期响应与身体驱动

当前 `cancel()` 只给 requested 发取消包，不清 options/query/预览；世界右键仍去查询目标；没有 Esc 分层取消。`ClientCombatState` 接受新 Encounter 时只 reset CombatControls，没有同步 reset ClientTacticalPlan；后者只有退出会话/换连接等路径才清理。必须修复同连接同世界 Encounter 切换、合并/关闭、恢复时的旧 requested、options、projection 残留。

明确区分：取消尚未提交的 UI 选择、取消已提交请求、等待服务端确认取消。取消 UI 后即使先前 Options 迟到也不能重开菜单；取消运行计划不能直接清 requested 当作成功。终态仍需处理并对账，已发生移动和已开始动作不退款。

客户端状态至少按 connection/session generation/encounter/selection revision/operation 绑定。在新身份进入时清除旧选择与驱动；原身份恢复不能复活旧请求。steering 必须确认当前 projection 属于当前 requested 与会话。失焦、打开 Screen、死亡、换维度、断线和异常都停止本地合成输入，不遗留被按住的键。

### 1.5 公开旧攻击/移动协议绕过新行为解析

CombatIntentHandler 仍公开接受旧 ATTACK 并直接调用 service.attack，MOVE_BEGIN 仍开放手动许可；requestFromUi 也保留这些分支。新注册器会拒绝未适配的物品，但旧 ATTACK 不经过同一行为支持检查。

收束玩家世界行为入口：旧消息显式拒绝，或经验证转换为同一 TacticalIntent/PLAN。保留结果账本的旧请求查询/重试兼容，不把相同 operation ID 改义后再次执行。内部 Mob 战术攻击、伤害子步骤和明确调试入口可复用底层执行器，但必须有明确边界，不能误删内部逻辑或留公开旁路。主流程不再要求先开启 MOVE_BEGIN 或切 CHARACTER 才能行走。

### 1.6 放置/使用的目标授权必须覆盖实际影响范围

当前 validate 只校验点击的 targetCell，prepare 只检查该位置的保护；ItemOnBlock 未核验原版最终放置位置。点到场内边缘方块，不意味着其外侧落点也在场内。可替换方块、门、床、双层方块、工具改块和流体也不能一律等同 clickedPos.relative(face)。桶目前额外检查了相邻位置，但这不是通用影响范围合同。

读取固定版本原版 placement/use 上下文，解析实际目的地和完整必要影响范围，检查已加载、维度、Encounter domain、世界边界、保护与碰撞。在首个不可逆副作用前授权；不事后 setBlock 回滚冒充原子性，不无界加载区块。不能安全确定影响范围的能力显式 unsupported 或使用受约束适配，不静默放行。

免费 USE_BLOCK 仍只允许方块自身交互，禁止回退手持物品的 useOn/onItemUseFirst；同一方块状态发生变化并不能区分免费/收费。测试场内点击/场外落点、门床越界、可替换目标、保护取消、边界桶行为以及其他 Encounter 的范围。

## 2. 完成目标式交互，而不是扩充独立动作按钮

采用一个有明确状态转移的客户端交互所有者，例如 Idle、Armed/Targeting、ContextMenu、AwaitingServer、Executing、CancelPending；名字可随工程调整。服务端继续由 TacticalActions/CombatEngine 执行，客户端不裁决资源。

鼠标合同：
- Idle：左键地面提交移动意图；左键可选实体用于检查。右键实体/方块/地面打开与该目标绑定的上下文菜单，不转镜头、不直接执行所有行为。
- 选择武器/方块/能力后进入 Targeting：左键确认合法目标；右键或 Esc 取消尚未提交的选择。
- ContextMenu：左键菜单项提交菜单绑定的目标与物品/能力，不能重新拿菜单下方的世界射线命中替换目标。右键/Esc 关闭，迟到响应不可复活菜单。
- Executing：镜头仍独立可用。取消必须走服务端计划取消并等待终态；不能同时启用手动与自动身体驱动。
- Esc 按当前实际状态分层处理：上下文菜单/预览优先，其次运行计划取消，其次面板，最后游戏菜单；一次按键只处理一个层级。Screen、输入框和同意弹窗优先，不透传到世界行动。

快捷键按本项目已选定的迁移布局实现，可重绑：
WASD 镜头水平平移；QE 及中键拖动绕焦点旋转；滚轮缩放；Home 回到受控角色；O 切换斜俯视/更高俯视预设；1–9 物品/能力槽；左 Ctrl 准备当前主手攻击；I 背包；Space 结束回合；Shift+Space 使用现有进入/合法退出规则，不无条件退出战斗；Esc 分层取消。

Alt 标签、T 提示固定、F10 战术 HUD 可在核心闭环完成后补；必须分别报告实现与未实现，不用装饰功能替代修复。F10 隐藏时不得留下透明 UI 命中区。不要新增与当前任务无关的潜行、投掷、推击、队伍控制和十二槽系统。Z 若尚无真正战术跳跃能力，不用残留原版跳跃键冒充完成。

镜头动作不得改变身体位置、朝向、物品槽或任何资源。键位冲突按 recipient/state 消费，不修改用户保存的原版配置；释放/失焦/Screen 切换清理按下状态。真实输入映射、辅助绑定与鼠标 binding 应使用同一语义入口，不只支持硬编码默认键。

目标值需要实体身份、方块坐标/面/命中点、地面目的地和 Self，基于实际渲染镜头做拾取，并复验加载范围。对象检查/选择与执行资格分离；不把“只能攻击某类已适配实体”变成“其他世界实体不能被识别”。

UI 展示当前物品与能力、绑定目标、预期动作费用、移动需求/可达性或查询中状态、禁用原因和服务端执行阶段。相关能力预算不足应显示原因；不相关适配器不要全部堆进固定列表。鼠标手势维持已有 owner/revision 和 UI 优先级，不用另一套无生命周期的 static 字段处理同一事件。

## 3. 行为、移动和费用合同

沿用现有规则，不引入新的多点 AP 体系：攻击/方块放置/方块破坏/物品使用消耗一次普通动作；仅方块自身交互消耗 0 动作，接近仍独立消耗移动。选择、查询、菜单、镜头和未提交预览不收费。真正换装/整理按规则执行，客户端选择不是服务端换装成功。

完整意图为“稳定物品/手别/behavior/目标 → 验证 → 必要时接近合法执行位置 → 到位复验 → 原版执行 → 观察与结算 → 单一根终态”。已经满足执行条件时不强行移动；寻找合法作用位置，不走入目标占据格。

保留候选阶段可以接近的现有机制；首次敌对执行何时激活先攻以规则文档及当前明确决策为准，不能为了 UI 方便无条件激活。目标移动、卸载、失效、路径阻塞、预算不足必须有明确结束原因；同一请求不自动换成附近敌人。路径搜索权重不等于移动费用，继续观察原版实际主动位移计费。

原版玩家移动只经唯一的 LocalPlayer 输入/tick/travel/collision 与服务端核验链，不 teleport/setPos，不复制 Mob Navigation 驱动到玩家，不循环补跑多次身体 tick。寻路须验证脚下支撑、扫掠、台阶/跳跃、对角挤角、玩家体型和已加载范围；路径只是一份提案。

根计划开始/接近不提前扣攻击；合法攻击开始后未命中仍收费。放置失败与使用未处理的提交点明确；破坏和持续使用按一次行为收费，不按每包/每 tick 重扣。已经消耗移动不退款；接受后的持续使用取消不返动作；不能确认副作用时记 UNKNOWN 且禁止盲目重放。

保持“一个物品可以有多个行为”的注册式适配。当前白名单是支持边界，不是所有物品已兼容。禁止把未知物品/实体静默降级为普通近战或免费 use-block；不足能力明确 unsupported 并列出尚未验收范围。需要覆盖的 vanilla 类别通过固定版本实际调用链扩展，不靠添加几件 item ID 假装通用。不要引入未获批准的新射程/伤害数值。

## 4. 必须补的回归与实际验收

先新增能暴露 1.1–1.6 的失败测试，再修复。common 测试必须能在 Java 17 合约下运行；UI/网络/世界行为用对应 target 测试，不将 mock 当作真实客户端。

至少验证：
1. 致死近战 ATTACK 与 PLAN 都有正确的已确认终态；重试不重复扣费/造成伤害。目标先消失与真正不确定结果仍区别处理。
2. 按 1→2→3、点击同一热栏槽、服务器延迟/拒绝选槽：最终能力属于同一件服务端确认物品；取消后迟到 Options 不重开菜单。
3. 选武器→真实鼠标选远处目标→绕开障碍接近→恰好一次攻击；选方块→真实鼠标选命中面→预览/确认→一次放置与计费。已在范围内不多走。
4. 右键对象 A 后改选 B，旧菜单不能对 A 意外执行；点击菜单不会穿透到其下方地面；拖动释放不被误当世界点击。
5. 空闲、菜单、目标预览、接近、蓄力/食饮、取消待确认阶段分别测试 Esc/右键/Space/失焦/打开 Screen。
6. 普通动作耗尽后可在剩余移动允许时接近并开门/拉杆/箱子，但不能放置、破坏、使用物品；无自身交互方块不能通过免费路径触发物品使用。
7. 放置和多方块结构跨域/受保护/未加载时在副作用前拒绝；失败不误扣物品、不重放。
8. 新 Encounter/合并、死亡、换维度、断线、恢复时旧 options/requested/projection 不控制新角色或卡住新请求；过期/重复响应不能倒退状态。
9. 旧 ATTACK/MOVE 消息不绕过行为注册和计划授权；同 ID 同负载返回原结果，同 ID 不同负载冲突。
10. 专服两客户端：镜头彼此独立，远端看到真实角色移动与动作，资源与结果收敛。网络模拟应包含延迟/重复与应用层乱序投影。

现有 ClientControlRegression.planTick 直接调用 target()/choose()，只能验证这些方法之后的路径。保留该分层测试，但必须另加从真实键盘、鼠标拾取、菜单事件到网络的入口测试；不能用它证明数字键/选择状态已正确。GameTest 的 setPos/noGravity 可用于搭建 fixture，但不得用它们模拟玩家计划移动的成功。

在 `targets/neoforge-26.1` 使用正确 JDK 运行并记录实际输出：
```powershell
.\gradlew.bat :common:test
.\gradlew.bat clean build
.\gradlew.bat runGameTestServer
```
检查 build.gradle 中已有 dndturnControlProbe/controlProbeActive/controlProbeRequirePeer 配置并运行真实专服/双客户端验收；如无法运行，只记录未执行及具体原因，不写 PASS。禁止将本次提供的离线探针结果当成本轮你已重跑的测试，更不能当作 Gradle/JUnit/GameTest 通过。

## 5. 实施顺序和交付

阶段 A：结算、选槽一致性、取消/身份生命周期、旧入口与世界影响范围授权的缺陷和回归测试。
阶段 B：统一客户端交互状态机、物品驱动能力、目标/菜单、镜头键位与 UI 清理。
阶段 C：补齐端到端接近后执行、物品/方块支持范围和真实客户端验收。

分阶段在实际工作树修改并验证；不要为了“全量重构”覆盖无关模块或已正确的时间/伤害/持久化系统。最终报告：修改文件、解决的复现、实际执行命令及结果、未执行的验收、明确剩余限制。遵守仓库文档职责记录规则修订和未验收项。不要只输出待办清单，也不要把新增测试方法/编译成功写成玩法完成。
