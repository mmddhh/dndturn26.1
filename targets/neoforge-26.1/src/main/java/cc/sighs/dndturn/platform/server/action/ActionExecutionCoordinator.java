package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.ability.ProcessState;
import cc.sighs.dndturn.domain.encounter.operation.NativeObservation;
import cc.sighs.dndturn.platform.server.ability.NativeObservations;
import cc.sighs.dndturn.platform.server.ai.MovementPorts;

import cc.sighs.dndturn.application.planning.MovementPlanner;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.action.ExecutionRequest;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.operation.DamageTrace;
import cc.sighs.dndturn.domain.encounter.operation.ExecutionConclusion;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.observation.ItemStackFingerprint;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.ability.AbilityExecutor;
import cc.sighs.dndturn.platform.server.actor.ActorEquipment;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import cc.sighs.dndturn.platform.server.control.VanillaInputPolicy;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import cc.sighs.dndturn.platform.spatial.AimGeometry;
import cc.sighs.dndturn.platform.spatial.PreviewPathfinder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Platform execution leases. The engine owns the root, children, resources and durable results. */
public final class ActionExecutionCoordinator {
    final EncounterRuntime service;
    final MinecraftServer server;
    final EncounterAuthority engine;
    private final Map<UUID, Execution> executions = new HashMap<>();
    private final Map<UUID, Execution> releaseFaults = new LinkedHashMap<>();
    private Execution releasing;
    public record ReleaseReconciliation(UUID operation, UUID ownerInstance, long tick, boolean retired) {}
    private final Map<UUID, ReleaseReconciliation> releaseReconciliations = new HashMap<>();
    public ReleaseReconciliation releaseReconciliation(UUID operation) { return releaseReconciliations.get(operation); }
    private long sequence;
    private final SelectedPositionPlanner selectedPositions = new SelectedPositionPlanner(this);
    List<ActionProtocol.PreviewStep> selectedPreviewPath(ServerPlayer player, ActionIntent intent, EncounterAuthority.StateView state) {
        return selectedPositions.accept(player, intent, state);
    }
    private record Selection(ActionProtocol.Query request, ActionProtocol.Options response) {}
    private final Map<UUID, Selection> selections = new HashMap<>();
    private record ContainerPermit(UUID encounter, long round, BlockPos pos, int menu) {}
    private final Map<UUID, ContainerPermit> containers = new HashMap<>();
    public UUID presentationUseId(UUID owner) {
        var execution = executions.get(owner);
        return execution == null ? null : execution.action;
    }
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
        final UUID ownerInstance;
        final AbilityExecutor behavior;
        ExecutionRequest ruleRequest;
        UUID targetInstance;
        java.lang.ref.WeakReference<LivingEntity> endpoint;
        UUID useIdentity;
        Integer destroyStart;
        public OperationRecord.Snapshot snapshot() { return root; }
        public UUID actionOperation() { return action; }
        final List<GridCell> path;
        List<ActionProtocol.PreviewStep> preciseRoute = List.of();
        Vec3 routeStart;
        Vec3 boundTargetPosition;
        String routePose;
        final GridCell targetCell;
        final String blockState;
        UUID movement, action;
        UUID releaseAction;
        int actionStep;
        DamageTrace launchTrace;
        AbilityCheckpoint.Phase phase = AbilityCheckpoint.Phase.APPROACH;
        long executionSteps;
        List<GridCell> footprint = List.of();
        Set<Integer> observedSlots = Set.of();
        AbilityCheckpoint.Sample before, observed;
        List<NativeObservation> nativeObservations = List.of();
        final List<AbilityCheckpoint.Spawn> spawned = new ArrayList<>();
        AimGeometry.Aim aimBefore, plannedAim;
        TacticalImpact.Prepared preparedImpact;
        String observationFailure = "";
        String runningItemRevision;
        AbilityCheckpoint.SourceChange sourceChange;
        int cursor;
        int stalled;
        boolean released;
        boolean effectInvoked;
        String releaseFailure = "";
        ActionFailure.Details failure = ActionFailure.Details.NONE;
        Vec3 previous;
        Execution(OperationRecord.Snapshot root, List<GridCell> path, GridCell targetCell, String blockState, Vec3 position, UUID ownerInstance) {
            this.behavior = AbilityAdapterRegistry.resolve(root.intent());
            this.root = root; this.path = List.copyOf(path); this.targetCell = targetCell;
            this.ownerInstance = Objects.requireNonNull(ownerInstance);
            this.blockState = blockState; previous = position;
        }
    }
    public ActionExecutionCoordinator(EncounterRuntime service, MinecraftServer server, EncounterAuthority engine) {
        this.service = service; this.server = server; this.engine = engine;
    }
    /** Explicit equipment command followed by a side-effect-free ability query. */
    public ActionProtocol.Options selectAndDiscover(ServerPlayer player, ActionProtocol.Query query) {
        DebugDiagnostics.log("PLAYER_QUERY", () -> "actor=" + player.getUUID() + " query=" + query.query()
            + " encounter=" + query.encounter() + " selection=" + query.revision()
            + " slot=" + query.slot() + " hand=" + query.hand() + " target=" + query.target());
        if (!server.isSameThread()) throw new IllegalStateException("server thread required");
        try {
            service.requireAbilityWork(player.getUUID(), query.encounter(), AbilityWorkBudget.Work.QUERY);
            validateSelection(player, query);
            var previous = selections.get(player.getUUID());
            if (previous != null && previous.request().equals(query)) return previous.response();
            if (query.hand() == ActionIntent.Hand.MAIN_HAND) player.getInventory().setSelectedSlot(query.slot());
            VanillaInputPolicy.correctInventory(player);
            var response = discoverInternal(player, query, false);
            DebugDiagnostics.log("QUERY_RESPONSE", () -> "actor=" + player.getUUID() + " query=" + query.query()
                + " encounter=" + query.encounter() + " selection=" + query.revision() + " slot=" + query.slot()
                + " version=" + response.version() + " item=" + response.item() + " default=" + response.defaultBehavior()
                + " abilities=" + response.abilities().stream().limit(32).map(ActionProtocol.Ability::id).toList()
                + " offers=" + response.offers().size() + " failure=" + response.failure() + " reason=" + response.reason());
            selections.put(player.getUUID(), new Selection(query, response));
            return response;
        } catch (RuntimeException failure) {
            VanillaInputPolicy.correctInventory(player);
            return discoveryFailure(query, failure);
        }
    }
    private void validateSelection(ServerPlayer player, ActionProtocol.Query query) {
        if (!service.matchesGeneration(query.generation())) throw new ActionFailure(ActionFailure.Code.EXPIRED_SESSION, ActionFailure.Retry.REFRESH_AND_REPROPOSE, "expired session");
        requireReleasedControl(player.getUUID());
        if (!query.encounter().equals(service.encounterOf(player.getUUID()))
            || !service.mayOrganizeInventory(player)) throw new IllegalStateException("not an interactive member turn");
        if (running(player.getUUID())) throw new IllegalStateException("cannot change selection while plan is running");
        if (query.revision() <= 0 || query.hand() == ActionIntent.Hand.MAIN_HAND && (query.slot() < 0 || query.slot() > 8)
            || query.hand() == ActionIntent.Hand.OFF_HAND && query.slot() != 40) throw new IllegalStateException("invalid selection slot");
        var previous = selections.get(player.getUUID());
        if (previous != null && previous.request().encounter().equals(query.encounter())
            && !previous.request().equals(query) && query.revision() <= previous.request().revision())
            throw ActionFailure.source("stale or conflicting selection");
        var stack = query.hand() == ActionIntent.Hand.MAIN_HAND ? player.getInventory().getItem(query.slot()) : player.getOffhandItem();
        if (query.expectedItem() != null && (query.expectedItem().slot() != query.slot()
            || !query.expectedItem().revision().equals(ItemStackFingerprint.revision(player, stack))))
            throw ActionFailure.source("selected item changed; select it again");
    }
    private ActionProtocol.Options discoveryFailure(ActionProtocol.Query query, RuntimeException failure) {
        DebugDiagnostics.log("QUERY_REJECTED", () -> "query=" + query.query() + " encounter=" + query.encounter()
            + " selection=" + query.revision() + " slot=" + query.slot() + " hand=" + query.hand()
            + " failure=" + ActionFailure.classify(failure) + " exception=" + DebugDiagnostics.failure(failure));
        return new ActionProtocol.Options(service.generation(), query.encounter(), query.query(), 0, List.of(),
            failure.getMessage() == null ? "discovery unavailable" : failure.getMessage(), query.revision(), null, "", List.of(), "", ActionFailure.classify(failure));
    }
    /** Reads the current equipment only. Never selects, synchronizes inventory, or acquires control. */
    public ActionProtocol.Options discover(ServerPlayer player, ActionProtocol.Query query) {
        return discoverInternal(player, query, true);
    }
    private ActionProtocol.Options discoverInternal(ServerPlayer player, ActionProtocol.Query query, boolean chargeQuery) {
        if (!server.isSameThread()) throw new IllegalStateException("server thread required");
        var offers = new ArrayList<ActionProtocol.Offer>();
        var abilities = new ArrayList<ActionProtocol.Ability>();
        long version = 0;
        try {
            if (!service.matchesGeneration(query.generation())) throw new ActionFailure(ActionFailure.Code.EXPIRED_SESSION, ActionFailure.Retry.REFRESH_AND_REPROPOSE, "expired session");
            requireReleasedControl(player.getUUID());
            if (!query.encounter().equals(service.encounterOf(player.getUUID()))
                || !service.mayOrganizeInventory(player)) throw new IllegalStateException("not an interactive member turn");
            if (running(player.getUUID())) throw new IllegalStateException("cannot change selection while plan is running");
            if (query.revision() <= 0 || query.hand() == ActionIntent.Hand.MAIN_HAND && (query.slot() < 0 || query.slot() > 8)
                || query.hand() == ActionIntent.Hand.OFF_HAND && query.slot() != 40) throw new IllegalStateException("invalid selection slot");
            if (query.hand() == ActionIntent.Hand.MAIN_HAND && query.slot() != player.getInventory().getSelectedSlot())
                throw ActionFailure.source("equipment selection must be confirmed before discovery");
            if (chargeQuery) service.requireAbilityWork(player.getUUID(), query.encounter(), AbilityWorkBudget.Work.QUERY);
            var selectedStack = query.hand() == ActionIntent.Hand.MAIN_HAND ? player.getInventory().getItem(query.slot()) : player.getOffhandItem();
            if (query.expectedItem() != null && (query.expectedItem().slot() != query.slot()
                || !query.expectedItem().revision().equals(ItemStackFingerprint.revision(player, selectedStack)))) throw ActionFailure.source("selected item changed; select it again");
            var state = engine.stateView(query.encounter()); version = state.version();
            var selected = query.target();
            var actor = new LiveActorContext(player);
            var actorSnapshot = MinecraftSnapshotCapture.capture(actor, state);
            for (var binding : actorSnapshot.abilities()) {
                if (binding.definition().activation() != AbilityDefinition.Activation.MANUAL) continue;
                var adapter = binding.definition();
                var source = binding.source();
                if (source.hand() != null && source.hand() != query.hand()) continue;
                abilities.add(new ActionProtocol.Ability(adapter.id(), adapter.label(), adapter.kind(), adapter.targets(), adapter.version(), source));
                ActionIntent.Target target = selected;
                if (adapter.targets().contains(ActionIntent.TargetKind.SELF))
                    target = new ActionIntent.Target(ActionIntent.TargetKind.SELF, selected.dimension(), null, null, -1, 0, 0, 0);
                else if (selected.kind() == ActionIntent.TargetKind.BLOCK && adapter.targets().contains(ActionIntent.TargetKind.GROUND)) {
                    BlockPos adjacent = pos(selected.cell()).relative(Direction.values()[selected.face()]);
                    target = new ActionIntent.Target(ActionIntent.TargetKind.GROUND, selected.dimension(), null, cell(adjacent), -1, 0, 0, 0);
                }
                if (!adapter.targets().contains(target.kind())) continue;
                var intent = new ActionIntent(adapter.id(), adapter.version(), adapter.kind(), target, source);
                String reason = "";
                ActionFailure.Details failure = ActionFailure.Details.NONE;
                boolean approach = false;
                try {
                    validate(player, intent, state);
                    var resolution = AbilityAdapterRegistry.definitions().resolve(MinecraftSnapshotCapture.capture(actor, actorSnapshot, intent, state,
                        source, ResolutionContext.Stage.PROPOSAL));
                    AbilityAdapterRegistry.requireAccepted(resolution);
                    if (resolution.status() == RuleResolver.Status.REQUIRES_APPROACH) {
                        approach = true;
                        if (state.members().get(player.getUUID()).movementTicks() == 0) reason = "movement budget exhausted";
                        else path(player, intent, state, targetCell(player,intent));
                    }
                } catch (RuntimeException rejected) { reason = rejected.getMessage() == null ? "behavior unavailable" : rejected.getMessage(); failure = ActionFailure.classify(rejected); }
                if (!reason.isEmpty() && failure.code() == ActionFailure.Code.NONE) failure = ActionFailure.Details.REJECTED;
                offers.add(new ActionProtocol.Offer(adapter.label(), intent, reason, approach, failure));
                if (DebugDiagnostics.enabled()) {
                    var diagnosticOffer = offers.getLast();
                    DebugDiagnostics.log("PLAYER_OFFER", () -> "actor=" + player.getUUID() + " query=" + query.query()
                        + " " + DebugDiagnostics.intent(diagnosticOffer.intent())
                        + " approachRequired=" + diagnosticOffer.approach()
                        + " failure=" + diagnosticOffer.failure() + " reason=" + diagnosticOffer.reason());
                }
            }
            var response = new ActionProtocol.Options(service.generation(), query.encounter(), query.query(), version, offers, "", query.revision(),
                new ActionIntent.ItemReference(query.slot(), ItemStackFingerprint.revision(player, player.getItemInHand(InteractionHand.valueOf(query.hand().name())))),
                defaultBehavior(player.getItemInHand(InteractionHand.valueOf(query.hand().name())),abilities),
                List.copyOf(abilities), selectedStack.getHoverName().getString());
            return response;
        } catch (RuntimeException failure) {
            return discoveryFailure(query, failure);
        }
    }
    private static String defaultBehavior(net.minecraft.world.item.ItemStack stack,List<ActionProtocol.Ability> abilities) {
        if (stack.isEmpty()) return "dndturn:intrinsic_melee";
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) return "dndturn:place";
        if (stack.getItem() instanceof net.minecraft.world.item.BowItem) return "dndturn:bow";
        if (stack.getItem() instanceof net.minecraft.world.item.CrossbowItem) return "dndturn:crossbow";
        if (stack.getItem() instanceof net.minecraft.world.item.SnowballItem) return "dndturn:snowball";
        if (stack.has(net.minecraft.core.component.DataComponents.CONSUMABLE)) return "dndturn:consume";
        for (var ability:abilities) if (Set.of("dndturn:equip","dndturn:brush","dndturn:spawn_item","dndturn:bucket","dndturn:bottle",
            "dndturn:fishing_rod","dndturn:reel","dndturn:egg","dndturn:experience_bottle","dndturn:splash_potion").contains(ability.id())) return ability.id();
        return "dndturn:melee";
    }
    public void request(ServerPlayer player, ActionProtocol.Request request) {
        submit(new LiveActorContext(player), request.generation(), request.encounter(), request.operation(), request.version(), request.intent(), request.cancel());
    }
    /** Shared server submission; AI supplies values directly, never a player or a packet. */
    public void submit(LiveActorContext actor, UUID generation, UUID encounter, UUID operation, long version, ActionIntent invocation, boolean cancellation) {
        DebugDiagnostics.log("REQUEST", () -> "actor=" + actor.id() + " instance=" + actor.instance()
            + " origin=" + (actor.body() instanceof ServerPlayer ? "PLAYER" : "MOB")
            + " generation=" + generation + " encounter=" + encounter + " op=" + operation
            + " version=" + version + " cancel=" + cancellation + " " + DebugDiagnostics.intent(invocation));
        Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(operation);
        if (version < 0 || !cancellation && invocation == null || cancellation && invocation != null)
            throw new IllegalArgumentException("invalid submission");
        actor.verifyCurrent();
        LivingEntity player = actor.body();
        if (!server.isSameThread()) throw new IllegalStateException("server thread required");
        boolean created = false;
        boolean mayRecordRejection = false;
        try {
            if (!service.matchesGeneration(generation)) throw new ActionFailure(ActionFailure.Code.EXPIRED_SESSION, ActionFailure.Retry.REFRESH_AND_REPROPOSE, "expired session");
            var completed = engine.resultFor(encounter, operation);
            var root = completed == null ? engine.pendingOperation(encounter, operation) : completed.snapshot();
            if (root != null) {
                if (root.kind() != OperationRecord.Kind.PLAN || !root.owner().equals(player.getUUID())
                    || !cancellation && (!Objects.equals(root.intent(), invocation) || root.encounterVersion() != version))
                    throw new ActionFailure(ActionFailure.Code.PAYLOAD_CONFLICT, ActionFailure.Retry.QUERY_OPERATION, "operation payload conflict");
                if (completed != null) {
                    DebugDiagnostics.log("REPLAY_RESULT", () -> "op=" + operation + " outcome=" + completed.outcome() + " reason=" + completed.reason());
                    send(player, root, null, false, completed.reason()); return;
                }
                if (cancellation) { cancel(player.getUUID(), "cancelled by player"); return; }
                Execution existing = executions.get(player.getUUID());
                DebugDiagnostics.log("REPLAY_PENDING", () -> "op=" + operation + " actor=" + actor.id());
                send(player, root, existing == null ? null : waypoint(existing), true, "already accepted");
                return;
            }
            if (cancellation) throw new IllegalStateException("unknown plan");
            mayRecordRejection = encounter.equals(service.encounterOf(player.getUUID()));
            requireReleasedControl(player.getUUID());
            if (!encounter.equals(service.encounterOf(player.getUUID())) || !service.actionHost().maySubmitPlan(actor))
                throw new IllegalStateException("not an interactive member turn");
            var state = engine.stateView(encounter);
            if (state.version() != version) throw ActionFailure.staleEncounter();
            ActionIntent intent = invocation;
            validate(player, intent, state);
            GridCell target = targetCell(player, intent);
            List<ActionProtocol.PreviewStep> precise = intent.approach() == null ? null
                : selectedPreviewPath(actor.requirePlayer(), intent, state);
            List<GridCell> path = precise == null ? path(player, intent, state, target)
                : precise.stream().map(s -> PreviewPathfinder.key(PreviewPathfinder.vector(s.feet()))).toList();
            if (!path.isEmpty() && state.members().get(player.getUUID()).movementTicks() < 1)
                throw new IllegalStateException("movement budget exhausted");
            root = new OperationRecord.Snapshot(operation, null, state.id(), player.getUUID(), player.getUUID(),
                intent.target().entity(), service.actionHost().planClock(), state.version(), cell(player.blockPosition()), target,
                OperationRecord.Kind.PLAN, service.generation(), intent);
            if (!engine.beginOperation(root)) throw new IllegalStateException("action unavailable or another plan pending");
            DebugDiagnostics.log("GATE_ACCEPTED", () -> "op=" + operation + " actor=" + actor.id()
                + " encounter=" + encounter + " phase=" + state.phase() + " version=" + state.version()
                + " resources=" + state.members().get(actor.id()) + " pathNodes=" + path.size());
            Execution execution = new Execution(root, path, target,
                intent.target().kind() == ActionIntent.TargetKind.BLOCK ? actor.level().getBlockState(pos(target)).toString() : "",
                player.position(), instance(player));
            if (intent.target().entity() != null) execution.targetInstance = entityInstance(actor.level().getEntity(intent.target().entity()));
            execution.endpoint = new java.lang.ref.WeakReference<>(player);
            if (precise != null) {
                execution.preciseRoute = precise;
                execution.routeStart = player.position();
                execution.routePose = SelectedPositionPlanner.posture((ServerPlayer)player);
                if (intent.target().entity() != null) execution.boundTargetPosition = actor.level().getEntity(intent.target().entity()).position();
            }
            executions.put(player.getUUID(), execution);
            created = true;
            checkpoint(execution);
            if (!path.isEmpty()) {
                execution.movement = UUID.randomUUID();
                if (player instanceof ServerPlayer input) service.actionHost().beginPlanMovement(input, root.operationId(), execution.movement);
                else if (player instanceof Mob mob) service.actionHost().beginMobPlanMovement(mob, root, execution.movement, path.getLast());
                else throw new IllegalStateException("movement port unavailable");
                send(player, root, waypoint(execution), true, "approaching");
            } else execute(player, execution);
            resync(player, state.id());
        } catch (RuntimeException failure) {
            DebugDiagnostics.log("SUBMIT_FAILURE", () -> "op=" + operation + " actor=" + actor.id()
                + " failure=" + ActionFailure.classify(failure) + " reason=" + failure.getMessage());
            if (!created && player instanceof Mob && failure instanceof ActionFailure deferred
                && deferred.code() == ActionFailure.Code.EVALUATION_DEFERRED) throw deferred;
            Execution execution = executions.get(player.getUUID());
            if (created && execution != null && execution.root.operationId().equals(operation)) {
                execution.failure = ActionFailure.classify(failure);
                if (execution.action != null) finishAction(player, execution, OperationRecord.Outcome.UNKNOWN, "behavior start outcome uncertain");
                else cancel(player.getUUID(), failure.getMessage());
            }
            else {
                String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                if (mayRecordRejection) {
                    var intent = invocation;
                    var rejected = new OperationRecord.Snapshot(operation, null, encounter, player.getUUID(), player.getUUID(),
                        intent.target().entity(), service.actionHost().planClock(), version, cell(player.blockPosition()), intent.target().cell(),
                        OperationRecord.Kind.PLAN, service.generation(), intent);
                    engine.rejectPlan(rejected, reason, ActionFailure.classify(failure));
                    resync(player, encounter);
                }
                if (player instanceof ServerPlayer recipient && NetworkRegistry.hasChannel(recipient.connection, ActionProtocol.Projection.TYPE.id()))
                    PacketDistributor.sendToPlayer(recipient, new ActionProtocol.Projection(service.generation(), encounter,
                        operation, ++sequence, null, false, reason, OperationRecord.Outcome.REJECTED, 0, null, ActionFailure.classify(failure)));
            }
        }
    }
    void validate(LivingEntity player, ActionIntent intent, EncounterAuthority.StateView state) {
        if (!intent.target().dimension().equals(player.level().dimension().identifier().toString()))
            throw new IllegalStateException("target dimension changed");
        var source = intent.source();
        var expectedSource = source;
        Execution active = executions.get(player.getUUID());
        boolean continuing = active != null && active.root.intent().equals(intent)
            && active.root.encounterId().equals(state.id()) && active.action != null
            && engine.pendingOperation(state.id(), active.root.operationId()) != null
            && engine.pendingOperation(state.id(), active.action) != null;

        if (source.kind() == GrantEvidence.Kind.EQUIPMENT) {
            int slot = source.item().slot();
            String expected = active != null && active.root.intent().equals(intent) && active.runningItemRevision != null
                ? active.runningItemRevision : source.item().revision();
            ActorEquipment.validate(new LiveActorContext(player), source, expected);
            expectedSource = source.withItemRevision(expected);
        }
        var actor = new LiveActorContext(player);
        var snapshot = MinecraftSnapshotCapture.capture(actor, AbilityAdapterRegistry.definitions().require(intent));
        var context = MinecraftSnapshotCapture.capture(actor, snapshot, intent, state, expectedSource,
            continuing ? ResolutionContext.Stage.CONTINUATION : ResolutionContext.Stage.PROPOSAL);
        var resolution = AbilityAdapterRegistry.definitions().resolve(context);
        AbilityAdapterRegistry.requireAccepted(resolution);
        GridCell target = targetCell(player, intent);
        if (target != null && (!player.level().hasChunkAt(pos(target))
            || !state.region().containsPoint(target.x() + .5, target.y() + .5, target.z() + .5)))
            throw new IllegalStateException("target outside loaded encounter");
        if (active != null && active.root.intent().equals(intent) && active.root.encounterId().equals(state.id()))
            active.ruleRequest = ExecutionRequest.from(active.root.operationId(), context, resolution);
    }
    private List<GridCell> path(LivingEntity player, ActionIntent intent, EncounterAuthority.StateView state, GridCell target) {
        if (canExecute(player, intent, player.position())) return List.of();
        if (target == null) throw new IllegalStateException("target required");
        if (player instanceof Mob mob) {
            var route = List.copyOf(MovementPorts.require(mob).port().plan(
                    new LiveActorContext(player), state, intent, target,
                    () -> service.requireAbilityWork(player.getUUID(), state.id(), AbilityWorkBudget.Work.PROBE)));
            if (route.isEmpty() || route.size() > 4096 || route.stream().anyMatch(cell -> !state.region().containsPoint(cell.x() + .5, cell.y(), cell.z() + .5)))
                throw new IllegalStateException("invalid movement proposal bounds");
            return route;
        }
        GridCell start = cell(player.blockPosition());
        MinecraftCellProbe worldProbe = new MinecraftCellProbe((ServerLevel)player.level(), player, state.region());
        MovementPlanner.CellProbe probe = new MovementPlanner.CellProbe() {
            public boolean canOccupy(GridCell cell) {
                service.requireAbilityWork(player.getUUID(), state.id(), AbilityWorkBudget.Work.PROBE);
                return worldProbe.canOccupy(cell);
            }
            public int traversalCost(GridCell from, GridCell to) {
                service.requireAbilityWork(player.getUUID(), state.id(), AbilityWorkBudget.Work.PROBE);
                return worldProbe.traversalCost(from, to);
            }
        };
        List<GridCell> goals = new ArrayList<>();
        int radius = AbilityAdapterRegistry.facts(intent).approachRadius();
        if (radius < 0 || radius > 16) throw new IllegalStateException("approach radius outside supported search bounds");
        for (int x = -radius; x <= radius; x++) for (int y = -1; y <= 1; y++) for (int z = -radius; z <= radius; z++) {
            if (radius == 0 && y != 0) continue;
            GridCell goal = new GridCell(target.x() + x, target.y() + y, target.z() + z);
            if (probe.canOccupy(goal) && AbilityAdapterRegistry.facts(intent).canPlanExecutionFrom(new LiveActorContext(player), intent, point(goal))) goals.add(goal);
        }
        var proposal = MovementPlanner.propose(start, Set.copyOf(goals), 128, 4096, state.region().version(), probe);
        if (proposal.cost() != Integer.MAX_VALUE && MovementPlanner.revalidate(start, proposal, state.region().version(), probe))
            return proposal.cells();
        throw new IllegalStateException("no supported path to execution position");
    }
    private boolean canExecute(LivingEntity player, ActionIntent intent, Vec3 feet) {
        return AbilityAdapterRegistry.facts(intent).canExecute(new LiveActorContext(player), intent, feet);
    }
    public boolean allowsMove(ServerPlayer player, Vec3 wanted) {
        Execution execution = executions.get(player.getUUID());
        if (execution == null) return true;
        GridCell next = waypoint(execution);
        if (next == null) return false;
        int limit=Math.min(execution.path.size()-1,execution.cursor+(execution.routeStart==null?0:2));
        for (int i=execution.cursor;i<=limit;i++) {
            Vec3 end=routePoint(execution,i), start=i==0
                ? execution.routeStart==null?point(execution.root.sourceCell()):execution.routeStart : routePoint(execution,i-1);
            if (i>execution.cursor) {
                var probe=new PreviewPathfinder(player,engine.stateView(execution.root.encounterId()).region(),
                    () -> service.requireAbilityWork(player.getUUID(),execution.root.encounterId(),AbilityWorkBudget.Work.PROBE));
                try { if (!probe.edge(start,end)) return false; } catch (ActionFailure exhausted) { return false; }
            }
            Vec3 edge=end.subtract(start);
            double t=Math.clamp(wanted.subtract(start).dot(edge)/Math.max(edge.lengthSqr(),.0001),0,1);
            if (wanted.distanceToSqr(start.add(edge.scale(t)))<=.81) return true;
        }
        return false;
    }
    public void tick() {
        selections.entrySet().removeIf(e -> !e.getValue().request().encounter().equals(service.encounterOf(e.getKey())));
        // Retry control cleanup only; immutable terminal evidence and world execution are never replayed.
        int cleanupBudget = Math.min(4, releaseFaults.size());
        for (int i = 0; i < cleanupBudget; i++) {
            var entry = releaseFaults.entrySet().iterator().next();
            Execution fault = entry.getValue();
            releaseFaults.remove(entry.getKey());
            release(fault.endpoint == null ? null : fault.endpoint.get(), fault);
            if (fault.released) releaseFaults.remove(fault.root.owner(), fault);
        }
        if (server.tickRateManager().isFrozen()) return;
        for (UUID owner : List.copyOf(containers.keySet())) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(owner);
            if (viewer == null || !mayUseContainer(viewer)) {
                var permit = containers.remove(owner);
                if (viewer != null && viewer.containerMenu.containerId == permit.menu()) viewer.closeContainer();
            }
        }
        for (Execution execution : List.copyOf(executions.values())) {
            LivingEntity player = resolve(execution.root.owner());
            if (player == null) { cancel(execution.root.owner(), "actor unavailable"); continue; }
            try {
                if (!execution.ownerInstance.equals(instance(player))) throw new IllegalStateException("plan owner instance replaced");
                if (!execution.root.encounterId().equals(service.encounterOf(player.getUUID())) || !player.isAlive())
                    throw new IllegalStateException("plan owner left");
                if (engine.pendingOperation(execution.root.encounterId(), execution.root.operationId()) == null)
                    throw new IllegalStateException("plan no longer pending");
                if (execution.targetInstance != null) {
                    var target = ((ServerLevel)player.level()).getEntity(execution.root.target());
                    if (target==null || !execution.targetInstance.equals(entityInstance(target)))
                        throw ActionFailure.source("target instance replaced");
                }
                if (execution.action != null) {
                    tickAction(player, execution);
                    continue;
                }
                if (execution.movement == null) continue;
                var intent = execution.root.intent();
                validate(player, intent, engine.stateView(execution.root.encounterId()));
                if (!Objects.equals(execution.targetCell, targetCell(player, intent))) throw new IllegalStateException("target moved");
                if (execution.routeStart != null && (!execution.routePose.equals(SelectedPositionPlanner.posture((ServerPlayer)player))
                    || execution.boundTargetPosition != null && !execution.boundTargetPosition.equals(((ServerLevel)player.level()).getEntity(intent.target().entity()).position())))
                    throw ActionFailure.source("preview movement dependencies changed");
                if (player instanceof Mob mob) {
                    service.actionHost().observeMobPlanMovement(mob, execution.movement);
                    var movementResult = engine.resultFor(execution.root.encounterId(), execution.movement);
                    if (movementResult != null && movementResult.outcome() == OperationRecord.Outcome.UNKNOWN) {
                        finish(player, execution, OperationRecord.Outcome.UNKNOWN, movementResult.reason());
                        continue;
                    }
                    if (canExecute(player, intent, player.position())) {
                        service.actionHost().finishPlanMovement(player.getUUID(), execution.movement);
                        execution.movement = null;
                        execute(player, execution);
                    } else if (!service.hasMobMoveLease(player.getUUID())) throw new IllegalStateException("navigation ended before execution position");
                    continue;
                }
                GridCell next = waypoint(execution);
                int advanced=0;
                while (next != null && advanced<3 && reachedWaypoint(player.position(),execution)) {
                    execution.cursor++;
                    advanced++;
                    next = waypoint(execution);
                }
                if (advanced>0) send(player, execution.root, next, true, "approaching");
                if (next == null) {
                    service.actionHost().finishPlanMovement(player.getUUID(), execution.movement);
                    execution.movement = null;
                    execute(player, execution);
                } else {
                    if (!service.hasPlayerMoveLease(player.getUUID())) throw new IllegalStateException("movement budget exhausted");
                    var probe = new MinecraftCellProbe((ServerLevel)player.level(), player, engine.stateView(execution.root.encounterId()).region());
                    GridCell current = cell(player.blockPosition());
                    boolean valid;
                    if (execution.routeStart != null) {
                        var preciseProbe = new PreviewPathfinder((ServerPlayer)player, engine.stateView(execution.root.encounterId()).region(),
                            () -> service.requireAbilityWork(player.getUUID(), execution.root.encounterId(), AbilityWorkBudget.Work.PROBE));
                        Vec3 from = execution.cursor == 0 ? execution.routeStart : routePoint(execution, execution.cursor - 1);
                        valid = preciseProbe.edge(from, routePoint(execution, execution.cursor));
                    } else valid = probe.canOccupy(next) && (current.equals(next) || probe.traversalCost(current, next) > 0);
                    if (!valid)
                        throw new IllegalStateException("path obstructed");
                    execution.stalled = player.position().distanceToSqr(execution.previous) < .0001 ? execution.stalled + 1 : 0;
                    execution.previous = player.position();
                    if (execution.stalled >= 100) throw new IllegalStateException("movement stalled");
                }
            } catch (RuntimeException error) {
                DebugDiagnostics.log("EXECUTION_FAILURE", () -> "op=" + execution.root.operationId()
                    + " child=" + execution.action + " phase=" + execution.phase
                    + " failure=" + ActionFailure.classify(error) + " reason=" + error.getMessage());
                execution.failure = ActionFailure.classify(error);
                if (execution.action != null) finishAction(player, execution, OperationRecord.Outcome.UNKNOWN, "behavior step outcome uncertain: " + error.getMessage());
                else cancel(execution.root.owner(), error.getMessage());
            }
        }
    }
    private void execute(LivingEntity player, Execution execution) {
        var root = execution.root;
        var intent = root.intent();
        validate(player, intent, engine.stateView(root.encounterId()));
        if (!canExecute(player, intent, player.position())) throw new IllegalStateException("execution position invalidated");
        if (!execution.blockState.isEmpty() && !execution.blockState.equals(player.level().getBlockState(pos(execution.targetCell)).toString()))
            throw new IllegalStateException("target block changed");
        var behavior = execution.behavior;
        var actor = new LiveActorContext(player);
        // Only the audited pose update precedes sampling. Arbitrary prepare hooks still
        // run after before/checkpoint, so their world effects cannot escape observation.
        if (behavior instanceof PlayerBehavior && player instanceof ServerPlayer inventory) {
            execution.aimBefore = new AimGeometry.Aim(inventory.getYRot(), inventory.getXRot());
            execution.plannedAim = PlayerBehavior.plannedAim(inventory, intent);
            PlayerBehavior.applyAim(execution.plannedAim, inventory);
        }
        execution.footprint = List.copyOf(behavior.prepareObservation(actor, execution));
        execution.observedSlots = Set.copyOf(behavior.observationSlots(actor, intent));
        if (execution.observedSlots.size() > 41 || execution.observedSlots.stream().anyMatch(slot -> slot < 0 || slot > 40))
            throw new IllegalStateException("invalid inventory observation scope");
        if (execution.footprint.size() > 8) throw new IllegalStateException("observation footprint too large");
        execution.before = AbilityObservations.capture(actor, intent, execution.footprint, execution.observedSlots);
        execution.phase = AbilityCheckpoint.Phase.PREPARE;
        checkpoint(execution);
        behavior.prepare(this, actor, execution);
        if (execution.plannedAim != null && (player.getYRot() != execution.plannedAim.yaw()
            || player.getXRot() != execution.plannedAim.pitch()))
            throw new IllegalStateException("prepared orientation changed");
        execution.phase = AbilityCheckpoint.Phase.EXECUTE;
        // Preparation is a distinct boundary: re-capture before the first irreversible native call.
        validate(player, intent, engine.stateView(root.encounterId()));
        if (!canExecute(player, intent, player.position())) throw new IllegalStateException("prepared execution position changed");
        requireRuleRequest(execution);
        checkpoint(execution);
        DebugDiagnostics.log("EXECUTE", () -> "op=" + root.operationId() + " actor=" + root.owner()
            + " instance=" + execution.ownerInstance + " " + DebugDiagnostics.intent(intent));
        behavior.start(this, actor, execution);
    }
    private void tickAction(LivingEntity player, Execution execution) {
        var intent = execution.root.intent();
        validate(player, intent, engine.stateView(execution.root.encounterId()));
        if (!canExecute(player, intent, player.position())) throw new IllegalStateException("execution target out of reach");
        var process = service.processes().require(execution.root.operationId());
        if (process.steps() >= process.definition().maximumSteps()) {
            cancel(execution.root.owner(), "PROCESS_STEP_LIMIT");
            return;
        }
        requireRuleRequest(execution);
        execution.executionSteps = Math.addExact(execution.executionSteps, 1);
        // Commit the bounded step before invoking native code, so a callback cannot outrun its budget.
        checkpoint(execution);
        execution.behavior.tick(this, new LiveActorContext(player), execution);
        if (executions.get(player.getUUID()) == execution) checkpoint(execution);
    }
    public void beginStep(LivingEntity player, Execution execution) {
        var root = execution.root;
        var snapshot = new OperationRecord.Snapshot(UUID.randomUUID(), root.operationId(), root.encounterId(), player.getUUID(), player.getUUID(),
            root.target(), service.actionHost().planClock(), engine.stateView(root.encounterId()).version(), cell(player.blockPosition()), execution.targetCell,
            execution.behavior.definition().executionKind(), service.generation());
        if (!engine.beginPlanStep(snapshot)) throw new IllegalStateException("execution step rejected");
        execution.action = snapshot.operationId();
        DebugDiagnostics.log("STEP_ACCEPTED", () -> "op=" + snapshot.operationId() + " parent=" + root.operationId()
            + " actor=" + root.owner() + " target=" + root.target() + " kind=" + snapshot.kind());
    }
    public void accept(LivingEntity player, Execution execution, String reason) {
        DebugDiagnostics.log("BEHAVIOR_ACCEPTED", () -> "op=" + execution.root.operationId()
            + " child=" + execution.action + " reason=" + reason);
        engine.publish(execution.root.encounterId(), execution.action, execution.actionStep++, OperationRecord.Outcome.ACCEPTED, reason, 0, 0, false);
        send(player, execution.root, null, true, reason);
    }
    public void finishAction(LivingEntity player, Execution execution, OperationRecord.Outcome outcome, String reason) {
        service.worldOutcomes().completeWorldOutcome(() -> finishActionWithEffects(player, execution, outcome, reason));
    }
    private void finishActionWithEffects(LivingEntity player, Execution execution, OperationRecord.Outcome outcome, String reason) {
        if (player.isAlive() && !player.isRemoved())
            service.actionHost().drainImmediateTriggers(new LiveActorContext(player), execution.root.encounterId(), execution.action);
        observe(player, execution);
        release(player, execution);
        if (!execution.observationFailure.isEmpty()) outcome = OperationRecord.Outcome.UNKNOWN;
        engine.publish(execution.root.encounterId(), execution.action, execution.actionStep++, outcome, reason, 0, 0, true);
        execution.action = null;
        if (player instanceof ServerPlayer inventory) VanillaInputPolicy.correctInventory(inventory);
        finish(player, execution, outcome, reason);
    }
    void observeMenu(ServerPlayer player, Execution execution) {
        if (player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu)
            containers.put(player.getUUID(), new ContainerPermit(execution.root.encounterId(),
                engine.stateView(execution.root.encounterId()).round(), pos(execution.targetCell), player.containerMenu.containerId));
        else if (player.containerMenu != player.inventoryMenu) player.closeContainer();
    }
    private void observe(LivingEntity player, Execution execution) {
        if (execution.before == null || player == null || execution.phase == AbilityCheckpoint.Phase.TERMINAL) return;
        try {
            execution.observed = AbilityObservations.capture(new LiveActorContext(player), execution.root.intent(), execution.footprint, execution.observedSlots);
            execution.observed = withSpawns(execution.observed, execution);
            execution.nativeObservations = NativeObservations.REGISTRY.validate(
                    execution.behavior.observeNative(new LiveActorContext(player), execution.root.operationId(), execution.root.intent()),
                    Set.of(), execution.root.operationId());
            NativeObservations.REGISTRY.validate(execution.nativeObservations,
                    execution.behavior.requiredObservations(), execution.root.operationId());
            execution.observationFailure = "";
        } catch (RuntimeException unavailable) {
            // Earlier confirmed samples remain useful, but cannot masquerade as final observation.
            execution.observationFailure = "final observation unavailable: " + unavailable.getClass().getSimpleName();
        }
        execution.phase = AbilityCheckpoint.Phase.OBSERVE;
        checkpoint(execution);
    }
    private static AbilityCheckpoint.Sample withSpawns(AbilityCheckpoint.Sample sample, Execution execution) {
        return new AbilityCheckpoint.Sample(sample.items(), sample.blocks(), sample.bodies(), execution.spawned);
    }
    /** Called immediately after an audited synchronous use hook, never by validation or discovery. */
    void confirmSourceChange(ServerPlayer player, Execution execution) {
        if (execution.root.intent().source().kind() != GrantEvidence.Kind.EQUIPMENT) return;
        String previous = execution.runningItemRevision == null ? execution.root.intent().source().item().revision() : execution.runningItemRevision;
        String current = ItemStackFingerprint.revision(player, PlayerBehavior.stack(player, execution.root.intent()));
        if (current.equals(previous)) return;
        execution.sourceChange = new AbilityCheckpoint.SourceChange(execution.executionSteps, previous, current);
        execution.runningItemRevision = current;
        execution.observed = AbilityObservations.capture(new LiveActorContext(player), execution.root.intent(), execution.footprint, execution.observedSlots);
        execution.observed = withSpawns(execution.observed, execution);
        checkpoint(execution);
    }
    private void checkpoint(Execution e) {
        if (service.processes().find(e.root.operationId()) == null)
            service.processes().open(e.root.operationId(), e.behavior.processDefinition(),
                    new ProcessState.Owner(
                            ProcessState.Ownership.ACTOR, e.root.owner()),
                    e.root.operationId(), e.root.operationId(), e.root.encounterId(), e.ownerInstance,
                    engine.movementTicksPerTurn(e.root.encounterId()), e.phase.name());
        var terminal = e.phase == AbilityCheckpoint.Phase.TERMINAL ? engine.resultFor(e.root.encounterId(), e.root.operationId()) : null;
        service.processes().observe(e.root.operationId(), e.phase.name(), e.executionSteps,
                e.phase == AbilityCheckpoint.Phase.EXECUTE,
                !e.releaseFailure.isEmpty() ? ProcessState.Release.FAILED
                        : e.released ? ProcessState.Release.RELEASED
                        : ProcessState.Release.HELD,
                terminal == null ? null : terminal.outcome(), terminal == null ? e.releaseFailure : terminal.reason());
        service.actionHost().recordAbilityCheckpoint(new AbilityCheckpoint(e.root.operationId(), e.root.encounterId(), e.root.owner(),
            e.ownerInstance, e.root.intent(), e.phase, e.behavior.executionClock(), service.actionHost().planClock(), e.executionSteps, e.movement, e.action,
            e.before, e.observed, e.sourceChange, e.observationFailure, !e.releaseFailure.isEmpty() ? AbilityCheckpoint.Release.FAILED : e.released ? AbilityCheckpoint.Release.RELEASED : AbilityCheckpoint.Release.HELD,
            e.releaseFailure, e.ruleRequest, e.nativeObservations));
    }
    private void requireRuleRequest(Execution execution) {
        var request = execution.ruleRequest;
        if (request == null || !execution.root.operationId().equals(request.operation())
                || !execution.ownerInstance.equals(request.instance())
                || !execution.root.intent().equals(request.intent())
                || !execution.behavior.definition().cost().equals(request.cost())
                || !execution.behavior.id().equals(request.executor()))
            throw new IllegalStateException("native execution lacks matching rule request");
    }
    public boolean runningIn(UUID encounter) { return executions.values().stream().anyMatch(e -> e.root.encounterId().equals(encounter)); }
    public boolean running(UUID owner) { return executions.containsKey(owner); }
    public boolean controlFault(UUID owner) { return releaseFaults.containsKey(owner); }
    void verifyContext(LiveActorContext actor, Execution execution, boolean release) {
        if (!server.isSameThread() || execution.released
            || !execution.root.owner().equals(actor.id()) || !execution.ownerInstance.equals(actor.instance())
            || executions.get(actor.id()) != execution
                && (!release || releaseFaults.get(actor.id()) != execution && releasing != execution))
            throw new IllegalStateException("execution context revoked");
        if (!release) {
            actor.verifyCurrent();
            if (!execution.root.encounterId().equals(service.encounterOf(actor.id()))
                || engine.pendingOperation(execution.root.encounterId(), execution.root.operationId()) == null
                || AbilityAdapterRegistry.resolve(execution.root.intent()) != execution.behavior)
                throw new IllegalStateException("execution binding invalidated");
        }
    }
    private void requireReleasedControl(UUID owner) {
        if (controlFault(owner)) throw new ActionFailure(ActionFailure.Code.CONTROL_FAULT,
            ActionFailure.Retry.RECOVERY_REVIEW, "previous control release requires reconciliation");
    }
    static UUID instance(LivingEntity player) {
        return entityInstance(player);
    }
    private static UUID entityInstance(net.minecraft.world.entity.Entity player) {
        // Required Entity mixin implements PresentationIdentity on both physical sides.
        if (!((Object)player instanceof PresentationIdentity identity))
            throw new IllegalStateException("required entity instance bridge missing");
        return identity.dndturn$presentationInstance();
    }
    private void release(LivingEntity player, Execution execution) {
        if (execution.released) return;
        if (execution.releaseAction == null) execution.releaseAction = execution.action;
        Execution previousRelease = releasing;
        releasing = execution;
        try {
            service.actionHost().finishPlanMovement(execution.root.owner(), execution.movement);
            LivingEntity old = execution.endpoint == null ? null : execution.endpoint.get();
            boolean retired = old == null || old.isRemoved()
                || ((ServerLevel)old.level()).getEntity(old.getUUID()) != old && server.getPlayerList().getPlayer(old.getUUID()) != old;
            if (!retired) {
                if (!execution.ownerInstance.equals(instance(old)))
                    throw new IllegalStateException("owner instance unavailable; release requires recovery review");
                execution.behavior.release(this, new LiveActorContext(old), execution);
            }
            execution.released = true;
            if (!execution.releaseFailure.isEmpty()) {
                releaseReconciliations.put(execution.root.operationId(),
                    new ReleaseReconciliation(execution.root.operationId(), execution.ownerInstance, service.actionHost().planClock(), retired));
                service.actionHost().recordReleaseReconciliation(execution.root.operationId(), retired);
            }
            releaseFaults.remove(execution.root.owner(), execution);
            if (execution.endpoint != null) execution.endpoint.clear();
        } catch (RuntimeException failure) {
            execution.releaseFailure = "control release failed: " + failure.getClass().getSimpleName();
            releaseFaults.put(execution.root.owner(), execution);
        } finally {
            releasing = previousRelease;
        }
    }
    public void cancel(UUID owner, String reason) {
        Execution execution = executions.get(owner);
        if (execution == null) return;
        if (service.processes().find(execution.root.operationId()) != null)
            service.processes().requestCancel(execution.root.operationId());
        LivingEntity player = resolve(owner);
        if (player != null && execution.ownerInstance.equals(instance(player))) try { execution.behavior.requestCancel(this, new LiveActorContext(player), execution); }
        catch (RuntimeException failure) {
            execution.releaseFailure = "cancel request failed: " + failure.getClass().getSimpleName();
            releaseFaults.put(owner, execution);
        }
        release(player, execution);
        if (execution.action != null && engine.encounterIds().contains(execution.root.encounterId())
            && engine.pendingOperation(execution.root.encounterId(), execution.action) != null) {
            engine.publish(execution.root.encounterId(), execution.action, execution.actionStep++, OperationRecord.Outcome.INTERRUPTED,
                reason == null ? "interrupted" : reason, 0, 0, true);
            execution.action = null;
        }
        finish(player, execution, OperationRecord.Outcome.INTERRUPTED, reason == null ? "interrupted" : reason);
    }
    void finish(LivingEntity player, Execution execution, OperationRecord.Outcome outcome, String reason) {
        var root = execution.root;
        observe(player, execution);
        if (!execution.observationFailure.isEmpty()) outcome = OperationRecord.Outcome.UNKNOWN;
        release(player, execution);
        var conclusion = new ExecutionConclusion(outcome, execution.releaseFailure.isEmpty()
            ? ExecutionConclusion.Release.RELEASED : ExecutionConclusion.Release.FAILED, execution.releaseFailure);
        // Unknown world effects always require reconciliation, even when validation also failed.
        var failure = outcome == OperationRecord.Outcome.UNKNOWN ? ActionFailure.Details.UNKNOWN
            : !execution.releaseFailure.isEmpty()
                ? new ActionFailure.Details(ActionFailure.Code.CONTROL_FAULT, ActionFailure.Retry.RECOVERY_REVIEW)
            : execution.failure.code() != ActionFailure.Code.NONE ? execution.failure
            : outcome == OperationRecord.Outcome.REJECTED ? ActionFailure.Details.REJECTED : ActionFailure.Details.NONE;
        if (engine.encounterIds().contains(root.encounterId()) && engine.pendingOperation(root.encounterId(), root.operationId()) != null)
            engine.publish(root.encounterId(), root.operationId(), 0, conclusion.outcome(), reason, 0, 0, true, null, conclusion, failure);
        execution.phase = AbilityCheckpoint.Phase.TERMINAL;
        checkpoint(execution);
        executions.remove(root.owner());
        var terminal = engine.resultFor(root.encounterId(), root.operationId());
        var observedOutcome = outcome;
        DebugDiagnostics.log("TERMINAL", () -> "op=" + root.operationId() + " encounter=" + root.encounterId()
            + " actor=" + root.owner() + " worldOutcome=" + observedOutcome + " release=" + conclusion.release()
            + " outcome=" + (terminal == null ? "UNPUBLISHED" : terminal.outcome())
            + " failure=" + failure + " reason=" + (terminal == null ? "no authoritative result" : terminal.reason())
            + " movement=" + (terminal == null ? 0 : terminal.actualMovementTicks())
            + " damage=" + (terminal == null ? 0 : terminal.actualDamage()));
        if (terminal != null) reason = terminal.reason();
        if (player != null) { resync(player, root.encounterId()); send(player, root, null, false, reason); }
    }
    void send(LivingEntity player, OperationRecord.Snapshot root, GridCell point, boolean running, String reason) {
        send(player, root.encounterId(), root.operationId(), point, running, reason);
    }
    private void send(LivingEntity player, UUID encounter, UUID operation, GridCell point, boolean running, String reason) {
        if (!(player instanceof ServerPlayer recipient) || !NetworkRegistry.hasChannel(recipient.connection, ActionProtocol.Projection.TYPE.id())) return;
        var result = running ? null : engine.resultFor(encounter, operation);
        var message = result != null && result.snapshot().kind() == OperationRecord.Kind.PLAN
            ? ActionProtocol.Projection.terminal(service.generation(), ++sequence, result)
            : new ActionProtocol.Projection(service.generation(), encounter, operation, ++sequence, point, running, reason,
                running ? null : OperationRecord.Outcome.REJECTED, 0);
        var execution = executions.get(player.getUUID());
        if (running && execution != null && execution.root.operationId().equals(operation) && execution.routeStart != null)
            message = new ActionProtocol.Projection(message.generation(), message.encounter(), message.operation(), message.sequence(),
                message.waypoint(), true, message.reason(), null, message.version(), null, message.failure(), execution.preciseRoute, execution.cursor);
        PacketDistributor.sendToPlayer(recipient, message);
    }
    static GridCell targetCell(LivingEntity player, ActionIntent intent) {
        if (intent.target().kind() == ActionIntent.TargetKind.ENTITY) {
            var target = ((ServerLevel)player.level()).getEntity(intent.target().entity());
            if (target == null) throw new IllegalStateException("target unavailable");
            return cell(target.blockPosition());
        }
        return intent.target().cell();
    }
    private LivingEntity resolve(UUID owner) {
        for (var level : server.getAllLevels()) if (level.getEntity(owner) instanceof LivingEntity living) return living;
        return null;
    }
    private void resync(LivingEntity body, UUID encounter) {
        if (body instanceof ServerPlayer player) service.resyncMember(player, encounter);
    }
    private static GridCell waypoint(Execution execution) { return execution.cursor < execution.path.size() ? execution.path.get(execution.cursor) : null; }
    private static boolean reachedWaypoint(Vec3 position, Execution e) {
        Vec3 end=routePoint(e,e.cursor);
        boolean last=e.cursor==e.path.size()-1;
        if (position.distanceToSqr(end)<(e.routeStart==null?.16:last?.0064:.09)) return true;
        if (last || e.routeStart==null) return false;
        Vec3 start=e.cursor==0?e.routeStart:routePoint(e,e.cursor-1), edge=end.subtract(start);
        double t=position.subtract(start).dot(edge)/Math.max(edge.lengthSqr(),.0001);
        return t>=1 && t<=2 && position.distanceToSqr(start.add(edge.scale(t)))<.09;
    }
    private static Vec3 routePoint(Execution e, int index) {
        return e.routeStart == null ? point(e.path.get(index)) : PreviewPathfinder.vector(e.preciseRoute.get(index).feet());
    }
    static GridCell cell(BlockPos pos) { return new GridCell(pos.getX(), pos.getY(), pos.getZ()); }
    static BlockPos pos(GridCell cell) { return new BlockPos(cell.x(), cell.y(), cell.z()); }
    static Vec3 point(GridCell cell) { return new Vec3(cell.x() + .5, cell.y(), cell.z() + .5); }
}
