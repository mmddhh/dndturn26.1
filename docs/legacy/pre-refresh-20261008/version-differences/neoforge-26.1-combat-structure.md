# 26.1 战斗结构接入记录（2026-09-30）

本记录对应 revised structural checklist 的增量实现，不表示 VM-00～VM-13 全部完成。固定依赖为 Minecraft 26.1.2／NeoForge 26.1.2.84；源码依据为本 target 解析生成的 `minecraft-patched-26.1.2.84-sources.jar`。common 保持 Java 17，新增平台接入仅在 NeoForge 26.1 启用。

## 已接入的状态与职责

| 边界 | 当前实现与限制 |
| --- | --- |
| 伤害前事件 | `CausalEvent` 携带 operation/root、Encounter 修订、来源、目标、阶段与 Damage payload。`TacticalDamageContext.hurtObserved` 在原生 hurt 调用前派发 ATTEMPT；`Intervention` 目前仅 PASS/CANCEL，取消不调用原生受伤入口。原有事后事件保持独立。未注册 ATTEMPT 处理器时不增加该捕获。不是任意直接 hurt 调用的全局事件系统。 |
| 付费反应 | `ActionCost.REACTION` 经 EncounterAuthority 扣除反应资源；非当前行动者可提交合法反应。付费 activation 只能发出与注册能力匹配的原生调用，不允许纯回调先修改状态再付费。原生 executor 可绑定同 ID 的 activation。G25 玩家确认窗口尚未接入。 |
| 过程值与所有者 | `ProcessAuthority` 单独持有不可变 `ProcessState`，保存定义／阶段转换、因果、owner、实例、捕获预算、语义值、随机游标、取消、原生待决与释放证据。手动执行上下文不再持有第二套 adapterState；同步触发执行也写入该 owner。每 owner 并发数及控制声明有界，冲突拒绝。 |
| 过程恢复 | 检查点采样时从运行 owner 获取值；恢复安装前验证定义、控制冲突、操作根和 actor 归属。因果历史按 Encounter 一次建索引，逐过程遍历有界父链。当前驱动只接受 AUTHORIZED_EXECUTION_STEP＋FAIL_UNKNOWN；恢复中的未终态过程转 UNKNOWN，不重放 callback。其他时钟／恢复模式明确拒绝，尚不能宣称跨回合续跑。 |
| 随机流 | 过程种子及游标进入纯值检查点，执行上下文通过 owner 推进确定性流；不替换世界随机源和 AI 评分随机源。尚无恢复后继续执行的验收。 |
| 移动入口 | `MovementPorts` 注册并冻结独立的规划／启动／驱动释放接口；GroundMovementPort 承担既有地面导航实现。执行继续使用原生导航链，实际移动费用仍由规则权威观察结算。非地面 provider 尚未安装或验收。 |
| 部件目标 | `BodyTargets` 将 hit body 映射回唯一 root Actor，目标携带 adapter ID／版本、part 和实例证据。能力事实和受伤适配分别显式声明 facet 支持，并复验归属／作用位置。未安装 Dragon adapter，不为部件创建成员或资源。 |
| AI 证据 | 观察增加位置、来源、年龄、置信度及关系；实例绑定的有界语义记忆保留已观察位置，遮挡目标不使用新的位置参与评分。planner 扩展控制、减益、区域及友方支援候选，并复核 provider 提案的能力绑定。记忆尚未持久化，新增策略尚无专用物种闭环。 |
| 类型化观察 | `ObservationRegistry` 冻结类型／版本／字段合同，`NativeObservation` 保留具体操作、主体、确定性和有界类型化值。executor 声明必要观察，缺失时保留已有证据并归 UNKNOWN。内置仅接入身体位置／生命／吸收／移除状态；不把它当成传送、生成或环境变化的完整观察。 |

## 固定版本入口核对

读取了 LivingEntity.hurtServer 的前置免疫／死亡／火焰早退及原生伤害链、ServerPlayer.hurtServer 的 override 早退和 super 路径。新 ATTEMPT seam 位于本模组获准的 TacticalDamageContext 调用链，不以 loader 的 IncomingDamage 事件冒充完整前置边界，也不新增可选 Mixin 掩盖覆盖缺失。

EnderDragonPart 为非 LivingEntity 的 PartEntity，final hurtServer 委派给 parentMob.hurt；BodyTargets 因而分开 root 与实际受伤 body。默认接收器不自动宣称支持 multipart，事实 provider 和 receiver 都必须显式支持。H04／H05 的玩法暂缓不变。

## 验证范围

