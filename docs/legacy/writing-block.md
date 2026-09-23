> 本文保留原任务／设计来源，不作为当前进度表；已整合的剩余工作统一见[未完成既定目标](02_GAPS_AND_CONFLICTS.md)。历史任务措辞不表示相关基础仍未实现。

你正在 DNDTurn 仓库中完成剩余基础架构重构。

当前源码基线以仓库实际内容为准。当前主要 target 是：

``` text
Minecraft 26.1
NeoForge 26.1.2.84
targets/neoforge-26.1
```

不要把其他近邻版本的源码、旧文档或方法名当成当前版本事实。

首先完整阅读根目录 `AGENTS.md`，遵守其中关于 ownership、common/target 边界、permit/operation 区别、Mixin 最小职责、Java 17 common 兼容和 GameTest 的所有约束。

本任务不是重新设计整个项目。当前已有三条基础设施方向：

``` text
Data Model
    管：Actor/Ability/Fact/Snapshot 是什么

AI Model
    管：Actor 想做什么

Gate / Authority Model
    管：已经存在的 Minecraft native subsystem
        此刻是否允许继续推进
```

本轮目标是完成仍未落地的 AI Model 与 Gate/Authority Model，并只在确有 ownership/依赖问题时修正现有 Data Model。

不要创建只转发调用的 facade 来宣称“完成架构重构”。

---

# 已确认的现状

Data Model 已经基本落地。

当前 common 已存在并应优先复用：

``` text
ActorDefinition
ActorPersistentState
ActorRuntimeState
ActorSnapshot
CombatantSnapshot

AbilityDefinition
AbilityGrant
AbilityBinding
AbilityInvocation
AbilityRegistry

FactKey
FactSlice
ReadContract

SnapshotCompiler
ResolutionContext
RuleResolver
ExecutionRequest
```

当前 target 已存在：

``` text
ActorDefinitions
NativeFacts
NativeSnapshots
TacticalCapabilities
NativeAbilityFacts
TacticalBehavior
TacticalActions
```

这些已经形成：

``` text
native facts
    ↓
ActorSnapshot / ResolutionContext
    ↓
RuleResolver
    ↓
ExecutionRequest / PLAN
    ↓
native executor
```

不得重新创建另一套 ActorSnapshot、Ability model、ResolutionContext 或 rule engine。

尤其保持这些不变量：

``` text
Snapshot = resolution authority
Snapshot != storage authority

ExecutionRequest != execution permit

operation id = identity/dedup
permit/lease = runtime authorization

Encounter remains owner of:
initiative
turn ownership
Action / Reaction / movement budget
hostility
environment authorization
```

如果 Minecraft/Mod 是事实写 owner，则 common snapshot 只采集，不复制出第二个可写真值。

---

# 当前真正未完成的两个架构域

## A. AI Definition / Bake

当前 `MobTurnStrategies` 是过渡实现。

它已经有一个正确性质：

``` text
Strategy
    ↓
TacticalIntent
    ↓
shared RuleResolver / PLAN / Executor
```

保持这一点。

但当前仍存在：

``` text
Strategy.matches(Mob)
resolve(Mob)

Zombie / Enderman runtime class matching

Decision capture 内直接读取：
Mob.getTarget()
Mob.hasLineOfSight()
Mob.canAttack()
position/navigation
GoalUtils / RandomPos

target-side DecisionView / PerceivedTarget

每个 target 再 TacticalCapabilities.discover(...)
```

这说明：

``` text
definition inference
perception
ability discovery
planning
native movement candidate generation
```

目前仍耦合在同一个 runtime 类中。

需要把它迁移到正式模型。

目标：

``` text
Native implementation
        ↓
definition bake / registered translation
        ↓
ActorDefinition
AiDefinition
Ability definition semantics
        ↓

runtime:

ActorSnapshot
Encounter projection
PerceptionSnapshot
AbilityBindings
AiRuntimeState
AiDefinition
        ↓
AiDecisionContext
        ↓
Common planner / strategy
        ↓
AbilityInvocation / movement proposal / EndTurn / Wait
        ↓
现有 RuleResolver + PLAN + Executor
```

