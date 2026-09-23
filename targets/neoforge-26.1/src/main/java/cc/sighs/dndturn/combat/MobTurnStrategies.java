package cc.sighs.dndturn.combat;

import cc.sighs.dndturn.DebugDiagnostics;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.util.RandomSource;

/** Strategies propose values only. Execution, resources and navigation belong to the shared PLAN. */
public final class MobTurnStrategies {
    public interface Strategy {
        String id();
        int version();
        int priority();
        boolean matches(Mob actor);
        /** Null ends the turn. Selection receives values only, never a mutable entity or engine. */
        TacticalIntent propose(DecisionView view);
        default Decision decide(DecisionView view) {
            var intent = propose(view);
            return intent == null ? new End() : new Propose(intent);
        }
    }
    public sealed interface Decision permits Propose, End, Wait {}
    public record Propose(TacticalIntent intent) implements Decision {
        public Propose { Objects.requireNonNull(intent); }
    }
    public record End() implements Decision {}
    /** Bounded observation wait, never a new gameplay resource or implicit root operation. */
    public record Wait(int ticks) implements Decision {
        public Wait { if (ticks < 1 || ticks > 20) throw new IllegalArgumentException("AI wait bound"); }
    }
    public record PerceivedTarget(UUID id, boolean visible, boolean currentTarget, boolean hostile,
                                  boolean player, boolean attackable, double distanceSquared,
                                  List<TacticalCapabilities.Discovered> abilities) {
        public PerceivedTarget { abilities = List.copyOf(abilities); }
    }
    public record DecisionView(UUID actor, GridCell position, CombatEngine.StateView state, OperationRecord.Result previous,
                               List<PerceivedTarget> targets, TacticalIntent stroll) {
        public DecisionView { targets = List.copyOf(targets); }
    }
    static DecisionView capture(TacticalActor actor, CombatEngine.StateView state,
                                        OperationRecord.Result previous, CombatEngine engine) {
        return capture(actor, state, previous, engine, new Capture(actor, state));
    }
    private record Dependencies(UUID instance, long version, NativeActorFacts facts,
                                net.minecraft.world.phys.Vec3 position, UUID target,
                                List<List<AbilitySource>> sources) {}
    private static Dependencies dependencies(TacticalActor actor, CombatEngine.StateView state) {
        var target = ((Mob)actor.body()).getTarget();
        return new Dependencies(actor.instance(), state.version(), NativeFacts.capture(actor), actor.body().position(),
            target == null ? null : target.getUUID(), TacticalCapabilities.all().stream()
                .map(behavior -> behavior.checkedSources(actor)).toList());
    }
    private static final class Capture {
        final UUID actor;
        final Dependencies dependencies;
        final BudgetedScan<UUID, PerceivedTarget> scan;
        Capture(TacticalActor actor, CombatEngine.StateView state) {
            this.actor = actor.id(); this.dependencies = dependencies(actor, state);
            scan = new BudgetedScan<>(state.members().keySet().stream().sorted().toList(), 4096);
        }
    }
    private static DecisionView capture(TacticalActor actor, CombatEngine.StateView state,
                                        OperationRecord.Result previous, CombatEngine engine, Capture capture) {
        var mob = (Mob) actor.body();
        var service = ServerCombatService.forServer(actor.level().getServer());
        boolean complete = capture.scan.advance(() -> service.abilityWork.take(service.planClock(), actor.id(),
            state.id(), AbilityWorkBudget.Work.CANDIDATE), id -> {
            if (id.equals(actor.id())) return null;
            var entity = actor.level().getEntity(id);
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()) return null;
            var center = living.getBoundingBox().getCenter();
            if (!state.region().containsPoint(center.x, center.y, center.z)) return null;
            boolean visible = mob.hasLineOfSight(living), current = mob.getTarget() == living;
            // Server lookup is not perception. Retain a vanilla target commitment even when obscured.
            if (!visible && !current) return null;
            var follow = capture.dependencies.facts().attributes().get("follow_range");
            if (!current && (follow == null || mob.distanceToSqr(living) > follow.effective() * follow.effective())) return null;
            var aim = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, state.region().dimension(), id, null, -1, 0, 0, 0);
            return new PerceivedTarget(id, visible, current, engine.isHostile(state.id(), actor.id(), id),
                living instanceof ServerPlayer, mob.canAttack(living), mob.distanceToSqr(living),
                TacticalCapabilities.discover(actor, state, aim));
        });
        if (!complete) throw new ActionFailure(ActionFailure.Code.EVALUATION_DEFERRED,
            ActionFailure.Retry.REFRESH_AND_REPROPOSE, "AI discovery retained its cursor for the next server tick");
        return new DecisionView(actor.id(), TacticalActions.cell(mob.blockPosition()), state, previous, capture.scan.results(),
            mob instanceof PathfinderMob && mob.getNavigation() instanceof GroundPathNavigation
                ? Ground.movement(actor, state, previous) : null);
    }
    private static final Map<String, Strategy> STRATEGIES = new LinkedHashMap<>();
    private static boolean frozen;
    static {
        register(new Ground());
        register(new ZombieAttack());
        register(new EndermanAttack());
    }
    private MobTurnStrategies() {}
    public static synchronized void register(Strategy strategy) {
        Objects.requireNonNull(strategy);
        if (frozen || STRATEGIES.size() >= 64) throw new IllegalStateException("strategy registration closed");
        if (strategy.id() == null || strategy.id().length() > 128 || !strategy.id().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || strategy.version() < 1)
            throw new IllegalArgumentException("strategy metadata");
        if (STRATEGIES.putIfAbsent(strategy.id(), strategy) != null) throw new IllegalArgumentException("duplicate strategy");
    }
    public static synchronized void freeze() { frozen = true; }
    private static synchronized Strategy resolve(Mob actor) {
        Strategy best = null;
        boolean ambiguous = false;
        for (var strategy : STRATEGIES.values()) if (strategy.matches(actor)) {
            if (best == null || strategy.priority() > best.priority()) {
                best = strategy;
                ambiguous = false;
            } else if (strategy.priority() == best.priority()) ambiguous = true;
        }
        if (ambiguous) throw new IllegalStateException("ambiguous Mob strategies");
        return best;
    }
    /** Bounded decision memory, not a resource balance. Reset on owner, instance or turn changes. */
    static final class Decisions {
        private record Turn(UUID actor, UUID instance, long round, int count, UUID operation) {}
        private record Proposal(UUID actor, UUID instance, long version, UUID operation, TacticalIntent intent) {}
        private final Map<UUID, Turn> turns = new HashMap<>();
        private final Map<UUID, Proposal> proposals = new HashMap<>();
        private record Waiting(long until, int total) {}
        private final Map<UUID, Waiting> waits = new HashMap<>();
        private final Map<UUID, Capture> captures = new HashMap<>();
        void retain(Set<UUID> ids) { turns.keySet().retainAll(ids); proposals.keySet().retainAll(ids); waits.keySet().retainAll(ids); captures.keySet().retainAll(ids); }
        TacticalIntent next(TacticalActor actor, CombatEngine.StateView state, CombatEngine engine) {
            var old = turns.get(state.id());
            if (old == null || !old.actor.equals(actor.id()) || !old.instance.equals(actor.instance()) || old.round != state.round()) {
                old = new Turn(actor.id(), actor.instance(), state.round(), 0, null);
                turns.put(state.id(), old);
                proposals.remove(state.id());
                waits.remove(state.id());
                captures.remove(state.id());
            }
            long now = ServerCombatService.forServer(actor.level().getServer()).planClock();
            var waiting = waits.get(state.id());
            if (waiting != null && now < waiting.until()) throw deferredWait();
            if (old.count >= 8) throw new IllegalStateException("AI_DECISION_LIMIT");
            var previous = old.operation == null ? null : engine.resultFor(state.id(), old.operation);
            if (old.operation != null && (previous == null || previous.outcome() == OperationRecord.Outcome.UNKNOWN
                || previous.outcome() == OperationRecord.Outcome.REJECTED || previous.outcome() == OperationRecord.Outcome.INTERRUPTED)) return null;
            var strategy = resolve((Mob)actor.body());
            var cached = proposals.get(state.id());
            if (cached != null && cached.actor.equals(actor.id()) && cached.instance.equals(actor.instance())
                && cached.version == state.version()) return cached.intent;
            proposals.remove(state.id());
            var pending = captures.get(state.id());
            if (pending == null || !pending.actor.equals(actor.id()) || !pending.dependencies.equals(dependencies(actor, state))) {
                pending = new Capture(actor, state);
                captures.put(state.id(), pending);
            }
            var decision = strategy == null ? new End() : strategy.decide(capture(actor, state, previous, engine, pending));
            DebugDiagnostics.log("MOB_DECISION", () -> "actor=" + actor.id() + " instance=" + actor.instance()
                + " encounter=" + state.id() + " version=" + state.version() + " round=" + state.round()
                + " strategy=" + (strategy == null ? "NONE" : strategy.id() + "@" + strategy.version())
                + " decision=" + decision.getClass().getSimpleName()
                + (decision instanceof Propose proposal ? " " + DebugDiagnostics.intent(proposal.intent()) : ""));
            captures.remove(state.id());
            if (decision instanceof Wait wait) {
                int total = (waiting == null ? 0 : waiting.total()) + wait.ticks();
                if (total > 100) throw new IllegalStateException("AI_WAIT_LIMIT");
                waits.put(state.id(), new Waiting(Math.addExact(now, wait.ticks()), total));
                throw deferredWait();
            }
            var intent = decision instanceof Propose proposal ? proposal.intent() : null;
            if (intent != null) proposals.put(state.id(), new Proposal(actor.id(), actor.instance(), state.version(), UUID.randomUUID(), intent));
            return intent;
        }
        private static ActionFailure deferredWait() {
            return new ActionFailure(ActionFailure.Code.EVALUATION_DEFERRED,
                ActionFailure.Retry.REFRESH_AND_REPROPOSE, "AI observation wait");
        }
        UUID operation(CombatEngine.StateView state) { return proposals.get(state.id()).operation; }
        void submitted(CombatEngine.StateView state, UUID operation) {
            var old = turns.get(state.id());
            turns.put(state.id(), new Turn(old.actor, old.instance, old.round, old.count + 1, operation));
            proposals.remove(state.id());
        }
    }
    private static class Ground implements Strategy {
        public String id() { return "dndturn:ground_stroll"; }
        public int version() { return 1; }
        public int priority() { return 0; }
        public boolean matches(Mob mob) { return mob instanceof PathfinderMob && mob.getNavigation() instanceof GroundPathNavigation; }
        public TacticalIntent propose(DecisionView view) { return view.stroll(); }
        private static TacticalIntent movement(TacticalActor actor, CombatEngine.StateView state, OperationRecord.Result previous) {
            if (previous != null) return null;
            var mob = (PathfinderMob)actor.body();
            if (mob.isNoAi() || mob.isPassenger() || mob.hasControllingPassenger() || state.members().get(actor.id()).movementTicks() < 1) return null;
            var origin = mob.blockPosition();
            if (!actor.level().hasChunksAt(origin.offset(-10, -7, -10), origin.offset(10, 7, 10))) return null;
            var path = mob.getNavigation().getPath();
            var end = path == null || mob.getNavigation().isDone() ? null : path.getEndNode();
            var position = end == null ? stroll(actor, state, mob) : new net.minecraft.world.phys.Vec3(end.x + .5, end.y, end.z + .5);
            if (position == null || !state.region().containsPoint(position.x, position.y, position.z)) return null;
            var cell = TacticalActions.cell(net.minecraft.core.BlockPos.containing(position));
            return new TacticalIntent("dndturn:move", 1, TacticalIntent.Capability.MOVE,
                new TacticalIntent.Target(TacticalIntent.TargetKind.GROUND, state.region().dimension(), null, cell, -1, 0, 0, 0), AbilitySource.basic());
        }
        /** Same bounded ten-candidate sampler as .84 DefaultRandomPos, with decision-owned entropy.
         * Re-evaluation cannot advance the entity's combat/vanilla random stream. */
        private static net.minecraft.world.phys.Vec3 stroll(TacticalActor actor, CombatEngine.StateView state, PathfinderMob mob) {
            long seed = actor.id().getMostSignificantBits() ^ actor.id().getLeastSignificantBits()
                ^ state.id().getMostSignificantBits() ^ Long.rotateLeft(state.round(), 23);
            RandomSource random = RandomSource.create(seed);
            boolean restrict = GoalUtils.mobRestricted(mob, 10);
            return RandomPos.generateRandomPos(mob, () -> {
                var direction = RandomPos.generateRandomDirection(random, 10, 7);
                var pos = RandomPos.generateRandomPosTowardDirection(mob, 10, random, direction);
                if (!actor.level().hasChunkAt(pos) || GoalUtils.isOutsideLimits(pos, mob)
                    || GoalUtils.isRestricted(restrict, mob, pos) || GoalUtils.isNotStable(mob.getNavigation(), pos)
                    || GoalUtils.hasMalus(mob, pos)) return null;
                return pos;
            });
        }
    }
    static final class ZombieAttack extends Ground {
        public String id() { return "dndturn:zombie_combat"; }
        public int priority() { return 10; }
        public boolean matches(Mob actor) { return actor instanceof Zombie && super.matches(actor); }
        public TacticalIntent propose(DecisionView view) {
            var state = view.state();
            if (state.phase() != EncounterPhase.ACTIVE && state.phase() != EncounterPhase.CANDIDATE) return super.propose(view);
            if (!state.members().get(view.actor()).action()) return null;
            // Zombie's audited player-acquisition preference may establish the first directed hostility.
            // This is a species policy, not a rule that every visible player is universally hostile.
            var target = selectTarget(view.targets());
            if (target == null) return super.propose(view);
            var aim = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, state.region().dimension(), target.id(), null, -1, 0, 0, 0);
            return target.abilities().stream().filter(ZombieAttack::usableMelee)
                .map(option -> option.binding().invocation(aim)).findFirst().orElse(null);
        }
        static PerceivedTarget selectTarget(List<PerceivedTarget> targets) {
            return targets.stream().filter(PerceivedTarget::player)
                .filter(PerceivedTarget::attackable)
                .filter(t -> t.visible() || t.currentTarget())
                .filter(t -> t.abilities().stream().anyMatch(ZombieAttack::usableMelee))
                .min(Comparator.<PerceivedTarget>comparingInt(t -> t.currentTarget() ? 0 : 1)
                    .thenComparingDouble(PerceivedTarget::distanceSquared).thenComparing(PerceivedTarget::id)).orElse(null);
        }
        private static boolean usableMelee(TacticalCapabilities.Discovered option) {
            return option.binding().id().equals("dndturn:intrinsic_melee")
                && (option.availability() == TacticalCapabilities.Availability.EXECUTABLE
                    || option.availability() == TacticalCapabilities.Availability.APPROACH_REQUIRED);
        }
    }
    private static final class EndermanAttack extends Ground {
        public String id() { return "dndturn:enderman_combat"; }
        public int priority() { return 10; }
        public boolean matches(Mob actor) { return actor.getClass() == net.minecraft.world.entity.monster.EnderMan.class && super.matches(actor); }
        public TacticalIntent propose(DecisionView view) {
            if (view.state().phase() != EncounterPhase.ACTIVE) return super.propose(view);
            if (!view.state().members().get(view.actor()).action()) return null;
            // Preserve an audited target commitment; visibility alone does not provoke Endermen.
            var target = ZombieAttack.selectTarget(view.targets().stream()
                .filter(t -> t.currentTarget() || t.hostile()).toList());
            if (target == null) return super.propose(view);
            var aim = new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY, view.state().region().dimension(),
                target.id(), null, -1, 0, 0, 0);
            return target.abilities().stream().filter(ZombieAttack::usableMelee)
                .map(option -> option.binding().invocation(aim)).findFirst().orElse(null);
        }
    }
}
