# 角色数据与属性来源合同

本页定义角色标准化的数据职责，不声明功能已经完成。实现及验证只在[当前状态](00_CURRENT_STATE.md)维护，任务只在[路线](02_ROADMAP.md)维护。数值与开放玩法以[有效合同](01_RULES_AND_CONTRACTS.md)为准。

## 模型与所有权

统一读取视图，不统一写入所有权。复用现有 ActorSnapshot、ActorPersistentState、ActorRuntimeState、CombatantSnapshot 和 Encounter 成员状态，不另建平行角色仓库或 ProcessManager。

```text
ActorDefinition
  Identity / SemanticVersion
  Classification / Tags / DefaultFacts
  IntrinsicGrants / ResourceDefinitions
  DefaultBehaviorProfileRef
ActorInstance
  ActorId / InstanceId / DefinitionBinding
  PersistentState: LearnedGrants / Prepared / ActorOwnedResources
  RuntimeState: TacticalEffects / ProcessRefs
  NativeBindingRef: 纯值引用，平台管理实际绑定
NativeActorObservation
  CaptureEvidence / Body / Transform / Vitals
  Attributes / Equipment / Selection / NativeEffects
ActorSnapshot
  Identity / Definition / Revisions
  ResolvedFacts / AbilityBindings / EffectViews / ResourceViews
  按需属性解释
Combatant（Encounter 内部成员）
  ActorRef / MembershipIdentity / Initiative / Eligibility
  TurnResources / EncounterFlags
Encounter
  CapturedRules / Members / HostilityRelations / TurnOrder
  EnvironmentParticipant / Field / Operations / Results / ProcessRefs
```

| 数据 | 唯一写入权威 | 读取方式 |
| --- | --- | --- |
| 位置、朝向、姿态、实际尺寸、生命、最大生命、吸收量 | Minecraft | 当前实例观测，分别保留单位与含义 |
| 装备、库存、耐久、原生属性与药效 | Minecraft/第三方原生实现 | 有界只读采样，不存第二套可裁决状态 |
| 学习/准备能力、Actor 自有资源 | Actor 长期状态 owner | 不可变快照 |
| 战术条件效果 | Actor 效果 owner | 效果实例快照 |
| 动作、反应、移动余额、资格与敌对 | Encounter | 成员/关系快照 |
| 引导、投射物及行动阶段 | 既有运行 owner | 过程引用和只读检查点 |
| 当前控制权 | 平台控制绑定及许可链 | 描述不是许可 |

ActorId 与当前 InstanceId 分离；服务器世代属于执行证据。UUID 相同不允许复用旧实例许可。Actor 离场后仍可存在；环境参与者不是 Actor。合并迁移资格、余额、首击欠账及过程归属，不重建满资源角色。

定义只提供其拥有的默认事实，不能覆盖原生字段。原生实体类型与规则分类、原生尺寸与规则体型分别表达。生命模板不代替当前最大生命。当前 AC 由护甲/持盾映射，不额外叠加静态 BaseAC。Action/Spell/Trait 复用能力定义、授予及失效链。六维、熟练、专注、完整抗性等仅为潜在扩展，未确认玩法不填默认值或自动开放。

## 属性层次与解释

```text
原生当前 base + 实际活跃 modifiers
  → 原生 effective 观测
  → 已确认规则映射
  → Actor 效果/持续能力贡献
  → 最终战术属性
```

保留 minecraft:armor/base、minecraft:armor/effective 与 dndturn:armor_class 等独立键。AC/减伤沿当前合同和 ActorCompilation 计算。装备 modifier 已在 effective 中计入，解释时不得再次加算。角色攻击属性不等于某次行动伤害输入；空手、选定武器、远程和特殊能力继续使用明确来源。

属性解释包含属性键、单位、base/effective、捕获证据、计算阶段、操作、操作数、贡献来源与完整性。原生 modifier 是否活跃，与是否能证明来自某件装备，是独立问题。装备关联核对属性、modifier 身份、操作、数值和当前槽位；结构匹配只表示当前装备声明匹配，不能证明任意第三方写入的历史因果。来源不唯一或无法证明时保留未知，不能按 ID 名称猜来源。

来源可表达定义、装备/选择证据、原生药效、原生 modifier、Actor 效果实例和能力 grant。原生解释器与事实提供者分工，解释器无权替换有效值。乘法、覆盖、限制与条件要保留步骤，不全部伪装成相加数值。数值已知但来源未知、解释被预算截断、数值本身未知分别表达。重建解释与有效值不一致时保留有效值并标记不完整，不虚构差额来源。

示例：base=2，胸甲 modifier=+6，其他 modifier=+2，effective=10；持盾时当前基础战术 AC=12。胸甲 +6 只解释 10 的来源，不再次加到 12。卸装预测属于独立反事实查询，依赖不足不预测，更不能实际换装后读取。

## 查询与公开

声明依赖 → 核验实例 → 同步捕获必要原生事实与 Actor 状态 → 规则派生 → 组合成员视图 → 按观察权限输出。执行复验、能力发现和 UI 使用同一规则派生链；来源解释只在调查等显式读取中按需捕获，不使每次发现遍历库存。

