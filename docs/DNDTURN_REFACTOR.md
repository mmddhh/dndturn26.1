# DNDTurn 数据模型与 CombatNexus 接入重构方案

本文面向 dndturn 开发者，目标是统一玩家、怪物、装备与能力的数据模型，并让 CombatNexus 承担通用战斗执行。可以替换现有类型和内部 API，不要求为当前模型保留兼容包装；保留已确认的玩法、内容、操作入口和结果语义。

方案依据 CombatNexus `5060481` 与同步后的 dndturn `3933318`，并核对 dndturn 工作区已更新的 NeoForge 配置。下文“现有”指源码已有能力，“目标”与“建议”指待实施设计。本文不是新的仓库开发规范，也不代表两模组已经联合接通。

## 一 结论与职责边界

目标结构是 **dndturn 裁决战术规则，CombatNexus 执行通用战斗效果，Minecraft 保存原生实体事实**。两边通过 target 中的窄适配器协作，不把 CombatNexus 的 Java 类型引入 dndturn common。

玩家和怪物应进入同一 Actor、属性派生、能力绑定及伤害解析链。玩家的职业成长和怪物的模板配置是不同来源，不应形成两套战斗后端。装备应以完整注册 ID、实例观测与类型化规则数据参与计算，不能靠名称前缀猜属性。

| 内容 | 唯一规则或状态所有者 |
| --- | --- |
| Encounter、先攻、回合资格、敌对、field、行动与反应费用 | dndturn |
| 职业与种族、成长选择、学习与准备、六维、熟练、AC、命中与豁免 | dndturn |
| 战术伤害骰、暴击、类型抗性与免疫、豁免成功后的伤害规则 | dndturn 的一次解析 |
| 来源安装、通用属性贡献、通用 Effect、护盾、治疗、战斗写入证据 | CombatNexus |
| 原生生命、装备与库存、耐久、实体身体和世界位置 | Minecraft，通过各自受控入口修改 |
| 跨回合战术过程、行动检查点、合并与取消 | dndturn 既有过程 owner |
| 通用能力内部续行、已安装来源的业务时间 | CombatNexus，按明确驱动策略执行 |
| 原生世界过程、方块、投射物物理、AI 行动门控 | dndturn 既有平台 owner；业务时钟不能替代这些机制 |
| UI、日志和解释 | 消费同次服务器快照及已确认结果 |

“统一模型”不意味着把生命、行动点和物品数量放进一个万能数值容器，也不意味着建设新的全局 Runtime 或 ProcessManager。

## 二 当前实现中的实际问题

### CombatNexus 已提供的基础

现有 `CombatWorld` 已公开 `equipment`、`abilities`、`drivers`、`time`、`facts`、`restrictions` 及领域服务。装备贡献支持 `ABILITY`，来源计划保存贡献 ID 到能力定义 ID 的映射；能力具备 grant、查询、执行、取消、续行、冷却和当前格式保存恢复。

外部驱动按 `TIME / ACTIVE / TRIGGER / PERIODIC / NUMERICAL` 独立授权。`DriverSession.damage(ResolvedDamage)` 可提交已解析通道分量；其路径跳过通常的数值计算，并抑制原版护甲、保护附魔、抗性 Effect 和固有减伤。护盾、吸收和受管保护由注册时的所有权声明决定。已解析治疗通过 `DriverSession.heal` 提交。

这些是接入基础，尚不能据此把整个 dndturn 后端直接替换掉。尤其是定义代际、回合边界、战术来源和重试语义仍需明确映射。

### 同步后的 dndturn 需要处理的问题

