# 视觉 ownership 修复：26.1 / NeoForge 26.1.2.84

变更类别：target-only；JDK 25.0.3。网络双向协议 **17**，客户端与服务器必须匹配；没有改变存档 schema、战斗规则、资源费用、common 或其他 target。保留本轮开始前的查询取消、药水粒子、普通箱盖和镜头采样修改。

## 合约与源码证据

本轮读取 `targets/neoforge-26.1/build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`，并用同版本 patched jar 的 `javap -c -p` 核对调用 owner。NeoForge `EntityRenderersEvent.AddLayers` 使用解析的 `.84-sources.jar` 核对。下列 Mixin 均必需匹配，配置 `defaultRequire: 1`；不以编译成功代替注入验收。

| 接入 | 位置、职责及取消语义 |
| --- | --- |
| `Entity` 实例 | `PresentationIdentityMixin` 增加只读随机 UUID；区分重生、重新加载及 UUID/runtime ID 复用。不改变 UUID、位置、tick 或存档。服务端缓存还包含观察者实例与维度。 |
| `LivingEntity.startUsingItem(InteractionHand):void` | loader `onItemUseStart` 拒绝早退之后，实际 `useItem` PUTFIELD 后捕获新的瞬态使用身份；重复 start 不生成新身份。`stopUsingItem():void` TAIL 清理。无取消、无重放使用回调。战术持续使用优先用已存在 action operation ID。 |
| `ServerCombatService.attack` | beginOperation/beginPlanStep 成功之后、命中检定之前发送独立 `TacticalSwing`；不调用 `LivingEntity.swing`，不再发送战术原版挥手包。命中/未命中共同经过登记边界，账本重试与拒绝在此前返回。追踪基线不包含历史挥手。 |
| `ServerEntityProjections` | 服务端线程按 `presentationFacts` 读取成员归属、当前区域控制归属、阶段、身体暂停与使用快照。缓存仅记录追踪订阅和已发送值。start tracking 强制完整基线；实例或维度变更使值不相等。自身玩家也有基线。 |
| 玩家/Mob 位移结算 | 复用既有主动、外力/混合归因，附 operation/step/sequence 与实际水平距离。只消费 ACTIVE；FORCED/PASSIVE/MIXED/CORRECTION 不新增步态。玩家多包在原有每 tick 结算后一次发送；不按路径或距离阈值猜测行动。无新证据时衰减。 |
| `ClientTickEvent.Post` / render-state modifier | 每个正常客户端 tick 推进表现副本一次；`Minecraft.isPaused` 或 tick manager 非正常运行时停止。渲染只读，并用 `EntityRenderState.partialTick` 与第一人称同一插值。 |
| `ClientPacketListener.handleDamageEvent` / `handleHurtAnimation` | TAIL 观察原版已执行受击；不取消、不产生伤害。**不再注入/取消 handleAnimate**，普通挥手与物品钩子继续原版。 |
| `ItemInHandRenderer.renderHandsWithItems(float,PoseStack,SubmitNodeCollector,LocalPlayer,int)` | Redirect 精确 `LocalPlayer.getAttackAnim(float)` 和 `swingingArm` 读取；仅活跃战术片段替换，结束恢复原值。不修改实体字段或通用 getter。 |
| `ItemInHandRenderer.renderArmWithItem(AbstractClientPlayer,float,float,InteractionHand,float,ItemStack,float,PoseStack,SubmitNodeCollector,int)` | 精确读取使用状态/剩余时间/手别及 SwingAnimation。片段动画类型和时长来自事件捕获；渲染的 ItemStack 必须匹配使用快照。使用快照的观察时刻固定，因此该调用中使用插值取 1，避免原版 `used + partial - 1` 少算一 tick。 |
| `evaluateWhichHandsToRender(LocalPlayer)` / `selectionUsingItemWhileHoldingBowLike(LocalPlayer)`；`applyEatTransform` / `applyBrushTransform` | 仅渲染侧读取匹配快照，保留原版手部选择和动作链；不推进使用 tick、消费、粒子引擎。 |
| `HumanoidMobRenderer.extractHumanoidRenderState(LivingEntity,HumanoidRenderState,float,ItemModelResolver)` | Redirect 使用手别、使用堆栈、已用时间及 using 标志。仅明确支持的实际 renderer/model 对生效；不是所有 Humanoid 的全局接管。 |
| `AvatarRenderer.getArmPose(Avatar,ItemStack,InteractionHand)` | 精确重载中的使用手别与剩余时间来自同一快照。NeoForge 自定义 arm pose 的原有早退保留。 |
| `UseDuration.get(ItemStack,ClientLevel,ItemOwner,int)` / 静态 `useDuration(ItemStack,LivingEntity)` | HEAD 仅有匹配控制快照时返回已用/剩余时间；停止或不匹配的物品返回 0。无快照走原版。 |
| `CrossbowPull.get(ItemStack,ClientLevel,ItemOwner,int)` | 同一使用快照；已装填仍为 0，使用进度除以原版充能时长。无匹配快照走原版。 |

