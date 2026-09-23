# DNDTurn：当前实现决策与支持边界

2026-09-27 环境重构增量：common 当前 schema11（S2C/C2S/envelope仍22/19/9）。具体ticker／事件分类、荷叶当前接触、TNT环境伤害与双域过滤、信标／潮涌核心有效时钟已接通。独立clean build、71项common、53项GameTest与当前格式历史跨进程保存读回实际通过；合同及未测边界见[环境验证](reviews/20260927-environment-refactor.md)。下方旧版本值保留为历史时点。

当前源码核查：**2026-09-27 工作树**；历史起点为 2026-09-26 / 073919。这是源码事实交接，不是发布验收单。规则见 [03](03_PLAYER_RULES.md)，唯一活动缺口见 [02](02_GAPS_AND_CONFLICTS.md)，拟议目标架构见 [04](04_ARCHITECTURE_CONTRACTS.md)。I01–I40 保留编号；本次证据见 [源码核查](reviews/20260927-capability-framework-doc-sync.md)，未重跑运行验收。

073919 文档核查仅做了原始归档比较、关键生产链路阅读、文档冲突整理和 common 主源码编译，未修改源码或运行 target。后续能力框架实现及实际验证单独记录如下；仅存在的函数与注册并不能证明整条实机链路通过。


## 1. 版本、结构与参数

2026-09-27 继续核验：当前 S2C/C2S/common/envelope 为 **22/19/10/9**。工作树已有类型化常驻环境席位、独立主动门禁、普通身体更新、STATUS 授予证据、只读 Mob 决策及独立随机源；不再将这些列为完全未实现。本轮补僵尸候选可攻击性/能力可用性筛选、持续使用表现输入与按需 AGE 所有权，修正创造模式测试玩家及新增保留世界的独立重启入口。实际命令、覆盖及剩余范围见 [继续验证](reviews/20260927-040106-continue-validation.md)。下方条目保留历史时点，不能用于覆盖本段当前事实。

2026-09-27 040106 修复增量：放置在专用姿态更新后捕获 Prepared/before，实际首写前复验同一范围与依赖；近战效果政策进入具体能力驱动，新增 actor 无选槽发现及延期提案缓存；表现增加事实输入与显式人形手部端口。当前 S2C/C2S/common/envelope 为 **21/18/9/9**。离线全部主源码和 GameTest 源码编译、66 项 common JUnit 实际执行通过；Gradle/平台/真实客户端未运行，未完成显式环境与默认身体更新。详见 [增量证据](reviews/20260927-040106-fix-validation.md)。

2026-09-27 P4–P6 增量（未编译／未运行验收）：新增能力检查点、追加恢复记录、发射调用证据、合并绑定阶段、累计计算预算及客户端表现通道注册，移除绝对年龄线性回退。wire/common/envelope 为 **21/9/9**，旧格式拒绝且保留原始数据。构建受 wrapper 下载和自动审批拒绝阻塞，不能声明阶段完成。见 [本轮证据与限制](reviews/20260927-capability-framework-p4-p6-validation.md)。下文历史版本和通过记录保留原范围。

