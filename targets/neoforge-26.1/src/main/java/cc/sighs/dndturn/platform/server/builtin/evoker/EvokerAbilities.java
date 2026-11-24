package cc.sighs.dndturn.platform.server.builtin.evoker;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.ai.AiDefinition;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.ai.PerceptionSnapshot;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.platform.server.action.ActionExecutionCoordinator;
import cc.sighs.dndturn.platform.server.action.AbilityExecutors;
import cc.sighs.dndturn.platform.server.action.MinecraftAbilityAdapter;
import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.ai.AiDefinitions;
import cc.sighs.dndturn.platform.server.character.CharacterContentService;
import cc.sighs.dndturn.platform.server.character.EncounterContentCache;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.Vex;

/** Evoker's summon action: one action, four vexes, six-round recharge. */
public final class EvokerAbilities {
    public static final String SUMMON = "dndturn:evoker_summon";
    public static final String FANGS = "dndturn:evoker_fangs";
    private static final int RECHARGE_ROUNDS = 6;
    private static final int SUMMON_RANGE = 8;
    private static final int FANGS_RANGE = 10;
    private static final Map<UUID, Long> READY_ROUNDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final AbilityDefinition DEFINITION = new AbilityDefinition(
        SUMMON, 1, "Summon vexes", ActionIntent.Capability.USE_ITEM, Set.of(ActionIntent.TargetKind.ENTITY),
        AbilityDefinition.Activation.MANUAL, ActionCost.USE, RuleFacts.AVAILABILITY, SUMMON,
        AbilityDefinition.TargetPolicy.NATIVE_INTERACTION, OperationRecord.Kind.USE_ITEM,
        Map.of(), AbilityDefinition.Learning.UNAVAILABLE);
    private static final AbilityDefinition FANG_DEFINITION = new AbilityDefinition(
        FANGS, 1, "Evoker fangs", ActionIntent.Capability.USE_ITEM, Set.of(ActionIntent.TargetKind.ENTITY),
        AbilityDefinition.Activation.MANUAL, ActionCost.USE, RuleFacts.AVAILABILITY, FANGS,
        AbilityDefinition.TargetPolicy.NATIVE_INTERACTION, OperationRecord.Kind.USE_ITEM,
        Map.of(), AbilityDefinition.Learning.UNAVAILABLE);

    private EvokerAbilities() {}

    public static void register() {
        var adapter = new Summon();
        AbilityExecutors.register(DEFINITION, adapter, adapter);
        var fangs = new Fangs();
        AbilityExecutors.register(FANG_DEFINITION, fangs, fangs);
        ActorDefinitions.register(new ActorDefinitions.Provider() {
            public String id() { return "dndturn:evoker"; }
            public int priority() { return 20; }
            public boolean matches(LiveActorContext actor) { return actor.body() instanceof Evoker; }
            public ActorDefinition definition(LiveActorContext actor) {
                return new ActorDefinition(id(), 1, Set.of(), new FactSlice(Map.of(), Map.of()),
                    List.of(new EffectDefinition.Grant(SUMMON, 1), new EffectDefinition.Grant(FANGS, 1)),
                    ActorDefinition.Classification.unspecified(), Map.of());
            }
        });
        AiPlanner.register(SUMMON, context -> {
            if (!context.actionAvailable()) return new AiPlanner.EndTurn("NO_ACTION");
            long round = context.runtime().round();
            boolean summonReady = READY_ROUNDS.getOrDefault(context.actor().actor(), 0L) <= round;
            var target = context.perception().actors().stream()
                .filter(t -> t.visible() && t.nativeAttackable() && context.hostile().contains(t.id()))
                .filter(t -> t.distanceSquared() <= FANGS_RANGE * FANGS_RANGE)
                .min(Comparator.comparingDouble(PerceptionSnapshot.ObservedActor::distanceSquared)
                    .thenComparing(PerceptionSnapshot.ObservedActor::id)).orElse(null);
            if (target == null) return fallback(context, "NO_TARGET_IN_RANGE");
            if (summonReady && target.distanceSquared() <= SUMMON_RANGE * SUMMON_RANGE)
                return propose(context, SUMMON, target.id());
            if (target.distanceSquared() <= FANGS_RANGE * FANGS_RANGE)
                return propose(context, FANGS, target.id());
            return fallback(context, "NO_TARGET_IN_RANGE");
        });
        AiDefinitions.register("dndturn:evoker", 1, new AiDefinition("dndturn:evoker", 1, SUMMON, Set.of(),
            AiDefinition.TargetPreference.VISIBLE_PLAYERS_OR_HOSTILE_ACTORS,
            AiDefinition.Positioning.GROUND_OPPORTUNITY, AiDefinition.ResourcePreference.CONSERVATIVE,
            AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY, ActorFacts.DECISION,
            new AiDefinition.Provenance(AiDefinition.Source.EXPLICIT_PROVIDER, "dndturn:evoker", 1,
                "Evoker summon action; four vexes and six-round recharge")));
    }

