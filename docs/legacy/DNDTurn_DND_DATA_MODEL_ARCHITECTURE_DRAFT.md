> 本文保留原任务／设计来源，不作为当前进度表；已整合的剩余工作统一见[未完成既定目标](02_GAPS_AND_CONFLICTS.md)。历史任务措辞不表示相关基础仍未实现。

# DNDTurn DND 数据模型与规则层目标架构（草案）

> 状态：目标架构草案，不是当前实现说明，不是迁移任务单，也不是源码级接入点文档。  
> 目的：在继续扩大 vanilla / 第三方模组兼容之前，先固定 DNDTurn 对“角色、能力、规则事实、会话状态和世界执行”的长期语义边界。  
> 后续：Minecraft 固定版本的具体类、方法、事件、Mixin、调用顺序与验证场景，应另行基于 patched 源码逐项细化；本文件只定义它们最终需要向 common 提供什么。

---

## 1. 背景与问题

DNDTurn 当前已经具备 Encounter、行动计划、能力发现、原版执行、结果观察、环境回合等大量运行时设施，但随着支持范围扩大，vanilla 兼容开始越来越依赖“在规则需要时直接读取 Minecraft 对象并现场解释其含义”。这种方式可以快速支持单个行为，却难以形成长期稳定的 DND 风格规则基础设施。

Minecraft 的数据和行为天然是分散的：属性来自 Attribute，伤害语义来自攻击入口和 DamageSource，能力可能来自实体类型、Goal/Brain、物品、状态、组件、附魔或第三方模组接口；装备、位置、姿态、库存、效果和世界状态又分别拥有不同生命周期。DNDTurn 不应试图消除这种分散性，也不应在 target 层重新复制一套 Minecraft。

目标应当是建立一条明确的语义收敛链：

```text
Minecraft / Mod 原生事实
        ↓
薄、点对点的 Native Adapter / Fact Provider
        ↓
DNDTurn canonical facts + DND Actor model
        ↓
不可变 Rule Snapshot
        ↓
DND Rule Interpreter / Planner
        ↓
Effect / Execution Request
        ↓
Minecraft / Mod Executor
        ↓
Typed Observation / Result
        ↓
规则状态与 Encounter 状态更新
        ↓
新的 Snapshot revision
        ↓
UI / AI / Character Sheet / Spellbook / Inspection 等投影视图
```

核心改变不是“增加更多 DND 数值”，而是让 common 首先拥有一套稳定语言，用于回答：

1. 一个角色**是什么**；
2. 当前角色**拥有什么能力以及为什么拥有**；
3. 某项规则**允许读取哪些事实**；
4. 某次行动在一个确定 revision 上**是否合法、需要什么资源、预期产生什么效果**；
5. 哪些事实归 Actor、Encounter、Minecraft World 或执行适配器所有；
6. 玩家、AI、UI 与网络看到的是哪种投影，而不是重新从 Minecraft 对象各自推导一遍。

---

## 2. 本文件的粗略目标

### 2.1 建立长期稳定的 common 数据语义

`common/` 应逐步承载与 Minecraft 版本无关的：

- Actor definition / actor state 的纯值模型；
- typed stat/resource/trait/condition/ability identifiers；
- Ability Definition、Grant、Binding、Invocation 等能力语义；
- Actor Snapshot、Encounter Participant Snapshot、Resolution Context 等不可变读取模型；
- 规则所需事实的声明式 Read Contract / Fact Requirement；
- 纯规则解释、合法性判断、费用与效果描述；
- 可独立单元测试的 reducer / resolver / projection。

`common` 不应知道 `LivingEntity`、`ItemStack`、`Level`、Goal、Brain、NeoForge event、Mixin 或平台 Codec。

### 2.2 让 Snapshot 成为规则裁决的唯一读取入口

规则层不再在执行过程中任意回读 Minecraft 对象：

```text
Rule / AI scoring / preview
    ×  LivingEntity.getAttribute(...)
    ×  ItemStack / MobEffect / ServerLevel 现场读取

Rule / AI scoring / preview
    ✓  ActorSnapshot / CombatantSnapshot / ResolutionContext
```

但这里的“唯一权威”必须严格限定：

> **Snapshot is authoritative for rule resolution, not authoritative for storage.**  
> Snapshot 是裁决某个 revision 时的唯一权威输入，不是 Minecraft 世界与所有长期状态的永久主副本。

### 2.3 把原版兼容问题收敛到 adapter，而不是扩散进规则

Minecraft 数据本来就散乱，因此 target 的采集可以散、可以点对点、可以版本特化；但每个 adapter 只负责把原生事实翻译成 canonical fact，或者把 common 产生的 execution request 翻译成原生执行。

Adapter 不应重新解释 DND 规则，也不应拥有动作费用、先攻、回合许可等核心规则状态。

### 2.4 为第三方模组保留稳定扩展面

第三方能力不要求全部伪装成 vanilla Item 或 vanilla Mob，也不要求 DNDTurn 核心不断加入物种/物品特判。扩展应围绕以下稳定轴发生：

- Fact Provider：提供额外 canonical facts；
- Actor Definition Provider：给某类实体提供身份/基础定义；
- Ability Grant Provider：声明某 actor 当前因何获得某能力；
- Ability Definition / Rule Resolver：定义规则语义；
- Native Executor：执行无法由通用 vanilla driver 表达的世界行为；
- Observation Adapter：返回可核验的效果证据；
- Presentation Adapter：独立提供客户端表现。

---

## 3. 明确的非目标

本架构草案**不要求**：

1. 现在立即重写 `TacticalBehavior`、`CombatEngine` 或现有执行链；
2. 把所有能力强制改写成 JSON、数据包或一个自制脚本 DSL；
3. 完整复刻某一版桌面 D&D 规则或立即决定 STR/DEX/AC 的最终数值公式；
4. 把 Minecraft 世界事实迁移到 common 作为第二套世界数据库；
5. 把 `ActorSnapshot` 当作存档格式、可变实体对象或永久 source of truth；
6. 声称通过 Fact Contract 可以安全沙箱化同 JVM 中的任意第三方模组；
7. 在本文件中固定 NeoForge 26.1/26.2 的最终 hook 方法、事件、Mixin 位置或调用顺序；
8. 为了“看起来分层”而新增没有 ownership 改变的 facade、manager 或 wrapper。

