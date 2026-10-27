# 当前状态

同步日期：**2026-10-27**。实施起点为本地 `dev_` / `bea6126` 加进入时已有工作区；现已按原生维护、镜头、效果、Actor 和 UI 边界分别提交。保留先前记录的验证结论及限制。

本次按用户确认方案实施[角色数据合同](05_ACTOR_DATA_MODEL.md)的动态来源、读取一致性及长期能力管理。此前角色展示、原版接缝、旧版兼容清理等证据仍保留。剩余任务只在[路线](02_ROADMAP.md)维护；玩法以[合同](01_RULES_AND_CONTRACTS.md)为准。


交付复核：效果及 Actor 提交边界的独立检出分别通过相关源码编译，最终提交内容的独立检出通过 26.1 `build --offline --console plain`（含 common 测试与 UI 检查）。文档另行提交，未将编译结果扩大为真实客户端验证。下文历史实施记录中的“未提交”描述其当时状态。

## Actor 动态来源与长期能力管理

基线 `dev_` / `bea6126` 加进入时已有工作区。确认 `origin/dev-mmddhh-field` 为 HEAD 祖先；不重复合并，保留既有角色展示、药效、镜头和 UI 改动。

- AbilityDefinition 增加显式学习策略；所有构造点传入，原生执行器须显式支持长期来源。管理命令对单个已加载玩家/Mob 提供 list/grant/revoke/prepare/unprepare，复用 ActorStateAuthority、修订与回执。重复授予不叠加，重准备/重授予不复活旧绑定；内置能力不自动开放学习。
- 保留静态定义的原生字段覆盖禁令、通用定义回退、效果与过程唯一 owner。装备绑定补 Actor/实例和写入代次，玩家主手补 Inventory 接缝；数量/组件继续实时指纹比较。受审计的合法自身消耗更新当前操作的继续执行证据。
- 普通快照/事实和完整调查增加同步读取边界复验，目标查询缓存包含 Actor 修订，调查发布前核验 Encounter/成员关系。持续贡献按当前来源选择完整采样路径；不会仅因其他角色注册了持续能力而让所有查询遍历完整能力。StatResolution 与数值计算共用运算累积，缺基准仍未知。
- 当前格式保存恢复校验学习定义与准备语义，无迁移、旧签名或旧许可恢复。已发生世界效果、UNKNOWN 和费用边界不变。

未新增测试文件。扩展 TacticalEffectStateTest 保护学习/准备/实例失效、旧绑定不可复活与缺基准；扩展 TacticalEffectChecks 保护管理员权限、玩家/Mob 命令到真实防御读取、重复授予、同值换装和原位组件变化。这些原生入口/命令接缝不能由纯规则层证明。MobRangedApproachChecks 的旧夹具补当前合同要求的定向目标和注入读取端口，费用/生成/释放断言保持原义。

实际执行：26.1 JDK25/common Java17 的 `build --offline --console plain` 通过，包含 common 测试；单独运行 `runGameTestServer` 的 `tactical_effect_state`、`default_interactions`、`mob_standardization_revoked`、`mob_standardization_approach`、`combat_save_values` 通过。前两类证明角色/装备及原生物品消耗，后两类分别证明接近中撤销保留攻击动作且不发箭、正常接近后射击并释放控制。Fabric 1.20.1、Forge 1.20.1、NeoForge 1.21.1 串行 `compileJava --offline --console plain` 通过。

独立 `build/actor-refactor-server` 普通专服两次启动（测试扩展仅在测试 classpath）：第一进程向已加载牛授予并准备可学习持续防御，save-all flush 后正常 stop；第二进程读取相同 Actor/grant 与准备状态，再撤销确认归零并正常保存停止。使用独立新世界及夹具加载区块，未写用户存档；这是长期状态跨进程证据，不代表全部活动能力续战。首启空 flat generator 配置产生原版缺 layers 提示，服务器仍正常生成/启动。

失败与修正：命令最初用 word 参数不接受命名空间冒号，改为 IdentifierArgument；持续能力注册使旧全局完整采样路径触发预算，已改为当前来源判断，并把效果夹具的密集结算移到下一 tick，未提高预算。Mob 接近夹具先因无定向目标未入场，再因缺读取端口失败，均修正后重跑；未放宽断言。临时诊断开关已移除。

`git diff --check`、活动文档本地链接、Mixin JSON 和发布 jar 测试资源隔离检查通过。未运行真实双客户端、实际客户端键鼠、真实第三方内容、全部活动过程的第二进程恢复。写入代次不保证识别第三方绕过受控入口并恢复同值的修改；不把采样 tick 当原生统一修订。不新增可学习玩法内容。

## Buff 通用接入：已实现切片，完整方案未完成

基线 `dev_` / `bea6126` 加进入时全部未提交工作；已确认 `9c05747` 为 HEAD 祖先。远端 `origin/dev-mmddhh-field` 还包含其他后续提交，未将整个分支重复合并。保留角色数据、镜头及实施中新增的用户 UI 工作，不自动提交或发布。

现有 VanillaEffectRoundController 托管所有参战持续药效的回合期限与隐藏链；移除跨端固定命名空间白名单。平台 EffectAdapters 注册声明语义身份、匹配、显式覆盖及当前已支持的计时行为，load-complete 冻结并拒绝冲突。基础行为通过回调覆写分类，不试执行未知回调；未知效果保留原生属性和回合期限，周期／开始／受伤／移除特殊回调暂停。检查点每个 timer 保存适配 ID 和语义版本，恢复不匹配拒绝，不添加旧格式补值或迁移。

再生、中毒、凋零、吸收沿原 owner；新增饥饿有效时长累计、持续饱和及持续瞬时治疗／伤害折算。一次性治疗／伤害／饱和通过行动或喷溅影响操作的有界 ApplicationScope、NATIVE_EFFECT 子操作及有限许可执行；原生治疗／伤害保留，未加 d20 或再次扣费。喷溅仅解除这三种已知瞬时类型的拒绝，未知瞬时类型仍拒绝。食饮已通过真实原版使用回调验证；喷溅入口已接但尚无本次真实命中证据。Creeper 药效云仍拒绝瞬时类型，未冒称云授权完成。到期事件内刷新时间／等级后不删除新状态。效果页沿既有纯值投影、分页与语言资源显示原生期限、剩余回合和服务端支持状态；客户端仅按服务端成员事实暂停全部药效，不按本地类型猜测。

未新增测试文件。确认 VanillaEffectsChecks 与已注册 ParticipantEffectsChecks 的 HEAD 内容仅类名不同后，移除未注册重复副本，将新增断言放入实际注册夹具。新增非 minecraft 基础／特殊药效、未知周期暂停、饥饿累计、查询支持状态、冻结后注册拒绝、适配语义不匹配拒绝、真实饮用治疗 4 点／伤害 6 点、空瓶与动作消费、同 ID 重试不重复效果；这些是原断言缺失的原生接缝。ParticipantEffectTest 补无许可拒绝及 NATIVE_EFFECT 单次许可耗尽；原有 END_TURN 目标限制仍保留。原夹具增加两次真实饮用后将执行窗口从 360 增至 600 tick，未放宽数值或授权断言。