AI 没有任何规则特权。

AI 不能：

``` text
直接 hurt
直接 Navigation.moveTo 作为规则行动
直接扣 Action
直接给自己 lease
直接改 Encounter
直接 tick Goal/Brain 取得决定
```

### AiDefinition

建立 pure-value、Java 17 compatible 的 definition-level AI model。

语义至少覆盖：

``` text
stable id/version
planner policy/reference
tactical traits
target preference
positioning preference
risk/resource preference
required perception/decision facts
provenance / compatibility evidence
```

不要把它做成第二棵 Behavior Tree DSL。

尽量：

``` text
shared planner
+
different AiDefinition
+
different AbilityBindings
+
different PerceptionSnapshot
```

产生行为差异。

不要把当前全部 Mob subclass 写进 common。

### Definition bake

建立 target-owned bake/translation layer。

至少显式区分：

``` text
EXPLICIT_PROVIDER

AUDITED_NATIVE_FAMILY

STRUCTURAL_CANDIDATE

GENERIC_FALLBACK / UNSUPPORTED
```

`STRUCTURAL_CANDIDATE` 只能产生证据，不能自动取得完整支持或 authority。

运行时不得再通过：

``` text
Strategy.matches(live Mob)
class name guess
method name guess
试跑 Goal
tick Brain
反射执行未知 native AI
```

来决定 Actor 的长期 AI 语义。

允许在：

``` text
registry/reload
server initialization
first-use + versioned cache
explicit compatibility provider registration
```

等明确 definition boundary bake。

### Perception

Hot native observations 不应错误地 bake。

例如：

``` text
current target
line of sight
current distance
last known target
current visible entities
terrain opportunity
```

属于 runtime perception。

建立 pure-value：

``` text
PerceptionSnapshot
```

target adapter 可以在决策边界调用当前 Minecraft LOS/sensing API，
但 native object 不得进入 common。

严格保持：

``` text
Perception
    != Relation

Relation
    != Preference

Preference
    != Legality

Legality
    != Execution Permit
```

当前 vanilla target commitment 可以作为 perception evidence，
但不能自动等于 hostility。

### AiRuntimeState

把当前 `MobTurnStrategies.Decisions` 中真正属于 AI private runtime 的状态正式化。

例如：

``` text
last selected target
bounded failed proposal history
planner continuation/cursor
wait state
focus/retreat intent
bounded memory
deterministic planner random state
```

它不能拥有：

``` text
HP
Action/Reaction/movement
initiative
hostility authority
Ability ownership
Goal/Brain object
Navigation object
Entity/Level
```

它应由 encounter-side AI runtime 拥有并按 actor instance / encounter 生命周期清理。

### Ability AI semantics

AI 需要理解 ability 战术意义，但这不是权限。

建立 `AiAffordance` 或等价 definition-level projection，例如：

``` text
DAMAGE
HEAL
CONTROL
BUFF
DEBUFF

MELEE
RANGED
AREA
SINGLE_TARGET

REPOSITION_SELF
REPOSITION_TARGET
ESCAPE
PROTECT_ALLY

RESOURCE_CHEAP
RESOURCE_LIMITED
HIGH_RISK
```

不要把：

``` text
DAMAGE tag
```

解释成：

``` text
may attack
```

真正权限仍来自：

``` text
AbilityBinding
+
RuleResolver
+
Execution Permit
```

如果直接修改 `AbilityDefinition` record 会不必要地破坏其他 target，可以使用 versioned/frozen semantic registry keyed by `ability id + version`，但 ownership 必须仍然是 definition-level，不能变成 AI runtime 自己猜。

