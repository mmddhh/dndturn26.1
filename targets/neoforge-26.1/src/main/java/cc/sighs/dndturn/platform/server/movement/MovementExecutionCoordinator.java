package cc.sighs.dndturn.platform.server.movement;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.platform.server.ai.MovementPorts.Driver;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.EncounterRegion;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import cc.sighs.dndturn.platform.projection.PresentationState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Owns movement leases, observed displacement, force attribution and movement receipts. */
public final class MovementExecutionCoordinator {
    public void finishPlanMovement(ServerPlayer player) { finishPlayerMove(player); }
    public void finishPlanMovement(UUID owner) { var lease = playerMoves.get(owner); if (lease != null) closePlayerMove(lease); }
    public void finishPlanMovement(UUID owner, UUID operation) {
        var lease = playerMoves.get(owner);
        if (operation != null && lease != null && operation.equals(lease.operationId)) closePlayerMove(lease);
        var mobLease = mobMoves.get(owner);
        if (operation != null && mobLease != null && operation.equals(mobLease.operationId)) closeMobMove(mobLease, "plan movement finished");
    }
    /** Navigation is a child of an admitted PLAN, with the existing observed movement ledger. */
    public void beginMobPlanMovement(Mob mob, OperationRecord.Snapshot root, UUID operation, GridCell goal) {
        if (!(mob.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || level.getEntity(mob.getUUID()) != mob || !mob.isAlive())
            throw new IllegalStateException("actor is not the current loaded server instance");
        if (!root.owner().equals(mob.getUUID()) || root.kind() != OperationRecord.Kind.PLAN
            || !root.equals(engine.pendingOperation(root.encounterId(), root.operationId())))
            throw new IllegalStateException("admitted owner plan required");
        if (mob.isNoAi() || mob.isPassenger() || mob.hasControllingPassenger()
            || !host.supportsMovement(mob))
            throw new IllegalStateException("Mob ground movement port unavailable");
        var snapshot = new OperationRecord.Snapshot(operation, root.operationId(), root.encounterId(), mob.getUUID(), mob.getUUID(),
            null, host.clock(), engine.stateView(root.encounterId()).version(), root.sourceCell(), goal,
            OperationRecord.Kind.MOVE, generation);
        if (!engine.beginPlanStep(snapshot)) throw new IllegalStateException("Mob plan movement rejected");
        var lease = new MobMoveLease(root.encounterId(), operation, mob);
        mobMoves.put(mob.getUUID(), lease);
        try {
            lease.driver = Objects.requireNonNull(host.startMovement(mob, goal), "movement driver");
            lease.pathStarted = true;
        } catch (RuntimeException failure) {
            closeMobMove(lease, "movement port could not start");
            throw failure;
        }
    }
    /** Observe one vanilla body tick; the parent plan, never this driver, decides what happens next. */
    public void observeMobPlanMovement(Mob mob, UUID operation) {
        var lease = mobMoves.get(mob.getUUID());
        if (lease == null || !lease.operationId.equals(operation)) return;
        if (!activeMobLease(lease)) { revokeMobLease(lease); return; }
        if (lease.ticked) {
            lease.ticked = false;
            var observation = settleMobDisplacement(mob, lease, engine.stateView(lease.encounterId), lease.driver != null && lease.driver.owns(mob));
            if (observation == MobObservation.NO_DISPLACEMENT) lease.stalled++;
        }
        if (lease.budgetBlocked || lease.stalled >= 5 || lease.driver == null || !lease.driver.owns(mob) || lease.driver.done(mob))
            closeMobMove(lease, "navigation completed or interrupted");
    }
    public void beginPlanMovement(ServerPlayer player, UUID parent, UUID operation) {
        UUID id = engine.encounterOf(player.getUUID());
        var snapshot = new OperationRecord.Snapshot(operation, parent, id, player.getUUID(), player.getUUID(), null,
            host.clock(), engine.stateView(id).version(), null, null, OperationRecord.Kind.MOVE, generation);
        if (unsupportedPlayerMovement(player) || !engine.beginPlanStep(snapshot)) throw new IllegalStateException("plan movement rejected");
        playerMoves.put(player.getUUID(), new PlayerMoveLease(id, operation, player.getUUID()));
        host.sync(engine.stateView(id)); host.syncBodyStateTransitions();
    }
    private final Map<UUID, PlayerMoveLease> playerMoves = new HashMap<>();
    /** Short-lived observed force events; vanilla's impulse grace is not proof of movement. */
    private final Map<UUID, ForcedMovementEvidence> forcedMovements = new HashMap<>();
    private final Map<UUID, MobMoveLease> mobMoves = new HashMap<>();
    private static final class PlayerMoveLease {
        final UUID encounterId;
        final UUID operationId;
        final UUID playerId;
        int nextStep;
        int spentTicks;
        int observedSteps;
        long observedTick = Long.MIN_VALUE;
        boolean moved;
        boolean mixedTick;
        String forcedCause;
        int baseCost;
        boolean jumped;
        double presentationHorizontal;

        PlayerMoveLease(UUID encounterId, UUID operationId, UUID playerId) {
            this.encounterId = encounterId;
            this.operationId = operationId;
            this.playerId = playerId;
        }
    }

    private static final class ForcedMovementEvidence {
        final UUID eventId;
        final UUID encounterId;
        final UUID sourceOperation;
        final String cause;
        final long sourceTick;
        long classifiedTick = Long.MIN_VALUE;

        ForcedMovementEvidence(UUID eventId, UUID encounterId, UUID sourceOperation,
                               String cause, long sourceTick) {
            this.eventId = eventId;
            this.encounterId = encounterId;
            this.sourceOperation = sourceOperation;
            this.cause = cause;
            this.sourceTick = sourceTick;
        }

        boolean available(long tick) {
            return tick >= sourceTick && tick <= sourceTick + 1
                && (classifiedTick == Long.MIN_VALUE || classifiedTick == tick);
        }

        String description() {
            return cause + " event=" + eventId
                + (sourceOperation == null ? "" : " sourceOperation=" + sourceOperation);
        }
    }

    private static final class MobMoveLease {
        final UUID encounterId;
        final UUID operationId;
        final UUID mobId;
        final UUID ownerInstance;
        Vec3 previous;
        boolean previousGrounded;
        boolean previousWater;
        int step;
        int spent;
        int stalled;
        boolean pathStarted;
        boolean ticked;
        boolean budgetBlocked;
        int authorizedCost;
        boolean underreserveNextExpensiveStepForGameTest;
        Driver driver;

        MobMoveLease(UUID encounterId, UUID operationId, Mob mob) {
            this.encounterId = encounterId;
            this.operationId = operationId;
            this.mobId = mob.getUUID();
            this.ownerInstance = ((PresentationIdentity)mob).dndturn$presentationInstance();
            this.previous = mob.position();
            this.previousGrounded = mob.onGround();
            this.previousWater = mob.isInWater();
        }
    }

    private enum MobObservation { NO_DISPLACEMENT, DISPLACED, TERMINAL_COMPLETED, TERMINAL_UNKNOWN }
    public boolean hasPlayerMoveLease(UUID playerId) { return playerMoves.containsKey(playerId); }
    public boolean hasMobMoveLease(UUID mobId) {
        MobMoveLease lease = mobMoves.get(mobId);
        return lease != null && activeMobLease(lease);
    }

    /** Fault injection after path inspection; the body still runs through vanilla's real tick. */
    public void underreserveNextExpensiveMobStepForGameTest(UUID mobId) {
        requireThread();
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer))
            throw new IllegalStateException("Mob reservation fault injection requires GameTestServer");
        MobMoveLease lease = mobMoves.get(mobId);
        if (lease == null || !activeMobLease(lease))
            throw new IllegalStateException("no active Mob movement lease");
        lease.underreserveNextExpensiveStepForGameTest = true;
    }

    private boolean activeMobLease(MobMoveLease lease) {
        if (mobMoves.get(lease.mobId) != lease
            || !engine.encounterIds().contains(lease.encounterId)
            || !lease.encounterId.equals(engine.encounterOf(lease.mobId))) return false;
        Entity current = resolve(lease.mobId);
        if (!(current instanceof Mob) || !lease.ownerInstance.equals(((PresentationIdentity)current).dndturn$presentationInstance())) return false;
        StateView state = engine.stateView(lease.encounterId);
        return (state.phase() == EncounterPhase.ACTIVE || state.phase() == EncounterPhase.CANDIDATE)
            && lease.mobId.equals(state.current())
            && state.members().containsKey(lease.mobId)
            && engine.pendingOperation(lease.encounterId, lease.operationId) != null;
    }

    private void revokeMobLease(MobMoveLease lease) {
        if (mobMoves.remove(lease.mobId, lease)) {
            stopMobNavigation(lease);
        }
    }
    /** A completed vanilla knockback changed velocity; it can explain at most one movement tick. */
    public void noteKnockbackImpulse(LivingEntity target, UUID sourceOperation) {
        requireThread();
        UUID encounterId = engine.encounterOf(target.getUUID());
        if (encounterId == null) return;
        long tick = host.clock();
        forcedMovements.put(target.getUUID(), new ForcedMovementEvidence(
            UUID.randomUUID(), encounterId, sourceOperation, "vanilla knockback impulse", tick));
        PlayerMoveLease lease = playerMoves.get(target.getUUID());
        if (lease != null && lease.moved && lease.observedTick == tick) {
            lease.mixedTick = true;
            lease.forcedCause = forcedMovements.get(target.getUUID()).description();
        }
    }

    private ForcedMovementEvidence availableForce(UUID entityId, long tick) {
        ForcedMovementEvidence evidence = forcedMovements.get(entityId);
        return evidence != null && evidence.encounterId.equals(engine.encounterOf(entityId))
            && evidence.available(tick) ? evidence : null;
    }
    public void noteMobTick(UUID mobId) {
        MobMoveLease lease = mobMoves.get(mobId);
        if (lease != null) {
            if (activeMobLease(lease)) lease.ticked = true;
            else revokeMobLease(lease);
        }
    }

    public OperationRecord.Result beginPlayerMove(ServerPlayer actor, UUID operationId,
                                                                 long expectedVersion) {
        requireThread();
        Objects.requireNonNull(operationId);
        UUID encounterId = engine.encounterOf(actor.getUUID());
        if (encounterId == null) throw new IllegalStateException("player is not a member");
        OperationRecord.Result previous = engine.resultFor(encounterId, operationId);
        if (previous != null) {
            if (previous.snapshot().kind() != OperationRecord.Kind.MOVE
                || !previous.snapshot().owner().equals(actor.getUUID())
                || previous.snapshot().encounterVersion() != expectedVersion)
                throw new IllegalStateException("operation ID payload conflict");
            return previous;
        }
        OperationRecord.Snapshot pending = engine.pendingOperation(encounterId, operationId);
        if (pending != null) {
            if (pending.kind() != OperationRecord.Kind.MOVE
                || !pending.owner().equals(actor.getUUID())
                || pending.encounterVersion() != expectedVersion)
                throw new IllegalStateException("operation ID payload conflict");
            return null;
        }
        StateView state = engine.stateView(encounterId);
        if (state.version() != expectedVersion || (state.phase() != EncounterPhase.ACTIVE && state.phase() != EncounterPhase.CANDIDATE)
            || !actor.getUUID().equals(state.current()) || unsupportedPlayerMovement(actor)
            || !state.region().dimension().equals(actor.level().dimension().identifier().toString())
            || state.members().get(actor.getUUID()).movementTicks() < 1
            || playerMoves.containsKey(actor.getUUID()))
            throw new IllegalStateException("player movement is not authorized in this phase");
        BlockPos pos = actor.blockPosition();
        OperationRecord.Snapshot snapshot = host.operationSnapshot(operationId, null,
            encounterId, actor.getUUID(), actor.getUUID(), null, host.clock(),
            expectedVersion, new GridCell(pos.getX(), pos.getY(), pos.getZ()), null,
            OperationRecord.Kind.MOVE);
        if (!engine.beginOperation(snapshot)) throw new IllegalStateException("movement operation rejected");
        playerMoves.put(actor.getUUID(), new PlayerMoveLease(encounterId, operationId, actor.getUUID()));
        host.sync(engine.stateView(encounterId));
        host.syncBodyStateTransitions();
        return null; // Accepted operation has no terminal result until observed movement is settled.
    }

    /** Check the proposed packet before vanilla movement can consume a lease budget. */
    public boolean preparePlayerMovePacket(ServerPlayer actor, ServerboundMovePlayerPacket packet,
                                           boolean correctionPending) {
        requireThread();
        PlayerMoveLease lease = playerMoves.get(actor.getUUID());
        if (lease == null || !packet.hasPosition()) return true;
        if (unsupportedPlayerMovement(actor)) {
            if (lease.moved && lease.observedTick == host.clock())
                lease.mixedTick = true;
            closePlayerMove(lease, "special movement mode has no active movement permit");
            return correctionPending || actor.isChangingDimension();
        }
        long tick = host.clock();
        if (lease.moved && lease.observedTick != tick) settleMoveTick(lease);
        if (playerMoves.get(actor.getUUID()) != lease) return false;
        Vec3 before = actor.position();
        double wantedX = packet.getX(before.x), wantedY = packet.getY(before.y), wantedZ = packet.getZ(before.z);
        if (!Double.isFinite(wantedX) || !Double.isFinite(wantedY) || !Double.isFinite(wantedZ))
            return true; // Vanilla rejects invalid packet coordinates before any world movement.
        if (!host.allowsMove(actor, new Vec3(wantedX, wantedY, wantedZ))) return false;
        boolean horizontal = Math.abs(wantedX - before.x) > 1.0E-5
                || Math.abs(wantedZ - before.z) > 1.0E-5;
        boolean rising = actor.onGround() && wantedY > before.y + 0.05;
        boolean verticalWater = (actor.isInWater() || actor.getFluidHeight(FluidTags.WATER) > 0)
            && Math.abs(wantedY - before.y) > 1.0E-5;
        if (correctionPending || actor.isChangingDimension()
            || availableForce(actor.getUUID(), tick) != null
            || (!horizontal && !rising && !verticalWater)) return true;
        AABB proposedBody = actor.getBoundingBox().move(
            wantedX - before.x, wantedY - before.y, wantedZ - before.z);
        int proposedWater = waterInLoadedBody(actor.level(), proposedBody);
        if (proposedWater < 0) {
            closePlayerMove(lease);
            return false;
        }
        boolean water = actor.isInWater() || proposedWater > 0;
        int required = Math.max(lease.baseCost, water ? 2 : 1)
            + (lease.jumped || rising ? 1 : 0);
        if (engine.stateView(lease.encounterId).members().get(lease.playerId).movementTicks() >= required)
            return true;
        closePlayerMove(lease);
        return false;
    }

    public void observePlayerMovePacket(ServerPlayer actor, Vec3 before, boolean hasPosition,
                                        boolean groundedBefore, boolean waterBefore,
                                        boolean correctionPending) {
        requireThread();
        PlayerMoveLease lease = playerMoves.get(actor.getUUID());
        if (lease != null && unsupportedPlayerMovement(actor)) {
            if (lease.moved && lease.observedTick == host.clock())
                lease.mixedTick = true;
            closePlayerMove(lease, "special movement mode has no active movement permit");
            return;
        }
        if (lease == null || !hasPosition || before.distanceToSqr(actor.position()) <= 1.0E-10) return;
        long tick = host.clock();
        if (lease.moved && lease.observedTick != tick) settleMoveTick(lease);
        if (playerMoves.get(actor.getUUID()) != lease) return;
        ForcedMovementEvidence force = availableForce(actor.getUUID(), tick);
        if (correctionPending || force != null || actor.isChangingDimension()) {
            // A position packet cannot separate intentional input from this forced move.
            // If an active packet also moved the player this tick, neither contribution is charged.
            if (lease.observedTick != tick) lease.mixedTick = false;
            lease.observedTick = tick;
            lease.mixedTick = true;
            lease.moved = true;
            lease.forcedCause = force == null
                ? correctionPending ? "vanilla pending teleport correction" : "dimension correction"
                : force.description();
            if (force != null) force.classifiedTick = tick;
            return;
        }
        boolean horizontalDisplacement = Math.abs(actor.getX() - before.x) > 1.0E-5
            || Math.abs(actor.getZ() - before.z) > 1.0E-5;
        boolean jumped = groundedBefore && !actor.onGround()
            && actor.getY() > before.y + 0.05;
        boolean verticalWater = (waterBefore || actor.isInWater())
            && Math.abs(actor.getY() - before.y) > 1.0E-5;
        if (!jumped && !verticalWater && !horizontalDisplacement) return;
        if (lease.observedTick != tick) lease.mixedTick = false;
        if (lease.observedTick != tick) lease.forcedCause = null;
        lease.observedTick = tick;
        lease.moved = true;
        lease.presentationHorizontal += actor.position().subtract(before).horizontalDistance();
        lease.baseCost = Math.max(lease.baseCost, waterBefore || actor.isInWater()
            || waterInLoadedBody(actor.level(), actor.getBoundingBox()) != 0 ? 2 : 1);
        lease.jumped |= jumped;
    }

    public static boolean unsupportedPlayerMovement(ServerPlayer player) {
        return player.isPassenger() || player.isFallFlying() || player.isAutoSpinAttack()
            || player.getAbilities().flying;
    }

    private static int observedMoveCost(boolean water, boolean jumped) {
        return (water ? 2 : 1) + (jumped ? 1 : 0);
    }

    /** Settle only server ticks that actually contained accepted vanilla displacement. */
    public void finishMovementTicks() {
        requireThread();
        for (PlayerMoveLease lease : Set.copyOf(playerMoves.values())) {
            ServerPlayer actor = server.getPlayerList().getPlayer(lease.playerId);
            if (actor != null && unsupportedPlayerMovement(actor)) {
                // This runs once at the end of the server tick, after Level advanced gameTime.
                // An unsettled move here belongs to this tick even if the level clock changed.
                if (lease.moved) lease.mixedTick = true;
                closePlayerMove(lease, "special movement mode has no active movement permit");
                continue;
            }
            if (lease.moved) settleMoveTick(lease);
        }
        long tick = host.clock();
        forcedMovements.entrySet().removeIf(entry -> entry.getValue().sourceTick + 1 < tick);
    }

    /** -1 means the body cannot be checked without loading terrain; never authorize it. */
    private static int waterInLoadedBody(ServerLevel level, AABB body) {
        BlockPos min = BlockPos.containing(body.minX, body.minY, body.minZ);
        BlockPos max = BlockPos.containing(body.maxX - 1.0E-7,
            body.maxY - 1.0E-7, body.maxZ - 1.0E-7);
        long width = (long) max.getX() - min.getX() + 1;
        long height = (long) max.getY() - min.getY() + 1;
        long depth = (long) max.getZ() - min.getZ() + 1;
        if (width < 1 || width > 4 || height < 1 || height > 4
            || depth < 1 || depth > 4 || width * height * depth > 32) return -1;
        boolean water = false;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) return -1;
            water |= level.getFluidState(pos).is(FluidTags.WATER);
        }
        return water ? 1 : 0;
    }

    /** Advances only a leased Mob body. Decision and navigation remain separate. */
    private MobObservation settleMobDisplacement(Mob mob, MobMoveLease lease,
                                                  StateView state, boolean navigationOwned) {
        Vec3 before = lease.previous;
        boolean groundedBefore = lease.previousGrounded;
        boolean waterBefore = lease.previousWater;
        boolean displaced = before.distanceToSqr(mob.position()) > 1.0E-10;
        lease.previous = mob.position();
        lease.previousGrounded = mob.onGround();
        lease.previousWater = mob.isInWater();
        if (!engine.encounterIds().contains(lease.encounterId)
            || engine.pendingOperation(lease.encounterId, lease.operationId) == null) {
            revokeMobLease(lease);
            return MobObservation.TERMINAL_UNKNOWN;
        }
        if (!displaced) return MobObservation.NO_DISPLACEMENT;
        lease.stalled = 0;
        boolean horizontal = Math.abs(before.x - mob.getX()) > 1.0E-5
            || Math.abs(before.z - mob.getZ()) > 1.0E-5;
        boolean jumped = groundedBefore && !mob.onGround() && mob.getY() > before.y + 0.05;
        ForcedMovementEvidence force = availableForce(mob.getUUID(), host.clock());
        boolean active = navigationOwned && force == null
            && (horizontal || jumped || ((waterBefore || mob.isInWater()) && mob.isJumping()));
        if (force != null) force.classifiedTick = host.clock();
        int cost = active ? observedMoveCost(waterBefore || mob.isInWater(), jumped) : 0;
        if (cost >= 2 && lease.underreserveNextExpensiveStepForGameTest
            && lease.authorizedCost < cost)
            lease.underreserveNextExpensiveStepForGameTest = false;
        int remaining = state.members().get(mob.getUUID()).movementTicks();
        if (cost > remaining || navigationOwned && cost > lease.authorizedCost) {
            try {
                engine.publish(lease.encounterId, lease.operationId, lease.step,
                    OperationRecord.Outcome.UNKNOWN,
                    "navigation displaced beyond preauthorized movement mode: delta="
                        + mob.position().subtract(before) + " reserved=" + lease.authorizedCost
                        + " observed=" + cost + " remaining=" + remaining
                        + " pathOwned=" + navigationOwned,
                    0, 0, true);
                lease.step++;
            } finally {
                revokeMobLease(lease);
            }
            host.sync(engine.stateView(lease.encounterId));
            return MobObservation.TERMINAL_UNKNOWN;
        }
        boolean terminal = cost > 0 && remaining == cost;
        engine.publish(lease.encounterId, lease.operationId, lease.step,
            terminal ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.ACCEPTED,
            active ? "vanilla Mob navigation displacement, preauthorized=" + lease.authorizedCost
                : "passive Mob displacement" + (force == null ? "" : " " + force.description()),
            cost, 0, terminal);
        host.movement(mob, new PresentationState.Movement(lease.operationId, lease.step,
            ++presentationMovementSequence, active ? PresentationState.Motion.ACTIVE
                : force != null ? PresentationState.Motion.FORCED : PresentationState.Motion.PASSIVE,
            mob.position().subtract(before).horizontalDistance()));
        lease.step++;
        lease.spent += cost;
        if (terminal) revokeMobLease(lease);
        host.sync(engine.stateView(lease.encounterId));
        return terminal ? MobObservation.TERMINAL_COMPLETED : MobObservation.DISPLACED;
    }
    private void stopMobNavigation(MobMoveLease lease) {
        Entity entity = resolve(lease.mobId);
        if (entity instanceof Mob mob && lease.ownerInstance.equals(((PresentationIdentity)mob).dndturn$presentationInstance())
                && lease.driver != null) lease.driver.release(mob);
        lease.driver = null;
    }

    private void closeMobMove(MobMoveLease lease, String reason) {
        if (mobMoves.get(lease.mobId) != lease) return;
        Entity entity = resolve(lease.mobId);
        if (lease.ticked && entity instanceof Mob mob && lease.ownerInstance.equals(((PresentationIdentity)mob).dndturn$presentationInstance())
            && engine.encounterIds().contains(lease.encounterId)) {
            lease.ticked = false;
            settleMobDisplacement(mob, lease, engine.stateView(lease.encounterId),
                lease.pathStarted && lease.driver != null && lease.driver.owns(mob));
        }
        if (mobMoves.get(lease.mobId) != lease) return;
        if (engine.encounterIds().contains(lease.encounterId)
            && engine.pendingOperation(lease.encounterId, lease.operationId) != null)
            engine.publish(lease.encounterId, lease.operationId, lease.step,
                lease.spent == 0 ? OperationRecord.Outcome.REJECTED : OperationRecord.Outcome.COMPLETED,
                reason, 0, 0, true);
        revokeMobLease(lease);
    }

    /** Space controls leases; membership survives crossing the fixed region boundary. */
    public OperationRecord.Result finishPlayerMove(ServerPlayer actor) {
        requireThread();
        PlayerMoveLease lease = playerMoves.get(actor.getUUID());
        if (lease == null) throw new IllegalStateException("no active player movement operation");
        return closePlayerMove(lease);
    }

    /** Settle the observed tick before releasing the operation, including lifecycle exits. */
    private OperationRecord.Result closePlayerMove(PlayerMoveLease lease) {
        return closePlayerMove(lease, "movement finished");
    }

    private OperationRecord.Result closePlayerMove(PlayerMoveLease lease, String reason) {
        OperationRecord.Result terminal = engine.resultFor(lease.encounterId, lease.operationId);
        if (terminal != null) { playerMoves.remove(lease.playerId, lease); return terminal; }
        if (lease.moved) settleMoveTick(lease);
        if (playerMoves.get(lease.playerId) != lease)
            return engine.resultFor(lease.encounterId, lease.operationId);
        OperationRecord.Result result = engine.publish(lease.encounterId, lease.operationId,
            lease.nextStep, lease.observedSteps == 0 ? OperationRecord.Outcome.REJECTED
                : OperationRecord.Outcome.COMPLETED,
            reason + "; observed displacement ticks=" + lease.observedSteps
                + "; movement cost=" + lease.spentTicks,
            0, 0, true);
        playerMoves.remove(lease.playerId);
        host.reanchor(lease.encounterId);
        host.sync(engine.stateView(lease.encounterId));
        host.syncBodyStateTransitions();
        return result;
    }

    private void settleMoveTick(PlayerMoveLease lease) {
        if (!lease.moved || playerMoves.get(lease.playerId) != lease) return;
        StateView state = engine.stateView(lease.encounterId);
        int remaining = state.members().get(lease.playerId).movementTicks();
        int cost = lease.mixedTick ? 0 : lease.baseCost + (lease.jumped ? 1 : 0);
        if (cost > remaining) throw new IllegalStateException("observed movement exceeded authorized budget");
        boolean terminal = remaining == cost;
        engine.publish(lease.encounterId, lease.operationId, lease.nextStep,
            terminal ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.ACCEPTED,
            (lease.mixedTick ? "mixed active/forced displacement, contribution unproven: "
                + lease.forcedCause + " at server tick "
                : "vanilla player displacement at server tick ") + lease.observedTick,
            cost, 0, terminal);
        var presentationActor = server.getPlayerList().getPlayer(lease.playerId);
        if (presentationActor != null) host.movement(presentationActor, new PresentationState.Movement(
            lease.operationId, lease.nextStep, ++presentationMovementSequence,
            lease.mixedTick ? PresentationState.Motion.MIXED : PresentationState.Motion.ACTIVE,
            lease.mixedTick ? 0 : lease.presentationHorizontal));
        lease.presentationHorizontal = 0;
        lease.nextStep++;
        lease.observedSteps++;
        lease.spentTicks += cost;
        lease.moved = false;
        lease.mixedTick = false;
        lease.forcedCause = null;
        lease.baseCost = 0;
        lease.jumped = false;
        if (terminal) playerMoves.remove(lease.playerId);
        host.sync(engine.stateView(lease.encounterId));
        if (terminal) host.syncBodyStateTransitions();
    }

    public void reconcileMobMovementStep(Entity entity) {
        MobMoveLease lease = mobMoves.get(entity.getUUID());
        if (lease != null && !activeMobLease(lease)) {
            revokeMobLease(lease);
            lease = null;
        }
        if (lease == null) return;
        if (lease.pathStarted && entity instanceof Mob mob) {
            int remaining = engine.stateView(lease.encounterId).members().get(entity.getUUID()).movementTicks();
            // Inspect the next vanilla navigation/control proposal and its loaded collision
            // corridor. Flat dry steps cost one; water and possible jumps need their own budget.
            int possibleCost = nextMobStepCost(mob, lease);
            if (possibleCost < 0 || remaining < possibleCost) {
                lease.budgetBlocked = true;
                closeMobMove(lease, "navigation budget or corridor invalidated");
                return;
            }
            lease.authorizedCost = possibleCost;
            if (lease.underreserveNextExpensiveStepForGameTest && possibleCost >= 2) {
                lease.authorizedCost = 1;
            }
        }
    }

    /** A provider's conservative budget estimate never replaces observed rule charges. */
    private static int nextMobStepCost(Mob mob, MobMoveLease lease) {
        if (lease.driver == null || !lease.driver.owns(mob)) return -1;
        int budget = lease.driver.nextStepBudget(mob);
        return budget >= 1 && budget <= 3 ? budget : -1;
    }

    private long presentationMovementSequence;

    /** One bounded navigation owner; it cannot grant rules or body simulation. */
    public interface Host {
        boolean supportsMovement(Mob actor);
        Driver startMovement(Mob actor, GridCell goal);
        long clock();
        boolean allowsMove(ServerPlayer actor, Vec3 position);
        void sync(StateView state);
        void syncBodyStateTransitions();
        void reanchor(UUID encounter);
        boolean isEntityInsidePausedRegion(Entity entity);
        void movement(Entity actor, PresentationState.Movement movement);
        OperationRecord.Snapshot operationSnapshot(UUID operationId, UUID parentId, UUID encounterId,
                UUID owner, UUID source, UUID target, long tick, long version, GridCell sourceCell,
                GridCell targetCell, OperationRecord.Kind kind);
    }
    private final MinecraftServer server;
    private final EncounterAuthority engine;
    private final UUID generation;
    private final Host host;
    public MovementExecutionCoordinator(MinecraftServer server, EncounterAuthority engine, UUID generation, Host host) {
        this.server = server; this.engine = engine; this.generation = generation; this.host = host;
    }
    private void requireThread() {
        if (!server.isSameThread()) throw new IllegalStateException("movement requires server thread");
    }
    private Entity resolve(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }
    private static EncounterRegion.Point pointOf(Entity entity) {
        Vec3 center = entity.getBoundingBox().getCenter();
        return new EncounterRegion.Point(center.x, center.y, center.z);
    }
    public UUID mobOperation(UUID owner) {
        var lease = mobMoves.get(owner); return lease == null ? null : lease.operationId;
    }
    public UUID playerOperation(UUID owner) {
        var lease = playerMoves.get(owner); return lease == null ? null : lease.operationId;
    }
    public void finishMember(UUID owner, String reason) {
        var player = playerMoves.get(owner);
        if (player != null) closePlayerMove(player);
        var mob = mobMoves.get(owner);
        if (mob != null) closeMobMove(mob, reason);
    }
    public void finishEncounters(Set<UUID> encounters, String reason) {
        for (var lease : List.copyOf(playerMoves.values()))
            if (encounters.contains(lease.encounterId)) closePlayerMove(lease);
        for (var lease : List.copyOf(mobMoves.values()))
            if (encounters.contains(lease.encounterId)) closeMobMove(lease, reason);
    }
    public void forgetForce(UUID owner) { forcedMovements.remove(owner); }
    public void close() {
        RuntimeException releaseFailure = null;
        try {
            for (var lease : List.copyOf(mobMoves.values())) {
                try { revokeMobLease(lease); }
                catch (RuntimeException failure) {
                    if (releaseFailure == null) releaseFailure = failure;
                    else releaseFailure.addSuppressed(failure);
                }
            }
            if (releaseFailure != null) throw releaseFailure;
        } finally { playerMoves.clear(); forcedMovements.clear(); }
    }
    private final List<cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope.LeaseEvidence> restoredLeaseEvidence = new ArrayList<>();
    public void restoreLeaseEvidence(List<cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope.LeaseEvidence> leases) {
        restoredLeaseEvidence.addAll(leases);
    }
    public List<cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope.LeaseEvidence> leaseEvidence() {
        var result = new ArrayList<cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope.LeaseEvidence>(restoredLeaseEvidence);
        for (var lease : playerMoves.values()) result.add(new cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope.LeaseEvidence(
                lease.encounterId, lease.operationId, lease.playerId, "PLAYER_MOVE", lease.nextStep, lease.spentTicks));
        for (var lease : mobMoves.values()) result.add(new cc.sighs.dndturn.platform.server.persistence.CombatPersistenceEnvelope.LeaseEvidence(
                lease.encounterId, lease.operationId, lease.mobId, "MOB_MOVE", lease.step, lease.spent));
        return List.copyOf(result);
    }


}