实际执行：JDK25、common Java17、target Gradle 9.8 的 `build --offline --console plain` 通过；`participant_effects`、`participant_fire`、`default_interactions`、`tactical_effect_state`、`creeper_effect` 实际运行通过。日志在 target 的 `build/buff-*.log`。初轮曾用根 Gradle 9.7.1 编译并误用不存在的 runtime getter，随后改为 target wrapper 与既有 Facts 注入；架构检查拒绝 actor 直接读 runtime 后未放宽规则。夹具初次误用 FoodData 不存在的 getter，改为测试内读取原生 exhaustion 字段。瞬时子操作曾在许可签发前捕获修订导致拒绝，已改为签发后采样并通过实际饮用。共享工作区并行 UI 改动曾短暂出现 ConsentOverlay 方法不匹配，未改动它们，后续构建恢复通过。

火焰夹具原先错误期待中立牛自动参战，改为开场前明确敌对，保留伤害／时间断言；先在开场后加入导致资格不同，已纠正顺序。等待断言随后间歇出现生命再减 1.5、火焰期限归零，补观测确认是第二次完整结算；干燥屋顶并未消除该失败。单等 ACTIVE 不会主动开战，初次直接攻击又受当前先攻限制；最终先在合法玩家回合真实攻击激活，等待实际牛回合再初始化火焰与生命，避免候选场激活重排引发的额外合法回合，保留全部伤害／思考等待／退出期限断言。最终 `build` 与 `participant_fire` 通过，日志 `build/buff-fire-final.log`。

仍未完成：虫蚀／渗浆／盘丝／蓄风的有界世界触发与逐项证据；不祥／袭击之兆时钟与袭击结果观察；药效云瞬时授权；面向第三方自定义特殊触发的完整受限执行端口；按整个原版注册表逐项消费者核对。当前这些原版特殊效果会显示需适配，不能据通用计时称原版 Buff 全覆盖。未执行本次真实客户端显示／清除／重连、双客户端、喷溅距离与亡灵反转实机、真实第三方模组、第二进程活动保存恢复及其他 Minecraft 目标。当前格式 JSON/同进程恢复夹具不等于第二进程续战。

最终 `build` 和 `participant_effects` 再次通过（`build/buff-final.log`）；`git diff --check`、语言 JSON 解析、所改活动文档 UTF-8 检查、发布 jar 无 GameTest/Checks/AutoJS 条目检查通过。没有将无窗口 UI 检查记录为真实客户端验证。

## 回合制 UI 常驻复用与原版界面隐藏

基线 `dev_` / `bea6126` 加进入时未提交工作区；再次确认 `origin/dev-mmddhh-field` 已为 HEAD 祖先。保留并行的规则、效果、角色数据及镜头修改。本次只调整客户端 UI 生命周期与投影刷新。

- 客户端资源就绪后（标题页亦可）预建战术与同意文档、固定面板/热栏/背包槽和静态 Canvas 图标，并预留小批能力、菜单、先攻及同意名单节点。动态列表按实际载荷需求扩容并复用；不预建协议最大名单，不把未知实体模型烘焙成静态头像。
- 换 Encounter、死亡、断线/换世界时保留 UI 骨架，清空角色/选项绑定、调查请求、物品展示、焦点/按压/选区和滚动状态。动态按钮回调只读取当前绑定；重绑定取消旧手势。资源重载结束后显式刷新文档并按新 generation 重新绑定，常规开关不重建节点。
- 完整战术投影从普通绘制路径移出；绘制前仅补重载/重新显示后的待更新状态及必要布局提交。布局按视口、面板、菜单锚点与列表结构变化刷新；先攻投影按快照/选择变化刷新，调查内容按请求/回复/语言变化缓存。保留库存比较、文本/属性差量写入和资源圆环按值重绘。实体头像和世界路径仍按需要动态绘制，不宣称 GPU 最终画面启动时永久烘焙。
- 按本次用户指令，任何原版 Screen（包括聊天、Esc、背包、箱子）打开时隐藏全部战术面板、同意窗口、头像及世界预览，并拒绝其输入；关闭 Screen 后复用原节点。原版 HUD 不再因后台仍有 Encounter 而被战术热栏隐藏门控误取消。

实际执行 JDK25/common Java17 的 `build --offline --console plain` 通过，包含现有 UI/输入/调试检查；common 测试 UP-TO-DATE。未新增测试文件或阶段框架。独立客户端测试目录先使用旧世界副本，遇到当前工作区效果检查点格式拒绝恢复，保留副本并改用全新原版测试世界，不修改原存档或添加迁移。创建世界的脚本 worldgen 回调曾阻塞，停止该测试进程后改由原版创建世界界面完成。

真实客户端后台验证标题页预建且隐藏、进入/退出/再次参战的文档与节点身份、能力换槽、右键菜单复用、调查内容、资源重载不重复绑定、重载后一次结束回合、GUI 缩放后命中一致及静止视觉修订不变。Esc/聊天/背包走 GLFW 键盘回调，箱子通过服务端打开原版容器；检查隐藏时绘制/输入门控并查看截图。同意窗口使用客户端请求投影夹具，仅证明隐藏/恢复/解绑，不冒充真实多人同意。后台鼠标回调没有完成点击验证，改用 AUI 实际坐标事件验证换槽/结束回合；重载后失焦时点击被正确拒绝，恢复测试焦点后通过。探针最初用了不存在的 Java.type/resizeDisplay API，分别改用 Java.loadClass/resizeGui；这些失败不计作通过。

最终编译版本另启客户端，复核标题页隐藏、进入新测试存档后的原节点复用、PauseScreen 隐藏/恢复，以及实际断线后清空会话且保留文档；断线检查通过后正常停止客户端。`git diff --check` 通过。

未运行前台实体鼠标完整操作、双客户端、真实第三方内容、全量名单压力或整客户端性能采样；不承诺零运行时分配、绝对零布局或量化帧率收益。当前保存/规则/网络所有权未因 UI 复用改变。

## 同类原版维护遗漏检查

沿当前 26.1 移动/载具、交互/库存、实体暂停、玩家 tick、追踪与网络生命周期检查提前返回路径，保留进入时用户工作区。载具移动原入口只取消，没有返回原版纠正包或维护追踪；现经 MovementInputHooks 返回受控载具当前位置并刷新原版追踪，不执行位移、不开放载具战术玩法，NaN/非法角度仍交给原版断开路径。被拒实体交互补发既有库存/手持槽同步，避免客户端预测物品状态残留。

战术 Action Request、CombatIntent、ConsentReply 在连接/玩家实例复验后更新原版最后活动时间；查询仍走只读路径，不以自动发现/调查保活，不改 Encounter 挂机跳过时钟。删除 ServerPlayer.doTick 的失效整体取消入口，保留实际身体步骤观察：当前普通玩家本就不被 SimulationPolicy 暂停，因此这项是清除阻断健康/经验/地图等维护的旧分支，不声明已复现生命同步故障。

