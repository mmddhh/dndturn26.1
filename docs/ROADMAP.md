# Development roadmap

Minecraft 26.1.2 on NeoForge is the gameplay target. Forge 1.20.1, Fabric 1.20.1 and NeoForge 1.21.1 have separate build and publication boundaries. A successful build of another target establishes its artifact integrity only.

## 1. Finish the content split before adding gameplay

The 26.1 distribution embeds two loader-discovered mods. `dndturn_characters` owns the shipped classes, races, starting kits, equipment items, item models and equipment profiles. `dndturn_mobs` owns the shipped mob catalog, species abilities, AI families, Creeper hooks and bundled entity presentation adapters. Core discovers installed catalogs and captures a validated revision for each encounter. World overrides keep their existing IDs and precedence.

Remaining work includes concrete fallback attack and mob stats in common, item-specific actions and ammunition in the target, native effect behaviors, character progression policies and content-specific UI choices or strings. Trace each from definition through actual consumers, persistence and presentation. Move it to the appropriate provider. Introduce a framework port only for an operation the provider actually needs. Keep generic movement, ownership, authorization and observation in core.

Close this stage when the remaining concrete policies have provider owners, missing content is handled explicitly on create and restore, and a real client renders the moved items and entity presentation. Repeat the packaged provider-omission checks after those extractions. Keep existing save IDs, world overrides and encounter bindings during extraction.

## 2. Establish a trustworthy time and effects baseline

Start with the six previously reproducible isolated failures below. For each, identify the first divergence between expected and observed authority, fix the responsible fixture or runtime owner and repeat both isolated and shared-world runs. Cover a participant turn, a delayed native effect, source attribution and interrupted release. Resolve the shared-only failures before relying on the full batch as a CI gate.

Close when the affected scenarios pass in both modes with the original behavioral assertions, or when a changed product rule has been explicitly agreed and recorded. Keep the current narrow CI GameTest while this work is under way.

## 3. Complete one playable character progression path

Choose one existing provider-owned class and an agreed ability. Connect acquisition, preparation where applicable, equipment evidence, discovery, preview, cost, execution, result and persistence. Run the same action through the player and AI backends where both are eligible. Carry typed damage and source identity through CombatNexus. Add the next class, spell or species after the first loop works in play.

Close with real input for character creation, selection, approach, action, end turn and exit. Verify stale selections, source loss, a full inventory and a retry after an observed effect. HELP and opportunity reactions still need legal ally selection, bounded response windows and stable resumption. Defer new spell lists, boss behavior and unconfirmed mechanics until their gameplay is specified.

## 4. Prove cross-process recovery and multiplayer

Connect the applicable CombatNexus time and active-driver lifecycles. Save a pending action or environmental step, stop an ordinary dedicated server and resume from the same world in a second process. Exercise merge, unload, rejoin and cancellation. Use the actual distribution jars and a matching dependency set.

Close when two real clients observe the same confirmed outcome, each accepted cost occurs once, unknown world effects are never replayed, and control and queue ownership are released once. Measure field queries, pathfinding, projection and rendering under representative encounters before selecting performance work.

## Verification evidence and limits

After the split, NeoForge GameTest servers loaded the staged jar from the mods directory without loose copies. Minecraft's always_pass completed with both providers (12 classes, 9 races, 38 mobs), with character content alone (12, 9, 0), with mob content alone (0, 0, 38), and with neither (0, 0, 0). Omission runs used temporary copies with the corresponding nested jars and index entries removed. Development GameTests passed character_creation, ghast_numerical, arrow_tactical_impact and scheduled_tick_hold. These checks exercise startup and selected behaviors. Full combat and restart coverage remains open.

The audit before the module split passed common JUnit (119), target JUnit (2), three headless checks and all four target builds. On 26.1, independent GameTest processes passed 66 of 72 cases. The shared run passed 58 of 72. Track the six independently reproducible failures by observation:

| Scenario | Observed failure |
| --- | --- |
| creeper_effect | The cloud timed out with player health at 10, short of the expected 12 after healing. |
| mob_standardization | Mob ranged contact and retaliation did not settle before timeout. |
| participant_effects | Real-time waiting changed effect duration or health. |
| participant_fire | The first participant turn missed the expected 1.5 fire damage. |
| regional_entity_gate | Speed duration fell from 100 to 90 before the owner's turn end. |
| tactical_effect_state | A native potion contribution lost its source. |

Eight additional cases failed only in that shared run: actor_history, mob_unknown_revokes_lease, prototype_five_rounds and five ghast_numerical scenarios. Their root cause is open. The prior foreground check covered character creation and reopening the sheet. Combined ordinary-server restart, real two-client combat, the wider CombatNexus internal-reference audit and full client presentation remain open.
