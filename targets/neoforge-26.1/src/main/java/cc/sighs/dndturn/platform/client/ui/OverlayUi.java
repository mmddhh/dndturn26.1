package cc.sighs.dndturn.platform.client.ui;

import com.sighs.apricityui.dom.TextNode;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.LayoutCommit;

/** Client-thread projection boundary for our two AUI documents. Does not advance gameplay or AUI ticks. */
public final class OverlayUi {
    private static boolean reloadPending, reloadRegistered;
    private OverlayUi() {}

    public static void registerReload(net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) {
        if (reloadRegistered) return;
        reloadRegistered = true;
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath("dndturn", "ui"),
            new net.minecraft.server.packs.resources.PreparableReloadListener() {
                @Override
                public java.util.concurrent.CompletableFuture<Void> reload(SharedState state,
                        java.util.concurrent.Executor background, PreparationBarrier barrier,
                        java.util.concurrent.Executor game) {
                    return java.util.concurrent.CompletableFuture.<Void>completedFuture(null)
                        .thenCompose(barrier::wait).thenRunAsync(() -> reloadPending = true, game);
                }
            });
    }

    /** Resource-ready client tick, including the title screen; never captures a world/player. */
    public static void warmup() {
        if (net.minecraft.client.Minecraft.getInstance().getOverlay() != null) return;
        if (reloadPending) {
            reloadPending = false;
            refresh(TacticalOverlay.document());
            refresh(ConsentOverlay.document());
        }
        TacticalOverlay.ensureDocument();
        ConsentOverlay.ensureDocument();
        syncVisibility(TacticalOverlay.document());
        syncVisibility(ConsentOverlay.document());
    }

    private static void refresh(Document document) {
        if (document == null || !document.isActive()) return;
        resetInteraction(document);
        document.refresh();
    }

    static void resetInteraction(Document document) {
        if (document == null) return;
        var pressed = document.getPressedElement();
        if (pressed != null && pressed.isScrollbarInteractionActive()) pressed.handleScrollbarMouseUp(null);
        document.setPressedElement(null);
        document.clearFocus(); document.clearDocumentSelection(); document.clearRichTextSelection();
        for (var element : document.getElements()) if (element.isHover) element.setHover(false);
        cc.sighs.dndturn.platform.client.input.ClientControl.documentClosed(document);
    }

    static void resetSession(Document document) {
        resetInteraction(document);
        if (document == null) return;
        for (var element : document.getElements()) {
            if (element.getScrollTop() != 0 || element.getTargetScrollTop() != 0) element.setScrollTop(0);
            if (element.getScrollLeft() != 0 || element.getTargetScrollLeft() != 0) element.setScrollLeft(0);
        }
        syncVisibility(document);
    }

    static void syncVisibility(Document document) {
        if (document == null || document.body == null) return;
        String style = visible(document) ? "display: block" : "display: none";
        if (!style.equals(document.body.getAttribute("style"))) {
            if (!visible(document)) resetInteraction(document);
            TacticalButton.style(document.body, style);
        }
    }

    public static boolean unobstructed() {
        var game = net.minecraft.client.Minecraft.getInstance();
        return game.screen == null && game.getOverlay() == null && !game.options.hideGui;
    }

    public static boolean visible(Document document) {
        if (document == null) return false;
        if (document == TacticalOverlay.document()) return TacticalOverlay.isOpen() && TacticalOverlay.hudVisible();
        if (document == ConsentOverlay.document()) return ConsentOverlay.visible();
        return true;
    }

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
        if (!document.isActive() || !visible(document)) return false;
        if (!stylesReady(document)) return false;
        try (var ignored = Document.withContext(document)) {
            syncVisibility(document);
            document.applyViewport(false);
            if (tactical) TacticalOverlay.prepareFrame();
            else ConsentOverlay.prepareFrame();
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