未新增测试文件。扩展既有 CursorFacingChecks，保护拒绝载具移动不改变双方位置且返回纠正、拒绝交互不执行骑乘且返回库存同步；原断言只覆盖玩家 Pos/Rot 和传送确认，无法证明这两个原生预测接缝。`runGameTestServer -PgameTestSelection=dndturn:movement_lease_exit --offline --console plain` 实际通过。首轮新增骑乘夹具下船后未确认原版传送，干扰后续旧断言，已按实际纠正包 ID 补确认，未放宽断言。

JDK25/common Java17 的 `build --offline --console plain` 通过（common 测试 UP-TO-DATE）。真实客户端读取当前世界独立副本，两只原 UUID 僵尸正常同步；通过实际自定义载荷验证调查不改最后活动时间、明确同意回复更新最后活动时间，玩家位置不变。第一次探针使用该目标不存在的 PacketDistributor.sendToServer，改用原版 ServerboundCustomPayloadPacket 发送后验证通过。客户端正常关闭，原存档未写入。`git diff --check` 通过。未运行双客户端、真实载具键鼠预测、实际挂机踢出倒计时及其他 Minecraft 目标；不宣称全部生命周期组合已穷尽。

## 当前存档重进后僵尸不可见

基线 `dev_` / `bea6126` 加进入时未提交工作区；已再次确认 field 源分支为 HEAD 祖先。只读检查当前 `run/saves/New World` 的原版实体区域文件及战斗日志，两只参战僵尸 UUID `f6fafbf8-0dcf-4b3b-a5b6-00fdad775439`、`b96d1da1-1278-4eed-9682-26cf51687523` 均仍在存档，生命约 7.06 / 17，位置仍在玩家附近。

在 `build/zombie-save-check` 独立副本实测复现：服务端两实体存在、所在 chunk 已向玩家发送，但原版实体追踪为 false，客户端没有这两实体。静止战术玩家的移动包在门控处提前返回，跳过原版 `ChunkMap.move` 中重试实体配对的维护；重进时实体先于 chunk 发送加载，双方静止便不能补建追踪。单独调用原版追踪维护即恢复客户端实体，未移动身体或改存档。

`MovementInputHooks.refreshTracking` 在已加载客户端的被拒移动包路径保留原版追踪维护，只读取服务端当前位置；Mixin 仍拒绝未授权位移，既有旋转/纠正语义不变。没有新增保存字段、迁移或实体重建，没有修改原存档。

实际执行：JDK25 / common Java17 的 `build --offline --console plain` 通过（common 测试为 UP-TO-DATE）；`runGameTestServer -PgameTestSelection=dndturn:movement_lease_exit --offline --console plain` 实际运行并通过；`git diff --check` 通过。未新增测试文件或断言，复用原移动门控检查并以真实客户端验证原版配对接缝。修复后的新客户端进程读取副本，两只僵尸自动进入客户端，UUID/位置/生命及玩家位置保持一致，并检查游戏截图；同一客户端保存退出后再进入，复核两实体仍在。测试客户端正常退出。调试过程中重复打开已加载副本产生锁占用提示、一次停客户端脚本相对路径错误，均已纠正，不计作通过证据。未运行双客户端、其他目标或完整长期续战；本次修复不代表全部保存/恢复边界完成。

## 角色数据：完整读取、来源与分页展示

基线 `dev_` / `bea6126` 加当前工作区；已核对 field 源分支在目标历史内，不重复合并。CameraRig、CutawayMask、TerrainCutaway、MovementPreviewChecks 及其文档记录来自并行的用户工作，保留未覆盖，不归入角色数据交付。

- ActorDefinition 明确规则分类/体型/标签及资源描述（单位、容量事实引用、刷新规则引用）；调用点显式提供元数据，未保留旧签名补值入口。缺少定义的分类、容量或刷新保持未知，不生成六维、专注、完整抗性或新资源机制。AI 策略沿独立 AiDefinitions 绑定。
- `ActorViews.CharacterSheet` 组合已有 ActorSnapshot、ActorPersistentState、NativeActorObservation、StatResolution 和行为定义。原生观测携带同次 actor/instance/capture、生命/最大生命/吸收、位置/朝向/尺寸/姿态、身体标记、控制类型、实际装备及玩家背包摘要。Mob 未提供原生背包接口时明确 UNSUPPORTED，不伪装玩家库存；只读取，不创建第二份可裁决状态。
- 原生属性按注册表有界发现，不限护甲/韧性或内置物种；保留 base/effective、modifier 操作、装备与药效声明匹配，无法唯一归因保持未知。每角色最多检查 512 个注册项、捕获 128 个属性，每属性 64 个 modifier、256 次来源声明访问，共享 2048 次声明访问预算；超限明确标记，保留原生有效值。装备最多 16 槽，显式背包摘要最多 64 槽，不序列化全部物品组件，也不进入普通能力发现路径。
- 战术派生保留效果实例/层数与持续能力原 binding/grant；与 ActorCompilation 共用最终计算语义。持续来源撤销不保留旧贡献，同一 binding 重复输入不重复计入。执行防御沿统一 captureFacts 编译链。能力页区分当前绑定、费用接受边界、装备/效果/长期来源和未准备/定义不可用；资源页分开原生生命、Actor 余额和 Encounter 动作/反应/移动，不擅自推断上限。
- 现有调查面板接入概览、属性、装备、背包、效果、能力、资源、参战八类分页；点击分类按钮切换，左右按钮翻页，刷新重新采样。参战页读取 Encounter 的先攻、资格、动作欠账、轮转、敌对、field/玩家锚点、环境预算及已有 owner 的当前实例活动过程。没有另建 Combatant 或过程 owner。
- 完整详情只向本人公开；他人仍走现有公开策略。请求携带分类/页码，保留世代、遭遇、目标与请求身份，过期回复不能覆盖新页。页面至多 24 行，并按 UTF-8 字节预算分割；回复仍受 32 字段/16 KiB 硬上限。纯事实仍为标量，解释作为独立纯值，不写入存档；不维护旧查询格式兼容、迁移或协议版本。

未新增测试文件。RepairRegressionTest 保护非本人拒绝、完整分页、不重复条目、截断与旧快照不变性；TacticalEffectStateTest 验证效果/持续能力来源、计算一致与同 binding 去重；TacticalEffectChecks 复用原生 Actor 状态夹具验证装备替换、药效与未知来源、预算不改数值、玩家生命/吸收/背包、Mob 未支持库存、八类页面逐页协议往返。新增断言只覆盖原断言缺失的长期数据/公开边界；原生装备、药效与载荷编码不能由纯标量层证明。

实际执行：26.1 JDK25 + common Java17 的 `build --offline --console plain` 通过，包含 common 测试、GameTest/UI 编译与既有 check；`runGameTestServer -PgameTestSelection=dndturn:tactical_effect_state --offline --console plain` 实际运行并通过。玩家夹具首轮因未设 MAX_ABSORPTION 而被原生钳制为 0，已设置合法上限后重跑，未放宽采样断言。前一切片曾把解释当成 FactKey 值导致初始化失败，最终保留标量边界并移除旧防御专用解释入口。另串行执行 Fabric 1.20.1、Forge 1.20.1、NeoForge 1.21.1 `compileJava --offline --console plain` 均通过。最终检查及未运行边界见下段。

