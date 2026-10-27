package cc.sighs.dndturn.platform.client.ui;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.TurnParticipant;
import cc.sighs.dndturn.platform.client.action.ClientTacticalPlan;
import cc.sighs.dndturn.platform.client.input.ClientControl;
import cc.sighs.dndturn.platform.client.input.CombatControls;
import cc.sighs.dndturn.platform.client.state.ClientCombatState;
import cc.sighs.dndturn.platform.client.state.ClientInspection;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** Client-only projection. No DOM value is accepted as a cost, roll, or permission. */
public final class TacticalOverlay {
    private static final double PANEL_HEIGHT_FRACTION = 0.20;
    private static final double DOCK_HEIGHT_FRACTION = 0.25;
    private static final int SLOT_SIZE = 13;
    private static final int DOCK_DESIGN_HEIGHT = SLOT_SIZE * 2 + 35;
    private static final int DOCK_SLOTS_TOP = 19;
    private static final int INITIATIVE_DESIGN_HEIGHT = 54;
    private static Document document;
    private static MovementRing movementRing;
    private static PlayerEffects playerEffects;
    private static long refreshGeneration = -1;
    private static UUID encounterId;
    private static int retryDelay;
    private static boolean projectionPending = true;
    private static final Map<String, String> values = new HashMap<>();
    private static final Map<UUID, Element> members = new LinkedHashMap<>();
    private static final Map<UUID, Element> memberLabels = new HashMap<>();
    private static final Map<UUID, Element> memberPortraits = new LinkedHashMap<>();
    private static final net.minecraft.world.item.ItemStack[] inventory = new net.minecraft.world.item.ItemStack[36];
    private static boolean inventoryOpen;
    private static boolean abilitiesTab;
    private static double contextX = .5, contextY = .5;
    private static final List<String> ACTION_SLOTS = List.of("move", "attack", "dash", "dodge", "disengage", "use-block", "break-block");
    private static List<UUID> memberOrder = List.of();

    private TacticalOverlay() {}

    public static boolean isOpen() { return encounterId != null && document != null && document.isActive(); }

    /** The shell survives encounters; no vanilla screen shares its input or visible HUD. */
    public static boolean hudVisible() {
        var game = Minecraft.getInstance();
        return ClientCombatState.encounter() != null && game.level != null && game.player != null
            && game.player.isAlive() && OverlayUi.unobstructed();
    }

    static void prepareFrame() {
        var state = ClientCombatState.encounter();
        var player = Minecraft.getInstance().player;
        if (state == null || player == null) return;
        if (document.getRefreshGeneration() != refreshGeneration || projectionPending) {
            if (document.getRefreshGeneration() != refreshGeneration) bind();
            renderProjection(state, player.getUUID(), ClientCombatState.latestStatus());
            updateInventory();
        }
        updateLayout();
    }

    /** GUI pixels reserved below chat; recomputed from the same geometry as the action dock. */
    public static int chatBottomInset() {
        if (!isOpen() || !hudVisible()) return 0;
        document.applyViewport(false);
        var size = document.getViewportSize();
        int height = Math.max(1, (int) size.height());
        double top = dockTop(height);
        double guiTop = document.documentToGuiPosition(new com.sighs.apricityui.layout.Position(0, top)).y;
        int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.clamp((int) Math.ceil(guiHeight - guiTop + 4), 0, Math.max(0, guiHeight - 60));
    }

