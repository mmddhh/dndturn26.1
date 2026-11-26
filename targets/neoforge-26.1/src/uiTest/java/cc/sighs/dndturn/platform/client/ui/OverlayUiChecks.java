package cc.sighs.dndturn.platform.client.ui;

import cc.sighs.dndturn.platform.client.action.ActionPlanChecks;
import cc.sighs.dndturn.platform.client.render.MovementPreviewChecks;
import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.spi.AuiResourceService;
import com.sighs.apricityui.spi.AuiServices;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;

/** Headless checks against the resolved AUI jar; does not claim GPU or real-input acceptance. */
public final class OverlayUiChecks {
    public static void main(String[] args) throws Exception {
        MovementPreviewChecks.run();
        ActionPlanChecks.run();
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
        for (int i : new int[]{27, 0}) {
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
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

}