最终 `build --offline --console plain`、`git diff --check`、所改活动文档本地链接、语言 JSON 及角色字段标签完整性检查通过。未运行真实客户端键鼠/分页布局、双客户端、真实第三方内容和第二进程恢复；既有无窗口 UI 检查不证明新面板的真实显示。当前保存格式及 owner 生命周期未改变。未确认玩法和超预算/未知第三方来源仍按合同明确保留未知，不声称任意模组可自动精确归因。

## 已有证据：移除本模组版本间兼容

基线 `dev_` / `a945183` 加进入时工作区（已有暂存日志删除保持不动）。重新核对共同祖先为 `9c05747ed7a474a2c95264ddf2758fa6a11dc6c9`，field 修复已包含，不重复合并。扫描 common、四个 target 的源码/资源/构建入口及有效文档，按调用点区分本模组版本兼容与当前第三方/平台适配；未修改历史归档或用户世界存档。

- 删除 Actor 旧容量故障修复的候选、对账记录、加载后自动解除故障与 tick 检查队列；删除其持久化字段和旧世界修复测试入口。当前历史压缩、精确区间、去重、故障隔离继续保留。
- 删除 `simulationArchive`、意图 parameters、来源 dependencies、能力 executionKind/parameters 缺失时的补值兼容。Actor 顶层、战斗 envelope/rules 和捕获设置要求当前字段，额外旧字段也拒绝；不维护 schema/protocol 格式版本。删除缺 processes 的 envelope 旧构造入口，调用点显式传当前字段。
- 删除网络 `MOVE_BEGIN` / `MOVE_END` / `ATTACK` 旧意图与旧编号保留规则；当前动作编号重新连续排列，UI 走当前战术计划。删除旧移动 END 的独立回执、存储字段、导出/恢复/清理及绕过版本比较的重试分支；当前计划移动和租约证据保留。
- 删除 `MeleeAdapters.Receiver` 及注册/查询别名、`MinecraftCellProbe` 固定 Bounds 废弃入口、无证据的旧感知构造入口，并更新现有调用/夹具。同步近战介入、当前无观察世代的纯规则值、其他 target 的独立预算、语义版本及第三方适配是当前功能，不作为旧档迁移删除。

约束已写入根 AGENTS 与有效合同：用户明确记录 MOD 发布之前，不维护本模组旧存档、旧配置、旧协议或旧 API 兼容，不以“稳定了”作为自动恢复条件。当前格式活动保存/恢复仍是 P4 目标；P7 的兼容仅指第三方内容适配。

未新增测试文件。删除仅验证旧容量故障自动修复的断言；既有 ActorHistoryTest/ActorHistoryChecks 继续保护压缩、去重、恢复和故障保留。复用 PersistencePolicyChecks 检查当前格式往返、缺失/多余字段拒绝且原文不改；原覆盖没有 Actor 缺 simulationArchive 的检查，必须在实际 Gson 持久化接缝验证，纯规则构造不能证明 JSON 缺字段行为。

本次 JDK25 + JDK17 toolchain、26.1 Gradle 9.8 执行 `build --offline` 通过，包含 common 测试、生产/GameTest/UI 编译及既有 check。`runGameTestServer` 分别选择 `combat_save_values`、`actor_history`、`presentation_protocol`、`repair_values` 均实际运行并通过。最初从根 wrapper 使用 9.7.1 后改用 target 的 9.8；编译曾指出 UI 两处旧签名和旧回执引用未移除，已修正。两次编辑脚本分别因相对路径和默认编码未生效，修正后重跑；存档检查曾因通用字段数错误遮蔽具体缺字段原因失败，调整检查顺序后通过。逗号组合选择实际只运行首项，其余已单独补跑，不把组合参数当作全部运行证据。

另串行执行 Fabric 1.20.1、Forge 1.20.1、NeoForge 1.21.1 的 `compileJava --offline` 均通过。`git diff --check` 通过，修改文档的本地文件链接有效。

未运行真实双客户端、普通专服第二进程续战、第三方内容和性能检查；本次原生夹具不能替代这些验证。旧版存档不会转换为新格式，旧客户端/服务端/API 不提供混用保证。

## 基线与阶段

`git merge-base HEAD origin/dev-mmddhh-field` 返回 `9c05747ed7a474a2c95264ddf2758fa6a11dc6c9`，field 修复已在当前历史内，P0 无需重复合并。`main` 不是本次工作分支；不切分支、不覆盖现场、不自动提交或发布。

活动文档已安装，旧文档位于 `docs/legacy/` 及 `pre-refresh-20261008/`，旧根文件保存在 `_root/`。P1 文档整理已有落点；测试精简仍须按独有不变量逐项判断，不能仅凭归档存在就关闭整个 P1。

当前实际主目标为 Minecraft 26.1.2 / NeoForge 26.1.2.122 / JDK25 / ApricityUI 1.2.8（目录名仍为 neoforge-26.1）。common 使用 Java17；其他三个 target 只维护构建边界。

## 已接入的生产链

| 领域 | 当前实现 | 未关闭边界 |
| --- | --- | --- |
| 规则权威 | Encounter 拥有成员、敌对、先攻、回合资源和规范结果；Actor 拥有长期/运行/效果状态 | 不把读取缓存或平台 coordinator 作为第二权威 |
| 行动 | 选择、只读发现、规范计划、接近、复验、原生执行、观察、取消/释放和操作去重 | 特殊效果与全部输入/生命周期组合仍需核对 |
| field 与介入 | 玩家锚点、35 半径、边界跟随；介入首击即时战术结算，下一次自身回合扣动作 | 已验证后台双客户端首击/续战；更广能力与负载组合属于后续阶段 |
| Mob 关系 | 读取 `setTarget` 提交后的实际目标；任一方已参战时可接纳另一 Mob，同场更新敌对、跨场请求安全合并 | 两者均未参战不建场；未参战玩家不能被索敌绕过同意；已通过专门 Mob 对 Mob 非重叠场地因果合并场景；后台双客户端已验证同意建场与介入 |
| Mob 行动 | `PVE_LIVING_MEMBER` 与敌对事实接通发现、通用/内置 AI、普通箭和天生近战；拒绝自攻/PvP及中立 Mob 对 Mob | 扩展声明的旧仅玩家策略保持原义；特色能力、友军/区域候选不自动兼容 |
| EXIT | common 权威距离判定、捕获/保存 `escapeDistance`、稳定拒绝原因和重试、回执、客户端距离/阈值投影 | 迟到 EXIT 回执按当前 operation 过滤；后台双客户端已验证回合/距离拒绝、合法退出和旧投影拒绝，前台连续键鼠体验未完成 |
| 原版操作 | 通用物品/方块后备；放置、工具、食饮、装备、桶/瓶/刷子/钓鱼、交互和部分投掷路径 | 开菜单不授权配方/交易；通用 use/useOn 不证明任意间接效果均受控 |
| 环境 | 环境参与者、有效步、scheduled block/fluid tick、block event、BE、TNT、漏斗等既有托管 | 所有时间输入、重锚/跨域/卸载/合并组合及真实负载未穷尽 |
| 效果/生物 | 原版指定药效、回合结算、Creeper Charge/Fuse、Skeleton 普通单箭及装备消耗政策 | HELP、完整借机、Guardian/ElderGuardian、长期能力 writer 和更多生产效果仍开放 |
| 客户端 | 战术镜头、行动/先攻/头像、调查、路径及部分轨迹、ClientSession 生命周期编排 | 原版胶囊剖切/补面与升降已有后台实机证据；复杂地形/高负载、缩放/命中和完整前台体验仍待验证；后台双客户端已有历史证据 |