2026-09-27 验收补齐：已受理 PLAN 的异常分类现在进入不可变结果，后续投影和重复请求读取同一机器码；释放异常使用 CONTROL_FAULT／RECOVERY_REVIEW，UNKNOWN 保持恢复核对优先。结果步骤去重同时比较 failure，Mob 策略只对最终最高优先级的并列匹配报冲突。现有 wire/common/envelope 字段未改变。仅 common 主源码 Java17 编译与 diff 检查通过，Gradle 下载受阻，未运行本轮平台验收；见 [记录](reviews/20260926-acceptance-repair-migration.md#2026-09-27-补齐)。

2026-09-26 134015 验收修复增量：当前 wire/common/envelope 为 **20/8/8**，不保留旧快照升级或缺字段适配。玩家物品、天然空手和 Mob 天然攻击使用共同 PLAN，Mob Navigation 为子步骤；MobTurnStrategies 独立持有策略与决策记忆，释放故障支持轮转与实例退役。空手基础伤害 1、局部原版推开及实际位移证据已接。独立 target clean build、66 项 common 和44项现有 GameTest 通过；未运行真实客户端、第二种 Mob 天然攻击专项及重启，不关闭整体验收。具体范围见 [修复记录](reviews/20260926-acceptance-repair-migration.md)。下文18/6/7是历史阶段基线。

2026-09-26 后续增量：Mob 接近固定所选目标及实体实例，失效不重选；玩家近战使用 `TacticalActor` 当前实例端口。common 新增纯值 `AbilitySource`，旧意图仅派生来源视图，装备校验已使用它；wire/common/envelope 仍为 **18/6/7**。天然能力尚未接入根计划。见 [来源与目标绑定验证](reviews/20260926-capability-source-and-target-validation.md)。

2026-09-26 P2 增量：近战攻击者支持与受击者支持改为独立注册，发现与服务端近战使用同一受击合同；关系、费用、伤害许可仍由既有后端核验。测试专用非 Zombie 受击适配通过发现、计划、真实伤害、费用、重试与退出清理；未实现天然能力作为通用计划执行者。见 [近战适配验证](reviews/20260926-capability-melee-adapters-validation.md)。

2026-09-26 能力框架 P1 增量：生产选择查询已分离，EXIT 重试使用机器码，根结果附带独立释放证据，实时 EXIT 豁免独立保存，行为注册在 common setup 冻结。当前版本轴为 **wire 18 / common 6 / envelope 7**，下表其余条目仍是 073919 基线。实际命令、47 项 GameTest 与剩余范围见 [P1 验证记录](reviews/20260926-capability-framework-p1-validation.md)。玩家/Mob 共用能力框架尚未完成。

| 项目 | 当前源码值/边界 |
|---|---|
| Minecraft / NeoForge | 26.1 / **26.1.2.84**；声明范围 `[26.1,)` 不证明范围内全部二进制兼容 |
| 参考源码 | 资料库 patched Minecraft **26.1.2.109**；不是完整 NeoForge 实现，也不是当前构建依赖 |
| 语言与源码 | common Java17，17 个主源码；26.1 target Java25，93 个主源码 |
| S2C / C2S / common schema / envelope schema | **22 / 19 / 10 / 9**；只接受当前格式，旧格式拒绝并保留原始数据 |
| 发现/几何预设 | 水平16、竖直8、圆盘半径8，最多16区块/64锚点；最终高度为锚点上下16 |
| 资源预设 | 每回合基础移动28 tick；环境20实际步骤；按会话捕获 |
| 战术击退 | 默认关闭；不宣称已有独立 gamerule |
| UI | NeoForge26.1 的 AUI 接入；核心目标式键鼠与物品选项，不是旧“1移动/2攻击”布局 |
| 正常入口 | 不需要开发门禁；`-debug` 仅日志；未知能力继续明确拒绝 |

版本直接证据见 [E01](reviews/20260926-073919-source-evidence.md#e01)；参数是当前预设，不是本轮重新决定的永久平衡数值。

## 2. 已有实现索引

| ID | 实际源码事实 | 准确限制 / 后续指向 | 证据 |
| --- | --- | --- | --- |
| I01 | common 是纯 Java 17 规则/值层；平台执行与 UI 在 target。 | 26.1 target 声明 Java 25、NeoForge 26.1.2.84；本次仅静态核查。 | [源码核查](reviews/20260927-capability-framework-doc-sync.md) |
| I02 | CombatEngine 持有成员、阶段、先攻、资源、敌对与操作裁决；TurnParticipant 显式表达实体及环境席位。 | MobTurnStrategies 持有决策记忆，共同 PLAN 使用同一规则权威；完整通用 AI 仍见 G21。 | [源码核查](reviews/20260927-capability-framework-doc-sync.md) |
| I03 | 稳定 operation ID、步骤/结果与不可变终态已有实现；P1 根结果新增独立释放证据。 | 规则提交不是 Minecraft 世界事务；全面类型化效果和生命周期对账仍见 G39。 | [P1](reviews/20260926-capability-framework-p1-validation.md) |
| I04 | 根行为与受许可的伤害/反应子操作分开。 | 许可作用域、深度和次数保护不能理解为新增玩家资源；后续效果类别需专用观察。 | [E04](reviews/20260926-073919-source-evidence.md#e04) |
| I05 | UNKNOWN 表达无法确认的执行结果，防止盲目重放。 | 已确认效果不能因清理异常被抹除；存活未知箭遵守持久隔离例外。 | [E04](reviews/20260926-073919-source-evidence.md#e04) |
| I06 | 连续场地、固定锚点、重新采样与加载边界已有实现。 | 发现区域、参战集合、几何域、因果域不同；16/8 发现预设不等于锚点上下16边界。 | [E06](reviews/20260926-073919-source-evidence.md#e06) |
| I07 | 候选可接近；实际合法攻击激活会话并重掷先攻。 | 选择和移动不触发攻击；一次首攻资格/已消费动作不因重掷恢复。只对当前受支持行为可作实现声明。 | [E10](reviews/20260926-073919-source-evidence.md#e10) |
| I08 | 迟到下一轮资格与死亡/掉线/换维度/卸载释放路径存在；实时 EXIT 豁免由独立所有者维护。 | 普通越界不是退出；完整合并、重启授权对账仍见 G40。 | [P1](reviews/20260926-capability-framework-p1-validation.md) |
| I09 | 合并选择当前最高先攻主域、安全环境入口并保留资源。 | 不能恢复旧的取最低先攻、全员重掷或补满资源做法。跨所有者恢复仍见 G15。 | [E06](reviews/20260926-073919-source-evidence.md#e06) |
| I10 | 合并保存 PREPARED/RULES_COMMITTED/BOUND 阶段；service 按规则与别名证据重绑衍生所有者。 | 阶段已实现，不等于跨世界保存原子提交；崩溃窗口与完整恢复见 G15。 | [源码核查](reviews/20260927-capability-framework-doc-sync.md) |
| I11 | 投影有独立世代、会话序号/版本与关闭/重同步边界。 | 客户端只读投影；真实合并与网络顺序验收未在本轮运行。 | [E03](reviews/20260926-073919-source-evidence.md#e03) |
| I12 | 玩家/Mob 的正常移动仍由原版执行并观察位移计费。 | 按服务端实际主动位移 tick，非节点数或路径权重；混合归因与特殊移动见 G05/G06。 | [E13](reviews/20260926-073919-source-evidence.md#e13) |
| I13 | TacticalPlanner 和 MinecraftCellProbe 形成路径提案与复验。 | 探针读取实时实体体型，尚不是稳定移动能力快照；不能误称为通用 AI 规划器。 | [E13](reviews/20260926-073919-source-evidence.md#e13) |
| I14 | Mob 导航 Lease 与决策门控保留获准控制链。 | 当前策略主要 Zombie/地面路径；不等于已保留任意 Brain/custom AI 的技能语义。 | [E10](reviews/20260926-073919-source-evidence.md#e10) |
| I15 | 环境按计划 tick、随机 tick、方块实体、block event 等通道门控。 | 没有覆盖即时红石、全部邻居副作用、天气或生成的通用承诺；环境预算不是每实体免费行动窗。 | [E06](reviews/20260926-073919-source-evidence.md#e06) |
| I16 | RegionalScheduledTicks 管理 held 所有权、查询/复制/保存与释放。 | 与区块及会话共同保存仍待独立验证；输入文档曾报告相关失败，不当作本轮复现。 | [E06](reviews/20260926-073919-source-evidence.md#e06) |
| I17 | d20/优势劣势/AC/韧性等基础规则集中。 | 数值按 03；伤害类型抗性与硬免疫合同仍搁置 H02，不扩写已实现。 | [E04](reviews/20260926-073919-source-evidence.md#e04) |
| I18 | 受支持攻击经过战术伤害上下文并观察原版受伤结果。 | 受害者 hurt 链不等于完整复用攻击者特殊技能；默认无击退，具体桥接仍需运行验证。 | [E10](reviews/20260926-073919-source-evidence.md#e10) |
| I19 | DamageTrace 保留伤害证据；AbilityCheckpoint/AbilityObservations 保存有限物品、方块和身体 before/observed。 | 仅覆盖采样字段；BE、容器、状态和生成等完整效果仍见 G41，不将父子证据重复计费。 | [源码核查](reviews/20260927-capability-framework-doc-sync.md) |
| I20 | 普通箭来源与可撤销模拟域分开，保留跨域因果身份。 | 预测接管还不等于精确边界接管；复杂组件/弹药不自动支持。 | [E07](reviews/20260926-073919-source-evidence.md#e07) |
| I21 | 普通箭因果合并与 pending 命中对账有生产路径。 | 历史 GameTest 不是本轮重跑；多域、来源消失和真实卸载见 G02–G04/G13。 | [E06](reviews/20260926-073919-source-evidence.md#e06) |
| I22 | 当前 common/envelope schema=10/9，仅接受当前格式。 | 旧格式拒绝并保留原始数据；已有正常跨 JVM 区块/终态读回记录，活动续战及崩溃仍待验收。 | [源码核查](reviews/20260927-capability-framework-doc-sync.md) |
| I23 | 存档包含规则、捕获配置、时钟、回执、来源、域与恢复证据。 | SavedData.update/setDirty 不是跨文件持久提交；persistNow 名字不代表已落盘。 | [E07](reviews/20260926-073919-source-evidence.md#e07) |
| I24 | 恢复候选先校验后安装，新世代撤销旧许可；未知箭隔离。 | 不强制加载成员区块，不复活旧 Navigation/运行对象；正式续战未验收。 | [E07](reviews/20260926-073919-source-evidence.md#e07) |
| I25 | S2C 注册协议22，C2S注册协议19；PLAN 结果和投影携带机器失败分类及独立释放结论。 | 真实协议握手、迟到/重连及故障重试不能由嵌入 GameTest 连接证明，见 G38。 | [源码核查](reviews/20260927-capability-framework-doc-sync.md) |
| I26 | DASH、DODGE、DISENGAGE 的规则状态和入口存在。 | HELP 和借机攻击闭环仍未完成；不能以撤离旗标证明反应玩法。 | [E10](reviews/20260926-073919-source-evidence.md#e10) |
| I27 | AUI 先攻、日志、物品与动态行为选项接入；参与者卡片为顶部居中无间隙的 30×48，只保留底部名字。快捷栏槽位最大 13px，物品／能力标签各固定两行：热栏＋选择确认后的动态行为，通用操作＋法术位空行；不再显示浮动行为面板与动作／反应常驻指标。ClientTacticalPlan 在物品未确认时不提供旧行为。 | 2026-09-27 此次修改后 JDK25 独立 target `gradlew.bat build --console plain --no-daemon` 通过；common 测试为 UP-TO-DATE，未运行游戏显示验收，见 G26。实体检查、实际可攻击资格和菜单存取权限不同。 | [界面源码](../targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalOverlay.java) |
| I28 | 个人库存整理与装备交换有独立受限许可。 | 满库存拒绝、跨菜单预测收敛和模组副作用仍见 G28；免费整理不是免费用物。 | [E02](reviews/20260926-073919-source-evidence.md#e02) |
| I29 | 结果分页、回执与补收机制存在。 | 字符串拒绝原因不能成为稳定控制协议；长历史和重连见 G19/G29。 | [E03](reviews/20260926-073919-source-evidence.md#e03) |
| I30 | 生产恢复采用先验证 RecoveryCandidate 再提交。 | 测试构造器/Codec 往返不能代替实际服务器重启。 | [E07](reviews/20260926-073919-source-evidence.md#e07) |
| I31 | EXIT 按当前已加载 Mob target 和最后玩家约束决定。 | 保留用户场内退出修订；将豁免与历史回执拆分是授权更正，不回退退出政策。 | [E05](reviews/20260926-073919-source-evidence.md#e05) |
| I32 | 有效会话自动进入独立客户端控制与目标式交互。 | 查询、瞄准、菜单、执行和取消区分；不会恢复旧 MOVE_BEGIN 手动许可为主交互。 | [E12](reviews/20260926-073919-source-evidence.md#e12) |
| I33 | focus/yaw/pitch/distance 镜头及 WASD/QE/中键/滚轮等接入。 | Home/O 等输入存在不证明地形避障正确；CAM-01仍仅记录。 | [E12](reviews/20260926-073919-source-evidence.md#e12) |
| I34 | 鼠标手势 owner/revision 和 UI 优先级防点击穿透。 | 必须用真实输入测迟到响应、失焦、屏幕切换，不能只直接调用 choose。 | [E12](reviews/20260926-073919-source-evidence.md#e12) |
| I35 | 客户端预测与服务端世界行为门控仍保留。 | 应通过授权行为打开有限执行能力，不得为兼容直接全放开原版输入。 | [E08](reviews/20260926-073919-source-evidence.md#e08) |
| I36 | TacticalBehavior 使用 TacticalActor；玩家 request 与 Mob 决策均进入 TacticalActions.submit，共用根 PLAN、费用及终态。 | AbilitySource 支持 EQUIPMENT/BASIC/INTRINSIC/STATUS；STATUS 授予示例在独立测试源集。目标/费用/提交点仍部分受 Capability 枚举约束。 | [源码核查](reviews/20260927-capability-framework-doc-sync.md) |
| I37 | 放置、破坏、食饮、工具、桶及有限远程有明确适配。 | 实际支持由物品、目标、组件和效果范围共同确定；类名或 capability 名称不是全部兼容证明。 | [E08](reviews/20260926-073919-source-evidence.md#e08) |
| I38 | PresentationAdapters 注册通道及独立 State/Frame，BuiltinPresentation 提供内置适配；客户端 setup 冻结。 | 旧绝对年龄线性回退已移除；AGE offset 按实例/renderer 生命周期持有，真实渲染及独立非人形兼容仍见 G45/G46。 | [源码核查](reviews/20260927-capability-framework-doc-sync.md) |
| I39 | 65 项测试路径从 main 转到 gameTest，新增独立 Bootstrap。 | 这是路径隔离，部分文件内容也改动；旧真实输入探针移除，不代表缺口关闭。 | [E14](reviews/20260926-073919-source-evidence.md#e14) |
| I40 | -debug 仅负责日志诊断；旧 LocalTime/客户端开发探针入口清理。 | 不能把诊断开关解释为新的测试世界、权限旁路或生产功能门禁。 | [E14](reviews/20260926-073919-source-evidence.md#e14) |

## 3. 支持范围必须分轴表达

“支持 Mob”至少区分：可以参与场地采样、可以暂停、可以成为成员/目标、可以正常导航、可以执行具体技能、可以自主决策、可以恢复正确表现。073919 的这些集合并不相等；加入先攻不代表已经实现所有攻击和特殊动画。

玩家物品同样按明确行为及效果范围声明支持。普通弓/弩/雪球、Consumable 与工具已有生产入口；特殊附魔/弹药、任意自定义 BlockItem override、模组流体或特殊容器不能由通用名称推定支持。免费方块自身交互不得回退到手持物品；开菜单与持续存取也不是一个许可。

## 4. 实际验证与历史记录分离

本次文档同步只核对源码、引用及现有日志，没有运行构建或测试。此前 69 项 common、48 项 GameTest 及两进程正常保存读回的命令和范围见 [继续验证](reviews/20260927-040106-continue-validation.md)，不提升为本次验收或完整框架通过。历史 E01–E17 所指原报告当前缺失。

073919 历史核查：OpenJDK21 运行 `javac --release 17` 编译全部17个 common 主源码，退出码0。该命令不运行测试，也不编译 NeoForge target。测试源集能编译不等于 `runGameTestServer` 已执行；本轮没有重新执行输入文档列出的46/37等历史次数。

输入文档报告的 `scheduled_tick_hold`、`arrow_pending_removal`、`tactical_behaviors` 与双客户端 camera/actor 探针问题，作为待核验历史保留于02；没有原始日志就不能给当前快照盖通过/失败章。旧探针移除后须有独立测试设施补足覆盖。

## 5. 下一次修改本文件的要求

每项事实必须同时写支持范围、真实落点与证据等级。重构后路径变化更新本表链接，但保留 I 编号；只新增接口或测试类，不得把对应 G 项直接关闭。新增能力应有注册、授权、真实执行、终态/恢复及实际输入的独立证据。