本文件首先固定**语义和所有权**。具体类名、包名与迁移顺序可以后续变化。

---

# 4. 理想架构总览

建议把长期模型理解为四个不同问题：

```text
A. Actor 是什么？
B. Actor 当前处于什么状态？
C. 在本次 Encounter / World Context 中规则看到了什么？
D. 规则决定之后，Minecraft 实际发生了什么？
```

对应架构：

```text
                    Minecraft / Mods
                          │
                          │ native observations
                          ▼
                ┌─────────────────────┐
                │ Native Fact Adapter │   target-owned
                └─────────────────────┘
                          │
                    Canonical Facts
                          │
        ┌─────────────────┼──────────────────────┐
        │                 │                      │
        ▼                 ▼                      ▼
 ActorDefinition   ActorPersistentState   ActorRuntimeState
      cold                warm                   hot
        │                 │                      │
        └──────────┬──────┴──────────────┬───────┘
                   │                     │
              Ability Grants       Conditions / Modifiers
                   │                     │
                   └──────────┬──────────┘
                              ▼
                     Snapshot Compiler
                              │
                              ▼
                        ActorSnapshot
                              │
                  ┌───────────┴───────────┐
                  │                       │
                  ▼                       ▼
      EncounterParticipantState      World Fact Slice
                  │                       │
                  └───────────┬───────────┘
                              ▼
                    ResolutionContext
                              │
                              ▼
                    DND Rule Interpreter
                              │
                       ResolutionPlan
                              │
                              ▼
                     Native Executor
                              │
                              ▼
                    Typed Observations
                              │
                    ┌─────────┴──────────┐
                    ▼                    ▼
             Actor/Grant State     Encounter State
                    │                    │
                    └─────────┬──────────┘
                              ▼
                      next revision
```

UI、AI、法术书、属性面板和调查系统位于这条链的**读取侧**，不再成为独立状态所有者：

```text
ActorSnapshot / Resolution-safe projections
             │
      ┌──────┼─────────┬───────────┐
      ▼      ▼         ▼           ▼
 Character  Spellbook  AI Input   Inspection
 Sheet                            + visibility policy
```

---

# 5. Actor 数据模型

## 5.1 ActorDefinition：冷定义，而不是运行时角色对象

`ActorDefinition` 表达“这一类/这个定义的 Actor 在 DNDTurn 规则语言中是什么”。它应当倾向于不可变并带版本。

可能包含：

- definition ID / version；
- identity groups / tags；
- 默认基础 stats；
- 默认 resource definitions；
- intrinsic ability grant templates；
- body / movement / targeting 的规则级分类；
- 与具体规则集相关但不会在每次行动中随意变化的基础配置。

它不应包含：

- 当前 Entity 实例引用；
- 当前位置；
- 当前 HP / action resource；
- 当前装备；
- 当前 Encounter initiative；
- 当前临时状态；
- Minecraft `EntityType`、`Holder` 等 target 类型。

### 不应把 Definition 设计成封闭巨型 record

长期稳定的应该是 schema 和 typed key 体系，而不是一次把所有未来字段写死：

```text
StatKey
ResourceKey
TraitKey
AbilityId
ConditionId
ActorGroupId
```

例如标准 DNDTurn 可以定义：

```text
dndturn:strength
dndturn:dexterity
dndturn:armor_class
dndturn:movement
```

第三方模组仍可扩展：

```text
some_mod:psionic_power
some_mod:heat_capacity
```

因此 `ActorDefinition` 应是长期稳定的**组合结构**，不是“核心维护一个永远扩大的字段表”。

---

## 5.2 ActorPersistentState：角色长期学习/成长所有权

这一层保存“这个具体 Actor 跨 Encounter、跨重新加载仍应拥有的 DNDTurn 状态”。

典型内容：

- 学习所得 ability grants；
- progression / identity choices；
- 永久解锁；
- prepared / selected loadout（若设计为跨会话持久）；
- DNDTurn 自己定义且明确需要持久化的资源或选择。

典型例子：

```text
玩家读卷轴学会 Fireball

scroll item       = 已消耗的历史来源
AbilityGrant      = ActorPersistentState 所有
ability definition= dndturn:fireball
当前是否仍持有卷轴 = 与该 grant 无关
```

这说明“能力来源”不能只是当前 provider 引用。**历史来源、当前生命周期 owner、当前有效性证据必须可区分。**

---

## 5.3 ActorRuntimeState：Actor 级热状态，但不是 Encounter 状态

这一层只保存 DNDTurn 自己拥有、与具体 Encounter 不等价的可变角色状态。

可能包括：

- DNDTurn 定义的当前资源值；
- 跨 Encounter 仍然持续的 condition instances；
- 具有 duration / stack / source identity 的 rule modifiers；
- 非 Encounter 专属的短期 grant；
- 与规则 revision 相关的动态状态。

是否把 HP、冷却、魔法槽等放在这里，应取决于**真实 owner**：

- 如果 Minecraft/第三方模组仍是该事实的实际 owner，则 ActorRuntimeState 不应复制第二份；由 adapter 采集为 canonical fact。
- 如果 DNDTurn 正式接管该规则状态，则由 ActorRuntimeState 持有，并通过明确 executor/bridge 映射到世界表现。

原则不是“所有热数据都塞进 ActorRuntimeState”，而是：

> **一个可裁决事实只能有一个写 owner。**

---

## 5.4 Modifier / Condition 不等于一个通用 `Modify(hot)`

原始公式：

```text
Actor Base(cold) + Actor Modify(hot) = Actor Snapshot
```

