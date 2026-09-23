package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.platform.server.damage.BodyTargets;

import cc.sighs.dndturn.application.actor.ActorCompilation;
import cc.sighs.dndturn.application.actor.SnapshotCompiler;
import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.AbilityGrant;
import cc.sighs.dndturn.domain.ability.AbilityInvocation;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EffectSnapshot;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.domain.resolution.CombatantSnapshot;
import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.AbilityWorkBudget;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.action.MinecraftCoordinates;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

/** Server-thread capture boundary. No native reference survives in a returned value. */
public final class MinecraftSnapshotCapture {
    private MinecraftSnapshotCapture() {}
    public static ActorSnapshot captureActor(LiveActorContext actor) {
        return captureActor(actor, Set.of());
    }
    public static FactSlice captureFacts(LiveActorContext actor, ReadContract reads) {
        actor.verifyCurrent();
        if (reads.keys().isEmpty()) return new FactSlice(Map.of(), Map.of());
        return captureActor(actor, reads.keys()).facts().restrict(reads);
    }
    private static ActorSnapshot captureActor(LiveActorContext actor, Set<FactKey<?>> requested) {
        var grants = captureReactionSources(actor);
        var serviceState = ServerRuntime.encounters(actor.level().getServer()).actorStates();
        if (serviceState.fault(actor.id()) != null) throw new IllegalStateException("actor rules quarantined");
        return compile(actor, grants, serviceState.state(actor.id()), requested);
    }
    /** Source discovery is sampled once for a bounded, synchronous, pure reaction chain.
     * Actor contributions are recompiled per wave; this value grants no world execution. */
    static List<AbilityBinding> captureReactionSources(LiveActorContext actor) {
        actor.verifyCurrent();
        var service = ServerRuntime.encounters(actor.level().getServer());
        service.requireAbilityWork(actor.id(), actor.id(), AbilityWorkBudget.Work.QUERY);
        var grants = new ArrayList<AbilityBinding>();
        for (var definition : AbilityAdapterRegistry.definitions().all()) if (AbilityAdapterRegistry.hasNativeFacts(definition.id())) {
            service.requireAbilityWork(actor.id(), actor.id(), AbilityWorkBudget.Work.CANDIDATE);
            for (var evidence : AbilityAdapterRegistry.facts(definition.id()).checkedSources(actor))
                grants.add(new AbilityBinding(definition, AbilityGrant.nativeGrant(actor.id(), evidence)));
        }
        return List.copyOf(grants);
    }
    static ActorSnapshot captureReactionWave(LiveActorContext actor, List<AbilityBinding> nativeSources) {
        actor.verifyCurrent();
        return compile(actor, nativeSources);
    }
    public static ActorSnapshot capture(LiveActorContext actor, EncounterAuthority.StateView state) {
        actor.verifyCurrent();
        var grants = new ArrayList<AbilityBinding>();
        for (var definition : AbilityAdapterRegistry.definitions().all()) {
            if (!AbilityAdapterRegistry.hasNativeFacts(definition.id())) continue;
            var service = ServerRuntime.encounters(actor.level().getServer());
            service.requireAbilityWork(actor.id(), state.id(), AbilityWorkBudget.Work.CANDIDATE);
            for (var evidence : AbilityAdapterRegistry.facts(definition.id()).checkedSources(actor))
                grants.add(new AbilityBinding(definition, AbilityGrant.nativeGrant(actor.id(), evidence)));
        }
        return compile(actor, grants);
    }
    public static ActorSnapshot capture(LiveActorContext actor, AbilityDefinition definition) {
        actor.verifyCurrent();
        if (!AbilityAdapterRegistry.continuous().isEmpty()) return captureActor(actor);
        var grants = AbilityAdapterRegistry.hasNativeFacts(definition.id())
                ? AbilityAdapterRegistry.facts(definition.id()).checkedSources(actor).stream()
                    .map(e -> new AbilityBinding(definition, AbilityGrant.nativeGrant(actor.id(), e))).toList()
                : List.<AbilityBinding>of();
        return compile(actor, grants);
    }
    private static ActorSnapshot compile(LiveActorContext actor, List<AbilityBinding> grants) {
        var state = ServerRuntime.encounters(actor.level().getServer()).actorStates().state(actor.id());
        var fault = ServerRuntime.encounters(actor.level().getServer()).actorStates().fault(actor.id());
        if (fault != null) throw new IllegalStateException("actor rules quarantined: " + fault);
        return compile(actor, grants, state);
    }
    static void validateActorState(LiveActorContext actor, ActorStates.State candidate) {
        compile(actor, List.of(), candidate);
    }
    private static ActorSnapshot compile(LiveActorContext actor, List<AbilityBinding> grants, ActorStates.State state) {
        return compile(actor, grants, state, Set.of());
    }
    private static ActorSnapshot compile(LiveActorContext actor, List<AbilityBinding> grants, ActorStates.State state, Set<FactKey<?>> requested) {
        for (var effect : state.runtime().effects().values()) AbilityAdapterRegistry.effects().require(effect.definition());
        var definition = ActorDefinitions.capture(actor);
        var bindings = new ArrayList<>(grants);
        for (var intrinsic : definition.intrinsicGrants()) bindings.add(new AbilityBinding(
                AbilityAdapterRegistry.definitions().require(intrinsic.ability(), intrinsic.version()),
                AbilityGrant.nativeGrant(actor.id(), GrantEvidence.intrinsic(definition.id(), definition.version(), actor.id(), actor.instance()))));
        bindings.addAll(ActorCompilation.bindings(actor.id(), actor.instance(), state, AbilityAdapterRegistry.definitions()));
        var expanded = AbilityAdapterRegistry.continuous().expand(bindings, AbilityAdapterRegistry.definitions());
        bindings = new ArrayList<>(expanded.bindings());
        var reads = new HashSet<>(ActorFacts.DECISION.keys());
        reads.addAll(requested);
        for (var binding : bindings) reads.addAll(binding.definition().reads().actorKeys());
        for (var binding : bindings) reads.addAll(AbilityAdapterRegistry.activations().reads(binding).actorKeys());
        for (var effect : state.runtime().effects().values()) for (var modifier : effect.definition().modifiers())
            reads.add(new FactKey<>(modifier.stat().id(), Double.class));
        for (var modifier : expanded.modifiers()) reads.add(new FactKey<>(modifier.stat().id(), Double.class));
        reads.remove(ActorFacts.ARMOR_CLASS); reads.remove(ActorFacts.DAMAGE_REDUCTION);
        reads.removeAll(definition.defaults().keys());
        var resources = ActorCompilation.resources(state, reads);
        reads.removeAll(resources.keys());
        var compiled = SnapshotCompiler.compile(actor.id(), actor.instance(), definition,
                new ActorSnapshot.Revision(UUID.randomUUID(), state.revision(), CombatRules.RULES_REVISION),
                MinecraftFactProviders.capture(actor, new ReadContract(reads)).merge(resources), bindings);
        return new ActorSnapshot(compiled.actor(), compiled.instance(), compiled.definition(), compiled.revision(),
                ActorCompilation.apply(compiled.facts(), state, expanded.modifiers()), compiled.abilities(), effects(actor, state));
    }
    private static List<EffectSnapshot> effects(LiveActorContext actor, ActorStates.State state) {
        if (actor.body().getActiveEffects().size() > 256)
            throw new IllegalStateException("native effect snapshot budget exceeded");
        var result = new ArrayList<EffectSnapshot>();
        state.runtime().effects().values().stream().sorted(Comparator.comparing(EffectInstance::id))
                .map(EffectSnapshot.Tactical::capture).forEach(result::add);
        actor.body().getActiveEffects().stream()
                .sorted(Comparator.comparing(effect -> BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString()))
                .forEach(effect -> result.add(new EffectSnapshot.Vanilla(
                        BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString(),
                        Math.addExact(effect.getAmplifier(), 1), effect.getDuration(), effect.isAmbient(), effect.isVisible())));
        return List.copyOf(result);
    }
    public static ResolutionContext capture(LiveActorContext actor, ActorSnapshot snapshot, ActionIntent intent,
                                            EncounterAuthority.StateView state, GrantEvidence expected,
                                            ResolutionContext.Stage stage) {
        actor.verifyCurrent();
        if (!actor.id().equals(snapshot.actor()) || !actor.instance().equals(snapshot.instance()))
            throw ActionFailure.source("actor instance replaced");
        var definition = AbilityAdapterRegistry.definitions().require(intent);
        var binding = snapshot.abilities().stream().filter(b -> b.id().equals(definition.id())
                && b.version() == definition.version() && b.source().equals(expected)).findFirst();
        boolean owned = binding.isPresent();
        var selected = binding.orElseGet(() -> new AbilityBinding(definition, AbilityGrant.nativeGrant(actor.id(), expected)));
        var nativeFacts = AbilityAdapterRegistry.facts(definition.id());
        String rejection = owned ? null : "ability grant revoked or replaced";
        boolean dimension = intent.target().dimension().equals(actor.level().dimension().identifier().toString());
        var cell = intent.target().cell();
        var target = intent.target().entity() == null ? null : actor.level().getEntity(intent.target().entity());
        boolean loaded = cell == null ? intent.target().entity() == null || target != null : actor.level().hasChunkAt(MinecraftCoordinates.pos(cell));
        boolean domain = cell == null || state.region().containsPoint(cell.x() + .5, cell.y() + .5, cell.z() + .5);
        if (intent.target().facet() != null) {
            if (!nativeFacts.supportsBodyFacet() || !(target instanceof LivingEntity)) rejection = "body facet unsupported by ability";
            else {
                var hit = BodyTargets.resolve((LivingEntity)target, intent.target().facet());
                var center = hit.body().getBoundingBox().getCenter();
                domain &= state.region().containsPoint(center.x, center.y, center.z);
                if (intent.capability() == ActionIntent.Capability.ATTACK
                        && cc.sighs.dndturn.platform.server.damage.DamageReceivers.server().find(hit) == null)
                    rejection = "body facet receiver unsupported";
            }
        }
        if (rejection == null && dimension && loaded && domain) rejection = nativeFacts.unavailable(actor, intent, state);
        boolean reach = owned && dimension && loaded && domain && rejection == null && nativeFacts.canExecute(actor, intent, actor.body().position());
        var world = new FactSlice(Map.of(RuleFacts.SOURCE_VALID, owned,
                RuleFacts.NATIVE_REJECTION, rejection == null ? "" : rejection,
                RuleFacts.IN_REACH, reach,
                RuleFacts.ACTOR_PLAYER, actor.body() instanceof net.minecraft.world.entity.player.Player,
                RuleFacts.TARGET_PLAYER, target instanceof net.minecraft.world.entity.player.Player,
                RuleFacts.TARGET_LIVING, target instanceof LivingEntity living && living.isAlive(),
                RuleFacts.DIMENSION_MATCH, dimension, RuleFacts.TARGET_LOADED, loaded, RuleFacts.TARGET_DOMAIN, domain), Map.of());
        var extraKeys = new HashSet<>(definition.reads().keys());
        extraKeys.removeAll(RuleFacts.AVAILABILITY.keys());
        if (!extraKeys.isEmpty()) world = world.merge(nativeFacts.captureAdditional(actor, intent, state, new ReadContract(extraKeys)));
        return new ResolutionContext(new CombatantSnapshot(snapshot, state.members().get(actor.id())), state,
                new AbilityInvocation(actor.id(), selected, intent, snapshot.revision()), world,
                target instanceof LivingEntity living && living.isAlive()
                    ? captureFacts(new LiveActorContext(living), definition.reads().target())
                    : new FactSlice(Map.of(), Map.of()), stage);
    }
    /** Native defense values sampled together before rule resolution. */
    public static FactSlice defense(LivingEntity target) {
        new LiveActorContext(target).verifyCurrent();
        double armor = target.getArmorValue(), toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        boolean shield = target.getMainHandItem().is(Items.SHIELD) || target.getOffhandItem().is(Items.SHIELD);
        var state = ServerRuntime.encounters(target.level().getServer()).actorStates().state(target.getUUID());
        for (var effect : state.runtime().effects().values()) AbilityAdapterRegistry.effects().require(effect.definition());
        var continuous = AbilityAdapterRegistry.continuous().isEmpty() ? List.<EffectDefinition.Modifier>of()
                : AbilityAdapterRegistry.continuous().expand(captureActor(new LiveActorContext(target)).abilities(), AbilityAdapterRegistry.definitions()).modifiers();
        return new FactSlice(Map.of(RuleFacts.ARMOR, armor, RuleFacts.TOUGHNESS, toughness, RuleFacts.SHIELD, shield,
                RuleFacts.RESOLVED_AC, ActorCompilation.stat(state, ActorFacts.ARMOR_CLASS.id(), CombatRules.armorClass(armor, shield), continuous),
                RuleFacts.RESOLVED_REDUCTION, ActorCompilation.stat(state, ActorFacts.DAMAGE_REDUCTION.id(), CombatRules.damageReduction(toughness), continuous)), Map.of());
    }
}
