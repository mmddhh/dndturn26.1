# DNDTurn 总体重构结论：世界 tick 默认放行，环境常驻轮末

**文档类型：架构总纲／实施约束**  
**版本：1.0 · 2026-09-26**  
**适用项目快照：`DNDTurn-sources(20260926-104127).zip`**  
**状态：目标设计与静态源码核对；不是实现完成报告。**

> **世界 tick 默认正常运行；主体主动行为按行动权执行；少数需要隔离思考时间的环境过程由环境参与者调度。环境是每个有效遭遇内始终存在的虚拟参与者，固定先攻显示值为 `-1`，永远位于普通参与者之后，每轮取得一次自己的回合。它不是普通成员结束回合后偷偷追加的若干 tick。**

这份文档固定的是总体方向。它不重新发明方块模拟，不把全部物理归给环境，也不恢复“世界只有获准执行窗口才运行”的设计。

## 0. 文档地位与旧结论修订

本文是下列配套指导的上位总纲：

- [同步交互与环境进度分离——重分类与重构指导](DNDTurn_Environment_Tick_Refactor_Guide_20260926.md)。继续用于 TNT、计划更新、漏斗及具体机制的分类。
- [对应源码证据](DNDTurn_Environment_Tick_Refactor_Evidence_20260926.md)。用于查找原始方法和调用链，不把旧建议当作新需求。

发生冲突时，以本轮明确的总体契约为准：

| 旧表述或误读 | 本文修订 |
|---|---|
| 世界只在环境回合／合法执行窗口运行 | 世界默认运行，只有被登记的敏感过程采用局部有效时间 |
| 环境只是普通成员队列耗尽后的过渡阶段 | 环境有常驻身份、先攻席位、当前行动者身份和自己的回合生命周期 |
| 核心回合引擎首轮完全不改 | 必须对参与者模型、轮转、快照和投影作必要修改；复用原有步骤设施，不重写第二套引擎 |
| 为保持方块兼容，必须追踪“获准后续反应” | 不建设通用因果授权继承；同步操作直接执行，未来托管工作按过程规则推进 |
| 本轮为防止 AI 泄漏，暂不解除普通实体冻结，因此以后也应继续整实体冻结 | “先建立独立主动行为门禁”只是安全迁移顺序；最终目标仍是放开普通身体与生命周期更新 |
| 在 UI 最后一行写“环境”，就完成显式参与者 | UI、服务端真实 order/current、存档与事件必须表达同一个环境席位 |

本文不替代原版全局冻结、区块加载等前置条件。“默认放行”指撤销 DNDTurn 自己不必要的拦截，不是强行更新原版本就不该更新的对象。

## 1. 必须固定的总体决策

| 编号 | 规范 |
|---|---|
| D01 | 不因为存在战斗、玩家正在思考、或当前不是环境回合，就取消整个世界更新。 |
| D02 | 不把“主体没有行动权”解释成“该实体不能受力、不能受伤、不能死亡、不能执行生命周期维护”。 |
| D03 | Mob 自主决策、玩家主动输入和能力执行单独鉴权；普通身体模拟不是这些权限的代用品。 |
| D04 | 计划更新、选定的生产／持续危险等使用环境有效时间，不能消费或补算纯等待经过的真实时间。 |
| D05 | 环境是回合参与者，不是真实 Minecraft 实体；每个存续遭遇有且只有一个环境身份。 |
| D06 | 环境固定显示先攻 `-1`，排序始终压在所有普通参与者之后，不参加掷骰和同分竞争。 |
| D07 | 环境获得自己的回合后自动执行已有预算，结束后才跨入下一轮；其他成员结束回合不额外附送环境进度。 |
| D08 | 环境不会因为当前没有 TNT、机器或待执行任务而从参与者集合中消失。 |
| D09 | 环境不计为敌人、存活生物、攻击目标或维持战斗人数；不能导致遭遇永不结束。 |
| D10 | 只做局部过程特判。不得为每个方块更新附加“最初是谁触发”的授权链。 |

“始终存在”的范围是**遭遇的存续期**：创建遭遇时建立身份，直到遭遇结束才销毁或归档。尚未进入正常轮转的准备状态可以暂不执行该席位，但身份已经存在。区域外的普通世界不需要为了正常运作再创建一个永久战斗。

## 2. 当前代码的实际差距

当前并非“完全没有环境概念”。源码已经包含 `ENVIRONMENT` 阶段、预算、步骤授权／提交／失败记录，以及客户端环境提示。需要升级的是其参与者语义，而不是把这些基础设施全部删除。[C01][C02][C05][C05b]

关键差距如下：

1. `order` 由普通 `Member` 构造和排序；`currentId()` 在环境阶段返回 `null`。[C01]
2. 普通成员游标走出列表后，`advanceCursorState()` 才调用 `enterEnvironment()`，把游标重设为 `0` 并设置环境预算。[C02]
3. 环境预算耗尽后，`commitEnvironmentStep()` 重建普通成员顺序并开始下一轮。[C02]
4. 服务端 roster 从 `state.members()` 构造，客户端有“环境推进”文字和剩余数值，但这不等于环境已是常驻的真实轮转项。[C05][C05b]
5. 普通实体当前仍可能因为处于受控区域且没有 `MobMoveLease` 而整个暂停；现有 Goal 抑制依赖移动 lease，玩家侧还存在整个 `doTick()` 的提前取消。[C03][C04][C04b]

因此，本次需要两条并行但独立的重构：**让环境成为真实的逻辑参与者；让世界／身体默认更新与主动控制权限脱钩。** 单做 HUD 或单改一个 tick 返回值都不构成完成。

## 3. 世界运行、主动控制与环境时间是三层规则

### 3.1 世界与普通身体默认运行

保留正常服务器更新骨架、实体与方块实体容器维护、同步、保存、合法直接交互，以及未被特判的原版物理和生命周期。死亡、掉落、载具关系、碰撞等不应仅因当事人缺少行动权而被总门禁截断。

“世界 tick 放行”不等于全部原版行为随便执行。它要求**拦截真正需要限制的行为入口，而不是用阻止整个实体更新替代行为控制**。

### 3.2 主动行为仍由主体行动权决定

玩家移动输入、攻击、物品使用、放置和破坏等，沿各自规则校验。Mob 的新自主决策、未经授权的导航驱动和攻击，不得因为身体 tick 恢复而泄漏。

