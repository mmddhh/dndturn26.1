# 当前状态

同步日期：**2026-10-24**。基线为本地 `dev_` / `b2e939ba7353d1a47f1e4d8542a011dd5c2d32a4` **加现有未提交工作区**。本页按当前实现重写，保留先前记录的验证结论及限制，不再把逐轮审阅作为并列当前状态。

本次追加修复 26.1 旧战斗存档读取：用户明确放弃旧版，移除捕获设置的旧字段默认值兼容，读取时明确检查字段完整性。此前活动文档同步与 AutoJS 调整的证据仍保留。剩余任务只在[路线](02_ROADMAP.md)维护；玩法以[合同](01_RULES_AND_CONTRACTS.md)为准。

## 基线与阶段

`git merge-base HEAD origin/dev-mmddhh-field` 返回 `9c05747ed7a474a2c95264ddf2758fa6a11dc6c9`，field 修复已在当前历史内，P0 无需重复合并。`main` 不是本次工作分支；不切分支、不覆盖现场、不自动提交或发布。

活动文档已安装，旧文档位于 `docs/legacy/` 及 `pre-refresh-20261008/`，旧根文件保存在 `_root/`。P1 文档整理已有落点；测试精简仍须按独有不变量逐项判断，不能仅凭归档存在就关闭整个 P1。

主目标仍为 Minecraft 26.1 / NeoForge 26.1.2.84 / JDK25 / ApricityUI 1.2.8。common 使用 Java17；其他三个 target 只维护构建边界。

## 已接入的生产链

| 领域 | 当前实现 | 未关闭边界 |
| --- | --- | --- |
| 规则权威 | Encounter 拥有成员、敌对、先攻、回合资源和规范结果；Actor 拥有长期/运行/效果状态 | 不把读取缓存或平台 coordinator 作为第二权威 |
| 行动 | 选择、只读发现、规范计划、接近、复验、原生执行、观察、取消/释放和操作去重 | 特殊效果与全部输入/生命周期组合仍需核对 |
| field 与介入 | 玩家锚点、35 半径、边界跟随；介入首击即时战术结算，下一次自身回合扣动作 | 已验证后台双客户端首击/续战；更广能力与负载组合属于后续阶段 |
| Mob 关系 | 读取 `setTarget` 提交后的实际目标；任一方已参战时可接纳另一 Mob，同场更新敌对、跨场请求安全合并 | 两者均未参战不建场；未参战玩家不能被索敌绕过同意；已通过专门 Mob 对 Mob 非重叠场地因果合并场景；后台双客户端已验证同意建场与介入 |
| Mob 行动 | `PVE_LIVING_MEMBER` 与敌对事实接通发现、通用/内置 AI、普通箭和天生近战；拒绝自攻/PvP及中立 Mob 对 Mob | 扩展声明的旧仅玩家策略保持原义；特色能力、友军/区域候选不自动兼容 |
| EXIT | common 权威距离判定、捕获/保存 `escapeDistance`、稳定拒绝原因和重试、回执、客户端距离/阈值投影 | 迟到 EXIT 回执按当前 operation 过滤；后台双客户端已验证回合/距离拒绝、合法退出和旧投影拒绝，前台连续键鼠体验未完成 |
| 原版操作 | 通用物品/方块后备；放置、工具、食饮、装备、桶/瓶/刷子/钓鱼、交互和部分投掷路径 | 开菜单不授权配方/交易；通用 use/useOn 不证明任意间接效果均受控 |
| 环境 | 环境参与者、有效步、scheduled block/fluid tick、block event、BE、TNT、漏斗等既有托管 | 所有时间输入、重锚/跨域/卸载/合并组合及真实负载未穷尽 |
| 效果/生物 | 原版指定药效、回合结算、Creeper Charge/Fuse、Skeleton 普通单箭及装备消耗政策 | HELP、完整借机、Guardian/ElderGuardian、长期能力 writer 和更多生产效果仍开放 |
| 客户端 | 战术镜头、行动/先攻/头像、调查、路径及部分轨迹、ClientSession 生命周期编排 | 地形遮挡、缩放/命中、完整前台键鼠体验；后台双客户端已运行 |