同样，`ActorDefinition → AiDefinition` 的关联可以通过兼容的 definition reference/binding 实现，不要求为了字段美观破坏未迁移 target。

### 当前 Ground strategy

`GoalUtils`、`RandomPos`、PathNavigation stability 等是 Minecraft-specific geometry/navigation evidence。

不要把它们移动进 common。

将其拆为类似：

``` text
NativeMovementOpportunityProvider
        ↓
pure MovementOpportunity / candidate values
        ↓
Common AI planner
```

Common planner 决定“是否值得移动到这个候选”，target adapter 决定“Minecraft 当前能提供哪些已审计候选”。

### 当前 ability discovery

不要在一次 AI decision 中对每个 perceived target 重新完整 capture 同一个 Actor。

尽量在决策边界：

``` text
capture actor snapshot once
resolve source-valid AbilityBindings once
capture perception
evaluate bindings against bounded target facts
```

只有 native fact revision 真正变化时才重新采集相关事实。

不要为了优化引入第二个 authority cache；缓存必须有明确 revision/invalidation。

---

# B. Gate / Authority Model

当前 gate 判断已经扩散到：

``` text
CombatEngine

ServerCombatService
    maySubmitPlan
    mayOrganizeInventory
    hasPlayerMoveLease
    hasMobMoveLease
    isEntityInsidePausedRegion
    isEntitySimulationPaused
    isBlockSimulationPaused

MinecraftCombatRuntime

VanillaInputPolicy

ActiveBodyControl

EnvironmentProcesses

MobNavigationLeaseMixin

ServerGamePacketListenerMixin

各种 entity/block/environment Mixins
```

问题不是类数量，而是：

``` text
membership
encounter phase
current actor
region containment
move lease
environment authority
recovery
server freeze
running operation
control fault
world-effect scope
projectile domain
```

被多个调用方重新组合。

目标不是：

``` text
BehaviorGateManager
gate.can(object, enum)
```

禁止创建包含整个游戏行为枚举的大型 God Object / switch。

目标是：

``` text
authoritative state owners
        ↓
bounded authority projection
        ↓
typed control facts
        ↓
typed policy
        ↓
typed decision
        ↓
native seam
```

## Shared decision vocabulary

建立统一语义：

``` text
ALLOW
HOLD
DENY
```

定义必须严格：

``` text
ALLOW
    当前 native process 可以推进。

HOLD
    行为/过程本身并非非法，
    但当前不能推进。
    具体如何 hold 由 native seam 决定。

DENY
    此次行为没有 authority / unsupported / invalid，
    不应作为待执行合法过程保留。
```

特别注意：

`HOLD` 不表示所有 seam 都建立一个 replay queue。

例如：

``` text
scheduled tick HOLD
    -> 保留当前 RegionalScheduledTicks ownership

block event HOLD
    -> reschedule

block entity ticker HOLD
    -> skip this ticker step

random tick HOLD
    -> skip this sampled callback；
       不需要虚构一个 future replay token

autonomous Goal decision HOLD
    -> 不推进 Goal decision

client unauthorized item use
    -> 通常应是 DENY，而不是 HOLD
```

Decision 应携带稳定 reason code 和必要 revision/diagnostic authority identity。

但：

``` text
GateDecision != Permit
GateDecision != Lease
GateDecision != transferable authority
```

它只能描述当前 evaluation。

绝不能：

``` text
if (oldDecision.allowed()) execute next tick
```

长期/跨 tick authority 仍必须由现有 typed lease/permit 持有。

不要制造 `GenericPermit`。

## 不要制造 GlobalGateSnapshot

建立有限 facts，例如语义上：

``` text
EncounterAuthorityView

InputControlFacts

ActorControlFacts

EntitySimulationFacts

BlockSimulationFacts

EffectControlFacts
```

名称可以依据代码调整。

它们只包含该 policy 必须读取的值。

禁止：

``` text
GlobalGateContext {
    everything on server
}
```