### field 仍需关注的源码事实

`EncounterLifecycleCoordinator.reanchor` 使用会话捕获设置；只将加载/容量受限的 `SamplingDeferred` 作为可重试采样失败。区域内容比较忽略版本及锚点顺序，保留维度、发现范围、半径和锚点身份/位置；内容不变时不修改规则/结构/区域版本、不重复绑定或发布。实际变化先准备精确 chunk 索引、核验 held 容器，再提交权威与派生绑定，最后 dirty/通知。规则版本溢出校验在写入区域前完成。

重锚将已离域的 held block/fluid tick 按剩余 delay 归还原生队列，落入其他现存 field 的任务转交其 owner；入域捕获继续在既有世界/队列边界完成，不强加载或新增半径规则。提交后迁移失败明确进入停场释放，独立收集 owner/held 释放故障，未完成停场保持阻断并重试，不宣称世界副作用回滚。停场期间不再递归重锚，旧场释放重试不取消成员在新场的行动。多人建场使用首次回合通知之后的最终权威 field 绑定世界，避免初始单玩家采样覆盖索引。

已通过不变重锚、未加载采样保留旧状态、竖向离域释放延迟与资源守恒、原生容器卸载/注册、多人首次绑定及移动接近后执行场景。已补观察者提交故障、原生容器缺失后的独立释放/重试与完整存档第二进程队列恢复；任意故障和负载组合未穷尽。生产链不恢复临时 field 协议；最后玩家离开结束场次并释放其余成员。

### 所有权与查询

生产链已统一使用 `server.runtime.EncounterRuntime`；移动、投射物、世界调度各有运行 owner。旧“双 runtime 尚未接通”、旧签名断点和旧类行数不再是当前结论。停服沿采样、保存、独立释放/失败收集链处理；最终观察核验执行者 UUID 与实例，不用替换实体覆盖旧证据，采样本身不推进阶段。

事实捕获注入当前 Actor/成员读取端口，共享协议发送由 server/network 负责，客户端由 ClientSession 组织 reset，common 已拆独立读取值、请求组装及内部账本协作者。结构迁移不证明全部取消/合并/恢复组合已完成。

`AbilityQueryService` 在一次同步发现内复用执行者与目标事实，最多缓存 128 个目标、每目标 128 个事实，关闭后清理，不跨 tick/写入/执行边界，不生成许可。普通目标按声明事实读取；存在持续能力时仍保留完整来源展开。候选空间探测与寻路仍有同步工作；未实测点击延迟，不能宣称卡顿已消除。

### 能力注册与扩展限制

生产 bootstrap 在 load-complete 排队调用 `RegistrationLifecycle.freeze`，统一校验和冻结能力、facts、Actor、Effect、Equipment、Ranged、AI/移动、近战/身体目标及观察表。扩展应在构造期或 common setup（含排队任务）完成注册，不能依赖不同 Mod 的构造顺序，不能在 load-complete/服务器启动后注册。

手动能力包括免费手动都需执行器；付费能力需执行器及 facts；周期型需匹配 activation；持续型必须免费、无执行器并有 contribution。触发型保留纯规则驱动及被其他规则调用的执行器链。执行器按定义 ID 注册，`executor` 不是任意共享别名。当前全局 64 定义、每能力来源最多16等预算未因兼容工作扩张。

能力定义、来源证据、原生 facts、AI 用途、受限 executor、效果观察及客户端表现是独立职责。通用近战不自动翻译原生 override 的中毒/召唤等副作用；Goal/Brain 结构诊断不调用 canUse/start/tick。普通单箭可复用装备远程链，但特殊箭、附魔、穿透等有明确限制。三叉戟、珍珠、风弹、滞留药水、烟花不能由通用入口推定支持。

## 过程、恢复与历史

已有 Process 纯值、运行 owner、阶段图、独立随机流、检查点和触发 outbox。当前 `ExecutionProcesses` 只安装 `AUTHORIZED_EXECUTION_STEP` 配合 `FAIL_UNKNOWN` / `RECONCILE_NATIVE` 的驱动，其他时钟与 `RESUME_SEMANTIC` 拒绝为 `PROCESS_DRIVER_UNSUPPORTED`；尚无完整跨回合 staged driver。

手动执行已有受限生命周期。触发路径主要为同步执行，STARTED 恢复保持 UNKNOWN；其上下文权限、来源撤销、无实体时外部队列/订阅释放仍需按能力族完善。对多段能力，任意一次血量/库存/方块变化不足以证明所有必要效果完成。

当前恢复将 APPROACH/PREPARE 收束为 INTERRUPTED，已有变化证据的部分执行可记录 COMPLETED，其余只读对账/UNKNOWN，不重放 start，不恢复旧许可。已有两进程 launcher 证明特定恢复路径，但未证明普通专服完整存档续战。效果未发生时的安全补执行、可归因补偿仍未实现；不据历史“能完成就完成”描述自动退款或回滚未知效果。

截止处理已从早期“全部执行超时取消”收窄：行动执行器只对 APPROACH 强制截止，陈旧检查点检查 APPROACH/PREPARE；环境步截止和玩家挂机跳过是独立路径。七游戏日引用感知清理已覆盖关闭场次及 Actor 终态回执，保留活跃因果/重试引用和未知年龄；真实七日老化、全部跨重启组合未验证。

## 既有验证证据

以下来自重写前工作区记录，保留其基线与证明范围；**本次没有重跑这些游戏场景**。期间未新增测试文件，断言扩充复用既有 suite/fixture。

