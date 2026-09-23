> 本文保留原任务／设计来源，不作为当前进度表；已整合的剩余工作统一见[未完成既定目标](02_GAPS_AND_CONFLICTS.md)。历史任务措辞不表示相关基础仍未实现。

# DNDTurn：AI 建模与 Definition-Level Bake 架构决策记录

**状态：架构目标 / 决策记录**  
**日期：2026-09-29**  
**建议位置：`docs/AI_MODEL_ARCHITECTURE_DECISIONS.md`**  
**性质：不要求立即重构；用于固定后续源码审计、Common 建模与 Vanilla/Mod AI 适配的语义边界。**

---

## 0. 文档定位

本文记录 DNDTurn 当前关于 AI 建模的已确认方向，并补全已有能力框架与 Mob 原生翻译层之间缺失的 **definition-level AI bake**。

本文不替代现有：

- `05_CAPABILITY_FRAMEWORK_IMPLEMENTATION.md`
- `06_MOB_NATIVE_TRANSLATION_IMPLEMENTATION.md`
- 当前 `AGENTS.md`
- DND Actor / Snapshot 数据模型文档

其中：

- **05** 继续拥有能力定义、能力来源、调用、规则授权、统一玩家/AI行动入口和 AI Strategy 运行时边界；
- **06** 继续拥有 Vanilla/Mod Mob 的事实读取、Goal/Brain 行为证据、原生行为翻译、执行端口和兼容性证据；
- **本文** 只补充并固定 AI Definition、Bake、Perception、AI Runtime State、AI-facing ability semantics 与 Planner 的长期边界。

本文中的类型名称均为**语义名**，不是强制 Java 类名。

---

# 1. 粗略目标

DNDTurn 的长期 AI 目标不是“在回合开始时重新运行或解析 Minecraft AI”，而是：

> **在适配阶段把 Minecraft / Mod 原生 AI 中可被可靠理解的语义，烘焙为稳定、版本化、可诊断的 DND Common AI Definition；运行时 AI 只消费 Common Definition 与当前 Snapshot。**

最终期望数据流：

```text
Minecraft / Mod 原生实现
        │
        │ inspection / explicit provider
        ▼
Native Facts + Behavior Evidence
        │
        │ registered translation / bake
        ▼
ActorDefinition
AbilityDefinition
AiDefinition
        │
        ├────────────── cold / stable
        │
        ▼
ActorSnapshot
AbilityBindings
PerceptionSnapshot
EncounterParticipantState
AiRuntimeState
        │
        ▼
AiDecisionContext
        │
        ▼
Common AI Planner / Strategy
        │
        ▼
AbilityInvocation / EndTurn / NoDecision
        │
        ▼
统一 Rule Interpreter / Executor
```

运行时不得重新依赖：

- Java 方法名；
- Goal 类名作为最终语义；
- Brain 内部对象结构；
- `canUse/start/tick` 等有副作用的 AI 方法；
- 反射试运行未知行为；
- 物种名直接决定技能或战术。

---

# 2. 核心决策

## 2.1 AI 必须具有 Definition-Level Cold Data

与 `ActorDefinition`、`AbilityDefinition` 相同，AI 需要稳定的定义级表达。

暂定名：

```text
AiDefinition
```

它描述：

> 一个 Actor 在战术层通常如何做决定。

它不描述：

- 当前看见谁；
- 当前能使用哪些能力；
- 当前剩余多少 Action / Reaction / Movement；
- 当前目标是谁；
- 当前具体要执行哪一步；
- 当前 Minecraft Goal 正在运行到哪里。

因此：

```text
AiDefinition = cold decision semantics
```

而不是：

```text
AiDefinition = current AI state
```

---

## 2.2 AI Bake 与 Runtime Decision 必须严格分离

定义：

```text
Bake:
    runtime-specific representation
        ↓
    canonical cold definition
```

```text
Decision:
    canonical definitions
    + current snapshots
        ↓
    tactical proposal
```

因此 Goal / Brain / custom AI inspection 只允许发生在：

```text
adapter / bake / compatibility discovery
```

不允许成为：

```text
every-turn decision dependency
```

