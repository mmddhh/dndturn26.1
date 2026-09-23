# NeoForge 26.1 玩家物品使用接入

2026-09-28文档同步核对当前源码常量及本文具名日志，没有重新执行测试。本文维护固定版本入口与分批证据；有效规则见[03物品修订](../03_PLAYER_RULES.md#player-item-rules)，目标执行合同见[04](../04_ARCHITECTURE_CONTRACTS.md)，活动余项只在[02](../02_GAPS_AND_CONFLICTS.md#player-items)维护。

## 依据与边界

### 2026-09-28物品攻击、箱子与刷怪蛋修复

- `VanillaBehaviors.Melee.supportsItem/unavailable`删除命名空间及ProjectileWeaponItem/Snowball近战门禁，任意非空主手物品可提交普通物品近战；专用远程能力仍按既有默认选择和授权执行。物品属性输入、命中、费用及原生hurtEnemy/postHurtEnemy链保持原状。用户明确拒绝额外添加玩家基础攻击力，因此无攻击属性物品的零伤害不是拒绝执行。允许原生物品钩子不构成对任意同JVM模组副作用的完整审计保证。
- 固定`.84` patched源码核对`BlockItem.useOn(UseOnContext)`→`place(BlockPlaceContext)`→`getPlacementState/canPlace/placeBlock`完整成功/失败分支；成功放置更新组件和setPlacedBy、耗物后返回CLIENT来源SUCCESS。`ChestBlock.getStateForPlacement(BlockPlaceContext)`只读选择SINGLE/LEFT/RIGHT及朝向，`updateShape`更新配对格；`TrappedChestBlock`继承此放置链，差异为方块实体及开箱红石。`TacticalImpact.prepare`保留精确类型、加载/保护/域及原版生存/碰撞条件，新增这两类；双箱配对格单独授权并进入写入观察，不能把已有箱体当作新放置碰撞检测。实际执行仍由ServerPlayerGameMode.useItemOn与原版BlockItem完成，没有手写setBlock替代放置。
- 固定服务端包处理`handleUseItemOn/handleUseItem`在Success.swingSource=SERVER时调用挥手，CLIENT来源通常由原版客户端预测负责。战术意图不执行客户端物品预测，原先漏掉此表现。`VanillaBehaviors.Interaction.start`现在于原版返回Success且来源非NONE时，通过既有`ServerEntityProjections.swing`发送一次绑定子operation/手别/原生SwingAnimation的表现事件。返回PASS/FAIL/CONSUME不补挥手；持续使用仍沿原版use及已有权威进度。不新增Mixin，不补跑客户端实体/物品tick。
- 固定`SpawnEggItem.useOn`→`spawnMob`→`EntityType.spawn`在服务端插入后消耗物品。沿既有`ServerLevel.addEntity`成功返回观察，`ItemUseEffects.close`先记录已发生生成身份，再将spawn_item产生的Mob交给`ServerCombatService.joinGeneratedMob`；检查原遭遇、维度、存活及位置后走唯一`CombatEngine.join`，捕获参与者效果、失效持久化修订并发布名单。加入不意味着提前获得本轮行动，不建第二套成员表。没有成功插入不入场，重试由既有操作账本拦截。
- 固定`Zombie.addBehaviourGoals`的原版目标策略含NearestAttackableTargetGoal<Player>；没有执行可能攻击/移动的Goal来查询目标。既有`MobTurnStrategies.capture`继续只读视线/追踪范围/canAttack和能力合法性；`ZombieAttack.propose`从仅ACTIVE改为同时接受CANDIDATE，消除“候选等待敌意、索敌又等待ACTIVE”的阻塞。实际攻击通过共同PLAN/费用/伤害链，不放开整套原版AI或未定义物种技能。

新增`InteractionRepairChecks`位于独立gameTest源集，覆盖全部已注册非空物品的近战发现、普通方块物品攻击与零属性原伤害合同、单箱/双箱原版放置与配对格证据、重试不重复耗物、刷怪蛋生成身份/立即成员/下一轮资格，以及不预设目标的候选僵尸自主攻击生存玩家。真实客户端的挥手、输入和双客户端仍待验收，不把服务器夹具视为画面证明。

本轮实际验证（Windows/JDK25.0.3，独立26.1 target）：初次新增测试编译因引用不存在的checkpoint.footprint失败，改为核对observed.blocks后，`gradlew.bat build runGameTestServer --console plain --no-daemon`成功、64项required通过（`verification-interaction-repairs.log`）。补充实际普通物品攻击断言后执行`clean build runGameTestServer`：构建/common/UI检查通过，GameTest新增场景通过，但五回合首攻、导航、三会话合并三项失败（`verification-interaction-repairs-final.log`）。五回合夹具原先依赖玩家抢先首攻，现已同步安排候选首攻，保留后续NORMAL与五轮资源断言；未放宽生产行为。最终`build runGameTestServer`为63/64通过，五回合/导航/合并及新场景通过，`external_melee`在tick30的external fixture membership断言失败（`verification-interaction-repairs-regression.log`）。最新整套未全通过，外部夹具入场失败原因仍待定位；此前64项通过不覆盖此后新增断言及夹具修改，也不关闭历史间歇问题。

目标参数为 `targets/neoforge-26.1/gradle.properties` 的 Minecraft 26.1、NeoForge `26.1.2.84`。审计依据是本地 Gradle 解析出的 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`；该产物中的 Minecraft 日志版本为26.1.2。没有通过近邻版本推断注入签名，没有升级依赖。Gradle JVM／目标语言使用 JDK25，common 保持 Java17。

审计沿物品 `use`／`useOn`／`interactLivingEntity`，组件 `Consumable`／`Equippable`，生物 `mobInteract`，持续使用和实体生成／投射命中调用链展开。未找到的旧维护文档不作为已读依据。以下是实际接入范围，不承诺全原版物品、任意组件替换或其他模组 override。

## 使用分类及当前范围

| 类别 | 玩家动作与原版入口 | 支持边界 |
|---|---|---|
| 自身食饮 | `dndturn:consume`，`ServerPlayerGameMode.useItem`，受控 `LivingEntity.updatingUsingItem` | 沿用 Consumable 发现；完整使用期间单次动作，不自动结束回合。特殊消耗效果仍受既有世界效果门控 |
| 自身穿戴 | `dndturn:equip`，`Equippable` 原版换装 | 精确 Item 类、swappable、槽位可用，仍按已有免费整理规则；不是开放束口袋或任意库存操作 |
| 指定方块／位置 | `place`／`tool`／`bucket`／`bottle`／`brush` | `TacticalImpact` 的精确类型和有界作用范围复验。骨粉仅已审计作物；刷子自动完成考古；水瓶装水、流体桶及实体桶释放；不开放树木生成或传送门点火 |
| 指定位置生成实体 | `dndturn:spawn_item` | 精确 SpawnEggItem、ArmorStandItem、HangingEntityItem、ItemFrameItem、MinecartItem、BoatItem、EndCrystalItem；拒绝非默认 ENTITY_DATA、Boss蛋、刷怪笼修改、末地龙复活及超预算几何。生成仍依赖原版合法位置、轨道／水体等条件 |
| 指定实体使用 | `dndturn:entity_item` | 命名牌、羊剪毛染色、空槽互动装备；精确牛／羊／猪／鸡／兔／山羊／海龟／蜜蜂／猫／狼的已审计喂食条件，狼骨头驯服，成年牛／山羊挤奶。无法接受食物时拒绝，不回退为坐下或骑乘 |
| 瞄准攻击 | 既有弓／弩／雪球 | 沿用现有弹药和战术攻击合同；弓完成蓄力、弩完成装填及发射，不因动作完成结束玩家回合 |
| 瞄准投掷 | 新增鸡蛋／经验瓶／喷溅药水 | BLOCK 或 ENTITY 目标，原版 `useItem` 发射；喷溅仅接受 ParticipantEffects 已支持的非即时药效。保留原版散布 |
| 鱼竿 | `dndturn:fishing_rod`／`dndturn:reel` | 抛竿和收竿是两个独立动作，收竿绑定本人已确认生成的浮漂；等待仍由环境推进，不保证一次抛竿获得鱼获，不放行载具控制 |
| 暂不接入 | 防御／观察／发声，身体／载具控制，无外部目标的功能，库存取出 | 遵循用户明确排除；不把这些行为混入通用右键回退 |

特殊投射物及其余实体／方块组合尚未全部接通，活动余项仅在[02 的 PI 条目](../02_GAPS_AND_CONFLICTS.md#player-items)维护。上述“支持”表示存在带校验的源码执行路径；未经单独运行测试的组合不声称验收完成。

## 执行与因果

选择、能力发现和预览沿既有纯查询链；提交后核验装备来源、目标身份、回合资源和合法执行位置。ItemOnBlock 的准备结果包含真正写入格及有限依赖。生成操作预检有界几何，并在加入事件再次校验实际包围盒；这些检查不等价于任意模组副作用的事务回滚。

`ItemUseEffects` 仅在一次同步调用／持续使用步内持有实体引用，结束后只保留 UUID、实例身份、类型和坐标。原版实体插入返回 true 才记录生成成功，不把加入事件或尚不可见的 UUID 查询当作完成。原版接受但没有确认插入时保留 UNKNOWN，不自动重放。

投掷物使用既有 projectile origin、调度域、持久隔离和来源保留机制。只有已确认 USE_ITEM 发射、匹配来源和当前环境授权才能登记 `beginCausalItemImpact`；接触是发射后的 ENVIRONMENT 子效果，不另收动作。重复 ID 同负载不重执行，不同负载拒绝。碰撞前核验有限作用区域、加载／保护／domain，以及受支持的目标和方块回调；效果后的异常保留 UNKNOWN。原版调用与结果提交期间暂缓死亡／离场，之后统一释放。

新投掷物目前限制在已审计的干燥空气及普通惰性方块；鱼竿另支持水体。未知遍历／接触不静默成功，不销毁投掷物，不新增超时。离线施放者、跨域和其他特殊影响尚需扩展，不能将这一有限实现描述为通用投射兼容。

## 固定版本接入点

| 类／完整方法形状 | 侧、调用路径与职责 | 取消／维护语义与验证 |
|---|---|---|
| `ServerLevel.addEntity(Entity): boolean`（private） | 服务端线程，addFreshEntity／addWithUUID 等委派；WrapMethod 观察原始返回 | 不取消插入；只有返回 true 才记录生成。GameTest 实测盔甲架、经验瓶、鱼钩；未跟踪 section 的 UUID lookup 可晚于插入成功 |
| `BrushItem.calculateHitResult(Player): HitResult` | `useOn`／`onUseTick` 射线；HEAD cancellable | 仅当前刷子执行作用域改用本次已选定朝向。原版工具函数读取旧朝向 `getViewVector(0)`，因此不能靠等待下一 tick 对齐；新桥保留方块和实体遮挡。刷取至原版砂砾／沙转换的测试覆盖疑似沙 |
| `Mob.mobInteract(Player, InteractionHand): InteractionResult`（protected） | 服务端线程，Invoker 动态分派精确已审计生物喂食／挤奶实现 | 调用前保留 CommonHooks.onInteractEntity 取消入口；不调用会优先解绳／转移周围牵绳的 Entity.interact。未知食物／目标组合拒绝；此新 Invoker 已经真实服务器加载，全部生物组合仍待逐项实测 |
| `Projectile.hitTargetOrDeflectSelf(HitResult): ProjectileDeflection` | 服务端实际命中前 WrapMethod；鸡蛋、经验瓶、喷溅药水精确类型 | 校验通过调用一次 original；拒绝返回 NONE 并隔离。未托管实体仍走原版，不依赖一般伤害事件代替全部命中门控 |
| `Projectile.onHit(HitResult): void` | FishingHook 原生 checkCollision 路径直接调用；独立 WrapMethod | 只桥接鱼钩；其他 projectile 在前一入口管理。抛收 GameTest 运行覆盖加载及普通落地路径 |

所有新增 Mixin 均列在 required 配置中，defaultRequire=1；未使用可选匹配隐藏签名失效。`ServerPlayerGameMode.useItem/useItemOn`、BrushItem、SpawnEggItem、各实体放置物品、BucketItem、FishingRodItem、Projectile／ThrowableProjectile／FishingHook 的调用路径以对应 patched 源码为准。

## 预览

`ProjectileProfiles` 只列当前注册弹种；`BowPreview` 按具体发射速度、重力、偏角及运动先后次序计算名义中心轨迹。弩不误加玩家继承速度；投掷物先重力／阻力后扫掠，鱼竿使用原生抛竿方向并在预计入水处停止。不会创建实体、消耗随机数或补跑真实 tick。客户端渲染消费预览点，服务器不信任客户端命中。

GameTest 将弓及鸡蛋／经验瓶／喷溅药水前两步与无散布的原版实体实测比较。实际发射仍保留散布，完整水体、不同碰撞体和附魔组合并未因此得到精确保证。

## 版本与验证

网络注册版本28、C2S既有版本22、common schema13、target envelope12。新增 EQUIP 值、因果使用接触以及生成身份观察；旧格式显式拒绝。common 只新增纯值规则和测试，不引用 Minecraft API；其他 target 不因共享枚举新增而启用上述物品能力。

2026-09-28，Windows，JDK25.0.3，在 neoforge-26.1 独立根实际执行：

```text
gradlew.bat clean build :common:test runGameTestServer --console plain --no-daemon
```

结果：BUILD SUCCESSFUL；common **81** 项、失败0；GameTest **63** 项 required 全通过。日志 `targets/neoforge-26.1/verification-player-items-clean.log`（本地验证输出，不作为发布资源）。新增 player_items 覆盖蜂蜜脾、作物骨粉、重复请求、穿戴免费、确认实体生成、刷取全过程及不结束回合／不扣移动；player_item_projectiles 覆盖经验瓶初始暂停、环境原生 XP 碰撞、因果结果、重复发射、抛竿和收竿。common 测试覆盖伪造来源、环境前拒绝、重复与冲突、无二次扣费及恢复后不重放。

本批曾出现 brush 旧朝向及生成实体即时 lookup 不可见问题，已通过作用域射线与成功插入观察修复。经验瓶测试扩大地面夹具以容纳原版散布；既有 environment_boundary_time 漏斗及 prototype_zombie_navigation 导航也曾间歇失败。最后单次全通过不关闭历史间歇风险。真实键鼠、双客户端、鱼获、全部生物／放置组合及跨进程恢复未运行，不把服务器夹具当作这些验收的替代。

共享代码兼容构建：JDK21.0.11，分别在 forge-1.20.1、fabric-1.20.1、neoforge-1.21.1 独立根执行 `gradlew.bat build --console plain --no-daemon`，最终三者均 BUILD SUCCESSFUL。初次并行执行时 Fabric 和 NeoForge1.21.1 因共用 common/build 的测试结果文件竞争失败，改为串行重跑后成功；不是代码断言失败，也没有删除断言或跳过测试。日志分别为 Forge 的 `verification-player-items-common-compatibility.log`、Fabric／NeoForge1.21.1 的 `verification-player-items-common-serial.log`。这些构建不代表旧 target 获得26.1玩家物品功能。
