# 原版 Mob 设计数据

本目录保存原版 Mob 的设计输入，不是活动合同，也不代表对应能力已经实现。

- [vanilla-mobs.yaml](vanilla-mobs.yaml)：37 条原版 Mob 的规范化数据。
- 来源：`C:\Users\mdh\Desktop\原版怪物数据.md`（用户提供，2026-11-03）。

## 当前口径

- **HP / AC**：使用 Minecraft 原生值 1:1。`native.hp` 为原生最大生命，`native.ac` 为原生护甲值；`design.ac` / `design.hp_avg` 仅保留原始图鉴数据，不参与运行时。
- **速度**：`native.speed_ticks` 为每回合移动预算 tick；原图鉴里的“尺”按 tick 读取。
- **其余字段**：`design.traits` / `design.actions` / `design.reaction` / `design.saves` / `design.resistances` / `design.immunities` 保留原始 5e 图鉴内容，作为后续能力设计输入。
- **状态**：
  - `integrated`：已有专用适配（Creeper、Skeleton 家族、Zombie 家族、Enderman）。
  - `generic`：只有通用近战/AI/受击兜底，没有专用能力。
  - `deferred`：Boss/飞行等按 `01_RULES_AND_CONTRACTS` §8 暂缓。

## 尚未实现、不能把本文件当作运行时属性表的部分

1. **每 Mob 移动预算**：当前 `EncounterAuthority` 所有成员共用 `movementTicksPerTurn`（默认 30），`native.speed_ticks` 还没有接入。
2. **攻击档案**：`MobAttackProfiles` 已把本表 28 个原版 Mob 的命中加值与伤害骰接入近战和投射命中；其余 9 条的动作是充能/药水/爆炸/光束/召唤等特殊攻击，不适用 `AttackProfile`。其中苦力怕爆炸已有专门适配，女巫、唤魔者、守卫者等仍是 `generic`，仍需专用能力适配。未映射的标准近战/箭仍用全局 `d20+5` + 原生 `attack_damage`；特殊投射/法术不套用该兜底。伤害类型目前只是标签，没有抗性/易伤消费。
3. **豁免/DC**：没有通用豁免系统；`design.saves` 目前只是数据。
4. **伤害类型/抗性/免疫**：没有完整类型与抗性系统；`design.resistances/immunities` 目前只是数据。
5. **条件与触发**：中毒、麻痹、恐慌、力竭、倒地等尚未作为可消费条件接入。
6. **充能/附赠动作/多重攻击**：没有充能资源、附赠动作或通用多重攻击。
7. **特殊特性**：不死坚韧、蛛行、蛛网感知、传奇抗性、骇人威仪、召唤、爆炸等仍需逐项能力化。
8. **Boss/飞行/载具**：按 §8 暂缓，不在本批实现。

## 维护规则

- 新增/修改 Mob 设计先改本文件，再决定是否写入 `01_RULES_AND_CONTRACTS.md`。
- 实现完成后在 `00_CURRENT_STATE.md` 记录证据，在 `02_ROADMAP.md` 更新阶段，不把本文件当作验收清单。
- `design` 与 `native` 冲突时，以当前有效合同和原生事实为准，不按本文件覆盖运行时。