| 切片及基线 | 已记录执行结果 | 证明限制 |
| --- | --- | --- |
| 重构接续：`c027b16` 加当时工作区 | 26.1 `:common:test compileJava compileGameTestJava compileUiTestJava`、`:common:test build --offline`；其他 target `compileJava --offline` 通过 | 结构/自动检查，不是实机或所有迁移组合 |
| 重构平台接缝 | `body_control`、`gate_melee_approach`、`movement_lease_exit`、`scheduled_tick_hold`、`repair_values` 通过 | 单项选择；首次组合选择未覆盖的项目另行单跑 |
| 两进程恢复 | `runRestartGameTestServer -PrestartTestDirectory=build/refactor-restart-continuation`，`DNDTURN_RESTART_TEST=dndturn:restart_history`：WRITE_READY → VERIFY_PASS，场次/操作一致 | 真实放置效果、历史、活动 APPROACH 释放及唯一 INTERRUPTED；launcher 保留 chunk/SavedData，不是普通 level.dat 续战 |
| 查询切片：`c027b16` 加当时工作区 | common 测试、生产/GameTest 编译；`gate_melee`、`gate_melee_approach`、`tactical_effect_state`、`discovery_cursor` 通过 | discovery 夹具改用当前 owner 的事实 context；未做真实性能测量 |
| 注册冻结：`195ece9` 加当时工作区 | `:common:test compileJava compileGameTestJava --offline`，`repair_values`、`creeper_effect` 通过 | 真实 loader 启动后拒绝迟注册；无第三方内容实测 |
| EXIT/CI：同一 `195ece9` 工作区 | common 测试；26.1 main/gameTest/uiTest 编译；四 target `build --offline`；`prototype_exit_retry`、`consent_multiplayer`、`repair_values` 通过 | 含距离/未知位置/回合、恰好16格、原生仇恨保留、最后/个人退出、回执及投影编解码；无真实输入 |
| Mob 对 Mob：同一工作区 | common 测试、四 target `build --offline`；`mob_standardization`、`mob_standardization_automatic`、`merge_world_boundary`、`prototype_exit_retry` 通过 | 实际箭伤、近战、取消索敌、费用/去重；普通合并不证明专门 Mob 跨场组合 |
| 早期环境与历史切片 | `scheduled_tick_hold`、`environment_processes/circuits/clock/classification/tnt/domains/boundary_time`，`combat_structure`、`creeper_effect`、`merge_discovery_expansion`、`gate_melee`、`gate_melee_approach`、`gate_cow`、`body_control`、`actor_history`、`repair_values` 等相关单项通过 | 部分旧固定场地夹具先失败，经玩家中心/实际仇恨规则修订后通过；不外推当前全套隔离 |

历史失败与纠正保留：错误场景名未执行任何测试，不算通过；discovery/Mob 夹具曾缺失查询事实注入，Skeleton 专用 AI 曾过滤 Mob，修正后对应场景通过；多个 Gradle 进程同时写 common/build 曾引起 Fabric 暂时缺类，串行重跑通过。既有 jar 检查记录无测试类/AutoJS 条目，不代替当前构建产物核对。

## 本次同步与检查

26.1 `run/logs/latest.log` 的 `invalid captured combat settings` 来自 `New World` 旧战斗存档缺少 discoveryHorizontal/discoveryVertical/escapeDistance，Gson 将缺失数值读为零。用户授权放弃旧版后，独占打开世界 session.lock 确认未占用，将 `run/saves/New World/data/dndturn/combat_state.dat` 移为同目录 `combat_state.dat.incompatible-backup`，前后 SHA256 一致；地图及玩家数据未改。代码不迁移旧战斗，改为反序列化前检查捕获设置完整性，并取消 discovery 参数容错默认值。复用 PersistencePolicyChecks 扩充缺失字段拒绝且原文不变的不变量；既有检查仅覆盖顶层字段，无法防止嵌套 primitive 缺失被 Gson 静默默认。未新增测试文件。

本次执行 JDK25 + JDK17 toolchain 的 `build runGameTestServer -PgameTestSelection=dndturn:combat_save_values --offline` 通过（含 common 测试及既有构建检查，所选 GameTest 实际执行并通过）。首次 wrapper 下载受沙箱网络权限阻止，授权后使用工作区指定的 Gradle 9.8 完成；已有 build.gradle 与期间出现的 wrapper 修改未覆盖。`git diff --check` 通过。未重新进入原世界、未做双客户端或第二进程续战验证；本次重置不声称旧战斗恢复。

本次核对 Git 基线/共同祖先、实际规则/平台调用点、Gradle/CI 配置并重写活动文档；归档正文保持原字节。AutoJS 从错误 group `com.sighs` 修正为仓库实际 POM 的 `com.sighs.autojs`，保留 `-Pautojs=true`，配置为显式 `localRuntimeOnly`，只接本地运行类路径。

本次 JDK25 + 显式 JDK17 toolchain 执行26.1 `build --offline` 通过，包含 common 测试、生产/GameTest/UI编译及既有UI/控制/诊断检查。临时 Gradle init 检查分别验证 AutoJS 默认关闭、显式启用进入运行类路径且不进入 compileClasspath/apiElements/runtimeElements；启用时实际解析到 JAR。首轮直接使用配置因插件未预置而失败，显式声明配置后通过；旧 group 的离线/在线解析失败，查询 Maven POM 修正坐标后成功。没有新增长期测试或测试文件。活动文档本地链接检查、归档正文哈希保全与 `git diff --check` 通过；26.1产物核对未包含AutoJS及已枚举的GameTest/UI/common测试类。未运行范围：真实客户端/AutoJS 脚本、双客户端、GameTest、第二进程恢复、第三方兼容、性能与七日老化；不因本次文档同步关闭这些目标。

## P2 本轮实施与验证

本轮按 P2 全部范围推进，保留进入任务时的存档、构建和 wrapper 修改。指定 field 修复 `9c05747` 已包含；远端同名分支后续提交未整体合并。未自动提交、推送或发布。

近战介入从 tick 末 UUID 队列改为伤害边界同步结算，先核验原生实体实例、阶段/运行操作、近战范围与适配来源，再复用战术后端。修复候选场接受介入后未转 ACTIVE、导致命中伤害许可失败的问题；保留新成员下一轮资格及下次自身回合动作欠账，未命中不返还、后续回合不重复扣。重复原生回调不重复首击。EXIT 客户端只接受当前退出 operation 的回执，reset 或新请求后的旧拒绝不能重新触发退出。

测试沿现有文件扩展：区域值层保护内容比较，既有 Encounter 回归保护介入转正式/不插队/动作仅扣一次，原生近战夹具保护同步回调与实际伤害守恒，scheduled tick 夹具保护加载失败和离域移交，合并夹具保护非重叠场地的真实 Mob 索敌与队列/回执，已有无窗口 UI 检查保护 EXIT 迟到回复。原有断言没有覆盖这些接缝；未新增测试文件，未放宽费用、终态或守恒断言。几何重叠合并仍由三场扩展场景覆盖。

本轮实际执行（日志在 26.1 `build/p2-*.log`）：

- JDK25/JDK17 toolchain 的 26.1 `build --offline`，包含 common 测试、生产/GameTest/UI 编译和既有 check。最后执行 `build runGameTestServer -PgameTestSelection=dndturn:gate_melee_approach --offline` 通过，新增旧场释放重试不取消新场行动的接缝断言；结果见 `build/p2-final.log`。
- JDK21/JDK17 toolchain 串行执行 Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1 的 `build --offline` 全部通过。活动文档本地链接、`git diff --check` 和最终 26.1 jar 隔离检查通过，未发现 GameTest/UI 夹具、test_instance 或 AutoJS 条目。
- 单项 `runGameTestServer -PgameTestSelection=dndturn:<名称> --offline`：`scheduled_tick_hold`、`consent_multiplayer`、`merge_world_boundary`、`merge_discovery_expansion`、`gate_melee`、`gate_melee_approach`、`prototype_exit_retry`、`arrow_first_ingress`、`arrow_cross_session`、`arrow_cross_session_reverse`、`arrow_cross_session_miss`、`arrow_cross_session_pvp`、`arrow_pending_removal`、`environment_domains`、`environment_boundary_time`、`environment_circuits`、`combat_save_values`，均有实际执行并通过的输出。
- 两个独立 JVM 串行执行 `runRestartGameTestServer -PrestartTestDirectory=build/p2-restart-continuation --offline`，`DNDTURN_RESTART_TEST=dndturn:restart_history`；出现同一场次/operation 的 WRITE_READY → VERIFY_PASS。仅证明既有 chunk/SavedData、历史和接近中断恢复，不是普通专服完整 level.dat 续战。

