package cc.sighs.dndturn.platform.client.ui;

import cc.sighs.dndturn.platform.network.EncounterProtocol.EffectNotice;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Retained, read-only self effect projection. No local ticking or native effect mutation. */
final class PlayerEffects {
    private final Document document;
    private final Element panel, tooltip;
    private final List<Entry> pool = new ArrayList<>();
    private record Duration(long remaining, long capacity) {}
    private final Map<String, Duration> capacities = new HashMap<>();
    private List<EffectNotice> effects = List.of();
    private Entry hovered;
    private double left, top, width, height, scale;
    private int columns;

    private final class Entry {
        final Element node;
        final Canvas ring;
        EffectNotice value;
        long remaining = Long.MIN_VALUE, capacity = Long.MIN_VALUE;
        Entry() {
            node = panel.appendChild(document.createElement("div"));
            node.setAttribute("class", "self-effect");
            var canvas = document.createElement("canvas");
            canvas.setAttribute("width", "128"); canvas.setAttribute("height", "128");
            canvas.setAttribute("class", "effect-ring");
            ring = (Canvas) node.appendChild(canvas);
            node.addEventListener("mouseenter", event -> { hovered = this; describe(); });
            node.addEventListener("mouseleave", event -> { if (hovered == this) { hovered = null; describe(); } });
        }
    }

    PlayerEffects(Document document) {
        this.document = document;
        panel = document.getElementById("self-effects");
        tooltip = document.getElementById("effect-tooltip");
        for (int i = 0; i < 8; i++) pool.add(new Entry());
        clear();
    }

    void clear() {
        effects = List.of(); capacities.clear(); hovered = null;
        for (var entry : pool) { entry.value = null; TacticalButton.style(entry.node, "display: none"); }
        TacticalButton.style(panel, "display: none");
        describe();
    }

    void update(List<EffectNotice> next) {
        if (effects.equals(next)) return;
        effects = next;
        var active = new java.util.HashSet<String>();
        while (pool.size() < next.size()) pool.add(new Entry());
        for (int i = 0; i < pool.size(); i++) {
            var entry = pool.get(i);
            var value = i < next.size() ? next.get(i) : null;
            if (entry.value == null || value == null || !entry.value.id().equals(value.id()) || entry.value.instant() != value.instant()) {
                if (hovered == entry) hovered = null;
            }
            entry.value = value;
            if (value == null) { TacticalButton.style(entry.node, "display: none"); continue; }
            String key = value.id() + "/" + value.level() + "/" + value.instant();
            active.add(key);
            var duration = capacities.compute(key, (ignored, previous) -> new Duration(value.rounds(),
                previous == null || value.rounds() > previous.remaining() ? Math.max(1, value.rounds()) : previous.capacity()));
            long capacity = duration.capacity();
            long remaining = value.instant() || value.rounds() == -1 ? capacity : value.rounds();
            if (entry.remaining != remaining || entry.capacity != capacity) {
                entry.remaining = remaining; entry.capacity = capacity;
                entry.ring.renderOperation(g -> MovementRing.paint(g, (int) Math.min(Integer.MAX_VALUE, remaining), (int) Math.min(Integer.MAX_VALUE, capacity)));
            }
        }
        capacities.keySet().retainAll(active);
        arrange(); describe();
    }

    void layout(double dockLeft, double dockTop, double dockHeight, double dockScale) {
        scale = Math.min(dockScale, Math.max(.35, (dockLeft - 12) / 52));
        width = Math.max(1, Math.min(76 * scale, dockLeft - 12));
        height = dockHeight;
        left = Math.max(4, dockLeft - width - 6);
        top = dockTop;
        columns = Math.max(1, (int) ((width - 4 * scale) / (24 * scale)));
        arrange(); describe();
    }

