# NeoForge 26.1 鼠标朝向

文档同步说明（2026-09-28）：下文54项平台测试为朝向修复批次记录；后续整套结果见[物品记录](neoforge-26.1-player-items.md)，不替代[AIM-01](../02_GAPS_AND_CONFLICTS.md#aim-01)真实输入验收。物品刷取新增的服务端射线桥仅在该行动作用域使用当前朝向，与本文客户端拾取／旋转链分别负责；本次未重跑测试。

固定依赖为 Minecraft 26.1 / NeoForge 26.1.2.84；核对当前 target 的 `gradle.properties` 与 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`，不涉及 common 或其他 target。

## 接入合同

- 客户端 `LocalPlayer.aiStep()` HEAD：在 `LivingEntity.baseTick` 捕获旧角度后、原版输入和移动计算前，由 `ClientControl.updateFacing` 读取当前鼠标拾取的接触点并写 yaw/pitch。仅本机存活、非睡眠玩家的战术镜头模式生效；原有拾取门禁排除 Screen、同意弹窗、失焦、HUD 和相机实体替换。MISS 保留朝向，不使用射线末端或目标格中心冒充接触点。实体沿用拾取包围盒的 0.1 容差。
- `Player.aiStep()` 随后更新头部，`LivingEntity.tickHeadTurn(float)` 按原版 50° 上限带动身体并维护角度跨界。没有直接写身体角度/旧角度，没有使用会同时重置身体插值的 `LivingEntity.lookAt`，没有补跑身体 tick。自动寻路输入仍按更新后的 yaw 投影。
- `LocalPlayer.tick()` 在正常身体 tick 后调用 `sendPosition()`，沿用原版 `Rot`/`PosRot` 和变化检测。暂停身体时不额外驱动旋转或模拟。
- 服务端 `ServerGamePacketListenerImpl.handleMovePlayer(ServerboundMovePlayerPacket)`：现有注入位于 `PacketUtils.ensureRunningOnSameThread` 之后；移动受限时仅在客户端已加载、非 wonGame、存活且未睡眠的条件下接受合法旋转，调用原版 `absSnapRotationTo`，不接受位置、落地或碰撞字段。纯转头及与权威坐标一致的位置包不触发纠正；不同坐标仍纠正并取消。NaN 坐标或非有限角度留给原版断线检查；无限坐标在原版会被 clamp，故仍必须由移动门禁拦截，不能放行。
- `ServerEntity.sendChanges()` 发送原版旋转及头部包；`ClientPacketListener.handleMoveEntity` 和头部处理保留原版远端插值。服务端与客户端都沿用玩家身体更新，不借表现层改姿态。旋转不授予攻击、物品使用或移动许可。

## 验证范围

### 2026-09-28 瞄准时序审计与修复

核对同一 patched sources 中的完整实现：

1. `LocalPlayer.sendPosition()` 除旋转变化外，每 20 tick 还发送位置提醒。`ClientPacketListener.handleMovePlayer` 应用纠正后发送 `AcceptTeleportation`，紧接着发送 `PosRot`。旧门禁在确认清除 `awaitingPositionFromClient` 后又对相同位置发送 teleport，形成重复纠正。
2. 原版绝对位置包经 `PositionMoveRotation.calculateAbsolute` 同时覆盖当前和旧 yaw/pitch；延迟到达会回写旧瞄准。现在仅本模组 `dndturn$correctMovement` 改用 `PositionMoveRotation(position, ZERO, 0, 0)` 加 `Y_ROT/X_ROT` 相对标记，仍纠正位置、速度并保留握手。原版合法传送／强制转向保持原语义，不在客户端屏蔽服务端位置包。
3. `Minecraft.pick(float)` 在 tick 和 `GameRenderer.update` 后各有调用，原版委托 `LocalPlayer.raycastHitResult` 从 camera entity 身体位置及插值角度拾取；不会自动使用 detached Camera 的屏幕鼠标射线。新增 `TacticalMinecraftInputMixin` 的 `pick` HEAD 可取消注入，仅战术相机持有时更新 `hitResult/crosshairPickEntity` 后取消该次原版拾取，无额外 tick，无攻击权限。原方法无 override/super 链；取消发生在 profiler push 前，无未配对栈。HUD/Screen/失焦等无有效拾取时清空显示，非战术相机不取消。
4. `Camera.update` 在 `alignWithEntity` 后才刷新渲染缓存；tick／鼠标回调可能早于该阶段。`previewPick` 改用 rig 自己的 yaw/pitch，四元数按固定版本 `Camera.setRotation(float,float,float)` 的 YXZ 公式构造，不再混用当前 rig 位置与上帧 Camera 旋转。朝向、预览、点击、预选框共用此算法，不把旧拾取结果缓存为下一次输入。保持现有 FOV／实体拾取范围，未扩大其他模组镜头适配承诺。

`CursorFacingChecks` 由 `movementLeaseExit` 调用，通过 embedded channel 检查相同位置无纠正、非法位移仍纠正、相对旋转保留延迟期间的新角度及旧插值角度、确认后的 PosRot 不产生新纠正，以及后续非法位移仍被保护。它使用原版包计算验证客户端角度合成，但不运行真实客户端。

本次时序修复验证：JDK 25.0.3，在 NeoForge 26.1 target 执行 `gradlew.bat build runGameTestServer --console plain --no-daemon`，构建成功、54 项 required GameTest 全部通过，包含新增包级回归。common 测试为 UP-TO-DATE，本次未重新执行；未运行 clean、真实客户端或双客户端。沙箱首次执行因依赖网络权限失败，随后使用获准的本机缓存／网络环境完成。日志为本地 `build/aim-verification.log`。客户端 `pick` 注入编译通过不等于实机注入／黑框验收通过，剩余范围见 [AIM-01](../02_GAPS_AND_CONFLICTS.md#aim-01)。

### 先前鼠标朝向接入验证（历史记录）

`LocalTimeGameTests.movementLeaseExit` 增加无移动 Lease 的 Rot/PosRot 检查：角度接收、非法位移拒绝、无限坐标不绕过门禁、移动资源不变，然后继续原有移动 Lease 退出场景。

2026-09-28 使用 `C:/Program Files/Java/jdk-25.0.3`，在该 target 执行 `gradlew.bat build runGameTestServer --console plain --no-daemon` 成功，54 项 required GameTest 全部通过，包含上述新增断言。common 测试为 UP-TO-DATE，并未重新执行。日志为 target 的 `build/facing-verification.log`（本地产物，不提交）。`git diff --check` 通过。

真实双客户端尚未运行：需覆盖地面/侧面/实体接触点、高低差、±180°跨界、原地头身50°跟转、移动时瞄准、其他玩家观察及重新跟踪、HUD/菜单/失焦/退出回合制。角色模式切换已按后续用户要求移除，见 [战术镜头控制](neoforge-26.1-tactical-camera.md)。服务端 GameTest 不能替代客户端 Mixin 注入和实际网络渲染验收。
