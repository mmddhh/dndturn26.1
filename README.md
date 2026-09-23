# DNDTurn

项目采用 `common + targets/<loader>-<minecraft-version>` 的独立 Gradle 工程结构。开发文档见 [docs 索引](docs/README.md)。当前阶段为 **M3 acceptance**，功能缺口与待验收项统一见 [M3 验收清单](docs/02_GAPS_AND_CONFLICTS.md)。

当前战斗规则、区域时间政策与多人行为见 [游戏规则](docs/03_PLAYER_RULES.md)。26.1 提供管理员 local/debug 工具和默认关闭的 Player/Zombie 原型与可选多人同意；配置与控制方式见 [接入说明](docs/version-differences/neoforge-26.1.2.84-seams.md)。正式 `/dndturn tactical` 遵循独立验收门槛。

Gradle group 与 Java 包名为 `cc.sighs.dndturn`，mod id 为 `dndturn`。`common` 和 Minecraft 1.20.1 targets 使用 Java 17 语言级别；Minecraft 与加载器 API 留在各 target。

## NeoForge 26.1 界面

已接 AUI 1.2.5 常驻动作栏、先攻/资源、命中日志、物品预览与多人同意模态。默认 `K` 切换光标；服务端原型和多人同意开关默认关闭。安装依赖、开发验证及支持边界见 [AUI 接入契约](docs/version-differences/neoforge-26.1.2.84-aui.md)。正式玩法仍未开放。

## IDEA

直接打开任意 `targets/<loader>-<version>/` 目录。IDEA 会导入当前 target 与可编辑的 `../../common` 源码模块，只下载该 target 的加载器和 Minecraft 依赖。

## Target

| Target | Gradle JVM | 当前能力 | 构建工作目录 |
| --- | --- | --- | --- |
| `forge-1.20.1` | JDK 21 | 构建占位，战术功能未启用 | `targets/forge-1.20.1/` |
| `fabric-1.20.1` | JDK 21 | 构建占位，战术功能未启用 | `targets/fabric-1.20.1/` |
| `neoforge-1.21.1` | JDK 21 | 构建占位，战术功能未启用 | `targets/neoforge-1.21.1/` |
| `neoforge-26.1` | JDK 25 | 正式战术入口；支持边界与待验收见 docs/02 | `targets/neoforge-26.1/` |

在表中对应目录执行 `.\gradlew.bat clean build`。表中的 JDK 是运行 Gradle 的版本；编译语言级别由各模块的 `options.release` 固定：`common`、Forge/Fabric 1.20.1 为 Java 17，NeoForge 1.21.1 为 Java 21，NeoForge 26.1 为 Java 25。

根项目默认只构建 `common`。使用 JDK 21 时可选择性构建前三个 target：

```powershell
.\gradlew.bat '-Ptarget=forge-1.20.1' build
.\gradlew.bat '-Ptarget=fabric-1.20.1' build
.\gradlew.bat '-Ptarget=neoforge-1.21.1' build
.\gradlew.bat -PallTargets=true build
```

`allTargets` 只包含前三个 target。使用 JDK 25 时，可在 `targets/neoforge-26.1/` 独立构建，或运行 `.\gradlew.bat '-Ptarget=neoforge-26.1' build`。

## 结构

- `common/`: 不依赖 Minecraft 或任意 loader 的共享 Java 代码。
- `targets/*`: loader 和版本专属入口、metadata、资源及 API 适配。

## 共享资源

将所有加载器和版本共用的资源放在 `common/src/main/resources/`。构建任意 target 时，该目录会与 target 自己的 `src/main/resources/` 合并并写入最终 jar。

加载器 metadata 仍必须保留在 target 中：Fabric 使用 `fabric.mod.json`，Forge 使用 `META-INF/mods.toml`，NeoForge 使用 `META-INF/neoforge.mods.toml`。

## 本地依赖

每个 target 都会自动将自身 `libs/` 目录中的 `*.jar` 作为 `implementation` 依赖。将 jar 放入对应目录后不需要在 `build.gradle` 中逐条声明；`*-sources.jar` 和 `*-javadoc.jar` 会被忽略。

```text
targets/forge-1.20.1/libs/
targets/fabric-1.20.1/libs/
targets/neoforge-1.21.1/libs/
targets/neoforge-26.1/libs/
```

本地 jar 的传递依赖无法自动推导。若某个 jar 还依赖其他库，需要将这些库也放入同一个 `libs/` 目录，或按常规方式声明依赖。

## 发布

每个 target 都提供 `publishMods`，可手动发布其自身的产物至 CurseForge 与 Modrinth。两个平台的项目 ID 是所有 target 共用的非敏感信息，在根 `gradle.properties` 中取消注释并填写：

```properties
publish_curseforge_project_id=你的CurseForge项目ID
publish_modrinth_project_id=你的Modrinth项目ID
```

token 只从环境变量读取，不要写入仓库。PowerShell 示例：

```powershell
$env:CURSEFORGE_TOKEN = '...'
$env:MODRINTH_TOKEN = '...'
$env:PUBLISH_CHANGELOG = '本次版本的更新说明' # 可选

cd targets\forge-1.20.1
.\gradlew.bat publishMods
```

将目录替换为其他 target 即可单独发布对应加载器和 Minecraft 版本。Fabric 会上传重映射后的 jar；Forge 与 NeoForge 上传各自的最终 jar。

## 版本参考

- [Minecraft 1.20.1、1.21.1、26.1 完整迁移差异参考](docs/version-differences/README.md)
- [多版本日常维护工作流](docs/legacy/MAINTENANCE_WORKFLOW.md)

## NeoForge 26.1 诊断

游戏启动参数 `-debug` 开启 DNDTurn 诊断日志（不是 JVM 参数）。默认关闭，不授予权限或自动执行动作。开发环境在该 target 运行 `.\gradlew.bat runClient -Pdebug` 或 `.\gradlew.bat runServer -Pdebug`。日志写入运行目录的 `logs/latest.log`，事件前缀为 `[DNDTurn/debug]`。

旧 `/dndturn local`、自动 UI/网络/渲染探针及对应 Gradle 开关已移除。正式命令为 `/dndturn tactical [start|end|exit]`。自动测试位于 `src/gameTest`，通过 `runGameTestServer` 加载，测试类与测试资源不进入发布 jar。固定版本依据和本轮验证见 [诊断契约](docs/version-differences/neoforge-26.1.2.84-diagnostics.md)。