同样，不要凭空发明 `membershipRevision`。

优先复用当前已经有明确语义的：

``` text
Encounter state version
structural revision
actor instance identity
operation id
environment step id
server generation
lease identity/lifecycle
```

只有在现有 revision 无法回答一个明确 invalidation 问题时，才能增加新的 revision，并必须指出它的 writer 与递增条件。

## Policy families

源码显示至少需要以下 typed families：

``` text
OperationAdmissionPolicy
InputPolicy
ActorControlPolicy
SimulationPolicy
EffectControlPolicy
```

另外：

``` text
EnvironmentProcessClassifier
```

只负责 process classification，不负责 runtime allow/hold。

其中 `OperationAdmissionPolicy` 是源码审计后必须补充的 family。

`ServerCombatService.maySubmitPlan()` 当前混合：

``` text
service closing
recovery failure/pending
world effect active
encounter existence
server frozen
running operation
control fault
current participant
environment phase
region containment
```

它既不是 vanilla Input，也不是 Mob native drive。

将其建模为独立 admission decision，
并让 rule legality 与 runtime service readiness 保持分离。

## AuthorityProjection

`ServerCombatService` 继续是大量 target runtime state 的 owner，
但不要让每个调用方直接去拼状态。

建立只读 projection/capture 层，从真正 owner 捕获：

``` text
CombatEngine
regions
recovery state
active operations
MoveLease owners
environment step owner
projectile domain
server freeze state
actor instance
```

然后 typed policy 消费 projection。

Projection：

``` text
不写状态
不执行行为
不授予 permit
不主动关闭 lease
不调用 native effect
```

---

# 必须保留的 typed authorities

以下现有概念不要统一成 GenericPermit：

``` text
PlayerMoveLease
MobMoveLease

CombatEngine.EffectPermit

Environment Step authorization

ExitAuthorizations

call-scoped ThreadLocal execution scope
```

它们可以共享命名/诊断规范，例如：

``` text
owner
encounter
operation
revision
scope
reason/provenance
```

但生命周期和权限语义保持不同。

特别保持：

``` text
MoveLease
    允许某 operation 暂时驱动 native movement subsystem

EffectPermit
    允许某 causal root 产生有限 child effect

Environment authorization
    允许明确 environment step 推进

ExitAuthorization
    允许明确 actor/operation 跨区域退出
```

任何：

``` text
permit != null => allow everything
```

都视为错误。

---

# `controlled` / `paused` 清理

当前多个 `controlled` 的含义不同。

逐步废除基础设施层宽泛 API：

``` text
VanillaInputPolicy.controlled
ActiveBodyControl.controlled
dndturn$controlled
```

替换为具体问题，例如：

``` text
gameplayInputDecision
movementInputDecision

autonomousDecision
nativeDriveDecision
nativeUseProcessDecision
lootPickupDecision

entitySimulationDecision
blockSimulationDecision

environmentProcessDecision
```

不要求机械使用以上方法名，但 production code 中不应继续依赖一个模糊的：

``` text
controlled == everything is blocked
```

因为当前 DNDTurn 的重要合同是：

``` text
ordinary entity body/lifecycle continues
while active autonomous AI/native skills can be held.
```

特别注意当前：

``` text
ServerCombatService.isEntitySimulationPaused(...)
```

对普通 living body 返回 false。

不要因为重构 Gate 而恢复“整个 encounter region entity tick freeze”。

现有 `BodyControlChecks` 所验证的外力、生命周期、被动物理必须继续运行。

---

# `prepareEntitySimulationStep()` 必须特殊处理

不要把：

``` text
ServerCombatService.prepareEntitySimulationStep(entity)
```

原样移动到 `SimulationPolicy.evaluate()`。

它当前包含真正副作用：

``` text
quarantined projectile handling

invalid MobMoveLease revocation

closeMobMove(...)

navigation corridor inspection

movement budget check

lease.authorizedCost mutation
```