    public static void hideVanillaHotbar(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (!hudVisible()) return;
        if (event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR)
            || event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.SELECTED_ITEM_NAME)
            || event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.EFFECTS))
            event.setCanceled(true);
    }

    public static void extractEffects(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        if (playerEffects != null && hudVisible() && !ConsentOverlay.isOpen()
            && OverlayUi.prepareForDraw(document)) {
            try (var ignored = Document.withContext(document)) { playerEffects.extract(graphics); }
        }
    }

    public static UUID selectedTarget() { return ClientTacticalPlan.inspected(); }
    public static Document document() { return document; }
    static Map<UUID, Element> portraits() { return java.util.Collections.unmodifiableMap(memberPortraits); }
    static void select(UUID target) { ClientTacticalPlan.inspect(target); }
    public static void cancelGesture() {
        if (document == null) return;
        var pressed = document.getPressedElement();
        if (pressed != null && pressed.isScrollbarInteractionActive()) pressed.handleScrollbarMouseUp(null);
        document.setPressedElement(null); document.clearFocus();
    }
    public static boolean hit(com.sighs.apricityui.layout.Position position) {
        return isOpen() && hudVisible() && OverlayUi.stylesReady(document) && Minecraft.getInstance().screen == null
            && document.hitTest(document.screenToDocumentPosition(position)) != null;
    }

    public static boolean closePanel() {
        if (ClientInspection.close()) return true;
        if (!inventoryOpen) return false;
        inventoryOpen = false; attribute("inventory-panel", "style", "display: none"); return true;
    }
    public static void toggleInventory() {
        if (!isOpen()) return;
        inventoryOpen = !inventoryOpen;
        attribute("inventory-panel", "style", inventoryOpen ? "display: block" : "display: none");
    }

    public static void close() {
        ClientInspection.close();
        if (encounterId == null) return;
        cancelGesture(); ClientControl.documentClosed(document);
        encounterId = null;
        if (playerEffects != null) playerEffects.clear();
        clearMembers();
        clearBehaviors();
        if (document != null && document.isActive()) {
            for (int i = 0; i < inventory.length; i++) {
                if (document.getElementById("inventory-" + i) instanceof com.sighs.apricityui.element.Item item)
                    item.setIngredientStack(net.minecraft.world.item.ItemStack.EMPTY);
                if (i < 9 && document.getElementById("hotbar-item-" + i) instanceof com.sighs.apricityui.element.Item item)
                    item.setIngredientStack(net.minecraft.world.item.ItemStack.EMPTY);
            }
            for (String id : List.of("description", "status", "target", "inspection-content")) text(id, "");
        }
        java.util.Arrays.fill(inventory, null);
        inventoryOpen = false;
        abilitiesTab = false;
        contextX = contextY = .5;
        layoutKey = null;
        lastMembersState = null;
        projectionPending = true;
        values.clear();
        OverlayUi.resetSession(document);
    }

    public static void tick() {
        ClientInspection.tick();
        var state = ClientCombatState.encounter();
        Minecraft game = Minecraft.getInstance();
        if (state == null || game.player == null || !game.player.isAlive()) { close(); return; }
        if (!state.encounterId().equals(encounterId)) { close(); encounterId = state.encounterId(); }
        if (hudVisible()) {
            renderProjection(state, game.player.getUUID(), ClientCombatState.latestStatus());
            updateInventory();
        } else projectionPending = true;
    }

    static void ensureDocument() {
        if (document != null && document.isActive()) {
            if (document.getRefreshGeneration() != refreshGeneration) bind();
            return;
        }
        if (retryDelay-- > 0 || com.sighs.apricityui.parser.HTML.getTemple("dndturn/tactical.html") == null) return;
        ClientControl.documentClosed(document);
        document = ApricityUI.createDocument("dndturn/tactical.html");
        if (document == null) { retryDelay = 100; return; }
        bind();
    }

    static void renderProjection(EncounterProtocol.EncounterState state, UUID viewer,
                                 EncounterProtocol.IntentStatus status) {
        ensureDocument();
        if (document == null || !document.isActive()) return;
        if (document.getRefreshGeneration() != refreshGeneration) bind();
        projectionPending = false;
        boolean myTurn = state.interactive() && viewer.equals(state.current()) && state.phase() != EncounterPhase.ENVIRONMENT;
        movementRing.update(state.movementTicks(), state.movementTicksPerTurn());
        playerEffects.update(state.effects());
        text("environment", "环境 " + state.environmentRemaining());
        updateMembers(state);
        text("investigate", ClientInspection.label("open"));
        text("inspection-title", ClientInspection.label("title"));
        text("inspection-refresh", ClientInspection.label("refresh"));
        text("inspection-close", ClientInspection.label("close"));
        text("inspection-content", ClientInspection.content());
        text("inspection-section", ClientInspection.sectionLabel());
        text("inspection-page", ClientInspection.pageLabel());
        enabled("inspection-section", ClientInspection.ownCharacter());
        enabled("inspection-prev", ClientInspection.hasPage(-1));
        enabled("inspection-next", ClientInspection.hasPage(1));
        enabled("investigate", selectedTarget() != null && state.members().stream().anyMatch(m ->
            m.id().equals(selectedTarget()) && m.kind() == TurnParticipant.Kind.ENTITY));
        enabled("move", myTurn && state.movementTicks() > 0);
        for (String id : List.of("attack", "dash", "dodge", "disengage"))
            enabled(id, myTurn && (id.equals("attack") || state.phase() == EncounterPhase.ACTIVE)
                && state.action() && !state.moving()
                );
        renderBehaviors();
        for (var entry : Map.of("break-block", ActionIntent.Capability.BREAK,
                "use-block", ActionIntent.Capability.USE_BLOCK).entrySet())
            enabled(entry.getKey(), myTurn && !ClientTacticalPlan.running());
        enabled("end", myTurn);
        enabled("exit", myTurn && !ClientTacticalPlan.running());
        var exit = state.exit();
        if (exit != null) values.put("exit:description", switch (exit.code()) {
            case EXIT_NOT_TURN -> "只能在自己的回合逃跑";
            case EXIT_BUSY -> "行动或反应执行中，结束后可重试";
            case EXIT_POSITION_UNKNOWN -> "敌人位置未知，可稍后重试";
            default -> exit.nearestDistance() < 0 ? "本场无存活敌人，可免费退出"
                : String.format(java.util.Locale.ROOT, "最近敌人 %.2f 格 / 逃跑所需 %.2f 格；提交时复验",
                    exit.nearestDistance(), exit.requiredDistance());
        });
        else values.remove("exit:description");
        for (var button : behaviorButtons) {
            TacticalButton.enabled(button, myTurn && !ClientTacticalPlan.running());
        }
        for (int i = 0; i < 9; i++) enabled("hotbar-slot-" + i, myTurn && !ClientTacticalPlan.running());
        text("status", ClientTacticalPlan.description());
        attribute("status", "class", status != null && !status.accepted() ? "rejected" : "muted");
        updateLayout();
    }

    private static void bind() {
        refreshGeneration = document.getRefreshGeneration();
        document.getElementById("investigate").addEventListener("click", event -> {
            if ("true".equals(values.get("investigate:enabled"))) ClientInspection.request(selectedTarget(), ClientControl.modal());
        });
        document.getElementById("inspection-refresh").addEventListener("click", event -> ClientInspection.refresh(ClientControl.modal()));
        document.getElementById("inspection-close").addEventListener("click", event -> ClientInspection.close());
        document.getElementById("inspection-section").addEventListener("click", event -> ClientInspection.nextSection(ClientControl.modal()));
        document.getElementById("inspection-prev").addEventListener("click", event -> ClientInspection.page(-1, ClientControl.modal()));
        document.getElementById("inspection-next").addEventListener("click", event -> ClientInspection.page(1, ClientControl.modal()));
        renderedOffers = List.of(); renderedAbilities = List.of(); renderedPhase = null;
        behaviorButtons.clear(); offerButtons.clear(); behaviorPool.clear(); offerPool.clear();
        abilityBindings.clear(); offerBindings.clear(); abilityIcons.clear();
        memberPool.clear(); memberNodes.clear();
        lastMembersState = null; layoutKey = null; projectionPending = true;
        values.clear(); members.clear(); memberLabels.clear(); memberPortraits.clear(); memberOrder = List.of();
        java.util.Arrays.fill(inventory, null);
        ability("move", ActionIntent.Capability.MOVE, "移动按实际获准位移计费；再次点击停止。结束回合会先结算移动。");
        ability("attack", ActionIntent.Capability.ATTACK, "消耗动作。d20＋5 对目标 AC；自然 1 未命中，自然 20 暴击。选择目标后自动接近并攻击。");
        action("dash", EncounterProtocol.IntentKind.DASH, "消耗动作，增加本会话捕获的一份基础移动预算。");
        action("dodge", EncounterProtocol.IntentKind.DODGE, "消耗动作，直到下次自身回合开始前，对你的命中检定有劣势。");
        action("disengage", EncounterProtocol.IntentKind.DISENGAGE, "消耗动作，设置本回合撤离状态。借机攻击流程尚未开放。");
        action("end", EncounterProtocol.IntentKind.END_TURN, "先结束并结算正在进行的移动，然后请求结束回合。");
        action("exit", EncounterProtocol.IntentKind.EXIT, "自己回合且无执行中行动或反应时可免费逃跑；距本场所有存活敌人的水平距离须达到建场配置（默认16格）。无敌人可直接退出，最后玩家退出后结束场次。");
        ability("use-block", ActionIntent.Capability.USE_BLOCK, "使用方块自身，不消耗动作；接近仍消耗移动。");
        ability("break-block", ActionIntent.Capability.BREAK, "选择要破坏的方块。");
        document.getElementById("items-tab").addEventListener("click", event -> { abilitiesTab = false; updateLayout(); });
        document.getElementById("abilities-tab").addEventListener("click", event -> { abilitiesTab = true; updateLayout(); });
        Element spells = document.getElementById("spell-slots");
        for (int i = 0; i < 9; i++) {
            Element slot = spells.appendChild(document.createElement("div"));
            slot.setAttribute("class", "empty-slot");
        }
        document.getElementById("behavior-hand").addEventListener("click", event -> { ClientTacticalPlan.toggleHand(); });
        document.getElementById("cancel-plan").addEventListener("click", event -> ClientTacticalPlan.cancel());
        document.getElementById("inventory-toggle").addEventListener("click", event -> toggleInventory());
        attribute("inventory-panel", "style", inventoryOpen ? "display: block" : "display: none");
        Element slots = document.getElementById("inventory");
        for (int i = 0; i < inventory.length; i++) {
            Element slot = document.createElement("slot");
            slot.setAttribute("class", "inventory-slot");
            final int selectedSlot = i;
            slot.addEventListener("click", event -> {
                var player = Minecraft.getInstance().player;
                if (player == null) return;
                if (selectedSlot > 8) { Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(player)); return; }
                ClientTacticalPlan.selectSlot(selectedSlot);
            });
            Element item = document.createElement("item");
            item.setAttribute("id", "inventory-" + i);
            slot.appendChild(item);
            slots.appendChild(slot);
        }
        for (String id : ACTION_SLOTS) TacticalIcons.append(document, document.getElementById(id), id);
        for (String id : List.of("end", "exit", "inventory-toggle", "behavior-hand", "cancel-plan")) {
            Element button = document.getElementById(id);
            TacticalIcons.append(document, button, id);
            if (id.equals("behavior-hand") || id.equals("cancel-plan") || id.equals("inventory-toggle"))
                button.addEventListener("mouseenter", event -> text("description", switch (id) {
                    case "behavior-hand" -> "切换主 / 副手 · 重新查询物品能力";
                    case "cancel-plan" -> "取消当前选择或请求停止行动";
                    default -> "随身物品 · 选择热栏物品准备行动";
                }));
        }
        Element hotbar = document.getElementById("hotbar");
        movementRing = new MovementRing(document, document.getElementById("end"));
        playerEffects = new PlayerEffects(document);
        for (int i = 0; i < 9; i++) {
            final int index = i;
            Element button = document.createElement("button");
            button.setAttribute("id", "hotbar-slot-" + i);
            button.setAttribute("class", "action-slot");
            button = hotbar.appendChild(button);
            Element visual = TacticalButton.visual(document, button);
            button.addEventListener("click", event -> {
                if ("true".equals(values.get("hotbar-slot-" + index + ":enabled"))) ClientTacticalPlan.selectSlot(index);
            });
            button.addEventListener("mouseenter", event -> {
                var player = Minecraft.getInstance().player;
                if (player != null) text("description", "物品 " + (index + 1) + " · " + player.getInventory().getItem(index).getHoverName().getString() + " · 点击准备能力");
            });
            Element item = document.createElement("item");
            item.setAttribute("id", "hotbar-item-" + i);
            item.setAttribute("class", "hotbar-item");
            visual.appendChild(item);
            Element number = document.createElement("span");
            number.setAttribute("class", "slot-number");
            OverlayUi.text(number, Integer.toString(i + 1));
            visual.appendChild(number);
        }
        for (int i = 0; i < 9; i++) { behaviorSlot(i); offerSlot(i); }
        for (int i = 0; i < 8; i++) memberPool.add(newMemberNodes());
    }

    private static ClientTacticalPlan.Phase renderedPhase;
    private static List<ActionProtocol.Ability> renderedAbilities = List.of();
    private static List<ActionProtocol.Offer> renderedOffers = List.of();
    private static final java.util.List<Element> behaviorButtons = new java.util.ArrayList<>();
    private static final java.util.List<Element> offerButtons = new java.util.ArrayList<>();
    private static final java.util.List<Element> behaviorPool = new java.util.ArrayList<>();
    private static final java.util.List<Element> offerPool = new java.util.ArrayList<>();
    private static final Map<Element, ActionProtocol.Ability> abilityBindings = new HashMap<>();
    private static final Map<Element, ActionProtocol.Offer> offerBindings = new HashMap<>();
    private static final Map<Element, String> abilityIcons = new HashMap<>();

    private static Element behaviorSlot(int index) {
        while (behaviorPool.size() <= index) {
            Element button = document.getElementById("behavior-options").appendChild(document.createElement("button"));
            button.addEventListener("click", event -> {
                var ability = abilityBindings.get(button);
                if (ability != null && !button.hasAttribute("disabled") && ClientControl.allowDocument(document))
                    ClientTacticalPlan.selectBehavior(ability);
            });
            button.addEventListener("mouseenter", event -> text("description", button.getAttribute("aria-label")));
            TacticalIcons.append(document, button, "use-item");
            abilityIcons.put(button, "use-item");
            TacticalButton.style(TacticalButton.host(button), "display: none");
            behaviorPool.add(button);
        }
        return behaviorPool.get(index);
    }

    private static Element offerSlot(int index) {
        while (offerPool.size() <= index) {
            Element button = document.getElementById("context-options").appendChild(document.createElement("button"));
            button.addEventListener("click", event -> {
                var offer = offerBindings.get(button);
                if (offer != null && offer.reason().isEmpty() && ClientControl.allowDocument(document)) ClientTacticalPlan.choose(offer);
            });
            button.addEventListener("mouseenter", event -> {
                var offer = offerBindings.get(button);
                if (offer != null) text("description", offer.reason());
            });
            TacticalButton.style(button, "display: none");
            offerPool.add(button);
        }
        return offerPool.get(index);
    }

    private static void clearBehaviors() {
        abilityBindings.clear(); offerBindings.clear();
        for (var button : behaviorPool) TacticalButton.style(TacticalButton.host(button), "display: none");
        for (var button : offerPool) { TacticalButton.style(button, "display: none"); OverlayUi.text(button, ""); }
        behaviorButtons.clear(); offerButtons.clear();
        renderedAbilities = List.of(); renderedOffers = List.of(); renderedPhase = null;
    }

    private static void renderBehaviors() {
        var offers = ClientTacticalPlan.offers();
        var abilities = ClientTacticalPlan.abilities();
        if (!abilities.equals(renderedAbilities)) {
            OverlayUi.resetInteraction(document);
            abilityBindings.clear(); behaviorButtons.clear();
            boolean itemUseAdded = false;
            for (var ability : abilities) {
                if (ability.kind() == ActionIntent.Capability.MOVE || ability.kind() == ActionIntent.Capability.USE_BLOCK) continue;
                boolean itemUse = cc.sighs.dndturn.platform.client.action.ItemUseSelection.includes(ability);
                if (itemUse && itemUseAdded) continue;
                itemUseAdded |= itemUse;
                Element button = behaviorSlot(behaviorButtons.size());
                String label = itemUse ? "使用物品" : ability.kind() == ActionIntent.Capability.ATTACK ? "攻击" : ability.label();
                button.setAttribute("aria-label", label);
                abilityBindings.put(button, ability); behaviorButtons.add(button);
                String icon = itemUse ? "use-item" : switch (ability.kind()) {
                    case MOVE -> "move"; case ATTACK -> "attack"; case PLACE -> "place";
                    case BREAK -> "break-block"; case USE_BLOCK -> "use-block"; case USE_ITEM, EQUIP -> "use-item";
                };
                if (!icon.equals(abilityIcons.put(button, icon))) TacticalIcons.update(button, icon);
            }
            for (int i = behaviorButtons.size(); i < behaviorPool.size(); i++)
                TacticalButton.style(TacticalButton.host(behaviorPool.get(i)), "display: none");
            renderedAbilities = abilities;
            layoutKey = null;
        }
        if (!offers.equals(renderedOffers) || renderedPhase != ClientTacticalPlan.phase()) {
            OverlayUi.resetInteraction(document);
            offerBindings.clear(); offerButtons.clear();
            if (ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.CONTEXT_MENU) for (var offer : offers) {
                Element button = offerSlot(offerButtons.size());
                OverlayUi.text(button, cc.sighs.dndturn.platform.client.action.ItemUseSelection.includes(offer.intent())
                    ? "使用物品" : offer.intent().capability() == ActionIntent.Capability.ATTACK ? "攻击" : offer.label());
                if (offer.reason().isEmpty()) button.removeAttribute("disabled"); else button.setAttribute("disabled", "");
                TacticalButton.style(button, "display: block");
                offerBindings.put(button, offer); offerButtons.add(button);
            }
            for (int i = offerButtons.size(); i < offerPool.size(); i++) TacticalButton.style(offerPool.get(i), "display: none");
            renderedOffers = offers;
            renderedPhase = ClientTacticalPlan.phase();
        }
        text("behavior-empty", ClientTacticalPlan.abilityStatus());
        text("context-status", ClientTacticalPlan.contextStatus());
        attribute("context-status", "style", offerButtons.isEmpty() ? "display: block" : "display: none");
    }
    private static void ability(String id, ActionIntent.Capability capability, String description) {
        var element = document.getElementById(id);
        element.addEventListener("click", event -> { if (!"false".equals(values.get(id + ":enabled"))) ClientTacticalPlan.select(capability); });
        element.addEventListener("mouseenter", event -> text("description", element.getAttribute("aria-label") + " · " + values.getOrDefault(id + ":description", description)));
    }

    private static void action(String id, EncounterProtocol.IntentKind kind, String description) {
        Element element = document.getElementById(id);
        element.addEventListener("click", event -> {
            if (!"true".equals(values.get(id + ":enabled"))) return;
            CombatControls.requestFromUi(kind);
        });
        element.addEventListener("mouseenter", event -> text("description", element.getAttribute("aria-label") + " · " + CombatControls.keyLabel(id) + " · " + values.getOrDefault(id + ":description", description)));
    }

    private static final class MemberNodes {
        final Element row, label, portrait, globe;
        UUID target;
        boolean entity;
        MemberNodes(Element row, Element label, Element portrait, Element globe) {
            this.row = row; this.label = label; this.portrait = portrait; this.globe = globe;
        }
        void clear() {
            target = null; entity = false;
            TacticalButton.style(row, "display: none");
            OverlayUi.text(label, "");
            row.setAttribute("class", "member");
        }
    }
    private static final java.util.ArrayDeque<MemberNodes> memberPool = new java.util.ArrayDeque<>();
    private static final Map<UUID, MemberNodes> memberNodes = new HashMap<>();
    private static EncounterProtocol.EncounterState lastMembersState;
    private static UUID lastSelectedMember;

    private static MemberNodes newMemberNodes() {
        Element row = document.getElementById("initiative").appendChild(document.createElement("div"));
        row.setAttribute("role", "button"); row.setAttribute("tabindex", "0");
        Element portrait = ParticipantPortraits.append(document, row, false);
        Element globe = ParticipantPortraits.append(document, row, true);
        Element label = row.appendChild(document.createElement("span")); label.setAttribute("class", "member-name");
        MemberNodes nodes = new MemberNodes(row, label, portrait, globe);
        row.addEventListener("click", event -> {
            if (nodes.entity && nodes.target != null && ClientControl.allowDocument(document)) ClientTacticalPlan.inspect(nodes.target);
        });
        row.addEventListener("mouseenter", event -> text("description", values.getOrDefault(nodes.target + ":detail", "")));
        nodes.clear();
        return nodes;
    }

    private static void releaseMember(UUID id) {
        MemberNodes nodes = memberNodes.remove(id);
        nodes.clear(); memberPool.add(nodes);
        members.remove(id); memberLabels.remove(id); memberPortraits.remove(id);
        values.keySet().removeIf(key -> key.startsWith(id.toString()));
    }

    private static void clearMembers() {
        for (UUID id : List.copyOf(memberNodes.keySet())) releaseMember(id);
        memberOrder = List.of(); lastSelectedMember = null;
    }

    private static void updateMembers(EncounterProtocol.EncounterState state) {
        if (lastMembersState == state && java.util.Objects.equals(lastSelectedMember, selectedTarget())) return;
        lastMembersState = state; lastSelectedMember = selectedTarget();
        List<UUID> order = state.members().stream().map(EncounterProtocol.MemberNotice::id).toList();
        var ids = new java.util.HashSet<>(order);
        Element host = document.getElementById("initiative");
        for (UUID id : List.copyOf(members.keySet())) if (!ids.contains(id)) releaseMember(id);
        for (var member : state.members()) {
            MemberNodes nodes = memberNodes.get(member.id());
            if (nodes == null) {
                nodes = memberPool.isEmpty() ? newMemberNodes() : memberPool.removeFirst();
                nodes.target = member.id(); nodes.entity = member.kind() == TurnParticipant.Kind.ENTITY;
                TacticalButton.style(nodes.entity ? nodes.globe : nodes.portrait, "display: none");
                memberNodes.put(member.id(), nodes); members.put(member.id(), nodes.row);
                memberLabels.put(member.id(), nodes.label);
                memberPortraits.put(member.id(), nodes.entity ? nodes.portrait : nodes.globe);
                layoutKey = null;
            }
            Element row = nodes.row;
            String flags = (member.dodging() ? " · 回避" : "") + (member.disengaged() ? " · 撤离" : "")
                + (member.eligibleRound() > state.round() ? " · 下轮行动" : "");
            values.put(member.id() + ":detail", member.name() + " · 先攻 " + member.initiative()
                + " · 动作 " + (member.action() ? 1 : 0) + " · 移动 " + member.movementTicks()
                + " · 反应 " + (member.reaction() ? 1 : 0) + flags);
            String label = member.name();
            if (member.kind() == TurnParticipant.Kind.ENVIRONMENT)
                values.put(member.id() + ":detail", member.name() + " · 先攻 -1 · 剩余步骤 " + state.environmentRemaining());
            if (!label.equals(values.put(member.id() + ":text", label))) OverlayUi.text(memberLabels.get(member.id()), label);
            String css = "member" + (member.id().equals(selectedTarget()) ? " selected" : "")
                + (member.id().equals(state.current()) ? " current" : "");
            if (!css.equals(values.put(member.id() + ":class", css))) row.setAttribute("class", css);
        }
        if (!memberOrder.equals(order)) {
            OverlayUi.resetInteraction(document);
            for (UUID id : order) host.appendChild(members.get(id));
            memberOrder = order;
        }
        text("target", selectedTarget() == null ? "未选择目标" : state.members().stream()
            .filter(member -> member.id().equals(selectedTarget())).map(member -> "目标 · " + member.name())
            .findFirst().orElse("未选择目标"));
    }

    private static void updateInventory() {
        var player = Minecraft.getInstance().player;
        if (player == null || !isOpen()) return;
        for (int i = 0; i < inventory.length; i++) {
            var stack = player.getInventory().getItem(i);
            if (i < 9) {
                attribute("hotbar-slot-" + i, "class", "action-slot" + (ClientTacticalPlan.selectedSlot(i) ? " selected" : ""));
                attribute("hotbar-slot-" + i, "aria-label", "物品 " + (i + 1) + " · " + stack.getHoverName().getString());
            }
            if (inventory[i] != null && net.minecraft.world.item.ItemStack.matches(inventory[i], stack)) continue;
            inventory[i] = stack.copy();
            Element node = document.getElementById("inventory-" + i);
            if (node instanceof com.sighs.apricityui.element.Item item) item.setIngredientStack(stack);
            if (i < 9 && document.getElementById("hotbar-item-" + i) instanceof com.sighs.apricityui.element.Item item)
                item.setIngredientStack(stack);
        }
    }

    /** Capture the click in document space, keeping a relative anchor across resizes. */
    public static void anchorContextMenu() {
        if (!isOpen()) return;
        document.applyViewport(false);
        var point = document.getMouseDocumentPosition();
        var size = document.getViewportSize();
        contextX = Math.clamp(point.x / Math.max(1, size.width()), 0, 1);
        contextY = Math.clamp(point.y / Math.max(1, size.height()), 0, 1);
    }

    private record LayoutKey(int width, int height, boolean inventory, boolean abilities, boolean inspection,
                             boolean context, int members, double contextX, double contextY) {}
    private static LayoutKey layoutKey;

    private static void updateLayout() {
        document.applyViewport(false);
        var size = document.getViewportSize();
        int width = Math.max(1, (int) size.width()), height = Math.max(1, (int) size.height());
        LayoutKey next = new LayoutKey(width, height, inventoryOpen, abilitiesTab, ClientInspection.open(),
            ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.CONTEXT_MENU, members.size(), contextX, contextY);
        if (next.equals(layoutKey)) return;
        layoutKey = next;
        boolean chatting = false;
        attribute("initiative", "class", chatting ? "chat-hidden" : "");
        attribute("journal", "class", "combat-panel journal" + (chatting ? " chat-hidden" : ""));
        double panelHeight = height * PANEL_HEIGHT_FRACTION;
        double dockScale = height * DOCK_HEIGHT_FRACTION / DOCK_DESIGN_HEIGHT;
        double slot = SLOT_SIZE * dockScale;
        double grid = slot * 9, dockWidth = (SLOT_SIZE * 9 + 60) * dockScale;
        int left = (int) Math.round((width - dockWidth) / 2);
        int top = (int) Math.round(dockTop(height));
        // AUI 1.2.5 hit testing reads layout Rects, not the drawing transform matrix.
        // Scale actual layout lengths so rendering and input share document coordinates.
        attribute("actions", "style", box((width - dockWidth) / 2,
            dockTop(height), dockWidth, DOCK_DESIGN_HEIGHT * dockScale)
            + "; border-width: " + .5 * dockScale + "px; " + textSize(6.5, 9, dockScale));
        attribute("resources", "style", scaledBox(3, 2, 117, 7, dockScale)
            + "; gap: " + 3 * dockScale + "px; " + textSize(5, 7, dockScale));
        attribute("environment", "style", textSize(5.5, 7, dockScale));
        attribute("dock-tabs", "style", scaledBox(3, 9, 117, 10, dockScale));
        attribute("investigate", "style", scaledBox(94, 9, 26, 10, dockScale)
            + "; padding: 0; " + textSize(5.5, 8, dockScale));
        int inspectWidth = Math.min(260, Math.max(1, width - 8));
        int inspectHeight = Math.min(220, Math.max(1, height - 8));
        attribute("inspection-panel", "style", (ClientInspection.open() && !chatting ? "display: block; " : "display: none; ")
            + box((width - inspectWidth) / 2, Math.max(4, (height - inspectHeight) / 2), inspectWidth, inspectHeight));
        attribute("inspection-content", "style", "height: " + Math.max(20, inspectHeight - 122) + "px");
        for (String id : List.of("items-tab", "abilities-tab"))
            attribute(id, "style", "height: " + 10 * dockScale + "px; padding: 0 "
                + 5 * dockScale + "px; border-width: " + .5 * dockScale + "px; " + textSize(5.5, 8, dockScale));
        String gridStyle = "grid-template-columns: repeat(9, " + slot + "px); grid-template-rows: " + slot + "px; width: " + grid + "px; height: " + slot + "px";
        attribute("action-grid", "style", gridStyle);
        attribute("hotbar", "style", gridStyle + "; position: absolute; left: 0; top: 0");
        attribute("spell-slots", "style", gridStyle + "; position: absolute; left: 0; top: " + slot + "px");
        // Canvas bitmap resolution must not determine the grid item's minimum size.
        for (String id : ACTION_SLOTS) TacticalButton.layout(document.getElementById(id), slot, 1.5 * dockScale, dockScale, "position: relative");
        for (int i = 0; i < 9; i++) TacticalButton.layout(document.getElementById("hotbar-slot-" + i), slot, 1.5 * dockScale, dockScale, "position: relative");
        for (Element empty : document.getElementById("spell-slots").getChildren())
            TacticalButton.style(empty, "border-width: " + .5 * dockScale + "px");
        attribute("behavior-options", "style", "position: absolute; left: 0; top: " + slot
            + "px; width: " + grid + "px; height: " + slot + "px");
        for (var button : behaviorButtons)
            TacticalButton.layout(button, slot, 1.5 * dockScale, dockScale, "position: relative");
        attribute("behavior-empty", "style", (behaviorButtons.isEmpty() ? "display: block; " : "display: none; ")
            + textSize(5, 11, dockScale));
        String pageBox = "; " + scaledBox(3, DOCK_SLOTS_TOP, SLOT_SIZE * 9, SLOT_SIZE * 2, dockScale);
        attribute("items-page", "style", (abilitiesTab ? "display: none" : "display: block") + pageBox);
        attribute("abilities-page", "style", (abilitiesTab ? "display: block" : "display: none") + pageBox);
        attribute("items-tab", "class", abilitiesTab ? "" : "active-tab");
        attribute("abilities-tab", "class", abilitiesTab ? "active-tab" : "");
        attribute("items-tab", "aria-selected", Boolean.toString(!abilitiesTab));
        attribute("abilities-tab", "aria-selected", Boolean.toString(abilitiesTab));
        TacticalButton.layout(document.getElementById("end"), 23 * dockScale, 4.5 * dockScale, dockScale,
            "position: absolute; left: " + (grid + 20 * dockScale) + "px; top: " + (DOCK_SLOTS_TOP + SLOT_SIZE - 11.5) * dockScale + "px");
        movementRing.layout(dockScale);
        playerEffects.layout(left, top, DOCK_DESIGN_HEIGHT * dockScale, dockScale);
        attribute("utilities", "style", scaledBox(SLOT_SIZE * 9 + 7, DOCK_DESIGN_HEIGHT - 14, 50.5, 11.5, dockScale)
            + "; gap: " + 1.5 * dockScale + "px");
        for (String id : List.of("behavior-hand", "cancel-plan", "inventory-toggle", "exit"))
            TacticalButton.layout(document.getElementById(id), 11.5 * dockScale, 1.5 * dockScale, dockScale, "position: relative");
        attribute("description", "style", scaledBox(3, DOCK_SLOTS_TOP + SLOT_SIZE * 2, SLOT_SIZE * 9, 7, dockScale)
            + "; " + textSize(5.5, 7, dockScale));
        attribute("status", "style", scaledBox(3, DOCK_SLOTS_TOP + SLOT_SIZE * 2 + 7, SLOT_SIZE * 9, 7, dockScale)
            + "; " + textSize(5.5, 7, dockScale));
        int journalWidth = Math.min(280, Math.max(170, width / 3));
        int journalHeight = Math.min(220, Math.max(116, top - 150));
        double initiativeScale = panelHeight / INITIATIVE_DESIGN_HEIGHT;
        double initiativeWidth = Math.min(members.size() * 30 * initiativeScale, width);
        attribute("initiative", "style", box((width - initiativeWidth) / 2,
            0, initiativeWidth, panelHeight) + "; justify-content: flex-start");
        for (var entry : members.entrySet()) {
            ParticipantPortraits.layout(memberPortraits.get(entry.getKey()), initiativeScale);
            TacticalButton.style(entry.getValue(), "width: " + 30 * initiativeScale + "px; min-width: "
                + 30 * initiativeScale + "px; height: " + 48 * initiativeScale + "px; padding: "
                + 2 * initiativeScale + "px; border-width: " + .5 * initiativeScale + "px");
            TacticalButton.style(memberLabels.get(entry.getKey()), "left: " + 2 * initiativeScale
                + "px; right: " + 2 * initiativeScale + "px; bottom: " + 2 * initiativeScale
                + "px; height: " + 8 * initiativeScale + "px; " + textSize(5, 8, initiativeScale));
        }
        attribute("journal", "style", box(width - journalWidth, Math.max(4, top - journalHeight - 10), journalWidth, journalHeight));
        attribute("log", "style", "height: " + Math.max(24, journalHeight - 102) + "px");
        boolean interactive = Minecraft.getInstance().screen == null;
        attribute("inventory-panel", "style", (inventoryOpen && interactive ? "display: block; " : "display: none; ")
            + box(left, Math.max(4, top - 190), Math.min(338, width - 8), 182));
        boolean contextVisible = interactive && ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.CONTEXT_MENU;
        int menuWidth = Math.min(150, width - 8);
        // Reserve the final panel while discovery is pending; response size must not resize it.
        int menuHeight = Math.min(144, height - 8);
        int x = (int) Math.clamp(contextX * width + 6, 4, Math.max(4, width - menuWidth - 4));
        int y = (int) Math.clamp(contextY * height + 6, 4, Math.max(4, height - menuHeight - 4));
        attribute("context-menu", "style", (contextVisible ? "display: block; " : "display: none; ") + box(x, y, menuWidth, menuHeight));
        attribute("context-options", "style", "height: " + Math.max(18, menuHeight - 8) + "px");
    }

    private static String scaledBox(double x, double y, double width, double height, double scale) {
        return box(x * scale, y * scale, width * scale, height * scale);
    }

    private static String textSize(double font, double line, double scale) {
        return "font-size: " + font * scale + "px; line-height: " + line * scale + "px";
    }

    private static double dockTop(int height) {
        return height * (1 - DOCK_HEIGHT_FRACTION);
    }

    private static String box(double x, double y, double width, double height) {
        return "left: " + x + "px; top: " + y + "px; width: " + width + "px; height: " + height + "px";
    }

    private static void enabled(String id, boolean enabled) {
        String value = Boolean.toString(enabled);
        if (value.equals(values.put(id + ":enabled", value))) return;
        Element node = document.getElementById(id);
        if (id.equals("investigate")) {
            if (enabled) node.removeAttribute("disabled"); else node.setAttribute("disabled", "");
            return;
        }
        TacticalButton.enabled(node, enabled);
    }

    private static void text(String id, String value) {
        if (!value.equals(values.put(id + ":text", value))) OverlayUi.text(document.getElementById(id), value);
    }

    private static void attribute(String id, String key, String value) {
        if (!value.equals(values.put(id + ":" + key, value))) document.getElementById(id).setAttribute(key, value);
    }
}
