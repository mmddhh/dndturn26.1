# NeoForge 26.1.2.84 目标行动接入

依据：target 声明与 `minecraft-patched-26.1.2.84-sources.jar`（Minecraft 26.1.2，JDK 25）。common 保持 Java 17。网络协议 15；规则 schema 5，执行 envelope schema 6，旧规则 1–4 迁移到 5，旧 envelope 4/5 迁移到 6。

## 执行契约

`TacticalNetwork.Request` 仅传 epoch、Encounter、操作 ID、初始版本及 TacticalIntent。连接决定 owner；同 ID 检查既有结果/计划后才检查版本。计划绑定完整堆栈 SHA-256（包含数量和序列化组件），此值是待验证指纹，不是客户端授权。能力选择没有世界效果。

`TacticalActions` 使用多目标 TacticalPlanner 和实时 MinecraftCellProbe 寻找可执行位置；最多 4096 搜索节点、128 搜索权重，不把搜索权重充作移动费用。按段下发 waypoint；客户端 KeyboardInput 只把该段转成普通 Input/Vec2。服务端保留原有网络移动、碰撞、位移观察和收费，另验证路径走廊。障碍/目标/堆栈变化、预算耗尽或停滞中断；不换目标、不传送、不无界加载。

## 固定版本 seam

| 类/方法 | 位置及职责 | 取消/异常语义与验证 |
| --- | --- | --- |
| ServerPlayerGameMode.useItemOn(ServerPlayer, Level, ItemStack, InteractionHand, BlockHitResult) | 保留保护与 NeoForge 事件；required Redirect 隔离 onItemUseFirst、BlockState.useItemOn、ItemStack.useOn | 免费方块能力仅进 useWithoutItem；付费物品能力不进方块自身交互。无上下文原样委托。GameTest 验证手持方块免费交互不放置、付费放置、箱子存取许可。 |
| ServerPlayerGameMode.handleBlockBreakAction / tick | START 接受后记录一次费用，读取 isDestroyingBlock/destroyPos/gameTicks/destroyProgressStart；逐服务端 post tick 累积原版进度后 STOP | ABORT 清除延迟破坏；拒绝不收费，已接受中断不退款。石头/铁镐多 tick GameTest。 |
| LivingEntity.updatingUsingItem() | private invoker，只推进当前授权物品一次；区域门控仍暂停身体 | 原版完成食物或更新弓弩蓄力；中断 stopUsingItem。苹果、弓、弩 GameTest。 |
| ServerPlayerGameMode.useItem / Player.releaseUsingItem | 发射在 scoped TacticalLaunchContext 内；EntityJoinLevelEvent 捕获 operation/owner/target/roll | scope finally 清理；投射物归属独立于发射终态。普通箭与雪球发射、弹药、重试及暂停调度 GameTest。 |
| ProjectileImpactEvent | 原有箭命中规则链复用发射检定；雪球显式完成 MISS/ZERO_DAMAGE | .84 Snowball.onHitEntity 对 Zombie 原版基础伤害为 0；烈焰人等其他目标不在本能力支持范围。跨域/错目标拒绝，不放行原版副作用。 |
| KeyboardInput / 原有网络移动门控 | 仅 current waypoint 的客户端 input；保留服务器许可 | 实际 dedicated client 验证 MoveTo 和接近后攻击；不手工补跑 move/travel。 |

所有平台执行在服务器线程。运行中取消先关闭移动/动作子步骤，再发布根终态。结束回合由客户端先取消、收到权威终态后使用新版本提交 END_TURN，避免改变原请求负载指纹。死亡/退出依赖统一生命周期入口；客户端会话失效同时清空计划。

## 本轮验证

- Common 测试覆盖计划预留/执行、费用一次、非法发布无部分提交、步骤权限、恢复/退出清理。
- GameTest：交互、食物、持续挖掘、弓、弩、雪球发射及重试，另运行原有回归。
- 真实 client + dedicated server：1280×720 / GUI 2，俯视初始镜头、WASD、中键/滚轮、UI 事件、改键、世界输入隔离；候选 MoveTo 及接近后攻击，保持 CAMERA 模式并观察移动收费。
- 更完整的失败/恢复/多客户端矩阵及不支持能力统一列于 `../02_GAPS_AND_CONFLICTS.md`，以上证据不代表所有任务书验收场景完成。

