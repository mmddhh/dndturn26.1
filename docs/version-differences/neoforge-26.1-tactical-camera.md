# NeoForge 26.1 战术镜头控制

文档同步说明（2026-09-28）：本文保留镜头入口退役时的构建证据；后续整套构建／平台结果见[物品记录](neoforge-26.1-player-items.md)，不替代[CAM-01](../02_GAPS_AND_CONFLICTS.md#cam-01)的真实地形验收。本次没有重新运行客户端或测试。

2026-09-28 按用户要求移除回合制内切换第一人称／角色控制的链路与 GUI。仅影响 NeoForge 26.1 客户端，无 common、协议或服务端规则变更。

## 固定版本与接入

依据 target `gradle.properties` 和 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar`，固定 Minecraft 26.1 / NeoForge 26.1.2.84。

- `TacticalCameraMixin` 保留 `Camera.alignWithEntity(float)` TAIL 注入：原版完成实体视角、第三人称距离及睡眠分支后，当前本机相机由战术姿态覆盖并设为 detached；不取消原版维护，不改真实身体。此次仅删除 `ClientControl.camera` 的可切换模式条件，仍核验会话、姿态、主相机及实体实例。
- `ClientAmbientSamplingMixin` 保留 `Minecraft.tick` 中 `ClientLevel.animateTick(int,int,int)` 调用重定向：移除模式条件，会话内按已加载的战术相机中心替换采样中心，仍只调用一次原有采样，不增加身体或环境 tick。
- 删除模式枚举、角色输入接收者、切换 API、反引号默认绑定、角色跳跃绑定及转发，以及 pointer GUI 的 DOM、样式、监听和布局更新。镜头平移/旋转/缩放、Home/O、目标式计划和鼠标朝向继续使用原入口；退出会话仍交还原版输入。

## 验证边界

本轮使用 JDK 25.0.3，在该 target 执行 `gradlew.bat clean build --console plain --no-daemon` 成功，common 测试实际执行通过，GameTest 源集编译通过但未运行 GameTest 服务端。已检查被删链路无残留引用、中英文 JSON 可解析及 `git diff --check` 通过；构建仍有既有弃用 API 警告。

待真实客户端验证：原切换键无效且控件/键位项消失；回合制内保持战术镜头，WASD/QE/鼠标仅按既有合同控制；目标移动与鼠标朝向正常；菜单/聊天/同意弹窗/失焦不穿透；退出回合制恢复原版视角和输入。编译不等于上述实机验证，既有 CAM-01 地形问题保持未解决状态。
