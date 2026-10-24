# NeoForge 26.1 DND 数据模型重构证据

<a id="free-inspection"></a>
## 当前增量：免费调查（2026-09-29）

用户确认本轮先完成调查相关缺口，范围为战术界面内免费查看当前公开事实，不增加永久知识、不新增或迁移测试。下方“没有界面／调查默认没有可见事实”仍是此前批次记录，由本节说明当前增量。

- common 增加纯值 InspectionPolicy.Observer、字段注册／过滤和不可变 ActorViews.InspectionView。规则只读取声明的FactSlice；输出唯一、最多32项，缺失字段不披露。旧 Inspection 与可信 inspect API保留源码兼容。common仍为Java17。
- NeoForge的InspectionService由ServerCombatService持有，检查当前玩家连接、世代、双方同遭遇、同维度、当前已加载存活实体；他人须非不可见且有原版视线。环境参与者拒绝。每20服务器tick每UUID最多4次，服务器每tick最多32次处理；限流记录只保留近期值，不持有连接或实体引用。
- ActorStateService.inspect(observer,target,encounter)按冻结策略采样。名称／类型来自NativeFacts，回避／撤离来自Encounter只读成员。默认不捕获完整能力列表或写角色状态；扩展规则事实按需经现有快照入口读取。自定义能力／条件不自动披露。
- 新增Query/Reply二进制协议，固定UUID与状态，字符串／集合／总字节均有界，最大16KiB。客户端不能提交观察者或读取集。新载荷单独使用必需的inspection-1版本；原C2S24/S2C30及存档版本不变。缺少新必需载荷的旧端不兼容，不静默降级。
- ClientInspection独立持有请求、目标实例和过滤投影，不调用选择并查询。战术目标选择后点击“调查”，加载／不可用／限流／超时有中英文本，可刷新或关闭；无自动轮询。超时100客户端tick后仅允许显式刷新，旧响应不自动重试。关闭、死亡、换世界、退场、目标卸载／实例替换、世代或遭遇变化均清理；响应核验请求和当前连接／世界／玩家实例。面板明确为查询时快照，已有公开信息不因失去视线而自动刷新。

### 固定源码核对

仍为Minecraft26.1、NeoForge26.1.2.84；读取本地patched sources和实际NeoForge sources.jar，没有新增Mixin或升级依赖。

| 入口 | 线程、职责与边界 |
|---|---|
| LivingEntity.hasLineOfSight(Entity)及其四参数重载 | 服务端线程；同Level、眼部射线、最大128格，COLLIDER/Fluid.NONE，经Level.clip；只读，不取消原生调用。 |
| Entity.isInvisibleTo(Player) | 尊重原版旁观者／队伍可见隐身语义；调查另拒绝旁观请求者。读取基类不声明所有模组override安全。 |
| ServerChunkCache.getChunkNow(int,int) | 主线程即时取得当前FULL区块，不等待future；调查先检查射线XZ包围区块，并为BlockGetter.traverseBlocks端点微小扩展预留边界。缺失立即拒绝，不请求加载。hasChunk票据状态不足以代替此检查。 |
| Entity.getName()/Component.getString(int)、实体类型注册表 | 名称限256字符，类型以注册ID传输，由客户端本地化；不传交互式聊天组件或平台对象。 |
| TacticalActor及ServerLevel当前UUID查询 | 核验服务器线程、当前实体实例、存活；网络排入合法线程后重新检查玩家连接。 |
| NeoForge IPayloadContext、PayloadRegistrar | 已核对connection/player/enqueueWork及双向play注册签名；服务端仅回复请求者，客户端处理器只在客户端入口注册。 |

具体新增功能的真实输入和双客户端场景尚未运行；无新增测试意味着现有通过结果不能替代调查权限、无副作用及迟到响应的专项验收。

### 本轮实际验证

| 环境／命令 | 结果 |
|---|---|
| 26.1 / JDK25.0.3，独立clean后运行build :common:test runGameTestServer --offline --console plain --no-daemon | 首批构建、现有UI／诊断检查通过，GameTest51/51；日志build/verification/inspection-build.log。 |
| 同环境，按钮禁用／客户端生命周期修正后build :common:test | 通过，日志inspection-final-build.log。 |
| 同环境，getChunkNow加载预检修正后build :common:test runGameTestServer | 构建、UI／诊断检查及61项common测试通过；GameTest50/51，整体退出1，日志inspection-final.log。 |
| Forge1.20.1 / Fabric1.20.1 / NeoForge1.21.1，JDK21.0.11，各自clean build --offline --console plain --no-daemon | 三个target均通过；各自build/verification/inspection-build.log。没有开放这些target的调查入口。 |

