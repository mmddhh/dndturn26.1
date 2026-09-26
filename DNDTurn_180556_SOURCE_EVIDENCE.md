# DNDTurn 180556 / patched Minecraft 26.1.2.109 — 源码证据

本文件由实际读取的原始文件生成。代码块左侧数字是各原文件的 1-based 行号，不是本 Markdown 的行号。
证据级别：静态源码；独立 common 探针另见验证包。没有执行 Minecraft 客户端、NeoForge target 构建、Mixin 注入或 GameTest。
本轮未修改项目源文件，也未修改资料库文件。相机问题仅记录，留待下一轮合并。

- `DNDTurn-sources(20260925-161358).zip` SHA-256: `d4b9f2f77d9ea8f74e4e25f7cc1ec56d0093103f8f9a5b211920909e13b1b599`
- `DNDTurn-sources(20260925-180556).zip` SHA-256: `a42d6f6946c783bb6d95399a267de99376072ca80c21690c3ce051b90037d0c6`
- Library `/minecraft-vanilla/minecraft-patched-26.1.2.109-sources.jar` SHA-256: `d41e656b9c49bac09e6b301532d2f1ef7cfc8ec8920d7bbe63a875d154a84f8a`

注意：项目仍声明 NeoForge 26.1.2.84；资料库为 26.1.2.109 patched Minecraft 源码。参考源码不等于项目已升级，也不能替代 .84 的二进制/Mixin 验证。

## E00 — 版本和变更范围

新旧压缩包逐文件比较：新增 4、修改 69、删除 0。111 个 Java 文件；common 主源码 18 个。ZIP 不包含 docs/；不据此断言用户完整仓库没有 docs/。

### [180556] `targets/neoforge-26.1/gradle.properties` : L1–L3

```text
    1 | neoforge_261_minecraft_version=26.1
    2 | neoforge_261_version=26.1.2.84
    3 | neoforge_261_version_range=[26.1,)
```

### [180556] `targets/neoforge-26.1/build.gradle` : L1–L26

```text
    1 | plugins {
    2 |     id 'java-library'
    3 |     id 'net.neoforged.moddev' version '2.0.141'
    4 |     id 'me.modmuss50.mod-publish-plugin' version '2.1.1'
    5 | }
    6 | 
    7 | apply from: file('../../gradle/target-conventions/properties.gradle')
    8 | 
    9 | group = mod_group_id
   10 | version = mod_version
   11 | 
   12 | base {
   13 |     archivesName = "${mod_name}-neoforge-26.1"
   14 | }
   15 | 
   16 | java {
   17 |     toolchain.languageVersion = JavaLanguageVersion.of(25)
   18 | }
   19 | 
   20 | sourceSets.main.output.dir(
   21 |         project(':common').layout.buildDirectory.dir('classes/java/main'),
   22 |         builtBy: ':common:classes'
   23 | )
   24 | 
   25 | neoForge {
   26 |     version = neoforge_261_version
```

### [180556] `common/build.gradle` : L1–L27

```text
    1 | plugins {
    2 |     id 'java-library'
    3 | }
    4 | 
    5 | group = 'cc.sighs.dndturn'
    6 | version = '1.0.0-SNAPSHOT'
    7 | 
    8 | repositories {
    9 |     mavenCentral()
   10 | }
   11 | 
   12 | java {
   13 |     toolchain.languageVersion = JavaLanguageVersion.of(17)
   14 | }
   15 | 
   16 | dependencies {
   17 |     testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
   18 |     testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
   19 | }
   20 | 
   21 | tasks.named('test', Test).configure {
   22 |     useJUnitPlatform()
   23 | }
   24 | 
   25 | tasks.withType(JavaCompile).configureEach {
   26 |     options.encoding = 'UTF-8'
   27 |     options.release = 17
```

### 完整变更文件清单

```json
{
  "added": [
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalImpact.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/ClientKeyboardProbeAccessor.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalEscapeMixin.java",
    "targets/neoforge-26.1/src/main/resources/data/dndturn/test_instance/neutral_mob_turn.json"
  ],
  "removed": [],
  "changed": [
    "common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java",
    "common/src/main/java/cc/sighs/dndturn/combat/CombatRules.java",
    "common/src/main/java/cc/sighs/dndturn/combat/EncounterPhase.java",
    "common/src/main/java/cc/sighs/dndturn/combat/EncounterProjectionOrder.java",
    "common/src/main/java/cc/sighs/dndturn/combat/StartDisposition.java",
    "common/src/main/java/cc/sighs/dndturn/combat/TacticalIntent.java",
    "common/src/test/java/cc/sighs/dndturn/combat/CombatEngineTest.java",
    "common/src/test/java/cc/sighs/dndturn/combat/RepairRegressionTest.java",
    "common/src/test/java/cc/sighs/dndturn/combat/TacticalPlanTest.java",
    "targets/neoforge-26.1/build.gradle",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/DNDTurnNeoForge261.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientCombatState.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControlRegression.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientEntitySimulation.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientTacticalPlan.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ConsentOverlay.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/DedicatedClientProbe.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/MouseInputReset.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalOverlay.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalUiSmokeTest.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatIntentHandler.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatNetwork.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatRecoveryCandidate.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatSubscriptions.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/RangedBehavior.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/RegionalScheduledTicks.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerConsentCoordinator.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerEntityProjections.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalBehavior.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalItems.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalNetwork.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalUseContext.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaInputPolicy.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/gametest/LocalTimeGameTests.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/gametest/RepairGameTests.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/gametest/TacticalPlanGameTests.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/PredictionAckAccessor.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/ServerGamePacketListenerMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/ServerLevelEntityGateMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/TacticalBlockUseMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/TacticalDestroyAccessor.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/TacticalUseTickAccessor.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/AuiPointerStateAccessor.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/ClientEntitySimulationMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/ClientGameplayInputMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/ClientInputProbeAccessor.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/ClientPacketProbeMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalAuiDocumentMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalAuiInputMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalCameraEffectsMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalCameraMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalKeyboardInputMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalLocalPlayerMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalMinecraftInputMixin.java",
    "targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalMouseMixin.java",
    "targets/neoforge-26.1/src/main/resources/assets/apricityui/apricity/dndturn/consent.html",
    "targets/neoforge-26.1/src/main/resources/assets/apricityui/apricity/dndturn/tactical.css",
    "targets/neoforge-26.1/src/main/resources/assets/apricityui/apricity/dndturn/tactical.html",
    "targets/neoforge-26.1/src/main/resources/assets/dndturn/lang/en_us.json",
    "targets/neoforge-26.1/src/main/resources/assets/dndturn/lang/zh_cn.json",
    "targets/neoforge-26.1/src/main/resources/data/dndturn/test_environment/player_only_start.json",
    "targets/neoforge-26.1/src/main/resources/data/dndturn/test_instance/player_only_start.json",
    "targets/neoforge-26.1/src/main/resources/data/dndturn/test_instance/repair_values.json",
    "targets/neoforge-26.1/src/main/resources/dndturn.mixins.json"
  ]
}
```

## E01 — 致死攻击：先确定计划终态，再清理死亡

源码顺序已修订。独立 common 探针验证对应规则顺序；这不是 Minecraft 致死攻击实机验收。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java` : L424–L440

```text
  424 |         requireThread();
  425 |         if (engine.encounterOf(entityId) != null) pendingDeaths.add(entityId);
  426 |     }
  427 | 
  428 |     public void confirmPendingDeaths() {
  429 |         requireThread();
  430 |         if (worldEffectDepth != 0) return;
  431 |         for (UUID entityId : Set.copyOf(pendingDeaths)) {
  432 |             pendingDeaths.remove(entityId);
  433 |             Entity entity = resolve(entityId);
  434 |             if (entity instanceof LivingEntity living
  435 |                 && ((LivingEntityDeathAccessor) living).dndturn$isDead()) leave(entityId);
  436 |         }
  437 |     }
  438 |     public UUID encounterOf(UUID entityId) { return engine.encounterOf(entityId); }
  439 | 
  440 |     public UUID encounterAtBlock(ServerLevel level, BlockPos pos) {
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java` : L1891–L1911

```text
 1891 |     public OperationRecord.Result attack(LivingEntity actor, UUID targetId,
 1892 |                                                                   UUID operationId, long expectedVersion) {
 1893 |         return attack(actor, targetId, operationId, expectedVersion, null);
 1894 |     }
 1895 |     OperationRecord.Result attackPlan(ServerPlayer actor, UUID target, UUID operation, UUID parent) {
 1896 |         worldEffectDepth++;
 1897 |         try {
 1898 |             var result = attack(actor, target, operation, engine.stateView(engine.encounterOf(actor.getUUID())).version(), parent);
 1899 |             // The confirmed synchronous attack is the final step of this melee plan.
 1900 |             engine.publish(result.snapshot().encounterId(), parent, 0, result.outcome(), result.reason(), 0, 0, true);
 1901 |             return result;
 1902 |         } finally {
 1903 |             worldEffectDepth--;
 1904 |             if (worldEffectDepth == 0) {
 1905 |                 confirmPendingDeaths();
 1906 |                 for (UUID departed : worldEffectDepth == 0 ? Set.copyOf(departuresDuringWorldEffect) : Set.<UUID>of()) {
 1907 |                     departuresDuringWorldEffect.remove(departed);
 1908 |                     leave(departed);
 1909 |                 }
 1910 |             }
 1911 |         }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java` : L2038–L2058

```text
 2038 |         } catch (RuntimeException error) {
 2039 |             if (engine.resultFor(encounterId, damageId) == null)
 2040 |                 engine.publish(encounterId, damageId, 0, OperationRecord.Outcome.UNKNOWN,
 2041 |                     "world damage outcome unknown: " + error.getClass().getSimpleName(), 0, 0, true);
 2042 |             if (engine.resultFor(encounterId, operationId) == null)
 2043 |                 return engine.publish(encounterId, operationId, 0, OperationRecord.Outcome.UNKNOWN,
 2044 |                     "world damage outcome unknown", 0, 0, true);
 2045 |             return engine.resultFor(encounterId, operationId);
 2046 |         } finally {
 2047 |             worldEffectDepth--;
 2048 |             confirmPendingDeaths();
 2049 |             for (UUID departed : worldEffectDepth == 0 ? Set.copyOf(departuresDuringWorldEffect) : Set.<UUID>of()) {
 2050 |                 departuresDuringWorldEffect.remove(departed);
 2051 |                 leave(departed);
 2052 |             }
 2053 |             if (engine.encounterIds().contains(encounterId)) sync(engine.stateView(encounterId));
 2054 |         }
 2055 |         } catch (RuntimeException error) {
 2056 |             OperationRecord.Result known = engine.resultFor(encounterId, operationId);
 2057 |             if (known != null) return known;
 2058 |             if (!engine.encounterIds().contains(encounterId)) throw error;
```

### [180556] `common/src/main/java/cc/sighs/dndturn/combat/CombatEngine.java` : L493–L524

```text
  493 |         joined.eligibleRound = eligibleRound;
  494 |         applyRoll(joined, sampleRoll());
  495 |         encounter.members.put(member, joined);
  496 |         membership.put(member, encounterId);
  497 |         encounter.version = nextVersion;
  498 |         encounter.structuralRevision = nextStructuralRevision;
  499 |         return true;
  500 |     }
  501 | 
  502 |     public boolean leave(UUID encounterId, UUID memberId) {
  503 |         Objects.requireNonNull(memberId);
  504 |         Encounter encounter = require(encounterId);
  505 |         if (!encounter.members.containsKey(memberId)) return false;
  506 |         Math.addExact(encounter.version, (long) encounter.pending.size() + 1);
  507 |         long nextStructuralRevision = Math.addExact(encounter.structuralRevision, 1);
  508 |         for (OperationRecord.Snapshot snapshot : pendingChildrenFirst(encounter)) {
  509 |             OperationRecord.Snapshot root = encounter.causes.get(rootOf(encounter, snapshot));
  510 |             if (!memberId.equals(snapshot.owner()) && !memberId.equals(snapshot.target())
  511 |                 && (root == null || !memberId.equals(root.target()))) continue;
  512 |             int step = encounter.stepResults.getOrDefault(snapshot.operationId(), Map.of()).size();
  513 |             boolean unstartedPlan = snapshot.kind() == OperationRecord.Kind.PLAN
  514 |                 && encounter.causes.values().stream().noneMatch(child -> snapshot.operationId().equals(child.parentId())
  515 |                     && child.kind() != OperationRecord.Kind.MOVE);
  516 |             publish(encounterId, snapshot.operationId(), step,
  517 |                 unstartedPlan ? OperationRecord.Outcome.INTERRUPTED : OperationRecord.Outcome.UNKNOWN,
  518 |                 unstartedPlan ? "member left before behavior execution" : "member left before world outcome was confirmed", 0, 0, true);
  519 |         }
  520 |         int index = encounter.order.indexOf(memberId);
  521 |         boolean wasCurrent = index == encounter.cursor && encounter.phase != EncounterPhase.ENVIRONMENT;
  522 |         encounter.members.remove(memberId);
  523 |         membership.remove(memberId);
  524 |         encounter.structuralRevision = nextStructuralRevision;
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java` : L340–L375

```text
  340 |         ServerPlayer player = server.getPlayerList().getPlayer(owner);
  341 |         if (player == null) for (var level : server.getAllLevels()) {
  342 |             if (level.getEntity(owner) instanceof ServerPlayer found) { player = found; break; }
  343 |         }
  344 |         if (player != null) try { TacticalCapabilities.resolve(execution.root.intent()).cancel(this, player, execution); }
  345 |         catch (RuntimeException failure) { player.stopUsingItem(); reason = "behavior cleanup failed: " + failure.getMessage(); }
  346 |         service.finishPlanMovement(owner);
  347 |         if (execution.action != null && engine.encounterIds().contains(execution.root.encounterId())
  348 |             && engine.pendingOperation(execution.root.encounterId(), execution.action) != null) {
  349 |             engine.publish(execution.root.encounterId(), execution.action, execution.actionStep++, OperationRecord.Outcome.INTERRUPTED,
  350 |                 reason == null ? "interrupted" : reason, 0, 0, true);
  351 |             execution.action = null;
  352 |         }
  353 |         finish(player, execution, OperationRecord.Outcome.INTERRUPTED, reason == null ? "interrupted" : reason);
  354 |     }
  355 |     void finish(ServerPlayer player, Execution execution, OperationRecord.Outcome outcome, String reason) {
  356 |         var root = execution.root;
  357 |         if (engine.encounterIds().contains(root.encounterId()) && engine.pendingOperation(root.encounterId(), root.operationId()) != null)
  358 |             engine.publish(root.encounterId(), root.operationId(), 0, outcome, reason, 0, 0, true);
  359 |         executions.remove(root.owner());
  360 |         var terminal = engine.resultFor(root.encounterId(), root.operationId());
  361 |         if (terminal != null) reason = terminal.reason();
  362 |         if (player != null) { service.resyncMember(player, root.encounterId()); send(player, root, null, false, reason); }
  363 |     }
  364 |     void send(ServerPlayer player, OperationRecord.Snapshot root, GridCell point, boolean running, String reason) {
  365 |         send(player, root.encounterId(), root.operationId(), point, running, reason);
  366 |     }
  367 |     private void send(ServerPlayer player, UUID encounter, UUID operation, GridCell point, boolean running, String reason) {
  368 |         if (!NetworkRegistry.hasChannel(player.connection, TacticalNetwork.Projection.TYPE.id())) return;
  369 |         var result = running ? null : engine.resultFor(encounter, operation);
  370 |         var message = result != null && result.snapshot().kind() == OperationRecord.Kind.PLAN
  371 |             ? TacticalNetwork.Projection.terminal(service.generation(), ++sequence, result)
  372 |             : new TacticalNetwork.Projection(service.generation(), encounter, operation, ++sequence, point, running, reason,
  373 |                 running ? null : OperationRecord.Outcome.REJECTED, 0);
  374 |         PacketDistributor.sendToPlayer(player, message);
  375 |     }
```

## E02 — 统一选择、选槽事务和客户端状态

数字键和 UI 调同一选槽入口；服务器读取并确认显式槽位；能力来自服务器，不再仅是无人读取的本地字段。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientTacticalPlan.java` : L15–L51

```text
   15 | public final class ClientTacticalPlan {
   16 |     private static TacticalNetwork.Projection projection;
   17 |     private static long sequence = -1;
   18 |     private static UUID requested;
   19 |     public enum Phase { IDLE, TARGETING, CONTEXT_MENU, AWAITING_SERVER, EXECUTING, CANCEL_PENDING }
   20 |     private static Phase phase = Phase.IDLE;
   21 |     private static long revision;
   22 |     private static int slot;
   23 |     private static String itemName = "";
   24 |     private static String behavior;
   25 |     private static TacticalIntent.Target selectedTarget;
   26 |     private static TacticalIntent.Capability preferredCost;
   27 |     private static java.util.List<TacticalNetwork.Ability> abilities = java.util.List.of();
   28 |     private static TacticalIntent.ItemReference confirmedItem;
   29 |     private static boolean arming, submitAfterQuery;
   30 |     public static Phase phase() { return phase; }
   31 |     public static java.util.List<TacticalNetwork.Ability> abilities() { return abilities; }
   32 |     public static void selectBehavior(TacticalNetwork.Ability value) {
   33 |         if (running() || !abilities.contains(value)) return;
   34 |         invalidate(); arming = false; behavior = value.id(); phase = Phase.TARGETING;
   35 |         reason = value.label() + " · 请选择目标";
   36 |         if (value.targets().contains(TacticalIntent.TargetKind.SELF)) { submitAfterQuery = true; target(null,null); }
   37 |     }
   38 |     public static UUID inspected() { return selectedTarget == null ? null : selectedTarget.entity(); }
   39 |     public static void inspect(UUID value) {
   40 |         if (running()) return;
   41 |         invalidate(); arming = false; behavior = null; phase = Phase.IDLE;
   42 |         var world = Minecraft.getInstance().level;
   43 |         selectedTarget = value == null || world == null ? null : new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY,world.dimension().identifier().toString(),value,null,-1,0,0,0);
   44 |         reason = "检查对象";
   45 |     }
   46 |     private static void invalidate() { revision++; queryId = null; options = null; submitAfterQuery = false; }
   47 |     public static void selectSlot(int value) {
   48 |         if (running()) { reason = "执行中不能换槽，请先取消计划"; return; }
   49 |         preferredCost = null; confirmedItem = null; invalidate(); slot = value; hand = TacticalIntent.Hand.MAIN_HAND; behavior = null; phase = Phase.TARGETING; arming = true;
   50 |         target(null, null);
   51 |     }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientTacticalPlan.java` : L69–L105

