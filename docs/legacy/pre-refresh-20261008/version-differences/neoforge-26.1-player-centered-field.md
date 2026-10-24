# NeoForge 26.1 玩家为中心的跟随场地（用户明确设计要求）

> **性质：用户明确指令的规则改动，非本仓库自主决定。** 本记录用于让协作者了解本次覆盖了哪些既有设计、理由与当前范围；实现事实与验收状态以 01/02 为准，本文件不代表完整验收通过。日期：本次会话。

## 背景与动机（用户要求）
原设计将遭遇场地 `EncounterRegion` 在开战时按"发现范围内**所有实体**"的锚点一次性生成并固定（见 `docs/legacy/03_PLAYER_RULES.md` 第 4 节、`docs/legacy/04_ARCHITECTURE_CONTRACTS.md`）。由此产生两个问题：
1. 玩家移动出固定场地后，服务端以 `target outside encounter` 拒绝其移动/行动，表现为"卡住不能动"。
2. 合并判据 `EncounterRegion.overlaps` 使用 `半径之和`，而场地锚点包含怪物，导致两场战斗很容易因怪物位置而重叠、**频繁合并**。

用户据此提出以下替代设计。

## 本次生效的规则
1. **场地以参战玩家为锚点**：场地几何 = 参战玩家水平坐标的凸包 + `regionRadius`；竖直仍为 `[最低玩家Y-VERTICAL_MARGIN, 最高玩家Y+VERTICAL_MARGIN]`（`VERTICAL_MARGIN` 保持 16）。不再是"发现范围内所有实体"的凸包。
   - 实现：`MinecraftRegionSampler.capturePlayers(...)`；`EncounterRuntime.sampleConsentRegion/sampleMergedRegion` 改用玩家锚点。
2. **场地半径 `regionRadius = 35`**（原默认 8）。这是配置默认值；已有存档的 `dndturn-server.properties` 已同步为 35。
3. **场地跟随玩家**：在**玩家移动计划到达终态（停下）**与**回合开始**时，以当前参战玩家凸包重采样并 `EncounterAuthority.updateRegion(...)`（bump `version`/`structuralRevision`）。
4. **允许离开场地、允许在场外行动**：取消（注释掉、未删除）区域外拒绝——
   - `MinecraftSnapshotCapture` 的 `RuleFacts.TARGET_DOMAIN`；
   - `ActionExecutionCoordinator` 的目标/路径区域限制；
   - `MinecraftCellProbe.canOccupy` 的区域包含判定；
   - `MinecraftMovementOpportunities` 与 `MobTurnStrategies` 感知的场地过滤；
   - `EncounterRuntime` 中"member moved outside fixed region"的租约结算；
   - DASH／防御行动／近战授权中的 `region().containsPoint` 前置条件。
   以上均以注释形式停用，保留原码，便于日后恢复。
5. **成员 = 参战玩家 + 有仇恨的实体**：非敌对 Mob **不再**自动进入回合制。
   - 开战时只拉入"已经以参战玩家为目标"的附近 Mob（`beginEncounter`）。
   - 运行中：`LivingChangeTargetEvent`（怪把参战玩家设为目标）→ 加入其遭遇并 **双向**建立敌意。
6. **场外玩家攻击场内成员**：`AttackEntityEvent` 检测到"非成员玩家攻击某遭遇的成员"→ 把该玩家加入该遭遇、**双向**敌意、重锚场地包住该玩家；取消该次 vanilla 攻击（按用户确认，攻击随后作为战术动作结算）。
7. **合并算法不变**（`EncounterRegion.overlaps` + 因果合并）；但因为锚点只剩玩家，几何合并的触发源由玩家数量界定，不再因怪物膨胀。用户确认**阈值不与半径解耦**（R=35 时两玩家中心线相距约 70 格内会合并），**环境 hold 范围也不解耦**。

## 覆盖/修订的既有条目
- `docs/legacy/03_PLAYER_RULES.md` 第 4 节："发现范围内所有实体的中心位置都可以作为场地锚点""锚点建立后固定，不自动拖动或扩大场地" —— 现改为玩家锚点、可跟随。
- `docs/legacy/03_PLAYER_RULES.md` 第 5 节（2026-09-26 修订）："发现范围内非敌对 Mob 也纳入先攻与回合移动" —— 现改为非敌对 Mob 不进入回合制。
- `docs/legacy/03_PLAYER_RULES.md` 第 2 节关于"跨出边界后不能在场地外发起主动移动"的安全边界 —— 现改为允许在场外行动。

## 用户已确认的取舍（后果自担）
- 合并判据、环境 hold 范围均**不**与 35 半径解耦。（即：较大范围内两玩家会合并；被 hold 的区块范围随场地增大。）
- 允许越界，因此不再有"越界即拒绝/结算"的保护。
- 这是**正式规则变更**，本文件即为给协作者的说明。

## 当前范围与未完成
- 已实现并编译通过（`compileJava`）；**未**进行 GameTest／真实客户端／双客户端验收。
- 世界效果类区域限制（如 `VanillaBehaviors` 桶效果、`TacticalImpact` 效果范围、爆破区域过滤）**未**停用；仅停用了与玩家/怪物移动和行动授权直接相关的区域门。若需要"任何效果都可在场外发生"，需另行处理。
- 场地方向/跟随时机若与预期不符，以用户实测为准。

## 附：边界可视化调试件（供协作者注释/删除）

统一标记串：**`DNDTURN-TEMP-BOUNDARY-VIZ`**。`grep -rn DNDTURN-TEMP-BOUNDARY-VIZ targets/neoforge-26.1` 可定位全部站点；这是纯调试显示，不改变任何规则。站点：

1. 新增文件 `platform/network/RegionBoundaryProtocol.java`（载荷 + 服务端发送/清除）。
2. 新增文件 `platform/client/render/RegionBoundaryRenderer.java`（Gizmos 绘制）。
3. `platform/network/EncounterProtocol.java` `register(...)`：注册可视化载荷。
4. `platform/bootstrap/DNDTurnNeoForgeClient.java`：import、`ExtractLevelRenderStateEvent` 监听、客户端载荷处理注册。
5. `platform/server/encounter/ProjectionPublisher.java`：`publish` 发送 / `clear` 清除。

删除时移除以上 5 处（含两个新增文件）即可，与玩家为中心的场地逻辑相互独立。