这条边界的直接目的包括：

1. 避免回合内依赖不稳定方法名或私有实现；
2. 避免为了“读意图”调用会修改状态的原生 AI；
3. 让 Common AI 可以纯值测试；
4. 让兼容判断可缓存、版本化和诊断；
5. 让未知 Mod Mob 的失败模式明确，而不是每回合重新猜测。

---

## 2.3 DNDTurn 不把 Vanilla Goal / Brain 当作 Common AI

Vanilla Goal / Brain 可以提供：

- 行为候选；
- 感知能力证据；
- 目标偏好证据；
- 导航或控制端口证据；
- 特殊过程存在证据；
- 已审计原型匹配证据。

但不能直接成为：

- `AiDefinition`；
- `AbilityDefinition`；
- `AbilityInvocation`；
- `ExecutionPermit`；
- Common Planner 的运行对象。

因此：

```text
Goal / Brain
    = adapter evidence
    ≠ common semantic authority
```

---

## 2.4 AI 与 Capability / Rule / Executor 必须分工

长期职责：

```text
AI
    决定：什么值得尝试

Capability
    决定：Actor 当前拥有什么能力

Rule Interpreter
    决定：本次调用是否合法

Executor
    决定：如何执行已授权的原生行为
```

AI 没有规则特权。

AI 生成的任何攻击、移动、施法、交互等最终都必须走与玩家相同的规范能力提交与规则验证链。

AI 和玩家之间的主要区别应是：

```text
Tactical / Ability Invocation 的生产者不同
```

而不是：

```text
规则和执行后端不同
```

---

# 3. AiDefinition 的职责

## 3.1 推荐语义

`AiDefinition` 应表达稳定的**策略偏好与决策配置**，例如：

```text
AiDefinition
│
├── stable id / semantic version
├── planner policy
├── tactical traits
├── target preference policy
├── positioning policy
├── risk policy
├── resource policy
├── cooperation policy
├── pursuit / disengage preferences
├── required decision facts
├── supported affordance preferences
└── provenance / compatibility evidence
```

示意：

```text
aggression         = HIGH
selfPreservation   = LOW
preferredRange     = CLOSE
focusFire          = true
protectAllies      = false
resourceUse        = CONSERVATIVE
pursuitPolicy      = AGGRESSIVE
```

以上都是 definition-level 值。

---

## 3.2 AiDefinition 不应成为第二棵 Behavior Tree

原则上不希望：

```text
AiDefinition
    selector
      sequence
        condition
        action
        selector
        ...
```

否则只是在 Common 内重新造一套 Goal/Behavior Tree DSL。

默认方向：

```text
Definition
    = policy + preference + semantic traits

Planner
    = reusable common algorithm
```

即多个 Actor 尽量共享 Planner，只通过：

- AiDefinition；
- Capability；
- Actor Snapshot；
- Perception；
- Encounter State

表现出差异。

特殊 Boss / Scripted Encounter 可以在后续通过显式策略扩展，但不把这种能力作为所有 Mob 的默认模型。

---

# 4. ActorDefinition 与 AiDefinition 的关系

不把 AI 永久焊死为：

```text
EntityType -> One Hardcoded AI
```

推荐关系：

```text
ActorDefinition
    └── AiDefinitionRef
```

或者等价的纯值引用。

原因：同一 Actor archetype 未来可能因为：

- variant；
- faction；
- encounter override；
- explicit mod provider；
- scripted role；
- data pack 配置

采用不同 AI profile。

因此：

```text
ActorDefinition
    定义“它是什么”

AiDefinition
    定义“它通常怎样做战术决策”
```

二者相关但不等同。

---

# 5. AI Runtime State

AI 需要独立运行时状态，但它不能成为第二套 Encounter 权威。

暂定名：

```text
AiRuntimeState
```

可包含：

- last selected target；
- last known target position；
- bounded threat memory；
- failed proposal history；
- current planner continuation / cursor；
- retreat intent；
- focus target；
- strategy-specific random state；
- bounded decision cooldown / retry evidence。

不应包含或取代：