已获准的导航需要保留必要的导航、控制器、身体更新协作；未经许可的主动输入应停止，但不能通过每 tick 清零整个速度向量来抹掉击退、水流和外力。被动位移也不能直接当作受击者主动移动来计费。

### 3.3 环境参与者只推进明确托管的过程

以下沿用配套分类指导，不重新增加因果继承：

| 类别 | 规则 |
|---|---|
| 直接操作与未被专门覆盖的同步响应 | 原版入口合法执行后，当前结果可以立即成立 |
| 环境托管的延时、周期、自动过程 | 只消费所属规则授予的有效环境步骤，纯等待不增加进度 |
| 其他普通更新／精确表现维护 | 常规直通，不向环境申请“存在资格” |

世界 `gameTime` 与环境有效步数不等价。全局时间可以前进，而 TNT 的有效引信、漏斗的自动进度或中继器的剩余延迟没有消费等待时间。不得为此全局伪造 `getGameTime()`，也不得恢复时集中补算等待。

**思考不改变战斗结果是需要逐机制满足的目标，不能仅凭“物理是被动的”宣称全部满足。** 对确实会借等待产生结果的机制，补充明确的局部过程规则；不以这个覆盖任务为理由退回全世界冻结。随机源、日照输入、绝对时间和持续接触也属于后续必须验收的边界。

## 4. 环境参与者的数据模型

### 4.1 回合参与者不等于实体成员

建议把两个集合分开，名称可随现有代码风格调整：

```text
战斗实体成员：玩家、Mob 等真实主体。
回合参与者：符合本轮资格的战斗实体成员 + 唯一环境席位。
```

不得仅向现有 `members` 映射塞一个伪 UUID，并期望所有消费者继续把它当成实体。那会把环境带入实体查找、死亡清理、敌对关系和资源恢复。

最小逻辑身份可以是：

```text
ParticipantKey = (kind, ownerId)
kind = ENTITY | ENVIRONMENT

ENTITY 的 ownerId 指向原实体成员。
ENVIRONMENT 的 ownerId 指向所属 encounter/domain。
```

这是接口契约示意，不要求机械增加同名类。也可以使用稳定的专门 ID，但必须有类型标记，不能靠“解析不到实体”猜它是环境。

### 4.2 环境身份的必需属性

| 属性 | 规范 |
|---|---|
| 所属者 | 某个明确的遭遇／模拟域 |
| 身份 | 该遭遇存续期间稳定，保存与恢复后可识别 |
| 显示名称 | 环境 |
| 先攻 | 固定 `-1`，不可掷骰、不可临时修改 |
| 排序角色 | 固定末位 |
| 回合预算 | 使用现有该遭遇捕获的环境预算，不因本次模型重构擅自改数值 |
| 执行者 | 服务端自动调度，不等待玩家确认 |
| 目标资格 | 不是可攻击、可寻路、可推开或可乘坐的世界目标 |
| 普通动作资源 | 不套用生物动作点、移动点和反应点模型；环境有自己的步骤预算 |

同一遭遇只能有一个。TNT、水、熔炉、漏斗等是该参与者所管理的过程，**不是每个对象各自增加一个先攻席位**。

### 4.3 无任务时仍然存在

环境身份的存在性不依赖任务队列是否为空。环境在轮转前、轮转中、尚无危险物时都应出现在视图里。

首版仍走明确的环境回合生命周期和预算，不根据“计划队列为空”跳过整个环境，因为方块实体、随机／接触过程不一定表现为一条待处理计划任务。以后可优化被证明完全无结果的空执行，但不得消失身份、额外赠送时间或绕过需要记录的回合开始／结束。

## 5. 先攻 `-1` 与“永远最后”的实现契约

### 5.1 推荐顺序

```text
第 N 轮
    普通参与者 A（先攻 18）
    普通参与者 B（先攻 12）
    普通参与者 C（先攻  4）
    环境        （先攻 -1）

环境回合结束
    → 第 N+1 轮
```

本轮顺序构造建议：

```text
1. 按原有资格选取普通参与者。
2. 按原有先攻、tieBreak 和稳定键排序。
3. 在列表末尾追加本遭遇唯一的环境参与者。
```

不要把 `-1` 解释成“未掷先攻”“无当前参与者”“不可见”。它是环境固定显示值。

### 5.2 数值不能代替排序保证

当前随机先攻路径使用 `1 + random.nextInt(20)`，因此 `-1` 低于这些正常新掷值。[C02] 但仅靠整数降序不能在允许 `-1` 同分或更低数值时保证环境末位。

所以规范是：**先按参与者种类保证普通在前、环境在后，再在普通参与者内部使用原有排序；或直接采用末尾追加。** 不必为了环境席位强行修改普通角色的属性范围。

测试必须包括普通角色 `-1`、`-5`、`Integer.MIN_VALUE` 等人工构造状态，证明末位保证来自规则而非碰巧的取值范围。

### 5.3 动态变化不破坏环境席位

新成员加入仍按现有本轮／下轮资格规则处理；不要悄悄改变原有加入时机。环境不得因排序、重掷、跳过死亡成员、删除成员或视图重建而重复插入、消失或提前执行。

建议使用稳定参与者身份定位当前席位，而不是仅靠数组下标修补删除后的游标；具体实现可以保留 cursor，但必须覆盖删除当前、删除末位普通成员及连续退场的测试。

## 6. 真正的环境回合生命周期

### 6.1 不再是“队列外的补 tick”

目标状态流：

```text
当前参与者 = 普通主体
    → 它的回合结束
    → 选择顺序中的下一个参与者

下一个参与者 = 环境
    → currentParticipant = 本遭遇环境身份
    → 记录并同步环境回合开始
    → 自动执行有限环境预算
    → 记录并同步环境回合结束
    → 环境是末位，因此推进轮数一次
    → 开始下一轮第一个有效参与者
```

环境仍可以保留 `EncounterPhase.ENVIRONMENT` 作为派生标签或兼容字段。**允许有环境 phase；不允许只有 phase、没有真实席位和 current 身份。**

不得保留两条同时生效的路径：新环境席位消耗预算之后，旧 `enterEnvironment()` 的队列外尾段又补跑一次。

### 6.2 执行沿真实世界更新推进