    private void arrange() {
        if (scale <= 0) return;
        int rows = Math.max(1, (effects.size() + columns - 1) / columns);
        double panelHeight = Math.min(height, (rows * 24 + 4) * scale);
        double panelTop = top + (height - panelHeight) / 2;
        TacticalButton.style(panel, (effects.isEmpty() ? "display: none; " : "display: block; ")
            + "left: " + left + "px; top: " + panelTop + "px; width: " + width + "px; height: " + panelHeight + "px");
        for (int i = 0; i < effects.size(); i++) {
            var entry = pool.get(i);
            TacticalButton.style(entry.node, "display: block; left: " + (2 + (i % columns) * 24) * scale
                + "px; top: " + (2 + (i / columns) * 24) * scale + "px; width: " + 24 * scale + "px; height: " + 24 * scale + "px");
        }
    }

    private static String label(String key, Object... args) {
        return Component.translatable("dndturn.effects." + key, args).getString();
    }

    private void describe() {
        if (hovered == null || hovered.value == null) { TacticalButton.style(tooltip, "display: none"); return; }
        var effect = hovered.value;
        var nativeEffect = BuiltInRegistries.MOB_EFFECT.getValue(Identifier.parse(effect.id()));
        String name = nativeEffect == null ? effect.id() : Component.translatable(nativeEffect.getDescriptionId()).getString();
        String duration = effect.instant() && effect.ticks() == 0 ? label("instant") : effect.rounds() == -1 ? label("infinite") : label("rounds", effect.rounds());
        String details = name + " " + label("level", effect.level()) + "\n" + duration;
        if (!effect.instant() && effect.ticks() >= 0) details += "\n" + label("native_time", String.format(java.util.Locale.ROOT, "%.1f", effect.ticks() / 20.0));
        if (nativeEffect != null) {
            details += "\n" + label(nativeEffect.isBeneficial() ? "beneficial" : "other");
            String description = nativeEffect.getDescriptionId() + ".description";
            if (net.minecraft.client.resources.language.I18n.exists(description)) details += "\n" + Component.translatable(description).getString();
            var attributes = new ArrayList<String>();
            nativeEffect.createModifiers(effect.level() - 1, (attribute, modifier) -> {
                if (attributes.size() >= 32 || modifier.amount() == 0) return;
                double amount = modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                    ? modifier.amount() : modifier.amount() * 100;
                attributes.add(Component.translatable("attribute.modifier." + (amount > 0 ? "plus." : "take.") + modifier.operation().id(),
                    String.format(java.util.Locale.ROOT, "%.2f", Math.abs(amount)),
                    Component.translatable(attribute.value().getDescriptionId())).getString());
            });
            if (!attributes.isEmpty()) details += "\n" + String.join("\n", attributes);
        }
        details += "\n" + label("support." + effect.support().toLowerCase(java.util.Locale.ROOT)) + "\n" + effect.id();
        OverlayUi.text(tooltip, details);
        double tipWidth = Math.min(220, document.getViewportSize().width() - 8);
        TacticalButton.style(tooltip, "display: block; left: " + Math.clamp(left, 4, Math.max(4, document.getViewportSize().width() - tipWidth - 4))
            + "px; bottom: " + (document.getViewportSize().height() - top + 6) + "px; width: " + tipWidth + "px; max-height: " + Math.max(40, top - 8) + "px");
    }

    void extract(GuiGraphicsExtractor graphics) {
        if (effects.isEmpty() || scale <= 0) return;
        var clip = panel.getBoundingClientRect();
        Position a = document.documentToGuiPosition(new Position(clip.left, clip.top));
        Position b = document.documentToGuiPosition(new Position(clip.right, clip.bottom));
        graphics.enableScissor((int) a.x, (int) a.y, (int) b.x, (int) b.y);
        try {
            for (int i = 0; i < effects.size(); i++) {
                var entry = pool.get(i);
                var rect = entry.node.getBoundingClientRect();
                Position start = document.documentToGuiPosition(new Position(rect.left + 5 * scale, rect.top + 5 * scale));
                Position end = document.documentToGuiPosition(new Position(rect.right - 5 * scale, rect.bottom - 5 * scale));
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.parse(entry.value.id()).withPrefix("mob_effect/"),
                    (int) start.x, (int) start.y, Math.max(1, (int) (end.x - start.x)), Math.max(1, (int) (end.y - start.y)));
            }
        } finally { graphics.disableScissor(); }
    }
}
