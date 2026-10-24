# NeoForge 26.1 环境与 Mob 实施证据

2026-09-28。依赖来自 target `gradle.properties` 及实际解析的 `minecraft-patched-26.1.2.84-sources.jar`；Gradle JVM／target编译 JDK25.0.3，common Java17。未升级依赖，保留工作树已有 UI 等改动。本文只记录本批事实；活动余项唯一登记在[02](../02_GAPS_AND_CONFLICTS.md)。

本次文档同步保留以下环境／Mob实施阶段证据，不重新运行其中命令。前轮静态核对的61项required通过属于 `verification-participant-effects-verified.log`；后续玩家物品批次的81项common／63项required及兼容构建见[物品记录](neoforge-26.1-player-items.md)。各自的测试数量与执行环境只属于对应运行，不把本次文档同步描述成重新验收。

## 固定版本接缝

| 类／方法 | 位置与职责 | 取消与支持边界 |
|---|---|---|
| DaylightDetectorBlock.tickEntity(Level, BlockPos, BlockState, DaylightDetectorBlockEntity) | 仅重定向采样条件的Level.getGameTime，使用当前执行环境有效时间 | updateSignalStrength仍读取真实有效天光和SUN_ANGLE；没有替换全局时间 |
| HopperBlockEntity.ejectItems(Level, BlockPos, HopperBlockEntity) | HEAD校验朝向接收位置，早于原版／NeoForge容器或handler查询及提取 | 暂停接收域返回false，不能由外部hopper偷渡；不宣称覆盖双箱完整足迹 |
| HopperBlockEntity.suckInItems(Level, Hopper) | 仅HopperBlockEntity的当前位置和上方来源早期门控 | 不扩大至未经审计的漏斗矿车；保留原生返回false路径 |
| HopperBlockEntity.addItem(Container, ItemEntity) | 提取前核验存活物品及当前源／目标位置 | 不能沿已失效接触继续取物；保留原tryMoveItems门控 |
| HopperBlockEntity.pushItemsTick | 源码审计冷却在原生获准ticker递减，tickedGameTime用于本次传输排序 | 未将排序戳错当期限替换；完整跨域冷却组合仍开放 |
| EnderMan.teleport(double,double,double) | HEAD在受控区域拒绝 | 门控覆盖hurtServer等非Goal来路；不取消整个身体tick、不提供传送能力 |
| MobEffectInstance.tickServer(ServerLevel,LivingEntity,Runnable) | HEAD仅托管已审计效果，跳过原生期限／周期；END_TURN调用原生applyEffectTick及隐藏链降级 | 不冻结身体；到期沿MobEffectEvent.Expired取消路径，onEffectsRemoved／onEffectUpdated维护属性及原生包 |
| PoisonMobEffect／WitherMobEffect.applyEffectTick、RegenerationMobEffect.applyEffectTick | 原生hurtServer／heal调用点仅在精确回合结算Frame内缩放单次输入 | 原生免疫、事件取消及最大生命限制保留；中毒保持至少1生命，周期相位不保存 |
| Entity.baseTick | 仅成员Mob的setRemainingFireTicks／火焰hurtServer调用重定向；回合末原生onFire输入 | 不截断身体／水中熄灭／免疫clearFire；玩家着火不在此Mob专用桥范围 |
| ServerPlayer.die(DamageSource) | markClientUnloadedAfterDeath调用之后记录当前实例已完成死亡；服务端主线程 | NeoForge可取消死亡早退之前不确认；ServerPlayer override不写LivingEntity.dead，不能只读基类字段 |
| LivingEntity.tickEffects／MobEffectInstance.tickClient | 服务端抑制冻结duration%600重复刷新；客户端仅对成员的已审计效果保持权威期限 | 客户端Mixin编译，真实客户端注入／重跟踪仍未验收 |
| GameTestInfo任务调度 | 运行中迭代FastUtil任务Map；回调内批量添加任务可扩容活跃Map | 环境边界测试改GameTestSequence；跨域箭等夹具预登记或等待原生加载条件 |

## 已接入行为与边界

统一RoundTime提供期限向上、已有充能向下、分数周期量与溢出检查；当前生产接入包括新会话移动／环境共同捕获，以及已审计药效／Mob火焰回合期限。特殊Mob技能仍未接入。现有common独立双预算接口保留其他target兼容；26.1新配置仅读取roundTicks，旧键原值及其他键保留。

NativeActorFacts携带属性基础／有效值、尺寸、姿态及翻译政策ID/版本。Mob扫描BudgetedScan按排序成员游标续行，生产Capture上限4096；内部预算异常不丢已完成目标，当前目标重新评估。实例、会话版本、位置、当前目标、属性与能力来源改变使捕获失效；最后执行仍复验。动态世界变化与高频来源变化的规模边界不因80实体场景通过而关闭；5000项纯值游标测试使用自己的构造上限，不是生产成员上限。

IntrinsicMelee使用TacticalAdapter的公开TacticalExecution.melee端口。端口仅执行绑定计划的一次已审计近战，不能指定新目标、伤害、operation或费用；回调离开、终态及版本／实例失配撤销。最多32个64字键／512字值组成计划托管状态，不恢复平台对象。release异常保留控制故障及状态，清理成功清空状态并追加对账，原结果不回写。旧平台内置物品驱动尚未全部迁到此端口，也未提供任意世界写入API。