捕获 ID 只证明同次采样；提交、接近后和必要执行边界仍复验。原生事件可帮助失效缓存，但不能作为唯一正确性依据。装备证据不生成或延长 permit。结果不可回写；刷新建立新快照，迟到响应不能覆盖新选择。来源明细也受公开策略限制，不能通过解释泄露隐藏装备或效果。未授权公开的属性与来源默认仅允许本人查看。

事实、modifier、装备声明扫描、输出条目和文本均设上限；截断解释不截断或改变原生有效值。来源匹配仅使用实际装备槽，不为解释扫描整个库存。用户显式请求完整角色资料时可以有界读取本人原生背包摘要（至多 64 槽），不把它加入普通发现/预览链，不序列化全部物品组件，不强加载。槽位观测证据只指向该次 capture，行动仍使用原选择证据复验。

## 扩展、保存与实施顺序

定义选择、原生事实读取、原生属性解释、贡献来源关联、规则派生、能力/效果授予与客户端显示分别承担职责；优先扩展既有注册体系。通用入口保持开放，缺少专用解释器不禁止参战；未知来源不授予额外能力。

Minecraft 保存原生状态；Actor、Encounter 和过程 owner 保存各自当前格式状态。属性解释通常不作为恢复权威；历史需要时保留当次必要纯值，不用历史快照写回原生数据。恢复等待自然就绪后重采样，不重放 UNKNOWN，不恢复旧许可，不新增格式版本或迁移分支。

角色实例采用既有 ActorSnapshot 与 ActorPersistentState 组合读取，完整资料入口为 ActorViews.CharacterSheet，不新建可写 ActorInstance owner。NativeActorObservation 管原生观测形状，StatResolution 管映射后的战术解释；CombatantSnapshot/Encounter 继续按既有权威组合。ActorDefinition.Classification 只表达明确声明的规则分类；ResourceDescriptor 只引用现有单位/容量事实/刷新规则，不因有声明就创造余额或刷新行为。行为定义仍由独立 AiDefinitions 选择并只读投影。调查分类/分页属于当前正式角色展示，非临时调试协议；完整资料只向本人公开，每次翻页重新采样，不承诺多个请求组成同一时刻快照。每个切片复用现有测试保护不重复计入、未知不伪造、查询只读、实例失效与唯一资源 owner；原生装备关联在既有平台夹具验证。面板编译与服务器夹具不代替真实键鼠/双客户端证据。


## 动态来源与长期能力管理

所有权、时间语义和来源是独立维度。ActorDefinition 是静态规则配方，分类/体型/标签不是当前原生身体状态；无专用定义时使用空规则通用回退。ActorRuntimeState 仅持有其拥有的效果实例，过程阶段继续属于既有运行 owner。

平台读取边界核验 Actor/实例、Actor 统一修订、采样 tick、主手地址和双手写入代次；tick 不是原生修订号。请求范围由 ReadContract/FactSlice 表达，capture 与规则身份沿 ActorSnapshot.Revision 保留。调查聚合发布前核验所读 Encounter 与成员关系。原生事实仍须在执行边界重新采样；不承诺能侦测任意第三方在同一同步读取内绕过入口的修改。查询缓存仅存活于一次同步读取，目标键包含当前 Actor 修订。

装备来源包含槽位、数量/组件指纹、Actor/实例和已审计写入代次。玩家主手使用 Inventory 写入证据，Mob/副手使用 EntityEquipment 证据；同值替换也使旧来源失效。可审计原生消耗允许当前操作更新继续执行证据，不因此生成新许可、退款或重放。指纹是值证据，不是物品 UUID；第三方直接修改暴露的底层容器而恢复同值不在严格身份保证内。

AbilityDefinition 显式声明 Learning：UNAVAILABLE、IMMEDIATE、PREPARED。内置操作、装备和物种能力显式不可学习。只有扩展声明的能力可由管理命令授予；有原生执行器的可学习能力还须由事实适配器显式支持长期来源，注册冻结时检查。准备要求属于冻结的定义语义，不由命令调用者任选。当前格式恢复校验定义与准备要求，不迁移旧数据。

管理员/控制台可用以下命令操作单个已加载玩家或 Mob，战斗内外均可，不强加载或离线修改：

```text
/dndturn actor ability list <target>
/dndturn actor ability grant <target> <ability> <semanticVersion>
/dndturn actor ability revoke <target> <grantId>
/dndturn actor ability prepare <target> <grantId>
/dndturn actor ability unprepare <target> <grantId>
```

写入通过 ActorStateAuthority 的现有 Learn/Forget/Prepare 与操作回执。重复管理授予同能力/语义版本返回既有 grant；撤销后重新授予使用新身份。准备变更仅调整指定授予，重准备不复活旧证据。撤销或取消准备后新查询不再绑定，旧计划在既有来源边界被拒；已发生效果与费用按原过程合同保留。没有新增内置可学习技能、职业、控制权切换或 PvP。