| 优先级 | 源码事实 | 重构要求 |
| --- | --- | --- |
| 高 | `GhastAbilities.Fireball.start` 两段均调用 `hurt(...mobAttack...)`，未读取伤害结果，最后直接报告 `COMPLETED` | 接入有类型的伤害分量和逐目标回执，按实际结果汇总；不能用“调用过”表示完成 |
| 高 | Ghast 声称火焰和力场，实际请求未表达这两种类型；区域使用 `AABB.inflate(6)`，没有球形距离过滤 | 明确火焰/力场映射及区域几何；若采用文档中的半径 6，则显式使用球形规则，不能把包围盒当球体 |
| 高 | `SaveService.bonus` 读取 `CharacterContentService.current()`；内容合同要求活动遭遇固定 `EncounterContent` | 从当前遭遇的内容绑定和同次 Actor 快照解析豁免，禁止执行过程中偷读 latest |
| 高 | CombatNexus grant 要求当前定义 generation；dndturn 要求活动遭遇保持原内容语义 | 联合接入必须解决定义生命周期差异，不能将两边 revision 当同一身份 |
| 中 | `PlayerWeaponRules` 丢弃 namespace、剥除 `iron_` 后查表；`PlayerArmorRules` 也仅使用 path 匹配 | 使用完整物品 ID 的显式绑定。第三方同名物品不能意外获得 dndturn 武器或护甲规则 |
| 中 | 武器资料、玩家护甲映射、怪物攻击档案、`MobDefinition` 和能力注册各有入口 | 编译为共同的 Actor 配方、装备规则和能力定义，删除重复权威表 |
| 中 | `DamagePacket` 已有分量和防御解析，主攻击链仍主要传标量 | 让分量贯穿骰子、减免、应用与证据，标量只作汇总 |
| 中 | Ghast 自建 `Random`，没有沿主攻击链保存完整掷骰事实 | 统一操作级采样与记录，续行和重试使用已有事实 |

Ghast 的区域当前会在命中失败后继续执行，也可能再次命中主目标。重构应显式表达这两个选择，不能在搬代码时暗改玩法。文档中的“火球”也不等于必须创建原版火球实体。

此外，dndturn 工作区的 NeoForge 已更新为 `26.1.2.126`，与 CombatNexus 一致；Minecraft 声明仍为 `26.1`，版本范围仍为 `[26.1,)`。CombatNexus 声明为 `26.1.2`、范围 `[26.1.2.126,26.1.3)`。联合运行前核对实际目标并同步元数据与支持范围，不由目录名推断兼容。

## 三 理想数据模型

以下名称是建议的领域形状，可直接重命名或重组现有类型。它们不是要求并行增加一套对象与仓库。

### 定义 内容绑定 实例与观测分开

```text
RulesCatalog                       不可变内容集合
  ActorArchetype                   玩家或怪物的组合配方
  ClassDefinition / SpeciesDefinition
  ProgressionDefinition            升级与选择规则
  EquipmentDefinition              武器、护甲、物品能力规则
  TacticalAbilityDefinition        目标、费用、裁决与过程规则
  ConditionDefinition              战术条件语义
  DamageKindDefinition             伤害种类与平台映射

ActorPersistentState               Actor 长期选择和余额
  Identity / ArchetypeBinding
  CharacterDevelopment?            玩家成长；怪物不携带空玩家对象
  LearnedAbilities / Preparation
  OwnedResources

ActorRuntimeState                  只保留其真正拥有的运行状态
  TacticalConditions
  ProcessReferences

ActorObservation                   当前原生实例的只读事实
  NativeIdentity / Vitals / Attributes
  Equipment / NativeEffects / Position
  CombatNexusSourceAndGrantViews

ResolvedActorSnapshot              同次捕获后的战术结果
  AbilityScores / Skills / Saves / Defense / Movement
  AbilityBindings / ResourceViews / Provenance

EncounterMember                    遭遇内成员状态
  ActorReference / Initiative / TurnResources / Eligibility
```

`ActorArchetype` 由基础数值来源、分类、移动配置、固有授予和 AI 引用组成。玩家配方再组合职业、种族与选择；怪物配方组合怪物内容。两者都产出同一种 `ResolvedActorSnapshot`，避免每个消费者各写一遍 `instanceof ServerPlayer`。

