# 当前状态

同步日期：**2026-10-24**。基线为本地 `dev_` / `195ece9d408107015f5c0d3034479fdd8cdfae42` **加现有未提交工作区**。本页按当前实现重写，保留先前记录的验证结论及限制，不再把逐轮审阅作为并列当前状态。

本次工作为活动文档同步与 AutoJS 本地调试依赖调整；未改玩法代码。先前构建/GameTest 记录见“既有验证证据”，不冒充本次重新运行。剩余任务只在[路线](02_ROADMAP.md)维护；玩法以[合同](01_RULES_AND_CONTRACTS.md)为准。

## 基线与阶段

`git merge-base HEAD origin/dev-mmddhh-field` 返回 `9c05747ed7a474a2c95264ddf2758fa6a11dc6c9`，field 修复已在当前历史内，P0 无需重复合并。`main` 不是本次工作分支；不切分支、不覆盖现场、不自动提交或发布。

活动文档已安装，旧文档位于 `docs/legacy/` 及 `pre-refresh-20261008/`，旧根文件保存在 `_root/`。P1 文档整理已有落点；测试精简仍须按独有不变量逐项判断，不能仅凭归档存在就关闭整个 P1。

主目标仍为 Minecraft 26.1 / NeoForge 26.1.2.84 / JDK25 / ApricityUI 1.2.5。common 使用 Java17；其他三个 target 只维护构建边界。

## 已接入的生产链

| 领域 | 当前实现 | 未关闭边界 |
| --- | --- | --- |
| 规则权威 | Encounter 拥有成员、敌对、先攻、回合资源和规范结果；Actor 拥有长期/运行/效果状态 | 不把读取缓存或平台 coordinator 作为第二权威 |
| 行动 | 选择、只读发现、规范计划、接近、复验、原生执行、观察、取消/释放和操作去重 | 特殊效果与全部输入/生命周期组合仍需核对 |
| field 与介入 | 玩家锚点、35 半径、边界跟随；介入首击即时战术结算，下一次自身回合扣动作 | 首击真实联机时序、全部合并和 held 队列迁移组合 |
| Mob 关系 | 读取 `setTarget` 提交后的实际目标；任一方已参战时可接纳另一 Mob，同场更新敌对、跨场请求安全合并 | 两者均未参战不建场；未参战玩家不能被索敌绕过同意；跨场 Mob 对 Mob 专门运行场景未完成 |
| Mob 行动 | `PVE_LIVING_MEMBER` 与敌对事实接通发现、通用/内置 AI、普通箭和天生近战；拒绝自攻/PvP及中立 Mob 对 Mob | 扩展声明的旧仅玩家策略保持原义；特色能力、友军/区域候选不自动兼容 |
| EXIT | common 权威距离判定、捕获/保存 `escapeDistance`、稳定拒绝原因和重试、回执、客户端距离/阈值投影 | 真实键鼠、迟到投影及完整联机体验未验证 |
| 原版操作 | 通用物品/方块后备；放置、工具、食饮、装备、桶/瓶/刷子/钓鱼、交互和部分投掷路径 | 开菜单不授权配方/交易；通用 use/useOn 不证明任意间接效果均受控 |
| 环境 | 环境参与者、有效步、scheduled block/fluid tick、block event、BE、TNT、漏斗等既有托管 | 所有时间输入、重锚/跨域/卸载/合并组合及真实负载未穷尽 |
| 效果/生物 | 原版指定药效、回合结算、Creeper Charge/Fuse、Skeleton 普通单箭及装备消耗政策 | HELP、完整借机、Guardian/ElderGuardian、长期能力 writer 和更多生产效果仍开放 |
| 客户端 | 战术镜头、行动/先攻/头像、调查、路径及部分轨迹、ClientSession 生命周期编排 | 地形遮挡、缩放/命中、失焦/迟到响应、真实双客户端 |

### field 仍需关注的源码事实

`EncounterLifecycleCoordinator.reanchor` 已读取会话捕获设置，在更新权威前采样并计算 chunk 集；随后 `engine.updateRegion`、`world.bind`、标记存档、发布投影。采样异常处理不再包住整个提交过程，但异常分类和提交故障仍须审计，不能声称整体可回滚。

