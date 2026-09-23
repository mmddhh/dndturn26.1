# DNDTurn：当前实现事实与文档索引

2026-09-28物品/生成修复：`VanillaBehaviors.Melee`移除物品命名空间及远程类别近战门禁，保留非空主手证据与既有伤害公式。`TacticalImpact`接入精确ChestBlock/TrappedChestBlock原版放置及双箱配对写入观察；`Interaction.start`按原版Success.swingSource发送既有去重挥手事件。`ItemUseEffects`成功插入证据经`joinGeneratedMob`加入规则权威，保持下一轮资格；`ZombieAttack`在候选阶段启用既有受限感知/近战策略。版本源码依据、平台回归和剩余实机范围见[物品版本记录](version-differences/neoforge-26.1-player-items.md)。

2026-09-28交互选择增量：26.1 `ClientTacticalPlan` 将攻击候选按显式物品选择归一显示，未选物品/空槽使用自身来源；移除空闲左键移动、检查及悬停自动攻击推断。`CombatControls` 结束回合仅失效临时目标/请求，保留选择，下次自身回合重新确认；退出/新会话仍完全重置。`TacticalOverlay` 右键候选仅显示名称，紧凑菜单及选择高亮与显式选择一致。内部来源ID、服务端执行校验及费用未改；构建和无窗口检查证据见[版本记录](version-differences/neoforge-26.1-log-gui.md)，实机验收保留在02。

**历史归档基线：`DNDTurn-sources(20260927-133552).zip`。当前事实以工作树源码及下述增量证据为准。**

**2026-09-28 文档同步范围：**本次包含玩家能力／物品使用、环境调度、参与者回合过程、Mob事实／发现／近战扩展、显示与构建边界。核对当前源码常量、接入范围与已有验证日志，替换前轮“排除玩家物品”的临时审计范围。`docs/legacy/` 既有正文保持历史原文。此次仅同步文档，未重新运行构建、GameTest或真实客户端；下述通过记录属于具名实现批次。

## 2026-09-28 玩家物品增量

2026-09-29 行为诊断增量：NeoForge 26.1 新增 `-debug` 公共惰性转发类与玩家／Mob请求、门禁、终态、骰点诊断；未启用时no-op，战斗日志面板仍空。覆盖范围、固定启动接入与验证限制见[行为诊断](version-differences/neoforge-26.1-action-debug.md)。此项更新下文历史批次“debug链路移除”的当前状态，不恢复旧战斗日志链路。

以下为上述文档静态核对之后的玩家物品实现增量。NeoForge 26.1 正常玩家能力入口新增刷取、实体放置／刷怪蛋、装水、部分实体喂食／命名／剪毛／染色／挤奶、抛竿／收竿、鸡蛋／经验瓶／受支持效果的喷溅药水。方块工具扩展蜂蜜脾、限定作物骨粉、火焰弹、剪刀、磁石指南针和水瓶用法；沿用既有食饮、桶、放置、破坏及弓弩／雪球链。免费穿戴沿用免费整理规则。

持续使用在同一玩家回合内推进；不自动 END_TURN。生成实体通过原版插入返回成功点记录身份。投掷物沿既有来源、环境模拟与隔离生命周期管理，延迟接触作为已确认使用的因果子效果登记，不扣下一回合动作。客户端按具体弹种计算中心轨迹；鱼竿预测到入水为止，不承诺鱼获。