`CharacterDevelopment` 只保存已经做出的角色选择、等级和 XP 等事实；熟练加值、攻击加值和最终 AC 由定义与来源派生。怪物配置的固定豁免加值使用明确的覆盖策略，不与六维修正无条件相加。

不把当前 HP 再保存到 Actor 长期状态中。最大生命成长如果属于既定玩家玩法，应由一个明确贡献路径更新原生最大生命，角色面板读取其结果；怪物模板中的设计 HP 继续不能覆盖已确认的原生 HP 规则。

### 属性模型

采用有类型和单位的属性键，并区分基础事实、规则计算和有效结果。例如 `native.armor`、`tactical.armor_class`、`ability.strength`、`save.dexterity` 各有独立含义。

```text
原生有效属性与来源事实 + 配方/成长 + 当前装备规则 + 活跃战术贡献
  → 同一战术解析器
  → 数值结果及有序解释
  → 执行、AI、预览、角色卡
```

AC 用明确的 `ArmorClassRule` 表达：原生护甲映射、无甲、轻甲、中甲、重甲等分别声明基数、DEX 处理、装备条件和叠加政策。把当前“完整套装”规则写进条件，不在重构中默认换成其他桌游规则。怪物和玩家可以选择不同 AC 规则，但共用解析器。

原生 effective 已包含的装备或药效收益只解释一次。战术专属规则由 dndturn 计算；真正作用于 Minecraft/CombatNexus 通用属性的贡献交给 CombatNexus 安装。两者通过来源与贡献 ID 关联，不互相复制余额或反复加算。

### 装备模型

`EquipmentDefinition` 使用完整内容 ID，平台通过完整物品注册 ID 或显式物品组件绑定。一个定义可包含武器、护甲及授予能力等类型化部分。

武器部分包含伤害分量、握持模式、攻击属性选择、熟练类别、射程/触及和属性集合。`finesse`、`versatile`、`ammunition` 等应是类型化语义；展示字符串由它们生成，不解析 `"finesse, light"` 来执行规则。

装备实例事实记录槽位、选择证据、当前物品组件和 CombatNexus 安装身份。装备定义不拥有库存、数量和耐久；卸装、同值替换、组件变化后的旧计划仍按实例证据失效。`CombatWorld.equipment(actor)` 提供已对账贡献和 grant，未接入 CombatNexus 的原生装备仍需平台观测。

铁制装备的注册、模型、翻译和已确认数值保留。替换 `DndEquipmentCatalog` 的可变全局表与 path 猜测，将目录、规则、说明和起始装备列表引用统一到同一内容集合。

### 能力模型

统一玩家技能、怪物动作、装备能力、职业特性和学习能力的描述，但分开四层：

| 层 | 内容 |
| --- | --- |
| 定义 | 目标约束、选择参数、行动费用、裁决规则、过程及效果引用 |
| 授予 | 来自哪个来源实例、哪项贡献、授予哪个 Actor |
| 可用性 | 是否已准备、当前资源、冷却、限制、合法目标和来源是否仍有效 |
| 执行 | operation、锁定目标、已消费费用、骰子事实、当前步骤与回执 |

学习、准备和固有授予是来源政策，不把所有 grant 都伪装为玩家学习。Actor 长期状态保存学习与准备；装备由实际装备来源产生 grant；怪物固有能力由 archetype 来源产生。来源撤销只取消尚未完成的适用过程，已确认效果保留。

将现有巨大 `MobAction` 改为类型化动作描述，按 `Attack / SaveArea / Summon / Channel / NativeAction` 等实际支持类别表达所需字段，消除一个 record 中大量不适用的零值和 null。只编译已有玩法消费者，不先实现完整脚本语言。

多重攻击是一个能力的有序步骤；充能是明确资源及刷新规则；传奇抗性是反应/资源规则。未实现的条目保持未支持，不因模型有字段而自动开放。

CombatNexus 的 `AbilityDefinition` 是通用效果执行定义，没有学习、准备、行动费用和 Encounter 权限。目标仍需一份 dndturn 战术包装描述，但它不复制 CombatNexus 的运行状态：