需要拆成：

``` text
pure fact capture
        ↓
SimulationPolicy.evaluate(...)
        ↓
GateDecision
```

和独立的：

``` text
EntitySimulationCoordinator
or equivalent execution-boundary prepare/reconcile
```

后者仍可：

``` text
validate current lease
revoke stale lease
reserve/authorize next native movement cost
quarantine unsupported projectile
close invalid operation
```

但这些 mutation 必须由真实 owner 执行，
不能隐藏在看起来是 pure policy 的 query 中。

当前 `SimulationDecision` cache 可以保留/重构，
但 cache key 必须继续覆盖真实 invalidation：

``` text
server tick
engine revision
active environment identity
以及新增 policy 真正依赖的 revision
```

---

# EnvironmentProcesses 拆分

当前：

``` text
EnvironmentProcesses.policy(...)
EnvironmentProcesses.tickerPolicy(...)
EnvironmentProcesses.eventPolicy(...)
```

基本属于 classification，方向正确。

当前：

``` text
EnvironmentProcesses.allowed(...)
```

同时又读取：

``` text
tickRateManager.runsNormally()
isFormalBlockSimulationPaused(...)
```

这是 classification 与 scheduling decision 混合。

改成：

``` text
EnvironmentProcessDefinition / ProcessClassification
    channel
    temporal policy
    explicit maintenance exception
```

然后：

``` text
SimulationPolicy.evaluate(processDefinition, facts)
        ↓
ALLOW / HOLD / DENY
```

保留当前精确 vanilla maintenance exceptions，例如 sign/hanging-sign 和 decorated-pot 的 audited bypass；不要扩大成继承树式自动放行。

迁移这些 seam：

``` text
RegionalScheduledTicks

LevelBlockEntityGateMixin

ServerLevelBlockEventGateMixin

ServerLevelRandomTickGateMixin

HopperProcessMixin

LilyPadProcessMixin
```

让它们不再自己理解 Encounter/Environment Turn。

Mixin 只负责：

``` text
在哪里拦
如何把 ALLOW/HOLD/DENY 映射到 vanilla
如何保留/reschedule vanilla ownership
```

---

# InputPolicy 拆分

当前 `VanillaInputPolicy` 同时含两类职责。

第一类是 control authority：

``` text
controlled
mayOrganize
```

第二类是 native protocol/container validation：

``` text
container id
state id
slot bounds
click mode
swapFitsInventory
prediction correction
```

不要把第二类塞进 ControlFacts。

正确关系：

``` text
InputPolicy
    判断该类 gameplay input 当前有没有 authority

Vanilla packet validator
    判断这个具体 packet 是否满足 vanilla/container
    结构约束

Mixin
    执行 cancel/correction
```

例如：

``` text
GateDecision ALLOW
+
packet invalid
=
仍然拒绝
```

Gate ALLOW 不是 packet validation bypass。

迁移 `ServerGamePacketListenerMixin` 后，
Mixin 不应再自行组合：

``` text
controlled
isMember
mayOrganize
move lease
```

但保留线程切换、malformed packet 让 vanilla 处理、
position correction、inventory resync 等 seam-specific 行为。

---

# ActorControlPolicy

将这些 native subsystem 权限统一到同一 authority projection：

``` text
autonomous Goal decisions

running Goal continuation

customServerAiStep

PathNavigation

MoveControl

LookControl

JumpControl

active item-use process

outer active skills
    Creeper fuse
    Enderman teleport
    Snow Golem trail
    Wither pursuit
    Dragon phase/direct skill

loot pickup
```

不要用一个巨型 `enum NativeBehavior` + switch。

可以提供少数语义明确的方法或小型 typed request。

例如：

``` text
actorPolicy.autonomousDecision(facts)
actorPolicy.nativeMovement(facts)
actorPolicy.activeUse(facts)
actorPolicy.autonomousWorldMutation(facts)
```

