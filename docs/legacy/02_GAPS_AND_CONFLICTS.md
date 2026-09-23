# DNDTurn 未完成既定目标

<a id="combat-structural-remainder"></a>
## Revised combat structural checklist 的剩余结构目标

本节细化下方 DM／MT／G25／恢复／扩展兼容条目，不另建阶段进度表。已接通的值模型、原生入口和构建证据移至[版本记录](../version-differences/neoforge-26.1-combat-structure.md)。

- **VM-01／03、G25：** 将移动离开触及范围接入同一付费反应后端，完成稳定先攻顺序、玩家武器选择／确认及共享 100 实际 tick 窗口；扩展伤害以外的类型化前置载荷和所需干预，不以现有 PASS/CANCEL 宣称完整替换／重定向。补真实保存／第二进程后的付费反应对账，不能用纯值恢复去重代替。
- **VM-02／08／09／11／12、MT-10：** 实现手动与触发共享的 staged driver、跨回合时钟、语义续跑／原生重绑定及 encounter/environment owner 的实际推进；完成 Guardian 引导与 Wither 多通道过程。当前仅 AUTHORIZED_EXECUTION_STEP＋FAIL_UNKNOWN，不能把持久化值当成可恢复执行器。补齐过程合并迁移、终态控制故障的追加对账及有因果引用约束的历史归档。
- **VM-04／06、MT-06：** 安装差异明显的 multipart 与非地面 provider 并验收 root 资源、几何、实例替换和释放；现有默认地面与 LivingEntity 适配不证明这些能力。H04／H05 正式玩法继续暂缓。
- **VM-05／10：** 完成伤害／声音／危险感知、持久语义记忆、过程启动提案及实际支持型策略；验证过程恢复前后随机序列，不仅验证随机值可编码。
- **VM-07、DM-01A：** 为传送、生成、状态、部件及世界变化逐适配器注册必要观察，覆盖副作用后的未知／部分效果；现有 BODY schema 不代替各类原生事实。
- **VM-13、DM-01C／MT-07／MT-12：** 独立兼容模块与最终 archetype 验收仍缺失。本次用户明确不新增测试，因此未新增兼容测试模块，不能将已有回归通过记为该项完成。

<a id="package-refactor-followup"></a>
## 包重构后的剩余职责拆分

