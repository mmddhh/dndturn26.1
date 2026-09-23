package cc.sighs.dndturn.client;

import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

/** A retained canvas around End Turn; reads server resource values, never advances a timer. */
final class MovementRing {
    private final Canvas canvas;
    private int remaining = -1, capacity = -1;

    MovementRing(Document document, Element button) {
        Element node = document.createElement("canvas");
        node.setAttribute("class", "movement-ring");
        node.setAttribute("width", "128");
        node.setAttribute("height", "128");
        canvas = (Canvas) TacticalButton.host(button).insertBefore(node, button);
    }

    void layout(double scale) {
        TacticalButton.style(canvas, "left: " + -4.5 * scale + "px; top: " + -4.5 * scale
            + "px; width: " + 32 * scale + "px; height: " + 32 * scale + "px");
    }

    void update(int remaining, int capacity) {
        if (this.remaining == remaining && this.capacity == capacity) return;
        this.remaining = remaining;
        this.capacity = capacity;
        canvas.renderOperation(g -> paint(g, remaining, capacity));
    }

    static void paint(Graphics2D g, int remaining, int capacity) {
        double fraction = capacity > 0 ? Math.clamp((double) remaining / capacity, 0, 1) : 0;
        // Canvas is retained: erase the previous arc before drawing a shorter one.
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, 128, 128);
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x605447));
        g.draw(new Arc2D.Double(8, 8, 112, 112, 90, -360, Arc2D.OPEN));
        g.setColor(new Color(remaining > capacity ? 0xe5cb90 : 0xa7d6bf));
        if (fraction > 0) g.draw(new Arc2D.Double(8, 8, 112, 112, 90, -360 * fraction, Arc2D.OPEN));
    }
}
