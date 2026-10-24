# DNDTurn

Minecraft 原版世界中的战术回合制战斗。玩家通过俯视镜头、行动栏和点击计划行动；玩家与 Mob 共用能力裁决，明确托管的延迟、周期及危险过程由环境参与者推进。普通世界更新与物理默认继续运行。

主开发目标为 **Minecraft 26.1 / NeoForge 26.1.2.84 / JDK 25**，UI 使用 ApricityUI 1.2.5。版本以 target 构建配置为准；不在功能补齐时顺带升级。

## 文档入口

[开发文档](docs/README.md)说明各页职责；[当前状态](docs/00_CURRENT_STATE.md)记录实现与证据，[有效规则](docs/01_RULES_AND_CONTRACTS.md)定义玩法，[路线](docs/02_ROADMAP.md)安排剩余工作，[提示词](docs/03_PROMPTS.md)供逐切片执行。[AGENTS.md](AGENTS.md)与[测试政策](docs/04_TEST_POLICY.md)约束所有改动。历史资料仅在 [legacy](docs/legacy/README.md)追溯。

## 当前支持范围

26.1 已接入 `/dndturn tactical start`、`end`、`exit`、战术镜头、行动栏、能力发现与执行、基础 Mob 决策、实际仇恨驱动的 Mob 对 Mob 近战/普通箭、环境调度及检查点对账。通用 Mob、物品和方块入口默认开放，专用适配优先；仍受来源、目标、费用、保护、加载和效果范围校验。

field 使用参战玩家锚点、默认半径 35，在移动结束及回合边界跟随。成员可在场外合法行动。主动 EXIT 限自己的空闲回合，要求距本场最近存活敌人的水平距离至少达到建场捕获阈值（默认 16 格）；未知位置拒绝重试，最后玩家退出后结束场次。

完整跨回合能力、HELP、完整借机攻击、Guardian、部分特殊投射物、长期能力管理、真实键鼠/双客户端与普通专服完整存档第二进程恢复仍开放。PvP、完整 Boss/飞行/载具玩法及旧格式迁移暂缓。保留空日志面板，不恢复战斗文本日志。服务器夹具通过不等于上述实机目标完成，也不代表任意第三方模组兼容。

## 构建与调试

| 工程 | Gradle JVM | 范围 |
| --- | --- | --- |
| `common/` | JDK 21 可启动，编译/测试使用 JDK 17 toolchain | 纯规则，Java 17 兼容 |
| `targets/neoforge-26.1/` | JDK 25 | 当前战术功能主目标 |
| 其他三个 target | JDK 21 | NeoForge 1.21.1、Forge/Fabric 1.20.1，仅维护构建边界 |

根目录运行 common 测试：

```powershell
.\gradlew.bat -p common test
```

在 `targets/neoforge-26.1/` 中使用 JDK 25，并确保 Gradle 可发现 JDK 17 toolchain：

```powershell
.\gradlew.bat compileJava
.\gradlew.bat build
.\gradlew.bat runClient
# 显式启用 AutoJS 本地调试
.\gradlew.bat runClient -Pautojs=true
```

AutoJS 使用 `localRuntimeOnly 'com.sighs.autojs:AutoJS-neoforge-26.1:1.0.0'`，坐标由仓库 [Maven POM](https://maven.sighs.cc/repository/maven-public/com/sighs/autojs/AutoJS-neoforge-26.1/1.0.0/AutoJS-neoforge-26.1-1.0.0.pom)确认。当前插件未预置该配置，target 显式声明后只让 `runtimeClasspath` 继承；默认关闭，不进入编译类路径或发布的 API/runtime 依赖。首次启用需要下载，离线运行须已有缓存。依赖解析通过不表示已验证 AutoJS 游戏内脚本。

`build` 包含 GameTest 编译及 UI projection、debug diagnostics、control policies 检查，**不自动运行 GameTest 或真实客户端**。平台变更按需选择既有 `runGameTestServer -PgameTestSelection=dndturn:<name>`。CI 的 push/pull_request 已覆盖 `targets/**`；远端结果另行确认。

各工程可能共用 `common/build`，本地构建串行执行。根工程默认只构建 common；`allTargets` 不代表包含 26.1 的全版本发布。每个 target 可单独用 IDEA 打开；共享资源在 common，加载器 metadata 留在各 target，`libs/*.jar` 为本地依赖。发布使用对应 target 的 `publishMods`，项目 ID 从配置提供、令牌只来自环境变量；不自动发布，每包对应一个 loader 和 Minecraft 版本。
