# DNDTurn：已确定且已有实现的决策交接

快照日期：2026-09-25。读者：后续实现者、文档作者与评审者。

## 1. 使用边界

本组文件以 `DNDTurn-sources(20260925-120401).zip` 为源码基线，并纳入本次后续修复。规则依据为用户本轮提供的三份文档。`.109` patched source 仅作原版行为参照；实际依赖仍为 `.84`。

- 本文件只报告**已有源码落点的决策与能力**，每项完成范围受本行限制。代码存在不等于完整玩法或运行验收完成。
- [02_GAPS_AND_CONFLICTS.md](02_GAPS_AND_CONFLICTS.md) 是本组三份文件中的唯一缺口、未决问题及冲突清单。
- [03_PLAYER_RULES.md](03_PLAYER_RULES.md) 汇总玩家可观察规则，并区分当前原型、目标规则与冲突。
- 无冲突的实现事实以本次修复源码为准。尚未落实的设计与源码发生冲突时，保留双方并登记冲突；不把缺陷改写成正式规则，也不自行改变产品决定。
- 旧 D/R/H/X/Q 编号保留为来源编号；本文件 I 编号用于交接实现事实。旧文档中“尚待 Q05/Q06/Q10 决定”等过期引用不再代表问题仍未决定。
- 当前不宣布 M4/M5/M6 完成。正常 `/dndturn tactical`、CombatIntent、有效会话调度已接通，不以开发开关规避剩余缺口。

以下路径相对仓库根。`core`＝`common/src/main/java/cc/sighs/dndturn/combat/`；`target`＝`targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/`。

## 2. 权威状态与操作模型

| ID / 来源 | 已落实的决定 | 源码与准确边界 |
| --- | --- | --- |
| I01 / D01–D03 | common 是纯 Java 17 规则和值模型；平台执行、网络、存档和显示在 target。首发战斗实现集中于 NeoForge 26.1。 | `common/`、各独立 `targets/`；26.1 固定依赖为 NeoForge `26.1.2.84`，不是辅助阅读源码的 `26.1.109`。 |
| I02 / D06、R05–R06、R16 | `CombatEngine` 单一裁决成员、阶段、先攻、资源及有向敌对关系；一个实体最多属于一个活动会话。 | `core/CombatEngine.java`。不能据此宣称已有所有 Mob、组队或阵营适配。 |
| I03 / D04–D05、R07、R42、R61 | 不可变操作快照、operation ID、步骤序号、不可变结果和唯一终态；同 ID 不同负载冲突，同一结果不重复扣费。 | `OperationRecord`、`CombatEngine.beginOperation/publish`。世界副作用不具备事务回滚保证。 |
| I04 / R48、R50 | 根行动与子伤害分开；伤害子操作核验来源、目标、阶段、使用次数与期限；嵌套有边界。 | `EffectPermit` 与 `beginOperation`；当前子深度限制 8、每根子操作数量限制 64。是保护限制，不是额外玩家行动资源。 |
| I05 / D10、R10 | 无法确认的已执行效果可发布 UNKNOWN，释放控制，不盲目重放。 | 操作结束、成员退出、环境异常与恢复入口已采用该模型；跨文件崩溃一致性仍未完成。 |

## 3. 建场、先攻与合并

| ID / 来源 | 已落实的决定 | 源码与准确边界 |
| --- | --- | --- |
| I06 / D07、R12、R27、Q13 | 发现范围内已加载实体的包围盒中心作为固定锚点；水平形状为二维凸包外扩圆盘，竖直为锚点最低 Y−16 至最高 Y+16；方块用方块中心查询。 | `MinecraftRegionSampler`、`EncounterRegion`。发现范围、最终场地和参战名单是三个不同概念；超过采样限制或遇未加载发现区拒绝建场。 |
| I07 / R11–R17 | 候选、正式行动、环境、结束阶段已建模；合法候选攻击在命中前触发开战并重掷先攻，不返还触发动作；首攻资格按会话及攻击者登记。 | `CombatEngine.beginOperation/activate`、service 近战入口。目标关系/首次优势的 target 适配当前局限于已支持实体。 |
| I08 / R19、R25、R62 | 迟到成员即时登记、下一轮取得行动资格；普通越界不直接退会，主动行动受空间门控；死亡、掉线、换维度、卸载等有释放入口。 | `join/leave`、`reconcileMemberLocations`、runtime 生命周期。复活、客户端残留及所有退出组合仍需验收。 |
| I09 / R23–R25、Q16 | 合并锁定当前回合先攻最高者所在主会话：11/12/13 取 13；在目标环境入口提交，保留已有先攻与已消费资源，迁入者下一轮行动。 | `MergePlan`、`planMerge/commitMerge`；错过入口可续约，已授权的环境步骤不当作新入口。 |
| I10 / D07、R23–R24 | 合并重新采样场地；碰到第三会话则扩展连通计划；旧会话有别名及关闭记录。 | `attemptPendingMerges/sampleMergedRegion`；已接精确区域与 held tick 所有权切换。正常战术会话使用统一合并调度；完整失败恢复未完成。 |
| I11 / R23、R66 | 合并保留逻辑主 ID，但发布更新的全局投影序号；同 ID 保留结果前缀，迁入会话重建结果游标。 | `EncounterProjectionOrder`、`ClientCombatState`、service 合并发布。纯值顺序测试不能替代真实联机。 |

