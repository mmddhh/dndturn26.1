# DNDTurn 原版战斗模型通用性复核 Checklist

> 状态：基于 `DNDTurn-sources(2).zip` 的修正后结构性 Checklist  
> 复核日期：2026-09-30  
> 目标：以“尽可能还原 Minecraft 原版战斗语义，同时保持 DNDTurn 的规则权威、许可、恢复和第三方扩展边界”为长期目标，检查当前模型是否能够承载 Enderman、Guardian、Wither、Ender Dragon 等复杂原版行为，而不是逐实体增加特判。  
> 本文是结构性子清单，建议并入 `docs/02_GAPS_AND_CONFLICTS.md`；不替代玩家规则、固定版本源码审计或版本验收记录。  
> H04 Enderman、H05 飞行／大型部位／Boss 目前仍属于既有“明确暂缓”范围；本文可以先实现通用基础设施，但不得仅凭本 Checklist 自动开放对应玩法。

---

## 0. 本轮复核结论与对上一版评估的修正

实现更新：本文保留结构要求与原始审计背景，不能继续将“当前已有骨架”等审计段落视为最新源码事实。2026-09-30 增量实现与固定版本验证见[战斗结构记录](version-differences/neoforge-26.1-combat-structure.md)；唯一剩余目标见[02](legacy/02_GAPS_AND_CONFLICTS.md#combat-structural-remainder)。值模型和扩展入口已接入，但 staged driver、G25 窗口及最终 archetype 尚未闭合，本文的最终完成判据未通过。

### 0.1 已确认完成或显著改善

- [x] **包级职责重构已经实际落地。**
  - common 主代码已按 `domain/*` 与 `application/*` 分层。
  - NeoForge 26.1 target 已拆为 `platform/server/{ability,action,actor,ai,control,damage,effect,encounter,persistence,runtime,world,...}` 与 `platform/server/builtin/*`。
  - `PackageArchitectureTest` 已加入源码级约束：
    - common 仅允许 `domain` / `application`；
    - `domain` 不反向依赖 `application`；
    - common 不依赖 Minecraft / loader / target `platform`；
    - generic server framework 不依赖 `builtin` 物种；
    - Mixin 只能进入批准的平台 seam。
  - 证据：
    - `common/src/test/java/cc/sighs/dndturn/architecture/PackageArchitectureTest.java`
    - `AGENTS.md §2`

- [x] **上一轮“没有跨 tick Ability 生命周期”的表述需要修正。**
  - 当前普通执行链已经有：
    - `AbilityExecutor.prepare/start/tick/release`
    - `ActionExecutionCoordinator.Execution.executionSteps`
    - 有界 `adapterState`
    - `AbilityCheckpoint` 的 `APPROACH/PREPARE/EXECUTE/OBSERVE/TERMINAL`
    - `ExecutionClock.AUTHORIZED_EXECUTION_STEP`
    - `Recovery.RECONCILE_WITHOUT_REPLAY`
  - 因此当前状态应描述为：**已经存在跨 tick 执行骨架，但还没有可持久恢复的 canonical Process 模型。**
  - 证据：
    - `platform/server/ability/AbilityExecutor.java`
    - `platform/server/action/ActionExecutionCoordinator.java`
    - `platform/server/action/AbilityExecutionContext.java`
    - `platform/server/action/AbilityCheckpoint.java`

- [x] **上一轮“没有 Reaction 资源”的表述需要修正。**
  - `ActionEconomy` 已有独立 `reaction` 余额；
  - `EncounterAuthority` 已有 `OperationRecord.Kind.INTERRUPT` 的 reaction 子操作准入与 `spendReaction()`。
  - 当前真正缺口是：**Effect/Actor activation 的 Triggered Ability 通道仍强制 `ActionCost.FREE`，没有把已有 reaction economy 接到通用事件触发能力链。**
  - 证据：
    - `domain/encounter/ActionEconomy.java`
    - `domain/encounter/EncounterAuthority.java`
    - `domain/actor/ActorActivations.java`

- [x] **世界效果观察能力比上一轮概括更完整。**
  - `WorldOutcomeObservation` 已覆盖 block / motion / spawn / damage；
  - `AbilityCheckpoint.Sample` 还覆盖 item / block / body / spawn。
  - 因此缺口不是“完全没有 observation”，而是：
    - observation 仍分散在不同 record；
    - 没有足够通用的 typed observation 扩展合同；
    - teleport、effect mutation、target/state mutation、multipart hit 等复杂语义没有正式值模型。

### 0.2 仍然成立的核心结论

- [ ] 当前 `ActorActivations` 仍主要是 **post-commit actor-local event/effect wave**，不是可在世界副作用提交前介入的通用 reaction window。
- [ ] 当前 triggered execution 仍是 **同步、有界、不可恢复继续执行** 的独立通道。
- [ ] 当前 AI common model 仍主要覆盖 **玩家目标 + DAMAGE(MELEE/RANGED) + ground opportunity**。
- [ ] 当前 actor runtime 仍以一个 `LivingEntity` 作为主体；缺少正式 multipart / body facet target model。
- [ ] 当前 movement opportunity 仍以 `PathfinderMob + GroundPathNavigation` 为主，非地面移动回退为 stationary。
- [ ] Dragon / Wither / Enderman 已有 fail-closed Mixin gate，但还没有对应 canonical gameplay replacement。

---

# 1. 验证基线：先证明包重构后的新快照本身成立

## VM-00 Fresh verification after package refactor

- [ ] **重新执行 common architecture / unit tests。**
  - 至少：
    ```text
    cd targets/neoforge-26.1
    ./gradlew :common:test
    ```
  - 必须实际执行 `PackageArchitectureTest`，不能只以测试源码存在作为通过证据。

- [ ] **重新执行 NeoForge 26.1 clean build。**
  ```text
  ./gradlew clean build
  ```

- [ ] **重新执行受包重构影响的 GameTest。**
  - 至少覆盖：
    - reaction；
    - Creeper；
    - AI/mob standardization；
    - action execution；
    - persistence/restart；
    - gate/control；
    - environment。

- [ ] **重新生成 verification logs。**
  - 当前快照附带的以下日志与上一快照字节一致：
    - `verification-current-acceptance.log`
    - `verification-current-reaction-verify.log`
    - `verification-current-creeper-verify.log`
  - 其中日志仍出现旧类名 `ServerCombatService`，而新源码运行 owner 已改为 `EncounterRuntime`，因此这些日志不能证明本次 package refactor 已运行通过。

- [ ] **受 common API/package 迁移影响的其他 target 至少做 compile compatibility。**
  - Forge 1.20.1
  - Fabric 1.20.1
  - NeoForge 1.21.1

### 完成标准

只有新的测试输出明确来自当前包结构时，才能把“包级重构”从 **静态源码完成** 提升为 **运行验收完成**。

---

# 2. P0 — Causal Event / Intervention：补齐“事情发生前可以介入”的模型

## VM-01 Typed causal event envelope

当前证据：

```text
ActivationSpec.Event
  APPLIED / REMOVED / EXPIRED / STACK_CHANGED
  HIT / DAMAGED
  TURN_START / TURN_END
  ABILITY_USED / ABILITY_RESOLVED
```

`ActorActivations.Event` 当前主要只有：

```text
id / cause / actor / other / kind / clock / sequence / EffectTransition
```

而 `HIT` / `DAMAGED` 在 `TacticalDamageContext.hurtObserved(...)` 和 `engine.publish(...)` 后才由 `ActorStateAuthority.observedHit(...)` 派发。

- [ ] **定义通用、类型化的 causal event 上下文。**
  - 不要求机械创建名为 `CombatEvent` 的类型；
  - 必须至少能够表达：
    - source；
    - subject / target；
    - cause/root operation；
    - event phase；
    - typed payload；
    - 当前规则/encounter revision；
    - 已发生与尚未发生的副作用边界。

- [ ] **正式区分至少三类时序语义。**
  - pre-commit / attempt；
  - post-commit / result；
  - observation-only。
  - 命名可以不同，但不能继续把所有事件都压成一个 `ActivationSpec.Event` 枚举。

- [ ] **Damage attempt 必须有 write-before intervention seam。**
  - 可以利用现有 `TacticalDamageContext` / NeoForge incoming damage 的固定版本入口作为平台桥；
  - 不能先 `hurtServer` 再通过 `DAMAGED` 假装实现“避免本次伤害”的反应。

- [ ] **保留当前 `HIT` / `DAMAGED` post-result 事件。**
  - 它们适合：
    - 受伤后 buff/debuff；
    - 反伤后的后续链；
    - telemetry；
    - confirmed-result trigger。
  - 不要为了 pre-commit 新需求破坏现有 post-commit 语义。

- [ ] **Reaction 结果只修改当前 causal operation 的裁决，不直接获得任意世界写权限。**
  - 推荐可表达：
    - pass；
    - cancel；
    - redirect；
    - replace；
    - start ability/process；
    - emit rule/effect state change。
  - 原世界副作用 owner 仍负责最终 commit。

- [ ] **禁止按物种扩展事件枚举。**
  - 不允许：
    ```text
    ENDERMAN_HIT
    DRAGON_PHASE
    WITHER_HEAD_FIRE
    ...
    ```
  - 物种差异应存在于 adapter / activation predicate / process implementation，而不是 common event taxonomy。

### 验收 archetype

- [ ] Enderman 类“伤害/投射接触触发特殊位移”的固定版本源码场景可以通过 typed pre/post event 表达。
- [ ] 同一套 event/intervention primitive 能被第三方 defensive teleport / dodge / redirect 能力复用。
- [ ] 实现 Enderman 时无需向 generic `EncounterRuntime` / `ActorStateAuthority` 写 `instanceof EnderMan`。

### 与现有缺口映射

- DM-02：触发能力扩展
- G25：完整借机攻击
- H04：Enderman 特殊能力（仍保持产品范围暂缓，除非另行授权）

---

# 3. P0 — Durable Execution Process：从“执行 callback”升级为 canonical staged process

## VM-02 Process lifecycle

当前已有骨架：

```text
AbilityExecutor
  prepare
  start
  tick
  requestCancel
  release

Execution
  executionSteps
  adapterState

AbilityCheckpoint
  APPROACH
  PREPARE
  EXECUTE
  OBSERVE
  TERMINAL
```

但当前 `AbilityCheckpoint` 明确声明：

> `never a resumable driver`

并且：

- `adapterState` 没有进入 `AbilityCheckpoint`；
- restart recovery 对未终态 action 走 observation reconciliation / UNKNOWN；
- 不恢复 callback 对象；
- triggered channel 的 `STARTED` 在恢复时直接转 UNKNOWN。

因此：

> 当前有 **multi-tick execution loop**，但没有 **durable semantic process state**。

- [ ] **定义 canonical process state。**
  - 名称不强制为 `ProcessInstance`；
  - 必须是纯值；
  - 不保存 Entity / Goal / Brain / Navigation / callback 实例；
  - 至少记录：
    - process id/version；
    - owner；
    - cause/root operation；
    - semantic phase；
    - canonical state；
    - timing domain；
    - recovery contract；
    - next eligible boundary；
    - adapter/process provenance。

- [ ] **区分 execution evidence 与 resumable process state。**
  - `AbilityCheckpoint` 继续承担：
    - 已发生副作用证据；
    - release 状态；
    - before/after observation；
    - UNKNOWN 对账。
  - Process state 承担：
    - “接下来还应该发生什么”。
  - 两者不能互相伪装。

- [ ] **普通 manual ability 与 triggered ability 必须能够进入同一种 staged lifecycle。**
  - 当前 `TriggeredAbilities` 的：
    ```text
    prepareTriggered
    executeTriggered
    releaseTriggered
    ```
    是同步独立通道。
  - 目标不是强制删除它，而是：
    - 同步能力可以继续走 fast path；
    - 需要跨 tick/跨回合的 triggered ability 能启动 durable process，而不是被迫同步完成。

- [ ] **明确 process ownership。**
  - 至少允许：
    - actor-owned；
    - encounter-owned；
    - environment-owned。
  - 共享生命周期设施，不共享 gameplay authority。

- [ ] **定义 process cancel / interrupt / completion / release。**
  - cancellation 请求；
  - gameplay interruption；
  - native control release；
  - process terminal result；
  - observation UNKNOWN；
  - 必须分别建模。

- [ ] **为 process 提供有界并发/控制资源合同。**
  - 复杂 Mob 可能同时存在多个内部过程；
  - 不应默认“一名 Actor 同时只能有一个 native process”。
  - 需要表达至少类似：
    - movement/control ownership；
    - look ownership；
    - attack channel；
    - target slot；
    - contact/world-effect channel。
  - 不要求实现万能锁管理器；可以由 adapter 声明窄 lease/resource contract。

### 验收 archetype

- [ ] Guardian：启动 → 引导 → 条件维持 → 结算/中断。
- [ ] Evoker/类似 warmup spell：warmup 与 cast 不是同一个同步 callback。
- [ ] Wither：主移动 + 多攻击通道不会被单一 `Execution` 强制串成一个技能。
- [ ] Creeper 现有同步 threshold → explode 仍可用，不因引入 process 变复杂。

### 与现有缺口映射

- DM-01B：运行状态与持续派生覆盖
- DM-02：触发能力扩展
- MT-04：跨回合技能托管状态
- MT-10：Guardian 引导
- 第 8 节 persistence/recovery

---

# 4. P0 — Reaction 资源与 Activation 正交化

## VM-03 Reaction cost bridge

当前事实：

- `ActionEconomy` 已有独立 reaction；
- `EncounterAuthority` 已允许合法 `INTERRUPT` 子操作消费 reaction；
- 但 `ActorActivations.Registration` 强制：
  ```text
  triggered/periodic ability cost == ActionCost.FREE
  ```

因此资源基础存在，但 effect activation runtime 与 reaction economy 未闭环。

- [ ] **Activation、Timing、Cost、Execution lifecycle 四者解耦。**
  - Activation：什么时候可尝试启动；
  - Timing：在哪个 causal boundary；
  - Cost：需要哪类 encounter resource；
  - Execution：启动后怎么运行。

- [ ] **允许 triggered activation 产生需要 reaction economy 的规范 invocation。**
  - 不能让 effect callback 自己直接 `spendReaction()`；
  - 必须仍由 `EncounterAuthority` 唯一消费。

- [ ] **统一 Opportunity Attack 与其他付费反应的规则入口。**
  - G25 不应另造一套“借机攻击专用资源/调度器”。

- [ ] **保留免费被动反应。**
  - Enderman 等固定版本审计后可属于 FREE；
  - 免费与付费由 ability definition / rule 决定，而不是由“triggered”这个 activation kind 决定。

### 验收

- [ ] 同一 event window 内，免费 reaction 与 reaction-cost ability 都能参加，但由确定性规则裁决。
- [ ] reaction 已消费后不能再次启动付费 reaction。
- [ ] 免费 trigger 不误消费 reaction。
- [ ] restart / duplicate request 不重复消费 reaction。

### 与现有缺口映射

- DM-02：付费反应
- G25：完整借机攻击

---

# 5. P0 — Actor / Target topology：支持 multipart，而不让部位变成 participant

## VM-04 Composite actor / body facet target

当前实现：

- `LiveActorContext` 仍是：
  ```text
  LivingEntity body
  ```
- `ActionIntent.TargetKind` 只有：
  ```text
  ENTITY / BLOCK / GROUND / SELF
  ```
- entity target 只持有一个 entity UUID；
- `DamageReceivers` 只匹配 `LivingEntity`；
- 当前源码中没有等价于 body facet / multipart target 的正式 common value model。

同时新 `AGENTS.md` 已明确正确的目标原则：

> 根生物承担回合与资源，命中部件由适配器映射；不为每个 PartEntity 自动分配独立成员身份。

- [ ] **增加“Actor identity”与“hit body/facet identity”分离。**
  - Actor 继续拥有：
    - turn；
    - resources；
    - AI；
    - effects；
    - process。
  - facet/part 只表达：
    - hit geometry；
    - targetability；
    - damage routing；
    - damage multiplier/特殊规则；
    - presentation identity（如需要）。

- [ ] **Target value 能够表达 entity-root + optional facet/part。**
  - 不要求名称一定为 `BodyFacetRef`；
  - 但不能继续只靠“命中的 entity UUID 恰好就是 Actor UUID”。

- [ ] **平台 target resolver 负责把原版 PartEntity / multipart hit 映射到根 Actor。**
  - common 不依赖 Minecraft `PartEntity` 类型。

- [ ] **Damage receiver / target facts 能接收 facet context。**
  - 不把 facet 当独立 `LivingEntity`；
  - 不为 facet 分配独立 action/reaction/movement。

- [ ] **Encounter discovery 不把 part 自动登记为 member。**

### 验收 archetype

- [ ] Ender Dragon：头、身体、翼等命中可以路由到同一 Dragon Actor。
- [ ] 不同 part 的伤害规则可以不同，但 initiative/resource/effect owner 唯一。
- [ ] 第三方 multipart boss 可通过独立 adapter 注册，无需修改 generic encounter core。

### 与现有缺口映射

- MT-06：完整体型/姿态/攻击范围/执行位置
- H05：大型部位/Boss（产品功能仍暂缓）

---

# 6. P1 — AI Perception / Memory / Relation：从“基础攻击 planner”扩展为原版战术语义

## VM-05 Perception and semantic AI memory

当前已有正确基础：

- [x] `AiDefinition` 是 cold semantic data。
- [x] `AiRuntimeState` 与 definition 分离。
- [x] Goal/target selector 只作为 structural candidate evidence。
- [x] common `AiPlanner` 不持有 Minecraft Goal/Brain。
- [x] `AiAffordance` 已预留：
  - HEAL / CONTROL / BUFF / DEBUFF；
  - REPOSITION；
  - ESCAPE；
  - PROTECT_ALLY；
  - AREA 等。

当前限制：

```text
PerceptionSnapshot.ObservedActor
  id
  visible
  currentTarget
  player
  nativeAttackable
  distanceSquared
```

shared planner 当前只真正消费：

```text
DAMAGE && (MELEE || RANGED)
```

且目标筛选明显 player-centric。

- [ ] **扩展 PerceptionSnapshot 的证据维度。**
  - last known position；
  - observation age；
  - sensor/source；
  - confidence；
  - damage/stimulus source；
  - hazard；
  - terrain opportunity；
  - projectile/area danger；
  - visibility 与 knowledge 分离。

- [ ] **引入有界 semantic memory。**
  - pure value；
  - typed key/value；
  - TTL / revision；
  - provenance；
  - 不持久化原版 Brain MemoryModule 对象本身。

- [ ] **Relation 从“是否玩家”中解耦。**
  - hostility；
  - owner；
  - ally；
  - protected actor；
  - retaliation source；
  - faction/team 等按已审计规则投影。
  - relation 不能直接等价为 attack permission。

- [ ] **Generic planner 实际消费更多 AiAffordance。**
  - heal；
  - buff/debuff；
  - control；
  - escape；
  - protect ally；
  - reposition；
  - area/single-target。
  - 不要求一个 universal utility AI 一次完成全部，但 common contract 必须允许策略扩展而不破坏 rule path。

- [ ] **Boss/custom strategy 只能产生 proposal / process start，不持有世界 authority。**

- [ ] **AI runtime memory 变化不触发 definition re-bake。**

### 验收 archetype

- [ ] 非玩家目标关系：Villager / Golem / owner / ally 等至少有一组固定版本场景。
- [ ] “最后已知位置”型行为在失去 LOS 后不会退化为 server-global omniscience。
- [ ] Warden/类似 memory-heavy Mob 可通过 semantic memory 映射，而不保存 Brain 对象。
- [ ] planner 对 unsupported semantic 明确返回 unsupported/deferred，不静默变成 Zombie 行为。

### 与现有缺口映射

- AI bake
- AI planner：更多能力决策
- AI perception：有界感知与记忆
- MT-03：统一发现和目标评估

---

# 7. P1 — Movement Port：从 GroundPathNavigation 扩展为能力化移动

## VM-06 Movement capability boundary

当前：

- `VanillaAiFamilies` 只将 `PathfinderMob + GroundPathNavigation` 识别为 ground family；
- `MinecraftMovementOpportunities` 直接 cast `PathfinderMob`；
- 非 ground generic AI 回退 `Positioning.STATIONARY`。

这对当前“地面 Mob 基线”是合理 fail-closed，但不能承载完整原版战斗。

- [ ] **把 movement support 作为独立兼容轴。**
  - ground path；
  - swim；
  - fly；
  - climb；
  - hover/orbit；
  - custom steering。
  - 不要求用 enum 固死全部未来类型；关键是由 adapter 提供能力与 proposal contract。

- [ ] **AI 只消费 movement opportunity / estimate，不直接持有 Navigation。**

- [ ] **movement execution 与 movement planning 分开。**
  - planner 的可达性/代价；
  - native execution port；
  - lease/control ownership；
  - 实际 movement observation；
  - 分别留证据。

- [ ] **Teleport 不归类为普通 path movement。**
  - Teleport 是 reposition ability/process；
  - 正常移动费用与 teleport cost/trigger 独立。

- [ ] **支持不同 movement port 与 ability process 并存。**
  - 例如飞行 Boss 的 phase process 可以控制 fly steering；
  - 但不因此给 planner 任意直接 setPos 权限。

### 验收 archetype

- [ ] ground PathfinderMob：现有行为不回归。
- [ ] flying attacker：至少一个 Ghast/Phantom 类 archetype。
- [ ] custom flight/phase：Dragon 只在 H05 获得玩法授权后做最终 E2E。
- [ ] third-party navigation provider 可注册而不修改 `AiPlanner`。

### 与现有缺口映射

- 移动扩展
- MT-06
- H05 飞行/专属游泳（仍保持产品范围暂缓）

---

# 8. P1 — Observation Contract：从固定 record 扩展为声明式副作用证据

## VM-07 Typed native observations

当前已有：

```text
WorldOutcomeObservation
  BlockChange
  Motion
  Spawn
  Damage

AbilityCheckpoint.Sample
  Item
  Block
  Body
  Spawn
```

以及 adapter 级：

```text
observationSlots(...)
observationFootprint(...)
prepareObservation(...)
```

这是正确基础，不应推翻。

- [ ] **明确一个稳定的 observation extension contract。**
  - 可以是 sealed observation family、typed registry 或等价机制；
  - 不建议继续向一个超大 record 无限加 nullable 字段。

- [ ] **补充复杂战斗所需的观察类别。**
  - teleport / discontinuous movement；
  - effect add/remove/change；
  - entity state/metadata change；
  - target change；
  - inventory/equipment mutation；
  - projectile ownership/lifecycle；
  - multipart hit/facet；
  - process state transition；
  - special entity/block callback result。

- [ ] **每个 native adapter 声明：**
  - reads；
  - direct write scope；
  - indirect/propagated effects policy；
  - required observations；
  - unsupported observation；
  - recovery/reconciliation rule。

- [ ] **执行前 dependency capture 与执行后 observation 分离。**
  - 事后 observation 不能冒充事前保护。

- [ ] **未知副作用保持 UNKNOWN / unsupported。**
  - 不通过整片世界 hash 假装事务；
  - 不通过“没有看到字段变化”推断无副作用。

### 验收

- [ ] 一个 teleport ability 可以完整区分 requested destination、actual destination、failure。
- [ ] 一个原生 MobEffect 应用可以观察 add/refresh/remove，而不重复施加 vanilla attribute modifier。
- [ ] third-party native adapter 可以增加一种 observation，而不修改 `EncounterRuntime` 巨型 switch。

### 与现有缺口映射

- DM-01A：声明式原生依赖与观察
- MT-05：完整翻译证据链
- PI-03：完整世界效果差量
- DM-02/ET-04：爆炸覆盖扩展

---

# 9. P1 — Special Native Process Gate：从“阻止原版自己跑”到“由 canonical replacement 接管”

## VM-08 Special process replacement

当前已有 fail-closed gate：

- `EndermanActiveProcessMixin`
  - gate teleport；
- `DragonActiveProcessMixin`
  - gate phase tick；
  - gate fly target；
  - gate contact damage/knockback；
  - gate wall behavior；
- `WitherActiveProcessMixin`
  - gate alternative target；
- Creeper 已有正式 ability/effect adapter。

这些 gate 作为安全边界是合理的，但 **gate 本身不等于支持原版行为**。

- [ ] **保留 special Mixin 的职责为：阻止未经授权的自主 native process。**
- [ ] **不要让 Mixin 自己承担 gameplay state machine。**
- [ ] **每个被 gate 的原版行为必须能映射到：**
  - Ability；
  - Process；
  - Movement Port；
  - Event/Reaction；
  - Observation；
  - 或显式 unsupported。
- [ ] **generic platform/server core 不添加物种知识。**
  - 物种差异放在 `platform/server/builtin/*` 或独立兼容模块。
- [ ] **逐 fixed-version 审计绕过 Goal/Brain 的入口。**
  - `aiStep`
  - `customServerAiStep`
  - direct target mutation
  - phase object
  - special projectile emission
  - contact damage
  - teleport
  - periodic skills。

### 验收

- [ ] 删除某个 builtin adapter 后，对应 Mob 明确 unsupported/fail-closed，而不是恢复未经规则授权的原版行为。
- [ ] 安装 adapter 后，不需要删除/放宽 gate 来“让功能工作”。

---

# 10. P1 — Time Translation：不要把所有 native tick 机械换成“每回合一次”

## VM-09 Staged ability clock contract

当前已经存在多个不同时间模型：

- `RoundTime`
- `EffectDefinition.Clock`
  - SIMULATION_STEP
  - TURN_START
  - TURN_END
  - EXPLICIT
- `AbilityExecutor.ExecutionClock.AUTHORIZED_EXECUTION_STEP`
- Environment scheduler/process time
- server cumulative ticks / archive time

这说明项目已经正确认识到“时间不是一个变量”，但 staged native ability 仍缺正式翻译合同。

- [ ] **为 process 明确 clock domain。**
  - actor turn boundary；
  - authorized execution step；
  - environment step；
  - real server tick（只有明确允许时）；
  - encounter/global progression。
  - 不要求合并成一个万能 `ClockDomain` enum；必须明确 owner 和暂停语义。

- [ ] **固定版本 native cooldown/warmup/fuse/periodic timer 必须声明翻译政策。**
  - 不能由 GuardianAdapter / EvokerAdapter / DragonAdapter 各自随意决定。

- [ ] **区分：**
  - “模拟了多少 native progression”；
  - “玩家现实思考了多久”；
  - “经过多少 actor turns”；
  - “经过多少 environment steps”。

- [ ] **process timing policy 进入 definition/provenance/version。**

### 验收

- [ ] 同一回合停留 2 秒和 2 分钟，结果不因现实等待变化。
- [ ] restart 后剩余 warmup/phase 不被重置或补跑现实等待。
- [ ] 不同 `roundTicks` 配置下翻译结果符合显式规则。

---

# 11. P1 — Deterministic Process RNG

## VM-10 Process random stream

当前 `MinecraftMovementOpportunities` 已使用 decision-owned deterministic seed，避免推进实体自己的 vanilla random stream，这是正确方向。

但 staged process 还需要长期 deterministic entropy contract。

- [ ] **为需要随机重试/候选的 process 保存确定性 entropy identity。**
- [ ] **若 process 会跨多个 step 消费随机数，保存 cursor 或等价 canonical state。**
- [ ] **恢复不能因重新进入 callback 而重新 roll。**
- [ ] **AI random 与 gameplay/rule random 保持分离。**
- [ ] **不反序列化 Minecraft `RandomSource` 对象本身。**

### 验收

- [ ] teleport candidate retry 在 checkpoint/restart 边界前后产生相同候选序列。
- [ ] AI 重新评分不推进 process 的 gameplay RNG。
- [ ] duplicate/retry 不重复消费随机结果。

---

# 12. P1 — Persistence：从“保守转 UNKNOWN”扩展为可恢复 semantic process

## VM-11 Process persistence and reconciliation

当前正确基础：

- [x] `AbilityCheckpoint` 保存执行证据；
- [x] restart 不盲目 replay native effect；
- [x] owner instance / source change 会使旧 execution 失效；
- [x] 无法确认时保留 UNKNOWN；
- [x] triggered `PENDING / STARTED / COMPLETED / UNKNOWN` 有 durable record。

当前缺口：

- 未终态普通 action 不恢复继续执行；
- `adapterState` 非持久；
- triggered `STARTED` 恢复后直接 UNKNOWN；
- staged gameplay state 与 world-effect evidence 尚未分层恢复。

- [ ] **Process semantic state 独立持久化。**
- [ ] **定义每种 process 的恢复模式。**
  - resume pure semantic progression；
  - reconcile native state then resume；
  - fail UNKNOWN；
  - complete from confirmed observation；
  - explicit unsupported。
- [ ] **绝不通过重新执行不可逆 native callback 恢复。**
- [ ] **process state 与 effect state / encounter state / environment state 跨文件对账。**
- [ ] **owner instance replacement、death、unload、dimension change、merge 都有明确 process policy。**

### 验收

- [ ] Guardian/类似 channel 在安全 checkpoint 重启后不会免费重启技能，也不会无条件丢失 semantic phase。
- [ ] 已经发生 world effect 但尚未写终态时仍保留 observation，且不会 replay。
- [ ] process 与 encounter merge 后 owner/cause/permit 迁移一致。

### 与现有缺口映射

- 第 8 节全部 recovery / merge / archive
- DM-01B / DM-01F / ET-06 / MT-11
- DM-02：真实故障窗口与跨文件对账

---

# 13. P2 — Encounter-owned Process：Boss encounter 不应塞进 Actor Effect 或 AI

## VM-12 Encounter process

完整 Boss 战可能包含不属于单一 LivingEntity 的过程，例如：

- boss arena condition；
- 多个外部对象关系；
- encounter progression；
- 全局 phase condition；
- encounter-owned spawn/effect schedule。

- [ ] **允许 encounter-owned semantic process。**
- [ ] **Actor AI 不持有 encounter-wide state machine。**
- [ ] **Tactical Effect 不被滥用为 Boss encounter manager。**
- [ ] **Environment process 也不自动取得 encounter rule authority。**
- [ ] **Encounter process 与 Actor process / Environment process 通过 typed cause/event 协作。**

### 验收 archetype

- [ ] Dragon fight 类 encounter 可以把：
  - Dragon 自身 phase/attack；
  - arena/外部对象/encounter progression；
  分成不同 owner，同时保持同一因果记录。

---

# 14. P2 — Extension contract：用第三方差异实体证明模型，而不是只做 Vanilla 特例

## VM-13 Independent compatibility module

当前 package 重构已经为这一步提供了较好的边界：

```text
platform/server/builtin/*
generic platform/server/*
```

- [ ] **建立一个独立测试兼容模块，不能修改 generic core。**
- [ ] **至少覆盖一个与 Zombie/Skeleton 差异明显的 Mob。**
  - staged attack；
  - non-ground movement；
  - custom target/memory；
  - 或 multipart。
- [ ] **兼容模块显式声明：**
  - actor definition；
  - AI definition/provider；
  - ability adapters；
  - movement support；
  - observations；
  - process support；
  - presentation（若需要）。
- [ ] **注册冲突/版本变化/缺失 adapter 均可诊断。**
- [ ] **缺失显示适配不改变服务端 gameplay legality。**

### 完成标准

增加新 Mob 支持时，如果仍需要：

- 修改 `EncounterRuntime`；
- 修改 common `AiPlanner` 加物种分支；
- 修改通用 Mixin 加物种规则；
- 修改通用 effect runtime 加物种 event；

则该 archetype 仍暴露结构缺口。

---

# 15. Reference archetype 验收矩阵

| Archetype | 建议代表 | 主要验证的通用原语 | 当前状态 |
| --- | --- | --- | --- |
| post-result trigger | Creeper / 现有 Effect | Effect wave、trigger、observation | 已有基础 |
| pre-commit defensive reaction | Enderman 类行为 | typed causal event、intervention | 缺 |
| paid reaction | Opportunity Attack | reaction economy bridge | 资源有，生产链缺 |
| channel / warmup | Guardian / Evoker 类 | durable process、time、cancel | 缺 |
| independent subprocess | Wither | process concurrency / control ownership | 缺 |
| semantic memory | Warden/社交类 Mob | perception、memory、relation | 缺 |
| non-ground movement | Ghast / Phantom 类 | movement port | 缺 |
| composite multipart boss | Ender Dragon | root Actor + body facet + phase | 缺 |
| encounter-wide boss logic | Dragon fight 类 | encounter-owned process | 缺 |
| third-party unknown behavior | 独立兼容测试 Mob | provider/registry/fail-closed | 部分基础 |

> 代表实体只用于架构验收。H04/H05 未解除前，不因本表自动启用 Enderman teleport、飞行或 Boss gameplay。

---

# 16. 建议加入 common / target 的结构测试

## Common pure-value tests

- [ ] causal event identity / root / phase invariants
- [ ] intervention conflict ordering
- [ ] free reaction vs paid reaction
- [ ] reaction duplicate/idempotency
- [ ] process state transition legality
- [ ] process cancel/interrupt/complete separation
- [ ] process RNG cursor deterministic replay of pure state
- [ ] process persistence codec/value roundtrip（target codec 可另测）
- [ ] composite target root/facet identity
- [ ] AI memory TTL / provenance / invalidation
- [ ] relation != perception != legality
- [ ] planner cannot use unbound ability
- [ ] encounter-owned process cannot spend actor resource without rule command

## NeoForge fixed-version GameTests

- [ ] pre-damage intervention executes before `hurtServer` irreversible result
- [ ] post-damage `DAMAGED` remains after confirmed loss
- [ ] triggered staged ability survives multiple authorized steps
- [ ] restart at each process phase
- [ ] target invalidation during staged process
- [ ] owner death/unload/replacement during process
- [ ] process control release fault
- [ ] multipart hit routing
- [ ] ground/fly/custom movement provider separation
- [ ] special process gate remains closed without adapter
- [ ] adapter installation does not require relaxing gate
- [ ] multiple subprocess controls do not conflict
- [ ] observation missing => UNKNOWN, not invented success

## Static architecture tests

- [x] common domain/application direction
- [x] generic server cannot import builtin species
- [x] approved Mixin seams
- [ ] add rule: common causal/process model cannot contain Minecraft class names
- [ ] add rule: generic `platform.server` cannot reference concrete `EnderMan/WitherBoss/EnderDragon/...`
- [ ] add rule: `builtin` may depend on generic framework，generic framework may not depend on `builtin`
- [ ] add rule: persistence writer cannot depend on live process callback owner
- [ ] add rule: body facet adapter cannot create encounter member directly

---

# 17. 不应采用的“修复”

以下方案即使能让某个 Mob 工作，也不能关闭本 Checklist：

- [ ] 不通过给 `ActivationSpec.Event` 增加一批物种事件名解决反应。
- [ ] 不通过 `instanceof EnderMan/Wither/Dragon` 写入 generic `EncounterRuntime`。
- [ ] 不让 `Effect` callback 直接 `teleport/hurt/spawn/explode` 绕过 ability/process/permit。
- [ ] 不把 `AbilityCheckpoint` 改名为 Process 就宣称可恢复。
- [ ] 不持久化 `Goal` / `Brain` / `Navigation` / `DragonPhaseInstance` / callback object。
- [ ] 不让每个 Dragon part 成为独立 encounter participant。
- [ ] 不把 flight/swim/teleport 全部伪装成 ground path movement。
- [ ] 不重新执行已发生但结果不确定的 native callback 来“恢复”。
- [ ] 不用旧 verification log 证明 package refactor 后的新源码通过。
- [ ] 不以“某类型/接口已存在”替代 production consumer + fixed-version E2E。

---

# 18. 推荐实施顺序

## Wave 0 — 先封住本次 package refactor

- [ ] fresh `:common:test`
- [ ] fresh `clean build`
- [ ] fresh affected GameTests
- [ ] 更新 verification evidence

## Wave 1 — Causal Event + Reaction bridge

- [ ] typed event/phase/payload
- [ ] pre-commit intervention
- [ ] post-commit event 保持兼容
- [ ] reaction economy 接入 triggered activation
- [ ] G25 使用同一基础设施

## Wave 2 — Durable Process

- [ ] canonical process state
- [ ] triggered/manual lifecycle convergence
- [ ] timing domain
- [ ] deterministic process RNG
- [ ] cancel/interrupt/release
- [ ] persistence/reconcile

## Wave 3 — Target topology + Movement Port

- [ ] root Actor / facet target
- [ ] multipart resolver
- [ ] movement provider abstraction
- [ ] non-ground compatibility axis

## Wave 4 — AI semantics

- [ ] richer perception
- [ ] relation
- [ ] semantic memory
- [ ] expanded affordance planning
- [ ] process-start proposal

## Wave 5 — Archetype acceptance

按顺序推荐：

1. Opportunity Attack（验证 paid reaction，不涉及复杂 native AI）
2. Enderman 类 defensive teleport（验证 causal intervention；H04 未解除时可用测试 adapter，而非正式玩法）
3. Guardian（验证 staged process）
4. 非地面测试 provider（验证 movement port）
5. Wither（验证 subprocess concurrency）
6. multipart 测试实体（验证 body facet）
7. Ender Dragon（最后综合验收；需 H05 规则授权）
8. 第三方独立兼容模块

---

# 19. 与现有 `02_GAPS_AND_CONFLICTS.md` 的建议合并方式

建议不要重新建立第二份长期待办。完成本文结构复核后，将下列条目合并进入现有唯一活动清单：

| 本文 | 合并目标 |
| --- | --- |
| VM-00 | 第 10 节验收覆盖 / 构建证据 |
| VM-01 | DM-02 触发能力扩展 + G25 + H04 前置架构 |
| VM-02 | DM-01B / DM-02 / MT-04 / MT-10 / 第 8 节恢复 |
| VM-03 | DM-02 付费反应 + G25 |
| VM-04 | MT-06 + H05 前置架构 |
| VM-05 | AI bake / planner / perception + MT-03 |
| VM-06 | 移动扩展 + H05 前置架构 |
| VM-07 | DM-01A + MT-05 + PI-03 |
| VM-08 | AI/Mob special-process fixed-version adapter |
| VM-09 | AI/Ability/Environment time contracts |
| VM-10 | process recovery / deterministic execution |
| VM-11 | 第 8 节 persistence / recovery |
| VM-12 | Boss/encounter architecture；H05 前置 |
| VM-13 | DM-01C / MT-07 / MT-12 扩展兼容 |

其中 H04/H05 继续保留“暂缓”状态，除非玩家规则文件收到明确范围修订。  
结构基础可以先实现，但不能以“基础设施已存在”宣称对应玩法已支持。

---

# 20. 最终完成判据

只有当下面四条同时成立，才可以把“原版复杂战斗模型通用性”视为结构上基本闭合：

- [ ] **Enderman 类问题不再需要物种专用 core 时序分支。**
- [ ] **Guardian / Wither 类 staged/parallel behavior 能使用同一 Process contract。**
- [ ] **Dragon 类 multipart + non-ground + phase behavior 不破坏 Actor/resource 单一 ownership。**
- [ ] **新增一个差异明显的第三方 Mob 时，只需增加 provider/adapter/process/presentation 模块，不修改 common rule core 与 generic platform authority。**

在达到上述条件前，Creeper、Skeleton、Zombie 等已有纵切只能证明对应 archetype 已接通，不能证明 Vanilla combat model 已通用化。
