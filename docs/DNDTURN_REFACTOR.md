# Content providers and CombatNexus

This design describes the seam between a content author, the tactical framework and Minecraft. The [roadmap](ROADMAP.md) tracks the remaining migrations. The [development contract](DEVELOPMENT.md) governs changes to the seam.

## Adding content on NeoForge 26.1

Give the provider its own mod ID and NeoForge descriptor. A bundled provider is an independent Gradle subproject and a Jar-in-Jar dependency of the distribution. Declare its dependency on DNDTurn in its descriptor. Preserve public item and ability IDs when moving their implementation between jars. Register native items on the provider's mod event bus.

For declarative classes, races and mob profiles, ship `data/<mod-id>/dndturn/catalog.json`. The current sections are `classes`, `races` and `mobs`. Each is an array. Omit sections the mod does not supply. Core discovers the resource through the owning mod file. Installed catalogs may contribute distinct IDs. Duplicate entries within a category reject the proposed catalog. World-local files in `dndturn-content/classes`, `races` and `mobs` can override installed entries. Keep the last valid revision when a reload fails.

See the [character catalog](../targets/neoforge-26.1/character-content/src/main/resources/data/dndturn_characters/dndturn/catalog.json) and [mob catalog](../targets/neoforge-26.1/mob-content/src/main/resources/data/dndturn_mobs/dndturn/catalog.json) for executable examples. Character content also supplies registered weapon and armor profiles. The resulting immutable maps are captured in the encounter catalog.

For behavior that needs code, register ability definitions, source adapters, executors, AI choices and observations through their existing owners. Register server behavior before the registries freeze. Put client-only presentation in a physical-client entry point. A species-specific native hook and its Mixin configuration belong in the provider jar. Keep generic Minecraft observations and constrained execution ports in the platform adapter.

## Following one action

1. A provider describes a capability and its sources. A grant binds its ID and semantic version to the actor or to an observed item or entity instance. Check preparation and source availability when relevant.
2. The server reads the encounter's pinned content and a bounded snapshot of native and actor-owned facts. Preserve units, sampling evidence and contribution source. Report incomplete explanations when the source cannot be observed.
3. Player selection and AI choice produce the same intent. The accepted plan fixes actor, source, target and costs. Execution obtains a scoped permit and rechecks the instance before invoking a native action.
4. Resolve the typed numerical input once. Keep the attack roll, save, damage types, absorption, cancellation and observed health change attributable to this operation. A later projectile or effect carries causal evidence and receives its own authorization when due.
5. Publish the outcome to the actor, history and UI through their owners. A retry reads the recorded outcome. Restoring a pending checkpoint waits for the correct instances and preserves prior random results and costs.

## Gameplay carried by the providers

Extraction preserves the shipped definition IDs, kit contents and observed action costs. Keep the following established decisions with their consumers when moving code:

- Character creation uses a 27-point buy. The selected race, class, ability increases and equipment kit are revalidated against the offered revision. Record actual inventory insertions and overflow drops. Charge a grant once even when the client retries.
- A normal attack rolls d20 against AC. Natural 1 misses and natural 20 hits critically. Advantage and disadvantage cancel each other. The first eligible attack against a target that has not locked the attacker can consume the encounter's first-attack benefit. A save uses its ability and proficiency against DC without automatic outcomes on 1 or 20.
- Resolve registered armor and weapons by full item ID against the bound catalog. Keep native armor, toughness, absorption and the player character's equipment calculation distinguishable. Capture the roll and typed damage at the appropriate boundary so an impact cannot roll an accepted attack again.
- DASH, DODGE, DISENGAGE and HELP have distinct costs, durations and consumers. Block interaction and held-item use are distinct operations. An open menu grants no authority for later trades, recipes or inventory transfers.
- A Creeper's accepted Charge spends one action. Its third charge resolves at its turn end. An Evoker's registered summon creates four Vex with a six-round cooldown. Keep their configuration and native consequences in the mob provider. Guardian channeling, boss abilities and other incomplete content stay on the roadmap until a playable consumer exists.
- Tactical camera controls, the empty log panel and server-confirmed projections belong to the framework UI. Character choices, ability-specific previews and species animation adapters follow their content. Keep registration on the physical client separate from server authority.

## CombatNexus integration

DNDTurn owns tactical eligibility, action budgets, actor selection and encounter time. Minecraft owns live equipment, entity state and world effects. Use CombatNexus's public numerical, ability and driver contracts at the relevant application boundary. Keep native inputs, rule-derived values and observed outcomes distinct. A vanilla modifier already reflected in an effective attribute contributes once.

Today only scoped numerical applications have been integrated. Extending TIME or ACTIVE ownership requires an explicit handoff, bounded steps, release on every terminal path and a recovery check in both mods. An open driver session or matching UUID cannot stand in for a permit. Keep migration scoped to one complete consumer at a time. The next consumer and its acceptance evidence are listed in the roadmap.
