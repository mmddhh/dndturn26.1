package cc.sighs.dndturn.platform.client.ui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

/** One layout box owns both the input surface and its non-interactive visual. */
final class TacticalButton {
    private TacticalButton() {}

    static Element visual(Document document, Element button) {
        Element parent = (Element) button.getParentNode();
        Element host = document.createElement("div");
        host.setAttribute("class", "icon-control");
        String id = button.getAttribute("id");
        if (id != null && !id.isEmpty()) host.setAttribute("id", id + "-control");
        host = parent.insertBefore(host, button);
        host.appendChild(button);
        Element visual = host.appendChild(document.createElement("div"));
        visual.setAttribute("class", "control-visual");
        return visual;
    }

    static Element host(Element button) { return (Element) button.getParentNode(); }

    /** Resolve both layers from one size, without nested percentage/intrinsic sizing. */
    static void layout(Element button, double size, double inset, double scale, String position) {
        Element host = host(button);
        style(host, position + "; width: " + size + "px; height: " + size + "px");
        style(button, "left: 0; top: 0; width: " + size + "px; height: " + size
            + "px; border-width: " + .5 * scale + "px");
        Element visual = host.getLastElementChild();
        double content = Math.max(0, size - inset * 2);
        style(visual, "left: " + inset + "px; top: " + inset + "px; width: " + content
            + "px; height: " + content + "px");
        // The first visual child is the canvas/item; optional slot labels keep their own size.
        style(visual.getFirstElementChild(), "width: " + content + "px; height: " + content + "px");
        if (visual.getChildElementCount() > 1)
            style(visual.getLastElementChild(), "left: " + scale + "px; top: 0; font-size: "
                + 4.5 * scale + "px; line-height: " + 7 * scale + "px");
    }

    static void style(Element element, String value) {
        if (!value.equals(element.getAttribute("style"))) element.setAttribute("style", value);
    }

    static void enabled(Element button, boolean enabled) {
        if (button.hasAttribute("disabled") == !enabled && host(button).hasAttribute("disabled") == !enabled) return;
        if (enabled) {
            button.removeAttribute("disabled");
            host(button).removeAttribute("disabled");
        } else {
            button.setAttribute("disabled", "");
            host(button).setAttribute("disabled", "");
        }
    }
}