## 4. 原版移动、环境与伤害

| ID / 来源 | 已落实的决定 | 源码与准确边界 |
| --- | --- | --- |
| I12 / R36–R42 | 普通主动移动按实际发生位移的服务端 tick 收费：普通 1、水中 2、起跳附加 1；零位移免费，同 tick 多包一次。 | service 玩家移动观察、Mob 移动观察与计费；不是按格或位移长度扣费。混合外力证据仍不完整。 |
| I13 / R09、R38 | 纯值三维路径提案及 target 碰撞探针存在；正常移动由原版网络移动或 Navigation/控制链执行。 | `TacticalPlanner`、`MinecraftCellProbe`、移动 Lease。完整体型/姿态/能力快照与复杂地形复验未完成。 |
| I14 / X05 | Mob 使用有边界的导航控制 Lease；终态 UNKNOWN 撤销 Lease，tick 门控独立检查当前成员、阶段、pending 和 ownership。 | service、`MobNavigationLeaseMixin`。只停止自己仍持有的路径；Zombie 的覆盖不能推广为所有 AI。 |
| I15 / R08、R29–R31 | 计划方块/流体 tick、随机 tick、方块实体、block event 已有分通道门控；环境预算在实际步骤成功完成后提交。 | `RegionalScheduledTicks`、runtime 与相关 Mixins。即时邻居更新、红石/活塞、天气/生成不包含在完整覆盖承诺内。 |
| I16 / R30 | held 计划 tick 保留剩余延迟、优先级与相对顺序，参与查询/复制/保存视图；正常释放交还原版。 | `RegionalScheduledTicks`；复制前统一捕获源条目，防同队列重叠复制递归。磁盘区块与会话共同恢复尚未证明。 |
| I17 / R01–R04、R44–R45 | d20＋5、自然 1/20、优势劣势抵消、AC 与韧性换算已集中实现。 | `CombatRules`：AC=max(0,⌊armor⌋)+持盾2；damage=⌊max(0,weaponDamage−max(0,⌊toughness⌋))×暴击倍率⌋。 |
| I18 / R43、R45–R47、Q17 | 受支持近战只执行一次战术减伤，经授权伤害上下文保留原版吸收/耐久路径，处理无敌帧；战术击退配置默认关闭。 | `TacticalDamageContext`、伤害/护甲/盾牌 Mixins、service 近战。启用击退有立即原版碰撞响应路径；免疫、取消、破损等仍需完整 service 验收。 |
| I19 / R47–R48 | 近战与普通箭结果带结构化 DamageTrace：骰子、AC、减伤、规则/区域版本、生命与吸收损失、装备差量及盾牌/击退观察；箭矢另记录历史 owner、来源会话和 root。 | `DamageTrace`、共用 `attackTrace/damageEvidence`；UNKNOWN 可保留已观察净损失，准备异常与所有副作用仍须扩充验收。 |

## 5. 投射物、存档与客户端已有基础

