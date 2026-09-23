# DNDTurn 环境 tick 重构：源码证据
日期：2026-09-26。本文是只读源码摘录；没有修改项目、编译或运行游戏。所有 `Lxxx` 都是原文件行号，不是 Files 引用行号。
实现目标：当前快照 `DNDTurn-sources(20260926-104127).zip`，声明 NeoForge `26.1.2.84`。原版参考是用户资料库 `minecraft-patched-26.1.2.109-sources.jar`，不能据此声称 `.84` 注入兼容。
- `DNDTurn-sources(20260926-104127).zip` SHA-256：`6ddead935527a9367f5666732b24d40187e553bcd65637a990294f7ddd4b5ca0`
- `minecraft-patched-26.1.2.109-sources.jar` SHA-256：`d41e656b9c49bac09e6b301532d2f1ef7cfc8ec8920d7bbe63a875d154a84f8a`

## 索引
- [S01 — 当前目标、预算与环境执行所有者](#s01)
- [S02 — 计划更新没有实体来源；延期与原版顺序](#s02)
- [S03 — 拉杆、按钮、门的同步效果与按钮延迟复位](#s03)
- [S04 — TNT 点燃、默认引信、物理与爆炸](#s04)
- [S05 — 中继器与其他红石计划更新](#s05)
- [S06 — 大型垂滴叶与普通荷叶：不可混称](#s06)
- [S07 — 漏斗自动转移与接触旁路](#s07)
- [S08 — 水流、沙子与火的自主后续调度](#s08)
- [S09 — 生产/接触分离的方块实体](#s09)
- [S10 — 粗入口门禁的当前实现](#s10)
- [S11 — 活塞、方块事件与显示维护（精确放行）](#s11)
- [S12 — 补充分类样本：从当前 JAR 提取的具体方法](#s12)

<a id="s01"></a>
## S01 — 当前目标、预算与环境执行所有者

### `targets/neoforge-26.1/gradle.properties`

来源：当前项目；SHA-256：`139ec7fc7ae6c03dc4355c86bbcf1eb027c8d06cf2ce90ee57bb5fd428c9f0fa`

原文件 L1–L3：
```text
    1 | neoforge_261_minecraft_version=26.1
    2 | neoforge_261_version=26.1.2.84
    3 | neoforge_261_version_range=[26.1,)
```

### `targets/neoforge-26.1/build.gradle`

来源：当前项目；SHA-256：`f388acd41e10b249732a3885b4c0d64aff09aa50c67884edc48f81b50c205174`

原文件 L1–L59：
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
   25 | // Fixtures are compiled and loaded only by the automated GameTest run.
   26 | sourceSets {
   27 |     gameTest {
   28 |         compileClasspath += sourceSets.main.output + sourceSets.main.compileClasspath
   29 |         runtimeClasspath += sourceSets.main.output + sourceSets.main.runtimeClasspath
   30 |     }
   31 | }
   32 | neoForge {
   33 |     version = neoforge_261_version
   34 |     mods {
   35 |         "${mod_id}" { sourceSet(sourceSets.main) }
   36 |         dndturnTests {
   37 |             sourceSet(sourceSets.main)
   38 |             sourceSet(sourceSets.gameTest)
   39 |         }
   40 |     }
   41 |     runs {
   42 |         client {
   43 |             client()
   44 |             loadedMods = [neoForge.mods.getByName(mod_id)]
   45 |             if (project.hasProperty('debug')) programArgument '-debug'
   46 |         }
   47 |         server {
   48 |             server()
   49 |             loadedMods = [neoForge.mods.getByName(mod_id)]
   50 |             programArgument '--nogui'
   51 |             if (project.hasProperty('debug')) programArgument '-debug'
   52 |         }
   53 |         gameTestServer {
   54 |             type = 'gameTestServer'
   55 |             gameDirectory = layout.buildDirectory.dir('gametest-run')
   56 |             sourceSet = sourceSets.gameTest
   57 |             loadedMods = [neoForge.mods.dndturnTests]
   58 |             if (project.hasProperty('debug')) programArgument '-debug'
   59 |         }
```

原文件 L103–L106：
```text
  103 | 
  104 | apply from: file('../../gradle/target-conventions/publish.gradle')
  105 | 
  106 | tasks.named('check') { dependsOn tasks.named('gameTestClasses') }
```

### `build.gradle`

来源：当前项目；SHA-256：`42eb9f108f8dab3279f7ccb5953dede79be0e5073b72678338985557b4921293`

原文件 L1–L47：
```text
    1 | group = providers.gradleProperty('mod_group_id').get()
    2 | version = providers.gradleProperty('mod_version').get()
    3 | 
    4 | def targetBuilds = [
    5 |         'forge-1.20.1'    : [directory: 'targets/forge-1.20.1', task: 'buildForge1201'],
    6 |         'fabric-1.20.1'   : [directory: 'targets/fabric-1.20.1', task: 'buildFabric1201'],
    7 |         'neoforge-1.21.1' : [directory: 'targets/neoforge-1.21.1', task: 'buildNeoForge1211'],
    8 |         'neoforge-26.1'   : [directory: 'targets/neoforge-26.1', task: 'buildNeoForge261']
    9 | ]
   10 | 
   11 | def selectedTarget = providers.gradleProperty('target').orNull
   12 | def buildAllTargets = providers.gradleProperty('allTargets').map { it.toBoolean() }.orElse(false)
   13 | 
   14 | if (selectedTarget != null && !targetBuilds.containsKey(selectedTarget)) {
   15 |     throw new GradleException("Unknown target '${selectedTarget}'. Available targets: ${targetBuilds.keySet().join(', ')}")
   16 | }
   17 | 
   18 | project(':common') {
   19 |     group = rootProject.group
   20 |     version = rootProject.version
   21 | }
   22 | 
   23 | targetBuilds.each { targetName, targetBuild ->
   24 |     tasks.register(targetBuild.task, Exec) {
   25 |         group = 'build'
   26 |         description = "Builds ${targetName} in its independent Gradle project."
   27 |         workingDir file(targetBuild.directory)
   28 |         commandLine 'cmd', '/c', 'gradlew.bat', 'build', '--console', 'plain', '--no-daemon'
   29 |     }
   30 | }
   31 | 
   32 | tasks.register('build') {
   33 |     group = 'build'
   34 |     description = 'Builds common and optionally one or all independent platform targets.'
   35 |     dependsOn ':common:build'
   36 | 
   37 |     if (selectedTarget != null) {
   38 |         dependsOn tasks.named(targetBuilds[selectedTarget].task)
   39 |     }
   40 | 
   41 |     if (buildAllTargets.get()) {
   42 |         ['forge-1.20.1', 'fabric-1.20.1', 'neoforge-1.21.1'].each { targetName ->
   43 |             dependsOn tasks.named(targetBuilds[targetName].task)
   44 |         }
   45 |     }
   46 | }
   47 | 
```

### `settings.gradle`

来源：当前项目；SHA-256：`5614e36fa77f62d570c53c0173623ef6cea36b7f0ba9a741a1808a7411d831b7`

原文件 L1–L4：
```text
    1 | rootProject.name = 'DNDTurn'
    2 | 
    3 | include 'common'
    4 | 
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/server/encounter/ServerCombatConfig.java`

来源：当前项目；SHA-256：`336acc2dc85bba3633e9117f7e2e64c756e5d63b08bc0c2f1091c8fe28b2f3e9`

原文件 L27–L43：
```java
   27 | 
   28 |     public static ServerCombatConfig load(MinecraftServer server) {
   29 |         Path path = server.getWorldPath(LevelResource.ROOT).resolve("dndturn-server.properties");
   30 |         Properties defaults = new Properties();
   31 |         defaults.setProperty("discoveryHorizontal", "16");
   32 |         defaults.setProperty("discoveryVertical", "8");
   33 |         defaults.setProperty("regionRadius", "8");
   34 |         defaults.setProperty("maxSampledChunks", "16");
   35 |         defaults.setProperty("maxAnchors", "64");
   36 |         defaults.setProperty("movementTicks", "28");
   37 |         defaults.setProperty("environmentTicks", "20");
   38 |         defaults.setProperty("tacticalKnockbackEnabled", "false");
   39 |         Properties values = new Properties(defaults);
   40 |         try {
   41 |             if (Files.exists(path)) {
   42 |                 try (InputStream in = Files.newInputStream(path)) { values.load(in); }
   43 |             } else {
```

### `common/src/main/java/cc/sighs/dndturn/domain/encounter/EncounterAuthority.java`

来源：当前项目；SHA-256：`3903e875f2d72a183e5dfb282774b4167a11f1b76bfe9f7ac202c92bc527999c`

原文件 L1218–L1237：
```java
 1218 |     /** Reserve one named simulation step; only one may execute at a time. */
 1219 |     public boolean authorizeEnvironmentStep(UUID encounterId, UUID stepId) {
 1220 |         Objects.requireNonNull(stepId);
 1221 |         Encounter encounter = require(encounterId);
 1222 |         if (encounter.completedEnvironmentSteps.contains(stepId)) return false;
 1223 |         if (encounter.phase != EncounterPhase.ENVIRONMENT || encounter.environmentRemaining < 1
 1224 |             || !encounter.pending.isEmpty()) throw new IllegalStateException("not ready for environment simulation");
 1225 |         if (encounter.authorizedEnvironmentStep != null) {
 1226 |             if (encounter.authorizedEnvironmentStep.equals(stepId)) return false;
 1227 |             throw new IllegalStateException("another environment step is executing");
 1228 |         }
 1229 |         long nextVersion = Math.addExact(encounter.version, 1);
 1230 |         encounter.authorizedEnvironmentStep = stepId;
 1231 |         encounter.environmentStepAuthorized = true;
 1232 |         encounter.version = nextVersion;
 1233 |         return true;
 1234 |     }
 1235 | 
 1236 |     /** Abort a reserved step when no simulation was performed. */
 1237 |     public boolean cancelEnvironmentStep(UUID encounterId, UUID stepId) {
```

原文件 L1259–L1310：
```java
 1259 |         }
 1260 |         if (!stepId.equals(encounter.authorizedEnvironmentStep)
 1261 |             || encounter.completedEnvironmentSteps.contains(stepId))
 1262 |             throw new IllegalStateException("environment step was not reserved");
 1263 |         long nextVersion = Math.addExact(encounter.version, 1);
 1264 |         // The encounter ID is the stable environment principal; no member owned this step.
 1265 |         OperationRecord.Snapshot snapshot = new OperationRecord.Snapshot(stepId, null,
 1266 |             encounterId, encounterId, null, null, serverTick, encounter.version,
 1267 |             null, null, OperationRecord.Kind.ENVIRONMENT, observationEpoch);
 1268 |         OperationRecord.Result result = new OperationRecord.Result(snapshot, 0,
 1269 |             OperationRecord.Outcome.UNKNOWN, reason, 0, 0, nextVersion, true);
 1270 |         encounter.authorizedEnvironmentStep = null;
 1271 |         encounter.completedEnvironmentSteps.add(stepId);
 1272 |         encounter.causes.put(stepId, snapshot);
 1273 |         encounter.finalResults.put(stepId, result);
 1274 |         encounter.stepResults.computeIfAbsent(stepId, ignored -> new HashMap<>()).put(0, result);
 1275 |         encounter.history.add(result);
 1276 |         encounter.version = nextVersion;
 1277 |         return result;
 1278 |     }
 1279 | 
 1280 |     /** Caller supplies a stable ID for the simulation step it actually completed. */
 1281 |     public boolean commitEnvironmentStep(UUID encounterId, UUID stepId) {
 1282 |         Objects.requireNonNull(stepId);
 1283 |         Encounter encounter = require(encounterId);
 1284 |         if (encounter.completedEnvironmentSteps.contains(stepId)) return false;
 1285 |         if (encounter.phase != EncounterPhase.ENVIRONMENT || encounter.environmentRemaining < 1)
 1286 |             throw new IllegalStateException("not an environment turn");
 1287 |         if (!stepId.equals(encounter.authorizedEnvironmentStep) || !encounter.pending.isEmpty())
 1288 |             throw new IllegalStateException("environment step was not authorized or is still executing");
 1289 |         long nextVersion = Math.addExact(encounter.version, 1);
 1290 |         long nextRound = encounter.environmentRemaining == 1 ? Math.addExact(encounter.round, 1) : encounter.round;
 1291 |         List<UUID> nextOrder = null;
 1292 |         if (encounter.environmentRemaining == 1) {
 1293 |             nextOrder = new ArrayList<>();
 1294 |             for (Member member : encounter.members.values())
 1295 |                 if (member.eligibleRound <= nextRound) nextOrder.add(member.id);
 1296 |             nextOrder.sort(Comparator.<UUID>comparingInt(id -> encounter.members.get(id).initiative).reversed()
 1297 |                 .thenComparingInt(id -> encounter.members.get(id).tieBreak).thenComparing(UUID::compareTo));
 1298 |             if (nextOrder.isEmpty()) throw new IllegalStateException("no eligible member for next round");
 1299 |         }
 1300 |         encounter.completedEnvironmentSteps.add(stepId);
 1301 |         encounter.authorizedEnvironmentStep = null;
 1302 |         if (--encounter.environmentRemaining == 0) {
 1303 |             encounter.phase = encounter.hostile.isEmpty() ? EncounterPhase.CANDIDATE : EncounterPhase.ACTIVE;
 1304 |             encounter.round = nextRound;
 1305 |             encounter.order.clear();
 1306 |             encounter.order.addAll(nextOrder);
 1307 |             encounter.cursor = 0;
 1308 |             beginMemberTurn(encounter, encounter.members.get(encounter.order.get(encounter.cursor)));
 1309 |         }
 1310 |         encounter.version = nextVersion;
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/server/encounter/EncounterRuntime.java`

来源：当前项目；SHA-256：`e252fa42d1232bac22385e0f967e882e13f3d3958b9a30de0c0ad051b7deca07`

原文件 L484–L526：
```java
  484 |     /** Called before ServerLevel advances game time or drains either scheduled queue. */
  485 |     public void beforeLevelTick(ServerLevel level) {
  486 |         requireThread();
  487 |         if (regions.isEmpty() || candidateRegionChunks(level).isEmpty()) return;
  488 |         auditRestoredEncounters(level);
  489 |         // Isolated recovery probes may audit values, but cannot replace the live
  490 |         // scheduler's queue ownership (including while the world is frozen).
  491 |         if (SERVERS.get(server) != this) return;
  492 |         // New encounters become visible atomically before scheduling platform work.
  493 |         for (UUID id : engine.encounterIds()) {
  494 |             EncounterRegion region = regions.get(id);
  495 |             if (region == null || !region.dimension().equals(level.dimension().identifier().toString())
  496 |                 || pendingMerges.values().stream().anyMatch(plan -> plan.encounters().contains(id))) continue;
  497 |             if (engine.encounterIds().stream().anyMatch(other -> !other.equals(id)
  498 |                 && region.overlaps(engine.stateView(other).region()))) scheduleMerge(id);
  499 |         }
  500 |         attemptPendingMerges(level);
  501 |         scheduledTicks.captureAll(level);
  502 |         if (!level.tickRateManager().runsNormally() || level.isDebug()) return;
  503 |         List<UUID> candidates = new ArrayList<>();
  504 |         for (UUID id : engine.encounterIds()) {
  505 |             if (recoveryPending.contains(id)) continue;
  506 |             EncounterRegion region = regions.get(id);
  507 |             if (region != null && region.dimension().equals(level.dimension().identifier().toString())
  508 |                 && engine.stateView(id).phase() == EncounterPhase.ENVIRONMENT) candidates.add(id);
  509 |         }
  510 |         candidates.sort(UUID::compareTo);
  511 |         UUID last = lastEnvironmentEncounter.get(level);
  512 |         int offset = last == null ? 0 : (candidates.indexOf(last) + 1) % Math.max(1, candidates.size());
  513 |         for (int i = 0; i < candidates.size(); i++) {
  514 |             UUID encounterId = candidates.get((offset + i) % candidates.size());
  515 |             CombatEngine.StateView state = engine.stateView(encounterId);
  516 |             boolean loadedMember = state.members().keySet().stream()
  517 |                 .anyMatch(id -> level.getEntity(id) != null);
  518 |             if (loadedMember && regionChunksLoaded(level, regionChunks.getOrDefault(encounterId, Set.of()))) {
  519 |                 UUID stepId = UUID.randomUUID();
  520 |                 engine.authorizeEnvironmentStep(encounterId, stepId);
  521 |                 activeEnvironmentSteps.put(level, stepId);
  522 |                 activeEnvironmentEncounters.put(level, encounterId);
  523 |                 lastEnvironmentEncounter.put(level, encounterId);
  524 |                 break;
  525 |             }
  526 |         }
```

原文件 L738–L778：
```java
  738 |     public void beforeBlockQueue(ServerLevel level) {
  739 |         requireThread();
  740 |         if (!candidateRegionChunks(level).isEmpty())
  741 |             scheduledTicks.beforeBlockQueue(level, activeEnvironmentEncounters.get(level));
  742 |     }
  743 | 
  744 |     public void beforeFluidQueue(ServerLevel level) {
  745 |         requireThread();
  746 |         if (!candidateRegionChunks(level).isEmpty())
  747 |             scheduledTicks.beforeFluidQueue(level, activeEnvironmentEncounters.get(level));
  748 |     }
  749 | 
  750 |     /** Reached only after the normal ServerLevel tick returned through all world stages. */
  751 |     public void afterLevelTick(ServerLevel level) {
  752 |         requireThread();
  753 |         UUID stepId = activeEnvironmentSteps.get(level);
  754 |         UUID encounterId = activeEnvironmentEncounters.get(level);
  755 |         if (stepId == null) return;
  756 |         if (encounterId != null && engine.encounterIds().contains(encounterId)) {
  757 |             engine.commitEnvironmentStep(encounterId, stepId);
  758 |             activeEnvironmentSteps.remove(level);
  759 |             activeEnvironmentEncounters.remove(level);
  760 |             sync(engine.stateView(encounterId));
  761 |             syncBodyStateTransitions();
  762 |         } else {
  763 |             activeEnvironmentSteps.remove(level);
  764 |             activeEnvironmentEncounters.remove(level);
  765 |         }
  766 |     }
  767 | 
  768 |     /** Called by the outer vanilla world-tick exception boundary, before it reports the crash. */
  769 |     public void abortLevelTick(ServerLevel level, Throwable failure) {
  770 |         requireThread();
  771 |         UUID stepId = activeEnvironmentSteps.remove(level);
  772 |         UUID encounterId = activeEnvironmentEncounters.remove(level);
  773 |         if (stepId == null || encounterId == null || !engine.encounterIds().contains(encounterId)) return;
  774 |         LOGGER.error("Environment step {} in encounter {} has an unknown partial world outcome; "
  775 |             + "releasing its control instead of retrying", stepId, encounterId, failure);
  776 |         engine.failEnvironmentStep(encounterId, stepId, cumulativeServerTicks,
  777 |             "world tick aborted after partial execution: " + failure.getClass().getSimpleName());
  778 |         stop(encounterId);
```

<a id="s02"></a>
## S02 — 计划更新没有实体来源；延期与原版顺序

### `net/minecraft/world/ticks/ScheduledTick.java`

来源：.109 参考源码；SHA-256：`0c0a3f814aa7de8e63a0cced4b11265145acc7a9cd24b1fe140a1d4713d7e2ec`

原文件 L8–L55：
```java
    8 | public record ScheduledTick<T>(T type, BlockPos pos, long triggerTick, TickPriority priority, long subTickOrder) {
    9 |     public static final Comparator<ScheduledTick<?>> DRAIN_ORDER = (o1, o2) -> {
   10 |         int compare = Long.compare(o1.triggerTick, o2.triggerTick);
   11 |         if (compare != 0) {
   12 |             return compare;
   13 |         } else {
   14 |             compare = o1.priority.compareTo(o2.priority);
   15 |             return compare != 0 ? compare : Long.compare(o1.subTickOrder, o2.subTickOrder);
   16 |         }
   17 |     };
   18 |     public static final Comparator<ScheduledTick<?>> INTRA_TICK_DRAIN_ORDER = (o1, o2) -> {
   19 |         int compare = o1.priority.compareTo(o2.priority);
   20 |         return compare != 0 ? compare : Long.compare(o1.subTickOrder, o2.subTickOrder);
   21 |     };
   22 |     public static final Strategy<ScheduledTick<?>> UNIQUE_TICK_HASH = new Strategy<ScheduledTick<?>>() {
   23 |         public int hashCode(ScheduledTick<?> o) {
   24 |             return 31 * o.pos().hashCode() + o.type().hashCode();
   25 |         }
   26 | 
   27 |         public boolean equals(@Nullable ScheduledTick<?> a, @Nullable ScheduledTick<?> b) {
   28 |             if (a == b) {
   29 |                 return true;
   30 |             } else {
   31 |                 return a != null && b != null ? a.type() == b.type() && a.pos().equals(b.pos()) : false;
   32 |             }
   33 |         }
   34 |     };
   35 | 
   36 |     public ScheduledTick(T type, BlockPos pos, long triggerTick, long subTickOrder) {
   37 |         this(type, pos, triggerTick, TickPriority.NORMAL, subTickOrder);
   38 |     }
   39 | 
   40 |     public ScheduledTick(T type, BlockPos pos, long triggerTick, TickPriority priority, long subTickOrder) {
   41 |         pos = pos.immutable();
   42 |         this.type = type;
   43 |         this.pos = pos;
   44 |         this.triggerTick = triggerTick;
   45 |         this.priority = priority;
   46 |         this.subTickOrder = subTickOrder;
   47 |     }
   48 | 
   49 |     public static <T> ScheduledTick<T> probe(T type, BlockPos pos) {
   50 |         return new ScheduledTick<>(type, pos, 0L, TickPriority.NORMAL, 0L);
   51 |     }
   52 | 
   53 |     public SavedTick<T> toSavedTick(long currentTick) {
   54 |         return new SavedTick<>(this.type, this.pos, (int)(this.triggerTick - currentTick), this.priority);
   55 |     }
```

### `net/minecraft/world/level/LevelAccessor.java`

来源：.109 参考源码；SHA-256：`0abbe93e93564a2d4c6c484220563122b396f7a39b270001719fcefc05463e77`

原文件 L25–L37：
```java
   25 | 
   26 | public interface LevelAccessor extends CommonLevelAccessor, ScheduledTickAccess {
   27 |     long nextSubTickCount();
   28 | 
   29 |     @Override
   30 |     default <T> ScheduledTick<T> createTick(BlockPos pos, T type, int tickDelay, TickPriority priority) {
   31 |         return new ScheduledTick<>(type, pos, this.getGameTime() + tickDelay, priority, this.nextSubTickCount());
   32 |     }
   33 | 
   34 |     @Override
   35 |     default <T> ScheduledTick<T> createTick(BlockPos pos, T type, int tickDelay) {
   36 |         return new ScheduledTick<>(type, pos, this.getGameTime() + tickDelay, this.nextSubTickCount());
   37 |     }
```

### `net/minecraft/world/ticks/LevelTicks.java`

来源：.109 参考源码；SHA-256：`0fabc26f2d91477572dd302103285db5061888cb1e2a26575aaf87a762b85868`

原文件 L71–L91：
```java
   71 |     public void schedule(ScheduledTick<T> tick) {
   72 |         long chunkKey = ChunkPos.pack(tick.pos());
   73 |         LevelChunkTicks<T> tickContainer = this.allContainers.get(chunkKey);
   74 |         if (tickContainer == null) {
   75 |             Util.logAndPauseIfInIde("Trying to schedule tick in not loaded position " + tick.pos());
   76 |         } else {
   77 |             tickContainer.schedule(tick);
   78 |         }
   79 |     }
   80 | 
   81 |     public void tick(long currentTick, int maxTicksToProcess, BiConsumer<BlockPos, T> output) {
   82 |         ProfilerFiller profiler = Profiler.get();
   83 |         profiler.push("collect");
   84 |         this.collectTicks(currentTick, maxTicksToProcess, profiler);
   85 |         profiler.popPush("run");
   86 |         profiler.incrementCounter("ticksToRun", this.toRunThisTick.size());
   87 |         this.runCollectedTicks(output);
   88 |         profiler.popPush("cleanup");
   89 |         this.cleanupAfterTick();
   90 |         profiler.pop();
   91 |     }
```

原文件 L182–L218：
```java
  182 |     private void runCollectedTicks(BiConsumer<BlockPos, T> output) {
  183 |         while (!this.toRunThisTick.isEmpty()) {
  184 |             ScheduledTick<T> entry = this.toRunThisTick.poll();
  185 |             if (!this.toRunThisTickSet.isEmpty()) {
  186 |                 this.toRunThisTickSet.remove(entry);
  187 |             }
  188 | 
  189 |             this.alreadyRunThisTick.add(entry);
  190 |             output.accept(entry.pos(), entry.type());
  191 |         }
  192 |     }
  193 | 
  194 |     private void cleanupAfterTick() {
  195 |         this.toRunThisTick.clear();
  196 |         this.containersToTick.clear();
  197 |         this.alreadyRunThisTick.clear();
  198 |         this.toRunThisTickSet.clear();
  199 |     }
  200 | 
  201 |     @Override
  202 |     public boolean hasScheduledTick(BlockPos pos, T block) {
  203 |         LevelChunkTicks<T> tickContainer = this.allContainers.get(ChunkPos.pack(pos));
  204 |         return tickContainer != null && tickContainer.hasScheduledTick(pos, block);
  205 |     }
  206 | 
  207 |     @Override
  208 |     public boolean willTickThisTick(BlockPos pos, T type) {
  209 |         this.calculateTickSetIfNeeded();
  210 |         return this.toRunThisTickSet.contains(ScheduledTick.probe(type, pos));
  211 |     }
  212 | 
  213 |     private void calculateTickSetIfNeeded() {
  214 |         if (this.toRunThisTickSet.isEmpty() && !this.toRunThisTick.isEmpty()) {
  215 |             this.toRunThisTickSet.addAll(this.toRunThisTick);
  216 |         }
  217 |     }
  218 | 
```

<a id="s03"></a>
## S03 — 拉杆、按钮、门的同步效果与按钮延迟复位

### `net/minecraft/world/level/block/LeverBlock.java`

来源：.109 参考源码；SHA-256：`5182ff47635037f53e741499e8b2c8449e62e8a44a8182d90cff9bb4329566ba`

原文件 L62–L110：
```java
   62 |     @Override
   63 |     protected InteractionResult useWithoutItem(BlockState stateBefore, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
   64 |         if (level.isClientSide()) {
   65 |             BlockState stateAfter = stateBefore.cycle(POWERED);
   66 |             if (stateAfter.getValue(POWERED)) {
   67 |                 makeParticle(stateAfter, level, pos, 1.0F);
   68 |             }
   69 |         } else {
   70 |             this.pull(stateBefore, level, pos, null);
   71 |         }
   72 | 
   73 |         return InteractionResult.SUCCESS;
   74 |     }
   75 | 
   76 |     @Override
   77 |     protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
   78 |         if (explosion.canTriggerBlocks()) {
   79 |             this.pull(state, level, pos, null);
   80 |         }
   81 | 
   82 |         super.onExplosionHit(state, level, pos, explosion, onHit);
   83 |     }
   84 | 
   85 |     public void pull(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
   86 |         state = state.cycle(POWERED);
   87 |         level.setBlock(pos, state, 3);
   88 |         this.updateNeighbours(state, level, pos);
   89 |         playSound(player, level, pos, state);
   90 |         level.gameEvent(player, state.getValue(POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
   91 |     }
   92 | 
   93 |     protected static void playSound(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState stateAfter) {
   94 |         float pitch = stateAfter.getValue(POWERED) ? 0.6F : 0.5F;
   95 |         level.playSound(player, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, pitch);
   96 |     }
   97 | 
   98 |     private static void makeParticle(BlockState state, LevelAccessor level, BlockPos pos, float scale) {
   99 |         Direction opposite = state.getValue(FACING).getOpposite();
  100 |         Direction oppositeConnect = getConnectedDirection(state).getOpposite();
  101 |         double x = pos.getX() + 0.5 + 0.1 * opposite.getStepX() + 0.2 * oppositeConnect.getStepX();
  102 |         double y = pos.getY() + 0.5 + 0.1 * opposite.getStepY() + 0.2 * oppositeConnect.getStepY();
  103 |         double z = pos.getZ() + 0.5 + 0.1 * opposite.getStepZ() + 0.2 * oppositeConnect.getStepZ();
  104 |         level.addParticle(new DustParticleOptions(16711680, scale), x, y, z, 0.0, 0.0, 0.0);
  105 |     }
  106 | 
  107 |     @Override
  108 |     public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
  109 |         if (state.getValue(POWERED) && random.nextFloat() < 0.25F) {
  110 |             makeParticle(state, level, pos, 0.5F);
```

### `net/minecraft/world/level/block/ButtonBlock.java`

来源：.109 参考源码；SHA-256：`00566a53e6e66c6dde7846b8c7b5c982c6fb501ee04218b8a1bbbacd4966bd9f`

原文件 L88–L112：
```java
   88 |     protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
   89 |         if (state.getValue(POWERED)) {
   90 |             return InteractionResult.CONSUME;
   91 |         } else {
   92 |             this.press(state, level, pos, player);
   93 |             return InteractionResult.SUCCESS;
   94 |         }
   95 |     }
   96 | 
   97 |     @Override
   98 |     protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> onHit) {
   99 |         if (explosion.canTriggerBlocks() && !state.getValue(POWERED)) {
  100 |             this.press(state, level, pos, null);
  101 |         }
  102 | 
  103 |         super.onExplosionHit(state, level, pos, explosion, onHit);
  104 |     }
  105 | 
  106 |     public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
  107 |         level.setBlock(pos, state.setValue(POWERED, true), 3);
  108 |         this.updateNeighbours(state, level, pos);
  109 |         level.scheduleTick(pos, this, this.ticksToStayPressed);
  110 |         this.playSound(player, level, pos, true);
  111 |         level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos);
  112 |     }
```

原文件 L144–L183：
```java
  144 |     @Override
  145 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
  146 |         if (state.getValue(POWERED)) {
  147 |             this.checkPressed(state, level, pos);
  148 |         }
  149 |     }
  150 | 
  151 |     @Override
  152 |     protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
  153 |         if (!level.isClientSide() && this.type.canButtonBeActivatedByArrows() && !state.getValue(POWERED)) {
  154 |             this.checkPressed(state, level, pos);
  155 |         }
  156 |     }
  157 | 
  158 |     protected void checkPressed(BlockState state, Level level, BlockPos pos) {
  159 |         AbstractArrow firstArrow = this.type.canButtonBeActivatedByArrows()
  160 |             ? level.getEntitiesOfClass(AbstractArrow.class, state.getShape(level, pos).bounds().move(pos)).stream().findFirst().orElse(null)
  161 |             : null;
  162 |         boolean shouldBePressed = firstArrow != null;
  163 |         boolean wasPressed = state.getValue(POWERED);
  164 |         if (shouldBePressed != wasPressed) {
  165 |             level.setBlock(pos, state.setValue(POWERED, shouldBePressed), 3);
  166 |             this.updateNeighbours(state, level, pos);
  167 |             this.playSound(null, level, pos, shouldBePressed);
  168 |             level.gameEvent(firstArrow, shouldBePressed ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
  169 |         }
  170 | 
  171 |         if (shouldBePressed) {
  172 |             level.scheduleTick(new BlockPos(pos), this, this.ticksToStayPressed);
  173 |         }
  174 |     }
  175 | 
  176 |     private void updateNeighbours(BlockState state, Level level, BlockPos pos) {
  177 |         Direction front = getConnectedDirection(state).getOpposite();
  178 |         Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(
  179 |             level, front, front.getAxis().isHorizontal() ? Direction.UP : state.getValue(FACING)
  180 |         );
  181 |         level.updateNeighborsAt(pos, this, orientation);
  182 |         level.updateNeighborsAt(pos.relative(front), this, orientation);
  183 |     }
```

### `net/minecraft/world/level/block/DoorBlock.java`

来源：.109 参考源码；SHA-256：`54b25b7daebcd4a69f13dcb93cf5538de1a816913b3e249bebade2f1413c749c`

原文件 L197–L234：
```java
  197 |     @Override
  198 |     protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
  199 |         if (!this.type.canOpenByHand()) {
  200 |             return InteractionResult.PASS;
  201 |         } else {
  202 |             state = state.cycle(OPEN);
  203 |             level.setBlock(pos, state, 10);
  204 |             this.playSound(player, level, pos, state.getValue(OPEN));
  205 |             level.gameEvent(player, this.isOpen(state) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
  206 |             return InteractionResult.SUCCESS;
  207 |         }
  208 |     }
  209 | 
  210 |     public boolean isOpen(BlockState state) {
  211 |         return state.getValue(OPEN);
  212 |     }
  213 | 
  214 |     public void setOpen(@Nullable Entity sourceEntity, Level level, BlockState state, BlockPos pos, boolean shouldOpen) {
  215 |         if (state.is(this) && state.getValue(OPEN) != shouldOpen) {
  216 |             level.setBlock(pos, state.setValue(OPEN, shouldOpen), 10);
  217 |             this.playSound(sourceEntity, level, pos, shouldOpen);
  218 |             level.gameEvent(sourceEntity, shouldOpen ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
  219 |         }
  220 |     }
  221 | 
  222 |     @Override
  223 |     protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
  224 |         boolean signal = level.hasNeighborSignal(pos)
  225 |             || level.hasNeighborSignal(pos.relative(state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
  226 |         if (!this.defaultBlockState().is(block) && signal != state.getValue(POWERED)) {
  227 |             if (signal != state.getValue(OPEN)) {
  228 |                 this.playSound(null, level, pos, signal);
  229 |                 level.gameEvent(null, signal ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
  230 |             }
  231 | 
  232 |             level.setBlock(pos, state.setValue(POWERED, signal).setValue(OPEN, signal), 2);
  233 |         }
  234 |     }
```

<a id="s04"></a>
## S04 — TNT 点燃、默认引信、物理与爆炸

### `net/minecraft/world/level/block/TntBlock.java`

来源：.109 参考源码；SHA-256：`dc858f41ff18f8b22603c29fbbbce6246887c5e9ba390b05b4f9c6c16b7e5331`

原文件 L48–L99：
```java
   48 |     protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
   49 |         if (!oldState.is(state.getBlock())) {
   50 |             if (level.hasNeighborSignal(pos) && onCaughtFire(state, level, pos, null, null)) {
   51 |                 level.removeBlock(pos, false);
   52 |             }
   53 |         }
   54 |     }
   55 | 
   56 |     @Override
   57 |     protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
   58 |         if (level.hasNeighborSignal(pos) && onCaughtFire(state, level, pos, null, null)) {
   59 |             level.removeBlock(pos, false);
   60 |         }
   61 |     }
   62 | 
   63 |     @Override
   64 |     public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
   65 |         if (!level.isClientSide() && !player.getAbilities().instabuild && state.getValue(UNSTABLE)) {
   66 |             onCaughtFire(state, level, pos, null, null);
   67 |         }
   68 | 
   69 |         return super.playerWillDestroy(level, pos, state, player);
   70 |     }
   71 | 
   72 |     @Override
   73 |     public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
   74 |         if (level.getGameRules().get(GameRules.TNT_EXPLODES)) {
   75 |             PrimedTnt primed = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, explosion.getIndirectSourceEntity());
   76 |             int fuse = primed.getFuse();
   77 |             primed.setFuse((short)(level.getRandom().nextInt(fuse / 4) + fuse / 8));
   78 |             level.addFreshEntity(primed);
   79 |         }
   80 |     }
   81 | 
   82 |     /** @deprecated Neo: use {@link net.neoforged.neoforge.common.extensions.IBlockStateExtension#onCaughtFire(Level,BlockPos,net.minecraft.core.Direction,LivingEntity)} instead */
   83 |     @Deprecated
   84 |     public static boolean prime(Level level, BlockPos pos) {
   85 |         return prime(level, pos, null);
   86 |     }
   87 | 
   88 |     /** @deprecated Neo: use {@link net.neoforged.neoforge.common.extensions.IBlockStateExtension#onCaughtFire(Level,BlockPos,net.minecraft.core.Direction,LivingEntity)} instead */
   89 |     @Deprecated
   90 |     private static boolean prime(Level level, BlockPos pos, @Nullable LivingEntity source) {
   91 |         if (level instanceof ServerLevel serverLevel && serverLevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
   92 |             PrimedTnt tnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, source);
   93 |             level.addFreshEntity(tnt);
   94 |             level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
   95 |             level.gameEvent(source, GameEvent.PRIME_FUSE, pos);
   96 |             return true;
   97 |         } else {
   98 |             return false;
   99 |         }
```

原文件 L102–L139：
```java
  102 |     @Override
  103 |     protected InteractionResult useItemOn(
  104 |         ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
  105 |     ) {
  106 |         if (!itemStack.is(Items.FLINT_AND_STEEL) && !itemStack.is(Items.FIRE_CHARGE)) {
  107 |             return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
  108 |         } else {
  109 |             if (onCaughtFire(state, level, pos, hitResult.getDirection(), player)) {
  110 |                 level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
  111 |                 Item item = itemStack.getItem();
  112 |                 if (itemStack.is(Items.FLINT_AND_STEEL)) {
  113 |                     itemStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
  114 |                 } else {
  115 |                     itemStack.consume(1, player);
  116 |                 }
  117 | 
  118 |                 player.awardStat(Stats.ITEM_USED.get(item));
  119 |             } else if (level instanceof ServerLevel serverLevel && !serverLevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
  120 |                 player.sendOverlayMessage(Component.translatable("block.minecraft.tnt.disabled"));
  121 |                 return InteractionResult.PASS;
  122 |             }
  123 | 
  124 |             return InteractionResult.SUCCESS;
  125 |         }
  126 |     }
  127 | 
  128 |     @Override
  129 |     protected void onProjectileHit(Level level, BlockState state, BlockHitResult blockHit, Projectile projectile) {
  130 |         if (level instanceof ServerLevel serverLevel) {
  131 |             BlockPos pos = blockHit.getBlockPos();
  132 |             Entity owner = projectile.getOwner();
  133 |             if (projectile.isOnFire()
  134 |                 && projectile.mayInteract(serverLevel, pos)
  135 |                 && onCaughtFire(state, level, pos, null, owner instanceof LivingEntity ? (LivingEntity)owner : null)) {
  136 |                 level.removeBlock(pos, false);
  137 |             }
  138 |         }
  139 |     }
```

原文件 L151–L154：
```java
  151 |     @Override
  152 |     public boolean onCaughtFire(BlockState state, Level world, BlockPos pos, net.minecraft.core.@Nullable Direction face, @Nullable LivingEntity igniter) {
  153 |         return prime(world, pos, igniter);
  154 |     }
```

### `net/minecraft/world/entity/item/PrimedTnt.java`

来源：.109 参考源码；SHA-256：`60d1d1bf3bb5e4628c814567be0bf40e4a3d088ac81b2157f79e215ef5a976eb`

原文件 L31–L117：
```java
   31 | public class PrimedTnt extends Entity implements TraceableEntity {
   32 |     private static final EntityDataAccessor<Integer> DATA_FUSE_ID = SynchedEntityData.defineId(PrimedTnt.class, EntityDataSerializers.INT);
   33 |     private static final EntityDataAccessor<BlockState> DATA_BLOCK_STATE_ID = SynchedEntityData.defineId(PrimedTnt.class, EntityDataSerializers.BLOCK_STATE);
   34 |     private static final short DEFAULT_FUSE_TIME = 80;
   35 |     private static final float DEFAULT_EXPLOSION_POWER = 4.0F;
   36 |     private static final BlockState DEFAULT_BLOCK_STATE = Blocks.TNT.defaultBlockState();
   37 |     private static final String TAG_BLOCK_STATE = "block_state";
   38 |     public static final String TAG_FUSE = "fuse";
   39 |     private static final String TAG_EXPLOSION_POWER = "explosion_power";
   40 |     private static final ExplosionDamageCalculator USED_PORTAL_DAMAGE_CALCULATOR = new ExplosionDamageCalculator() {
   41 |         @Override
   42 |         public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
   43 |             return state.is(Blocks.NETHER_PORTAL) ? false : super.shouldBlockExplode(explosion, level, pos, state, power);
   44 |         }
   45 | 
   46 |         @Override
   47 |         public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState block, FluidState fluid) {
   48 |             return block.is(Blocks.NETHER_PORTAL) ? Optional.empty() : super.getBlockExplosionResistance(explosion, level, pos, block, fluid);
   49 |         }
   50 |     };
   51 |     private @Nullable EntityReference<LivingEntity> owner;
   52 |     private boolean usedPortal;
   53 |     private float explosionPower = 4.0F;
   54 | 
   55 |     public PrimedTnt(EntityType<? extends PrimedTnt> type, Level level) {
   56 |         super(type, level);
   57 |         this.blocksBuilding = true;
   58 |     }
   59 | 
   60 |     public PrimedTnt(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
   61 |         this(EntityType.TNT, level);
   62 |         this.setPos(x, y, z);
   63 |         double rot = level.getRandom().nextDouble() * (float) (Math.PI * 2);
   64 |         this.setDeltaMovement(-Math.sin(rot) * 0.02, 0.2F, -Math.cos(rot) * 0.02);
   65 |         this.setFuse(80);
   66 |         this.xo = x;
   67 |         this.yo = y;
   68 |         this.zo = z;
   69 |         this.owner = EntityReference.of(owner);
   70 |     }
   71 | 
   72 |     @Override
   73 |     protected void defineSynchedData(SynchedEntityData.Builder entityData) {
   74 |         entityData.define(DATA_FUSE_ID, 80);
   75 |         entityData.define(DATA_BLOCK_STATE_ID, DEFAULT_BLOCK_STATE);
   76 |     }
   77 | 
   78 |     @Override
   79 |     protected Entity.MovementEmission getMovementEmission() {
   80 |         return Entity.MovementEmission.NONE;
   81 |     }
   82 | 
   83 |     @Override
   84 |     public boolean isPickable() {
   85 |         return !this.isRemoved();
   86 |     }
   87 | 
   88 |     @Override
   89 |     protected double getDefaultGravity() {
   90 |         return 0.04;
   91 |     }
   92 | 
   93 |     @Override
   94 |     public void tick() {
   95 |         this.handlePortal();
   96 |         this.applyGravity();
   97 |         this.move(MoverType.SELF, this.getDeltaMovement());
   98 |         this.applyEffectsFromBlocks();
   99 |         this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
  100 |         if (this.onGround()) {
  101 |             this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
  102 |         }
  103 | 
  104 |         int fuse = this.getFuse() - 1;
  105 |         this.setFuse(fuse);
  106 |         if (fuse <= 0) {
  107 |             this.discard();
  108 |             if (!this.level().isClientSide()) {
  109 |                 this.explode();
  110 |             }
  111 |         } else {
  112 |             this.updateFluidInteraction();
  113 |             if (this.level().isClientSide()) {
  114 |                 this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
  115 |             }
  116 |         }
  117 |     }
```

原文件 L119–L153：
```java
  119 |     protected void explode() {
  120 |         if (this.level() instanceof ServerLevel level && level.getGameRules().get(GameRules.TNT_EXPLODES)) {
  121 |             this.level()
  122 |                 .explode(
  123 |                     this,
  124 |                     Explosion.getDefaultDamageSource(this.level(), this),
  125 |                     this.usedPortal ? USED_PORTAL_DAMAGE_CALCULATOR : null,
  126 |                     this.getX(),
  127 |                     this.getY(0.0625),
  128 |                     this.getZ(),
  129 |                     this.explosionPower,
  130 |                     false,
  131 |                     Level.ExplosionInteraction.TNT
  132 |                 );
  133 |         }
  134 |     }
  135 | 
  136 |     @Override
  137 |     protected void addAdditionalSaveData(ValueOutput output) {
  138 |         output.putShort("fuse", (short)this.getFuse());
  139 |         output.store("block_state", BlockState.CODEC, this.getBlockState());
  140 |         if (this.explosionPower != 4.0F) {
  141 |             output.putFloat("explosion_power", this.explosionPower);
  142 |         }
  143 | 
  144 |         EntityReference.store(this.owner, output, "owner");
  145 |     }
  146 | 
  147 |     @Override
  148 |     protected void readAdditionalSaveData(ValueInput input) {
  149 |         this.setFuse(input.getShortOr("fuse", (short)80));
  150 |         this.setBlockState(input.read("block_state", BlockState.CODEC).orElse(DEFAULT_BLOCK_STATE));
  151 |         this.explosionPower = Mth.clamp(input.getFloatOr("explosion_power", 4.0F), 0.0F, 128.0F);
  152 |         this.owner = EntityReference.read(input, "owner");
  153 |     }
```

<a id="s05"></a>
## S05 — 中继器与其他红石计划更新

### `net/minecraft/world/level/block/DiodeBlock.java`

来源：.109 参考源码；SHA-256：`8a3549cfe63e4cbc3bf47fe7626ff3c0ee38cd6ce2b7b52c4554419e3f5360ec`

原文件 L25–L204：
```java
   25 | import org.jspecify.annotations.Nullable;
   26 | 
   27 | public abstract class DiodeBlock extends HorizontalDirectionalBlock {
   28 |     public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
   29 |     private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 2.0);
   30 | 
   31 |     public DiodeBlock(BlockBehaviour.Properties properties) {
   32 |         super(properties);
   33 |     }
   34 | 
   35 |     @Override
   36 |     protected abstract MapCodec<? extends DiodeBlock> codec();
   37 | 
   38 |     @Override
   39 |     protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
   40 |         return SHAPE;
   41 |     }
   42 | 
   43 |     @Override
   44 |     protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
   45 |         BlockPos belowPos = pos.below();
   46 |         return this.canSurviveOn(level, belowPos, level.getBlockState(belowPos));
   47 |     }
   48 | 
   49 |     protected boolean canSurviveOn(LevelReader level, BlockPos neightborPos, BlockState neighborState) {
   50 |         return neighborState.isFaceSturdy(level, neightborPos, Direction.UP, SupportType.RIGID);
   51 |     }
   52 | 
   53 |     @Override
   54 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   55 |         if (!this.isLocked(level, pos, state)) {
   56 |             boolean on = state.getValue(POWERED);
   57 |             boolean shouldTurnOn = this.shouldTurnOn(level, pos, state);
   58 |             if (on && !shouldTurnOn) {
   59 |                 level.setBlock(pos, state.setValue(POWERED, false), 2);
   60 |             } else if (!on) {
   61 |                 level.setBlock(pos, state.setValue(POWERED, true), 2);
   62 |                 if (!shouldTurnOn) {
   63 |                     level.scheduleTick(pos, this, this.getDelay(state), TickPriority.VERY_HIGH);
   64 |                 }
   65 |             }
   66 |         }
   67 |     }
   68 | 
   69 |     @Override
   70 |     protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
   71 |         return state.getSignal(level, pos, direction);
   72 |     }
   73 | 
   74 |     @Override
   75 |     protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
   76 |         if (!state.getValue(POWERED)) {
   77 |             return 0;
   78 |         } else {
   79 |             return state.getValue(FACING) == direction ? this.getOutputSignal(level, pos, state) : 0;
   80 |         }
   81 |     }
   82 | 
   83 |     @Override
   84 |     protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
   85 |         if (state.canSurvive(level, pos)) {
   86 |             this.checkTickOnNeighbor(level, pos, state);
   87 |         } else {
   88 |             BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
   89 |             dropResources(state, level, pos, blockEntity);
   90 |             level.removeBlock(pos, false);
   91 | 
   92 |             for (Direction direction : Direction.values()) {
   93 |                 level.updateNeighborsAt(pos.relative(direction), this);
   94 |             }
   95 |         }
   96 |     }
   97 | 
   98 |     protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
   99 |         if (!this.isLocked(level, pos, state)) {
  100 |             boolean on = state.getValue(POWERED);
  101 |             boolean shouldTurnOn = this.shouldTurnOn(level, pos, state);
  102 |             if (on != shouldTurnOn && !level.getBlockTicks().willTickThisTick(pos, this)) {
  103 |                 TickPriority priority = TickPriority.HIGH;
  104 |                 if (this.shouldPrioritize(level, pos, state)) {
  105 |                     priority = TickPriority.EXTREMELY_HIGH;
  106 |                 } else if (on) {
  107 |                     priority = TickPriority.VERY_HIGH;
  108 |                 }
  109 | 
  110 |                 level.scheduleTick(pos, this, this.getDelay(state), priority);
  111 |             }
  112 |         }
  113 |     }
  114 | 
  115 |     public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
  116 |         return false;
  117 |     }
  118 | 
  119 |     protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
  120 |         return this.getInputSignal(level, pos, state) > 0;
  121 |     }
  122 | 
  123 |     protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
  124 |         Direction direction = state.getValue(FACING);
  125 |         BlockPos targetPos = pos.relative(direction);
  126 |         int input = level.getSignal(targetPos, direction);
  127 |         if (input >= 15) {
  128 |             return input;
  129 |         } else {
  130 |             BlockState targetBlockState = level.getBlockState(targetPos);
  131 |             return Math.max(input, targetBlockState.is(Blocks.REDSTONE_WIRE) ? targetBlockState.getValue(RedStoneWireBlock.POWER) : 0);
  132 |         }
  133 |     }
  134 | 
  135 |     protected int getAlternateSignal(SignalGetter level, BlockPos pos, BlockState state) {
  136 |         Direction direction = state.getValue(FACING);
  137 |         Direction clockWise = direction.getClockWise();
  138 |         Direction counterClockWise = direction.getCounterClockWise();
  139 |         boolean sideInputDiodesOnly = this.sideInputDiodesOnly();
  140 |         return Math.max(
  141 |             level.getControlInputSignal(pos.relative(clockWise), clockWise, sideInputDiodesOnly),
  142 |             level.getControlInputSignal(pos.relative(counterClockWise), counterClockWise, sideInputDiodesOnly)
  143 |         );
  144 |     }
  145 | 
  146 |     @Override
  147 |     protected boolean isSignalSource(BlockState state) {
  148 |         return true;
  149 |     }
  150 | 
  151 |     @Override
  152 |     public BlockState getStateForPlacement(BlockPlaceContext context) {
  153 |         return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
  154 |     }
  155 | 
  156 |     @Override
  157 |     public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
  158 |         if (this.shouldTurnOn(level, pos, state)) {
  159 |             level.scheduleTick(pos, this, 1);
  160 |         }
  161 |     }
  162 | 
  163 |     @Override
  164 |     protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
  165 |         this.updateNeighborsInFront(level, pos, state);
  166 |     }
  167 | 
  168 |     @Override
  169 |     protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
  170 |         if (!movedByPiston) {
  171 |             this.updateNeighborsInFront(level, pos, state);
  172 |         }
  173 |     }
  174 | 
  175 |     protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
  176 |         Direction direction = state.getValue(FACING);
  177 |         BlockPos oppositePos = pos.relative(direction.getOpposite());
  178 |         if (net.neoforged.neoforge.event.EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), java.util.EnumSet.of(direction.getOpposite()), false).isCanceled())
  179 |             return;
  180 |         Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, direction.getOpposite(), Direction.UP);
  181 |         level.neighborChanged(oppositePos, this, orientation);
  182 |         level.updateNeighborsAtExceptFromFacing(oppositePos, this, direction, orientation);
  183 |     }
  184 | 
  185 |     protected boolean sideInputDiodesOnly() {
  186 |         return false;
  187 |     }
  188 | 
  189 |     protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
  190 |         return 15;
  191 |     }
  192 | 
  193 |     public static boolean isDiode(BlockState state) {
  194 |         return state.getBlock() instanceof DiodeBlock;
  195 |     }
  196 | 
  197 |     public boolean shouldPrioritize(BlockGetter level, BlockPos pos, BlockState state) {
  198 |         Direction direction = state.getValue(FACING).getOpposite();
  199 |         BlockState oppositeState = level.getBlockState(pos.relative(direction));
  200 |         return isDiode(oppositeState) && oppositeState.getValue(FACING) != direction;
  201 |     }
  202 | 
  203 |     protected abstract int getDelay(BlockState state);
  204 | }
