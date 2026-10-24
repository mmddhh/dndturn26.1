# NeoForge 26.1 Tactical Effect 实施与验证

<a id="current-snapshot-recheck"></a>
## 当前快照重新验收（2026-09-30 15:37–15:42，Asia/Shanghai）

本节是收到重新评估后实际执行的结果，不复用下方历史通过结论。本轮未修改生产代码、测试断言、依赖或玩法；只校正 checklist 并保存当前源码的验收证据。环境实际具备 JDK25.0.3、JDK21.0.11 和可用 Gradle 分发；首次沙箱执行在下载分发时权限失败，取消审批后由用户切换为无限制环境，再执行成功。

所有命令在各 target 独立根执行，附带 `--offline --console plain --no-daemon`。本地完整证据目录为 [current-acceptance-20260930](../../targets/neoforge-26.1/run/current-acceptance-20260930/)；其中 `source-sha256.txt` 标识源码／构建输入，`common-test-results/` 保留 XML。日志是本地产物，不提交 build 输出。

| 当前命令／场景 | 实际结果与证据文件（相对上述目录） |
| --- | --- |
| JDK25，26.1 `gradlew.bat clean build :common:test runGameTestServer` | PASS，19任务实际执行；common 83/83，0失败／错误／跳过；GameTest55/55。`verification-current-acceptance.log`、`test-summary.json` |
| JDK21，Forge1.20.1、Fabric1.20.1、NeoForge1.21.1 各 `gradlew.bat compileJava` | 全部PASS；`<target>-compile.log`。本轮只证明编译兼容，不冒充三个target重新 clean build 或运行验收 |
| 两个独立JVM，`DNDTURN_RESTART_TEST=dndturn:creeper_effect`，`runRestartGameTestServer -PrestartTestDirectory=run/current-creeper-20260930-1539` | 写入／加载各1/1；`verification-current-creeper-write.log`、`verification-current-creeper-verify.log`。加载标记明确输出 pending=PENDING、started=UNKNOWN、completed=COMPLETED，并核对云来源／源实体移除 |
| 两个独立JVM，`DNDTURN_RESTART_TEST=dndturn:effect_reaction_restart`，`runRestartGameTestServer -PrestartTestDirectory=run/current-reaction-20260930-1541` | 写入／加载各1/1；`verification-current-reaction-write.log`、`verification-current-reaction-verify.log`，含 WRITE_READY / VERIFY_PASS |
| 构建内独立 checks 与发布jar检查 | ControlPolicy、UI、移动预览及诊断 checks 通过；jar 中 EffectInvocation major61，未包含 gametest、CreeperEffectChecks 或 test_instance |

恢复复验限定为正常保存／关服和新JVM读取；STARTED 为夹具注入，pending检查证明状态保留，不声称本次已经重新执行 pending 爆炸。真实崩溃及跨文件保存顺序、双客户端、云完整寿命／跨域合并、第三方扩展与性能未执行。单批55/55不关闭历史间歇风险。

### 当前静态审计及后续边界

按 checklist 第26节对 common/main 与26.1/main执行固定字符串搜索，逐行结果为 `static-audit.txt`，并检查实际注册调用和事件派发。

| 搜索／责任边界 | 本轮观察 |
| --- | --- |
| `controlled(` | 1：BuiltinPresentation 消费展示输入并更新独立 WalkAnimationState，不授予模拟权 |
| `isEntityInsidePausedRegion(` | 11：AuthorityProjection采样、ServerCombatService协调，以及 EnvironmentExplosion／CreeperClouds 的效果过滤；后两者仍需统一策略 |
| `hasMobMoveLease(`/`hasPlayerMoveLease(` | 3/4：owner查询、投影及TacticalActions执行边界复核 |
| `state.phase(`/`state.current(`/`recoveryPending` | 22/23/18：规则恢复、值捕获、准入／生命周期协调、网络及客户端交互；未据搜索结果宣称全量Mixin授权审计完成 |
| `EnvironmentProcesses.allowed` / `Strategy.matches(` | 0/0；零命中不是全部变体覆盖证明 |
| `instanceof Zombie` / `instanceof EnderMan` | 2/0；另有正式精确Creeper provider，不表示未知Mob均受支持 |
| `registerContinuous` / `registerActorEffect` | 1/2：前者只有API声明；后者包含Creeper实际注册。`ConditionRegistry.register`/`ActorActivations.register`字面0命中，实际通过实例及TacticalCapabilities注册，不以字面搜索代替调用链检查 |
| `new ActorStates.Apply` / `new ActorStates.Remove` | 2/1：Creeper入场／付费充能／退出；Effect已进入生产链 |
| 事件与长期状态 | HIT、DAMAGED、TURN_START、TURN_END有生产派发，但各event专项使用者／验收仍缺；ABILITY_USED／ABILITY_RESOLVED未发现生产派发。Learn／Forget／Prepare无生产gameplay writer |