## 字段边界与生命周期

`ClientEntitySimulation` 是权威投影，`ClientPresentation` 只保留数值、UUID 和动画副本；`PresentationAdapters` 是明确的 renderer/model 适配表。支持 Player 的原版 AvatarRenderer/PlayerModel、精确 Zombie/ZombieRenderer/ZombieModel、精确 Bat/BatRenderer/BatModel、精确 ItemEntity/ItemEntityRenderer。Frog、Warden、替代或继承 renderer 不修改模型时间、步态、受击和挥手字段；粒子桥接单独遵守身体暂停范围。

投影需匹配 generation、实体 UUID、runtime ID、服务端实例、维度，并绑定本地实体实例。缺失基线不能创建接管权。世界/连接/玩家实例切换全清；实体卸载按身份清理并保留序号墓碑，迟到旧基线不能重新绑定复用 ID。局部 Encounter 消息不全清表现；每个实体依据自身后续权威投影更新控制归属，合并不会清掉或重放有限片段。

年龄/步态在控制归属释放后四个正常客户端 tick 交回原版。Bat 的 age 和 fly/rest 起点一起交接。挥手按捕获时长结束，独立于年龄交接；消费过的受击残值仅抑制到原版 hurtTime 为零或新受击事件。剩余字段全部释放后丢弃片段缓存；去重水位留在追踪生命周期内，不靠片段存活实现去重。

使用快照带使用 ID、手别、完整物品值摘要、已用/剩余时间，STOPPED 显式清除进度。完整摘要复用 `TacticalItems` 的 canonical 组件序列化；本轮只将其参数从 Player 放宽为 LivingEntity，不改变摘要算法。快照不匹配则保留原版读取，不将旧进度套到新物品。

## 本轮实际验证（2026-09-26）

输出隔离在 `build/visual-ownership-validation/`；init script `build/visual-ownership-validation.gradle`。复用已有专服测试目录、EULA 和测试账号权限，不发布、不升级依赖。

- 独立 target `clean build :common:test runGameTestServer`：clean/build 与 common **58** 项通过；最终完整 GameTest **46/48**，总命令失败。失败为 `tactical_behaviors` 的 fixture chunk entity registration 超时、`scheduled_tick_hold` 的退出后计划 tick 恢复断言。见 `build/visual-ownership-final-build-tests.log`。新增 `presentation_protocol` 已实际通过，覆盖协议 17 往返、实体身份复用、普通实体无控制归属、原版使用身份重复 start/stop/restart。
- 较早 GameTest 曾出现弓/弩/雪球 fixture 不可见；其夹具现在使用稳定隔离坐标、显式难度恢复与真实追踪就绪等待。最终这三项通过；这不消除上述其他失败。
- 真实客户端连隔离专服，30/60/144 FPS **配置上限**下字段回归通过，日志为 `build/visual-ownership-client-30.log`、`-60.log`、`-144.log`。没有测量或保证 GPU 实际输出恒定帧率。60 FPS 还验证真实替代 Zombie renderer 的 sentinel 字段不被覆盖。运行成功证明本轮必需客户端 Mixin 在 `.84` 注入成功。
- 最终代码再次运行 `build/visual-ownership-client-final.log` 通过，包含卸载序号墓碑拒绝迟到旧基线，以及最新使用身份 Mixin。客户端已退出；本轮专服进程已清理。
- 回归覆盖 Player/Zombie 挥手、重复片段拒绝、片段结束恢复普通挥手、四 tick 交接、两个独立投影归属、受击残值/新受击、Bat/掉落物、Frog/Warden 反例、主副手共享片段值、Humanoid 与弓弩 numeric property、换物品/停止、主动/强制/被动/混合/纠正证据、重复证据衰减、重复渲染只读和 `/tick freeze` 专服同步。扩展模型/ownership 场景使用真实客户端内的显式测试投影和可丢弃实体，不等于真实服务器完成所有会话生命周期。
- 双真实客户端连接已执行，但 `visual-ownership-dual.log` / `visual-ownership-peer2.log` 在攻击前的既有 `peer camera/actor was changed by primary` 断言失败；未将其报告为攻击命中/未命中/致死/重试双客户端通过。
- `build/visual-ownership-attack.log`：最终代码的单客户端连接专服实际攻击/重试回归通过；断言攻击前后及同请求重发后 `RECEIVED_SELF_SWING` 仍为 1。该轮还覆盖真实输入、MoveTo、放置和免费交互；日志沿用的 peer convergence 文案不代表本轮双客户端通过。
- jar 检查包含正确 metadata、Mixin 配置、common CombatEngine 和新增 PresentationState。`git diff --check` 通过。

真实暂停菜单、完整死亡重生/换维度/重连/合并、多客户端命中结果矩阵、真实使用动作目视与像素级一致性尚未完成，统一保留在 G36。既有 180556 文档的早先通过记录不是本轮复验结果。