作为直觉成立，但长期实现不应只有一个万能 `Modify` 容器，因为不同变化来源拥有完全不同的生命周期和撤销条件。

至少应概念上区分：

- Persistent state：永久学习/成长；
- Runtime resource：当前值；
- Modifier instance：对已有 stat 的有来源修正；
- Condition instance：具有状态语义、持续期、触发时机；
- Ability grant：授予能力本身；
- Equipment-derived fact/grant：随当前装备证据存在；
- Encounter participant state：只在某场 Encounter 内存在。

它们最后都可以参与 Snapshot 编译，但不能因此拥有同一 lifecycle owner。

---

# 6. Snapshot：规则权威输入，而不是永久状态容器

## 6.1 ActorSnapshot 的核心语义

`ActorSnapshot` 应当是不可变、可版本化、脱离 Minecraft 对象的纯值对象。

它回答：

> “在 revision N 上，DNDTurn 规则允许把这个 Actor 当成什么？”

Snapshot 可以包含已经归一化后的：

- effective stats；
- resource read view；
- body / movement rule facts；
- conditions / modifiers 的有效结果及必要来源信息；
- 当前可用 grants / capability references；
- 规则所需的 native facts；
- definition/ruleset/revision identity。

规则、preview、AI scoring、character sheet 等不应继续自行回读 native object 后重新解释一遍。

## 6.2 Snapshot 不拥有存储权

不要采用：

```text
Minecraft world → 同步到 mutable ActorSnapshot → 所有人读写 Snapshot
```

建议采用：

```text
真实 owner state
      ↓ capture / reduce / resolve
ActorSnapshot revision N
      ↓ pure rule resolution
result / execution request
      ↓
真实 owner state changes
      ↓
ActorSnapshot revision N+1
```

旧 Snapshot 不回写、不变异；它可以与 operation/result 一起保留，用于复核为什么某次规则得出了某个结论。

## 6.3 Snapshot 不是每个 Minecraft tick 的全量复制

“行为发生后 Snapshot 更新”应理解为**revision 失效与重新编译**，而不是强制每个 tick 全量重建全 Actor 数据。

长期允许：

```text
native/state mutation
    ↓
relevant fact keys marked dirty / revision advanced
    ↓
下次规则边界需要读取
    ↓
增量或缓存式 Snapshot compile
```

优化策略可以以后决定，但规则语义必须保证：

- 规则只对一个明确 revision 做裁决；
- 执行前必要事实可再次核验；
- 同一个 resolution step 不混读两个 revision；
- 旧 Snapshot 永不被原位修改。

---

# 7. EncounterParticipantState：为什么先攻不能塞进 ActorSnapshot

Encounter 是独立 owner。

当前/未来以下状态应由 Encounter participant 所有，而不是 Actor 本体：

- initiative roll / initiative result；
- initiative tie-break；
- 当前 round / eligible round；
- Action / Reaction / movement budget；
- dodge / disengage 等 Encounter 状态；
- 当前 turn ownership；
- Encounter 内敌对关系与参与关系；
- Encounter-specific temporary rights / leases（按现有架构合同另行管理）。

ActorSnapshot 可以提供：

```text
initiative modifier
```

Encounter 在入场时根据规则产生：

```text
initiative roll/result
```

之后即使 Actor 的 DEX 或 initiative modifier 改变，也不会自动改写已经产生的先攻结果，除非规则明确有“重投/重算先攻”的效果。

因此：

```text
ActorSnapshot               = Actor 在规则上的当前事实
EncounterParticipantState   = Actor 在某次 Encounter 中的会话事实
CombatantSnapshot           = 两者在某个裁决边界上的只读组合
```

这也避免角色同时存在于世界与不同历史 Encounter 记录时，Actor 自己被迫持有某一个会话的临时字段。

---

# 8. World Fact Slice / ResolutionContext

并非所有规则事实都属于 Actor。

攻击、施法、移动和交互通常还需要：

- source / target 的距离；
- line of sight / line of effect；
- block / fluid / collision facts；
- dimension；
- loaded / unresolved state；
- target relation；
- 当前 Encounter phase；
- 当前规则版本；
- 环境 process / domain 的必要信息。

不要把这些为了方便全部复制进 `ActorSnapshot`。

更合理的是：

```text
ResolutionContext
    actor: ActorSnapshot / CombatantSnapshot
    target: optional ActorSnapshot / target facts
    encounter: Encounter read view
    world: bounded WorldFactSlice
    invocation: AbilityInvocation
    ruleset/version
```

`ResolutionContext` 是一次 rule evaluation 的完整、纯值输入边界。

世界查询必须有界；不得为了一个 Ability 自动捕获整片世界。能力通过 Fact Requirement 声明它需要什么，adapter 只构造必要 slice。

---

# 9. 能力模型：Definition / Grant / Binding / Invocation

“能力”不能继续同时代表：定义、来源、Actor 是否拥有、当前是否可用、如何执行和某次调用。

建议至少分为四层。

## 9.1 AbilityDefinition：能力是什么

`AbilityDefinition` 是稳定、带 ID/version 的 common 规则定义。

至少可以描述：

- Ability ID / version；
- rule tags / categories；
- ActivationSpec；
- TargetSpec；
- CostSpec；
- FactRequirements / ReadContract；
- 规则 resolver / effect semantics；
- 必要的 execution driver kind / adapter contract；
- 可选的 presentation metadata（不影响规则）。

例如 Fireball：

```text
id: dndturn:fireball
activation: MANUAL / ACTION
selector: AREA
range: ...
requirements:
  actor spellcasting stats
  actor resources
  world range/line-of-effect facts
resolution:
  saving throw + fire damage + area effect
execution contract:
  apply typed damage/effect requests
```

### AbilityDefinition 不等于必须 JSON 化

简单能力可以高度数据化；复杂能力可以由 common 中注册的纯 resolver 实现。长期目标是稳定的数据契约，而不是为了“数据驱动”创造一个难以维护的万能脚本语言。

---

## 9.2 AbilityGrant：为什么这个 Actor 拥有能力