- HP；
- Action / Reaction / Movement；
- initiative；
- Encounter hostility authority；
- Actor equipment ownership；
- Ability ownership；
- Minecraft Goal/Brain object；
- live `Entity` / `Level` reference；
- native navigation object；
- persistent lambda / callback。

Ownership：

```text
AiRuntimeState
    owner = strategy runtime instance / encounter-side AI runtime
```

而不是：

```text
ActorSnapshot owner
```

---

# 6. Perception 必须单独建模

服务器知道一个实体存在，不表示该 AI 知道它存在。

因此 AI 不应直接读取整个世界事实集。

应存在：

```text
World / Native Facts
        ↓
Perception Resolver
        ↓
PerceptionSnapshot
```

暂定 `PerceptionSnapshot` 可包含：

- visible actors；
- visible terrain / relevant geometry；
- audible / sensed events；
- known hazards；
- last-known actor facts；
- known objectives；
- perception timestamps / revisions；
- source / confidence when needed。

AI Planner 只能消费已经授权的 perception view。

这条边界用于支持：

- line of sight；
- invisibility；
- stealth；
- fog-of-war；
- last-known-position；
- investigation / knowledge limits；
- sensory differences between mobs。

---

# 7. Perception / Relation / Preference / Legality 四层分离

继承 05 的既有决策：

```text
Perception
    我知道谁/什么存在？

Relation
    我和它是什么关系？

Preference
    我更想优先处理谁？

Legality
    当前规则允许我用什么能力作用于谁？
```

禁止把以下概念合并：

```text
visible == hostile
hostile == preferred target
preferred target == legal attack target
current target == execution permit
```

例如：

- 玩家可能被看见但不是敌人；
- 敌人可能不可见；
- 当前仇恨目标可能已经不合法；
- AI 可能偏好攻击某目标，但剩余资源不允许；
- 当前存在攻击能力，不表示目标满足其 TargetContract。

---

# 8. AI Decision Context

运行时应构造一个 Common pure-value 决策输入。

暂定：

```text
AiDecisionContext
```

建议包含：

```text
AiDecisionContext
│
├── self Actor / Combatant Snapshot
├── EncounterParticipantState projection
├── PerceptionSnapshot
├── relation facts
├── source-valid AbilityBindings
├── current Availability summaries
├── relevant spatial facts
├── previous bounded action results
├── AiRuntimeState read view
└── AiDefinition
```

注意：

- `AiDecisionContext` 是一次决策输入；
- 不拥有长期世界状态；
- 不开放写世界 API；
- 不提供任意 `ServerLevel` / `Entity`；
- 不把所有 Encounter 数据无过滤暴露给 AI。

---

# 9. Planner / Strategy 输出

AI Strategy / Planner 的规范输出继续沿用 05 的边界。

合法结果至少包括：

```text
AbilityInvocation proposal
EndTurn
NoDecision(reason)
Wait / deferred decision(reason)
```

Planner 不直接：

- 调用 `hurt`；
- 调用 Navigation；
- 改位置；
- 改库存；
- 切槽；
- 扣 Action；
- 写 Encounter；
- 启动 Goal；
- tick Brain。

所有实际世界动作必须经过统一 Rule / PLAN / Native Execution 后端。

---

# 10. Ability 需要 AI-facing Semantic Metadata

Capability Framework 已经定义能力身份、来源、调用、费用与执行合同，但通用 AI 还需要知道：

> 这个能力在战术上意味着什么？

暂定增加：

```text
AiAffordance
```

或等价命名。

它属于 `AbilityDefinition` 的 AI-facing semantic projection，不属于执行许可。

示例：

```text
DAMAGE
HEAL
CONTROL
BUFF
DEBUFF
SUMMON
INTERACT

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

一个能力可以有多个 affordance。

示例：

```text
Fireball
    DAMAGE
    RANGED
    AREA
    RESOURCE_LIMITED
```

```text
Basic Sword Attack
    DAMAGE
    MELEE
    RESOURCE_CHEAP
```

```text
Healing Spell
    HEAL
    ALLY_TARGET
    RESOURCE_LIMITED
```

## 10.1 Affordance 不是权限

严格禁止：

```text
ability has DAMAGE tag
    => AI automatically may attack
