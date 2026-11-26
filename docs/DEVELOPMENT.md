# Development contract

## Artifacts and dependency direction

The target distribution contains the framework and embeds its first-party content mods. Each content jar has its own loader identity, resources and dependency declaration. Content may consume framework contracts. Core must compile and run without importing a provider or relying on a particular catalog entry. Packaging selects the bundled providers. The same registration and validation rules apply to bundled and external providers.

The shared Java 17 module owns platform-independent domain and application logic. Domain cannot depend on application. Each target owns its Minecraft and loader integration, narrow Mixin entry points, threading, networking and presentation. Keep client classes off dedicated-server paths. Build a distinct artifact per target. Shared resources have one owner and resource lookup cannot rely on classpath precedence.

Core owns encounter scheduling, authorization, catalog validation, immutable bindings, bounded observation, dispatch and recovery. Content owns concrete races, classes, equipment profiles, fallback stat blocks, species behavior, ability formulas, effects and their presentation. The roadmap identifies remaining implementations on the wrong side of this boundary. Preserve their gameplay during extraction.

## State and execution

| Authority | Facts it may change |
| --- | --- |
| Encounter | Membership, hostility, initiative, eligibility, turn resources and canonical rule outcomes |
| Actor state and effect owners | Character choices, grants, preparation, actor resources and individual effect instances |
| Process owner | Current stage, accepted inputs, effective clock, checkpoint and release |
| Minecraft | Live entity, health, equipment, inventory, world and connection facts |

Players and AI use the same discovery, validation, authorization and execution path. Discovery, previews and scoring read bounded immutable snapshots. Bind a plan to the actor instance, ability source, target, selection and captured semantics. Revalidate at submission, after approach and before a dependent side effect. A stale target requires a new choice. Adapters translate facts and execute the request within its permit.

An operation ID deduplicates a payload. A permit grants bounded authority. Repeating the same operation and payload returns its recorded outcome. Conflicting reuse is rejected. Charge once at the accepted gameplay boundary. Preserve observed damage, absorption, cancellation, consumption and cleanup separately. Interrupted work keeps effects already applied. An unknown outcome cannot authorize replay or refund. Corrections append evidence to history.

## Time, field and recovery

Each encounter has one environment participant following entity turns. Its field follows participating player anchors at movement completion or a turn boundary, with a default radius of 35 blocks. Membership and simulation range are separate. Neutral mobs in range stay outside initiative until eligible. Actions crossing the field still require valid phase, target, loaded area, protection and bounded work.

Ordinary physics and synchronous world responses continue. Gate active execution and explicitly managed delayed or periodic work. Advance a process only on its authorized step or participant boundary. Count real-time consent windows separately. Scheduled ticks, block events, block entities and projectiles each retain their owner, remaining delay, ordering and deduplication across transfers. Prepare bounded transfers before committing owner and index changes, then notify observers.

Capture settings, content and ability semantics for the session. A catalog reload validates an entire candidate before publication. Preserve each participant's binding across merge and restore. Save value checkpoints. Keep live entity, level, connection and navigation references inside bounded platform lifetimes. Restore after the instance and area are available naturally. Old receipts cannot reauthorize work. Attempt cleanup for every owner and report failures individually.

Before the first declared release, maintain one current save, config and network format. Preserve and reject unreadable input. Keep semantic versions, instance identities, revisions and operation IDs that are needed to interpret live work.

## Client boundary

Derive identity from the server connection. Bound payload size and cumulative query and pathfinding work. Revalidate selections and revisions on the server. Late replies cannot reopen cancelled selections. Clear client state across reconnects and world changes. The tactical camera moves independently of the player. Presentation consumes confirmed facts. Keep the tactical camera and empty log panel. Route input through the active screen and respect focus changes.

## Verification

Use common JUnit for pure rules, target JUnit for codecs and pure target contracts, headless checks for client state transitions and GameTest for world and loader seams. Use actual clients for input, camera and multiplayer behavior. Use a second ordinary dedicated-server process and the saved world for restart claims.

From the repository root, run `.\gradlew.bat -p common check` with a Java 17 toolchain available. Use `.\scripts\build-target.ps1 -Target neoforge-26.1` for the release artifact. Run targets serially when sharing common build outputs. The 26.1 target uses JDK 25, NeoForge 1.21.1 uses JDK 21 and both 1.20.1 targets compile for Java 17. Target properties and loader metadata specify exact versions.

From the 26.1 target, run `.\gradlew.bat verifyScheduledTick` for the CI queue-ownership seam or `.\gradlew.bat runGameTestServer -PgameTestSelection=dndturn:character_creation` for catalog consumption. Check the fresh XML under `build/boundary-gametest` or `build/gametest-run`. Inspect the staged release under `build/verified-release` for loader metadata, nested providers and fixture leakage.
