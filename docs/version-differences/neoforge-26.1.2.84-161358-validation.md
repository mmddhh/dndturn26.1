# 161358 修订验证记录

适用 Minecraft 26.1 / NeoForge 26.1.2.84 工作树。保留任务开始前已有修改；未发布或提交。规则修订见 `../03_PLAYER_RULES.md` 和 `../legacy/COMBAT_ARCHITECTURE_DECISIONS.md`，剩余验收只在 `../02_GAPS_AND_CONFLICTS.md` G35 维护。

## 修改边界

- common `CombatEngine.leave` 区分尚未执行世界行为的计划中断与未知副作用；已发布结果保持不可变。`TacticalPlanTest` 验证恢复、去重、未确认子操作与退出。
- `ServerCombatService.attackPlan` 在死亡/离场清理前提交已确认 ATTACK 和 PLAN；`TacticalActions`/`TacticalNetwork` 的根终态投影来自账本。恢复审计探针不再接管运行服务的计划 tick 队列。
- `ClientTacticalPlan` 为物品、手别、行为、目标、菜单、取消和执行的唯一交互 owner；`ClientControl`/`CombatControls`/`TacticalOverlay`、输入 Mixin 与 HTML/CSS 消费同一状态。数字键/UI 热栏统一协议，旧公共攻击/移动包明确拒绝；历史重试先查询账本。协议 15 要求两端匹配。
- `TacticalImpact` 与方块使用 Mixin 在已核验的直接写入范围复验域、加载、保护和碰撞；未知 override 明确不支持。完整支持边界见 gameplay-chain 文档。

## 工具与隔离

NeoForge 26.1 使用 JDK 25.0.3；其他 target 使用 JDK 21.0.11；common 的 Gradle toolchain 为 Java 17。

原默认输出有用户运行中的 `build/live-loop/server`，普通 clean 因文件占用失败。后续使用根 `build/161358-validation.gradle` 将本次构建与世界重定向到 `build/161358-validation/`；兼容矩阵使用 `build/161358-matrix.gradle`。仅停止本次隔离验证进程，没有停止用户实例。init script、日志和世界均为本地忽略产物。

在 `targets/neoforge-26.1` 执行：

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-25.0.3'
.\gradlew.bat -I ../../build/161358-validation.gradle clean build :common:test runGameTestServer
.\gradlew.bat -I ../../build/161358-validation.gradle runServer
.\gradlew.bat -I ../../build/161358-validation.gradle runClient -PdndturnControlProbe -PcontrolProbeActive -PcontrolProbePeer -PcontrolProbeRequirePeer
.\gradlew.bat -I ../../build/161358-validation.gradle runClient -PdndturnControlProbe -PcontrolProbeActive -PcontrolProbeRequirePeer
```

两条 client 命令启动两个真实 Minecraft 进程，连接隔离专服端口 25575。输入探针调用真实键鼠回调、渲染镜头拾取和 AUI 鼠标事件；它不是物理 OS 按键自动化。方法级 `planMethodTick` 保留，但本次 active probe 使用 raw `planTick`。测试专用乱序探针重投递实际收到的 Options/运行投影，经原客户端接收函数处理，不伪造成功状态。

## 实际证据

- `build/161358-before.log`：新增致死回归先失败，ATTACK COMPLETED 而 PLAN UNKNOWN。修复后该断言通过；随后增补确定性未命中/非致死、动作消费、原请求重试与终态 codec/账本一致性。
- common XML 报告共 58 项、0 failures、0 errors。包括 9 项 TacticalPlanTest；这是值状态测试，不能证明真实崩溃恢复。
- 其他 target 分别在自身目录以 JDK 21 运行 `gradlew.bat -I ../../build/161358-matrix.gradle clean build`，日志 `161358-forge-final.log`、`161358-fabric-final.log`、`161358-neoforge121-final.log` 均 BUILD SUCCESSFUL。
- `161358-final-tests.log`、`161358-final-recheck2.log`：46 项 required GameTests 通过。
- 最终 `161358-delivery.log`：隔离 `clean build :common:test runGameTestServer` 通过，58 项 common 测试和 46 项 required GameTests 通过；包含新加的未命中/非致死/致死近战断言。
- `161358-client10.log` / `161358-peer10.log`：两份 PASS。覆盖真实输入 1→2→3、Esc/Space、菜单关闭及菜单项点击、平地 MoveTo、接近攻击、一次 dirt 放置与扣费、动作耗尽后免费拉杆、镜头独立与远端角色位置收敛。取消后迟到重复 Options、终态后迟到重复运行投影未恢复选择或身体驱动。

## 失败记录与证据限制

- 早期双客户端测试在先攻重排后立即结束非本人回合，被正确拒绝；夹具改为等待权威当前成员。放置射线曾命中测试 peer，调整夹具站位后通过，未绕开实体拾取。
- `161358-clean-final.log` 有两项失败：tactical_behaviors 的新实体尚未进入 level 查询，以及 scheduled_tick_hold。实体夹具现在等待实际注册并在物品测试期间放在发现范围外；不能用固定延迟冒充已注册。
- `161358-final-recheck3.log` 的新增近战测试通过，但既有 arrow_pending_removal 间歇失败。scheduled_tick_hold 在后续运行通过，但本轮尚未证明这些既有测试在所有随机测试场地/调度下稳定。保留日志，不把多次重跑描述为每次全绿。
- 任务要求的 1.1–1.6 全部“先失败再修复”流程未完整做到：有明确修复前失败记录的是致死结算和 common 离场分类，其余回归大多与修复一起添加。不能补造先失败证据。
- 双客户端覆盖平地核心链路和应用层重投递，不是传输层延迟/丢包模拟，也不代表复杂障碍、全部取消阶段、所有生命周期、真实重启或第三方保护模组验收；见 G35。