| ID / 来源 | 已落实的决定 | 源码与准确边界 |
| --- | --- | --- |
| I20 / R32、R67–R70 | 普通箭有不可变来源和可撤销模拟归属；失效归属撤销后重新检测当前路径，合并别名指向活动主域，来源会话结束后无活动域时恢复原版推进。 | 入域仍用预测运动段；用户要求的精确边界接管尚未实现（C04）。不增加模组寿命。 |
| I21 / R48–R50 | 普通箭命中先校验 PvP 与受支持效果，分别处理模拟域、射手域和目标域；合法 AB 进入安全入口合并再结算，待决碰撞复验目标位置。 | 双向 AB GameTest 与未命中/零伤害/拒绝/吸收 trace 回归；复杂组件、异常和生命周期覆盖见 G02–G04/G13。 |
| I22 / R63 | common schema 3 按阶段、资格和 cursor 恢复队列，允许合法 ENVIRONMENT 空队列；每会话保存移动与环境预算。 | target envelope schema 5；旧 common 1 迁移每会话预算，common 2 迁移 observation epoch 语义；target 旧 schema 逐级迁至 5，包含回执字段重命名与投射物隔离记录，不用新配置替换旧预算。 |
| I23 / R63、C07 | 持久化包含规则值、累计时钟、来源/模拟域、待合并/命中、回执、Lease 证据及隔离投射物；箭矢元数据变化独立触发保存。 | 新建会话捕获当前预算；合并保留已消费资源，后续回合使用主会话预算。实际重启与崩溃对账仍待验收。 |
| I24 / D10、R64 | 恢复撤销旧许可，待决工作保守 UNKNOWN；全部活动会话走统一恢复审计，证据不足释放会话，不复活旧导航对象；UNKNOWN 存活箭保持隔离。 | `auditRestoredEncounters`、`failRestoredWork` 等；未证明活动正式会话可连续恢复。 |
| I25 / D03、R66 | C2S 是意图；S2C 有世代、序号、关闭墓碑、结果分页与重同步；旧连接回调核对连接及维度。 | `CombatNetwork` 当前协议 **12**。同世代已完成请求在当前成员检查前按旧快照身份核对；有效成员可补收只读结果。 |
| I26 / R39、R53–R54 | DASH、DODGE、DISENGAGE 已有 common 状态、target 服务端动作与可重绑原型按键。 | 疾走消耗动作增加一个基础移动预算；回避到下次自身回合，撤离到本回合结束。借机攻击执行器未接，撤离旗标不代表完整反应玩法。 |
| I27 / D09、R58 | 原型有 AUI 常驻动作栏、资源、先攻头像与成员状态、命中信息和滚动日志；按键与界面共用服务端意图。 | `TacticalOverlay`、`CombatControls`、`ClientCombatState`。AUI 物品面板仍为预览；原版个人背包在本人合法回合有整理许可。自动虚拟镜头见 I32；区域/路径及完整联机边界见缺口清单。 |

## 6. 当前参数与支持范围

这些是 120401 基线沿用的开发预设，可覆盖，不是永久平衡数值。来源：`ServerCombatConfig`、`CombatRules`。

| 参数 | 当前值/行为 |
| --- | --- |
| 配置位置 | 世界目录 `dndturn-server.properties` |
| 入口能力 | 正常战术入口无开发开关；`/dndturn local` 保持独立管理员能力 |
| 水平发现 / 竖直发现 | 16 / 8；与最终场地的上下 16 包络不同，本轮已明确区分，见 C03 |
| 圆盘笔刷半径 | 8 |
| 发现区采样上限 | 16 区块、64 锚点；不是暂停粒度 |
| 每回合基础移动 / 环境预算 | 28 / 20 tick |
| 战术击退 | `tacticalKnockbackEnabled=false`；当前是 config，不另宣称已有 gamerule |
| 近战 | Player↔Zombie 受限路径；方块位置各轴差不超过 1 且有视线 |
| 正式远程 | 未接；common 的 6 格常量不代表可用远程行动 |
| 会话捕获配置 | 半径、采样上限、击退，以及 common 权威持有的移动/环境预算；旧会话沿用保存值，新会话读取当前配置。 |

## AUI 与多人同意接入

NeoForge 26.1 固定使用 AUI 1.2.5。动作栏、资源、先攻成员/头像/资格、防御状态、选择、命中详情和最多 128 条有序日志已接 Overlay；物品使用原版 ItemStack 的 AUI 渲染副本。节点按状态差量更新，并处理 AUI 挂载转换与 refresh 代次。

common `ConsentWindow` 与服务端同意协调器支持动态名单、离场撤销、重叠合并/最早截止、掉线取消、400 实际 tick 期限、稳定请求/回复身份、建场前重新采样。待决请求不暂停世界。

修复待决 AB 箭矢移除时在错误成员域查找导致漏发 UNKNOWN；修复客户端历史尾部补收和已完成移动重试的等待状态。网络协议为 12，阶段、意图、结果与 START 状态有固定编码，结果游标独立于规则版本；就绪投影具有独立修订号。

