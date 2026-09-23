package cc.sighs.dndturn.platform.server.builtin.creeper;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.actor.TriggeredAbilityInvocation;
import cc.sighs.dndturn.domain.ai.AiDefinition;
import cc.sighs.dndturn.domain.ai.AiPlanner;
import cc.sighs.dndturn.domain.ai.PerceptionSnapshot;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.effect.EffectSnapshot;
import cc.sighs.dndturn.domain.effect.EffectTransition;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.platform.mixin.server.effect.CreeperEffectAccess;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.ability.MinecraftAbilityAdapter;
import cc.sighs.dndturn.platform.server.action.ActionExecutionCoordinator;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.actor.ActorLifecycleAdapters;
import cc.sighs.dndturn.platform.server.ai.AiDefinitions;
import cc.sighs.dndturn.platform.server.effect.TriggeredAbilities;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import cc.sighs.dndturn.platform.server.world.CloudOrigins;
import cc.sighs.dndturn.platform.server.world.WorldOutcomeHooks;
import java.util.*;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/** Built-in provider, using the same registries and command/ability boundaries as extensions. */
public final class CreeperAbilities {
    public static final String FUSE = "dndturn:creeper_fuse", CHARGE = "dndturn:creeper_charge",
            EXPLODE = "dndturn:creeper_explode", THRESHOLD = "dndturn:creeper_fuse_threshold";
    public static final EffectDefinition DEFINITION = new EffectDefinition(FUSE, 1, EffectDefinition.Clock.EXPLICIT,
            EffectDefinition.Stacking.STACK, 3, false, true, List.of(), List.of(new EffectDefinition.Grant(THRESHOLD, 1, 3, 1)),
            Set.of(), EffectDefinition.InstancePolicy.SINGLE_PER_TARGET, EffectDefinition.Overflow.REJECT, EffectDefinition.Refresh.KEEP);
    private CreeperAbilities() {}
    private static AbilityDefinition definition(String id, AbilityDefinition.Activation activation, ActionCost cost, ActionIntent.TargetKind target) {
        return new AbilityDefinition(id, 1, id, ActionIntent.Capability.USE_ITEM, Set.of(target), activation,
                cost, RuleFacts.AVAILABILITY, id, AbilityDefinition.TargetPolicy.NATIVE_INTERACTION);
    }
    public static void register() {
        WorldOutcomeHooks.register("dndturn:creeper", new WorldOutcomeHooks.Observer() {
            public boolean admitSpawn(net.minecraft.world.entity.Entity entity) { return CreeperExplosion.admitSpawn(entity); }
            public void inserted(net.minecraft.world.entity.Entity entity) { CreeperExplosion.inserted(entity); }
            public boolean authorizesDamage(net.minecraft.world.entity.LivingEntity target, net.minecraft.world.damagesource.DamageSource source) { return CreeperExplosion.allows(target, source); }
        });
        CloudOrigins.register("dndturn:creeper", new CloudOrigins.Provider() {
            public boolean tagged(net.minecraft.world.entity.Entity entity) { return CreeperClouds.tagged(entity); }
            public UUID domain(net.minecraft.world.entity.Entity entity) { return CreeperClouds.domain(entity); }
            public boolean confirmed(net.minecraft.world.entity.Entity entity, EncounterRuntime runtime) { return CreeperClouds.confirmed(entity, runtime); }
        });
        ActorLifecycleAdapters.register(Creeper.class, new ActorLifecycleAdapters.Adapter() {
            public void capture(EncounterRuntime service, net.minecraft.world.entity.LivingEntity body) {
                if (!body.isAlive() || body.isRemoved() || service.encounterOf(body.getUUID()) == null) return;
                var access = (CreeperEffectAccess)body;
                var state = service.actorStates().state(body.getUUID());
                if (fuse(state) != null || access.dndturn$swell() == 0) return;
                int maximum = access.dndturn$maxSwell();
                if (maximum < 1) throw new IllegalArgumentException("invalid native fuse");
                int stacks = Math.min(3, (int)((long)access.dndturn$swell() * 3 / maximum));
                if (stacks > 0) {
                    UUID operation = UUID.randomUUID();
                    service.actorStates().command(new LiveActorContext(body), new ActorStates.Command(operation, body.getUUID(), state.revision(),
                            List.of(new ActorStates.Apply(new EffectInstance(operation, operation, DEFINITION, stacks, 0,
                                    state.revision() + 1, 1, body.getUUID(), CHARGE)))));
                }
                access.dndturn$swell(0); access.dndturn$oldSwell(0);
            }
            public void release(EncounterRuntime service, net.minecraft.world.entity.LivingEntity body) {
                if (!body.isAlive() || body.isRemoved()) return;
                var state = service.actorStates().state(body.getUUID()); var fuse = fuse(state);
                if (fuse == null) return;
                var access = (CreeperEffectAccess)body; int maximum = access.dndturn$maxSwell();
                if (maximum < 1) throw new IllegalArgumentException("invalid native fuse");
                int nativeTicks = fuse.stacks() == 3 ? maximum - 1 : (int)((long)maximum * fuse.stacks() / 3);
                // Native ignited/swell direction retain their vanilla ownership.
                service.actorStates().command(new LiveActorContext(body), new ActorStates.Command(UUID.randomUUID(), body.getUUID(), state.revision(),
                        List.of(new ActorStates.Remove(fuse.id()))));
                access.dndturn$swell(nativeTicks); access.dndturn$oldSwell(nativeTicks);
                for (var invocation : service.actorStates().invocations(body.getUUID()))
                    if (invocation.status() == TriggeredExecutionRecord.Status.PENDING && invocation.invocation().ability().equals(EXPLODE))
                        service.actorStates().invocationStatus(invocation.operation(), TriggeredExecutionRecord.Status.REJECTED, "fuse handed back to native lifetime");
            }
        });
        AbilityAdapterRegistry.effects().register(DEFINITION);
        var threshold = new AbilityDefinition(THRESHOLD, 1, "Fuse threshold", ActionIntent.Capability.USE_ITEM,
                Set.of(ActionIntent.TargetKind.SELF), AbilityDefinition.Activation.TRIGGERED, ActionCost.FREE,
                new ReadContract(Set.of(RuleFacts.SOURCE_VALID)), THRESHOLD, AbilityDefinition.TargetPolicy.NATIVE_INTERACTION);
        AbilityAdapterRegistry.registerActorEffect(threshold,
                new ActivationSpec(AbilityDefinition.Activation.TRIGGERED, ActivationSpec.Event.STACK_CHANGED, null, 0), input -> {
                    var transition = input.event().transition();
                    if (transition == null || !transition.crosses(EffectTransition.Edge.CROSS_UP, 3)
                            || !transition.instance().equals(input.binding().source().grant())) return List.of();
                    return List.of(new TriggeredAbilityInvocation(EXPLODE, 1,
                            new ActionIntent.Target(ActionIntent.TargetKind.SELF, input.dimension(), null, null, -1, 0, 0, 0),
                            TriggeredAbilityInvocation.Timing.OWNER_TURN_END));
                });
        var charge = new Charge(); var explode = new Explode();
        AbilityAdapterRegistry.register(charge.definition(), charge, charge);
        AbilityAdapterRegistry.register(explode.definition(), explode, explode);
        ActorDefinitions.register(new ActorDefinitions.Provider() {
            public String id() { return "dndturn:creeper"; }
            public int priority() { return 0; }
            public boolean matches(LiveActorContext actor) { return actor.body() instanceof Creeper; }
            public ActorDefinition definition(LiveActorContext actor) {
                return new ActorDefinition(id(), 1, Set.of(), new FactSlice(Map.of(), Map.of()),
                        List.of(new EffectDefinition.Grant(CHARGE, 1)));
            }
        });
        AiPlanner.register(CHARGE, c -> {
            int stacks = c.actor().effects().stream().filter(e -> e instanceof EffectSnapshot.Tactical && e.effect().equals(FUSE))
                    .mapToInt(EffectSnapshot::stacks).findFirst().orElse(0);
            if (stacks >= 3 || !c.actionAvailable()) return new AiPlanner.EndTurn("FUSE_SETTLEMENT");
            var target = c.perception().actors().stream().filter(t -> t.player() && t.visible() && t.nativeAttackable())
                    .filter(t -> c.options().stream().anyMatch(o -> o.target().equals(t.id()) && o.available() && o.binding().id().equals(CHARGE)))
                    .min(Comparator.comparingDouble(PerceptionSnapshot.ObservedActor::distanceSquared).thenComparing(PerceptionSnapshot.ObservedActor::id));
            if (target.isPresent()) {
                var option = c.options().stream().filter(o -> o.target().equals(target.get().id()) && o.available() && o.binding().id().equals(CHARGE)).findFirst().orElseThrow();
                return new AiPlanner.Propose(option.binding().invocation(new ActionIntent.Target(ActionIntent.TargetKind.ENTITY,
                        c.dimension(), target.get().id(), null, -1, 0, 0, 0)));
            }
            return c.movementOpportunity() == null ? new AiPlanner.EndTurn("NO_PERCEIVED_TARGET") : new AiPlanner.Propose(c.movementOpportunity());
        });
        AiDefinitions.register("dndturn:creeper", 1, new AiDefinition("dndturn:creeper", 1, CHARGE, Set.of(),
                AiDefinition.TargetPreference.VISIBLE_PLAYERS, AiDefinition.Positioning.GROUND_OPPORTUNITY,
                AiDefinition.ResourcePreference.CONSERVATIVE, AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY,
                ActorFacts.DECISION, new AiDefinition.Provenance(AiDefinition.Source.EXPLICIT_PROVIDER, "dndturn:creeper", 1, "NeoForge 26.1.2.84 exact Creeper")));
    }
    static EffectInstance fuse(ActorStates.State state) {
        return state.runtime().effects().values().stream().filter(e -> e.definition().id().equals(FUSE)).findFirst().orElse(null);
    }
    private abstract static class Adapter extends MinecraftAbilityAdapter {
        Adapter(AbilityDefinition definition) { super(definition); }
        public GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) { return null; }
        public String unavailable(LiveActorContext actor, ActionIntent intent, EncounterAuthority.StateView state) {
            return actor.body() instanceof Creeper ? null : "unsupported Creeper implementation";
        }
        public void tick(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
            throw new IllegalStateException("synchronous Creeper ability");
        }
    }
    private static final class Charge extends Adapter {
        Charge() { super(CreeperAbilities.definition(CHARGE, AbilityDefinition.Activation.MANUAL, ActionCost.USE, ActionIntent.TargetKind.ENTITY)); }
        public String unavailable(LiveActorContext actor, ActionIntent intent, EncounterAuthority.StateView state) {
            var rejected = super.unavailable(actor, intent, state); if (rejected != null) return rejected;
            var target = actor.level().getEntity(intent.target().entity());
            if (!(target instanceof net.minecraft.world.entity.player.Player living) || !living.isAlive()
                    || !state.members().containsKey(target.getUUID()) || !((Creeper)actor.body()).canAttack(living)
                    || !((Creeper)actor.body()).hasLineOfSight(living)) return "charge target unavailable";
            var fuse = fuse(ServerRuntime.encounters(actor.level().getServer()).actorStates().state(actor.id()));
            return fuse != null && fuse.stacks() >= 3 ? "fuse already charged" : null;
        }
        public boolean canExecute(LiveActorContext actor, ActionIntent intent, Vec3 feet) {
            var target = actor.level().getEntity(intent.target().entity());
            return target != null && target.position().distanceToSqr(feet) < 9;
        }
        public void start(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
            var states = ServerRuntime.encounters(actor.level().getServer()).actorStates(); var state = states.state(actor.id()); var fuse = fuse(state);
            var operation = UUID.nameUUIDFromBytes((execution.snapshot().operationId() + ":charge").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ActorStates.Change change = fuse == null ? new ActorStates.Apply(new EffectInstance(operation, operation,
                    DEFINITION, 1, 0, state.revision() + 1, 1, actor.id(), CHARGE)) : new ActorStates.AddStacks(fuse.id(), 1);
            ActorStates.preview(state, List.of(change));
            actions.beginStep(actor.body(), execution);
            states.command(actor, new ActorStates.Command(operation, actor.id(), state.revision(), List.of(change), execution.actionOperation()));
            actions.finishAction(actor.body(), execution, OperationRecord.Outcome.COMPLETED, "fuse charged");
        }
    }
    private static final class Explode extends Adapter {
        Explode() { super(CreeperAbilities.definition(EXPLODE, AbilityDefinition.Activation.TRIGGERED, ActionCost.FREE, ActionIntent.TargetKind.SELF)); }
        public boolean canExecute(LiveActorContext actor, ActionIntent intent, Vec3 feet) { return true; }
        public void start(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) { throw new IllegalStateException("server trigger required"); }
        public void prepareTriggered(LiveActorContext actor, TriggeredExecutionRecord emission) {
            var source = emission.source().source();
            var fuse = fuse(ServerRuntime.encounters(actor.level().getServer()).actorStates().state(actor.id()));
            if (!(actor.body() instanceof Creeper) || fuse == null || fuse.stacks() != 3 || !fuse.id().equals(source.grant())
                    || fuse.grantRevision() != source.revision()) throw new IllegalStateException("fuse trigger revoked");
            CreeperExplosion.validate((Creeper)actor.body());
        }
        public OperationRecord.Outcome executeTriggered(TriggeredAbilities.Context context) {
            var state = context.service().actorStates().state(context.actor().id()); var fuse = fuse(state);
            var operation = UUID.nameUUIDFromBytes((context.emission().operation() + ":consume-fuse").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            context.service().actorStates().command(context.actor(), new ActorStates.Command(operation, context.actor().id(), state.revision(),
                    List.of(new ActorStates.ConsumeStacks(fuse.id(), 3)), context.operation().operationId()));
            return CreeperExplosion.execute(context);
        }
    }
}