具体结构根据代码选择。

`MobNavigationLeaseMixin` 最终不能：

``` text
get ServerCombatService
query region
query lease
combine them
```

理想状态：

``` text
decision = narrow actor-control API

if decision is HOLD/DENY:
    perform seam-specific skip
```

同理 `TacticalNavigationArrivalMixin` 不应直接查询 raw `hasMobMoveLease()`，
而应读取明确的 active movement authority/projection。

但是 PLAN/TacticalActions 内部作为 lease owner/reconciler，
仍然可以直接访问自己拥有的 typed lease state；
不要为了“所有代码都走 Gate”破坏 owner 内部逻辑。

---

# EffectControlPolicy

这一部分保守迁移。

当前：

``` text
InteractionPolicy.Decision
CombatEngine.EffectPermit
ParticipantEffects
EnvironmentExplosion ThreadLocal frame
TacticalDamageContext
```

已经包含大量正确的 typed authority。

不要重写 EffectPermit。

不要把 common `InteractionPolicy` 的：

``` text
ALLOW
REJECT
REQUIRES_CLASSIFICATION
```

机械替换成 runtime：

``` text
ALLOW
HOLD
DENY
```

二者回答的问题不同。

`InteractionPolicy` 是：

``` text
domain compatibility / classification
```

`EffectPermit` 是：

``` text
causal execution authority
```

EffectControlPolicy 只负责把 native effect seam 当前需要的：

``` text
domain compatibility
current causal frame
existing typed permit
environment step
target domain
```

投影成 seam decision，且不能扩大原许可范围。

优先迁移当前明显重新拼：

``` text
isMember
+
EnvironmentExplosion.allows
+
region/environment queries
```

的地方。

如果某 effect path 已经有唯一、清晰且没有重复事实解释的 typed authority，
不要为了统一外观多包一层无意义 facade。

---

# Presentation 不得重新解释 authority

当前：

``` text
syncBodyStateTransitions()
clientEntityPaused()
CombatSubscriptions
presentationFacts(...)
```

有部分重新组合 pause/member/environment facts。

Presentation 只应消费正式 control projection/decision 的显示投影。

不得：

``` text
UI/presentation 自己决定一个 entity 应不应该被允许执行
```

Presentation facts 永远不是 runtime permit。

---

# Mixin 最终约束

完成后，每个 Mixin 的职责尽量接近：

``` text
identify native seam
capture minimum native arguments
query one narrow typed policy/interface
translate decision into cancel/skip/reschedule/correction
preserve required vanilla maintenance
```

Mixin 不应该知道：

``` text
谁当前回合
Encounter phase 为什么是这个值
recoveryPending 的意义
lease 为什么有效
Environment Turn 如何授权
EffectPermit 为什么成立
```

允许 Mixin 知道当前 Minecraft 方法的具体取消/恢复语义，
因为那正是 native seam 的责任。

---

# 实施顺序

按以下顺序实际修改源码，不要只写设计报告：