### field 仍需关注的源码事实

`EncounterLifecycleCoordinator.reanchor` 使用会话捕获设置；只将加载/容量受限的 `SamplingDeferred` 作为可重试采样失败。区域内容比较忽略版本及锚点顺序，保留维度、发现范围、半径和锚点身份/位置；内容不变时不修改规则/结构/区域版本、不重复绑定或发布。实际变化先准备精确 chunk 索引、核验 held 容器，再提交权威与派生绑定，最后 dirty/通知。规则版本溢出校验在写入区域前完成。

重锚将已离域的 held block/fluid tick 按剩余 delay 归还原生队列，落入其他现存 field 的任务转交其 owner；入域捕获继续在既有世界/队列边界完成，不强加载或新增半径规则。提交后迁移失败明确进入停场释放，独立收集 owner/held 释放故障，未完成停场保持阻断并重试，不宣称世界副作用回滚。停场期间不再递归重锚，旧场释放重试不取消成员在新场的行动。多人建场使用首次回合通知之后的最终权威 field 绑定世界，避免初始单玩家采样覆盖索引。

已通过不变重锚、未加载采样保留旧状态、竖向离域释放延迟与资源守恒、原生容器卸载/注册、多人首次绑定及移动接近后执行场景。已补观察者提交故障、原生容器缺失后的独立释放/重试与完整存档第二进程队列恢复；任意故障和负载组合未穷尽。生产链不恢复临时 field 协议；最后玩家离开结束场次并释放其余成员。

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

26.1 `run/logs/latest.log` 的 `invalid captured combat settings` 来自 `New World` 旧战斗存档缺少 discoveryHorizontal/discoveryVertical/escapeDistance，Gson 将缺失数值读为零。用户授权放弃旧版后，独占打开世界 session.lock 确认未占用，将 `run/saves/New World/data/dndturn/combat_state.dat` 移为同目录 `combat_state.dat.incompatible-backup`，前后 SHA256 一致；地图及玩家数据未改。代码不迁移旧战斗，改为反序列化前检查捕获设置完整性，并取消 discovery 参数容错默认值。复用 PersistencePolicyChecks 扩充缺失字段拒绝且原文不变的不变量；既有检查仅覆盖顶层字段，无法防止嵌套 primitive 缺失被 Gson 静默默认。未新增测试文件。

本次执行 JDK25 + JDK17 toolchain 的 `build runGameTestServer -PgameTestSelection=dndturn:combat_save_values --offline` 通过（含 common 测试及既有构建检查，所选 GameTest 实际执行并通过）。首次 wrapper 下载受沙箱网络权限阻止，授权后使用工作区指定的 Gradle 9.8 完成；已有 build.gradle 与期间出现的 wrapper 修改未覆盖。`git diff --check` 通过。未重新进入原世界、未做双客户端或第二进程续战验证；本次重置不声称旧战斗恢复。

本次核对 Git 基线/共同祖先、实际规则/平台调用点、Gradle/CI 配置并重写活动文档；归档正文保持原字节。AutoJS 从错误 group `com.sighs` 修正为仓库实际 POM 的 `com.sighs.autojs`，保留 `-Pautojs=true`，配置为显式 `localRuntimeOnly`，只接本地运行类路径。

本次 JDK25 + 显式 JDK17 toolchain 执行26.1 `build --offline` 通过，包含 common 测试、生产/GameTest/UI编译及既有UI/控制/诊断检查。临时 Gradle init 检查分别验证 AutoJS 默认关闭、显式启用进入运行类路径且不进入 compileClasspath/apiElements/runtimeElements；启用时实际解析到 JAR。首轮直接使用配置因插件未预置而失败，显式声明配置后通过；旧 group 的离线/在线解析失败，查询 Maven POM 修正坐标后成功。没有新增长期测试或测试文件。活动文档本地链接检查、归档正文哈希保全与 `git diff --check` 通过；26.1产物核对未包含AutoJS及已枚举的GameTest/UI/common测试类。未运行范围：真实客户端/AutoJS 脚本、双客户端、GameTest、第二进程恢复、第三方兼容、性能与七日老化；不因本次文档同步关闭这些目标。