失败及纠正：首次构建/平台运行受 sandbox wrapper 网络权限阻止，获准后运行。逗号组合选择只实际执行一个场景，随后改为逐项执行。近战新增断言暴露候选未激活的生产问题并修复；纯规则用例曾误设新入场者可进入当前轮，现按既定下一轮资格明确断言不插队。箭夹具的旧 24 格场间距、场外箭位置及非可索敌 mock 已按当前 field/原生关系修正；EXIT 夹具曾假定结束一个 Mob 候选回合必定轮到玩家，修正为容纳候选激活重掷先攻，距离边界断言保持原值。

上述是前一轮的构建/夹具证据；以下补齐 P2 收尾。按用户最新要求，客户端验收改为后台自动化，不再切换前台或移动系统指针。P2 已按当前支持范围完成闭环；不据此关闭 P3–P7。

恢复现在先等待玩家与所需区块自然就绪，不设超时，不把缺席生成 UNKNOWN。等待期间门控行动、AI、关系合并、挂机/执行截止；临时登出/卸载不删除待恢复成员。已提交合并的运行绑定在就绪后、恢复开放之前修复。原许可撤销及未知效果不重放保持不变。新增管理员/控制台 `dndturn encounter stop <uuid>`；显式结束清除恢复等待，失败清理独立重试。停服现有“捕获保存→释放→移除 runtime”顺序经普通专服验证，关闭期间离场不再改写规则成员。

回合通知改在操作登记和规则版本提交后触发；重锚修订不会被外层版本覆盖，通知失败不抹掉已接受操作。复用既有规则测试覆盖重锚和通知抛错；复用恢复夹具覆盖缺席等待不推进/不造 UNKNOWN 和显式结束；复用 scheduled tick 夹具移除一个容器注入失败，验证其他区块仍释放、恢复容器后重试保留唯一任务及剩余 delay。未新增测试文件。

普通专服与两个真实客户端使用 `build/p2-live/{server,client-a,client-b}`，独立用户名 P2Alice/P2Bob，仅连接本机 25586 端口；没有使用原有世界。通过 `runServer` 正常入口、完整 `level.dat`/玩家/chunk/SavedData 保存退出，分别重启候选场和活动战斗。后台键盘从 GLFW 回调进入客户端，UI 点击从 AUI 坐标事件分发进入同意界面；没有直接调用开战/EXIT 网络处理器代替输入。场景布置使用管理员命令，近战介入通过原生伤害边界触发。后台临时改绑开战键为 F8，未验证默认 Shift+Space 的完整前台体验。

已观察并记录：

- 双客户端同意建场产生两玩家锚点，世界索引与权威 field 一致。候选场重启后无人/仅一人登录仍保留原版本、回合、先攻、资源和唯一环境参与者；等待时 END 输入不推进，两人就绪后可继续回合。
- 原生 Mob 介入即时造成 3 点实际伤害，重复原生回调没有第二份伤害/结果。活动场再次重启保留该历史和欠账；新入场者不进入旧轮，首次自身回合动作被扣除。
- 一个红石灯任务在普通存档重启及等待后仍为唯一 held tick，剩余 delay=5；真实环境回合推进后熄灭，held 队列清空。玩家恢复期间的自然回血不是伤害重放或退款。
- 后台客户端 EXIT 验证非自身回合拒绝、1 格敌距拒绝、合法退出；退出后注入原活动投影返回 IGNORE，不能复活已结束场次。管理员重复 stop 正常幂等。

日志为 `build/p2-live-*.log`，逐次脚本/观察为 `build/p2-live-evidence.jsonl`，隔离世界保留在 build 供复查。两客户端与普通专服已正常关闭。临时脚本、截图和世界不进入源码或发布 jar。

本轮 26.1 `build`、`combat_save_values`、`scheduled_tick_hold` 已实际通过，包含 common、UI、控制/诊断检查。最终改动后另逐项运行 `gate_melee`、`merge_world_boundary`、`merge_discovery_expansion`、`environment_circuits`、`environment_boundary_time`、`prototype_exit_retry`、`arrow_cross_session`，均实际执行并通过，日志为 `build/p2-completion-*.log`。首次新增测试编译因误用观察者方法名失败，改用现有绑定接口后通过；首次 AutoJS 启动缺 Rhino，补为 `-Pautojs=true` 下的可选本地依赖后启动成功。脚本适配阶段的反射/鼠标回调尝试不计入通过证据，最终后台输入和 UI 事件链已得到服务端状态验证。

本轮还以 JDK21/JDK17 串行重新构建 Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1，全部通过。活动文档本地链接、`git diff --check` 与最终 jar 隔离检查通过；没有测试/AutoJS 条目，没有新增测试文件。

范围限制：没有前台连续键鼠/镜头体验、真实第三方兼容、性能或长时间运行证据；后台旧投影验证是向客户端接收入口注入，不是网络代理乱序测试。未证明所有能力的活动过程和任意故障组合均可恢复，P4 的完整生命周期任务继续保留。


## 原版战术镜头与地形剖切

基线 `dev_` / `7f42a6d`，进入时工作区干净。再次确认 `9c05747` 为 HEAD 祖先，不重复合并 field 修复。本次依用户指令只实施原版渲染；实际使用仓库当前 NeoForge `26.1.2.122`，启动日志的 Minecraft 为 `26.1.2`，不是前文历史验证的 `.84` 环境。未自动提交、推送或发布。

- CameraRig 保留 WASD 水平平移与 QE 围绕焦点旋转，增加 Space 上升、左 Shift 下降；默认模式切换改 F8，结束回合改 Enter，仍可改绑。已保存的用户按键配置不迁移，若自行配置冲突需在按键设置调整。镜头不再被实体方块挤回焦点，继续限制到已加载区块，不移动玩家身体。
- 隐藏区域使用相机到虚拟焦点的有限胶囊，按方块中心判定，在焦点前截断；进入半径 2、恢复半径 2.75 格，并对终点深度使用滞回。只扫描有界、已加载区域，每 100ms 合并一次变化，按变化单元及接面/AO 邻域标记区段，重建沿原版预算执行，不逐帧全量重建视锥。
- RenderSectionRegion 捕获不可变、世界实例隔离的局部掩码，隐藏单元仅在渲染视图中呈现为空气；原版构网同步生成暴露接面、流体边界和 VisGraph 可见性。真实方块、碰撞和光照引擎不改。区段结果携带来源直至 GPU 网格安装，拒绝过期掩码/旧世界/已复用区段结果，释放被拒网格，不覆盖现有网格；区段重置清理显示索引。
- 显示拾取只跳过已安装剖切网格中的单元，不改变服务端可见性/行动许可。隐藏箱子等方块实体、破坏裂纹和镜头所在隐藏流体的雾随显示掩码处理。菜单/失焦不推进镜头，退出恢复原地形，世界切换隔离旧快照。保留原世界光照，室内切面可能较暗。