环境取得回合后，平台调度器在正常服务器更新边界申请、执行并提交环境步骤。一个有效环境步表示所属过程获得一次一致的推进机会，不表示为了该回合再调用一次整个世界的 `tick()`。

```text
真实服务器更新
    → 原版前置条件通过
    → 检查当前参与者确为该域的环境、仍有预算
    → 保留／授权一个命名步骤
    → 原版更新骨架继续执行
        普通更新照常
        匹配该环境步骤的托管过程取得执行机会
        其他遭遇的托管过程仍不推进
    → 观察已完成结果并提交一次
    → 剩余预算耗尽则结束环境回合
```

同一域在同一个服务器 tick 中不能重复消费同一环境步。方块、流体、实体和 BE 共享该步骤机会，不是每个对象各领一份预算。禁止同一个服务器 tick 内手动循环调用整个世界或 TNT 的 tick 来快速耗完环境预算。

### 6.3 时间、失败与循环边界

环境预算在开始该环境回合时初始化一次，不在每个服务器 tick、重连或读档时重新填满。已有 `authorizeEnvironmentStep / commitEnvironmentStep` 及去重、失败记录应复用。[C02]

原版全局冻结时不偷跑。若没有执行世界副作用，可以取消尚未执行的预留；若已经发生部分副作用，不能当作“完全失败”再次重演爆炸、物品转移或掉落。

跨步骤的任务按自己的有效剩余延迟保存，剩余工作可跨下一次环境回合。不能排空整个新生任务队列，也不能为持续电路无限续发预算。

## 7. 存档、网络、界面与遭遇结束必须同步修改

### 7.1 快照和网络必须区分参与者种类

需要同步与持久化的语义包括：环境稳定身份、完整参与者顺序、当前参与者、当前轮数、环境回合是否已经开始、剩余预算，以及现有步骤授权／完成记录。

当前快照主要保存 `MemberState`、`order`、`cursor`、`phase` 和环境剩余值；其恢复检查假定顺序中的身份都来自普通成员。[C06][C06b] 因此不能只往现有 order 中追加一个未知 UUID；需要版本化迁移和新的验证条件。

旧存档恢复的建议：

- 创建或恢复恰好一个环境身份；普通成员顺序与资格保持原语义。
- 旧 phase 为 `ENVIRONMENT` 时，将 current 指向环境，并保留剩余预算及既有步骤证据；不得重启满额环境回合。
- 旧 phase 为普通行动时，保留当前实体，将环境补在末尾，不消费额外时间。
- 正在执行但结果未知的旧步骤沿既有恢复规则隔离，不自动重放。

字段和 schema 的具体升级以目标仓库实际版本为准。不要用删除旧存档、静默重置或丢掉托管任务来代替迁移。

### 7.2 先攻条必须显示真实环境项

环境始终显示在该遭遇的先攻条末尾，标明 `-1`。环境取得回合时像其他项一样高亮，可显示已用／剩余环境步；不展示生物生命值、移动点或装备槽。

环境身份不能走实体解析后因实体不存在而被过滤。客户端必须使用服务端给出的参与者类型和顺序，不能单独再按整数先攻排序。

玩家不能通过“结束回合”请求跳过或重复结束环境回合；环境自动执行。观察镜头、界面和允许的非战斗交互不因为当前环境回合而被整个客户端暂停。

### 7.3 战斗存续判断只看真实战斗成员

胜负、敌对关系、在线玩家、逃离、死亡清理等，使用真实成员集合，不把环境当作永远活着的最后一个敌人。

遭遇结束时，环境身份随之退出／归档；托管过程按明确的退出策略恢复原版或迁移，且只释放一次。**“环境始终存在”不是“最后一个玩家退出后环境仍强制维持空战斗”。** 已被点燃的 TNT 等世界对象不能因为逻辑环境席位销毁而被随意删除。

合并遭遇后只能保留目标遭遇的一个环境身份；尚未完成的队列、步骤和预算按现有合并安全边界迁移，不简单叠加两份完整预算。A 域的环境取得回合，不能给 B 域偷跑时间。

## 8. 与方块／TNT 分类的关系

显式环境席位不改变已经确定的机制分工，只把原来隐蔽的推进机会变成可观察、可保存的回合。

| 场景 | 普通参与者操作时 | 环境参与者回合 |
|---|---|---|
| 拉杆／按钮无延时供电到 TNT | 同步状态变化与点燃成立，创建点燃实体 | 推进 TNT 的已登记环境过程，引信到期才爆炸 |
| 经中继器供电到 TNT | 登记延迟任务；不能因找到了玩家来源就跳过延迟 | 达到有效延迟后改变输出、点燃 TNT；后续按剩余预算与原版阶段继续 |
| 漏斗 | 合法玩家存取不与自动搬运混为一谈 | 冷却与自动搬运，包括接触吸入旁路，使用同一环境规则 |
| 水源放置 | 获准放水的直接结果成立 | 推进受管控的流体传播 |
| 普通身体、已成立死亡的生命周期 | 不因为“不是自己的回合”而一律取消 | 环境可以产生伤害，但被害者无需取得行动权才能受伤／死亡 |

具体延迟、特殊实体步、旁路和叶片分类仍以配套指导及目标源码为准，不在这份总纲中重新增加“玩家反应续接”层。特别是 TNT 的局部环境物理建议，不应推广成所有 Mob、物品、船都只能在环境回合更新。

## 9. 实施顺序与文件职责

### 阶段 A：环境成为显式参与者

先补纯规则测试，再修改参与者身份、order/current、开始／结束调度。保留现有预算与步骤授权机制，但让它们服务于“当前环境参与者”，而不是继续独立于队列运行。

### 阶段 B：快照和客户端形成闭环

同步修改保存／恢复、协议及先攻条。环境必须从第一次遭遇投影起就存在；中途保存再恢复，不重置预算、不重复取得一次环境回合。完成真实调度与显示的一致性，不接受仅 UI 实现。

### 阶段 C：独立主动行为门禁，再放开普通身体

先使 Mob 自主决策与玩家主动输入的门禁不再依赖整实体冻结；检查 Goal、运行中控制、Brain／特定行为入口的目标版本调用链。然后撤掉普通实体与玩家身体的默认总冻结，保留明确登记的局部环境特例。