## P2 本轮实施与验证

本轮按 P2 全部范围推进，保留进入任务时的存档、构建和 wrapper 修改。指定 field 修复 `9c05747` 已包含；远端同名分支后续提交未整体合并。未自动提交、推送或发布。

近战介入从 tick 末 UUID 队列改为伤害边界同步结算，先核验原生实体实例、阶段/运行操作、近战范围与适配来源，再复用战术后端。修复候选场接受介入后未转 ACTIVE、导致命中伤害许可失败的问题；保留新成员下一轮资格及下次自身回合动作欠账，未命中不返还、后续回合不重复扣。重复原生回调不重复首击。EXIT 客户端只接受当前退出 operation 的回执，reset 或新请求后的旧拒绝不能重新触发退出。

测试沿现有文件扩展：区域值层保护内容比较，既有 Encounter 回归保护介入转正式/不插队/动作仅扣一次，原生近战夹具保护同步回调与实际伤害守恒，scheduled tick 夹具保护加载失败和离域移交，合并夹具保护非重叠场地的真实 Mob 索敌与队列/回执，已有无窗口 UI 检查保护 EXIT 迟到回复。原有断言没有覆盖这些接缝；未新增测试文件，未放宽费用、终态或守恒断言。几何重叠合并仍由三场扩展场景覆盖。

本轮实际执行（日志在 26.1 `build/p2-*.log`）：

- JDK25/JDK17 toolchain 的 26.1 `build --offline`，包含 common 测试、生产/GameTest/UI 编译和既有 check。最后执行 `build runGameTestServer -PgameTestSelection=dndturn:gate_melee_approach --offline` 通过，新增旧场释放重试不取消新场行动的接缝断言；结果见 `build/p2-final.log`。
- JDK21/JDK17 toolchain 串行执行 Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1 的 `build --offline` 全部通过。活动文档本地链接、`git diff --check` 和最终 26.1 jar 隔离检查通过，未发现 GameTest/UI 夹具、test_instance 或 AutoJS 条目。
- 单项 `runGameTestServer -PgameTestSelection=dndturn:<名称> --offline`：`scheduled_tick_hold`、`consent_multiplayer`、`merge_world_boundary`、`merge_discovery_expansion`、`gate_melee`、`gate_melee_approach`、`prototype_exit_retry`、`arrow_first_ingress`、`arrow_cross_session`、`arrow_cross_session_reverse`、`arrow_cross_session_miss`、`arrow_cross_session_pvp`、`arrow_pending_removal`、`environment_domains`、`environment_boundary_time`、`environment_circuits`、`combat_save_values`，均有实际执行并通过的输出。
- 两个独立 JVM 串行执行 `runRestartGameTestServer -PrestartTestDirectory=build/p2-restart-continuation --offline`，`DNDTURN_RESTART_TEST=dndturn:restart_history`；出现同一场次/operation 的 WRITE_READY → VERIFY_PASS。仅证明既有 chunk/SavedData、历史和接近中断恢复，不是普通专服完整 level.dat 续战。

失败及纠正：首次构建/平台运行受 sandbox wrapper 网络权限阻止，获准后运行。逗号组合选择只实际执行一个场景，随后改为逐项执行。近战新增断言暴露候选未激活的生产问题并修复；纯规则用例曾误设新入场者可进入当前轮，现按既定下一轮资格明确断言不插队。箭夹具的旧 24 格场间距、场外箭位置及非可索敌 mock 已按当前 field/原生关系修正；EXIT 夹具曾假定结束一个 Mob 候选回合必定轮到玩家，修正为容纳候选激活重掷先攻，距离边界断言保持原值。

上述是前一轮的构建/夹具证据；以下补齐 P2 收尾。按用户最新要求，客户端验收改为后台自动化，不再切换前台或移动系统指针。P2 已按当前支持范围完成闭环；不据此关闭 P3–P7。