- dndturn 原生战术能力由既有过程 owner 执行，效果经 CombatNexus 领域入口应用。
- 导入的 CombatNexus 能力由 dndturn 负责发现、费用和战术授权，随后委托其 executor；dndturn 保存执行引用及结果，不另推进相同续行。
- 一个具体效果步骤只有一个 executor。不能同时跑 dndturn 旧处理器和 CombatNexus ability executor。

### 伤害与随机事实

建议将 `DamagePacket` 拆成意图、裁决结果和应用证据：

```text
AttackIntent / SaveIntent
  → CapturedResolutionInput
  → RollFacts
  → ResolvedDamagePacket
      componentId / damageKind / rolled / afterSave / afterDefense
  → CombatNexus ResolvedDamage
  → DamageApplicationEvidence
      target / healthLoss / absorptionLoss / shieldLoss / outcome
```

命中、暴击、豁免、免疫/抗性/易伤和舍入顺序在 dndturn 解析一次。保留当前已确认规则；新规则必须显式替换旧公式，不能悄悄混用。`DefenseProfile.magical` 的孤立布尔值应改为防御规则的条件与攻击事实的匹配，避免“Actor 是否魔法”承载不清楚的意思。

每次操作保存实际骰子事实与必要输入，随机源由操作裁决入口提供。保存 seed 不能替代已发生的骰子和事件修改结果。豁免事件后的最终 die/bonus/DC 也属于事实；查询与 UI 不掷骰。

伤害种类、CombatNexus 通道和 Minecraft damage type 分开。13 种战术伤害不直接冒充 13 个已存在通道；火焰可映射现有 `combatnexus:fire`，力场等需要显式定义或映射。需要被护盾过滤或证据区分的种类不能随意合并。多个分量可在一个原生应用中处理；不同目标或不同伤害事件分别提交，避免把一次混合伤害拆成多次原生 hurt 而额外触发无敌帧和反应。

### 条件 资源与时间

战术条件用明确的行为贡献表达：限制行动、调整命中/豁免、属性贡献、边界触发与解除条件。不能把每个条件只压成一个通用伤害倍率。

战术条件的回合寿命归 dndturn；其附带通用属性/Effect 投影归 CombatNexus 来源安装。以同一来源关联，在 dndturn 到期时撤销投影，不能让两个系统分别倒计时。同理，原生药效若迁交 CombatNexus 业务时间，dndturn 的 `VanillaEffectRoundController` 必须停止推进同一实例，改为读取和驱动适配。

动作、反应、移动预算归 Encounter；职业资源和有限次数归 Actor；护盾余额归 CombatNexus。资源刷新按已确认的回合/休息边界执行，不以普通服务器 tick 代替。

时钟至少明确区分：真实等待时间、世界 tick、战术边界和 CombatNexus 业务 tick。回合到 tick 的映射来自捕获的规则设置，不硬编码“一个回合等于固定 tick”覆盖所有效果。按“自身回合结束”触发与按“每 N 业务 tick”触发不是同一种寿命策略。

## 四 内容生命周期

将玩家、怪物、装备、能力、条件和类型映射编译为一个不可变 `RulesCatalog`，在采用前检查引用与执行器支持。按场次保存其内容绑定；AI、发现、命中、豁免和 UI 均从同一绑定读取。原生动态事实仍在操作边界重新捕获。

长期状态只保存选择和自己拥有的值。来源与 grant 在恢复时重新对账，旧句柄不复活。内容 revision、世界 session、实体 instance 与 operation 是不同身份，不新增 schema/protocol version，也不为旧格式维护迁移代码。

**CombatNexus 还缺少与活动遭遇固定内容直接匹配的公开能力生命周期契约。** 当前 `DefaultAbilityService.current` 要求当前 generation，重载对账会使旧 grant 失效；普通在途请求的定义句柄不等于跨回合内容租约。

