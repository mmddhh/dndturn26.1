# NeoForge 26.1 Mob 标准化与装备弓箭

2026-09-30；固定配置 Minecraft 26.1 / NeoForge 26.1.2.84，解析产物报告 Minecraft 26.1.2。未升级依赖。Gradle JVM/target 为 JDK 25.0.3，common 为 Java 17。基于开始时已有的未提交 Effect、Gate、文档与测试改动增量实施。

## 实现与边界

- `AbilityDefinition` 分开持有目标集合／目标政策、`ActionCost`、执行操作 kind 和执行器；能力参数是定义声明的有界字符串选项。参数进入意图相等性／去重，非法值在规则写入前拒绝。已有能力的默认执行 kind 和费用不变。
- `ActorEquipment` 为玩家库存与 Mob 真实双手提供共同的地址、读取、指纹和验证端口；`AbilityObservations` 不再强制玩家库存。玩家选槽和持续使用仍沿用原确认链；未声明 Mob 具有玩家背包或菜单权限。
- `EquipmentEffects` 按标准 Tactical Effect 定义 ID／版本注册类型化装备政策，解析活跃实例及 grantRevision。重复定义／竞争贡献拒绝；效果状态仍仅由 ActorStates 持有。GroupEffects 在入场生命周期安装组声明的标准效果，退出释放自身效果；查询不安装效果，同一已绑定生命周期内撤销后不靠每 tick 补回。
- 精确 Skeleton 的组定义、效果注册和原生射击调用集中在 `SkeletonFamily`。标准 Actor、能力发现、规则和 planner 无 Skeleton 类型判断。普通弓是装备能力：持有真实弓且有有效原生射击效果、匹配的已审计驱动时才形成绑定。
- 手持普通箭优先，否则由效果提供原生普通箭来源；射击不扣箭堆、不损耗弓，但合法攻击开始扣一次 ACTION。普通箭和手持箭均经过真实原生发射，不伪造箭伤害。特殊箭、附魔、未审计组件、未知子类／override 保持拒绝。
- 复合装备来源记录 actor／实例、弓地址／内容、双手写入代次、弹药来源、Effect 实例／grantRevision、翻译政策 ID／版本和 native driver ID／版本。相同内容的替换装备也使旧来源失效。调用、检查点及投射物保存的原始 invocation 保留证据；结果／演出按 operation/root 关联。
- `TacticalExecution.ranged()` 只执行当前装备计划绑定的已注册驱动；回调过期、准备／释放、重复调用拒绝。发射数和物品变化被观察；不确定结果为 UNKNOWN，不重放。投射物继续复用既有环境域、碰撞、命中、因果及资源后端，发射后的 Effect 撤销不篡改箭的来源。
- AI 家族匹配移入注册适配器，显式 provider 优先、家族同优先级冲突拒绝，注册变动清 bake 缓存、启动冻结。结构候选诊断限 32 项，只读 Goal 包装配置，不运行 Goal。未知地面 fallback 仍仅移动。
- 共享 planner 支持可用近战／远程绑定；执行者无动作时仍可提出合法移动。射程仍为六格，规划选点预留半格到位余量，实际执行按六格和 LOS 复验；不放宽射程、不修改原版导航完成判定。

其他三个 target 仅保持构建兼容，未启用这些平台能力。Guardian／ElderGuardian、完整复杂 AI、特殊攻击副作用和未定 Boss／飞行能力不在本次交付范围。

## 固定版本接入审计