一个 Actor 可以通过不同来源得到同一个 AbilityDefinition。

典型来源：

- `INTRINSIC / IDENTITY`：来自 ActorDefinition、种族/职业/身份组；
- `LEARNED`：学习、卷轴、升级、永久解锁；
- `EQUIPMENT`：当前主手、副手、护甲、饰品等；
- `CONDITION / STATUS`：Buff、Debuff、形态、变身；
- `ENCOUNTER`：特定 Encounter 临时授予；
- `EXTERNAL_PROVIDER`：第三方模组或脚本化场景显式提供。

但 `source` 一个字段仍然不足。建议概念上拆成：

### GrantOrigin

回答：**它最初为什么出现？**

例如：

```text
learned_from_scroll
identity_group
main_hand_item
status:blessing
```

### GrantOwner

回答：**谁负责这个 grant 的生命周期？**

例如：

```text
ActorPersistentState
EquipmentDerivedGrantProvider
ConditionInstance
EncounterParticipantState
ExternalProvider
```

### GrantEvidence

回答：**当前凭什么确认它仍有效？**

例如：

```text
persistent grant UUID + revision
equipment slot + item revision
condition instance UUID + condition revision
encounter id + participant revision
provider id + provider version
```

例一：读卷轴学习 Fireball：

```text
origin       = learned_from_scroll
owner        = ActorPersistentState
live evidence= persistent grant revision
scroll       = 不再是当前依赖
```

例二：戒指提供 Fireball：

```text
origin       = equipment
owner        = equipment grant provider
live evidence= accessory slot + item instance/revision
```

这两个能力在 UI 上都叫 Fireball，但生命周期完全不同。

---

## 9.3 AbilityBinding：当前 Actor 可调用的绑定

`AbilityBinding` 是 `AbilityDefinition + AbilityGrant + 当前必要参数` 的解析结果。

它回答：

> “当前这个 Actor 通过哪个 grant，可以提出对哪个 AbilityDefinition 的调用？”

Binding 应保持纯值，不持有 `ItemStack` 或 `LivingEntity`。

同一 AbilityDefinition 可以同时有多个 Binding；例如同一个法术既来自角色学习，也来自装备。是否在 UI 合并显示，是 projection policy，不应在底层丢失 provenance。

---

## 9.4 AbilityInvocation：一次明确行动请求

Invocation 是 Actor 选择某个 Binding 后，对明确目标和必要参数提出的一次意图。

可以包含：

- actor id；
- binding / ability id + versions；
- target selector result；
- optional approach proposal；
- user-selected variants / mode；
- captured revision references；
- operation identity（按现有 Operation 合同）。

Invocation 不是执行许可，也不应携带客户端声称的最终伤害、最终费用或最终位置。

---

# 10. Active / Passive 不应是核心二元类型

“主动/被动”适合 UI 分类，但不足以定义运行语义。

一个能力可能是：

```text
手动主动技能
被动 stat modifier
OnHit 触发
OnDamaged reaction
TurnStart trigger
Aura / continuous effect
Periodic effect
被动授予另一个主动能力
主动能力施加一个持续被动 condition
```

因此核心建议使用 `ActivationSpec`，例如：

```text
MANUAL
TRIGGERED
CONTINUOUS
PERIODIC
```

再由子结构描述：

```text
Manual:
  Action / BonusAction / Reaction / Free / Movement...

Triggered:
  OnHit / OnDamaged / OnTurnStart / OnTurnEnd / ...

Continuous:
  Modifier / Aura / Grant / RuleConstraint

Periodic:
  timing owner + interval/turn phase + effect
```

UI 仍然可以把 MANUAL 显示为“主动”、其他大部分显示为“被动”，但底层执行模型不被这两个标签限制。

---

# 11. Fact Requirement / Read Contract：限制能力可以依赖什么数据

原先“能力拥有一个提前定义的数据源组”的想法应保留，但建议避免 `DataSource` 命名，以免与能力来源、native source 混淆。

建议概念名：

- `FactKey<T>`：一个 canonical fact 的稳定键；
- `FactRequirement`：能力要求读取什么；
- `ReadContract`：能力允许消费的事实集合与范围；
- `FactBundle / FactSlice`：本次实际捕获到的纯值数据。

例如 melee attack 可能声明：

```text
ACTOR.strength
ACTOR.proficiency
ACTOR.weapon_profile
TARGET.armor_class
TARGET.conditions
ENCOUNTER.action_economy
WORLD.distance
WORLD.line_of_effect
```

Rule Resolver 只收到这些 canonical facts，而不是 `ServerLevel`。

这带来三个收益：

1. **依赖可审计**：知道为什么一个 ability 需要某类 native 数据；
2. **预览和 AI 可复用**：同一事实输入得到同一规则结论；
3. **第三方适配明确**：缺一个 Fact Provider 时可以返回 typed unsupported / unresolved，而不是偷偷回退到错误行为。

### 安全边界说明

Read Contract 是 DNDTurn 自身的架构隔离，不是 JVM sandbox。一个同 JVM 第三方模组如果自行取得 `ServerLevel`，DNDTurn 无法阻止它访问世界。

DNDTurn 能保证的是：

> **进入 DNDTurn common Rule API 的规则实现只消费声明过的 canonical facts。**

---

# 12. Action kind、费用和 Ability 不要继续混成一个概念

当前 `TacticalIntent.Capability { MOVE, ATTACK, PLACE, BREAK, USE_ITEM, USE_BLOCK, EQUIP }` 实际同时承担了“行为种类”和“费用推断”的语义。

长期概念上应至少分开：

```text
IntentKind / OperationKind
    MOVE
    ATTACK
    INTERACT
    USE_ITEM
    PLACE
    BREAK
    EQUIP
    ...

CostSpec / ActionCost
    ACTION
    BONUS_ACTION
    REACTION
    MOVEMENT
    FREE
    custom resource costs...
```

