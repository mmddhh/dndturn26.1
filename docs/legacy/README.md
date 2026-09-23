# 开发文档入口

当前开发阶段为 **M3 acceptance**。功能缺口、规则疑点与待验收场景统一维护在 [M3 验收与功能缺口](M3_ACCEPTANCE.md)，不保留旧快照的完成宣称或累计通过次数。

| 文档 | 职责 |
| --- | --- |
| [游戏规则](DECISIONS_RULES_CHECKLIST.md) | 稳定 D/R/H/X 编号、有效玩法与明确修订；不记录实现进度 |
| [架构决策](COMBAT_ARCHITECTURE_DECISIONS.md) | 设计理由与职责边界 |
| [M3 验收与功能缺口](M3_ACCEPTANCE.md) | 唯一缺口清单，区分源码问题、功能缺失、规则待定与运行待验收 |
| [26.1 固定版本接入](version-differences/neoforge-26.1.2.84-seams.md) | 原版类/方法、注入位置、取消语义和验证场景 |
| [维护工作流](MAINTENANCE_WORKFLOW.md) | 协作、评审、构建与发布约定 |
| [CI target 发现](CI_TARGET_DISCOVERY.md) | CI 描述文件与矩阵生成 |
| [Maven 发布](PUBLISHING.md) | 对应 target 的 Maven 产物和凭据配置 |

原始规则见 [dndrule.txt](dndrule.txt)，后续显式修订以游戏规则文档为准。程序约束见根目录 [AGENTS.md](../AGENTS.md)。

[版本迁移参考](version-differences/README.md) 与 [旧版源码](dndturn-main/README.md) 仅供定向查阅，不是当前开发任务或实现依据；原版接入以目标固定依赖及其 patched 源码为准。
