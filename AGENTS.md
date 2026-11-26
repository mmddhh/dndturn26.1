# DNDTurn repository instructions

Read the [development contract](docs/DEVELOPMENT.md) before changing a boundary. The [roadmap](docs/ROADMAP.md) records outstanding work and verification limits. The [content and CombatNexus design](docs/DNDTURN_REFACTOR.md) explains the intended integration. The README is for players.

## Decide who owns the change

- Put new game content in a content mod. Ship supported first-party content as loader-discovered nested jars. A new class, race, equipment mapping, species action, spell or concrete effect belongs to its provider.
- Keep common on Java 17 with pure domain and application code. Domain dependencies point inward. Target-specific loader, Minecraft, network, rendering and Mixin code belongs in the target. Keep packages distinct across modules.
- Extend the existing owner of each fact or process. Trace creation, advancement, cancellation, merge, unload, restoration and disposal together. A query, cache, UI, adapter or content provider cannot grant itself authority to change the world.
- Implement a playable or externally observable slice. Keep existing gameplay semantics during extraction. Record ambiguous product decisions in the roadmap and continue independent work.

## Change and verify

Inspect the actual branch, working tree, affected entry points and target sources first. Preserve unrelated edits. Check the target branch and merge base before merging. Commit, publish and push only on request.

Choose the lowest layer that can observe the invariant. Prefer an existing test. A new assertion should identify the failure it detects. Verify publication boundaries against the distributable jar and runtime boundaries against the matching loader. Keep fixtures and debugging tools out of releases. Record the command, result and remaining gap when evidence changes.

Keep lasting engineering rules in the contract and open work in the roadmap. Keep temporary investigation notes, stage-specific checklists and dated acceptance pages out of the repository.