```text
   69 |     public static void receiveOptions(TacticalNetwork.Options value, IPayloadContext context) {
   70 |         if (Boolean.getBoolean("dndturn.controlProbe")) ClientControlRegression.captureOptions(() -> receiveOptions(value, context));
   71 |         var connection = context.connection(); var level = Minecraft.getInstance().level; var player = Minecraft.getInstance().player;
   72 |         context.enqueueWork(() -> {
   73 |             var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter();
   74 |             if (state == null || mc.level != level || mc.player != player || mc.getConnection() == null || mc.getConnection().getConnection() != connection
   75 |                 || !state.generation().equals(value.generation()) || !state.encounterId().equals(value.encounter()) || !value.query().equals(queryId) || value.revision() != revision) return;
   76 |             options = value; itemName = value.itemName(); abilities = value.abilities(); reason = value.reason().isEmpty() ? "选择目标或服务器提供的行为" : value.reason();
   77 |             if (value.item() == null) { phase = Phase.IDLE; arming = false; confirmedItem = null; submitAfterQuery = false; return; }
   78 |             confirmedItem = value.item();
   79 |             if (arming) { if (behavior == null) behavior = preferredCost == null ? value.defaultBehavior() : abilities.stream().filter(a -> a.cost() == preferredCost).map(TacticalNetwork.Ability::id).findFirst().orElse(value.defaultBehavior()); phase = Phase.TARGETING; arming = false; reason = abilities.stream().filter(a -> a.id().equals(behavior)).map(TacticalNetwork.Ability::label).findFirst().orElse("能力不可用") + " · 请选择目标"; }
   80 |             else if (submitAfterQuery) {
   81 |                 submitAfterQuery = false;
   82 |                 var selected = value.offers().stream().filter(o -> o.intent().behaviorId().equals(behavior)).findFirst();
   83 |                 if (selected.isPresent() && selected.get().reason().isEmpty()) choose(selected.get());
   84 |                 else { phase = Phase.TARGETING; reason = selected.map(TacticalNetwork.Offer::reason).orElse("目标不支持所选行为"); }
   85 |             }
   86 |         });
   87 |     }
   88 |     public static void choose(TacticalNetwork.Offer offer) {
   89 |         var state = ClientCombatState.encounter();
   90 |         if (state == null || options == null || running() || !offer.reason().isEmpty() || !options.offers().contains(offer)) return;
   91 |         queryId = null; requested = UUID.randomUUID(); phase = Phase.AWAITING_SERVER;
   92 |         ClientPacketDistributor.sendToServer(new TacticalNetwork.Request(state.generation(), state.encounterId(), requested, options.version(), offer.intent(), false));
   93 |         options = null; reason = "等待服务器验证";
   94 |     }
   95 | 
   96 |     private static String reason = "左键地面移动；右键对象查看行为";
   97 |     private ClientTacticalPlan() {}
   98 |     public static void reset() { phase = Phase.IDLE; selectedTarget = null; preferredCost = null; abilities = java.util.List.of(); confirmedItem = null; revision++; itemName = ""; behavior = null; arming = false; submitAfterQuery = false; slot = 0; queryId = null; options = null; hand = TacticalIntent.Hand.MAIN_HAND; projection = null; sequence = -1; requested = null; endAfterCancel = false; reason = "左键地面移动；右键对象查看行为"; }
   99 |     public static void select(TacticalIntent.Capability value) {
  100 |         if (running()) return;
  101 |         preferredCost = value;
  102 |         behavior = switch (value) { case MOVE -> "dndturn:move"; case ATTACK -> null; case PLACE -> "dndturn:place"; case BREAK -> "dndturn:break"; case USE_BLOCK -> "dndturn:block"; case USE_ITEM -> null; };
  103 |         var p = Minecraft.getInstance().player; if (p == null) return;
  104 |         confirmedItem = null; slot = p.getInventory().getSelectedSlot(); phase = Phase.TARGETING; arming = true; target(null, null);
  105 |     }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java` : L108–L121

```text
  108 |     /** Called after vanilla KeyMapping.set/click, before the next client input tick. */
  109 |     public static void onKeyInput(InputEvent.Key event) {
  110 |         if (!ClientControl.key(event.getKeyEvent(), event.getAction())) return;
  111 |         Minecraft game = Minecraft.getInstance();
  112 |         if (event.getAction() == GLFW.GLFW_RELEASE && JUMP.matches(event.getKeyEvent())) releaseJump();
  113 |         if (game.player == null || game.screen != null || !game.isWindowActive() || ClientControl.modal()) return;
  114 |         boolean tactical = ClientCombatState.encounter() != null;
  115 |         if (tactical && event.getAction() == GLFW.GLFW_PRESS) {
  116 |             for (int i = 0; i < game.options.keyHotbarSlots.length; i++)
  117 |                 if (game.options.keyHotbarSlots[i].matches(event.getKeyEvent())
  118 |                     && game.options.keyHotbarSlots[i].isActiveAndMatches(InputConstants.getKey(event.getKeyEvent()))
  119 |                     && !matchesAction(event.getKeyEvent())) {
  120 |                     ClientTacticalPlan.selectSlot(i); clear(game.options.keyHotbarSlots[i]); return;
  121 |                 }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/TacticalOverlay.java` : L132–L198

```text
  132 |         values.clear(); members.clear(); memberLabels.clear(); memberFaces.clear(); logs.clear(); memberOrder = List.of();
  133 |         java.util.Arrays.fill(inventory, null);
  134 |         ability("move", cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE, "移动按实际获准位移计费；再次点击停止。结束回合会先结算移动。");
  135 |         ability("attack", cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK, "消耗动作。d20＋5 对目标 AC；自然 1 未命中，自然 20 暴击。选择目标后自动接近并攻击。");
  136 |         action("dash", CombatNetwork.IntentKind.DASH, "消耗动作，增加本会话捕获的一份基础移动预算。");
  137 |         action("dodge", CombatNetwork.IntentKind.DODGE, "消耗动作，直到下次自身回合开始前，对你的命中检定有劣势。");
  138 |         action("disengage", CombatNetwork.IntentKind.DISENGAGE, "消耗动作，设置本回合撤离状态。借机攻击流程尚未开放。");
  139 |         action("end", CombatNetwork.IntentKind.END_TURN, "先结束并结算正在进行的移动，然后请求结束回合。");
  140 |         action("exit", CombatNetwork.IntentKind.EXIT, "没有 Mob 以你为目标时可直接退出；否则须先中心越界。其他玩家继续回合制；仍有玩家被 Mob 锁定时不能结束整个会话。");
  141 |         ability("place", cc.sighs.dndturn.combat.TacticalIntent.Capability.PLACE, "选择放置面；原版接受放置后消耗一次动作。");
  142 |         ability("use-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_BLOCK, "使用方块自身，不消耗动作；接近仍消耗移动。");
  143 |         ability("break-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.BREAK, "选择要破坏的方块。");
  144 |         ability("use-item", cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_ITEM, "使用当前物品。");
  145 |         document.getElementById("behavior-hand").addEventListener("click", event -> { ClientTacticalPlan.toggleHand(); });
  146 |         document.getElementById("cancel-plan").addEventListener("click", event -> ClientTacticalPlan.cancel());
  147 |         document.getElementById("inventory-toggle").addEventListener("click", event -> toggleInventory());
  148 |         attribute("inventory-panel", "style", inventoryOpen ? "display: block" : "display: none");
  149 |         Element slots = document.getElementById("inventory");
  150 |         for (int i = 0; i < inventory.length; i++) {
  151 |             Element slot = document.createElement("slot");
  152 |             slot.setAttribute("class", "inventory-slot");
  153 |             final int selectedSlot = i;
  154 |             slot.addEventListener("click", event -> {
  155 |                 var player = Minecraft.getInstance().player;
  156 |                 if (player == null) return;
  157 |                 if (selectedSlot > 8) { Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(player)); return; }
  158 |                 ClientTacticalPlan.selectSlot(selectedSlot);
  159 |             });
  160 |             Element item = document.createElement("item");
  161 |             item.setAttribute("id", "inventory-" + i);
  162 |             slot.appendChild(item);
  163 |             slots.appendChild(slot);
  164 |         }
  165 |         document.getElementById("pointer").addEventListener("click", event -> togglePointer());
  166 |     }
  167 | 
  168 |     private static ClientTacticalPlan.Phase renderedPhase;
  169 |     private static List<cc.sighs.dndturn.combat.TacticalNetwork.Ability> renderedAbilities = List.of();
  170 |     private static List<cc.sighs.dndturn.combat.TacticalNetwork.Offer> renderedOffers = List.of();
  171 |     private static final java.util.List<Element> behaviorButtons = new java.util.ArrayList<>();
  172 |     private static void renderBehaviors() {
  173 |         var offers = ClientTacticalPlan.offers();
  174 |         var abilities = ClientTacticalPlan.abilities();
  175 |         if (offers.equals(renderedOffers) && abilities.equals(renderedAbilities) && renderedPhase == ClientTacticalPlan.phase()) return;
  176 |         renderedPhase = ClientTacticalPlan.phase();
  177 |         var host = document.getElementById("behavior-options");
  178 |         for (var button : behaviorButtons) host.removeChild(button);
  179 |         behaviorButtons.clear();
  180 |         if (ClientTacticalPlan.phase() != ClientTacticalPlan.Phase.CONTEXT_MENU) for (var ability : abilities) {
  181 |             var button = document.createElement("button");
  182 |             button.setTextContent(ability.label() + " · " + (ability.cost() == cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE ? "移动" : ability.cost() == cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_BLOCK ? "0 动作" : "1 动作"));
  183 |             button.addEventListener("click", event -> ClientTacticalPlan.selectBehavior(ability));
  184 |             host.appendChild(button); behaviorButtons.add(button);
  185 |         }
  186 |         if (ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.CONTEXT_MENU) for (var offer : offers) {
  187 |             var button = document.createElement("button");
  188 |             button.setAttribute("id", "offer-" + offer.intent().behaviorId().replace(':','-'));
  189 |             button.setTextContent(offer.label() + (offer.approach() ? " · 需接近" : " · 已在范围内") + (offer.intent().requiresAction() ? " · 1 动作" : " · 0 动作") + (offer.reason().isEmpty() ? "" : " · " + offer.reason()));
  190 |             if (!offer.reason().isEmpty()) button.setAttribute("disabled", "");
  191 |             button.addEventListener("click", event -> ClientTacticalPlan.choose(offer));
  192 |             host.appendChild(button);
  193 |             behaviorButtons.add(button);
  194 |         }
  195 |         renderedOffers = offers; renderedAbilities = abilities;
  196 |     }
  197 |     private static void ability(String id, cc.sighs.dndturn.combat.TacticalIntent.Capability capability, String description) {
  198 |         var element = document.getElementById(id);
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalActions.java` : L53–L116

```text
   53 |     public TacticalNetwork.Options discover(ServerPlayer player, TacticalNetwork.Query query) {
   54 |         if (!server.isSameThread()) throw new IllegalStateException("server thread required");
   55 |         var offers = new ArrayList<TacticalNetwork.Offer>();
   56 |         long version = 0;
   57 |         try {
   58 |             if (!service.matchesGeneration(query.generation()) || !query.encounter().equals(service.encounterOf(player.getUUID()))
   59 |                 || !service.mayOrganizeInventory(player)) throw new IllegalStateException("not an interactive member turn");
   60 |             if (running(player.getUUID())) throw new IllegalStateException("cannot change selection while plan is running");
   61 |             if (query.revision() <= 0 || query.hand() == TacticalIntent.Hand.MAIN_HAND && (query.slot() < 0 || query.slot() > 8)
   62 |                 || query.hand() == TacticalIntent.Hand.OFF_HAND && query.slot() != 40) throw new IllegalStateException("invalid selection slot");
   63 |             var previousSelection = selections.get(player.getUUID());
   64 |             if (previousSelection != null && previousSelection.request().encounter().equals(query.encounter())) {
   65 |                 if (previousSelection.request().equals(query)) return previousSelection.response();
   66 |                 if (query.revision() <= previousSelection.request().revision()) throw new IllegalStateException("stale or conflicting selection");
   67 |             }
   68 |             var selectedStack = query.hand() == TacticalIntent.Hand.MAIN_HAND ? player.getInventory().getItem(query.slot()) : player.getOffhandItem();
   69 |             if (query.expectedItem() != null && (query.expectedItem().slot() != query.slot()
   70 |                 || !query.expectedItem().revision().equals(TacticalItems.revision(player, selectedStack)))) throw new IllegalStateException("selected item changed; select it again");
   71 |             if (query.hand() == TacticalIntent.Hand.MAIN_HAND) player.getInventory().setSelectedSlot(query.slot());
   72 |             VanillaInputPolicy.correctInventory(player);
   73 |             var state = engine.stateView(query.encounter()); version = state.version();
   74 |             var selected = query.target();
   75 |             for (var adapter : TacticalCapabilities.all()) {
   76 |                 if (!adapter.supportsItem(selectedStack)) continue;
   77 |                 TacticalIntent.Target target = selected;
   78 |                 if (adapter.targets().contains(TacticalIntent.TargetKind.SELF))
   79 |                     target = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF, selected.dimension(), null, null, -1, 0, 0, 0);
   80 |                 else if (selected.kind() == TacticalIntent.TargetKind.BLOCK && adapter.targets().contains(TacticalIntent.TargetKind.GROUND)) {
   81 |                     BlockPos adjacent = pos(selected.cell()).relative(Direction.values()[selected.face()]);
   82 |                     target = new TacticalIntent.Target(TacticalIntent.TargetKind.GROUND, selected.dimension(), null, cell(adjacent), -1, 0, 0, 0);
   83 |                 }
   84 |                 if (!adapter.targets().contains(target.kind())) continue;
   85 |                 var hand = InteractionHand.valueOf(query.hand().name());
   86 |                 var item = new TacticalIntent.ItemReference(hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40,
   87 |                     TacticalItems.revision(player, player.getItemInHand(hand)));
   88 |                 var intent = new TacticalIntent(adapter.id(), adapter.version(), query.hand(), adapter.cost(), target, item);
   89 |                 String reason = "";
   90 |                 boolean approach = false;
   91 |                 try {
   92 |                     validate(player, intent, state);
   93 |                     if (intent.requiresAction() && !state.members().get(player.getUUID()).action()) reason = "action unavailable";
   94 |                     else if (!adapter.canExecute(player, intent, player.position())) {
   95 |                         approach = true;
   96 |                         if (state.members().get(player.getUUID()).movementTicks() == 0) reason = "movement budget exhausted";
   97 |                         else path(player, intent, state, targetCell(player,intent));
   98 |                     }
   99 |                 } catch (RuntimeException rejected) { reason = rejected.getMessage() == null ? "behavior unavailable" : rejected.getMessage(); }
  100 |                 offers.add(new TacticalNetwork.Offer(adapter.label(), intent, reason, approach));
  101 |             }
  102 |             var response = new TacticalNetwork.Options(service.generation(), query.encounter(), query.query(), version, offers, "", query.revision(),
  103 |                 new TacticalIntent.ItemReference(query.slot(), TacticalItems.revision(player, player.getItemInHand(InteractionHand.valueOf(query.hand().name())))),
  104 |                 defaultBehavior(player.getItemInHand(InteractionHand.valueOf(query.hand().name()))),
  105 |                 TacticalCapabilities.all().stream().filter(a -> a.supportsItem(selectedStack))
  106 |                     .map(a -> new TacticalNetwork.Ability(a.id(),a.label(),a.cost(),a.targets())).toList(), selectedStack.getHoverName().getString());
  107 |             selections.put(player.getUUID(),new Selection(query,response));
  108 |             return response;
  109 |         } catch (RuntimeException failure) {
  110 |             VanillaInputPolicy.correctInventory(player);
  111 |             return new TacticalNetwork.Options(service.generation(), query.encounter(), query.query(), version, List.of(),
  112 |                 failure.getMessage() == null ? "discovery unavailable" : failure.getMessage(), query.revision(), null, "", List.of(), "");
  113 |         }
  114 |     }
  115 |     private static String defaultBehavior(net.minecraft.world.item.ItemStack stack) {
  116 |         if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) return "dndturn:place";
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalNetwork.java` : L15–L59

```text
   15 |     private TacticalNetwork() {}
   16 |     private static <T> StreamCodec<ByteBuf, T> codec(Class<T> type) {
   17 |         var text = ByteBufCodecs.stringUtf8(32768);
   18 |         return StreamCodec.of((buffer, value) -> text.encode(buffer, JSON.toJson(value)),
   19 |             buffer -> Objects.requireNonNull(JSON.fromJson(text.decode(buffer), type)));
   20 |     }
   21 |     public record Query(UUID generation, UUID encounter, UUID query, TacticalIntent.Target target,
   22 |                         TacticalIntent.Hand hand, int slot, long revision, TacticalIntent.ItemReference expectedItem) implements CustomPacketPayload {
   23 |         public Query(UUID generation, UUID encounter, UUID query, TacticalIntent.Target target, TacticalIntent.Hand hand) {
   24 |             this(generation, encounter, query, target, hand, hand == TacticalIntent.Hand.MAIN_HAND ? 0 : 40, System.nanoTime(), null);
   25 |         }
   26 |         public Query { Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(query); Objects.requireNonNull(target); Objects.requireNonNull(hand); }
   27 |         public static final Type<Query> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "behavior_query"));
   28 |         public static final StreamCodec<ByteBuf, Query> CODEC = codec(Query.class);
   29 |         public Type<? extends CustomPacketPayload> type() { return TYPE; }
   30 |     }
   31 |     public record Ability(String id, String label, TacticalIntent.Capability cost, java.util.Set<TacticalIntent.TargetKind> targets) {}
   32 |     public record Offer(String label, TacticalIntent intent, String reason, boolean approach) {
   33 |         public Offer { Objects.requireNonNull(label); Objects.requireNonNull(intent); Objects.requireNonNull(reason); }
   34 |     }
   35 |     public record Options(UUID generation, UUID encounter, UUID query, long version, java.util.List<Offer> offers,
   36 |                           String reason, long revision, TacticalIntent.ItemReference item, String defaultBehavior, java.util.List<Ability> abilities, String itemName) implements CustomPacketPayload {
   37 |         public Options { abilities = java.util.List.copyOf(abilities); offers = java.util.List.copyOf(offers); Objects.requireNonNull(reason); }
   38 |         public static final Type<Options> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "behavior_options"));
   39 |         public static final StreamCodec<ByteBuf, Options> CODEC = codec(Options.class);
   40 |         public Type<? extends CustomPacketPayload> type() { return TYPE; }
   41 |     }
   42 |     public record Request(UUID generation, UUID encounter, UUID operation, long version,
   43 |                           TacticalIntent intent, boolean cancel) implements CustomPacketPayload {
   44 |         public Request {
   45 |             Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(operation);
   46 |             if (version < 0 || !cancel && intent == null || cancel && intent != null)
   47 |                 throw new IllegalArgumentException("invalid plan request");
   48 |         }
   49 |         public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "plan_request"));
   50 |         public static final StreamCodec<ByteBuf, Request> CODEC = codec(Request.class);
   51 |         @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
   52 |     }
   53 |     public record Projection(UUID generation, UUID encounter, UUID operation, long sequence,
   54 |                              GridCell waypoint, boolean running, String reason, OperationRecord.Outcome outcome, long version) implements CustomPacketPayload {
   55 |         public static Projection terminal(UUID generation, long sequence, OperationRecord.Result result) {
   56 |             if (!result.terminal() || result.snapshot().kind() != OperationRecord.Kind.PLAN) throw new IllegalArgumentException("terminal plan required");
   57 |             return new Projection(generation,result.snapshot().encounterId(),result.snapshot().operationId(),sequence,null,false,result.reason(),result.outcome(),result.publishedVersion());
   58 |         }
   59 |         public Projection {
```