```

### `net/minecraft/world/level/block/RepeaterBlock.java`

来源：.109 参考源码；SHA-256：`d66c179d941f99871a48d4479b60fbb1b70cfca7c745c61a744ce9926415c19b`

原文件 L20–L113：
```java
   20 | import net.minecraft.world.phys.BlockHitResult;
   21 | 
   22 | public class RepeaterBlock extends DiodeBlock {
   23 |     public static final MapCodec<RepeaterBlock> CODEC = simpleCodec(RepeaterBlock::new);
   24 |     public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
   25 |     public static final IntegerProperty DELAY = BlockStateProperties.DELAY;
   26 | 
   27 |     @Override
   28 |     public MapCodec<RepeaterBlock> codec() {
   29 |         return CODEC;
   30 |     }
   31 | 
   32 |     public RepeaterBlock(BlockBehaviour.Properties properties) {
   33 |         super(properties);
   34 |         this.registerDefaultState(
   35 |             this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DELAY, 1).setValue(LOCKED, false).setValue(POWERED, false)
   36 |         );
   37 |     }
   38 | 
   39 |     @Override
   40 |     protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
   41 |         if (!player.getAbilities().mayBuild) {
   42 |             return InteractionResult.PASS;
   43 |         } else {
   44 |             level.setBlock(pos, state.cycle(DELAY), 3);
   45 |             return InteractionResult.SUCCESS;
   46 |         }
   47 |     }
   48 | 
   49 |     @Override
   50 |     protected int getDelay(BlockState state) {
   51 |         return state.getValue(DELAY) * 2;
   52 |     }
   53 | 
   54 |     @Override
   55 |     public BlockState getStateForPlacement(BlockPlaceContext context) {
   56 |         BlockState state = super.getStateForPlacement(context);
   57 |         return state.setValue(LOCKED, this.isLocked(context.getLevel(), context.getClickedPos(), state));
   58 |     }
   59 | 
   60 |     @Override
   61 |     protected BlockState updateShape(
   62 |         BlockState state,
   63 |         LevelReader level,
   64 |         ScheduledTickAccess ticks,
   65 |         BlockPos pos,
   66 |         Direction directionToNeighbour,
   67 |         BlockPos neighbourPos,
   68 |         BlockState neighbourState,
   69 |         RandomSource random
   70 |     ) {
   71 |         if (directionToNeighbour == Direction.DOWN && !this.canSurviveOn(level, neighbourPos, neighbourState)) {
   72 |             return Blocks.AIR.defaultBlockState();
   73 |         } else {
   74 |             return !level.isClientSide() && directionToNeighbour.getAxis() != state.getValue(FACING).getAxis()
   75 |                 ? state.setValue(LOCKED, this.isLocked(level, pos, state))
   76 |                 : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
   77 |         }
   78 |     }
   79 | 
   80 |     @Override
   81 |     public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
   82 |         return this.getAlternateSignal(level, pos, state) > 0;
   83 |     }
   84 | 
   85 |     @Override
   86 |     protected boolean sideInputDiodesOnly() {
   87 |         return true;
   88 |     }
   89 | 
   90 |     @Override
   91 |     public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
   92 |         if (state.getValue(POWERED)) {
   93 |             Direction direction = state.getValue(FACING);
   94 |             double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
   95 |             double y = pos.getY() + 0.4 + (random.nextDouble() - 0.5) * 0.2;
   96 |             double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
   97 |             float offset = -5.0F;
   98 |             if (random.nextBoolean()) {
   99 |                 offset = state.getValue(DELAY) * 2 - 1;
  100 |             }
  101 | 
  102 |             offset /= 16.0F;
  103 |             double xo = offset * direction.getStepX();
  104 |             double zo = offset * direction.getStepZ();
  105 |             level.addParticle(DustParticleOptions.REDSTONE, x + xo, y, z + zo, 0.0, 0.0, 0.0);
  106 |         }
  107 |     }
  108 | 
  109 |     @Override
  110 |     protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
  111 |         builder.add(FACING, DELAY, LOCKED, POWERED);
  112 |     }
  113 | }