## 通用行为契约（2026-09-26）

`TacticalBehavior` 注册稳定 ID/version、标签、目标类型、成本分类和完整生命周期；`unavailable`/`canExecute` 是无副作用查询，`prepare` 在接近后运行，`start`/`tick` 在观察原版接受/完成后通过 `beginStep`、`accept`、`finishAction` 提交；`cancel` 必须幂等且不触发释放发射。攻击在合法提交收费，普通使用在首次接受收费，免费交互不收费，移动继续按原有位移证据收费。扩展实例不保存实体、ItemStack、Level；根 intent 保存行为版本、目标和完整堆栈指纹，每步重新解析对象。适配器不能在查询阶段使用世界操作。

`TacticalActions` 不再识别物品类型、决定射程或弓弩蓄力终点。`TacticalCapabilities.register(TacticalBehavior)` 是唯一扩展入口，重复 ID 拒绝；服务端 `Query → Options` 提供明确的行为及拒绝原因，客户端通用列表提交完整 intent。旧能力按钮只是选择入口，不再推断物品支持范围。主/副手来自服务端复验，近战/破坏/方块自身分支明确要求主手。UI 新行为无需添加枚举分支。

`VanillaBehaviors` 提供移动、近战、放置、破坏、方块自身、工具对方块、Consumable 自身/持续使用、普通流体桶和受约束实体物品链。`RangedBehavior` 分别注册弓/弩/雪球，沿用 `TacticalLaunchContext`、费用和独立投射物 ownership。PvP/成员/现有 AI 支持边界没有扩大。

新增固定源码证据：

- `Item.getPlayerPOVHitResult(Level, Player, ClipContext.Fluid)` 用 `calculateViewVector(getXRot(), getYRot())` 与 `blockInteractionRange()`，不是 `getViewVector`（后者 LivingEntity override 读取头部角度）。桶准备阶段瞄准所选命中点并按完全相同射线模式复验方块和面；空桶 SOURCE_ONLY，装液桶 NONE。当前/相邻影响格先检查加载、场地和保护，原版 `gameMode.useItem` 保留 RightClickItem hook。
- `Player.interactOn(Entity, InteractionHand, Vec3)` 先调用 `CommonHooks.onInteractEntity`，再 entity.interact，最后物品 interactLivingEntity；当前命名牌适配对已支持成员执行该链，不泛化其他实体副作用。
- `ServerPlayerGameMode.useItemOn` 保留 RightClickBlock 取消/UseBlock/UseItem 结果；免费分支仅 useWithoutItem，PASS 明确拒绝且不回退。非 ChestMenu 打开后关闭，不授予菜单存取权限。

启动前的有效成员请求拒绝由 `CombatEngine.rejectPlan` 构造完整结果后写账本，不预留许可、不扣费。同 ID 重试先读结果，行为 ID/版本/手别/堆栈/目标变化均为冲突。无成员或过期 epoch 不写他人的会话。schema 1–4 缺失行为绑定的历史 intent 迁移为 `dndturn:legacy_unresolved`，不根据当前物品猜测旧行为。恢复报告缺失适配器或不确定副作用，继续 UNKNOWN/释放而不重放；该策略不承诺任意崩溃下恰好执行一次。

## 本轮独立验证证据

日志位于根 `build/behavior-work/`（构建产物不提交）。JDK 25.0.3 用于 NeoForge 26.1，其他三个 target 使用 JDK 21.0.11；common 编译语言级别 17。

