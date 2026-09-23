# NeoForge 26.1 日志与 GUI

文档同步说明（2026-09-28）：以下协议号、测试数量和命令保留对应GUI实施批次，不是当前全项目版本。当前常量见[01](../01_IMPLEMENTED_DECISIONS.md#baseline)，后续81／63项整套验证见[物品记录](neoforge-26.1-player-items.md)。本次只同步文档；真实客户端布局／点击仍按[GUI-01](../02_GAPS_AND_CONFLICTS.md#gui-01)验收。

2026-09-28，固定 Minecraft 26.1 / NeoForge 26.1.2.84、ApricityUI 1.2.5 (`wwrdhxiM`)，Gradle JVM/编译使用 JDK 25.0.3。仅影响此 target，common 规则、其他 target 的功能范围不变。

## 实现与边界

交互选择修订（2026-09-28）：仅修改既有客户端计划/控件及HTML/CSS，不新增或修改原版/Mixin接入点、协议和common。攻击候选由服务端已返回的能力中按当前显式物品选择筛选，界面只显示“攻击”；无选择/空槽使用既有IntrinsicMelee，选中物品且没有受支持攻击时不回退空手。内部能力ID与来源证据保留。移除空闲左键默认移动/检查与悬停攻击推断；回合切换清除目标、预览、查询身份及待提交标记，保留物品/能力，自己的新回合重新确认，查询回复额外核对当前回合。退出/新会话清空选择。菜单从300×240上限缩至150×144，padding从8降至3，行高从32降至20，去掉标题和状态说明，保留禁用态及底栏悬停拒绝反馈；此尺寸覆盖后文旧240px记录，仍在请求期间预留固定框。

本轮JDK25.0.3在独立target执行 `gradlew.bat clean build --console plain --no-daemon` 成功（`verification-selection-ui.log`），后续补充回合回复校验及测试后执行 `gradlew.bat build --console plain --no-daemon` 成功（`verification-selection-ui-final.log`）。`verifyUiProjection`增加进入空闲左键、回合失效不丢物品/能力、清除待提交、新会话清空选择的无窗口检查；common测试在clean构建执行，后续为UP-TO-DATE。GameTest仅编译，未启动真实客户端，不声称点击/视觉或联机验收通过。

- 保留 `tactical.html` 的 `journal`、`log`、`hit` 容器及布局，清除旧提示。删除 `TacticalOverlay` 日志拼接/条目监听、客户端历史缓存/补页、服务端日志订阅游标、`ResultNotice`/`HitNotice` 及注册发送入口；移除成功行动的 actionbar 叙述和备用 HUD 结果详情。行动状态/拒绝反馈、服务端不可变结果、费用、存档和去重保留。
- 移除 `DebugDiagnostics`、调用参数求值、仅用于诊断的条件、启动调用、Gradle `-debug` 参数以及 `ServerDebugArgumentsMixin` 和其注册；移除服务端合并/配置跟踪信息。错误与警告处理保留。原版 debug 世界判断属于环境调度规则，保持不变。
- C2S 注册版本从21到22，`CombatIntent` 移除日志游标，枚举代码6退役且不复用；S2C 从24到25，移除日志载荷。连接双方须更新，不将旧日志请求解释为新操作。`EncounterState` 的现有结果计数字段暂保留，不触发客户端日志或补页。
- `TacticalButton` 为图标控件建立共享父容器。用户反馈此版本仍有渲染和位置异常后，追加 `layout`：从同一尺寸生成父容器、按钮、内缩展示层与canvas/item的像素框，替代嵌套100%尺寸；展示层明确 `z-index: 1`，按钮为0，展示层不拦截输入。禁用状态同时影响按钮和显示，动态行为移除时移除整个容器。
- 行动栏设计高度从75压缩至61，资源/标签/双槽位/说明/状态按紧凑行坐标布置；按用户澄清，底边继续贴底，以视口高度25%作为整栏缩放目标，顶边位于视口高度75%处。结束回合按钮与两行槽位中心对齐。聊天避让与布局共用 `dockTop`。先攻栏仍沿用20%高度缩放。
- 本机解析 jar 字节码确认 `Canvas.drawCanvas` 读取 `Rect.getContentPosition` 与 `Box.innerSize`，`Document.hitTest` 经 `RenderQueue` 使用命中缓存。进一步检查确认 `HitTestCache.resolveCommittedBounds` 为普通按钮读取 `Rect.position + margin` 与 `getElementSize`，未应用world transform；`LayoutCommit.commitElement` 分别提交Rect与world transform，`Base.applyTransform`用于绘制。这解释了整栏CSS缩放后点击区与图形不一致的源码路径。
- 此次移除行动栏与先攻栏的 `transform: scale`，将相同比例直接应用于布局坐标、宽高、字体/行高、边框、图标内缩、数字标签与工具栏间距。按钮与图标继续共用父容器；结束回合按钮位于两行槽位右侧中心，文档视口的GUI换算保持原API。未修改依赖或新增Mixin，未用单独鼠标偏移补偿掩盖绘制差异。

## 验证

UI时序与圆环最终增量（2026-09-28）：

- 固定jar中 `Element.setTextContent` 会清除旧子节点及 `legacyRenderTextNode`，`getLegacyRenderTextNode` 随后惰性构造节点；`TextNode.setData` 可原位更新并使父文本/布局缓存失效。两文档的动态文本统一使用后者，首次创建显式DOM文本节点。此证据说明原路径的节点重建行为，未将其单独认定为实机放大虚影的唯一原因。
- `StyleAsyncHandler.attach/applyOnMainThread/rebuildCssCache` 异步装载外部样式；`OverlayUi.stylesReady` 以共享CSS中的 `#end-control` 规则实际进入缓存为依据，加载完成前暂不绘制或接收自有文档点击。无窗口检查直接调用该版本CSS解析器验证该缓存键。不以固定tick延迟猜测资源准备完成。
- 既有 `Base.drawOverlayDocument(PoseStack, Document)` 静态客户端绘制入口HEAD现在调用 `OverlayUi.prepareForDraw`：进入Document上下文、应用视口、更新纯客户端投影、提交样式及脏绘制状态、提交LayoutCommit、失效命中缓存，再进入原绘制。仅处理战术HUD与同意文档，不补跑AUI tick、AI、世界或物品行为。`Base`原先在读取视口后才进入样式/布局提交，本修复在读取视口前完成准备。无变化时不额外提交完整布局；禁用状态不再重复写相同属性。
- 右键菜单从候选结果到达前就预留最终240px上限（小视口内收缩），列表在固定区域滚动，不随结果数从80px增长。同意弹窗改用文档像素居中与有界滚动，移除CSS位移，名单/倒计时更新共用稳定文本。原版聊天已有 `init/extractRenderState` HEAD避让，仍使用同一 `chatBottomInset`，未改原版文本渲染。
- `MovementRing` 位于结束按钮共享父容器内，32px设计直径、128px位图，指针穿透。按服务端剩余量/会话基础预算显示，超出基础预算用金色满圈。更新先用 `AlphaComposite.Clear` 清空旧内容，再绘制底环和新弧；只在数值/预算变化或DOM重建时重绘。S2C从25升至26，`EncounterState` 增加非负 `movementTicksPerTurn`；生产活动投影来自 `engine.movementTicksPerTurn(encounterId)`，不硬编码28或用客户端首次观察值充当上限。旧构造重载/结束投影的0表示无可用基准。
- JDK25.0.3执行 `gradlew.bat build runGameTestServer --console plain --no-daemon` 成功，54项必需GameTest通过；`RepairGameTests.values` 增加36基准/18剩余的完整快照Codec检查。日志 `verification-ui-timing.log`。随后独立 `gradlew.bat clean build --console plain --no-daemon` 成功，73项common测试、`verifyUiProjection`无窗口检查通过，日志 `verification-ui-final.log`。`uiTest`独立源集只用于检查，最终jar检查无 `OverlayUiChecks` 或GameTest类。无窗口检查覆盖固定CSS缓存键、连续文本更新/空值/恢复的节点身份及单一渲染文本、圆环递减清除/空环/额外资源/未知基准；不替代GPU、Mixin注入或真实鼠标验收。

交互错配修复后，在JDK25.0.3下独立执行 `gradlew.bat build --console plain --no-daemon` 成功；Java与资源重新构建，common测试及GameTest源集编译为UP-TO-DATE。`git diff --check`通过。此次未运行GameTest，未完成真实客户端鼠标悬停/点击及多GUI缩放验收；固定依赖字节码审计是原因证据，不是画面验收通过。

用户澄清顶边25%对齐后，在JDK25.0.3下再次运行 `gradlew.bat build --console plain --no-daemon` 成功；Java重新编译、jar重新生成，common测试为UP-TO-DATE。此次仍未执行真实客户端画面与点击验收。

在 target 运行 `gradlew.bat clean build runGameTestServer --console plain --no-daemon` 成功，73项 common 测试、54项必需 GameTest 通过；日志在本地 `targets/neoforge-26.1/verification-gui-logs.log`。删除日志载荷对应的 Codec 测试片段，保留原有规则/结果测试；`RepairGameTests` 改为验证普通行动 Codec，并验证退役代码6拒绝。

最后清理跟踪输出和绑定时序后再次运行 `gradlew.bat build runGameTestServer --console plain --no-daemon` 成功，54项必需 GameTest 再次通过，common测试为UP-TO-DATE；结果另记 `verification-gui-logs-final.log`。客户端尚未实机运行；分辨率、GUI缩放、点击对齐及生命周期验收登记在02的GUI-01。原有Gradle/API弃用警告不作为功能失败，也不证明GUI验收完成。

底栏顶边上调和渲染修复追加验证（2026-09-28）：重新检查固定ApricityUI jar中 `NodeTree.appendChild/insertBefore`、`Element.init` 与 `Canvas.drawCanvas` 字节码；插入操作可能返回初始化后的元素，控件保留 `insertBefore` 返回值；Canvas绘制使用内容位置与innerSize。JDK25.0.3下独立运行 `gradlew.bat build --console plain --no-daemon` 成功，Java与资源重新构建，common测试及GameTest源集编译为UP-TO-DATE。本次未执行GameTest或真实客户端显示/点击验收；没有将旧平台测试结果作为此次渲染修复通过的依据。