当前重锚仍构造递增 region version；无变化更新及自身移动造成计划失效的全部组合未证明。`world.bind` 更新 region/index，重锚未直接调用 `scheduledTicks.captureAll`；其他边界有接管入口，不据此推断一定漏队列或迁移已完整。生产源码未检出旧 `DNDTURN-TEMP-BOUNDARY-VIZ` 标记；不恢复临时协议。最后玩家离开时结束并释放剩余成员，不用 Mob fallback 长期维持场次。

### 所有权与查询

生产链已统一使用 `server.runtime.EncounterRuntime`；移动、投射物、世界调度各有运行 owner。旧“双 runtime 尚未接通”、旧签名断点和旧类行数不再是当前结论。停服沿采样、保存、独立释放/失败收集链处理；最终观察核验执行者 UUID 与实例，不用替换实体覆盖旧证据，采样本身不推进阶段。

事实捕获注入当前 Actor/成员读取端口，共享协议发送由 server/network 负责，客户端由 ClientSession 组织 reset，common 已拆独立读取值、请求组装及内部账本协作者。结构迁移不证明全部取消/合并/恢复组合已完成。

`AbilityQueryService` 在一次同步发现内复用执行者与目标事实，最多缓存 128 个目标、每目标 128 个事实，关闭后清理，不跨 tick/写入/执行边界，不生成许可。普通目标按声明事实读取；存在持续能力时仍保留完整来源展开。候选空间探测与寻路仍有同步工作；未实测点击延迟，不能宣称卡顿已消除。

### 能力注册与扩展限制

生产 bootstrap 在 load-complete 排队调用 `RegistrationLifecycle.freeze`，统一校验和冻结能力、facts、Actor、Effect、Equipment、Ranged、AI/移动、近战/身体目标及观察表。扩展应在构造期或 common setup（含排队任务）完成注册，不能依赖不同 Mod 的构造顺序，不能在 load-complete/服务器启动后注册。

手动能力包括免费手动都需执行器；付费能力需执行器及 facts；周期型需匹配 activation；持续型必须免费、无执行器并有 contribution。触发型保留纯规则驱动及被其他规则调用的执行器链。执行器按定义 ID 注册，`executor` 不是任意共享别名。当前全局 64 定义、每能力来源最多16等预算未因兼容工作扩张。

能力定义、来源证据、原生 facts、AI 用途、受限 executor、效果观察及客户端表现是独立职责。通用近战不自动翻译原生 override 的中毒/召唤等副作用；Goal/Brain 结构诊断不调用 canUse/start/tick。普通单箭可复用装备远程链，但特殊箭、附魔、穿透等有明确限制。三叉戟、珍珠、风弹、滞留药水、烟花不能由通用入口推定支持。

## 过程、恢复与历史

已有 Process 纯值、运行 owner、阶段图、独立随机流、检查点和触发 outbox。当前 `ExecutionProcesses` 只安装 `AUTHORIZED_EXECUTION_STEP` 配合 `FAIL_UNKNOWN` / `RECONCILE_NATIVE` 的驱动，其他时钟与 `RESUME_SEMANTIC` 拒绝为 `PROCESS_DRIVER_UNSUPPORTED`；尚无完整跨回合 staged driver。

手动执行已有受限生命周期。触发路径主要为同步执行，STARTED 恢复保持 UNKNOWN；其上下文权限、来源撤销、无实体时外部队列/订阅释放仍需按能力族完善。对多段能力，任意一次血量/库存/方块变化不足以证明所有必要效果完成。

当前恢复将 APPROACH/PREPARE 收束为 INTERRUPTED，已有变化证据的部分执行可记录 COMPLETED，其余只读对账/UNKNOWN，不重放 start，不恢复旧许可。已有两进程 launcher 证明特定恢复路径，但未证明普通专服完整存档续战。效果未发生时的安全补执行、可归因补偿仍未实现；不据历史“能完成就完成”描述自动退款或回滚未知效果。

截止处理已从早期“全部执行超时取消”收窄：行动执行器只对 APPROACH 强制截止，陈旧检查点检查 APPROACH/PREPARE；环境步截止和玩家挂机跳过是独立路径。七游戏日引用感知清理已覆盖关闭场次及 Actor 终态回执，保留活跃因果/重试引用和未知年龄；真实七日老化、全部跨重启组合未验证。

## 既有验证证据

以下来自重写前工作区记录，保留其基线与证明范围；**本次没有重跑这些游戏场景**。期间未新增测试文件，断言扩充复用既有 suite/fixture。

