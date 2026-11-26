package cc.sighs.dndturn.platform.client.action;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.StartDisposition;
import cc.sighs.dndturn.platform.client.input.CombatControls;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import java.util.UUID;

public final class ActionPlanChecks {
    private ActionPlanChecks() {}
    public static void run() { checkSelectionLifecycle(); checkDiscoveryLifecycle(); checkExitReplies(); checkItemUse(); }
    private static void checkItemUse() {
        var source = cc.sighs.dndturn.domain.ability.GrantEvidence.equipment(ActionIntent.Hand.MAIN_HAND,
            new ActionIntent.ItemReference(0, "confirmed-item"));
        var tool = new ActionProtocol.Ability("dndturn:tool", "tool", ActionIntent.Capability.USE_ITEM,
            java.util.Set.of(ActionIntent.TargetKind.BLOCK), 1, source);
        var nativeUse = new ActionProtocol.Ability("dndturn:native_use", "use", ActionIntent.Capability.USE_ITEM,
            java.util.Set.of(ActionIntent.TargetKind.SELF), 1, source);
        var entityUse = new ActionProtocol.Ability("dndturn:entity_item", "entity", ActionIntent.Capability.USE_ITEM,
            java.util.Set.of(ActionIntent.TargetKind.ENTITY), 1, source);
        var generic = java.util.List.of(nativeUse, entityUse, tool);
        require(ItemUseSelection.resolve(generic, ActionIntent.TargetKind.BLOCK) == tool,
            "cursor block lost its useOn binding to self use");
        require(ItemUseSelection.resolve(generic, ActionIntent.TargetKind.ENTITY) == entityUse,
            "cursor entity lost its interaction binding");
        require(ItemUseSelection.resolve(generic, ActionIntent.TargetKind.SELF) == nativeUse,
            "untargeted native use unavailable");
        for (String id : java.util.List.of("place", "spawn_item", "bucket", "bottle", "brush", "fishing_rod")) {
            var dedicated = new ActionProtocol.Ability("dndturn:" + id, id,
                id.equals("place") ? ActionIntent.Capability.PLACE : ActionIntent.Capability.USE_ITEM,
                java.util.Set.of(ActionIntent.TargetKind.BLOCK), 1, source);
            var choices = java.util.List.of(tool, nativeUse, entityUse, dedicated);
            require(ItemUseSelection.resolve(choices, ActionIntent.TargetKind.BLOCK) == dedicated,
                "generic entry bypassed dedicated lifecycle: " + id);
            require(ItemUseSelection.resolve(choices, ActionIntent.TargetKind.SELF) == null,
                "missing cursor target fell back to untargeted use: " + id);
            require(ItemUseSelection.resolve(choices, ActionIntent.TargetKind.ENTITY) == entityUse,
                "item lost native entity interaction: " + id);
        }
        var consume = new ActionProtocol.Ability("dndturn:consume", "consume", ActionIntent.Capability.USE_ITEM,
            java.util.Set.of(ActionIntent.TargetKind.SELF), 1, source);
        require(ItemUseSelection.resolve(java.util.List.of(tool, consume), ActionIntent.TargetKind.BLOCK) == tool,
            "consumable lost native useOn semantics");
        require(ItemUseSelection.resolve(java.util.List.of(tool, consume), ActionIntent.TargetKind.SELF) == consume,
            "consumable self use lost its confirmed source");
        var block = new ActionProtocol.Ability("dndturn:block", "block", ActionIntent.Capability.USE_BLOCK,
            java.util.Set.of(ActionIntent.TargetKind.BLOCK), 1, cc.sighs.dndturn.domain.ability.GrantEvidence.basic());
        require(!ItemUseSelection.includes(block), "free block interaction merged into paid item use");
    }
    private static void checkExitReplies() {
        var exits = new CombatControls.ExitRequests();
        var id = exits.request(1);
        require(id.equals(exits.request(1)), "repeat input replaced pending exit");
        var stale = staleExit(id);
        exits.receive(stale);
        require(!exits.needsRefresh(1) && exits.needsRefresh(2), "stale exit must wait for a new projection");
        var replacement = exits.request(2);
        exits.receive(stale);
        require(replacement.equals(exits.request(2)), "old reply displaced the replacement exit");
        exits.reset();
        exits.receive(staleExit(replacement));
        require(!exits.needsRefresh(3), "late exit reply revived canceled request");
    }
    private static EncounterProtocol.IntentStatus staleExit(UUID id) {
        return new EncounterProtocol.IntentStatus(
            UUID.randomUUID(), UUID.randomUUID(), id,
            EncounterProtocol.IntentKind.EXIT, false, "stale", false,
            -1, 0, 0, StartDisposition.NONE,
            ActionFailure.Code.STALE_ENCOUNTER,
            ActionFailure.Retry.REFRESH_AND_REPROPOSE);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void checkSelectionLifecycle() {
        var intrinsic = attackAbility("dndturn:intrinsic_melee");
        var melee = attackAbility("dndturn:melee");
        var bow = attackAbility("dndturn:bow");
        var candidates = java.util.List.of(intrinsic, melee, bow);
        require("dndturn:intrinsic_melee".equals(ClientTacticalPlan.attackForSelection(false, false, bow.id(), candidates)),
            "unselected held item replaced empty-hand attack");
        require("dndturn:intrinsic_melee".equals(ClientTacticalPlan.attackForSelection(true, true, melee.id(), candidates)),
            "empty selected slot lost empty-hand attack");
        require(bow.id().equals(ClientTacticalPlan.attackForSelection(true, false, bow.id(), candidates)),
            "selected ranged item used an unrelated attack");
        require(melee.id().equals(ClientTacticalPlan.attackForSelection(true, false, "dndturn:place", java.util.List.of(intrinsic, melee))),
            "selected ordinary item fell back to intrinsic attack");
        require(ClientTacticalPlan.attackForSelection(true, false, "unsupported", java.util.List.of(intrinsic)) == null,
            "unsupported item silently fell back to empty hand");
        ClientTacticalPlan.reset();
        require(ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.IDLE, "entry armed a default action");
        require(!ClientTacticalPlan.previewClick(null), "idle left-click entered the preview path");
        ClientTacticalPlan.worldClick(false, UUID.randomUUID(), null);
        ClientTacticalPlan.worldClick(false, null, null);
        require(ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.IDLE && ClientTacticalPlan.inspected() == null,
            "idle left-click inspected or moved");
    }
    private static void checkDiscoveryLifecycle() {
        var actor = UUID.randomUUID();
        var encounter = UUID.randomUUID();
        var generation = UUID.randomUUID();
        var target = new ActionIntent.Target(ActionIntent.TargetKind.SELF, "minecraft:overworld", null, null, -1, 0, 0, 0);
        ClientTacticalPlan.selectSlot(3);
        var request = ClientTacticalPlan.beginQuery(generation, encounter, actor, 7, target, false);
        require(!ClientTacticalPlan.observeTurn(actor, 7), "first tick canceled same-turn discovery");
        var melee = attackAbility("dndturn:melee");
        var reply = new ActionProtocol.Options(generation, encounter, request.query(), 1, java.util.List.of(), "",
            request.revision(), new ActionIntent.ItemReference(3, "test-item"), melee.id(), java.util.List.of(melee), "weapon");
        ClientTacticalPlan.applyOptions(reply, false);
        require(ClientTacticalPlan.abilities().contains(melee) && ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.TARGETING,
            "confirmation did not arm the chosen slot");
        ClientTacticalPlan.cancel();
        ClientTacticalPlan.applyOptions(reply, false);
        require(ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.IDLE, "duplicate reply revived targeting");
        ClientTacticalPlan.suspendSelection();
        require(ClientTacticalPlan.selectedSlot(3) && ClientTacticalPlan.abilities().isEmpty(),
            "turn boundary must retain only the selection");
        ClientTacticalPlan.worldClick(true, actor, null);
        var menu = ClientTacticalPlan.beginQuery(generation, encounter, actor, 8, target, false);
        var rejected = new ActionProtocol.Options(generation, encounter, menu.query(), 1, java.util.List.of(),
            "selection changed", menu.revision(), null, "", java.util.List.of(), "");
        ClientTacticalPlan.applyOptions(rejected, false);
        require(ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.CONTEXT_MENU
            && rejected.reason().equals(ClientTacticalPlan.contextStatus()), "rejection lost menu feedback");
        ClientTacticalPlan.selectSlot(4);
        ClientTacticalPlan.applyOptions(rejected, false);
        require(ClientTacticalPlan.selectedSlot(4) && ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.TARGETING,
            "late rejection displaced a new selection");
        ClientTacticalPlan.reset();
        require(!ClientTacticalPlan.hasSelection(), "new session inherited old choice");
    }
    private static ActionProtocol.Ability attackAbility(String id) {
        return new ActionProtocol.Ability(id, "攻击",
            ActionIntent.Capability.ATTACK,
            java.util.Set.of(ActionIntent.TargetKind.ENTITY), 1, null);
    }
}
