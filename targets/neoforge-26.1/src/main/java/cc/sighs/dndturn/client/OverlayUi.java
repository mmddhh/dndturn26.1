package cc.sighs.dndturn.client;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.LayoutCommit;

/** Client-thread projection boundary for our two AUI documents. Does not advance gameplay or AUI ticks. */
public final class OverlayUi {
    private OverlayUi() {}

    public static boolean stylesReady(Document document) {
        if (document == null || document != TacticalOverlay.document() && document != ConsentOverlay.document()) return true;
        // Both templates load tactical.css asynchronously. This rule is in that stylesheet,
        // not the global defaults or consent's inline styles; no elapsed-frame heuristic.
        return document.CSSCache.containsKey("#end-control");
    }

    public static boolean prepareForDraw(Document document) {
        boolean tactical = document != null && document == TacticalOverlay.document();
        boolean consent = document != null && document == ConsentOverlay.document();
        if (!tactical && !consent) return true;
        if (!document.isActive() || tactical && !TacticalOverlay.hudVisible()) return false;
        if (!stylesReady(document)) return false;
        try (var ignored = Document.withContext(document)) {
            document.applyViewport(false);
            if (tactical) TacticalOverlay.prepareFrame();
            else ConsentOverlay.tick();
            if (!document.isActive()) return false;
            // Finish all DOM mutations before committing styles, paint nodes and geometry.
            // Base.drawOverlayDocument reads the viewport before its normal style commit.
            document.commitStyleRecalc();
            if (document.hasPendingRenderState()) {
                document.commitRenderState();
                LayoutCommit.commit(document);
                document.markHitTestDirtyAll();
            }
        }
        return true;
    }

    /** Keep a real DOM text node alive instead of replacing AUI's lazy legacy render text on every value change. */
    static void text(Element element, String value) {
        var children = element.getChildNodes();
        if (children.size() == 1 && children.getFirst() instanceof TextNode text) {
            if (!value.equals(text.getData())) text.setData(value);
            return;
        }
        element.setTextContent("");
        element.appendChild(element.document.createTextNode(value));
    }
}
