# DNDTurn 文档核查与后续修复记录

日期：2026-09-25。基线：DNDTurn-sources(20260925-120401).zip。

## 应用方式

本包提供完整修改文件，不是 diff。将 `docs/`、`targets/` 按相同相对路径覆盖到 **120401 基线**。若本地已有后续修改，先比较对应文件，不整文件覆盖新改动。包内不包含未修改的源码、依赖或构建产物，需在原仓库使用。

正常战术入口保持接通，未添加开发开关。common 生产代码未修改，其他 target 未修改。

## 本次代码修复

1. `VanillaInputPolicy`：SWAP 进入原版溢出插入分支前，按交换后的服务端库存计算完整容量。覆盖源槽拆分、副手、已有同组件堆栈以及已损坏装备的空槽需求。没有容量则取消并由原有路径回发库存。允许正常无溢出交换，不用关闭所有装备整理规避问题。未扩展拖拽/双击收集，也不宣称覆盖其他模组装备回调。
2. `CombatNetwork` / `ClientCombatState`：缺页和尾页补收共用 resultSync 工厂，规则版本与非零 fromIndex 分离；协议布局不变。
3. `ServerCombatService`：构造器接受隔离 SavedData 的内部重载；GameTest 专用入口受服务器类型/线程约束，不注册或替换生产服务。移除重复 capturedSettings 检查，候选校验仍由 CombatRecoveryCandidate 统一承担。
4. `RepairGameTests`：坏存档经过生产构造器，检查不安装成员、不覆盖 JSON、不能 START；合法存档检查实际安装和隔离箭记录。新增非零游标编解码断言。
5. `LocalTimeGameTests`：在已有授权玩家连接场景调用背包回归，覆盖热栏/副手来源、满包拒绝、空槽允许、源槽拆分后可合并，并检查未生成 ItemEntity，finally 恢复库存。
6. `TacticalOverlay`：EXIT 提示明确先中心越界、场内拒绝与其他玩家留场行为。
7. 仅 NeoForge 26.1 的 Unix wrapper 转为 LF 并恢复可执行位，修复 bash 的 CRLF 语法失败。

## 文档修正

- 三份文档统一指向 120401 与本次修复；保留既定玩家规则和未完成功能，不把缺陷包装成产品决定。
- common schema 为 3，target envelope 为 5，网络协议为 12，控制类为 CombatControls。
- 删除“原型拒绝合并”“原型恢复直接结束”的过期实现分流描述。
- 区分 AUI 物品预览与原版个人背包整理许可；明确整理不能隐含掉落或删除装备。
- UNKNOWN 存活箭的持久隔离优先于普通已知箭无活动域时恢复原版推进。
- 历史测试次数降为输入文档报告，未附带历史日志不冒充复验；缺失的契约/日志文件只保留路径说明。
- 缺口文件保留 G02–G27，并增加 G28–G32 记录本次修复的 target 验收、架构和文案边界。

## 本次实际验证

本节记录输入修复包的验证报告；本地应用后的独立复验见文末，不将两轮环境与结果混用。

| 验证 | 结果与限制 |
| --- | --- |
| common 主源码、测试源码编译 | 通过。使用本机 OpenJDK 17.0.20 的 jdk.compiler 模块，`--release 17`。 |
| common JUnit | 48/48 通过。使用可用的 JUnit Platform Console Standalone 1.10.3（Jupiter 5.10.3），不是工程声明的 JUnit 5.11.4 / Gradle 路径。XML 报告随包附带。 |
| 7 个修改后的 Java 文件语法解析 | 通过。JavacTask.parse，仅语法；未解析 Minecraft 类型、不检查 Mixin 或 target API。 |
| NeoForge `clean build :common:test runGameTestServer --offline` | 未进入构建；Gradle 9.7.1 分发未缓存，wrapper 下载因网络不可达失败。日志随包附带。 |
| 新增 GameTest、`.84` API / Mixin、客户端和 dedicated server | 未执行，不能写为通过。当前只有 JDK 17 可用于本轮命令；target 需要 JDK 25。 |

原版 SWAP 语义依据提供的 `.109` patched source：AbstractContainerMenu 的 SWAP 溢出路径会调用 Inventory.add，失败后 player.drop；Inventory.add 的目的地为主背包和可合并副手，已损坏物品需要主背包空槽。实际依赖 `.84` 未升级，兼容性须由固定版本构建及运行确认。

## 在用户环境继续验证

在 JDK 25 环境进入 `targets/neoforge-26.1`：

```bat
gradlew.bat clean build :common:test runGameTestServer --console plain
```

新增断言位于已有 `repair_values` 和 `development_attack` 场景，运行完整必需 GameTest 集，不以选择单个容易通过的测试替代原有回归。

