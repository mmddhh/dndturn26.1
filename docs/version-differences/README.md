# 固定版本接入与验证索引

当前细化证据集中在 NeoForge 26.1，固定依赖 `26.1.2.84`；以 target 参数、实际解析产物和对应源码为准。本目录不是跨版本通用 API 保证。

| 记录 | 范围 |
|---|---|
| [战斗结构接入](neoforge-26.1-combat-structure.md) | 伤害前事件、反应资源、过程 owner、移动端口、facet、AI 记忆与类型化观察；包含明确未完成范围 |
| [包边界与状态所有权](neoforge-26.1-package-ownership.md) | domain／application／platform、Effect 词汇、运行与发布／调度 owner、架构约束及迁移验证 |
| [Mob 标准化与装备弓箭](neoforge-26.1-mob-standardization.md) | 标准组效果、装备政策／代次、有限原生发射、AI 家族与 Skeleton／独立非 Skeleton 闭环 |
| [门禁与近战目标](neoforge-26.1-gate.md) | Effect 策略、类型化执行准备、受击注册所有权、Mixin／helper 审计与斧头攻击苦力怕回归 |
| [Tactical Effect与回归](neoforge-26.1-tactical-effects.md) | 实例／授予修订、原生效果投影、纯反应波次、schema4两进程恢复、80项common与三种子各五批54项GameTest；全清单仍未完成 |
| [玩家物品](neoforge-26.1-player-items.md) | 使用分类、持续过程、实体生成、投射／鱼竿预览及81／63项验证批次 |
| [环境与Mob](neoforge-26.1-environment-mob.md) | 有效时间、药效／火焰、事实发现、近战扩展及历史80／61项验证批次 |
| [战术镜头](neoforge-26.1-tactical-camera.md) | 统一镜头模式与原第一人称入口退役 |
| [鼠标朝向](neoforge-26.1-cursor-facing.md) | 拾取、朝向同步与位置纠正 |
| [日志与GUI](neoforge-26.1-log-gui.md) | 空日志面板、AUI布局／更新、移动力环与无窗口验证 |
| [参与者头像](neoforge-26.1-participant-portraits.md) | 原版实体渲染预览、环境图标及生命周期 |
| [玩家为中心的跟随场地](neoforge-26.1-player-centered-field.md) | **用户明确要求**：场地以玩家为锚点、半径35、跟随玩家、允许越界、仅玩家+仇恨实体入场；覆盖 03 §4/§5 |

当前协议／schema集中见[01实现事实](../legacy/01_IMPLEMENTED_DECISIONS.md)，活动状态只见[02](../02_GAPS_AND_CONFLICTS.md)。各记录保留当时的命令和测试数量；后续整套构建通过不替代早先未执行的真实客户端场景。

Forge／Fabric 1.20.1 和 NeoForge1.21.1 的本轮共享代码兼容构建见Tactical Effect与回归记录；仅证明构建兼容，不启用其战术功能，也不声称覆盖所有版本迁移差异。
