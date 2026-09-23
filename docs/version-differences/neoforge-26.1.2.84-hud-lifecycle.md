# 回合 HUD 与原版 Screen 生命周期

固定依赖：NeoForge 26.1.2.84，解析的 Minecraft 26.1.2 patched 源码，ApricityUI 1.2.5（`wwrdhxiM`），JDK 25。仅修改本 target 的客户端显示与输入门控。

## AUI 文档

发布 jar 字节码显示 `Client.drawScreen(ScreenEvent.Render.Post)` 在原版 Screen 之后提交 AUI PiP；`ApricityUiPipRenderer.renderToTexture` 遍历非 world、非 manuallyRendered 文档，调用 `Base.drawOverlayDocument(PoseStack, Document)`。该方法没有 Screen 可见性检查，不能仅凭 AUI overlay 文档中“普通 HUD 自动隐藏”的描述作保证。

`TacticalAuiRenderMixin` 在 `Base.drawOverlayDocument` HEAD 取消本模组隐藏状态的 tactical 文档，发生在 context、矩阵与 scissor 入栈之前，不改变其他文档。`hudVisible()` 要求有效本地会话、存活玩家/世界、非 F1、无加载 overlay，且 Screen 为空或 ChatScreen。Screen 打开时保留同一 Document 和服务器投影，退出/死亡等既有结束路径才 remove。隐藏文档不能参与 tactical 命中；Screen/失焦沿用既有输入与手势清理优先级。

聊天时 HUD 上方先攻、日志、标题和浮动面板隐藏，避免 AUI 在 Screen Post 遮住输入栏或补全；底部行动栏仅显示，不消费聊天输入。四个工具按钮以底栏内部 right/bottom 各 8px 锚定、101×23 容器排布，禁止 flex 收缩。tactical CSS 全部元素使用 `user-select: none`。

## 聊天布局

- `TacticalOverlay.chatBottomInset()` 将与行动栏相同的文档几何通过 `documentToGuiPosition` 转到 GUI 像素，提供统一底部预留量。视口缩放/窗口变化重算；无会话、隐藏 HUD 时为零。
- patched `ChatScreen.init()` 用 `height - 12` 创建 EditBox；`extractRenderState` 用 `height - 14/-2` 绘制背景，`CommandSuggestions` 使用 `screen.height` 放置列表和用法文本。Mixin 在 init HEAD 设置此 ChatScreen 的可用高度，并在渲染 HEAD 更新输入 Y 与补全信息，避免平移画面但保留旧点击区域。按真实窗口高度重算，非累积减法；不改其他 Screen 尺寸。
- `ChatComponent` 公共绘制与 `captureClickableText` 都调用私有 `extractRenderState(ChatGraphicsAccess graphics, int screenHeight, int ticks, DisplayMode mode)`。其局部槽为 this=0、graphics=1、screenHeight=2、ticks=3、mode=4；Mixin 通过完整描述符、argsOnly、index=2 仅调整 screenHeight。原版在聊天缩放前计算 `(screenHeight - 40) / scale`，因此绘制、滚动条、悬停和链接点击共享同一位移，不改 ticks 或鼠标坐标。
- 所有 Mixin 维持 defaultRequire=1；菜单切换不更改世界行动费用、计划或服务器许可。

## 验证范围

使用 build 下临时隔离客户端夹具和全新平坦世界，通过正式 START/EXIT 协议建立/结束会话；真实 Screen 切换、原版消息 clickable 捕获/点击、截图与 DOM 边界检查覆盖暂停、背包、聊天、补全、F1、960×640/854×480、文档复用及刷新、工具按钮边界、退出恢复聊天。截图与本轮结果保留在 `out/hud-lifecycle-review/`。夹具不是用户键鼠端到端联机测试，不表示已验证其他模组的 ChatScreen 子类或 dedicated 多客户端。

正式产物通过独立 `gradlew.bat clean build` 清除临时检查类后重新生成；检查 Mixin 配置及正式类/资源，不携带 build 目录夹具。