随后按缺口文档验证双客户端：本人回合满背包装备交换、拒绝后的客户端库存收敛、非零游标补收、场内 EXIT 拒绝、中心越界退出后不再接收留场战报。生产构造回归不能替代真实服务器重启、实体与区块保存顺序、UNKNOWN 箭重载隔离验证。

## 明确保留的未完成事项

G31 的 service/runtime 双向依赖和移动/投射物 ownership 拆分、G17 七日引用感知归档、G18 非投射物超期、精确箭入域边界、持久化崩溃对账及真实联机均未在本次实现。没有用无意义转发类或简单 TTL 冒充完成，也不因保留这些缺口而关闭正常入口。

## 2026-09-25 本地应用与独立复验

逐项核对当前工作区后补齐本说明的 SWAP 容量预检、两个 resultSync 调用点及工厂、生产恢复构造测试入口、恢复/编解码/背包回归和 EXIT 提示。保留工作区已有修改；本轮未修改 common 生产代码或其他 target。Unix wrapper 使用 LF、Git mode 100755，并以限定路径的 `.gitattributes` 防止再次转回 CRLF。

原版依据已改为本地 `minecraft-patched-26.1.2.84-sources.jar` 的实际实现，详见 [固定版本接入契约](version-differences/neoforge-26.1.2.84-seams.md)。库存预检计入拆分后的来源和副手同组件容量；损坏物品要求主背包空槽。测试另覆盖创造模式不删除装备、空副手不能当作普通空槽、正常满包交换仍允许。

JDK `25.0.3`、Gradle `9.7.1`：首次标准 `clean build :common:test runGameTestServer --console=plain` 在 clean 阶段因既有 live-loop 服务端持有文件失败，日志 `build/repair-validation.log`。未终止既有进程；随后用本地 init script 将所有项目 buildDirectory 和 GameTest 工作目录隔离，在 `targets/neoforge-26.1` 执行：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-25.0.3'
.\gradlew.bat -I ../../build/repair-validation.init.gradle clean build :common:test runGameTestServer --console=plain
```

- 独立 target clean build 成功；common JUnit 48/48、全部必需 GameTest 41/41 通过，无筛选测试。
- `development_attack` 中执行真实服务器包处理器的嵌入连接背包回归；`repair_values` 执行生产构造及非零版本 73 / 游标 19 往返。坏存档产生的预期 ERROR 日志用于验证拒绝恢复，不是测试失败。
- 完整日志：`build/repair-validation-isolated.log`；JUnit XML：`build/repair-validation/common/test-results/test/`。临时 init script、日志与产物均为本地 build 输出，不纳入源码交付。
- 产物：`build/repair-validation/DNDTurn-neoforge-26.1/libs/DNDTurn-neoforge-26.1-1.0.0-SNAPSHOT.jar`。
- 未运行双客户端、真实网络缺页/库存收敛、EXIT 显示、实际服务器重启或崩溃对账。G28–G30 保留这些剩余边界；G31、G17/G18 等范围不变。

## 2026-09-25 20:34 客户端 START 崩溃与无敌人建场修复

最新崩溃由 `CombatControls.send` 在无会话时使用 expectedVersion=-1 引起；协议构造器拒绝负版本，START 尚未发送客户端就崩溃。客户端现共用 `CombatIntent.start`，发送版本 0 和空会话/目标，保留服务端原有校验。

用户明确无敌人也允许进入回合制。建场将附近 Zombie 改为可选成员；只有获准玩家时建立候选会话，玩家结束后推进环境阶段，再返回玩家回合刷新资源。不伪造敌意、不取消多人同意或既有退出边界。

原先以“没有 Zombie”为失败条件的 `start_failure` 替换为 `player_only_start`：同一 START 工厂编解码后经过生产 handler，核验仅玩家成员、重复请求不重复建场、两轮实际环境调度和资源刷新。场景强制加载其测试区块并释放自己新增的票据；独立 environment 批次防止与旧箭矢固定时刻断言竞争环境调度。初次测试因缺少区块 tick 条件超时，随后同批次调度影响旧箭矢断言；补足测试前提并隔离批次后完整套件通过，未放宽生产调度条件或旧断言。

JDK 25.0.3 / NeoForge .84，在上述隔离目录执行 `build :common:test runGameTestServer` 成功；common 48 项测试报告全部通过（最终增量运行复用本轮 clean build 生成的结果），全部必需 GameTest 41/41 通过。日志为 `build/player-only-start-validation.log`。本轮未修改 common 生产代码或其他 target；未执行真实客户端按键与双客户端验收，协议/服务端回归不替代该证据。