历史文档引用 `version-differences/neoforge-26.1.2.84-aui.md`；该文件不在本次附件中，未核验，不提供失效链接。

## 7. 输入文档记载的历史验证（未经本轮独立核验）

以下次数及通过结论仅保留输入文档原始报告；附件未包含可关联到 120401 的完整运行日志，不能据此给本次修复盖章。

- common：46 项测试通过，覆盖合法空队列往返及继续推进、非法资格/cursor、预算隔离、疾走和合并后的预算继承。
- NeoForge 26.1：JDK 25 独立 `clean build :common:test runGameTestServer` 通过，37 项必需 GameTest 通过。
- 箭矢回归：真实 A→B 与反向因果合并、跨域未命中、PvP 拒绝、待合并移除唯一 UNKNOWN 与历史保留、旧会话结束后再接管、MISS/ZERO_DAMAGE/VANILLA_REJECTED/VANILLA_ACCEPTED、吸收及 trace 编解码。
- 客户端：实际启动新平坦世界，AUI 布局、选择、节点复用、重载、物品/头像、同意模态/截止/关闭探针通过；不是双客户端联机证据。
- 多人同意：common 4 项状态机测试、真实服务端三玩家建场/名单变更/合并/重试/掉线/过期，以及协议往返通过。
- SavedData：实际写盘重读合法空队列并推进到新成员；旧 envelope 2/common 1 活动值迁移保留预算。
- Forge/Fabric 1.20.1、NeoForge 1.21.1：JDK 21 独立 `clean build` 通过；仅证明 common 构建兼容。
- 对应未覆盖范围统一见缺口文档。历史 `legacy/CODEX_LONG_TASK_LOG.md` 未随附件提供。本次执行证据见本交付的 `REPAIR_NOTES.md`，不以历史结果替代。

新增箭矢场景使用独立 test environment 批次，避免挤占既有固定轮数测试的环境预算。GameTest 不替代真实联网客户端。

## 8. 交接给其他作者的工程背景

这些是现有架构约束与维护约定，不能混写为玩家规则或已经执行的协作事实。根 AGENTS 保持程序实现约束；本组三份文档描述实现与规则；本次源码修改及验证单独记录在 `REPAIR_NOTES.md`。未附带的历史文件不声称已更新。

- common 不持有 Entity/Level/导航/网络对象；服务端状态单线程写入，平台执行前后通过值快照与结果衔接。
- Mixin 只桥接固定版本 seam；新入口需查完整调用链、取消与早退、动态 override，不以邻近源码或编译通过代替目标运行验证。
- 不以 teleport/setPos 实现正常行走，不以永久 setNoAi 接管战斗；正常退出只释放自己持有的控制。
- CI、构建、发布和维护职责仍是工程流程，不作为功能完成证据。120401 源码包未包含完整 `.github/` 与根 `scripts/`，动态 CI 与分支保护只能保留文档约定，不能确认部署。

| target | Gradle JDK / 语言级别 | 发布单元 |
| --- | --- | --- |
| Forge 1.20.1 | 21 / 17 | 独立 target、独立 jar |
| Fabric 1.20.1 | 21 / 17 | 独立 target、独立 jar |
| NeoForge 1.21.1 | 21 / 21 | 独立 target、独立 jar |
| NeoForge 26.1 | 25 / 25 | 独立 target、独立 jar |

每个 target 独立 `clean build`；26.1 另跑 `:common:test`、`runGameTestServer`。根聚合不代替 26.1 独立验证。修改 common 须覆盖受影响 target；docs-only 检查一致性与链接即可。

维护责任按 common、各 target、构建/文档分开。PR 记录变更类别、common 合约影响、JDK、命令、实际运行范围及未支持部分；不得静默 no-op 冒充已适配。新增 target 要独立目录、wrapper、工具链、能力范围与验证；弃用需保留可重现历史。

现有 `publish.gradle` 同时有 Maven `publish` 和 CurseForge/Modrinth `publishMods`，二者用途不同，不能把文档中的命令当成冲突。Maven 按 SNAPSHOT/release 分仓，凭据仅从环境获取，POM 不外泄运行时构建依赖；模组平台发布用对应项目 ID 与 token。每次发布前核验目标 jar、metadata、common 资源和 client/server 启动。本文没有执行发布。

## 9. 本次明确修正的旧文档状态

