# NeoForge 26.1 门禁与近战目标接入

固定 Minecraft 26.1 / NeoForge 26.1.2.84，JDK 25.0.3。2026-09-30；源码依据为 target 解析的 `minecraft-patched-26.1.2.84-sources.jar`，未升级依赖。

## 所有权与修复

`EffectControlPolicy` 接收纯值事实，统一环境爆炸的当前 step／目标暂停域组合，以及云的因果确认、活动 domain、目标成员／step、全局冻结和恢复组合。`EnvironmentExplosion` 持有有界调用 frame；`CreeperClouds` 验证来源记录；`ServerCombatService` 捕获当前事实。策略不签发许可、不扣预算、不修改归属。过期爆炸 frame 清空影响集合；云的 domain 必须与已登记 invocation 的 canonical encounter 一致，畸形／伪造 tag 拒绝且查询不改写 tag。

`SimulationPreparation` 区分 ADVANCE、HELD、CONTACT_HANDLED、QUARANTINED、ENDED。纯暂停查询不处理碰撞；执行边界准备由 service 对账。缓存绑定实体实例、服务器 tick 和调度修订；同 tick 已处理接触不因规则修订重新执行。隔离、撤销和真实预算仍属于既有 owner，原版桥接仅消费是否跳过 native tick。

斧头攻击苦力怕失败的直接原因是受击适配缺失，发现阶段返回 `target combat adapter unavailable`，尚未创建接近计划；与斧头伤害计算无关。前置重构将受击注册表移至独立 `DamageReceivers`，玩家发现、执行及 Mob 近战共用该目标合同。旧 `MeleeAdapters.Receiver` 兼容注册委托到同一 owner，不再持有第二套表。

固定源码审计后，标准 LivingEntity 受击 profile 注册精确 Creeper、Skeleton、Stray、Bogged、WitherSkeleton 类；保留已审计 Player、Zombie、EnderMan profile。未知 subclass／override 拒绝，不由 EntityType 或父类匹配扩大支持。注册受击能力不授予攻击、AI 或身体模拟权。没有斧头、物品与目标组合的放行分支。

客户端 `ClientTacticalPlan` 独立保存权威失败信息，后台移动预览刷新不再覆盖它；新的明确选择／提交／取消或生命周期 reset 清理。`ClientEffectRoundMixin` 的效果暂停查询移至 `ClientEntitySimulation` 投影 owner。

## 接入审计

本次检查全部 83 个生产 Mixin 文件（服务端／通用 55、客户端 28），并追踪保留 helper 的职责。关键原版签名、调用顺序及早退按上述 patched 源码复核；编译证据与运行证据分开。

| 入口／消费者 | 裁决所有者与取消边界 |
|---|---|
| ServerLevel.tickNonPassenger / tickPassenger、实体 simulation step | 实例捕获与准备由 service 持有；原版 freeze 仍生效，取消实体推进不取消容器维护 |
| ServerGamePacketListener 输入／移动／库存 | 输入策略与移动 lease 分开；线程、传送确认及纠正维护保留，身体窗口不授权物品行为 |
| Mob AI、Goal、Navigation、move/look/jump、特殊实体 AI | actor 策略与 typed lease；导航控制和自主决策分开，不由一次 body 放行推导攻击权 |
| TNT 爆炸、爆炸实体／方块集合与伤害入口 | 原生 explode frame 关联真实源，策略过滤影响集合；伤害仍需当前源及同 step，不将域成员当作许可 |
| AreaEffectCloud.tick 目标循环 | 写入 victims／药效前过滤；已登记来源、当前 domain 与目标策略共同约束，查询无副作用 |
| LivingEntity / Player 动态受伤、盾牌／护甲、主动过程 | DamageContext／有限能力执行 owner；保留原生免疫、取消及事后观察，纯 gate 不构造伤害结果 |
| 箭接触、投掷物、方块使用 | 准备结果与效果许可分离；已处理接触不再次 native tick，隔离不被普通暂停结果覆盖 |
| held tick、区块查询／卸载、随机 tick、block event、漏斗 | 调度 owner 保留队列和维护；漏斗分别核验源／目标写入，不创建额外授权 |
| Conduit 等绝对计时桥接 | helper 承担已批准的计时基线，不持有第二套阶段／成员裁决 |
| 客户端 body／输入／效果计时 | ClientEntitySimulation 消费权威投影；本地输入接收者和原版玩家例外不扩展服务端权限 |
| RenderState、模型、粒子、箱盖、CameraRig | 表现 owner 的 controlled 表示显示通道归属；箱盖仅桥接受限视觉信号，不开放环境预算；镜头不驱动身体 |

标准 receiver 复核了 LivingEntity.hurtServer、actuallyHurt、护甲／魔法减伤、knockback、die 及 Mob／Monster／上述具体类的 override 关系；受击支持不等于这些实体所有特殊技能均已适配。

## 验证与限制

命令在 `targets/neoforge-26.1` 执行，`JAVA_HOME=C:/Program Files/Java/jdk-25.0.3`，`DNDTURN_TEST_SEED=260930`：

- `gradlew.bat clean build :common:test --offline --console plain --no-daemon`。
- `gradlew.bat runGameTestServer -PgameTestSelection=dndturn:gate_melee* --offline --console plain --no-daemon`。
- `gradlew.bat build :common:test runGameTestServer --offline --console plain --no-daemon`。

结果：clean build 通过；最终增量 build 与 common 83 项通过（0 failure/error/skipped），完整 GameTest 57/57 通过。最终日志 `verification-gate-final2.log`；另以种子 260931 重跑近战两项，2/2 通过（`verification-gate-melee-seed2.log`）。构建同时通过 verifyUiProjection、verifyDebugDiagnostics、verifyControlPolicies。产物 `build/libs/DNDTurn-neoforge-26.1-1.0.0-SNAPSHOT.jar`，未发布。日志和 build 产物只作本地证据，不纳入源码提交。

修复前的同一夹具在斧头发现阶段复现受击适配拒绝。修复后原地斧头、剑、石块走共同入口，记录实际生命损失 8、5、0；验证动作收费与重复请求不重复执行。接近夹具通过真实移动包和原版碰撞推进，检查接近期间未扣攻击动作、移动余额实际减少、MOVE 回执、攻击完成及控制释放。未用 setPos 代替待测接近。

接近测试最初在攻击后固定时刻读取余额，完整批次暴露回合重置后的误报；现于 lease 存活时记录余额减少，并核对不可变 MOVE 回执。保留完整收费断言，未改生产规则。策略检查覆盖爆炸组合、云冻结／恢复／域／目标反例及准备状态；GameTest 补畸形／伪造云 domain、未知 receiver subclass 拒绝。

真实鼠标／KeyMapping、客户端自动路径驱动和双客户端显示未在本批执行；嵌入连接的移动包不能替代真实输入验收。没有新增其他 target 功能；common 未由本次门禁修改，其他 target 未重跑。本批不关闭原有生命周期、硬崩溃恢复或历史间歇问题。
