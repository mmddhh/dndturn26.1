# NeoForge 26.1.2.84：AUI 接入契约

## 固定依赖

- [ApricityUI](https://github.com/Tower-of-Sighs/AUI)，1.2.5，NeoForge 26.1 发布物；Modrinth Maven 坐标 `maven.modrinth:apricityui:wwrdhxiM`。
- 源码核对提交：`d5a6f556c663bafccfb3552d0eb73573f3daacd0`，分支 `snow`。上游对应 target 同样使用 NeoForge `26.1.2.84`。
- Rhino：`dev.latvian.mods:rhino:2101.2.7-build.82`，按该 AUI target 的实际依赖声明配置。AUI 的发布 POM 不携带这些运行依赖。
- AUI 和上述 Rhino 均为客户端必需依赖，metadata 显式声明；DNDTurn 没有复制或打包上游实现。其他三个 target 不引入 AUI。发布安装需同时安装对应 AUI 与 Rhino，不能使用其他版本 target 的 AUI jar。

## 接口与线程

`ApricityUI.createDocument("dndturn/tactical.html")` 创建 Overlay；页面资源位于 `assets/apricityui/apricity/dndturn/`。所有 DOM 创建、更新和清理由客户端线程执行。

`Document.getRefreshGeneration()` 变化时重绑节点与事件。特别注意 `Element.appendChild` 会通过 `Element.init` 将通用节点替换为具体类型，必须保留返回的实例；缓存原始 `createElement` 对象会使文字更新和节点移除作用于脱离 DOM 的对象。

同一代次内复用成员、日志和物品节点，只更新变化的文本/属性/ItemStack。日志缓存最多 128 条；服务端有序结果仍独立分页发送。协议 **12** 的会话投影携带结果总数，客户端在历史尾部也能请求补收；命中消息携带骰子、AC、战术伤害、吸收、生命损失与观察阶段。

头像使用 Minecraft 玩家皮肤；Mob 与不可解析实体保留黑底金边空框，隐藏空纹理节点。物品使用 AUI `Item.setIngredientStack` 的副本显示，不把展示槽伪装为服务端 Menu。当前未开放通过这些槽执行消耗或换武器。

## 输入与生命周期

- Tactical Overlay 采用固定 960×540 逻辑视口、等比适配；根元素透过页面命中，空白由客户端路由接收，不落到原版输入。Consent 使用实际窗口视口。默认反引号切换镜头/角色模式，提示读取实际绑定。
- 按键和 UI 共用 `CombatControls` 意图与移动停止状态机；结束回合先结算移动。服务端投影明确是否具有原型交互能力，开发只读投影不显示可执行动作。
- 退出、死亡、连接/玩家/维度改变时移除文档、选择和缓存。`ClientControl` 唯一持有模式、镜头与手势；旧 `OverlayMouseControl` 已移除。Screen/失焦只暂时接管，不清模式。事件边界在 AUI 派发后阻止原版抓取和按键写入，不能依靠后续 tick 释放鼠标补救。
- 同意窗口使用独立模态 Overlay、实际窗口背景与唯一 Document 输入门禁，不暂停服务器或建立区域控制。服务端关闭消息撤销窗口；客户端时钟不能延长授权。固定版本反编译与新 seam 见 [客户端控制接入](neoforge-26.1.2.84-client-control.md)。

## 多人同意

正常战术入口与多人同意已接通，不依赖旧 prototypeEnabled/multiplayerConsentEnabled 开关；受真实身份、同意、阶段与资源授权约束。

`ConsentWindow` 是 common 值状态机；`ServerConsentCoordinator` 在服务器线程采样名单并协调请求。START 表达发起者的同意；其他当前名单成员需确认。普通离场撤销个人同意，再入须重确认；名单内玩家掉线取消整项请求。

窗口为 400 个实际服务器 tick（20 TPS 时 20 秒）。重叠请求合并后保留最早截止与仍有效的同意；建场前重新采样，新增被覆盖玩家必须同意。待决窗口不注册模拟区域，世界继续运行。请求和回复有稳定 ID、世代与名单版本；重试不重复建场，冲突和过期回复拒绝。

多人原型中，EXIT 仅移除请求者成员关系；重试不改变留下的成员，最后一个玩家退出仍结束原型。这不证明退出后的控制已释放：退出者留在场内仍被区域暂停，冲突见 G27/C09，多人退出未完成验收。

等待窗口不跨服务器重启恢复：没有已授权世界效果可重放，连接世代清理客户端窗口。已建立会话继续走既有持久化框架；不能据此声称活动会话重启验收完成。

## 运行证据与限制

命令（本 target、JDK 25）：

```powershell
.\gradlew.bat clean build :common:test runGameTestServer
.\gradlew.bat runClient -PdndturnUiSmoke
```

客户端探针只在显式 JVM 开关下创建独立平坦测试世界，验证真实 AUI 布局、点击选中、节点复用、热重载、物品/头像、同意模态、截止显示与清理。菜单与焦点回归通过 Windows/Python 原生鼠标输入经过 GLFW → MouseHandler → NeoForge → AUI 命中检测，验证同意/拒绝点击；此探针需要桌面、Python 及测试窗口焦点，会移动测试窗口中的鼠标。截图和结果在 `build/ui-smoke/`。Gradle 会删除旧结果并检查本轮 PASS，不能靠正常进程退出冒充探针通过。

GameTest 覆盖协议往返、多玩家实际服务端建场、离场再入、重叠请求、重复回复、掉线和超时。探针中的展示数据与 GameTest 嵌入玩家均不替代真实双客户端 dedicated server 验收。

箭矢伤害夹具在远处场地强制加载后，等待实际 block ticking 与实体加载条件再生成目标、执行碰撞；固定等待一个测试 tick 不足以证明异步区块已就绪。

剩余功能和验收只在 [缺口清单](../02_GAPS_AND_CONFLICTS.md) 维护。

## 2026-09-25 用户层与输入接入

用户要求采用 BG3 的界面布局、黑棕底与金色细边。固定 960×540 逻辑画布统一 `box-sizing: border-box`，顶部先攻队列超过五人改为左对齐横向滚动；底栏、右侧日志、左侧物品面板留出间隔。完整状态仍来自服务端，Mob 空框不伪造头像或生命值。

固定源码 `minecraft-patched-26.1.2.84-sources.jar`：`KeyboardHandler.keyPress(long, int, KeyEvent)` 在 Screen 处理与 `KeyMapping.set/click` 后调用 `ClientHooks.onKeyInput`。客户端 `InputEvent.Key` 监听只清除重叠的原版按键状态/点击队列，不取消事件；数字行动槽不同时切换原版快捷栏，空格不同时跳跃。Shift＋空格通过 NeoForge `KeyModifier.SHIFT` 注册，切换进入/退出并清掉同次结束回合点击；长按重复不重复提交行动。Z 在战术会话内桥接原版跳跃 held 状态，释放、Screen/同意模态及会话重置时清理。客户端 tick 消费同一套可重绑 KeyMapping，按键与 AUI 共用移动停止和结束回合状态机。

布局探针检查日志/行动栏、物品/行动栏、物品/日志、标题/先攻不相交，末尾物品槽与全部行动按钮处于面板内；仍验证节点复用、刷新、真实鼠标同意/拒绝与清理。默认键与已有 options.txt 分开验收，旧绑定不会被强制替换。

本轮执行 `build runClient -PdndturnUiSmoke`，独立 target 构建与真实客户端探针通过；检查战术界面、展开物品和同意模态截图，覆盖 854×480 与 1280×720 窗口样例。产物包含 metadata、AUI 页面/样式及 CombatControls。`clean build` 的 clean 步骤因既有 live-loop 服务器进程锁住 build 文件失败，未终止该进程。真实双客户端按键、全部缩放组合与长队列仍按 G26 跟踪。