- NeoForge 26.1：JDK 25.0.3，`clean :common:clean build :common:test --offline --console plain --no-daemon` 通过，common 101 项测试零失败。此后恢复因果索引调整的增量 `build :common:test` 和 restart_history 场景也已通过。
- Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1：JDK 21.0.11，各自 `clean build --offline --console plain --no-daemon` 通过；只证明共享代码构建兼容。
- 已有独立 GameTest 场景通过：tactical_effect_reactions、creeper_effect、mob_standardization、prototype_zombie_navigation、restart_history、effect_reaction_restart、merge_world_boundary、gate_melee、body_control、environment_processes、repair_values。后续阶段合同调整重跑反应、Creeper、导航、历史恢复、反应恢复及合并场景。
- 上述 structural 批次没有新增、删除或改写测试。现有测试未发现需要按新规则移除的冲突。首次误用未注册的 navigation_regressions 选择名导致零测试，随后改用已注册的 prototype_zombie_navigation 并通过；零测试不计通过。后续 P0 批次新增测试见下节。
- 命令输出保存在本地忽略目录 `build/verification-structural-*.log`。独立场景通过不代表全量同进程、真实客户端、崩溃恢复或第三方兼容验收通过。

未完成目标统一记录在 [02 缺口](../legacy/02_GAPS_AND_CONFLICTS.md#combat-structural-remainder)，不以本记录关闭 checklist 的 archetype 或最终完成判据。

## P0 执行前校验修复（2026-09-30）

- VM-01／03：activation 注册独立声明 `ReadContract`，既有三参数入口仍采用定义的读取合同。触发判断不再被迫读取仅在能力执行准入时捕获的目标可用性事实；执行仍经过完整 resolver。ATTEMPT 不能冒充 DAMAGED 等事后事件。
- VM-03：过程容量、控制冲突、时钟／恢复支持和初始值校验提前到触发能力扣费之前。EncounterAuthority 校验执行器、来源和世代；待接受效果的行动／反应从规范 pending 操作推导占用，阻止嵌套调用同时占用同一余额，不增加第二套资源账本。既有 INTERRUPT 入口也检查反应占用。关闭会话的同负载重试不重新准入；未结算的触发子操作阻止父操作提前发布终态，保留已有持久许可下的延迟伤害因果语义。
- VM-02：手动执行的步骤预算在原生 tick 回调之前校验并记录，到达上限后走取消／释放。过程边界不可倒退，已释放控制不能重新推进原生步骤；释放失败不撤销尚未释放的控制声明。终态及原观察不改写，后续控制对账的完整接入仍保留在02。
- VM-04：`BodyFacet` 同时绑定根 Actor 实例和命中 body 实例。根不变但 body 被替换时，旧目标明确失效；新的 body 仍映射同一 Actor，不生成独立成员或资源。

固定版本核对重新读取本地 patched LivingEntity／ServerPlayer 的 `hurtServer` 前置早退及 EnderDragonPart 的 final 委派入口；没有新增 Mixin 或开放 Enderman／Boss 玩法。新增 `CombatStructureChecks` 位于独立 `compat` 测试包，仅使用公开注册入口与既有 GameTest authority 夹具；不能视为真实客户端输入或正式 multipart 战斗验收。

新增 common `CombatStructureTest` 验证原子拒绝、事件边界、费用、资源占用、去重／恢复、过程控制、独立读取合同和部件身份。新增 `dndturn:combat_structure` 实际运行验证：同一伤害窗口的免费／付费防御、控制冲突不扣费、反应耗尽后不再次执行、取消时原生 IncomingDamage 事件为零，以及替换 body 后旧目标拒绝。

本批次日志使用本地忽略目录 `build/verification-p0-*.log`，与前面的 structural 批次分开。VM-02 staged／跨回合续跑、G25 确认窗口及正式 multipart 能力尚未完成；本节不关闭全部 P0。

验证结果：NeoForge 26.1 使用 JDK 25 执行 `clean build :common:test --offline --console plain --no-daemon` 通过，common 共112项（含7项 PackageArchitectureTest），零失败。Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1 使用 JDK 21 分别执行独立 `clean build` 通过；common 仍为 Java17 值合同，其他 target 未启用新的平台能力。

独立 GameTest 共10个场景通过：combat_structure、tactical_effect_reactions、creeper_effect、mob_standardization、gate_melee、prototype_zombie_navigation、restart_history、effect_reaction_restart、body_control、environment_processes。每次输出均明确运行并通过1项 required test；这里的 restart 场景是普通 GameTest 运行，不宣称完成第二进程恢复或崩溃对账。测试中发现并修复了触发／执行读取合同耦合；既有延迟伤害回归还确认父操作终态限制必须保留原有许可因果语义，未删除或改写该测试。