这一步可以分提交，但交付版本不能出现“总冻结已撤掉，主动门禁尚未建立”的中间状态。不得把纯粹恢复普通身体所需的工作无限推迟，最后仍以旧冻结作为正式完成结果。

### 阶段 D：对接过程分类与生命周期回归

复用计划队列托管，落实 TNT、漏斗等少量具体覆盖；验证死亡、掉落、导航、载具、存档和失败边界。不改变环境默认预算，不通过升级 NeoForge 来逃避当前版本差异。

| 现有文件／模块 | 修改责任 |
|---|---|
| `common/.../CombatEngine.java` | 区分实体成员与参与者；常驻环境身份；完整 order/current；唯一轮转路径 |
| `common/.../CombatStateSnapshot.java` | 新身份语义及版本化恢复；旧环境阶段迁移到环境 current |
| `combat/ServerCombatService.java` | 由显式当前环境身份驱动步骤；真实成员与展示 roster 分开；收缩整实体默认暂停 |
| `combat/CombatNetwork.java` | 传递参与者种类、完整顺序和 current；避免环境落入实体字段假设 |
| `client/TacticalOverlay.java` 等投影消费者 | 末位常驻环境项、当前高亮、预算展示；不靠本地插入假项 |
| `mixin/MobNavigationLeaseMixin.java` 及相关控制入口 | 自主行为权限独立于身体更新；保留获准导航执行能力 |
| `mixin/ServerPlayerTickMixin.java`、实体更新门禁 | 从整个身体取消迁移到主动输入／过程级控制 |
| `RegionalScheduledTicks` 及具体过程适配 | 继续保留延期、去重、有效进度；接收合法环境步骤，不自己推进回合 |

以上路径依据当前快照；新类型名是职责建议，不要求照名创建大量抽象层。

## 10. 必须通过的验收合同

| 编号 | 场景 | 验收要求 |
|---|---|---|
| B01 | 首次创建遭遇，场上无环境任务 | 服务端参与者和首份客户端先攻条已有一个环境项，显示 -1 |
| B02 | 普通角色先攻任意、含 -1 和更低值 | 所有普通角色始终在环境之前；普通角色内部排序不被破坏 |
| B03 | 多个普通成员依次结束 | 每次只进入下一个参与者；环境每轮一次，不每人结束就执行一次 |
| B04 | 最后普通成员结束 | current 成为环境身份，而非 null；轮数尚未提前增加 |
| B05 | 环境预算全部完成 | 环境结束与轮数增加各发生一次，再进入下一轮首位 |
| B06 | 环境当前无显式计划任务 | 环境席位不被删除；不能仅凭队列空跳过其他时间进度 |
| B07 | 改变玩家思考时长 | 世界时钟正常推进；已托管 TNT、延迟、生产等不额外消耗有效进度 |
| B08 | 普通 Mob／玩家身体默认更新 | 无未授权自主移动或攻击；合法受力、伤害、死亡和必要维护不因无行动权被一律取消 |
| B09 | 导航与被动位移混合 | 获准导航保持控制与身体协作；停止主动控制不抹掉外力、不把被动位移全算主动消耗 |
| B10 | 拉杆／按钮直接 TNT，对照中继器路径 | 前者可直接点燃；后者先等有效延迟；爆炸不受思考长度额外推进 |
| B11 | 环境回合中保存／重连 | 恢复相同环境身份、current、剩余预算与步骤记录，不重新填满预算 |
| B12 | 普通回合中保存／旧存档升级 | 保留普通 current，环境恰好补一份，不重复或漏过已完成回合 |
| B13 | 成员死亡、退出、加入 | 真实成员逻辑正确；环境不被死亡清理，不重复、不计作敌人 |
| B14 | 全部真实战斗主体按规则退出 | 遭遇能结束，环境不维持空战斗；托管状态只释放一次 |
| B15 | 两个域与域合并 | A 环境不推进 B；合并后唯一环境身份，预算及任务无复制 |
| B16 | 原版全局 freeze／step | 不绕过全局条件，不在无有效执行时扣环境预算 |
| B17 | 步骤部分失败 | 不自动重放爆炸、转移或掉落；执行状态可审计 |
| B18 | 客户端伪造环境结束或行动包 | 服务端拒绝，不允许用户替环境跳步或复位预算 |
| B19 | 回合重建、先攻重掷、移除末位普通成员 | 环境不参与随机掷骰，也不被排序／游标变更遗漏 |
| B20 | 更新去重和调度顺序 | 一个环境步不因多个回调或两条旧新路径被扣两次；不手动反复调用世界 tick |

测试报告应区分规则单元测试、目标源码编译、GameTest 实际运行和人工场景。本次文档制作没有执行这些验证，不能将该表写成已通过。

## 11. 给实施者的执行摘要

> 阅读本总纲，再读取配套的方块／环境分类指导。先将环境建模为每个遭遇常驻的虚拟回合参与者，固定显示先攻 -1，类型级末位保证，不掷骰，不绑定真实世界实体。order、current、开始／结束、快照、网络与 HUD 必须一致；即使无环境任务也不得移除席位。普通回合结束只推进到下一参与者；环境回合用现有命名步骤和预算自动推进，完成后才增加一轮，旧的队列外环境补跑必须失效。世界 tick 与未托管普通身体默认运行，主动 AI／玩家控制单独鉴权；必须先切断对整实体冻结的权限依赖再解除冻结。计划更新、机器与明确危险仍采用局部环境时间，不补算思考时长，不创建通用获准反应或玩家来源传播。环境不计入攻击目标、敌对和存续人数；保存恢复、合并、退出、异常均要保持唯一身份、预算与副作用不重复。按 B01—B20 报告真实验证结果，未完成的机制明确列出，不通过重新全局冻结来掩盖。

---

## 附录：本轮直接核对的项目源码

以下内容是**当前实现证据**，不是目标代码。来自用户上传 ZIP 的原始文本解包，因该 ZIP 的 Files 正文索引不可读而使用原始字节核查。`Lxxx` 为对应 Java 原文件行号，不是伪造的 Files 引用行号。

本轮总纲不依赖新增的原版 API 事实；机制细节沿用配套 `.109` 核查。项目声明目标 `.84`，实施仍须验证目标版本方法及描述符，不擅自升级。

