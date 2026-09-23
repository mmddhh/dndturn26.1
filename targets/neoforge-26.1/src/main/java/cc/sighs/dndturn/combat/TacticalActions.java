package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Platform execution leases. The engine owns the root, children, resources and durable results. */
public final class TacticalActions {
    final ServerCombatService service;
    final MinecraftServer server;
    final CombatEngine engine;
    private final Map<UUID, Execution> executions = new HashMap<>();
    private long sequence;
    private record Selection(TacticalNetwork.Query request, TacticalNetwork.Options response) {}
    private final Map<UUID, Selection> selections = new HashMap<>();
    private record ContainerPermit(UUID encounter, long round, BlockPos pos, int menu) {}
    private final Map<UUID, ContainerPermit> containers = new HashMap<>();
    public boolean mayUseContainer(ServerPlayer player) {
        var permit = containers.get(player.getUUID());
        if (permit == null || !permit.encounter().equals(service.encounterOf(player.getUUID()))
            || !service.mayOrganizeInventory(player) || engine.stateView(permit.encounter()).round() != permit.round()
            || player.containerMenu.containerId != permit.menu() || !player.containerMenu.stillValid(player)
            || !player.isWithinBlockInteractionRange(permit.pos(), 0)) return false;
        return player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu;
    }
    public static final class Execution {
        final OperationRecord.Snapshot root;
        public OperationRecord.Snapshot snapshot() { return root; }
        final List<GridCell> path;
        final GridCell targetCell;
        final String blockState;
        UUID movement, action;
        int actionStep;
        DamageTrace launchTrace;
        String runningItemRevision;
        int cursor;
        int stalled;
        Vec3 previous;
        Execution(OperationRecord.Snapshot root, List<GridCell> path, GridCell targetCell, String blockState, Vec3 position) {
            this.root = root; this.path = List.copyOf(path); this.targetCell = targetCell;
            this.blockState = blockState; previous = position;
        }
    }
    TacticalActions(ServerCombatService service, MinecraftServer server, CombatEngine engine) {
        this.service = service; this.server = server; this.engine = engine;
    }
    public TacticalNetwork.Options discover(ServerPlayer player, TacticalNetwork.Query query) {
        if (!server.isSameThread()) throw new IllegalStateException("server thread required");
        var offers = new ArrayList<TacticalNetwork.Offer>();
        long version = 0;
        try {
            if (!service.matchesGeneration(query.generation()) || !query.encounter().equals(service.encounterOf(player.getUUID()))
                || !service.mayOrganizeInventory(player)) throw new IllegalStateException("not an interactive member turn");
            if (running(player.getUUID())) throw new IllegalStateException("cannot change selection while plan is running");
            if (query.revision() <= 0 || query.hand() == TacticalIntent.Hand.MAIN_HAND && (query.slot() < 0 || query.slot() > 8)
                || query.hand() == TacticalIntent.Hand.OFF_HAND && query.slot() != 40) throw new IllegalStateException("invalid selection slot");
            var previousSelection = selections.get(player.getUUID());
            if (previousSelection != null && previousSelection.request().encounter().equals(query.encounter())) {
                if (previousSelection.request().equals(query)) return previousSelection.response();
                if (query.revision() <= previousSelection.request().revision()) throw new IllegalStateException("stale or conflicting selection");
            }
            var selectedStack = query.hand() == TacticalIntent.Hand.MAIN_HAND ? player.getInventory().getItem(query.slot()) : player.getOffhandItem();
            if (query.expectedItem() != null && (query.expectedItem().slot() != query.slot()
                || !query.expectedItem().revision().equals(TacticalItems.revision(player, selectedStack)))) throw new IllegalStateException("selected item changed; select it again");
            if (query.hand() == TacticalIntent.Hand.MAIN_HAND) player.getInventory().setSelectedSlot(query.slot());
            VanillaInputPolicy.correctInventory(player);
            var state = engine.stateView(query.encounter()); version = state.version();
            var selected = query.target();
            for (var adapter : TacticalCapabilities.all()) {
                if (!adapter.supportsItem(selectedStack)) continue;
                TacticalIntent.Target target = selected;
                if (adapter.targets().contains(TacticalIntent.TargetKind.SELF))
                    target = new TacticalIntent.Target(TacticalIntent.TargetKind.SELF, selected.dimension(), null, null, -1, 0, 0, 0);
                else if (selected.kind() == TacticalIntent.TargetKind.BLOCK && adapter.targets().contains(TacticalIntent.TargetKind.GROUND)) {
                    BlockPos adjacent = pos(selected.cell()).relative(Direction.values()[selected.face()]);
                    target = new TacticalIntent.Target(TacticalIntent.TargetKind.GROUND, selected.dimension(), null, cell(adjacent), -1, 0, 0, 0);
                }
                if (!adapter.targets().contains(target.kind())) continue;
                var hand = InteractionHand.valueOf(query.hand().name());
                var item = new TacticalIntent.ItemReference(hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40,
                    TacticalItems.revision(player, player.getItemInHand(hand)));
                var intent = new TacticalIntent(adapter.id(), adapter.version(), query.hand(), adapter.cost(), target, item);
                String reason = "";
                boolean approach = false;
                try {
                    validate(player, intent, state);
                    if (intent.requiresAction() && !state.members().get(player.getUUID()).action()) reason = "action unavailable";
                    else if (!adapter.canExecute(player, intent, player.position())) {
                        approach = true;
                        if (state.members().get(player.getUUID()).movementTicks() == 0) reason = "movement budget exhausted";
                        else path(player, intent, state, targetCell(player,intent));
                    }
                } catch (RuntimeException rejected) { reason = rejected.getMessage() == null ? "behavior unavailable" : rejected.getMessage(); }
                offers.add(new TacticalNetwork.Offer(adapter.label(), intent, reason, approach));
            }
            var response = new TacticalNetwork.Options(service.generation(), query.encounter(), query.query(), version, offers, "", query.revision(),
                new TacticalIntent.ItemReference(query.slot(), TacticalItems.revision(player, player.getItemInHand(InteractionHand.valueOf(query.hand().name())))),
                defaultBehavior(player.getItemInHand(InteractionHand.valueOf(query.hand().name()))),
                TacticalCapabilities.all().stream().filter(a -> a.supportsItem(selectedStack))
                    .map(a -> new TacticalNetwork.Ability(a.id(),a.label(),a.cost(),a.targets())).toList(), selectedStack.getHoverName().getString());
            selections.put(player.getUUID(),new Selection(query,response));
            return response;
        } catch (RuntimeException failure) {
            VanillaInputPolicy.correctInventory(player);
            return new TacticalNetwork.Options(service.generation(), query.encounter(), query.query(), version, List.of(),
                failure.getMessage() == null ? "discovery unavailable" : failure.getMessage(), query.revision(), null, "", List.of(), "");
        }
    }
    private static String defaultBehavior(net.minecraft.world.item.ItemStack stack) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) return "dndturn:place";
        if (stack.is(net.minecraft.world.item.Items.BOW)) return "dndturn:bow";
        if (stack.is(net.minecraft.world.item.Items.CROSSBOW)) return "dndturn:crossbow";
        if (stack.is(net.minecraft.world.item.Items.SNOWBALL)) return "dndturn:snowball";
        if (stack.has(net.minecraft.core.component.DataComponents.CONSUMABLE)) return "dndturn:consume";
        return "dndturn:melee";
    }
    public void request(ServerPlayer player, TacticalNetwork.Request request) {
        if (!server.isSameThread()) throw new IllegalStateException("server thread required");
        boolean created = false;
        boolean mayRecordRejection = false;
        try {
            if (!service.matchesGeneration(request.generation())) throw new IllegalStateException("expired session");
            var completed = engine.resultFor(request.encounter(), request.operation());
            var root = completed == null ? engine.pendingOperation(request.encounter(), request.operation()) : completed.snapshot();
            if (root != null) {
                if (root.kind() != OperationRecord.Kind.PLAN || !root.owner().equals(player.getUUID())
                    || !request.cancel() && (!Objects.equals(root.intent(), request.intent()) || root.encounterVersion() != request.version()))
                    throw new IllegalStateException("operation payload conflict");
                if (completed != null) { send(player, root, null, false, completed.reason()); return; }
                if (request.cancel()) { cancel(player.getUUID(), "cancelled by player"); return; }
                Execution existing = executions.get(player.getUUID());
                send(player, root, existing == null ? null : waypoint(existing), true, "already accepted");
                return;
            }
            if (request.cancel()) throw new IllegalStateException("unknown plan");
            mayRecordRejection = request.encounter().equals(service.encounterOf(player.getUUID()));
            if (!request.encounter().equals(service.encounterOf(player.getUUID())) || !service.mayOrganizeInventory(player))
                throw new IllegalStateException("not an interactive member turn");
            var state = engine.stateView(request.encounter());
            if (state.version() != request.version()) throw new IllegalStateException("stale encounter version");
            TacticalIntent intent = request.intent();
            validate(player, intent, state);
            GridCell target = targetCell(player, intent);
            List<GridCell> path = path(player, intent, state, target);
            if (!path.isEmpty() && state.members().get(player.getUUID()).movementTicks() < 1)
                throw new IllegalStateException("movement budget exhausted");
            root = new OperationRecord.Snapshot(request.operation(), null, state.id(), player.getUUID(), player.getUUID(),
                intent.target().entity(), service.planClock(), state.version(), cell(player.blockPosition()), target,
                OperationRecord.Kind.PLAN, service.generation(), intent);
            if (!engine.beginOperation(root)) throw new IllegalStateException("action unavailable or another plan pending");
            Execution execution = new Execution(root, path, target,
                intent.target().kind() == TacticalIntent.TargetKind.BLOCK ? player.level().getBlockState(pos(target)).toString() : "",
                player.position());
            executions.put(player.getUUID(), execution);
            created = true;
            if (!path.isEmpty()) {
                execution.movement = UUID.randomUUID();
                service.beginPlanMovement(player, root.operationId(), execution.movement);
                send(player, root, waypoint(execution), true, "approaching");
            } else execute(player, execution);
            service.resyncMember(player, state.id());
        } catch (RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().debug("Tactical request rejected: {}", failure.toString());
            Execution execution = executions.get(player.getUUID());
            if (created && execution != null && execution.root.operationId().equals(request.operation())) {
                if (execution.action != null) finishAction(player, execution, OperationRecord.Outcome.UNKNOWN, "behavior start outcome uncertain");
                else cancel(player.getUUID(), failure.getMessage());
            }
            else {
                String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                if (mayRecordRejection) {
                    var intent = request.intent();
                    var rejected = new OperationRecord.Snapshot(request.operation(), null, request.encounter(), player.getUUID(), player.getUUID(),
                        intent.target().entity(), service.planClock(), request.version(), cell(player.blockPosition()), intent.target().cell(),
                        OperationRecord.Kind.PLAN, service.generation(), intent);
                    engine.rejectPlan(rejected, reason);
                    service.resyncMember(player, request.encounter());
                }
                if (NetworkRegistry.hasChannel(player.connection, TacticalNetwork.Projection.TYPE.id()))
                    PacketDistributor.sendToPlayer(player, new TacticalNetwork.Projection(service.generation(), request.encounter(),
                        request.operation(), ++sequence, null, false, reason, OperationRecord.Outcome.REJECTED, 0));
            }
        }
    }
    private void validate(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
        if (!intent.target().dimension().equals(player.level().dimension().identifier().toString()))
            throw new IllegalStateException("target dimension changed");
        if (intent.item() != null) {
            int slot = intent.item().slot();
            if (slot != (intent.hand() == TacticalIntent.Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40)) throw new IllegalStateException("equip selected item first");
            Execution active = executions.get(player.getUUID());
            String expected = active != null && active.runningItemRevision != null ? active.runningItemRevision : intent.item().revision();
            if (!expected.equals(TacticalItems.revision(player, TacticalBehavior.stack(player, intent))))
                throw new IllegalStateException("item stack changed");
        }
        TacticalCapabilities.resolve(intent).validate(player, intent, state);
        GridCell target = targetCell(player, intent);
        if (target != null && (!player.level().hasChunkAt(pos(target))
            || !state.region().containsPoint(target.x() + .5, target.y() + .5, target.z() + .5)))
            throw new IllegalStateException("target outside loaded encounter");
    }
    private List<GridCell> path(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state, GridCell target) {
        if (canExecute(player, intent, player.position())) return List.of();
        if (target == null) throw new IllegalStateException("target required");
        GridCell start = cell(player.blockPosition());
        MinecraftCellProbe probe = new MinecraftCellProbe(player.level(), player, state.region());
        List<GridCell> goals = new ArrayList<>();
        int radius = TacticalCapabilities.resolve(intent).approachRadius();
        for (int x = -radius; x <= radius; x++) for (int y = -1; y <= 1; y++) for (int z = -radius; z <= radius; z++) {
            if (radius == 0 && y != 0) continue;
            GridCell goal = new GridCell(target.x() + x, target.y() + y, target.z() + z);
            if (probe.canOccupy(goal) && canExecute(player, intent, point(goal))) goals.add(goal);
        }
        var proposal = TacticalPlanner.propose(start, Set.copyOf(goals), 128, 4096, state.region().version(), probe);
        if (proposal.cost() != Integer.MAX_VALUE && TacticalPlanner.revalidate(start, proposal, state.region().version(), probe))
            return proposal.cells();
        throw new IllegalStateException("no supported path to execution position");
    }
    private boolean canExecute(ServerPlayer player, TacticalIntent intent, Vec3 feet) {
        return TacticalCapabilities.resolve(intent).canExecute(player, intent, feet);
    }
    public boolean allowsMove(ServerPlayer player, Vec3 wanted) {
        Execution execution = executions.get(player.getUUID());
        if (execution == null) return true;
        GridCell next = waypoint(execution);
        if (next == null) return false;
        Vec3 end = point(next), start = execution.cursor == 0 ? point(execution.root.sourceCell()) : point(execution.path.get(execution.cursor - 1));
        Vec3 edge = end.subtract(start);
        double t = Math.max(0, Math.min(1, wanted.subtract(start).dot(edge) / Math.max(edge.lengthSqr(), .0001)));
        return wanted.distanceToSqr(start.add(edge.scale(t))) <= .81;
    }
    public void tick() {
        selections.entrySet().removeIf(e -> !e.getValue().request().encounter().equals(service.encounterOf(e.getKey())));
        if (server.tickRateManager().isFrozen()) return;
        for (UUID owner : List.copyOf(containers.keySet())) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(owner);
            if (viewer == null || !mayUseContainer(viewer)) {
                var permit = containers.remove(owner);
                if (viewer != null && viewer.containerMenu.containerId == permit.menu()) viewer.closeContainer();
            }
        }
        for (Execution execution : List.copyOf(executions.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(execution.root.owner());
            if (player == null) for (var level : server.getAllLevels()) {
                if (level.getEntity(execution.root.owner()) instanceof ServerPlayer found) { player = found; break; }
            }
            if (player == null) { cancel(execution.root.owner(), "player unavailable"); continue; }
            try {
                if (!execution.root.encounterId().equals(service.encounterOf(player.getUUID())) || !player.isAlive())
                    throw new IllegalStateException("plan owner left");
                if (engine.pendingOperation(execution.root.encounterId(), execution.root.operationId()) == null)
                    throw new IllegalStateException("plan no longer pending");
                if (execution.action != null) { tickAction(player, execution); continue; }
                if (execution.movement == null) continue;
                var intent = execution.root.intent();
                validate(player, intent, engine.stateView(execution.root.encounterId()));
                if (!Objects.equals(execution.targetCell, targetCell(player, intent))) throw new IllegalStateException("target moved");
                GridCell next = waypoint(execution);
                if (next != null && player.position().distanceToSqr(point(next)) < .16) {
                    execution.cursor++;
                    next = waypoint(execution);
                    send(player, execution.root, next, true, "approaching");
                }
                if (next == null) {
                    service.finishPlanMovement(player);
                    execution.movement = null;
                    execute(player, execution);
                } else {
                    if (!service.hasPlayerMoveLease(player.getUUID())) throw new IllegalStateException("movement budget exhausted");
                    var probe = new MinecraftCellProbe(player.level(), player, engine.stateView(execution.root.encounterId()).region());
                    GridCell current = cell(player.blockPosition());
                    if (!probe.canOccupy(next) || !current.equals(next) && probe.traversalCost(current, next) <= 0)
                        throw new IllegalStateException("path obstructed");
                    execution.stalled = player.position().distanceToSqr(execution.previous) < .0001 ? execution.stalled + 1 : 0;
                    execution.previous = player.position();
                    if (execution.stalled >= 100) throw new IllegalStateException("movement stalled");
                }
            } catch (RuntimeException error) {
                if (execution.action != null) finishAction(player, execution, OperationRecord.Outcome.UNKNOWN, "behavior step outcome uncertain: " + error.getMessage());
                else cancel(execution.root.owner(), error.getMessage());
            }
        }
    }
    private void execute(ServerPlayer player, Execution execution) {
        var root = execution.root;
        var intent = root.intent();
        validate(player, intent, engine.stateView(root.encounterId()));
        if (!canExecute(player, intent, player.position())) throw new IllegalStateException("execution position invalidated");
        if (!execution.blockState.isEmpty() && !execution.blockState.equals(player.level().getBlockState(pos(execution.targetCell)).toString()))
            throw new IllegalStateException("target block changed");
        TacticalCapabilities.resolve(intent).prepare(this, player, execution);
        TacticalCapabilities.resolve(intent).start(this, player, execution);
    }
    private void tickAction(ServerPlayer player, Execution execution) {
        var intent = execution.root.intent();
        validate(player, intent, engine.stateView(execution.root.encounterId()));
        if (!canExecute(player, intent, player.position())) throw new IllegalStateException("execution target out of reach");
        TacticalCapabilities.resolve(intent).tick(this, player, execution);
    }
    public void beginStep(ServerPlayer player, Execution execution) {
        var root = execution.root;
        var snapshot = new OperationRecord.Snapshot(UUID.randomUUID(), root.operationId(), root.encounterId(), player.getUUID(), player.getUUID(),
            root.target(), service.planClock(), engine.stateView(root.encounterId()).version(), cell(player.blockPosition()), execution.targetCell,
            root.intent().executionKind(), service.generation());
        if (!engine.beginPlanStep(snapshot)) throw new IllegalStateException("execution step rejected");
        execution.action = snapshot.operationId();
    }
    public void accept(ServerPlayer player, Execution execution, String reason) {
        engine.publish(execution.root.encounterId(), execution.action, execution.actionStep++, OperationRecord.Outcome.ACCEPTED, reason, 0, 0, false);
        send(player, execution.root, null, true, reason);
    }
    public void finishAction(ServerPlayer player, Execution execution, OperationRecord.Outcome outcome, String reason) {
        try { TacticalCapabilities.resolve(execution.root.intent()).cancel(this, player, execution); }
        catch (RuntimeException failure) {
            player.stopUsingItem(); outcome = OperationRecord.Outcome.UNKNOWN;
            reason = "behavior cleanup failed: " + failure.getClass().getSimpleName();
        }
        engine.publish(execution.root.encounterId(), execution.action, execution.actionStep++, outcome, reason, 0, 0, true);
        execution.action = null;
        VanillaInputPolicy.correctInventory(player);
        finish(player, execution, outcome, reason);
    }
    void observeMenu(ServerPlayer player, Execution execution) {
        if (player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu)
            containers.put(player.getUUID(), new ContainerPermit(execution.root.encounterId(),
                engine.stateView(execution.root.encounterId()).round(), pos(execution.targetCell), player.containerMenu.containerId));
        else if (player.containerMenu != player.inventoryMenu) player.closeContainer();
    }
    public boolean runningIn(UUID encounter) { return executions.values().stream().anyMatch(e -> e.root.encounterId().equals(encounter)); }
    public boolean running(UUID owner) { return executions.containsKey(owner); }
    public void cancel(UUID owner, String reason) {
        Execution execution = executions.get(owner);
        if (execution == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null) for (var level : server.getAllLevels()) {
            if (level.getEntity(owner) instanceof ServerPlayer found) { player = found; break; }
        }
        if (player != null) try { TacticalCapabilities.resolve(execution.root.intent()).cancel(this, player, execution); }
        catch (RuntimeException failure) { player.stopUsingItem(); reason = "behavior cleanup failed: " + failure.getMessage(); }
        service.finishPlanMovement(owner);
        if (execution.action != null && engine.encounterIds().contains(execution.root.encounterId())
            && engine.pendingOperation(execution.root.encounterId(), execution.action) != null) {
            engine.publish(execution.root.encounterId(), execution.action, execution.actionStep++, OperationRecord.Outcome.INTERRUPTED,
                reason == null ? "interrupted" : reason, 0, 0, true);
            execution.action = null;
        }
        finish(player, execution, OperationRecord.Outcome.INTERRUPTED, reason == null ? "interrupted" : reason);
    }
    void finish(ServerPlayer player, Execution execution, OperationRecord.Outcome outcome, String reason) {
        var root = execution.root;
        if (engine.encounterIds().contains(root.encounterId()) && engine.pendingOperation(root.encounterId(), root.operationId()) != null)
            engine.publish(root.encounterId(), root.operationId(), 0, outcome, reason, 0, 0, true);
        executions.remove(root.owner());
        var terminal = engine.resultFor(root.encounterId(), root.operationId());
        if (terminal != null) reason = terminal.reason();
        if (player != null) { service.resyncMember(player, root.encounterId()); send(player, root, null, false, reason); }
    }
    void send(ServerPlayer player, OperationRecord.Snapshot root, GridCell point, boolean running, String reason) {
        send(player, root.encounterId(), root.operationId(), point, running, reason);
    }
    private void send(ServerPlayer player, UUID encounter, UUID operation, GridCell point, boolean running, String reason) {
        if (!NetworkRegistry.hasChannel(player.connection, TacticalNetwork.Projection.TYPE.id())) return;
        var result = running ? null : engine.resultFor(encounter, operation);
        var message = result != null && result.snapshot().kind() == OperationRecord.Kind.PLAN
            ? TacticalNetwork.Projection.terminal(service.generation(), ++sequence, result)
            : new TacticalNetwork.Projection(service.generation(), encounter, operation, ++sequence, point, running, reason,
                running ? null : OperationRecord.Outcome.REJECTED, 0);
        PacketDistributor.sendToPlayer(player, message);
    }
    static GridCell targetCell(ServerPlayer player, TacticalIntent intent) {
        if (intent.target().kind() == TacticalIntent.TargetKind.ENTITY) {
            var target = player.level().getEntity(intent.target().entity());
            if (target == null) throw new IllegalStateException("target unavailable");
            return cell(target.blockPosition());
        }
        return intent.target().cell();
    }
    private static GridCell waypoint(Execution execution) { return execution.cursor < execution.path.size() ? execution.path.get(execution.cursor) : null; }
    static GridCell cell(BlockPos pos) { return new GridCell(pos.getX(), pos.getY(), pos.getZ()); }
    static BlockPos pos(GridCell cell) { return new BlockPos(cell.x(), cell.y(), cell.z()); }
    static Vec3 point(GridCell cell) { return new Vec3(cell.x() + .5, cell.y(), cell.z() + .5); }
}
