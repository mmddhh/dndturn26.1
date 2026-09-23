package cc.sighs.dndturn.compat;

import cc.sighs.dndturn.domain.ability.*;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.*;
import cc.sighs.dndturn.domain.effect.*;
import cc.sighs.dndturn.domain.encounter.*;
import cc.sighs.dndturn.domain.encounter.operation.*;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.gametest.TestPlayers;
import cc.sighs.dndturn.platform.server.ability.*;
import cc.sighs.dndturn.platform.server.action.*;
import cc.sighs.dndturn.platform.server.damage.*;
import cc.sighs.dndturn.platform.server.effect.TriggeredAbilities;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Independent test adapters. No registrations or fixture state enter the production jar. */
public final class CombatStructureChecks {
    private static final Map<Entity, LivingEntity> ROOTS = new IdentityHashMap<>();
    private static final Map<LivingEntity, Entity> PARTS = new IdentityHashMap<>();
    private static final List<UUID> CALLS = new ArrayList<>();
    private static final EffectDefinition DEFENSE = new EffectDefinition("test:causal_defense", 1,
            EffectDefinition.Clock.EXPLICIT, EffectDefinition.Stacking.REJECT, 1, false, true,
            List.of(), List.of(new EffectDefinition.Grant("test:defense_free", 1), new EffectDefinition.Grant("test:defense_paid", 1)));
    private static ProcessDefinition process(String id) {
        return new ProcessDefinition(id, 1, ProcessDefinition.Clock.AUTHORIZED_EXECUTION_STEP,
                ProcessDefinition.Recovery.FAIL_UNKNOWN, 2, Set.of("test:defense_control"), "GameTest defensive adapter");
    }
    public static void register() {
        BodyTargets.register(new BodyTargets.Registration("test:body_parts", 1, new BodyTargets.Adapter() {
            public LivingEntity root(Entity hit) { return ROOTS.get(hit); }
            public String part(Entity hit) { return "head"; }
            public Entity resolve(LivingEntity root, String part) { return "head".equals(part) ? PARTS.get(root) : null; }
        }));
        for (String name : List.of("free", "paid")) {
            var definition = new AbilityDefinition("test:defense_" + name, 1, name, ActionIntent.Capability.USE_ITEM,
                    Set.of(ActionIntent.TargetKind.SELF), AbilityDefinition.Activation.TRIGGERED,
                    name.equals("free") ? ActionCost.FREE : ActionCost.REACTION, RuleFacts.AVAILABILITY,
                    "test:defense_" + name, AbilityDefinition.TargetPolicy.NATIVE_INTERACTION);
            var adapter = new MinecraftAbilityAdapter(definition) {
                public GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) { return null; }
                public String unavailable(LiveActorContext actor, ActionIntent intent, EncounterAuthority.StateView state) { return null; }
                public boolean canExecute(LiveActorContext actor, ActionIntent intent, Vec3 feet) { return true; }
                public ProcessDefinition processDefinition() { return process(id()); }
                public void start(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
                    throw new IllegalStateException("trigger only");
                }
                public void tick(ActionExecutionCoordinator actions, LiveActorContext actor, ActionExecutionCoordinator.Execution execution) {
                    throw new IllegalStateException("synchronous test defense");
                }
                public void prepareTriggered(LiveActorContext actor, TriggeredExecutionRecord emission) { actor.verifyCurrent(); }
                public OperationRecord.Outcome executeTriggered(TriggeredAbilities.Context context) {
                    CALLS.add(context.emission().operation());
                    return OperationRecord.Outcome.COMPLETED;
                }
                public Intervention intervention(TriggeredAbilities.Context context) {
                    return new Intervention(context.emission().event().id(), Intervention.Disposition.CANCEL, "test defense");
                }
            };
            AbilityAdapterRegistry.register(definition, adapter, adapter);
            AbilityAdapterRegistry.registerActorEffect(definition, new ActivationSpec(AbilityDefinition.Activation.TRIGGERED,
                    ActivationSpec.Event.DAMAGE_ATTEMPT, null, 0), new cc.sighs.dndturn.domain.fact.ReadContract(Set.of(RuleFacts.SOURCE_VALID)),
                    input -> List.of(new TriggeredAbilityInvocation(
                    definition.id(), 1, new ActionIntent.Target(ActionIntent.TargetKind.SELF, input.dimension(), null, null, -1, 0, 0, 0),
                    TriggeredAbilityInvocation.Timing.IMMEDIATE)));
        }
        AbilityAdapterRegistry.effects().register(DEFENSE);
    }
    public static void run(GameTestHelper h) {
        var level = h.getLevel(); var base = h.absolutePos(new BlockPos(190001, 160, 1));
        var forced = new HashSet<Long>();
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++)
            for (int z = (base.getZ()-24)>>4; z <= (base.getZ()+24)>>4; z++) {
                level.getChunk(x,z); if (level.setChunkForced(x,z,true)) forced.add(ChunkPos.pack(x,z));
            }
        level.waitForEntities(ChunkPos.containing(base), 1);
        for (var p : BlockPos.betweenClosed(base.offset(-5,-1,-5), base.offset(5,4,5)))
            level.setBlockAndUpdate(p, p.getY() < base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        var player = TestPlayers.survival(h);
        player.setPos(base.getX()+.5, base.getY(), base.getZ()+.5);
        var cow = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
        cow.setPos(base.getX()+1.5, base.getY(), base.getZ()+.5); cow.setNoAi(true); cow.setPersistenceRequired(); level.addFreshEntity(cow);
        var runtime = ServerRuntime.encounters(level.getServer());
        Runnable cleanup = () -> {
            var encounter = runtime.encounterOf(player.getUUID()); if (encounter != null) runtime.stop(encounter);
            ROOTS.clear(); PARTS.clear(); CALLS.clear(); cow.discard(); player.discard();
            for (long key : forced) { var pos = ChunkPos.unpack(key); level.setChunkForced(pos.x(), pos.z(), false); }
            forced.clear();
        };
        h.runAtTickTime(99, cleanup);
        h.runAtTickTime(20, () -> {
            try {
                runtime.requestStart(player, UUID.randomUUID());
                var encounter = runtime.encounterOf(player.getUUID());
                h.assertTrue(encounter != null && runtime.state(encounter).members().containsKey(cow.getUUID()), "fixture membership missing");
                var engine = ActionTestAccess.authority(runtime.tacticalActions());
                engine.setHostile(encounter, player.getUUID(), cow.getUUID(), true);
                runtime.endCurrentTurn(encounter, UUID.randomUUID(), engine.view(encounter).version());
                LivingEntity attacker = engine.view(encounter).current().equals(player.getUUID()) ? player : cow;
                LivingEntity defender = attacker == player ? cow : player;
                var actor = new LiveActorContext(defender); var states = runtime.actorStates();
                UUID effect = UUID.randomUUID();
                states.command(actor, new ActorStates.Command(UUID.randomUUID(), actor.id(), states.state(actor.id()).revision(),
                        List.of(new ActorStates.Apply(new EffectInstance(effect, UUID.randomUUID(), DEFENSE, 1, 0, 1)))));
                UUID attack = UUID.randomUUID(), permit = UUID.randomUUID();
                var parent = new OperationRecord.Snapshot(attack, null, encounter, attacker.getUUID(), attacker.getUUID(), defender.getUUID(),
                        0, engine.view(encounter).version(), null, null, OperationRecord.Kind.ATTACK, runtime.generation());
                h.assertTrue(engine.beginOperation(parent), "attack admission failed");
                engine.issueOutcomePermit(new EncounterAuthority.OutcomePermit(permit, encounter, attack, attacker.getUUID(), Set.of(defender.getUUID()),
                        Set.of(EncounterPhase.ACTIVE), 4, engine.stateView(encounter).round()));
                UUID blocker = UUID.randomUUID();
                runtime.processes().open(blocker, process("test:blocker"), new ProcessState.Owner(ProcessState.Ownership.ACTOR, actor.id()),
                        attack, attack, encounter, actor.instance(), 30, "EXECUTE");
                var source = level.damageSources().source(TacticalDamageContext.DAMAGE_TYPE, attacker);
                int[] incoming = {0};
                java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> listener = event -> {
                    if (event.getEntity() == defender) incoming[0]++;
                };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
                try {
                    for (int attempt = 0; attempt < 3; attempt++) {
                        UUID operation = UUID.randomUUID();
                        var damage = new OperationRecord.Snapshot(operation, attack, encounter, attacker.getUUID(), attacker.getUUID(), actor.id(),
                                0, engine.view(encounter).version(), null, null, OperationRecord.Kind.DAMAGE, runtime.generation());
                        h.assertTrue(engine.beginOperation(damage, permit), "damage admission failed");
                        if (attempt == 0) {
                            h.assertTrue(!runtime.cancelDamageAttempt(defender, source, 1, operation), "blocked defense executed");
                            h.assertTrue(CALLS.isEmpty() && engine.stateView(encounter).members().get(actor.id()).reaction(), "process conflict spent reaction");
                            runtime.processes().observe(blocker, "TERMINAL", 0, false, ProcessState.Release.RELEASED, OperationRecord.Outcome.COMPLETED, "released");
                        } else {
                            float before = defender.getHealth(); int previousCalls = CALLS.size();
                            var observation = TacticalDamageContext.hurtObserved(level, defender, source, 1, false, operation);
                            h.assertTrue(!observation.accepted() && defender.getHealth() == before && incoming[0] == 0,
                                    "canceled attempt entered native hurtServer");
                            h.assertTrue(CALLS.size() == previousCalls + (attempt == 1 ? 2 : 1), "free/paid reaction admission mismatch");
                            h.assertTrue(!engine.stateView(encounter).members().get(actor.id()).reaction(), "paid defense did not spend reaction");
                        }
                        engine.publish(encounter, operation, 0, OperationRecord.Outcome.REJECTED, "test canceled", 0, 0, true);
                    }
                } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); }
                engine.publish(encounter, attack, 0, OperationRecord.Outcome.COMPLETED, "test attack settled", 0, 0, true);
                var part = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
                var replacement = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
                ROOTS.put(part, cow); PARTS.put(cow, part);
                var target = BodyTargets.identify(part);
                h.assertTrue(target.entity().equals(cow.getUUID()) && BodyTargets.resolve(cow, target.facet()).body() == part, "facet lost root mapping");
                ROOTS.remove(part); ROOTS.put(replacement, cow); PARTS.put(cow, replacement);
                boolean rejected = false;
                try { BodyTargets.resolve(cow, target.facet()); } catch (IllegalStateException expected) { rejected = true; }
                h.assertTrue(rejected, "old target accepted replacement body");
                h.assertTrue(BodyTargets.identify(replacement).entity().equals(cow.getUUID()), "replacement created a new actor identity");
                h.succeed();
            } finally { cleanup.run(); }
        });
    }
}
