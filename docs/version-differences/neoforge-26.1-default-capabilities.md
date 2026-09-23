# NeoForge 26.1 默认通用能力（2026-09-30）

用户明确要求“移除所有精确白名单，默认可用”，并确认同时覆盖物品和方块交互。此修订覆盖历史精确类型准入说明；客户端渲染适配不在范围。目标实际依赖为 Minecraft 26.1.2 / NeoForge 26.1.2.84，构建 JVM／语言 JDK 25.0.3，common 仍 Java 17。

## 原因与实现

最新客户端日志 `run/logs/latest.log` 的提交与查询曾返回 `target combat adapter unavailable`：牛未列入 DamageReceivers 的精确类型集合。现在内置注册器提供通用 LivingEntity 受击与 Mob 近战后备，明确专用注册优先，冲突仍拒绝。没有 ATTACK_DAMAGE 属性的 Mob 默认伤害为零，不虚构伤害。AI 通用入口使用真实感知、敌对／目标关系与导航能力；中立单位不因此无条件主动攻击玩家。

Skeleton／EnderMan／Creeper 等专用行为按家族匹配；ActorLifecycleAdapters 选择最近注册父类。雪球实体命中移除仅限 Zombie 的检查。新增近战后备曾使选项注册顺序压过已装备远程能力，现由平台采样按 RANGED 语义优先排列，不加入核心物种分支。

物品提供通用 use、useOn 与 interactOn 入口，BlockItem 放置不按具体 block/item class 或原版命名空间准入。装备按组件判定，专用蓄力／瞄准／消耗生命周期保留，物品家族接纳子类。查询不执行原版交互，原版 PASS／FAIL 不报告成功。免费方块交互与付费物品使用仍分开。

## 固定版本接入

已查阅本 target 解析出的 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`，而非相邻版本资料：

| 接入 | 位置及职责 | 拒绝／取消语义 |
| --- | --- | --- |
| ServerPlayerGameMode.useItemOn | 保留原有固定签名重定向；付费路径允许 onItemUseFirst、BlockState.useItemOn、ItemStack.useOn | 免费路径不调用物品；付费路径的 TRY_WITH_EMPTY_HAND 不回退为免费方块分支 |
| Player.interactOn(Entity, InteractionHand, Vec3) | 服务端原版实体／物品交互链，目标绑定后使用身体中心相对坐标 | 尊重 CommonHooks 和原版返回值；未伪造精确客户端身体部位命中 |
| Level.setBlock(BlockPos, BlockState, int, int) | HEAD，先于 chunk 写入与邻居传播，记录实际写入位置 | ItemUseEffects 同步作用域内核验线程、世界、已加载、遭遇范围与保护；失败抛出，由行动层保留 UNKNOWN |
| Level.destroyBlock(BlockPos, boolean, Entity, int) | HEAD，先于掉落与底层 setBlock | 同上，不能只在 setBlock 阻止而让掉落先发生 |
| Level.setBlockEntity / removeBlockEntity | HEAD，先于注册／移除 | 同上；不涵盖 BE 内部任意字段改变 |

ItemWorldWriteMixin 在无当前物品／方块执行作用域时不改变行为。实际同步写入有界扩展前置快照与 footprint，最多256个方块。既有准备仍预检已知多格放置；部分副作用之后遇到拒绝不声称世界回滚。任意模组直接区块写入、绕过 Level 的覆盖方法、BlockEntity 内部字段与延迟传播不构成完整沙箱保证。

特殊效果语义限制（例如特殊弹药／附魔、瞬时药水、刷怪笼修改和龙复活的专用生成计划）、预算、保护、域及授权仍各自生效；它们不是要求实体列入名单才能使用通用受击／攻击／交互。ParticipantEffects 中既有精确效果时钟转换范围保持不变；通用喷溅查询不再以是否存在该转换适配作为持续药效的准入条件。不宣称任意特殊技能已完成翻译。

## 本轮验证

在 target 独立 Gradle 根等价执行（Windows gradlew.bat，JDK25）：

- `clean build :common:test` 成功，16个任务实际执行，包含 UI 检查和 common 测试。
- `runGameTestServer -PgameTestSelection=dndturn:<名称>` 逐项运行13个场景全部通过：`gate_cow`、`default_interactions`、`repair_values`、`body_control`、`mob_standardization`、`mob_standardization_external`、`mob_standardization_held`、`mob_standardization_miss`、`neutral_mob_turn`、`creeper_effect`、`environment_processes`、`gate_melee`、`gate_melee_approach`。
- `gate_cow`：选择→查询→提交→伤害→扣动作→重复请求，铁斧8、铁剑5点实际生命损失，石头命中0点仍按原规则结算。
- `default_interactions`：栅栏原版放置、水瓶将泥土转泥巴、持红石免费操作拉杆、原版对牛挤奶；检查纯查询、实际方块／物品变化、动作费用和免费路径不消耗手持物。
- 修复远程优先级后，标准化四项覆盖原生箭来源、持箭、独立扩展生物和未命中。

日志在 target 的 `run/default-*-test.log` 与 `run/default-capabilities-clean-build.log`（本地构建证据，不提交日志产物）。新增 Mixin 已在实际 GameTest 服务端加载并执行，非仅编译验证。

**未通过／未执行：**全量 `runGameTestServer` 曾失败，包含修复前远程优先级失败及同进程夹具成员／关闭会话干扰，随后测试序列读取 unknown encounter 导致服务端崩溃。修复后相关场景逐项通过，没有宣称全量同进程通过。真实客户端鼠标菜单、双客户端与任意第三方 override 尚未验收。本次仅改26.1平台功能，未扩展其他 target 的能力范围。