没有新增测试文件。现有 MovementPreviewChecks 增补中心判定/显隐滞回、已发布值不变、未加载区域排除、跨区段接面与 AO 失效、旧世界/邻域过期拒绝；原有路径断言不能保护这些渲染值不变量，故在已有客户端几何检查层补充。PackageArchitectureTest 只新增此次窄平台入口与快照访问接口的许可，原有依赖限制保留。

JDK25 + 显式 JDK17 toolchain，使用 target 的 Gradle 9.8 执行 `build --offline` 通过，包括 common、生产/GameTest/UI 编译及既有 check；最终日志 `build/camera-final-build.log`。首次编译因 final Results 的 Mixin 接口转换需经 Object 失败，修正后通过；架构检查首次拒绝新增入口，补充精确入口后通过。中途误用根 wrapper 9.7.1，最终已改用 target 9.8。实机首次暴露当前 NeoForge 滚轮钩子签名变化，改接含 Vector2ic 的当前签名；最初使用 Vector2i 描述符仍失败，按字节码核正。首次区段构造器注入仅命中过时重载，导致实际区域未捕获掩码，已改为精确匹配当前完整构造签名并重新验证，不以编译通过代替客户端启动证据。

真实专服和一个真实客户端运行于隔离 `build/camera-live/{server,client}`，仅连接本机 25587；使用可选 AutoJS 和隐藏窗口的 GLFW 回调输入，没有移动系统指针。已实际验证：F8 进入/退出、WASD/Space/Shift/QE 移动镜头且身体位置/规则修订不变、Home 返回、跨区段平移后旧墙恢复、失焦和真实 PauseScreen 阻断及释放后不复活按键、墙/屋顶剖切与接面、隐藏墙后地面拾取、箱子/水隐藏且真实状态仍在、镜头位于真实水中但隐藏后雾为 NONE、退出恢复。额外以原生 extractLevel 检查隐藏裂纹被过滤而可见裂纹保留；以已构建的旧网格调用生产安装接缝，确认退出后的迟到安装被拒且可见网格不变。这两项是接缝检查，不声称真实乱序网络或 GPU 故障注入。临时脚本的反射/集合迭代失败，以及在帧结束清空后错误读取破坏状态，均修正取样方式后重跑，不算通过证据。

客户端截图、日志和逐次观察保留于 `build/camera-live`；这些临时材料不进入源码或发布 jar。客户端与专服均正常关闭。最终 `git diff --check` 与 jar 测试/AutoJS 隔离检查通过。

未运行双客户端、前台连续手动体验、Sodium/Iris/光影或其他第三方兼容、长时间/高负载性能检查。原版各区段仍分别异步上传，不承诺跨区段整组原子切换或任意负载下绝对无闪烁；滞回和过期结果拒绝已接入，不把这些机制等同于全场景性能验收。本次只推进 P3 的原版镜头切片，不关闭整个 P3/P7。


## 选择性透视性能优化

基线 `bea6126` 加进入时用户 Actor 文档修改；实施期间另有 Actor 数据/调查并行修改，保持原样。`git merge-base --is-ancestor 9c05747 HEAD` 成功，field 修复已包含。本轮只改客户端相机/剖切及既有几何检查，不自动提交或发布。

- 掩码改为每区段 4096 位不可变位图，构造克隆输入，未变化区段共享；构网捕获使用固定 27 槽局部查询，空掩码提前返回。已显示索引与最终安装校验同步使用位图，仍以实际安装网格决定拾取和隐藏。
- 每 100ms 检查相机、焦点和包围盒涉及的区块加载状态；静止且加载不变时跳过格子扫描和掩码重建。卸载/重载、清理和世界切换失效缓存；空气仍在掩码内，放置方块不需另设状态缓存。
- 胶囊判定使用标量计算；位图 XOR 只对变化格传播脏区段，内部格只标本区段，边界保留面/边/角及 AO 邻域。相机每次平移仅计算一次最终姿态，静止复用采样几何并继续核验加载列。不改变 100ms 节奏、半径、终点截断、滞回或异步重建调度。

未新增测试文件。复用 `MovementPreviewChecks` 增补位图输入防回写、正负坐标面/边/角精确失效、局部快照一致、静止不扫描、卸载/重载、reset 与世界身份隔离；这些不变量原有中心判定和单个角落断言覆盖不足，使用既有客户端纯几何层即可证明，不增加 GameTest。

JDK25 + Java17 toolchain、target Gradle 9.8 的 `build --offline` 最终通过，包含 common、生产/GameTest/UI 编译和既有检查，日志 `targets/neoforge-26.1/build/cutaway-perf/final-build.log`。首轮 common 7 项失败来自同时修改的 ActorFacts 使用不受支持的 fact 类型；该并行修改修正后重跑通过，未为此改动 common。最初版本检查调用了根 wrapper 9.7.1，实际构建均使用 target wrapper 9.8。

临时采样保存在 `build/cutaway-perf`，不进入源码和发布 jar：从 HEAD 提取原掩码实现，与新实现对照 600 次镜头移动/加载变化，隐藏格与脏区段集合完全一致。JVM 预热后每组 2000 次、三轮采样（约 32 格斜向镜头），旧/新移动更新分别约 **176–177µs / 72µs**，线程分配约 **323KB / 19.5KB**；静止旧/新约 **106–107µs / 0.19–0.39µs**，分配约 **196KB / 92–188B**。该口径包含掩码与脏区段计算、驱动输入的少量分配，不含真实加载查询、区段构网、GPU 上传和帧时间，不能换算为 FPS 提升。结果见 `final-results.log`，不作为 CI 毫秒阈值。

使用隔离 `build/camera-live` 专服和真实客户端、本机 25587 与 AutoJS 后台 GLFW 输入，已验证升降/WASD/QE、约 32 格缩放、身体及规则修订不随镜头改变、隐藏墙后地面拾取、箱子/水隐藏而真实状态保留、跨区段后旧墙恢复、Home 返回和 F8 退出后掩码/显示恢复。截图 `client/screenshots/cutaway-optimized.png` 已查看，切面可见且保留原光照。初次输入断言被场景水流推人干扰，清除隔离场景水流后重跑通过；临时脚本尝试反射静态字段受 Rhino 限制失败，最终改用公开接口核验，不计作通过证据。测试客户端与专服正常关闭。

未执行优化前后整客户端帧时间/GC/构网过期率对照、真实加载边界卸载/重载实机注入、复杂地形长时间高负载、双客户端、前台连续人工操作及第三方渲染兼容。加载变化由既有几何层检查与临时对照覆盖；不能据纯计算加速关闭 P3/P7 或声称异步构网瓶颈已解决。