- 四个 target 独立 `clean build`；26.1 最后 clean/build 的 54 个 common 测试通过，随后夹具修订后的 `build runGameTestServer` 通过；使用 init script 将输出重定向到隔离目录，因原有开发进程占用 common jar，未停止用户进程。
- common 54 项单元测试通过，新增行为身份、拒绝无部分提交、拒绝持久化恢复及冲突测试。
- `runGameTestServer`：46 项 required tests 通过。新增注册扩展可发现、接近预留、接受计费、正常 tick 完成、取消与重复请求；无处理免费方块不回退，保护取消无费用，堆栈变化拒绝不重放，错误旧朝向的流体取水，食物持续使用中断，副手命名牌对实体真实消耗。原有弓、弩、雪球、放置/破坏/食饮回归通过。
- 真实 dedicated server + 两个真实 client：`client4.log`/`peer4.log`，两份 PASS 结果；服务端发现 MoveTo 与 melee，原版输入接近、实际移动费用、独立镜头及远端角色位置收敛通过。新实例 onboarding、旧按钮禁用和夹具遗留 Zombie 导致的早先失败保留日志，不计为通过；最终夹具为独立新世界、测试防护及 NoAI 测试目标。
- 双客户端只覆盖上述路径；所有行为的 UI 点击组合、复杂水中路径、模组保护组合、真实重启/崩溃仍见 G34/G16，不以服务端回归代替。

最终 GameTest 夹具修订：远距离 arena 不再使用会受测试模板旋转影响的相对 spawn；先在已强制加载的绝对位置创建，再检查 addFreshEntity 成功，避免测试实体在未加载区无法被服务端查到。强制区块加载后等待 20 tick 再开始实体测试，避免实体管理器尚未就绪；最终日志 `final-verified3.log`，46 项 required tests 通过。


## 161358 修订接入（2026-09-26）

协议提升至 15，需匹配客户端与服务端；持久化 schema 不因本次网络字段变化升级。Query 携带 generation/encounter/query、槽位、手别、单调 revision 和可选服务端确认指纹；Options 回显身份与 ItemReference、默认行为、适用能力、目标选项、费用/接近原因。重复相同查询返回已有确认，旧 revision 或冲突拒绝。Request 仍使用现有完整 intent 与根 operation 去重。

- `ServerCombatService.attackPlan` 嵌套 worldEffectDepth，ATTACK 终态后发布 PLAN 终态，再在 finally 处理死亡与离场。非计划攻击仍有自己的清理。客户端 Projection.terminal 只接受账本 PLAN 终态，携带 outcome/reason/publishedVersion。
- `ServerGamePacketListenerImpl.handleSetCarriedItem` 在线程交接后取消战术成员的旧热栏包并同步权威值；Query 执行真正选槽。个人库存与装备仍走原有授权，但计划执行时禁止整理。
- `.84 BlockPlaceContext.getClickedPos()` 依据 canBeReplaced 返回点击格或邻面格；BlockItem.place 先取得 placement state，再 placeBlock、组件及 setPlacedBy。DoubleHighBlockItem 在下格之前写上格；DoorBlock.setPlacedBy 写上半，BedBlock.setPlacedBy 写朝向头部。TacticalImpact 在 prepare 和 item-only 的 ItemStack.useOn 之前复验完整直接写入范围。
- 目前放置契约接受精确类 BlockItem/DoubleHighBlockItem/BedItem，且 block 为 Block、RotatedPillarBlock、SlabBlock、StairBlock、DoorBlock、BedBlock、DoublePlantBlock。未知 override 与 BLOCK_STATE/BLOCK_ENTITY_DATA 拒绝。工具限已核验斧/锄/铲；桶只取普通液体格或向空气/普通液体格倒入。邻接传播不在该直接写入合同内。
- `.84 CommonHooks.onPlaceItemIntoWorld` 的 EntityPlace/MultiPlace 事件在 useOn 后发生，loader 自身可回滚。保留该原版链，但不声称第三方写后事件是本模组的事前保护验证。spawn protection、mayInteract、mayUseItemAt、RightClickBlock 取消与 domain 在相应副作用之前验证。
- `.84 KeyboardHandler.keyPress(long,int,KeyEvent)` HEAD 仅拦截当前窗口、无 Screen 的分层 Esc，重复/释放保持一次按键一次层级；其余输入使用实际 KeyMapping。required 注入，不用可选匹配。

161358 验证记录见 [独立验证记录](neoforge-26.1.2.84-161358-validation.md)，剩余验收见 `../02_GAPS_AND_CONFLICTS.md` 的 G35。历史“本轮验证”段落属于原实现轮次，不代表 161358 全矩阵已通过。