近战精确增加Enderman，保留玩家对Mob／Mob对玩家关系限制；真实7基础＋3modifier得到10有效伤害且不重复加装备。原生点燃、反伤、附魔、未知override不因类型匹配取得权限。Guardian、Creeper、ElderGuardian、Skeleton装备弓仍未正式接入。

本实施批次为EntitySimulation增加已提交environmentTime，当时S2C27／C2S22、common schema12／target envelope11；后续玩家物品增量后的当前版本见[01](../legacy/01_IMPLEMENTED_DECISIONS.md#baseline)。参与者期限与回合结算证据保留，旧格式拒绝且保留原始数据。客户端PrimedTnt精确烟雾通道消费新增环境事实；初次跟踪只建立基线，不重演历史。不改变真实引信、位置、tickCount或ParticleEngine整体tick。

ParticipantEffects持有唯一过程值；RoundDuration保留每个期限的基准，合并不重捕获已有期限。支持minecraft命名空间下精确MobEffect基类、Regeneration／Poison／Wither／Absorption实现；其他特殊效果保留未翻译状态。隐藏链上限16、每主体128种效果、每边界129条观察。无限期限保留-1；极端期限的回合数不截断，写回原生int表示时饱和Integer.MAX_VALUE，这是原生表示限制，不代表可无损恢复更长实时期限。

END_TURN先登记规范身份，经已有有限permit接纳自体DAMAGE子操作，调用原生周期入口并留存生命／吸收前后及返回事实，再推进期限。原生治疗无返回值，nativeAccepted仅表示heal调用正常返回，是否恢复生命以观察差值为准。子操作完成后发布根结果，并在对外同步前确认死亡及成员退出。异常保留UNKNOWN并停止会话；同根请求不重放。恢复比对期限及隐藏链后绑定新实例，不用旧实例UUID当许可，也不承诺实体／SavedData任意保存顺序原子一致。

## 实施阶段实际验证（本次文档同步未重跑）

- `:common:test`：该实施阶段记录80项，0失败／错误／跳过。覆盖30／17基准、分数周期量、捕获／往返与游标5000项，以及自体回合许可、非法输入无部分提交、重复边界、无限／极端期限；数量来自当时JUnit XML。
- 26.1独立 `clean build --max-workers=2`：通过。曾将common测试／clean／build／GameTest合并执行时出现Gradle任务调度无进展，后分开执行，不归因为源码通过或失败。
- `runGameTestServer --max-workers=2`：`verification-environment-mob-boundary-evidence.log`记录59项全部通过（包括4项本批新增及独立包近战）。最终释放故障扩展后，`verification-environment-mob-clean-final.log`记录clean build全部15项任务实际执行并通过；`verification-environment-mob-release-final.log`记录59项GameTest全部通过。故障场景核验首次release抛出后控制隔离、下一次清理保留托管状态、不重复伤害／费用、追加ReleaseReconciliation且不回写原终态。
- 新平台场景：旧配置保留与新键／非默认捕获、近战注册冲突、环境时间协议；80实体跨tick发现；玩家／Enderman共享根结果与幂等费用；真实hopper跨域阻止与授权后转移、日光有效20步；独立包非人形适配执行与回调撤销。
- 其余三个target分别独立 `build`，JDK21：forge-1.20.1、fabric-1.20.1、neoforge-1.21.1均通过。其已批准功能范围未扩大。
- 最终生产jar `DNDTurn-neoforge-26.1-1.0.0-SNAPSHOT.jar`复查不含gameTest、test_instance、test_environment、新测试类或example.compat包；包含公开TacticalExecution类。独立compatTest源码集只进入验证运行。

参与者回合桥追加验证：`verification-participant-effects-clean.log` 为独立 `clean build`，15项任务实际执行通过；`verification-participant-effects-verified.log` 为 `:common:test runGameTestServer`，80项规则测试与61项平台测试全部通过。新增两个场景覆盖37／7实时tick等待、身体继续推进、施加当回合与末回合完整再生0.6、中毒生命底限、凋零取消及致死、治疗取消与生命上限、隐藏药效降级、到期取消后重试、重复请求、Mob着火1.5与退出恢复、检查点Codec和原生期限对账。新ServerPlayer死亡确认钩子同时验证死亡取消不移除成员。客户端药效Mixin未在真实客户端运行。

此次变更后其余三个target再次独立 `build` 通过，各自日志为 `verification-participant-effects-compatibility.log`，JDK21.0.11。新生产jar再次检查测试目录／fixture类／独立兼容包，命中0项；含ParticipantEffects、RoundDuration和ServerPlayerDeathMixin。

失败证据保留在participant-effects首轮／second／third／final日志：首轮定位玩家死亡override缺口、Cow生命初值错误及旧药效预期；后续定位合并扩展可能改选已消费入口的主会话，以及实体UUID可查但空间查询尚未就绪的夹具时序。合并测试现通过后续合法环境入口完成三会话／新锚点断言；火焰与旧导航场景等待原生发现就绪，导航仍要求实际移动与观察扣费，不借传送替代行走。旧间歇风险仍留02，不凭一次全通过关闭。

本轮多次完整套件曾报告action_preview、跨域箭、旧环境循环、漏斗及合并发现的间歇失败；保留失败日志，未删除断言。已定位的夹具Map扩容、并行批次、加载条件修复不构成所有间歇性根因已消除的证明，按02继续跟踪。

未执行真实双客户端、活动保存两进程重启、特殊过程故障窗口和新增显示通道实机验证。历史GameTest与本次完整套件不替代这些证据；未关闭任何整个ET／MT大项。
