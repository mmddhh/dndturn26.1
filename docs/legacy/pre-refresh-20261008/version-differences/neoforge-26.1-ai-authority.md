# NeoForge 26.1 AI / Authority 重构证据

2026-09-29。配置固定 Minecraft 26.1 / NeoForge 26.1.2.84；Gradle JVM/target 为 JDK 25.0.3，common 编译 Java 17。没有升级依赖。开始时工作树已有 Data Model、文档迁移与测试删除；本次保留这些改动，没有删除测试。AGENTS 指向的 01–04 当前实际在 `docs/legacy/`；MAINTENANCE_WORKFLOW.md 不存在。

## Ownership 与依赖

| 所有者 | 本次边界 |
|---|---|
| CombatEngine | 成员、先攻、当前回合、资源、敌对关系、结果仍唯一权威；无 AI 特权 |
| ServerCombatService | recovery、运行状态、区域、PlayerMoveLease/MobMoveLease、环境步、投射物域继续由真实 owner 管理；`captureAdmission`、`captureEntitySimulation`、`controlEvidence` 只读 |
| AuthorityProjection | 捕获 actor/input/block/incoming-damage 有限事实；不授予许可，不撤销控制；container 原版检查只在 containerInput 采集 |
| OperationAdmissionPolicy | runtime readiness；不承担 RuleResolver 合法性或费用 |
| InputPolicy / VanillaInputPolicy | 前者纯 authority；后者保留 container ID/state/slot/click、swap 容量检查及校正 |
| ActorControlPolicy | Goal/custom AI、移动、持续使用、单一身体驱动分别求值；移动 lease 不放行自主决策或使用 |
| SimulationPolicy | 环境过程 ALLOW/HOLD、普通身体、展示时间投影；不推进世界或资源 |
| EffectControlPolicy | 只迁移无战术 frame 时的成员伤害／环境爆炸例外；既有 EffectPermit 与 InteractionPolicy 不改 |
| AiDefinitions | definition boundary 注册、冻结、first-use 缓存；key 为 ActorDefinition id/version + implementation/navigation 类型；装备、当前目标、LOS 不参与 bake |
| MobTurnStrategies.Decisions | encounter/actor instance/round 生命周期；AiRuntimeState、待提交 operation、等待、预算扫描。退出/实例/轮次变化清理 |
| AiPlanner | 只消费纯值；生成 TacticalIntent，经原 TacticalActions.submit → RuleResolver/ExecutionRequest → PLAN/executor |
| NativeMovementOpportunities | .84 GoalUtils/RandomPos/路径终点及稳定性证据；只产生移动提案，不调用 moveTo |

common 新增 `GateDecision`（ALLOW/HOLD/DENY、稳定 reason、可选诊断 Evidence）、`AiDefinition`、`AiAffordance`、冻结版本化 `AiAbilitySemantics`、`PerceptionSnapshot`、`AiRuntimeState`、`AiDecisionContext`、`AiPlanner`。现有 Actor/Ability/Resolution 模型没有另建副本。GateDecision 没有 executor、lease 接口；任何执行入口不接受它作为授权参数。

PlayerMoveLease/MobMoveLease 保留各自驱动与结算；EffectPermit 仍限定因果子效果；environment step 仍限定环境执行窗口；ExitAuthorizations 保留实时退出所有权；ActiveBodyControl 只持有调用栈内 USE_STEP。没有 GenericPermit。

## AI 语义与支持范围

Zombie family 既有候选/正式玩家偏好、精确 Enderman 的 committed/hostile 偏好、Ground 非敌对移动迁入 AiDefinition 与共享 planner。Zombie family 的原匹配范围保留，但具体攻击仍须既有 binding/resolver/native adapter 支持。未知地面 Mob 显式 GENERIC_FALLBACK（只移动提案），非地面 UNKNOWN 为 UNSUPPORTED；STRUCTURAL_CANDIDATE 不能通过 planner 取得支持。显式 provider 以 ActorDefinition id/version 注册，重复拒绝，启动冻结。无 Goal/Brain 试跑、反射或 turn-time species dispatch。

LOS/current-target/current distance/native canAttack/区域内可见对象仍是 runtime hot observation，因为会随世界改变；hostility 单独从 CombatEngine 读取。common perception 不含实体引用。vanilla current-target 不写 hostility。AiAffordance 只提供 definition-level 意义，`available` 与真实 AbilityBinding 分别检查，提交再次 resolver 复验。