1. 当前协议以 `CombatIntentHandler` 和客户端注册器的 **12** 为准；协议 5/10 均为历史描述。本轮未附带 `M3_ACCEPTANCE`，不声称已修改其正文。
2. F05 的“EXIT/MOVE_END 回执尚未持久化”更正为：已有 envelope 编码和恢复入口，跨重启行为与归档未验收。
3. F04 的“SavedData 已写盘重读”保留；不得扩大成活动会话重启通过。
4. F06 的 DASH/DODGE/DISENGAGE 接入保留；HELP、消耗品、正式远程、反应不因 common 枚举存在而完成。
5. 先攻取最高值 13 已定，不再保留取 11 的口误版本。合并保留原先攻，迁入者下一轮行动。

## 10. 本次后续修复的实现事实

| ID | 源码事实 | 验证边界 |
| --- | --- | --- |
| I28 | 背包 SWAP 在原版执行前计算交换后可用容量；溢出的旧装备必须全部能留在个人库存，不授权掉落或创造模式删除。 | `VanillaInputPolicy`；新增真实连接 fixture 下满背包、空槽、热栏/副手及源槽合并回归，运行状态见交付记录。 |
| I29 | 缺页和尾页补收统一使用 `CombatIntent.resultSync`，分别填写规则版本和结果游标。 | 编码布局仍为协议 12；新增非零游标往返断言，真实网络仍见 G19。 |
| I30 | 恢复测试将隔离的 SavedData 送入生产服务构造器，校验拒绝安装、禁止 START、保留原始 JSON，以及合法恢复和隔离记录。 | 不替换运行服务；不等价于磁盘重启、区块先后保存或崩溃验证，见 G16。 |
| I31 | EXIT 提示说明先中心越界、场内拒绝及多人离场行为。 | 客户端只提示，不自行裁决位置或豁免服务端校验。 |

这里的“原型”仅表示受限玩法成熟度，不再代表一类被排除合并或统一恢复审计的服务端会话。

## 客户端控制重构（2026-09-25）

| ID | 源码事实 | 证据边界 |
| --- | --- | --- |
| I32 | `ClientControl` 唯一持有镜头/角色模式、独立镜头位姿、临时输入接收者、同意模态状态、鼠标手势和镜头按键。移除 `OverlayMouseControl` 及 Overlay 的 pointer 布尔状态。`ClientCombatState` 保留权威会话/身体许可投影；`CombatControls` 只保留意图/移动请求回执状态。 | 有效会话自动镜头；Screen/模态/失焦不会清除用户模式。具体运行证据与范围见下述接入文档及 G33。 |
| I33 | `.84 Camera.alignWithEntity` 后、视锥/矩阵前应用虚拟位姿；不替换 camera entity，不修改真实玩家位姿、碰撞、飞行能力或服务端状态。镜头按渲染帧更新，目标射线独立生成，攻击取消旧 hitResult 回退。 | 原版 `isControlledCamera` 和 `sendPosition` 保留；本机暂停身体时仍执行网络维护，远端插值门禁保持。 |
| I34 | `MouseHandler` 原生 Pre 派发包围点记录手势并在 AUI 处理后阻止原版抓鼠标/按键写入；AUI 固定版本文档派发限定到唯一接收 Document。模态为实际窗口 viewport，空白输入有明确归属。 | UI 回调和底层包/热栏/资源同时断言；非完整多客户端验收。 |
| I35 | `Minecraft` 根输入与 `MultiPlayerGameMode.interact/useItem/useItemOn/attack/破坏` 门禁阻止未授权预测/挥手/发包；身体暂停与本机模态捕获分开。重绑定不写 options，冲突按稳定行动顺序消解。 | 服务端许可没有放宽；HELP/食饮/远程/反应保持原范围。 |

固定版本源码、实际 jar 反编译证据、命令与精确运行边界见 [客户端控制接入记录](version-differences/neoforge-26.1.2.84-client-control.md)。本轮从含用户未提交修改的工作区增量修复，未取得指定 124521 压缩包，不宣称完成该包逐文件比较。

2026-09-25 后续修订：`CombatIntent.start` 统一无会话 START 参数（版本 0），`CombatControls` 使用此入口。`beginEncounter` 将 Zombie 改为可选成员；无敌人时已批准玩家可建立候选会话并与环境阶段循环。`player_only_start` 从协议编解码和服务端 handler 进入，检查纯玩家成员、重试及两个环境周期；运行证据见 REPAIR_NOTES，不等同真实客户端按键验收。
