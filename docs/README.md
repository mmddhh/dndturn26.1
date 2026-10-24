# 开发文档

从[当前状态](00_CURRENT_STATE.md)了解实际实现与验证，再按[路线](02_ROADMAP.md)选择剩余切片。field 源提交已在当前 `dev_` 历史中，活动文档和归档已存在，不重复合并、安装或搬迁。

| 文档 | 唯一职责 |
| --- | --- |
| [00_CURRENT_STATE.md](00_CURRENT_STATE.md) | 当前基线、实现边界、既有证据、本次检查及未运行范围 |
| [01_RULES_AND_CONTRACTS.md](01_RULES_AND_CONTRACTS.md) | 有效玩法、已确认边界及暂缓范围 |
| [02_ROADMAP.md](02_ROADMAP.md) | 阶段状态、剩余任务、依赖与关闭条件 |
| [03_PROMPTS.md](03_PROMPTS.md) | 基于当前事实执行单个切片的提示词 |
| [04_TEST_POLICY.md](04_TEST_POLICY.md) | 长期不变量、测试维护及按影响运行政策 |

根[README](../README.md)提供支持声明、构建与 AutoJS 调试入口；根[AGENTS](../AGENTS.md)维护长期工程约束，不积累阶段进度。

[legacy](legacy/README.md)只追溯历史。旧 docs 位于该目录及 `pre-refresh-20261008/`，旧根 README/AGENTS 位于后者 `_root/`；历史正文不随当前实现改写，旧“当前/唯一清单”不再有效。归档入口可修正导航，正文断链不作为恢复旧规则的理由。

新决定写合同，完成事实写状态，剩余任务写路线，执行提示同步引用有效规则。每次收尾只更新涉及项，不再追加互相覆盖的阶段流水、不另建 gap 表或日期验收清单。文档重写也保留既有未提交成果和真实验证限制。