原始 ZIP SHA-256：`6ddead935527a9367f5666732b24d40187e553bcd65637a990294f7ddd4b5ca0`。

| 标识 | 内容 |
|---|---|
| [C01](#c01) | 现有实体成员、队列与环境时 current=null |
| [C02](#c02) | 环境步骤、普通先攻排序与队列外环境入口 |
| [C03](#c03) | 区域内无移动许可即暂停的普通实体分支 |
| [C04](#c04) | 主动行为抑制依赖 lease；玩家身体整体提前返回 |
| [C04b](#c04b) | 玩家身体整体提前返回 |
| [C05](#c05) | 服务端投影仅由 members 构造；已有环境文字不等于席位 |
| [C05b](#c05b) | 客户端已有环境阶段与剩余步骤展示 |
| [C06](#c06) | 当前快照和恢复的成员队列假设 |
| [C06b](#c06b) | 恢复时校验 order 全部属于普通 members |
| [C07](#c07) | 当前目标版本声明 |

<a id="c01"></a>
### C01 — 现有实体成员、队列与环境时 current=null

原文件：`common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`  
SHA-256：`3903e875f2d72a183e5dfb282774b4167a11f1b76bfe9f7ac202c92bc527999c`

原文件 L40—L51：

```java
L0040 |     public record MemberView(UUID id, int initiative, int tieBreak, int movementTicks, boolean action,
L0041 |                              boolean reaction, boolean dodging, boolean disengaged, long eligibleRound) {}
L0042 |     public record View(UUID id, EncounterPhase phase, long version, Bounds bounds, List<UUID> order,
L0043 |                        UUID current, Map<UUID, MemberView> members, List<OperationRecord.Result> results,
L0044 |                        EncounterRegion region, long round) {
L0045 |         public View { order = List.copyOf(order); members = Map.copyOf(members); results = List.copyOf(results); }
L0046 |     }
L0047 |     /** Turn state without copying the ever-growing result history. */
L0048 |     public record StateView(UUID id, EncounterPhase phase, long version, List<UUID> order, UUID current,
L0049 |                             Map<UUID, MemberView> members, long round, int environmentRemaining,
L0050 |                             EncounterRegion region) {
L0051 |         public StateView { order = List.copyOf(order); members = Map.copyOf(members); }
```

原文件 L84—L103：

```java
L0084 |     private static final class Encounter {
L0085 |         final UUID id;
L0086 |         final int movementTicksPerTurn;
L0087 |         final int environmentTicks;
L0088 |         EncounterRegion region;
L0089 |         EncounterPhase phase = EncounterPhase.CANDIDATE;
L0090 |         long version;
L0091 |         long structuralRevision;
L0092 |         long round;
L0093 |         int cursor;
L0094 |         int environmentRemaining;
L0095 |         UUID authorizedEnvironmentStep;
L0096 |         /** Once any simulation step is authorized, this entry can no longer accept a merge. */
L0097 |         boolean environmentStepAuthorized;
L0098 |         InitiativeRoll boundaryPriority;
L0099 |         final Map<UUID, Member> members = new LinkedHashMap<>();
L0100 |         final Set<Relation> hostile = new HashSet<>();
L0101 |         /** A projectile attack can connect sessions before their fixed regions overlap. */
L0102 |         final Set<UUID> causalMergeNeighbors = new HashSet<>();
L0103 |         final List<UUID> order = new ArrayList<>();
```

原文件 L590—L593：

```java
L0590 |     private static UUID currentId(Encounter encounter) {
L0591 |         return encounter.phase == EncounterPhase.ENVIRONMENT || encounter.phase == EncounterPhase.ENDED
L0592 |             || encounter.order.isEmpty() ? null : encounter.order.get(encounter.cursor);
L0593 |     }
```

<a id="c02"></a>
### C02 — 环境步骤、普通先攻排序与队列外环境入口

原文件：`common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`  
SHA-256：`3903e875f2d72a183e5dfb282774b4167a11f1b76bfe9f7ac202c92bc527999c`

原文件 L1218—L1234：

```java
L1218 |     /** Reserve one named simulation step; only one may execute at a time. */
L1219 |     public boolean authorizeEnvironmentStep(UUID encounterId, UUID stepId) {
L1220 |         Objects.requireNonNull(stepId);
L1221 |         Encounter encounter = require(encounterId);
L1222 |         if (encounter.completedEnvironmentSteps.contains(stepId)) return false;
L1223 |         if (encounter.phase != EncounterPhase.ENVIRONMENT || encounter.environmentRemaining < 1
L1224 |             || !encounter.pending.isEmpty()) throw new IllegalStateException("not ready for environment simulation");
L1225 |         if (encounter.authorizedEnvironmentStep != null) {
L1226 |             if (encounter.authorizedEnvironmentStep.equals(stepId)) return false;
L1227 |             throw new IllegalStateException("another environment step is executing");
L1228 |         }
L1229 |         long nextVersion = Math.addExact(encounter.version, 1);
L1230 |         encounter.authorizedEnvironmentStep = stepId;
L1231 |         encounter.environmentStepAuthorized = true;
L1232 |         encounter.version = nextVersion;
L1233 |         return true;
L1234 |     }
```

原文件 L1280—L1310：

```java
L1280 |     /** Caller supplies a stable ID for the simulation step it actually completed. */
L1281 |     public boolean commitEnvironmentStep(UUID encounterId, UUID stepId) {
L1282 |         Objects.requireNonNull(stepId);
L1283 |         Encounter encounter = require(encounterId);
L1284 |         if (encounter.completedEnvironmentSteps.contains(stepId)) return false;
L1285 |         if (encounter.phase != EncounterPhase.ENVIRONMENT || encounter.environmentRemaining < 1)
L1286 |             throw new IllegalStateException("not an environment turn");
L1287 |         if (!stepId.equals(encounter.authorizedEnvironmentStep) || !encounter.pending.isEmpty())
L1288 |             throw new IllegalStateException("environment step was not authorized or is still executing");
L1289 |         long nextVersion = Math.addExact(encounter.version, 1);
L1290 |         long nextRound = encounter.environmentRemaining == 1 ? Math.addExact(encounter.round, 1) : encounter.round;
L1291 |         List<UUID> nextOrder = null;
L1292 |         if (encounter.environmentRemaining == 1) {
L1293 |             nextOrder = new ArrayList<>();
L1294 |             for (Member member : encounter.members.values())
L1295 |                 if (member.eligibleRound <= nextRound) nextOrder.add(member.id);
L1296 |             nextOrder.sort(Comparator.<UUID>comparingInt(id -> encounter.members.get(id).initiative).reversed()
L1297 |                 .thenComparingInt(id -> encounter.members.get(id).tieBreak).thenComparing(UUID::compareTo));
L1298 |             if (nextOrder.isEmpty()) throw new IllegalStateException("no eligible member for next round");
L1299 |         }
L1300 |         encounter.completedEnvironmentSteps.add(stepId);
L1301 |         encounter.authorizedEnvironmentStep = null;
L1302 |         if (--encounter.environmentRemaining == 0) {
L1303 |             encounter.phase = encounter.hostile.isEmpty() ? EncounterPhase.CANDIDATE : EncounterPhase.ACTIVE;
L1304 |             encounter.round = nextRound;
L1305 |             encounter.order.clear();
L1306 |             encounter.order.addAll(nextOrder);
L1307 |             encounter.cursor = 0;
L1308 |             beginMemberTurn(encounter, encounter.members.get(encounter.order.get(encounter.cursor)));
L1309 |         }
L1310 |         encounter.version = nextVersion;
```

原文件 L1344—L1348：

```java
L1344 |     private InitiativeRoll sampleRoll() { return new InitiativeRoll(1 + random.nextInt(20), random.nextInt()); }
L1345 |     private Map<UUID, InitiativeRoll> sampleInitiatives(Encounter encounter) {
L1346 |         Map<UUID, InitiativeRoll> rolls = new HashMap<>();
L1347 |         for (Member member : encounter.members.values()) rolls.put(member.id, sampleRoll());
L1348 |         return rolls;
```

原文件 L1364—L1400：

```java
L1364 |     private void buildOrder(Encounter encounter) {
L1365 |         encounter.order.clear();
L1366 |         for (Member member : encounter.members.values())
L1367 |             if (member.eligibleRound <= encounter.round) encounter.order.add(member.id);
L1368 |         sortOrder(encounter);
L1369 |         encounter.cursor = 0;
L1370 |     }
L1371 |     private void sortOrder(Encounter encounter) {
L1372 |         encounter.order.sort(Comparator.<UUID>comparingInt(id -> encounter.members.get(id).initiative).reversed()
L1373 |             .thenComparingInt(id -> encounter.members.get(id).tieBreak).thenComparing(UUID::compareTo));
L1374 |     }
L1375 |     private boolean finishMemberTurnState(Encounter encounter, Map<UUID, InitiativeRoll> rolls) {
L1376 |         if (encounter.phase == EncounterPhase.CANDIDATE && !encounter.hostile.isEmpty()) {
L1377 |             activate(encounter, rolls);
L1378 |             return true;
L1379 |         }
L1380 |         advanceCursorState(encounter);
L1381 |         return false;
L1382 |     }
L1383 |     private void advanceCursorState(Encounter encounter) {
L1384 |         Member finishing = encounter.members.get(encounter.order.get(encounter.cursor));
L1385 |         if (finishing != null) finishing.disengaged = false;
L1386 |         encounter.cursor++;
L1387 |         if (encounter.cursor >= encounter.order.size()) {
L1388 |             enterEnvironment(encounter);
L1389 |         } else beginMemberTurn(encounter, encounter.members.get(encounter.order.get(encounter.cursor)));
L1390 |     }
L1391 |     private void enterEnvironment(Encounter encounter) {
L1392 |         if (!encounter.order.isEmpty()) {
L1393 |             Member previous = encounter.members.get(encounter.order.get(encounter.order.size() - 1));
L1394 |             if (previous != null) encounter.boundaryPriority = new InitiativeRoll(previous.initiative, previous.tieBreak);
L1395 |         }
L1396 |         encounter.cursor = 0;
L1397 |         encounter.phase = EncounterPhase.ENVIRONMENT;
L1398 |         encounter.environmentRemaining = encounter.environmentTicks;
L1399 |         encounter.environmentStepAuthorized = false;
L1400 |     }
```

<a id="c03"></a>
### C03 — 区域内无移动许可即暂停的普通实体分支

原文件：`targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`  
SHA-256：`e252fa42d1232bac22385e0f967e882e13f3d3958b9a30de0c0ad051b7deca07`

原文件 L2694—L2712：

```java
L2694 |     /** Pure query. Preparing a vanilla simulation step is a separate execution boundary. */
L2695 |     public boolean isEntitySimulationPaused(Entity entity) {
L2696 |         if (quarantinedProjectiles.containsKey(entity.getUUID())) return true;
L2697 |         if (entity instanceof AbstractArrow || entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball && projectileOrigins.containsKey(entity.getUUID())) {
L2698 |             UUID recorded = projectileSimulationDomains.get(entity.getUUID());
L2699 |             UUID domain = recorded == null ? null : engine.canonicalEncounterId(recorded);
L2700 |             if (domain == null || !engine.encounterIds().contains(domain)) return isEntityInsidePausedRegion(entity);
L2701 |             return pendingProjectileAttacks.containsKey(entity.getUUID())
L2702 |                 || !domain.equals(activeEnvironmentEncounters.get(entity.level()));
L2703 |         }
L2704 |         if (!isEntityInsidePausedRegion(entity)) return false;
L2705 |         MobMoveLease lease = mobMoves.get(entity.getUUID());
L2706 |         if (lease == null || !activeMobLease(lease)) return true;
L2707 |         if (lease.pathStarted && entity instanceof Mob mob) {
L2708 |             int cost = nextMobStepCost(mob, lease);
L2709 |             return cost < 0 || engine.stateView(lease.encounterId).members().get(lease.mobId).movementTicks() < cost;
L2710 |         }
L2711 |         return false;
L2712 |     }
```

原文件 L2731—L2747：

```java
L2731 |     private boolean prepareEntitySimulationStep(Entity entity) {
L2732 |         if (quarantinedProjectiles.containsKey(entity.getUUID())) return true;
L2733 |         if (entity instanceof AbstractArrow arrow) return arrowSimulationPaused(arrow);
L2734 |         if (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball && projectileOrigins.containsKey(entity.getUUID())) {
L2735 |             UUID domain = liveProjectileDomain(entity.getUUID());
L2736 |             return domain != null && !domain.equals(activeEnvironmentEncounters.get(entity.level()));
L2737 |         }
L2738 |         MobMoveLease lease = mobMoves.get(entity.getUUID());
L2739 |         if (lease != null && !activeMobLease(lease)) {
L2740 |             revokeMobLease(lease);
L2741 |             lease = null;
L2742 |         }
L2743 |         if (!isEntityInsidePausedRegion(entity)) {
L2744 |             if (lease != null) closeMobMove(lease, "member moved outside fixed region");
L2745 |             return false;
L2746 |         }
L2747 |         if (lease == null) return true;
```

<a id="c04"></a>
### C04 — 主动行为抑制依赖 lease；玩家身体整体提前返回

原文件：`targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/MobNavigationLeaseMixin.java`  
SHA-256：`c4ffc1963d750f2a92372f554574fc65f2b25360869f2ba98ba72949d7336cd3`

原文件 L12—L40：

```java
L0012 | /** During a tactical navigation lease, keep vanilla navigation and controls but stop autonomous goals. */
L0013 | @Mixin(Mob.class)
L0014 | public abstract class MobNavigationLeaseMixin {
L0015 |     @Shadow protected abstract void customServerAiStep(ServerLevel level);
L0016 |     private boolean dndturn$hasLease() {
L0017 |         Mob mob = (Mob) (Object) this;
L0018 |         if (!(mob.level() instanceof ServerLevel level)) return false;
L0019 |         ServerCombatService service = ServerCombatService.existing(level.getServer());
L0020 |         return service != null && service.hasMobMoveLease(mob.getUUID());
L0021 |     }
L0022 | 
L0023 |     @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
L0024 |         target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
L0025 |     private void dndturn$pauseGoalDecisions(GoalSelector selector) {
L0026 |         if (!dndturn$hasLease()) selector.tick();
L0027 |     }
L0028 | 
L0029 |     @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
L0030 |         target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"))
L0031 |     private void dndturn$pauseRunningGoals(GoalSelector selector, boolean full) {
L0032 |         if (!dndturn$hasLease()) selector.tickRunningGoals(full);
L0033 |     }
L0034 | 
L0035 |     @Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
L0036 |         target = "Lnet/minecraft/world/entity/Mob;customServerAiStep(Lnet/minecraft/server/level/ServerLevel;)V"))
L0037 |     private void dndturn$pauseCustomDecision(Mob mob, ServerLevel level) {
L0038 |         if (!dndturn$hasLease()) customServerAiStep(level);
L0039 |     }
L0040 | }
```

<a id="c04b"></a>
### C04b — 玩家身体整体提前返回

原文件：`targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/ServerPlayerTickMixin.java`  
SHA-256：`5bb697172c7abe2eaf19e7252f3a5f38eb2df35004ef895606d52ce7e25e90fb`

原文件 L10—L19：

```java
L0010 | /** ServerPlayer#doTick runs from the connection, outside EntityTickEvent.Pre. */
L0011 | @Mixin(ServerPlayer.class)
L0012 | public abstract class ServerPlayerTickMixin {
L0013 |     @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
L0014 |     private void dndturn$pauseBodyTick(CallbackInfo callback) {
L0015 |         if (MinecraftCombatRuntime.isBodyPaused((ServerPlayer) (Object) this)) {
L0016 |             callback.cancel();
L0017 |         }
L0018 |     }
L0019 | }
```

<a id="c05"></a>
### C05 — 服务端投影仅由 members 构造；已有环境文字不等于席位

原文件：`targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java`  
SHA-256：`e252fa42d1232bac22385e0f967e882e13f3d3958b9a30de0c0ad051b7deca07`

原文件 L2880—L2906：

```java
L2880 |     private void sync(CombatEngine.StateView state) {
L2881 |         long projectionRevision = Math.addExact(projectionRevisions.getOrDefault(state.id(), 0L), 1);
L2882 |         List<CombatNetwork.MemberNotice> roster = state.members().values().stream()
L2883 |             .sorted(java.util.Comparator.comparingInt(CombatEngine.MemberView::initiative).reversed()
L2884 |                 .thenComparingInt(CombatEngine.MemberView::tieBreak)
L2885 |                 .thenComparing(CombatEngine.MemberView::id))
L2886 |             .map(member -> {
L2887 |                 Entity entity = resolve(member.id());
L2888 |                 return new CombatNetwork.MemberNotice(member.id(),
L2889 |                     entity == null ? member.id().toString() : entity.getName().getString(),
L2890 |                     member.initiative(), member.eligibleRound(), member.dodging(), member.disengaged(),
L2891 |                     member.movementTicks(), member.action(), member.reaction());
L2892 |             }).toList();
L2893 |         projectionRevisions.put(state.id(), projectionRevision);
L2894 |         for (var entry : state.members().entrySet()) {
L2895 |             ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
L2896 |             if (player == null) continue;
L2897 |             CombatEngine.MemberView member = entry.getValue();
L2898 |             subscriptions.publish(player, new CombatNetwork.EncounterState(generation,
L2899 |                 state.id(), sessionSequences.getOrDefault(state.id(), 0L), true,
L2900 |                 state.version(), state.phase(), state.round(),
L2901 |                 state.current(), state.region().version(), member.movementTicks(),
L2902 |                 member.action(), member.reaction(), state.environmentRemaining(),
L2903 |                 playerMoves.containsKey(player.getUUID()),
L2904 |                 playerMoves.containsKey(player.getUUID())
L2905 |                     ? playerMoves.get(player.getUUID()).operationId : null, roster,
L2906 |                 engine.resultPage(state.id(), 0, 1).total(), !recoveryPending.contains(state.id()), projectionRevision));
```

<a id="c05b"></a>
### C05b — 客户端已有环境阶段与剩余步骤展示

原文件：`targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalOverlay.java`  
SHA-256：`1e0ddecf10a157c8d14e831ce3979a51183bd97e4d369c94f95df591ee72cc53`

原文件 L129—L150：

```java
L0129 |         if (document.getRefreshGeneration() != refreshGeneration) bind();
L0130 |         boolean myTurn = state.interactive() && viewer.equals(state.current()) && state.phase() != cc.sighs.dndturn.combat.EncounterPhase.ENVIRONMENT;
L0131 |         text("phase", "第 " + state.round() + " 轮 · " + (state.phase() == cc.sighs.dndturn.combat.EncounterPhase.ENVIRONMENT ? "环境推进"
L0132 |             : myTurn ? "你的回合" : "等待行动"));
L0133 |         text("action", state.action() ? "● 动作 1" : "○ 动作 0");
L0134 |         text("movement", "移动 " + state.movementTicks());
L0135 |         text("reaction", state.reaction() ? "● 反应 1" : "○ 反应 0");
L0136 |         text("environment", "环境 " + state.environmentRemaining());
L0137 |         text("pointer", (ClientControl.mode() == ClientControl.Mode.CAMERA ? "镜头控制 · " : "角色控制 · ")
L0138 |             + CombatControls.uiKeyLabel() + " 切换");
L0139 |         updateMembers(state);
L0140 |         enabled("move", myTurn && state.movementTicks() > 0);
L0141 |         for (String id : List.of("attack", "dash", "dodge", "disengage"))
L0142 |             enabled(id, myTurn && (id.equals("attack") || state.phase() == cc.sighs.dndturn.combat.EncounterPhase.ACTIVE)
L0143 |                 && state.action() && !state.moving()
L0144 |                 );
L0145 |         renderBehaviors();
L0146 |         for (var entry : Map.of("place", cc.sighs.dndturn.combat.TacticalIntent.Capability.PLACE,
L0147 |                 "break-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.BREAK,
L0148 |                 "use-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_BLOCK).entrySet())
L0149 |             enabled(entry.getKey(), myTurn && !ClientTacticalPlan.running());
L0150 |         enabled("end", myTurn);
```

<a id="c06"></a>
### C06 — 当前快照和恢复的成员队列假设

原文件：`common/src/main/java/cc/sighs/dndturn/combat/CombatStateSnapshot.java`  
SHA-256：`7982ef7cb6884c1fb636dc347cfcc08f817dc65884fcbab7df766883a88ddf5f`

原文件 L9—L17：

```java
L0009 | /** Versioned, platform-free save boundary. Every collection is detached from live rule state. */
L0010 | public record CombatStateSnapshot(int schemaVersion, int movementTicksPerTurn, int environmentTicks,
L0011 |                                   List<EncounterState> encounters, List<ClosedState> closed,
L0012 |                                   Map<UUID, UUID> mergedInto, List<MergeReceiptState> mergeReceipts) {
L0013 |     public static final int CURRENT_SCHEMA = 5;
L0014 | 
L0015 |     public CombatStateSnapshot {
L0016 |         if (schemaVersion != CURRENT_SCHEMA || movementTicksPerTurn < 1 || environmentTicks < 1)
L0017 |             throw new IllegalArgumentException("unsupported combat snapshot schema or budget");
```

原文件 L40—L43：

```java
L0040 |     public record MemberState(UUID id, int initiative, int tieBreak, long eligibleRound,
L0041 |                               int movementTicks, boolean action, boolean reaction,
L0042 |                               boolean dodging, boolean disengaged,
L0043 |                               boolean preserveSpentActionOnNextTurn) {}
```

原文件 L55—L68：

```java
L0055 |     public record EncounterState(UUID id, CombatEngine.Bounds bounds, RegionState region,
L0056 |                                  EncounterPhase phase, long version, long structuralRevision,
L0057 |                                  long round, int cursor, int environmentRemaining,
L0058 |                                  UUID authorizedEnvironmentStep, boolean environmentStepAuthorized,
L0059 |                                  Integer boundaryInitiative, Integer boundaryTieBreak,
L0060 |                                  List<MemberState> members, List<Hostility> hostile,
L0061 |                                  List<UUID> order, List<OperationRecord.Result> history,
L0062 |                                  Map<UUID, OperationRecord.Snapshot> pending,
L0063 |                                  Map<UUID, OperationRecord.Snapshot> causes,
L0064 |                                  Set<UUID> attackersWithRegisteredAttempt,
L0065 |                                  List<PermitState> permits, Map<UUID, Integer> childrenByRoot,
L0066 |                                  Set<UUID> completedEnvironmentSteps,
L0067 |                                  Set<UUID> causalMergeNeighbors,
L0068 |                                  int movementTicksPerTurn, int environmentTicks) {
```

<a id="c06b"></a>
### C06b — 恢复时校验 order 全部属于普通 members

原文件：`common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java`  
SHA-256：`3903e875f2d72a183e5dfb282774b4167a11f1b76bfe9f7ac202c92bc527999c`

原文件 L287—L298：

```java
L0287 |             Set<UUID> eligible = new HashSet<>();
L0288 |             for (Member member : encounter.members.values())
L0289 |                 if (member.eligibleRound <= encounter.round) eligible.add(member.id);
L0290 |             boolean environment = encounter.phase == EncounterPhase.ENVIRONMENT;
L0291 |             if (encounter.members.isEmpty()
L0292 |                 || (environment ? state.cursor() != 0
L0293 |                     : state.order().isEmpty() || state.cursor() < 0 || state.cursor() >= state.order().size())
L0294 |                 || !eligible.equals(new HashSet<>(state.order()))
L0295 |                 || !encounter.members.keySet().containsAll(state.order())
L0296 |                 || new HashSet<>(state.order()).size() != state.order().size())
L0297 |                 throw new IllegalArgumentException("invalid turn order snapshot");
L0298 |             encounter.order.addAll(state.order());
```

<a id="c07"></a>
### C07 — 当前目标版本声明

原文件：`targets/neoforge-26.1/gradle.properties`  
SHA-256：`139ec7fc7ae6c03dc4355c86bbcf1eb027c8d06cf2ce90ee57bb5fd428c9f0fa`

原文件 L1—L3：

```properties
L0001 | neoforge_261_minecraft_version=26.1
L0002 | neoforge_261_version=26.1.2.84
L0003 | neoforge_261_version_range=[26.1,)
```

---

**交付状态：本文件确立目标规范并附当前代码证据；未修改项目源文件，未编译、未运行测试，也未将这些设计写回用户资料库。**


[C01]: #c01
[C02]: #c02
[C03]: #c03
[C04]: #c04
[C04b]: #c04b
[C05]: #c05
[C05b]: #c05b
[C06]: #c06
[C06b]: #c06b
[C07]: #c07