最终失败为arrow_cross_session_miss（tick200）：结果中仅有一个ATTACK，damageTrace存在且为MISS、伤害0；结合原断言，未满足的是目标生命仍等于夹具初始值。尚未定位生命变化来源，不能据此归因于调查或排除已有并发世界行为。原断言／测试保持，DM-01E继续开放；首批全通过不关闭历史间歇问题。

静态检查：git diff --check通过，中英文15项调查文本可解析；四个发布jar均包含Java17字节码的InspectionPolicy，没有GameTest、test_instance/test_environment或兼容测试条目。新common类不引用Minecraft／loader／Mixin。首次沙箱编译因Gradle缓存／网络访问受限失败，授权使用本地缓存后完成上述命令。未新增测试、未执行真实客户端和双客户端、未发布。

## 当前增量：角色状态与规则边界（2026-09-29）

本节覆盖下方首次迁移记录中的旧接口与“尚无角色状态”描述。没有新增或迁移测试，没有新增法术、职业、玩家资源配额或界面。完整计划尚不能声明完成，活动剩余项仍以 [DM-01](../02_GAPS_AND_CONFLICTS.md#dnd-data-model) 为准。

- `ActorStates` 是角色状态的唯一写入口：纯候选 reducer、预期修订、操作负载去重、不可变回执。`ActorPersistentState` 持有学习授予、准备选择与显式创建的角色资源；`ActorRuntimeState` 持有条件实例。原生生命／库存、Encounter 的动作／反应／移动及先攻没有搬家。`StatKey` 与 `ResourceKey` 分离，资源余额不能作为条件修正统计量。
- `ActorStateService` 由服务器服务持有，在合法线程核验当前实体后接受受信任集成命令；不是新增 C2S 写状态入口。命令先试编译候选状态，检查定义、事实、修正溢出，再交由 owner 提交。持久授予证据绑定获取／准备修订，条件证据绑定条件代次；持续时间推进不冒充 grant 替换。
- `ActorSavedData` 独立 schema 1，保存纯值状态、命令／结果、逻辑时钟和隔离原因；字节与嵌套有界。非持久条件在恢复时移除并推进角色修订。坏数据保留原文，角色规则明确不可用，不用空状态覆盖。保存至多每 20 次服务保存检查一次，关闭时强制标记最新值；这仍不是角色、Encounter 和 Minecraft 世界的跨文件原子事务。
- `ActorDefinitions` 支持受限原生匹配与确定优先级；同优先级冲突拒绝，定义不能覆盖 native-owned facts。定义原生授予、学习／准备授予和条件授予进入实际快照发现；条件注册冻结并核验能力版本。缺失学习能力在法术书投影标记 `DEFINITION_UNAVAILABLE`，不删除持久来源。
- `ContinuousAbilities` 从有效 binding 派生修正与子授予，拒绝授予环且限制展开规模，不创建第二份条件状态。`ActorActivations` 将已确认命中／受伤、回合边界及获准模拟步转换为有界角色状态效果；空效果也留回执，失败隔离并保留先前已提交事实。当前触发／周期后端只执行角色纯状态差量，不提供新的原生世界效果许可或通用反应攻击。
- 条件先按声明时钟推进／到期，再分发该边界的触发；模拟时钟不离线追赶。死亡仅清除声明不保留的条件，长期学习／准备状态保留。`HIT` 当前表示原生伤害入口已接受，`DAMAGED` 表示观察到生命或吸收损失且目标仍存活；不是覆盖所有世界伤害或死亡反应的声明。
- `ReadContract` 区分 actor／target／world，规则只收到裁剪后的事实。`EncounterInput` 仅保留本次执行者、明确目标及必要阶段身份，移除完整会话 roster/history 的读取。维度、加载、domain 是独立事实，由 common 裁决；物品支持与复杂几何仍有原生复合事实，未宣称全部细分完成。
- `AttackResolution` 接通近战、发射与延迟命中，使用捕获的骰值和防御输入，复用唯一 `CombatRules` 公式。采样仍在行动接受后，延迟命中沿用发射证据。候选转正式阶段可能触发角色规则，因此攻击接受后、原生副作用前重新核验授予和防御。条件／持续能力对 AC 和减伤的修正也进入实际伤害裁决。
- `CharacterSheet`、`Spellbook`、`Inspection` 是只读投影 API，服务器提供调用入口；调查默认没有可见事实，没有新增客户端完整快照广播。Mob 决策缓存增加角色修订依赖。

### 固定版本接入增量

仍使用 Minecraft 26.1 / NeoForge 26.1.2.84，JDK 25.0.3，common Java 17。读取本地 patched sources 核对以下完整路径后接入：

| 接入点 | 位置、线程与语义 |
|---|---|
| `ServerLevel.tickNonPassenger(Entity)` | 已有 NeoForge `EntityTickEvent.Post` 在 Pre 未取消且 `entity.tick()` 返回后触发；只观察服务端 LivingEntity，排除玩家身体链。 |
| `ServerLevel.tickPassenger(Entity, Entity)` | `Entity.rideTick()` 调用返回后注入；原有 HEAD 取消与无效骑乘关系维护仍保留。无实际 rideTick 不记步，玩家单独处理。 |
| `ServerPlayer.doTick()` | `Player.tick()` 调用返回后注入；原有暂停 HEAD 取消保留，旁观者未加载分支未调用父 tick 时不记步。同一角色同服务器周期去重，全局冻结不额外推进。 |
| `CombatEngine` 入场／激活／`beginMemberTurn` | 值事件通知角色 owner；观察者不得重入 Encounter 写入，失败由角色隔离记录。网络重发、UI 查询和 `sync` 不推进回合条件。 |
| 现有确认死亡及近战／箭命中观察 | 确认后的 actor 状态事件，不把可取消 LivingDeathEvent 本身当作最终死亡；没有恢复世界副作用的旁路。 |

新协议 C2S 24 / S2C 30，common schema 15、target envelope 14；Actor checkpoint schema 独立为 1。旧战斗格式仍明确拒绝并保留，不实现升级。

### 本次验证

使用各 target 独立 Gradle 根和固定 JDK；没有新增测试。26.1 已单独执行 `clean`，随后执行 `build :common:test runGameTestServer --offline --console plain --no-daemon`。首批 common 61/61 通过，GameTest 50/51；失败为 `environment_boundary_time`，tick 73 日光探测器 power 4，预期 13。后续源码审查批次分别出现 `arrow_cross_session_miss`（tick 200 未完成因果合并）及 `merge_discovery_expansion`（tick 95 三会话未合并）。保留原断言，不能把后续单批通过当作关闭这些问题。

日志放在 `targets/neoforge-26.1/build/verification/`，包括 `verification-dnd-completion.log`、`verification-dnd-completion-final.log`、`verification-dnd-completion-reviewed.log`。GameTest 启动并执行证明已加载所涉服务端 Mixin，不证明新增角色状态／扩展能力场景已覆盖。

最终验证补记：

| 命令／环境 | 实际结果 |
|---|---|
| 26.1 / JDK 25.0.3：`build :common:test runGameTestServer --offline --console plain --no-daemon` | `build`、UI 投影、诊断检查和 61 项 common 测试通过；GameTest 51 项中 50 通过，`merge_discovery_expansion` tick95 失败，整体命令退出 1。最终日志 `verification-dnd-actor-post-fix.log`。 |
| Forge 1.20.1 / JDK 21.0.11：`build --offline --console plain --no-daemon` | 通过，日志为该 target 的 `build/verification/verification-dnd-architecture.log`。 |
| Fabric 1.20.1 / JDK 21.0.11：同上 | 通过，同名 target 本地日志。 |
| NeoForge 1.21.1 / JDK 21.0.11：同上 | 通过，同名 target 本地日志。其他 target 没有因此启用新的战术玩法。 |

中间 `verification-dnd-architecture-final.log` 批次在实体 Post 观察入口崩溃：Cow 在自身 tick 后不再是加载表中的当前实例。修正为先检查 `removed` 和 `ServerLevel.getEntity(UUID) == entity`，再构造 TacticalActor；无角色状态且无定义 provider 时直接返回。修正后上述 51 项实际完成，原异常未再出现；此证据不是专门的实例替换回归测试。

静态检查：common 主源码没有 Minecraft／loader／Mixin import；四个发布 jar 均包含 `ActorStates.class`，major version 61（Java 17），没有 GameTest／test_instance／test_environment／外部兼容夹具条目。生成日志已归入各 target 的 build 目录。没有新增测试，本次未再发现必须因废弃接口删除的保留测试，已有旧测试删除保持。真实重启、跨文件故障窗口、新扩展模块和玩家实际输入没有验收，不能关闭 DM-01。

## 首次迁移记录（以下为此前证据）

2026-09-29。依据 `DNDTurn_DND_DATA_MODEL_ARCHITECTURE_DRAFT.md`，本次迁移现有行为，不增加法术学习、法术书、职业数值或新的动作资源。用户明确允许直接破坏旧能力 API，并要求清理旧测试、不迁移或新增测试。工作树原有文档移动予以保留；本文从版本目录引用现存 `docs/legacy/` 文档。

## 已实施边界

- common 新增 `rules` 包：类型化 `FactKey`、`ReadContract`、不可变 `FactSlice`、Actor 定义/快照/编译器、Combatant/ResolutionContext，以及 Definition、Grant、Binding、Invocation、ExecutionRequest。事实仅支持不可变标量；未知、缺失和重复 owner 不使用默认值掩盖。快照仅供裁决，不成为存档或世界状态 owner。
- `GrantEvidence` 替代旧 `AbilitySource`。`AbilityGrant` 将 origin、owner、当前证据分开；现有 equipment/basic/intrinsic/status 来源接入，其中既有 status provider 不被误认成原生 MobEffect owner。未增加持久学习来源实现。
- `BuiltinAbilities` 明确声明现有 22 项能力的目标、激活方式、费用和提交时点。`Capability` 仅保留操作分类；`TacticalIntent.requiresAction()` 删除。`CombatEngine` 从已绑定的冻结定义取得 PLAN 子步骤费用，接近移动独立结算；直接 DASH/DODGE 等既有命令维持原规则。
- `AbilityRegistry` 独立注册纯 `AbilityRule`；定义版本绑定 resolver 语义，注册冻结后不替换。`TacticalCapabilities` 分别注册定义、原生事实端口和执行端口，旧单参数注册 API 删除。`TacticalBehavior` 只保留执行/观察生命周期和定义引用；内置适配器可由一个对象实现两个端口，但发现、规则和资源不再由执行器裁决。
- `NativeFacts` 按 ReadContract 读取所需属性、体型、姿态和持盾事实，删除固定 `NativeActorFacts`。`SnapshotCompiler` 在 common 派生 AC/韧性折算，不重复增加原生已应用的装备修正。Actor 快照不含先攻结果、动作余额或当前回合。
- 玩家发现与 Mob 发现从快照 bindings 投影，提交/接近/持续执行复用 common resolver。攻击目标成员关系和玩家类别政策进入 common。持续执行只在当前根/子操作均仍待决时使用 continuation，防止已扣动作后每 tick 再要求动作。原版保护、物品支持和可达性检查继续由受审计的 native facts 端口捕获。
- 接近后、prepare 后及持续步骤前重新捕获必要事实；执行请求绑定根操作、当前实例、Encounter 版本、定义/规则版本、来源证据和费用。检查点保存这个纯值请求，不保存 resolver、FactKey 的 Class 对象、Entity 或可恢复执行器。已有来源消耗差量、结果、许可和幂等释放链继续使用。
- 近战、远程发射和延迟命中的防御值先捕获为 FactSlice，再调用 common 折算。玩家所选物品贡献、天然攻击输入和空手输入保持既有差异。UI 仍接收原有受限投影；未将完整服务端 ActorSnapshot 发给客户端。环境回合不伪装为 Actor。

## 版本与固定源码

固定 Minecraft 26.1 / NeoForge 26.1.2.84，未升级依赖或改变 Mixin 配置。Gradle JVM 为 JDK 25.0.3，target 编译 Java 25，common 编译 Java 17。其他三个 target 使用 JDK 21.0.11。

读取实际生成的 `targets/neoforge-26.1/build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`，核对：

| 接入点 | 本次用途和限制 |
|---|---|
| `LivingEntity.getAttribute(Holder<Attribute>)` / `getAttributeValue(Holder<Attribute>)` | 分别取得当前 AttributeInstance / 有效值；采样经 TacticalActor 核验服务器线程和当前加载实例。没有调用装备维护函数或原生 AI。 |
| `LivingEntity.getArmorValue()` | 当前实现对有效护甲取整；防御桥保留该原生入口，不以修改伤害公式完成迁移。 |
| `ItemAttributeModifiers.compute(Holder<Attribute>, double, EquipmentSlot)` | 原源码按槽位及 modifier operation 汇总；玩家物品输入仍以 0 为基值，未额外加入玩家基础攻击。 |

本次没有新增注入点。读取基类实现不证明未知 override 全部受支持，原有精确适配范围仍适用。

网络注册版本分别更新为 C2S 23 / S2C 29；common schema 14、target envelope 13。TacticalIntent 增加捕获规则版本，Ability 投影将原本表示操作分类的 `cost` 字段改为 `kind`。旧网络/存档格式明确拒绝，继续使用既有原始数据保留路径，不做跨格式迁移。

## 测试清理与验证

没有新增测试，也没有将旧测试迁到新 API。删除 `AbilitySourceTest`、旧外部近战兼容夹具、`TacticalPlanGameTests`、`AbilityPolicyChecks`、`ActionPreviewChecks`、`MobAbilityLoopChecks`、`MobPlanMovementChecks`、`PlayerItemChecks`、依赖其夹具的 `InteractionRepairChecks`、`NativeMeleeChecks`。`TacticalPlanTest` 仅保留原有独立坐标合法性测试。同步移除 13 项 GameTest 注册、对应专用资源及已无源码的 compatTest 源集。

因此来源、共同 PLAN、物品使用、外部能力、预览和近战部分回归覆盖丢失；删除不是修复，也不是新 API 兼容验收。

实际命令均在对应 target 独立根执行，使用 `--offline --console plain --no-daemon`：

| 命令/范围 | 本轮结果 |
|---|---|
| 26.1 `:common:compileJava compileJava` | 通过。 |
| 26.1 `:common:test clean build` | 首次失败于残留 NativeMeleeChecks 对已删除 API 的引用；随后按用户要求清理该旧测试。 |
| 26.1 `:common:test clean build runGameTestServer` | 出现 Gradle 9.7.1 clean/test 任务调度无法推进；不计为游戏执行。改为单独 clean 后再 build。 |
| 26.1 独立 `clean`，随后 `build :common:test` | 通过，61 项剩余 common 测试通过；既有 UI/移动预览与诊断检查通过。 |
| 26.1 最终 `build :common:test runGameTestServer` | build/common/UI 检查通过；51 项 GameTest 实际运行，49 通过、2 失败，命令最终退出 1。 |
| Forge 1.20.1 `build` | 通过；不代表该 target 开放战术能力。 |
| Fabric 1.20.1 `build` | 通过；不代表该 target 开放战术能力。 |
| NeoForge 1.21.1 `build` | 通过；不代表该 target 开放战术能力。 |

GameTest 保留的失败：

- `discovery_cursor`：tick 30，`deferrals=0 work=81`，未达到夹具预期的分批发现；尚未定位到具体过滤条件，不认定是测试注册清理造成，也不删除此测试。
- `environment_boundary_time`：tick 85，已授权目的地未观察到漏斗转移。保留原断言与环境逻辑；本次结果不足以认定为历史间歇问题或已修复。

日志保留在各 target 的 `build/verification/`；26.1 有 `verification-dnd-clean.log`、`verification-dnd-build.log`、`verification-dnd-final.log` 及前序失败记录。这些是本地产物，不进入发布 jar。

最终静态检查：四个 target 的发布 jar 均包含新 rules 类，未包含 GameTest 类、test_instance/test_environment 资源或外部兼容测试夹具。common 主源码未发现 Minecraft、loader 或 Mixin import；旧 AbilitySource/NativeActorFacts 源码引用已清理。`git diff --check` 通过。

真实键鼠、双客户端、两进程恢复、崩溃窗口及新扩展 API 的独立兼容闭环未执行。本次是目标架构的实质迁移，不是完整草案所有长期能力的完成声明；剩余项唯一登记于 [02](../02_GAPS_AND_CONFLICTS.md#dnd-data-model)。
