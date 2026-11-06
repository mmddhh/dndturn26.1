# 怪物能力基础架构

本页说明怪物设计文档到回合制执行之间的数据与规则边界，不声明全部怪物能力已经完成。当前进度与验证见[当前状态](00_CURRENT_STATE.md)。

## 数据来源与硬边界

怪物数据只来自用户提供的 `原版怪物数据.md` 及规范化后的 JSON，不读取 D&D 5e 怪物图鉴。

已确认：

- **HP**：Minecraft 原生最大生命值，1:1；
- **AC**：`floor(原生护甲值) + 持盾 2`，1:1，不额外加基础 10；
- **速度**：文档中的尺直接作为 tick 预算，25 尺 = 25 tick；
- 文档的 `ac` / `hp_avg` / `hp_dice` 仅作设计记录，不覆盖运行时；
- 未提供数据的第三方 Mob 使用默认模板：六维 10、原生 HP/AC、30 tick、+5 命中、10 点钝击近战。

## 已实现的数据类型

`domain.mob`：

- `DamageType`：13 种伤害类型；
- `DamageComponent`：骰子/固定值/类型；
- `DamagePacket`：多段伤害；
- `DefenseProfile`：抗性/免疫/易伤；
- `MovementMode`：地面/飞行/游泳/攀爬/静止；
- `MobAction`：攻击、豁免区域、召唤、引导、特殊；
- `MobDefinition`：六维、豁免加值、速度 tick、防御、条件免疫、特性、动作、多重攻击、充能、传奇抗性。

`ContentSnapshot` 现在包含 `mobs`。加载：

```text
dndturn-content/default-mobs.json
dndturn-content/mobs/*.json
```

遭遇开始时随 `EncounterContent` 固定整份快照；之后 reload 不影响进行中的遭遇。

## 默认模板

`MobDefinition.fallback(entityId)`：

```text
STR/DEX/CON/INT/WIS/CHA = 10
speed = 30 ticks (ground)
attack = +5, 10 bludgeoning
```

`MobAttackProfiles.DEFAULT` 已接入近战/普通箭的既有攻击 fallback，所以未覆盖的第三方 Mob 也能参与回合制。

## 通用豁免

`SaveService`：

- 服务端 d20；
- 玩家使用职业豁免熟练；
- Mob 使用 `MobDefinition.saveBonuses`，否则六维修正；
- 掷骰后、提交前广播 `SaveRollEvent`，监听者可修改 die/bonus/DC；
- 结果在玩家动作栏显示；
- 不提供 QTE、不重掷、不自动重试。

## 还没完成

- 37 个怪物的数据规范化；
- `DamagePacket` 接入现有伤害链；
- 条件运行时；
- `MobAction` 的可执行步骤；
- 范围/召唤/引导/充能过程；
- 特殊动作的 AI 语义；
- 原生特殊攻击 adapter；
- 行为级测试与实机验收。

## 下一步

1. 把 `DamagePacket`/`DefenseProfile` 接入 `DamageTrace`；
2. 条件定义与运行时；
3. 以 Ghast 火球打通“范围 + 豁免 + 半伤 + 火焰免疫”的完整纵向切片。
