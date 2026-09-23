# NeoForge 26.1.2.84 接入契约

目标声明：`targets/neoforge-26.1/gradle.properties` 的 Minecraft `26.1` / NeoForge `26.1.2.84`；解析后的 patched Minecraft 为 `26.1.2`。核对文件为该 target 的 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar` 及对应 patched jar；不能用 `.109` 或相邻版本代替。

本文件只记录代码接入位置、职责、取消语义及验证场景，不维护进度或通过宣称。功能缺口和待运行验收统一见 [M3 acceptance](../02_GAPS_AND_CONFLICTS.md)。下列 GameTest 名称用于定位源码场景，不代表本次执行过。

## 会话与入口

### 合并修复后的所有权与协议契约

无会话 START 由 `CombatIntent.start(operationId)` 构造：generation、encounterId、targetId 均为空，expectedVersion 与 fromIndex 为 0。客户端 START 按键共用此工厂，不能把本地“未取得版本”的 -1 哨兵写入协议。`player_only_start` 覆盖工厂编解码、handler 建场/重试和纯玩家环境轮转；近邻 Zombie 为可选成员，未发现敌人不拒绝建场。

- `CombatEngine` 唯一持有成员、阶段、资源和不可变结果；service 负责服务器线程编排。`CombatSubscriptions` 独占连接投影、待发送快照与结果游标，每次发送重新校验成员；owner 的既有结果重试独立于订阅。未协商通道或发送失败不推进游标。
- `ServerEntityProjections` 只为已有 tracking 关系发送 UUID、维度、epoch、序号和暂停状态；客户端缓存不是行动许可。连接、玩家实例或 Level 替换后丢弃旧回调与缓存。
- Consent 仅接收成员查询、区域采样和建场提交能力；scheduled ticks 仅依赖 `RegionAccess`。入站 handler 与不创建服务的出站协议分离。移动与投射物执行状态仍由 service 持有；纯查询不执行碰撞、撤销 lease 或扣费，`prepareEntitySimulation` 只在实际 tick/ride seam 调用并缓存同一步决定。
- `CombatRecoveryCandidate` 在安装 engine 前完成规则与执行元数据校验。失败保留原 JSON；成功审计先移除 pending，再增加独立 projectionRevision 发布就绪。客户端保留同会话结果前缀。
- target schema 5 / common schema 3：操作观测时间为 `observationEpoch + cumulativeServerTicks`；旧值 epoch 为 null，不伪造其时钟来源。世界 scheduled ticks 仍使用所在 Level 的 gameTime，不能与操作时钟比较。
- 协议 12：IntentKind、EncounterPhase、Outcome、StartDisposition 使用固定编码；RESULT_SYNC 的 fromIndex 不复用 expectedVersion。START 区分 WAITING、STARTED、FAILED、CLOSED。新场地先提交规则及回执，再接管队列；接管异常保留非交互状态并在世界推进前审计，不把已提交 START 改成等待同意。
- 关服保存运行状态后释放本服务持有的导航与 held ticks，清理注册及投影；关闭期间 `forServer` 拒绝重建。异常释放继续尝试其他租约/条目，保留异常信息；保存标记不构成跨文件事务。
- UNKNOWN 箭由 envelope 的 quarantinedProjectiles 保留操作、会话、目标与原因；模拟、碰撞和方块效果入口都拒绝推进。会话结束不删除该证据或把箭交还原版。

活跃规则按 Revision 缓存导出，SavedData 按会话 version/structuralRevision 缓存 JSON；累计时钟更新不重新导出全部规则。未决因果、隔离箭、alias 与合法重试回执均保留。没有已定义的重试失效期限，因此七日不能直接删除其引用的历史；独立磁盘归档与增长压测仍见 G17，当前不声称内存或总存档大小有界。

### 原版包与客户端 seam（固定 .84）

#### 个人背包 SWAP 溢出预检

固定 `.84` patched source 的 `AbstractContainerMenu.doClick(int,int,ContainerInput,Player)` 在 SWAP 分支中，若来源数量超过目标槽上限且目标非空，先 `source.split` / `target.setByPlayer` / `target.onTake`，再 `Inventory.add(ItemStack)`；失败调用 `player.drop`。`Inventory.add(int,ItemStack)` 对已损坏物品只找主背包空槽，普通物品可合并主背包及副手同组件堆栈，再找主背包空槽；创造模式可能直接清除放不下的余量。

`VanillaInputPolicy.mayClick` 在现有 `ServerGamePacketListenerImpl.handleContainerClick(ServerboundContainerClickPacket)` 的线程检查后、原版执行前预检；根据来源拆分后余量和目标替换后的服务端库存计算完整容量，不修改物品。普通不溢出交换继续走原版。容量不足沿现有取消路径同步权威库存与热栏，不调用装备回调或掉落。该契约仅覆盖原版个人菜单，不保证其他模组装备回调无额外副作用。

`development_attack` 的已授权连接 fixture 调用 `RepairGameTests.inventorySwap`，覆盖热栏/副手来源、满包、空槽、损坏装备、创造模式、来源拆分后合并、副手合并、空副手反例、正常交换和无 ItemEntity；finally 恢复库存。`repair_values` 通过隔离 SavedData 的生产构造器验证拒绝安装、禁止 START、保留 JSON、合法安装与隔离箭；测试入口限 GameTestServer 服务器线程且不注册服务。RESULT_SYNC 两个客户端入口共用工厂，协议 12 布局不变，非零规则版本与游标独立编解码。

服务端包注入位于 `PacketUtils.ensureRunningOnSameThread(Packet,PacketListener,ServerLevel)V` 调用后，而非 HEAD；已对照 patched 字节码。移动仍经原版 collision 链，拒绝时只发权威位置纠正。`handlePlayerAction/UseItem/UseItemOn` 拒绝副作用后仍执行 sequence ACK、加载范围内方块纠正及容器同步。热栏、个人装备/背包、THROW、外部容器、配方、菜单按钮、创造槽及飞行能力统一经过 `VanillaInputPolicy`；聊天、保活、传送 ACK 和容器关闭维持原版。

客户端 `MultiPlayerGameMode` 的挖掘、使用、攻击入口减少非法预测；`ClientLevel.tickNonPassenger/tickPassenger` 单独门控本地乘客及受跟踪的远端实体，保留无效乘坐关系清理及逐乘客遍历。暂停时仅为远端实体保留 `.84` 的 `getInterpolation().interpolate()` 原版网络位置收敛，并更新旧渲染姿态；不推进 gravity/travel/body timers，不用自定义 setPos 演出。真实双客户端的插值收敛、骑乘组合与预测纠正仍须运行验收，不能由 Mixin 编译或 UI smoke 推导。

对应回归入口：`repair_values`、`player_only_start`、`movement_lease_exit`、`scheduled_tick_hold`；实际运行证据见任务记录，真实联机缺口见 G27 及合并修复验收项。

`ServerCombatService` 按服务器实例持有 `CombatEngine`；成员、阶段和资源只能由 engine 裁决。区域索引、移动租约和同步游标是执行状态或投影。命令与网络均在服务器线程调用服务；`MinecraftCombatRuntime` 桥接事件，debug-local controller 不能发放正式行动许可。

| 入口 | 职责与取消语义 | 验证场景 |
| --- | --- | --- |
| `ServerStartedEvent`、`ServerStoppingEvent`、`ServerStoppedEvent` | 创建服务；停止时先把运行中会话的纯值状态标记待保存，再释放会话控制并交还 held ticks，最后释放服务器引用；不取消世界生命周期。`setDirty` 不证明磁盘已落盘。 | 启停、正常磁盘保存/重启、重复清理、计划 tick 交还。 |
| `RegisterCommandsEvent` / `CombatCommands` / `TacticalCommands` | `/dndturn local` 为管理员时间调试；`/dndturn tactical` 调用统一服务。status/stop 可按会话 ID 由控制台执行。 | `encounter_service`，管理员与普通玩家能力隔离。 |
| `LivingDeathEvent`、`LivingEntity.dead`、`ServerTickEvent.Post`、logout、dimension change、`EntityLeaveLevelEvent` | 死亡事件为正式与 local 控制分别登记待确认 UUID；受伤结束和服务器 tick 后以最终 dead 标记确认再释放。实体离开世界也释放 local 控制；取消死亡并恢复实体不会当场退会。 | `canceled_death`；真实死亡/卸载/掉线/换维度及最后成员退出见 A02/A04。 |
| `RegisterKeyMappingsEvent`、`RegisterGuiLayersEvent`、`CombatIntent` | 客户端发意图；连接决定主体，服务器核验会话、世代、版本与授权。 | 实际按键、非法请求、重试及退出。 |

每世界配置文件为 `dndturn-server.properties`，服务器启动时读取。正常入口不使用旧开发开关；`tacticalKnockbackEnabled` 默认 false。开发预设与规则说明见 [游戏规则对应章节](../03_PLAYER_RULES.md)。修改配置后重启服务器。

正常键位见玩家规则，所有操作仍由服务端授权。建场使用统一同意流程与 Zombie 选择，重叠区域进入安全合并调度。

## 几何采样与路径探针

| 固定版本方法 | 位置与职责 | 验证场景 |
| --- | --- | --- |
| `Level.getEntities(@Nullable Entity, AABB, Predicate<? super Entity>): List<Entity>` | 已加载实体索引按包围盒相交查询，含 NeoForge dragon part 分支。`MinecraftRegionSampler.capture` 再按真实中心筛选、去重，只传 UUID/坐标进入 common。 | `region_sampler`：中心位置、锚点上限；未加载与多锚点边界。 |
| `Entity.getBoundingBox(): AABB`；`ResourceKey.identifier(): Identifier` | 捕获 `getCenter()` 和维度字符串；锚点与后续实体移动分离。 | 同一方块内实体与方块中心的不同归属。 |
| `CollisionGetter.noCollision(@Nullable Entity, AABB): boolean` | `MinecraftCellProbe` 同步读取占位、脚底薄层支撑与分段边扫掠，不执行移动。 | `cell_probe`：世界阻挡改变使路径提案失效。 |
| `LevelReader.hasChunkAt(BlockPos): boolean`；`EntityDimensions.makeBoundingBox(double,double,double): AABB`；`Entity.maxUpStep(): float` | 探针核验已加载区域、当前姿态尺寸和台阶能力。live probe 不得充当异步不可变世界快照。 | 加载边界、足迹、姿态/台阶变化。 |

采样在服务器线程执行，无取消或 Mixin。调用者提供 discovery、radius、区域版本、区块/锚点上限；超限或未加载显式拒绝，不截断实体，不主动加载世界。

## 模拟与队列

实体按碰撞包围盒中心，方块按自身中心查询同一 `EncounterRegion`；chunk 只作候选筛选。debug encounter 的环境阶段通常不自动执行世界步骤；待合并的开发会话在捕获入口前可以自动推进，提交或取消计划后停止此临时推进。prototype 会话由 `beforeLevelTick` 预约。身体、玩家网络移动和环境通道分别授权。

| 固定版本入口 | 注入位置、职责与取消语义 | 验证场景 |
| --- | --- | --- |
| `ServerLevel.tick(BooleanSupplier)` | HEAD 先检查待合并会话的目标环境入口，再捕获计划 tick 和预约环境步骤；两个 `LevelTicks.tick(long,int,BiConsumer)` 调用前按 ordinal 0/1 分别接管 block/fluid 队列；正常 TAIL 才提交，成功后再清除活动步骤 ID。提交前异常仍可由外层按原 ID 归因。全局冻结、debug world 或区域区块不具备模拟资格时不预约。 | `merge_world_boundary`、`prototype_environment_loop`；冻结/未加载预算不减、异常注入见 E02。 |
| `MinecraftServer.tickChildren(BooleanSupplier)` 调用 `ServerLevel.tick(BooleanSupplier)` | 世界调用外层 Redirect 保留原版 try/catch；异常以原步骤 ID 发布 UNKNOWN 结果，不扣环境预算，再结束受影响会话并将原异常交给原版崩溃报告。已发生世界效果不回滚；规则结果虽进入 SavedData 值快照，仍须以磁盘和区块/实体跨文件证据验收。 | 异常注入、部分效果及重启对账见 E02/F04。 |
| `LevelChunkTicks.getAll/removeIf`；`LevelTicks.schedule/hasScheduledTick/clearArea/copyAreaFrom/count` | 在原版 collect/dequeue 前暂存精确区域内条目；保留 remaining、priority、subTickOrder。`schedule` HEAD 对 held/原版容器统一去重；有 held 源时 `copyAreaFrom` HEAD 捕获原版与 held 的统一源快照，按 drain 顺序复制并取消原版重复复制；环境步骤使到期项回到原版队列执行。 | `scheduled_tick_hold`：重叠复制不得由 x=1 递归生成 x=3；未验收边界见 E01。 |
| `LevelChunk.getTicksForSerialization(long)`；`unregisterTickContainerFromLevel(ServerLevel)` | 固定版 `ChunkMap.scheduleUnload` 先 `save(chunk)`，其中 `SerializableChunkData.copyOf` 同步取 tick 快照，再 `ServerLevel.unload` 调用 unregister。捕获、剩余延迟变化和释放 held 时对已加载 chunk 调用 `markUnsaved`，使 `save` 的 `tryMarkSaved` 有机会写入更新。保存方法 RETURN 合并原版与 held 的非破坏性打包视图；unregister HEAD 将 held 条目交还容器。不取消保存/卸载，也不承诺跨文件原子性。 | `scheduled_tick_hold` 验证重复序列化、待保存标记、内存 unregister/register 后继续暂停与唯一队列条目；真实磁盘卸载/重载及崩溃恢复见 E01/M5。 |
| `ServerLevel.lambda$tick$0(TickRateManager, ProfilerFiller, Entity)` | Redirect `TickRateManager.isEntityFrozen(Entity)`，位置早于 checkDespawn、tickCount 与 tickNonPassenger。保留实体管理器容器维护与原版冻结条件。 | `regional_entity_gate`：同 chunk 内外实体、停止恢复。 |
| `ServerLevel.tickPassenger(Entity,Entity)` | 有效乘坐关系在 rideTick/tickCount 前取消；无效关系仍执行 stopRiding 清理。 | 外部载具与内部乘客的不同门控。 |
| `Level.tickBlockEntities()` | 仅 Redirect ticker 条件中的 shouldTickBlocksAt(BlockPos)；保留 onLoad、pending 注册及 removed 清理。 | 内外 ticker、退出恢复与生命周期。 |
| `ServerLevel.runBlockEvents()` | 区域暂停时 shouldTickBlocksAt 为 false；原版 blockEventsToReschedule 保存事件到下一次执行。 | 内外活塞及恢复，不用它承担 scheduled tick 延迟账本。 |
| `ServerLevel.tickChunk(LevelChunk,int)` | 分别 Redirect block/fluid 的 randomTick(ServerLevel,BlockPos,RandomSource)；保留原版 chunk/section 采样和外部回调。暂停不保存随机采样点供重放。 | 内外方块与流体随机 tick。 |

Mixins 为 required 注入。实体列表调用位于此固定构建的 synthetic `lambda$tick$0`，升级时应重查描述符及调用链，不能以可选匹配掩盖失效。

### 合并切换边界

`CombatEngine.MergePlan` 捕获连通分量、主 domain、结构修订和目标环境轮次。任何首个环境 step 的授权都会关闭本轮入口，取消授权也不重开；仅因错过入口而续约时保留原主 domain。`ServerCombatService.beforeLevelTick` 在 `.84` 的 `ServerLevel.tick` HEAD、任何世界回调和新环境 step 之前重新采样；若投影触及额外会话，扩大连通计划并重复采样，直到闭包稳定。随后结算移动租约、检查新区域所需 chunk 的 block-ticking 资格及 held 队列容器，提交规则迁移，替换区域投影，按新范围重绑或释放 held tick，发送旧会话墓碑。若规则已提交而投影仍未完成，抛出异常阻止世界 tick 继续；此路径尚无跨崩溃恢复事务。相交区各方未同时授权环境 step 前，`RegionalScheduledTicks.releaseDue` 保持条目暂停。`merge_world_boundary` 定位真实 tick 边界、held 延迟/优先级和合并后 debug 会话不继续自动推进；`merge_discovery_expansion` 定位重采样额外触及第三会话的场景。跨崩溃投影恢复仍见 F03/F04。

## 玩家移动与 Mob 控制

| 固定版本入口 | 职责与取消语义 | 验证场景 |
| --- | --- | --- |
| `ServerGamePacketListenerImpl.handleMovePlayer(ServerboundMovePlayerPacket)` | 在线程交接后以拟议位移、位置/着地/水状态核验预算，RETURN 以实际位移结算；客户端最近按键不作为免费证据，区分待确认传送/维度切换与有来源的短期冲量记录；`isInPostImpulseGraceTime()` 仅属原版状态，不作为免费位移证据。原版经 `ServerPlayer.move(MoverType.PLAYER,Vec3)` 执行碰撞、速度和位置纠正；RETURN 观察实际位移并按 server tick 汇总。同 tick 有可观测外力与主动输入时依规则 0.9 发布零费用且说明证据。移动 lease 仅放行此移动路径。 | `movement_lease_exit`；其他强制来源、水中竖直、起跳、多包及真实客户端纠正见 M3-03/A02。 |
| `LivingEntity.knockback(double,double,double)` | HEAD 保存速度，RETURN 在 `CommonHooks.onLivingKnockBack` 可取消事件与抗性判断结束后，仅在速度实际改变时登记一条短期强制位移证据；无伤害操作上下文时保留事件身份，不推断其拥有战术行动权。此方法在固定版 Player/ServerPlayer 路径未被 override。 | 取消/抗性、迟到外力、混合输入及嵌套来源见 M3-03。 |
| `ServerPlayer.doTick()`；`ServerGamePacketListenerImpl.handlePlayerCommand(ServerboundPlayerCommandPacket)` 及其他 packet input handlers | 服务器身体保持暂停；物品、交互、挖掘和载具等已列入的输入仍拒绝。`START_FALL_FLYING` 与骑乘跳跃命令在暂停区域拒绝；步行租约不得给骑乘、鞘翅、旋转冲刺或飞行状态使用，切入这些状态释放租约。合法位置纠正仍走原版。连接维护继续执行。 | `movement_lease_exit` 覆盖飞行状态初始拒绝与同 tick 切换零收费；其他特殊位移来源、真实客户端和强制位移归因见 M3-03/A02。 |
| `LocalPlayer.tick`；`LivingEntity.tickEffects/updateUsingItem` | `BodyState` 分离 bodyPaused/movementAllowed；移动期保留本地预测 tick，仅对本地玩家抑制相应身体子系统。 | 真实客户端移动与身体计时同步，见 A02/A06。 |
| `Mob.serverAiStep()`；`EntityTickEvent.Post` | lease 下 Redirect GoalSelector.tick/tickRunningGoals 和 customServerAiStep(ServerLevel)，保留 sensing、Navigation 与 move/look/jump control；实体 tick 门控前检查下一路径节点、水体、碰撞和控制器跳跃提示，只授权有预算的下一次身体 tick，且独立核验原操作 pending、owner、阶段和会话有效性。Post 标记实际身体 tick，后续只对该 tick 观察位移；若观察成本超过事前授权，发布含位移与费用证据的 UNKNOWN，立即撤销 lease，仅在原路径仍属本租约时停止 Navigation。首次身体 tick 用于原版着地；无长期 setNoAi 或删除 Goal。 | `prototype_zombie_navigation`、`mob_unknown_revokes_lease`；其余路径变化、Goal/Brain 及释放见 M3-04/A07。 |

玩家实际位移由 MOVE 有序步骤结算，零位移不收费；预算耗尽、finish、stop/leave 调用统一结算。普通空间出界结算并释放移动 lease，但保留 Encounter 成员资格；换维度与显式离场仍退出。玩家和 Mob 执行/归因的剩余边界见 M3-03/M3-04。`movement_lease_exit` 与 `zombie_melee_reach` 分别定位退出结算和非相邻目标的反例场景。

## 战术伤害

| 固定版本入口 | 职责与取消语义 | 验证场景 |
| --- | --- | --- |
| `ServerCombatService.attackSupportedForDevelopment` | 服务端核验回合、成员、加载、场地、共同近战资格与视线；登记前捕获 Zombie 当前 target，并查询此 Encounter 内发起玩家是否已有合法攻击。首次登记消费资格，候选与正式阶段共享；按 R17 判定优势。命中后签发伤害 permit/子操作，通过 `TacticalDamageContext` 调用原版。根结果的纯值 `DamageTrace` 捕获规则修订、来源、区域版本、格挡/击退观察及攻击者主手与目标装备槽前后值；适配范围为 Player↔Zombie。 | `candidate_attack_miss`、`development_attack` 与 `zombie_melee_reach`；取消/异常分支见 M3/A 条目。 |
| `LivingEntity.hurtServer(ServerLevel,DamageSource,float)` | 使用 `dndturn:tactical`；标签绕过已由规则替代的 armor/shield/effects/resistance/enchantment 减伤与旧 cooldown。保留吸收、生命及可达副作用。 | 护甲、吸收、旧无敌帧、Player/ServerPlayer override。 |
| `LivingIncomingDamageEvent` | 仅精确 source/target 上下文设置 post-attack invulnerability 为 1；.84 `DamageContainer` 拒绝 0，hurtServer 接受后包装器清零。非上下文原型成员伤害被取消。 | 早退、取消、未知来源不能只用此事件代表完整受伤过程。 |
| `LivingEntity.getDamageAfterArmorAbsorb(DamageSource,float)` HEAD | `TacticalArmorDurabilityMixin` 在精确战术上下文调用虚方法 hurtArmor；原版 BYPASSES_ARMOR 会跳过护甲减伤及耐久，此处只桥接耐久。Player.hurtArmor 走四个护甲槽装备 hook；LivingEntity 基类为空。 | 生存玩家实际护甲耐久、不重复减伤。 |
| `ItemStack.hurtEnemy/postHurtEnemy` | service 在伤害接受后，对玩家主手执行一次武器回调；重试先查结果。 | 实际武器损耗与重复请求。 |
| `LivingEntity.applyItemBlocking(ServerLevel,DamageSource,float)`；`BlocksAttacks.hurtBlockingItem(Level,ItemStack,LivingEntity,InteractionHand,float,int)` | `.84` 原版在此依次检查举盾物品、箭矢穿透、水平朝向角与 `resolveBlockedDamage`，再经 `CommonHooks.onDamageBlock` 的可取消/覆盖事件链减伤和磨损。required HEAD Mixin 仅在精确战术上下文捕获格挡贡献/事件结果并返回零减伤；`hurtServer` 接受后、同一盾仍在原手时仅调用一次耐久方法。其他伤害继续走原版。 | `tactical_shield_wear` 的正/背面与不重复减伤；正式授权链、破盾、免疫与重复请求仍见 A03/M3-06。 |
| `LivingKnockBackEvent` / `LivingEntity.knockback` | 精确战术目标按配置取消或保留冲量。启用时包装器以一次 `move(MoverType.SELF,delta)` 碰撞位移立即响应，再恢复旧速度；不放行目标自主行动。 | 默认关闭、启用、碰撞、玩家纠正与远端显示。 |

.84 `LivingEntity.hurtServer` 在 Incoming 之前可因 invulnerability、死亡和火焰抗性返回；Player/ServerPlayer override 也有早退。`LivingEntity.die` 在设置 dead 前触发可取消事件。上下文须成对清理；事件取消、动态入口与阶段结果的待验收范围见 A02/A03/F01。

## 网络与客户端

`CombatNetwork` 注册协议版本 `12`，在原有意图序号末尾加入原型 DASH、DODGE 和 DISENGAGE，并给 IntentStatus 增加同世代已完成操作的重试标记、终态、实际位移 tick 和伤害；旧协议客户端不能混用新枚举或 payload。C2S 只传意图；S2C 分为带世代/顺序的身体标记、带会话投影序号的快照、IntentStatus 和有序 ResultNotice。合并保留规则主会话 ID，却给新投影分配晚于所有旧投影的序号；主会话保留原结果前缀与服务器发送游标，迁入成员从结果 0 接收。客户端同 ID 新投影保留结果游标，换 ID 重置游标与补发标记，迟到旧墓碑和 active 快照按序号丢弃。终态重试在活动成员检查前查不可变结果，按原 encounter ID、连接 owner、kind、target 与版本核验；离场 owner 仍能读取自己在活动或已关闭会话中的旧结果。MOVE_BEGIN 使用操作快照版本，MOVE_END 使用结束请求时观察的版本：首个 END 若在自动终态后到达，版本须落在操作开始与终态发布之间；首次合法 END 绑定该负载，后续异版本拒绝。成功 EXIT 的回执另按连接玩家、原会话 ID、操作 ID 和请求版本核验，均不授予新操作权限。`ClientCombatState` 在连接、玩家或维度变化时清理缓存，并核对排队回调的连接与维度；结果缺序请求按页补发，服务器每次最多发送 32 条并在后续 tick 继续。状态重同步若携带玩家已关闭的旧会话 ID，会先发旧会话关闭墓碑再发当前会话快照。发送方检查 `NetworkRegistry.hasChannel`；嵌入 GameTest 连接不保证 payload 送达。

## 箭矢与持久化接入边界

| 固定版本入口 | 现有职责与取消语义 | 后续验收 |
| --- | --- | --- |
| `ServerLevel.lambda$tick$0` 的实体冻结查询 | 箭矢身体 tick 前，用当前位置到 `getDeltaMovement()` 终点的连续线段查询固定场地；第一次跨域即登记环境调度域，避免高速箭两个端点均在外时穿场。此查询只分类空间归属，不证明攻击意图。 | `arrow_first_ingress`；曲线/传送及未加载边界。 |
| `AbstractArrow.stepMoveAndHit(BlockHitResult)` 调用 `applyEffectsFromBlocks(Vec3,Vec3)` 与 `handlePortal()` | required Redirect 在沿途块效果调用前，对已归会话调度的箭矢扫描其运动段附近、中心位于场地内的已加载方块。首版只允许空气且无流体；其他块/流体、无法有限扫描的运动段及带火箭矢发布 REJECTED 并结束箭矢，随后 `handlePortal` 仅在箭矢仍存活时调用。未登记或会话已结束的箭矢照常走原版。扫描采用保守方块包络，可能拒绝边缘擦过而未触及的箭；落地/noPhysics 等其他分支尚未覆盖。 | `arrow_block_rejection` 检查拒绝原因来自前置调用；跨界非固体、portal、雨水、落地与高速包络精度仍待验收。 |
| `AbstractArrow.stepMoveAndHit(BlockHitResult)`；`ProjectileImpactEvent` | 固定 `.84` patched 源码中，私有 `stepMoveAndHit` 在找出实体碰撞后先 `setPos(nextLocation)`、调用 `applyEffectsFromBlocks(initialPosition,nextLocation)`，之后才调用 `EventHooks.onProjectileImpact`。事件取消能阻止其后的原版 `hitTargetOrDeflectSelf`、点燃、伤害与箭矢销毁；**不能证明已阻止沿途方块效果**。目标成员的碰撞作为攻击证据，原型普通箭矢在事件处登记因果 ATTACK 与有限伤害许可；未授权的方块/实体撞击记录 REJECTED 并结束该箭矢。 | `arrow_tactical_impact`、`arrow_block_rejection`；仍须在沿途效果前授权或拒绝，并覆盖偏转、状态、爆炸及非生命实体。 |
| `EntityJoinLevelEvent` / `EntityLeaveLevelEvent`、`Entity#getRemovalReason()` | 新发射箭在加入时捕获 owner、源会话、武器/弹药和固有伤害；磁盘加载不伪造新的发射证据。`UNLOADED_TO_CHUNK` / `UNLOADED_WITH_PLAYER` 保留调度绑定供重载对账；销毁或换维度释放实时调度绑定，保留已登记箭的历史发射来源。待合并碰撞在箭矢销毁时以 UNKNOWN 结案，不重放。 | `arrow_block_rejection` 验证销毁后实时 domain 释放且来源保留；继续验证真正区块卸载/重载、换维度、死亡/离线 owner 和跨重启因果对账。 |
| `SavedDataType<CombatSavedData>`、`SavedDataStorage#saveAndJoin()` 与 `SavedData#setDirty()` | 全局 `dndturn:combat_state` 使用 schema 5 值记录 envelope 与 target Codec；JSON 按最多 30000 字符分段存入 `Codec.STRING.listOf()`。早期单字符串空会话及 schema 1 无活动会话可迁移；schema 1 活动会话缺失捕获配置，拒绝恢复。重采样半径/上限与战术击退开关按活动会话保存；恢复先校验再重建规则索引，旧世代许可失效，不反序列化实体或连接。独立 GameTest 存储目录实际写盘、关闭并重新打开验证 Codec 往返；`setDirty` 仍只请求后续世界保存，不与实体、区块构成事务。 | 活动会话磁盘保存/重启、两种保存先后顺序、执行中崩溃、held tick 对账及 UNKNOWN 不重放。 |

