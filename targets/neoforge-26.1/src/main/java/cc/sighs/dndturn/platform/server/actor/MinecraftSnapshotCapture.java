package cc.sighs.dndturn.platform.server.actor;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.platform.server.fact.MinecraftFactProviders;

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
import cc.sighs.dndturn.platform.server.budget.AbilityWorkBudget;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.spatial.MinecraftCoordinates;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

/** Server-thread capture boundary. No native reference survives in a returned value. */
public final class MinecraftSnapshotCapture {
    private LiveActorContext.Facts facts;
    public MinecraftSnapshotCapture(LiveActorContext.Facts facts) { this.facts = Objects.requireNonNull(facts); }
    private boolean bound;
    public void bindFacts(LiveActorContext.Facts facts) {
        if (bound) throw new IllegalStateException("snapshot facts already bound");
        this.facts = Objects.requireNonNull(facts); bound = true;
    }
    public LiveActorContext context(LivingEntity actor) { return new LiveActorContext(actor, facts); }
    public static MinecraftSnapshotCapture forActor(LiveActorContext actor) { return new MinecraftSnapshotCapture(actor.requireFacts()); }
    public ActorSnapshot captureActor(LiveActorContext input) {
        var actor = context(input.body());
        return captureActor(actor, Set.of());
    }
    public FactSlice captureFacts(LiveActorContext input, ReadContract reads) {
        var actor = context(input.body());
        actor.verifyCurrent();
        if (reads.keys().isEmpty()) return new FactSlice(Map.of(), Map.of());
        // Continuous contributions depend on the expanded grant graph. Keep that path
        // until its dependency closure can be obtained without discovering sources.
        if (!AbilityAdapterRegistry.continuous().isEmpty())
            return captureActor(actor, reads.keys()).facts().restrict(reads);
        var state = facts.state(actor.id());
        if (facts.fault(actor.id()) != null) throw new IllegalStateException("actor rules quarantined");
        for (var effect : state.runtime().effects().values()) AbilityAdapterRegistry.effects().require(effect.definition());
        var definition = ActorDefinitions.capture(actor);
        var keys = new HashSet<>(reads.keys());
        if (keys.remove(ActorFacts.ARMOR_CLASS)) {
            keys.add(ActorFacts.ARMOR_EFFECTIVE); keys.add(ActorFacts.HOLDING_SHIELD);
        }
        if (keys.remove(ActorFacts.DAMAGE_REDUCTION)) keys.add(ActorFacts.TOUGHNESS_EFFECTIVE);
        keys.removeAll(definition.defaults().keys());
        var resources = ActorCompilation.resources(state, keys);
        keys.removeAll(resources.keys());
        var nativeFacts = MinecraftFactProviders.capture(actor, new ReadContract(keys)).merge(resources);
        var compiled = SnapshotCompiler.compileFacts(definition, nativeFacts).restrict(reads);
        var modified = new HashMap<FactKey<?>, Object>();
        var modifiedKeys = new HashSet<FactKey<Double>>();
        for (var effect : state.runtime().effects().values()) for (var modifier : effect.definition().modifiers())
            modifiedKeys.add(new FactKey<>(modifier.stat().id(), Double.class));
        for (var key : modifiedKeys) if (reads.keys().contains(key)) {
            try { modified.put(key, ActorCompilation.stat(state, key.id(), compiled.read(reads, key))); }
            catch (FactSlice.MissingFact unavailable) { /* Preserve missing evidence. */ }
        }
        return compiled.derive(modified);
    }
    private ActorSnapshot captureActor(LiveActorContext input, Set<FactKey<?>> requested) {
        var actor = context(input.body());
        var grants = captureReactionSources(actor);
        var serviceState = facts;
        if (serviceState.fault(actor.id()) != null) throw new IllegalStateException("actor rules quarantined");
        return compile(actor, grants, serviceState.state(actor.id()), requested);
    }
    /** Source discovery is sampled once for a bounded, synchronous, pure reaction chain.
     * Actor contributions are recompiled per wave; this value grants no world execution. */
    List<AbilityBinding> captureReactionSources(LiveActorContext input) {
        var actor = context(input.body());
        actor.verifyCurrent();
        var service = facts;
        service.requireAbilityWork(actor.id(), actor.id(), AbilityWorkBudget.Work.QUERY);
        var grants = new ArrayList<AbilityBinding>();
        for (var definition : AbilityAdapterRegistry.definitions().all()) if (AbilityAdapterRegistry.hasNativeFacts(definition.id())) {
            service.requireAbilityWork(actor.id(), actor.id(), AbilityWorkBudget.Work.CANDIDATE);
            for (var evidence : AbilityAdapterRegistry.facts(definition.id()).checkedSources(actor))
                grants.add(new AbilityBinding(definition, AbilityGrant.nativeGrant(actor.id(), evidence)));
        }
        return List.copyOf(grants);
    }
    ActorSnapshot captureReactionWave(LiveActorContext input, List<AbilityBinding> nativeSources) {
        var actor = context(input.body());
        actor.verifyCurrent();
        return compile(actor, nativeSources);
    }
    public ActorSnapshot capture(LiveActorContext input, StateView state) {
        var actor = context(input.body());
        actor.verifyCurrent();
        var grants = new ArrayList<AbilityBinding>();
        for (var definition : AbilityAdapterRegistry.definitions().all()) {
            if (!AbilityAdapterRegistry.hasNativeFacts(definition.id())) continue;
            var service = facts;
            service.requireAbilityWork(actor.id(), state.id(), AbilityWorkBudget.Work.CANDIDATE);
            for (var evidence : AbilityAdapterRegistry.facts(definition.id()).checkedSources(actor))
                grants.add(new AbilityBinding(definition, AbilityGrant.nativeGrant(actor.id(), evidence)));
        }
        return compile(actor, grants);
    }
    public ActorSnapshot capture(LiveActorContext input, AbilityDefinition definition) {
        var actor = context(input.body());
        actor.verifyCurrent();
        if (!AbilityAdapterRegistry.continuous().isEmpty()) return captureActor(actor);
        var grants = AbilityAdapterRegistry.hasNativeFacts(definition.id())
                ? AbilityAdapterRegistry.facts(definition.id()).checkedSources(actor).stream()
                    .map(e -> new AbilityBinding(definition, AbilityGrant.nativeGrant(actor.id(), e))).toList()
                : List.<AbilityBinding>of();
        return compile(actor, grants);
    }
    private ActorSnapshot compile(LiveActorContext input, List<AbilityBinding> grants) {
        var actor = context(input.body());
        var state = facts.state(actor.id());
        var fault = facts.fault(actor.id());
        if (fault != null) throw new IllegalStateException("actor rules quarantined: " + fault);
        return compile(actor, grants, state);
    }
    void validateActorState(LiveActorContext input, ActorStates.State candidate) {
        var actor = context(input.body());
        compile(actor, List.of(), candidate);
    }
    private ActorSnapshot compile(LiveActorContext input, List<AbilityBinding> grants, ActorStates.State state) {
        var actor = context(input.body());
        return compile(actor, grants, state, Set.of());
    }
    private ActorSnapshot compile(LiveActorContext input, List<AbilityBinding> grants, ActorStates.State state, Set<FactKey<?>> requested) {
        var actor = context(input.body());
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
    private List<EffectSnapshot> effects(LiveActorContext actor, ActorStates.State state) {
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
    public ResolutionContext capture(LiveActorContext input, ActorSnapshot snapshot, ActionIntent intent,
                                            StateView state, GrantEvidence expected,
                                            ResolutionContext.Stage stage) {
        return capture(input, snapshot, intent, state, expected, stage, this::captureFacts);
    }
    /** The reader may reuse facts only within the caller's synchronous read-only boundary. */
    public ResolutionContext capture(LiveActorContext input, ActorSnapshot snapshot, ActionIntent intent,
                                            StateView state, GrantEvidence expected,
                                            ResolutionContext.Stage stage,
                                            java.util.function.BiFunction<LiveActorContext, ReadContract, FactSlice> targetFacts) {
        var actor = context(input.body());
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
        boolean domain = true;
        if (intent.target().facet() != null) {
            if (!nativeFacts.supportsBodyFacet() || !(target instanceof LivingEntity)) rejection = "body facet unsupported by ability";
            else {
                var hit = BodyTargets.resolve((LivingEntity)target, intent.target().facet());
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
        var invocation = new AbilityInvocation(actor.id(), selected, intent, snapshot.revision());
        return new ResolutionContext(new CombatantSnapshot(snapshot, state.members().get(actor.id())), cc.sighs.dndturn.domain.resolution.EncounterInput.capture(state, invocation),
                invocation, world,
                target instanceof LivingEntity living && living.isAlive()
                    ? targetFacts.apply(context(living), definition.reads().target())
                    : new FactSlice(Map.of(), Map.of()), stage);
    }
    /** Native defense values sampled together before rule resolution. */
    public FactSlice defense(LivingEntity target) {
        context(target).verifyCurrent();
        double armor = target.getArmorValue(), toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        boolean shield = target.getMainHandItem().is(Items.SHIELD) || target.getOffhandItem().is(Items.SHIELD);
        var state = facts.state(target.getUUID());
        for (var effect : state.runtime().effects().values()) AbilityAdapterRegistry.effects().require(effect.definition());
        var continuous = AbilityAdapterRegistry.continuous().isEmpty() ? List.<EffectDefinition.Modifier>of()
                : AbilityAdapterRegistry.continuous().expand(captureActor(context(target)).abilities(), AbilityAdapterRegistry.definitions()).modifiers();
        return new FactSlice(Map.of(RuleFacts.ARMOR, armor, RuleFacts.TOUGHNESS, toughness, RuleFacts.SHIELD, shield,
                RuleFacts.RESOLVED_AC, ActorCompilation.stat(state, ActorFacts.ARMOR_CLASS.id(), CombatRules.armorClass(armor, shield), continuous),
                RuleFacts.RESOLVED_REDUCTION, ActorCompilation.stat(state, ActorFacts.DAMAGE_REDUCTION.id(), CombatRules.damageReduction(toughness), continuous)), Map.of());
    }
}
