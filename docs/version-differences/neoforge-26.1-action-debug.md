# NeoForge 26.1 行为诊断

2026-09-29，Minecraft 26.1 / NeoForge 26.1.2.84，JDK25.0.3。仅此 target；不改 common、规则、费用、协议和其他 target 范围。

## 启动与边界

在游戏 **program arguments** 中加入 `-debug`，不是 JVM 参数。本 target 开发启动使用 `gradlew.bat runClient -PactionDebug=true` 或 `gradlew.bat runServer -PactionDebug=true`，Gradle 转发为同一个 `-debug`。默认关闭。远程联机需在客户端和专服分别开启。

公共类 `cc.sighs.dndturn.DebugDiagnostics` 未启用时立即返回，不求值 Supplier、不初始化 logger；启用后通过 `dndturn.actions` 的 INFO 级别输出 `[DNDTurn/debug]`，不依赖外部 DEBUG 级别设置。可在 `log` 内设置统一条件断点。每条最多2048字符，每进程每秒最多300条，超限数量在后续窗口报告。格式化／后端 RuntimeException 不传播到行为链；限流只影响日志。

检查时 `run/logs/debug.log` 和 `latest.log` 存在；旧 DebugDiagnostics 与旧 debug 调用已在先前批次移除，无残留旧抛出点。本次建立新的定点诊断，保留故障 warn/error 与历史日志文件。战斗日志 GUI 继续为空，不恢复文本拼接、历史订阅、聊天或 S2C 日志输出。

## 断点与分析路径

| 标记 | 位置 | 内容 |
| --- | --- | --- |
| PLAYER_SEND | ClientTacticalPlan.submitLocal / choose | 最终提取的能力／版本、来源、目标、预览位置、operation |
| PLAYER_QUERY / PLAYER_OFFER / QUERY_REJECTED | TacticalActions.selectAndDiscover / discoverInternal / discoveryFailure | 选择修订、槽位、候选、需接近标记、失败码和原因 |
| MOB_DECISION / MOB_DECISION_FAILURE | MobTurnStrategies.Decisions.next / ServerCombatService.advanceMobTurns | 策略／版本、actor实例、会话版本、提议／结束／等待及异常原因 |
| REQUEST / GATE_ACCEPTED / SUBMIT_FAILURE | TacticalActions.submit | 来源、世代、会话、operation、目标、资源、路径与门禁失败 |
| REPLAY_RESULT / REPLAY_PENDING | TacticalActions.submit | 已完成／待决重试，不是重新执行 |
| EXECUTE / STEP_ACCEPTED / BEHAVIOR_ACCEPTED / EXECUTION_FAILURE | TacticalActions.execute / beginStep / accept / tick | 到位复验、步骤父子关联、接受和异常 |
| ATTACK_ACCEPTED / PROJECTILE_ATTACK_ACCEPTED | ServerCombatService 的近战／箭命中入口 | 投掷operation与父计划或投射来源root关联 |
| ATTACK_ROLL_OBSERVATION | ServerCombatService.attackTrace | 模式、两骰、选定骰、总值、AC、命中、暴击、基础伤害、减伤、战术伤害、接受、吸收／生命损失、观察阶段 |
| TERMINAL | TacticalActions.finish | 世界效果、释放结果、账本终态、失败码／重试策略、原因、已结算移动与伤害 |
| CONTROL_REQUEST / CONTROL_RESULT | CombatIntentHandler.handleIntent / CombatNetwork.sendIntentStatus | 开始、退出、移动、结束回合等控制请求及反馈 |

按 `op` 关联，再沿 `parent`／`root` 查看攻击。候选可用和 GATE_ACCEPTED 不代表行为成功。ATTACK_ROLL_OBSERVATION 可对同一次投掷输出准备与事后观察，不代表重掷，不能累计重复伤害；PREPARED的accepted=false不是最终失败。箭沿用发射时捕获的骰点。诊断不另取随机数、不为日志调用能力查询／执行。没有逐帧输出悬停或全部Mob调度跳过分支。

## 固定版本与验证

读取实际生成的 `build/moddev/artifacts/minecraft-patched-26.1.2.84-sources.jar` 中客户端 `net.minecraft.client.main.Main` 和服务端 `net.minecraft.server.Main` 完整启动方法。两者均为启动线程的 `public static void main(String[])`，无override/super；客户端 OptionParser 允许未知选项，服务端不允许。必需Mixin以 HEAD ModifyVariable 消费精确 `-debug`，保留其他参数及后续初始化／早退，不取消main、不绕过EULA。未新增世界tick／行为接入。

- 最终 `gradlew.bat build --console plain --no-daemon` 成功，日志 `targets/neoforge-26.1/build/verification-action-debug-final.log`。独立 `verifyDebugDiagnostics` 检查默认no-op、精确参数消费、惰性转发与格式化异常隔离；现有无窗口UI检查通过。common测试为UP-TO-DATE，GameTest仅编译。测试类处于uiTest源集，不进入生产jar。
- 隔离 `build/debug-startup-smoke` 中执行 `runServer -PactionDebug=true` 并追加 `--help`，实际输出服务端DEBUG_ENABLED及原版帮助，证明服务端Mixin注入／参数消费生效。随后FML包装器因help未创建服务器线程抛出 `Couldn't find Minecraft server thread`，runServer返回1；不是完整开服通过。日志 `build/verification-action-debug-enabled.log`。
- 未启动真实客户端、实战玩家／Mob链、双客户端或GameTest；不能据此判断实际行为符合预期。客户端Mixin注入及实战日志待验收，统一登记02。
