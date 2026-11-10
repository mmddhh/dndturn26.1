package cc.sighs.dndturn.platform.server.builtin.mob;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.ai.AiAffordance;
import cc.sighs.dndturn.domain.ai.AiAbilitySemantics;
import cc.sighs.dndturn.domain.ai.AiDefinition;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.ai.PerceptionSnapshot;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.domain.mob.DefenseProfile;
import cc.sighs.dndturn.domain.mob.MobAction;
import cc.sighs.dndturn.domain.mob.MobDefinition;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import cc.sighs.dndturn.platform.server.action.AbilityExecutors;
import cc.sighs.dndturn.platform.server.action.ActionExecutionCoordinator;
import cc.sighs.dndturn.platform.server.action.MinecraftAbilityAdapter;
import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.ai.AiDefinitions;
import cc.sighs.dndturn.platform.server.character.CharacterContentService;
import cc.sighs.dndturn.platform.server.character.EncounterContentCache;
import cc.sighs.dndturn.platform.server.character.SaveService;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * One bounded, data-driven executor for the attacks and area actions in the content mob table.
 * It deliberately does not pretend to implement traits, reactions, or condition runtimes.
 */
public final class ContentMobActions {
    public static final String ACTOR = "dndturn:content_mob";
    public static final String ACTION = "dndturn:content_action";
    public static final String ACTION_PARAM = "action";
    private static final AbilityDefinition DEFINITION = new AbilityDefinition(
        ACTION, 1, "Monster action", ActionIntent.Capability.USE_ITEM,
        Set.of(ActionIntent.TargetKind.ENTITY), AbilityDefinition.Activation.MANUAL, ActionCost.USE,
        RuleFacts.AVAILABILITY, ACTION, AbilityDefinition.TargetPolicy.NATIVE_INTERACTION,
        OperationRecord.Kind.USE_ITEM, Map.of(ACTION_PARAM, java.util.stream.IntStream.range(0, 32)
            .mapToObj(Integer::toString).collect(java.util.stream.Collectors.toUnmodifiableSet())),
        AbilityDefinition.Learning.UNAVAILABLE);

    private ContentMobActions() {}

    private static cc.sighs.dndturn.domain.fact.FactKey<Double> rangeKey(int index) {
        return new cc.sighs.dndturn.domain.fact.FactKey<>("dndturn:content_range/" + index, Double.class);
    }

    public static void register() {
        var adapter = new Adapter();
        AbilityExecutors.register(DEFINITION, adapter, adapter);
        AiDefinitions.SEMANTICS.register(new AiAbilitySemantics.Key(ACTION, 1),
            Set.of(AiAffordance.DAMAGE, AiAffordance.MELEE, AiAffordance.RANGED, AiAffordance.AREA));
        ActorDefinitions.register(new ActorDefinitions.Provider() {
            public String id() { return ACTOR; }
            public int priority() { return -10; }
            public boolean matches(LiveActorContext actor) {
                if (!(actor.body() instanceof Mob)) return false;
                var definition = contentDefinition(actor);
                return definition != null && definition.rechargeMin() == 0
                    && !definition.actions().isEmpty() && definition.actions().size() <= 32;
            }
            public ActorDefinition definition(LiveActorContext actor) {
                var content = java.util.Objects.requireNonNull(contentDefinition(actor));
                var ranges = new java.util.HashMap<cc.sighs.dndturn.domain.fact.FactKey<?>, Object>();
                for (int i = 0; i < content.actions().size(); i++) ranges.put(rangeKey(i), range(content.actions().get(i)));
                return new ActorDefinition(ACTOR, 1, Set.of(), new FactSlice(ranges, Map.of()),
                    List.of(new EffectDefinition.Grant(ACTION, 1)),
                    ActorDefinition.Classification.unspecified(), Map.of());
            }
        });
        AiDefinitions.register(ACTOR, 1, new AiDefinition(ACTOR, 1, "dndturn:mob_action", Set.of(),
            AiDefinition.TargetPreference.VISIBLE_PLAYERS_OR_HOSTILE_ACTORS,
            AiDefinition.Positioning.GROUND_OPPORTUNITY, AiDefinition.ResourcePreference.CONSERVATIVE,
            AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY, ActorFacts.DECISION,
            new AiDefinition.Provenance(AiDefinition.Source.EXPLICIT_PROVIDER, ACTOR, 1,
                "captured content actions; bounded numerical applications")));
        AiPlanner.register("dndturn:mob_action", ContentMobActions::plan);
    }