EffectControlPolicy目前仍只裁决无战术上下文的伤害；TNT过滤、云当前domain／环境step组合尚未统一。静态审计已执行且发现活动缺口，不等于Gate收口通过。下一项实现优先收尾这些Effect/world-effect边界，不能只移动或重命名原布尔组合来关闭缺口。活动剩余统一见[DM-02](../02_GAPS_AND_CONFLICTS.md#tactical-effect-framework)。

## 当前交付（2026-09-30）

固定依赖 NeoForge 26.1.2.84，实际 patched Minecraft 26.1.2；未升级依赖。26.1 使用 JDK25.0.3，common Java17；另外三个 target 使用 JDK21.0.11。以下当前证据覆盖后文历史批次的格式号、测试数量及“尚无生产注册”等描述。

按用户明确范围完成 Creeper + 既有原生药效。`RuleEmission` 的世界调用走共享 resolver／CombatEngine，outbox 与纯状态命令回执共同提交；当前同步 executor 返回类型化观察。Creeper Charge 花 ACTION 加一层，Fuse3 仅记录待决调用，自身回合末才执行爆炸。移除／到期／降阈值按实例转换前 grant 派发；native rank=amplifier+1，EXPLICIT remaining=0。产物不新增流血／破甲／护盾玩法。

schema/protocol 版本已移除。当前必需字段、不变量、定义 ID／语义版本仍校验；未知定义拒绝，不迁移旧存档。NeoForge `PayloadRegistrar` 要求非空版本字符串，所有注册统一固定 `dndturn`，不再维护递增协议号；这不承诺任意不同构建可互联。服务器世代、操作身份、选择修订及能力语义版本保留。

### 固定版本入口审计

依据本地 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar` 与实际解析的 `neoforge-26.1.2.84-sources.jar`，不是近邻版本资料。

| 接入点 | 时序、职责及范围 |
| --- | --- |
| `Creeper.explodeCreeper()` private，新增 Invoker；swell/oldSwell/maxSwell/explosionRadius Accessor | 服务器线程同步；原方法标记死亡、调用爆炸、生成云、触发死亡药效并 discard。仅精确 Creeper 注册，检查半径及药效；不复制原版爆炸算法。原 `CreeperActiveProcessMixin` 继续限制自主 swell，显式触发不放开 AI |
| `ServerExplosion.hurtEntities(List<BlockPos>)` → `ExplosionEvent.Detonate` | 伤害／位移／方块回调前过滤实体及方块。限制已加载域、成员、普通 Block 精确类型、256实体／8192方块；被拒范围计数并返回 PARTIAL。不是对任意第三方监听器的沙箱 |
| 同方法虚调用 `Entity.hurtServer(ServerLevel, DamageSource, float)` | 新增 required Redirect；无当前 Creeper 上下文原样调用，有上下文则绑定独立 DAMAGE 子操作和一次许可。覆盖原生早退／免疫／取消／吸收，不清无敌帧、不重复战术伤害计算。入口、事后 health/absorption、已确认/未知分别留证 |
| `EntityJoinLevelEvent` 与现有成功插入观察入口 | 当前爆炸范围仅允许精确 ItemEntity/AreaEffectCloud，插入前核对 AABB、加载和域，插入成功后记录 UUID/type；生成云绑定 operation/actor/domain |
| `AreaEffectCloud.serverTick(ServerLevel)` 中 `Level.getEntitiesOfClass(Class,AABB)` | required ModifyExpressionValue 只过滤候选受体；保留原生 wait/duration/radius/tick 及施药。精确 cloud 和支持药效通过 `potionContents` Accessor 验证，来源须关联已完成输出的生成 UUID；未知执行云隔离，不加超时。活动域要求同环境步，源域结束后仍防止写入其他暂停区域 |
| `ParticipantEffects.capture/release` → 冻结的 `ActorLifecycleAdapters` | 精确类型 handoff；入场 swell 按比例截断为层数，退出1/2层转1/3、2/3最大 fuse，3层转max-1；保留原生 ignition。未持久化平台对象 |

新增 mixin 在本次 GameTest 实际加载／调用。类型边界及反例验证见下；编译成功本身不作为注入证据。

### 最终源码实际验证

所有 Gradle 命令均带 `--offline --console plain --no-daemon`，工作目录为各 target 独立根。日志归入对应 target `run/verification-20260930/effect-closeout/`，保持中间失败记录。

| 命令／场景 | 结果及日志 |
| --- | --- |
| 26.1 `gradlew.bat clean build :common:test runGameTestServer` | PASS；common83，0失败/错误/跳过；GameTest55/55。`verification-effect-complete.log` |
| Creeper生产场景 | 正常 paid PLAN 第三次充能、一次ACTION、重试去重、三层不提前爆炸、AI提议／结束、原生爆炸／吸收、免疫与取消零损失、非成员伤害／位移不变、云来源和暂停时不施药；含于55项 |
| 自有生命周期／波次 | common及真实 ActorStateService：自有REMOVED/EXPIRED/CROSS_DOWN仍激活；整波冲突、深度限制、重复输出、当前结构恢复；含于83/55项 |
| 两个独立 JVM，`runRestartGameTestServer -PrestartTestDirectory=run/creeper-final-20260930`，`DNDTURN_RESTART_TEST=dndturn:creeper_effect` | 两次各1/1；`verification-creeper-final-write.log` / `verification-creeper-final-verify.log`。真实爆炸与云正常保存；新世代 pending=PENDING、started=UNKNOWN、completed=COMPLETED，源未复活、云来源可核对 |
| 两个独立 JVM，`runRestartGameTestServer -PrestartTestDirectory=run/effect-final-20260930`，`DNDTURN_RESTART_TEST=dndturn:effect_reaction_restart` | 两次各1/1；`verification-effect-final-write.log` / `verification-effect-final-verify.log`。纯事件正常保存／加载、完成与重复派发去重 |
| Forge1.20.1、Fabric1.20.1、NeoForge1.21.1 各 `gradlew.bat clean build` | 全部PASS，各target `verification-effect-compat.log`；未扩大功能范围 |
| 发布 jar 静态检查 | `DNDTurn-neoforge-26.1-1.0.0-SNAPSHOT.jar` common EffectInvocation major61；无 gametest类、CreeperEffectChecks或test_instance资源 |

两 JVM 测试使用正常关服。STARTED 是明确的测试故障窗口注入，不能据此声称真实崩溃瞬间世界与检查点原子保存；不自动重放 UNKNOWN，也不承诺任意保存顺序恰好一次。尚未执行真实双客户端、硬崩溃矩阵、云完整生命周期／跨域合并、独立第三方适配模块或性能 profiling。

### 本次失败及修正

- `verification-effect-creeper.log`：测试设置吸收值前没有提高 MAX_ABSORPTION，值被原生钳为0；改为显式夹具属性后保留伤害／吸收断言。另发现持久化 grant 内嵌定义含 Class，已用 ID／语义版本 Codec 解决，不序列化执行器。
- `verification-effect-platform.log`：旧身体测试仍期望 Creeper 无策略；按新增支持更新，保留未知 SnowGolem 不受支持断言。neutral夹具增加原生实体加载等待，未改生产移动规则。
- `verification-effect-acceptance.log`：54/55，merge_discovery_expansion 在实体存储未完成加载时生成，当前实例断言失败。布置前按已强制加载的核心位置调用 `waitForEntities`，保留区段就绪及三会话合并断言。随后最终 clean全套55/55；不据单批通过关闭全部历史间歇风险。

活动剩余范围统一见 [DM-02](../02_GAPS_AND_CONFLICTS.md#tactical-effect-framework)。以下保留既有历史定位／批次，既有路径仍保持，不以历史80/54或schema4证明本次83/55。

---

## 历史批次记录（以下不是当前格式或本次验收）

2026-09-30。target 参数与 metadata 下界为 Minecraft 26.1；固定 NeoForge 26.1.2.84 实际解析／运行日志为 Minecraft 26.1.2。本文证据针对这一实际产物，不证明声明范围内其他补丁版本兼容。Gradle JVM JDK25.0.3，common Java17。本批未增加或更改 Minecraft / loader 注入点、未升级依赖。

**本轮最终验证**：26.1独立clean后build/common80/GameTest54通过；种子26101、26102、26103各五个连续批次全部54/54。日志为 `run/verification-20260930/final-clean.log`、`final-build.log`、`acceptance-seed-<seed>-batch-<1..5>.log`；逐批包含种子、实际场地起点和结果。Java17字节码／测试隔离记录在 `final-artifacts.txt`。schema4两进程纯事件恢复及其他三个target独立clean build通过，详情如下。没有发布；整个架构清单尚未完成，正式玩法、真实客户端和原生副作用恢复不能由这些回归证据替代。

## 后续增量与最新证据（2026-09-30）

下方原批次记录保留作失败追踪；本节覆盖其中已过时的实现描述，不将历史通过算作当前验收。

- 实例 `revision` 与 `grantRevision` 已分离。期限变化推进实例修订但保留原授予证据；来源、rank 或层数改变重新生成授予代次。
- 不可变 `EffectSnapshot` 同时投影 TacticalEffect 与当前原版 MobEffect。后者的 rank 等于零基 amplifier、stacks 固定为一、期限为原版 tick（无限期为 -1）；不复制原版所有权或隐藏药效链。
- `EffectTransition` 保存实例 before/after 修订及标准变更事件；四种阈值边沿有测试。`EffectWave` 冻结输入、稳定排序、合并可交换加层；移除冲突、过量消费拒绝整波。每根事件深度16、事件256、变更512，已提交事实不回滚。
- 服务端纯 Actor 反应以迭代队列派发。状态回执、原事件 inbox 及其 PENDING/COMPLETED/FAULTED 终态同存于 actor schema 4；schema 1/2/3 明确拒绝、保留原文。恢复已提交波次读取回执，不再次运行其回调。此账本不是原生执行器的 STARTED/OBSERVED 账本。
- 固定源码核对：`LivingEntity.getActiveEffects()`、`MobEffectInstance` 的 amplifier/duration/ambient/visible；`PersistentEntitySectionManager.Callback.onMove()` → `stopTracking()` → `ServerLevel.EntityCallbacks.onTrackingEnd()`。后者说明导航夹具提前跨入未开放实体 tick 的区段会触发退场，未证明生产 Navigation 本身故障。
- 导航／合并夹具现在等待实际 `isPositionEntityTicking`，导航还在布置后断言成员保留。未删除移动、扣费或合并断言。种子设施只在 gameTest 源集控制 world、initiative、attack、living RNG；实体 UUID 仍唯一。

| 本轮命令／环境 | 实际结果与证据 |
| --- | --- |
| JDK25 / `build :common:test runGameTestServer --offline --console plain --no-daemon`，`DNDTURN_TEST_SEED=26101` | common 79项、GameTest 54项通过；`build/verification/effect-inbox-platform.log` |
| 两次独立 JVM：`runRestartGameTestServer -PrestartTestDirectory=run/effect-restart-20260930-1305 --offline --console plain --no-daemon`，环境 `DNDTURN_RESTART_TEST=dndturn:effect_reaction_restart` | 第一进程保存待派发事件并正常关服；第二进程加载原实体、核验新世代、完成加层和阈值、重复事件不重放；各1项通过。日志 `effect-restart-write-ready.log`、`effect-restart-verify.log`；首次夹具等待失败保留在 `effect-restart-write.log` |

上述两进程证据来自 schema 3。随后修复恢复预算：命令保存 `proposedMutations`，回执恢复按合并前计数继续限额，不能按合并后的较小变更列表放宽预算；actor 格式升至4并明确拒绝1/2/3。新增 common 与平台 Codec 测试已通过，新格式两进程复验另行记录。

两进程证据限定为纯 Actor 反应的正常保存恢复；设施重建 GameTest level settings，不覆盖专服 level.dat、崩溃窗口或伤害／爆炸等原生副作用。正式 DODGE／流血／充能、付费触发能力、公开 UI 与全部 AI／移动／环境扩展仍未完成。

### 连续回归发现与修正

首次三个种子批次在第13批停止：26101、26102各5批通过；26103第3批日光传感器未在有效步20更新。诊断重现“初始输出仍保留、外层 tick 允许”，随后清理前记录显示多个 `ENTITY_TICKING` 区块的实体存储仍未加载。固定 .84 `LevelChunk.BoundTickingBlockEntity.tick → isTicking` 除距离范围外，还要求 `FullChunkStatus.BLOCK_TICKING` 与 `ServerLevel.areEntitiesLoaded`。`ServerCombatService.regionChunksLoaded` 现以 `getChunkNow` 无加载查询补齐两项，防止环境时钟在原生内部早退时照常消费。此查询同时供恢复／合并协调使用；不修改全球日程或强加载区块。

环境夹具改为完整加载的嵌入玩家并等待原生准备条件；400 tick 上限包含异步加载等待，原有效20步采样、30步结束、暂停期不推进、跨界漏斗转移断言保留。`environment-fixture-ready.log` 54项通过。失败与诊断全部保留，未将每次重跑通过当作关闭依据。

诊断期间再次出现历史 `arrow_cross_session_miss`：唯一箭回执为MISS／零伤害，但目标最终生命变化。固定 .84 `Mob.aiStep → burnUndead` 不受 NoAI 阻止；露天静止僵尸夹具已增加原生顶棚并检查不见天空，不添加护甲／免疫、不改箭伤害规则，保留未命中血量断言。

最新独立 clean 后 `build :common:test runGameTestServer` 已通过：common80项、GameTest54项；common class major61（Java17），发布 jar 不含 gameTest 类或测试资源。原失败日志保存在 target 的 `run/verification-20260930/prior/`，最新命令在 `run/verification-20260930/`。

**中间连续批次未通过**：修正后26101五批均54/54；26102第一批53/54，`arrow_cross_session_miss` 的箭停在目标碰撞边界，`tickCount=1`、未移除、目标原生查询可见，但200 tick内两会话未因果合并。该批未记录合并加载拒绝日志；不能直接归因于本次加载检查，也不能据更早54/54关闭。现场见 `final-seed-26102-batch-1.log`，原断言保留。

随后追加待合并计划／投射物域／隔离诊断，五次诊断均54/54，未再捕获停滞，不能据此关闭。核对调度后，原200 tick测试期限不足以容纳并发会话的最坏合法等待：一组最多九个用例、各两个域、每级只推进一个环境步，因果合并可等待下一环境窗口。夹具现按 `2 × 9 × 2 × 30 + 120 = 1200` tick驱动并断言（外层1220），保留合并、一次伤害证据和命中／未命中生命断言；不延长生产投射物寿命、不提高环境预算。`arrow-scheduler-budget.log` 的 build/common80/GameTest54通过，三个种子各五批重新执行，尚未据时间上限推断生产合并链已完整验收。

固定种子覆盖 initiative、attack、world随机源与living随机源；世界生成起点和UUID仍有变化，各次场地起点由GameTestServer日志记录。这是有记录的重复场景覆盖，不是逐指令确定性重放。

后续重复批次还发现两项异步夹具问题，均保留失败记录：`scheduler-seed-26103-batch-1.log`、`environment-margin.log` 显示400个加速测试tick仍不能证明实体I/O完成；`managed-seed-26101-batch-4.log` 中 `discovery_cursor` 读取角色时不再是当前可见实例。固定源码提供 `ServerLevel.waitForEntities`，通过 `MinecraftServer.managedBlock` 与 `PersistentEntitySectionManager.processPendingLoads` 完成已经请求的核心区块加载。远端环境、角色周期、身体控制和发现游标夹具现使用这一原生准备入口；发现游标另检查81个实体均为当前实例。没有手动调用实体／方块实体tick，没有修改生产I/O、测试中的环境步数或发现工作量断言。

日光夹具仅等待角色核心区块，不把强制加载的外圈维护范围当作模拟域；生产调度仍逐项核验真正区域区块。超时在清理前明确失败并记录状态，防止清空集合后等待条件空集通过。最新 `final-clean.log`、`final-build.log` 记录独立clean后的build/common80/GameTest54通过；最终源码 `acceptance-seed-*` 三种子各五批全部54/54。此结论限定为现有54个场景的重复回归；历史失败记录保留，未用增加重试次数或删除断言关闭。

schema4独立世界 `run/effect-restart-schema4-20260930` 的两个 JVM 已分别通过1/1：`schema4-restart-write.log` 与 `schema4-restart-verify.log` 包含写入、正常保存关闭、新世代加载及重复事件不重放标记。合并前计数由 common 测试及生产 ActorSavedData 往返检查；两进程场景仅覆盖尚未开始的纯事件派发，不声称覆盖波次中间故障或原生副作用。

最终其他target均以 JDK21 执行独立 `clean build --offline --console plain --no-daemon` 通过：`compat-forge-1.20.1.log`、`compat-fabric-1.20.1.log`、`compat-neoforge-1.21.1.log`。未扩大这些target的玩法。没有执行发布、真实双客户端键鼠链、原生能力故障窗口、全量兼容provider或性能profiling；这些条目仍待验收。

### 静态搜索记录

按清单第26节逐字搜索 common/main 与26.1/main，完整逐行结果保存在 `run/verification-20260930/static-audit.txt`。零命中不代表所有拼写变体已审计：

| 搜索组 | 命中及职责 |
| --- | --- |
| `controlled(` | 1：BuiltinPresentation 消费表现输入、更新独立 WalkAnimationState，不授予模拟权 |
| `isEntityInsidePausedRegion(` | 8：AuthorityProjection 捕获事实、ServerCombatService 区域／模拟协调、EnvironmentExplosion 的 TNT 作用域过滤；爆炸策略尚未全部类型化 |
| `hasMobMoveLease(`/`hasPlayerMoveLease(` | 3/4：Lease owner 查询、AuthorityProjection、TacticalActions 接近步骤边界复核 |
| `state.phase(`/`state.current(` | 22/23：CombatEngine 恢复、EncounterInput 值捕获、服务端协调／计划／调查、网络编码和客户端输入／菜单事实消费 |
| `recoveryPending` | 18：服务端恢复所有者、准入策略、恢复与合并协调、投影可交互性 |
| `EnvironmentProcesses.allowed` / `Strategy.matches(` | 0/0 |
| `instanceof Zombie` / `instanceof EnderMan` | 2/0：AiDefinitions 家族捕获、MeleeAdapters 显式注册；仍需完成版本化家族 provider 扩展 |
| `registerContinuous` / `registerActorEffect` | 各1且仅注册API声明；main 中 `new ActorStates.Apply/Remove` 为0。不能据此宣称正式 Effect 玩法已接通 |

全量 Mixin 授权收口、世界效果／爆炸类型化与独立兼容模块仍保持待完成。

## 原批次已接入范围（历史）

`ConditionDefinition` 保留原源码名称，语义明确为 DNDTurn-owned Tactical Effect 定义；原生 MobEffect 仍由 Minecraft 持有。本批扩展现有 `ActorStates`，没有创建第二个状态所有者。

- 定义新增 tags、实例归并策略、溢出 REJECT/CLAMP、刷新 KEEP/REPLACE/MAXIMUM；时钟、应用模式、最大层数及死亡／持久策略保留。
- 实例新增独立 rank、来源 actor／ability。`EffectInstanceKey` 在 actor 局部按定义生成：单目标、每来源 actor、每来源 actor+ability、每来源 operation 的独立应用。调用方实例 UUID 不再决定叠层；合并保留第一次实例 UUID。相同 key 的定义版本冲突拒绝，非 REPLACE 的 rank 冲突拒绝。
- `AddStacks`、`ConsumeStacks`、`RefreshDuration` 经同一候选 reducer、预期 actor revision、命令去重及原服务端快照校验后提交。消耗至零删除实例；过量消耗和 REJECT 溢出不留下部分资源变更或回执。
- 修正区分 CONSTANT/PER_STACK；条件 grant 按最低 rank 与层数过滤，消耗后下一快照撤销授予。未修改现有动作费用或原生属性。
- 时钟递减与持续时间刷新不改变授予代次，层数变更改变代次。当前 `Condition.revision` 仍是授予证据，尚不是完整事件 before/after revision。
- 旧 Java 构造器保留，默认 rank 1、独立 application、PER_STACK 和既有刷新行为。未声明来源的旧构造器不能用于需要 actor／ability 的归并策略。

## 保存边界

`ActorSavedData` 增加三种命令编码，actor checkpoint 独立升级为 schema 2。schema 1 明确拒绝并保留原文，不静默猜测缺失的归并、rank 或刷新语义。网络和 Combat checkpoint 未变。本批没有旧档迁移，也没有触发账本。

新增 `TacticalEffectChecks` 仅在 gameTest 源集注册测试定义，通过真实 `ActorStateService.command` 和 `NativeSnapshots` 验证属性／授予／消耗／到期；同时验证生产 ActorSavedData 编解码与历史命令恢复去重。该测试不是两进程重启或跨文件崩溃恢复验收，测试注册不算正式内置 gameplay 注册。

## 实际验证

| 命令／环境 | 本批结果 |
| --- | --- |
| 26.1 / JDK25：独立 `gradlew.bat clean --offline --console plain --no-daemon` | 通过 |
| 26.1 / JDK25：`gradlew.bat build :common:test runGameTestServer --offline --console plain --no-daemon` 首批 | 通过；common 72/72，GameTest 52/52，包括 `tactical_effect_state`；UI、控制策略、移动预览和诊断检查通过 |
| 同命令，拒绝重复条件授予后的最终源码 | build、common 73/73 通过；GameTest 51/52，`tactical_effect_state` 通过，`prototype_zombie_navigation` 失败，整体命令退出 1 |
| Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1 / JDK21：各自 `gradlew.bat compileJava --offline --console plain --no-daemon` | 三个 target 均通过；没有开放新玩法 |

26.1 首批／最终日志分别为 `targets/neoforge-26.1/build/verification/tactical-effect.log` 和 `tactical-effect-final.log`。最终导航失败在 tick150：初始与当前位置相同、`navDone=true`、`leased=false`、phase=CANDIDATE，结果仅有 `hit with zero tactical damage` 和 `turn ended`；没有实际移动及扣费证据。未定位根因，不改断言，也不以首批通过关闭问题。首次沙箱执行无法访问 Gradle 分发／缓存，批准沙箱外执行后完成上述验证。发布 jar 检查包含 common 新类型，未包含新增测试类、test_instance 或 test_environment。

## 剩余范围

Effect 事件、阈值、同波次反应、触发能力、持久触发账本、正式内置 Effect 注册、AI/界面投影及 Creeper 充能／爆炸尚未实现。用户已明确修订 Creeper 为每次消耗动作加一层，三层后该自身回合末结算爆炸；规则与清单已同步，不能将规则更新当作执行器已实现。全架构清单、真实客户端、真实重启及完整扩展覆盖均未验收，活动登记见[DM-02](../02_GAPS_AND_CONFLICTS.md#tactical-effect-framework)。