每次 Capture 创建一次 ActorSnapshot/source-valid bindings，所有候选目标复用。跨 tick 扫描保留原实例、会话版本、位置、当前目标、actor facts、来源证据和 actor-state revision 失效条件；没有增加可写 authority cache。目标动态变化仍需执行时复验，不把扫描结果当许可。80目标测试候选工作量由重复捕获的 >1024 降为103；夹具用已有工作预算显式耗尽，保留跨tick续扫断言并检查总量上限。

2026-09-30 后续增量已接入精确 Skeleton 的标准组效果／装备普通弓箭、家族注册和共享远程决策，见[Mob 标准化记录](neoforge-26.1-mob-standardization.md)。Guardian/特殊 Boss 能力或任意第三方 Goal/Brain 仍未启用；Ground fallback 不授予工作、繁殖、传送、爆炸、攻击。无 hot reload，也不声明完整原生 AI 翻译或 AI 记忆跨重启恢复。

## Native seam 与固定源码

读取 `targets/neoforge-26.1/build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`，未用近邻版本替代。保持原注入签名和取消点；实际 GameTest 启动验证服务端注入。

| seam | 保留的 native responsibility |
|---|---|
| Mob.serverAiStep（final） | sensing/外层维护保留；两个 GoalSelector 分支、customServerAiStep、navigation、move/look/jump 分别 skip |
| LivingEntity.aiStep / updatingUsingItem | applyInput 后清主动输入；packet driver 时不重复 travel；普通外力/身体继续；使用 scope 不跨调用 |
| PathNavigation.followThePath | 原版写 waypoint tolerance 后，仅当前移动 authority 调整最后节点容差 |
| Creeper.tick / EnderMan.teleport(DDD) | 仅 active fuse/teleport 边界门控；Creeper super.tick 保留 |
| SnowGolem.aiStep / WitherBoss.aiStep / Dragon phase/direct skills | 拦主动世界修改、追逐和技能；不取消整个身体 |
| Level.tickBlockEntities | onLoad、ticker 注册/删除仍由原版维护，只 skip ticker.tick |
| ServerLevel.runBlockEvents | HOLD 放回原 blockEventsToReschedule；保留精确 pot/sign 维护例外 |
| ServerLevel.tickChunk | random block/fluid callback skip，不建 replay token |
| Hopper/LilyPad | 当前源/目标位置门控，保留原返回/接触路径，不重演历史接触 |
| ServerGamePacketListenerImpl | 保留 ensureRunningOnSameThread、malformed packet 原版处理、位置纠正、预测 ack/库存 resync；不再解释 phase/member/lease |

`EnvironmentProcesses` 只分类及既有环境时间采样；`allowed()` 删除。DIRECT/PASS_THROUGH 不继承 ENVIRONMENT HOLD。RegionalScheduledTicks 仍独占 held tick，policy 不接管队列。

## Prepare / reconcile 的边界及保留查询

`reconcileMobMovementStep` 是 lease-owner 执行准备：验证 active lease、revoke、出界 close、走廊/预算检查、authorizedCost 写入全部留在这里。它只由执行准备调用，不由纯查询调用。`isEntitySimulationPaused` 只 capture + SimulationPolicy；普通 living 返回 ALLOW。

投射物的 domain 入场、pending contact 结算、隔离与遍历校验继续在原 `prepareEntitySimulationStep` / `arrowSimulationPaused` 执行边界。最终已逐分支把环境窗口调度交给纯SimulationPolicy；部分boolean仍表示本次接触已处理，尚未完全转换成typed preparation result。自动审批拒绝整体改写后，改用小范围等价提取；最终投射物GameTest通过，但整批导航失败，不能冒称全部运行验收通过。

执行准备缓存仍有 server tick、engine revision、environment encounter，补充真实 environment step ID 和 actor instance；隔离状态在命中时复验。不新增虚构 membershipRevision。缓存仅用于防同一执行边界重复副作用，不作为跨tick许可。

