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
import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.domain.character.ContentSnapshot;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.domain.mob.DamagePacket;
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
import cc.sighs.dndturn.platform.server.character.PlayerArmorRules;
import cc.sighs.dndturn.platform.server.character.SaveService;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final java.util.Random RANDOM = new java.util.Random();
    private static final Map<UUID, MobDefinition> ACTOR_DEFINITIONS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final AbilityDefinition DEFINITION = new AbilityDefinition(
        ACTION, 1, "Monster action", ActionIntent.Capability.USE_ITEM,
        Set.of(ActionIntent.TargetKind.ENTITY), AbilityDefinition.Activation.MANUAL, ActionCost.USE,
        RuleFacts.AVAILABILITY, ACTION, AbilityDefinition.TargetPolicy.NATIVE_INTERACTION,
        OperationRecord.Kind.USE_ITEM, Map.of(ACTION_PARAM, Set.of("*")),
        AbilityDefinition.Learning.UNAVAILABLE);

    private ContentMobActions() {}

    public static void register() {
        AbilityExecutors.register(DEFINITION, new Adapter(), new Adapter());
        AiDefinitions.SEMANTICS.register(new AiAbilitySemantics.Key(ACTION, 1),
            Set.of(AiAffordance.DAMAGE, AiAffordance.MELEE, AiAffordance.RANGED, AiAffordance.AREA));
        ActorDefinitions.register(new ActorDefinitions.Provider() {
            public String id() { return ACTOR; }
            public int priority() { return 10; }
            public boolean matches(LiveActorContext actor) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(actor.body().getType()).toString();
                if (id.equals("minecraft:ghast")) return false; // kept on the audited explicit adapter
                return actor.body() instanceof Mob && contentDefinition(actor) != null
                    && contentDefinition(actor).actions().stream().anyMatch(a -> supported(a));
            }
            public ActorDefinition definition(LiveActorContext actor) {
                MobDefinition content = contentDefinition(actor);
                if (content == null) throw new IllegalStateException("content definition disappeared: " + actor.id());
                if (ACTOR_DEFINITIONS.size() > 4096) ACTOR_DEFINITIONS.clear();
                ACTOR_DEFINITIONS.put(actor.id(), content);
                LOGGER.info("content actor {} uses {} actions ({})", content.id(), content.actions().size(),
                    content.actions().stream().map(MobAction::id).toList());
                return new ActorDefinition(ACTOR, 1, Set.of(), new FactSlice(Map.of(), Map.of()),
                    List.of(new EffectDefinition.Grant(ACTION, 1)),
                    ActorDefinition.Classification.unspecified(), Map.of());
            }
        });
        AiDefinitions.register(ACTOR, 1, new AiDefinition(ACTOR, 1, "dndturn:mob_action", Set.of(),
            AiDefinition.TargetPreference.VISIBLE_PLAYERS_OR_HOSTILE_ACTORS,
            AiDefinition.Positioning.GROUND_OPPORTUNITY, AiDefinition.ResourcePreference.CONSERVATIVE,
            AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY, ActorFacts.DECISION,
            new AiDefinition.Provenance(AiDefinition.Source.EXPLICIT_PROVIDER, ACTOR, 1,
                "content mob action table; bounded attacks/areas/summons")));
        AiPlanner.register("dndturn:mob_action", ContentMobActions::plan);
    }

    private static AiPlanner.Decision plan(cc.sighs.dndturn.domain.ai.AiDecisionContext context) {
        if (!context.actionAvailable()) return new AiPlanner.EndTurn("NO_ACTION");
        MobDefinition definition = ACTOR_DEFINITIONS.get(context.actor().actor());
        if (definition == null) {
            LOGGER.warn("content AI missing definition for {}", context.actor().actor());
            return nativeFallback(context, "NO_CONTENT_DEFINITION");
        }
        var target = context.perception().actors().stream()
            .filter(t -> t.visible() && t.nativeAttackable() && context.hostile().contains(t.id()))
            .min(Comparator.comparingDouble(PerceptionSnapshot.ObservedActor::distanceSquared)
                .thenComparing(PerceptionSnapshot.ObservedActor::id)).orElse(null);
        if (target == null) return nativeFallback(context, "NO_TARGET");
        MobAction action = choose(definition, Math.sqrt(target.distanceSquared()));
        if (action != null) {
            var option = context.options().stream()
                .filter(o -> o.target().equals(target.id()) && o.available() && o.binding().id().equals(ACTION))
                .findFirst().orElse(null);
            var binding = option == null ? context.actor().abilities().stream()
                .filter(b -> b.id().equals(ACTION) && b.version() == 1).findFirst().orElse(null) : null;
            if (option != null || binding != null) {
                var source = option != null ? option.binding().source() : binding.source();
                var targetIntent = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, context.dimension(),
                    target.id(), null, -1, 0, 0, 0);
                LOGGER.info("content AI {} -> {} on {}", definition.id(), action.id(), target.id());
                return new AiPlanner.Propose(new ActionIntent(ACTION, 1, source.hand(),
                    ActionIntent.Capability.USE_ITEM, targetIntent, source.item(),
                    source, null, CombatRules.RULES_REVISION,
                    Map.of(ACTION_PARAM, action.id())));
            }
        }
        return nativeFallback(context, action == null ? "NO_CONTENT_IN_RANGE" : "NO_CONTENT_OPTION");
    }

    /** Keep vanilla adapters usable when the content table has no usable action at this distance. */
    private static AiPlanner.Decision nativeFallback(cc.sighs.dndturn.domain.ai.AiDecisionContext context, String reason) {
        var option = context.options().stream()
            .filter(o -> o.available() && !o.binding().id().equals(ACTION))
            .filter(o -> o.semantics().contains(AiAffordance.DAMAGE) || o.semantics().contains(AiAffordance.CONTROL)
                || o.semantics().contains(AiAffordance.DEBUFF)).findFirst().orElse(null);
        if (option != null) {
            var target = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, context.dimension(),
                option.target(), null, -1, 0, 0, 0);
            return new AiPlanner.Propose(option.binding().invocation(target));
        }
        if (context.movementOpportunity() != null) return new AiPlanner.Propose(context.movementOpportunity());
        return new AiPlanner.EndTurn(reason);
    }

    private static boolean supported(MobAction action) {
        return action.kind() == MobAction.Kind.ATTACK || action.kind() == MobAction.Kind.SAVE_AREA
            || action.kind() == MobAction.Kind.SUMMON;
    }

    static MobDefinition contentDefinition(LiveActorContext actor) {
        return snapshot(actor).mobs().get(
            BuiltInRegistries.ENTITY_TYPE.getKey(actor.body().getType()).toString());
    }

    private static ContentSnapshot snapshot(LiveActorContext actor) {
        var runtime = ServerRuntime.existingEncounter(actor.level().getServer());
        if (runtime == null) return CharacterContentService.current();
        java.util.UUID encounter = runtime.encounterOf(actor.id());
        var content = encounter == null ? null : EncounterContentCache.server().find(encounter);
        return content == null ? CharacterContentService.current() : content.content();
    }

    static MobAction choose(MobDefinition definition, double distance) {
        return definition.actions().stream()
            .filter(ContentMobActions::supported)
            .filter(a -> distance <= range(a))
            .findFirst().orElse(null);
    }

    private static double range(MobAction action) {
        if (action.kind() == MobAction.Kind.ATTACK)
            return action.rangeTicks() > 0 ? action.rangeTicks() : Math.max(3, action.reachTicks());
        if (action.kind() == MobAction.Kind.SAVE_AREA && action.rangeTicks() > 0) return action.rangeTicks();
        return 3;
    }

    private static final class Adapter extends MinecraftAbilityAdapter {
        Adapter() { super(DEFINITION); }

        @Override
        public String unavailable(LiveActorContext actor, ActionIntent intent, StateView state) {
            MobDefinition def = contentDefinition(actor);
            MobAction action = actionFor(def, intent);
            if (action == null) return "unknown content action";
            Entity raw = actor.level().getEntity(intent.target().entity());
            if (!(raw instanceof LivingEntity target) || !target.isAlive()
                || !state.members().containsKey(target.getUUID()) || action.kind() != MobAction.Kind.SUMMON
                    && !actor.body().hasLineOfSight(target)) return "content target unavailable";
            double distance = actor.body().distanceTo(target);
            if (action.kind() != MobAction.Kind.SUMMON && distance > range(action)) return "content action out of range";
            return null;
        }

        @Override
        public boolean canExecute(LiveActorContext actor, ActionIntent intent, net.minecraft.world.phys.Vec3 feet) {
            Entity raw = actor.level().getEntity(intent.target().entity());
            MobAction action = actionFor(contentDefinition(actor), intent);
            return action != null && raw instanceof LivingEntity living && living.isAlive()
                && (action.kind() == MobAction.Kind.SUMMON || living.distanceToSqr(feet) <= range(action) * range(action));
        }

        @Override
        public cc.sighs.dndturn.domain.ability.GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) {
            return null;
        }

        @Override
        public void start(ActionExecutionCoordinator actions, LiveActorContext actor,
                          ActionExecutionCoordinator.Execution execution) {
            actions.beginStep(actor.body(), execution);
            MobDefinition def = contentDefinition(actor);
            MobAction action = actionFor(def, execution.snapshot().intent());
            if (def == null || action == null) throw new IllegalStateException("content action disappeared");
            LOGGER.info("content action start actor={} action={}", actor.id(), action.id());
            LivingEntity target = (LivingEntity) actor.level().getEntity(execution.snapshot().intent().target().entity());
            String detail;
            if (action.kind() == MobAction.Kind.SUMMON) {
                detail = summon(actor.body(), action);
            } else if (action.kind() == MobAction.Kind.SAVE_AREA) {
                detail = area(actor.body(), target, action);
            } else {
                detail = attack(actor.body(), target, action, def.multiattack());
            }
            actions.finishAction(actor.body(), execution, OperationRecord.Outcome.COMPLETED, detail);
        }

        @Override
        public void tick(ActionExecutionCoordinator actions, LiveActorContext actor,
                         ActionExecutionCoordinator.Execution execution) {
            throw new IllegalStateException("synchronous content mob action");
        }
    }

    private static String attack(LivingEntity actor, LivingEntity target, MobAction action, int multiattack) {
        int attacks = action.kind() == MobAction.Kind.ATTACK ? Math.max(1, multiattack) : 1;
        StringBuilder result = new StringBuilder("action=").append(action.id()).append(" attacks=").append(attacks);
        for (int i = 0; i < attacks && target.isAlive(); i++) {
            int ac = armorClass(target);
            var roll = CombatRules.rollAttack(RANDOM, CombatRules.RollMode.NORMAL, ac, action.attackBonus());
            int amount = 0;
            if (roll.hit()) amount = packet(actor, target, action);
            result.append(" roll=").append(roll.total()).append(" ac=").append(ac)
                .append(" hit=").append(roll.hit()).append(" damage=").append(amount);
            if (amount > 0) target.hurt(actor.level().damageSources().mobAttack(actor), amount);
            if (roll.hit()) {
                boolean applies = true;
                if (action.saveAbility() != null && action.saveDc() > 0)
                    applies = !SaveService.roll(target, action.saveAbility(), action.saveDc(), action.id(), RANDOM).success();
                if (applies) applyCondition(target, action);
            }
        }
        return result.toString();
    }

    private static String area(LivingEntity actor, LivingEntity center, MobAction action) {
        if (action.saveAbility() == null) return "action=" + action.id() + " missing save";
        AABB box = center.getBoundingBox().inflate(Math.max(1, action.radiusTicks()));
        int affected = 0;
        for (LivingEntity target : center.level().getEntitiesOfClass(LivingEntity.class, box,
            e -> e.isAlive() && e != actor)) {
            var save = SaveService.roll(target, action.saveAbility(), action.saveDc(), action.id(), RANDOM);
            int amount = packet(actor, target, action);
            if (save.success() && action.halfOnSave()) amount /= 2;
            else if (save.success()) amount = 0;
            if (amount > 0) target.hurt(actor.level().damageSources().mobAttack(actor), amount);
            if (!save.success()) applyCondition(target, action);
            affected++;
        }
        return "action=" + action.id() + " affected=" + affected;
    }

    private static void applyCondition(LivingEntity target, MobAction action) {
        if (action.condition() == null) return;
        MobEffect effect = switch (action.condition()) {
            case "hunger" -> MobEffects.HUNGER.value();
            case "slowed", "slow" -> MobEffects.SLOWNESS.value();
            case "weakness" -> MobEffects.WEAKNESS.value();
            case "poisoned", "poison" -> MobEffects.POISON.value();
            case "mining_fatigue" -> MobEffects.MINING_FATIGUE.value();
            case "levitating", "levitation" -> MobEffects.LEVITATION.value();
            case "blinded", "blindness" -> MobEffects.BLINDNESS.value();
            default -> null;
        };
        if (effect == null) return;
        int duration = Math.max(20, (action.durationRounds() > 0 ? action.durationRounds() : 1) * 20);
        target.addEffect(new MobEffectInstance(net.minecraft.core.Holder.direct(effect), duration, 0));
    }

    private static String summon(LivingEntity actor, MobAction action) {
        if (action.summonId() == null || action.summonCount() <= 0) return "action=" + action.id() + " no summon";
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
            net.minecraft.resources.Identifier.parse(action.summonId())).orElse(null);
        if (type == null) return "action=" + action.id() + " unknown summon " + action.summonId();
        int count = 0;
        for (int i = 0; i < action.summonCount(); i++) {
            Entity entity = type.create(actor.level(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (entity == null) continue;
            entity.snapTo(actor.getX() + (i % 3) * .8, actor.getY(), actor.getZ() + (i / 3) * .8,
                actor.getYRot(), 0);
            if (actor.level().addFreshEntity(entity)) count++;
        }
        return "action=" + action.id() + " summoned=" + count;
    }

    private static int packet(LivingEntity actor, LivingEntity target, MobAction action) {
        if (action.damage().isEmpty()) return 0;
        String targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        MobDefinition targetDef = snapshot(new LiveActorContext(target)).mobs().get(targetId);
        DefenseProfile defense = targetDef == null ? new DefenseProfile(Set.of(), Set.of(), Set.of(), false)
            : new DefenseProfile(targetDef.resistances(), targetDef.immunities(), targetDef.vulnerabilities(), false);
        DamagePacket damage = new DamagePacket(action.damage());
        return damage.total(damage.resolve(defense, RANDOM));
    }

    private static int armorClass(LivingEntity target) {
        var service = ServerRuntime.existingEncounter(target.level().getServer());
        if (service == null) return Math.max(0, target.getArmorValue());
        int player = PlayerArmorRules.armorClass(target,
            service.actorStates().state(target.getUUID()).persistent().character());
        return player >= 0 ? player : RuleResolver.armorClass(service.actorStates().snapshots().defense(target));
    }

    private static MobAction actionFor(MobDefinition definition, ActionIntent intent) {
        if (definition == null) return null;
        String id = intent.parameters().get(ACTION_PARAM);
        return definition.actions().stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }
}