恢复现在先等待玩家与所需区块自然就绪，不设超时，不把缺席生成 UNKNOWN。等待期间门控行动、AI、关系合并、挂机/执行截止；临时登出/卸载不删除待恢复成员。已提交合并的运行绑定在就绪后、恢复开放之前修复。原许可撤销及未知效果不重放保持不变。新增管理员/控制台 `dndturn encounter stop <uuid>`；显式结束清除恢复等待，失败清理独立重试。停服现有“捕获保存→释放→移除 runtime”顺序经普通专服验证，关闭期间离场不再改写规则成员。

回合通知改在操作登记和规则版本提交后触发；重锚修订不会被外层版本覆盖，通知失败不抹掉已接受操作。复用既有规则测试覆盖重锚和通知抛错；复用恢复夹具覆盖缺席等待不推进/不造 UNKNOWN 和显式结束；复用 scheduled tick 夹具移除一个容器注入失败，验证其他区块仍释放、恢复容器后重试保留唯一任务及剩余 delay。未新增测试文件。

普通专服与两个真实客户端使用 `build/p2-live/{server,client-a,client-b}`，独立用户名 P2Alice/P2Bob，仅连接本机 25586 端口；没有使用原有世界。通过 `runServer` 正常入口、完整 `level.dat`/玩家/chunk/SavedData 保存退出，分别重启候选场和活动战斗。后台键盘从 GLFW 回调进入客户端，UI 点击从 AUI 坐标事件分发进入同意界面；没有直接调用开战/EXIT 网络处理器代替输入。场景布置使用管理员命令，近战介入通过原生伤害边界触发。后台临时改绑开战键为 F8，未验证默认 Shift+Space 的完整前台体验。

已观察并记录：

- 双客户端同意建场产生两玩家锚点，世界索引与权威 field 一致。候选场重启后无人/仅一人登录仍保留原版本、回合、先攻、资源和唯一环境参与者；等待时 END 输入不推进，两人就绪后可继续回合。
- 原生 Mob 介入即时造成 3 点实际伤害，重复原生回调没有第二份伤害/结果。活动场再次重启保留该历史和欠账；新入场者不进入旧轮，首次自身回合动作被扣除。
- 一个红石灯任务在普通存档重启及等待后仍为唯一 held tick，剩余 delay=5；真实环境回合推进后熄灭，held 队列清空。玩家恢复期间的自然回血不是伤害重放或退款。
- 后台客户端 EXIT 验证非自身回合拒绝、1 格敌距拒绝、合法退出；退出后注入原活动投影返回 IGNORE，不能复活已结束场次。管理员重复 stop 正常幂等。

日志为 `build/p2-live-*.log`，逐次脚本/观察为 `build/p2-live-evidence.jsonl`，隔离世界保留在 build 供复查。两客户端与普通专服已正常关闭。临时脚本、截图和世界不进入源码或发布 jar。

本轮 26.1 `build`、`combat_save_values`、`scheduled_tick_hold` 已实际通过，包含 common、UI、控制/诊断检查。最终改动后另逐项运行 `gate_melee`、`merge_world_boundary`、`merge_discovery_expansion`、`environment_circuits`、`environment_boundary_time`、`prototype_exit_retry`、`arrow_cross_session`，均实际执行并通过，日志为 `build/p2-completion-*.log`。首次新增测试编译因误用观察者方法名失败，改用现有绑定接口后通过；首次 AutoJS 启动缺 Rhino，补为 `-Pautojs=true` 下的可选本地依赖后启动成功。脚本适配阶段的反射/鼠标回调尝试不计入通过证据，最终后台输入和 UI 事件链已得到服务端状态验证。

本轮还以 JDK21/JDK17 串行重新构建 Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1，全部通过。活动文档本地链接、`git diff --check` 与最终 jar 隔离检查通过；没有测试/AutoJS 条目，没有新增测试文件。

范围限制：没有前台连续键鼠/镜头体验、真实第三方兼容、性能或长时间运行证据；后台旧投影验证是向客户端接收入口注入，不是网络代理乱序测试。未证明所有能力的活动过程和任意故障组合均可恢复，P4 的完整生命周期任务继续保留。
