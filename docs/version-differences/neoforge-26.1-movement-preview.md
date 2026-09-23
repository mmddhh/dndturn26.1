# NeoForge 26.1 移动预览

适用固定依赖：Minecraft 26.1、NeoForge 26.1.2.84、JDK 25.0.3。仅修改该target的玩家预览；common与其他target未改动。本轮未发现`docs/legacy/MAINTENANCE_WORKFLOW.md`。

## 接入与语义

- `ActionPreviewRenderer.extract(ExtractLevelRenderStateEvent)`沿用客户端每帧Gizmo collector。使用`Gizmos.line(Vec3, Vec3, int, float)`取代移动箭头，不增加Mixin或改变模拟tick。核对本地`build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`中的完整line签名与collector调用；路径点抬高0.06米以避免贴地重叠。投射轨迹仍使用原显示链。
- `MovementPathLines`按路径三维折线累计长度生成每米长/短虚线，长0.50、短0.20、两个间隔各0.15米；转弯和节点不重置相位，预算边界拆分颜色。灰色终点有小点，避免落在间隔中而不可见。取消了原跳跃装饰折线，米标尺统一跟随规划路径，不声称它是实际跳跃轨迹。
- `MovementPreviewBudget`使用当前移动速度属性，普通地面名义速度为`speed * .98 / (1 - .6 * .91)`，水中名义速度至多`.02 * .98 / (1 - .8)`且移动tick成本乘2，跳跃另加1。核对固定版本`LivingEntity.travelInAir`、`getFrictionInfluencedSpeed`、`travelInWater`；公式仅对应普通摩擦和默认水中参数，非完整物理预测。预算估算预留`min(8, ticks/2)`供起步及现有终点减速，不扣除这部分资源。高差段不足时停在前一节点，不插入空中的停止点。
- `LocalActionPreview.complete`保存完整显示路线与可达累计距离；纯移动超预算时将`TacticalIntent.Target.cell`和`Approach.feet`一起绑定灰色末端。原鼠标目标继续作为本地搜索身份，预算变化使预览失效重算；没有可达段时不提交。攻击/物品接近仍保持原能力目标，不擅自改为纯移动。
- C2S仍只提交选点意图，不新增可信费用或移动许可；`SelectedPositionPlanner.accept`重新核对站立面、区域、路径、目标格与精确坐标，原移动Lease/步进复验/费用/终态保持。等待服务器确认时不显示旧完整路径为全灰；收到投影后显示服务器路线。未增加协议、存档schema或生产探针。

## 默认移动与碰撞拾取修订（2026-09-30）

`ClientTacticalPlan.previewClick` 在本人回合、空闲且未等待选择确认时，将方块左键接入既有精确落点移动预览／提交链。实体及 MISS 不触发默认操作，显式 Targeting 能力继续优先；HUD、Screen、失焦及菜单沿用既有输入分发。服务器路径、资源与执行许可校验不变。

`ClientControl.previewPick` 改用 `ClipContext.Block.COLLIDER`／`Fluid.NONE`，移动拾取、朝向与预选框均穿过无碰撞方块和流体，实体仍按最近接触点遮挡。核对当前 target 的 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`：`ClipContext.getBlockShape` 使用玩家碰撞上下文取得碰撞形状；`BlockGetter.clip` 比较块／流体命中，`clipWithInteractionOverride` 仅在碰撞形状先命中后调整命中面，空形状不会被外形重新挡住。此调用在既有客户端输入／显示线程执行，不新增 Mixin 或取消原版 tick。无碰撞对象及流体不再成为该战术鼠标射线的直接目标。

JDK 25.0.3 下在独立 target 执行 `gradlew.bat clean build --console plain --no-daemon` 成功，日志 `targets/neoforge-26.1/verification-default-move-build.log`。执行 common 测试和既有无窗口 UI 检查；GameTest 仅编译，未启动平台测试或真实客户端。草／火把／水体后方地面、实体遮挡、非本人回合、显式能力及菜单优先级的真实输入验收保留在[02](../02_GAPS_AND_CONFLICTS.md)。

## 验证记录（2026-09-28）

在`targets/neoforge-26.1`设置`JAVA_HOME=C:/Program Files/Java/jdk-25.0.3`执行：

| 命令 | 结果与本地日志 |
| --- | --- |
| `gradlew.bat build --console plain --no-daemon` | 通过；`verification-movement-preview-build.log`。首次沙箱执行因Gradle下载socket权限失败，获准使用本机缓存/网络后重试通过 |
| `gradlew.bat clean build --console plain --no-daemon` | 最终等待确认时的显示修正后通过；`verification-movement-preview-clean.log`。包括common测试、GameTest源集编译及无窗口预览/UI检查；未以此重复声明GameTest运行 |
| `gradlew.bat clean build runGameTestServer --console plain --no-daemon` | 构建及无窗口检查通过，64项GameTest完成，62项通过、2项失败；`verification-movement-preview-final.log`。`action_preview`通过；失败为`interaction_repairs: ordinary block item attack was rejected on tick 35`及`player_item_projectiles: waiting for projectile entity lookup on tick 302`，因此整个命令退出码1 |

无窗口检查覆盖跨节点/转弯米标尺、同一长线内灰红分割、超预算精确截断、零预算、速度变化、水中成本、拒绝半空停止点及不修改原路线。平台检查向既有服务端规划器提交灰色末端，核对精确坐标、可走路径及查询不修改资源；不是实际玩家行走测试。

真实客户端/GPU、键鼠至网络再至实际行走、双客户端与不同地形/摩擦/速度下的停止误差尚未验收。服务端仍可能因费用耗尽或依赖变化提前中断，不能把名义估算解释为精确物理预测或新的米制费用规则。活动项仅登记在[02](../02_GAPS_AND_CONFLICTS.md)。