1. 固定当前行为基线并列出 ownership map。先检查 `AGENTS.md`、现有 common data model、`MobTurnStrategies`、所有 gate callsite 和 GameTests。不要删除现有测试来使新结构通过。
2. 先建立最小 shared Gate decision semantics 和 typed authority projections。共享 DTO 必须保持 Java 17 且无 Minecraft 类型。若一个类型只对 NeoForge native seam 有意义，则留在 target，不为了“基础设施感”强塞 common。
3. 在 target 建立 `OperationAdmissionPolicy / InputPolicy / ActorControlPolicy / SimulationPolicy`，先迁移重复最多的 facts 组合。每个 policy 尽量纯；mutation 留给 state owner/coordinator。
4. 拆 `prepareEntitySimulationStep`、`EnvironmentProcesses.allowed` 和 `controlled` 系列 API。保证普通 body tick、被动力学和生命周期行为完全不回退。
5. 迁移 Mixins，使其停止直接组合 ServerCombatService 的 encounter/region/lease 状态。删除或缩小已经没有调用者的旧 boolean helpers。暂时需要兼容 wrapper 时标记迁移用途，并在本轮结束前尽量移除，不把 wrapper 当最终架构。
6. 实现 AI common semantic model、target bake、runtime perception、AiRuntimeState 和 common planner boundary。把 `Strategy.matches(Mob)` / runtime species resolution 从 turn-time 决策链移除。保留真正 hot 的 LOS/current-target/world observation，但转换成 pure perception facts。
7. 将现有 Zombie/Enderman/Ground 行为迁移为新模型的首批 audited vertical slices。必须证明：AI 仍通过与玩家相同的 RuleResolver/PLAN/Executor；Mob movement lease 仍仅在已接受 movement operation 中取得；换装备/状态只改变 runtime bindings/availability，不重新 bake AiDefinition。
8. 清理 presentation、effect seam 和 diagnostics 的重复事实解释，运行测试和构建，并提交一份最终 ownership/dependency 报告。

---

# 必须新增的测试

Common unit tests 应覆盖：

``` text
ALLOW / HOLD / DENY semantic invariants

GateDecision 不能充当 transferable authority

typed policy 对同一 facts 的确定性

AI planner 只消费 pure values

AiDefinition 不因普通 runtime state change rebake

Perception / relation / preference / legality 分离

unknown/unsupported AI fallback 显式

AiAffordance 不授予 legality
```

NeoForge GameTest 必须保持并实际运行现有相关场景，包括至少：

``` text
BodyControlChecks

regionalEntityGate

scheduledTickHold

prototypeEnvironmentLoop

prototypeZombieNavigation

mobUnknownRevokesLease

movementLeaseExit

inventory swap / input correction

block entity / block event / random tick / hopper environment behavior
```

增加针对重构边界的反例：

``` text
controlled region + no move lease
    autonomous decision HOLD
    body lifecycle ALLOW

controlled region + valid MobMoveLease
    Goal decision still HOLD
    native navigation/control ALLOW

movement lease
    does not allow attack/item-use/other skills

Environment process outside active environment step
    HOLD

same process in authorized environment step
    ALLOW

DIRECT process
    does not accidentally inherit Environment HOLD

invalid/expired authority
    cannot reuse an old GateDecision

AI definition already baked
    runtime equipment/perception change does not rebake

unknown Mob
    never silently becomes Zombie strategy
```

测试必须验证实际 behavior，而不是只验证新类存在。

---

# 静态验收

完成后用搜索证明 production source 中不再存在明显业务事实扩散。

重点检查：

``` text
VanillaInputPolicy.controlled
ActiveBodyControl.controlled

Mixin direct calls to:
    isEntityInsidePausedRegion
    hasMobMoveLease
    hasPlayerMoveLease
    state.phase/current
    recoveryPending
    server frozen

EnvironmentProcesses.allowed

MobTurnStrategies.Strategy.matches(Mob)
runtime AI class matching
```

并逐项解释仍保留的任何直接查询为什么属于真正 state owner / executor，而不是 gate leak。

不要为了让 grep 为空而改名隐藏同一逻辑。

---

# 构建与验证

至少执行当前 target：

``` text
cd targets/neoforge-26.1

./gradlew :common:test
./gradlew clean build
./gradlew runGameTestServer
```

如果 common public API 变化影响其他 target，则按 `AGENTS.md` 检查：

``` text
forge-1.20.1
fabric-1.20.1
neoforge-1.21.1
```

的构建兼容。

不要为了其他 target 尚未启用 AI/Gate 功能而静默增加 fake implementation/no-op support。

如果某平台无法迁移，只保持 compile-compatible contract 并明确 UNSUPPORTED。

---

# 最终必须满足的架构