```

### `net/minecraft/world/level/block/ObserverBlock.java`

来源：.109 参考源码；SHA-256：`35dc096752cc7996e282fc5dd00c8499b474898513e400a56265032cb23df9dc`

原文件 L45–L131：
```java
   45 |     @Override
   46 |     protected BlockState mirror(BlockState state, Mirror mirror) {
   47 |         return state.rotate(mirror.getRotation(state.getValue(FACING)));
   48 |     }
   49 | 
   50 |     @Override
   51 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   52 |         if (state.getValue(POWERED)) {
   53 |             level.setBlock(pos, state.setValue(POWERED, false), 2);
   54 |         } else {
   55 |             level.setBlock(pos, state.setValue(POWERED, true), 2);
   56 |             level.scheduleTick(pos, this, 2);
   57 |         }
   58 | 
   59 |         this.updateNeighborsInFront(level, pos, state);
   60 |     }
   61 | 
   62 |     @Override
   63 |     protected BlockState updateShape(
   64 |         BlockState state,
   65 |         LevelReader level,
   66 |         ScheduledTickAccess ticks,
   67 |         BlockPos pos,
   68 |         Direction directionToNeighbour,
   69 |         BlockPos neighbourPos,
   70 |         BlockState neighbourState,
   71 |         RandomSource random
   72 |     ) {
   73 |         if (state.getValue(FACING) == directionToNeighbour && !state.getValue(POWERED)) {
   74 |             this.startSignal(level, ticks, pos);
   75 |         }
   76 | 
   77 |         return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
   78 |     }
   79 | 
   80 |     private void startSignal(LevelReader level, ScheduledTickAccess ticks, BlockPos pos) {
   81 |         if (!level.isClientSide() && !ticks.getBlockTicks().hasScheduledTick(pos, this)) {
   82 |             ticks.scheduleTick(pos, this, 2);
   83 |         }
   84 |     }
   85 | 
   86 |     protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
   87 |         Direction direction = state.getValue(FACING);
   88 |         BlockPos oppositePos = pos.relative(direction.getOpposite());
   89 |         Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, direction.getOpposite(), null);
   90 |         level.neighborChanged(oppositePos, this, orientation);
   91 |         level.updateNeighborsAtExceptFromFacing(oppositePos, this, direction, orientation);
   92 |     }
   93 | 
   94 |     @Override
   95 |     protected boolean isSignalSource(BlockState state) {
   96 |         return true;
   97 |     }
   98 | 
   99 |     @Override
  100 |     protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
  101 |         return state.getSignal(level, pos, direction);
  102 |     }
  103 | 
  104 |     @Override
  105 |     protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
  106 |         return state.getValue(POWERED) && state.getValue(FACING) == direction ? 15 : 0;
  107 |     }
  108 | 
  109 |     @Override
  110 |     protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
  111 |         if (!state.is(oldState.getBlock())) {
  112 |             if (!level.isClientSide() && state.getValue(POWERED) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
  113 |                 BlockState newState = state.setValue(POWERED, false);
  114 |                 level.setBlock(pos, newState, 18);
  115 |                 this.updateNeighborsInFront(level, pos, newState);
  116 |             }
  117 |         }
  118 |     }
  119 | 
  120 |     @Override
  121 |     protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
  122 |         if (state.getValue(POWERED) && level.getBlockTicks().hasScheduledTick(pos, this)) {
  123 |             this.updateNeighborsInFront(level, pos, state.setValue(POWERED, false));
  124 |         }
  125 |     }
  126 | 
  127 |     @Override
  128 |     public BlockState getStateForPlacement(BlockPlaceContext context) {
  129 |         return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite().getOpposite());
  130 |     }
  131 | }
```

### `net/minecraft/world/level/block/RedstoneLampBlock.java`

来源：.109 参考源码；SHA-256：`db188318331f17e0fef4679539364a20035589284f655a2104ec4972b5fb0cc5`

原文件 L20–L60：
```java
   20 |     @Override
   21 |     public MapCodec<RedstoneLampBlock> codec() {
   22 |         return CODEC;
   23 |     }
   24 | 
   25 |     public RedstoneLampBlock(BlockBehaviour.Properties properties) {
   26 |         super(properties);
   27 |         this.registerDefaultState(this.defaultBlockState().setValue(LIT, false));
   28 |     }
   29 | 
   30 |     @Override
   31 |     public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
   32 |         return this.defaultBlockState().setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
   33 |     }
   34 | 
   35 |     @Override
   36 |     protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
   37 |         if (!level.isClientSide()) {
   38 |             boolean isLit = state.getValue(LIT);
   39 |             if (isLit != level.hasNeighborSignal(pos)) {
   40 |                 if (isLit) {
   41 |                     level.scheduleTick(pos, this, 4);
   42 |                 } else {
   43 |                     level.setBlock(pos, state.cycle(LIT), 2);
   44 |                 }
   45 |             }
   46 |         }
   47 |     }
   48 | 
   49 |     @Override
   50 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   51 |         if (state.getValue(LIT) && !level.hasNeighborSignal(pos)) {
   52 |             level.setBlock(pos, state.cycle(LIT), 2);
   53 |         }
   54 |     }
   55 | 
   56 |     @Override
   57 |     protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
   58 |         builder.add(LIT);
   59 |     }
   60 | }
```

### `net/minecraft/world/level/block/DispenserBlock.java`

来源：.109 参考源码；SHA-256：`672bf4255a8773a5e007f79de95ed54b646d0bd660bf511a07af05833d2081bb`

原文件 L120–L189：
```java
  120 |         }
  121 |     }
  122 | 
  123 |     @Override
  124 |     protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
  125 |         boolean shouldTrigger = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
  126 |         boolean isTriggered = state.getValue(TRIGGERED);
  127 |         if (shouldTrigger && !isTriggered) {
  128 |             level.scheduleTick(pos, this, 4);
  129 |             level.setBlock(pos, state.setValue(TRIGGERED, true), 2);
  130 |         } else if (!shouldTrigger && isTriggered) {
  131 |             level.setBlock(pos, state.setValue(TRIGGERED, false), 2);
  132 |         }
  133 |     }
  134 | 
  135 |     @Override
  136 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
  137 |         this.dispenseFrom(level, state, pos);
  138 |     }
  139 | 
  140 |     @Override
  141 |     public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
  142 |         return new DispenserBlockEntity(worldPosition, blockState);
  143 |     }
  144 | 
  145 |     @Override
  146 |     public BlockState getStateForPlacement(BlockPlaceContext context) {
  147 |         return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
  148 |     }
  149 | 
  150 |     @Override
  151 |     protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
  152 |         Containers.updateNeighboursAfterDestroy(state, level, pos);
  153 |     }
  154 | 
  155 |     public static Position getDispensePosition(BlockSource source) {
  156 |         return getDispensePosition(source, 0.7, Vec3.ZERO);
  157 |     }
  158 | 
  159 |     public static Position getDispensePosition(BlockSource source, double scale, Vec3 offset) {
  160 |         Direction direction = source.state().getValue(FACING);
  161 |         return source.center()
  162 |             .add(scale * direction.getStepX() + offset.x(), scale * direction.getStepY() + offset.y(), scale * direction.getStepZ() + offset.z());
  163 |     }
  164 | 
  165 |     @Override
  166 |     protected boolean hasAnalogOutputSignal(BlockState state) {
  167 |         return true;
  168 |     }
  169 | 
  170 |     @Override
  171 |     protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
  172 |         return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
  173 |     }
  174 | 
  175 |     @Override
  176 |     protected BlockState rotate(BlockState state, Rotation rotation) {
  177 |         return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
  178 |     }
  179 | 
  180 |     @Override
  181 |     protected BlockState mirror(BlockState state, Mirror mirror) {
  182 |         return state.rotate(mirror.getRotation(state.getValue(FACING)));
  183 |     }
  184 | 
  185 |     @Override
  186 |     protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
  187 |         builder.add(FACING, TRIGGERED);
  188 |     }
  189 | }
```

<a id="s06"></a>
## S06 — 大型垂滴叶与普通荷叶：不可混称

### `net/minecraft/world/level/block/BigDripleafBlock.java`

来源：.109 参考源码；SHA-256：`327b67fa7b1db1e77e6e8088c2273d15e29a8578820550e89a22b1bfa080a98c`

原文件 L45–L69：
```java
   45 | public class BigDripleafBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, BonemealableBlock {
   46 |     public static final MapCodec<BigDripleafBlock> CODEC = simpleCodec(BigDripleafBlock::new);
   47 |     private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
   48 |     private static final EnumProperty<Tilt> TILT = BlockStateProperties.TILT;
   49 |     private static final int NO_TICK = -1;
   50 |     private static final Object2IntMap<Tilt> DELAY_UNTIL_NEXT_TILT_STATE = Util.make(new Object2IntArrayMap<>(), map -> {
   51 |         map.defaultReturnValue(-1);
   52 |         map.put(Tilt.UNSTABLE, 10);
   53 |         map.put(Tilt.PARTIAL, 10);
   54 |         map.put(Tilt.FULL, 100);
   55 |     });
   56 |     private static final int MAX_GEN_HEIGHT = 5;
   57 |     private static final int ENTITY_DETECTION_MIN_Y = 11;
   58 |     private static final int LOWEST_LEAF_TOP = 13;
   59 |     private static final Map<Tilt, VoxelShape> SHAPE_LEAF = Maps.newEnumMap(
   60 |         Map.of(
   61 |             Tilt.NONE,
   62 |             Block.column(16.0, 11.0, 15.0),
   63 |             Tilt.UNSTABLE,
   64 |             Block.column(16.0, 11.0, 15.0),
   65 |             Tilt.PARTIAL,
   66 |             Block.column(16.0, 11.0, 13.0),
   67 |             Tilt.FULL,
   68 |             Shapes.empty()
   69 |         )
```

原文件 L128–L131：
```java
  128 |     @Override
  129 |     protected void onProjectileHit(Level level, BlockState state, BlockHitResult blockHit, Projectile projectile) {
  130 |         this.setTiltAndScheduleTick(state, level, blockHit.getBlockPos(), Tilt.FULL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN);
  131 |     }
```

原文件 L191–L261：
```java
  191 |     @Override
  192 |     protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
  193 |         if (!level.isClientSide()) {
  194 |             if (state.getValue(TILT) == Tilt.NONE && canEntityTilt(pos, entity) && !level.hasNeighborSignal(pos)) {
  195 |                 this.setTiltAndScheduleTick(state, level, pos, Tilt.UNSTABLE, null);
  196 |             }
  197 |         }
  198 |     }
  199 | 
  200 |     @Override
  201 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
  202 |         if (level.hasNeighborSignal(pos)) {
  203 |             resetTilt(state, level, pos);
  204 |         } else {
  205 |             Tilt tilt = state.getValue(TILT);
  206 |             if (tilt == Tilt.UNSTABLE) {
  207 |                 this.setTiltAndScheduleTick(state, level, pos, Tilt.PARTIAL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN);
  208 |             } else if (tilt == Tilt.PARTIAL) {
  209 |                 this.setTiltAndScheduleTick(state, level, pos, Tilt.FULL, SoundEvents.BIG_DRIPLEAF_TILT_DOWN);
  210 |             } else if (tilt == Tilt.FULL) {
  211 |                 resetTilt(state, level, pos);
  212 |             }
  213 |         }
  214 |     }
  215 | 
  216 |     @Override
  217 |     protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
  218 |         if (level.hasNeighborSignal(pos)) {
  219 |             resetTilt(state, level, pos);
  220 |         }
  221 |     }
  222 | 
  223 |     private static void playTiltSound(Level level, BlockPos pos, SoundEvent tiltSound) {
  224 |         float pitch = Mth.randomBetween(level.getRandom(), 0.8F, 1.2F);
  225 |         level.playSound(null, pos, tiltSound, SoundSource.BLOCKS, 1.0F, pitch);
  226 |     }
  227 | 
  228 |     private static boolean canEntityTilt(BlockPos pos, Entity entity) {
  229 |         return entity.onGround() && entity.position().y > pos.getY() + 0.6875F;
  230 |     }
  231 | 
  232 |     private void setTiltAndScheduleTick(BlockState state, Level level, BlockPos pos, Tilt tilt, @Nullable SoundEvent sound) {
  233 |         setTilt(state, level, pos, tilt);
  234 |         if (sound != null) {
  235 |             playTiltSound(level, pos, sound);
  236 |         }
  237 | 
  238 |         int tickDelay = DELAY_UNTIL_NEXT_TILT_STATE.getInt(tilt);
  239 |         if (tickDelay != -1) {
  240 |             level.scheduleTick(pos, this, tickDelay);
  241 |         }
  242 |     }
  243 | 
  244 |     private static void resetTilt(BlockState state, Level level, BlockPos pos) {
  245 |         setTilt(state, level, pos, Tilt.NONE);
  246 |         if (state.getValue(TILT) != Tilt.NONE) {
  247 |             playTiltSound(level, pos, SoundEvents.BIG_DRIPLEAF_TILT_UP);
  248 |         }
  249 |     }
  250 | 
  251 |     private static void setTilt(BlockState state, Level level, BlockPos pos, Tilt tilt) {
  252 |         Tilt previousTilt = state.getValue(TILT);
  253 |         level.setBlock(pos, state.setValue(TILT, tilt), 2);
  254 |         if (tilt.causesVibration() && tilt != previousTilt) {
  255 |             level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
  256 |         }
  257 |     }
  258 | 
  259 |     @Override
  260 |     protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
  261 |         return SHAPE_LEAF.get(state.getValue(TILT));
```

### `net/minecraft/world/level/block/LilyPadBlock.java`

来源：.109 参考源码；SHA-256：`e6df11f71509b376524db85bda4bfc67ea0024d4e329fd362ee36a55346c60a6`

原文件 L1–L52：
```java
    1 | package net.minecraft.world.level.block;
    2 | 
    3 | import com.mojang.serialization.MapCodec;
    4 | import net.minecraft.core.BlockPos;
    5 | import net.minecraft.server.level.ServerLevel;
    6 | import net.minecraft.tags.BlockTags;
    7 | import net.minecraft.tags.FluidTags;
    8 | import net.minecraft.world.entity.Entity;
    9 | import net.minecraft.world.entity.InsideBlockEffectApplier;
   10 | import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
   11 | import net.minecraft.world.level.BlockGetter;
   12 | import net.minecraft.world.level.Level;
   13 | import net.minecraft.world.level.block.state.BlockBehaviour;
   14 | import net.minecraft.world.level.block.state.BlockState;
   15 | import net.minecraft.world.level.material.FluidState;
   16 | import net.minecraft.world.level.material.Fluids;
   17 | import net.minecraft.world.phys.shapes.CollisionContext;
   18 | import net.minecraft.world.phys.shapes.VoxelShape;
   19 | 
   20 | public class LilyPadBlock extends VegetationBlock {
   21 |     public static final MapCodec<LilyPadBlock> CODEC = simpleCodec(LilyPadBlock::new);
   22 |     private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 1.5);
   23 | 
   24 |     @Override
   25 |     public MapCodec<LilyPadBlock> codec() {
   26 |         return CODEC;
   27 |     }
   28 | 
   29 |     public LilyPadBlock(BlockBehaviour.Properties properties) {
   30 |         super(properties);
   31 |     }
   32 | 
   33 |     @Override
   34 |     protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
   35 |         super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
   36 |         if (level instanceof ServerLevel && entity instanceof AbstractBoat) {
   37 |             level.destroyBlock(new BlockPos(pos), true, entity);
   38 |         }
   39 |     }
   40 | 
   41 |     @Override
   42 |     protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
   43 |         return SHAPE;
   44 |     }
   45 | 
   46 |     @Override
   47 |     protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
   48 |         FluidState fluidState = level.getFluidState(pos);
   49 |         FluidState fluidAbove = level.getFluidState(pos.above());
   50 |         return (fluidState.is(FluidTags.SUPPORTS_LILY_PAD) || state.is(BlockTags.SUPPORTS_LILY_PAD)) && fluidAbove.is(Fluids.EMPTY);
   51 |     }
   52 | }
```

### `net/minecraft/world/level/block/VegetationBlock.java`

来源：.109 参考源码；SHA-256：`a6da89abc631258208ddc4f491a266a318adf2c62a048e25954024315cca8e82`

原文件 L20–L61：
```java
   20 |     @Override
   21 |     protected abstract MapCodec<? extends VegetationBlock> codec();
   22 | 
   23 |     protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
   24 |         return state.is(BlockTags.SUPPORTS_VEGETATION);
   25 |     }
   26 | 
   27 |     @Override
   28 |     protected BlockState updateShape(
   29 |         BlockState state,
   30 |         LevelReader level,
   31 |         ScheduledTickAccess ticks,
   32 |         BlockPos pos,
   33 |         Direction directionToNeighbour,
   34 |         BlockPos neighbourPos,
   35 |         BlockState neighbourState,
   36 |         RandomSource random
   37 |     ) {
   38 |         return !state.canSurvive(level, pos)
   39 |             ? Blocks.AIR.defaultBlockState()
   40 |             : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
   41 |     }
   42 | 
   43 |     @Override
   44 |     protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
   45 |         BlockPos below = pos.below();
   46 |         BlockState belowBlockState = level.getBlockState(below);
   47 |         var soilDecision = belowBlockState.canSustainPlant(level, below, Direction.UP, state);
   48 |         if (!soilDecision.isDefault()) return soilDecision.isTrue();
   49 |         return this.mayPlaceOn(belowBlockState, level, below);
   50 |     }
   51 | 
   52 |     @Override
   53 |     protected boolean propagatesSkylightDown(BlockState state) {
   54 |         return state.getFluidState().isEmpty();
   55 |     }
   56 | 
   57 |     @Override
   58 |     protected boolean isPathfindable(BlockState state, PathComputationType type) {
   59 |         return type == PathComputationType.AIR && !this.hasCollision ? true : super.isPathfindable(state, type);
   60 |     }
   61 | }
```

<a id="s07"></a>
## S07 — 漏斗自动转移与接触旁路

### `net/minecraft/world/level/block/entity/HopperBlockEntity.java`

来源：.109 参考源码；SHA-256：`e8982340766a3f3fd46048c3b897b5dbfdb862ecdbd6cfc50568a8abe11e5353`

原文件 L132–L164：
```java
  132 |     public static void pushItemsTick(Level level, BlockPos pos, BlockState state, HopperBlockEntity entity) {
  133 |         entity.cooldownTime--;
  134 |         entity.tickedGameTime = level.getGameTime();
  135 |         if (!entity.isOnCooldown()) {
  136 |             entity.setCooldown(0);
  137 |             tryMoveItems(level, pos, state, entity, () -> suckInItems(level, entity));
  138 |         }
  139 |     }
  140 | 
  141 |     private static boolean tryMoveItems(Level level, BlockPos pos, BlockState state, HopperBlockEntity entity, BooleanSupplier action) {
  142 |         if (level.isClientSide()) {
  143 |             return false;
  144 |         } else {
  145 |             if (!entity.isOnCooldown() && state.getValue(HopperBlock.ENABLED)) {
  146 |                 boolean changed = false;
  147 |                 if (!entity.isEmpty()) {
  148 |                     changed = ejectItems(level, pos, entity);
  149 |                 }
  150 | 
  151 |                 if (!entity.inventoryFull()) {
  152 |                     changed |= action.getAsBoolean();
  153 |                 }
  154 | 
  155 |                 if (changed) {
  156 |                     entity.setCooldown(8);
  157 |                     setChanged(level, pos, state);
  158 |                     return true;
  159 |                 }
  160 |             }
  161 | 
  162 |             return false;
  163 |         }
  164 |     }
```

原文件 L503–L509：
```java
  503 |     public static void entityInside(Level level, BlockPos pos, BlockState blockState, Entity entity, HopperBlockEntity hopper) {
  504 |         if (entity instanceof ItemEntity itemEntity
  505 |             && !itemEntity.getItem().isEmpty()
  506 |             && entity.getBoundingBox().move(-pos.getX(), -pos.getY(), -pos.getZ()).intersects(hopper.getSuckAabb())) {
  507 |             tryMoveItems(level, pos, blockState, hopper, () -> addItem(hopper, itemEntity));
  508 |         }
  509 |     }