验证必须同时覆盖已完成操作重试、pending 重试、冲突负载、过期版本、缺序及生命周期。协议扩展和联机证据只在 [F05/A06](../02_GAPS_AND_CONFLICTS.md) 跟踪，不从构建成功推导客户端验收结论。

## 本轮修订的值合约与箭矢顺序

common 快照 schema 3 为活动会话保存移动/环境预算。恢复按阶段、成员资格和 cursor 检查 order；ENVIRONMENT 中仅有下一轮成员时允许空 order/cursor=0。目标 envelope schema 3 迁移旧 schema 2/common 1 的全局预算，不混入新配置。

实时 projectile domain 先解合并别名，结束后撤销并重新检查当前路径；调度变化独立标记待保存，不删除历史来源。无活动域时走原版，重新入域受调度。精确边界接管尚未完成，现有预测 seam 不能作为其证据。

命中先核验 PvP 与组件，再处理模拟域、射手成员域、目标成员域关系。跨域命中保留待决碰撞，在安全入口合并后复验目标仍在观察位置，再经单一因果攻击结算。伤害 trace 复用近战观察模型并记录历史 owner/source encounter/root。相关场景：arrow_cross_session、arrow_cross_session_reverse、arrow_first_ingress、arrow_trace_miss/zero/rejected/absorbed、combat_save_values。