    private static AiPlanner.Decision propose(cc.sighs.dndturn.domain.ai.AiDecisionContext context,
                                               String ability, UUID target) {
        var binding = context.actor().abilities().stream()
            .filter(b -> b.id().equals(ability) && b.version() == 1).findFirst().orElse(null);
        if (binding == null) return fallback(context, "NO_BINDING");
        var source = binding.source();
        return new AiPlanner.Propose(new ActionIntent(ability, 1, source.hand(),
            ActionIntent.Capability.USE_ITEM, new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,
                context.dimension(), target, null, -1, 0, 0, 0), source.item(), source));
    }

    private static AiPlanner.Decision fallback(cc.sighs.dndturn.domain.ai.AiDecisionContext context, String reason) {
        var option = context.options().stream()
            .filter(o -> o.available() && !o.binding().id().equals(SUMMON) && !o.binding().id().equals(FANGS)
                && o.semantics().contains(cc.sighs.dndturn.domain.ai.AiAffordance.DAMAGE)
                && context.hostile().contains(o.target())).findFirst().orElse(null);
        if (option != null) return new AiPlanner.Propose(option.binding().invocation(new ActionIntent.Target(
            ActionIntent.TargetKind.ENTITY, context.dimension(), option.target(), null, -1, 0, 0, 0)));
        if (context.movementOpportunity() != null) return new AiPlanner.Propose(context.movementOpportunity());
        return new AiPlanner.EndTurn(reason);
    }

    private static final class Fangs extends MinecraftAbilityAdapter {
        Fangs() { super(FANG_DEFINITION); }

        @Override public cc.sighs.dndturn.domain.ability.GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) {
            return null;
        }

        @Override public String unavailable(LiveActorContext actor, ActionIntent intent, StateView state) {
            var raw = actor.level().getEntity(intent.target().entity());
            if (!(raw instanceof net.minecraft.world.entity.LivingEntity target) || !target.isAlive()
                || !state.members().containsKey(target.getUUID()) || !actor.body().hasLineOfSight(target))
                return "Evoker fang target unavailable";
            return actor.body().distanceToSqr(target) <= FANGS_RANGE * FANGS_RANGE
                ? null : "Evoker fang target out of range";
        }

        @Override public boolean canExecute(LiveActorContext actor, ActionIntent intent, net.minecraft.world.phys.Vec3 feet) {
            var raw = actor.level().getEntity(intent.target().entity());
            return raw instanceof net.minecraft.world.entity.LivingEntity target && target.isAlive()
                && target.distanceToSqr(feet) <= FANGS_RANGE * FANGS_RANGE
                && actor.body().hasLineOfSight(target);
        }

        @Override public void start(ActionExecutionCoordinator actions, LiveActorContext actor,
                                   ActionExecutionCoordinator.Execution execution) {
            actions.worldOutcome(() -> fangs(actions, actor, execution));
        }

        private void fangs(ActionExecutionCoordinator actions, LiveActorContext actor,
                           ActionExecutionCoordinator.Execution execution) {
            actions.beginStep(actor.body(), execution);
            var service = ServerRuntime.existingEncounter(actor.level().getServer());
            var encounter = service == null ? null : service.encounterOf(actor.id());
            if (service == null || encounter == null) throw new IllegalStateException("Evoker not in encounter");
            var target = (net.minecraft.world.entity.LivingEntity) actor.level()
                .getEntity(execution.snapshot().intent().target().entity());
            var level = (net.minecraft.server.level.ServerLevel) actor.level();
            var start = actor.body().getEyePosition();
            var end = target.position().add(0, target.getBbHeight() / 2, 0);
            var line = end.subtract(start);
            double length = line.length();
            if (length <= 0 || length > FANGS_RANGE) {
                actions.finishAction(actor.body(), execution, OperationRecord.Outcome.REJECTED,
                    "evoker fang line out of range");
                return;
            }
            var direction = line.normalize();
            var candidates = new java.util.ArrayList<>(level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                net.minecraft.world.phys.AABB.ofSize(start, length * 2 + 4, length * 2 + 4, length * 2 + 4),
                e -> e.isAlive() && e != actor.body()));
            candidates.sort(Comparator.comparing(net.minecraft.world.entity.LivingEntity::getUUID));
            int hits = 0;
            var results = new java.util.ArrayList<cc.sighs.dndturn.domain.encounter.operation.ResolvedDamageStep>();
            for (var victim : candidates) {
                if (hits >= 16) break;
                var center = victim.getBoundingBox().getCenter();
                var relative = center.subtract(start);
                double projection = relative.dot(direction);
                if (projection < 0 || projection > FANGS_RANGE
                    || relative.subtract(direction.scale(projection)).length() > 1.5) continue;
                var content = EncounterContentCache.server().find(encounter);
                if (content == null) break;
                String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(victim.getType()).toString();
                var mob = content.contentFor(victim.getUUID()).mobOrFallback(id);
                var defense = new cc.sighs.dndturn.domain.mob.DefenseProfile(
                    mob.resistances(), mob.immunities(), mob.vulnerabilities());
                int applied = defense.apply(cc.sighs.dndturn.domain.mob.DamageType.PIERCING, 10);
                results.add(actions.numericalStep(actor.body(), execution, victim,
                    "fang_" + hits, "minecraft:magic", "dndturn:piercing", start, FANGS_RANGE,
                    List.of(), 0, 0, true, 10, 10, applied));
                hits++;
            }
            var outcome = results.stream().anyMatch(r -> r.outcome() == OperationRecord.Outcome.UNKNOWN)
                ? OperationRecord.Outcome.UNKNOWN
                : results.isEmpty() ? OperationRecord.Outcome.COMPLETED
                : results.stream().allMatch(r -> r.outcome() == OperationRecord.Outcome.COMPLETED)
                    ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.PARTIAL;
            actions.finishAction(actor.body(), execution, outcome, "evoker fangs hits=" + hits);
        }

        @Override public void tick(ActionExecutionCoordinator actions, LiveActorContext actor,
                                  ActionExecutionCoordinator.Execution execution) {
            throw new IllegalStateException("synchronous evoker fangs");
        }
    }

    private static final class Summon extends MinecraftAbilityAdapter {
        Summon() { super(DEFINITION); }

        @Override public cc.sighs.dndturn.domain.ability.GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) {
            return null;
        }

        @Override public String unavailable(LiveActorContext actor, ActionIntent intent, StateView state) {
            var raw = actor.level().getEntity(intent.target().entity());
            if (!(raw instanceof net.minecraft.world.entity.LivingEntity target) || !target.isAlive()
                || !state.members().containsKey(target.getUUID()) || !actor.body().hasLineOfSight(target))
                return "Evoker target unavailable";
            return actor.body().distanceToSqr(target) <= SUMMON_RANGE * SUMMON_RANGE
                ? null : "Evoker target out of range";
        }

        @Override public boolean canExecute(LiveActorContext actor, ActionIntent intent, net.minecraft.world.phys.Vec3 feet) {
            var raw = actor.level().getEntity(intent.target().entity());
            return raw instanceof net.minecraft.world.entity.LivingEntity target && target.isAlive()
                && target.distanceToSqr(feet) <= SUMMON_RANGE * SUMMON_RANGE
                && actor.body().hasLineOfSight(target);
        }

        @Override public void start(ActionExecutionCoordinator actions, LiveActorContext actor,
                                   ActionExecutionCoordinator.Execution execution) {
            actions.worldOutcome(() -> summon(actions, actor, execution));
        }

        private void summon(ActionExecutionCoordinator actions, LiveActorContext actor,
                            ActionExecutionCoordinator.Execution execution) {
            actions.beginStep(actor.body(), execution);
            var service = ServerRuntime.existingEncounter(actor.level().getServer());
            var encounter = service == null ? null : service.encounterOf(actor.id());
            if (service == null || encounter == null) throw new IllegalStateException("Evoker not in encounter");
            var target = actor.level().getEntity(execution.snapshot().intent().target().entity());
            ServerPlayer owner = target instanceof ServerPlayer player
                ? player : actor.level().getNearestPlayer(actor.body(), 32) instanceof ServerPlayer nearest ? nearest : null;
            if (owner == null) throw new IllegalStateException("Evoker summon has no owner");
            int count = 0;
            var level = (net.minecraft.server.level.ServerLevel) actor.level();
            var random = service.combat().random(encounter);
            for (int i = 0; i < 4; i++) {
                net.minecraft.core.BlockPos pos = actor.body().blockPosition()
                    .offset(-2 + random.nextInt(5), 1, -2 + random.nextInt(5));
                Vex vex = EntityType.VEX.create(level, EntitySpawnReason.MOB_SUMMONED);
                if (vex == null) continue;
                // Match vanilla EvokerSummonSpellGoal placement: one block above the caster,
                // within a five-block footprint, with owner and bound origin.
                vex.snapTo(pos, 0, 0);
                vex.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
                vex.setOwner((Evoker) actor.body());
                vex.setBoundOrigin(pos);
                vex.setLimitedLife(20 * (30 + random.nextInt(90)));
                vex.setInvisible(false);
                vex.setNoAi(false);
                vex.setNoGravity(true);
                vex.setDeltaMovement(0, 0, 0);
                vex.setPersistenceRequired();
                if (!level.addFreshEntity(vex)) continue;
                actions.joinGeneratedMob(owner, vex, encounter);
                if (service.encounterOf(vex.getUUID()) == null)
                    throw new IllegalStateException("summoned Vex did not join encounter");
                if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                    actions.setHostile(encounter, vex.getUUID(), living.getUUID());
                    vex.setTarget(living);
                }
                count++;
            }
            READY_ROUNDS.put(actor.id(), service.state(encounter).round() + RECHARGE_ROUNDS);
            actions.finishAction(actor.body(), execution, OperationRecord.Outcome.COMPLETED,
                "evoker summon vexes=" + count);
        }

        @Override public void tick(ActionExecutionCoordinator actions, LiveActorContext actor,
                                  ActionExecutionCoordinator.Execution execution) {
            throw new IllegalStateException("synchronous evoker summon");
        }
    }
}