```

正确关系：

```text
AbilityBinding
    决定 Actor 是否拥有该能力

Availability / Rule
    决定当前是否合法

AiAffordance
    只帮助 AI 理解战术意义
```

---

# 11. Definition-Level Bake

## 11.1 Bake 输入

Bake 可能消费：

- explicit mod compatibility provider；
- audited Vanilla EntityType family；
- audited Goal / Brain families；
- attributes / components；
- equipment capability patterns；
- navigation / control capability；
- native perception data；
- explicitly registered translation policy；
- known special process evidence。

## 11.2 Bake 输出

可能生成或绑定：

```text
ActorDefinition
AbilityDefinition refs / grant templates
AiDefinition
Perception contract / profile
Native execution contract refs
Compatibility metadata
```

Bake 不应：

- 执行技能；
- 扣资源；
- 修改实体；
- 启动导航；
- 推进 Brain；
- 调用未知 Goal；
- 产生当前回合 Action plan。

---

# 12. Bake 时机

“Bake”表示语义转换，不强制等同某一个生命周期事件。

可接受的实际时机包括：

- registry / datapack load；
- target initialization；
- compatibility provider registration；
- server startup；
- first-use + versioned cache；
- 明确的 reload boundary。

关键约束是：

```text
Bake Once / On Semantic Revision
        ↓
Canonical Representation
        ↓
Runtime repeatedly consumes canonical data
```

而不是：

```text
Every Turn
        ↓
Re-inspect Minecraft implementation
```

---

# 13. Bake Provenance 与 Compatibility Evidence

06 已经区分：

```text
explicit provider
audited vanilla family
structural candidate
unknown
```

该概念应被保留，并进入 definition / bake diagnostics。

暂定：

```text
DefinitionProvenance
```

可记录：

```text
sourceKind
sourceId
sourceVersion
translationPolicyId
translationPolicyVersion
auditedDependencyRange
confidence / evidence class if needed
unsupported dimensions
```

例如：

```text
AiDefinition: dndturn:skeleton_standard
sourceKind: VANILLA_AUDITED_TRANSLATION
sourceVersion: minecraft-26.x
translationPolicy: dndturn:skeleton_ai_v1
```

或：

```text
sourceKind: EXPLICIT_PROVIDER
provider: some_mod:dndturn_adapter
```

或：

```text
sourceKind: GENERIC_FALLBACK
```

Provenance 主要用于：

- debug；
- compatibility report；
- source audit；
- reload/version checks；
- Codex inspection；
- 明确区分 declared / inferred / fallback。

不得将低证据 inference 静默表现成完全支持。

---

# 14. Vanilla / Mod AI 接入层级

## 14.1 Tier A：Explicit Provider

最可靠。

外部 Mod 或 DNDTurn 内置 adapter 明确提供：

```text
Actor definition contribution
Ability grants / definitions
AiDefinition
Perception contract
Native execution ports
```

仍必须经过 DND Rule 验证。

---

## 14.2 Tier B：Audited Native Family Translation

对明确审计过的原版/模组行为族建立翻译政策，例如：

- 标准近战；
- 标准 ranged；
- 已知 target family；
- 已知 navigation family；
- 已审计 perception source；
- 特定特殊过程。

Goal/Brain 在这一层只作为证据，不进入 Common Runtime。

---

## 14.3 Tier C：Structural Candidate

结构特征可以产生：

```text
BehaviorCandidate
CompatibilityEvidence
```

但不能自动形成完整 `AiDefinition` 或能力授权。

需要：

- explicit translation policy；
- audited family match；
- 或保守 fallback。

---

## 14.4 Tier D：Generic Fallback

对于无法可靠解释原生 AI 的 Mob，可以仅根据已经确定的：

- Actor facts；
- movement support；
- valid AbilityBindings；
- perception support；
- DND relation；
- AiAffordance

使用 Common Generic AI。

Fallback 的语义必须显式。

它不表示：

> 已兼容原 Mob 的完整原生 AI。

而只表示：

> 该实体可以在当前已支持的 DND 能力集合上使用通用战术策略。

---

# 15. Unknown / Unsupported AI 的处理

禁止：

- 静默降级为 Zombie；
- 通过类名猜测敌意；
- 试跑 Goal；
- 执行整个 `Mob.tick()` 获取行为；
- 无限制开放 Brain；
- 把无法理解的能力当免费动作；
- 把未知世界副作用当作合法附带行为。

允许的保守结果：

```text
participant supported
movement supported
ability partially supported
strategy unsupported
```

或：

```text
generic fallback strategy
```

但必须在 compatibility status 中明确区分。

---

# 16. Ownership 详细表

| 数据 / 职责 | 权威 Owner | 生命周期 | 不应由谁持有 |
|---|---|---|---|
| AI archetype/profile | `AiDefinition` | definition version | live Goal/Brain |
| Actor 与 AI profile 的默认关联 | `ActorDefinition` | definition version | current Encounter |
| Actor 当前能力集合 | Capability/Grant/Binding 层 | runtime revision | `AiDefinition` |
| Ability 战术意义 | `AbilityDefinition` 的 AI semantic projection | definition version | AI runtime |
| 当前可见/已感知对象 | `PerceptionSnapshot` | perception revision | global world view |
| AI 私有记忆 | `AiRuntimeState` | encounter/runtime | ActorDefinition |
| 敌对关系 | Encounter / relation authority | encounter revision | AI private memory |
| Action/Reaction/Movement | Encounter participant authority | turn/encounter | AI runtime |
| 当前是否合法 | Rule Interpreter | per invocation | AI Planner |
| 当前行动提案 | AI Strategy / Planner output | one decision | Capability registry |
| 原生行为识别 | target adapter / bake layer | semantic revision | Common runtime |
| Goal/Brain 对象 | Minecraft target | native lifetime | Common/persistence |
| Navigation/control ownership | target execution / lease | execution lifetime | AiDefinition |
| 世界修改 | audited Native Executor | execution permit | Planner |
| AI Definition 来源证据 | bake/compat metadata | definition version | UI-only state |

---

# 17. 与 Actor Snapshot 模型的关系

AI 模型遵守 Actor 数据层已经确定的 Snapshot 原则：

> Snapshot 是规则/决策读取时的权威视图，不是所有长期状态的唯一存储 Owner。

因此：

```text
ActorDefinition
+ ActorPersistentState
+ ActorRuntimeState
+ Grants / Conditions
+ Native Fact Slice
        ↓