理想方案是在 CombatNexus 增加受控的定义绑定生命周期：已建立的外部执行/安装显式引用被保留的不可变定义，来源撤销和会话结束后释放；新安装使用新定义。dndturn 只持有引用，不持有内部仓库或恢复权限。具体支持哪些注册、原生资源和定义可保留必须明确，不能仅把旧 grant 的 generation 检查删掉。

在该能力落地前，不应声称热重载中的跨回合通用能力已支持。首个实现切片可使用 dndturn 固定的战术内容直接提交已解析领域请求；该路径仍需记录当次 CombatNexus 应用定义，不能宣称整个跨模组场次完全固定语义。

## 五 CombatNexus 接入契约与缺口

### 注册 查询与授权

通过 NeoForge 游戏事件总线上的 `CombatRegisterEvent` 注册 executor、内容和 `DriverDefinition`，保留返回的真实 `DriverRegistration`。注册回调结束后不可继续使用 registrar。相同 ID 的自制对象不能替代注册凭据。

服务器线程通过 `CombatAccess.find(WorldLookup)` 查询已经存在的世界服务。查询失败表示当前不可接入，不自行创建内部 runtime。适配器只使用公开 API，不依赖 `internal` 或 `spi`。

`DriverService.bind(registration, target, scopes, source)` 的 target 在数值模式下是**受伤或受治疗者**，在能力模式下是施法者。`NUMERICAL` 当前只允许实体范围，即 `Optional.empty()`；来源范围需要实际安装的来源键，不能拿能力定义 ID 或装备模板 ID 顶替。

建议第一步使用实体范围绑定。若最终需要精细来源绑定，应补公开的来源键访问契约，避免 dndturn 复制 `SourceInstallations.key` 的内部编码规则。

### 驱动不是全开开关

| scope | 现有含义 | 采用条件 |
| --- | --- | --- |
| TIME | 接管对应业务时间，普通世界 tick 不再自然推进该受管时钟 | dndturn 已能在合法战术边界推进并收束未完成增量 |
| ACTIVE | 控制通用主动能力执行和相应续行 | 战术提交入口已经接通 |
| TRIGGER | 控制相应结算反应与触发能力 | 已明确哪些反应由谁调度，能避免漏掉触发 |
| PERIODIC | 控制周期行为与周期能力 | 已有外部周期调度或明确委托策略 |
| NUMERICAL | 拒绝目标的普通受管伤害/治疗，要求已解析入口 | 所有需要生效的数值路径已有接入，包含环境和派生效果 |

不要为“接管战斗”一口气声明五个 scope。只接管 TIME 时，允许的通用周期行为可随显式业务时间推进；再接管 PERIODIC 会改变这些行为的执行资格，必须另有执行者。

当前 `AbilityExecution.damage`、`DomainCommand.Heal` 仍是普通领域请求。目标被 NUMERICAL 接管后，它们不会自动获得已解析权限。因此“绑定所有 scope 后直接调用任意通用能力”不是可行的完整方案。

目标 API 需要在通用能力的短期执行上下文中提供类型化的数值委托：向外部裁决者提交明确输入，再以当前实例、真实父链和权限交集应用已解析结果。现有精确请求关联必须保留，不能开放一个线程标记，让嵌套普通伤害也跳过计算。该扩展属于 CombatNexus 通用驱动能力，不写 dndturn 特判。

### 已解析数值与结果

`ResolvedDamage` 包含 requestId、来源、原生 damage type 和有序通道分量；1 至 32 项，非负有限数，总量至多 1000000。伤害通道必须已注册。使用前已完成战术计算，不能随后再执行同一份 AC、抗性或暴击。

注册时分别声明护盾、吸收和受管保护由 `COMBATNEXUS` 还是 `EXTERNAL` 执行。`EXTERNAL` 的含义是调用者已经处理该机制，不是要求 CombatNexus 替调用者扣除对应余额。默认建议仍由 CombatNexus 应用这些资源，dndturn 只消费回执。原生死亡保护事件仍属于原生路径。