## E03 — 仍未闭合的 Idle→地面查询→Esc 路径

worldClick 最后一支设置 submitAfterQuery 但不离开 IDLE；Esc 只在 phase 非 IDLE 时 cancel；迟到 Options 会 choose。属于源码可达时序风险，未在实际客户端复现。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientTacticalPlan.java` : L46–L94

```text
   46 |     private static void invalidate() { revision++; queryId = null; options = null; submitAfterQuery = false; }
   47 |     public static void selectSlot(int value) {
   48 |         if (running()) { reason = "执行中不能换槽，请先取消计划"; return; }
   49 |         preferredCost = null; confirmedItem = null; invalidate(); slot = value; hand = TacticalIntent.Hand.MAIN_HAND; behavior = null; phase = Phase.TARGETING; arming = true;
   50 |         target(null, null);
   51 |     }
   52 |     public static void worldClick(boolean right, UUID entity, net.minecraft.world.phys.BlockHitResult hit) {
   53 |         if (right && (phase == Phase.TARGETING || phase == Phase.CONTEXT_MENU || running())) { cancel(); return; }
   54 |         if (running()) return;
   55 |         if (arming) { reason = "等待服务器确认物品"; return; }
   56 |         if (right) { slot = Minecraft.getInstance().player.getInventory().getSelectedSlot(); behavior = null; arming = false; phase = Phase.CONTEXT_MENU; target(entity, hit); return; }
   57 |         if (phase == Phase.TARGETING) { arming = false; submitAfterQuery = true; target(entity, hit); return; }
   58 |         if (entity != null) { inspect(entity); return; }
   59 |         slot = Minecraft.getInstance().player.getInventory().getSelectedSlot();
   60 |         behavior = "dndturn:move"; submitAfterQuery = true; arming = false; target(null, hit);
   61 |     }
   62 |     private static long terminalVersion;
   63 |     private static boolean endAfterCancel;
   64 |     private static UUID queryId;
   65 |     private static TacticalNetwork.Options options;
   66 |     private static TacticalIntent.Hand hand = TacticalIntent.Hand.MAIN_HAND;
   67 |     public static java.util.List<TacticalNetwork.Offer> offers() { return options == null ? java.util.List.of() : options.offers(); }
   68 |     public static void toggleHand() { if (running()) return; preferredCost = null; confirmedItem = null; invalidate(); hand = hand == TacticalIntent.Hand.MAIN_HAND ? TacticalIntent.Hand.OFF_HAND : TacticalIntent.Hand.MAIN_HAND; arming = true; phase = Phase.TARGETING; behavior = null; target(null, null); }
   69 |     public static void receiveOptions(TacticalNetwork.Options value, IPayloadContext context) {
   70 |         if (Boolean.getBoolean("dndturn.controlProbe")) ClientControlRegression.captureOptions(() -> receiveOptions(value, context));
   71 |         var connection = context.connection(); var level = Minecraft.getInstance().level; var player = Minecraft.getInstance().player;
   72 |         context.enqueueWork(() -> {
   73 |             var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter();
   74 |             if (state == null || mc.level != level || mc.player != player || mc.getConnection() == null || mc.getConnection().getConnection() != connection
   75 |                 || !state.generation().equals(value.generation()) || !state.encounterId().equals(value.encounter()) || !value.query().equals(queryId) || value.revision() != revision) return;
   76 |             options = value; itemName = value.itemName(); abilities = value.abilities(); reason = value.reason().isEmpty() ? "选择目标或服务器提供的行为" : value.reason();
   77 |             if (value.item() == null) { phase = Phase.IDLE; arming = false; confirmedItem = null; submitAfterQuery = false; return; }
   78 |             confirmedItem = value.item();
   79 |             if (arming) { if (behavior == null) behavior = preferredCost == null ? value.defaultBehavior() : abilities.stream().filter(a -> a.cost() == preferredCost).map(TacticalNetwork.Ability::id).findFirst().orElse(value.defaultBehavior()); phase = Phase.TARGETING; arming = false; reason = abilities.stream().filter(a -> a.id().equals(behavior)).map(TacticalNetwork.Ability::label).findFirst().orElse("能力不可用") + " · 请选择目标"; }
   80 |             else if (submitAfterQuery) {
   81 |                 submitAfterQuery = false;
   82 |                 var selected = value.offers().stream().filter(o -> o.intent().behaviorId().equals(behavior)).findFirst();
   83 |                 if (selected.isPresent() && selected.get().reason().isEmpty()) choose(selected.get());
   84 |                 else { phase = Phase.TARGETING; reason = selected.map(TacticalNetwork.Offer::reason).orElse("目标不支持所选行为"); }
   85 |             }
   86 |         });
   87 |     }
   88 |     public static void choose(TacticalNetwork.Offer offer) {
   89 |         var state = ClientCombatState.encounter();
   90 |         if (state == null || options == null || running() || !offer.reason().isEmpty() || !options.offers().contains(offer)) return;
   91 |         queryId = null; requested = UUID.randomUUID(); phase = Phase.AWAITING_SERVER;
   92 |         ClientPacketDistributor.sendToServer(new TacticalNetwork.Request(state.generation(), state.encounterId(), requested, options.version(), offer.intent(), false));
   93 |         options = null; reason = "等待服务器验证";
   94 |     }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientTacticalPlan.java` : L123–L183

```text
  123 |     public static void receive(TacticalNetwork.Projection state, IPayloadContext context) {
  124 |         if (Boolean.getBoolean("dndturn.controlProbe") && state.running()) ClientControlRegression.captureRunningProjection(() -> receive(state, context));
  125 |         var connection = context.connection();
  126 |         var level = Minecraft.getInstance().level;
  127 |         var player = Minecraft.getInstance().player;
  128 |         context.enqueueWork(() -> {
  129 |             var mc = Minecraft.getInstance();
  130 |             var encounter = ClientCombatState.encounter();
  131 |             if (mc.level != level || mc.player != player || mc.getConnection() == null
  132 |                 || mc.getConnection().getConnection() != connection || encounter == null
  133 |                 || !encounter.generation().equals(state.generation()) || !encounter.encounterId().equals(state.encounter())
  134 |                 || state.sequence() <= sequence || requested == null || !requested.equals(state.operation())) return;
  135 |             sequence = state.sequence(); projection = state; reason = state.reason();
  136 |             requested = state.running() ? state.operation() : null;
  137 |             if (!state.running()) { phase = Phase.IDLE; projection = null; confirmedItem = null; }
  138 |             else if (phase != Phase.CANCEL_PENDING) phase = Phase.EXECUTING;
  139 |             if (!state.running()) terminalVersion = state.version();
  140 |             tick();
  141 |         });
  142 |     }
  143 |     public static void tick() {
  144 |         var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter();
  145 |         if (endAfterCancel && !running() && state != null && state.version() >= terminalVersion
  146 |             && mc.screen == null && mc.isWindowActive() && !ClientControl.modal()) {
  147 |             endAfterCancel = false;
  148 |             CombatControls.requestFromUi(CombatNetwork.IntentKind.END_TURN, null);
  149 |         }
  150 |     }
  151 |     public static boolean running() { return requested != null; }
  152 |     public static void endTurn() {
  153 |         if (!running()) { CombatControls.requestFromUi(CombatNetwork.IntentKind.END_TURN, null); return; }
  154 |         endAfterCancel = true; cancel();
  155 |     }
  156 |     public static void cancel() {
  157 |         invalidate(); arming = false; behavior = null;
  158 |         if (!running()) { selectedTarget = null; phase = Phase.IDLE; reason = "已取消选择"; return; }
  159 |         phase = Phase.CANCEL_PENDING;
  160 |         var state = ClientCombatState.encounter();
  161 |         if (requested != null && state != null)
  162 |             ClientPacketDistributor.sendToServer(new TacticalNetwork.Request(state.generation(), state.encounterId(), requested,
  163 |                 state.version(), null, true));
  164 |     }
  165 |     public static void target(UUID entity, net.minecraft.world.phys.BlockHitResult hit) {
  166 |         var mc = Minecraft.getInstance();
  167 |         var state = ClientCombatState.encounter();
  168 |         if (state == null || mc.player == null || running()) return;
  169 |         String dimension = mc.level.dimension().identifier().toString();
  170 |         TacticalIntent.Target target;
  171 |         if (entity != null) target = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, dimension, entity, null, -1, 0, 0, 0);
  172 |         else if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
  173 |             target = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF, dimension, null, null, -1, 0, 0, 0);
  174 |         else {
  175 |             BlockPos pos = hit.getBlockPos(); Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
  176 |             target = new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK, dimension, null, new GridCell(pos.getX(), pos.getY(), pos.getZ()),
  177 |                 hit.getDirection().ordinal(), Math.clamp(local.x, 0, 1), Math.clamp(local.y, 0, 1), Math.clamp(local.z, 0, 1));
  178 |         }
  179 |         selectedTarget = target;
  180 |         revision++; queryId = UUID.randomUUID(); options = null;
  181 |         ClientPacketDistributor.sendToServer(new TacticalNetwork.Query(state.generation(), state.encounterId(), queryId, target, hand, hand == TacticalIntent.Hand.MAIN_HAND ? slot : 40, revision, confirmedItem));
  182 |         reason = "查询可用行为";
  183 |     }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java` : L335–L348

```text
  335 |     private static boolean escapeHeld;
  336 |     public static boolean escape(int action, net.minecraft.client.input.KeyEvent event) {
  337 |         var mc = Minecraft.getInstance();
  338 |         if (event.key() != GLFW.GLFW_KEY_ESCAPE) return false;
  339 |         if (action == GLFW.GLFW_RELEASE) { boolean consumed = escapeHeld; escapeHeld = false; return consumed; }
  340 |         if (escapeHeld) return true;
  341 |         if (action != GLFW.GLFW_PRESS || mc.screen != null || !mc.isWindowActive()) return false;
  342 |         if (modal()) { escapeHeld = true; return true; }
  343 |         if (!session) return false;
  344 |         if (ClientTacticalPlan.phase() != ClientTacticalPlan.Phase.IDLE) {
  345 |             ClientTacticalPlan.cancel(); escapeHeld = true; return true;
  346 |         }
  347 |         if (TacticalOverlay.closePanel()) { escapeHeld = true; return true; }
  348 |         return false;
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientCombatState.java` : L88–L112

```text
   88 |     public static void receiveEncounter(CombatNetwork.EncounterState state, IPayloadContext context) {
   89 |         PacketOrigin origin = captureOrigin(context);
   90 |         context.enqueueWork(() -> {
   91 |             refreshSession();
   92 |             if (!acceptsOrigin(origin)) return;
   93 |             var update = EncounterProjectionOrder.advance(
   94 |                 lastEncounter == null ? null : stamp(lastEncounter),
   95 |                 nextResultIndex, resyncPending, stamp(state));
   96 |             var decision = update.decision();
   97 |             if (decision == EncounterProjectionOrder.Decision.IGNORE) return;
   98 |             nextResultIndex = update.nextResultIndex();
   99 |             resyncPending = update.resyncPending();
  100 |             if (decision == EncounterProjectionOrder.Decision.ACCEPT_RESET_RESULTS) {
  101 |                 latestResult = null;
  102 |                 results.clear();
  103 |                 latestStatus = null;
  104 |                 CombatControls.resetForNewEncounter();
  105 |                 ClientTacticalPlan.reset();
  106 |             }
  107 |             expectedResultCount = state.resultCount();
  108 |             lastEncounter = state;
  109 |             encounter = state.active() ? state : null;
  110 |             if (!state.active()) ClientTacticalPlan.reset();
  111 |             CombatControls.onEncounterState(state);
  112 |         });
```

## E04 — 关闭旧公开世界动作旁路

旧 ATTACK / MOVE_BEGIN / MOVE_END 新请求明确拒绝；前面的历史结果重试只返回已记录结果，不重新执行。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/CombatIntentHandler.java` : L74–L118

```text
   74 |             OperationRecord.Kind retryKind = switch (intent.kind()) {
   75 |                 case MOVE_BEGIN, MOVE_END -> OperationRecord.Kind.MOVE;
   76 |                 case ATTACK -> OperationRecord.Kind.ATTACK;
   77 |                 case END_TURN -> OperationRecord.Kind.END_TURN;
   78 |                 case DASH -> OperationRecord.Kind.DASH;
   79 |                 case DODGE -> OperationRecord.Kind.DODGE;
   80 |                 case DISENGAGE -> OperationRecord.Kind.DISENGAGE;
   81 |                 default -> null;
   82 |             };
   83 |             if (retryKind != null) {
   84 |                 var completed = service.completedRetry(player, intent.encounterId(), intent.operationId(),
   85 |                     retryKind, intent.expectedVersion(), intent.targetId(),
   86 |                     intent.kind() != IntentKind.MOVE_END);
   87 |                 if (completed != null) {
   88 |                     service.resyncMember(player, intent.encounterId());
   89 |                     sendIntentStatus(player, new IntentStatus(service.generation(), intent.encounterId(),
   90 |                         intent.operationId(), intent.kind(), true,
   91 |                         completed.reason(), true, completed.outcome().code(),
   92 |                         completed.actualMovementTicks(), completed.actualDamage()));
   93 |                     player.sendOverlayMessage(Component.literal("DNDTurn retry: " + completed.reason()));
   94 |                     return;
   95 |                 }
   96 |             }
   97 |             if (!intent.encounterId().equals(service.encounterOf(player.getUUID())))
   98 |                 throw new IllegalStateException("combat session is no longer active");
   99 |             switch (intent.kind()) {
  100 |                 case EXIT -> {
  101 |                     service.exitEncounter(player, intent.encounterId(), intent.operationId(),
  102 |                         intent.expectedVersion());
  103 |                 }
  104 |                 case MOVE_BEGIN, MOVE_END, ATTACK -> throw new IllegalStateException("legacy world action disabled; submit a tactical plan");
  105 |                 case END_TURN -> service.endTurn(intent.encounterId(), player, intent.operationId(),
  106 |                     intent.expectedVersion());
  107 |                 case DASH -> {
  108 |                     if (intent.targetId() != null) throw new IllegalStateException("DASH does not accept a target");
  109 |                     var result = service.dash(player, intent.operationId(), intent.expectedVersion());
  110 |                     player.sendOverlayMessage(Component.literal("DNDTurn: " + result.reason()));
  111 |                 }
  112 |                 case DODGE, DISENGAGE -> {
  113 |                     if (intent.targetId() != null) throw new IllegalStateException(intent.kind() + " does not accept a target");
  114 |                     OperationRecord.Kind kind = intent.kind() == IntentKind.DODGE
  115 |                         ? OperationRecord.Kind.DODGE : OperationRecord.Kind.DISENGAGE;
  116 |                     var result = service.defensiveAction(player, intent.operationId(),
  117 |                         intent.expectedVersion(), kind);
  118 |                     player.sendOverlayMessage(Component.literal("DNDTurn: " + result.reason()));
```

## E05 — 放置实际影响范围：受限类型适配

影响范围校验在 invoke 之前。此处是已知直接写入范围的保守白名单，不构成所有模组方块或所有邻居连锁效果的通用证明。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/TacticalImpact.java` : L13–L69

```text
   13 | /** Fixed .84 direct-write contracts. Unknown overrides never inherit placement permission. */
   14 | public final class TacticalImpact {
   15 |     private TacticalImpact() {}
   16 | 
   17 |     public static void authorize(ServerPlayer player, BlockPos pos) {
   18 |         var level = player.level();
   19 |         var service = ServerCombatService.forServer(level.getServer());
   20 |         var id = service.encounterOf(player.getUUID());
   21 |         if (id == null || !level.hasChunkAt(pos) || !level.isInWorldBounds(pos)
   22 |             || !level.getWorldBorder().isWithinBounds(pos)
   23 |             || !service.state(id).region().containsBlock(pos.getX(), pos.getY(), pos.getZ())
   24 |             || !id.equals(service.encounterAtBlock(level, pos)))
   25 |             throw new IllegalStateException("effect outside loaded encounter");
   26 |         if (level.getServer().isUnderSpawnProtection(level, pos, player) || !level.mayInteract(player, pos))
   27 |             throw new IllegalStateException("protected effect destination");
   28 |     }
   29 | 
   30 |     public static void itemOnBlock(UseOnContext use) {
   31 |         if (!(use.getPlayer() instanceof ServerPlayer player)) return;
   32 |         authorize(player, use.getClickedPos());
   33 |         var item = use.getItemInHand().getItem();
   34 |         if (item instanceof BlockItem blockItem) {
   35 |             if (use.getItemInHand().has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
   36 |                 || !use.getItemInHand().getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_STATE,
   37 |                     net.minecraft.world.item.component.BlockItemStateProperties.EMPTY).isEmpty())
   38 |                 throw new IllegalStateException("custom placement state requires an impact adapter");
   39 |             if (item.getClass() != BlockItem.class && item.getClass() != DoubleHighBlockItem.class && item.getClass() != BedItem.class)
   40 |                 throw new IllegalStateException("unsupported placement override");
   41 |             var block = blockItem.getBlock();
   42 |             Set<Class<?>> supported = Set.of(Block.class, RotatedPillarBlock.class, SlabBlock.class,
   43 |                 StairBlock.class, DoorBlock.class, BedBlock.class, DoublePlantBlock.class);
   44 |             if (!supported.contains(block.getClass())) throw new IllegalStateException("placement impact adapter unavailable");
   45 |             var context = new BlockPlaceContext(use);
   46 |             BlockPos destination = context.getClickedPos();
   47 |             Set<BlockPos> writes = new LinkedHashSet<>();
   48 |             writes.add(destination);
   49 |             if (item instanceof DoubleHighBlockItem || block instanceof DoorBlock || block instanceof DoublePlantBlock)
   50 |                 writes.add(destination.above());
   51 |             if (block instanceof BedBlock) writes.add(destination.relative(context.getHorizontalDirection()));
   52 |             for (var pos : writes) {
   53 |                 authorize(player, pos);
   54 |                 if (!player.mayUseItemAt(pos,use.getClickedFace(),use.getItemInHand())) throw new IllegalStateException("placement permission denied");
   55 |             }
   56 |             // Placement state queries inspect neighboring blocks; do not let these load chunks.
   57 |             for (var pos : BlockPos.betweenClosed(destination.offset(-1,-1,-1), destination.offset(1,2,1)))
   58 |                 if (!player.level().hasChunkAt(pos)) throw new IllegalStateException("placement neighborhood unloaded");
   59 |             var state = block.getStateForPlacement(context);
   60 |             if (!context.canPlace() || state == null || !state.canSurvive(player.level(), destination))
   61 |                 throw new IllegalStateException("placement unavailable");
   62 |             for (var pos : writes)
   63 |                 if (!player.level().isUnobstructed(state, pos, CollisionContext.placementContext(player)))
   64 |                     throw new IllegalStateException("placement obstructed");
   65 |         } else if (item.getClass() != AxeItem.class && item.getClass() != HoeItem.class && item.getClass() != ShovelItem.class) {
   66 |             throw new IllegalStateException("item impact adapter unavailable");
   67 |         }
   68 |     }
   69 | }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java` : L100–L122

```text
  100 |     static final class ItemOnBlock extends Interaction {
  101 |         private final Predicate<ItemStack> supports;
  102 |         ItemOnBlock(String id, String label, TacticalIntent.Capability cost, Predicate<ItemStack> supports) {
  103 |             super(id, label, cost, TacticalIntent.TargetKind.BLOCK); this.supports = supports;
  104 |         }
  105 |         public boolean supportsItem(ItemStack stack) { return supports.test(stack); }
  106 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) { return supports.test(stack(p, i)) ? null : "item-on-block contract unavailable"; }
  107 |         public void prepare(TacticalActions a, ServerPlayer p, Execution e) {
  108 |             super.prepare(a,p,e);
  109 |             TacticalImpact.itemOnBlock(new net.minecraft.world.item.context.UseOnContext(p,hand(e.root.intent()),hit(e.root.intent())));
  110 |         }
  111 |         protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
  112 |             try (var context = new TacticalUseContext(p, pos(i.target().cell()), false)) {
  113 |                 return p.gameMode.useItemOn(p, p.level(), stack(p, i), hand(i), hit(i));
  114 |             }
  115 |         }
  116 |     }
  117 |     static final class Consume extends Interaction {
  118 |         public boolean supportsItem(ItemStack stack) { return stack.has(DataComponents.CONSUMABLE); }
  119 |         Consume() { super("dndturn:consume", "自身使用 / 食饮", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.SELF); }
  120 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
  121 |             return stack(p, i).has(DataComponents.CONSUMABLE) ? null : "consumable component required";
  122 |         }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/VanillaBehaviors.java` : L133–L165

```text
  133 |             if (p.level().getServer().isUnderSpawnProtection(p.level(), adjacent, p) || !p.level().mayInteract(p, adjacent)) return "protected fluid destination";
  134 |             return null;
  135 |         }
  136 |         protected ClipContext.Fluid fluids(ServerPlayer p, TacticalIntent i) { return stack(p, i).is(Items.BUCKET) ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE; }
  137 |         public void prepare(TacticalActions a, ServerPlayer p, Execution e) {
  138 |             super.prepare(a, p, e);
  139 |             TacticalImpact.authorize(p, pos(e.root.intent().target().cell()));
  140 |             TacticalImpact.authorize(p, pos(e.root.intent().target().cell()).relative(Direction.values()[e.root.intent().target().face()]));
  141 |             var i = e.root.intent();
  142 |             var clicked = p.level().getBlockState(pos(i.target().cell()));
  143 |             var adjacent = p.level().getBlockState(pos(i.target().cell()).relative(Direction.values()[i.target().face()]));
  144 |             if (stack(p,i).is(Items.BUCKET)) {
  145 |                 if (clicked.getBlock().getClass() != net.minecraft.world.level.block.LiquidBlock.class)
  146 |                     throw new IllegalStateException("bucket pickup impact adapter unavailable");
  147 |             } else if (clicked.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer
  148 |                 || !adjacent.isAir() && adjacent.getBlock().getClass() != net.minecraft.world.level.block.LiquidBlock.class)
  149 |                 throw new IllegalStateException("bucket replacement impact adapter unavailable");
  150 |             var eye = p.getEyePosition();
  151 |             var actual = p.level().clip(new ClipContext(eye, eye.add(p.calculateViewVector(p.getXRot(), p.getYRot()).scale(p.blockInteractionRange())), ClipContext.Block.OUTLINE, fluids(p, i), p));
  152 |             if (actual.getType() != HitResult.Type.BLOCK || !actual.getBlockPos().equals(pos(i.target().cell())) || actual.getDirection().ordinal() != i.target().face())
  153 |                 throw new IllegalStateException("bucket vanilla ray no longer matches target and face: " + actual.getType() + " " + actual.getBlockPos() + " " + actual.getDirection() + " eye=" + eye + " view=" + p.getViewVector(1));
  154 |         }
  155 |         protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) { return p.gameMode.useItem(p, p.level(), stack(p, i), hand(i)); }
  156 |     }
  157 |     static final class EntityItem extends Interaction {
  158 |         public boolean supportsItem(ItemStack stack) { return stack.is(Items.NAME_TAG); }
  159 |         EntityItem() { super("dndturn:entity_item", "物品对实体使用", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.ENTITY); }
  160 |         public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
  161 |             if (!stack(p, i).is(Items.NAME_TAG)) return "entity item effects require a registered contract";
  162 |             return attackTarget(p, i, s);
  163 |         }
  164 |         protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
  165 |             var target = p.level().getEntity(i.target().entity());