| 切片及基线 | 已记录执行结果 | 证明限制 |
| --- | --- | --- |
| 重构接续：`c027b16` 加当时工作区 | 26.1 `:common:test compileJava compileGameTestJava compileUiTestJava`、`:common:test build --offline`；其他 target `compileJava --offline` 通过 | 结构/自动检查，不是实机或所有迁移组合 |
| 重构平台接缝 | `body_control`、`gate_melee_approach`、`movement_lease_exit`、`scheduled_tick_hold`、`repair_values` 通过 | 单项选择；首次组合选择未覆盖的项目另行单跑 |
| 两进程恢复 | `runRestartGameTestServer -PrestartTestDirectory=build/refactor-restart-continuation`，`DNDTURN_RESTART_TEST=dndturn:restart_history`：WRITE_READY → VERIFY_PASS，场次/操作一致 | 真实放置效果、历史、活动 APPROACH 释放及唯一 INTERRUPTED；launcher 保留 chunk/SavedData，不是普通 level.dat 续战 |
| 查询切片：`c027b16` 加当时工作区 | common 测试、生产/GameTest 编译；`gate_melee`、`gate_melee_approach`、`tactical_effect_state`、`discovery_cursor` 通过 | discovery 夹具改用当前 owner 的事实 context；未做真实性能测量 |
| 注册冻结：`195ece9` 加当时工作区 | `:common:test compileJava compileGameTestJava --offline`，`repair_values`、`creeper_effect` 通过 | 真实 loader 启动后拒绝迟注册；无第三方内容实测 |
| EXIT/CI：同一 `195ece9` 工作区 | common 测试；26.1 main/gameTest/uiTest 编译；四 target `build --offline`；`prototype_exit_retry`、`consent_multiplayer`、`repair_values` 通过 | 含距离/未知位置/回合、恰好16格、原生仇恨保留、最后/个人退出、回执及投影编解码；无真实输入 |
| Mob 对 Mob：同一工作区 | common 测试、四 target `build --offline`；`mob_standardization`、`mob_standardization_automatic`、`merge_world_boundary`、`prototype_exit_retry` 通过 | 实际箭伤、近战、取消索敌、费用/去重；普通合并不证明专门 Mob 跨场组合 |
| 早期环境与历史切片 | `scheduled_tick_hold`、`environment_processes/circuits/clock/classification/tnt/domains/boundary_time`，`combat_structure`、`creeper_effect`、`merge_discovery_expansion`、`gate_melee`、`gate_melee_approach`、`gate_cow`、`body_control`、`actor_history`、`repair_values` 等相关单项通过 | 部分旧固定场地夹具先失败，经玩家中心/实际仇恨规则修订后通过；不外推当前全套隔离 |

历史失败与纠正保留：错误场景名未执行任何测试，不算通过；discovery/Mob 夹具曾缺失查询事实注入，Skeleton 专用 AI 曾过滤 Mob，修正后对应场景通过；多个 Gradle 进程同时写 common/build 曾引起 Fabric 暂时缺类，串行重跑通过。既有 jar 检查记录无测试类/AutoJS 条目，不代替当前构建产物核对。

## 本次同步与检查

本次核对 Git 基线/共同祖先、实际规则/平台调用点、Gradle/CI 配置并重写活动文档；归档正文保持原字节。AutoJS 从错误 group `com.sighs` 修正为仓库实际 POM 的 `com.sighs.autojs`，保留 `-Pautojs=true`，配置为显式 `localRuntimeOnly`，只接本地运行类路径。

本次 JDK25 + 显式 JDK17 toolchain 执行26.1 `build --offline` 通过，包含 common 测试、生产/GameTest/UI编译及既有UI/控制/诊断检查。临时 Gradle init 检查分别验证 AutoJS 默认关闭、显式启用进入运行类路径且不进入 compileClasspath/apiElements/runtimeElements；启用时实际解析到 JAR。首轮直接使用配置因插件未预置而失败，显式声明配置后通过；旧 group 的离线/在线解析失败，查询 Maven POM 修正坐标后成功。没有新增长期测试或测试文件。活动文档本地链接检查、归档正文哈希保全与 `git diff --check` 通过；26.1产物核对未包含AutoJS及已枚举的GameTest/UI/common测试类。未运行范围：真实客户端/AutoJS 脚本、双客户端、GameTest、第二进程恢复、第三方兼容、性能与七日老化；不因本次文档同步关闭这些目标。