结果读取 `DamageResult.settlement()`、治疗 settlement 与 application journal，分清确认生命损失、吸收损失、护盾损失、取消、部分和未知。`applied()` 或原生布尔返回值不能代替完整结算。CombatNexus 返回对象中可能包含原生 request 引用，dndturn 持久历史只保存所需纯值证据。

普通请求 ID 是单次提交身份，重复提交返回拒绝，不保证返回原回执。dndturn 既有 operation/result owner 继续负责同 ID 同负载返回既有结果、异负载拒绝；未知写入不换 ID 重试，也不自动退款。

### 业务时间与恢复

外部推进只在空闲服务器入口调用 `session.advance(sequence, requestId, ticks)`，不能从 COMPUTE、完成通知或嵌套战斗执行中推进。单次 ticks 为 1 至 1200000，推进受服务器预算约束，可能返回未完成回执。

未完成时，下次使用相同 sequence、requestId 和原始 ticks 继续；不要把 remaining 当新请求量，也不要重复赠送整个回合。恢复后先读 `session.time().latest()`，若未完成则继续它，否则使用 `nextSequence()` 开始新增量。不得在同一服务器 tick 忙循环重试预算拒绝。

当前 `close()` 会检查活会话，重复关闭可抛错；未完成的时间增量也会阻止释放。dndturn 的清理 owner 必须记录释放阶段、完成剩余增量或进入明确的待处理状态，不能无条件多次 close。理想 API 可补类型化的释放结果和未完成推进处置，但不能用清零吞掉已经消费的时间。

读档恢复保留驱动所有权但不自动恢复可执行 session。等待世界与来源恢复后，凭当前注册凭据重新 bind，再恢复业务推进。离场、合并、卸载和停服不得重复安装来源、补发护盾或重放完成步骤。

## 六 建议实现顺序

### 第一阶段 统一定义与角色读取

把当前玩家成长、怪物模板和装备资料编译进共同内容集合；实现统一 Actor 配方和战术属性解析。替换 `SaveService` 对 latest 的读取，修复装备 namespace 丢失，保持现有规则数值。职业、种族、铁制物品和怪物内容转为同一模型的输入。

完成条件：玩家与怪物的预览、AI、执行和角色卡读取同一解析结果；重载不改变活动遭遇的规则；装备同名异 namespace 不混淆；旧的重复数值表已退出执行。

### 第二阶段 打通有类型的伤害切片

统一 `AttackProfile`、`DamagePacket` 和伤害证据；把 Ghast 作为多阶段、多目标切片，但先确定其区域几何、两段关系及类型映射。沿现有 `ActionExecutionCoordinator` 建立唯一父操作、有限子操作和每目标结果，不另建过程管理器。

对接 CombatNexus 已解析入口，保持 dndturn 命中/豁免与费用所有权。主攻击、弓箭命中、药效、环境和反应数值路径逐项迁移；路径未覆盖前不对整个 Actor 开启 NUMERICAL 后宣称闭环。

完成条件：有实际火焰/力场语义；每段每目标可追溯；原生拒绝、护盾吸收、部分写入与异常不被改写为全部成功；随机和费用只消费一次。

### 第三阶段 统一授予 能力与来源

重组 dndturn 的能力定义与绑定，统一固有、装备、学习和准备来源。先将一个真实装备能力接入 `CombatWorld.equipment` 与 `abilities`，再扩展怪物动作。为每种能力声明由 dndturn 还是 CombatNexus 推进内部步骤。

补齐通用能力与数值外部裁决的接缝后，才扩大 ACTIVE/TRIGGER/PERIODIC 接管。删除被替代的旧 executor 和重复贡献安装，不保留隐式双执行分支。

### 第四阶段 条件 时间与当前格式恢复

明确战术条件和通用 Effect 的投影关系，逐类迁移 `VanillaEffectRoundController` 的职责。接通 TIME 的增量、预算中断、续行、退出与恢复。补足定义绑定生命周期后，验证热重载与跨回合能力的组合。