```

## E06 — 输入入口和回归覆盖

Home/O/Ctrl 已注册，I 可打开实际背包；新 raw 输入路径已写入测试，但本轮没有运行这些客户端测试。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java` : L22–L41

```text
   22 | /** Rebindable prototype controls. Packets carry intent, never authoritative costs or damage. */
   23 | public final class CombatControls {
   24 |     private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
   25 |         Identifier.fromNamespaceAndPath(DNDTurnNeoForge261.MOD_ID, "prototype"));
   26 |     private static final KeyMapping UI = key("ui", GLFW.GLFW_KEY_GRAVE_ACCENT);
   27 |     private static final KeyMapping START = new KeyMapping("key.dndturn.start", KeyConflictContext.IN_GAME,
   28 |         KeyModifier.SHIFT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
   29 |     private static final KeyMapping EXIT = key("exit", GLFW.GLFW_KEY_UNKNOWN);
   30 |     private static final KeyMapping MOVE = key("move", GLFW.GLFW_KEY_UNKNOWN);
   31 |     private static final KeyMapping ATTACK = key("attack", GLFW.GLFW_KEY_LEFT_CONTROL);
   32 |     private static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_UNKNOWN);
   33 |     private static final KeyMapping DODGE = key("dodge", GLFW.GLFW_KEY_UNKNOWN);
   34 |     private static final KeyMapping DISENGAGE = key("disengage", GLFW.GLFW_KEY_UNKNOWN);
   35 |     private static final KeyMapping END_TURN = key("end_turn", GLFW.GLFW_KEY_SPACE);
   36 |     private static final KeyMapping INVENTORY = key("inventory", GLFW.GLFW_KEY_I);
   37 |     private static final KeyMapping ROTATE_LEFT = key("rotate_left", GLFW.GLFW_KEY_Q);
   38 |     private static final KeyMapping ROTATE_RIGHT = key("rotate_right", GLFW.GLFW_KEY_E);
   39 |     private static final KeyMapping HOME = key("camera_home", GLFW.GLFW_KEY_HOME);
   40 |     private static final KeyMapping PRESET = key("camera_preset", GLFW.GLFW_KEY_O);
   41 |     private static final KeyMapping JUMP = key("jump", GLFW.GLFW_KEY_UNKNOWN);
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/CombatControls.java` : L87–L105

```text
   87 |             if (ClientCombatState.encounter() != null) { requestExit(); continue; }
   88 |             if (startOperationId == null) startOperationId = UUID.randomUUID();
   89 |             send(CombatNetwork.IntentKind.START, null, startOperationId);
   90 |         }
   91 |         while (EXIT.consumeClick()) requestExit();
   92 |         while (MOVE.consumeClick()) ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE);
   93 |         while (ATTACK.consumeClick()) {
   94 |             ClientTacticalPlan.select(cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK);
   95 |         }
   96 |         while (DASH.consumeClick()) send(CombatNetwork.IntentKind.DASH, null);
   97 |         while (DODGE.consumeClick()) send(CombatNetwork.IntentKind.DODGE, null);
   98 |         while (DISENGAGE.consumeClick()) send(CombatNetwork.IntentKind.DISENGAGE, null);
   99 |         while (END_TURN.consumeClick()) requestEndTurn();
  100 |         while (INVENTORY.consumeClick()) game.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(game.player));
  101 |         while (HOME.consumeClick()) ClientControl.home();
  102 |         while (PRESET.consumeClick()) ClientControl.preset();
  103 |         while (ROTATE_LEFT.consumeClick()) { }
  104 |         while (ROTATE_RIGHT.consumeClick()) { }
  105 |         while (JUMP.consumeClick()) { /* Held state is forwarded by onKeyInput. */ }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControlRegression.java` : L204–L240

```text
  204 |         var mc = Minecraft.getInstance(); var camera = mc.gameRenderer.getMainCamera();
  205 |         var delta = target.subtract(ClientControl.position());
  206 |         var local = new org.joml.Vector3f((float)delta.x,(float)delta.y,(float)delta.z)
  207 |             .rotate(new org.joml.Quaternionf(camera.rotation()).conjugate());
  208 |         double tan = Math.tan(Math.toRadians(mc.options.fov().get())/2);
  209 |         double aspect = (double)mc.getWindow().getScreenWidth()/mc.getWindow().getScreenHeight();
  210 |         point((local.x/(-local.z*tan*aspect)+1)/2, (1-local.y/(-local.z*tan))/2);
  211 |     }
  212 |     private static void planTick() throws Exception {
  213 |         var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter(); stageTicks++;
  214 |         require(stageTicks < 400,"raw input stage timeout " + activeStage + " " + ClientTacticalPlan.description()+" current="+state.current()+" self="+mc.player.getUUID()+" action="+state.action()+" status="+ClientCombatState.latestStatus());
  215 |         switch (activeStage) {
  216 |             case 0 -> {
  217 |                 if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) Files.writeString(output().resolveSibling("active-ready.txt"), "ready");
  218 |                 if (!mc.player.getUUID().equals(state.current())) return;
  219 |                 TacticalOverlay.closePanel(); ClientTacticalPlan.cancel(); ClientControl.home();
  220 |                 actor = mc.player.position(); budget = state.movementTicks();
  221 |                 nextStage();
  222 |             }
  223 |             case 1 -> {
  224 |                 if (stageTicks == 5) { pointAt(actor.add(2,0,0)); button(1,1); button(1,0); }
  225 |                 if (stageTicks < 15) return;
  226 |                 require(ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.CONTEXT_MENU && !ClientTacticalPlan.offers().isEmpty(),"right click did not open target menu");
  227 |                 rawKey(GLFW.GLFW_KEY_ESCAPE);
  228 |                 require(!ClientTacticalPlan.running() && ClientTacticalPlan.offers().isEmpty(),"menu cancellation submitted an action");
  229 |                 pointAt(actor.add(2,0,0)); button(0,1); button(0,0); nextStage();
  230 |             }
  231 |             case 2 -> {
  232 |                 if (stageTicks < 20 || ClientTacticalPlan.running()) return;
  233 |                 require(mc.player.position().distanceToSqr(actor)>1 && state.movementTicks()<budget,"raw ground click did not move and charge");
  234 |                 rawKey(GLFW.GLFW_KEY_1); rawKey(GLFW.GLFW_KEY_2); rawKey(GLFW.GLFW_KEY_3); nextStage();
  235 |             }
  236 |             case 3 -> {
  237 |                 if (stageTicks < 10) return;
  238 |                 if (cancelledSelection) {
  239 |                     require(ClientTacticalPlan.phase()==ClientTacticalPlan.Phase.IDLE && ClientTacticalPlan.offers().isEmpty(),"late duplicate Options revived cancelled selection");
  240 |                     rawKey(GLFW.GLFW_KEY_3); nextStage(); return;
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControlRegression.java` : L264–L305

```text
  264 |                 require(ClientControl.mode()==ClientControl.Mode.CAMERA,"plan changed camera mode");
  265 |                 if (Boolean.getBoolean("dndturn.controlProbe.requirePeer")) {
  266 |                     var evidence=com.google.gson.JsonParser.parseString(Files.readString(output().resolveSibling("peer-state.json"))).getAsJsonObject();
  267 |                     require(evidence.get("independent").getAsBoolean(),"peer camera changed");
  268 |                     require(new Vec3(evidence.get("primaryX").getAsDouble(),evidence.get("primaryY").getAsDouble(),evidence.get("primaryZ").getAsDouble()).distanceToSqr(mc.player.position())<.01,"peer actor did not converge");
  269 |                 }
  270 |                 // First hostile execution rolls initiative; its owner need not act first.
  271 |                 // Wait for the authoritative turn before testing the end-turn key.
  272 |                 if (!mc.player.getUUID().equals(state.current())) return;
  273 |                 rawKey(GLFW.GLFW_KEY_SPACE); nextStage();
  274 |             }
  275 |             case 6 -> {
  276 |                 if (!mc.player.getUUID().equals(state.current()) || !state.action()) return;
  277 |                 require(replayRunning!=null,"no running projection captured");
  278 |                 replayRunning.run(); replayRunning.run();
  279 |                 rawKey(GLFW.GLFW_KEY_1); ClientControl.home(); nextStage();
  280 |             }
  281 |             case 7 -> {
  282 |                 require(!ClientTacticalPlan.running(),"late running projection revived terminal operation");
  283 |                 if (stageTicks<10 || ClientTacticalPlan.phase()!=ClientTacticalPlan.Phase.TARGETING) return;
  284 |                 placed=mc.player.blockPosition().offset(-1,0,1);
  285 |                 require(mc.level.getBlockState(placed).isAir(),"placement fixture occupied");
  286 |                 pointAt(Vec3.atLowerCornerOf(placed).add(.5,0,.5)); button(0,1); button(0,0); nextStage();
  287 |             }
  288 |             case 8 -> {
  289 |                 if (stageTicks<20 || ClientTacticalPlan.running()) return;
  290 |                 require(mc.level.getBlockState(placed).is(net.minecraft.world.level.block.Blocks.DIRT)
  291 |                     && mc.player.getMainHandItem().getCount()==7 && !state.action(),"raw placement did not place/charge once: "+ClientTacticalPlan.description());
  292 |                 pointAt(new Vec3(1.5,120.2,-1.5)); button(1,1); button(1,0); nextStage();
  293 |             }
  294 |             case 9 -> {
  295 |                 if (stageTicks<10 || ClientTacticalPlan.offers().isEmpty()) return;
  296 |                 var offer=ClientTacticalPlan.offers().stream().filter(o -> o.intent().behaviorId().equals("dndturn:block")).findFirst().orElseThrow();
  297 |                 require(offer.reason().isEmpty(),"free block disabled after action spent: "+offer.reason());
  298 |                 element("offer-dndturn-block"); button(0,1); button(0,0); nextStage();
  299 |             }
  300 |             case 10 -> {
  301 |                 if (stageTicks<20 || ClientTacticalPlan.running()) return;
  302 |                 var lever=mc.level.getBlockState(new net.minecraft.core.BlockPos(1,120,-2));
  303 |                 require(lever.is(net.minecraft.world.level.block.Blocks.LEVER) && lever.getValue(net.minecraft.world.level.block.LeverBlock.POWERED)
  304 |                     && !state.action() && mc.player.getMainHandItem().getCount()==7,"menu free interaction failed or fell back to item: "+ClientTacticalPlan.description());
  305 |                 finish("PASS: real keyboard 1/2/3, Esc, Space; target menu cancel and menu-item mouse activation; late duplicate Options and running projections ignored after cancellation/terminal; rendered-camera MoveTo, approach attack, one paid placement and free lever after action spent; camera isolation and peer convergence.");
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControlRegression.java` : L538–L550

```text
  538 |         var mc = Minecraft.getInstance(); var doc = TacticalOverlay.document();
  539 |         var rect = doc.getElementById(id).getBoundingClientRect();
  540 |         var p = doc.documentToScreenPosition(new Position(rect.x + rect.width / 2, rect.y + rect.height / 2));
  541 |         cursor(p.x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
  542 |             p.y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
  543 |     }
  544 |     private static void button(int button, int action) {
  545 |         mouse().dndturn$button(Minecraft.getInstance().getWindow().handle(), new MouseButtonInfo(button, 0), action);
  546 |     }
  547 |     private static void require(boolean condition, String text) { if (!condition) throw new IllegalStateException(text); }
  548 |     private static void finish(String result) {
  549 |         done = true;
  550 |         replayOptions = null; replayRunning = null;
```

## V01 — 当前暂停门控只保留网络维护/位姿收敛