读取对应 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`；以下均为服务器合法线程内的适配／读取。没有将附近版本文档当作当前注入依据。

| 入口 | 核对结果与职责 |
| --- | --- |
| `AbstractSkeleton.performRangedAttack(LivingEntity,float)` | 读取持弓手、`getProjectile`、`getArrow`，调用原生生成／发射并播放声音；不扣箭堆／弓耐久。只注册精确 Skeleton，未把其他变种视为同义能力。 |
| `Monster.getProjectile(ItemStack)` | 原生持有弹药查找，空缺时普通箭，再经 NeoForge hook；发现阶段只读取双手和显式效果，不调用此 hook 试探。任意第三方 hook 不属于隔离保证。 |
| `ProjectileUtil.getMobArrow` | 经 ArrowItem 创建箭、调用 `setBaseDamageFromMob`；原生箭基础伤害在插入时捕获，之后经现有战术规则结算一次。 |
| `EntityEquipment.set/setAll/clear` | 普通 set 返回后推进该槽代次；批量替换／清空后推进各槽代次。Mixin 必须匹配，不取消原方法。对象内代次不持久化为跨实例权限；内容原地变化另由完整指纹检查。 |
| `LivingEntity.equipment` | 只读 accessor；`PlayerEquipment` 的玩家库存差异保留，未声称其所有写入由普通 EntityEquipment.set 覆盖。 |
| `Mob.goalSelector/targetSelector` | 只读 accessor；诊断读取 `getAvailableGoals`、包装的 priority 和 Goal 类型，不调用 canUse/start/tick。结构证据不授予攻击。 |
| 原有箭插入、环境推进和受伤入口 | 发射调用栈登记生成身份及既有 ProjectileOrigin／ProjectileAbility，最终伤害仍使用现有许可与虚拟受伤观察链。 |

新增 Mixin 通过实际 GameTest 启动验证；标准层没有新增按 Skeleton 类型分支的 Mixin。

## 本轮验证

- JDK 25.0.3：`gradlew.bat clean build :common:test` 成功；包含独立 GameTest 源集编译及既有 UI／debug 独立检查。
- `DNDTURN_TEST_SEED=26101`，`gradlew.bat runGameTestServer`：63/63 required 通过。此前全套运行暴露测试将新 AI 移动误认为旧 Lease；修正为按 operation 核验释放后，全套通过。
- common 最终 88/88 测试通过（含新增装备 Effect／复合来源／有界参数及 AI 远程／动作后移动测试）。
- `DNDTURN_TEST_SEED=26102`，`runGameTestServer -PgameTestSelection=dndturn:mob_standardization*`：新增7项全部通过。
- `DNDTURN_TEST_SEED=26104`，相同 Mob 专项选择：7/7 通过；`player_only_start` 同种子单独运行1/1通过，不能据此关闭其整套交错失败。
- `DNDTURN_TEST_SEED=26103`，`gradlew.bat build :common:test runGameTestServer`：最终64/64 required 通过；日志为 `build/mob-final-verification.log`。
- Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1 在各自目录、JDK 21.0.11 下执行独立 `gradlew.bat clean build`，均成功；没有开放这些 target 的战术入口。
- `mob_standardization`／`held`：真实装备、纯发现、AI 提案、相同内容换装失效、特殊弹药拒绝、单次发射／费用、重试、物品零差量、检查点生成证据、Effect 撤销后箭继续完成原生接触。
- `mob_standardization_external`：独立 `compat` 包为带测试标记的 Cow 注册组效果、AI provider 和原生箭驱动，复用标准装备能力，无核心物种分支。
- `mob_standardization_approach`／`revoked`：原版导航实际移动及移动费用、接近不扣动作、到位再射击／扣动作；途中撤销保留动作且释放原计划 Lease，不生成箭。
- `mob_standardization_miss`：固定自然 1，真实箭接触后得到未命中、零伤害；动作不退款。
- `mob_standardization_automatic`：仅经正常 START／结束玩家回合进入自动 Mob 决策，不直接 submit，观察标准弓箭 PLAN、原生碰撞与伤害；退出清除组所属效果。

首轮测试发现入场效果安装晚于首次查询，已移至发布首次可用回合前。接近测试还发现边界规划点与原版到位容差不相容，修复选点余量；没有取消移动／费用断言或用传送代替待测导航。

额外种子26104的整套运行在既有 `player_only_start` 等待器读取已结束遭遇处崩溃（`RepairGameTests.java` 的 `state(encounter)`，`unknown encounter`）；该次整套不算通过，日志 `build/mob-release-verification.log`。保留清理时点／环境两轮等待的排查项，不将之前64项通过扩写为所有种子通过。

本轮未运行真实键鼠／双客户端、硬崩溃和完整活动重启续战；历史导航等间歇缺口不因本批通过直接关闭。

产物检查确认发布 jar 含标准装备／效果／Skeleton 适配类及 Mixin 资源，不含本轮 GameTest／compat 测试类。文档检查修正01迁移至legacy后遗漏的相对路径；历史 `src/compatTest/.../ExternalMeleeChecks.java` 当前不存在，已显式注明，没有把它当成本轮扩展证据。
