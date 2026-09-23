# NeoForge 26.1 参与者头像

文档同步说明（2026-09-28）：本文保留头像实施批次的构建与源码依据；后续整套验证见[物品记录](neoforge-26.1-player-items.md)。真实模型构图、裁切、重跟踪及双客户端显示仍归[GUI-01](../02_GAPS_AND_CONFLICTS.md#gui-01)，本次文档同步未新增实机证据。

2026-09-28，仅修改 NeoForge 26.1 客户端。固定 Minecraft 26.1、NeoForge 26.1.2.84、ApricityUI 1.2.5（`wwrdhxiM`），未引入 JER 依赖或改变 common/网络/服务端规则。

## 参考与固定版本依据

- 实施阶段记录曾阅读本地 `docs/JustEnoughResources-master/Common/src/main/java/jeresources/util/RenderHelper.java` 的 `renderEntity` 及 `jei/mob/MobWrapper.java`：JER通过背包预览入口绘制实体，按宽高选缩放。该参考目录在本次工作树中已不存在，未重新核验其内容。当前`ParticipantPortraits`可核对为使用背包预览入口，未修改真实实体ID或使用物种偏移表；历史JER阅读记录不作为本次源码证据。
- 核对 target 的 `gradle.properties` 与实际生成的 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`。`InventoryScreen.extractEntityInInventoryFollowsMouse(GuiGraphicsExtractor,int,int,int,int,int,float,float,float,LivingEntity)` 将鼠标位置转换成角度，调用 patched `renderEntityInInventoryFollowsAngle`（同参数类型）。后者经 renderer `createRenderState(entity,1.0F)` 创建状态，清除阴影/轮廓，只调整状态的身体/头部角度和缩放后调用 `GuiGraphicsExtractor.entity`。本实现直接使用角度入口，固定 `.9f/.1f`，无真实实体写入或额外 tick。
- `GuiGraphicsExtractor.entity` 设置预览满亮度，提交带矩形和当前 scissor 的 `GuiEntityRenderState`；`GuiEntityRenderer.renderToTexture` 使用 `ENTITY_IN_UI` 灯光和独立相机状态。预览继承实际 renderer、装备和本模组已存在的只读表现采样；不创建或持久保存假实体。
- 核对解析出的 ApricityUI jar 字节码：`ApricityGuiLayers.submitUi(GuiGraphicsExtractor,ApricityUiPipRenderState.FloatingItemBatch)` 先按当前 extractor 去重，再提交全屏界面 PIP；`submitOverlay` 顺序为界面、光标。`TacticalPortraitLayerMixin` 在双参数 `submitUi` 内 `submitPictureInPictureRenderState` 调用后注入，只转交 `ParticipantPortraits.extract`，不取消原调用，沿用配置 `defaultRequire: 1`。因此重复提交早退不会重复提取头像，头像在界面之后、光标之前；只在客户端 GUI 提取线程使用。
- 提取前调用既有 `OverlayUi.prepareForDraw` 完成当前文档投影/样式/布局。ApricityUI `Element.getBoundingClientRect` 读取 Rect 与 margin/size，`LayoutCommit` 的滚动更新会平移已提交 Rect；头像框由同一卡片布局产生，通过 `documentToGuiPosition` 转换一次。先攻栏外 scissor 成对释放，不根据缩放重新推算鼠标命中。

## 行为与范围

`TacticalOverlay` 为每个成员保存一个显示节点；实体为预览占位框，环境为 96×96 Canvas，绘制圆盘、外轮廓、经线、赤道与两条纬线，不新增头像边框。所有节点随布局比例变化，姓名保留底部空间，指针事件穿透到原卡片。环境仍不能被选为实体目标。

只为与先攻栏相交的实体卡片提取预览。每帧一次遍历客户端现有可渲染实体，用 UUID 对应当帧实例；不跨帧持有实体/渲染状态，也不为头像加载区块。按用户后续澄清，头像只显示模型上半部：先按原版 living scale 归一实体高度，再以半高加6%头顶余量计算等比放大倍率，向上取整。背包入口默认以半高居中，传入 `offsetY = 框高 / (2 × 倍率)` 将半高位置移到头像底边；PIP纹理自身边界裁掉下半身及超出的两侧。缩放不再受全身宽度约束，避免宽模型缩小后重新露出全身。固定角度仍保持不变。找不到已加载 LivingEntity 或尺寸无效时显示问号。关闭 HUD、聊天/其他 Screen、F1、同意弹窗时不提交实体头像；环境画布跟随文档可见性。成员移除、会话关闭与文档重建清理节点引用。

所有 LivingEntity 使用其已注册 renderer；特殊几何、外部 renderer 与菜单覆盖下的实际显示仍需实机验证，不声明所有物种构图或第三方渲染兼容完成。未新增服务端能力支持。

## 实施阶段验证（本次文档同步未重跑）

JDK 25.0.3，在 `targets/neoforge-26.1` 执行 `gradlew.bat clean build --console plain --no-daemon`，结果 `BUILD SUCCESSFUL`。Java、资源、jar、common 测试、现有 `verifyUiProjection` 无窗口检查及 GameTest 源集编译完成；既有 API/Gradle 弃用警告仍存在。无窗口检查覆盖原有文本/样式/移动环，不验证新增 GPU 头像。`git diff --check` 通过。

未运行 GameTest server 或真实客户端；新 Mixin 运行注入、实体/装备画面、不同 GUI 缩放、横向滚动、菜单遮挡、实体卸载/重跟踪、重载和退出恢复仍按 [GUI-01](../02_GAPS_AND_CONFLICTS.md#gui-01按钮与图标位置不一致) 待验收。

上半部裁剪修订后，JDK 25.0.3 下执行 `gradlew.bat build --console plain --no-daemon` 成功，头像类与 jar 重新生成，现有无窗口 UI 检查通过；common 测试为 UP-TO-DATE，未重跑 GameTest 或真实客户端。重新核对 `PictureInPictureRenderer` 的居中/缩放与 `GuiEntityRenderer` 的平移/旋转顺序，确定正向 offset 将半高位置下移至框底；该源码依据不替代上半部构图的画面验收。
