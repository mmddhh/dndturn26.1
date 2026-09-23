package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Independent compat fixture: normal START/turn policy -> submit -> approach -> attack -> MOVE. */
public final class MobAbilityLoopChecks {
    private static final String TAG = "dndturn_test_ability_loop";
    private static final Map<UUID, MobTurnStrategies.DecisionView> FOLLOWUPS = new HashMap<>();
    public static void register() {
        MeleeAdapters.server().register(new MeleeAdapters.Attacker("test:loop_cow", 1,
            e -> e.getType() == EntityType.COW && e.entityTags().contains(TAG), e -> 1));
        MeleeAdapters.server().register(new MeleeAdapters.Receiver() {
            public String id() { return "test:loop_cow_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity e) { return e.getType() == EntityType.COW && e.entityTags().contains(TAG); }
            public boolean unawareOf(LivingEntity receiver, LivingEntity attacker) { return false; }
        });
        TacticalCapabilities.register(new TacticalBehavior("test:loop_attack", "Compat natural attack",
            TacticalIntent.Capability.ATTACK, Set.of(TacticalIntent.TargetKind.ENTITY)) {
            private final IntrinsicMelee driver = new IntrinsicMelee();
            public AbilitySource source(TacticalActor actor, TacticalIntent.Hand hand) {
                return actor.body().entityTags().contains(TAG) ? driver.source(actor, hand) : null;
            }
            public String unavailable(TacticalActor actor, TacticalIntent intent, CombatEngine.StateView state) {
                return driver.unavailable(actor, intent, state);
            }
            public boolean canExecute(TacticalActor actor, TacticalIntent intent, Vec3 feet) { return driver.canExecute(actor, intent, feet); }
            public MeleeEffects meleeEffects(TacticalActor actor, boolean configuredKnockback) { return new MeleeEffects(1, false, true, false); }
            public void start(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) { driver.start(actions, actor, execution); }
            public void tick(TacticalActions actions, TacticalActor actor, TacticalActions.Execution execution) { driver.tick(actions, actor, execution); }
        });
        MobTurnStrategies.register(new MobTurnStrategies.Strategy() {
            public String id() { return "test:loop_strategy"; }
            public int version() { return 1; }
            public int priority() { return 100; }
            public boolean matches(Mob actor) { return actor.getType() == EntityType.COW && actor.entityTags().contains(TAG); }
            public TacticalIntent propose(MobTurnStrategies.DecisionView view) {
                if (view.previous() == null) {
                    for (var target : view.targets()) if (target.player() && target.visible())
                        for (var option : target.abilities()) if (option.binding().id().equals("test:loop_attack")
                            && (option.availability() == TacticalCapabilities.Availability.APPROACH_REQUIRED
                                || option.availability() == TacticalCapabilities.Availability.EXECUTABLE))
                            return option.binding().invocation(new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY,
                                view.state().region().dimension(), target.id(), null, -1, 0, 0, 0));
                    return null;
                }
                if (view.previous().snapshot().intent().capability() != TacticalIntent.Capability.ATTACK) return null;
                FOLLOWUPS.put(view.actor(), view);
                var cell = view.position();
                return new TacticalIntent("dndturn:move", 1, TacticalIntent.Capability.MOVE,
                    new TacticalIntent.Target(TacticalIntent.TargetKind.GROUND, view.state().region().dimension(),
                        null, new GridCell(cell.x(), cell.y(), cell.z() + 1), -1, 0, 0, 0), AbilitySource.basic());
            }
        });
    }
    public static void run(GameTestHelper helper) {
        var level = helper.getLevel(); var base = new BlockPos(73001,151,1);
        Set<Long> forced = new HashSet<>();
        for (int x = (base.getX()-24)>>4; x <= (base.getX()+24)>>4; x++)
            for (int z = (base.getZ()-24)>>4; z <= (base.getZ()+24)>>4; z++) {
                level.getChunk(x,z);
                if (level.setChunkForced(x,z,true)) forced.add(net.minecraft.world.level.ChunkPos.pack(x,z));
            }
        for (var pos : BlockPos.betweenClosed(base.offset(-6,-1,-6),base.offset(6,4,6)))
            level.setBlockAndUpdate(pos, pos.getY() < base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(base.getX()+1.5,base.getY(),base.getZ()+.5); player.setNoGravity(true);
        var cow = EntityType.COW.create(level,EntitySpawnReason.COMMAND);
        cow.addTag(TAG); cow.setPos(base.getX()+.5,base.getY(),base.getZ()+.5); cow.setNoAi(true);
        level.addFreshEntity(cow);
        var service = ServerCombatService.forServer(level.getServer());
        Runnable cleanup = () -> {
            service.leave(player.getUUID()); service.leave(cow.getUUID()); cow.discard(); FOLLOWUPS.remove(cow.getUUID());
            for (long key : forced) { var chunk = net.minecraft.world.level.ChunkPos.unpack(key); level.setChunkForced(chunk.x(),chunk.z(),false); }
            forced.clear();
        };
        helper.runAtTickTime(198, () -> {
            try { helper.assertTrue(FOLLOWUPS.containsKey(cow.getUUID()),
                "compat followup absent before timeout: encounter=" + service.encounterOf(cow.getUUID())
                    + " results=" + service.results(service.encounterOf(cow.getUUID()),0,128).results().stream()
                        .filter(r -> r.terminal()).map(r -> r.snapshot().kind()+":"+r.outcome()+":"+r.reason()).toList()); }
            finally { cleanup.run(); }
        });
        helper.runAtTickTime(40, () -> {
            try {
                service.requestStart(player, UUID.randomUUID()); UUID encounter = service.encounterOf(player.getUUID());
                helper.assertTrue(encounter != null && encounter.equals(service.encounterOf(cow.getUUID())), "normal START omitted compat actor");
                if (!player.getUUID().equals(service.state(encounter).current()))
                    service.endCurrentTurn(encounter,UUID.randomUUID(),service.state(encounter).version());
                var opener = new IntrinsicMelee(); var actor = new TacticalActor(player);
                service.tacticalActions().request(player, new TacticalNetwork.Request(service.generation(), encounter,
                    UUID.randomUUID(), service.state(encounter).version(), new TacticalIntent("test:natural_no_push", 1,
                        TacticalIntent.Capability.ATTACK, new TacticalIntent.Target(TacticalIntent.TargetKind.ENTITY,
                            service.state(encounter).region().dimension(), cow.getUUID(), null, -1, 0, 0, 0), opener.source(actor, null)), false));
                helper.assertTrue(service.state(encounter).phase() == EncounterPhase.ACTIVE, "fixture opener did not activate encounter");
                // Initial combat fixture only. The measured compat root has not been proposed yet.
                player.setPos(base.getX()+2.5,base.getY(),base.getZ()+.5);
                if (!cow.getUUID().equals(service.state(encounter).current()))
                    service.endCurrentTurn(encounter,UUID.randomUUID(),service.state(encounter).version());
                helper.assertTrue(cow.getUUID().equals(service.state(encounter).current()), "normal turn rotation did not select compat actor");
                long round = service.state(encounter).round(); var start = cow.position();
                cow.setNoAi(false); cow.setOnGround(true);
                helper.startSequence().thenWaitUntil(() -> {
                    helper.assertTrue(encounter.equals(service.encounterOf(cow.getUUID())), "compat encounter closed before two roots completed");
                    var roots = service.results(encounter,0,128).results().stream().filter(r -> r.terminal()
                        && r.snapshot().kind() == OperationRecord.Kind.PLAN && r.snapshot().owner().equals(cow.getUUID())).toList();
                    helper.assertTrue(roots.size() >= 2, "compat policy has not completed attack and subsequent MOVE: "
                        + roots.stream().map(r -> r.outcome() + ":" + r.reason()).toList()
                        + " current=" + service.state(encounter).current() + " actor=" + cow.getUUID()
                        + " running=" + service.tacticalActions().running(cow.getUUID())
                        + " fault=" + service.tacticalActions().controlFault(cow.getUUID())
                        + " followup=" + FOLLOWUPS.containsKey(cow.getUUID())
                        + " economy=" + service.state(encounter).members().get(cow.getUUID()));
                    helper.assertTrue(roots.stream().allMatch(r -> r.outcome() == OperationRecord.Outcome.COMPLETED),
                        "compat root failed: " + roots.stream().map(r -> r.snapshot().intent().behaviorId() + ":" + r.outcome() + ":" + r.reason()).toList());
                }).thenExecute(() -> {
                    var results = service.results(encounter,0,128).results();
                    var attack = results.stream().filter(r -> r.snapshot().kind() == OperationRecord.Kind.PLAN
                        && r.snapshot().owner().equals(cow.getUUID()) && r.snapshot().intent().capability() == TacticalIntent.Capability.ATTACK).findFirst().orElseThrow();
                    helper.assertTrue(results.stream().anyMatch(r -> r.snapshot().kind() == OperationRecord.Kind.MOVE
                        && attack.snapshot().operationId().equals(r.snapshot().parentId()) && r.actualMovementTicks() > 0), "attack skipped paid approach");
                    var followup = FOLLOWUPS.get(cow.getUUID()); var resources = followup.state().members().get(cow.getUUID());
                    helper.assertTrue(followup.state().round() == round && cow.getUUID().equals(followup.state().current())
                        && !resources.action() && resources.movementTicks() > 0, "executor ended turn/reset resources before policy followup");
                    helper.assertTrue(cow.position().distanceToSqr(start) > 1 && !service.hasMobMoveLease(cow.getUUID()), "compat movement or release missing");
                    cleanup.run();
                }).thenSucceed();
            } catch (Throwable failure) { cleanup.run(); throw failure; }
        });
    }
}