```

### `net/minecraft/world/level/block/HopperBlock.java`

来源：.109 参考源码；SHA-256：`8cd8c3807024fb19e74b7f8e38f0f84668e85a0fba01439036b56ab733ce072f`

原文件 L1–L169：
```java
    1 | package net.minecraft.world.level.block;
    2 | 
    3 | import com.google.common.collect.ImmutableMap;
    4 | import com.mojang.serialization.MapCodec;
    5 | import java.util.Map;
    6 | import java.util.function.Function;
    7 | import net.minecraft.core.BlockPos;
    8 | import net.minecraft.core.Direction;
    9 | import net.minecraft.server.level.ServerLevel;
   10 | import net.minecraft.stats.Stats;
   11 | import net.minecraft.world.Containers;
   12 | import net.minecraft.world.InteractionResult;
   13 | import net.minecraft.world.entity.Entity;
   14 | import net.minecraft.world.entity.InsideBlockEffectApplier;
   15 | import net.minecraft.world.entity.player.Player;
   16 | import net.minecraft.world.inventory.AbstractContainerMenu;
   17 | import net.minecraft.world.item.context.BlockPlaceContext;
   18 | import net.minecraft.world.level.BlockGetter;
   19 | import net.minecraft.world.level.Level;
   20 | import net.minecraft.world.level.block.entity.BlockEntity;
   21 | import net.minecraft.world.level.block.entity.BlockEntityTicker;
   22 | import net.minecraft.world.level.block.entity.BlockEntityType;
   23 | import net.minecraft.world.level.block.entity.HopperBlockEntity;
   24 | import net.minecraft.world.level.block.state.BlockBehaviour;
   25 | import net.minecraft.world.level.block.state.BlockState;
   26 | import net.minecraft.world.level.block.state.StateDefinition;
   27 | import net.minecraft.world.level.block.state.properties.BlockStateProperties;
   28 | import net.minecraft.world.level.block.state.properties.BooleanProperty;
   29 | import net.minecraft.world.level.block.state.properties.EnumProperty;
   30 | import net.minecraft.world.level.pathfinder.PathComputationType;
   31 | import net.minecraft.world.level.redstone.Orientation;
   32 | import net.minecraft.world.phys.BlockHitResult;
   33 | import net.minecraft.world.phys.Vec3;
   34 | import net.minecraft.world.phys.shapes.BooleanOp;
   35 | import net.minecraft.world.phys.shapes.CollisionContext;
   36 | import net.minecraft.world.phys.shapes.Shapes;
   37 | import net.minecraft.world.phys.shapes.VoxelShape;
   38 | import org.jspecify.annotations.Nullable;
   39 | 
   40 | public class HopperBlock extends BaseEntityBlock {
   41 |     public static final MapCodec<HopperBlock> CODEC = simpleCodec(HopperBlock::new);
   42 |     public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING_HOPPER;
   43 |     public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
   44 |     private final Function<BlockState, VoxelShape> shapes;
   45 |     private final Map<Direction, VoxelShape> interactionShapes;
   46 | 
   47 |     @Override
   48 |     public MapCodec<HopperBlock> codec() {
   49 |         return CODEC;
   50 |     }
   51 | 
   52 |     public HopperBlock(BlockBehaviour.Properties properties) {
   53 |         super(properties);
   54 |         this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(ENABLED, true));
   55 |         VoxelShape inside = Block.column(12.0, 11.0, 16.0);
   56 |         this.shapes = this.makeShapes(inside);
   57 |         this.interactionShapes = ImmutableMap.<Direction, VoxelShape>builderWithExpectedSize(5)
   58 |             .putAll(Shapes.rotateHorizontal(Shapes.or(inside, Block.boxZ(4.0, 8.0, 10.0, 0.0, 4.0))))
   59 |             .put(Direction.DOWN, inside)
   60 |             .build();
   61 |     }
   62 | 
   63 |     private Function<BlockState, VoxelShape> makeShapes(VoxelShape inside) {
   64 |         VoxelShape spoutlessHopperOutline = Shapes.or(Block.column(16.0, 10.0, 16.0), Block.column(8.0, 4.0, 10.0));
   65 |         VoxelShape spoutlessHopper = Shapes.join(spoutlessHopperOutline, inside, BooleanOp.ONLY_FIRST);
   66 |         Map<Direction, VoxelShape> spouts = Shapes.rotateAll(Block.boxZ(4.0, 4.0, 8.0, 0.0, 8.0), new Vec3(8.0, 6.0, 8.0).scale(0.0625));
   67 |         return this.getShapeForEachState(
   68 |             state -> Shapes.or(spoutlessHopper, Shapes.join(spouts.get(state.getValue(FACING)), Shapes.block(), BooleanOp.AND)), ENABLED
   69 |         );
   70 |     }
   71 | 
   72 |     @Override
   73 |     protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
   74 |         return this.shapes.apply(state);
   75 |     }
   76 | 
   77 |     @Override
   78 |     protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
   79 |         return this.interactionShapes.get(state.getValue(FACING));
   80 |     }
   81 | 
   82 |     @Override
   83 |     public BlockState getStateForPlacement(BlockPlaceContext context) {
   84 |         Direction direction = context.getClickedFace().getOpposite();
   85 |         return this.defaultBlockState().setValue(FACING, direction.getAxis() == Direction.Axis.Y ? Direction.DOWN : direction).setValue(ENABLED, true);
   86 |     }
   87 | 
   88 |     @Override
   89 |     public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
   90 |         return new HopperBlockEntity(worldPosition, blockState);
   91 |     }
   92 | 
   93 |     @Override
   94 |     public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
   95 |         return level.isClientSide() ? null : createTickerHelper(type, BlockEntityType.HOPPER, HopperBlockEntity::pushItemsTick);
   96 |     }
   97 | 
   98 |     @Override
   99 |     protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
  100 |         if (!oldState.is(state.getBlock())) {
  101 |             this.checkPoweredState(level, pos, state);
  102 |         }
  103 |     }
  104 | 
  105 |     @Override
  106 |     protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
  107 |         if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HopperBlockEntity hopper) {
  108 |             player.openMenu(hopper);
  109 |             player.awardStat(Stats.INSPECT_HOPPER);
  110 |         }
  111 | 
  112 |         return InteractionResult.SUCCESS;
  113 |     }
  114 | 
  115 |     @Override
  116 |     protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
  117 |         this.checkPoweredState(level, pos, state);
  118 |     }
  119 | 
  120 |     private void checkPoweredState(Level level, BlockPos pos, BlockState state) {
  121 |         boolean shouldBeOn = !level.hasNeighborSignal(pos);
  122 |         if (shouldBeOn != state.getValue(ENABLED)) {
  123 |             level.setBlock(pos, state.setValue(ENABLED, shouldBeOn), 2);
  124 |         }
  125 |     }
  126 | 
  127 |     @Override
  128 |     protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
  129 |         Containers.updateNeighboursAfterDestroy(state, level, pos);
  130 |     }
  131 | 
  132 |     @Override
  133 |     protected boolean hasAnalogOutputSignal(BlockState state) {
  134 |         return true;
  135 |     }
  136 | 
  137 |     @Override
  138 |     protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
  139 |         return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
  140 |     }
  141 | 
  142 |     @Override
  143 |     protected BlockState rotate(BlockState state, Rotation rotation) {
  144 |         return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
  145 |     }
  146 | 
  147 |     @Override
  148 |     protected BlockState mirror(BlockState state, Mirror mirror) {
  149 |         return state.rotate(mirror.getRotation(state.getValue(FACING)));
  150 |     }
  151 | 
  152 |     @Override
  153 |     protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
  154 |         builder.add(FACING, ENABLED);
  155 |     }
  156 | 
  157 |     @Override
  158 |     protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
  159 |         BlockEntity blockEntity = level.getBlockEntity(pos);
  160 |         if (blockEntity instanceof HopperBlockEntity) {
  161 |             HopperBlockEntity.entityInside(level, pos, state, entity, (HopperBlockEntity)blockEntity);
  162 |         }
  163 |     }
  164 | 
  165 |     @Override
  166 |     protected boolean isPathfindable(BlockState state, PathComputationType type) {
  167 |         return false;
  168 |     }
  169 | }
```

<a id="s08"></a>
## S08 — 水流、沙子与火的自主后续调度

### `net/minecraft/world/level/material/FlowingFluid.java`

来源：.109 参考源码；SHA-256：`18b08c235f8f84909ed72ed3db31fb9b5a5cb0561099c91f8efe885779e8d6e0`

原文件 L436–L453：
```java
  436 |     public void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState) {
  437 |         if (!fluidState.isSource()) {
  438 |             FluidState newFluidState = this.getNewLiquid(level, pos, level.getBlockState(pos));
  439 |             int tickDelay = this.getSpreadDelay(level, pos, fluidState, newFluidState);
  440 |             if (newFluidState.isEmpty()) {
  441 |                 fluidState = newFluidState;
  442 |                 blockState = Blocks.AIR.defaultBlockState();
  443 |                 level.setBlock(pos, blockState, 3);
  444 |             } else if (newFluidState != fluidState) {
  445 |                 fluidState = newFluidState;
  446 |                 blockState = newFluidState.createLegacyBlock();
  447 |                 level.setBlock(pos, blockState, 3);
  448 |                 level.scheduleTick(pos, newFluidState.getType(), tickDelay);
  449 |             }
  450 |         }
  451 | 
  452 |         this.spread(level, pos, blockState, fluidState);
  453 |     }
```

### `net/minecraft/world/level/block/FallingBlock.java`

来源：.109 参考源码；SHA-256：`639bf71c7259860311ae5151e7320dc8900e3c52a6a31661e246e3899629ac60`

原文件 L20–L78：
```java
   20 | public abstract class FallingBlock extends Block implements Fallable {
   21 |     public FallingBlock(BlockBehaviour.Properties properties) {
   22 |         super(properties);
   23 |     }
   24 | 
   25 |     @Override
   26 |     protected abstract MapCodec<? extends FallingBlock> codec();
   27 | 
   28 |     @Override
   29 |     protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
   30 |         level.scheduleTick(pos, this, this.getDelayAfterPlace());
   31 |     }
   32 | 
   33 |     @Override
   34 |     protected BlockState updateShape(
   35 |         BlockState state,
   36 |         LevelReader level,
   37 |         ScheduledTickAccess ticks,
   38 |         BlockPos pos,
   39 |         Direction directionToNeighbour,
   40 |         BlockPos neighbourPos,
   41 |         BlockState neighbourState,
   42 |         RandomSource random
   43 |     ) {
   44 |         ticks.scheduleTick(pos, this, this.getDelayAfterPlace());
   45 |         return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
   46 |     }
   47 | 
   48 |     @Override
   49 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   50 |         if (isFree(level.getBlockState(pos.below())) && pos.getY() >= level.getMinY()) {
   51 |             FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, state);
   52 |             this.falling(entity);
   53 |         }
   54 |     }
   55 | 
   56 |     protected void falling(FallingBlockEntity entity) {
   57 |     }
   58 | 
   59 |     protected int getDelayAfterPlace() {
   60 |         return 2;
   61 |     }
   62 | 
   63 |     public static boolean isFree(BlockState state) {
   64 |         return state.isAir() || state.is(BlockTags.FIRE) || state.liquid() || state.canBeReplaced();
   65 |     }
   66 | 
   67 |     @Override
   68 |     public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
   69 |         if (random.nextInt(16) == 0) {
   70 |             BlockPos below = pos.below();
   71 |             if (isFree(level.getBlockState(below))) {
   72 |                 ParticleUtils.spawnParticleBelow(level, pos, random, new BlockParticleOption(ParticleTypes.FALLING_DUST, state));
   73 |             }
   74 |         }
   75 |     }
   76 | 
   77 |     public abstract int getDustColor(BlockState blockState, BlockGetter level, BlockPos pos);
   78 | }
```

### `net/minecraft/world/level/block/FireBlock.java`

来源：.109 参考源码；SHA-256：`2501d300dff918681167cd9afb537a36b01176d1a28016ae0e873fdf4a2860bf`

原文件 L135–L285：
```java
  135 |     protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
  136 |         BlockPos below = pos.below();
  137 |         return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || this.isValidFireLocation(level, pos);
  138 |     }
  139 | 
  140 |     @Override
  141 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
  142 |         level.scheduleTick(pos, this, getFireTickDelay(level.getRandom()));
  143 |         if (level.canSpreadFireAround(pos)) {
  144 |             if (!state.canSurvive(level, pos)) {
  145 |                 level.removeBlock(pos, false);
  146 |             }
  147 | 
  148 |             BlockState belowState = level.getBlockState(pos.below());
  149 |             boolean infiniBurn = belowState.isFireSource(level, pos.below(), Direction.UP);
  150 |             int age = state.getValue(AGE);
  151 |             if (!infiniBurn && level.isRaining() && this.isNearRain(level, pos) && random.nextFloat() < 0.2F + age * 0.03F) {
  152 |                 level.removeBlock(pos, false);
  153 |             } else {
  154 |                 int newAge = Math.min(15, age + random.nextInt(3) / 2);
  155 |                 if (age != newAge) {
  156 |                     state = state.setValue(AGE, newAge);
  157 |                     level.setBlock(pos, state, 260);
  158 |                 }
  159 | 
  160 |                 if (!infiniBurn) {
  161 |                     if (!this.isValidFireLocation(level, pos)) {
  162 |                         BlockPos below = pos.below();
  163 |                         if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || age > 3) {
  164 |                             level.removeBlock(pos, false);
  165 |                         }
  166 | 
  167 |                         return;
  168 |                     }
  169 | 
  170 |                     if (age == 15 && random.nextInt(4) == 0 && !this.canCatchFire(level, pos.below(), Direction.UP)) {
  171 |                         level.removeBlock(pos, false);
  172 |                         return;
  173 |                     }
  174 |                 }
  175 | 
  176 |                 boolean increasedBurnout = level.environmentAttributes().getValue(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, pos);
  177 |                 int extra = increasedBurnout ? -50 : 0;
  178 |                 this.checkBurnOut(level, pos.east(), 300 + extra, random, age, Direction.WEST);
  179 |                 this.checkBurnOut(level, pos.west(), 300 + extra, random, age, Direction.EAST);
  180 |                 this.checkBurnOut(level, pos.below(), 250 + extra, random, age, Direction.UP);
  181 |                 this.checkBurnOut(level, pos.above(), 250 + extra, random, age, Direction.DOWN);
  182 |                 this.checkBurnOut(level, pos.north(), 300 + extra, random, age, Direction.SOUTH);
  183 |                 this.checkBurnOut(level, pos.south(), 300 + extra, random, age, Direction.NORTH);
  184 |                 BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();
  185 | 
  186 |                 for (int xx = -1; xx <= 1; xx++) {
  187 |                     for (int zz = -1; zz <= 1; zz++) {
  188 |                         for (int yy = -1; yy <= 4; yy++) {
  189 |                             if (xx != 0 || yy != 0 || zz != 0) {
  190 |                                 int rate = 100;
  191 |                                 if (yy > 1) {
  192 |                                     rate += (yy - 1) * 100;
  193 |                                 }
  194 | 
  195 |                                 testPos.setWithOffset(pos, xx, yy, zz);
  196 |                                 int igniteOdds = this.getIgniteOdds(level, testPos);
  197 |                                 if (igniteOdds > 0) {
  198 |                                     int odds = (igniteOdds + 40 + level.getDifficulty().getId() * 7) / (age + 30);
  199 |                                     if (increasedBurnout) {
  200 |                                         odds /= 2;
  201 |                                     }
  202 | 
  203 |                                     if (odds > 0 && random.nextInt(rate) <= odds && (!level.isRaining() || !this.isNearRain(level, testPos))) {
  204 |                                         int spreadAge = Math.min(15, age + random.nextInt(5) / 4);
  205 |                                         level.setBlock(testPos, this.getStateWithAge(level, testPos, spreadAge), 3);
  206 |                                     }
  207 |                                 }
  208 |                             }
  209 |                         }
  210 |                     }
  211 |                 }
  212 |             }
  213 |         }
  214 |     }
  215 | 
  216 |     protected boolean isNearRain(Level level, BlockPos testPos) {
  217 |         return level.isRainingAt(testPos)
  218 |             || level.isRainingAt(testPos.west())
  219 |             || level.isRainingAt(testPos.east())
  220 |             || level.isRainingAt(testPos.north())
  221 |             || level.isRainingAt(testPos.south());
  222 |     }
  223 | 
  224 |     @Deprecated //Forge: Use IForgeBlockState.getFlammability, Public for default implementation only.
  225 |     public int getBurnOdds(BlockState state) {
  226 |         return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
  227 |             ? 0
  228 |             : this.burnOdds.getInt(state.getBlock());
  229 |     }
  230 | 
  231 |     @Deprecated //Forge: Use IForgeBlockState.getFireSpreadSpeed
  232 |     public int getIgniteOdds(BlockState state) {
  233 |         return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
  234 |             ? 0
  235 |             : this.igniteOdds.getInt(state.getBlock());
  236 |     }
  237 | 
  238 |     private void checkBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face) {
  239 |         int odds = level.getBlockState(pos).getFlammability(level, pos, face);
  240 |         if (random.nextInt(chance) < odds) {
  241 |             BlockState oldState = level.getBlockState(pos);
  242 |             oldState.onCaughtFire(level, pos, face, null);
  243 | 
  244 |             if (random.nextInt(age + 10) < 5 && !level.isRainingAt(pos)) {
  245 |                 int newAge = Math.min(age + random.nextInt(5) / 4, 15);
  246 |                 level.setBlock(pos, this.getStateWithAge(level, pos, newAge), 3);
  247 |             } else {
  248 |                 level.removeBlock(pos, false);
  249 |             }
  250 |         }
  251 |     }
  252 | 
  253 |     private BlockState getStateWithAge(LevelReader level, BlockPos pos, int age) {
  254 |         BlockState stateForPlacement = getState(level, pos);
  255 |         return stateForPlacement.is(Blocks.FIRE) ? stateForPlacement.setValue(AGE, age) : stateForPlacement;
  256 |     }
  257 | 
  258 |     private boolean isValidFireLocation(BlockGetter level, BlockPos pos) {
  259 |         for (Direction direction : Direction.values()) {
  260 |             if (this.canCatchFire(level, pos.relative(direction), direction.getOpposite())) {
  261 |                 return true;
  262 |             }
  263 |         }
  264 | 
  265 |         return false;
  266 |     }
  267 | 
  268 |     private int getIgniteOdds(LevelReader level, BlockPos pos) {
  269 |         if (!level.isEmptyBlock(pos)) {
  270 |             return 0;
  271 |         } else {
  272 |             int odds = 0;
  273 | 
  274 |             for (Direction direction : Direction.values()) {
  275 |                 BlockState blockState = level.getBlockState(pos.relative(direction));
  276 |                 odds = Math.max(blockState.getFireSpreadSpeed(level, pos.relative(direction), direction.getOpposite()), odds);
  277 |             }
  278 | 
  279 |             return odds;
  280 |         }
  281 |     }
  282 | 
  283 |     @Override
  284 |     @Deprecated //Forge: Use canCatchFire with more context
  285 |     protected boolean canBurn(BlockState state) {
```

<a id="s09"></a>
## S09 — 生产/接触分离的方块实体

### `net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java`

来源：.109 参考源码；SHA-256：`1c7793bcdc404f2da371a8a443c73eb48cdeba9bfc0700c8d159407394fec8ad`

原文件 L150–L326：
```java
  150 |     public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity entity) {
  151 |         boolean changed = false;
  152 |         boolean isLit;
  153 |         boolean wasLit;
  154 |         if (entity.litTimeRemaining > 0) {
  155 |             wasLit = true;
  156 |             entity.litTimeRemaining--;
  157 |             isLit = entity.litTimeRemaining > 0;
  158 |         } else {
  159 |             wasLit = false;
  160 |             isLit = false;
  161 |         }
  162 | 
  163 |         ItemStack fuel = entity.items.get(1);
  164 |         ItemStack ingredient = entity.items.get(0);
  165 |         boolean hasIngredient = !ingredient.isEmpty();
  166 |         boolean hasFuel = !fuel.isEmpty();
  167 |         if (isLit || hasFuel && hasIngredient) {
  168 |             if (hasIngredient) {
  169 |                 SingleRecipeInput input = new SingleRecipeInput(ingredient);
  170 |                 RecipeHolder<? extends AbstractCookingRecipe> recipe = entity.quickCheck.getRecipeFor(input, level).orElse(null);
  171 |                 if (recipe != null) {
  172 |                     int maxStackSize = entity.getMaxStackSize();
  173 |                     ItemStack burnResult = recipe.value().assemble(input);
  174 |                     if (!burnResult.isEmpty() && canBurn(entity.items, maxStackSize, burnResult)) {
  175 |                         if (!isLit) {
  176 |                             int newLitTime = entity.getBurnDuration(level.fuelValues(), fuel);
  177 |                             entity.litTimeRemaining = newLitTime;
  178 |                             entity.litTotalTime = newLitTime;
  179 |                             if (newLitTime > 0) {
  180 |                                 consumeFuel(entity.items, fuel);
  181 |                                 isLit = true;
  182 |                                 changed = true;
  183 |                             }
  184 |                         }
  185 | 
  186 |                         if (isLit) {
  187 |                             entity.cookingTimer++;
  188 |                             if (entity.cookingTimer == entity.cookingTotalTime) {
  189 |                                 entity.cookingTimer = 0;
  190 |                                 entity.cookingTotalTime = recipe.value().cookingTime();
  191 |                                 burn(entity.items, ingredient, burnResult);
  192 |                                 entity.setRecipeUsed(recipe);
  193 |                                 changed = true;
  194 |                             }
  195 |                         } else {
  196 |                             entity.cookingTimer = 0;
  197 |                         }
  198 |                     } else {
  199 |                         entity.cookingTimer = 0;
  200 |                     }
  201 |                 }
  202 |             } else {
  203 |                 entity.cookingTimer = 0;
  204 |             }
  205 |         } else if (entity.cookingTimer > 0) {
  206 |             entity.cookingTimer = Mth.clamp(entity.cookingTimer - 2, 0, entity.cookingTotalTime);
  207 |         }
  208 | 
  209 |         if (wasLit != isLit) {
  210 |             changed = true;
  211 |             state = state.setValue(AbstractFurnaceBlock.LIT, isLit);
  212 |             level.setBlock(pos, state, 3);
  213 |         }
  214 | 
  215 |         if (changed) {
  216 |             setChanged(level, pos, state);
  217 |         }
  218 |     }
  219 | 
  220 |     private static void consumeFuel(NonNullList<ItemStack> items, ItemStack fuel) {
  221 |         Item fuelItem = fuel.getItem();
  222 |         // Neo: Query the crafting remainder prior to stack mutation (shrink) in order to grab the correct value
  223 |         ItemStackTemplate remainder = fuel.getCraftingRemainder();
  224 |         fuel.shrink(1);
  225 |         if (fuel.isEmpty()) {
  226 |             items.set(1, remainder != null ? remainder.create() : ItemStack.EMPTY);
  227 |         }
  228 |     }
  229 | 
  230 |     private static boolean canBurn(NonNullList<ItemStack> items, int maxStackSize, ItemStack burnResult) {
  231 |         ItemStack resultItemStack = items.get(2);
  232 |         if (resultItemStack.isEmpty()) {
  233 |             return true;
  234 |         } else if (!ItemStack.isSameItemSameComponents(resultItemStack, burnResult)) {
  235 |             return false;
  236 |         } else {
  237 |             int resultCount = resultItemStack.getCount() + burnResult.count();
  238 |             int maxResultCount = Math.min(maxStackSize, burnResult.getMaxStackSize());
  239 |             return resultCount <= maxResultCount;
  240 |         }
  241 |     }
  242 | 
  243 |     private static void burn(NonNullList<ItemStack> items, ItemStack inputItemStack, ItemStack result) {
  244 |         ItemStack resultItemStack = items.get(2);
  245 |         if (resultItemStack.isEmpty()) {
  246 |             items.set(2, result.copy());
  247 |         } else {
  248 |             resultItemStack.grow(result.getCount());
  249 |         }
  250 | 
  251 |         if (inputItemStack.is(Items.WET_SPONGE) && !items.get(1).isEmpty() && items.get(1).is(Items.BUCKET)) {
  252 |             items.set(1, new ItemStack(Items.WATER_BUCKET));
  253 |         }
  254 | 
  255 |         inputItemStack.shrink(1);
  256 |     }
  257 | 
  258 |     protected int getBurnDuration(FuelValues fuelValues, ItemStack itemStack) {
  259 |         return itemStack.getBurnTime(this.recipeType, fuelValues);
  260 |     }
  261 | 
  262 |     private static int getTotalCookTime(ServerLevel level, AbstractFurnaceBlockEntity entity) {
  263 |         SingleRecipeInput input = new SingleRecipeInput(entity.getItem(0));
  264 |         return entity.quickCheck.getRecipeFor(input, level).map(recipeHolder -> recipeHolder.value().cookingTime()).orElse(200);
  265 |     }
  266 | 
  267 |     @Override
  268 |     public int[] getSlotsForFace(Direction direction) {
  269 |         if (direction == Direction.DOWN) {
  270 |             return SLOTS_FOR_DOWN;
  271 |         } else {
  272 |             return direction == Direction.UP ? SLOTS_FOR_UP : SLOTS_FOR_SIDES;
  273 |         }
  274 |     }
  275 | 
  276 |     @Override
  277 |     public boolean canPlaceItemThroughFace(int slot, ItemStack itemStack, @Nullable Direction direction) {
  278 |         return this.canPlaceItem(slot, itemStack);
  279 |     }
  280 | 
  281 |     @Override
  282 |     public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
  283 |         return direction == Direction.DOWN && slot == 1 ? itemStack.is(Items.WATER_BUCKET) || itemStack.is(Items.BUCKET) : true;
  284 |     }
  285 | 
  286 |     @Override
  287 |     public int getContainerSize() {
  288 |         return this.items.size();
  289 |     }
  290 | 
  291 |     @Override
  292 |     protected NonNullList<ItemStack> getItems() {
  293 |         return this.items;
  294 |     }
  295 | 
  296 |     @Override
  297 |     protected void setItems(NonNullList<ItemStack> items) {
  298 |         this.items = items;
  299 |     }
  300 | 
  301 |     @Override
  302 |     public void setItem(int slot, ItemStack itemStack) {
  303 |         setItem(slot, itemStack, false);
  304 |     }
  305 | 
  306 |     // Neo: Skip side-effects if insideTransaction is true so the caller can defer them until the transaction commits
  307 |     @Override
  308 |     public void setItem(int slot, ItemStack itemStack, boolean insideTransaction) {
  309 |         ItemStack oldStack = this.items.get(slot);
  310 |         boolean same = !itemStack.isEmpty() && ItemStack.isSameItemSameComponents(oldStack, itemStack);
  311 |         this.items.set(slot, itemStack);
  312 |         itemStack.limitSize(this.getMaxStackSize(itemStack));
  313 |         if (slot == 0 && !same && this.level instanceof ServerLevel serverLevel && !insideTransaction) {
  314 |             this.cookingTotalTime = getTotalCookTime(serverLevel, this);
  315 |             this.cookingTimer = 0;
  316 |             this.setChanged();
  317 |         }
  318 |     }
  319 | 
  320 |     // Neo: Reset cooking time when the input changes inside a transaction
  321 |     private boolean needsCookingReset = false;
  322 |     private final net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Boolean> cookingResetJournal = new net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Boolean>() {
  323 |         @Override
  324 |         protected Boolean createSnapshot() {
  325 |             return needsCookingReset;
  326 |         }
```

### `net/minecraft/world/level/block/CampfireBlock.java`

来源：.109 参考源码；SHA-256：`636cab18b0c3ee5e47a8499e7651f71be71dfa966fa810238cd78f74779a5006`

原文件 L90–L115：
```java
   90 |     protected InteractionResult useItemOn(
   91 |         ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
   92 |     ) {
   93 |         if (level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire) {
   94 |             ItemStack itemInHand = player.getItemInHand(hand);
   95 |             if (level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(itemInHand)) {
   96 |                 if (level instanceof ServerLevel serverLevel && campfire.placeFood(serverLevel, player, itemInHand)) {
   97 |                     player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
   98 |                     return InteractionResult.SUCCESS_SERVER;
   99 |                 }
  100 | 
  101 |                 return InteractionResult.CONSUME;
  102 |             }
  103 |         }
  104 | 
  105 |         return InteractionResult.TRY_WITH_EMPTY_HAND;
  106 |     }
  107 | 
  108 |     @Override
  109 |     protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
  110 |         if (state.getValue(LIT) && entity instanceof LivingEntity) {
  111 |             entity.hurt(level.damageSources().campfire(), this.fireDamage);
  112 |         }
  113 | 
  114 |         super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
  115 |     }
```

原文件 L300–L315：
```java
  300 |     public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
  301 |         if (level instanceof ServerLevel serverLevel) {
  302 |             if (blockState.getValue(LIT)) {
  303 |                 RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> quickCheck = RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);
  304 |                 return createTickerHelper(
  305 |                     type,
  306 |                     BlockEntityType.CAMPFIRE,
  307 |                     (innerLevel, pos, state, entity) -> CampfireBlockEntity.cookTick(serverLevel, pos, state, entity, quickCheck)
  308 |                 );
  309 |             } else {
  310 |                 return createTickerHelper(type, BlockEntityType.CAMPFIRE, CampfireBlockEntity::cooldownTick);
  311 |             }
  312 |         } else {
  313 |             return blockState.getValue(LIT) ? createTickerHelper(type, BlockEntityType.CAMPFIRE, CampfireBlockEntity::particleTick) : null;
  314 |         }
  315 |     }