非本地实体在 ClientLevel.tickNonPassenger HEAD 被取消；本地实体另在 LocalPlayer.tick 被取消。暂停车辆仍递归处理乘客。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/ClientEntitySimulationMixin.java` : L14–L44

```text
   14 | @Mixin(ClientLevel.class)
   15 | public abstract class ClientEntitySimulationMixin {
   16 |     @Shadow private void tickPassenger(Entity vehicle, Entity passenger) {
   17 |         throw new AssertionError("Mixin shadow");
   18 |     }
   19 | 
   20 |     @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
   21 |     private void dndturn$entity(Entity entity, CallbackInfo ci) {
   22 |         if (entity != Minecraft.getInstance().player && !entity.isRemoved() && ClientEntitySimulation.paused(entity)) {
   23 |             dndturn$pausedProjection(entity);
   24 |             ci.cancel();
   25 |         }
   26 |     }
   27 | 
   28 |     @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
   29 |     private void dndturn$passenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
   30 |         if (passenger != Minecraft.getInstance().player && !passenger.isRemoved() && passenger.getVehicle() == vehicle
   31 |             && ClientEntitySimulation.paused(passenger)) {
   32 |             dndturn$pausedProjection(passenger);
   33 |             ci.cancel();
   34 |         }
   35 |     }
   36 | 
   37 |     @Unique private void dndturn$pausedProjection(Entity entity) {
   38 |         entity.setOldPosAndRot();
   39 |         // Keep vanilla convergence to received positions, without gravity, travel or body timers.
   40 |         if (entity != Minecraft.getInstance().player && entity.isInterpolating())
   41 |             entity.getInterpolation().interpolate();
   42 |         // A paused vehicle does not own the simulation or lifecycle of every passenger.
   43 |         for (Entity passenger : entity.getPassengers()) tickPassenger(entity, passenger);
   44 |     }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalLocalPlayerMixin.java` : L13–L32

```text
   13 | /** Preserve connection input/position maintenance when the server pauses bodily simulation. */
   14 | @Mixin(LocalPlayer.class)
   15 | public abstract class TacticalLocalPlayerMixin {
   16 |     @Shadow private Input lastSentInput;
   17 |     @Shadow protected abstract void sendPosition();
   18 |     @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   19 |     private void dndturn$maintenance(CallbackInfo ci) {
   20 |         if (!ClientCombatState.bodySimulationPaused()) return;
   21 |         LocalPlayer player = (LocalPlayer)(Object)this;
   22 |         if (!player.isAlive()) return;
   23 |         player.input.keyPresses = Input.EMPTY;
   24 |         if (player.connection.hasClientLoaded()) {
   25 |             if (!lastSentInput.equals(Input.EMPTY)) {
   26 |                 player.connection.send(new ServerboundPlayerInputPacket(Input.EMPTY));
   27 |                 lastSentInput = Input.EMPTY;
   28 |             }
   29 |             sendPosition();
   30 |         }
   31 |         ci.cancel();
   32 |     }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/ClientMovementBodyMixin.java` : L12–L29

```text
   12 | /** Local movement prediction keeps running while body effects and active item use remain paused. */
   13 | @Mixin(LivingEntity.class)
   14 | public abstract class ClientMovementBodyMixin {
   15 |     private boolean dndturn$holdBody() {
   16 |         return (Object) this == Minecraft.getInstance().player
   17 |             && ClientCombatState.holdBodySubsystemsDuringMovement();
   18 |     }
   19 | 
   20 |     @Inject(method = "tickEffects", at = @At("HEAD"), cancellable = true)
   21 |     private void dndturn$holdEffects(CallbackInfo callback) {
   22 |         if (dndturn$holdBody()) callback.cancel();
   23 |     }
   24 | 
   25 |     @Inject(method = "updateUsingItem(Lnet/minecraft/world/item/ItemStack;)V",
   26 |         at = @At("HEAD"), cancellable = true)
   27 |     private void dndturn$holdItemUse(ItemStack stack, CallbackInfo callback) {
   28 |         if (dndturn$holdBody()) callback.cancel();
   29 |     }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientEntitySimulation.java` : L1–L39

```text
    1 | package cc.sighs.dndturn.client;
    2 | 
    3 | import cc.sighs.dndturn.combat.CombatNetwork;
    4 | import java.util.HashMap;
    5 | import java.util.Map;
    6 | import java.util.UUID;
    7 | import net.minecraft.client.Minecraft;
    8 | import net.minecraft.world.entity.Entity;
    9 | import net.neoforged.neoforge.network.handling.IPayloadContext;
   10 | 
   11 | /** Display-only tracking projection. Never writes positions or submits damage/resource values. */
   12 | public final class ClientEntitySimulation {
   13 |     private static final Map<UUID, CombatNetwork.EntitySimulation> states = new HashMap<>();
   14 |     private ClientEntitySimulation() {}
   15 |     public static void clear() { states.clear(); }
   16 | 
   17 |     public static void receive(CombatNetwork.EntitySimulation state, IPayloadContext context) {
   18 |         var origin = context.connection();
   19 |         var world = Minecraft.getInstance().level;
   20 |         context.enqueueWork(() -> {
   21 |             ClientCombatState.refreshSession();
   22 |             var minecraft = Minecraft.getInstance();
   23 |             if (minecraft.getConnection() == null || minecraft.getConnection().getConnection() != origin
   24 |                 || world == null || minecraft.level != world
   25 |                 || !world.dimension().identifier().toString().equals(state.dimension())) return;
   26 |             var previous = states.get(state.entityId());
   27 |             if (previous != null && previous.generation().equals(state.generation())
   28 |                 && previous.sequence() >= state.sequence()) return;
   29 |             states.put(state.entityId(), state);
   30 |         });
   31 |     }
   32 | 
   33 |     public static boolean paused(Entity entity) {
   34 |         if (entity == Minecraft.getInstance().player) return ClientCombatState.bodySimulationPaused();
   35 |         var state = states.get(entity.getUUID());
   36 |         return state != null && state.tracked() && state.paused()
   37 |             && state.dimension().equals(entity.level().dimension().identifier().toString());
   38 |     }
   39 | }
```

## V02 — .109 tick 与渲染状态提取边界

tickCount/实际身体 tick/渲染帧并不相同。最终 render-state modifier 位于完整 extract 与 finalize 之后。建议只覆盖表现状态，不能为了 ageInTicks 写回实体 tickCount。

### [26.1.2.109 reference] `net/minecraft/client/multiplayer/ClientLevel.java` : L342–L387

```text
  342 |     public void tickEntities() {
  343 |         this.tickingEntities.forEach(entity -> {
  344 |             if (!entity.isRemoved() && !entity.isPassenger() && !this.tickRateManager.isEntityFrozen(entity)) {
  345 |                 this.guardEntityTick(this::tickNonPassenger, entity);
  346 |             }
  347 |         });
  348 |     }
  349 | 
  350 |     public boolean isTickingEntity(Entity entity) {
  351 |         return this.tickingEntities.contains(entity);
  352 |     }
  353 | 
  354 |     @Override
  355 |     public boolean shouldTickDeath(Entity entity) {
  356 |         return entity.chunkPosition().getChessboardDistance(this.minecraft.player.chunkPosition()) <= this.serverSimulationDistance;
  357 |     }
  358 | 
  359 |     public void tickNonPassenger(Entity entity) {
  360 |         entity.setOldPosAndRot();
  361 |         entity.tickCount++;
  362 |         Profiler.get().push(entity.typeHolder()::getRegisteredName);
  363 |         // Neo: Permit cancellation of Entity#tick via EntityTickEvent.Pre
  364 |         if (!net.neoforged.neoforge.event.EventHooks.fireEntityTickPre(entity).isCanceled()) {
  365 |             entity.tick();
  366 |             net.neoforged.neoforge.event.EventHooks.fireEntityTickPost(entity);
  367 |         }
  368 |         Profiler.get().pop();
  369 | 
  370 |         for (Entity passenger : entity.getPassengers()) {
  371 |             this.tickPassenger(entity, passenger);
  372 |         }
  373 |     }
  374 | 
  375 |     private void tickPassenger(Entity vehicle, Entity entity) {
  376 |         if (entity.isRemoved() || entity.getVehicle() != vehicle) {
  377 |             entity.stopRiding();
  378 |         } else if (entity instanceof Player || this.tickingEntities.contains(entity)) {
  379 |             entity.setOldPosAndRot();
  380 |             entity.tickCount++;
  381 |             entity.rideTick();
  382 | 
  383 |             for (Entity passenger : entity.getPassengers()) {
  384 |                 this.tickPassenger(entity, passenger);
  385 |             }
  386 |         }
  387 |     }
```

### [26.1.2.109 reference] `net/minecraft/client/renderer/entity/EntityRenderer.java` : L163–L193

```text
  163 |     public abstract S createRenderState();
  164 | 
  165 |     public final S createRenderState(T entity, float partialTicks) {
  166 |         S state = this.createRenderState();
  167 |         this.extractRenderState(entity, state, partialTicks);
  168 |         this.finalizeRenderState(entity, state);
  169 |         net.neoforged.neoforge.client.renderstate.RenderStateExtensions.onUpdateEntityRenderState(this, entity, state);
  170 |         return state;
  171 |     }
  172 | 
  173 |     public void extractRenderState(T entity, S state, float partialTicks) {
  174 |         state.entityType = entity.getType();
  175 |         state.x = Mth.lerp((double)partialTicks, entity.xOld, entity.getX());
  176 |         state.y = Mth.lerp((double)partialTicks, entity.yOld, entity.getY());
  177 |         state.z = Mth.lerp((double)partialTicks, entity.zOld, entity.getZ());
  178 |         state.isInvisible = entity.isInvisible();
  179 |         state.partialTick = partialTicks;
  180 |         state.ageInTicks = entity.tickCount + partialTicks;
  181 |         state.boundingBoxWidth = entity.getBbWidth();
  182 |         state.boundingBoxHeight = entity.getBbHeight();
  183 |         state.eyeHeight = entity.getEyeHeight();
  184 |         if (entity.isPassenger()
  185 |             && entity.getVehicle() instanceof AbstractMinecart minecart
  186 |             && minecart.getBehavior() instanceof NewMinecartBehavior behavior
  187 |             && behavior.cartHasPosRotLerp()) {
  188 |             double cartLerpX = Mth.lerp((double)partialTicks, minecart.xOld, minecart.getX());
  189 |             double cartLerpY = Mth.lerp((double)partialTicks, minecart.yOld, minecart.getY());
  190 |             double cartLerpZ = Mth.lerp((double)partialTicks, minecart.zOld, minecart.getZ());
  191 |             state.passengerOffset = behavior.getCartLerpPosition(partialTicks).subtract(new Vec3(cartLerpX, cartLerpY, cartLerpZ));
  192 |         } else {
  193 |             state.passengerOffset = null;
```

### [26.1.2.109 reference] `net/minecraft/client/renderer/entity/LivingEntityRenderer.java` : L254–L277

```text
  254 |     public void extractRenderState(T entity, S state, float partialTicks) {
  255 |         super.extractRenderState(entity, state, partialTicks);
  256 |         float headRot = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
  257 |         state.bodyRot = solveBodyRot(entity, headRot, partialTicks);
  258 |         state.yRot = Mth.wrapDegrees(headRot - state.bodyRot);
  259 |         state.xRot = entity.getXRot(partialTicks);
  260 |         state.isUpsideDown = this.isEntityUpsideDown(entity);
  261 |         if (state.isUpsideDown) {
  262 |             state.xRot *= -1.0F;
  263 |             state.yRot *= -1.0F;
  264 |         }
  265 | 
  266 |         if (!entity.isPassenger() && entity.isAlive()) {
  267 |             state.walkAnimationPos = entity.walkAnimation.position(partialTicks);
  268 |             state.walkAnimationSpeed = entity.walkAnimation.speed(partialTicks);
  269 |         } else {
  270 |             state.walkAnimationPos = 0.0F;
  271 |             state.walkAnimationSpeed = 0.0F;
  272 |         }
  273 | 
  274 |         if (entity.getVehicle() instanceof LivingEntity vehicle) {
  275 |             state.wornHeadAnimationPos = vehicle.walkAnimation.position(partialTicks);
  276 |         } else {
  277 |             state.wornHeadAnimationPos = state.walkAnimationPos;
```

### [26.1.2.109 reference] `net/minecraft/client/renderer/entity/LivingEntityRenderer.java` : L288–L309

```text
  288 |         state.isFullyFrozen = entity.isFullyFrozen();
  289 |         state.isBaby = entity.isBaby();
  290 |         state.isInWater = entity.isInWater() || entity.getFluidInteraction().isInFluidMatching(entity, (e, fluidType, _) -> e.canSwimInFluidType(fluidType));
  291 |         state.isAutoSpinAttack = entity.isAutoSpinAttack();
  292 |         state.ticksSinceKineticHitFeedback = entity.getTicksSinceLastKineticHitFeedback(partialTicks);
  293 |         state.hasRedOverlay = entity.hurtTime > 0 || entity.deathTime > 0;
  294 |         ItemStack headItem = entity.getItemBySlot(EquipmentSlot.HEAD);
  295 |         if (headItem.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractSkullBlock skullBlock) {
  296 |             state.wornHeadType = skullBlock.getType();
  297 |             state.wornHeadProfile = headItem.get(DataComponents.PROFILE);
  298 |             state.headItem.clear();
  299 |         } else {
  300 |             state.wornHeadType = null;
  301 |             state.wornHeadProfile = null;
  302 |             if (!HumanoidArmorLayer.shouldRender(headItem, EquipmentSlot.HEAD)) {
  303 |                 this.itemModelResolver.updateForLiving(state.headItem, headItem, ItemDisplayContext.HEAD, entity);
  304 |             } else {
  305 |                 state.headItem.clear();
  306 |             }
  307 |         }
  308 | 
  309 |         state.deathTime = entity.deathTime > 0 ? entity.deathTime + partialTicks : 0.0F;
```

### [26.1.2.109 reference] `net/minecraft/client/renderer/entity/state/ArmedEntityRenderState.java` : L16–L26

```text
   16 | public class ArmedEntityRenderState extends LivingEntityRenderState {
   17 |     public HumanoidArm mainArm = HumanoidArm.RIGHT;
   18 |     public HumanoidArm attackArm = HumanoidArm.RIGHT;
   19 |     public HumanoidModel.ArmPose rightArmPose = HumanoidModel.ArmPose.EMPTY;
   20 |     public final ItemStackRenderState rightHandItemState = new ItemStackRenderState();
   21 |     public ItemStack rightHandItemStack = ItemStack.EMPTY;
   22 |     public HumanoidModel.ArmPose leftArmPose = HumanoidModel.ArmPose.EMPTY;
   23 |     public final ItemStackRenderState leftHandItemState = new ItemStackRenderState();
   24 |     public ItemStack leftHandItemStack = ItemStack.EMPTY;
   25 |     public SwingAnimationType swingAnimationType = SwingAnimationType.WHACK;
   26 |     public float attackTime;
```

### [26.1.2.109 reference] `net/minecraft/client/renderer/entity/state/ArmedEntityRenderState.java` : L44–L56

```text
   44 |     public static void extractArmedEntityRenderState(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver itemModelResolver, float partialTicks) {
   45 |         state.mainArm = entity.getMainArm();
   46 |         state.attackArm = entity.swingingArm != InteractionHand.OFF_HAND ? state.mainArm : state.mainArm.getOpposite();
   47 |         ItemStack itemStack = entity.getItemHeldByArm(state.attackArm);
   48 |         state.swingAnimationType = itemStack.getSwingAnimation().type();
   49 |         state.attackTime = entity.getAttackAnim(partialTicks);
   50 |         itemModelResolver.updateForLiving(
   51 |             state.rightHandItemState, entity.getItemHeldByArm(HumanoidArm.RIGHT), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity
   52 |         );
   53 |         itemModelResolver.updateForLiving(state.leftHandItemState, entity.getItemHeldByArm(HumanoidArm.LEFT), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
   54 |         state.leftHandItemStack = entity.getItemHeldByArm(HumanoidArm.LEFT).copy();
   55 |         state.rightHandItemStack = entity.getItemHeldByArm(HumanoidArm.RIGHT).copy();
   56 |     }
```

### [26.1.2.109 reference] `net/minecraft/client/renderer/entity/HumanoidMobRenderer.java` : L59–L83

```text
   59 |     public static void extractHumanoidRenderState(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver itemModelResolver) {
   60 |         ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, itemModelResolver, partialTicks);
   61 |         state.isCrouching = entity.isCrouching();
   62 |         state.isFallFlying = entity.isFallFlying();
   63 |         state.isVisuallySwimming = entity.isVisuallySwimming();
   64 |         state.isPassenger = entity.isPassenger() && (entity.getVehicle() != null && entity.getVehicle().shouldRiderSit());
   65 |         state.speedValue = 1.0F;
   66 |         if (state.isFallFlying) {
   67 |             state.speedValue = (float)entity.getDeltaMovement().lengthSqr();
   68 |             state.speedValue /= 0.2F;
   69 |             state.speedValue = state.speedValue * (state.speedValue * state.speedValue);
   70 |         }
   71 | 
   72 |         if (state.speedValue < 1.0F) {
   73 |             state.speedValue = 1.0F;
   74 |         }
   75 | 
   76 |         state.swimAmount = entity.getSwimAmount(partialTicks);
   77 |         state.attackArm = getAttackArm(entity);
   78 |         state.useItemHand = entity.getUsedItemHand();
   79 |         state.maxCrossbowChargeDuration = CrossbowItem.getChargeDuration(entity.getUseItem(), entity);
   80 |         state.ticksUsingItem = entity.getTicksUsingItem(partialTicks);
   81 |         state.isUsingItem = entity.isUsingItem();
   82 |         state.elytraRotX = entity.elytraAnimationState.getRotX(partialTicks);
   83 |         state.elytraRotY = entity.elytraAnimationState.getRotY(partialTicks);
```

### [26.1.2.109 reference] `net/minecraft/client/DeltaTracker.java` : L8–L16

```text
    8 | public interface DeltaTracker {
    9 |     DeltaTracker ZERO = new DeltaTracker.DefaultValue(0.0F);
   10 |     DeltaTracker ONE = new DeltaTracker.DefaultValue(1.0F);
   11 | 
   12 |     float getGameTimeDeltaTicks();
   13 | 
   14 |     float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame);
   15 | 
   16 |     float getRealtimeDeltaTicks();
```

### [26.1.2.109 reference] `net/minecraft/client/DeltaTracker.java` : L99–L120

```text
   99 |         public void updateFrozenState(boolean frozen) {
  100 |             this.frozen = frozen;
  101 |         }
  102 | 
  103 |         @Override
  104 |         public float getGameTimeDeltaTicks() {
  105 |             return this.deltaTicks;
  106 |         }
  107 | 
  108 |         @Override
  109 |         public float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame) {
  110 |             if (!ignoreFrozenGame && this.frozen) {
  111 |                 return 1.0F;
  112 |             } else {
  113 |                 return this.paused ? this.pausedDeltaTickResidual : this.deltaTickResidual;
  114 |             }
  115 |         }
  116 | 
  117 |         @Override
  118 |         public float getRealtimeDeltaTicks() {
  119 |             return this.realtimeDeltaTicks > 7.0F ? 0.5F : this.realtimeDeltaTicks;
  120 |         }
```

## V03 — 身体 tick 中的动画和模拟混杂

受伤、无敌、死亡、物品使用、aiStep 等同处实体生命周期，不能笼统当作 visual tick 恢复。

### [26.1.2.109 reference] `net/minecraft/world/entity/LivingEntity.java` : L461–L499

```text
  461 |             if (!Objects.equal(this.lastPos, pos)) {
  462 |                 this.lastPos = pos;
  463 |                 this.onChangedBlock(level, pos);
  464 |             }
  465 |         }
  466 | 
  467 |         if (this.hurtTime > 0) {
  468 |             this.hurtTime--;
  469 |         }
  470 | 
  471 |         if (this.invulnerableTime > 0 && !(this instanceof ServerPlayer)) {
  472 |             this.invulnerableTime--;
  473 |         }
  474 | 
  475 |         if (this.isDeadOrDying() && this.level().shouldTickDeath(this)) {
  476 |             this.tickDeath();
  477 |         }
  478 | 
  479 |         if (this.lastHurtByPlayerMemoryTime > 0) {
  480 |             this.lastHurtByPlayerMemoryTime--;
  481 |         } else {
  482 |             this.lastHurtByPlayer = null;
  483 |         }
  484 | 
  485 |         if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
  486 |             this.lastHurtMob = null;
  487 |         }
  488 | 
  489 |         LivingEntity hurtByMob = this.getLastHurtByMob();
  490 |         if (hurtByMob != null) {
  491 |             if (!hurtByMob.isAlive()) {
  492 |                 this.setLastHurtByMob(null);
  493 |             } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
  494 |                 this.setLastHurtByMob(null);
  495 |             }
  496 |         }
  497 | 
  498 |         this.tickEffects();
  499 |         this.yHeadRotO = this.yHeadRot;
```

### [26.1.2.109 reference] `net/minecraft/world/entity/LivingEntity.java` : L2228–L2242

```text
 2228 | 
 2229 |     protected void updateSwingTime() {
 2230 |         int currentSwingDuration = this.getCurrentSwingDuration();
 2231 |         if (this.swinging) {
 2232 |             this.swingTime++;
 2233 |             if (this.swingTime >= currentSwingDuration) {
 2234 |                 this.swingTime = 0;
 2235 |                 this.swinging = false;
 2236 |             }
 2237 |         } else {
 2238 |             this.swingTime = 0;
 2239 |         }
 2240 | 
 2241 |         this.attackAnim = (float)this.swingTime / currentSwingDuration;
 2242 |     }
```

### [26.1.2.109 reference] `net/minecraft/world/entity/LivingEntity.java` : L2697–L2709

```text
 2697 |     public void calculateEntityAnimation(boolean useY) {
 2698 |         float distance = (float)Mth.length(this.getX() - this.xo, useY ? this.getY() - this.yo : 0.0, this.getZ() - this.zo);
 2699 |         if (!this.isPassenger() && this.isAlive()) {
 2700 |             this.updateWalkAnimation(distance);
 2701 |         } else {
 2702 |             this.walkAnimation.stop();
 2703 |         }
 2704 |     }
 2705 | 
 2706 |     protected void updateWalkAnimation(float distance) {
 2707 |         float targetSpeed = Math.min(distance * 4.0F, 1.0F);
 2708 |         this.walkAnimation.update(targetSpeed, 0.4F, this.isBaby() ? 3.0F : 1.0F);
 2709 |     }
```

### [26.1.2.109 reference] `net/minecraft/world/entity/LivingEntity.java` : L2787–L2799

```text
 2787 |     public void tick() {
 2788 |         super.tick();
 2789 |         this.updatingUsingItem();
 2790 |         this.updateSwimAmount();
 2791 |         if (!this.level().isClientSide()) {
 2792 |             int arrowCount = this.getArrowCount();
 2793 |             if (arrowCount > 0) {
 2794 |                 if (this.removeArrowTime <= 0) {
 2795 |                     this.removeArrowTime = 20 * (30 - arrowCount);
 2796 |                 }
 2797 | 
 2798 |                 this.removeArrowTime--;
 2799 |                 if (this.removeArrowTime <= 0) {
```

### [26.1.2.109 reference] `net/minecraft/world/entity/LivingEntity.java` : L2811–L2829

```text
 2811 |                 if (this.removeStingerTime <= 0) {
 2812 |                     this.setStingerCount(stingerCount - 1);
 2813 |                 }
 2814 |             }
 2815 | 
 2816 |             this.detectEquipmentUpdates();
 2817 |             if (this.tickCount % 20 == 0) {
 2818 |                 this.getCombatTracker().recheckStatus();
 2819 |             }
 2820 | 
 2821 |             if (this.isSleeping() && (!this.canInteractWithLevel() || !this.checkBedExists())) {
 2822 |                 this.stopSleeping();
 2823 |             }
 2824 |         }
 2825 | 
 2826 |         if (!this.isRemoved()) {
 2827 |             this.aiStep();
 2828 |         }
 2829 | 
```

### [26.1.2.109 reference] `net/minecraft/world/entity/LivingEntity.java` : L3455–L3468

```text
 3455 |     private void updatingUsingItem() {
 3456 |         if (this.isUsingItem()) {
 3457 |             ItemStack itemStack = this.getItemInHand(this.getUsedItemHand());
 3458 |             if (net.neoforged.neoforge.common.CommonHooks.canContinueUsing(this.useItem, itemStack)) {
 3459 |                 this.useItem = itemStack;
 3460 |             }
 3461 |             if (itemStack == this.useItem) {
 3462 |                 this.updateUsingItem(this.useItem);
 3463 |             } else {
 3464 |                 this.stopUsingItem();
 3465 |             }
 3466 |         }
 3467 |     }
 3468 | 
```

### [26.1.2.109 reference] `net/minecraft/world/entity/LivingEntity.java` : L3501–L3512

```text
 3501 |     }
 3502 | 
 3503 |     protected void updateUsingItem(ItemStack useItem) {
 3504 |         if (!useItem.isEmpty())
 3505 |             this.useItemRemaining = net.neoforged.neoforge.event.EventHooks.onItemUseTick(this, useItem, this.getUseItemRemainingTicks());
 3506 |         if (this.getUseItemRemainingTicks() > 0)
 3507 |         useItem.onUseTick(this.level(), this, this.getUseItemRemainingTicks());
 3508 |         if (--this.useItemRemaining <= 0 && !this.level().isClientSide() && !useItem.useOnRelease()) {
 3509 |             this.completeUsingItem();
 3510 |         }
 3511 |     }
 3512 | 
```

### [26.1.2.109 reference] `net/minecraft/client/player/RemotePlayer.java` : L33–L77

```text
   33 | 
   34 |     @Override
   35 |     public boolean hurtClient(DamageSource source) {
   36 |         return true;
   37 |     }
   38 | 
   39 |     @Override
   40 |     public void tick() {
   41 |         super.tick();
   42 |         this.calculateEntityAnimation(false);
   43 |     }
   44 | 
   45 |     @Override
   46 |     public void aiStep() {
   47 |         if (this.isInterpolating()) {
   48 |             this.getInterpolation().interpolate();
   49 |         }
   50 | 
   51 |         if (this.lerpHeadSteps > 0) {
   52 |             this.lerpHeadRotationStep(this.lerpHeadSteps, this.lerpYHeadRot);
   53 |             this.lerpHeadSteps--;
   54 |         }
   55 | 
   56 |         if (this.lerpDeltaMovementSteps > 0) {
   57 |             this.addDeltaMovement(
   58 |                 new Vec3(
   59 |                     (this.lerpDeltaMovement.x - this.getDeltaMovement().x) / this.lerpDeltaMovementSteps,
   60 |                     (this.lerpDeltaMovement.y - this.getDeltaMovement().y) / this.lerpDeltaMovementSteps,
   61 |                     (this.lerpDeltaMovement.z - this.getDeltaMovement().z) / this.lerpDeltaMovementSteps
   62 |                 )
   63 |             );
   64 |             this.lerpDeltaMovementSteps--;
   65 |         }
   66 | 
   67 |         this.updateSwingTime();
   68 |         this.updateBob();
   69 | 
   70 |         try (Zone ignored = Profiler.get().zone("push")) {
   71 |             this.pushEntities();
   72 |         }
   73 |     }
   74 | 
   75 |     @Override
   76 |     public void lerpMotion(Vec3 movement) {
   77 |         this.lerpDeltaMovement = movement;
```

## V04 — 战术攻击缺失挥手触发；接收事件后也需要动画推进

攻击服务执行规则/伤害但不显式发 swing/animate。全源码扫描见 S01；不是对外部模组/反射路径的绝对否定。标准 swing 含物品钩子，不能不审计副作用就当纯发送函数调用。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java` : L1965–L2022

```text
 1965 |         var origin = actor.blockPosition();
 1966 |         var destination = target.blockPosition();
 1967 |         OperationRecord.Snapshot root = operationSnapshot(operationId, planParent,
 1968 |             encounterId, actor.getUUID(), actor.getUUID(), targetId, cumulativeServerTicks, expectedVersion,
 1969 |             new GridCell(origin.getX(), origin.getY(), origin.getZ()),
 1970 |             new GridCell(destination.getX(), destination.getY(), destination.getZ()), OperationRecord.Kind.ATTACK);
 1971 |         if (!(planParent == null ? engine.beginOperation(root) : engine.beginPlanStep(root))) throw new IllegalStateException("attack already executing or unauthorized");
 1972 |         UUID permitId = null;
 1973 |         UUID damageId = null;
 1974 |         boolean effectStarted = false;
 1975 |         try {
 1976 |         CombatRules.RollMode rollMode = CombatRules.mode(openingAdvantage,
 1977 |             state.members().get(targetId).dodging());
 1978 |         CombatRules.AttackRoll roll = CombatRules.rollAttack(
 1979 |             gameTestAttackRandom.getOrDefault(encounterId, attackRandom), rollMode, ac);
 1980 |         if (!roll.hit()) {
 1981 |             DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
 1982 |                 weaponDamage, reduction, 0, false, 0, 0,
 1983 |                 damageEvidence(actor, state, DamageTrace.Stage.MISS, null, List.of()));
 1984 |             OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
 1985 |                 OperationRecord.Outcome.COMPLETED, "miss: die=" + roll.die() + " AC=" + ac, 0, 0, true, trace);
 1986 |             sync(engine.stateView(encounterId));
 1987 |             return result;
 1988 |         }
 1989 |         int tacticalDamage = CombatRules.damageAfterReduction(weaponDamage, reduction, roll.critical());
 1990 |         if (tacticalDamage == 0) {
 1991 |             DamageTrace trace = attackTrace(operationId, targetId, rollMode, roll, ac,
 1992 |                 weaponDamage, reduction, 0, false, 0, 0,
 1993 |                 damageEvidence(actor, state, DamageTrace.Stage.ZERO_DAMAGE, null, List.of()));
 1994 |             OperationRecord.Result result = engine.publish(encounterId, operationId, 0,
 1995 |                 OperationRecord.Outcome.COMPLETED, "hit with zero tactical damage", 0, 0, true, trace);
 1996 |             sync(engine.stateView(encounterId));
 1997 |             return result;
 1998 |         }
 1999 |         permitId = UUID.randomUUID();
 2000 |         engine.issueEffectPermit(new CombatEngine.EffectPermit(permitId, encounterId, operationId,
 2001 |             actor.getUUID(), Set.of(targetId), Set.of(EncounterPhase.ACTIVE), 1,
 2002 |             engine.stateView(encounterId).round()));
 2003 |         damageId = UUID.randomUUID();
 2004 |         OperationRecord.Snapshot child = operationSnapshot(damageId, operationId,
 2005 |             encounterId, actor.getUUID(), actor.getUUID(), targetId, cumulativeServerTicks,
 2006 |             engine.stateView(encounterId).version(), root.sourceCell(), root.targetCell(),
 2007 |             OperationRecord.Kind.DAMAGE);
 2008 |         if (!engine.beginOperation(child, permitId)) throw new IllegalStateException("damage permit rejected");
 2009 |         DamageSource source = level.damageSources().source(TacticalDamageContext.DAMAGE_TYPE, actor);
 2010 |         float healthBefore = target.getHealth();
 2011 |         float absorptionBefore = target.getAbsorptionAmount();
 2012 |         Map<EquipmentKey, EquipmentValue> equipmentBefore = equipmentSnapshot(actor, target);
 2013 |         effectStarted = true;
 2014 |         worldEffectDepth++;
 2015 |         try {
 2016 |             var observed = TacticalDamageContext.hurtObserved(level, target, source, tacticalDamage,
 2017 |                 settingsFor(encounterId).tacticalKnockbackEnabled(), damageId);
 2018 |             boolean accepted = observed.accepted();
 2019 |             if (accepted && actor instanceof ServerPlayer player) {
 2020 |                 ItemStack weapon = player.getMainHandItem();
 2021 |                 if (weapon.hurtEnemy(target, player)) weapon.postHurtEnemy(target, player);
 2022 |             }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/client/TacticalMinecraftInputMixin.java` : L1–L21

```text
    1 | package cc.sighs.dndturn.mixin.client;
    2 | 
    3 | import cc.sighs.dndturn.client.ClientControl;
    4 | import net.minecraft.client.Minecraft;
    5 | import org.spongepowered.asm.mixin.Mixin;
    6 | import org.spongepowered.asm.mixin.injection.At;
    7 | import org.spongepowered.asm.mixin.injection.Inject;
    8 | import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
    9 | import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
   10 | 
   11 | @Mixin(Minecraft.class)
   12 | public abstract class TacticalMinecraftInputMixin {
   13 |     @Inject(method = "handleKeybinds", at = @At("HEAD"))
   14 |     private void dndturn$queues(CallbackInfo ci) { ClientControl.beforeKeybinds(); }
   15 |     @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
   16 |     private void dndturn$attack(CallbackInfoReturnable<Boolean> ci) {
   17 |         if (ClientControl.blockWorldActions()) ci.setReturnValue(false);
   18 |     }
   19 |     @Inject(method = {"continueAttack", "startUseItem", "pickBlockOrEntity"}, at = @At("HEAD"), cancellable = true)
   20 |     private void dndturn$world(CallbackInfo ci) { if (ClientControl.blockWorldActions()) ci.cancel(); }
   21 | }
```

### [26.1.2.109 reference] `net/minecraft/world/entity/LivingEntity.java` : L2050–L2105

```text
 2050 |     }
 2051 | 
 2052 |     public final void setStingerCount(int count) {
 2053 |         this.entityData.set(DATA_STINGER_COUNT_ID, count);
 2054 |     }
 2055 | 
 2056 |     public int getCurrentSwingDuration() {
 2057 |         InteractionHand hand = this.swingingArm != null ? this.swingingArm : InteractionHand.MAIN_HAND;
 2058 |         ItemStack handStack = this.getItemInHand(hand);
 2059 |         int swingDuration = handStack.getSwingAnimation().duration();
 2060 |         if (MobEffectUtil.hasDigSpeed(this)) {
 2061 |             return swingDuration - (1 + MobEffectUtil.getDigSpeedAmplification(this));
 2062 |         } else {
 2063 |             return this.hasEffect(MobEffects.MINING_FATIGUE)
 2064 |                 ? swingDuration + (1 + this.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2
 2065 |                 : swingDuration;
 2066 |         }
 2067 |     }
 2068 | 
 2069 |     public void swing(InteractionHand hand) {
 2070 |         this.swing(hand, false);
 2071 |     }
 2072 | 
 2073 |     public void swing(InteractionHand hand, boolean sendToSwingingEntity) {
 2074 |         ItemStack stack = this.getItemInHand(hand);
 2075 |         if (!stack.isEmpty() && stack.onEntitySwing(this, hand)) return;
 2076 |         if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
 2077 |             this.swingTime = -1;
 2078 |             this.swinging = true;
 2079 |             this.swingingArm = hand;
 2080 |             if (this.level() instanceof ServerLevel) {
 2081 |                 ClientboundAnimatePacket packet = new ClientboundAnimatePacket(this, hand == InteractionHand.MAIN_HAND ? 0 : 3);
 2082 |                 ServerChunkCache chunkSource = ((ServerLevel)this.level()).getChunkSource();
 2083 |                 if (sendToSwingingEntity) {
 2084 |                     chunkSource.sendToTrackingPlayersAndSelf(this, packet);
 2085 |                 } else {
 2086 |                     chunkSource.sendToTrackingPlayers(this, packet);
 2087 |                 }
 2088 |             }
 2089 |         }
 2090 |     }
 2091 | 
 2092 |     @Override
 2093 |     public void handleDamageEvent(DamageSource source) {
 2094 |         this.walkAnimation.setSpeed(1.5F);
 2095 |         this.invulnerableTime = 20;
 2096 |         this.hurtDuration = 10;
 2097 |         this.hurtTime = this.hurtDuration;
 2098 |         SoundEvent hurtSound = this.getHurtSound(source);
 2099 |         if (hurtSound != null) {
 2100 |             this.playSound(hurtSound, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
 2101 |         }
 2102 | 
 2103 |         this.lastDamageSource = source;
 2104 |         this.lastDamageStamp = this.level().getGameTime();
 2105 |     }
```

### [26.1.2.109 reference] `net/minecraft/client/multiplayer/ClientPacketListener.java` : L1104–L1122

```text
 1104 |     public void handleAnimate(ClientboundAnimatePacket packet) {
 1105 |         PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft.packetProcessor());
 1106 |         Entity entity = this.level.getEntity(packet.getId());
 1107 |         if (entity != null) {
 1108 |             if (packet.getAction() == 0) {
 1109 |                 LivingEntity mob = (LivingEntity)entity;
 1110 |                 mob.swing(InteractionHand.MAIN_HAND);
 1111 |             } else if (packet.getAction() == 3) {
 1112 |                 LivingEntity mob = (LivingEntity)entity;
 1113 |                 mob.swing(InteractionHand.OFF_HAND);
 1114 |             } else if (packet.getAction() == 2) {
 1115 |                 Player player = (Player)entity;
 1116 |                 player.stopSleepInBed(false, false);
 1117 |             } else if (packet.getAction() == 4) {
 1118 |                 this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.CRIT);
 1119 |             } else if (packet.getAction() == 5) {
 1120 |                 this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.ENCHANTED_HIT);
 1121 |             }
 1122 |         }
```

### [26.1.2.109 reference] `net/minecraft/client/multiplayer/ClientPacketListener.java` : L1203–L1235

```text
 1203 |     public void handleEntityEvent(ClientboundEntityEventPacket packet) {
 1204 |         PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft.packetProcessor());
 1205 |         Entity entity = packet.getEntity(this.level);
 1206 |         if (entity != null) {
 1207 |             switch (packet.getEventId()) {
 1208 |                 case 21:
 1209 |                     this.minecraft.getSoundManager().play(new GuardianAttackSoundInstance((Guardian)entity));
 1210 |                     break;
 1211 |                 case 35:
 1212 |                     int tickLength = 40;
 1213 |                     this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
 1214 |                     this.level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TOTEM_USE, entity.getSoundSource(), 1.0F, 1.0F, false);
 1215 |                     if (entity == this.minecraft.player) {
 1216 |                         this.minecraft.gameRenderer.displayItemActivation(findTotem(this.minecraft.player));
 1217 |                     }
 1218 |                     break;
 1219 |                 case 63:
 1220 |                     this.minecraft.getSoundManager().play(new SnifferSoundInstance((Sniffer)entity));
 1221 |                     break;
 1222 |                 default:
 1223 |                     entity.handleEntityEvent(packet.getEventId());
 1224 |             }
 1225 |         }
 1226 |     }
 1227 | 
 1228 |     @Override
 1229 |     public void handleDamageEvent(ClientboundDamageEventPacket packet) {
 1230 |         PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft.packetProcessor());
 1231 |         Entity entity = this.level.getEntity(packet.entityId());
 1232 |         if (entity != null) {
 1233 |             entity.handleDamageEvent(packet.getSource(this.level));
 1234 |         }
 1235 |     }
```

## V05 — 既有粒子和跟随发射器并未被局部回合门控整体停止

Minecraft 顶层仍 tick ParticleEngine，前提是没有真正 pause/global tick freeze。不要再额外补跑一次粒子 tick。

### [26.1.2.109 reference] `net/minecraft/client/Minecraft.java` : L1911–L1925

```text
 1911 |         if (this.level != null) {
 1912 |             if (!this.pause) {
 1913 |                 profiler.popPush("gameRenderer");
 1914 |                 this.gameRenderer.tick();
 1915 |                 profiler.popPush("entities");
 1916 |                 this.level.tickEntities();
 1917 |                 profiler.popPush("blockEntities");
 1918 |                 this.level.tickBlockEntities();
 1919 |             }
 1920 |         } else if (this.gameRenderer.currentPostEffect() != null) {
 1921 |             this.gameRenderer.clearPostEffect();
 1922 |         }
 1923 | 
 1924 |         this.musicManager.tick();
 1925 |         this.soundManager.tick(this.pause);
```

### [26.1.2.109 reference] `net/minecraft/client/Minecraft.java` : L1957–L1987

```text
 1957 |             profiler.popPush("animateTick");
 1958 |             if (!this.pause && this.isLevelRunningNormally()) {
 1959 |                 this.level.animateTick(this.player.getBlockX(), this.player.getBlockY(), this.player.getBlockZ());
 1960 |             }
 1961 | 
 1962 |             profiler.popPush("particles");
 1963 |             if (!this.pause && this.isLevelRunningNormally()) {
 1964 |                 this.particleEngine.tick();
 1965 |             }
 1966 | 
 1967 |             ClientPacketListener connection = this.getConnection();
 1968 |             if (connection != null && !this.pause) {
 1969 |                 connection.send(ServerboundClientTickEndPacket.INSTANCE);
 1970 |             }
 1971 |         } else if (this.pendingConnection != null) {
 1972 |             profiler.popPush("pendingConnection");
 1973 |             this.pendingConnection.tick();
 1974 |         }
 1975 | 
 1976 |         profiler.popPush("keyboard");
 1977 |         this.keyboardHandler.tick();
 1978 |         profiler.pop();
 1979 | 
 1980 |         if (this.gameLoadFinished) {
 1981 |             net.neoforged.neoforge.client.ClientHooks.fireClientTickPost();
 1982 |         }
 1983 |     }
 1984 | 
 1985 |     private boolean isLevelRunningNormally() {
 1986 |         return this.level == null || this.level.tickRateManager().runsNormally();
 1987 |     }
```

### [26.1.2.109 reference] `net/minecraft/client/particle/ParticleEngine.java` : L85–L109

```text
   85 |     public void tick() {
   86 |         this.particles.forEach((type, group) -> {
   87 |             Profiler.get().push(type.name());
   88 |             group.tickParticles();
   89 |             Profiler.get().pop();
   90 |         });
   91 |         if (!this.trackingEmitters.isEmpty()) {
   92 |             List<TrackingEmitter> removed = Lists.newArrayList();
   93 | 
   94 |             for (TrackingEmitter emitter : this.trackingEmitters) {
   95 |                 emitter.tick();
   96 |                 if (!emitter.isAlive()) {
   97 |                     removed.add(emitter);
   98 |                 }
   99 |             }
  100 | 
  101 |             this.trackingEmitters.removeAll(removed);
  102 |         }
  103 | 
  104 |         Particle particle;
  105 |         if (!this.particlesToAdd.isEmpty()) {
  106 |             while ((particle = this.particlesToAdd.poll()) != null) {
  107 |                 this.particles.computeIfAbsent(particle.getGroup(), this::createParticleGroup).add(particle);
  108 |             }
  109 |         }
```

### [26.1.2.109 reference] `net/minecraft/client/particle/TrackingEmitter.java` : L21–L52

```text
   21 |     public TrackingEmitter(ClientLevel level, Entity entity, ParticleOptions particleType, int lifeTime) {
   22 |         this(level, entity, particleType, lifeTime, entity.getDeltaMovement());
   23 |     }
   24 | 
   25 |     public TrackingEmitter(ClientLevel level, Entity entity, ParticleOptions particleType, int lifeTime, Vec3 movement) {
   26 |         super(level, entity.getX(), entity.getY(0.5), entity.getZ(), movement.x, movement.y, movement.z);
   27 |         this.entity = entity;
   28 |         this.lifeTime = lifeTime;
   29 |         this.particleType = particleType;
   30 |         this.tick();
   31 |     }
   32 | 
   33 |     @Override
   34 |     public void tick() {
   35 |         for (int i = 0; i < 16; i++) {
   36 |             double xa = this.random.nextFloat() * 2.0F - 1.0F;
   37 |             double ya = this.random.nextFloat() * 2.0F - 1.0F;
   38 |             double za = this.random.nextFloat() * 2.0F - 1.0F;
   39 |             if (!(xa * xa + ya * ya + za * za > 1.0)) {
   40 |                 double x = this.entity.getX(xa / 4.0);
   41 |                 double y = this.entity.getY(0.5 + ya / 4.0);
   42 |                 double z = this.entity.getZ(za / 4.0);
   43 |                 this.level.addParticle(this.particleType, x, y, z, xa, ya + 0.2, za);
   44 |             }
   45 |         }
   46 | 
   47 |         this.life++;
   48 |         if (this.life >= this.lifeTime) {
   49 |             this.remove();
   50 |         }
   51 |     }
   52 | }
```

### [26.1.2.109 reference] `net/minecraft/client/particle/Particle.java` : L90–L117

```text
   90 |         return this.lifetime;
   91 |     }
   92 | 
   93 |     public void tick() {
   94 |         this.xo = this.x;
   95 |         this.yo = this.y;
   96 |         this.zo = this.z;
   97 |         if (this.age++ >= this.lifetime) {
   98 |             this.remove();
   99 |         } else {
  100 |             this.yd = this.yd - 0.04 * this.gravity;
  101 |             this.move(this.xd, this.yd, this.zd);
  102 |             if (this.speedUpWhenYMotionIsBlocked && this.y == this.yo) {
  103 |                 this.xd *= 1.1;
  104 |                 this.zd *= 1.1;
  105 |             }
  106 | 
  107 |             this.xd = this.xd * this.friction;
  108 |             this.yd = this.yd * this.friction;
  109 |             this.zd = this.zd * this.friction;
  110 |             if (this.onGround) {
  111 |                 this.xd *= 0.7F;
  112 |                 this.zd *= 0.7F;
  113 |             }
  114 |         }
  115 |     }
  116 | 
  117 |     public abstract ParticleRenderType getGroup();
```

### [26.1.2.109 reference] `net/minecraft/client/multiplayer/ClientPacketListener.java` : L2312–L2353

```text
 2312 |     public void handleParticleEvent(ClientboundLevelParticlesPacket packet) {
 2313 |         PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft.packetProcessor());
 2314 |         if (packet.getCount() == 0) {
 2315 |             double xa = packet.getMaxSpeed() * packet.getXDist();
 2316 |             double ya = packet.getMaxSpeed() * packet.getYDist();
 2317 |             double za = packet.getMaxSpeed() * packet.getZDist();
 2318 | 
 2319 |             try {
 2320 |                 this.level
 2321 |                     .addParticle(packet.getParticle(), packet.isOverrideLimiter(), packet.alwaysShow(), packet.getX(), packet.getY(), packet.getZ(), xa, ya, za);
 2322 |             } catch (Throwable var17) {
 2323 |                 LOGGER.warn("Could not spawn particle effect {}", packet.getParticle());
 2324 |             }
 2325 |         } else {
 2326 |             for (int i = 0; i < packet.getCount(); i++) {
 2327 |                 double xVarience = this.random.nextGaussian() * packet.getXDist();
 2328 |                 double yVarience = this.random.nextGaussian() * packet.getYDist();
 2329 |                 double zVarience = this.random.nextGaussian() * packet.getZDist();
 2330 |                 double xa = this.random.nextGaussian() * packet.getMaxSpeed();
 2331 |                 double ya = this.random.nextGaussian() * packet.getMaxSpeed();
 2332 |                 double za = this.random.nextGaussian() * packet.getMaxSpeed();
 2333 | 
 2334 |                 try {
 2335 |                     this.level
 2336 |                         .addParticle(
 2337 |                             packet.getParticle(),
 2338 |                             packet.isOverrideLimiter(),
 2339 |                             packet.alwaysShow(),
 2340 |                             packet.getX() + xVarience,
 2341 |                             packet.getY() + yVarience,
 2342 |                             packet.getZ() + zVarience,
 2343 |                             xa,
 2344 |                             ya,
 2345 |                             za
 2346 |                         );
 2347 |                 } catch (Throwable var16) {
 2348 |                     LOGGER.warn("Could not spawn particle effect {}", packet.getParticle());
 2349 |                     return;
 2350 |                 }
 2351 |             }
 2352 |         }
 2353 |     }
```

## V06 — 状态粒子发生器与效果持续时间必须拆开

tickEffects 的客户端分支既扣 effect duration 又发粒子；只需要表现不能整段恢复。

### [26.1.2.109 reference] `net/minecraft/world/entity/LivingEntity.java` : L849–L886

```text
  849 |     protected void tickEffects() {
  850 |         if (this.level() instanceof ServerLevel serverLevel) {
  851 |             Iterator<Holder<MobEffect>> iterator = this.activeEffects.keySet().iterator();
  852 | 
  853 |             try {
  854 |                 while (iterator.hasNext()) {
  855 |                     Holder<MobEffect> mobEffect = iterator.next();
  856 |                     MobEffectInstance effect = this.activeEffects.get(mobEffect);
  857 |                     if (!effect.tickServer(serverLevel, this, () -> this.onEffectUpdated(effect, true, null))) {
  858 |                         if (!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired(this, effect)).isCanceled()) {
  859 |                         iterator.remove();
  860 |                         this.onEffectsRemoved(List.of(effect));
  861 |                         }
  862 |                     } else if (effect.getDuration() % 600 == 0) {
  863 |                         this.onEffectUpdated(effect, false, null);
  864 |                     }
  865 |                 }
  866 |             } catch (ConcurrentModificationException var6) {
  867 |             }
  868 |         } else {
  869 |             for (MobEffectInstance effect : this.activeEffects.values()) {
  870 |                 effect.tickClient();
  871 |             }
  872 | 
  873 |             List<ParticleOptions> particles = this.entityData.get(DATA_EFFECT_PARTICLES);
  874 |             if (!particles.isEmpty()) {
  875 |                 boolean isAmbient = this.entityData.get(DATA_EFFECT_AMBIENCE_ID);
  876 |                 int bound = this.isInvisible() ? 15 : 4;
  877 |                 int ambientFactor = isAmbient ? 5 : 1;
  878 |                 if (this.random.nextInt(bound * ambientFactor) == 0) {
  879 |                     this.level()
  880 |                         .addParticle(Util.getRandom(particles, this.random), this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 1.0, 1.0, 1.0);
  881 |                 }
  882 |             }
  883 |         }
  884 |     }
  885 | 
  886 |     private void updateDirtyEffects() {
```

### [26.1.2.109 reference] `net/minecraft/world/effect/MobEffectInstance.java` : L235–L259

```text
  235 |     public void tickClient() {
  236 |         if (this.hasRemainingDuration()) {
  237 |             this.tickDownDuration();
  238 |             this.downgradeToHiddenEffect();
  239 |         }
  240 | 
  241 |         this.blendState.tick(this);
  242 |     }
  243 | 
  244 |     private boolean hasRemainingDuration() {
  245 |         return this.isInfiniteDuration() || this.duration > 0;
  246 |     }
  247 | 
  248 |     private void tickDownDuration() {
  249 |         if (this.hiddenEffect != null) {
  250 |             this.hiddenEffect.tickDownDuration();
  251 |         }
  252 | 
  253 |         this.duration = this.mapDuration(d -> d - 1);
  254 |     }
  255 | 
  256 |     private boolean downgradeToHiddenEffect() {
  257 |         if (this.duration == 0 && this.hiddenEffect != null) {
  258 |             this.setDetailsFrom(this.hiddenEffect);
  259 |             this.hiddenEffect = this.hiddenEffect.hiddenEffect;
```

## V07 — 环境表现采样仍以实际玩家为中心

Minecraft.animateTick 调用参数见 V05；ClientLevel 抽样范围围绕这些参数，而非脱离身体的战术镜头。镜头观察远处时“没有新环境粒子”与引擎暂停不同。

### [26.1.2.109 reference] `net/minecraft/client/multiplayer/ClientLevel.java` : L458–L467

```text
  458 |     public void animateTick(int xt, int yt, int zt) {
  459 |         int r = 32;
  460 |         RandomSource animateRandom = RandomSource.createThreadLocalInstance();
  461 |         Block markerParticleTarget = this.getMarkerParticleTarget();
  462 |         BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
  463 | 
  464 |         for (int i = 0; i < 667; i++) {
  465 |             this.doAnimateTick(xt, yt, zt, 16, animateRandom, markerParticleTarget, pos);
  466 |             this.doAnimateTick(xt, yt, zt, 32, animateRandom, markerParticleTarget, pos);
  467 |         }
```

### [26.1.2.109 reference] `net/minecraft/client/multiplayer/ClientLevel.java` : L482–L516

```text
  482 |     public void doAnimateTick(int xt, int yt, int zt, int r, RandomSource animateRandom, @Nullable Block markerParticleTarget, BlockPos.MutableBlockPos pos) {
  483 |         int x = xt + this.random.nextInt(r) - this.random.nextInt(r);
  484 |         int y = yt + this.random.nextInt(r) - this.random.nextInt(r);
  485 |         int z = zt + this.random.nextInt(r) - this.random.nextInt(r);
  486 |         pos.set(x, y, z);
  487 |         BlockState state = this.getBlockState(pos);
  488 |         state.getBlock().animateTick(state, this, pos, animateRandom);
  489 |         FluidState fluidState = this.getFluidState(pos);
  490 |         if (!fluidState.isEmpty()) {
  491 |             fluidState.animateTick(this, pos, animateRandom);
  492 |             ParticleOptions dripParticle = fluidState.getDripParticle();
  493 |             if (dripParticle != null && this.random.nextInt(10) == 0) {
  494 |                 boolean hasWatertightBottom = state.isFaceSturdy(this, pos, Direction.DOWN);
  495 |                 BlockPos below = pos.below();
  496 |                 this.trySpawnDripParticles(below, this.getBlockState(below), dripParticle, hasWatertightBottom);
  497 |             }
  498 |         }
  499 | 
  500 |         if (markerParticleTarget == state.getBlock()) {
  501 |             this.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state), x + 0.5, y + 0.5, z + 0.5, 0.0, 0.0, 0.0);
  502 |         }
  503 | 
  504 |         if (!state.isCollisionShapeFullBlock(this, pos)) {
  505 |             for (AmbientParticle particle : this.environmentAttributes().getValue(EnvironmentAttributes.AMBIENT_PARTICLES, pos)) {
  506 |                 if (particle.canSpawn(this.random)) {
  507 |                     this.addParticle(
  508 |                         particle.particle(),
  509 |                         pos.getX() + this.random.nextDouble(),
  510 |                         pos.getY() + this.random.nextDouble(),
  511 |                         pos.getZ() + this.random.nextDouble(),
  512 |                         0.0,
  513 |                         0.0,
  514 |                         0.0
  515 |                     );
  516 |                 }
```

## V08 — 箱盖：客户端动画未封锁，服务器开合信号却可能排队

当前服务器 block event 使用整个位置模拟门控；标准 Chest 开合 count 通过 block event 1 到客户端。只改渲染时钟不能解决信号未到。只讨论经确认的展示消息，不能放开所有 block event。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/LevelBlockEntityGateMixin.java` : L11–L22

```text
   11 | /** Keep vanilla ticker registration, onLoad, and removed-ticker cleanup active. */
   12 | @Mixin(Level.class)
   13 | public abstract class LevelBlockEntityGateMixin {
   14 |     @Redirect(method = "tickBlockEntities()V",
   15 |         at = @At(value = "INVOKE",
   16 |             target = "Lnet/minecraft/world/level/Level;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"))
   17 |     private boolean dndturn$regionalBlockEntityFreeze(Level level, BlockPos pos) {
   18 |         return level.shouldTickBlocksAt(pos)
   19 |             && (!(level instanceof ServerLevel serverLevel)
   20 |                 || !MinecraftCombatRuntime.isFormalBlockSimulationPaused(serverLevel, pos));
   21 |     }
   22 | }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/mixin/ServerLevelBlockEventGateMixin.java` : L10–L20

```text
   10 | /** Vanilla runBlockEvents reschedules entries when this positional gate returns false. */
   11 | @Mixin(ServerLevel.class)
   12 | public abstract class ServerLevelBlockEventGateMixin {
   13 |     @Redirect(method = "runBlockEvents()V",
   14 |         at = @At(value = "INVOKE",
   15 |             target = "Lnet/minecraft/server/level/ServerLevel;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"))
   16 |     private boolean dndturn$regionalBlockEventFreeze(ServerLevel level, BlockPos pos) {
   17 |         return level.shouldTickBlocksAt(pos)
   18 |             && !MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos);
   19 |     }
   20 | }
```

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/combat/ServerCombatService.java` : L2788–L2798

```text
 2788 |     /** Use the same continuous region with a block's center, not its whole chunk. */
 2789 |     public boolean isBlockSimulationPaused(ServerLevel level, BlockPos pos) {
 2790 |         String dimension = level.dimension().identifier().toString();
 2791 |         for (var entry : regions.entrySet()) {
 2792 |             EncounterRegion region = entry.getValue();
 2793 |             if (region.dimension().equals(dimension)
 2794 |                 && region.containsBlock(pos.getX(), pos.getY(), pos.getZ()))
 2795 |                 if (!entry.getKey().equals(activeEnvironmentEncounters.get(level))) return true;
 2796 |         }
 2797 |         return false;
 2798 |     }
```

### [26.1.2.109 reference] `net/minecraft/world/level/block/entity/ChestBlockEntity.java` : L52–L55

```text
   52 |         @Override
   53 |         protected void openerCountChanged(Level level, BlockPos pos, BlockState blockState, int previous, int current) {
   54 |             ChestBlockEntity.this.signalOpenCount(level, pos, blockState, previous, current);
   55 |         }
```

### [26.1.2.109 reference] `net/minecraft/world/level/block/entity/ChestBlockEntity.java` : L105–L107

```text
  105 |     public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, ChestBlockEntity entity) {
  106 |         entity.chestLidController.tickLid();
  107 |     }
```

### [26.1.2.109 reference] `net/minecraft/world/level/block/entity/ChestBlockEntity.java` : L125–L149

```text
  125 |     @Override
  126 |     public boolean triggerEvent(int b0, int b1) {
  127 |         if (b0 == 1) {
  128 |             this.chestLidController.shouldBeOpen(b1 > 0);
  129 |             return true;
  130 |         } else {
  131 |             return super.triggerEvent(b0, b1);
  132 |         }
  133 |     }
  134 | 
  135 |     @Override
  136 |     public void startOpen(ContainerUser containerUser) {
  137 |         if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
  138 |             this.openersCounter
  139 |                 .incrementOpeners(
  140 |                     containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState(), containerUser.getContainerInteractionRange()
  141 |                 );
  142 |         }
  143 |     }
  144 | 
  145 |     @Override
  146 |     public void stopOpen(ContainerUser containerUser) {
  147 |         if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
  148 |             this.openersCounter.decrementOpeners(containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState());
  149 |         }
```

### [26.1.2.109 reference] `net/minecraft/world/level/block/entity/ChestBlockEntity.java` : L212–L215

```text
  212 |     protected void signalOpenCount(Level level, BlockPos pos, BlockState blockState, int previous, int current) {
  213 |         Block block = blockState.getBlock();
  214 |         level.blockEvent(pos, block, 1, current);
  215 |     }
```

### [26.1.2.109 reference] `net/minecraft/world/level/block/entity/ChestLidController.java` : L5–L27

```text
    5 | public class ChestLidController {
    6 |     private boolean shouldBeOpen;
    7 |     private float openness;
    8 |     private float oOpenness;
    9 | 
   10 |     public void tickLid() {
   11 |         this.oOpenness = this.openness;
   12 |         float speed = 0.1F;
   13 |         if (!this.shouldBeOpen && this.openness > 0.0F) {
   14 |             this.openness = Math.max(this.openness - 0.1F, 0.0F);
   15 |         } else if (this.shouldBeOpen && this.openness < 1.0F) {
   16 |             this.openness = Math.min(this.openness + 0.1F, 1.0F);
   17 |         }
   18 |     }
   19 | 
   20 |     public float getOpenness(float a) {
   21 |         return Mth.lerp(a, this.oOpenness, this.openness);
   22 |     }
   23 | 
   24 |     public void shouldBeOpen(boolean shouldBeOpen) {
   25 |         this.shouldBeOpen = shouldBeOpen;
   26 |     }
   27 | }
```

### [26.1.2.109 reference] `net/minecraft/server/level/ServerLevel.java` : L1275–L1310

```text
 1275 |     @Override
 1276 |     public void blockEvent(BlockPos pos, Block block, int b0, int b1) {
 1277 |         this.blockEvents.add(new BlockEventData(pos, block, b0, b1));
 1278 |     }
 1279 | 
 1280 |     private void runBlockEvents() {
 1281 |         this.blockEventsToReschedule.clear();
 1282 | 
 1283 |         while (!this.blockEvents.isEmpty()) {
 1284 |             BlockEventData eventData = this.blockEvents.removeFirst();
 1285 |             if (this.shouldTickBlocksAt(eventData.pos())) {
 1286 |                 if (this.doBlockEvent(eventData)) {
 1287 |                     this.server
 1288 |                         .getPlayerList()
 1289 |                         .broadcast(
 1290 |                             null,
 1291 |                             eventData.pos().getX(),
 1292 |                             eventData.pos().getY(),
 1293 |                             eventData.pos().getZ(),
 1294 |                             64.0,
 1295 |                             this.dimension(),
 1296 |                             new ClientboundBlockEventPacket(eventData.pos(), eventData.block(), eventData.paramA(), eventData.paramB())
 1297 |                         );
 1298 |                 }
 1299 |             } else {
 1300 |                 this.blockEventsToReschedule.add(eventData);
 1301 |             }
 1302 |         }
 1303 | 
 1304 |         this.blockEvents.addAll(this.blockEventsToReschedule);
 1305 |     }
 1306 | 
 1307 |     private boolean doBlockEvent(BlockEventData eventData) {
 1308 |         BlockState state = this.getBlockState(eventData.pos());
 1309 |         return state.is(eventData.block()) ? state.triggerEvent(this, eventData.pos(), eventData.paramA(), eventData.paramB()) : false;
 1310 |     }
```

## V09 — 不能用 ageInTicks 一个字段解决所有实体

ItemEntity bob/spin 可通过渲染 age 单独恢复，实际寿命/重力不能推进；Bat 还需要在客户端表现副本上管理 AnimationState，不能调用其会改坐标的 tick。

### [26.1.2.109 reference] `net/minecraft/client/renderer/entity/ItemEntityRenderer.java` : L28–L70

```text
   28 |         super(context);
   29 |         this.itemModelResolver = context.getItemModelResolver();
   30 |         this.shadowRadius = 0.15F;
   31 |         this.shadowStrength = 0.75F;
   32 |     }
   33 | 
   34 |     public ItemEntityRenderState createRenderState() {
   35 |         return new ItemEntityRenderState();
   36 |     }
   37 | 
   38 |     public void extractRenderState(ItemEntity entity, ItemEntityRenderState state, float partialTicks) {
   39 |         super.extractRenderState(entity, state, partialTicks);
   40 |         state.bobOffset = entity.bobOffs;
   41 |         state.shouldBob = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(entity.getItem()).shouldBobAsEntity(entity.getItem());
   42 |         state.shouldSpread = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(entity.getItem()).shouldSpreadAsEntity(entity.getItem());
   43 |         state.extractItemGroupRenderState(entity, entity.getItem(), this.itemModelResolver);
   44 |     }
   45 | 
   46 |     public void submit(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
   47 |         if (!state.item.isEmpty()) {
   48 |             poseStack.pushPose();
   49 |             AABB boundingBox = state.item.getModelBoundingBox();
   50 |             float minOffsetY = -((float)boundingBox.minY) + 0.0625F;
   51 |             float bob = state.shouldBob ? Mth.sin(state.ageInTicks / 10.0F + state.bobOffset) * 0.1F + 0.1F : 0;
   52 |             poseStack.translate(0.0F, bob + minOffsetY, 0.0F);
   53 |             float spin = ItemEntity.getSpin(state.ageInTicks, state.bobOffset);
   54 |             poseStack.mulPose(Axis.YP.rotation(spin));
   55 |             submitMultipleFromCount(poseStack, submitNodeCollector, state.lightCoords, state, this.random, boundingBox);
   56 |             poseStack.popPose();
   57 |             super.submit(state, poseStack, submitNodeCollector, camera);
   58 |         }
   59 |     }
   60 | 
   61 |     public static void submitMultipleFromCount(
   62 |         PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, ItemClusterRenderState state, RandomSource random
   63 |     ) {
   64 |         submitMultipleFromCount(poseStack, submitNodeCollector, lightCoords, state, random, state.item.getModelBoundingBox());
   65 |     }
   66 | 
   67 |     public static void submitMultipleFromCount(
   68 |         PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, ItemClusterRenderState state, RandomSource random, AABB modelBoundingBox
   69 |     ) {
   70 |         int amount = state.count;
```

### [26.1.2.109 reference] `net/minecraft/world/entity/AnimationState.java` : L5–L54

```text
    5 | public class AnimationState {
    6 |     private static final int STOPPED = Integer.MIN_VALUE;
    7 |     private int startTick = Integer.MIN_VALUE;
    8 | 
    9 |     public void start(int tickCount) {
   10 |         this.startTick = tickCount;
   11 |     }
   12 | 
   13 |     public void startIfStopped(int tickCount) {
   14 |         if (!this.isStarted()) {
   15 |             this.start(tickCount);
   16 |         }
   17 |     }
   18 | 
   19 |     public void animateWhen(boolean condition, int tickCount) {
   20 |         if (condition) {
   21 |             this.startIfStopped(tickCount);
   22 |         } else {
   23 |             this.stop();
   24 |         }
   25 |     }
   26 | 
   27 |     public void stop() {
   28 |         this.startTick = Integer.MIN_VALUE;
   29 |     }
   30 | 
   31 |     public void ifStarted(Consumer<AnimationState> timer) {
   32 |         if (this.isStarted()) {
   33 |             timer.accept(this);
   34 |         }
   35 |     }
   36 | 
   37 |     public void fastForward(int ticks, float timeScale) {
   38 |         if (this.isStarted()) {
   39 |             this.startTick -= (int)(ticks * timeScale);
   40 |         }
   41 |     }
   42 | 
   43 |     public long getTimeInMillis(float ageInTicks) {
   44 |         float timeInTicks = ageInTicks - this.startTick;
   45 |         return (long)(timeInTicks * 50.0F);
   46 |     }
   47 | 
   48 |     public boolean isStarted() {
   49 |         return this.startTick != Integer.MIN_VALUE;
   50 |     }
   51 | 
   52 |     public void copyFrom(AnimationState state) {
   53 |         this.startTick = state.startTick;
   54 |     }
```

### [26.1.2.109 reference] `net/minecraft/world/entity/ambient/Bat.java` : L115–L126

```text
  115 |     @Override
  116 |     public void tick() {
  117 |         super.tick();
  118 |         if (this.isResting()) {
  119 |             this.setDeltaMovement(Vec3.ZERO);
  120 |             this.setPosRaw(this.getX(), Mth.floor(this.getY()) + 1.0 - this.getBbHeight(), this.getZ());
  121 |         } else {
  122 |             this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.6, 1.0));
  123 |         }
  124 | 
  125 |         this.setupAnimationStates();
  126 |     }
```

### [26.1.2.109 reference] `net/minecraft/world/entity/ambient/Bat.java` : L234–L241

```text
  234 |     private void setupAnimationStates() {
  235 |         if (this.isResting()) {
  236 |             this.flyAnimationState.stop();
  237 |             this.restAnimationState.startIfStopped(this.tickCount);
  238 |         } else {
  239 |             this.restAnimationState.stop();
  240 |             this.flyAnimationState.startIfStopped(this.tickCount);
  241 |         }
```

### [26.1.2.109 reference] `net/minecraft/client/renderer/entity/BatRenderer.java` : L23–L33

```text
   23 |     public BatRenderState createRenderState() {
   24 |         return new BatRenderState();
   25 |     }
   26 | 
   27 |     public void extractRenderState(Bat entity, BatRenderState state, float partialTicks) {
   28 |         super.extractRenderState(entity, state, partialTicks);
   29 |         state.isResting = entity.isResting();
   30 |         state.flyAnimationState.copyFrom(entity.flyAnimationState);
   31 |         state.restAnimationState.copyFrom(entity.restAnimationState);
   32 |     }
   33 | }
```

### [26.1.2.109 reference] `net/minecraft/client/model/ambient/BatModel.java` : L73–L80

```text
   73 |     public void setupAnim(BatRenderState state) {
   74 |         super.setupAnim(state);
   75 |         if (state.isResting) {
   76 |             this.applyHeadRotation(state.yRot);
   77 |         }
   78 | 
   79 |         this.flyingAnimation.apply(state.flyAnimationState, state.ageInTicks);
   80 |         this.restingAnimation.apply(state.restAnimationState, state.ageInTicks);
```

## C01 — 相机问题仅记录

用户报告：镜头高度不随地形变化，进入方块后出现进入地下现象。记录待下一轮合并；未独立实机复现，不把用户关于碰撞的原因推测当成已验证根因。本轮不修改相机。下面仅保留当前实现作后续基线。

### [180556] `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/client/ClientControl.java` : L278–L334

```text
  278 |     }
  279 |     public static void frame() {
  280 |         long now = System.nanoTime();
  281 |         double seconds = frameTime == 0 ? 0 : Math.min(.05, (now - frameTime) / 1e9);
  282 |         frameTime = now;
  283 |         if (recipient != Recipient.CAMERA) return;
  284 |         var o = Minecraft.getInstance().options;
  285 |         double f = axis(o.keyUp, o.keyDown), l = axis(o.keyLeft, o.keyRight);
  286 |         yaw = Mth.wrapDegrees(yaw + (float)(axis(CombatControls.rotateRightKey(), CombatControls.rotateLeftKey()) * seconds * 90));
  287 |         Vec3 forward = Vec3.directionFromRotation(0, yaw);
  288 |         Vec3 left = new Vec3(forward.z, 0, -forward.x).normalize();
  289 |         Vec3 delta = forward.scale(f).add(left.scale(l));
  290 |         if (delta.lengthSqr() > 0) move(delta.normalize().scale(seconds * (cameraKeys.contains(o.keySprint) ? 12 : 6)));
  291 |         updateRig();
  292 |     }
  293 |     private static int axis(KeyMapping positive, KeyMapping negative) {
  294 |         return (cameraKeys.contains(positive) ? 1 : 0) - (cameraKeys.contains(negative) ? 1 : 0);
  295 |     }
  296 |     private static Vec3 forward() { return Vec3.directionFromRotation(pitch, yaw); }
  297 |     private static void move(Vec3 delta) {
  298 |         if (focus == null) return;
  299 |         var world = Minecraft.getInstance().level;
  300 |         if (world == null) return;
  301 |         int steps = Math.max(1, (int)Math.ceil(delta.length() * 2));
  302 |         for (int i = 1; i <= steps; i++)
  303 |             if (!world.hasChunkAt(BlockPos.containing(focus.add(delta.scale((double)i / steps))))) return;
  304 |         focus = focus.add(delta);
  305 |         updateRig();
  306 |     }
  307 |     private static void updateRig() {
  308 |         if (focus == null) return;
  309 |         var mc = Minecraft.getInstance();
  310 |         Vec3 desired = focus.subtract(forward().scale(distance));
  311 |         // Clip the visual boom without loading terrain or moving the camera entity.
  312 |         if (mc.level != null && mc.player != null) {
  313 |             Vec3 boom = desired.subtract(focus);
  314 |             for (int i = 1, count = Math.max(1, (int)Math.ceil(distance * 2)); i <= count; i++) {
  315 |                 Vec3 point = focus.add(boom.scale((double)i / count));
  316 |                 if (!mc.level.hasChunkAt(BlockPos.containing(point))) {
  317 |                     desired = focus.add(boom.scale((double)(i - 1) / count));
  318 |                     break;
  319 |                 }
  320 |             }
  321 |             var hit = mc.level.clip(new ClipContext(focus, desired, ClipContext.Block.VISUAL,
  322 |                 ClipContext.Fluid.NONE, mc.player));
  323 |             if (hit.getType() != HitResult.Type.MISS) {
  324 |                 Vec3 offset = hit.getLocation().subtract(focus);
  325 |                 desired = focus.add(offset.normalize().scale(Math.max(0, offset.length() - .2)));
  326 |             }
  327 |         }
  328 |         position = desired;
  329 |     }
  330 |     public static void home() {
  331 |         var mc = Minecraft.getInstance();
  332 |         if (session && mc.player != null) { focus = mc.player.position().add(0,1,0); updateRig(); }
  333 |     }
  334 |     public static void preset() { if (session) { pitch = pitch < 65 ? 78 : 55; updateRig(); } }
```

## S01 — 全 ZIP Java 文本扫描

扫描 111 个 Java 文件中的明确直接调用/类名。零命中只支持“此包没有这些显式生产/适配入口”，不能证明外部依赖或反射完全不产生任何动画。

```json
{
  "archive": "DNDTurn-sources(20260925-180556).zip",
  "sha256": "a42d6f6946c783bb6d95399a267de99376072ca80c21690c3ce051b90037d0c6",
  "java_file_count": 111,
  "scan_scope": "all .java in this archive; literal method/class usage, not proof against reflective/external-mod producers",
  "queries": {
    "\\.swing\\s*\\(": [],
    "ClientboundAnimatePacket": [],
    "createTrackingEmitter": [],
    "particleEngine\\.tick": [],
    "RegisterRenderStateModifiersEvent": [],
    "emitParticlesAndSounds": []
  }
}
```

## T01 — 本轮实际执行的独立 common 探针

这是 Java 规则层探针，不是 JUnit/GameTest/Minecraft。10 项正向通过；目标 .84/.109 均未运行。

```text
PASS candidate approach preserves action, spends observed movement; cancellation does not refund movement
PASS wrong plan step rejected without version mutation
PASS pre-effect placement rejection leaves action available
PASS accepted continuous action charges once; cancellation does not refund action
PASS block-only interaction allowed after action exhaustion; next paid action denied
PASS root cannot finish before child; recovery closes pending movement then root without action charge
PASS confirmed attack child and parent before target leave stay COMPLETED across restore
PASS target departure before attack execution interrupts plan, preserves action and spent movement
PASS unconfirmed world effect remains UNKNOWN on departure and restore; no blanket success conversion
PASS completed plan is immutable after owner departure and restore
SUMMARY: 10 positive standalone common probes passed. No JUnit, Minecraft, Mixin, renderer, or real input runtime was executed.
```
