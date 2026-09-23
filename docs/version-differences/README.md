# 固定版本接入与验证索引

当前细化证据集中在 NeoForge 26.1，固定依赖 `26.1.2.84`；以 target 参数、实际解析产物和对应源码为准。本目录不是跨版本通用 API 保证。

| 记录 | 范围 |
|---|---|
| [玩家物品](neoforge-26.1-player-items.md) | 使用分类、持续过程、实体生成、投射／鱼竿预览及81／63项验证批次 |
| [环境与Mob](neoforge-26.1-environment-mob.md) | 有效时间、药效／火焰、事实发现、近战扩展及历史80／61项验证批次 |
| [战术镜头](neoforge-26.1-tactical-camera.md) | 统一镜头模式与原第一人称入口退役 |
| [鼠标朝向](neoforge-26.1-cursor-facing.md) | 拾取、朝向同步与位置纠正 |
| [日志与GUI](neoforge-26.1-log-gui.md) | 空日志面板、AUI布局／更新、移动力环与无窗口验证 |
| [参与者头像](neoforge-26.1-participant-portraits.md) | 原版实体渲染预览、环境图标及生命周期 |

当前协议／schema集中见[01版本说明](../01_IMPLEMENTED_DECISIONS.md#baseline)，活动状态只见[02](../02_GAPS_AND_CONFLICTS.md)。各记录保留当时的命令和测试数量；后续整套构建通过不替代早先未执行的真实客户端场景。本次同步核对既有日志，不生成新的通过记录。

Forge／Fabric 1.20.1 和 NeoForge1.21.1 的最新共享代码兼容构建见玩家物品记录；仅证明构建兼容，不启用其战术功能，也不声称覆盖所有版本迁移差异。
