package cc.sighs.dndturn.platform.client.ui;

import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

/** Native inventory previews over the AUI cards; retains no world entities or render states. */
public final class ParticipantPortraits {
    private static final double UPPER_BODY_FRACTION = .5;
    private static final double HEADROOM_FRACTION = .06;
    private ParticipantPortraits() {}

    static Element append(Document document, Element row, boolean environment) {
        Element node = document.createElement(environment ? "canvas" : "div");
        node.setAttribute("class", environment ? "member-globe" : "member-portrait");
        if (environment) {
            node.setAttribute("width", "96");
            node.setAttribute("height", "96");
        }
        Element portrait = row.appendChild(node);
        if (portrait instanceof Canvas canvas) canvas.renderOperation(g -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x183b48));
            g.fill(new Ellipse2D.Double(6, 6, 84, 84));
            g.setStroke(new BasicStroke(2.5f));
            g.setColor(new Color(0x9dd9ef));
            g.draw(new Ellipse2D.Double(6, 6, 84, 84));
            g.draw(new Ellipse2D.Double(25, 6, 46, 84));
            g.drawLine(48, 6, 48, 90);
            g.drawLine(6, 48, 90, 48);
            g.draw(new Ellipse2D.Double(12, 23, 72, 18));
            g.draw(new Ellipse2D.Double(12, 55, 72, 18));
        });
        return portrait;
    }

    static void layout(Element portrait, double scale) {
        boolean globe = portrait instanceof Canvas;
        TacticalButton.style(portrait, "left: " + 2 * scale + "px; top: " + (globe ? 6.5 : 2) * scale
            + "px; width: " + 25 * scale + "px; height: " + (globe ? 25 : 34) * scale + "px");
    }

    /** Called after AUI's full-screen PIP submission and before its cursor submission. */
    public static void extract(GuiGraphicsExtractor graphics) {
        Minecraft game = Minecraft.getInstance();
        Document document = TacticalOverlay.document();
        if (document == null || game.screen != null || ConsentOverlay.isOpen()
            || !TacticalOverlay.hudVisible() || !OverlayUi.prepareForDraw(document)) return;
        try (var ignored = Document.withContext(document)) {
            ScreenRectangle clip = bounds(document, document.getElementById("initiative"));
            if (clip.width() <= 0 || clip.height() <= 0) return;
            Map<UUID, ScreenRectangle> visible = new LinkedHashMap<>();
            TacticalOverlay.portraits().forEach((id, element) -> {
                if (element instanceof Canvas) return;
                ScreenRectangle box = bounds(document, element);
                if (box.width() > 0 && box.height() > 0 && box.intersection(clip) != null) visible.put(id, box);
            });
            if (visible.isEmpty()) return;
            graphics.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
            try {
                // Resolve only this frame's instances; one pass for all visible cards, no per-card world scan.
                for (var entity : game.level.entitiesForRendering()) {
                    ScreenRectangle box = visible.get(entity.getUUID());
                    if (box == null || !(entity instanceof LivingEntity living) || entity.isRemoved()) continue;
                    float entityScale = living.getScale();
                    double height = living.getBbHeight() / entityScale;
                    if (!Double.isFinite(height) || height <= 0) continue;
                    // Frame the upper half with a little space above the head. Fit height only:
                    // wide models crop at the sides instead of shrinking back to a full-body view.
                    int size = Math.max(1, (int) Math.ceil(box.height()
                        / (height * (UPPER_BODY_FRACTION + HEADROOM_FRACTION))));
                    // Inventory's default translation centers height / 2. Move that waist line
                    // to the bottom edge; the PIP texture bounds clip the lower body and sides.
                    float offsetY = box.height() / (2F * size);
                    // JER's inventory-preview path, with a stable three-quarter pose. The fixed version
                    // changes only extracted render-state angles; never setId/rotate/tick the real entity.
                    InventoryScreen.renderEntityInInventoryFollowsAngle(graphics,
                        box.left(), box.top(), box.right(), box.bottom(), size, offsetY, .9f, .1f, living);
                    visible.remove(entity.getUUID());
                    if (visible.isEmpty()) break;
                }
                for (ScreenRectangle box : visible.values())
                    graphics.text(game.font, "?", (box.left() + box.right() - game.font.width("?")) / 2,
                        (box.top() + box.bottom() - game.font.lineHeight) / 2, 0xffa49682, false);
            } finally {
                graphics.disableScissor();
            }
        }
    }

    private static ScreenRectangle bounds(Document document, Element element) {
        // AUI's committed Rect already includes scrolling. Convert once from document pixels to GUI pixels.
        var rect = element.getBoundingClientRect();
        Position start = document.documentToGuiPosition(new Position(rect.left, rect.top));
        Position end = document.documentToGuiPosition(new Position(rect.right, rect.bottom));
        int left = (int) Math.ceil(start.x), top = (int) Math.ceil(start.y);
        return new ScreenRectangle(left, top, Math.max(0, (int) Math.floor(end.x) - left),
            Math.max(0, (int) Math.floor(end.y) - top));
    }
}