``` text
                COMMON / RULE AUTHORITY

ActorDefinition
AbilityDefinition
AiDefinition

Actor/Encounter authoritative state
        │
        ▼
ActorSnapshot / ResolutionContext
        │
        ├──────────────► Common AI Planner
        │                    │
        │              AbilityInvocation
        │                    │
        ▼                    ▼
Rule Interpreter ◄───────────┘
        │
ExecutionRequest / authorized PLAN
        │
────────────────────────────────────────
        │
        ▼
                 TARGET RUNTIME

actual state owners
CombatEngine / lease owners /
environment owner / recovery state /
native domain state
        │
        ▼
AuthorityProjection
        │
 ┌──────┼────────┬──────────┬───────────┐
 ▼      ▼        ▼          ▼           ▼
Admission Input Actor    Simulation   Effect
Policy    Policy Control   Policy      Control
                 Policy                Policy
        │
        ▼
typed decision
ALLOW / HOLD / DENY
        │
────────────────────────────────────────
        ▼
native seams
packet / Goal / navigation / move-control /
entity simulation / scheduled tick /
block entity / block event / random tick /
hurt / explosion / projectile
```

同时 AI target side：

``` text
Native implementation
        │
        ├─ definition-time evidence
        │        ↓
        │      Bake
        │        ↓
        │    AiDefinition
        │
        └─ runtime observation
                 ↓
          PerceptionSnapshot
                 │
                 ▼
           AiDecisionContext
                 │
                 ▼
           Common Planner
```

最重要的不变量：

``` text
Data Model
    解释“是什么”

AI Model
    解释“想做什么”

Rule Authority
    解释“规则允许发起什么”

Lease / Permit
    解释“哪个已授权执行当前拥有 native execution authority”

Gate / Control Policy
    解释“这个具体 native subsystem 此刻能否推进”

Mixin
    只解释“在 Minecraft 哪里以及如何拦”
```

这五种责任不能互相替代。

---

# 禁止事项

不要：

``` text
创建万能 BehaviorGateManager

创建 EVERYTHING GateType enum

创建 GenericPermit

创建 GlobalGateSnapshot / GlobalContext

把 RuleResolver 搬到 target gate

把 lease existence 当作任意行为权限

把 GateDecision 存起来跨 tick 当许可

把 AI Planner 直接连接 Navigation/hurt/world mutation

把 Goal/Brain object 放入 common

把 live Entity 放入 common AI state

把 native perception bake 成永久 definition

把所有 encounter entity body tick freeze

把 packet structural validation 当成 rule gate

用 facade/rename 代替 ownership 重构

用删除/放宽 GameTest 断言让迁移通过
```

---

# 完成报告

最终回复不要只说“done”。

报告必须给出：

``` text
1. 修改了哪些 ownership 边界
2. 新增了哪些 common pure-value model
3. 哪些 facts 现在只有一个 projection/capture source
4. 哪些旧 boolean/controlled helper 被删除
5. 哪些 Mixin 已经只剩 seam responsibility
6. AI 哪些语义改为 definition bake
7. 哪些 native observation 仍然保持 runtime hot perception，以及原因
8. Lease / EffectPermit / Environment authorization 为什么仍保持 typed separate
9. prepare/reconcile 中哪些 side effects 没有进入 pure policy
10. 实际执行的构建和 GameTest 命令及结果
11. 尚未支持的 AI/native behavior
12. 仍存在的 gate fact recomposition callsite；如果不为零，逐一解释
```

如果实际源码证明这里建议的某个类名不适合，可以改变类名；不能改变上述 ownership 与 authority 语义。

本任务的完成标准不是“新增 Gate 类”。

完成标准是：

``` text
同一个 authority fact 不再由多个 native seam 自行解释；

AI runtime 不再重新推断 definition-level semantic；

规则授权、runtime admission、lease/permit、native gate、
execution preparation 各自具有明确 owner；

Mixin 不再承担 DNDTurn 业务规则。
```