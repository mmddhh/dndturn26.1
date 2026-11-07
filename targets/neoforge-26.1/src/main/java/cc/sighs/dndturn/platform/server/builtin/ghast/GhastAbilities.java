package cc.sighs.dndturn.platform.server.builtin.ghast;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.ai.AiDefinition;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.ai.PerceptionSnapshot;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.platform.server.action.ActionExecutionCoordinator;
import cc.sighs.dndturn.platform.server.action.AbilityExecutors;
import cc.sighs.dndturn.platform.server.action.MinecraftAbilityAdapter;
import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.character.SaveService;
import cc.sighs.dndturn.platform.server.character.PlayerArmorRules;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.ai.AiDefinitions;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.phys.AABB;

/** Audited Ghast fireball: one ranged attack, then a radius-6 DEX save for force damage. */
public final class GhastAbilities {
    public static final String FIREBALL = "dndturn:ghast_fireball";
    private GhastAbilities() {}

    private static AbilityDefinition definition() {
        return new AbilityDefinition(FIREBALL, 1, "Ghast fireball", ActionIntent.Capability.USE_ITEM,
            Set.of(ActionIntent.TargetKind.ENTITY), AbilityDefinition.Activation.MANUAL, ActionCost.USE,
            RuleFacts.AVAILABILITY, FIREBALL,
            AbilityDefinition.TargetPolicy.NATIVE_INTERACTION,
            cc.sighs.dndturn.domain.ability.AbilityDefinition.Learning.UNAVAILABLE);
    }

    private static final AbilityDefinition DEFINITION = definition();

    public static void register() {
        var definition = DEFINITION;
        var executor = new Fireball();
        AbilityExecutors.register(definition, executor, executor);
        ActorDefinitions.register(new ActorDefinitions.Provider() {
            public String id() { return "dndturn:ghast"; }
            public int priority() { return 0; }
            public boolean matches(LiveActorContext actor) { return actor.body() instanceof Ghast; }
            public ActorDefinition definition(LiveActorContext actor) {
                return new ActorDefinition(id(), 1, Set.of(), new FactSlice(java.util.Map.of(), java.util.Map.of()),
                    List.of(new EffectDefinition.Grant(FIREBALL, 1)), ActorDefinition.Classification.unspecified(), java.util.Map.of());
            }
        });
        AiPlanner.register(FIREBALL, context -> {
            if (!context.actionAvailable()) return new AiPlanner.EndTurn("NO_ACTION");
            var target = context.perception().actors().stream()
                .filter(t -> t.visible() && t.nativeAttackable() && context.hostile().contains(t.id()))
                .filter(t -> context.options().stream().anyMatch(o -> o.target().equals(t.id())
                    && o.available() && o.binding().id().equals(FIREBALL)))
                .min(Comparator.comparingDouble(PerceptionSnapshot.ObservedActor::distanceSquared)
                    .thenComparing(PerceptionSnapshot.ObservedActor::id));
            if (target.isEmpty()) return new AiPlanner.EndTurn("NO_TARGET");
            var option = context.options().stream()
                .filter(o -> o.target().equals(target.get().id()) && o.available()
                    && o.binding().id().equals(FIREBALL)).findFirst().orElseThrow();
            return new AiPlanner.Propose(option.binding().invocation(new ActionIntent.Target(
                ActionIntent.TargetKind.ENTITY, context.dimension(), target.get().id(), null, -1, 0, 0, 0)));
        });
        AiDefinitions.register("dndturn:ghast", 1, new AiDefinition("dndturn:ghast", 1, FIREBALL, Set.of(),
            AiDefinition.TargetPreference.VISIBLE_PLAYERS_OR_HOSTILE_ACTORS, AiDefinition.Positioning.STATIONARY,
            AiDefinition.ResourcePreference.CONSERVATIVE, AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY,
            ActorFacts.DECISION, new AiDefinition.Provenance(AiDefinition.Source.EXPLICIT_PROVIDER,
            "dndturn:ghast", 1, "Minecraft 26.1 / NeoForge 26.1.2.84 audited Ghast fireball")));
    }

    private static final class Fireball extends MinecraftAbilityAdapter {
        Fireball() { super(DEFINITION); }

        @Override
        public GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) { return null; }

        @Override
        public String unavailable(LiveActorContext actor, ActionIntent intent, cc.sighs.dndturn.domain.encounter.StateView state) {
            if (!(actor.body() instanceof Ghast ghast)) return "not a Ghast";
            Entity raw = actor.level().getEntity(intent.target().entity());
            if (!(raw instanceof LivingEntity target) || !target.isAlive()
                || !state.members().containsKey(target.getUUID()) || !ghast.hasLineOfSight(target)
                || ghast.distanceToSqr(target) > 60 * 60) return "Ghast target unavailable";
            return null;
        }

        @Override
        public boolean canExecute(LiveActorContext actor, ActionIntent intent, net.minecraft.world.phys.Vec3 feet) {
            Entity raw = actor.level().getEntity(intent.target().entity());
            return raw instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(feet) <= 60 * 60;
        }

        @Override
        public void start(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
            actions.beginStep(actor.body(), execution);
            LivingEntity target = (LivingEntity) actor.level().getEntity(execution.snapshot().intent().target().entity());
            var ghast = (Ghast) actor.body();
            var random = new java.util.Random();
            int attack = 4;
            var service = ServerRuntime.encounters(actor.level().getServer());
            var defenseFacts = service.actorStates().snapshots().defense(target);
            int ac = PlayerArmorRules.armorClass(target,
                service.actorStates().state(target.getUUID()).persistent().character());
            if (ac < 0) ac = RuleResolver.armorClass(defenseFacts);
            var roll = cc.sighs.dndturn.domain.resolution.CombatRules.rollAttack(random,
                cc.sighs.dndturn.domain.resolution.CombatRules.RollMode.NORMAL, ac, attack);
            if (roll.hit()) target.hurt(actor.level().damageSources().mobAttack(ghast), 6.0F);
            if (!target.isRemoved()) {
                AABB area = target.getBoundingBox().inflate(6);
                for (LivingEntity affected : target.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && e != ghast)) {
                    var save = SaveService.roll(affected, Ability.DEX, 15, "dndturn:ghast_fireball", random);
                    affected.hurt(actor.level().damageSources().mobAttack(ghast), save.success() ? 5.0F : 10.0F);
                }
            }
            actions.finishAction(actor.body(), execution, OperationRecord.Outcome.COMPLETED,
                "ghast fireball attack=" + roll.total() + " ac=" + ac + " hit=" + roll.hit());
        }

        @Override
        public void tick(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
            throw new IllegalStateException("synchronous Ghast fireball");
        }
    }
}
