# NeoForge 26.1.2.84 恢复审计

## UNKNOWN 合约

`CombatEngine.recordRestoredUnknown` 只登记无法核对的恢复证据，不执行伤害、不授权行动、不扣资源。`targetId == null` 表示会话级证据缺失；非空目标仍须属于该会话。已有 operation ID 的相同负载返回原结果，冲突负载拒绝；结果构造成功后才提交版本与历史。

2026-09-26 00:22:19 的开发日志中，`ServerCombatService.auditRestoredEncounters` 在区域或成员无法核对时传入 null 目标，而旧校验无条件要求目标属于成员集合，导致 UNKNOWN 尚未发布、会话尚未释放就抛出异常。修复保留无目标事实，不伪造攻击目标，不删除用户存档。

## 固定版本接入与时机

依据 `minecraft-patched-26.1.2.84-sources.jar`：`ServerLevel.tick(BooleanSupplier)` 在服务器线程读取 `TickRateManager.runsNormally()`；冻结期间并非所有世界容器维护都停止。`ServerLevelSimulationStepMixin` 在该方法 HEAD 调用 `beforeLevelTick`，桥接不取消世界 tick。

恢复审计在 `runsNormally() == false` 或 debug 世界时保持待审计，不发布失败或释放会话。可执行世界 tick 才核对成员、区域及待决证据；不强制加载区块。候选回合的存档也须接受审计，因为恢复未就绪时玩家输入被阻止，不能等待玩家先结束回合才恢复。这里的调度机会指世界可以执行环境模拟的边界，不要求存档的 Encounter 已处于 ENVIRONMENT 阶段；它不保证登录或区块加载一定已完成。

证据无法核对时记录 UNKNOWN 后释放会话；存活箭的持久隔离继续保留。已归档结果再次加载不得重建会话或重放效果。

## 回归范围

- common `restoredEncounterUnknownAcceptsNoTargetAndRemainsIdempotent`：无目标 UNKNOWN、资源不变、重复请求、冲突拒绝无部分提交、结束后值快照恢复。
- GameTest `dndturn:repair_values`：通过生产恢复构造器及 `beforeLevelTick` 检查冻结不裁决、缺失成员 UNKNOWN/释放、SavedData JSON 往返、再次审计幂等及箭隔离保留。服务实例隔离，不替换运行中的全局服务。
- 此测试直接调用生产 tick 桥接入口，不等同于真实客户端重进世界或磁盘崩溃窗口验收；剩余验收统一见 `../02_GAPS_AND_CONFLICTS.md` 的 G16。

本轮执行：四个 target 独立 `clean build`，JDK 25.0.3 / 21.0.11，隔离构建输出；common 55 项测试通过。`runGameTestServer --tests dndturn:repair_values` 通过（由临时 Gradle init script 配置程序参数），报告 `build/recovery-work/recovery.xml`。完整 GameTest 两轮未全通过：`scheduled_tick_hold` 均失败，第一次另有 `zombie_target_invalidated` 场地未就绪，第二次该项通过。完整输出在 `build/recovery-work/neo.log`、`neo-recheck.log`，未将这两轮记为整体通过；held tick 剩余问题见 G09。