ActorSnapshot
```

AI 进一步消费：

```text
ActorSnapshot
+ EncounterParticipantState
+ PerceptionSnapshot
+ AbilityBindings
+ AiRuntimeState read view
+ AiDefinition
        ↓
AiDecisionContext
```

AI 不直接读取 live Entity 来绕过 Snapshot。

特殊执行阶段若必须读取 native state，属于 target adapter / executor 的复验职责，不属于 Planner。

---

# 18. Revision / Invalidation 原则

AI bake 与 runtime snapshot 必须使用不同 revision 语义。

## 18.1 Definition revision

以下变化可能使 Definition bake 失效：

- loaded mod/version 变化；
- provider semantic version 变化；
- translation policy 变化；
- data pack / definition reload；
- audited behavior family version 变化。

## 18.2 Runtime revision

以下变化通常只使 Snapshot / DecisionContext 失效：

- 装备变化；
- 状态变化；
- HP/resource 变化；
- Actor position / pose 变化；
- perception 变化；
- Encounter phase/turn 变化；
- Ability grant 变化。

原则：

```text
runtime state change
    ≠ rebake AiDefinition
```

例如拿剑换弓，通常应该改变：

```text
AbilityBindings
Availability
Positioning opportunity
```

而不是重新解析 Goal/Brain。

---

# 19. AI 与装备/学习能力的关系

`AiDefinition` 不写死 Actor 的全部动作列表。

例如：

```text
Bandit A: sword
Bandit B: bow
Bandit C: learned spell
```

三者可以共享相同 `AiDefinition`。

实际 Candidate Generation 来源于：

```text
current AbilityBindings
        +
AiAffordance
        +
current Availability
```

因此：

```text
AiDefinition
    决定偏好

AbilityBinding
    决定拥有