```

### `net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java`

来源：.109 参考源码；SHA-256：`c796b5d6531dbf89adf6d2653fbc6adc02de9c8d180d7f45d75c4b3853919ebf`

原文件 L100–L181：
```java
  100 |     }
  101 | 
  102 |     public static void serverTick(Level level, BlockPos pos, BlockState selfState, BrewingStandBlockEntity entity) {
  103 |         ItemStack fuel = entity.items.get(4);
  104 |         if (entity.fuel <= 0 && fuel.is(ItemTags.BREWING_FUEL)) {
  105 |             entity.fuel = 20;
  106 |             fuel.shrink(1);
  107 |             setChanged(level, pos, selfState);
  108 |         }
  109 | 
  110 |         boolean brewable = isBrewable(level.potionBrewing(), entity.items);
  111 |         boolean isBrewing = entity.brewTime > 0;
  112 |         ItemStack ingredient = entity.items.get(3);
  113 |         if (isBrewing) {
  114 |             entity.brewTime--;
  115 |             boolean isDoneBrewing = entity.brewTime == 0;
  116 |             if (isDoneBrewing && brewable) {
  117 |                 doBrew(level, pos, entity.items);
  118 |             } else if (!brewable || !ingredient.is(entity.ingredient)) {
  119 |                 entity.brewTime = 0;
  120 |             }
  121 | 
  122 |             setChanged(level, pos, selfState);
  123 |         } else if (brewable && entity.fuel > 0) {
  124 |             entity.fuel--;
  125 |             entity.brewTime = 400;
  126 |             entity.ingredient = ingredient.getItem();
  127 |             setChanged(level, pos, selfState);
  128 |         }
  129 | 
  130 |         boolean[] newCount = entity.getPotionBits();
  131 |         if (!Arrays.equals(newCount, entity.lastPotionCount)) {
  132 |             entity.lastPotionCount = newCount;
  133 |             BlockState state = selfState;
  134 |             if (!(selfState.getBlock() instanceof BrewingStandBlock)) {
  135 |                 return;
  136 |             }
  137 | 
  138 |             for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; i++) {
  139 |                 state = state.setValue(BrewingStandBlock.HAS_BOTTLE[i], newCount[i]);
  140 |             }
  141 | 
  142 |             level.setBlock(pos, state, 2);
  143 |         }
  144 |     }
  145 | 
  146 |     private boolean[] getPotionBits() {
  147 |         boolean[] result = new boolean[3];
  148 | 
  149 |         for (int potion = 0; potion < 3; potion++) {
  150 |             if (!this.items.get(potion).isEmpty()) {
  151 |                 result[potion] = true;
  152 |             }
  153 |         }
  154 | 
  155 |         return result;
  156 |     }
  157 | 
  158 |     private static boolean isBrewable(PotionBrewing potionBrewing, NonNullList<ItemStack> items) {
  159 |         ItemStack ingredient = items.get(3);
  160 |         if (ingredient.isEmpty()) {
  161 |             return false;
  162 |         } else if (!potionBrewing.isIngredient(ingredient)) {
  163 |             return false;
  164 |         } else {
  165 |             for (int dest = 0; dest < 3; dest++) {
  166 |                 ItemStack itemStack = items.get(dest);
  167 |                 if (!itemStack.isEmpty() && potionBrewing.hasMix(itemStack, ingredient)) {
  168 |                     return true;
  169 |                 }
  170 |             }
  171 | 
  172 |             return false;
  173 |         }
  174 |     }
  175 | 
  176 |     private static void doBrew(Level level, BlockPos pos, NonNullList<ItemStack> items) {
  177 |         if (net.neoforged.neoforge.event.EventHooks.onPotionAttemptBrew(items)) return;
  178 |         ItemStack ingredient = items.get(3);
  179 |         PotionBrewing potionBrewing = level.potionBrewing();
  180 | 
  181 |         for (int dest = 0; dest < 3; dest++) {
```

<a id="s10"></a>
## S10 — 粗入口门禁的当前实现

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/server/encounter/EncounterRuntime.java`

来源：当前项目；SHA-256：`e252fa42d1232bac22385e0f967e882e13f3d3958b9a30de0c0ad051b7deca07`

原文件 L2694–L2763：
```java
 2694 |     /** Pure query. Preparing a vanilla simulation step is a separate execution boundary. */
 2695 |     public boolean isEntitySimulationPaused(Entity entity) {
 2696 |         if (quarantinedProjectiles.containsKey(entity.getUUID())) return true;
 2697 |         if (entity instanceof AbstractArrow || entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball && projectileOrigins.containsKey(entity.getUUID())) {
 2698 |             UUID recorded = projectileSimulationDomains.get(entity.getUUID());
 2699 |             UUID domain = recorded == null ? null : engine.canonicalEncounterId(recorded);
 2700 |             if (domain == null || !engine.encounterIds().contains(domain)) return isEntityInsidePausedRegion(entity);
 2701 |             return pendingProjectileAttacks.containsKey(entity.getUUID())
 2702 |                 || !domain.equals(activeEnvironmentEncounters.get(entity.level()));
 2703 |         }
 2704 |         if (!isEntityInsidePausedRegion(entity)) return false;
 2705 |         MobMoveLease lease = mobMoves.get(entity.getUUID());
 2706 |         if (lease == null || !activeMobLease(lease)) return true;
 2707 |         if (lease.pathStarted && entity instanceof Mob mob) {
 2708 |             int cost = nextMobStepCost(mob, lease);
 2709 |             return cost < 0 || engine.stateView(lease.encounterId).members().get(lease.mobId).movementTicks() < cost;
 2710 |         }
 2711 |         return false;
 2712 |     }
 2713 | 
 2714 |     private record SimulationDecision(long tick, CombatEngine.Revision revision, UUID environment, boolean paused) {}
 2715 |     private final Map<UUID, SimulationDecision> simulationDecisions = new HashMap<>();
 2716 | 
 2717 |     /** Called only from the vanilla entity/ride execution gate; repeated visits cannot replay effects. */
 2718 |     public boolean prepareEntitySimulation(Entity entity) {
 2719 |         requireThread();
 2720 |         SimulationDecision prior = simulationDecisions.get(entity.getUUID());
 2721 |         UUID environment = activeEnvironmentEncounters.get(entity.level());
 2722 |         if (prior != null && prior.tick() == cumulativeServerTicks
 2723 |             && prior.revision().equals(engine.revision()) && Objects.equals(prior.environment(), environment))
 2724 |             return prior.paused();
 2725 |         boolean paused = prepareEntitySimulationStep(entity);
 2726 |         simulationDecisions.put(entity.getUUID(), new SimulationDecision(cumulativeServerTicks,
 2727 |             engine.revision(), environment, paused));
 2728 |         return paused;
 2729 |     }
 2730 | 
 2731 |     private boolean prepareEntitySimulationStep(Entity entity) {
 2732 |         if (quarantinedProjectiles.containsKey(entity.getUUID())) return true;
 2733 |         if (entity instanceof AbstractArrow arrow) return arrowSimulationPaused(arrow);
 2734 |         if (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball && projectileOrigins.containsKey(entity.getUUID())) {
 2735 |             UUID domain = liveProjectileDomain(entity.getUUID());
 2736 |             return domain != null && !domain.equals(activeEnvironmentEncounters.get(entity.level()));
 2737 |         }
 2738 |         MobMoveLease lease = mobMoves.get(entity.getUUID());
 2739 |         if (lease != null && !activeMobLease(lease)) {
 2740 |             revokeMobLease(lease);
 2741 |             lease = null;
 2742 |         }
 2743 |         if (!isEntityInsidePausedRegion(entity)) {
 2744 |             if (lease != null) closeMobMove(lease, "member moved outside fixed region");
 2745 |             return false;
 2746 |         }
 2747 |         if (lease == null) return true;
 2748 |         if (lease.pathStarted && entity instanceof Mob mob) {
 2749 |             int remaining = engine.stateView(lease.encounterId).members().get(entity.getUUID()).movementTicks();
 2750 |             // Inspect the next vanilla navigation/control proposal and its loaded collision
 2751 |             // corridor. Flat dry steps cost one; water and possible jumps need their own budget.
 2752 |             int possibleCost = nextMobStepCost(mob, lease);
 2753 |             if (possibleCost < 0 || remaining < possibleCost) {
 2754 |                 lease.budgetBlocked = true;
 2755 |                 return true;
 2756 |             }
 2757 |             lease.authorizedCost = possibleCost;
 2758 |             if (lease.underreserveNextExpensiveStepForGameTest && possibleCost >= 2) {
 2759 |                 lease.authorizedCost = 1;
 2760 |             }
 2761 |         }
 2762 |         return false;
 2763 |     }
```

原文件 L2794–L2817：
```java
 2794 |     public boolean isEntityInsidePausedRegion(Entity entity) {
 2795 |         if (!(entity.level() instanceof ServerLevel level)) return false;
 2796 |         Vec3 center = entity.getBoundingBox().getCenter();
 2797 |         String dimension = level.dimension().identifier().toString();
 2798 |         for (var entry : regions.entrySet()) {
 2799 |             EncounterRegion region = entry.getValue();
 2800 |             if (region.dimension().equals(dimension)
 2801 |                 && region.containsPoint(center.x, center.y, center.z)
 2802 |                 && !exitedRegion(entity, entry.getKey())) return true;
 2803 |         }
 2804 |         return false;
 2805 |     }
 2806 | 
 2807 |     /** Use the same continuous region with a block's center, not its whole chunk. */
 2808 |     public boolean isBlockSimulationPaused(ServerLevel level, BlockPos pos) {
 2809 |         String dimension = level.dimension().identifier().toString();
 2810 |         for (var entry : regions.entrySet()) {
 2811 |             EncounterRegion region = entry.getValue();
 2812 |             if (region.dimension().equals(dimension)
 2813 |                 && region.containsBlock(pos.getX(), pos.getY(), pos.getZ()))
 2814 |                 if (!entry.getKey().equals(activeEnvironmentEncounters.get(level))) return true;
 2815 |         }
 2816 |         return false;
 2817 |     }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/server/world/RegionalScheduledTicks.java`

来源：当前项目；SHA-256：`d699e972c4ad8a2a6ea6daeab184f16f5d693867485a72ea5d51349e8dfa7a59`

原文件 L23–L93：
```java
   23 | /** One in-memory owner for scheduled ticks temporarily removed from vanilla's loaded chunk queues. */
   24 | public final class RegionalScheduledTicks {
   25 |     private static final Map<LevelTicks<?>, RegionalScheduledTicks> OWNERS = new IdentityHashMap<>();
   26 |     interface RegionAccess {
   27 |         UUID encounterAtBlock(ServerLevel level, BlockPos pos);
   28 |         UUID activeEnvironmentStep(ServerLevel level);
   29 |         Set<Long> candidateRegionChunks(ServerLevel level);
   30 |         boolean isBlockSimulationPaused(ServerLevel level, BlockPos pos);
   31 |     }
   32 |     private final RegionAccess owner;
   33 |     private final Map<LevelTicks<?>, QueueState<?>> queues = new IdentityHashMap<>();
   34 |     private boolean transferring;
   35 | 
   36 |     private record TickKey(BlockPos pos, Object type) {
   37 |         @Override public boolean equals(Object other) {
   38 |             return other instanceof TickKey key && pos.equals(key.pos) && type == key.type;
   39 |         }
   40 |         @Override public int hashCode() { return 31 * pos.hashCode() + System.identityHashCode(type); }
   41 |     }
   42 | 
   43 |     private static final class QueueState<T> {
   44 |         final ServerLevel level;
   45 |         final LevelTicks<T> queue;
   46 |         final Map<TickKey, Held<T>> byKey = new HashMap<>();
   47 |         final Map<Long, LinkedHashMap<TickKey, Held<T>>> byChunk = new HashMap<>();
   48 |         QueueState(ServerLevel level, LevelTicks<T> queue) { this.level = level; this.queue = queue; }
   49 |         void add(Held<T> entry) {
   50 |             TickKey key = new TickKey(entry.original.pos(), entry.original.type());
   51 |             if (byKey.putIfAbsent(key, entry) == null)
   52 |                 byChunk.computeIfAbsent(ChunkPos.pack(key.pos()), ignored -> new LinkedHashMap<>()).put(key, entry);
   53 |         }
   54 |         void remove(Held<T> entry) {
   55 |             TickKey key = new TickKey(entry.original.pos(), entry.original.type());
   56 |             byKey.remove(key);
   57 |             Map<TickKey, Held<T>> entries = byChunk.get(ChunkPos.pack(key.pos()));
   58 |             if (entries != null) {
   59 |                 entries.remove(key);
   60 |                 if (entries.isEmpty()) byChunk.remove(ChunkPos.pack(key.pos()));
   61 |             }
   62 |         }
   63 |         @SuppressWarnings("unchecked")
   64 |         void removeUnknown(Held<?> entry) { remove((Held<T>) entry); }
   65 |         List<Held<T>> inChunk(long chunk) {
   66 |             Map<TickKey, Held<T>> entries = byChunk.get(chunk);
   67 |             return entries == null ? List.of() : List.copyOf(entries.values());
   68 |         }
   69 |     }
   70 | 
   71 |     private static final class Held<T> {
   72 |         final ServerLevel level;
   73 |         final LevelTicks<T> queue;
   74 |         final ScheduledTick<T> original;
   75 |         final UUID encounterId;
   76 |         final UUID bornStep;
   77 |         long remaining;
   78 | 
   79 |         Held(ServerLevel level, LevelTicks<T> queue, ScheduledTick<T> original,
   80 |              UUID encounterId, long remaining, UUID bornStep) {
   81 |             this.level = level;
   82 |             this.queue = queue;
   83 |             this.original = original;
   84 |             this.encounterId = encounterId;
   85 |             this.remaining = remaining;
   86 |             this.bornStep = bornStep;
   87 |         }
   88 | 
   89 |         void release(long triggerTick) {
   90 |             queue.schedule(new ScheduledTick<>(original.type(), original.pos(), triggerTick,
   91 |                 original.priority(), original.subTickOrder()));
   92 |         }
   93 |     }
```

原文件 L170–L244：
```java
  170 |     void captureAll(ServerLevel level) {
  171 |         capture(level, level.getBlockTicks(), level.getGameTime());
  172 |         capture(level, level.getFluidTicks(), level.getGameTime());
  173 |     }
  174 | 
  175 |     private <T> boolean holdDirect(LevelTicks<T> queue, ScheduledTick<T> tick) {
  176 |         if (transferring) return false;
  177 |         @SuppressWarnings("unchecked") QueueState<T> state = (QueueState<T>) queues.get(queue);
  178 |         if (state == null) return false;
  179 |         LevelChunkTicks<T> container = ((LevelTicksAccessor<T>) queue).dndturn$containers()
  180 |             .get(ChunkPos.pack(tick.pos()));
  181 |         if (container == null) return false;
  182 |         if (container.hasScheduledTick(tick.pos(), tick.type())) return true;
  183 |         UUID encounterId = owner.encounterAtBlock(state.level, tick.pos());
  184 |         if (encounterId == null) return state.byKey.containsKey(new TickKey(tick.pos(), tick.type()));
  185 |         TickKey key = new TickKey(tick.pos(), tick.type());
  186 |         if (!state.byKey.containsKey(key)) {
  187 |             if (!markChunkUnsaved(state.level, tick.pos())) return false;
  188 |             state.add(new Held<>(state.level, queue, tick,
  189 |                 encounterId, Math.max(0, tick.triggerTick() - state.level.getGameTime()),
  190 |                 owner.activeEnvironmentStep(state.level)));
  191 |         }
  192 |         return true;
  193 |     }
  194 | 
  195 |     void beforeBlockQueue(ServerLevel level, UUID activeEncounter) {
  196 |         LevelTicks<net.minecraft.world.level.block.Block> queue = level.getBlockTicks();
  197 |         capture(level, queue, level.getGameTime());
  198 |         if (activeEncounter != null) releaseDue(level, queue, level.getGameTime(), activeEncounter);
  199 |     }
  200 | 
  201 |     void beforeFluidQueue(ServerLevel level, UUID activeEncounter) {
  202 |         LevelTicks<net.minecraft.world.level.material.Fluid> queue = level.getFluidTicks();
  203 |         capture(level, queue, level.getGameTime());
  204 |         if (activeEncounter != null) releaseDue(level, queue, level.getGameTime(), activeEncounter);
  205 |     }
  206 | 
  207 |     private <T> void capture(ServerLevel level, LevelTicks<T> queue, long now) {
  208 |         QueueState<T> state = state(level, queue);
  209 |         var containers = ((LevelTicksAccessor<T>) queue).dndturn$containers();
  210 |         for (long key : owner.candidateRegionChunks(level)) {
  211 |             LevelChunkTicks<T> container = containers.get(key);
  212 |             if (container == null) continue;
  213 |             net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(
  214 |                 ChunkPos.getX(key), ChunkPos.getZ(key));
  215 |             if (chunk == null) continue;
  216 |             List<ScheduledTick<T>> captured = container.getAll().filter(tick ->
  217 |                 owner.encounterAtBlock(level, tick.pos()) != null).toList();
  218 |             if (captured.isEmpty()) continue;
  219 |             chunk.markUnsaved();
  220 |             Set<ScheduledTick<T>> selected = Set.copyOf(captured);
  221 |             container.removeIf(selected::contains);
  222 |             for (ScheduledTick<T> tick : captured) {
  223 |                 state.add(new Held<>(level, queue, tick, owner.encounterAtBlock(level, tick.pos()),
  224 |                     Math.max(0, tick.triggerTick() - now), owner.activeEnvironmentStep(level)));
  225 |             }
  226 |         }
  227 |     }
  228 | 
  229 |     private <T> void releaseDue(ServerLevel level, LevelTicks<T> queue, long now, UUID activeEncounter) {
  230 |         @SuppressWarnings("unchecked") QueueState<T> state = (QueueState<T>) queues.get(queue);
  231 |         if (state == null) return;
  232 |         UUID stepId = owner.activeEnvironmentStep(level);
  233 |         for (Held<T> entry : List.copyOf(state.byKey.values())) {
  234 |             if (!entry.encounterId.equals(activeEncounter)) continue;
  235 |             if (owner.isBlockSimulationPaused(level, entry.original.pos())) continue;
  236 |             if (entry.remaining > 0 && !java.util.Objects.equals(stepId, entry.bornStep)) {
  237 |                 entry.remaining--;
  238 |                 markChunkUnsaved(level, entry.original.pos());
  239 |             }
  240 |             if (entry.remaining == 0) {
  241 |                 transfer(state, entry, now);
  242 |             }
  243 |         }
  244 |     }
```

原文件 L294–L369：
```java
  294 |     void releaseChunk(ServerLevel level, ChunkPos chunk) {
  295 |         for (QueueState<?> state : List.copyOf(queues.values())) {
  296 |             if (state.level != level) continue;
  297 |             for (Held<?> entry : state.inChunk(chunk.pack())) transferUnknown(state, entry,
  298 |                 level.getGameTime() + entry.remaining);
  299 |         }
  300 |     }
  301 | 
  302 |     void releaseAll() {
  303 |         RuntimeException releaseFailure = null;
  304 |         try {
  305 |             for (QueueState<?> state : List.copyOf(queues.values())) {
  306 |                 for (Held<?> entry : List.copyOf(state.byKey.values())) {
  307 |                     try { transferUnknown(state, entry, entry.level.getGameTime() + entry.remaining); }
  308 |                     catch (RuntimeException failure) {
  309 |                         if (releaseFailure == null) releaseFailure = failure;
  310 |                         else releaseFailure.addSuppressed(failure);
  311 |                     }
  312 |                 }
  313 |             }
  314 |         } finally {
  315 |             // Shutdown persists held evidence first. Failed transfers must not retain a dead server.
  316 |             OWNERS.entrySet().removeIf(entry -> entry.getValue() == this);
  317 |             queues.clear();
  318 |         }
  319 |         if (releaseFailure != null) throw releaseFailure;
  320 |     }
  321 | 
  322 |     private void releaseMatching(QueueState<?> state, Predicate<Held<?>> predicate) {
  323 |         for (Held<?> entry : List.copyOf(state.byKey.values())) {
  324 |             if (!predicate.test(entry)) continue;
  325 |             transferUnknown(state, entry, entry.level.getGameTime() + entry.remaining);
  326 |         }
  327 |     }
  328 | 
  329 |     @SuppressWarnings({"unchecked", "rawtypes"})
  330 |     private void transferUnknown(QueueState<?> state, Held<?> entry, long when) {
  331 |         transfer((QueueState) state, (Held) entry, when);
  332 |     }
  333 | 
  334 |     private <T> void transfer(QueueState<T> state, Held<T> entry, long when) {
  335 |         if (!((LevelTicksAccessor<T>) state.queue).dndturn$containers()
  336 |             .containsKey(ChunkPos.pack(entry.original.pos())))
  337 |             throw new IllegalStateException("held tick chunk container is not loaded");
  338 |         transferring = true;
  339 |         try {
  340 |             entry.release(when);
  341 |             state.remove(entry);
  342 |             markChunkUnsaved(entry.level, entry.original.pos());
  343 |         } finally {
  344 |             transferring = false;
  345 |         }
  346 |     }
  347 | 
  348 |     private static boolean markChunkUnsaved(ServerLevel level, BlockPos pos) {
  349 |         net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(
  350 |             pos.getX() >> 4, pos.getZ() >> 4);
  351 |         if (chunk == null) return false;
  352 |         chunk.markUnsaved();
  353 |         return true;
  354 |     }
  355 | 
  356 |     boolean hasHeld(LevelTicks<?> queue, BlockPos pos, Object type) {
  357 |         QueueState<?> state = queues.get(queue);
  358 |         return state != null && state.byKey.containsKey(new TickKey(pos, type));
  359 |     }
  360 | 
  361 |     <T> List<SavedTick<T>> savedInChunk(LevelTicks<T> queue, ChunkPos chunk) {
  362 |         @SuppressWarnings("unchecked") QueueState<T> state = (QueueState<T>) queues.get(queue);
  363 |         if (state == null) return List.of();
  364 |         return state.inChunk(chunk.pack()).stream()
  365 |             .sorted((first, second) -> ScheduledTick.DRAIN_ORDER.compare(first.original, second.original))
  366 |             .map(entry -> new SavedTick<>(entry.original.type(),
  367 |             entry.original.pos(), Math.toIntExact(entry.remaining), entry.original.priority())).toList();
  368 |     }
  369 | 
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/mixin/server/world/LevelBlockEntityGateMixin.java`

来源：当前项目；SHA-256：`d7b324a08cc7991126149c1289cedccd63610591e4871289d6e50a56dca3b956`

原文件 L1–L22：
```java
    1 | package cc.sighs.dndturn.mixin;
    2 | 
    3 | import cc.sighs.dndturn.platform.server.runtime.MinecraftCombatRuntime;
    4 | import net.minecraft.core.BlockPos;
    5 | import net.minecraft.server.level.ServerLevel;
    6 | import net.minecraft.world.level.Level;
    7 | import org.spongepowered.asm.mixin.Mixin;
    8 | import org.spongepowered.asm.mixin.injection.At;
    9 | import org.spongepowered.asm.mixin.injection.Redirect;
   10 | 
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

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/mixin/server/world/ServerLevelBlockEventGateMixin.java`

来源：当前项目；SHA-256：`8cb4cfa9a4d407988d23ba23c317b47fcde5a6ecb8933fd7eec5cc38ca1cc3fb`

原文件 L1–L42：
```java
    1 | package cc.sighs.dndturn.mixin;
    2 | 
    3 | import cc.sighs.dndturn.platform.server.runtime.MinecraftCombatRuntime;
    4 | import cc.sighs.dndturn.platform.server.presentation.ChestPresentation;
    5 | import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
    6 | import java.util.List;
    7 | import net.minecraft.core.BlockPos;
    8 | import net.minecraft.server.level.ServerLevel;
    9 | import net.minecraft.world.level.BlockEventData;
   10 | import net.minecraft.world.level.block.Block;
   11 | import org.spongepowered.asm.mixin.Final;
   12 | import org.spongepowered.asm.mixin.Mixin;
   13 | import org.spongepowered.asm.mixin.Shadow;
   14 | import org.spongepowered.asm.mixin.injection.At;
   15 | import org.spongepowered.asm.mixin.injection.Inject;
   16 | import org.spongepowered.asm.mixin.injection.Redirect;
   17 | import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
   18 | 
   19 | /** Vanilla runBlockEvents reschedules entries when this positional gate returns false. */
   20 | @Mixin(ServerLevel.class)
   21 | public abstract class ServerLevelBlockEventGateMixin {
   22 |     @Shadow @Final private ObjectLinkedOpenHashSet<BlockEventData> blockEvents;
   23 |     @Shadow @Final private List<BlockEventData> blockEventsToReschedule;
   24 | 
   25 |     @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true)
   26 |     private void dndturn$lid(BlockPos pos, Block block, int event, int count, CallbackInfo ci) {
   27 |         ServerLevel level = (ServerLevel)(Object)this;
   28 |         if (!ChestPresentation.accepts(level, pos, block, event)) return;
   29 |         // This display channel now has one owner; old queued targets must never replay later.
   30 |         blockEvents.removeIf(e -> e.pos().equals(pos) && e.block() == block && e.paramA() == 1);
   31 |         blockEventsToReschedule.removeIf(e -> e.pos().equals(pos) && e.block() == block && e.paramA() == 1);
   32 |         ChestPresentation.send(level, pos, block, count);
   33 |         ci.cancel();
   34 |     }
   35 |     @Redirect(method = "runBlockEvents()V",
   36 |         at = @At(value = "INVOKE",
   37 |             target = "Lnet/minecraft/server/level/ServerLevel;shouldTickBlocksAt(Lnet/minecraft/core/BlockPos;)Z"))
   38 |     private boolean dndturn$regionalBlockEventFreeze(ServerLevel level, BlockPos pos) {
   39 |         return level.shouldTickBlocksAt(pos)
   40 |             && !MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos);
   41 |     }
   42 | }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/mixin/server/world/ServerLevelRandomTickGateMixin.java`

来源：当前项目；SHA-256：`7073709ece28fcf18bf31284f22d91ce8961b7a798d21b4b29c81945d887c283`

原文件 L1–L31：
```java
    1 | package cc.sighs.dndturn.mixin;
    2 | 
    3 | import cc.sighs.dndturn.platform.server.runtime.MinecraftCombatRuntime;
    4 | import net.minecraft.core.BlockPos;
    5 | import net.minecraft.server.level.ServerLevel;
    6 | import net.minecraft.util.RandomSource;
    7 | import net.minecraft.world.level.block.state.BlockState;
    8 | import net.minecraft.world.level.material.FluidState;
    9 | import org.spongepowered.asm.mixin.Mixin;
   10 | import org.spongepowered.asm.mixin.injection.At;
   11 | import org.spongepowered.asm.mixin.injection.Redirect;
   12 | 
   13 | /** Skip sampled block/fluid callbacks at exact paused positions while other positions retain vanilla sampling. */
   14 | @Mixin(ServerLevel.class)
   15 | public abstract class ServerLevelRandomTickGateMixin {
   16 |     @Redirect(method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
   17 |         at = @At(value = "INVOKE",
   18 |             target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
   19 |     private void dndturn$regionalRandomBlockFreeze(BlockState state, ServerLevel level,
   20 |                                                     BlockPos pos, RandomSource random) {
   21 |         if (!MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos)) state.randomTick(level, pos, random);
   22 |     }
   23 | 
   24 |     @Redirect(method = "tickChunk(Lnet/minecraft/world/level/chunk/LevelChunk;I)V",
   25 |         at = @At(value = "INVOKE",
   26 |             target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
   27 |     private void dndturn$regionalRandomFluidFreeze(FluidState state, ServerLevel level,
   28 |                                                     BlockPos pos, RandomSource random) {
   29 |         if (!MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos)) state.randomTick(level, pos, random);
   30 |     }
   31 | }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/mixin/server/world/ServerLevelSimulationStepMixin.java`

来源：当前项目；SHA-256：`b79345f395702cf3ce851ec245ed00a65e404cd2286d5c25b9d3744c3015ffa4`

原文件 L1–L42：
```java
    1 | package cc.sighs.dndturn.mixin;
    2 | 
    3 | import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
    4 | import java.util.function.BooleanSupplier;
    5 | import net.minecraft.server.level.ServerLevel;
    6 | import org.spongepowered.asm.mixin.Mixin;
    7 | import org.spongepowered.asm.mixin.injection.At;
    8 | import org.spongepowered.asm.mixin.injection.Inject;
    9 | import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
   10 | 
   11 | @Mixin(ServerLevel.class)
   12 | public abstract class ServerLevelSimulationStepMixin {
   13 |     @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
   14 |     private void dndturn$beforeWorldSimulation(BooleanSupplier haveTime, CallbackInfo callback) {
   15 |         ServerLevel level = (ServerLevel) (Object) this;
   16 |         ServerCombatService service = ServerCombatService.existing(level.getServer());
   17 |         if (service != null) service.beforeLevelTick(level);
   18 |     }
   19 | 
   20 |     @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At(value = "INVOKE",
   21 |         target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V", ordinal = 0))
   22 |     private void dndturn$beforeBlockQueue(BooleanSupplier haveTime, CallbackInfo callback) {
   23 |         ServerLevel level = (ServerLevel) (Object) this;
   24 |         ServerCombatService service = ServerCombatService.existing(level.getServer());
   25 |         if (service != null) service.beforeBlockQueue(level);
   26 |     }
   27 | 
   28 |     @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At(value = "INVOKE",
   29 |         target = "Lnet/minecraft/world/ticks/LevelTicks;tick(JILjava/util/function/BiConsumer;)V", ordinal = 1))
   30 |     private void dndturn$beforeFluidQueue(BooleanSupplier haveTime, CallbackInfo callback) {
   31 |         ServerLevel level = (ServerLevel) (Object) this;
   32 |         ServerCombatService service = ServerCombatService.existing(level.getServer());
   33 |         if (service != null) service.beforeFluidQueue(level);
   34 |     }
   35 | 
   36 |     @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
   37 |     private void dndturn$afterWorldSimulation(BooleanSupplier haveTime, CallbackInfo callback) {
   38 |         ServerLevel level = (ServerLevel) (Object) this;
   39 |         ServerCombatService service = ServerCombatService.existing(level.getServer());
   40 |         if (service != null) service.afterLevelTick(level);
   41 |     }
   42 | }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/mixin/server/world/ServerLevelEntityGateMixin.java`

来源：当前项目；SHA-256：`a234a63e0773c3595cacdaf6de18fd2ff36605d75a159b63b8d30d830d5e471e`

原文件 L1–L30：
```java
    1 | package cc.sighs.dndturn.mixin;
    2 | 
    3 | import cc.sighs.dndturn.platform.server.runtime.MinecraftCombatRuntime;
    4 | import net.minecraft.server.level.ServerLevel;
    5 | import net.minecraft.world.TickRateManager;
    6 | import net.minecraft.world.entity.Entity;
    7 | import org.spongepowered.asm.mixin.Mixin;
    8 | import org.spongepowered.asm.mixin.injection.At;
    9 | import org.spongepowered.asm.mixin.injection.Redirect;
   10 | import org.spongepowered.asm.mixin.injection.Inject;
   11 | import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
   12 | 
   13 | /** Keep the vanilla entity container maintenance while skipping paused simulation before despawn and tickCount. */
   14 | @Mixin(ServerLevel.class)
   15 | public abstract class ServerLevelEntityGateMixin {
   16 |     @Redirect(method = "lambda$tick$0(Lnet/minecraft/world/TickRateManager;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/world/entity/Entity;)V",
   17 |         at = @At(value = "INVOKE",
   18 |         target = "Lnet/minecraft/world/TickRateManager;isEntityFrozen(Lnet/minecraft/world/entity/Entity;)Z"))
   19 |     private boolean dndturn$regionalEntityFreeze(TickRateManager manager, Entity entity) {
   20 |         return manager.isEntityFrozen(entity) || MinecraftCombatRuntime.prepareFormalEntitySimulation(entity);
   21 |     }
   22 | 
   23 |     @Inject(method = "tickPassenger(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V",
   24 |         at = @At("HEAD"), cancellable = true)
   25 |     private void dndturn$regionalPassengerFreeze(Entity vehicle, Entity passenger, CallbackInfo callback) {
   26 |         // Let vanilla repair an invalid riding relation before considering a simulation pause.
   27 |         if (!passenger.isRemoved() && passenger.getVehicle() == vehicle
   28 |             && MinecraftCombatRuntime.prepareFormalEntitySimulation(passenger)) callback.cancel();
   29 |     }
   30 | }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/server/runtime/MinecraftCombatRuntime.java`

来源：当前项目；SHA-256：`db3d57b84fb17e37b8d303d2581dc1c7784bb55ec5025270d358ff86feea1380`

原文件 L25–L78：
```java
   25 | 
   26 |     public static boolean isBodyPaused(ServerPlayer player) {
   27 |         ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
   28 |         return formal != null && formal.isEntitySimulationPaused(player);
   29 |     }
   30 | 
   31 |     /** A movement lease opens movement input and client prediction, not the server body tick. */
   32 |     public static boolean isPlayerMovementPaused(ServerPlayer player) {
   33 |         ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
   34 |         return formal != null && formal.isEntityInsidePausedRegion(player)
   35 |                 && !formal.hasPlayerMoveLease(player.getUUID());
   36 |     }
   37 | 
   38 |     public static boolean isGameplayInputPaused(ServerPlayer player) {
   39 |         ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
   40 |         return formal != null && formal.isEntityInsidePausedRegion(player);
   41 |     }
   42 | 
   43 |     public static void onEntityTickPost(EntityTickEvent.Post event) {
   44 |         Entity entity = event.getEntity();
   45 |         if (entity.level() instanceof ServerLevel level) {
   46 |             ServerCombatService formal = ServerCombatService.existing(level.getServer());
   47 |             if (formal != null) formal.noteMobTick(entity.getUUID());
   48 |         }
   49 |     }
   50 | 
   51 |     public static void onServerTick(ServerTickEvent.Post event) {
   52 |         ServerCombatService formal = ServerCombatService.existing(event.getServer());
   53 |         if (formal != null) {
   54 |             formal.tickConsent();
   55 |             formal.confirmPendingDeaths();
   56 |             formal.finishMovementTicks();
   57 |             formal.reconcileMemberLocations();
   58 |             formal.advanceMobTurns();
   59 |             formal.flushResultPages();
   60 |             formal.syncBodyStateTransitions();
   61 |             formal.advancePersistenceClock();
   62 |             formal.persistIfChanged();
   63 |         }
   64 |     }
   65 | 
   66 |     public static boolean isFormalEntitySimulationPaused(Entity entity) {
   67 |         if (!(entity.level() instanceof ServerLevel level)) return false;
   68 |         ServerCombatService formal = ServerCombatService.existing(level.getServer());
   69 |         return formal != null && formal.isEntitySimulationPaused(entity);
   70 |     }
   71 | 
   72 |     public static boolean isFormalBlockSimulationPaused(ServerLevel level, BlockPos pos) {
   73 |         ServerCombatService formal = ServerCombatService.existing(level.getServer());
   74 |         return formal != null && formal.isBlockSimulationPaused(level, pos);
   75 |     }
   76 | 
   77 |     public static boolean prepareFormalEntitySimulation(Entity entity) {
   78 |         if (!(entity.level() instanceof ServerLevel level)) return false;
```

原文件 L94–L121：
```java
   94 |     }
   95 | 
   96 |     public static void onDeath(LivingDeathEvent event) {
   97 |         if (event.getEntity().level() instanceof ServerLevel level) {
   98 |             ServerCombatService formal = ServerCombatService.existing(level.getServer());
   99 |             if (formal != null) formal.noteDeath(event.getEntity().getUUID());
  100 |         }
  101 |     }
  102 | 
  103 |     public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
  104 |         if (event.getLevel() instanceof ServerLevel level) {
  105 |             ServerCombatService formal = ServerCombatService.existing(level.getServer());
  106 |             if (formal != null && (event.getEntity() instanceof AbstractArrow
  107 |                 || event.getEntity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball))
  108 |                 formal.noteArrowLeave(event.getEntity());
  109 |             if (formal != null) formal.leave(event.getEntity().getUUID());
  110 |         }
  111 |     }
  112 | 
  113 |     public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
  114 |         if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball ball) {
  115 |             ServerCombatService formal = ServerCombatService.existing(level.getServer());
  116 |             if (formal != null) formal.captureSnowballOrigin(ball, event.loadedFromDisk());
  117 |         }
  118 |         if (event.getLevel() instanceof ServerLevel level
  119 |             && event.getEntity() instanceof AbstractArrow arrow) {
  120 |             ServerCombatService formal = ServerCombatService.existing(level.getServer());
  121 |             if (formal != null) formal.captureArrowOrigin(arrow, event.loadedFromDisk());
```

<a id="s11"></a>
## S11 — 活塞、方块事件与显示维护（精确放行）

### `net/minecraft/world/level/block/piston/PistonBaseBlock.java`

来源：.109 参考源码；SHA-256：`963b0b4776ef0618e826a6569de11d4618c4587478f6457c230c5ed3fd62fcb0`

原文件 L120–L350：
```java
  120 | 
  121 |             level.blockEvent(pos, this, event, direction.get3DDataValue());
  122 |         }
  123 |     }
  124 | 
  125 |     private boolean getNeighborSignal(SignalGetter level, BlockPos pos, Direction pushDirection) {
  126 |         for (Direction direction : Direction.values()) {
  127 |             if (direction != pushDirection && level.hasSignal(pos.relative(direction), direction)) {
  128 |                 return true;
  129 |             }
  130 |         }
  131 | 
  132 |         if (level.hasSignal(pos, Direction.DOWN)) {
  133 |             return true;
  134 |         } else {
  135 |             BlockPos above = pos.above();
  136 | 
  137 |             for (Direction directionx : Direction.values()) {
  138 |                 if (directionx != Direction.DOWN && level.hasSignal(above.relative(directionx), directionx)) {
  139 |                     return true;
  140 |                 }
  141 |             }
  142 | 
  143 |             return false;
  144 |         }
  145 |     }
  146 | 
  147 |     @Override
  148 |     protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
  149 |         Direction direction = state.getValue(FACING);
  150 |         BlockState extendedState = state.setValue(EXTENDED, true);
  151 |         if (!level.isClientSide()) {
  152 |             boolean extend = this.getNeighborSignal(level, pos, direction);
  153 |             if (extend && (b0 == 1 || b0 == 2)) {
  154 |                 level.setBlock(pos, extendedState, 2);
  155 |                 return false;
  156 |             }
  157 | 
  158 |             if (!extend && b0 == 0) {
  159 |                 return false;
  160 |             }
  161 |         }
  162 | 
  163 |         RandomSource random = level.getRandom();
  164 |         if (b0 == 0) {
  165 |             if (net.neoforged.neoforge.event.EventHooks.onPistonMovePre(level, pos, direction, true)) return false;
  166 |             if (!this.moveBlocks(level, pos, direction, true)) {
  167 |                 return false;
  168 |             }
  169 | 
  170 |             level.setBlock(pos, extendedState, 67);
  171 |             level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.25F + 0.6F);
  172 |             level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(extendedState));
  173 |         } else if (b0 == 1 || b0 == 2) {
  174 |             if (net.neoforged.neoforge.event.EventHooks.onPistonMovePre(level, pos, direction, false)) return false;
  175 |             BlockEntity prevBlockEntity = level.getBlockEntity(pos.relative(direction));
  176 |             if (prevBlockEntity instanceof PistonMovingBlockEntity) {
  177 |                 ((PistonMovingBlockEntity)prevBlockEntity).finalTick();
  178 |             }
  179 | 
  180 |             BlockState movingPistonState = Blocks.MOVING_PISTON
  181 |                 .defaultBlockState()
  182 |                 .setValue(MovingPistonBlock.FACING, direction)
  183 |                 .setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);
  184 |             level.setBlock(pos, movingPistonState, 276);
  185 |             level.setBlockEntity(
  186 |                 MovingPistonBlock.newMovingBlockEntity(
  187 |                     pos, movingPistonState, this.defaultBlockState().setValue(FACING, Direction.from3DDataValue(b1 & 7)), direction, false, true
  188 |                 )
  189 |             );
  190 |             level.updateNeighborsAt(pos, movingPistonState.getBlock());
  191 |             movingPistonState.updateNeighbourShapes(level, pos, 2);
  192 |             if (this.isSticky) {
  193 |                 BlockPos twoPos = pos.offset(direction.getStepX() * 2, direction.getStepY() * 2, direction.getStepZ() * 2);
  194 |                 BlockState movingState = level.getBlockState(twoPos);
  195 |                 boolean pistonPiece = false;
  196 |                 if (movingState.is(Blocks.MOVING_PISTON)
  197 |                     && level.getBlockEntity(twoPos) instanceof PistonMovingBlockEntity entity
  198 |                     && entity.getDirection() == direction
  199 |                     && entity.isExtending()) {
  200 |                     entity.finalTick();
  201 |                     pistonPiece = true;
  202 |                 }
  203 | 
  204 |                 if (!pistonPiece) {
  205 |                     if (b0 != 1
  206 |                         || movingState.isAir()
  207 |                         || !isPushable(movingState, level, twoPos, direction.getOpposite(), false, direction)
  208 |                         || movingState.getPistonPushReaction() != PushReaction.NORMAL
  209 |                             && !movingState.is(Blocks.PISTON)
  210 |                             && !movingState.is(Blocks.STICKY_PISTON)) {
  211 |                         level.removeBlock(pos.relative(direction), false);
  212 |                     } else {
  213 |                         this.moveBlocks(level, pos, direction, false);
  214 |                     }
  215 |                 }
  216 |             } else {
  217 |                 level.removeBlock(pos.relative(direction), false);
  218 |             }
  219 | 
  220 |             level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.15F + 0.6F);
  221 |             level.gameEvent(GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(movingPistonState));
  222 |         }
  223 | 
  224 |         net.neoforged.neoforge.event.EventHooks.onPistonMovePost(level, pos, direction, (b0 == 0));
  225 |         return true;
  226 |     }
  227 | 
  228 |     public static boolean isPushable(BlockState state, Level level, BlockPos pos, Direction direction, boolean allowDestroyable, Direction connectionDirection) {
  229 |         if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY() || !level.getWorldBorder().isWithinBounds(pos)) {
  230 |             return false;
  231 |         } else if (state.isAir()) {
  232 |             return true;
  233 |         } else if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.RESPAWN_ANCHOR) || state.is(Blocks.REINFORCED_DEEPSLATE)) {
  234 |             return false;
  235 |         } else if (direction == Direction.DOWN && pos.getY() == level.getMinY()) {
  236 |             return false;
  237 |         } else if (direction == Direction.UP && pos.getY() == level.getMaxY()) {
  238 |             return false;
  239 |         } else {
  240 |             if (!state.is(Blocks.PISTON) && !state.is(Blocks.STICKY_PISTON)) {
  241 |                 if (state.getDestroySpeed(level, pos) == -1.0F) {
  242 |                     return false;
  243 |                 }
  244 | 
  245 |                 switch (state.getPistonPushReaction()) {
  246 |                     case BLOCK:
  247 |                         return false;
  248 |                     case DESTROY:
  249 |                         return allowDestroyable;
  250 |                     case PUSH_ONLY:
  251 |                         return direction == connectionDirection;
  252 |                 }
  253 |             } else if (state.getValue(EXTENDED)) {
  254 |                 return false;
  255 |             }
  256 | 
  257 |             return !state.hasBlockEntity();
  258 |         }
  259 |     }
  260 | 
  261 |     private boolean moveBlocks(Level level, BlockPos pistonPos, Direction direction, boolean extending) {
  262 |         BlockPos armPos = pistonPos.relative(direction);
  263 |         if (!extending && level.getBlockState(armPos).is(Blocks.PISTON_HEAD)) {
  264 |             level.setBlock(armPos, Blocks.AIR.defaultBlockState(), 276);
  265 |         }
  266 | 
  267 |         PistonStructureResolver resolver = new PistonStructureResolver(level, pistonPos, direction, extending);
  268 |         if (!resolver.resolve()) {
  269 |             return false;
  270 |         } else {
  271 |             Map<BlockPos, BlockState> deleteAfterMove = Maps.newHashMap();
  272 |             List<BlockPos> toPush = resolver.getToPush();
  273 |             List<BlockState> toPushShapes = Lists.newArrayList();
  274 | 
  275 |             for (BlockPos pos : toPush) {
  276 |                 BlockState state = level.getBlockState(pos);
  277 |                 toPushShapes.add(state);
  278 |                 deleteAfterMove.put(pos, state);
  279 |             }
  280 | 
  281 |             List<BlockPos> toDestroy = resolver.getToDestroy();
  282 |             BlockState[] toUpdate = new BlockState[toPush.size() + toDestroy.size()];
  283 |             Direction pushDirection = extending ? direction : direction.getOpposite();
  284 |             int updateIndex = 0;
  285 | 
  286 |             for (int i = toDestroy.size() - 1; i >= 0; i--) {
  287 |                 BlockPos pos = toDestroy.get(i);
  288 |                 BlockState state = level.getBlockState(pos);
  289 |                 BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
  290 |                 dropResources(state, level, pos, blockEntity);
  291 |                 if (!state.is(BlockTags.FIRE) && level.isClientSide()) {
  292 |                     level.levelEvent(2001, pos, getId(state));
  293 |                 }
  294 | 
  295 |                 state.onDestroyedByPushReaction(level, pos, direction, level.getFluidState(pos));
  296 |                 toUpdate[updateIndex++] = state;
  297 |             }
  298 | 
  299 |             for (int i = toPush.size() - 1; i >= 0; i--) {
  300 |                 BlockPos pos = toPush.get(i);
  301 |                 BlockState blockState = level.getBlockState(pos);
  302 |                 pos = pos.relative(pushDirection);
  303 |                 deleteAfterMove.remove(pos);
  304 |                 BlockState actualState = Blocks.MOVING_PISTON.defaultBlockState().setValue(FACING, direction);
  305 |                 level.setBlock(pos, actualState, 324);
  306 |                 level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(pos, actualState, toPushShapes.get(i), direction, extending, false));
  307 |                 toUpdate[updateIndex++] = blockState;
  308 |             }
  309 | 
  310 |             if (extending) {
  311 |                 PistonType type = this.isSticky ? PistonType.STICKY : PistonType.DEFAULT;
  312 |                 BlockState state = Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, direction).setValue(PistonHeadBlock.TYPE, type);
  313 |                 BlockState blockState = Blocks.MOVING_PISTON
  314 |                     .defaultBlockState()
  315 |                     .setValue(MovingPistonBlock.FACING, direction)
  316 |                     .setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);
  317 |                 deleteAfterMove.remove(armPos);
  318 |                 level.setBlock(armPos, blockState, 324);
  319 |                 level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(armPos, blockState, state, direction, true, true));
  320 |             }
  321 | 
  322 |             BlockState air = Blocks.AIR.defaultBlockState();
  323 | 
  324 |             for (BlockPos pos : deleteAfterMove.keySet()) {
  325 |                 level.setBlock(pos, air, 82);
  326 |             }
  327 | 
  328 |             for (Entry<BlockPos, BlockState> entry : deleteAfterMove.entrySet()) {
  329 |                 BlockPos pos = entry.getKey();
  330 |                 BlockState oldState = entry.getValue();
  331 |                 oldState.updateIndirectNeighbourShapes(level, pos, 2);
  332 |                 air.updateNeighbourShapes(level, pos, 2);
  333 |                 air.updateIndirectNeighbourShapes(level, pos, 2);
  334 |             }
  335 | 
  336 |             Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, resolver.getPushDirection(), null);
  337 |             updateIndex = 0;
  338 | 
  339 |             for (int i = toDestroy.size() - 1; i >= 0; i--) {
  340 |                 BlockState state = toUpdate[updateIndex++];
  341 |                 BlockPos pos = toDestroy.get(i);
  342 |                 if (level instanceof ServerLevel serverLevel) {
  343 |                     state.affectNeighborsAfterRemoval(serverLevel, pos, false);
  344 |                 }
  345 | 
  346 |                 state.updateIndirectNeighbourShapes(level, pos, 2);
  347 |                 level.updateNeighborsAt(pos, state.getBlock(), orientation);
  348 |             }
  349 | 
  350 |             for (int i = toPush.size() - 1; i >= 0; i--) {
```

### `net/minecraft/world/level/block/piston/PistonMovingBlockEntity.java`

来源：.109 参考源码；SHA-256：`f402eaabd47e287741b775dd3fb68717e11e06609959f4843a00df59f596489a`

原文件 L275–L402：
```java
  275 |             this.progress = 1.0F;
  276 |             this.progressO = this.progress;
  277 |             this.level.removeBlockEntity(this.worldPosition);
  278 |             this.setRemoved();
  279 |             if (this.level.getBlockState(this.worldPosition).is(Blocks.MOVING_PISTON)) {
  280 |                 BlockState newState;
  281 |                 if (this.isSourcePiston) {
  282 |                     newState = Blocks.AIR.defaultBlockState();
  283 |                 } else {
  284 |                     newState = Block.updateFromNeighbourShapes(this.movedState, this.level, this.worldPosition);
  285 |                 }
  286 | 
  287 |                 this.level.setBlock(this.worldPosition, newState, 3);
  288 |                 this.level
  289 |                     .neighborChanged(
  290 |                         this.worldPosition, newState.getBlock(), ExperimentalRedstoneUtils.initialOrientation(this.level, this.getPushDirection(), null)
  291 |                     );
  292 |             }
  293 |         }
  294 |     }
  295 | 
  296 |     @Override
  297 |     public void preRemoveSideEffects(BlockPos pos, BlockState state) {
  298 |         this.finalTick();
  299 |     }
  300 | 
  301 |     public Direction getPushDirection() {
  302 |         return this.extending ? this.direction : this.direction.getOpposite();
  303 |     }
  304 | 
  305 |     public static void tick(Level level, BlockPos pos, BlockState state, PistonMovingBlockEntity entity) {
  306 |         entity.lastTicked = level.getGameTime();
  307 |         entity.progressO = entity.progress;
  308 |         if (entity.progressO >= 1.0F) {
  309 |             if (level.isClientSide() && entity.deathTicks < 5) {
  310 |                 entity.deathTicks++;
  311 |             } else {
  312 |                 level.removeBlockEntity(pos);
  313 |                 entity.setRemoved();
  314 |                 if (level.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
  315 |                     BlockState newState = Block.updateFromNeighbourShapes(entity.movedState, level, pos);
  316 |                     if (newState.isAir()) {
  317 |                         level.setBlock(pos, entity.movedState, 340);
  318 |                         Block.updateOrDestroy(entity.movedState, newState, level, pos, 3);
  319 |                     } else {
  320 |                         if (newState.hasProperty(BlockStateProperties.WATERLOGGED) && newState.getValue(BlockStateProperties.WATERLOGGED)) {
  321 |                             newState = newState.setValue(BlockStateProperties.WATERLOGGED, false);
  322 |                         }
  323 | 
  324 |                         level.setBlock(pos, newState, 67);
  325 |                         level.neighborChanged(pos, newState.getBlock(), ExperimentalRedstoneUtils.initialOrientation(level, entity.getPushDirection(), null));
  326 |                     }
  327 |                 }
  328 |             }
  329 |         } else {
  330 |             float newProgress = entity.progress + 0.5F;
  331 |             moveCollidedEntities(level, pos, newProgress, entity);
  332 |             moveStuckEntities(level, pos, newProgress, entity);
  333 |             entity.progress = newProgress;
  334 |             if (entity.progress >= 1.0F) {
  335 |                 entity.progress = 1.0F;
  336 |             }
  337 |         }
  338 |     }
  339 | 
  340 |     @Override
  341 |     protected void loadAdditional(ValueInput input) {
  342 |         super.loadAdditional(input);
  343 |         this.movedState = input.read("blockState", BlockState.CODEC).orElse(DEFAULT_BLOCK_STATE);
  344 |         this.direction = input.read("facing", Direction.LEGACY_ID_CODEC).orElse(Direction.DOWN);
  345 |         this.progress = input.getFloatOr("progress", 0.0F);
  346 |         this.progressO = this.progress;
  347 |         this.extending = input.getBooleanOr("extending", false);
  348 |         this.isSourcePiston = input.getBooleanOr("source", false);
  349 |     }
  350 | 
  351 |     @Override
  352 |     protected void saveAdditional(ValueOutput output) {
  353 |         super.saveAdditional(output);
  354 |         output.store("blockState", BlockState.CODEC, this.movedState);
  355 |         output.store("facing", Direction.LEGACY_ID_CODEC, this.direction);
  356 |         output.putFloat("progress", this.progressO);
  357 |         output.putBoolean("extending", this.extending);
  358 |         output.putBoolean("source", this.isSourcePiston);
  359 |     }
  360 | 
  361 |     public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos) {
  362 |         VoxelShape pistonHeadShape;
  363 |         if (!this.extending && this.isSourcePiston && this.movedState.getBlock() instanceof PistonBaseBlock) {
  364 |             pistonHeadShape = this.movedState.setValue(PistonBaseBlock.EXTENDED, true).getCollisionShape(level, pos);
  365 |         } else {
  366 |             pistonHeadShape = Shapes.empty();
  367 |         }
  368 | 
  369 |         Direction noClipDirection = NOCLIP.get();
  370 |         if (this.progress < 1.0 && noClipDirection == this.getMovementDirection()) {
  371 |             return pistonHeadShape;
  372 |         } else {
  373 |             BlockState blockState;
  374 |             if (this.isSourcePiston()) {
  375 |                 blockState = Blocks.PISTON_HEAD
  376 |                     .defaultBlockState()
  377 |                     .setValue(PistonHeadBlock.FACING, this.direction)
  378 |                     .setValue(PistonHeadBlock.SHORT, this.extending != 1.0F - this.progress < 0.25F);
  379 |             } else {
  380 |                 blockState = this.movedState;
  381 |             }
  382 | 
  383 |             float extendedProgress = this.getExtendedProgress(this.progress);
  384 |             double dx = this.direction.getStepX() * extendedProgress;
  385 |             double dy = this.direction.getStepY() * extendedProgress;
  386 |             double dz = this.direction.getStepZ() * extendedProgress;
  387 |             return Shapes.or(pistonHeadShape, blockState.getCollisionShape(level, pos).move(dx, dy, dz));
  388 |         }
  389 |     }
  390 | 
  391 |     public long getLastTicked() {
  392 |         return this.lastTicked;
  393 |     }
  394 | 
  395 |     @Override
  396 |     public void setLevel(Level level) {
  397 |         super.setLevel(level);
  398 |         if (level.holderLookup(Registries.BLOCK).get(this.movedState.getBlock().builtInRegistryHolder().key()).isEmpty()) {
  399 |             this.movedState = Blocks.AIR.defaultBlockState();
  400 |         }
  401 |     }
  402 | }
```

### `net/minecraft/world/level/block/entity/SignBlockEntity.java`

来源：.109 参考源码；SHA-256：`513d1a839794e4cf8e572786f676e0d16348a2ba48820ddf3e0704cf9e4280ad`

原文件 L1–L280：
```java
    1 | package net.minecraft.world.level.block.entity;
    2 | 
    3 | import com.mojang.brigadier.exceptions.CommandSyntaxException;
    4 | import com.mojang.logging.LogUtils;
    5 | import java.util.List;
    6 | import java.util.UUID;
    7 | import java.util.function.UnaryOperator;
    8 | import net.minecraft.commands.CommandSource;
    9 | import net.minecraft.commands.CommandSourceStack;
   10 | import net.minecraft.core.BlockPos;
   11 | import net.minecraft.core.HolderLookup;
   12 | import net.minecraft.nbt.CompoundTag;
   13 | import net.minecraft.network.chat.ClickEvent;
   14 | import net.minecraft.network.chat.Component;
   15 | import net.minecraft.network.chat.ComponentUtils;
   16 | import net.minecraft.network.chat.ResolutionContext;
   17 | import net.minecraft.network.chat.Style;
   18 | import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
   19 | import net.minecraft.server.level.ServerLevel;
   20 | import net.minecraft.server.network.FilteredText;
   21 | import net.minecraft.server.permissions.LevelBasedPermissionSet;
   22 | import net.minecraft.sounds.SoundEvent;
   23 | import net.minecraft.sounds.SoundEvents;
   24 | import net.minecraft.util.Mth;
   25 | import net.minecraft.world.entity.player.Player;
   26 | import net.minecraft.world.level.Level;
   27 | import net.minecraft.world.level.block.SignBlock;
   28 | import net.minecraft.world.level.block.state.BlockState;
   29 | import net.minecraft.world.level.storage.ValueInput;
   30 | import net.minecraft.world.level.storage.ValueOutput;
   31 | import net.minecraft.world.phys.Vec2;
   32 | import net.minecraft.world.phys.Vec3;
   33 | import org.jspecify.annotations.Nullable;
   34 | import org.slf4j.Logger;
   35 | 
   36 | public class SignBlockEntity extends BlockEntity {
   37 |     private static final Logger LOGGER = LogUtils.getLogger();
   38 |     private static final int MAX_TEXT_LINE_WIDTH = 90;
   39 |     private static final int TEXT_LINE_HEIGHT = 10;
   40 |     private static final boolean DEFAULT_IS_WAXED = false;
   41 |     private @Nullable UUID playerWhoMayEdit;
   42 |     private SignText frontText;
   43 |     private SignText backText;
   44 |     private boolean isWaxed = false;
   45 | 
   46 |     public SignBlockEntity(BlockPos worldPosition, BlockState blockState) {
   47 |         this(BlockEntityType.SIGN, worldPosition, blockState);
   48 |     }
   49 | 
   50 |     public SignBlockEntity(BlockEntityType<? extends SignBlockEntity> type, BlockPos worldPosition, BlockState blockState) {
   51 |         super(type, worldPosition, blockState);
   52 |         this.frontText = this.createDefaultSignText();
   53 |         this.backText = this.createDefaultSignText();
   54 |     }
   55 | 
   56 |     protected SignText createDefaultSignText() {
   57 |         return new SignText();
   58 |     }
   59 | 
   60 |     public boolean isFacingFrontText(Player player) {
   61 |         if (this.getBlockState().getBlock() instanceof SignBlock sign) {
   62 |             Vec3 signPositionOffset = sign.getSignHitboxCenterPosition(this.getBlockState());
   63 |             double xd = player.getX() - (this.getBlockPos().getX() + signPositionOffset.x);
   64 |             double zd = player.getZ() - (this.getBlockPos().getZ() + signPositionOffset.z);
   65 |             float signYRot = sign.getYRotationDegrees(this.getBlockState());
   66 |             float playerYRot = (float)(Mth.atan2(zd, xd) * 180.0F / (float)Math.PI) - 90.0F;
   67 |             return Mth.degreesDifferenceAbs(signYRot, playerYRot) <= 90.0F;
   68 |         } else {
   69 |             return false;
   70 |         }
   71 |     }
   72 | 
   73 |     public SignText getText(boolean isFrontText) {
   74 |         return isFrontText ? this.frontText : this.backText;
   75 |     }
   76 | 
   77 |     public SignText getFrontText() {
   78 |         return this.frontText;
   79 |     }
   80 | 
   81 |     public SignText getBackText() {
   82 |         return this.backText;
   83 |     }
   84 | 
   85 |     public int getTextLineHeight() {
   86 |         return 10;
   87 |     }
   88 | 
   89 |     public int getMaxTextLineWidth() {
   90 |         return 90;
   91 |     }
   92 | 
   93 |     @Override
   94 |     protected void saveAdditional(ValueOutput output) {
   95 |         super.saveAdditional(output);
   96 |         output.store("front_text", SignText.DIRECT_CODEC, this.frontText);
   97 |         output.store("back_text", SignText.DIRECT_CODEC, this.backText);
   98 |         output.putBoolean("is_waxed", this.isWaxed);
   99 |     }
  100 | 
  101 |     @Override
  102 |     protected void loadAdditional(ValueInput input) {
  103 |         super.loadAdditional(input);
  104 |         this.frontText = input.read("front_text", SignText.DIRECT_CODEC).map(this::loadLines).orElseGet(SignText::new);
  105 |         this.backText = input.read("back_text", SignText.DIRECT_CODEC).map(this::loadLines).orElseGet(SignText::new);
  106 |         this.isWaxed = input.getBooleanOr("is_waxed", false);
  107 |     }
  108 | 
  109 |     private SignText loadLines(SignText data) {
  110 |         for (int i = 0; i < 4; i++) {
  111 |             Component unfilteredMessage = this.loadLine(data.getMessage(i, false));
  112 |             Component filteredMessage = this.loadLine(data.getMessage(i, true));
  113 |             data = data.setMessage(i, unfilteredMessage, filteredMessage);
  114 |         }
  115 | 
  116 |         return data;
  117 |     }
  118 | 
  119 |     private Component loadLine(Component component) {
  120 |         if (this.level instanceof ServerLevel serverLevel) {
  121 |             try {
  122 |                 return ComponentUtils.resolve(ResolutionContext.create(createCommandSourceStack(null, serverLevel, this.worldPosition)), component);
  123 |             } catch (CommandSyntaxException var4) {
  124 |             }
  125 |         }
  126 | 
  127 |         return component;
  128 |     }
  129 | 
  130 |     public void updateSignText(Player player, boolean frontText, List<FilteredText> lines) {
  131 |         if (!this.isWaxed() && player.getUUID().equals(this.getPlayerWhoMayEdit()) && this.level != null) {
  132 |             this.updateText(text -> this.setMessages(player, lines, text), frontText);
  133 |             this.setAllowedPlayerEditor(null);
  134 |             this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
  135 |         } else {
  136 |             LOGGER.warn("Player {} just tried to change non-editable sign", player.getPlainTextName());
  137 |         }
  138 |     }
  139 | 
  140 |     public boolean updateText(UnaryOperator<SignText> function, boolean isFrontText) {
  141 |         SignText text = this.getText(isFrontText);
  142 |         return this.setText(function.apply(text), isFrontText);
  143 |     }
  144 | 
  145 |     private SignText setMessages(Player player, List<FilteredText> lines, SignText text) {
  146 |         for (int i = 0; i < lines.size(); i++) {
  147 |             FilteredText line = lines.get(i);
  148 |             Style currentTextStyle = text.getMessage(i, player.isTextFilteringEnabled()).getStyle();
  149 |             if (player.isTextFilteringEnabled()) {
  150 |                 text = text.setMessage(i, Component.literal(line.filteredOrEmpty()).setStyle(currentTextStyle));
  151 |             } else {
  152 |                 text = text.setMessage(
  153 |                     i, Component.literal(line.raw()).setStyle(currentTextStyle), Component.literal(line.filteredOrEmpty()).setStyle(currentTextStyle)
  154 |                 );
  155 |             }
  156 |         }
  157 | 
  158 |         return text;
  159 |     }
  160 | 
  161 |     public boolean setText(SignText text, boolean isFrontText) {
  162 |         return isFrontText ? this.setFrontText(text) : this.setBackText(text);
  163 |     }
  164 | 
  165 |     private boolean setBackText(SignText text) {
  166 |         if (text != this.backText) {
  167 |             this.backText = text;
  168 |             this.markUpdated();
  169 |             return true;
  170 |         } else {
  171 |             return false;
  172 |         }
  173 |     }
  174 | 
  175 |     private boolean setFrontText(SignText text) {
  176 |         if (text != this.frontText) {
  177 |             this.frontText = text;
  178 |             this.markUpdated();
  179 |             return true;
  180 |         } else {
  181 |             return false;
  182 |         }
  183 |     }
  184 | 
  185 |     public boolean canExecuteClickCommands(boolean isFrontText, Player player) {
  186 |         return this.isWaxed() && this.getText(isFrontText).hasAnyClickCommands(player);
  187 |     }
  188 | 
  189 |     public boolean executeClickCommandsIfPresent(ServerLevel level, Player player, BlockPos pos, boolean isFrontText) {
  190 |         boolean hasAnyClickCommand = false;
  191 | 
  192 |         for (Component message : this.getText(isFrontText).getMessages(player.isTextFilteringEnabled())) {
  193 |             Style style = message.getStyle();
  194 |             switch (style.getClickEvent()) {
  195 |                 case ClickEvent.RunCommand command:
  196 |                     level.getServer().getCommands().performPrefixedCommand(createCommandSourceStack(player, level, pos), command.command());
  197 |                     hasAnyClickCommand = true;
  198 |                     break;
  199 |                 case ClickEvent.ShowDialog dialog:
  200 |                     player.openDialog(dialog.dialog());
  201 |                     hasAnyClickCommand = true;
  202 |                     break;
  203 |                 case ClickEvent.Custom custom:
  204 |                     level.getServer().handleCustomClickAction(custom.id(), custom.payload(), (net.minecraft.server.level.ServerPlayer) player, player.getGameProfile());
  205 |                     hasAnyClickCommand = true;
  206 |                     break;
  207 |                 case null:
  208 |                 default:
  209 |             }
  210 |         }
  211 | 
  212 |         return hasAnyClickCommand;
  213 |     }
  214 | 
  215 |     private static CommandSourceStack createCommandSourceStack(@Nullable Player player, ServerLevel level, BlockPos pos) {
  216 |         String textName = player == null ? "Sign" : player.getPlainTextName();
  217 |         Component displayName = (Component)(player == null ? Component.literal("Sign") : player.getDisplayName());
  218 |         return new CommandSourceStack(
  219 |             CommandSource.NULL, Vec3.atCenterOf(pos), Vec2.ZERO, level, LevelBasedPermissionSet.GAMEMASTER, textName, displayName, level.getServer(), player
  220 |         );
  221 |     }
  222 | 
  223 |     public ClientboundBlockEntityDataPacket getUpdatePacket() {
  224 |         return ClientboundBlockEntityDataPacket.create(this);
  225 |     }
  226 | 
  227 |     @Override
  228 |     public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
  229 |         return this.saveCustomOnly(registries);
  230 |     }
  231 | 
  232 |     public void setAllowedPlayerEditor(@Nullable UUID playerUUID) {
  233 |         this.playerWhoMayEdit = playerUUID;
  234 |     }
  235 | 
  236 |     public @Nullable UUID getPlayerWhoMayEdit() {
  237 |         return this.playerWhoMayEdit;
  238 |     }
  239 | 
  240 |     private void markUpdated() {
  241 |         this.setChanged();
  242 |         this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
  243 |     }
  244 | 
  245 |     public boolean isWaxed() {
  246 |         return this.isWaxed;
  247 |     }
  248 | 
  249 |     public boolean setWaxed(boolean isWaxed) {
  250 |         if (this.isWaxed != isWaxed) {
  251 |             this.isWaxed = isWaxed;
  252 |             this.markUpdated();
  253 |             return true;
  254 |         } else {
  255 |             return false;
  256 |         }
  257 |     }
  258 | 
  259 |     public boolean playerIsTooFarAwayToEdit(UUID player) {
  260 |         Player editingPlayer = this.level.getPlayerByUUID(player);
  261 |         return editingPlayer == null || !editingPlayer.isWithinBlockInteractionRange(this.getBlockPos(), 4.0);
  262 |     }
  263 | 
  264 |     public static void tick(Level level, BlockPos blockPos, BlockState blockState, SignBlockEntity signBlockEntity) {
  265 |         UUID playerWhoMayEdit = signBlockEntity.getPlayerWhoMayEdit();
  266 |         if (playerWhoMayEdit != null) {
  267 |             signBlockEntity.clearInvalidPlayerWhoMayEdit(signBlockEntity, level, playerWhoMayEdit);
  268 |         }
  269 |     }
  270 | 
  271 |     private void clearInvalidPlayerWhoMayEdit(SignBlockEntity signBlockEntity, Level level, UUID playerWhoMayEdit) {
  272 |         if (signBlockEntity.playerIsTooFarAwayToEdit(playerWhoMayEdit)) {
  273 |             signBlockEntity.setAllowedPlayerEditor(null);
  274 |         }
  275 |     }
  276 | 
  277 |     public SoundEvent getSignInteractionFailedSoundEvent() {
  278 |         return SoundEvents.WAXED_SIGN_INTERACT_FAIL;
  279 |     }
  280 | }
```

### `net/minecraft/world/level/block/ChestBlock.java`

来源：.109 参考源码；SHA-256：`554a699c28a9a5444ee44af6304195eae1d19739d8a86172c9f3cc412b64ca23`

原文件 L350–L400：
```java
  350 | 
  351 |         return false;
  352 |     }
  353 | 
  354 |     @Override
  355 |     protected boolean hasAnalogOutputSignal(BlockState state) {
  356 |         return true;
  357 |     }
  358 | 
  359 |     @Override
  360 |     protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
  361 |         return AbstractContainerMenu.getRedstoneSignalFromContainer(getContainer(this, state, level, pos, false));
  362 |     }
  363 | 
  364 |     @Override
  365 |     protected BlockState rotate(BlockState state, Rotation rotation) {
  366 |         return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
  367 |     }
  368 | 
  369 |     @Override
  370 |     protected BlockState mirror(BlockState state, Mirror mirror) {
  371 |         BlockState rotated = state.rotate(mirror.getRotation(state.getValue(FACING)));
  372 |         return mirror == Mirror.NONE ? rotated : rotated.setValue(TYPE, rotated.getValue(TYPE).getOpposite());  // Forge: Fixed MC-134110 Structure mirroring breaking apart double chests
  373 |     }
  374 | 
  375 |     @Override
  376 |     protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
  377 |         builder.add(FACING, TYPE, WATERLOGGED);
  378 |     }
  379 | 
  380 |     @Override
  381 |     protected boolean isPathfindable(BlockState state, PathComputationType type) {
  382 |         return false;
  383 |     }
  384 | 
  385 |     @Override
  386 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
  387 |         BlockEntity blockEntity = level.getBlockEntity(pos);
  388 |         if (blockEntity instanceof ChestBlockEntity) {
  389 |             ((ChestBlockEntity)blockEntity).recheckOpen();
  390 |         }
  391 |     }
  392 | 
  393 |     public SoundEvent getOpenChestSound() {
  394 |         return this.openSound;
  395 |     }
  396 | 
  397 |     public SoundEvent getCloseChestSound() {
  398 |         return this.closeSound;
  399 |     }
  400 | }
```

### `net/minecraft/world/level/block/entity/ChestBlockEntity.java`

来源：.109 参考源码；SHA-256：`8816a4ec8f62098ad5cb8a7aef69aa000eb6b608869d6495befee0788243c72e`

原文件 L1–L216：
```java
    1 | package net.minecraft.world.level.block.entity;
    2 | 
    3 | import java.util.List;
    4 | import java.util.Objects;
    5 | import net.minecraft.core.BlockPos;
    6 | import net.minecraft.core.Direction;
    7 | import net.minecraft.core.NonNullList;
    8 | import net.minecraft.network.chat.Component;
    9 | import net.minecraft.sounds.SoundEvent;
   10 | import net.minecraft.sounds.SoundSource;
   11 | import net.minecraft.world.CompoundContainer;
   12 | import net.minecraft.world.Container;
   13 | import net.minecraft.world.ContainerHelper;
   14 | import net.minecraft.world.entity.ContainerUser;
   15 | import net.minecraft.world.entity.player.Inventory;
   16 | import net.minecraft.world.entity.player.Player;
   17 | import net.minecraft.world.inventory.AbstractContainerMenu;
   18 | import net.minecraft.world.inventory.ChestMenu;
   19 | import net.minecraft.world.item.ItemStack;
   20 | import net.minecraft.world.level.BlockGetter;
   21 | import net.minecraft.world.level.Level;
   22 | import net.minecraft.world.level.block.Block;
   23 | import net.minecraft.world.level.block.ChestBlock;
   24 | import net.minecraft.world.level.block.state.BlockState;
   25 | import net.minecraft.world.level.block.state.properties.ChestType;
   26 | import net.minecraft.world.level.storage.ValueInput;
   27 | import net.minecraft.world.level.storage.ValueOutput;
   28 | 
   29 | public class ChestBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
   30 |     private static final int EVENT_SET_OPEN_COUNT = 1;
   31 |     private static final Component DEFAULT_NAME = Component.translatable("container.chest");
   32 |     private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
   33 |     private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
   34 |         {
   35 |             Objects.requireNonNull(ChestBlockEntity.this);
   36 |         }
   37 | 
   38 |         @Override
   39 |         protected void onOpen(Level level, BlockPos pos, BlockState blockState) {
   40 |             if (blockState.getBlock() instanceof ChestBlock chestBlock) {
   41 |                 ChestBlockEntity.playSound(level, pos, blockState, chestBlock.getOpenChestSound());
   42 |             }
   43 |         }
   44 | 
   45 |         @Override
   46 |         protected void onClose(Level level, BlockPos pos, BlockState blockState) {
   47 |             if (blockState.getBlock() instanceof ChestBlock chestBlock) {
   48 |                 ChestBlockEntity.playSound(level, pos, blockState, chestBlock.getCloseChestSound());
   49 |             }
   50 |         }
   51 | 
   52 |         @Override
   53 |         protected void openerCountChanged(Level level, BlockPos pos, BlockState blockState, int previous, int current) {
   54 |             ChestBlockEntity.this.signalOpenCount(level, pos, blockState, previous, current);
   55 |         }
   56 | 
   57 |         @Override
   58 |         public boolean isOwnContainer(Player player) {
   59 |             if (!(player.containerMenu instanceof ChestMenu)) {
   60 |                 return false;
   61 |             } else {
   62 |                 Container container = ((ChestMenu)player.containerMenu).getContainer();
   63 |                 return container == ChestBlockEntity.this
   64 |                     || container instanceof CompoundContainer && ((CompoundContainer)container).contains(ChestBlockEntity.this);
   65 |             }
   66 |         }
   67 |     };
   68 |     private final ChestLidController chestLidController = new ChestLidController();
   69 | 
   70 |     protected ChestBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState) {
   71 |         super(type, worldPosition, blockState);
   72 |     }
   73 | 
   74 |     public ChestBlockEntity(BlockPos worldPosition, BlockState blockState) {
   75 |         this(BlockEntityType.CHEST, worldPosition, blockState);
   76 |     }
   77 | 
   78 |     @Override
   79 |     public int getContainerSize() {
   80 |         return 27;
   81 |     }
   82 | 
   83 |     @Override
   84 |     protected Component getDefaultName() {
   85 |         return DEFAULT_NAME;
   86 |     }
   87 | 
   88 |     @Override
   89 |     protected void loadAdditional(ValueInput input) {
   90 |         super.loadAdditional(input);
   91 |         this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
   92 |         if (!this.tryLoadLootTable(input)) {
   93 |             ContainerHelper.loadAllItems(input, this.items);
   94 |         }
   95 |     }
   96 | 
   97 |     @Override
   98 |     protected void saveAdditional(ValueOutput output) {
   99 |         super.saveAdditional(output);
  100 |         if (!this.trySaveLootTable(output)) {
  101 |             ContainerHelper.saveAllItems(output, this.items);
  102 |         }
  103 |     }
  104 | 
  105 |     public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, ChestBlockEntity entity) {
  106 |         entity.chestLidController.tickLid();
  107 |     }
  108 | 
  109 |     private static void playSound(Level level, BlockPos worldPosition, BlockState blockState, SoundEvent event) {
  110 |         ChestType type = blockState.getValue(ChestBlock.TYPE);
  111 |         if (type != ChestType.LEFT) {
  112 |             double x = worldPosition.getX() + 0.5;
  113 |             double y = worldPosition.getY() + 0.5;
  114 |             double z = worldPosition.getZ() + 0.5;
  115 |             if (type == ChestType.RIGHT) {
  116 |                 Direction direction = ChestBlock.getConnectedDirection(blockState);
  117 |                 x += direction.getStepX() * 0.5;
  118 |                 z += direction.getStepZ() * 0.5;
  119 |             }
  120 | 
  121 |             level.playSound(null, x, y, z, event, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
  122 |         }
  123 |     }
  124 | 
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
  150 |     }
  151 | 
  152 |     @Override
  153 |     public List<ContainerUser> getEntitiesWithContainerOpen() {
  154 |         return this.openersCounter.getEntitiesWithContainerOpen(this.getLevel(), this.getBlockPos());
  155 |     }
  156 | 
  157 |     @Override
  158 |     protected NonNullList<ItemStack> getItems() {
  159 |         return this.items;
  160 |     }
  161 | 
  162 |     @Override
  163 |     protected void setItems(NonNullList<ItemStack> items) {
  164 |         this.items = items;
  165 |     }
  166 | 
  167 |     @Override
  168 |     public float getOpenNess(float a) {
  169 |         return this.chestLidController.getOpenness(a);
  170 |     }
  171 | 
  172 |     public static int getOpenCount(BlockGetter level, BlockPos pos) {
  173 |         BlockState state = level.getBlockState(pos);
  174 |         if (state.hasBlockEntity()) {
  175 |             BlockEntity blockEntity = level.getBlockEntity(pos);
  176 |             if (blockEntity instanceof ChestBlockEntity) {
  177 |                 return ((ChestBlockEntity)blockEntity).openersCounter.getOpenerCount();
  178 |             }
  179 |         }
  180 | 
  181 |         return 0;
  182 |     }
  183 | 
  184 |     public static void swapContents(ChestBlockEntity one, ChestBlockEntity two) {
  185 |         NonNullList<ItemStack> items = one.getItems();
  186 |         one.setItems(two.getItems());
  187 |         two.setItems(items);
  188 |     }
  189 | 
  190 |     @Override
  191 |     protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
  192 |         return ChestMenu.threeRows(containerId, inventory, this);
  193 |     }
  194 | 
  195 |     @Override
  196 |     public void setBlockState(BlockState blockState) {
  197 |         var oldState = getBlockState();
  198 |         super.setBlockState(blockState);
  199 |         // Neo: Chest state change might change the chest item handler -> invalidate
  200 |         if ((oldState.getValue(ChestBlock.FACING) != blockState.getValue(ChestBlock.FACING))
  201 |                 || (oldState.getValue(ChestBlock.TYPE) != blockState.getValue(ChestBlock.TYPE))) {
  202 |             this.invalidateCapabilities();
  203 |         }
  204 |     }
  205 | 
  206 |     public void recheckOpen() {
  207 |         if (!this.remove) {
  208 |             this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
  209 |         }
  210 |     }
  211 | 
  212 |     protected void signalOpenCount(Level level, BlockPos pos, BlockState blockState, int previous, int current) {
  213 |         Block block = blockState.getBlock();
  214 |         level.blockEvent(pos, block, 1, current);
  215 |     }
  216 | }
```

### `targets/neoforge-26.1/src/main/java/cc/sighs/dndturn/platform/server/presentation/ChestPresentation.java`

来源：当前项目；SHA-256：`5079fb57db42dc02e54d96dc8d9701533c0fc4993ec8a779d8da0489bf0749cb`

原文件 L1–L24：
```java
    1 | package cc.sighs.dndturn.combat;
    2 | 
    3 | import net.minecraft.core.BlockPos;
    4 | import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
    5 | import net.minecraft.server.level.ServerLevel;
    6 | import net.minecraft.world.level.block.Block;
    7 | import net.minecraft.world.level.block.Blocks;
    8 | import net.minecraft.world.level.block.entity.ChestBlockEntity;
    9 | 
   10 | /** Audited normal chest lid signal; trapped/modded chests and simulation events stay queued. */
   11 | public final class ChestPresentation {
   12 |     private ChestPresentation() {}
   13 |     public static boolean accepts(ServerLevel level, BlockPos pos, Block block, int event) {
   14 |         return event == 1 && block == Blocks.CHEST && level.hasChunkAt(pos)
   15 |             && level.getBlockState(pos).is(Blocks.CHEST)
   16 |             && level.getBlockEntity(pos) != null && level.getBlockEntity(pos).getClass() == ChestBlockEntity.class
   17 |             && level.tickRateManager().runsNormally()
   18 |             && MinecraftCombatRuntime.isFormalBlockSimulationPaused(level, pos);
   19 |     }
   20 |     public static void send(ServerLevel level, BlockPos pos, Block block, int count) {
   21 |         level.getServer().getPlayerList().broadcast(null, pos.getX(), pos.getY(), pos.getZ(), 64,
   22 |             level.dimension(), new ClientboundBlockEventPacket(pos, block, 1, count));
   23 |     }
   24 | }
```

<a id="s12"></a>
## S12 — 补充分类样本：从当前 JAR 提取的具体方法

### `net/minecraft/world/level/block/BasePressurePlateBlock.java`

来源：.109 参考源码；SHA-256：`338cec0c841d55c6acd4bf7731b2cef047441b12400de79a0d1e19859f4c17b7`

原文件 L76–L81：
```java
   76 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   77 |         int signal = this.getSignalForState(state);
   78 |         if (signal > 0) {
   79 |             this.checkPressed(null, level, pos, state, signal);
   80 |         }
   81 |     }
```

原文件 L84–L91：
```java
   84 |     protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
   85 |         if (!level.isClientSide()) {
   86 |             int signal = this.getSignalForState(state);
   87 |             if (signal == 0) {
   88 |                 this.checkPressed(entity, level, pos, state, signal);
   89 |             }
   90 |         }
   91 |     }
```

### `net/minecraft/world/level/block/ScaffoldingBlock.java`

来源：.109 参考源码；SHA-256：`56b374bda535ee0b592cf2d2fa07e4a0581546b48e4b991a5ae4ed55a744d24e`

原文件 L117–L129：
```java
  117 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
  118 |         int distance = getDistance(level, pos);
  119 |         BlockState newState = state.setValue(DISTANCE, distance).setValue(BOTTOM, this.isBottom(level, pos, distance));
  120 |         if (newState.getValue(DISTANCE) == 7) {
  121 |             if (state.getValue(DISTANCE) == 7) {
  122 |                 FallingBlockEntity.fall(level, pos, newState);
  123 |             } else {
  124 |                 level.destroyBlock(pos, true);
  125 |             }
  126 |         } else if (state != newState) {
  127 |             level.setBlock(pos, newState, 3);
  128 |         }
  129 |     }
```

### `net/minecraft/world/level/block/ComposterBlock.java`

来源：.109 参考源码；SHA-256：`1f5f560854d7f6d81a8cf1d50503533a8d3ade32f7a07c5b75d15e067de60159`

原文件 L338–L343：
```java
  338 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
  339 |         if (state.getValue(LEVEL) == 7) {
  340 |             level.setBlock(pos, state.cycle(LEVEL), 3);
  341 |             level.playSound(null, pos, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 1.0F, 1.0F);
  342 |         }
  343 |     }
```

### `net/minecraft/world/level/block/ComparatorBlock.java`

来源：.109 参考源码；SHA-256：`017238e8ed7b892fc56f676119ce48cd21e65705e28c16a8b447868a457e7d8d`

原文件 L145–L155：
```java
  145 |     protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
  146 |         if (!level.getBlockTicks().willTickThisTick(pos, this)) {
  147 |             int outputValue = this.calculateOutputSignal(level, pos, state);
  148 |             BlockEntity blockEntity = level.getBlockEntity(pos);
  149 |             int oldValue = blockEntity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity)blockEntity).getOutputSignal() : 0;
  150 |             if (outputValue != oldValue || state.getValue(POWERED) != this.shouldTurnOn(level, pos, state)) {
  151 |                 TickPriority priority = this.shouldPrioritize(level, pos, state) ? TickPriority.HIGH : TickPriority.NORMAL;
  152 |                 level.scheduleTick(pos, this, 2, priority);
  153 |             }
  154 |         }
  155 |     }
```

原文件 L180–L182：
```java
  180 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
  181 |         this.refreshOutputState(level, pos, state);
  182 |     }
```

### `net/minecraft/world/level/block/RedstoneTorchBlock.java`

来源：.109 参考源码；SHA-256：`5e67a53c17fc7eacfa7396654d08e5957702643d9a717b8be40997f2e0f3d947`

原文件 L73–L92：
```java
   73 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   74 |         boolean neighborSignal = this.hasNeighborSignal(level, pos, state);
   75 |         List<RedstoneTorchBlock.Toggle> toggles = RECENT_TOGGLES.get(level);
   76 | 
   77 |         while (toggles != null && !toggles.isEmpty() && level.getGameTime() - toggles.get(0).when > 60L) {
   78 |             toggles.remove(0);
   79 |         }
   80 | 
   81 |         if (state.getValue(LIT)) {
   82 |             if (neighborSignal) {
   83 |                 level.setBlock(pos, state.setValue(LIT, false), 3);
   84 |                 if (isToggledTooFrequently(level, pos, true)) {
   85 |                     level.levelEvent(1502, pos, 0);
   86 |                     level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 160);
   87 |                 }
   88 |             }
   89 |         } else if (!neighborSignal && !isToggledTooFrequently(level, pos, false)) {
   90 |             level.setBlock(pos, state.setValue(LIT, true), 3);
   91 |         }
   92 |     }
```

原文件 L95–L99：
```java
   95 |     protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
   96 |         if (state.getValue(LIT) == this.hasNeighborSignal(level, pos, state) && !level.getBlockTicks().willTickThisTick(pos, this)) {
   97 |             level.scheduleTick(pos, this, 2);
   98 |         }
   99 |     }
```

### `net/minecraft/world/level/block/CrafterBlock.java`

来源：.109 参考源码；SHA-256：`dd665b40d5f6b17625091ae381330bddea48975e8436478608f668de7f6ae1e5`

原文件 L88–L90：
```java
   88 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   89 |         this.dispenseFrom(state, level, pos);
   90 |     }
```

### `net/minecraft/world/level/block/LeavesBlock.java`

来源：.109 参考源码；SHA-256：`1d41ac7bdbf04e30f499462d989fafbf88743afc8236e60d47a91e8e793e6d1f`

原文件 L67–L72：
```java
   67 |     protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   68 |         if (this.decaying(state)) {
   69 |             dropResources(state, level, pos);
   70 |             level.removeBlock(pos, false);
   71 |         }
   72 |     }
```

原文件 L79–L81：
```java
   79 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   80 |         level.setBlock(pos, updateDistance(state, level, pos), 3);
   81 |     }
```

### `net/minecraft/world/level/block/FarmlandBlock.java`

来源：.109 参考源码；SHA-256：`44de41c8a97455ee310d723579b3970dc9fbdc130901eac7ada7f837650a085c`

原文件 L88–L92：
```java
   88 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   89 |         if (!state.canSurvive(level, pos)) {
   90 |             turnToDirt(null, state, level, pos);
   91 |         }
   92 |     }
```

原文件 L95–L106：
```java
   95 |     protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   96 |         int moisture = state.getValue(MOISTURE);
   97 |         if (!isNearWater(level, pos) && !level.isRainingAt(pos.above())) {
   98 |             if (moisture > 0) {
   99 |                 level.setBlock(pos, state.setValue(MOISTURE, moisture - 1), 2);
  100 |             } else if (!shouldMaintainFarmland(level, pos)) {
  101 |                 turnToDirt(null, state, level, pos);
  102 |             }
  103 |         } else if (moisture < 7) {
  104 |             level.setBlock(pos, state.setValue(MOISTURE, 7), 2);
  105 |         }
  106 |     }
```

原文件 L109–L116：
```java
  109 |     public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
  110 |         if (level instanceof ServerLevel serverLevel
  111 |             && net.neoforged.neoforge.common.CommonHooks.onFarmlandTrample(serverLevel, pos, Blocks.DIRT.defaultBlockState(), fallDistance, entity)) { // Neo: Move logic to Entity#canTrample
  112 |             turnToDirt(entity, state, level, pos);
  113 |         }
  114 | 
  115 |         super.fallOn(level, state, pos, entity, fallDistance);
  116 |     }
```

### `net/minecraft/world/level/block/DaylightDetectorBlock.java`

来源：.109 参考源码；SHA-256：`1c606243e303d962875d69f625c5279008c779088a4758ed3119ccb2f230332a`

原文件 L59–L75：
```java
   59 |     private static void updateSignalStrength(BlockState state, Level level, BlockPos pos) {
   60 |         int target = level.getEffectiveSkyBrightness(pos);
   61 |         float sunAngle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, pos) * (float) (Math.PI / 180.0);
   62 |         boolean isInverted = state.getValue(INVERTED);
   63 |         if (isInverted) {
   64 |             target = 15 - target;
   65 |         } else if (target > 0) {
   66 |             float offset = sunAngle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
   67 |             sunAngle += (offset - sunAngle) * 0.2F;
   68 |             target = Math.round(target * Mth.cos(sunAngle));
   69 |         }
   70 | 
   71 |         target = Mth.clamp(target, 0, 15);
   72 |         if (state.getValue(POWER) != target) {
   73 |             level.setBlock(pos, state.setValue(POWER, target), 3);
   74 |         }
   75 |     }
```

原文件 L104–L108：
```java
  104 |     public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState blockState, BlockEntityType<T> type) {
  105 |         return !level.isClientSide() && level.dimensionType().hasSkyLight()
  106 |             ? createTickerHelper(type, BlockEntityType.DAYLIGHT_DETECTOR, DaylightDetectorBlock::tickEntity)
  107 |             : null;
  108 |     }
```

原文件 L110–L114：
```java
  110 |     private static void tickEntity(Level level, BlockPos blockPos, BlockState blockState, DaylightDetectorBlockEntity blockEntity) {
  111 |         if (level.getGameTime() % 20L == 0L) {
  112 |             updateSignalStrength(blockState, level, blockPos);
  113 |         }
  114 |     }
```

### `net/minecraft/world/level/block/entity/BeaconBlockEntity.java`

来源：.109 参考源码；SHA-256：`a66c07be15dbf8c264fe284a15c842d36b6763696664bee6fd64f6cfabaab91a`

原文件 L123–L199：
```java
  123 |     public static void tick(Level level, BlockPos pos, BlockState selfState, BeaconBlockEntity entity) {
  124 |         int x = pos.getX();
  125 |         int y = pos.getY();
  126 |         int z = pos.getZ();
  127 |         BlockPos checkPos;
  128 |         if (entity.lastCheckY < y) {
  129 |             checkPos = pos;
  130 |             entity.checkingBeamSections = Lists.newArrayList();
  131 |             entity.lastCheckY = pos.getY() - 1;
  132 |         } else {
  133 |             checkPos = new BlockPos(x, entity.lastCheckY + 1, z);
  134 |         }
  135 | 
  136 |         BeaconBeamOwner.Section lastBeamSection = entity.checkingBeamSections.isEmpty()
  137 |             ? null
  138 |             : entity.checkingBeamSections.get(entity.checkingBeamSections.size() - 1);
  139 |         int lastSetBlock = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
  140 | 
  141 |         for (int i = 0; i < 10 && checkPos.getY() <= lastSetBlock; i++) {
  142 |             BlockState state = level.getBlockState(checkPos);
  143 |             Integer color = state.getBeaconColorMultiplier(level, checkPos, pos);
  144 |             if (color != null) {
  145 |                 if (entity.checkingBeamSections.size() <= 1) {
  146 |                     lastBeamSection = new BeaconBeamOwner.Section(color);
  147 |                     entity.checkingBeamSections.add(lastBeamSection);
  148 |                 } else if (lastBeamSection != null) {
  149 |                     if (color == lastBeamSection.getColor()) {
  150 |                         lastBeamSection.increaseHeight();
  151 |                     } else {
  152 |                         lastBeamSection = new BeaconBeamOwner.Section(ARGB.average(lastBeamSection.getColor(), color));
  153 |                         entity.checkingBeamSections.add(lastBeamSection);
  154 |                     }
  155 |                 }
  156 |             } else {
  157 |                 if (lastBeamSection == null || state.getLightDampening() >= 15 && !state.is(Blocks.BEDROCK)) {
  158 |                     entity.checkingBeamSections.clear();
  159 |                     entity.lastCheckY = lastSetBlock;
  160 |                     break;
  161 |                 }
  162 | 
  163 |                 lastBeamSection.increaseHeight();
  164 |             }
  165 | 
  166 |             checkPos = checkPos.above();
  167 |             entity.lastCheckY++;
  168 |         }
  169 | 
  170 |         int previousLevels = entity.levels;
  171 |         if (level.getGameTime() % 80L == 0L) {
  172 |             if (!entity.beamSections.isEmpty()) {
  173 |                 entity.levels = updateBase(level, x, y, z);
  174 |             }
  175 | 
  176 |             if (entity.levels > 0 && !entity.beamSections.isEmpty()) {
  177 |                 applyEffects(level, pos, entity.levels, entity.primaryPower, entity.secondaryPower);
  178 |                 playSound(level, pos, SoundEvents.BEACON_AMBIENT);
  179 |             }
  180 |         }
  181 | 
  182 |         if (entity.lastCheckY >= lastSetBlock) {
  183 |             entity.lastCheckY = level.getMinY() - 1;
  184 |             boolean wasActive = previousLevels > 0;
  185 |             entity.beamSections = entity.checkingBeamSections;
  186 |             if (!level.isClientSide()) {
  187 |                 boolean isActive = entity.levels > 0;
  188 |                 if (!wasActive && isActive) {
  189 |                     playSound(level, pos, SoundEvents.BEACON_ACTIVATE);
  190 | 
  191 |                     for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(x, y, z, x, y - 4, z).inflate(10.0, 5.0, 10.0))) {
  192 |                         CriteriaTriggers.CONSTRUCT_BEACON.trigger(player, entity.levels);
  193 |                     }
  194 |                 } else if (wasActive && !isActive) {
  195 |                     playSound(level, pos, SoundEvents.BEACON_DEACTIVATE);
  196 |                 }
  197 |             }
  198 |         }
  199 |     }
```

### `net/minecraft/world/level/block/entity/ShulkerBoxBlockEntity.java`

来源：.109 参考源码；SHA-256：`174e4f360c38de56a614ad2703454a285692c5a0b686f3ab97a995cbb46373dc`

原文件 L62–L64：
```java
   62 |     public static void tick(Level level, BlockPos pos, BlockState state, ShulkerBoxBlockEntity entity) {
   63 |         entity.updateAnimation(level, pos, state);
   64 |     }
```

原文件 L66–L101：
```java
   66 |     private void updateAnimation(Level level, BlockPos pos, BlockState blockState) {
   67 |         this.progressOld = this.progress;
   68 |         switch (this.animationStatus) {
   69 |             case CLOSED:
   70 |                 this.progress = 0.0F;
   71 |                 break;
   72 |             case OPENING:
   73 |                 this.progress += 0.1F;
   74 |                 if (this.progressOld == 0.0F) {
   75 |                     doNeighborUpdates(level, pos, blockState);
   76 |                 }
   77 | 
   78 |                 if (this.progress >= 1.0F) {
   79 |                     this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.OPENED;
   80 |                     this.progress = 1.0F;
   81 |                     doNeighborUpdates(level, pos, blockState);
   82 |                 }
   83 | 
   84 |                 this.moveCollidedEntities(level, pos, blockState);
   85 |                 break;
   86 |             case OPENED:
   87 |                 this.progress = 1.0F;
   88 |                 break;
   89 |             case CLOSING:
   90 |                 this.progress -= 0.1F;
   91 |                 if (this.progressOld == 1.0F) {
   92 |                     doNeighborUpdates(level, pos, blockState);
   93 |                 }
   94 | 
   95 |                 if (this.progress <= 0.0F) {
   96 |                     this.animationStatus = ShulkerBoxBlockEntity.AnimationStatus.CLOSED;
   97 |                     this.progress = 0.0F;
   98 |                     doNeighborUpdates(level, pos, blockState);
   99 |                 }
  100 |         }
  101 |     }
```

原文件 L112–L132：
```java
  112 |     private void moveCollidedEntities(Level level, BlockPos pos, BlockState state) {
  113 |         if (state.getBlock() instanceof ShulkerBoxBlock) {
  114 |             Direction direction = state.getValue(ShulkerBoxBlock.FACING);
  115 |             AABB aabb = Shulker.getProgressDeltaAabb(1.0F, direction, this.progressOld, this.progress, pos.getBottomCenter());
  116 |             List<Entity> entities = level.getEntities(null, aabb);
  117 |             if (!entities.isEmpty()) {
  118 |                 for (Entity entity : entities) {
  119 |                     if (entity.getPistonPushReaction() != PushReaction.IGNORE) {
  120 |                         entity.move(
  121 |                             MoverType.SHULKER_BOX,
  122 |                             new Vec3(
  123 |                                 (aabb.getXsize() + 0.01) * direction.getStepX(),
  124 |                                 (aabb.getYsize() + 0.01) * direction.getStepY(),
  125 |                                 (aabb.getZsize() + 0.01) * direction.getStepZ()
  126 |                             )
  127 |                         );
  128 |                     }
  129 |                 }
  130 |             }
  131 |         }
  132 |     }
```

### `net/minecraft/world/level/block/entity/DecoratedPotBlockEntity.java`

来源：.109 参考源码；SHA-256：`0f92e3b976a60e51c65f4be0e512513ed8e3f1f1b1a9e21ea93f4cb62a98f4f2`

原文件 L167–L175：
```java
  167 |     public boolean triggerEvent(int event, int data) {
  168 |         if (this.level != null && event == 1 && data >= 0 && data < DecoratedPotBlockEntity.WobbleStyle.values().length) {
  169 |             this.wobbleStartedAtTick = this.level.getGameTime();
  170 |             this.lastWobbleStyle = DecoratedPotBlockEntity.WobbleStyle.values()[data];
  171 |             return true;
  172 |         } else {
  173 |             return super.triggerEvent(event, data);
  174 |         }
  175 |     }
```

### `net/minecraft/world/level/block/entity/EnchantingTableBlockEntity.java`

来源：.109 参考源码；SHA-256：`2258520f1cb8bd4677a90a9f68393b083fcea05186ea2aadc3a501ff3da4d47f`

原文件 L50–L106：
```java
   50 |     public static void bookAnimationTick(Level level, BlockPos worldPosition, BlockState state, EnchantingTableBlockEntity entity) {
   51 |         entity.oOpen = entity.open;
   52 |         entity.oRot = entity.rot;
   53 |         Player player = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 3.0, false);
   54 |         if (player != null) {
   55 |             double xd = player.getX() - (worldPosition.getX() + 0.5);
   56 |             double zd = player.getZ() - (worldPosition.getZ() + 0.5);
   57 |             entity.tRot = (float)Mth.atan2(zd, xd);
   58 |             entity.open += 0.1F;
   59 |             if (entity.open < 0.5F || RANDOM.nextInt(40) == 0) {
   60 |                 float old = entity.flipT;
   61 | 
   62 |                 do {
   63 |                     entity.flipT = entity.flipT + (RANDOM.nextInt(4) - RANDOM.nextInt(4));
   64 |                 } while (old == entity.flipT);
   65 |             }
   66 |         } else {
   67 |             entity.tRot += 0.02F;
   68 |             entity.open -= 0.1F;
   69 |         }
   70 | 
   71 |         while (entity.rot >= (float) Math.PI) {
   72 |             entity.rot -= (float) (Math.PI * 2);
   73 |         }
   74 | 
   75 |         while (entity.rot < (float) -Math.PI) {
   76 |             entity.rot += (float) (Math.PI * 2);
   77 |         }
   78 | 
   79 |         while (entity.tRot >= (float) Math.PI) {
   80 |             entity.tRot -= (float) (Math.PI * 2);
   81 |         }
   82 | 
   83 |         while (entity.tRot < (float) -Math.PI) {
   84 |             entity.tRot += (float) (Math.PI * 2);
   85 |         }
   86 | 
   87 |         float rotDir = entity.tRot - entity.rot;
   88 | 
   89 |         while (rotDir >= (float) Math.PI) {
   90 |             rotDir -= (float) (Math.PI * 2);
   91 |         }
   92 | 
   93 |         while (rotDir < (float) -Math.PI) {
   94 |             rotDir += (float) (Math.PI * 2);
   95 |         }
   96 | 
   97 |         entity.rot += rotDir * 0.4F;
   98 |         entity.open = Mth.clamp(entity.open, 0.0F, 1.0F);
   99 |         entity.time++;
  100 |         entity.oFlip = entity.flip;
  101 |         float diff = (entity.flipT - entity.flip) * 0.4F;
  102 |         float max = 0.2F;
  103 |         diff = Mth.clamp(diff, -0.2F, 0.2F);
  104 |         entity.flipA = entity.flipA + (diff - entity.flipA) * 0.9F;
  105 |         entity.flip = entity.flip + entity.flipA;
  106 |     }
```

### `net/minecraft/world/level/block/entity/SkullBlockEntity.java`

来源：.109 参考源码；SHA-256：`6c83487981b2ce0cabbde1fcfaed5abbc591e325b668e94101fbd019937a6666`

原文件 L51–L58：
```java
   51 |     public static void animation(Level level, BlockPos pos, BlockState state, SkullBlockEntity entity) {
   52 |         if (state.hasProperty(SkullBlock.POWERED) && state.getValue(SkullBlock.POWERED)) {
   53 |             entity.isAnimating = true;
   54 |             entity.animationTickCount++;
   55 |         } else {
   56 |             entity.isAnimating = false;
   57 |         }
   58 |     }
```

### `net/minecraft/world/level/block/CauldronBlock.java`

来源：.109 参考源码；SHA-256：`36cb0b47a7a510ea479db34f4840b6442cd02d3e6659b1da22633cd83a6e5c18`

原文件 L42–L52：
```java
   42 |     public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
   43 |         if (shouldHandlePrecipitation(level, precipitation)) {
   44 |             if (precipitation == Biome.Precipitation.RAIN) {
   45 |                 level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState());
   46 |                 level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
   47 |             } else if (precipitation == Biome.Precipitation.SNOW) {
   48 |                 level.setBlockAndUpdate(pos, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState());
   49 |                 level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
   50 |             }
   51 |         }
   52 |     }
```

### `net/minecraft/world/level/block/CoralBlock.java`

来源：.109 参考源码；SHA-256：`8c06f095c1c34505b6596aa9d354c3d3cb649aecbd84c3990fef093775c7cd7c`

原文件 L38–L42：
```java
   38 |     protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   39 |         if (!this.scanForWater(level, pos)) {
   40 |             level.setBlock(pos, this.deadBlock.defaultBlockState(), 2);
   41 |         }
   42 |     }
```

### `net/minecraft/world/level/block/CropBlock.java`

来源：.109 参考源码；SHA-256：`ae6fd83d93567958a2b538c664e20fa26796d7996023fb951614ea8655d7e32a`

原文件 L79–L91：
```java
   79 |     protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
   80 |         if (!level.isAreaLoaded(pos, 1)) return; // Forge: prevent loading unloaded chunks when checking neighbor's light
   81 |         if (level.getRawBrightness(pos, 0) >= 9) {
   82 |             int age = this.getAge(state);
   83 |             if (age < this.getMaxAge()) {
   84 |                 float f = getGrowthSpeed(state, level, pos);
   85 |                 if (net.neoforged.neoforge.common.CommonHooks.canCropGrow(level, pos, state, random.nextInt((int)(25.0F / f) + 1) == 0)) {
   86 |                     level.setBlock(pos, this.getStateForAge(age + 1), 2);
   87 |                     net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(level, pos, state);
   88 |                 }
   89 |             }
   90 |         }
   91 |     }
```

### `net/minecraft/world/level/block/entity/ConduitBlockEntity.java`

来源：.109 参考源码；SHA-256：`da3589a470909496d6dc153d6259311738cf8810c72ff5f2e1308492a2ee3f25`

原文件 L92–L121：
```java
   92 |     public static void serverTick(Level level, BlockPos pos, BlockState state, ConduitBlockEntity entity) {
   93 |         entity.tickCount++;
   94 |         long gameTime = level.getGameTime();
   95 |         List<BlockPos> effectBlocks = entity.effectBlocks;
   96 |         if (gameTime % 40L == 0L) {
   97 |             boolean active = updateShape(level, pos, effectBlocks);
   98 |             if (active != entity.isActive) {
   99 |                 SoundEvent event = active ? SoundEvents.CONDUIT_ACTIVATE : SoundEvents.CONDUIT_DEACTIVATE;
  100 |                 level.playSound(null, pos, event, SoundSource.BLOCKS, 1.0F, 1.0F);
  101 |             }
  102 | 
  103 |             entity.isActive = active;
  104 |             updateHunting(entity, effectBlocks);
  105 |             if (active) {
  106 |                 applyEffects(level, pos, effectBlocks);
  107 |                 updateAndAttackTarget((ServerLevel)level, pos, state, entity, effectBlocks.size() >= 42);
  108 |             }
  109 |         }
  110 | 
  111 |         if (entity.isActive()) {
  112 |             if (gameTime % 80L == 0L) {
  113 |                 level.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
  114 |             }
  115 | 
  116 |             if (gameTime > entity.nextAmbientSoundActivation) {
  117 |                 entity.nextAmbientSoundActivation = gameTime + 60L + level.getRandom().nextInt(40);
  118 |                 level.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.BLOCKS, 1.0F, 1.0F);
  119 |             }
  120 |         }
  121 |     }
```
