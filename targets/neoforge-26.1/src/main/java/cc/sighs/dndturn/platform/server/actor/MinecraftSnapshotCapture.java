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
import cc.sighs.dndturn.domain.actor.NativeActorObservation;
import cc.sighs.dndturn.domain.actor.StatResolution;
import cc.sighs.dndturn.application.inspection.ActorViews;
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
    /** Bounded synchronous evidence, never an execution permit or a native revision clock. */
    private record ReadStamp(UUID actor, UUID instance, long actorRevision, long tick, int selected,
                             long mainGeneration, long offGeneration) {}
    private ReadStamp stamp(LiveActorContext actor) {
        actor.verifyCurrent();
        return new ReadStamp(actor.id(), actor.instance(), facts.state(actor.id()).revision(), actor.level().getGameTime(),
                ActorEquipment.address(actor, ActionIntent.Hand.MAIN_HAND),
                ActorEquipment.generation(actor, ActionIntent.Hand.MAIN_HAND), ActorEquipment.generation(actor, ActionIntent.Hand.OFF_HAND));
    }
    private <T> T read(LiveActorContext input, java.util.function.Supplier<T> capture) {
        var actor = context(input.body());
        var before = stamp(actor);
        T result = capture.get();
        if (!before.equals(stamp(actor))) throw ActionFailure.source("actor changed during bounded capture; refresh query");
        return result;
    }
    public ActorSnapshot captureActor(LiveActorContext input) {
        var actor = context(input.body());
        return read(actor, () -> captureActor(actor, Set.of()));
    }
    /** Full explicit character read; normal discovery continues to use demand-driven facts. */
    public ActorViews.CharacterSheet captureCharacter(LiveActorContext input) {
        return read(input, () -> captureCharacterWithinRead(input));
    }
    private ActorViews.CharacterSheet captureCharacterWithinRead(LiveActorContext input) {
        var actor = context(input.body()); actor.verifyCurrent();
        if (facts.fault(actor.id()) != null) throw new IllegalStateException("actor rules quarantined");
        var state = facts.state(actor.id());
        var observed = NativeActorCapture.capture(actor, UUID.randomUUID());
        var derivations = new LinkedHashMap<String, StatResolution>();
        var requested = Set.<FactKey<?>>of(ActorFacts.HEALTH, ActorFacts.MAX_HEALTH, ActorFacts.MAX_HEALTH_BASE,
                ActorFacts.ABSORPTION, ActorFacts.ALIVE, ActorFacts.DIMENSION, ActorFacts.X, ActorFacts.Y, ActorFacts.Z, ActorFacts.YAW, ActorFacts.PITCH);
        var snapshot = compile(actor, captureReactionSources(actor), state, requested, observed, derivations);
        var behavior = actor.body() instanceof net.minecraft.world.entity.Mob mob
                ? cc.sighs.dndturn.platform.server.ai.AiDefinitions.definition(snapshot.definition(), mob) : null;
        actor.verifyCurrent();
        if (facts.state(actor.id()).revision() != state.revision()) throw new IllegalStateException("actor changed during character capture");
        return new ActorViews.CharacterSheet(snapshot, state.persistent(), observed, derivations, behavior);
    }
    private boolean requiresExpandedCapture(LiveActorContext actor) {
        if (AbilityAdapterRegistry.continuous().isEmpty()) return false;
        // Native continuous sources can be dynamic. Their providers must still be sampled.
        if (AbilityAdapterRegistry.definitions().all().stream().anyMatch(d -> d.activation() == AbilityDefinition.Activation.CONTINUOUS
                && AbilityAdapterRegistry.hasNativeFacts(d.id()))) return true;
        if (ActorDefinitions.capture(actor).intrinsicGrants().stream().anyMatch(g ->
                AbilityAdapterRegistry.definitions().require(g.ability(), g.version()).activation() == AbilityDefinition.Activation.CONTINUOUS)) return true;
        return ActorCompilation.bindings(actor.id(), actor.instance(), facts.state(actor.id()), AbilityAdapterRegistry.definitions())
                .stream().anyMatch(b -> b.definition().activation() == AbilityDefinition.Activation.CONTINUOUS);
    }
    public FactSlice captureFacts(LiveActorContext input, ReadContract reads) {
        return read(input, () -> captureFactsWithinRead(input, reads));
    }
    private FactSlice captureFactsWithinRead(LiveActorContext input, ReadContract reads) {
        var actor = context(input.body());
        actor.verifyCurrent();
        if (reads.keys().isEmpty()) return new FactSlice(Map.of(), Map.of());
        // Continuous contributions depend on the expanded grant graph. Keep that path
        // until its dependency closure can be obtained without discovering sources.
        if (requiresExpandedCapture(actor))
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
        return read(input, () -> captureWithinRead(input, state));
    }
    private ActorSnapshot captureWithinRead(LiveActorContext input, StateView state) {
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
        return read(input, () -> captureWithinRead(input, definition));
    }
    private ActorSnapshot captureWithinRead(LiveActorContext input, AbilityDefinition definition) {
        var actor = context(input.body());
        actor.verifyCurrent();
        if (requiresExpandedCapture(actor)) return captureActor(actor);
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
        return compile(input, grants, state, requested, null, null);
    }
    private ActorSnapshot compile(LiveActorContext input, List<AbilityBinding> grants, ActorStates.State state,
                                  Set<FactKey<?>> requested, NativeActorObservation observed, Map<String, StatResolution> derivations) {
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
        if (observed != null) for (var descriptor : definition.resources().values())
            if (descriptor.capacityFact() != null) reads.add(new FactKey<>(descriptor.capacityFact(), Double.class));
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
                new ActorSnapshot.Revision(observed == null ? UUID.randomUUID() : observed.capture(), state.revision(), CombatRules.RULES_REVISION),
                (observed == null ? MinecraftFactProviders.capture(actor, new ReadContract(reads))
                        : MinecraftFactProviders.captureObserved(actor, new ReadContract(reads), observed)).merge(resources), bindings);
        if (derivations != null) {
            var keys = new LinkedHashSet<String>(List.of(ActorFacts.ARMOR_CLASS.id(), ActorFacts.DAMAGE_REDUCTION.id()));
            for (var effect : state.runtime().effects().values()) for (var modifier : effect.definition().modifiers()) keys.add(modifier.stat().id());
            for (var modifier : expanded.modifiers()) keys.add(modifier.stat().id());
            for (var id : keys) {
                var key = new FactKey<>(id, Double.class);
                try { derivations.put(id, ActorCompilation.explain(state, id, compiled.facts().read(new ReadContract(Set.of(key)), key), expanded)); }
                catch (FactSlice.MissingFact unavailable) { /* Unknown remains unknown, never an invented baseline. */ }
            }
        }
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
                .forEach(effect -> result.add(facts.effect(actor.body(), effect)));
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
        if (!actor.id().equals(snapshot.actor()) || !actor.instance().equals(snapshot.instance())
                || facts.state(actor.id()).revision() != snapshot.revision().actorState())
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
                RuleFacts.TARGET_HOSTILE, target != null && facts.hostile(state.id(), actor.id(), target.getUUID()),
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
        var reads = new ReadContract(Set.of(ActorFacts.ARMOR_EFFECTIVE, ActorFacts.TOUGHNESS_EFFECTIVE,
                ActorFacts.HOLDING_SHIELD, ActorFacts.ARMOR_CLASS, ActorFacts.DAMAGE_REDUCTION));
        var captured = captureFacts(context(target), reads);
        return new FactSlice(Map.of(
                RuleFacts.ARMOR, captured.read(reads, ActorFacts.ARMOR_EFFECTIVE),
                RuleFacts.TOUGHNESS, captured.read(reads, ActorFacts.TOUGHNESS_EFFECTIVE),
                RuleFacts.SHIELD, captured.read(reads, ActorFacts.HOLDING_SHIELD),
                RuleFacts.RESOLVED_AC, captured.read(reads, ActorFacts.ARMOR_CLASS),
                RuleFacts.RESOLVED_REDUCTION, captured.read(reads, ActorFacts.DAMAGE_REDUCTION)), Map.of());
    }
}