一个 ATTACK 不必天然总是 Action；Reaction attack、bonus attack、free follow-up 都不应需要创造新的 operation kind。

这只是未来语义目标，本文件不要求现在立即重命名现有 enum。

---

# 13. Character Sheet、Spellbook 与 Investigation 都应主要是 Projection

## 13.1 Character Sheet

Character Sheet 是 ActorSnapshot 的读取投影：

```text
ActorSnapshot
    ↓ visibility/formatting policy
CharacterSheetView
```

它不拥有 Strength、AC 或当前资源的第二份真值。

## 13.2 Spellbook

“Spellbook”需要区分**状态**与**UI**：

- learned spell grants / prepared selections 如果需要持久化，属于 ActorPersistentState；
- 当前装备/condition 临时提供的 spell 属于各自 grant owner；
- `SpellbookView` 是把这些 grants + definitions 按规则和 UI 分类投影出来的 read model。

因此 spellbook UI 可以展示：

```text
Known
Prepared
Granted by equipment
Granted temporarily
Unavailable and reason
```

但不应复制一份独立“所有技能清单”成为新的 authority。

## 13.3 Investigation / Inspect

服务端 ActorSnapshot 可能知道玩家不应该直接看到的事实：

- 完整 HP；
- 隐藏抗性；
- 未公开能力；
- AI/Encounter 隐藏状态。

因此客户端调查面板不应直接得到完整 Snapshot：

```text
InspectionView
    = ActorSnapshot
    + ObserverKnowledge
    + VisibilityPolicy
```

“服务器知道”与“该玩家有权知道”必须分开。

---

# 14. 详细 Ownership 模型

下面表格是本架构最重要的约束之一。后续任何类设计首先应回答“写 owner 是谁”。

| 概念 / 数据 | 建议唯一写 owner | 常见读取者 | 生命周期 | 是否进入 ActorSnapshot | 持久化倾向 |
| --- | --- | --- | --- | --- | --- |
| Actor definition ID/version | Definition registry | Snapshot compiler / UI | 定义版本 | 是 | 配置/注册表 |
| 基础 DND stats | ActorDefinition 或明确 persistent override owner | rules/UI | 定义/角色 | 是 | 是/定义数据 |
| 学习所得 ability grant | ActorPersistentState | snapshot/compiler | 角色长期 | 是 | 是 |
| 当前装备事实 | Minecraft inventory/equipment | native fact provider | 世界实时 | 是，作为捕获事实 | Minecraft 自己 |
| 装备授予 ability | Equipment Grant Provider（派生） | snapshot/compiler | 装备存在期间 | 是 | 通常不单独保存 |
| 当前 vanilla attribute effective value | Minecraft entity AttributeMap | native fact provider | 世界实时 | 按需 | Minecraft 自己 |
| DNDTurn-owned resource current value | ActorRuntime/Persistent state（按设计） | rules/UI | 角色动态 | 是 | 视资源而定 |
| vanilla-owned health/effect | Minecraft entity | adapter/rules | 世界实时 | 按需 | Minecraft 自己 |
| Condition instance（DNDTurn-owned） | ActorRuntimeState | rules/UI | 有期限 | 是 | 按规则 |
| initiative modifier | ActorSnapshot 派生 | Encounter admission/rules | actor revision | 是 | 否/可重建 |
| initiative roll/result | EncounterParticipantState | turn engine/UI | Encounter | 否；进入 CombatantSnapshot | Encounter checkpoint |
| Action/Reaction/movement budget | EncounterParticipantState | rules/UI | turn/round | 否；进入 CombatantSnapshot | Encounter checkpoint |
| current turn / round | Encounter | scheduler/UI | Encounter | 否 | Encounter checkpoint |
| hostility / membership | Encounter | rules/AI/UI | Encounter | 否 | Encounter checkpoint |
| 实体当前位置 | Minecraft world | spatial adapter | 世界实时 | 可作为 bounded fact，不应长期复制 | Minecraft 自己 |
| LoS / collision / block facts | Minecraft world，由 adapter 查询 | resolver/path/preview | evaluation slice | 否，进入 ResolutionContext | 否 |
| AbilityDefinition | common registry/data | compiler/rules/UI | definition version | 引用 | 配置/代码 |
| AbilityBinding | Snapshot compiler / capability resolver 生成 | UI/AI/invocation | snapshot revision | 是/可派生 | 否 |
| Operation result | Operation/Encounter ledger | client/history/recovery | 不可变历史 | 否 | 按现有 ledger 合同 |
| Native execution context | target execution layer | executor only | stack/execution bounded | 绝不 | 否 |
| Client presentation state | client presentation runtime | renderer | 客户端临时 | 绝不回写规则 | 否/局部 |

### Ownership 的核心规则

1. **读副本可以多份，写 owner 只能一个。**
2. Snapshot 是 immutable read model，不是新的写 owner。
3. Projection 是 read model，不是新的写 owner。
4. Adapter 可以观察和执行，但不能凭自己方便复制 Encounter authority。
5. 如果一个值到底归 Minecraft 还是 DNDTurn 所有尚未决定，应显式标注 undecided，而不是两边各维护一份再“同步”。

---

# 15. Revision、失效与裁决边界

不建议只有一个“全局 snapshotVersion”。不同事实有不同变更来源，长期至少要能区分概念上的版本：

```text
DefinitionVersion
ActorStateRevision
NativeFactRevision / captured evidence revision
GrantRevision
EncounterVersion / participant revision
RulesetVersion
Selection / equipment evidence revision
```

不要求这些全部成为独立 long 字段；关键是不要让一个计数器同时证明互不相关的事情。

一次 rule resolution 应明确绑定所使用的版本组合。常见流程：

```text
1. compile ResolutionContext at revision N
2. pure validate / preview / plan
3. before first irreversible effect:
      recheck required volatile evidence
4. execute through authorized adapter
5. observe actual effects
6. update the true owners
7. advance relevant revisions
8. next resolution compiles revision N+1
```

