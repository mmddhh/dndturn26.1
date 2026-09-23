# 合并修复执行记录

2026-09-25 开始；基线为已有未提交改动的工作区，未回退用户文件。

## 已确认的规则修订

- 主动 EXIT 仅在实体中心离开所属活动场地后允许；场内拒绝保留成员与 UI。生命周期清理独立。
- UNKNOWN 的不确定存活箭持久隔离，证据明确前不重放、不释放原版、不自动销毁。
- 本人合法回合可免费整理自身存储/装备及切换热栏；不包括丢弃、外部容器、合成、使用物品或特殊移动。

## 当前工作

已实施恢复候选预检、就绪投影修订、typed START、场内 EXIT 拒绝、关服不重建、订阅撤销、完整 BodyState、EMPTY 输入位移收费、物品入口门控和 ACK、UNKNOWN 箭隔离、实体跟踪暂停投影。
已抽取入站 handler、投影订阅与发送重试、恢复预检和实体投影；Consent/Scheduler 依赖缩小为各自能力；查询与模拟准备分离；共享阶段类型独立；操作时间统一为 epoch＋累计服务器 tick。
schema：target 5 / common 3；网络 12。旧时钟 epoch 保持 null，不假定为新时间。Bounds 仅留历史 DTO；活动会话必须有精确区域。
四 target 隔离 clean build 与无 UI GameTest 启动已完成。A08 已缓存规则快照与逐会话历史编码；仍不以无规则 TTL 遗忘有效重试。
本记录仅保存执行证据；未关闭缺口统一见 ../02_GAPS_AND_CONFLICTS.md。

## 本次验证

- 已确认实际固定依赖 26.1.2.84，patched sources 位于 target/build/moddev/artifacts。
- 已确认本机 JDK 17、21、25、26；构建指定 JDK 25，common 保持 Java 17。
- JDK 25：`:common:test compileJava` 通过；当前 common 48 项通过。
- 第一次 GameTest：39/39 必需测试通过。添加 EMPTY 输入、ACK、容器 THROW、自身热栏及恢复/失败 START 回归后：41/41 必需测试通过（2026-09-25 19:36）。
- 区域模型迁移曾使一个 common 测试因对象身份比较失败；现改为完整持久化值比较后通过。共享阶段迁移曾有遗漏引用编译失败，已修正。
- `-PdndturnUiSmoke -PdndturnRepairClient runClient` 通过；新建隔离世界，真实 client/integrated server 启动；UI 原生点击、焦点、鼠标与销毁探针 PASS。不是双客户端联机证据。
- 尚未完成普通 dedicated 双客户端、真实重启/崩溃、七游戏日与长时压测；不以已有测试替代。

## 收尾复核与命令

- 追加修正：未协商通道不推进结果游标；Outcome 使用固定编码；关服异常继续释放其他租约/held 条目；旧玩家/Level 实例排队回调拒绝；成员中心越界后原版特殊移动/交互仍受能力门控。
- START 先提交回执再接管队列，接管失败进入非交互审计并记录错误；合并规划放在世界推进前。曾尝试延后队列接管，被 `scheduled_tick_hold` 待保存标记回归发现，恢复即时接管后 41/41 再次通过。
- 客户端暂停保留原版远端网络插值和逐乘客维护，不推进身体模拟；真实双客户端仍未验证。
- 常规 Forge `clean build` 被占用的 common jar 阻止。发现已有 live-loop Java 进程，未停止其进程、未启动普通 dedicated。随后采用只改变输出路径的 Gradle init script：`build/repair-verification/init.gradle`，避免删除或覆盖在用产物。
- 各 target 独立执行 `gradlew.bat -I ../../build/repair-verification/init.gradle clean build --console=plain`：Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1 使用 JDK 21，NeoForge 26.1 使用 JDK 25，全部成功；各自 common Java 17 测试通过。
- NeoForge 26.1：`gradlew.bat -I ../../build/repair-verification/init.gradle -PdndturnRepairServer clean build runGameTestServer --console=plain` 于 19:57 完成，48 common、41 必需 GameTest 通过；runtimeClasspath 排除 AUI/Rhino。此参数只用于物理侧启动检查，不改变正常发布依赖。
- 四个 target jar 检查：各含唯一 loader metadata 与共享 EncounterPhase 类，无重复 ZIP 路径；26.1 的全部声明 Mixin class 存在，required=true/defaultRequire=1。
- 隔离 UI smoke 曾因输出系统属性仍指向固定 build 路径导致 Gradle 找不到 PASS 文件；客户端实际已输出 PASS。已统一使用 layout.buildDirectory，重新运行确认，不把那次 Gradle 失败写成成功。
- 最终 UI 复验：`gradlew.bat -I ../../build/repair-verification/init.gradle -PdndturnUiSmoke -PdndturnRepairClient runClient --console=plain` 于 20:00 成功，完整探针 PASS，包含新增客户端 Mixin 的实际类加载。日志与 PASS 副本保存在忽略提交的 `build/repair-verification/evidence/`。

## 任务书逐项对应

| 项目 | 实现与证据范围 |
| --- | --- |
| F01 | 就绪标记先提交，独立 projectionRevision，reducer 保留游标；真实恢复交互见缺口。 |
| F02 | CombatRecoveryCandidate 安装前校验、失败丢弃候选且保留 JSON；repair_values。 |
| F03 | typed START、回执先于平台准备；start_failure 与既有同意/重试 GameTest。 |
| F04 | 用户明确中心越界规则；场内拒绝及越界 EXIT GameTest，双客户端待验。 |
| F05 | 关闭 tombstone、纯 payload 出站与异常清理；GameTest/集成客户端正常关闭路径，真实连接关服待验。 |
| F06 | 隔离证据持久化及所有箭效果门控，Codec 往返；真实崩溃/实体卸载待验。 |
| C01 | EMPTY 按键不能免费，保留零位移/强制证据分支；真实水流混合归因仍有覆盖缺口。 |
| C02 | 订阅组件发送前授权，离开撤销，个人重试分离，历史 consent recipient 最小通知；真实包观测待验。 |
| C03/C04 | 线程交接后输入能力、容器 THROW 拒绝、热栏允许、预测 ACK/纠正；GameTest，真实客户端操作矩阵待验。 |
| C05/C06 | 完整 BodyState、实例生命周期、跟踪实体投影、普通/乘客与插值分离；双客户端待验。 |
| C07 | Dist.CLIENT 分组、metadata 及无 UI 物理 GameTest 启动；普通 dedicated 按用户选择未启动。 |
| A01 | 纯查询与实际执行准备分离，同步步决策缓存；GameTest 执行链回归。 |
| A02/A03 | 订阅、跟踪投影、恢复候选、入站 handler 提取，Consent/调度缩小权限；移动/投射物仍在 service。 |
| A04 | epoch＋累计观察 tick、schema 迁移明确旧时钟未知。 |
| A05/A06 | 活跃精确区域单模型、伤害重载一致、endTurn 收窄、共享阶段/启动类型；四 target 构建。 |
| A07 | 固定协议码、独立 fromIndex、typed START、投影修订；common 与 target 协议回归。 |
| A08 | 规则/逐会话编码缓存、保留合法重试/因果引用；独立归档与增长压测仍见 G17。 |

正常 tactical/网络/会话循环、既有 Zombie 统一选择与共享鼠标管理保留。未发布、推送或回退用户原有改动。剩余项目集中在缺口文档，以上不表示任务书的全部运行验收已经关闭。