静态搜索 production 中以下为0：`VanillaInputPolicy.controlled`、`ActiveBodyControl.controlled`、`dndturn$controlled`、`EnvironmentProcesses.allowed`、`Strategy.matches(Mob)`/`resolve(Mob)`。Mixin 对 region、move lease、state.phase/current、recoveryPending、isFrozen 的直接查询为0（`BrushRay.current` 是光线调用作用域，不是回合查询）。

仍保留的直接查询逐项：

- AuthorityProjection：唯一 native seam facts capture，读取 owner，不再自行决策。
- ServerCombatService：admission/entity capture、lease reconciliation、投射物执行边界与环境 owner；其投射物 preparation boolean 余项见上段。
- TacticalActions：容器 permit、选择/提交入口 readiness、已接受 PLAN 内的移动 lease 收尾；属于执行/permit owner。
- RegionalScheduledTicks.RegionAccess：held 队列 owner 读取空间暂停事实，再交 SimulationPolicy；不是 Mixin 解释 Encounter。
- EnvironmentExplosion.onDetonate/allows：唯一调用栈 frame owner 复验 source/target step 及真实影响范围；保留 typed authority，不包装成 GenericPermit。
- MinecraftCombatRuntime：既有 body/entity 生命周期入口委托 owner 的纯 SimulationPolicy 查询；原 block/gameplay boolean helper 删除。
- presentation：BodyState 消费 actor control decision；ChestPresentation 消费 block projection 的展示需求；clientEntityPaused 的整个环境阶段展示窗和实际 tick 内 step 窗不同，显式交 SimulationPolicy.presentationDuringEnvironment，展示不授予执行许可。

## 实际验证

命令均在对应 target 独立根执行，`--max-workers=2`：

- 26.1 `gradlew.bat :common:test`：最终 XML 67 tests，0 failures/errors/skipped；新增6项纯值AI/Gate测试。target纯策略 `verifyControlPolicies` 验证确定性、旧decision不改变新evaluation、movement/use/autonomy隔离、DIRECT与环境区别；随build执行。
- 26.1 `gradlew.bat clean build`：最终 `build/ai-gate-clean-final.log`，16任务全部执行，成功；common Java17与target Java25、GameTest/UI源码集、资源产物构建。
- 26.1 `gradlew.bat runGameTestServer`：初批50/51，discovery_cursor旧工作量前提失败；修正夹具预算前提后两批51/51。最终clean后一次旧导航夹具只轮转一人导致非玩家攻击并崩溃；有界轮转并断言前置条件后，下一批50/51，已有 environment_boundary_time 漏斗间歇失败。**同源码、同断言再次运行 `build/ai-gate-platform-repeat.log` 为51/51通过**。失败日志分别在 `build/ai-gate-platform-final.log`、`build/ai-gate-platform-recheck.log`；不据此关闭原环境间歇缺口。
- 最终51项包含 BodyControlChecks、regionalEntityGate、scheduledTickHold、prototypeEnvironmentLoop、prototypeZombieNavigation、mobUnknownRevokesLease、movementLeaseExit、inventory swap/input correction、block entity/event/random/hopper。增强原场景断言，没有删除或放宽行为断言。
- **最新批次覆盖前述全通过结论**：投射物调度判断逐项接入纯policy后，`gradlew.bat build runGameTestServer --max-workers=2` 的build/typed policy检查通过，但 `build/ai-gate-owner-boundary.log` 为50/51，`prototype_zombie_navigation` tick150未实际移动/扣费（phase=CANDIDATE，navDone=true，leased=false），整体退出1。未删除/放宽断言，根因尚未确定，不能以之前51/51覆盖此结果。最新源码的整体验收未完成。
- JDK21.0.11：forge-1.20.1、fabric-1.20.1、neoforge-1.21.1 各自 `gradlew.bat build` 成功，日志在 `build/ai-gate-*-compat.log`。未启用这些 target 的 AI/Gate 功能。
- `git diff --check` 无 whitespace error（已有换行转换 warning）；common新增模型平台 import 搜索0。

没有执行真实键鼠/双客户端、真实重启/崩溃窗口、第三方 explicit provider 闭环或性能 profiling。GameTest 内嵌输入检查不是实际客户端输入验收。整项仍受投射物 preparation typed result 余项及已有环境间歇问题限制，活动登记见[未完成既定目标](../02_GAPS_AND_CONFLICTS.md)。