接近目标、等待环境回合、异步寻路等长操作尤其不能假设最初的 Snapshot 永久有效。它们应保留 captured intent/versions，并在进入不可逆边界前按 Read Contract 复验必要事实。

---

# 16. Rule Interpreter 与 Executor 的边界

## 16.1 Rule Interpreter

应尽量是 common 中的纯逻辑：

输入：

```text
ResolutionContext
```

输出可能是：

```text
Allowed / Rejected / RequiresApproach / Deferred
CostPlan
Target/EffectPlan
ExecutionRequest(s)
ObservationRequirements
```

它不直接：

- `hurt()`；
- `ItemStack.use()`；
- 改 block；
- spawn projectile；
- 写 inventory；
- tick Goal/Brain。

## 16.2 Native Executor

target adapter 负责把审计过的 `ExecutionRequest` 翻译成 Minecraft/第三方模组操作。

Executor 可以拥有短生命周期的 native references，但它必须：

- 核验 current instance；
- 核验执行许可；
- 遵守 server thread；
- 使用明确支持的原版/loader/第三方入口；
- 在副作用之后产生 typed observation；
- 不重新决定 DND action cost / initiative / rule legality。

## 16.3 Observation / Reconcile

规则不能把“调用了原版方法”当作“效果已发生”。

例如一次攻击可能出现：

```text
rejected
miss
immune
zero damage
absorbed
health loss
knockback
item durability changed
status applied
target died
```

因此执行后应以 typed observations 作为 reconcile 输入，分别更新 Minecraft-owned facts、DNDTurn state 与 operation result。

---

# 17. Environment Turn 与 Actor 模型的关系

Environment Turn 是 Encounter participant / scheduler 概念，不应该为了统一接口而伪装成普通 `ActorDefinition` 或 `ActorSnapshot`。

环境运行可能需要自己的：

```text
EnvironmentProcessState
EnvironmentFactSlice
EnvironmentExecutionBudget
ScheduledProcess ownership
```

它与 Actor 共享：

- operation identity；
- rule/execution/observation/reconcile 的阶段思想；
- bounded facts；
- immutable result/history；
- Encounter scheduling。

但不共享：

- 装备；
- spellbook；
- character stats；
- actor ability grants。

这样能避免为了“万物皆 Actor”而产生大量假的字段和特殊分支。

---

# 18. Vanilla Adapter 的粗略接入面

本节只列**需要的事实类别与执行职责**。具体 Minecraft 26.x patched 源码中的方法、事件、Mixin、线程和取消语义后续单独审计。

## 18.1 Identity / Lifecycle Adapter

目标：确认规则处理的是当前、有效、加载中的实体实例。

粗略来源：

- entity UUID；
- current entity instance；
- dimension / level；
- alive / removed / unloaded；
- DNDTurn 自己需要的 instance generation/presentation identity。

当前代码中的 `TacticalActor` 已经是这种**短生命周期 target context** 的原型；它不应升级成 common Actor 本体。

## 18.2 Native Attribute Adapter

目标：按 Fact Requirement 读取原版 effective/base attributes，而不是把整个 `AttributeMap` 暴露给 common。

粗略来源：

- `LivingEntity` AttributeMap；
- standard `Attributes.*`；
- 第三方注册的 attribute keys（通过扩展 provider）。

当前 `NativeFacts.capture()` → `NativeActorFacts` 是早期原型。长期应从固定的少量字段采集演化为有界 `FactProvider`，而不是变成 `captureEverythingAboutActor()`。

## 18.3 Body / Pose / Movement Facts

目标：提供规则所需的纯值身体事实：

- width / height / bounding profile；
- pose；
- feet/world position；
- movement categories；
- movement/collision 评估所需的 bounded facts。

这些事实用于 Snapshot 或 WorldFactSlice，但真实位置仍由 Minecraft world 所有。

## 18.4 Equipment / Inventory / Selection Adapter

目标：提供：

- main/off hand；
- armor slots；
- inventory slot reference；
- item identity/content revision evidence；
- selected slot；
- accessory / trinket extension slots（通过第三方 provider）；
- item-derived grants 所需证据。

装备本身不应被复制为 common `ItemStack`。Common 只接收稳定 item/profile ID、必要属性、slot/evidence 和 derived grants。

## 18.5 Effect / Condition Adapter

目标：读取 vanilla / mod effects，并转换成：

- canonical condition facts；
- stat modifiers；
- ability grants；
- trigger facts。

不是每个 MobEffect 都必须自动映射成 DND Condition；映射应由明确 adapter/provider 定义。

## 18.6 Actor Definition Resolver

目标：根据原生实体身份解析 `ActorDefinition` 或 definition fragments。

粗略输入可能包括：

- entity type registry ID；
- entity tags/groups；
- player identity / configured archetype；
- 第三方注册 provider；
- 明确 mod integration。

不要把 `instanceof Zombie` 之类物种分支长期散落在 CombatEngine 或 Ability resolver 中。

## 18.7 Ability Grant Provider

目标：收集当前 grants：

```text
identity/intrinsic grants
learned grants
equipment grants
status grants
encounter temporary grants
external mod grants
```

Provider 只负责“拥有关系 + lifecycle evidence”；AbilityDefinition 仍由 common registry/definition layer 提供。

## 18.8 Spatial / Targeting Fact Adapter

目标：按 Read Contract 提供：

- distance；
- line of sight / line of effect；
- loaded state；
- collision / support / reachable execution position facts；
- block hit / face / fluid / occupancy；
- bounded spatial query results。

寻路可以使用更丰富的 snapshot，但它仍然只是空间提案，不拥有 Actor 或 Encounter 状态。

## 18.9 Vanilla Ability Execution Bridges

粗略分类：

- melee / hurt ingress；
- ranged/projectile launch；
- vanilla item use / useOn；
- block interaction；
- place / break；
- consume / durability；
- equip / inventory mutation；
- custom entity ability / Goal/Brain 中可审计执行入口。