这不是全物品／全效果支持声明。精确类型、作用范围、关键 Mixin、验证与限制见[固定版本玩家物品接入](version-differences/neoforge-26.1-player-items.md)，余项统一登记[02 的 PI 条目](02_GAPS_AND_CONFLICTS.md#player-items)。真实鼠标／键盘到双客户端显示仍待验收。当前 S2C 注册版本／C2S／common schema／target envelope 为 **28／22／13／12**；旧存档显式拒绝，不宣称自动迁移。

## 2026-09-28 环境与 Mob 增量

26.1 依赖仍固定 `26.1.2.84`；common 编译合约仍为 Java 17。已接入会话捕获 `roundTicks`（默认30），移动与环境新会话预算同源；旧配置键保留但不再计算，新键原子写入。活动会话继续使用捕获值。环境与 Mob 批次的 S2C／C2S／common schema／target envelope 基线分别为 **27／22／12／11**；后续玩家物品版本见上节，不迁移旧存档。

能力来源支持无手别与多个状态来源；Mob 发现跨 tick 保留已完成目标的游标，属性采样直接读取有效值与基础值，不重复加装备贡献。近战注册支持明确优先级覆盖、拒绝重复 ID／未声明覆盖／同优先级歧义。精确 Enderman 普通近战接共同 PLAN；受控期间阻断其未支持的原生传送。

`TacticalAdapter`／`TacticalExecution` 提供回调有效期内的受限近战端口与最多32项字符串运行状态；绑定既有计划、来源、目标与行为版本，不能自报伤害、费用或许可。内置 IntrinsicMelee 使用相同端口；正常释放清理运行状态，释放失败保留状态供控制清理重试，世界效果不重放。其他效果端口、跨回合技能状态与完整客户端扩展尚未完成。

日光感应器仅将20步采样节奏映射到有效环境时间，太阳／天气仍读执行时原版值；漏斗增加接收方、上方来源及物品实体当前位置门控。S2C 发送已提交环境时间，TNT 客户端烟雾只消费新增事实，不依赖已清空的许可窗口，也不推进引信或身体。

已接入所属参与者的唯一 END_TURN 药效／Mob着火结算：期限向上换算，施加当回合生效，保留分数周期量与末回合完整量；重复请求不重算。支持原版精确 MobEffect 基类、再生、中毒、凋零和伤害吸收实现，含隐藏药效链、原生到期取消与属性刷新；未知 override、饥饿、袭击及其他特殊效果尚未翻译，不能据此宣称全部药效受控。身体继续原生 tick；火焰期限退出时恢复实时推进。回合伤害使用既有自体有限许可与子结果，不新增战术命中；玩家死亡在未取消的原生完成点确认。

ParticipantEffectState 保存每个过程的捕获基准、剩余回合、原生期限预期及结算政策／观察。恢复先比对加载实体上的期限与隐藏链，再绑定新实例；不符则 UNKNOWN 并退出，不重放副作用。仅内存／Codec及平台对账已有证据，两进程保存和任意崩溃次序仍待验收。

规则、平台、兼容构建的实际命令及限制见[本批固定版本证据](version-differences/neoforge-26.1-environment-mob.md)。**这些源码增量不代表 ET／MT 整体完成。** 其他特殊药效、费用解耦、Skeleton装备弓、Guardian／Creeper／ElderGuardian、跨进程恢复及双客户端仍按[02](02_GAPS_AND_CONFLICTS.md)开放。

历史整理把 `docs(3).zip` 收束为四份；2026-09-28实施时按当前AGENTS恢复02唯一缺口登记与04架构合同。以下133552数据属于历史基线，当前增量另列。代码决定实现，明确用户修订决定规则；共同能力框架的基础保留，后续是环境过程与Mob翻译，不重开整套05。

<a id="index"></a>
## 1. 文档职责

| 文件 | 唯一职责 | 从旧文件接收的内容 |
|---|---|---|
| 本文件 | 当前源码事实、证据等级、旧编号路由与历史验证摘要 | 历史01的实现及05基础；不承担活动缺口登记 |
| [02_GAPS_AND_CONFLICTS.md](02_GAPS_AND_CONFLICTS.md) | 唯一活动缺口、冲突、搁置与待验收登记 | ET／MT、镜头／GUI及旧G剩余范围 |
| [03_PLAYER_RULES.md](03_PLAYER_RULES.md) | 玩家可观察规则及明确修订 | 历史规则与后续已确认修订，不记录实现进度 |
| [04_ARCHITECTURE_CONTRACTS.md](04_ARCHITECTURE_CONTRACTS.md) | 稳定职责、接口语义与验收合同 | 从03恢复的架构合同及05六层能力概念 |
| [ENVIRONMENT_TICK.md](ENVIRONMENT_TICK.md) | 环境过程设计、源码背景和专项验收合同 | environment总纲＋重构指南；活动状态转02 |
| [06_MOB_NATIVE_TRANSLATION_IMPLEMENTATION.md](06_MOB_NATIVE_TRANSLATION_IMPLEMENTATION.md) | MOB事实→DnD映射→受限执行及独立兼容验收合同 | 06＋05通用接口目标；活动状态转02 |

活动缺口与关闭证据统一维护于[02](02_GAPS_AND_CONFLICTS.md)，稳定架构合同见[04](04_ARCHITECTURE_CONTRACTS.md)。环境与Mob专题保留设计、源码背景和验收合同；其中旧状态描述为历史基线，以02的活动登记为准。历史05不重开共同PLAN。

<a id="baseline"></a>
## 2. 当前版本与历史基线

工作树固定26.1／NeoForge 26.1.2.84，common编译17、target编译25；当前注册版本S2C28／C2S22，common schema13／target envelope12，分别由 `CombatNetwork`、`CombatIntentHandler`、`CombatStateSnapshot`、`CombatPersistenceEnvelope` 声明。新会话通过 `ServerCombatConfig.roundTicks` 同时传入移动与环境预算，默认30。其他三个target的编译级别仍为17／17／21，玩家物品实现批次已独立构建通过，不扩大其玩法支持。此次文档同步未重跑。以下表格专门保留133552归档数据，不能用其协议、预算和文件数量描述当前工作树。

| 项目 | 133552实际基线 |
|---|---|
| Minecraft／NeoForge | 26.1／26.1.2.84；`[26.1,)`声明不等于全部版本已验证 |
| Java | common目标17；该target25 |
| 代码规模 | 317个归档文件，21个common主Java，123个26.1 target主Java |
| S2C／C2S／common schema／envelope | **22／19／11／9**，四项独立记录；旧17/5/6、18/6/7、10/9不再标“当前” |
| 原版参考 | 输入06曾核对 `.109` patched Minecraft；不能替代实际`.84`接入验证 |
| 兼容政策 | **不做版本间兼容／旧存档迁移；拒绝非当前格式且保留原始数据。**同一当前格式的保存、重启、身份去重和故障对账仍须完成 |
| 预设 | 发现水平16/竖直8、圆盘半径8、至多16区块/64锚点、锚点上下16；移动28、环境20有效步骤，按会话捕获 |
| 控制总纲 | 世界与普通身体默认原版更新；主动行为独立授权，选定敏感过程消耗环境有效步 |

与074734相比，133552归档只更改了4个客户端/UI文件；该历史比较不包含上面的工作树增量。

<a id="evidence-level"></a>
## 3. 状态与验证口径

- **代码已接入**：在当前工作树的实际调用、注册或状态链中存在；标为133552的表格与行号只证明历史基线。可以移出“从零实现”，但不自动标记全部场景验收完成。
- **有测试场景**：当前源码有对应断言/测试入口；需核对它是规则测试、平台测试还是直接方法夹具。
- **文档报告通过**：输入文档明确记录曾运行成功；本轮尊重记录，但原始日志未随包时不改写为本轮复验。
- **仍需实现／扩展**：当前合同或生产入口缺失；主要集中在ENVIRONMENT_TICK余项、06与少量已定玩法。
- **未决／搁置**：产品范围尚未明确或已明确暂缓；不能由实现便利补成免费/已支持。

133552文档整理阶段只做原档阅读和文档核对，没有执行构建；这一历史说明不代表2026-09-28实施阶段的验证结果。新证据单列在顶部增量及版本文档中。

<a id="implemented"></a>
## 4. 133552已实现基础与后续增量路由

下表保留133552历史支持范围；包括玩家能力／物品使用在内的当前增量见开头及第9节，不把历史表格中的版本号和旧覆盖范围当作现状。

| 旧索引 | 边界 | 133552事实 | 仍需区分 | 证据 |
| --- | --- | --- | --- | --- |
| I01–I05、I17–I19 | 规则/费用/终态 | 单一CombatEngine、operation去重、父子许可、效果与release分别记账；已受理错误进入机器分类和不可变结果。 | 不是世界事务；广泛状态/生成/容器观察与故障复核仍有边界。 | [E-ABI](01_IMPLEMENTED_DECISIONS.md#e-abi) |
| I06–I11 | 场地/成员/合并 | 固定连续几何、候选/激活/迟到资格；类型化环境席位；PREPARED/RULES_COMMITTED/BOUND合并证据。 | 实际多域合并、活动保存与故障对账仍需专项验收，不重置规则和资源。 | [E-ENV-SEAT](01_IMPLEMENTED_DECISIONS.md#e-env-seat) |
| I12–I14 | 移动与AI主链 | 玩家和Mob进入共同submit/PLAN；不同身体驱动、导航子步骤；注册策略及只读DecisionView、目标承诺和结果反馈。 | 普通地面支持不等于任意导航/Brain；原生能力与公开端口剩余归06。 | [E-AI](01_IMPLEMENTED_DECISIONS.md#e-ai) |
| I15–I16 | 环境过程 | D/E/P分类、held队列、常驻环境身份、普通身体放行、选定主动门禁；TNT/漏斗/荷叶/电路/有效周期已接入。 | 源码背景及合同见ENVIRONMENT_TICK，活动边界统一在02登记。 | [E-ENV-PROCESS](01_IMPLEMENTED_DECISIONS.md#e-env-process) |
| I20–I24、I29–I30 | 延迟效果/恢复 | 箭来源与模拟域分开；能力检查点、RecoveryAudit、新世代许可、当前格式恢复及未知箭隔离。 | 只支持当前格式；已完成历史读回不证明活动续战/任意崩溃恰好一次。 | [E-RECOVERY](01_IMPLEMENTED_DECISIONS.md#e-recovery) |
| I25、I31 | 协议/退出 | S2C22/C2S19，结构化Code/Retry；ExitAuthorizations独立，不靠扫描旧EXIT回执决定当前暂停豁免。 | 真实重连/多故障/归档/合并仍需证据；不要再做一遍文本错误码和授权拆分。 | [E-RECOVERY](01_IMPLEMENTED_DECISIONS.md#e-recovery) |
| I26–I28、I32–I35 | 既有行动/客户端 | DASH/DODGE/DISENGAGE和有限物品行为；显式选槽确认、查询/取消状态、目标菜单、独立镜头与新HUD。 | HELP/借机攻击未完整接通；满库存/特殊菜单/多客户端和CAM-01仍保留。 | [E-UI](01_IMPLEMENTED_DECISIONS.md#e-ui) |
| I36–I37 | 上层能力框架 | EQUIPMENT/BASIC/INTRINSIC/STATUS值；TacticalActor；玩家/Mob共同计划；空手有独立效果政策；放置准备与采样同上下文。 | 装备执行仍偏玩家，目标/费用仍由Capability耦合；定义存在不等于全部来源/能力已兼容。 | [E-PORTS](01_IMPLEMENTED_DECISIONS.md#e-ports) |
| I38 | 表现接口 | 注册式State/Frame/通道、动作事件/使用事实、人形独立端口；AGE采用实例/renderer生命周期偏移，旧倒退插值已移除。 | 真实渲染和外部模型适配未证明全覆盖；不要再次要求绝对AGE四tick归零。 | [E-PRESENT](01_IMPLEMENTED_DECISIONS.md#e-present) |
| I39–I40 | 测试与诊断 | gameTest源集隔离、独立测试引导；2026-09-28按用户要求移除-debug参数、诊断类及调用，错误/警告处理保留。 | 测试类存在/编译不等于运行；同核心包测试不等于公开compat API已验收。 | [E-VERSION](01_IMPLEMENTED_DECISIONS.md#e-version) |

<a id="phase-status"></a>
## 5. 05的P0–P6如何归档

| 阶段 | 整理后状态 | 保留的实现 | 剩余去向 |
| --- | --- | --- | --- |
| P0 | 已建立基线与特征测试 | 不再要求从零盘点073919；本版身份、调用链与测试已存在。补缺少的原始验收记录。 | 本文件验证账本 |
| P1 | 主要实现已落地 | 显式选择/纯查询边界、机器失败、效果/release、实时EXIT授权、故障轮转与实例退役。 | 06的来源统一；当前版本异常/重连回归 |
| P2 | 共同纵切已接通 | 天生来源真实进入意图；玩家/Mob共用PLAN；测试Cow从正常轮转接近并攻击。 | 独立Java包、真实装备Mob、通用受击/关系适配 |
| P3 | 主要生产迁移已落地 | 既有玩家行为和Mob策略走共用后端；攻击后可由策略提出MOVE；不是旧独立根ATTACK脚本。 | 仍受限的来源枚举/目标合同/公共执行端口，归MT-01～04 |
| P4 | 部分已实现 | 有限效果检查点、来源自变证据、RecoveryAudit、合并阶段、预算、持久环境时钟。 | 当前格式活动恢复/故障，类型化效果扩展，发现延期进展 |
| P5 | 部分已实现 | 表现注册及事件事实、人形桥接、AGE政策、交互新UI。 | 独立compat/非人形与真实双客户端，环境显示时间边界 |
| P6 | 有分项验证记录，整体不关闭 | 输入文档报告构建、common/GameTest和完成历史跨进程读回通过。 | 补原始日志；真实键鼠/双客户端/活动重启/负载证据 |

“主要已实现”不是一个未经测量的百分比，也不表示40项CAP-T全部通过。删除旧P0–P6待办的理由是代码已承担基础职责；尚未完成的接口/验收转入下面两条主线，不再复制新一轮P1/P2/P3计划。

<a id="remaining"></a>
## 6. 剩余工作只按边界推进

| 主线 | 仍需做什么 | 不再做什么 | 详细入口 |
|---|---|---|---|
| 环境过程收尾 | 剩余时间输入、未翻译特殊效果、接触/跨域/生成阶段、held及活动恢复、TNT真实显示和联机 | 再建环境身份、基本过程、已审计药效回合桥或已提交环境时间投影；恢复全身冻结 | 活动状态见[02](02_GAPS_AND_CONFLICTS.md)，合同见[ET-01～ET-08](ENVIRONMENT_TICK.md#remaining) |
| MOB翻译/接口 | 完整装备/范围/感知事实、其他效果端口与跨回合托管、特殊Mob映射及高频失效规模 | 再建CombatEngine/PLAN、NativeFacts、近战公开端口或发现游标；用物种名自动授予任意攻击 | 活动状态见[02](02_GAPS_AND_CONFLICTS.md)，合同见[MT-01～MT-12](06_MOB_NATIVE_TRANSLATION_IMPLEMENTATION.md#roadmap) |
| 玩家物品与客户端回归 | 特殊投射物、其他原版物品组合、完整效果证据、取消/实例替换、满库存与菜单、真实键鼠/双客户端 | 重做已有持续使用／抛收／生成身份链；把中心轨迹视为保证命中或测试夹具视为真实输入 | [PI条目](02_GAPS_AND_CONFLICTS.md#player-items)、[架构合同](04_ARCHITECTURE_CONTRACTS.md#architecture)及CAP映射 |
| 后置但不丢失 | HELP、完整借机攻击、七日引用感知归档和非投射物截止、箭精确边界/复杂组件、CAM-01 | 因它们未做就隐藏正常战术入口；把已搁置新玩法夹带成必做范围 | 本节旧G映射与[保留范围](03_PLAYER_RULES.md#scope) |

跨界职责：环境层提供有效步/归属/生命周期；06为特定Mob提供阶段及原生事实/效果映射。ElderGuardian周期属于二者接缝，但不得两边各存一份相互竞争的时钟、能力余额或行动队列。

<a id="legacy-g"></a>
### 6.1 旧G编号迁移表（编号保留，不再复制多轮状态段落）

| 旧G | 当前结论 | 唯一后续去向 |
| --- | --- | --- |
| G02/G03/G13/G22 | 普通箭和有限远程已接；精确边界、复杂组件及全部跨域/来源生命周期未完成。 | 环境ET-04/ET-06；06 MT-09/MT-11；已有箭规则保持 |
| G04/G07/G08 | 基础伤害及独立结果已有；耐久/吸收/清理/多层效果需按支持范围验收。 | 06 MT-05/MT-08/MT-11及现有伤害回归 |
| G05/G06 | 原版驱动与实际移动费已接；26.1 玩家平地直达及绕障搜索权重已修正并通过平台路径断言，真实点击/行走仍待验收；体型、混合位移、特殊导航合同及多包实际输入未完整覆盖。 | 06 MT-06/MT-09；环境ET-04被动位移对照 |
| G09/G10/G11 | held及过程分类、活塞/流体/TNT测试已有，不能整项留为未实现。 | 仅环境ET-01～ET-08中的明确边界 |
| G12/G14 | 精确环境TNT爆炸授权已接；不代表全部自然伤害、生成、状态、传播类别已覆盖。 | 环境ET-03/ET-04；06 MT-10/MT-11；H01保留TNT例外 |
| G15/G16/G30 | 合并阶段、当前格式恢复审计、历史读回已有；活动续战和崩溃仍待。 | 环境ET-06；06 MT-11；不做旧存档迁移 |
| G17/G18 | 七游戏日引用归档与非投射物执行截止仍是已定未完整实现工作。 | 后置维护；不变成投射物寿命，也不堵住其余两条主线 |
| G19/G20/G27/G29/G32 | 回执、动态同意、EXIT、结果补收已接；当前版本多连接/网络时序回归未完整。 | 已有协议/生命周期验收；不重新讨论已定退出和同意政策 |
| G21/G34 | 共同执行/能力来源/注册基础已接；原生Mob事实和通用目标/来源接口仍有边界。 | 06 MT-01～MT-12 |
| G23/G25 | HELP及完整借机攻击规则已定，但生产闭环尚未完成。 | 后续玩法；不能以有反应资源/伤害子操作冒充已实现 |
| G24/G28 | 食饮和有限容器已接；持续中断、满库存、模组菜单与物品副作用继续验证。 | 06 MT-02/MT-11；已有库存/使用回归 |
| G26/G33/G35 | 新的目标式交互/UI和Querying取消存在；布局和真实联机仍待。 | 当前UI与输入验证；不复活旧Idle查询未取消的旧缺陷 |
| G31 | 共同所有者、AI策略和部分分阶段迁移已提取；核心服务仍大但行数不是缺陷证据。 | 只在环境/06纵切中提取所需职责，避免另建全局拆类工程 |
| G36/G44/G45 | 表现注册、AGE新政策和测试源集隔离已接；独立显示/双客户端仍需。 | 环境ET-07及06 MT-12；CAM-01独立 |
| G37 | 显式选槽命令与纯查询已拆分；不同发现入口的能力集合尚未完全统一。 | 已完成基础不重做；余项归06 MT-03 |
| G38 | Code/Retry、PLAN failure和不可变结果已接；不是仅EXIT有机器码。 | 真实网络拒绝/重复/重连验收 |
| G39 | 结果/费用/release分离，故障轮转与旧实例退役已有。 | 对照测试及当前格式恢复，不重写基础取消模型 |
| G40 | ExitAuthorizations独立实时记录已接，不再扫描回执授权。 | 合并/重入/归档/断线生命周期验收 |
| G41 | 有限物品/方块/身体观察及准备后取样已接；任意状态/生成/BE不支持。 | 06 MT-05/MT-10/MT-11；环境精确影响范围验证 |
| G42 | Mob发现已用BudgetedScan跨tick保留完成目标；依赖失效会重建捕获，当前目标内部超预算仍需重评。 | 02 MT-03/MT-07/MT-12跟踪高频失效与单目标工作量边界；不再从零实现游标 |
| G43 | 当前值检查点/RecoveryAudit已接；现有四版本轴27/22/12/11，参与者期限与结算证据进入envelope。 | 当前格式主动效果故障对账；旧格式只拒绝保原文 |
| G46 | 行为/近战/策略/表现注册已存在；另有compatTest独立包使用公开近战端口，不能推及其他效果／显示扩展。 | 02 MT-04/MT-07/MT-12 |
| G47 | 持久环境时钟、信标80步/潮涌40步已接；其他绝对计时/AI期限未全闭环。 | 环境ET-01/ET-02；06 MT-10/MT-11 |

<a id="cap-mapping"></a>
### 6.2 旧CAP-T验收如何继续使用

案例编号仍可被日志引用；下表分组覆盖CAP-T01～CAP-T40，不把“有测试/已报告过通过”统一变成全部完成。

| 原案例 | 原主题 | 整理后用途 |
| --- | --- | --- |
| CAP-T01～CAP-T08 | 来源/注册/纯发现/装备一致性 | 基础值与已有玩家路径保留；跨actor装备、多绑定、hand=null能力余项归06 MT-02/MT-03/MT-06。 |
| CAP-T09～CAP-T16 | 共同PLAN、接近、费用、取消、致死/释放 | 已有核心与平台场景，按原断言持续回归；不从零再写共同后端。 |
| CAP-T17～CAP-T22 | 关系/感知/策略续步及AI时间 | 策略和测试续步已接，原生感知/Goal/Brain合同余项归06；普通地面漫步不授予任意AI。 |
| CAP-T23～CAP-T25 | 免费方块、多格作用范围、延迟效果 | 已有受限适配及准备修复；边界/跨域归环境ET-04/ET-05与06实际驱动。 |
| CAP-T26～CAP-T32 | 保存、实例、合并、EXIT、网络和未知箭 | 按当前版本做活动恢复/故障；CAP-T32中的旧版本迁移要求被当前兼容政策取代，改验旧格式拒绝且原数据不变。 |
| CAP-T33～CAP-T40 | 外部compat、表现、预算、构建、双客户端 | 注册与采样底座已接；同包测试不替代独立模块，余项归MT-12/ET-07及真实输入。 |

<a id="verification"></a>
## 7. 验证账本：尊重已有结果，不挪用本轮时态

| 记录 | 原文明确报告的范围 | 本次处理 |
|---|---|---|
| 2026-09-28 寻路修复 | JDK 25.0.3、NeoForge 26.1.2.84；在该 target 执行 `gradlew.bat build runGameTestServer --console plain --no-daemon`，构建成功、54项必需GameTest通过；common测试为UP-TO-DATE | 本地日志 `targets/neoforge-26.1/build/verification-pathfinding.log`；`ActionPreviewChecks` 覆盖超过8格的非45°直达、精确终点、搜索结果直达、绕墙长度、地面缺口及64段预算。未运行真实客户端键鼠/联机行走，不关闭G05/G06整体验收 |
| 133552环境基线 | clean build、71项common、53项GameTest、当前格式已完成历史的两进程保存读回通过 | 保留为“输入文档报告通过”；对应reviews原始日志未在两份输入归档中，本次未复验 |
| 133552 UI基线 | JDK25独立`gradlew.bat build --console plain --no-daemon`通过；common为UP-TO-DATE，未跑显示 | 保留原范围；后续UI增量见版本文档，不升级成实机显示通过 |
| 早期P1/P2及能力修复 | 文档有47/44/48项平台、60/66/69项common等分阶段结果 | 仅视为历史阶段；最新基线不继续并排挂多组“当前协议”和计数 |
| 早期间歇问题 | scheduled_tick_hold、arrow_pending_removal、tactical_behaviors、camera/actor、zombie_target_invalidated、arrow_block_rejection等 | 不作为133552已复现失败；未确认替代覆盖时保留回归场景，不删除断言自证完成 |
| 本源包两份旧日志 | wrapper下载在socket权限处失败，Gradle未启动 | 只证明那两次尝试；不覆盖文档后续通过声明，也不当作本轮失败 |
| 06前轮独立核查 | 文档报告21个common主源码编译、9项纯值/规则核查 | 原生接口/真实Mob、平台、恢复并未由此验收 |
| 133552文档整理 | 当时记录读取两原档、核对关键生产/测试链、链接和稳定编号及原文件一致性 | 历史文档工作，不是当前源码审计的输入声明 |
| 2026-09-28前轮非玩家文档核对 | 当时排除玩家能力／物品使用；核对环境／Mob实现及61项required既有日志 | 历史审计范围，不再作为本次全量同步的排除条件 |
| 2026-09-28玩家物品实现验证 | JDK25独立 `clean build :common:test runGameTestServer` 成功；81项common、63项required通过；其余三个target以JDK21构建成功 | [具名日志、串行重试及未执行项](version-differences/neoforge-26.1-player-items.md)；真实客户端／重启及PI余项未关闭 |
| 2026-09-28本次全量文档同步 | 核对源码版本、已有日志、文档职责、当前／历史范围和本地链接 | 仅文档改动；没有重新执行构建／游戏，不生成新的功能验收结论 |

后续实际命令、JDK和证据登记到01与版本文档，活动状态只在02维护。完成历史读回不等于活动续战；测试源码和classpath编译不等于GameTest执行；嵌入玩家不等于真实双客户端。

寻路实现增量：26.1 的 `PreviewPathfinder` 原先对直边/斜边统一计1，配合 Chebyshev 启发式及同分深度优先，会保留较长的斜向折线；`directTo` 还以距离平方64将直线预检限制在8格内。现搜索采用实际端点间几何距离与水平距离下界，搜索深度仍单独限制64步、节点仍限制1024；平地直线按至多64个短段检查碰撞、支撑、加载和区域，搜索结果也优先返回通过这些检查的直线。客户端先尝试当前终点直达，再复用旧路径。只影响该target玩家预览/选点规划，不修改common、Mob原版导航、移动驱动或实际tick费用。

移动预览预算增量（2026-09-28）：`MovementPathLines` 沿路径累计距离生成0.50米长线、0.15米间隔、0.20米短线、0.15米间隔，跨节点不重置；灰红分界可拆分同一条虚线。`MovementPreviewBudget` 只作名义距离估算，`LocalActionPreview` 保留完整显示路径，但纯移动提交绑定灰色末端的精确坐标及对应目标格；剩余预算变化失效旧预览。服务端继续使用既有选点规划、移动Lease与实际tick费用。执行投影只显示权威路径，待确认期间不把原红色尾段当成可执行路线。验证和估算范围见[移动预览接入记录](version-differences/neoforge-26.1-movement-preview.md)。

<a id="migration"></a>
## 8. 旧文档整理与合入

| 旧文件 | 整理决定 |
|---|---|
| 01、02 | 01保留实现事实和历史I/G路由；02恢复为唯一活动缺口登记 |
| 03、04 | 03保留玩家规则；已把其中稳定架构合同移回04，历史04原件不变 |
| 05 | 退出活动待办；六层合同归04、P0–P6历史及CAP案例归本文件、接口合同归06、活动缺口归02 |
| EnvironmentParticipant Baseline＋Environment Guide | 合并为ENVIRONMENT_TICK；当前身份/门禁/已接过程列基础，按实际差距留下ET目标 |
| Environment Evidence | 旧版本证据留原始归档；不把旧current=null/整体冻结片段转抄为当前事实。当前核对入口见本文件证据索引 |
| 06 | 保留主体，补当前上层完成边界、与环境分工、当前格式政策；替换指向未随包证据的断链 |

**维护边界：**按仓库AGENTS保留01、02、03、04各自职责；05及旧环境来源留归档，环境/Mob专题只维护设计与验收合同，不另立活动状态权威。

AGENTS的当前阅读与文档职责要求继续有效；不以历史文档包中的合并方案覆盖仓库约束。

后续更新只改对应边界：先改规则才改数值；实现落地移入本文件基础区；验证补原始结果；环境与MT任务完成后删去“从零实施”措辞而保留回归。旧版本兼容不再重新列待办，除非用户另作明确修订。

<a id="source-evidence"></a>
## 9. 源码证据索引

当前主要入口如下，使用文件／符号定位，避免沿用已漂移的历史行号：

| 范围 | 当前入口 |
|---|---|
| 玩家物品发现与执行 | [VanillaBehaviors](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java)、[TacticalActions](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java)、[NativeEntityUse](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/NativeEntityUse.java)、[TacticalImpact](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalImpact.java) |
| 生成、投射及观察 | [ItemUseEffects](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ItemUseEffects.java)、[PlacedEntityBehavior](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/PlacedEntityBehavior.java)、[ThrownItemBehavior](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ThrownItemBehavior.java)、[FishingBehavior](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/FishingBehavior.java)、`CombatEngine.beginCausalItemImpact`、`ServerCombatService.itemProjectileImpact` |
| 轨迹与物品验证 | [BowPreview](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/BowPreview.java)、[ProjectileProfiles](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ProjectileProfiles.java)、[PlayerItemChecks](../targets/neoforge-26.1/src/gameTest/java/cc/sighs/dndturn/combat/PlayerItemChecks.java)、[固定版本记录](version-differences/neoforge-26.1-player-items.md) |
| 捕获时间与期限值 | [RoundTime](../common/src/main/java/cc/sighs/dndturn/combat/RoundTime.java)、[RoundDuration](../common/src/main/java/cc/sighs/dndturn/combat/RoundDuration.java)、[ServerCombatConfig](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatConfig.java) |
| 回合末过程与保存证据 | [ParticipantEffects](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ParticipantEffects.java)、[ParticipantEffectState](../common/src/main/java/cc/sighs/dndturn/combat/ParticipantEffectState.java)、`ServerCombatService.endTurnAs`、`CombatPersistenceEnvelope` |
| Mob事实、发现和注册 | [NativeFacts](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/NativeFacts.java)、[BudgetedScan](../common/src/main/java/cc/sighs/dndturn/combat/BudgetedScan.java)、[MobTurnStrategies](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/MobTurnStrategies.java)、[MeleeAdapters](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/MeleeAdapters.java) |
| 公开近战扩展 | [TacticalExecution](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalExecution.java)、[独立包夹具](../targets/neoforge-26.1/src/compatTest/java/example/compat/dndturn/ExternalMeleeChecks.java) |
| 环境接缝、显示及平台证据 | [环境与Mob版本记录](version-differences/neoforge-26.1-environment-mob.md)、[GUI](version-differences/neoforge-26.1-log-gui.md)、[头像](version-differences/neoforge-26.1-participant-portraits.md)、[朝向](version-differences/neoforge-26.1-cursor-facing.md) |

以下E编号保留133552来源路由；代码路径相对归档根，L为当时原文件1-based行号，**不是当前工作树行号**。各节另列的后续增量以具名类／方法及版本记录为准；它们不是本次运行日志。

<a id="e-version"></a>
### E-VERSION — 版本与构建

- `targets/neoforge-26.1/gradle.properties:L1–L3`
- `targets/neoforge-26.1/build.gradle:L15–L22`
- `common/src/main/java/cc/sighs/dndturn/combat/CombatStateSnapshot.java:L13–L17`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatPersistenceEnvelope.java:L28–L32`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatNetwork.java:L24–L28`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatIntentHandler.java:L15–L19`

<a id="e-env-seat"></a>
### E-ENV-SEAT — 环境身份、实际 current/order、时钟与提交

- `common/src/main/java/cc/sighs/dndturn/combat/TurnParticipant.java:L1–L18`
- `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java:L48–L70`
- `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java:L1322–L1369`
- `common/src/test/java/cc/sighs/dndturn/combat/EnvironmentParticipantTest.java:L1–L71`

<a id="e-env-step"></a>
### E-ENV-STEP — 调度、全局条件、有效时钟及失败

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java:L496–L509`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java:L569–L611`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java:L898–L920`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java:L923–L943`

<a id="e-env-process"></a>
### E-ENV-PROCESS — 通道分类与维护例外

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/EnvironmentProcesses.java:L1–L54`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/LevelBlockEntityGateMixin.java:L1–L23`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/ServerLevelBlockEventGateMixin.java:L1–L46`

<a id="e-env-contact"></a>
### E-ENV-CONTACT — 漏斗、荷叶与当前接触

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/HopperProcessMixin.java:L1–L26`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/LilyPadProcessMixin.java:L1–L24`
- `targets/neoforge-26.1/src/gameTest/java/cc/sighs/dndturn/combat/EnvironmentClassificationChecks.java:L35–L76`

<a id="e-env-tnt"></a>
### E-ENV-TNT — TNT执行、伤害和双域

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/EnvironmentExplosion.java:L1–L59`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/TntEnvironmentMixin.java:L1–L18`
- `targets/neoforge-26.1/src/gameTest/java/cc/sighs/dndturn/combat/EnvironmentTntChecks.java:L41–L71`
- `targets/neoforge-26.1/src/gameTest/java/cc/sighs/dndturn/combat/EnvironmentDomainChecks.java:L29–L53`

<a id="e-env-clock"></a>
### E-ENV-CLOCK — 周期时钟与电路阶段

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/BeaconEnvironmentTimeMixin.java:L1–L18`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/ConduitEnvironmentTimeMixin.java:L1–L42`
- `targets/neoforge-26.1/src/gameTest/java/cc/sighs/dndturn/combat/EnvironmentClockChecks.java:L25–L52`
- `targets/neoforge-26.1/src/gameTest/java/cc/sighs/dndturn/combat/EnvironmentCircuitChecks.java:L28–L71`

<a id="e-held"></a>
### E-HELD — 计划更新完整所有权

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/RegionalScheduledTicks.java:L1–L387`

<a id="e-body"></a>
### E-BODY — 身体默认推进与独立主动控制

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ActiveBodyControl.java:L1–L29`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java:L2716–L2755`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java:L2838–L2860`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/MobNavigationLeaseMixin.java:L1–L80`

<a id="e-abi"></a>
### E-ABI — 能力值、规则分类与共同提交

- `common/src/main/java/cc/sighs/dndturn/combat/AbilitySource.java:L1–L41`
- `common/src/main/java/cc/sighs/dndturn/combat/TacticalIntent.java:L1–L72`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalBehavior.java:L1–L89`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java:L202–L270`

<a id="e-rules"></a>
### E-RULES — 既定数值、近战几何与空手输入

- `common/src/main/java/cc/sighs/dndturn/combat/CombatRules.java:L1–L59`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java:L1323–L1333`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/MeleeAdapters.java:L1–L97`

<a id="e-discovery"></a>
### E-DISCOVERY — 显式选择与两条发现路径

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java:L88–L198`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalCapabilities.java:L1–L84`

<a id="e-ports"></a>
### E-PORTS — 装备及私有状态/执行的接口边界

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActor.java:L1–L30`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/PlayerBehavior.java:L1–L104`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java:L45–L85`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java:L289–L306`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/IntrinsicMelee.java:L1–L46`

<a id="e-ai"></a>
### E-AI — 策略、预算与测试兼容范围

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/MobTurnStrategies.java:L1–L212`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/AbilityWorkBudget.java:L1–L39`
- `targets/neoforge-26.1/src/gameTest/java/cc/sighs/dndturn/combat/MobAbilityLoopChecks.java:L1–L136`
- `targets/neoforge-26.1/src/gameTest/java/cc/sighs/dndturn/combat/AbilityPolicyChecks.java:L1–L123`

<a id="e-results"></a>
### E-RESULTS — 独立终态、释放与来源观察

- `common/src/main/java/cc/sighs/dndturn/combat/ExecutionConclusion.java:L1–L16`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java:L477–L520`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java:L533–L575`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/AbilityObservations.java:L1–L41`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/AbilityCheckpoint.java:L1–L49`

<a id="e-recovery"></a>
### E-RECOVERY — 实时授权、当前格式与恢复

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ExitAuthorizations.java:L1–L60`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatSavedData.java:L47–L60`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatRecoveryCandidate.java:L1–L64`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatPersistenceEnvelope.java:L73–L89`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatPersistenceEnvelope.java:L83–L101`

<a id="e-present"></a>
### E-PRESENT — 注册式表现和实例AGE政策

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/PresentationAdapters.java:L1–L68`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/BuiltinPresentation.java:L1–L70`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientPresentation.java:L108–L145`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/PresentationUse.java:L1–L51`

<a id="e-ui"></a>
### E-UI — 133552交互与UI

第一人称切换移除增量（2026-09-28，NeoForge 26.1）：删除 `ClientControl.Mode/CHARACTER` 及切换方法、`CombatControls` 的 ui 快捷键与角色跳跃转发、HUD pointer 按钮及其样式/文本/事件。有效会话统一使用战术镜头；目标式计划、鼠标朝向、Screen/失焦优先级及退出后的原版输入交接保留。固定版本依据与待验收范围见 [战术镜头控制](version-differences/neoforge-26.1-tactical-camera.md)。

鼠标朝向增量（NeoForge 26.1）：`ClientControl.WorldPick` 保留最近接触点，`TacticalLocalPlayerMixin` 在 `LocalPlayer.aiStep` 开始时提交 yaw/pitch，位置不变；原版随后维护头身跟转、旧角度及 `sendPosition`。服务端移动门禁只接收合法旋转，位置继续拒绝；原版实体跟踪与远端插值承担显示。固定版本接入与验证见 [鼠标朝向](version-differences/neoforge-26.1-cursor-facing.md)。仍待真实双客户端验证本机/远端转身、跨±180°、移动同时瞄准、菜单/失焦及重新跟踪；不据此关闭整体客户端验收。

鼠标朝向时序修复（2026-09-28，NeoForge 26.1）：移动门禁忽略与权威坐标一致的位置字段，不再对传送确认后的 PosRot／周期位置提醒重复纠正；本模组位置纠正使用相对零旋转，避免旧服务器朝向覆盖新输入。战术拾取从 rig 同时读取位置和旋转；`Minecraft.pick(float)` 在战术相机持有期间使用同一鼠标拾取算法更新原版预选框，Screen/HUD 等阻挡时清空，退出交还原版。客户端拾取仍不是执行许可。源码与包级验证边界见上述版本文档；真实显示待验收登记为 [AIM-01](02_GAPS_AND_CONFLICTS.md#aim-01鼠标朝向回跳与黑色预选框闪烁)。

当前布局增量：顶部先攻栏仍按文档视口高度20%缩放；底部行动栏将行布局压缩到61px设计高度，再等比缩放至视口高度25%，底边贴底、顶边位于视口高度75%处。`TacticalOverlay.dockTop` 同时供布局和聊天避让使用，结束回合按钮按两行槽位中心定位。`TacticalButton.layout` 从同一尺寸为父容器、按钮、展示层和canvas/item写入明确像素框，CSS明确展示层在按钮上方且不拦截输入。当前修复的构建与客户端待验收边界见 [日志与 GUI](version-differences/neoforge-26.1-log-gui.md)。

参与者头像增量（2026-09-28）：`ParticipantPortraits` 参考本地 JER 的 `RenderHelper.renderEntity` / `MobWrapper`，使用固定版本原版背包实体预览链，按当前实体高度与卡片尺寸放大、固定斜正面视角，以半高位置对齐头像底边，仅显示上半部并保留头顶余量；仅提取渲染状态，不修改实体 ID、朝向或推进 tick。环境使用保留式 Canvas 绘制二维圆球/经纬线。`TacticalPortraitLayerMixin` 在 AUI 界面 PIP 提交后、光标提交前插入实体预览，按文档坐标转换与先攻栏裁剪；未加载实体显示问号。新增头像节点随成员移除、会话关闭和文档重建清理。构建通过与实机待验收范围见[头像接入记录](version-differences/neoforge-26.1-participant-portraits.md)。

交互错配修复：以上缩放现直接作用于布局坐标、尺寸、字体、边框与间距，先攻栏/行动栏不再使用CSS `transform: scale`。固定ApricityUI的命中缓存读取布局Rect，绘制矩阵单独提交；共享父组件与明确局部尺寸本身不足以修正整栏绘制缩放造成的命中偏差。真实输入与显示验收仍保留GUI-01。

UI更新时序与移动力环增量：`OverlayUi` 在两个自有AUI文档绘制前完成当前投影、样式、绘制节点、几何和命中缓存提交；外部`tactical.css`实际进入CSS缓存前不绘制/接收文档点击，不用等待固定帧数。动态文字复用显式`TextNode`，不再每次资源更新都替换惰性legacy文本。候选框预留固定高度，避免异步结果数改变外框尺寸；同意弹窗改为有界、可滚动的文档坐标布局。`MovementRing` 替代移动力常驻文字，复用Canvas且每次更新先清空旧弧；S2C快照增加会话基础移动预算，协议26，服务端按会话捕获值发送。独立clean build、73项common测试、无窗口UI检查通过；该增量的54项必需GameTest通过。无窗口检查不等于GPU/真实输入验收，详见[日志与GUI](version-differences/neoforge-26.1-log-gui.md)。

日志与控件增量（2026-09-28）：战斗日志 GUI 空面板保留，日志拼接、历史订阅/补页/载荷和 debug 日志链路移除，规则账本/行动确认/拒绝反馈保留。`TacticalButton` 共享父组件统一持有图标按钮与热栏物品的布局框。独立 clean build、73项common与54项必需GameTest通过；最终增量构建和54项GameTest再次通过。协议变化、固定依赖依据与验证范围见 [日志与 GUI](version-differences/neoforge-26.1-log-gui.md)，真实客户端对齐验收仍在02的GUI-01登记。

- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientTacticalPlan.java:L1–L217`
- `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalOverlay.java:L1–L469`
- `targets/neoforge-26.1/src/main/resources/assets/apricityui/apricity/dndturn/tactical.css:L1–L59`


<a id="input-identity"></a>
## 10. 输入身份

- `DNDTurn-sources(20260927-133552).zip`：SHA-256 `c7d75dddf052df51ee03a582b0468bc258168853672840499ab9af9875e0bea3`。
- `docs(3).zip`：SHA-256 `60bfaa5902791ca9eee7227eeaae50340c1132ff02ad3a93806f18ba7183d1f4`，含9份Markdown。
- 上述身份为历史整理记录，本次未重新读取两份原档或核验其哈希；本次依据为当前工作树。未修改原档或资料库，也未重新下载／核对`.109`。
