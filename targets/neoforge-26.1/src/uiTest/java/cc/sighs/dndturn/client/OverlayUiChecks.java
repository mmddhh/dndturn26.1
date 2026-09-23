package cc.sighs.dndturn.client;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.spi.AuiResourceService;
import com.sighs.apricityui.spi.AuiServices;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Headless checks against the resolved AUI jar; does not claim GPU or real-input acceptance. */
public final class OverlayUiChecks {
    public static void main(String[] args) throws Exception {
        MovementPreviewChecks.run();
        checkSelectionLifecycle();
        var declarations = new HashMap<String, Map<String, CSS.Declaration>>();
        try (var stream = OverlayUiChecks.class.getResourceAsStream("/assets/apricityui/apricity/dndturn/tactical.css")) {
            if (stream == null) throw new AssertionError("missing tactical stylesheet");
            CSS.readCSS(new String(stream.readAllBytes(), StandardCharsets.UTF_8), declarations,
                "dndturn/tactical.css", new Size(960, 540));
        }
        require(declarations.containsKey("#end-control"), "async stylesheet readiness rule is not parsed by this AUI version");

        // No Minecraft resource manager is installed in this process. Only document construction
        // uses this stub; DOM mutation and text invalidation are the real dependency implementation.
        AuiServices.setResources((AuiResourceService) Proxy.newProxyInstance(AuiResourceService.class.getClassLoader(),
            new Class<?>[] {AuiResourceService.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "openResource" -> Optional.empty();
                case "listResourcePaths" -> Map.of();
                default -> throw new AssertionError("unexpected resource operation: " + method.getName());
            }));
        Document document = new Document("dndturn/headless-text-check.html", false);
        Element label = new Element(document, "span");
        label.setAttribute("style", "font-size: 7px; line-height: 9px");
        OverlayUi.text(label, "移动 28");
        var node = label.getChildNodes().getFirst();
        require(node instanceof TextNode, "dynamic label did not create a real text node");
        for (int i = 27; i >= 0; i--) {
            OverlayUi.text(label, "移动 " + i);
            require(label.getChildNodes().size() == 1 && label.getChildNodes().getFirst() == node,
                "resource update replaced its text node");
            require(label.getTextContent().equals("移动 " + i), "text update was delayed");
            require(label.getRenderChildNodes().size() == 1 && label.getRenderChildNodes().getFirst() == node,
                "resource update introduced a second legacy render text");
        }
        OverlayUi.text(label, "");
        OverlayUi.text(label, "新回合");
        require(label.getChildNodes().getFirst() == node, "empty/refilled label replaced its node");
        require(label.getAttribute("style").contains("7px"), "text update changed font size");

        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            MovementRing.paint(graphics, 36, 36);
            require((image.getRGB(8, 64) & 0xffffff) == 0xa7d6bf, "full ring missing at left edge");
            MovementRing.paint(graphics, 9, 36);
            require((image.getRGB(8, 64) & 0xffffff) == 0x605447, "decreasing ring retained its old arc");
            MovementRing.paint(graphics, 0, 36);
            require((image.getRGB(120, 64) & 0xffffff) == 0x605447, "empty ring retained progress");
            require(image.getRGB(64, 64) == 0, "ring obscures the End Turn button");
            MovementRing.paint(graphics, 54, 36);
            require((image.getRGB(8, 64) & 0xffffff) == 0xe5cb90, "extra movement has no surplus indication");
            MovementRing.paint(graphics, 10, 0);
            require((image.getRGB(8, 64) & 0xffffff) == 0x605447, "unknown capacity fabricated progress");
        } finally { graphics.dispose(); }
        System.out.println("UI checks passed: stylesheet readiness, retained text updates, ring redraw/empty/surplus.");
    }

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
        setPlanField("preferredCost", cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK);
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
    private static cc.sighs.dndturn.combat.TacticalNetwork.Ability attackAbility(String id) {
        return new cc.sighs.dndturn.combat.TacticalNetwork.Ability(id, "攻击",
            cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK,
            java.util.Set.of(cc.sighs.dndturn.combat.TacticalIntent.TargetKind.ENTITY), 1, null);
    }
    private static void setPlanField(String name, Object value) throws Exception {
        var field = ClientTacticalPlan.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
}
