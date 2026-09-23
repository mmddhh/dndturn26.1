package cc.sighs.dndturn.platform.client.action;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.platform.network.ActionProtocol;

public final class ActionPlanChecks {
    private ActionPlanChecks() {}
    public static void run() throws Exception { checkSelectionLifecycle(); checkDiscoveryLifecycle(); }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void checkSelectionLifecycle() throws Exception {
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
        ClientTacticalPlan.worldClick(false, java.util.UUID.randomUUID(), null);
        ClientTacticalPlan.worldClick(false, null, null);
        require(ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.IDLE && ClientTacticalPlan.inspected() == null,
            "idle left-click inspected or moved");
        setPlanField("itemSelected", true);
        setPlanField("slot", 3);
        setPlanField("behavior", "dndturn:melee");
        setPlanField("preferredCost", ActionIntent.Capability.ATTACK);
        setPlanField("queryId", java.util.UUID.randomUUID());
        setPlanField("pendingClick", java.util.UUID.randomUUID());
        setPlanField("submitAfterQuery", true);
        setPlanField("phase", ClientTacticalPlan.Phase.QUERYING);
        ClientTacticalPlan.suspendSelection();
        require(ClientTacticalPlan.selectedSlot(3) && "dndturn:melee".equals(planField("behavior")),
            "turn boundary lost item or ability choice");
        require(planField("queryId") == null && planField("pendingClick") == null && Boolean.FALSE.equals(planField("submitAfterQuery")),
            "turn boundary retained a pending submission");
        require(!ClientTacticalPlan.previewClick(null), "suspended choice executed before confirmation");
        ClientTacticalPlan.reset();
        require(!ClientTacticalPlan.selectedSlot(3) && planField("behavior") == null,
            "new encounter inherited previous selection");
    }
    private static Object planField(String name) throws Exception {
        var field = ClientTacticalPlan.class.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static void checkDiscoveryLifecycle() throws Exception {
        var actor = java.util.UUID.randomUUID();
        ClientTacticalPlan.reset();
        setPlanField("itemSelected", true);
        setPlanField("slot", 3);
        setPlanField("arming", true);
        setPlanField("phase", ClientTacticalPlan.Phase.TARGETING);
        ClientTacticalPlan.beginQuery(actor, 7, false);
        require(!ClientTacticalPlan.observeTurn(actor, 7), "first tick invalidated a query from the same turn");
        var melee = attackAbility("dndturn:melee");
        var reply = options(java.util.List.of(melee), "", true);
        ClientTacticalPlan.applyOptions(reply, false);
        require(ClientTacticalPlan.abilities().equals(java.util.List.of(melee)), "weapon confirmation lost the dynamic action row");
        require(ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.TARGETING, "weapon confirmation did not arm targeting");
        require(planField("queryId") == null, "accepted reply was not consumed");
        ClientTacticalPlan.cancel();
        ClientTacticalPlan.applyOptions(reply, false);
        require(ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.IDLE, "duplicate reply revived canceled targeting");
        ClientTacticalPlan.suspendSelection();
        require(ClientTacticalPlan.hasSelection() && ClientTacticalPlan.selectedSlot(3), "item-only choice will not refresh next turn");
        require(ClientTacticalPlan.abilities().isEmpty(), "turn boundary retained confirmed authority");

        setPlanField("phase", ClientTacticalPlan.Phase.CONTEXT_MENU);
        ClientTacticalPlan.beginQuery(actor, 8, false);
        require(ClientTacticalPlan.contextStatus().contains("查询"), "pending menu has no feedback");
        var rejected = options(java.util.List.of(), "selected item changed; select it again", false);
        ClientTacticalPlan.applyOptions(rejected, false);
        require(ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.CONTEXT_MENU, "rejected discovery closed the menu");
        require(ClientTacticalPlan.contextStatus().equals(rejected.reason()), "menu lost discovery rejection evidence");
        require(ClientTacticalPlan.abilities().isEmpty() && ClientTacticalPlan.offers().isEmpty(), "rejection retained executable choices");
        ClientTacticalPlan.cancel();
        ClientTacticalPlan.beginQuery(actor, 8, false);
        setPlanField("arming", true);
        setPlanField("phase", ClientTacticalPlan.Phase.TARGETING);
        var replacement = options(java.util.List.of(melee), "", true);
        ClientTacticalPlan.applyOptions(rejected, false);
        require(Boolean.TRUE.equals(planField("arming")), "late rejected reply overwrote a new selection");
        ClientTacticalPlan.applyOptions(replacement, false);
        require(ClientTacticalPlan.abilities().contains(melee), "new explicit selection failed to recover the row");
        require(ClientTacticalPlan.observeTurn(actor, 9), "actual turn change was ignored");
        ClientTacticalPlan.reset();
    }
    private static ActionProtocol.Options options(
            java.util.List<ActionProtocol.Ability> abilities, String reason, boolean confirmed) throws Exception {
        return new ActionProtocol.Options(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            (java.util.UUID) planField("queryId"), 1, java.util.List.of(), reason, (long) planField("revision"),
            confirmed ? new ActionIntent.ItemReference(3, "test-item") : null,
            "dndturn:melee", abilities, "测试武器");
    }
    private static ActionProtocol.Ability attackAbility(String id) {
        return new ActionProtocol.Ability(id, "攻击",
            ActionIntent.Capability.ATTACK,
            java.util.Set.of(ActionIntent.TargetKind.ENTITY), 1, null);
    }
    private static void setPlanField(String name, Object value) throws Exception {
        var field = ClientTacticalPlan.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
}
