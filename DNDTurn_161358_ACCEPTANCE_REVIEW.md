# DNDTurn 161358 修改验收

## 结论

**部分通过，不能标记为“交互重构完成”。** 新版已经有真实的战术镜头和服务端计划执行基础，不应回退到上一版的“完全没有 MoveTo/世界行为”判断。当前主要问题是客户端选择状态没有成为执行依据、输入到物品引用的时序不一致、取消和会话生命周期不闭合，并发现父计划死亡结算顺序回归。

审查：`DNDTurn-sources(20260925-161358).zip`，SHA-256 `d4b9f2f77d9ea8f74e4e25f7cc1ec56d0093103f8f9a5b211920909e13b1b599`。
对照：`DNDTurn-sources(20260925-135634).zip`。新增 26 文件、修改 25 文件、删除 0 文件。原始源码未修改。
主要 target：Minecraft 26.1 / NeoForge 26.1.2.84。新旧包均无 AGENTS.md 引用的 docs/。

## 本轮验证范围

| 验证 | 本轮结果 | 能说明什么 |
|---|---|---|
| 新旧源文件差异和调用链检查 | 已执行 | 当前实现、缺口和调用顺序 |
| 全部 18 个 common 主源码，以 javac 21.0.11 --release 17 编译 | 通过 | common 主源码可编译且符合该语言目标 |
| 独立离线计划/计费探针 | 6 项正向通过，1 项回归复现 | 无 Minecraft 的规则状态与终态顺序 |
| 仓库 JUnit | 未执行 | 依赖下载 DNS 失败；不能写成测试通过 |
| target 构建、Mixin 注入、GameTest | 未执行 | 环境没有 target 所需 JDK 25 |
| 真实客户端/专服/双客户端 | 未执行 | 不能确认实际键盘、鼠标、渲染和网络体验 |

下载：完整 Codex 提示词 `DNDTurn_161358_CODEX_PROMPT.md`；源码原行号摘录 `DNDTurn_161358_SOURCE_EVIDENCE.md`；探针源码、运行脚本和输出 `DNDTurn_161358_VALIDATION.zip`。

## 可以保留的修改

| 修改 | 当前验收级别 | 证据 |
|---|---|---|
| focus/yaw/pitch/distance 镜头；固定斜俯视初始姿态 | 源码确认 | E01 |
| WASD 水平平移、QE 与中键旋转、滚轮缩放 | 源码确认，真实输入未复验 | E01 |
| 1–5 不再默认绑定旧独立动作 | 源码确认 | E01 |
| 服务端 Query/Options、typed target、behaviorId/version、物品完整值指纹 | 源码确认 | E02/E03/E05 |
| PLAN→MOVE→执行；玩家合成输入；实际位移计费 | 源码已接入，部分规则离线通过 | E05/E09 |
| 根计划不提前扣动作；有效子操作按阶段计费；免费 USE_BLOCK 不扣动作 | common 层有正向探针通过 | E09 |
| 免费方块自身交互与付费物品分支隔离 | Mixin 实现存在，固定版本运行未复验 | E10 |
| 放置、破坏、食饮、桶、工具、命名牌、受限弓弩/雪球适配 | 实现存在且范围明确，不是所有物品通用兼容 | E10 |

## 必修问题

### R1：目标死亡会把已完成攻击的父计划记成 UNKNOWN

源位置：VanillaBehaviors.java:54–56；ServerCombatService.java:1969–1989、428–436、2112–2127；CombatEngine.java:500–512；TacticalActions.java:316–321。完整摘录 E06。

顺序：
```text
PLAN 尚未终结
→ ATTACK 子步骤已发布 COMPLETED
→ attack finally 清理死亡目标
→ engine.leave(target) 把仍指向 target 的 pending PLAN 发布 UNKNOWN
→ Melee.start 返回后再 finish 根计划，此时它已经是终态
```

离线对原 common 类复现了这个顺序，输出为 `ATTACK child=COMPLETED; PLAN=UNKNOWN`。Minecraft 中的实际致死路径仍需 GameTest，但规则层的结算顺序缺陷已经复现。不能把所有 UNKNOWN 改成成功，也不能改写历史结果；应修复执行完成与死亡清理的提交边界。

### R2：能力按钮只是写了一个无人读取的字段