当前 `TacticalBehavior` / `VanillaBehaviors` / 若干具体 Behavior 可以视为现有执行桥的原型，但长期不应继续同时承担 Definition、Grant discovery、Rule validation 和 Native execution 四种职责。

## 18.10 Observation Adapters

执行后需要从原版事实产生 typed observation：

- damage / health delta；
- death；
- effect applied/removed；
- projectile spawned/removed/impact；
- item count/durability；
- equipment change；
- block/fluid change；
- spawned entity；
- movement / knockback；
- unsupported / unknown effect evidence。

Observation 是规则 reconcile 的事实，不是 adapter 自己给出的“成功”布尔值。

## 18.11 Environment Process Adapters

与现有 Environment Turn 设计一致，粗略需覆盖：

- scheduled block/fluid tick；
- block event；
- block entity ticker；
- 需要 Encounter 接管时序的延迟 process；
- 其他被明确分类为环境参与者责任的 outcome-changing process。

它们应进入 environment-specific fact/execution 模型，而不是塞进 ActorSnapshot。

## 18.12 Presentation Adapter

客户端只消费服务器批准的 projection / presentation event：

- CharacterSheetView；
- Ability/SpellbookView；
- InspectionView；
- operation result / presentation events；
- movement/path/target preview。

客户端 renderer、camera、animation 不应成为规则事实 owner。

---

# 19. 当前源码概念与目标模型的粗略对应

此表仅用于帮助理解，不是迁移任务列表。

| 当前概念 | 当前价值 | 长期目标语义 |
| --- | --- | --- |
| `NativeFacts.capture()` | 已开始把 Minecraft attribute 转成 detached facts | 演化为多个按需 `NativeFactProvider` / bounded fact slice |
| `NativeActorFacts` | common 中纯值 native evidence | 成为 canonical actor/native fact 的一部分，而非完整 Actor model |
| `TacticalActor` | current native instance + server-thread context | 保留为 target/execution scoped context，不进入 common/persistence |
| `AbilitySource` | 已区分 equipment/basic/intrinsic/status，并携带证据 | 概念上拆为 `GrantOrigin + GrantOwner + GrantEvidence` |
| `TacticalCapabilities` | registry + discovery + binding | 长期 discovery 主要由 Snapshot/Grant/Definition 解析；native registry 只负责 executor/integration |
| `TacticalBehavior` | 当前同时负责 metadata、source、validation、reach、execution | 长期拆分语义职责：AbilityDefinition / RuleResolver / NativeExecutor / Observation contract |
| `CombatEngine.MemberView` | 已正确持有 initiative/action/reaction 等 Encounter 数据 | 继续视为 EncounterParticipantState/Projection，而不是搬入 ActorSnapshot |
| `TacticalIntent.Capability` | 当前同时表达 operation kind 与成本关系 | 长期语义上拆 `IntentKind` 与 `CostSpec`，不要求立即改名 |

现有源码中已经出现多个与目标架构一致的苗头，因此本草案不意味着“推倒重来”；目标是以后新增能力时沿着统一边界增长，逐步让旧职责自然收缩。

---

# 20. 典型数据流示例

## 20.1 装备剑后执行普通攻击

```text
Minecraft equipment
  main hand = sword, item revision R17
        ↓
Equipment Fact Provider
        ↓
canonical equipment facts
        ↓
Equipment Grant Provider
  grant: dndturn:basic_melee
  owner: equipment provider
  evidence: MAIN_HAND + R17
        ↓
Snapshot Compiler
        ↓
ActorSnapshot N
  stats + melee binding
        ↓
player/AI selects target
        ↓
ResolutionContext
        ↓
Rule Interpreter
  validate turn/cost/range/etc.
        ↓
ExecutionRequest: audited melee driver
        ↓
Native Executor
        ↓
Minecraft attack/hurt chain
        ↓
Damage/knockback/durability observations
        ↓
Reconcile + Encounter cost commit
        ↓
relevant revisions advance
```

如果剑被替换成另一件物品，旧 Binding 不需要“主动删除自己”；其 equipment evidence 在下一次复验时失效，新的 Snapshot 编译出新的 grant/binding。

## 20.2 读卷轴永久学习法术

```text
Scroll execution
    ↓ actual consume observation
Learn effect accepted
    ↓
ActorPersistentState adds grant G42
    origin = learned_from_scroll
    owner  = ActorPersistentState
    ↓
ActorStateRevision++
    ↓
Snapshot N+1 contains Fireball binding via G42
```

卷轴之后不存在并不影响 grant。

## 20.3 Buff 临时授予技能

```text
Condition C11 exists
    ↓
Condition Grant Provider
    ↓
AbilityGrant
  ability = dndturn:misty_step
  owner = Condition C11
  evidence = condition revision
    ↓
Snapshot contains binding
```

Condition C11 结束后，grant 失效；不需要让 AbilityDefinition 自己管理计时。

## 20.4 DEX 在战斗中变化

```text
ActorSnapshot N:
  DEX = 14
  initiative modifier = +2

EncounterParticipantState:
  initiative result = 17

buff changes DEX → 18
ActorSnapshot N+1:
  DEX = 18
  initiative modifier = +4

EncounterParticipantState:
  initiative result remains 17
```

除非某个明确 ability/effect 修改或重投 initiative。

---

# 21. 第三方模组兼容策略

长期兼容不应以“识别所有第三方实现细节”为目标，而应提供可组合的契约。

### 最低级兼容

只有标准 native facts 可读取：

- actor 可以参战；
- 可以移动/成为目标；
- 只暴露 DNDTurn 已知的 basic capabilities；
- 未知主动技能明确 unavailable，不伪造 vanilla Zombie 行为。

### 声明式兼容

模组通过 registration 提供：

- actor definition fragment；
- fact provider；
- ability definitions/grants；
- 可用通用 executor 表达的 execution contract。

### 深度兼容

对于特殊技能：

- 自定义 native executor；
- typed observation adapter；
- optional presentation adapter。

