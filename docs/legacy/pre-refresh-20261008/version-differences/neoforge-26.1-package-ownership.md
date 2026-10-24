# NeoForge 26.1：包边界与状态所有权

本记录对应 2026-09-30 包级重构。target 声明 Minecraft 26.1，实际解析 Minecraft 26.1.2／NeoForge 26.1.2.84；Gradle 9.7.1，JDK 25.0.3，common 保持 Java 17。未调整依赖、玩法费用、退出规则、非敌对移动或恢复时的 UNKNOWN 合同。

## 源码映射

| 原职责／名称 | 当前归属 |
| --- | --- |
| common rules／combat／control | domain.actor／fact／ability／effect／ai／encounter／control／action／resolution／spatial 与 application.actor／planning／inspection／projection |
| ConditionDefinition／ConditionRegistry／ActorRuntimeState.Condition | domain.effect.EffectDefinition／EffectRegistry／EffectInstance；ActorRuntimeState.effects 与 EFFECT／EFFECT_INSTANCE 来源 |
| CombatEngine／CombatStateSnapshot | domain.encounter.EncounterAuthority／EncounterStateSnapshot |
| TacticalIntent／TacticalPlanner | domain.action.ActionIntent／application.planning.MovementPlanner |
| EffectInvocation／WorldEffectObservation | domain.encounter.operation.TriggeredExecutionRecord／WorldOutcomeObservation |
| ParticipantEffectState／ParticipantEffects | platform.server.effect.vanilla.VanillaEffectSettlementState／VanillaEffectRoundController；前者及独立结算测试迁出 common |
| ActorStateService／NativeSnapshots／NativeFacts | platform.server.actor.ActorStateAuthority／MinecraftSnapshotCapture／MinecraftFactProviders |
| TacticalActions／TacticalExecution／TacticalActor | platform.server.action.ActionExecutionCoordinator／AbilityExecutionContext／LiveActorContext |
| TacticalCapabilities／TacticalBehavior／NativeAbilityFacts | platform.server.ability.AbilityAdapterRegistry／AbilityExecutor／MinecraftAbilityFacts |
| CombatNetwork／TacticalNetwork／InspectionNetwork | platform.network.EncounterProtocol／ActionProtocol／InspectionProtocol；C2S 实现留在 server.network |
| EffectControlPolicy／ItemUseEffects | platform.server.control.WorldOutcomePolicy／platform.server.action.ItemWorldMutationScope |
| CreeperEffects／SkeletonFamily／VanillaAiFamilies | platform.server.builtin.creeper.CreeperAbilities／builtin.skeleton.SkeletonFamily／builtin.ai.VanillaAiFamilies |
| 平铺 client／mixin | platform.client 的 state／input／action／ui／presentation／render／simulation；platform.mixin 按 server／client 及接入业务分包 |

纯规则 ResolutionContext／RuleResolver／CombatRules 及领域使用的 action 值合同留在 domain；不能为了匹配示意目录而使领域依赖 application。RuleEmission 是密封接口，按 Java 17 未命名模块要求与实现一起放在 domain.actor。

## 所有权与生命周期

| Owner | 独占状态及生命周期 |
| --- | --- |
| ServerRuntime | MinecraftServer 实例注册、服务器世代、ActorStateAuthority 与 EncounterRuntime 创建；关闭时先保存／释放 Encounter，再关闭 Actor，finally 移除实例，关闭后不重新创建 |
| EncounterAuthority | 成员、阶段、资源、规则版本、操作及结果；仍是唯一规则 writer |
| ActorStateAuthority | ActorStates、当前实体证据、编译／反应／持久化；服务器线程校验与关闭保存 |
| ActionExecutionCoordinator | 行动执行、准备／接近／观察／释放；受限上下文不向外开放可变 Execution 字段 |
| ProjectionPublisher | 会话发布序号、投影修订、连接订阅；恢复安装序号，合并绑定，离开清投影，关闭释放全部订阅 |
| EnvironmentScheduler | 每个 ServerLevel 的当前环境步及公平轮转游标；规则权威仍持有环境预算；成功提交后清绑定，异常记 UNKNOWN 后结束控制，关闭清 Level 引用 |
| VanillaEffectRoundController | Minecraft MobEffect 回合接管；VanillaEffectSettlementState 只描述该平台计时，不作为 DND Effect owner |

原 ServerCombatService 的服务器注册、Actor 创建／销毁、发布 Map 与环境步 Map 已移交上述 owner。EncounterRuntime 保留跨 owner 的加入／退出／恢复／合并及现有移动、投射物协调。它仍较大，不能把这次改名描述成完成全部业务分解。