源位置：ClientTacticalPlan.java:19、35–47、79–95；TacticalOverlay.java:132–142、175–187；ClientControl.java:333–359。完整摘录 E02。

`select(capability)` 没有影响 `target()` 的查询，也没有决定最终 Request；真正执行是另一个列表的 `choose(offer)`。旧六类动作按钮和服务器行为列表并存，使界面看起来可选动作，实际上选择没有进入请求。左键又只改 TacticalOverlay.selectedTarget，不会清掉旧 options。应把物品、行为与世界目标交给一个选择状态所有者，不能只是给旧抽象按钮补一条传参。

### R3：数字热栏与 UI 热栏不是同一条事务

源位置：CombatControls.java:109–120；TacticalNetwork.java:21–23；TacticalActions.java:69–72；TacticalOverlay.java:152–157。完整摘录 E03。

数字键事件在原版消费切槽队列前就发查询，查询没有 slot/reference；服务器读收到时的主手当前槽，因此存在查询旧物品的时序路径。UI 点击却手工选槽、发送 carried packet，再查询。需统一入口并绑定选择 revision、手别、槽位与服务端确认的物品身份；仅加一 tick 延迟不是稳健修复。

### R4：取消和会话身份不闭合

源位置：ClientTacticalPlan.java:45、49–95；ClientControl.java:211–213；ClientCombatState.java:100–109；CombatControls.java:267–285。完整摘录 E04。

取消只处理 requested，不清未提交 options/query。世界右键没有 Targeting/ContextMenu/Executing 分流，Esc 分层取消未实现。新 Encounter 被接受时只重置 CombatControls，未重置 ClientTacticalPlan；在同连接/同世界的身份变化中存在旧请求、菜单、投影残留路径。应明确 UI 取消、服务端取消待确认和身份重置三个不同动作。

### R5：新旧公开行为入口并存

源位置：CombatIntentHandler.java:104–115；CombatControls.java:254–262；ServerCombatService.java:1842–1907。完整摘录 E08。

公开旧 ATTACK 仍直接到 service.attack，不经过新行为注册器对物品的支持检查；MOVE_BEGIN 也保留旧手动许可入口。不能仅因默认键位解绑就认为旁路已关闭。玩家世界行为应汇入同一计划/行为校验；保留内部 Mob 执行器与历史结果重试的必要兼容。

### R6：点击目标的授权不等于最终放置影响范围的授权

源位置：TacticalActions.java:153–168；TacticalBehavior.java:51–56；VanillaBehaviors.java:96–104。完整摘录 E07。

新计划校验 clicked targetCell，但放置可以影响相邻位置，门/床等可以影响多个位置。桶有单独的相邻范围检查，不代表通用放置已覆盖。该缺口来自源码审查，未在 Minecraft 中实机复现越界。必须按固定版本 placement 上下文授权真实影响范围，在副作用前拒绝域外/受保护/未加载行为；不得事后回滚冒充原子提交。

## 未完成的交互迁移与测试覆盖

Home 回焦、O 镜头预设、Esc 分层取消、左 Ctrl 攻击准备等未在本模组注册；Alt/T/F10 也不能记为完成。核心顺序应先完善物品选择、目标/菜单和取消，再补便利功能。E01 提供完整键位注册。

背包可点击的只有前 9 个槽，其他槽仍是显示；“先整理至热栏”应有实际可达的整理入口。tactical.html 仍写“右键世界目标执行”，但实际右键只查询行为，应和最终状态机合同同步。

ClientControlRegression.planTick 确实新增真实客户端计划路径脚本，但直接调用 target()/choose()；它不经过数字键选槽、真实鼠标射线拾取与菜单点击，不能覆盖 R2/R3。TacticalPlanGameTests 有多个后端集成用例，但本轮没有执行。完整源码见 E11。

## 建议实施顺序

A：先修 R1–R6 并补能复现它们的测试，保留现有后端。
B：统一 Idle/Targeting/ContextMenu/AwaitingServer/Executing/CancelPending 的客户端状态，贯通数字热栏、I 面板、世界鼠标和能力选择；补核心镜头/取消键。
C：通过实际玩家移动链完成接近后攻击/方块/物品，补实际影响范围与支持合同，用专服双客户端从真实输入入口验收。

完整可直接交付 Codex 的任务、文件范围、限制与验收矩阵见配套提示词文件。