Availability
    决定当前候选
```

这样装备、饰品、学习能力、状态 grant 都无需修改 AI archetype 本身。

---

# 20. Candidate Generation 与 Scoring

推荐运行链：

```text
AiDecisionContext
        ↓
Candidate Generator
        ↓
legal-ish tactical proposals
        ↓
Evaluator / Scoring / Planner
        ↓
selected proposal
        ↓
AbilityInvocation
        ↓
Rule Interpreter final validation
```

注意 Candidate Generation 可以使用：

- current Availability；
- range / target contracts；
- perception；
- affordance；
- spatial facts；
- known rule projection。

但最终 Rule Interpreter 仍然必须复验。

AI 的“认为合法”不是执行许可。

---

# 21. 规划算法不是本文件的固定目标

本文不固定 Common Planner 必须采用：

- Utility AI；
- GOAP；
- Behavior Tree；
- MCTS；
- Minimax；
- HTN；
- Rule-based scoring。

首阶段更重要的是固定：

```text
input contract
output contract
ownership
snapshot semantics
capability semantics
```

Planner 实现可以更换，只要不破坏这些边界。

因此 `plannerPolicy` 可以是稳定 ID / strategy reference，而不是把整个算法状态塞进 Definition。

---

# 22. 计算预算与可恢复决策

继承 06 已经指出的 AI 捕获预算问题。

对于大量 Actor / Ability / Target 组合，不允许：

```text
每 tick 超预算
    ↓
丢弃全部进度
    ↓
下一 tick 从头扫描
```

允许：

- bounded candidate generation；
- resumable cursor；
- bounded planner continuation；
- fair actor scheduling；
- revision-aware partial invalidation。

若相关 snapshot revision 未变化，可以继续计算。

若相关事实变化，只失效相关部分。

AI budget exhaustion 必须可诊断。

不得因为计算预算耗尽：

- 修改游戏资源；
- 自动结束合法已接受行动；
- 执行未验证 fallback；
- 无限创建新的 operation。

---

# 23. Randomness

AI 随机性必须与规则随机性分离。

例如：

```text
AI preference random
    ≠ attack roll random