共同完成创建、离场、死亡、同 UUID 新实例、跨维度、Encounter 合并、世界卸载和两进程恢复。保存各 owner 真正拥有的值，不保存活服务、旧许可或可执行闭包，不引入旧格式迁移。

### 第五阶段 删除旧链并收束展示

删除 path 推断、重复属性/伤害计算、同效果双倒计时、并行的玩家与怪物执行分支。角色创建页、ASI/子职页、战术栏与自身效果窗口继续消费统一投影；不把模型重构变成一次无关 UI 重做。

模型与接口可以一次性破坏性更新，迁移步骤只是代码实施顺序，不是保留旧 API 或兼容旧存档的要求。

## 七 验证范围

复用两仓库既有验证设施。CombatNexus 行为使用 AutoJS，不新增 JUnit/GameTest 接线；dndturn 沿自身测试政策复用现有不变量检查。两边各自通过不等于联合行为通过。

| 场景 | 要证明的结果 |
| --- | --- |
| 玩家与怪物相同伤害/豁免流程 | 共用裁决与应用，配置差异不形成第二套后端 |
| 换装、同值替换、卸装重装 | 来源失效正确，不叠加重复收益，不恢复旧许可 |
| Ghast 未命中、主目标死亡、范围部分拒绝 | 各段是否执行符合明确规则，结果逐项保留 |
| 护盾全吸收、原版吸收、取消、写入后异常 | 资源损失与生命损失分开，PARTIAL/UNKNOWN 不丢失 |
| 普通能力遇到 NUMERICAL 接管 | 经真实数值委托执行，或明确拒绝；不能静默失效 |
| 同请求重试、重复回合通知 | 不重掷、不重扣费用、不重复推进和写入 |
| 真实等待与预算中断 | 等待不推进业务时间，续行不跳过或重复事件 |
| 内容重载、来源撤销、同 UUID 新实例 | 内容绑定与实例权限按已声明政策处理 |
| 合并、退出、保存后新进程恢复 | 所有权转移完整，余额、剩余时间和已消费次数保留 |
| 玩家 UI 与 AI | 读取同一服务器事实，不各自重算或提前消费 |

联合验证前统一活动版本；发布前分别执行各仓库要求的构建及相应运行验证。本文不沿用上一轮断言数作为当前通过证明。

## 八 源码入口

CombatNexus 路径相对本仓库，dndturn 路径相对其仓库。Java 根包分别为 `io/github/fatalitaya/combatnexus` 与 `cc/sighs/dndturn`。

| 仓库 | 入口 |
| --- | --- |
| CombatNexus target | `combat/api/CombatWorld.java`、`combat/platform/api/CombatRegisterEvent.java` |
| CombatNexus common | `combat/ability/api/`、`combat/driver/api/`、`combat/time/api/` |
| CombatNexus target | `combat/driver/api/DriverSession.java`、`combat/driver/internal/WorldDrivers.java` |
| CombatNexus target | `combat/ability/internal/DefaultAbilityService.java`、`combat/time/internal/WorldBusinessTime.java` |
| CombatNexus common | `combat/api/item/EquipmentBindingFact.java`、`combat/api/source/SourcePlan.java` |
| CombatNexus 验证示例 | `scripts/autojs/effect-extension/java/probe/AbilityExtension.java`、`DriverExtension.java` |
| dndturn common | `domain/character/`、`domain/mob/`、`domain/ability/`、`domain/actor/` |
| dndturn target | `platform/server/character/PlayerWeaponRules.java`、`PlayerArmorRules.java`、`SaveService.java` |
| dndturn target | `platform/server/builtin/ghast/GhastAbilities.java`、`platform/server/damage/DamageResolution.java` |
| dndturn target | `platform/server/action/CombatActionExecution.java`、`platform/server/effect/vanilla/VanillaEffectRoundController.java` |