这比核心不断增加 `instanceof SomeMob`、`if item == X` 更可维护，也能允许服务端规则兼容与客户端视觉兼容分开演进。

---

# 22. 关键不变量（未来实现与评审应持续验证）

1. `common` 的规则层永不依赖 Minecraft/loader 对象。
2. ActorSnapshot / CombatantSnapshot / ResolutionContext 一经构造不可变。
3. 规则裁决只读 Snapshot/Context，不任意回读 native world。
4. Snapshot 是 resolution authority，不是 storage authority。
5. 每个可变事实有唯一写 owner；不存在两份都可裁决的“同步真值”。
6. initiative result、turn resource、hostility 等 Encounter 状态不属于 ActorSnapshot。
7. AbilityDefinition 与 Actor 为什么拥有能力严格分开。
8. Ability grant 至少能区分 origin、lifetime owner 和 current evidence。
9. Active/passive 是 projection 分类；真正运行语义由 ActivationSpec 表达。
10. Ability/Rule 明确声明 bounded Fact Requirements。
11. Adapter 只翻译事实/执行请求，不重新决定 DND 规则费用和 legality。
12. 调用 Minecraft 方法不等于效果成功；实际效果通过 typed observation 进入 reconcile。
13. CharacterSheet/Spellbook/Inspection 是 projection，不复制核心 authority。
14. 服务端完整 Snapshot 与客户端可见 InspectionView 分开。
15. 不为统一接口把 Environment Turn 假装成普通 Actor。
16. Unknown / unsupported / unresolved 均显式表达，不静默降级到错误能力。
17. 运行中的 invocation/operation 绑定 definition/ruleset/evidence version，不在中途静默换语义。
18. 长步骤在不可逆执行前复验其 Read Contract 中的 volatile evidence。
19. 版本适配细节全部留在 target；common schema 不随 Minecraft 小版本方法名漂移。
20. 新的 manager/facade 只有在改变 ownership、依赖方向或生命周期边界时才有架构价值。

---

# 23. 建议的概念命名（暂定，不作为 API 决定）

| 当前讨论词 | 建议概念名 | 理由 |
| --- | --- | --- |
| Actor Base | `ActorDefinition` | 表达冷定义，而非实例状态 |
| Actor Modify | 拆分 `PersistentState / RuntimeState / Modifier / Condition / Grant` | 避免万能热状态袋 |
| Actor Snapshot | `ActorSnapshot` | 可保留；明确 immutable resolution view |
| 会话中的 Actor 快照 | `CombatantSnapshot` 或 `ParticipantSnapshot` | 与 Actor 本体、Encounter 状态区分 |
| 能力来源 | `GrantOrigin` | 只回答 provenance |
| 能力来源生命周期 | `GrantOwner` | 回答谁撤销/恢复/保存 |
| 来源有效性 | `GrantEvidence` | 回答当前为什么仍有效 |
| 主动/被动 | `ActivationSpec` | 表达 manual/triggered/continuous/periodic |
| 数据源 | `FactRequirement / ReadContract` | 避免和 ability source/native source 混淆 |
| 能力本体 | `AbilityDefinition` | 稳定 ID/version 的规则定义 |
| 角色拥有能力 | `AbilityGrant` | definition 与 ownership 解耦 |
| 当前可调用能力 | `AbilityBinding` | Actor-specific resolved binding |
| 一次能力请求 | `AbilityInvocation` | 明确目标与版本的一次 intent |
| 原版采集 | `NativeFactProvider / Adapter` | 强调点对点事实翻译 |
| 原版行为实现 | `NativeExecutor / ExecutionDriver` | 与规则定义分开 |
| 原版执行结果 | `Observation` | 不用单一 success bool 代替事实 |

这些名称可以在后续源码设计时继续商榷；重要的是语义不要重新合并。

---

# 24. 后续源码级 Vanilla Adapter 文档应回答的问题

后续针对固定 Minecraft/NeoForge 源码细化接入点时，每一个 adapter 至少应记录：

1. canonical fact / execution contract 名称；
2. 对应 Minecraft 类和完整方法签名；
3. 调用发生的 side / thread；
4. 数据读取发生在什么生命周期阶段；
5. 是否包含 equipment modifier / effect modifier 等已经应用后的值；
6. 是否存在 subclass override、final、early return；
7. 事件/Mixin 的顺序和取消语义；
8. 是否覆盖 Player、Mob、PartEntity、projectile 等特殊路径；
9. 第三方模组可能绕过的路径；
10. snapshot/evidence 的失效条件；
11. 执行前需要复验什么；
12. 实际副作用如何观察；
13. UNKNOWN / unsupported 如何表达；
14. 对应 GameTest / unit test 验收场景。

源码级文档的目标不是“找一个能 hook 的方法”，而是证明这个接入点能稳定履行本文件定义的 ownership 与 fact/execution contract。

---

# 25. 最终目标的简化表达

如果需要把整个目标压缩为一条长期设计公式，可以使用：

```text
Native Facts
    + Actor Definition
    + Actor-owned State
    + Ability Grants
        ↓
Immutable Actor Snapshot

Actor Snapshot
    + Encounter Participant State
    + bounded World Facts
    + Ability Invocation
        ↓
Resolution Context
        ↓
DND Rule Interpreter
        ↓
Execution Requests
        ↓
Vanilla / Mod Executors
        ↓
Typed Observations
        ↓
State owners update → next revision
```

对玩家来说最终表现为：

```text
Character Sheet / Spellbook / Ability Bar / Inspection / Preview
```

但这些全部是上层投影；真正稳定的基础设施是下面四条边界：

> **Definition 决定“是什么”。**  
> **State / Grant owner 决定“当前拥有什么、处于什么状态”。**  
> **Snapshot 决定“本次规则裁决允许看到什么”。**  
> **Executor + Observation 决定“Minecraft 世界实际发生了什么”。**

只要这四条边界稳定，vanilla 和第三方兼容可以继续在 target 层增长，而不需要每增加一种 Minecraft 行为就重新定义一次 DND 战斗模型。
