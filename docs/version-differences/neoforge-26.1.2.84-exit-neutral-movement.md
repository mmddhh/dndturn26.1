# NeoForge 26.1.2.84：退出与非敌对移动

## 固定版本接入

依据 target gradle.properties 与 minecraft-patched-26.1.2.84-sources.jar。

- `Mob.getTarget(): LivingEntity` 返回 `asValidTarget` 过滤后的当前目标，创造/旁观玩家和不可攻击目标会被过滤；override 保留动态分派。`ServerLevel.getAllEntities()` 仅枚举当前已加载实体，不主动加载区块。EXIT 在服务器线程完成版本、身份、当前目标与结束条件检查，再释放状态；成功重试先查退出账本。
- `ServerCombatService.exitEncounter` 是网络及 tactical exit 的共同入口。仅最后玩家主动退出可能结束会话，结束前检查会话所有玩家的当前受敌对状态；`stop/leave` 为内部生命周期及调试清理保留。退出凭据恢复后仍可解除原场地对非成员玩家的冻结；重新入会优先，合并沿用 canonical ID。
- `Mob.serverAiStep()` 是 final：sensing → targetSelector/goalSelector → navigation.tick → customServerAiStep → move/look/jump controls。既有 `MobNavigationLeaseMixin` 在租约期间门控两类 GoalSelector 调用和 customServerAiStep，保留导航与控制。没有新增可选注入。
- 非敌对 `PathfinderMob` + `GroundPathNavigation` 使用已有路径或 `DefaultRandomPos.getPos(mob, 10, 7)`，后者为固定版本 `RandomStrollGoal.getPosition()` 的原版参数。生成前检查该范围区块均已加载，候选终点校验场地；路径仍经过逐步预算、地形检查与原版碰撞。无路径/不支持模式发布明确拒绝并结束自身回合。
- Zombie 既有战术近战/目标选择能力保留。非敌对移动不放开新的攻击或 Brain 副作用；其他 Mob 的特殊动作、飞行与游泳导航不在本次支持范围。

## 验证场景

GameTest `prototype_exit_retry`：目标锁定时场内拒绝且版本不变，目标清空后原地退出，成功重试及负载冲突。

GameTest `consent_multiplayer`：目标从请求者切换至另一玩家后，原地退出并释放身体门控，其他玩家继续会话；最后玩家被锁定时越界仍拒绝结束，清空 target 后允许结束。

GameTest `neutral_mob_turn`：牛、羊进入先攻，候选阶段牛通过原版导航真实移动并收费，其他成员保持暂停，租约终结且不伪造敌意。真实双客户端、断线重连以及特殊 Mob 不由这些嵌入测试证明。

2026-09-26 本轮验证：JDK 25，NeoForge 26.1 独立 `runGameTestServer`，47 项 required tests 全部通过；包含以上三项及既有 Zombie 导航、伤害、PvP、生命周期回归。
独立 `clean build`（含 common 测试）通过；jar metadata、Mixin 配置与 common class 已检查。
