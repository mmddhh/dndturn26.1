package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.*;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Executes only the current server waypoint through the ordinary local player input/tick chain. */
public final class ClientTacticalPlan {
    private static TacticalNetwork.Projection projection;
    private static long sequence = -1;
    private static UUID requested;
    public enum Phase { IDLE, TARGETING, CONTEXT_MENU, AWAITING_SERVER, EXECUTING, CANCEL_PENDING }
    private static Phase phase = Phase.IDLE;
    private static long revision;
    private static int slot;
    private static String itemName = "";
    private static String behavior;
    private static TacticalIntent.Target selectedTarget;
    private static TacticalIntent.Capability preferredCost;
    private static java.util.List<TacticalNetwork.Ability> abilities = java.util.List.of();
    private static TacticalIntent.ItemReference confirmedItem;
    private static boolean arming, submitAfterQuery;
    public static Phase phase() { return phase; }
    public static java.util.List<TacticalNetwork.Ability> abilities() { return abilities; }
    public static void selectBehavior(TacticalNetwork.Ability value) {
        if (running() || !abilities.contains(value)) return;
        invalidate(); arming = false; behavior = value.id(); phase = Phase.TARGETING;
        reason = value.label() + " · 请选择目标";
        if (value.targets().contains(TacticalIntent.TargetKind.SELF)) { submitAfterQuery = true; target(null,null); }
    }
    public static UUID inspected() { return selectedTarget == null ? null : selectedTarget.entity(); }
    public static void inspect(UUID value) {
        if (running()) return;
        invalidate(); arming = false; behavior = null; phase = Phase.IDLE;
        var world = Minecraft.getInstance().level;
        selectedTarget = value == null || world == null ? null : new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY,world.dimension().identifier().toString(),value,null,-1,0,0,0);
        reason = "检查对象";
    }
    private static void invalidate() { revision++; queryId = null; options = null; submitAfterQuery = false; }
    public static void selectSlot(int value) {
        if (running()) { reason = "执行中不能换槽，请先取消计划"; return; }
        preferredCost = null; confirmedItem = null; invalidate(); slot = value; hand = TacticalIntent.Hand.MAIN_HAND; behavior = null; phase = Phase.TARGETING; arming = true;
        target(null, null);
    }
    public static void worldClick(boolean right, UUID entity, net.minecraft.world.phys.BlockHitResult hit) {
        if (right && (phase == Phase.TARGETING || phase == Phase.CONTEXT_MENU || running())) { cancel(); return; }
        if (running()) return;
        if (arming) { reason = "等待服务器确认物品"; return; }
        if (right) { slot = Minecraft.getInstance().player.getInventory().getSelectedSlot(); behavior = null; arming = false; phase = Phase.CONTEXT_MENU; target(entity, hit); return; }
        if (phase == Phase.TARGETING) { arming = false; submitAfterQuery = true; target(entity, hit); return; }
        if (entity != null) { inspect(entity); return; }
        slot = Minecraft.getInstance().player.getInventory().getSelectedSlot();
        behavior = "dndturn:move"; submitAfterQuery = true; arming = false; target(null, hit);
    }
    private static long terminalVersion;
    private static boolean endAfterCancel;
    private static UUID queryId;
    private static TacticalNetwork.Options options;
    private static TacticalIntent.Hand hand = TacticalIntent.Hand.MAIN_HAND;
    public static java.util.List<TacticalNetwork.Offer> offers() { return options == null ? java.util.List.of() : options.offers(); }
    public static void toggleHand() { if (running()) return; preferredCost = null; confirmedItem = null; invalidate(); hand = hand == TacticalIntent.Hand.MAIN_HAND ? TacticalIntent.Hand.OFF_HAND : TacticalIntent.Hand.MAIN_HAND; arming = true; phase = Phase.TARGETING; behavior = null; target(null, null); }
    public static void receiveOptions(TacticalNetwork.Options value, IPayloadContext context) {
        if (Boolean.getBoolean("dndturn.controlProbe")) ClientControlRegression.captureOptions(() -> receiveOptions(value, context));
        var connection = context.connection(); var level = Minecraft.getInstance().level; var player = Minecraft.getInstance().player;
        context.enqueueWork(() -> {
            var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter();
            if (state == null || mc.level != level || mc.player != player || mc.getConnection() == null || mc.getConnection().getConnection() != connection
                || !state.generation().equals(value.generation()) || !state.encounterId().equals(value.encounter()) || !value.query().equals(queryId) || value.revision() != revision) return;
            options = value; itemName = value.itemName(); abilities = value.abilities(); reason = value.reason().isEmpty() ? "选择目标或服务器提供的行为" : value.reason();
            if (value.item() == null) { phase = Phase.IDLE; arming = false; confirmedItem = null; submitAfterQuery = false; return; }
            confirmedItem = value.item();
            if (arming) { if (behavior == null) behavior = preferredCost == null ? value.defaultBehavior() : abilities.stream().filter(a -> a.cost() == preferredCost).map(TacticalNetwork.Ability::id).findFirst().orElse(value.defaultBehavior()); phase = Phase.TARGETING; arming = false; reason = abilities.stream().filter(a -> a.id().equals(behavior)).map(TacticalNetwork.Ability::label).findFirst().orElse("能力不可用") + " · 请选择目标"; }
            else if (submitAfterQuery) {
                submitAfterQuery = false;
                var selected = value.offers().stream().filter(o -> o.intent().behaviorId().equals(behavior)).findFirst();
                if (selected.isPresent() && selected.get().reason().isEmpty()) choose(selected.get());
                else { phase = Phase.TARGETING; reason = selected.map(TacticalNetwork.Offer::reason).orElse("目标不支持所选行为"); }
            }
        });
    }
    public static void choose(TacticalNetwork.Offer offer) {
        var state = ClientCombatState.encounter();
        if (state == null || options == null || running() || !offer.reason().isEmpty() || !options.offers().contains(offer)) return;
        queryId = null; requested = UUID.randomUUID(); phase = Phase.AWAITING_SERVER;
        ClientPacketDistributor.sendToServer(new TacticalNetwork.Request(state.generation(), state.encounterId(), requested, options.version(), offer.intent(), false));
        options = null; reason = "等待服务器验证";
    }

    private static String reason = "左键地面移动；右键对象查看行为";
    private ClientTacticalPlan() {}
    public static void reset() { phase = Phase.IDLE; selectedTarget = null; preferredCost = null; abilities = java.util.List.of(); confirmedItem = null; revision++; itemName = ""; behavior = null; arming = false; submitAfterQuery = false; slot = 0; queryId = null; options = null; hand = TacticalIntent.Hand.MAIN_HAND; projection = null; sequence = -1; requested = null; endAfterCancel = false; reason = "左键地面移动；右键对象查看行为"; }
    public static void select(TacticalIntent.Capability value) {
        if (running()) return;
        preferredCost = value;
        behavior = switch (value) { case MOVE -> "dndturn:move"; case ATTACK -> null; case PLACE -> "dndturn:place"; case BREAK -> "dndturn:break"; case USE_BLOCK -> "dndturn:block"; case USE_ITEM -> null; };
        var p = Minecraft.getInstance().player; if (p == null) return;
        confirmedItem = null; slot = p.getInventory().getSelectedSlot(); phase = Phase.TARGETING; arming = true; target(null, null);
    }
    public static String description() {
        String stage = switch (phase) {
            case IDLE -> "空闲"; case TARGETING -> "选择目标"; case CONTEXT_MENU -> "目标菜单";
            case AWAITING_SERVER -> "等待确认"; case EXECUTING -> "执行中"; case CANCEL_PENDING -> "等待取消";
        };
        String target = "";
        if (selectedTarget != null) {
            if (selectedTarget.kind() == TacticalIntent.TargetKind.SELF) target = "自身";
            else if (selectedTarget.cell() != null) target = selectedTarget.cell().x()+", "+selectedTarget.cell().y()+", "+selectedTarget.cell().z();
            else {
                target = "所选实体";
                var level = Minecraft.getInstance().level;
                if (level != null) for (var entity : level.entitiesForRendering()) if (entity.getUUID().equals(selectedTarget.entity())) { target=entity.getName().getString(); break; }
            }
        }
        return stage + " · " + itemName + " · " + (hand == TacticalIntent.Hand.MAIN_HAND ? "主手" : "副手") + " · " + reason + (target.isEmpty() ? "" : " · " + target);
    }
    public static void receive(TacticalNetwork.Projection state, IPayloadContext context) {
        if (Boolean.getBoolean("dndturn.controlProbe") && state.running()) ClientControlRegression.captureRunningProjection(() -> receive(state, context));
        var connection = context.connection();
        var level = Minecraft.getInstance().level;
        var player = Minecraft.getInstance().player;
        context.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            var encounter = ClientCombatState.encounter();
            if (mc.level != level || mc.player != player || mc.getConnection() == null
                || mc.getConnection().getConnection() != connection || encounter == null
                || !encounter.generation().equals(state.generation()) || !encounter.encounterId().equals(state.encounter())
                || state.sequence() <= sequence || requested == null || !requested.equals(state.operation())) return;
            sequence = state.sequence(); projection = state; reason = state.reason();
            requested = state.running() ? state.operation() : null;
            if (!state.running()) { phase = Phase.IDLE; projection = null; confirmedItem = null; }
            else if (phase != Phase.CANCEL_PENDING) phase = Phase.EXECUTING;
            if (!state.running()) terminalVersion = state.version();
            tick();
        });
    }
    public static void tick() {
        var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter();
        if (endAfterCancel && !running() && state != null && state.version() >= terminalVersion
            && mc.screen == null && mc.isWindowActive() && !ClientControl.modal()) {
            endAfterCancel = false;
            CombatControls.requestFromUi(CombatNetwork.IntentKind.END_TURN, null);
        }
    }
    public static boolean running() { return requested != null; }
    public static void endTurn() {
        if (!running()) { CombatControls.requestFromUi(CombatNetwork.IntentKind.END_TURN, null); return; }
        endAfterCancel = true; cancel();
    }
    public static void cancel() {
        invalidate(); arming = false; behavior = null;
        if (!running()) { selectedTarget = null; phase = Phase.IDLE; reason = "已取消选择"; return; }
        phase = Phase.CANCEL_PENDING;
        var state = ClientCombatState.encounter();
        if (requested != null && state != null)
            ClientPacketDistributor.sendToServer(new TacticalNetwork.Request(state.generation(), state.encounterId(), requested,
                state.version(), null, true));
    }
    public static void target(UUID entity, net.minecraft.world.phys.BlockHitResult hit) {
        var mc = Minecraft.getInstance();
        var state = ClientCombatState.encounter();
        if (state == null || mc.player == null || running()) return;
        String dimension = mc.level.dimension().identifier().toString();
        TacticalIntent.Target target;
        if (entity != null) target = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, dimension, entity, null, -1, 0, 0, 0);
        else if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
            target = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF, dimension, null, null, -1, 0, 0, 0);
        else {
            BlockPos pos = hit.getBlockPos(); Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
            target = new TacticalIntent.Target(TacticalIntent.TargetKind.BLOCK, dimension, null, new GridCell(pos.getX(), pos.getY(), pos.getZ()),
                hit.getDirection().ordinal(), Math.clamp(local.x, 0, 1), Math.clamp(local.y, 0, 1), Math.clamp(local.z, 0, 1));
        }
        selectedTarget = target;
        revision++; queryId = UUID.randomUUID(); options = null;
        ClientPacketDistributor.sendToServer(new TacticalNetwork.Query(state.generation(), state.encounterId(), queryId, target, hand, hand == TacticalIntent.Hand.MAIN_HAND ? slot : 40, revision, confirmedItem));
        reason = "查询可用行为";
    }
    public record Steering(Input keys, Vec2 vector) {}
    public static Steering steering() {
        var mc = Minecraft.getInstance();
        var state = ClientCombatState.encounter();
        if (phase == Phase.CANCEL_PENDING) return new Steering(Input.EMPTY, Vec2.ZERO);
        if (requested == null || projection == null || !requested.equals(projection.operation()) || !projection.running() || projection.waypoint() == null || mc.player == null
            || state == null || !state.generation().equals(projection.generation()) || !state.encounterId().equals(projection.encounter()) || !ClientCombatState.movementAllowed()) return null;
        if (mc.screen != null || !mc.isWindowActive() || ClientControl.modal()) return new Steering(Input.EMPTY, Vec2.ZERO);
        GridCell cell = projection.waypoint();
        Vec3 delta = new Vec3(cell.x() + .5, cell.y(), cell.z() + .5).subtract(mc.player.position());
        if (delta.lengthSqr() < .16) return new Steering(Input.EMPTY, Vec2.ZERO);
        double yaw = Math.toRadians(mc.player.getYRot());
        double forward = -Math.sin(yaw) * delta.x + Math.cos(yaw) * delta.z;
        double left = Math.cos(yaw) * delta.x + Math.sin(yaw) * delta.z;
        double length = Math.max(.001, Math.hypot(forward, left));
        float scale = (float)Math.min(1, delta.horizontalDistance() * 3);
        boolean jump = delta.y > .5 && mc.player.onGround();
        return new Steering(new Input(forward > .01, forward < -.01, left > .01, left < -.01, jump, false, false),
            new Vec2((float)(left / length) * scale, (float)(forward / length) * scale));
    }
}
