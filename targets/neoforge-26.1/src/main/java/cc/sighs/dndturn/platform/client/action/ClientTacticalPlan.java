package cc.sighs.dndturn.platform.client.action;

import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.operation.ExecutionConclusion;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.client.input.ClientControl;
import cc.sighs.dndturn.platform.client.input.CombatControls;
import cc.sighs.dndturn.platform.client.state.ClientCombatState;
import cc.sighs.dndturn.platform.client.ui.TacticalOverlay;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.spatial.PreviewPathfinder;
import cc.sighs.dndturn.platform.spatial.ProjectileProfiles;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Executes only the current server waypoint through the ordinary local player input/tick chain. */
public final class ClientTacticalPlan {
    private static ActionProtocol.Projection projection;
    private static long sequence = -1;
    private static int steeringCursor;
    private static UUID steeringOperation;
    private static UUID requested;
    // Terminal authority feedback survives background selection refresh and local path recomputation.
    private static String terminalFailure = "";
    private static ActionPreviewSnapshot preview;
    private static LocalActionPreview localPreview;
    private static int previewTicks;
    private static Vec3 previewOrigin;
    private static Vec3 jobOrigin;
    private static UUID localGeneration, localEncounter, pendingClick;
    private static long localRevision;
    private static net.minecraft.world.entity.Entity hoverEntityInstance;
    public static ActionPreviewSnapshot preview() { return preview; }
    public static Vec3 previewOrigin() { return previewOrigin; }
    public static java.util.List<ActionProtocol.PreviewStep> displayRoute() {
        if (running()) return projection == null ? java.util.List.of()
            : projection.route().subList(projection.cursor(), projection.route().size());
        return preview == null ? java.util.List.of() : preview.route();
    }
    public static boolean trajectoryReady() { return preview != null; }
    private static void clearPreview() {
        preview=null; localPreview=null; pendingClick=null; jobOrigin=null; hoverEntityInstance=null;
        localGeneration=null; localEncounter=null;
    }
    private static ActionIntent.Point value(Vec3 p) { return new ActionIntent.Point(p.x(), p.y(), p.z()); }
    private static Vec3 vector(ActionIntent.Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static boolean attackSelected() { return abilities.stream().anyMatch(a -> a.id().equals(behavior) && a.kind() == ActionIntent.Capability.ATTACK); }
    public record ApproachRing(Vec3 center, float radius) {}
    public static ApproachRing approachRing() {
        if (hoverEntityInstance==null || hoverEntityInstance.isRemoved() || !hoverEntityInstance.isAlive()) return null;
        String id=localPreview==null?behavior:localPreview.behavior;
        float radius=ProjectileProfiles.contains(id)?6:1.75F;
        return new ApproachRing(hoverEntityInstance.position().add(0,.035,0),radius);
    }
    private static Vec3 ringHit(ClientControl.WorldPick pick) {
        var ring=approachRing();
        if (ring==null || pick.block().getType()==net.minecraft.world.phys.HitResult.Type.MISS) return null;
        var hit=pick.block().getLocation();
        return Math.hypot(hit.x-ring.center().x,hit.z-ring.center().z)<=ring.radius()
            && Math.abs(hit.y-ring.center().y)<1.1 ? hit : null;
    }
    public static boolean previewClick(ClientControl.WorldPick pick) {
        if (pick == null) return false;
        if (phase == Phase.IDLE && !running() && !arming && pick.entity() == null
            && pick.block().getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            var state = ClientCombatState.encounter();
            var player = Minecraft.getInstance().player;
            if (state == null || player == null || !player.getUUID().equals(state.current())) return true;
            invalidate(); selectedTarget = null;
            behavior = "dndturn:move"; preferredCost = ActionIntent.Capability.MOVE;
            phase = Phase.TARGETING;
        }
        if (running() || phase!=Phase.TARGETING || arming || behavior==null) return false;
        boolean ground=pick.entity()==null && "dndturn:move".equals(behavior);
        boolean selected=attackSelected() || abilities.stream().anyMatch(a -> a.id().equals(behavior) && a.targets().contains(ActionIntent.TargetKind.BLOCK));
        if (!ground && !selected) return false;
        terminalFailure="";
        if (ground && !attackSelected()) hoverEntityInstance=null;
        updatePreview(pick);
        if (localPreview==null) { reason="此位置没有可用的本地预览"; return true; }
        pendingClick=localPreview.id;
        submitLocal();
        return true;
    }
    private static void submitLocal() {
        if (pendingClick==null || localPreview==null || !pendingClick.equals(localPreview.id) || !localPreview.done()) return;
        pendingClick=null;
        var result=localPreview.snapshot(); var state=ClientCombatState.encounter();
        if (result==null || result.intent()==null || state==null) { reason=result==null?"本地路径不可用":result.reason(); return; }
        terminalFailure="";
        requested=UUID.randomUUID(); phase=Phase.AWAITING_SERVER; queryId=null;
        DebugDiagnostics.log("PLAYER_SEND", () -> "op=" + requested + " encounter=" + state.encounterId()
            + " version=" + state.version() + " " + DebugDiagnostics.intent(result.intent()));
        ClientPacketDistributor.sendToServer(new ActionProtocol.Request(state.generation(),state.encounterId(),requested,state.version(),result.intent(),false));
        reason="等待服务器校验鼠标落点";
    }
    private static void updatePreview() {
        previewTicks++;
        updatePreview(ClientControl.previewPick());
    }
    private static void updatePreview(ClientControl.WorldPick pick) {
        var mc=Minecraft.getInstance(); var state=ClientCombatState.encounter();
        if (mc.player!=null) previewOrigin=mc.player.position();
        if (state==null || mc.player==null || mc.screen!=null || !mc.isWindowActive() || ClientControl.modal()) { clearPreview(); return; }
        if (running()) {
            if (localPreview!=null && ProjectileProfiles.contains(localPreview.behavior) && mc.player.isUsingItem() && previewTicks%4==0) {
                localPreview.refreshFlight(); preview=localPreview.snapshot();
            }
            return;
        }
        if (!state.generation().equals(localGeneration) || !state.encounterId().equals(localEncounter)
            || jobOrigin!=null && jobOrigin.distanceToSqr(mc.player.position())>.0001 || localRevision!=revision) clearPreview();
        if (arming || phase!=Phase.TARGETING || behavior==null || pick==null || !mc.player.getUUID().equals(state.current())) { clearPreview(); return; }
        if (hoverEntityInstance!=null && (hoverEntityInstance.isRemoved() || !hoverEntityInstance.isAlive() || hoverEntityInstance.level()!=mc.level)) clearPreview();
        java.util.UUID entityId=pick.entity();
        Vec3 inRing=ringHit(pick);
        if (entityId==null && inRing!=null && !(phase==Phase.IDLE && behavior==null && hoverEntityInstance==null)) entityId=hoverEntityInstance.getUUID();
        if (entityId!=null) {
            final var wanted=entityId;
            hoverEntityInstance=null;
            for (var found:mc.level.entitiesForRendering()) if (found.getUUID().equals(wanted)) { hoverEntityInstance=found; break; }
        } else hoverEntityInstance=null;
        boolean explicitEntity=behavior!=null && abilities.stream().anyMatch(a -> a.id().equals(behavior) && a.targets().contains(ActionIntent.TargetKind.ENTITY));
        String id=entityId!=null ? explicitEntity?behavior:null : behavior;
        if (id==null) { clearPreview(); return; }
        var ability=abilities.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
        boolean blockAction=entityId==null && ability!=null && ability.targets().contains(ActionIntent.TargetKind.BLOCK);
        if (entityId==null && !id.equals("dndturn:move") && !blockAction) { clearPreview(); return; }
        var hit=pick.block();
        if (entityId==null && hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS) { clearPreview(); return; }
        Vec3 desired=entityId==null?hit.getLocation():pick.entity()==null?inRing:null;
        String dimension=mc.level.dimension().identifier().toString();
        ActionIntent.Target target;
        if (entityId!=null) target=new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,dimension,entityId,null,-1,0,0,0);
        else if (blockAction) {
            var bp=hit.getBlockPos(); var h=hit.getLocation().subtract(Vec3.atLowerCornerOf(bp));
            target=new ActionIntent.Target(ActionIntent.TargetKind.BLOCK,dimension,null,new GridCell(bp.getX(),bp.getY(),bp.getZ()),hit.getDirection().ordinal(),Math.clamp(h.x,0,1),Math.clamp(h.y,0,1),Math.clamp(h.z,0,1));
            desired=null;
        } else target=new ActionIntent.Target(ActionIntent.TargetKind.GROUND,dimension,null,PreviewPathfinder.key(desired),-1,0,0,0);
        boolean invalid=localPreview!=null && !localPreview.stillValid();
        boolean changed=localPreview==null || invalid || !localPreview.behavior.equals(id) || !target.equals(localPreview.target)
            || !java.util.Objects.equals(desired,localPreview.requested) || hoverEntityInstance!=localPreview.entity
            || hoverEntityInstance!=null && !hoverEntityInstance.position().equals(localPreview.entityPosition);
        if (changed) {
            pendingClick=null;
            localPreview=new LocalActionPreview(id,target,desired,hoverEntityInstance,ability,invalid?null:localPreview);
            jobOrigin=mc.player.position(); localGeneration=state.generation(); localEncounter=state.encounterId(); localRevision=revision;
            // Never hide a same-target path while its replacement is being computed.
            if (preview!=null && (preview.intent()==null || !preview.intent().target().equals(target))) preview=null;
        }
        var ready=localPreview.advance();
        if (ready!=null) { preview=ready; reason=ready.reason(); }
        else reason="本地计算路径";
        submitLocal();
    }
    public enum Phase { IDLE, TARGETING, QUERYING, CONTEXT_MENU, AWAITING_SERVER, EXECUTING, CANCEL_PENDING }
    private static Phase phase = Phase.IDLE;
    private static long revision;
    private static int slot;
    private static String itemName = "";
    private static String behavior;
    private static ActionIntent.Target selectedTarget;
    private static ActionIntent.Capability preferredCost;
    private static java.util.List<ActionProtocol.Ability> abilities = java.util.List.of();
    private static ActionIntent.ItemReference confirmedItem;
    private static boolean arming, submitAfterQuery;
    private static boolean itemSelected;
    private static UUID turnOwner;
    private static long turnRound = -1;
    private static String attackBehavior;
    public static Phase phase() { return phase; }
    public static java.util.List<ActionProtocol.Ability> abilities() { return confirmedItem == null ? java.util.List.of() : abilities; }
    public static void selectBehavior(ActionProtocol.Ability value) {
        if (running() || !abilities().contains(value)) return;
        terminalFailure = "";
        if (value.source().kind() == GrantEvidence.Kind.EQUIPMENT) itemSelected = true;
        invalidate(); arming = false; behavior = value.id(); preferredCost = value.kind(); phase = Phase.TARGETING;
        reason = (value.kind() == ActionIntent.Capability.ATTACK ? "攻击" : value.label()) + " · 请选择目标";
        if (value.id().equals("dndturn:reel") && Minecraft.getInstance().player.fishing!=null) {
            target(Minecraft.getInstance().player.fishing.getUUID(),null,true); return;
        }
        if (value.targets().contains(ActionIntent.TargetKind.SELF)) target(null, null, true);
    }
    public static UUID inspected() { return selectedTarget == null ? null : selectedTarget.entity(); }
    public static void inspect(UUID value) {
        if (running()) return;
        terminalFailure = "";
        invalidate(); arming = false; behavior = null; preferredCost = null; phase = Phase.IDLE;
        var world = Minecraft.getInstance().level;
        selectedTarget = value == null || world == null ? null : new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,world.dimension().identifier().toString(),value,null,-1,0,0,0);
        reason = "检查对象";
    }
    /** Evaluated only by DebugDiagnostics' lazy supplier, never from the render hot path. */
    private static String diagnosticState() {
        return "query=" + queryId + " selection=" + revision + " phase=" + phase + " op=" + requested
            + " slot=" + slot + " hand=" + hand + " itemSelected=" + itemSelected
            + " confirmed=" + confirmedItem + " arming=" + arming + " autoSubmit=" + submitAfterQuery
            + " behavior=" + behavior + " turnOwner=" + turnOwner + " round=" + turnRound;
    }
    private static void invalidate() {
        DebugDiagnostics.log("GUI_QUERY_INVALIDATE", ClientTacticalPlan::diagnosticState);
        clearPreview(); revision++; queryId = null; options = null; submitAfterQuery = false;
    }
    public static void selectSlot(int value) {
        DebugDiagnostics.log("GUI_SELECT_SLOT", () -> "wantedSlot=" + value + " " + diagnosticState());
        if (running()) { reason = "执行中不能换槽，请先取消计划"; return; }
        terminalFailure = "";
        itemSelected = true; preferredCost = null; confirmedItem = null; invalidate(); slot = value; hand = ActionIntent.Hand.MAIN_HAND; behavior = null; phase = Phase.TARGETING; arming = true;
        target(null, null);
    }
    public static void worldClick(boolean right, UUID entity, net.minecraft.world.phys.BlockHitResult hit) {
        DebugDiagnostics.log("GUI_WORLD_CLICK", () -> "right=" + right + " entity=" + entity + " " + diagnosticState());
        if (right && phase != Phase.IDLE && phase != Phase.CONTEXT_MENU) { cancel(); return; }
        if (running()) return;
        if (arming) { reason = "等待服务器确认物品"; return; }
        if (right) {
            TacticalOverlay.anchorContextMenu();
            if (!itemSelected) slot = Minecraft.getInstance().player.getInventory().getSelectedSlot();
            arming = false;
            phase = Phase.CONTEXT_MENU; target(entity, hit); return;
        }
        if (phase == Phase.CONTEXT_MENU) { cancel(); return; }
        if (phase == Phase.TARGETING || phase == Phase.QUERYING) { arming = false; target(entity, hit, true); return; }
        // Idle block clicks use previewClick's precise movement path; entities and MISS do nothing.
    }
    private static long terminalVersion;
    private static boolean endAfterCancel;
    private static UUID queryId;
    private static long queryRound;
    private static ActionProtocol.Options options;
    private static ActionIntent.Hand hand = ActionIntent.Hand.MAIN_HAND;
    public static java.util.List<ActionProtocol.Offer> offers() { return options == null ? java.util.List.of() : options.offers().stream()
        .filter(o -> o.intent().capability() != ActionIntent.Capability.ATTACK || o.intent().behaviorId().equals(attackBehavior)).toList(); }
    public static void toggleHand() { if (running()) return; itemSelected = true; preferredCost = null; confirmedItem = null; invalidate(); hand = hand == ActionIntent.Hand.MAIN_HAND ? ActionIntent.Hand.OFF_HAND : ActionIntent.Hand.MAIN_HAND; arming = true; phase = Phase.TARGETING; behavior = null; target(null, null); }
    public static void receiveOptions(ActionProtocol.Options value, IPayloadContext context) {
        DebugDiagnostics.log("GUI_OPTIONS_RECEIVE", () -> "query=" + value.query() + " selection=" + value.revision()
            + " generation=" + value.generation() + " encounter=" + value.encounter() + " version=" + value.version()
            + " item=" + value.item() + " abilities=" + value.abilities().size() + " offers=" + value.offers().size()
            + " failure=" + value.failure() + " reason=" + value.reason());
        var connection = context.connection(); var level = Minecraft.getInstance().level; var player = Minecraft.getInstance().player;
        context.enqueueWork(() -> {
            var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter();
            if (state == null || mc.level != level || mc.player != player || mc.getConnection() == null || mc.getConnection().getConnection() != connection
                || !state.generation().equals(value.generation()) || !state.encounterId().equals(value.encounter()) || !value.query().equals(queryId) || value.revision() != revision
                || player == null || !player.getUUID().equals(state.current()) || state.round() != queryRound) {
                DebugDiagnostics.log("GUI_OPTIONS_DROP", () -> "reply=" + value.query() + " replySelection=" + value.revision()
                    + " sameLevel=" + (mc.level == level) + " samePlayer=" + (mc.player == player)
                    + " sameConnection=" + (mc.getConnection() != null && mc.getConnection().getConnection() == connection)
                    + " currentEncounter=" + (state == null ? null : state.encounterId())
                    + " currentGeneration=" + (state == null ? null : state.generation())
                    + " currentOwner=" + (state == null ? null : state.current())
                    + " currentRound=" + (state == null ? -1 : state.round()) + " queryRound=" + queryRound + " " + diagnosticState());
                return;
            }
            var stack = hand == ActionIntent.Hand.MAIN_HAND ? mc.player.getInventory().getItem(slot) : mc.player.getOffhandItem();
            applyOptions(value, stack.isEmpty());
        });
    }
    /** Apply only the still-current reply; also used by headless lifecycle regression checks. */
    static void applyOptions(ActionProtocol.Options value, boolean emptyStack) {
        if (!value.query().equals(queryId) || value.revision() != revision) return;
        queryId = null;
        options = value;
        if (value.item() == null || value.failure().code() != ActionFailure.Code.NONE) {
            confirmedItem = null; abilities = java.util.List.of(); attackBehavior = null;
            arming = false; submitAfterQuery = false;
            // A rejected discovery is feedback, not a user gesture closing the menu.
            if (phase != Phase.CONTEXT_MENU) phase = Phase.IDLE;
            reason = value.reason().isEmpty() ? "查询不可用，请重新选择" : value.reason();
            DebugDiagnostics.log("GUI_OPTIONS_REJECTED", () -> "reply=" + value.query() + " failure=" + value.failure()
                + " reason=" + reason + " " + diagnosticState());
            return;
        }
        itemName = itemSelected ? value.itemName() : "空手";
        attackBehavior = attackForSelection(itemSelected, emptyStack, value.defaultBehavior(), value.abilities());
        abilities = value.abilities().stream().filter(a -> a.kind() != ActionIntent.Capability.ATTACK || a.id().equals(attackBehavior)).toList();
        reason = value.reason().isEmpty() ? "选择目标或服务器提供的行为" : value.reason();
        confirmedItem = value.item();
        if (arming) {
            if (preferredCost == ActionIntent.Capability.ATTACK) behavior = attackBehavior;
            else if (behavior == null) behavior = preferredCost == null ? value.defaultBehavior()
                : abilities.stream().filter(a -> a.kind() == preferredCost).map(ActionProtocol.Ability::id).findFirst().orElse(null);
            var selectedAbility = abilities.stream().filter(a -> a.id().equals(behavior)).findFirst();
            selectedAbility.ifPresent(a -> preferredCost = a.kind());
            phase = selectedAbility.isEmpty() ? Phase.IDLE : Phase.TARGETING; arming = false;
            reason = selectedAbility.isEmpty() ? "能力不可用" : "请选择目标";
        }
        else if (submitAfterQuery) {
            submitAfterQuery = false;
            var selected = value.offers().stream().filter(o -> o.intent().behaviorId().equals(behavior)).findFirst();
            if (selected.isPresent() && selected.get().failure().code() == ActionFailure.Code.NONE) choose(selected.get());
            else { phase = Phase.TARGETING; reason = selected.map(ActionProtocol.Offer::reason).orElse("目标不支持所选行为"); }
        }
        DebugDiagnostics.log("GUI_OPTIONS_APPLIED", () -> "reply=" + value.query() + " default=" + value.defaultBehavior()
            + " emptyStack=" + emptyStack + " attack=" + attackBehavior + " abilities="
            + abilities.stream().limit(32).map(ActionProtocol.Ability::id).toList()
            + " offers=" + offers().size() + " reason=" + reason + " " + diagnosticState());
    }
    public static String abilityStatus() {
        if (arming) return "正在确认物品…";
        if (confirmedItem == null && options != null) return reason;
        return itemSelected ? "此物品没有可用行为" : "选择物品查看行为";
    }
    public static String contextStatus() {
        if (queryId != null) return "正在查询…";
        return options != null && !options.reason().isEmpty() ? options.reason() : "此目标没有可用行为";
    }
    public static void choose(ActionProtocol.Offer offer) {
        var state = ClientCombatState.encounter();
        if (state == null || options == null || running() || offer.failure().code() != ActionFailure.Code.NONE || !offers().contains(offer)) return;
        terminalFailure = "";
        behavior = offer.intent().behaviorId(); preferredCost = offer.intent().capability();
        if (offer.intent().source().kind() == GrantEvidence.Kind.EQUIPMENT) itemSelected = true;
        queryId = null; requested = UUID.randomUUID(); phase = Phase.AWAITING_SERVER;
        DebugDiagnostics.log("PLAYER_SEND", () -> "op=" + requested + " encounter=" + state.encounterId()
            + " version=" + options.version() + " " + DebugDiagnostics.intent(offer.intent()));
        ClientPacketDistributor.sendToServer(new ActionProtocol.Request(state.generation(), state.encounterId(), requested, options.version(), offer.intent(), false));
        options = null; reason = "等待服务器验证";
    }

    static String attackForSelection(boolean selected, boolean empty, String preferred, java.util.List<ActionProtocol.Ability> candidates) {
        if (!selected || empty) return "dndturn:intrinsic_melee";
        return candidates.stream().filter(a -> a.kind() == ActionIntent.Capability.ATTACK && !a.id().equals("dndturn:intrinsic_melee"))
            .sorted(java.util.Comparator.comparing(a -> !a.id().equals(preferred)))
            .map(ActionProtocol.Ability::id).findFirst().orElse(null);
    }

    private static String reason = "选择物品或能力；右键对象查看行为";
    private ClientTacticalPlan() {}
    public static void reset() { terminalFailure = ""; clearPreview(); phase = Phase.IDLE; selectedTarget = null; preferredCost = null; abilities = java.util.List.of(); confirmedItem = null; revision++; itemName = ""; behavior = null; arming = false; submitAfterQuery = false; slot = 0; queryId = null; options = null; hand = ActionIntent.Hand.MAIN_HAND; projection = null; sequence = -1; requested = null; endAfterCancel = false; itemSelected = false; attackBehavior = null; turnOwner = null; turnRound = -1; reason = "选择物品或能力；右键对象查看行为"; }
    public static boolean selectedSlot(int value) { return itemSelected && hand == ActionIntent.Hand.MAIN_HAND && slot == value; }
    /** Retain choice only, never a target, preview, query or execution authorization. */
    public static void suspendSelection() {
        DebugDiagnostics.log("GUI_SELECTION_SUSPEND", ClientTacticalPlan::diagnosticState);
        invalidate(); selectedTarget = null; confirmedItem = null; arming = false;
        if (!running()) phase = Phase.IDLE;
    }
    public static void select(ActionIntent.Capability value) {
        if (running()) return;
        terminalFailure = "";
        preferredCost = value;
        behavior = switch (value) { case MOVE -> "dndturn:move"; case ATTACK -> null; case PLACE -> "dndturn:place"; case BREAK -> "dndturn:break"; case USE_BLOCK -> "dndturn:block"; case USE_ITEM -> null; case EQUIP -> "dndturn:equip"; };
        var p = Minecraft.getInstance().player; if (p == null) return;
        confirmedItem = null; if (!itemSelected) slot = p.getInventory().getSelectedSlot(); phase = Phase.TARGETING; arming = true; target(null, null);
    }
    public static String description() {
        String stage = switch (phase) {
            case IDLE -> "空闲"; case TARGETING -> "选择目标"; case QUERYING -> "查询目标"; case CONTEXT_MENU -> "目标菜单";
            case AWAITING_SERVER -> "等待确认"; case EXECUTING -> "执行中"; case CANCEL_PENDING -> "等待取消";
        };
        String target = "";
        if (selectedTarget != null) {
            if (selectedTarget.kind() == ActionIntent.TargetKind.SELF) target = "自身";
            else if (selectedTarget.cell() != null) target = selectedTarget.cell().x()+", "+selectedTarget.cell().y()+", "+selectedTarget.cell().z();
            else {
                target = "所选实体";
                var level = Minecraft.getInstance().level;
                if (level != null) for (var entity : level.entitiesForRendering()) if (entity.getUUID().equals(selectedTarget.entity())) { target=entity.getName().getString(); break; }
            }
        }
        return stage + " · " + itemName + " · " + (hand == ActionIntent.Hand.MAIN_HAND ? "主手" : "副手") + " · " + (terminalFailure.isEmpty() ? reason : terminalFailure) + (target.isEmpty() ? "" : " · " + target);
    }
    public static void receive(ActionProtocol.Projection state, IPayloadContext context) {
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
            DebugDiagnostics.log("GUI_PLAN_PROJECTION", () -> "op=" + state.operation() + " running=" + state.running()
                + " version=" + state.version() + " failure=" + state.failure() + " reason=" + state.reason());
            if (state.conclusion() != null && state.conclusion().release() == ExecutionConclusion.Release.FAILED)
                reason += "；控制释放失败，服务端正在核对";
            requested = state.running() ? state.operation() : null;
            if (!state.running()) {
                if (state.failure().code() != ActionFailure.Code.NONE) terminalFailure = reason;
                suspendSelection(); projection = null;
                if (!endAfterCancel) refreshSelection();
            }
            else if (phase != Phase.CANCEL_PENDING) phase = Phase.EXECUTING;
            if (!state.running()) terminalVersion = state.version();
            tick();
        });
    }
    public static void tick() {
        var mc = Minecraft.getInstance(); var state = ClientCombatState.encounter();
        if (state != null && observeTurn(state.current(), state.round())) {
            if (!running()) { suspendSelection(); refreshSelection(); }
        }
        updatePreview();
        if (endAfterCancel && !running() && state != null && state.version() >= terminalVersion
            && mc.screen == null && mc.isWindowActive() && !ClientControl.modal()) {
            endAfterCancel = false;
            CombatControls.requestFromUi(EncounterProtocol.IntentKind.END_TURN, null);
        }
    }
    public static boolean running() { return requested != null; }
    static boolean observeTurn(UUID current, long round) {
        boolean changed = !java.util.Objects.equals(turnOwner, current) || turnRound != round;
        turnOwner = current; turnRound = round;
        return changed;
    }
    private static void refreshSelection() {
        var state = ClientCombatState.encounter(); var player = Minecraft.getInstance().player;
        DebugDiagnostics.log("GUI_SELECTION_REFRESH", () -> "current=" + (state == null ? null : state.current())
            + " player=" + (player == null ? null : player.getUUID()) + " " + diagnosticState());
        if (state == null || player == null || !player.getUUID().equals(state.current()) || running()
            || !hasSelection()) return;
        arming = true; phase = Phase.TARGETING; target(null, null);
    }
    static boolean hasSelection() { return itemSelected || behavior != null || preferredCost != null; }
    public static void endTurn() {
        if (!running()) { CombatControls.requestFromUi(EncounterProtocol.IntentKind.END_TURN, null); return; }
        endAfterCancel = true; cancel();
    }
    public static void cancel() {
        DebugDiagnostics.log("GUI_CANCEL", ClientTacticalPlan::diagnosticState);
        terminalFailure = "";
        invalidate(); arming = false;
        if (!endAfterCancel) { behavior = null; preferredCost = null; }
        if (!running()) { selectedTarget = null; phase = Phase.IDLE; reason = "已取消选择"; return; }
        phase = Phase.CANCEL_PENDING;
        var state = ClientCombatState.encounter();
        if (requested != null && state != null)
            ClientPacketDistributor.sendToServer(new ActionProtocol.Request(state.generation(), state.encounterId(), requested,
                state.version(), null, true));
    }
    public static void target(UUID entity, net.minecraft.world.phys.BlockHitResult hit) {
        target(entity, hit, false);
    }
    private static void target(UUID entity, net.minecraft.world.phys.BlockHitResult hit, boolean autoSubmit) {
        var mc = Minecraft.getInstance();
        var state = ClientCombatState.encounter();
        if (state == null || mc.player == null || running()) return;
        String dimension = mc.level.dimension().identifier().toString();
        ActionIntent.Target target;
        if (entity != null) target = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, dimension, entity, null, -1, 0, 0, 0);
        else if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
            target = new ActionIntent.Target(ActionIntent.TargetKind.SELF, dimension, null, null, -1, 0, 0, 0);
        else {
            BlockPos pos = hit.getBlockPos(); Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
            target = new ActionIntent.Target(ActionIntent.TargetKind.BLOCK, dimension, null, new GridCell(pos.getX(), pos.getY(), pos.getZ()),
                hit.getDirection().ordinal(), Math.clamp(local.x, 0, 1), Math.clamp(local.y, 0, 1), Math.clamp(local.z, 0, 1));
        }
        selectedTarget = target;
        // Submission belongs to this query only; discovery must never inherit it.
        beginQuery(state.current(), state.round(), autoSubmit);
        if (autoSubmit) phase = Phase.QUERYING;
        else if (phase == Phase.IDLE || phase == Phase.QUERYING) phase = Phase.TARGETING;
        DebugDiagnostics.log("GUI_QUERY_SEND", () -> "generation=" + state.generation() + " encounter=" + state.encounterId()
            + " version=" + state.version() + " action=" + state.action() + " movement=" + state.movementTicks()
            + " target=" + selectedTarget + " " + diagnosticState());
        ClientPacketDistributor.sendToServer(new ActionProtocol.Query(state.generation(), state.encounterId(), queryId, target, hand, hand == ActionIntent.Hand.MAIN_HAND ? slot : 40, revision, confirmedItem));
        reason = "查询可用行为";
    }
    static void beginQuery(UUID current, long round, boolean autoSubmit) {
        invalidate(); queryId = UUID.randomUUID(); queryRound = round; submitAfterQuery = autoSubmit;
        // This query already belongs to the observed turn. The next tick must not suspend it
        // merely because input/packet handling ran before that tick's turn reconciliation.
        observeTurn(current, round);
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
        if (!projection.operation().equals(steeringOperation)) { steeringOperation=projection.operation(); steeringCursor=projection.cursor(); }
        steeringCursor=Math.max(steeringCursor,projection.cursor());
        var path=projection.route();
        if (!path.isEmpty()) {
            while (steeringCursor<path.size()-1
                && mc.player.position().distanceToSqr(vector(path.get(steeringCursor).feet()))<.09) steeringCursor++;
        }
        Vec3 waypoint = projection.route().isEmpty() ? new Vec3(cell.x() + .5, cell.y(), cell.z() + .5)
            : vector(path.get(steeringCursor).feet());
        Vec3 delta = waypoint.subtract(mc.player.position());
        boolean finalPoint=path.isEmpty() || steeringCursor==path.size()-1;
        if (delta.lengthSqr() < (path.isEmpty() ? .16 : finalPoint?.0064:.09)) return new Steering(Input.EMPTY, Vec2.ZERO);
        double yaw = Math.toRadians(mc.player.getYRot());
        double forward = -Math.sin(yaw) * delta.x + Math.cos(yaw) * delta.z;
        double left = Math.cos(yaw) * delta.x + Math.sin(yaw) * delta.z;
        double length = Math.max(.001, Math.hypot(forward, left));
        float scale = finalPoint ? (float)Math.min(1, delta.horizontalDistance() * 3) : 1;
        boolean jump = delta.y > .5 && mc.player.onGround();
        return new Steering(new Input(forward > .01, forward < -.01, left > .01, left < -.01, jump, false, false),
            new Vec2((float)(left / length) * scale, (float)(forward / length) * scale));
    }
}
