# 180556 视觉恢复：固定版本接入与验证

> 本文记录上一轮实现与验收。当前协议、ownership、模型支持边界和渲染接入由 [visual-ownership](neoforge-26.1.2.84-visual-ownership.md) 修订；下文协议 16、通用 living 覆盖与 handleAnimate 取消不再是当前契约。

变更类别：target-only。Minecraft 26.1 / NeoForge 26.1.2.84 / JDK 25.0.3；common 与其他 target 未改变。依据根目录两份 180556 调查，保留既有 QUERYING/Esc 修复。没有将参考资料的 .109 升级为运行依赖。

## 固定版本来源与接入合同

本轮读取 target 的 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`，以及 Gradle 已解析的 `neoforge-26.1.2.84-sources.jar`。核对方法、调用方与取消后行为：

| seam | 位置、职责与取消语义 |
| --- | --- |
| `EntityRenderer.createRenderState(Entity,float)` | extract 与 finalize 后调用 NeoForge `RenderStateExtensions.onUpdateEntityRenderState`。通过 `RegisterRenderStateModifiersEvent` 注册基础 renderer modifier；继承匹配覆盖 Avatar，仅注册一次。渲染读取表现值，不推进时钟。 |
| `ClientTickEvent.Post` | 客户端线程每周期推进表现副本一次。真实 pause 或 TickRateManager 非正常运行时不推进；不补跑 entity、aiStep、effect 或 ParticleEngine。原版恢复时保留表现相位，受击字段不因冻结残值重播。 |
| `ClientPacketListener.handleAnimate(ClientboundAnimatePacket)` | 注入 `ClientLevel.getEntity(int)` 调用前，位于 `PacketUtils.ensureRunningOnSameThread` 后。仅战术对象的主/副手挥手改为表现片段并取消原版客户端 swing，避免物品钩子与身体字段变化；其他动画和普通世界挥手继续原版。 |
| `handleDamageEvent` / `handleHurtAnimation` | TAIL 观察已执行原版事件，重置有限红色覆盖片段；不再产生伤害事件、声音或伤害结果。 |
| `ServerCombatService` 近战执行 | beginOperation/beginPlanStep 成功后、命中检定前向 tracking/self 发送一个原版主手动画包；未命中也播放，拒绝及账本重试不重发。真实 critical 且原版接受伤害后才发送 CRITICAL_HIT。没有调用服务端 `LivingEntity.swing`。 |
| `LivingEntity.DATA_EFFECT_PARTICLES/AMBIENCE_ID` | client-only accessor 读取同步粒子选项；按 .84 客户端 tickEffects 发射概率、位置与速度生成粒子，使用独立随机源。没有调用 MobEffectInstance.tickClient、物品使用回调或补跑粒子引擎。 |
| `Minecraft.tick` → `ClientLevel.animateTick(int,int,int)` | Redirect 替换既有调用的采样中心为已加载战术镜头位置；保持原版调用条件与 667×2 预算，不增加第二轮采样、不请求区块。客户端未加载区仍使用 ClientChunkCache 的原有空区块语义。 |
| `ServerLevel.blockEvent(BlockPos,Block,int,int)` | HEAD 仅分类普通 CHEST、精确 ChestBlockEntity、事件 1、已加载且局部暂停并未全局冻结的开合目标。直接发原版展示包，移除该位置/方块/事件在两条队列中的旧目标，再取消入队。未分类事件仍沿既有 runBlockEvents 门控保留。 |

表现支持通用 living 的 age、观察位移步态、有限挥手/受击/死亡，ItemEntity bob/spin，以及 Bat 的独立 fly/rest AnimationState 副本；不推进箭的轨迹、真实掉落物寿命或 Bat 身体。大幅位置纠正不生成步态。实体卸载、世界/连接/玩家实例变化、Encounter 重置/结束和 generation 变化清理表现状态。未知模型专属字段不通过运行实体 tick 补齐。

`EntitySimulation` 增加服务端 `ticksUsingItem`，协议从 15 提升为 **16**，客户端与服务器须匹配。服务端按变化发送投影，并覆盖 tracking 不包含的自身玩家；仅暂停主体的已确认使用进度用于 Humanoid render state，不在客户端推测完成或改变物品状态。原版使用手别/姿态继续读取同步数据。纯视觉不增加规则资源或行动许可。

普通箱子的 open/close 信号来自已发生的原版容器计数变化；此桥接不放开 ticker、活塞或陷阱箱红石。自然移除后的死亡实体不保留渲染残影。剩余范围统一记录在 G36。

## 本轮验证

构建与运行文件隔离在根 `build/180556-visual-validation/`，init script 为 `build/180556-visual-validation.gradle`。真实客户端连接独立测试服 `127.0.0.1:25577`；复用既有测试账号与测试 EULA，不改变日常世界。以下命令在 NeoForge target 目录使用 JDK 25 执行：

```powershell
.\gradlew.bat -I ../../build/180556-visual-validation.gradle clean build runGameTestServer
.\gradlew.bat -I ../../build/180556-visual-validation.gradle runServer
.\gradlew.bat -I ../../build/180556-visual-validation.gradle runClient -PdndturnControlProbe -PcontrolProbeActive
```

- `build/180556-visual-final2-tests.log`：clean build、common 测试、全部 **47** 项必需 GameTest 通过。TacticalPlanGameTests 检查真实免费开关箱、旧开盖事件清除、未分类事件仍入队、活塞/陷阱箱反例。
- `build/180556-visual-client-final.log` 和 `build/180556-visual-presentation-result.txt`：真实 .84 客户端与专服连接通过。实际 renderer modifier 与包注入生效；本地 Avatar 与远端 Zombie 挥手推进并结束，待机 age 连续，同周期重复 render extraction 不推进状态，冻结坐标/速度/tickCount/swing 字段不变，本地真实 active effect duration 不变，远端同步效果粒子 accessor 有有效数据。
- 客户端片段测试向真实客户端 handler 注入动画包，不能单凭它证明服务端攻击发送或网络重试去重；服务端发送路径由另外的实际攻击回归检查。
- `build/180556-visual-full-client.log`：增加 `-PfullVisualRegression` 后真实键鼠回调、地面查询 Esc/迟到重复回复、MoveTo、接近攻击、同请求重发、付费放置与免费交互通过。实际近战前后及重发后 `RECEIVED_SELF_SWING` 始终为 1。该运行未开启 requirePeer，日志沿用的 “peer convergence” 文案不代表本轮运行了第二客户端。
- 曾因仍运行的隔离专服持有 patched jar，导致一次 clean 失败；停止该测试进程后重新 clean build/GameTest 通过。不是源码编译失败。

未宣称双客户端全矩阵、画面质量、多 FPS、崩溃恢复或所有特殊模型通过。G36 保留这些验收项及 CAM-01 地形高度调查。
