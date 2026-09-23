# 客户端控制重构执行计划

2026-09-25；仅 NeoForge 26.1 target 与相关文档。保留工作区既有修改。

- [ ] 比较 124521 基线与工作区，核对既有修复。
- [x] 阅读规则、决策、固定 NeoForge 26.1.2.84 / AUI 1.2.5 源码与输入调用链。
- [x] 集中客户端控制模式、生命周期和手势归属；接入独立虚拟镜头。
- [x] 修复 AUI 全窗口输入路由及客户端预测门禁，保留 Screen/重绑定。
- [ ] 增补回归，固定版本构建、注入及实际客户端/独立服务端验证。
- [ ] 更新三份文档、seam 证据、命令结果及未验证范围。

## 执行记录

- 初始 git status 存在大量 staged/unstaged/untracked 修改，禁止基线覆盖。
- 沙箱终端与 Node 均在初始化报 `helper_unknown_error: apply deny-read ACLs`；沙箱外只读 PowerShell 已成功。
- 已读取根 AGENTS、维护工作流及三份规则/交接文档；固定 target 声明 NeoForge 26.1.2.84。
- 124521 命名基线未在工作区、Downloads 和 D 盘搜索中找到；现有 build/DNDTurn-sources.zip 不冒充该基线。保留并核对指定八项修复。
- 已反编译 Gradle 实际 AUI jar（SHA-1 d4311449e5b1cdc9a59e33a7f2b02435d7441546），对照本地 AUI 源码；固定 .84 Camera/MouseHandler/LocalPlayer/KeyboardInput/MultiPlayerGameMode 源码已读。
- 隔离 clean build、48 个 common 测试、41 个必需 GameTest 通过。
- 普通 dedicated server 联网回归通过，包含正常 START、自动镜头、位姿/包/预算/热栏、禁用控件、日志滚轮、跨文档手势、重绑定及位置心跳。ClientLevel 本机玩家网络维护门禁已修正并复验。
- 原生鼠标探针引导页/重复回调夹具已修正并重跑通过；额外断言模态无世界操作包、热栏不变、世界时间继续推进。
- 1280×720 / GUI 2、1000×800 / GUI 3 联网回归通过；单端活动战斗许可、角色原版移动与计费、死亡/复活/实例替换/换维度/重新进入/合法 EXIT 通过。
- 双客户端同一候选会话的独立镜头与角色位姿通过；活动角色同步追加验证中。先前出生点、地形、加入时序、自然怪物与回合交接夹具失败保留为失败，未冒充通过。
- 临时 level 4 权限曾被自动审批拒绝，用户明确授权仅用于隔离测试世界后继续；收尾必须停服并清空该世界 ops.json。
