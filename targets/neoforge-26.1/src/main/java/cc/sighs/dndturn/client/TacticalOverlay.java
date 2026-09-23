package cc.sighs.dndturn.client;
import cc.sighs.dndturn.combat.EncounterPhase;

import cc.sighs.dndturn.combat.CombatNetwork;
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
    private static Document document;
    private static long refreshGeneration = -1;
    private static UUID encounterId;
    private static int retryDelay;
    private static final Map<String, String> values = new HashMap<>();
    private static final Map<UUID, Element> members = new LinkedHashMap<>();
    private static final Map<Integer, Element> logs = new LinkedHashMap<>();
    private static final Map<UUID, Element> memberLabels = new HashMap<>();
    private static final Map<UUID, Element> memberFaces = new HashMap<>();
    private static final net.minecraft.world.item.ItemStack[] inventory = new net.minecraft.world.item.ItemStack[36];
    private static boolean inventoryOpen;
    private static List<UUID> memberOrder = List.of();

    private TacticalOverlay() {}

    public static boolean isOpen() { return document != null && document.isActive(); }

    public static UUID selectedTarget() { return ClientTacticalPlan.inspected(); }
    static Document document() { return document; }
    static void select(UUID target) { ClientTacticalPlan.inspect(target); }
    static void cancelGesture() {
        if (document == null) return;
        var pressed = document.getPressedElement();
        if (pressed != null && pressed.isScrollbarInteractionActive()) pressed.handleScrollbarMouseUp(null);
        document.setPressedElement(null); document.clearFocus();
    }
    static boolean hit(com.sighs.apricityui.layout.Position position) {
        return isOpen() && document.hitTest(document.screenToDocumentPosition(position)) != null;
    }

    public static boolean closePanel() {
        if (!inventoryOpen) return false;
        inventoryOpen = false; attribute("inventory-panel", "style", "display: none"); return true;
    }
    public static void toggleInventory() {
        if (!isOpen()) return;
        inventoryOpen = !inventoryOpen;
        attribute("inventory-panel", "style", inventoryOpen ? "display: block" : "display: none");
    }

    public static void togglePointer() {
        ClientControl.toggleMode();
    }

    public static void close() {
        cancelGesture(); ClientControl.documentClosed(document);
        if (document != null) document.remove();
        document = null;
        encounterId = null;
        refreshGeneration = -1;
        values.clear(); members.clear(); memberLabels.clear(); memberFaces.clear(); logs.clear(); memberOrder = List.of();
        java.util.Arrays.fill(inventory, null);
        retryDelay = 0;
        inventoryOpen = false;
    }

    public static void tick() {
        var state = ClientCombatState.encounter();
        Minecraft game = Minecraft.getInstance();
        if (state == null || game.player == null || !game.player.isAlive()) { close(); return; }
        if (!state.encounterId().equals(encounterId)) { close(); encounterId = state.encounterId(); }
        renderProjection(state, game.player.getUUID(), ClientCombatState.results(), ClientCombatState.latestStatus());
        updateInventory();
    }

    static void renderProjection(CombatNetwork.EncounterState state, UUID viewer,
                                 List<CombatNetwork.ResultNotice> results, CombatNetwork.IntentStatus status) {
        if (!isOpen()) {
            if (retryDelay-- > 0) return;
            ClientControl.documentClosed(document);
            document = ApricityUI.createDocument("dndturn/tactical.html");
            if (document == null) { retryDelay = 100; return; }
            refreshGeneration = -1;
        }
        if (document.getRefreshGeneration() != refreshGeneration) bind();
        boolean myTurn = state.interactive() && viewer.equals(state.current()) && state.phase() != cc.sighs.dndturn.combat.EncounterPhase.ENVIRONMENT;
        text("phase", "第 " + state.round() + " 轮 · " + (state.phase() == cc.sighs.dndturn.combat.EncounterPhase.ENVIRONMENT ? "环境推进"
            : myTurn ? "你的回合" : "等待行动"));
        text("action", state.action() ? "● 动作 1" : "○ 动作 0");
        text("movement", "移动 " + state.movementTicks());
        text("reaction", state.reaction() ? "● 反应 1" : "○ 反应 0");
        text("environment", "环境 " + state.environmentRemaining());
        text("pointer", (ClientControl.mode() == ClientControl.Mode.CAMERA ? "镜头控制 · " : "角色控制 · ")
            + CombatControls.uiKeyLabel() + " 切换");
        updateMembers(state);
        enabled("move", myTurn && state.movementTicks() > 0);
        text("move", "选择目的地");
        for (String id : List.of("attack", "dash", "dodge", "disengage", "end", "exit", "inventory-toggle")) {
            String label = switch (id) {
                case "attack" -> "选攻击"; case "dash" -> "疾走"; case "dodge" -> "回避";
                case "disengage" -> "撤离"; case "end" -> "结束回合"; case "exit" -> "退出";
                default -> "物品";
            };
            text(id, label + (CombatControls.keyLabel(id).equals("Unknown") ? "" : "\n" + CombatControls.keyLabel(id)));
        }
        for (String id : List.of("attack", "dash", "dodge", "disengage"))
            enabled(id, myTurn && (id.equals("attack") || state.phase() == cc.sighs.dndturn.combat.EncounterPhase.ACTIVE)
                && state.action() && !state.moving()
                );
        renderBehaviors();
        attribute("behavior-options", "style", inventoryOpen ? "display: none" : "display: block");
        for (var entry : Map.of("place", cc.sighs.dndturn.combat.TacticalIntent.Capability.PLACE,
                "break-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.BREAK,
                "use-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_BLOCK).entrySet())
            enabled(entry.getKey(), myTurn && !ClientTacticalPlan.running());
        enabled("end", myTurn);
        enabled("exit", state.interactive());
        updateLog(results);
        text("status", ClientTacticalPlan.description());
        attribute("status", "class", status != null && !status.accepted() ? "rejected" : "muted");
    }

    private static void bind() {
        refreshGeneration = document.getRefreshGeneration();
        renderedOffers = List.of(); renderedAbilities = List.of(); behaviorButtons.clear();
        values.clear(); members.clear(); memberLabels.clear(); memberFaces.clear(); logs.clear(); memberOrder = List.of();
        java.util.Arrays.fill(inventory, null);
        ability("move", cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE, "移动按实际获准位移计费；再次点击停止。结束回合会先结算移动。");
        ability("attack", cc.sighs.dndturn.combat.TacticalIntent.Capability.ATTACK, "消耗动作。d20＋5 对目标 AC；自然 1 未命中，自然 20 暴击。选择目标后自动接近并攻击。");
        action("dash", CombatNetwork.IntentKind.DASH, "消耗动作，增加本会话捕获的一份基础移动预算。");
        action("dodge", CombatNetwork.IntentKind.DODGE, "消耗动作，直到下次自身回合开始前，对你的命中检定有劣势。");
        action("disengage", CombatNetwork.IntentKind.DISENGAGE, "消耗动作，设置本回合撤离状态。借机攻击流程尚未开放。");
        action("end", CombatNetwork.IntentKind.END_TURN, "先结束并结算正在进行的移动，然后请求结束回合。");
        action("exit", CombatNetwork.IntentKind.EXIT, "先让角色中心离开场地再退出；场内请求会被拒绝。有其他玩家留场时只移除你，其余玩家继续战斗。");
        ability("place", cc.sighs.dndturn.combat.TacticalIntent.Capability.PLACE, "选择放置面；原版接受放置后消耗一次动作。");
        ability("use-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_BLOCK, "使用方块自身，不消耗动作；接近仍消耗移动。");
        ability("break-block", cc.sighs.dndturn.combat.TacticalIntent.Capability.BREAK, "选择要破坏的方块。");
        ability("use-item", cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_ITEM, "使用当前物品。");
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
        document.getElementById("pointer").addEventListener("click", event -> togglePointer());
    }

    private static ClientTacticalPlan.Phase renderedPhase;
    private static List<cc.sighs.dndturn.combat.TacticalNetwork.Ability> renderedAbilities = List.of();
    private static List<cc.sighs.dndturn.combat.TacticalNetwork.Offer> renderedOffers = List.of();
    private static final java.util.List<Element> behaviorButtons = new java.util.ArrayList<>();
    private static void renderBehaviors() {
        var offers = ClientTacticalPlan.offers();
        var abilities = ClientTacticalPlan.abilities();
        if (offers.equals(renderedOffers) && abilities.equals(renderedAbilities) && renderedPhase == ClientTacticalPlan.phase()) return;
        renderedPhase = ClientTacticalPlan.phase();
        var host = document.getElementById("behavior-options");
        for (var button : behaviorButtons) host.removeChild(button);
        behaviorButtons.clear();
        if (ClientTacticalPlan.phase() != ClientTacticalPlan.Phase.CONTEXT_MENU) for (var ability : abilities) {
            var button = document.createElement("button");
            button.setTextContent(ability.label() + " · " + (ability.cost() == cc.sighs.dndturn.combat.TacticalIntent.Capability.MOVE ? "移动" : ability.cost() == cc.sighs.dndturn.combat.TacticalIntent.Capability.USE_BLOCK ? "0 动作" : "1 动作"));
            button.addEventListener("click", event -> ClientTacticalPlan.selectBehavior(ability));
            host.appendChild(button); behaviorButtons.add(button);
        }
        if (ClientTacticalPlan.phase() == ClientTacticalPlan.Phase.CONTEXT_MENU) for (var offer : offers) {
            var button = document.createElement("button");
            button.setAttribute("id", "offer-" + offer.intent().behaviorId().replace(':','-'));
            button.setTextContent(offer.label() + (offer.approach() ? " · 需接近" : " · 已在范围内") + (offer.intent().requiresAction() ? " · 1 动作" : " · 0 动作") + (offer.reason().isEmpty() ? "" : " · " + offer.reason()));
            if (!offer.reason().isEmpty()) button.setAttribute("disabled", "");
            button.addEventListener("click", event -> ClientTacticalPlan.choose(offer));
            host.appendChild(button);
            behaviorButtons.add(button);
        }
        renderedOffers = offers; renderedAbilities = abilities;
    }
    private static void ability(String id, cc.sighs.dndturn.combat.TacticalIntent.Capability capability, String description) {
        var element = document.getElementById(id);
        element.addEventListener("click", event -> { if (!"false".equals(values.get(id + ":enabled"))) ClientTacticalPlan.select(capability); });
        element.addEventListener("mouseenter", event -> text("description", description));
    }

    private static void action(String id, CombatNetwork.IntentKind kind, String description) {
        Element element = document.getElementById(id);
        element.addEventListener("click", event -> {
            if (!"true".equals(values.get(id + ":enabled"))) return;
            CombatControls.requestFromUi(kind, kind == CombatNetwork.IntentKind.ATTACK ? selectedTarget() : null);
        });
        element.addEventListener("mouseenter", event -> text("description", description));
        enabled(id, true);
    }

    private static void updateMembers(CombatNetwork.EncounterState state) {
        List<UUID> order = state.members().stream().map(CombatNetwork.MemberNotice::id).toList();

        Element host = document.getElementById("initiative");
        attribute("initiative", "style", order.size() <= 5 ? "justify-content: center" : "justify-content: flex-start");
        for (UUID id : List.copyOf(members.keySet())) if (!order.contains(id)) {
            host.removeChild(members.remove(id));
            memberLabels.remove(id); memberFaces.remove(id);
            values.keySet().removeIf(key -> key.startsWith(id.toString()));
        }
        for (var member : state.members()) {
            Element row = members.computeIfAbsent(member.id(), id -> {
                Element node = host.appendChild(document.createElement("div"));
                node.setAttribute("role", "button");
                node.setAttribute("tabindex", "0");
                node.addEventListener("click", event -> ClientTacticalPlan.inspect(id));
                node.addEventListener("mouseenter", event -> text("description",
                    values.getOrDefault(id + ":detail", "")));
                Element portrait = node.appendChild(document.createElement("div"));
                portrait.setAttribute("class", "portrait");
                Element face = portrait.appendChild(document.createElement("texture"));
                memberFaces.put(id, face);
                Element label = node.appendChild(document.createElement("span"));
                memberLabels.put(id, label);
                return node;
            });
            String flags = (member.dodging() ? " · 回避" : "") + (member.disengaged() ? " · 撤离" : "")
                + (member.eligibleRound() > state.round() ? " · 下轮行动" : "");
            values.put(member.id() + ":detail", member.name() + " · 先攻 " + member.initiative()
                + " · 动作 " + (member.action() ? 1 : 0) + " · 移动 " + member.movementTicks()
                + " · 反应 " + (member.reaction() ? 1 : 0) + flags);
            String label = (member.id().equals(state.current()) ? "▶ " : "") + member.name()
                + "  " + member.initiative() + "\n" + (member.action() ? "●" : "○")
                + "  移动 " + member.movementTicks() + "\n" + (member.reaction() ? "反应 ●" : "反应 ○") + flags;
            if (!label.equals(values.put(member.id() + ":text", label))) memberLabels.get(member.id()).setTextContent(label);
            String texture = portrait(member.id());
            if (!texture.equals(values.put(member.id() + ":portrait", texture))) {
                memberFaces.get(member.id()).setAttribute("src", texture);
                memberFaces.get(member.id()).setAttribute("style", texture.isEmpty() ? "display: none" : "display: block");
            }
            String css = "member" + (member.id().equals(selectedTarget()) ? " selected" : "")
                + (member.id().equals(state.current()) ? " current" : "");
            if (!css.equals(values.put(member.id() + ":class", css))) row.setAttribute("class", css);
        }
        if (!memberOrder.equals(order)) {
            for (UUID id : order) host.appendChild(members.get(id));
            memberOrder = order;
        }
        text("target", selectedTarget() == null ? "未选择目标" : state.members().stream()
            .filter(member -> member.id().equals(selectedTarget())).map(member -> "目标 · " + member.name())
            .findFirst().orElse("未选择目标"));
    }

    private static void updateLog(List<CombatNetwork.ResultNotice> results) {
        Element host = document.getElementById("log");
        int first = results.isEmpty() ? Integer.MAX_VALUE : results.getFirst().index();
        for (int index : List.copyOf(logs.keySet())) if (index < first) host.removeChild(logs.remove(index));
        for (var result : results) {
            if (logs.containsKey(result.index())) continue;
            Element row = document.createElement("button");
            row.setAttribute("class", "log-entry");
            row.setTextContent("#" + result.index() + "  " + result.reason());
            String detail = detail(result);
            row.addEventListener("click", event -> text("hit", detail));
            row = host.appendChild(row);
            logs.put(result.index(), row);
            text("hit", detail);
        }
    }

    private static String portrait(UUID id) {
        Minecraft game = Minecraft.getInstance();
        var info = game.getConnection() == null ? null : game.getConnection().getPlayerInfo(id);
        if (info != null) return info.getSkin().body().texturePath().toString();
        return "";
    }

    private static void updateInventory() {
        var player = Minecraft.getInstance().player;
        if (!inventoryOpen || player == null || !isOpen()) return;
        for (int i = 0; i < inventory.length; i++) {
            var stack = player.getInventory().getItem(i);
            if (inventory[i] != null && net.minecraft.world.item.ItemStack.matches(inventory[i], stack)) continue;
            inventory[i] = stack.copy();
            Element node = document.getElementById("inventory-" + i);
            if (node instanceof com.sighs.apricityui.element.Item item) item.setIngredientStack(stack);
        }
    }

    private static String detail(CombatNetwork.ResultNotice result) {
        var hit = result.hit();
        if (hit == null) return result.reason() + " · 移动 " + result.movementTicks() + " · 生命损失 " + result.damage();
        String mode = switch (hit.mode()) {
            case "ADVANTAGE" -> "优势"; case "DISADVANTAGE" -> "劣势"; default -> "普通";
        };
        String stage = switch (hit.stage()) {
            case "MISS" -> "未命中"; case "ZERO_DAMAGE" -> "零伤害";
            case "VANILLA_ACCEPTED" -> "伤害已生效"; case "VANILLA_REJECTED" -> "伤害未生效";
            default -> "结果未确定";
        };
        return mode + "  d20 " + hit.firstDie() + "/" + hit.secondDie() + " → " + hit.selectedDie()
            + " · 总计 " + hit.rollTotal() + " / AC " + hit.armorClass() + "\n"
            + stage + " · 战术伤害 " + hit.damage() + "\n吸收 " + hit.absorptionLoss()
            + " · 生命损失 " + hit.healthLoss();
    }

    private static void enabled(String id, boolean enabled) {
        String value = Boolean.toString(enabled);
        if (value.equals(values.put(id + ":enabled", value))) return;
        Element node = document.getElementById(id);
        if (enabled) node.removeAttribute("disabled"); else node.setAttribute("disabled", "");
    }

    private static void text(String id, String value) {
        if (!value.equals(values.put(id + ":text", value))) document.getElementById(id).setTextContent(value);
    }

    private static void attribute(String id, String key, String value) {
        if (!value.equals(values.put(id + ":" + key, value))) document.getElementById(id).setAttribute(key, value);
    }
}