原 friend package 的访问通过 ActionHost、WorldOutcomeHost、VanillaEffectHost、ControlFacts 暴露必要命令或事实；实现委托原校验路径，原内部方法保持非 public。行动内 PlayerBehavior／VanillaBehaviors／物品与远程适配仍作为高内聚簇一起迁移，不为搬入 builtin 而公开执行内部状态。具体物种已经通过冻结注册机制安装；WorldOutcomeHooks／CloudOrigins 消除了通用世界／伤害逻辑对苦力怕的反向引用。后续剩余拆分见[唯一缺口清单](../02_GAPS_AND_CONFLICTS.md#package-refactor-followup)。

客户端共享几何、装备指纹、原版药效分类和表现身份放在 platform.spatial／observation／projection，不导入 server 实现。Mixin 只调用批准的 hook／policy；新增生命周期、移动、投射物和世界调度 seam，未改变固定版本注入目标、描述符、require 或取消语义。87 个 Java Mixin 与 JSON 逐项对应。

## 验证

根目录调用对应独立 target 的 wrapper 配置，均使用 `--offline --console plain --no-daemon`。迁移后的测试源集保留原断言；因包分离需要的访问桥只放 gameTest／uiTest，不进入发布 jar。

- `gradlew.bat -p targets/neoforge-26.1 clean :common:clean build :common:test`：通过，包含平台主源码、gameTest、uiTest 编译及 UI／控制／诊断检查；common 100 项测试（包含 6 项架构约束），target 1 项原版药效结算单测。
- 最后命名／引用整理后再执行 `-p targets/neoforge-26.1 build :common:test`：通过；再次独立运行 `tactical_effect_reactions`、`participant_effects`、`creeper_effect`：全部通过，覆盖最终 Mixin 入口与 Effect 字段命名。
- 使用 JDK 21.0.11 分别执行 `-p targets/forge-1.20.1 clean build`、`-p targets/fabric-1.20.1 clean build`、`-p targets/neoforge-1.21.1 clean build`：全部通过；只验证 common 源码兼容与各 target 构建，没有为旧 target 启用26.1功能。
- 发布 jar 内87个 Mixin 配置条目均有对应 class，无旧 combat／rules package 及 GameTest 访问桥；common EncounterAuthority 字节码 major=61（Java17）。
- 架构测试检查 domain 不引用 application／platform，common 不引用 Minecraft，common／target 无 split package，Java package 与路径对应，客户端及共享平台查询不引用 server，通用框架不引用 builtin／具体物种，Mixin 仅使用批准 seam，Mixin JSON 完整且无重复。`Enemy` 是通用分类接口，不按具体物种禁止。
- 整套 `runGameTestServer` 已实际执行，但未通过：`RepairGameTests.playerOnlyStart` 的序列读取已不存在的 Encounter，抛出 `unknown encounter` 并终止服务器。同进程夹具干扰在重构前的[默认能力记录](neoforge-26.1-default-capabilities.md)及02已有同类记录；单项通过支持隔离问题的判断，但不等于完成根因修复。本轮仍保留失败，不降低断言、关闭生产入口或声称全量通过。
- 分别执行 `runGameTestServer -PgameTestSelection=dndturn:<name>`，每项独立 JVM，以下 15 项全部通过：`player_only_start`、`tactical_effect_state`、`tactical_effect_reactions`、`participant_effects`、`scheduled_tick_hold`、`environment_processes`、`creeper_effect`、`mob_standardization`、`mob_standardization_external`、`gate_melee`、`default_interactions`、`body_control`、`restart_history`、`effect_reaction_restart`、`merge_world_boundary`。

运行过程中发现并修正迁移测试桥 AiTestAccess 的自调用错误，再重新执行上述场景；不是用删除测试规避失败。运行日志保存在本地忽略目录 `build/verification/package/`。本轮没有执行真实客户端／双客户端输入、镜头和显示验收；GameTest 中的历史／恢复检查也不替代真实崩溃保存顺序验证。

这是源码/API 及当前保存值命名迁移；不引入旧枚举、旧字段的兼容别名或 schema 递增。原存档校验失败时保留原文的合同继续适用。旧名称出现在历史证据中时按本表映射，不把过去的构建或玩法验收重写成本轮通过。

<a id="persistence-owner"></a>
## 持久化 owner 后续拆分（2026-09-30）

`platform.server.persistence.EncounterPersistence` 从 EncounterRuntime 接管 SavedData 引用、候选读取／校验与失败隔离、上次保存修订／时钟、不可变规则快照缓存、行动检查点及追加恢复证据。对外只提供记录命令、不可变快照与按需采样写入入口；没有把运行时内部方法改为 public，也没有持有 Entity／Level 或行动驱动。

EncounterRuntime 创建并持有该 owner，安装验证后的规则及平台值。跨 owner 的检查点采样、当前世界实例复验、待恢复会话及合并／Lease 对账仍由运行时协调。关闭仍先强制采样保存、再释放控制；合并仍在 PREPARED、RULES_COMMITTED 和 BOUND 边界保存。证据不会因成员离开或会话合并而清空，随运行时生命周期释放，不增加 TTL。终态检查点不可回写，对账追加新证据；读取接口返回脱离可变 Map 的快照。

保存策略保持原语义：规则修订或平台证据变化时写入，其他情况每20实际服务器tick保存时钟；仅在采样和 SavedData.update 成功后确认保存修订，异常保留重试资格。固定版本源码 `minecraft-patched-26.1.2.84-sources.jar` 中 `SavedData.setDirty(boolean)` 只写 dirty 标志，本次没有新增立即落盘或跨文件原子性承诺，也没有修改 Mixin 接入点、存档结构、许可或玩法。

本次验证（JDK25.0.3，独立26.1 target，offline）：

- `clean build :common:test` 通过：common 101项（含7项架构检查），target原版药效单测1项，以及已有UI／控制检查。新增架构检查限制持久化写入 owner 不能反向依赖运行时、实体或世界 owner。
- `repair_values` 中增加独立持久化检查：20tick写入节奏、证据变化立即保存、不可变读取、捕获失败不确认保存、损坏存档原文保留、终态检查点保护及追加对账。
- 分别独立运行 `repair_values`、`restart_history`、`effect_reaction_restart`、`merge_world_boundary`，均通过。日志在本地忽略目录 `build/verification/package/persistence-*.log`。
- 没有修改 common 生产代码或旧 target，本次不重复声明旧 target 构建通过；未运行全量同进程GameTest、真实客户端或崩溃保存顺序验收，既有缺口继续保留。

普通JUnit源集不含Minecraft依赖，因此新增SavedData检查放在gameTest源集并通过真实GameTest服务器执行；不为测试向生产jar加入探针或放宽可见性。
