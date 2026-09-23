# DNDTurn Tactical Effect Refactor Acceptance — 2026-09-30

## 实施结论（2026-09-30，覆盖下方原审计结论）

**IMPLEMENTED / COVERAGE EXPANDING**。本次按已确认范围完成 Creeper 和既有原生药效纵切；不新增流血、破甲、玩家护盾或公开 Effect UI。完整目标架构不能标为 COMPLETE。

收到重新评估后，已重新执行当前快照：common83、26.1 clean build／GameTest55、三个旧target compileJava，以及Creeper／纯事件各两个独立JVM的正常保存恢复，均通过。完整日志、当前输入指纹、静态审计发现与未执行范围见[当前快照复验](version-differences/neoforge-26.1-tactical-effects.md#current-snapshot-recheck)。Checklist已区分生产接线、专项coverage和最终架构完成；不会用本次命令通过关闭Effect/world-effect Gate。

| 原审计剩余项 | 当前结果 |
| --- | --- |
| 世界行动输出 | `RuleEmission` 支持纯差量与 `TriggeredAbilityInvocation`；原子保存输出、共享 resolver/规则权威准入、类型化原生观察和子操作终态。输出不是许可 |
| 自身移除／到期／降阈值反应 | 对应实例使用转换前授予证据；其余来源仍消费冻结快照，不复活常驻 grant；common 与真实 dispatcher 测试覆盖 |
| 正式注册与 AI | `CreeperEffects` 注册 ActorDefinition、intrinsic Charge、Fuse 与三层阈值；AI 纯读 EffectSnapshot，通过既有 PLAN 提交 |
| Creeper 行为 | Charge 花 ACTION 加一层；三层产生一次持久待决触发，在该自身回合末调用原生 explode；不直接从反应回调改世界 |
| 原生药效 | 保持 Minecraft 所有权；公共 rank=amplifier+1、stacks=1，amplifier 单独可读 |
| 格式与定义版本 | 按用户修订移除 schema/protocol 版本及格式迁移分支；当前必需结构／不变量校验，未知能力 ID／语义版本拒绝。loader 必填注册字符串固定为 `dndturn`，不再维护协议号 |
| EXPLICIT 期限 | remaining 规范为0；非显式时钟仍要求有效正期限 |
| 本次验证 | common 83项、26.1 clean build、GameTest 55项通过；其他三个 target 独立 clean build 通过。两进程纯事件及触发恢复证据见版本报告 |

恢复分别记录 PENDING、STARTED、COMPLETED、REJECTED、UNKNOWN；STARTED 重启转 UNKNOWN，不重放世界动作。检查点里的定义编码为 ID／语义版本引用。已完成爆炸观察包含伤害、吸收、位移、改块、生成身份与源实体移除；原生云持久来源关联确认输出，不新增自然寿命上限。

本次正常保存／第二 JVM 加载验证了 pending 不丢失、completed 不重复、started 隔离及真实云实体来源。STARTED 窗口由夹具注入；这不是硬崩溃测试，也不承诺 Actor/Encounter/世界文件跨保存顺序原子性。即时触发仅由已接通行动收尾边界派发，任意事件上下文的自动世界行动不在本次支持范围。

爆炸只支持精确 Creeper、已加载域内目标、审计过的普通方块和已支持药效；特殊 block override、非生命目标及越域写入明确排除并记录 PARTIAL。直接爆炸范围过滤不等于所有邻居传播和外部模组回调已经覆盖。真实双客户端、原生云完整到期过程、真实崩溃窗口、通用扩展模块与规模验收仍开放，统一见 [DM-02](02_GAPS_AND_CONFLICTS.md#tactical-effect-framework)。

命令、固定版本入口、失败记录及实际验证边界见[本次版本证据](version-differences/neoforge-26.1-tactical-effects.md)。以下保留原上传快照审计，所有“未实现／未执行”描述仅指原快照，不再作为活动状态。

---

## 原上传快照审计（历史）

## Verdict

**Overall: PARTIAL ACCEPTANCE**

This snapshot contains a real implementation of the tactical-effect state core and a bounded pure-state reaction runtime. It is not a facade-only refactor.

However, the overall Tactical Effect Framework must **not** be marked `COMPLETE` yet.

The main blockers are:

1. no `TriggeredAbilityInvocation` / world-action emission path;
2. self-owned removal/expiry/down-threshold reactions can lose their binding before reaction resolution;
3. all Tactical Effect definitions/reactions remain GameTest-only; no production vertical slice exists;
4. native effect `rank()` is not normalized to the same semantic scale as tactical effect rank;
5. persistence/version migration is unresolved;
6. there is no current post-change Gradle/GameTest execution evidence.

---

# 1. Snapshot comparison

Compared:

```text
previous: DNDTurn-sources.zip
current:  DNDTurn-sources(1).zip
```

The current snapshot adds or materially changes:

```text
common:
    ConditionDefinition
    ActorRuntimeState
    ActorStates
    ActorCompilation
    ActorActivations
    ActivationSpec
    ActorSnapshot

new:
    EffectInstanceKey
    EffectSnapshot
    EffectTransition
    EffectWave

tests:
    TacticalEffectStateTest
    EffectWaveTest

NeoForge target:
    ActorStateService
    ActorSavedData
    NativeSnapshots
    ServerCombatService

new GameTests:
    TacticalEffectChecks
    EffectReactionChecks
    EffectReactionRestartChecks
```

This is a substantive state/ownership change, not only a naming layer.

---

# 2. Accepted: Tactical Effect state core

## 2.1 Instance identity

Implemented:

```text
SINGLE_PER_TARGET
PER_SOURCE_ACTOR
PER_SOURCE_ABILITY
UNIQUE_APPLICATION
```

`EffectInstanceKey` is now derived from Definition policy rather than caller UUID.

This closes the previous problem where the caller implicitly decided stacking merely by reusing an instance UUID.

**Status: PASS**

---

## 2.2 Rank and stacks are separated

`ActorRuntimeState.Condition` now has independent:

```text
rank
stacks
```

and effect grants can require:

```text
minimumStacks
minimumRank
```

**Status: PASS for tactical effects**

A native projection inconsistency remains; see blocker 4.

---

## 2.3 Explicit stack mutation

Implemented:

```text
AddStacks
ConsumeStacks
RefreshDuration
Apply
Remove
Advance
```

There is no new unrestricted public `SetStacks`.

Overflow policy is explicitly represented:

```text
REJECT
CLAMP
```

and duration refresh is independently represented:

```text
KEEP
REPLACE
MAXIMUM
```

Although the old `Stacking` enum remains as the apply mode, stack overflow and duration refresh are no longer one undifferentiated policy.

**Status: PASS**

---

## 2.4 Contributions and conditional grants

Implemented:

```text
Modifier.Scaling.CONSTANT
Modifier.Scaling.PER_STACK
```

and conditional ability grants:

```text
minimumStacks
minimumRank
```

`ActorCompilation` applies these values into rule facts and AbilityBindings.

GameTest `TacticalEffectChecks` verifies:

- effect modifier reaches ActorSnapshot facts;
- stack-scaled contribution;
- grant unavailable below threshold;
- grant available at threshold;
- duration-only refresh does not revoke the grant;
- stack consumption can revoke the grant.

**Status: PASS**

---

## 2.5 EffectSnapshot

ActorSnapshot now includes:

```text
List<EffectSnapshot>
```

with:

```text
EffectSnapshot.Tactical
EffectSnapshot.Native
```

This correctly preserves separate ownership:

```text
Tactical effect
    owner = DNDTurn Actor state

Native MobEffect
    owner = Minecraft
```

The snapshot is immutable and native hidden-effect chains are not converted into tactical stacks.

**Status: PASS with rank-normalization issue**

---

# 3. Accepted: transition and bounded pure reaction mechanics

## 3.1 EffectTransition

Implemented transition evidence:

```text
before
after
beforeRevision
afterRevision
events
```

Events include:

```text
APPLIED
REMOVED
EXPIRED
STACK_CHANGED
```

Edge predicates exist:

```text
CROSS_UP
CROSS_DOWN
BECAME_ZERO
BECAME_NONZERO
```

This is materially better than checking only:

```text
stacks >= threshold
```

and prevents normal level-trigger repetition.

**Status: PASS**

---

## 3.2 Same-wave consistency

`ActorStateService.drain()` captures a single actor state/snapshot for a reaction wave, resolves all matching callbacks against that frozen revision, then reconciles writes through `EffectWave`.

GameTest verifies two callbacks see the same revision.

**Status: PASS**

---

## 3.3 Conflict handling

`EffectWave`:

- merges commuting `AddStacks`;
- merges bounded `ConsumeStacks`;
- rejects over-consumption;
- rejects incompatible writes to the same effect/resource;
- previews the full result before owner commit;
- preserves original proposed-mutation counts.

This is fail-closed and prevents registration order from silently deciding conflicting writes.

**Status: PASS**

---

## 3.4 Bounds

Implemented:

```text
MAX_DEPTH     = 16
MAX_EVENTS    = 256
MAX_MUTATIONS = 512
```

and explicit faults:

```text
EFFECT_DEPTH_LIMIT
EFFECT_EVENT_LIMIT
EFFECT_MUTATION_LIMIT
EFFECT_WRITE_CONFLICT
...
```

Cycle GameTest exists.

**Status: PASS**

---

# 4. Accepted with pending runtime verification: reaction recovery

The new model adds durable:

```text
ReactionDelivery
    PENDING
    COMPLETED
    FAULTED
```

and persists deliveries in `ActorSavedData`.

Wave operation IDs are deterministic from:

```text
reaction root
actor
wave event identities
```

On recovery, already committed wave receipts are reused and derived transitions are reconstructed instead of running the callback again.

`EffectReactionRestartChecks` also contains a selected separate-JVM persistent test path.

Architecturally this is a valid recovery design for the current pure-state reaction model.

However, this cannot be declared fully accepted at runtime because no fresh post-change Gradle/GameTest log exists and the current environment cannot download Gradle 9.7.1.

**Status: IMPLEMENTED / CURRENT ACCEPTANCE PENDING**

---

# 5. BLOCKER 1 — no TriggeredAbilityInvocation

This is the largest missing piece.

Current reaction API is still:

```java
ActorActivations.Rule
    -> List<ActorStates.Change>
```

It can mutate:

```text
effect stacks
resources
persistent grants
other Actor-owned state
```

but cannot emit:

```text
TriggeredAbilityInvocation
```

or an equivalent typed rule emission.

`RuleResolver` also still contains:

```text
if activation != MANUAL
    return UNSUPPORTED("activation driver unavailable")
```

Therefore the target vertical slice:

```text
Creeper Charge
    ↓
Fuse 2 → 3
    ↓
CROSS_UP 3
    ↓
TriggeredAbilityInvocation(creeper_explode)
    ↓
RuleResolver / admission / permit / executor
```

cannot currently be expressed.

A reaction can only mutate ActorState.

This means the system currently implements:

> reactive state mutation

not yet:

> standard tactical effect-triggered gameplay actions.

**Status: BLOCKING — Tactical Effect Framework cannot be COMPLETE**

---

# 6. BLOCKER 2 — self-owned REMOVED / EXPIRED / CROSS_DOWN reactions lose their source binding

Reaction matching currently uses:

```text
commit state transition
    ↓
capture current ActorSnapshot
    ↓
iterate current snapshot.abilities()
    ↓
find triggered registrations
```

This is correct for many post-state reactions but fails for lifecycle reactions granted by the effect being removed.

Example:

```text
Effect A
    grants on-expire Ability X

Effect A expires
    ↓
Effect A removed from ActorRuntimeState
    ↓
ActorCompilation no longer grants Ability X
    ↓
EXPIRED event is delivered
    ↓
current snapshot has no Ability X
    ↓
X cannot react
```

The same problem applies to:

```text
REMOVED
BECAME_ZERO
CROSS_DOWN below a grant threshold
```

if the reaction ability came from the effect whose post-transition state no longer grants it.

The current tests avoid this case by having a separate `OBSERVER` effect remain alive while observing changes on `COUNTER`.

Required fix should preserve the reaction authority/source from the appropriate transition boundary, for example:

```text
EffectTransition
    captures eligible before/after reaction bindings

or

EffectDefinition reaction registry
    resolves lifecycle reactions independently of current post-state AbilityBindings
```

The exact type is flexible, but lifecycle events cannot depend only on post-commit current grants.

**Status: BLOCKING**

---

# 7. BLOCKER 3 — no production Tactical Effect vertical slice

Static search of production source finds no actual calls to:

```text
TacticalCapabilities.conditions().register(...)
TacticalCapabilities.registerActorEffect(...)
ActorDefinitions.register(...)
```

outside the registration APIs themselves.

All new Tactical Effect definitions and reaction registrations are inside GameTest sources.

Therefore the framework has:

```text
real core implementation
real test integration
```

but not:

```text
real gameplay integration
```

At minimum, completion should require production examples such as:

```text
one simple buff/debuff
one stack/consume effect
Creeper Fuse
```

The Creeper slice is especially important because it proves the missing transition from effect reaction to normal gameplay ability execution.

**Status: BLOCKING for COMPLETE; acceptable for framework-core milestone**

---

# 8. ISSUE 4 — native rank is not semantically normalized

`EffectSnapshot` exposes one common method:

```text
rank()
```

but its two implementations use different scales.

Tactical:

```text
rank >= 1
```

Native:

```text
rank = raw MobEffect amplifier
```

so:

```text
Strength I
    native rank = 0

tactical rank I
    tactical rank = 1
```

This defeats the value of a standardized `EffectSnapshot.rank()` for AI/UI/rules.

The current GameTest explicitly asserts raw amplifier semantics, so this is deliberate in the implementation, not an accidental typo.

Recommended correction:

```text
EffectSnapshot.Native.rank = amplifier + 1
```

If raw native amplifier must remain observable, expose it under a native-specific field instead of overloading the common semantic `rank`.

**Status: SHOULD FIX BEFORE STANDARD EFFECT VIEW IS COMPLETE**

---

# 9. ISSUE 5 — persistence/version story remains incomplete

`ActorSavedData` schema jumps:

```text
1 → 4
```

and explicitly rejects any schema other than 4.

The previous snapshot writes schema 1, so an existing actor-state save from that snapshot will now produce:

```text
Actor checkpoint rejected;
original data retained and actor rules unavailable
```

This is fail-safe, but it is not a migration.

Additionally:

```text
ActorRuntimeState.Condition
```

still stores the full `ConditionDefinition` object rather than a small stable definition reference, while `ConditionRegistry` is keyed only by effect ID.

Consequences needing an explicit long-term policy:

- effect definition upgrade;
- persisted old definition version;
- coexistence/migration of old effect instances;
- data-pack/mod semantic reload.

This does not invalidate the new in-version reducer, but persistence/versioning cannot be marked complete.

**Status: PARTIAL**

---

# 10. Effect duration still has one residual modeling compromise

`Clock.EXPLICIT` effects still require:

```text
remaining >= 1
```

even though `remaining` has no true countdown meaning for explicit-lifetime effects.

This is usable, but the value is a dummy positive duration rather than a semantically clean lifetime representation.

Not a P0 blocker, but the finalized TacticalEffectInstance should avoid requiring meaningless state.

**Status: MINOR ARCHITECTURE DEBT**

---

# 11. Test-change review

The unrelated GameTest changes do not appear to weaken acceptance.

Examples:

```text
BodyControlChecks
EnvironmentProcessChecks
ParticipantEffectsChecks
```

add:

```java
level.waitForEntities(...)
```

after chunk forcing.

`DiscoveryCursorChecks` also adds an assertion that all fixture entities still resolve to their current native instances before scanning.

This replaces fixed timing assumptions with stronger native-load evidence.

`ServerCombatService.regionChunksLoaded()` is also tightened to require:

```text
chunk present
shouldTickBlocksAt
entities loaded
FullChunkStatus >= BLOCK_TICKING
```

before spending an environment step.

**Status: ACCEPT**

---

# 12. Build / runtime evidence

Performed on this snapshot:

```text
javac --release 17
common/src/main/java/**
```

Result:

```text
PASS
```

Attempted:

```text
targets/neoforge-26.1
./gradlew :common:test
```

The uploaded wrapper has CRLF line endings, so a temporary LF-only copy was used for acceptance.

Gradle then attempted to download:

```text
gradle-9.7.1-bin.zip
```

but the acceptance environment has no external network access.

Therefore these were **not executed here**:

```text
:common:test
clean build
runGameTestServer
separate-JVM effect_reaction_restart
```

All `verification-*.log` files in the snapshot are timestamped 2026-09-29, while the new Effect sources/tests are timestamped 2026-09-30.

Those logs cannot be used as evidence for the current modifications.

---

# 13. Checklist delta

## Can now be checked

```text
[x] instance policy
[x] deterministic merge key
[x] rank and stacks separated for tactical instances
[x] AddStacks
[x] ConsumeStacks
[x] RefreshDuration
[x] contribution scaling
[x] conditional ability grants
[x] APPLIED / REMOVED / EXPIRED / STACK_CHANGED transition events
[x] CROSS_UP
[x] CROSS_DOWN
[x] BECAME_ZERO
[x] BECAME_NONZERO
[x] pure bounded reaction waves
[x] same-wave revision consistency
[x] conflict detection
[x] depth/event/mutation bounds
[x] durable pure-reaction delivery ledger
[x] Tactical/Native EffectSnapshot separation
```

## Must remain unchecked

```text
[ ] TriggeredAbilityInvocation output
[ ] effect reaction capable of normal gameplay/world action
[ ] self-owned REMOVED reaction
[ ] self-owned EXPIRED reaction
[ ] self-owned downward-threshold reaction
[ ] Creeper Fuse production vertical slice
[ ] production TacticalEffect registration
[ ] AI uses EffectSnapshot
[ ] normalized common native rank semantics
[ ] definition-version migration policy
[ ] current :common:test evidence
[ ] current clean build evidence
[ ] current runGameTestServer evidence
[ ] verified separate-JVM reaction restart
```

---

# 14. Recommended next change set

Do not redesign the reducer again.

The state core is now good enough to retain.

Next work should be narrowly focused on:

```text
1. RuleEmission
       ├── ActorStateMutation
       └── TriggeredAbilityInvocation

2. activation driver
       TriggeredAbilityInvocation
           ↓
       normal rule resolution
           ↓
       admission
           ↓
       normal executor

3. lifecycle reaction source semantics
       REMOVED / EXPIRED / CROSS_DOWN
       must preserve pre-transition eligibility

4. production Creeper Fuse vertical slice

5. normalize EffectSnapshot.Native rank

6. run current full acceptance
```

The key acceptance target should be:

```text
Creeper uses Charge
    ↓
Fuse stack changes through ActorStates
    ↓
CROSS_UP threshold is emitted once
    ↓
effect reaction emits a typed triggered invocation
    ↓
the invocation enters the same rule/authority/native-execution chain as other abilities
    ↓
restart cannot lose or duplicate that trigger
```

Once that succeeds, the Tactical Effect framework can reasonably move from:

```text
PARTIAL / STATE CORE IMPLEMENTED
```

to:

```text
IMPLEMENTED / COVERAGE EXPANDING
```

but not before.