- EncounterRuntime 仍集中持有移动 Lease／投射物因果、跨 owner 检查点采样及世界恢复对账；继续提取时须连同创建、终结、合并与恢复 owner 迁移，不能只新增转发 facade。已完成的存档写入与行动证据 owner 拆分见[包与所有权记录](version-differences/neoforge-26.1-package-ownership.md#persistence-owner)。
- 行动内部的 PlayerBehavior／VanillaBehaviors／物品与远程适配仍共用有界执行上下文。进一步移入 builtin 前，需要将其 package-private 协作改为有限执行端口，不能公开可变 Execution 或将内部方法批量 public 化。
- 全量同进程 GameTest 的夹具隔离与真实客户端验收沿用下方现有缺口；本次迁移证据见[包与所有权记录](version-differences/neoforge-26.1-package-ownership.md)，独立场景通过不关闭整套验收。

## 默认能力开放后的待验收范围（2026-09-30）

- 真实客户端选择／右键菜单到专服、双客户端与第三方物品／方块 override 的端到端验收仍待完成；服务端内嵌连接测试不替代真实输入。
- 通用入口的同步 Level 写入边界不涵盖直接区块写入、BlockEntity 内部字段和后续延迟效果的完整来源／恢复观察；保留现有预算和 UNKNOWN，不以恢复精确类型白名单掩盖缺口。
- 全量 GameTest 同进程运行出现跨夹具干扰：成员未发现、已关闭会话仍被测试序列读取并导致崩溃；需独立修复夹具隔离。单项执行证据见[版本记录](version-differences/neoforge-26.1-default-capabilities.md)，不能据此宣称全量通过。


本文件是唯一活动目标清单，整合原 target architecture checklist、活动缺口、数据模型／AI／能力／环境合同及固定版本记录。只保留未完成实现、未完成验收和明确暂缓的既定目标；同一工作只登记一次。已完成的基础、历史通过记录、被明确替代的方案及用户取消的玩法不列为待办。

初次整理依据截至 2026-09-30 的仓库文档及其具名证据；后续实现与验证按对应版本记录增量更新。未勾选不一定表示没有代码；各项明确区分待实现、待扩展和待验收。后续完成项从此清单移除，实现事实与关闭证据分别维护于[实现记录](legacy/01_IMPLEMENTED_DECISIONS.md)和[固定版本记录](version-differences/README.md)。

## 1. 授权与世界效果边界

来源：[AI／Authority 证据](version-differences/neoforge-26.1-ai-authority.md)、[Effect 当前审计](version-differences/neoforge-26.1-tactical-effects.md#current-snapshot-recheck)、[AI 架构决策](DNDTurn_AI_MODEL_ARCHITECTURE_DECISIONS.md)、[架构合同](legacy/04_ARCHITECTURE_CONTRACTS.md)。

- [ ] **Gate 真实输入与联机验收。** 从鼠标／KeyMapping 复核近战发现、自动接近、失败提示保留及双客户端表现；覆盖真实输入下区域、lease、阶段、恢复和全局 freeze 组合。已完成的策略／准备重构、生产入口审计和服务器回归证据见[门禁记录](version-differences/neoforge-26.1-gate.md)，嵌入连接测试不能替代此项。

<a id="dnd-data-model"></a>
## 2. Actor、能力与规则事实

来源：[数据模型草案](DNDTurn_DND_DATA_MODEL_ARCHITECTURE_DRAFT.md)、[数据模型证据](version-differences/neoforge-26.1-dnd-data-model.md)、[能力合同](legacy/05_CAPABILITY_FRAMEWORK_IMPLEMENTATION.md)、[Mob 翻译合同](legacy/06_MOB_NATIVE_TRANSLATION_IMPLEMENTATION.md)。

- [ ] **DM-01A：声明式原生依赖与观察。** 继续拆分专有物品支持、几何到位和碰撞中的 native 复合判断，逐适配器声明必要世界依赖、实际作用范围及类型化副作用观察。
- [ ] **DM-01B：长期状态进入正式玩法。** 为 `ActorPersistentState` 的学习／遗忘／准备提供真实 gameplay writer，至少完成一个改变 `AbilityBinding` 的长期状态闭环；不把 API 存在当作玩法接通。
- [ ] **DM-01B／DM-02：运行状态与持续派生覆盖。** 扩展 Creeper Fuse 之外的 production-owned state 使用者，补齐 continuous／condition grants 的正式注册、来源失效和完整生命周期。
- [ ] **DM-01B：细粒度 native dirty 缓存。** 明确事实写入者、修订和失效依赖，复用必要采样；native capture UUID 不作为世界版本。以采样计数和依赖变化场景验收。
- [ ] **DM-01G：Encounter-owned grants。** 实现真正的持久 owner、加入／退出与合并迁移，补足 ENCOUNTER 来源证据之外的状态所有权。
- [ ] **DM-01G：复合资源提交。** 完成 Actor 与 Encounter 资源的共同费用校验和提交边界，验证非法输入无部分提交、取消与已发生效果的费用保留。
- [ ] **MT-02：装备端口覆盖扩展。** 补玩家库存全部写入代次、第三方装备容器及更多消耗型 Mob 装备能力；满库存、实例替换及执行中换装限制的完整组合继续验收。
- [ ] **MT-03：统一发现和目标评估。** 对齐玩家／AI、按目标查询／按能力查询的能力集合与目标合法性；补多状态端到端覆盖，沿用现有分批游标。
- [ ] **MT-04：有限执行端口扩展。** 补近战／普通装备弓箭之外的效果端口、跨回合技能托管状态及取消／释放生命周期，保持回调权限有界。
- [ ] **MT-05：完整翻译证据链。** 将翻译政策 ID／语义版本贯穿调用、检查点、类型化观察和演出事实，保留耐久、吸收、多层效果及清理结果的独立证据。
- [ ] **MT-06：几何与感知事实。** 补完整体型、姿态、攻击范围、执行位置、感知与翻译版本合同；验证动态属性变化、混合主动／被动位移及到位复验。

<a id="tactical-effect-framework"></a>
## 3. Tactical Effect 与原生药效

来源：[Effect 验收记录的当前结论](DNDTurn_TACTICAL_EFFECT_ACCEPTANCE_20260930.md)、[固定版本证据](version-differences/neoforge-26.1-tactical-effects.md)、[角色状态合同](legacy/04_ARCHITECTURE_CONTRACTS.md)。

- [ ] **DM-02：实例修订正式闭环验收。** 验证真实玩法中实例 revision 与 grantRevision 的失效边界、期限刷新、来源／rank／层数变化及恢复；保留已通过的纯 Actor 测试，不重新列为核心未实现。
- [ ] **DM-02：生产事件使用者。** 为已有派发的 HIT、DAMAGED、TURN_START、TURN_END 分别补正式 Tactical Effect 使用者和专项平台验收；补 ABILITY_USED、ABILITY_RESOLVED 的生产派发与去重证据。
- [ ] **DM-02：触发能力扩展。** 补任意已审计事件上下文中的世界触发、非 Creeper 触发能力、付费反应及连续来源生命周期；当前仅在已接通行动收尾边界排空的即时触发不能代表全覆盖。
- [ ] **DM-02：贡献的生产应用。** 补统计量之外的资源／规则事实贡献，并让 contribution 在正式生产链中影响 Snapshot 和后续裁决。测试夹具的属性投影证据不能替代生产使用者。
- [ ] **DM-02：buff／debuff 参考闭环。** 以原版 MobEffect 投影和原生执行完成各一个专项玩法验收；保留 Minecraft 写入所有权，不重复施加原生属性修正。通用层数消费的生产使用按既定能力验证，不新增护盾次数玩法。
- [ ] **DM-02：云完整生命周期。** 验收 Creeper 云真实施药、自然到期、跨域、合并及来源域结束；现有生成／暂停／来源持久化证据不替代完整生命周期。
- [ ] **DM-02／ET-04：爆炸覆盖扩展。** 逐入口审计特殊 block override、连锁／邻居传播、非生命实体、未支持药效及第三方回调；仅在明确规则和作用范围内接入，直接范围过滤不代表传播已受控。

Effect 波次中间故障、原生结果恢复和长期回执归档统一见第 8 节；公开 effect 投影见第 9 节。已完成的实例策略、层数命令、阈值边沿、自有生命周期反应、确定性触发与 Creeper 正式链不重新列入待办。

## 4. AI 与 Mob 能力扩展

来源：[AI 架构决策](DNDTurn_AI_MODEL_ARCHITECTURE_DECISIONS.md)、[AI 版本记录](version-differences/neoforge-26.1-ai-authority.md)、[Mob 目标与验收合同](legacy/06_MOB_NATIVE_TRANSLATION_IMPLEMENTATION.md)、[已确认时间规则](legacy/03_PLAYER_RULES.md)。

- [ ] **AI bake：家族覆盖与定义依赖。** 扩展更多已审计家族和冷定义依赖组合的失效验收；结构候选不自动获得能力授权，未知地面 fallback 保持只移动边界。
- [ ] **AI planner：更多能力决策。** 补普通弓箭之外的远程、范围攻击、治疗、增益、减益、控制、逃离、保护盟友及资源感知规划；通过能力绑定与共享规则／计划后端执行，不新增 AI 特权。
- [ ] **AI perception：有界感知与记忆。** 补最后已知位置、感官事件、危险／目标感知、地形机会、置信度／来源和 sensor profile；复杂 AI 记忆的持久化及实例／会话清理一并验收。
- [ ] **MT-10：Guardian 引导。** 按既定规则完成启动扣动作、准备／引导跨回合、禁移动／另行动、自动维持及结束回合；目标／条件失效中断、不退款、不换目标。一次战术命中，两个原生伤害输入分别留证且不重复结算韧性／暴击。
- [ ] **MT-10：ElderGuardian 周期。** 每 40 个自身回合末按原版审计条件检查，疲劳持续受影响者 200 回合；不耗动作、不重复叠加，补退出／恢复边界。
- [ ] **MT-08：近战副作用支持边界。** 逐项审计点燃、附魔、反伤和未知 override，补相应来源、授权与原生观察；现有标准近战家族不视为所有特殊技能已支持。

## 5. 移动、投射物与物品

来源：[玩家物品版本记录](version-differences/neoforge-26.1-player-items.md)、[移动预览](version-differences/neoforge-26.1-movement-preview.md)、[玩家规则](legacy/03_PLAYER_RULES.md)、[Mob 合同](legacy/06_MOB_NATIVE_TRANSLATION_IMPLEMENTATION.md)。

- [ ] **移动扩展：** 补攀爬和已审计第三方 navigation provider，完善体型、扫掠／支撑、混合位移和不同速度／摩擦场景。飞行、专属游泳、载具与特殊滑翔范围按第 11 节暂缓，不从清单自动开放。
- [ ] **G02／G03／G13／G22：箭的精确边界。** 补首次入域、跨域、来源生命周期及碰撞前因果合并的精确接管；保留原生行进、在线／死亡／离线射手区别和 UNKNOWN 隔离。
- [ ] **投射物语义扩展：** 补完整箭附魔、火焰、穿透、药效映射、雪球／鸡蛋特殊结果、第三方投射物 provider 与不支持弹药诊断；发射时捕获来源，不能命中时读取射手新装备。

<a id="player-items"></a>

- [ ] **PI-01：特殊投射效果。** 为三叉戟、末影珍珠、风弹、滞留药水和烟花弹药提供专用适配与验收；普通投掷许可不授权传送、爆炸、持久药水云或特殊箭。
- [ ] **PI-02：其余已定物品／目标组合。** 补水桶捕捉、牵绳和其余驯服／修补／特殊实体交互。树木／结构骨粉、刷怪笼修改、Boss 蛋、末地龙复活、特殊放置 override 维持当前拒绝边界，逐项审计后再接入；不因“全部原版物品”概括而放开未定 Boss 规则。
- [ ] **PI-03：完整世界效果差量。** 补名称、毛色、繁殖、驯服、装备／药效、方块实体数据和投射接触观察；与第 8 节真实保存／故障对账共同验收。
- [ ] **PI-04：跨域与特殊接触。** 补特殊方块回调、跨域、离线施放者和更多作用范围；喷溅水及瞬时伤害／治疗仍需专门映射，不从非即时药效适配推断支持。
- [ ] **PI-05：轨迹与实际执行对照。** 补完整碰撞体、水体、附魔及发射散布场景，验证预览与真实输入、服务端发射、延迟取消和双客户端显示；中心轨迹不作为命中保证。
- [ ] **G24／G28：库存与容器验收。** 补满库存换装、持续使用中断、拖拽／双击收集、特殊或模组菜单的授权与物品守恒；打开菜单不授权任意存取、合成、交易或丢弃。
- [ ] **PI-06：物品平台边界验收。** 补陷阱箱独立场景、放置／使用挥手、耗尽显示和双客户端事件去重；物品、生成 Mob 与模组命中钩子按声明范围验证。历史失败统一见第 10 节。

## 6. 环境过程

来源：[环境合同 ET-01～ET-08](legacy/ENVIRONMENT_TICK.md#remaining)、[环境／Mob 版本记录](version-differences/neoforge-26.1-environment-mob.md)、[玩家规则](legacy/03_PLAYER_RULES.md)。

- [ ] **ET-01：全部时间输入审计。** 补已托管过程的全部时钟／外部输入和火把历史窗口，验证入域、离域、合并、重载后的剩余延迟／相位；不补跑真实等待、不重置冷却。已实现的日光有效步采样不重复开工。
- [ ] **ET-02：参与者周期扩展。** 补特殊药效、非默认 roundTicks 平台场景及真实客户端对照；按所属参与者回合末结算，验证不同真实等待时长不改变同回合数结果。
- [ ] **ET-03：漏斗与接触边界。** 补多格容器、完整冷却、跨域、接触变化、方块替换和卸载组合，保持当前接触判定和容器生命周期。
- [ ] **ET-04：新生 TNT 与连锁。** 补新生 TNT、短引信连锁、跨域和效果来源边界，与爆炸／投射物共用因果与授权合同。
- [ ] **ET-05：held 队列组合。** 补 delay0、同位重复安排、新生任务与旧步、取消／重新放置、队列释放与 movingBE 完成顺序；保留延迟、优先级、相对顺序、查询／去重和保存的唯一所有权。
- [ ] **ET-08：调度公平与预算。** 扩展多域公平、未加载、全局 freeze／step、失败不扣预算的验收；区块／实体原生早退不能记作有效模拟步。
- [ ] **环境覆盖扩展：** 审计邻居传播、跨边界写入政策、天气、生成相关过程、剩余原版特殊过程和第三方过程扩展点。各类单独声明支持，未知过程不默认获得推进窗口。

ET-06 活动保存见第 8 节；ET-07 客户端有效步骤、TNT 与视觉通道见第 9 节。

## 7. 已定后置玩法

来源：[玩家规则](legacy/03_PLAYER_RULES.md)、[旧 G 编号映射](legacy/01_IMPLEMENTED_DECISIONS.md#legacy-g)。

- [ ] **G23：HELP。** 完成既定 HELP 规则的正式生产闭环、费用／来源／目标和效果消费验收。
- [ ] **G25：完整借机攻击。** 完成触发、反应资源、目标与时序、取消／失效及结果归因；现有 Reaction 资源或伤害子操作不代表借机攻击已经实现。

## 8. 持久化、生命周期、合并与归档

来源：[恢复与合并合同](legacy/04_ARCHITECTURE_CONTRACTS.md)、[环境合同](legacy/ENVIRONMENT_TICK.md)、[Effect 当前恢复证据](version-differences/neoforge-26.1-tactical-effects.md#current-snapshot-recheck)、[七日规则](legacy/03_PLAYER_RULES.md)。

- [ ] **DM-01B／DM-01F／ET-06／MT-11：当前结构的活动续战。** 完成真实专服保存／第二进程加载，覆盖 Actor、Encounter、世界／实体、环境 current、剩余预算、具名 step、held 队列、过程相位、参与者期限和物品效果；验证实例替换、缺失定义、卸载、死亡、退出和正常关服。
- [ ] **DM-02：真实故障窗口与跨文件对账。** 覆盖副作用前后、观察／提交前后、波次中间及不同保存次序；STARTED 夹具注入转 UNKNOWN 不替代不可逆副作用期间的真实崩溃。只在已审计证据允许时重建原生结果，无法确认保持 UNKNOWN，不自动重放／退款。
- [ ] **DM-01G：可恢复事件派发。** 补现有纯反应及 Creeper 之外的事件派发恢复、连续来源与原生执行之间的故障窗口；持久事件保留与实际世界效果分别对账。
- [ ] **ET-06／MT-11：合并跨 owner 迁移。** 验证规则、区域、调度、别名、grant、许可、实时退出豁免及客户端订阅的一致迁移和失败后可重建性；不能只 canonical 化 ID 或重置全员资源。
- [ ] **G40：实时退出授权生命周期。** 验证合并、重入、断线和历史归档前后的授权生效／撤销；历史回执不恢复过期实时豁免。
- [ ] **G17／DM-01G：七游戏日引用感知归档。** 用跨重启累计实际服务器 tick 维护结果／Actor 回执，暂停仍计时、快进不缩短；有效重试、别名、短暂实体及因果引用延长保留。容量拒绝不能替代长期归档；无效果模拟事件的精确区间压缩不覆盖有规则回执、世界调用及其他事件的长期增长。
- [ ] **G18：非投射物执行截止。** 七游戏日无终态的跨 tick 操作按规则转 UNKNOWN 并释放控制；与结果保留期分开，不新增投射物自然寿命上限。

## 9. 客户端、网络、表现与诊断验收

来源：[镜头](version-differences/neoforge-26.1-tactical-camera.md)、[瞄准](version-differences/neoforge-26.1-cursor-facing.md)、[GUI](version-differences/neoforge-26.1-log-gui.md)、[头像](version-differences/neoforge-26.1-participant-portraits.md)、[移动预览](version-differences/neoforge-26.1-movement-preview.md)、[调查](version-differences/neoforge-26.1-dnd-data-model.md#free-inspection)、[诊断](version-differences/neoforge-26.1-action-debug.md)。

<a id="cam-01"></a>

- [ ] **CAM-01：镜头地形问题定位／验收。** 复现坡地、高差、平移后焦点或镜头入块、碰撞解除后距离恢复及未加载区域；已有射线裁剪不作为问题解决证据，仅在 CameraRig 内修正，不移动玩家身体。

<a id="aim-01"></a>
<a id="aim-01鼠标朝向回跳与黑色预选框闪烁"></a>

- [ ] **AIM-01：朝向回跳与预选框实机验收。** 覆盖快速连续瞄准、静止超过 20 tick、相机平移／旋转、位置纠正期间输入、地面／侧面／实体、高低差、±180°、头身跟转、移动瞄准及远端重跟踪；验证 HUD／Screen／失焦／MISS 和退出交接。

<a id="gui-01"></a>
<a id="gui-01按钮与图标位置不一致"></a>

- [ ] **GUI-01：绘制与命中对齐。** 实机验证不同分辨率／GUI 缩放、结束回合按钮、图标、标签、能力刷新、禁用／悬停／点击边界及菜单遮挡；源码布局修复不直接关闭用户反馈。
- [ ] **GUI-01：更新时序与选择。** 验证移动力圆环无虚影、右键候选首帧、首次进入／资源重载／窗口缩放、同意名单与倒计时；覆盖数字键／槽位／攻击按钮、空手／空槽／耗尽、不可用物品、跨回合保留、执行中结束回合、迟到响应、取消、重新入场及菜单滚动。重点复验武器确认后的第二行动作、查询失败时菜单保留及原因、重启后重新选择；[源码修复与自动验证](version-differences/neoforge-26.1-log-gui.md)不替代实机反馈闭环。
- [ ] **GUI-01：头像与客户端 Mixin。** 实机验证头像注入、上半部构图／头顶余量／装备裁切、玩家／幼体／体型差异、横向裁剪、卸载／重跟踪、F1／聊天／同意弹窗和退出清理。
- [ ] **移动预览实机验收。** 从真实鼠标选点验证空闲方块左键默认移动、无碰撞方块／流体穿透、实体／MISS无默认行为、显式能力与HUD输入优先级，以及虚线、灰红预算分界、灰色末端提交和联机停止位置，覆盖不同速度／摩擦／水中／跳跃／小额剩余预算；名义估算不承诺复杂地形精确耗尽预算。
- [ ] **DM-01G：调查基础设施。** 补永久 observer knowledge、隐藏事实解锁／撤销、完整 visibility policy、通用条件与能力披露；现有免费公开事实调查保留。
- [ ] **DM-01G：免费调查实机验收。** 验证真实鼠标、非本人回合、双客户端信息隔离、延迟关闭／切换目标、缩放及查询前后资源／修订不变。
- [ ] **DM-02：标准 effect projection 使用。** 补 UI／inspection 对标准效果投影的消费。公开 Effect UI 属既定后续目标，不把上一批 Creeper／原生药效交付范围扩展为立即新增玩法或面板。
- [ ] **ET-07／MT-12：表现生命周期。** 验证权威有效步骤、TNT 烟雾、其他视觉通道、独立非人形显示适配和双客户端；覆盖暂停／恢复、重跟踪、资源重载、世界切换与卸载，不推进真实实体／物品 tick。
- [ ] **G19／G20／G27／G29／G32／G38：真实网络链。** 从键鼠／菜单到专服验证动态同意、EXIT、重复请求、迟到／缺页／结果补收、取消待确认、重连／换世界及错误重试策略；验证当前固定注册标识下的握手兼容，不恢复递增协议号。
- [ ] **DBG-01：行为诊断实机验收。** 验证客户端启动注入、鼠标请求、Mob 决策、命中／未命中／暴击、拒绝／取消和双客户端关联；有／无 `-debug` 的相同场景费用与结果一致。战斗日志空面板保持现有规则，限流缺日志不解释为缺行为。

## 10. 扩展兼容、规模与未关闭回归

来源：[当前 Effect 复验及历史失败](version-differences/neoforge-26.1-tactical-effects.md)、[AI 记录](version-differences/neoforge-26.1-ai-authority.md)、[数据模型记录](version-differences/neoforge-26.1-dnd-data-model.md)、[环境／Mob 记录](version-differences/neoforge-26.1-environment-mob.md)、[物品记录](version-differences/neoforge-26.1-player-items.md)。

- [ ] **DM-01C／MT-07／MT-12：独立兼容模块闭环。** 扩展已有独立包近战／非 Skeleton 装备射击证据，补独立表现、取消、卸载／恢复、注册冲突、缺失／版本变化的完整组合；不修改核心调度或通用 Mixin 完成适配。
- [ ] **MT-07：累计工作预算与规模。** 验证单目标超预算、高频依赖失效、动态世界、大量实体／能力候选、其他注册轴及长期 Actor／事件／回执规模；记录实际计数或 profiling。5000 项纯值夹具不扩大生产 capture 4096 上限。
- [ ] **验收覆盖核对。** 对照现有 common、target 独立 checks 和 GameTest 证据，补尚无对应证据的快照不变性、唯一 writer、能力来源、GateDecision 不可转移、AI 定义与运行态分离、感知／关系／偏好／合法性分离等反例。已具名通过的 Effect 核心、阈值、波次及 Creeper 测试不因旧 checklist 未勾选而重复登记为未实现。
- [ ] **AI-GATE-01／PI-06：导航间歇风险。** 继续定位 `prototype_zombie_navigation` 未实际移动／扣费、CANDIDATE／navDone／leased 状态组合，以及导航／火焰发现的历史失败；保留加载前置条件和真实移动／费用断言。
- [ ] **DM-01D／DM-01H：发现与当前实例边界。** 覆盖 `discovery_cursor` 的工作量／延期异常、加载实例丢失，以及 tick 后实体失效观察窗口的专项反例；已有当前实例校验不替代完整窗口验收。
- [ ] **DM-01E：环境／合并／箭间歇风险。** 保留 `environment_boundary_time` 漏斗转移／日光输入、`prototype_environment_loop`、`merge_discovery_expansion`、`arrow_cross_session_miss` 的加载、因果合并等待及生命变化历史。核对已有加载、顶棚与有界调度等待修正后剩余复现范围；单批通过不关闭全部风险。
- [ ] **环境等待／测试清理边界。** 复核 `player_only_start` 两轮环境等待与 tick99 清理的交错；2026-09-30 Mob 验证额外种子26104整套在等待器读取已结束遭遇处出现 `unknown encounter`。区分场地加载、环境推进和测试生命周期，不删断言或把崩溃批次计为通过；证据见[Mob 标准化记录](version-differences/neoforge-26.1-mob-standardization.md)。
- [ ] **PI-06／GUI-01：交互与扩展回归。** 复核 `action_preview`、`interaction_repairs` 普通物品攻击、`player_item_projectiles` 实体查找超时及 `external_melee` 成员前提失败；不把夹具修改、删除测试或一次重跑通过当作问题关闭。

关闭对应子项须提供实际场景、命令、环境和结果。纯值、固定版本平台、真实键鼠／双客户端、活动重启／故障、扩展／规模证据分别记录；不要求仅文档整理重跑构建。后续代码改动按 [AGENTS](../AGENTS.md) 执行受影响 target 的验证，其他三个 target 仍仅保持已定构建边界。

## 11. 明确暂缓及前置规则未定的目标

来源：[玩家规则的保留范围](legacy/03_PLAYER_RULES.md#scope)及其后续明确修订。以下保留方向和前置条件，不进入当前实现范围；未经产品修订不得按通用 checklist 自动开放。

- [ ] **H01／H02：自然伤害与类型体系。** 完整自然伤害、抗性／硬免疫的来源、叠加和映射仍待定义；已定 TNT 例外不回退，也不扩展为全部自然伤害授权。
- [ ] **H03：HIDE／潜行。** 暂缓，待明确触发、发现、期限、费用和解除规则。
- [ ] **H04：Enderman 定点传送及未定特殊能力。** 暂缓，待明确能力规则；Guardian／ElderGuardian 的已定流程见第 4 节，Creeper 已接基础不重开。
- [ ] **H05：飞行、专属游泳、大型部位／Boss。** 暂缓，待对应规则与适配合同；保留普通水中移动。原 checklist 中的载具／乘客、鞘翅／特殊穿行同样不超出已确认的方向性身体／载具控制暂缓范围。

已取消的护盾次数玩法、未获本次玩法授权的流血／破甲示例、旧 schema／protocol 迁移、PvP、恢复第一人称切换和战斗日志功能不列为未完成既定目标。后续如获明确新指令，再按具体范围登记。