    private static AiPlanner.Decision plan(cc.sighs.dndturn.domain.ai.AiDecisionContext context) {
        if (!context.actionAvailable()) return new AiPlanner.EndTurn("NO_ACTION");
        var target = context.perception().actors().stream()
            .filter(t -> t.visible() && t.nativeAttackable() && context.hostile().contains(t.id()))
            .min(Comparator.comparingDouble(PerceptionSnapshot.ObservedActor::distanceSquared)
                .thenComparing(PerceptionSnapshot.ObservedActor::id)).orElse(null);
        var binding = context.actor().abilities().stream().filter(b -> b.id().equals(ACTION)).findFirst().orElse(null);
        if (target != null && binding != null) {
            var facts = context.actor().definition().defaults();
            var reads = new cc.sighs.dndturn.domain.fact.ReadContract(facts.keys());
            for (int i = 0; i < 32; i++) {
                var key = rangeKey(i);
                if (!facts.keys().contains(key)) continue;
                double range = facts.read(reads, key);
                if (target.distanceSquared() > range * range) continue;
                var source = binding.source();
                return new AiPlanner.Propose(new ActionIntent(ACTION, 1, source.hand(),
                    ActionIntent.Capability.USE_ITEM, new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,
                        context.dimension(), target.id(), null, -1, 0, 0, 0), source.item(), source, null,
                    CombatRules.RULES_REVISION, Map.of(ACTION_PARAM, Integer.toString(i))));
            }
        }
        var option = context.options().stream().filter(o -> o.available() && !o.binding().id().equals(ACTION))
            .filter(o -> context.hostile().contains(o.target()) && o.semantics().contains(AiAffordance.DAMAGE))
            .findFirst().orElse(null);
        if (option != null) return new AiPlanner.Propose(option.binding().invocation(new ActionIntent.Target(
            ActionIntent.TargetKind.ENTITY, context.dimension(), option.target(), null, -1, 0, 0, 0)));
        return context.movementOpportunity() != null ? new AiPlanner.Propose(context.movementOpportunity())
            : new AiPlanner.EndTurn("NO_CONTENT_IN_RANGE");
    }

    private static cc.sighs.dndturn.platform.server.character.EncounterContent encounter(LiveActorContext actor) {
        var runtime = ServerRuntime.existingEncounter(actor.level().getServer());
        var id = runtime == null ? null : runtime.encounterOf(actor.id());
        return id == null ? null : EncounterContentCache.server().find(id);
    }

    private static MobDefinition contentDefinition(LiveActorContext actor) {
        var content = encounter(actor);
        var runtime = ServerRuntime.existingEncounter(actor.level().getServer());
        // Candidate creation notifies the first turn before its catalog is installed. No content grant yet.
        if (content == null && runtime != null && runtime.encounterOf(actor.id()) != null) return null;
        var catalog = content == null ? CharacterContentService.current() : content.contentFor(actor.id());
        return catalog.mobs().get(BuiltInRegistries.ENTITY_TYPE.getKey(actor.body().getType()).toString());
    }

    private static MobAction actionFor(LiveActorContext actor, ActionIntent intent) {
        var definition = contentDefinition(actor);
        if (definition == null || definition.rechargeMin() != 0) return null;
        String selected = intent.parameters().get(ACTION_PARAM);
        if (selected == null) return null;
        int index;
        try { index = Integer.parseInt(selected); } catch (NumberFormatException invalid) { return null; }
        return index >= 0 && index < definition.actions().size() ? definition.actions().get(index) : null;
    }

    private static double range(MobAction action) {
        return action instanceof MobAction.Attack attack ? Math.max(attack.reachTicks(), attack.rangeTicks())
            : ((MobAction.SaveArea) action).rangeTicks();
    }

    private static final class Adapter extends MinecraftAbilityAdapter {
        Adapter() { super(DEFINITION); }
        @Override public cc.sighs.dndturn.domain.ability.GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) { return null; }
        @Override public String unavailable(LiveActorContext actor, ActionIntent intent, StateView state) {
            var action = actionFor(actor, intent);
            if (action == null) return "unknown content action";
            Entity raw = actor.level().getEntity(intent.target().entity());
            if (!(raw instanceof LivingEntity target) || !target.isAlive()
                    || !state.members().containsKey(target.getUUID()) || !actor.body().hasLineOfSight(target))
                return "content target unavailable";
            return actor.body().distanceToSqr(target) <= range(action) * range(action) ? null : "content action out of range";
        }
        @Override public boolean canExecute(LiveActorContext actor, ActionIntent intent, net.minecraft.world.phys.Vec3 feet) {
            var action = actionFor(actor, intent);
            Entity raw = actor.level().getEntity(intent.target().entity());
            return action != null && raw instanceof LivingEntity target && target.isAlive()
                && target.distanceToSqr(feet) <= range(action) * range(action) && actor.body().hasLineOfSight(target);
        }
        @Override public void start(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
            actions.worldOutcome(() -> execute(actions, actor, execution));
        }
        @Override public void tick(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
            throw new IllegalStateException("synchronous content mob action");
        }
    }

    private static void execute(ActionExecutionCoordinator actions, LiveActorContext actor,
                                ActionExecutionCoordinator.Execution execution) {
        var action = java.util.Objects.requireNonNull(actionFor(actor, execution.snapshot().intent()));
        var content = java.util.Objects.requireNonNull(encounter(actor));
        var runtime = ServerRuntime.encounters(actor.level().getServer());
        var encounterId = runtime.encounterOf(actor.id());
        var random = runtime.combat().random(encounterId);
        var target = (LivingEntity) actor.level().getEntity(execution.snapshot().intent().target().entity());
        var center = action instanceof MobAction.SaveArea ? target.position() : actor.body().position();
        double radius = action instanceof MobAction.SaveArea area ? area.radiusTicks() : range(action);
        var targets = new java.util.ArrayList<LivingEntity>();
        if (action instanceof MobAction.SaveArea) {
            actor.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class),
                AABB.ofSize(center, radius * 2, radius * 2, radius * 2), e -> true, targets, 64);
            if (targets.size() >= 64) throw new IllegalStateException("content area scan budget exceeded");
            targets.removeIf(e -> !e.isAlive() || e == actor.body() || e.position().distanceToSqr(center) > radius * radius);
            targets.sort(Comparator.comparing(LivingEntity::getUUID));
        } else targets.add(target);
        int repeats = action instanceof MobAction.Attack ? Math.max(1, contentDefinition(actor).multiattack()) : 1;
        if ((long) targets.size() * repeats * action.damage().size() > 63)
            throw new IllegalStateException("content damage target budget exceeded");
        actions.beginStep(actor.body(), execution);
        var results = new java.util.ArrayList<cc.sighs.dndturn.domain.encounter.operation.ResolvedDamageStep>();
        for (var victim : targets) for (int repeat = 0; repeat < repeats && victim.isAlive(); repeat++) {
            boolean success;
            boolean critical = false;
            int bonus, threshold;
            List<Integer> dice;
            if (action instanceof MobAction.Attack attack) {
                threshold = RuleResolver.armorClass(runtime.actorStates().snapshots().defense(victim));
                bonus = attack.attackBonus();
                var member = runtime.state(encounterId).members().get(victim.getUUID());
                var roll = CombatRules.rollAttack(random, CombatRules.mode(false, member != null && member.dodging()), threshold, bonus);
                success = roll.hit(); critical = roll.critical(); dice = List.of(roll.first(), roll.second());
            } else {
                var area = (MobAction.SaveArea) action;
                var save = SaveService.roll(victim, area.saveAbility(), area.saveDc(), action.id(), content, random);
                success = save.success(); bonus = save.bonus(); threshold = save.dc(); dice = List.of(save.die());
            }
            var defense = content.contentFor(victim.getUUID()).mobOrFallback(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString());
            var profile = new DefenseProfile(defense.resistances(), defense.immunities(), defense.vulnerabilities());
            for (int part = 0; part < action.damage().size() && victim.isAlive(); part++) {
                var component = action.damage().get(part);
                int rolled = component.roll(random) * (critical ? 2 : 1);
                int after = action instanceof MobAction.Attack ? (success ? rolled : 0)
                    : !success ? rolled : ((MobAction.SaveArea) action).halfOnSave() ? rolled / 2 : 0;
                results.add(actions.numericalStep(actor.body(), execution, victim, "content_" + repeat + "_" + part,
                    "dndturn:tactical", cc.sighs.dndturn.platform.server.damage.CombatNexusAdapter.channel(component.type()),
                    center, radius, dice, bonus, threshold, success, rolled, after, profile.apply(component.type(), after)));
                if (results.getLast().outcome() == OperationRecord.Outcome.UNKNOWN) break;
            }
            if (!results.isEmpty() && results.getLast().outcome() == OperationRecord.Outcome.UNKNOWN) break;
        }
        var outcome = results.stream().anyMatch(r -> r.outcome() == OperationRecord.Outcome.UNKNOWN) ? OperationRecord.Outcome.UNKNOWN
            : results.stream().allMatch(r -> r.outcome() == OperationRecord.Outcome.COMPLETED) ? OperationRecord.Outcome.COMPLETED
            : results.stream().allMatch(r -> r.outcome() == OperationRecord.Outcome.REJECTED) ? OperationRecord.Outcome.REJECTED
            : OperationRecord.Outcome.PARTIAL;
        actions.finishAction(actor.body(), execution, outcome, "content action=" + action.id() + " steps=" + results.size());
    }
}