```

查询、UI preview、能力发现不能消费会影响：

- 命中；
- 伤害；
- 规则检定

的随机序列。

AiRuntimeState 可以拥有独立、可控、可测试的 decision random state。

---

# 24. Persistence 原则

默认不持久化：

- Goal 对象；
- Brain 对象；
- Path 对象；
- Navigation controller；
- live Entity reference；
- lambda / callback；
- planner 内部任意对象图。

允许持久化的 AI 状态必须是：

- 有版本；
- bounded；
- pure value；
- 有明确恢复语义。

例如：

```text
lastKnownTargetId
lastKnownCell
focusTargetId
plannerPhaseKey
bounded retry evidence
```

是否需要持久化某项 AI memory，后续按玩法需求决定；本文不要求全部 AI memory 跨 server restart。

---

# 25. Environment Turn 与 AI

Environment Turn 是 Encounter 中的虚拟参与者/调度职责，不自动等同 Mob AI。

它负责：

- outcome-changing time progression；
- scheduled environmental processes；
- 已定义环境拥有的效果。

Mob AI 负责：

- 为 Actor 选择战术意图。

二者不能混合为：

```text
Environment Turn = tick all Mob AI
```

环境过程与 Actor 决策继续使用不同 ownership 和 scheduler。

---

# 26. 非目标 / 防误读

本文**不要求立即**：

1. 重写所有 `MobTurnStrategies`；
2. 删除现有 Goal/Brain；
3. 用 Common AI 接管非战斗区原版 AI；
4. 实现完整 DND 怪物策略库；
5. 自动兼容所有 Mod Mob；
6. 把所有 AI 数据 JSON 化；
7. 实现新的 Behavior Tree DSL；
8. 实现复杂 GOAP / MCTS；
9. 把 Vanilla 世界状态复制到 Common；
10. 把 `AiDefinition` 做成超大万能 record；
11. 现在就固定所有字段命名；
12. 因 AI bake 存在而跳过运行时来源/规则复验。

本文首先固定语义边界。

---

# 27. Common 层长期不变量

以下原则应作为后续实现审查的长期不变量：

1. Common AI 不依赖 Minecraft / NeoForge 类型。
2. Goal / Brain 不跨入 Common Runtime。
3. Planner 不修改世界。
4. Planner 不拥有规则资源。
5. AI Invocation 必须通过统一 Rule Interpreter。
6. Ability ownership 不由 AI 推断。
7. AI preference 不授予 Ability。
8. Perception 与 server-global knowledge 分离。
9. Relation 与 perception 分离。
10. Preference 与 legality 分离。
11. `AiDefinition` 是 cold semantic data。
12. `AiRuntimeState` 是独立 hot state。
13. Runtime state 变化通常不触发 re-bake。
14. Bake 输出必须有 semantic version / provenance。
15. Unknown behavior 不通过试运行发现。
16. Class/method name 不单独形成 gameplay authority。
17. Fallback 必须显式标记。
18. Generic AI 只能使用当前已确认的 AbilityBindings。
19. Snapshot/DecisionContext 是运行时读取边界。
20. Execution 继续由受限 Native Execution Port 完成。
21. AI random 与 rule random 分离。
22. AI query 不产生世界副作用。
23. AI runtime 不持久化原始 Goal/Brain/Path。
24. Capability / Strategy / Perception / Execution 的支持维度独立。
25. 一个 Mob “可入场”不等于“完整 AI 已兼容”。

---

# 28. 粗粒度 Vanilla AI Adapter 接入面

本文暂不绑定具体源码 hook，仅记录后续源码审计方向。

后续 Vanilla Adapter 文档至少需要细化以下类别：

## 28.1 Definition / Archetype Evidence

用于确定：

- entity identity / type；
- variant；
- attributes；
- body / movement capabilities；
- default equipment or components；
- native AI family evidence。

## 28.2 GoalSelector Family

审计：

- 哪些 Goal 类型可以安全读取配置；
- 哪些字段是 pure configuration；
- 哪些方法具有副作用；
- priority / selector 结构是否可作为候选证据；
- Goal inheritance 是否被 mod mixin 改义。

只用于：

```text
BehaviorCandidate / CompatibilityEvidence
```

## 28.3 Brain / Sensor / Memory

审计：

- sensors；
- memory modules；
- activity / behavior configuration；
- 哪些信息可以作为 perception/target preference evidence；
- 哪些 memory 是 runtime mutable state，禁止进入 cold definition；
- 哪些 tick/behavior 调用有副作用。

## 28.4 Navigation / Controls

AI Definition 不拥有 Navigation。

后续审计：

- ground / swim / fly / climb movement evidence；
- navigation execution ports；
- move/look/jump control ownership；
- lease/gating integration。

## 28.5 Targeting / Perception

后续审计：

- vanilla line of sight；
- sensing；
- FOLLOW_RANGE；
- current target；
- nearest target goal；
- teams / owner relations；
- Brain perception memories。

要求映射到：

```text
Perception facts
Relation facts
Preference evidence
```

不能简单映射为：

```text
attack permission
```

## 28.6 Special AI Processes

例如：

- Creeper swell/fuse；
- Guardian beam；
- Enderman teleport；
- Elder Guardian periodic effect；
- charging / channeling / staged attacks；
- modded boss mechanics。

后续需要区分：

```text
AI decides to start ability
```

和：

```text
ability execution owns staged native process
```

避免 Planner 持有原生过程生命周期。

---

# 29. 与 05 / 06 的职责映射

## 29.1 05 Capability Framework 继续拥有

```text
AbilityDefinition
AbilityBinding
Availability
AbilityInvocation
ExecutionPermit
AbilityExecution
统一玩家/AI规范提交入口
AI Strategy output contract
```

本文不重新定义这些语义。

## 29.2 06 Mob Native Translation 继续拥有

```text
NativeMobFacts
BehaviorCandidate
CompatibilityEvidence
NativeBehaviorInspector
MobAbilityTranslator
NativeExecutionPort
CompatibilityResolver
```

本文新增的 `AiDefinition bake` 应使用这些已有 translation/evidence 机制，而不是建立第二套独立 Mob scanner。

## 29.3 本文新增职责

```text
AiDefinition
Definition-level AI bake
AiRuntimeState boundary
PerceptionSnapshot boundary
AiDecisionContext
AI-facing Ability Affordance
Definition provenance
Common planner architecture contract
```

---

# 30. 后续源码审计应回答的问题

后续要求 Codex 基于实际 Minecraft / NeoForge 源码细化 AI Adapter 时，应至少回答：

1. Vanilla `GoalSelector` 的配置与运行状态分别存放在哪里？
2. 哪些 Goal 构造参数/字段可以纯读取？
3. 哪些 Goal 方法会修改导航、target、cooldown、memory 或随机状态？
4. Goal priority / flag 能否稳定作为行为候选证据？
5. Brain 的 sensor / memory / activity 中哪些适合映射 perception？
6. 哪些 Brain 信息属于 hot runtime state，不能 bake？
7. Vanilla current target 的 authority 与 Goal / Brain memory 如何交互？
8. `FOLLOW_RANGE`、LOS 与 Sensor 在不同 Mob 中如何组合？
9. Navigation / MoveControl / LookControl / JumpControl 的执行 ownership 如何安全 lease？
10. `customServerAiStep` 中有哪些行为绕开 Goal/Brain？
11. 特殊攻击有哪些“决策入口”和“执行过程”不是同一个方法？
12. 哪些原生行为可以映射为标准 `AbilityDefinition`？
13. 哪些只能产生 `BehaviorCandidate`？
14. 哪些需要显式 provider 才能支持？
15. EntityType、Goal family、Brain family、component/config 哪一种最适合作为 bake cache key？
16. Mod Mixin/override 如何使已审计 family evidence 失效？
17. 如何记录 dependency/version provenance？
18. 哪些事实应在 definition load 时 bake，哪些必须在 actor runtime capture？
19. 当前源码中 AI candidate budget 从头重算的具体位置在哪里？
20. 如何在不改变现有规则的情况下插入 definition cache 与 resumable candidate generation？

---

# 31. 临时命名表

| 当前语义 | 暂定名 | 备注 |
|---|---|---|
| AI 冷定义 | `AiDefinition` | 推荐保留 |
| AI 动态记忆 | `AiRuntimeState` | 推荐保留 |
| AI 可见/已知事实 | `PerceptionSnapshot` | 推荐保留 |
| 一次决策输入 | `AiDecisionContext` | 可考虑 `DecisionView` 延续现有 05 命名 |
| 能力的 AI 战术语义 | `AiAffordance` | 名称可商榷 |
| bake 来源与证据 | `DefinitionProvenance` | 名称可商榷 |
| AI profile 引用 | `AiDefinitionRef` | 也可直接使用 namespaced ID |
| 原生行为候选 | `BehaviorCandidate` | 继承 06 |
| 原生兼容证据 | `CompatibilityEvidence` | 继承 06 |
| 通用回退策略 | `GenericCombatAi` / `GenericAiDefinition` | 仅语义示例 |

命名应在源码落地前结合现有 package/class 统一，不要求为满足本文立即增加空壳类型。

---

# 32. 当前阶段结论

当前 DNDTurn 的 AI 架构不需要推翻 05 / 06。

现有结构已经拥有：

```text
Capability semantics
统一 player/AI invocation path
Strategy runtime boundary
Native behavior evidence
Mob translation layer
Native execution boundary
Compatibility dimensions
```

真正缺失的是：

```text
Native translation
        ↓
canonical cold AI definition
        ↓
runtime snapshot-based decision
```

因此长期模型确定为：

```text
Native implementation
        ↓ bake / translate
ActorDefinition + AbilityDefinition + AiDefinition
        ↓
Snapshots + Bindings + Perception + Encounter State
        ↓
Common AI Planner
        ↓
AbilityInvocation
        ↓
Common Rule Interpreter
        ↓
Native Executor
```

核心原则可以概括为：

> **DNDTurn 不在每个回合重新解释 Minecraft AI；Minecraft AI 只在适配层提供事实与证据。Common AI 基于稳定 Definition 和当前 Snapshot 进行决策。**

