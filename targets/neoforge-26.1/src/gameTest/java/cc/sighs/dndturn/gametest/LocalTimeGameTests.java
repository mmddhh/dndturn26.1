package cc.sighs.dndturn.gametest;
import cc.sighs.dndturn.combat.EncounterPhase;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import cc.sighs.dndturn.DNDTurnNeoForge261;
import cc.sighs.dndturn.combat.MinecraftCombatRuntime;
import cc.sighs.dndturn.combat.MinecraftCellProbe;
import cc.sighs.dndturn.combat.CombatEngine;
import cc.sighs.dndturn.combat.CombatPersistenceEnvelope;
import cc.sighs.dndturn.combat.CombatSavedData;
import cc.sighs.dndturn.combat.CombatRules;
import cc.sighs.dndturn.combat.EncounterRegion;
import cc.sighs.dndturn.combat.GridCell;
import cc.sighs.dndturn.combat.MinecraftRegionSampler;
import cc.sighs.dndturn.combat.ServerCombatService;
import cc.sighs.dndturn.combat.TacticalPlanner;
import cc.sighs.dndturn.combat.TacticalDamageContext;
import cc.sighs.dndturn.combat.OperationRecord;
import java.util.Set;
import java.util.UUID;
import java.util.Random;
import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.GameType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Exercises world, connection-driven player, and controller ticks in a real server. */
public final class LocalTimeGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, DNDTurnNeoForge261.MOD_ID);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CELL_PROBE =
        TEST_FUNCTIONS.register("cell_probe", () -> LocalTimeGameTests::cellProbe);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REPAIR_VALUES =
        TEST_FUNCTIONS.register("repair_values", () -> RepairGameTests::values);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PRESENTATION_PROTOCOL =
        TEST_FUNCTIONS.register("presentation_protocol", () -> PresentationGameTests::protocol);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TACTICAL_CROSSBOW =
        TEST_FUNCTIONS.register("tactical_crossbow", () -> TacticalPlanGameTests::crossbow);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TACTICAL_SNOWBALL =
        TEST_FUNCTIONS.register("tactical_snowball", () -> TacticalPlanGameTests::snowball);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TACTICAL_RANGED =
        TEST_FUNCTIONS.register("tactical_ranged", () -> TacticalPlanGameTests::ranged);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TACTICAL_PLAN =
        TEST_FUNCTIONS.register("tactical_plan", () -> TacticalPlanGameTests::interactions);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TACTICAL_BEHAVIORS =
        TEST_FUNCTIONS.register("tactical_behaviors", () -> TacticalPlanGameTests::behaviors);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PLAYER_ONLY_START =
        TEST_FUNCTIONS.register("player_only_start", () -> RepairGameTests::playerOnlyStart);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REGION_SAMPLER =
        TEST_FUNCTIONS.register("region_sampler", () -> LocalTimeGameTests::regionSampler);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ENCOUNTER_SERVICE =
        TEST_FUNCTIONS.register("encounter_service", () -> LocalTimeGameTests::encounterService);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REGIONAL_ENTITY_GATE =
        TEST_FUNCTIONS.register("regional_entity_gate", () -> LocalTimeGameTests::regionalEntityGate);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DEVELOPMENT_ATTACK =
        TEST_FUNCTIONS.register("development_attack", () -> LocalTimeGameTests::developmentAttack);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TACTICAL_SHIELD_WEAR =
        TEST_FUNCTIONS.register("tactical_shield_wear", () -> LocalTimeGameTests::tacticalShieldWear);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SCHEDULED_TICK_HOLD =
        TEST_FUNCTIONS.register("scheduled_tick_hold", () -> LocalTimeGameTests::scheduledTickHold);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PROTOTYPE_ENVIRONMENT_LOOP =
        TEST_FUNCTIONS.register("prototype_environment_loop", () -> LocalTimeGameTests::prototypeEnvironmentLoop);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PROTOTYPE_ZOMBIE_NAVIGATION =
        TEST_FUNCTIONS.register("prototype_zombie_navigation", () -> LocalTimeGameTests::prototypeZombieNavigation);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MOB_UNKNOWN_REVOKES_LEASE =
        TEST_FUNCTIONS.register("mob_unknown_revokes_lease", () -> LocalTimeGameTests::mobUnknownRevokesLease);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MOVEMENT_LEASE_EXIT =
        TEST_FUNCTIONS.register("movement_lease_exit", () -> LocalTimeGameTests::movementLeaseExit);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ZOMBIE_MELEE_REACH =
        TEST_FUNCTIONS.register("zombie_melee_reach", () -> LocalTimeGameTests::zombieMeleeReach);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CANCELED_DEATH =
        TEST_FUNCTIONS.register("canceled_death", () -> LocalTimeGameTests::canceledDeath);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PROTOTYPE_FIVE_ROUNDS =
        TEST_FUNCTIONS.register("prototype_five_rounds", () -> LocalTimeGameTests::prototypeFiveRounds);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PROTOTYPE_EXIT_RETRY =
        TEST_FUNCTIONS.register("prototype_exit_retry", () -> LocalTimeGameTests::prototypeExitRetry);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> COMPLETED_RETRY_AFTER_LEAVE =
        TEST_FUNCTIONS.register("completed_retry_after_leave", () -> LocalTimeGameTests::completedRetryAfterLeave);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CANDIDATE_ATTACK_MISS =
        TEST_FUNCTIONS.register("candidate_attack_miss", () -> LocalTimeGameTests::candidateAttackMiss);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CANDIDATE_TARGET_REVIEW =
        TEST_FUNCTIONS.register("candidate_target_review", () -> LocalTimeGameTests::candidateTargetReview);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MERGE_WORLD_BOUNDARY =
        TEST_FUNCTIONS.register("merge_world_boundary", () -> LocalTimeGameTests::mergeWorldBoundary);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MERGE_DISCOVERY_EXPANSION =
        TEST_FUNCTIONS.register("merge_discovery_expansion", () -> LocalTimeGameTests::mergeDiscoveryExpansion);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_FIRST_INGRESS =
        TEST_FUNCTIONS.register("arrow_first_ingress", () -> LocalTimeGameTests::arrowFirstIngress);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_TACTICAL_IMPACT =
        TEST_FUNCTIONS.register("arrow_tactical_impact", () -> LocalTimeGameTests::arrowTacticalImpact);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_CROSS_SESSION =
        TEST_FUNCTIONS.register("arrow_cross_session", () -> LocalTimeGameTests::arrowCrossSession);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_CROSS_SESSION_REVERSE =
        TEST_FUNCTIONS.register("arrow_cross_session_reverse", () -> helper -> arrowCrossSession(helper, true));
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_TRACE_MISS =
        TEST_FUNCTIONS.register("arrow_trace_miss", () -> helper -> arrowTacticalImpact(helper, 1));
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_TRACE_ZERO =
        TEST_FUNCTIONS.register("arrow_trace_zero", () -> helper -> arrowTacticalImpact(helper, 2));
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_TRACE_REJECTED =
        TEST_FUNCTIONS.register("arrow_trace_rejected", () -> helper -> arrowTacticalImpact(helper, 3));
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_TRACE_ABSORBED =
        TEST_FUNCTIONS.register("arrow_trace_absorbed", () -> helper -> arrowTacticalImpact(helper, 4));
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_BLOCK_REJECTION =
        TEST_FUNCTIONS.register("arrow_block_rejection", () -> LocalTimeGameTests::arrowBlockRejection);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> COMBAT_SAVE_VALUES =
        TEST_FUNCTIONS.register("combat_save_values", () -> LocalTimeGameTests::combatSaveValues);

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_CROSS_SESSION_MISS =
        TEST_FUNCTIONS.register("arrow_cross_session_miss", () -> helper -> arrowCrossSession(helper, false, true));

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_PENDING_REMOVAL =
        TEST_FUNCTIONS.register("arrow_pending_removal", () -> helper -> arrowCrossSession(helper, false, false, 1));

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> COMBAT_UI_PROTOCOL =
        TEST_FUNCTIONS.register("combat_ui_protocol", () -> LocalTimeGameTests::combatUiProtocol);

    private static void combatUiProtocol(GameTestHelper helper) {
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            UUID id = UUID.randomUUID();
            var member = new cc.sighs.dndturn.combat.CombatNetwork.MemberNotice(id, "participant", 17,
                3, true, false, 19, false, true);
            var state = new cc.sighs.dndturn.combat.CombatNetwork.EncounterState(id, id, 2, true, 8,
                2, 2, null, 3, 19, false, true, 7, false, null, List.of(member), 67, true);
            cc.sighs.dndturn.combat.CombatNetwork.EncounterState.STREAM_CODEC.encode(buffer, state);
            helper.assertTrue(state.equals(cc.sighs.dndturn.combat.CombatNetwork.EncounterState.STREAM_CODEC.decode(buffer))
                    && !buffer.isReadable(), "roster, qualification, resources or history count lost on wire");
            buffer.clear();
            var consent = new cc.sighs.dndturn.combat.CombatNetwork.ConsentState(id, 5, id, 3, 400, 100,
                true, List.of(new cc.sighs.dndturn.combat.CombatNetwork.ConsentMember(id, "participant", true)), "waiting");
            cc.sighs.dndturn.combat.CombatNetwork.ConsentState.STREAM_CODEC.encode(buffer, consent);
            helper.assertTrue(consent.equals(cc.sighs.dndturn.combat.CombatNetwork.ConsentState.STREAM_CODEC.decode(buffer))
                && !buffer.isReadable(), "consent deadline or roster lost on wire");
            buffer.clear();
            var response = new cc.sighs.dndturn.combat.CombatNetwork.ConsentReply(id, id, UUID.randomUUID(), id, 3, true);
            cc.sighs.dndturn.combat.CombatNetwork.ConsentReply.STREAM_CODEC.encode(buffer, response);
            helper.assertTrue(response.equals(cc.sighs.dndturn.combat.CombatNetwork.ConsentReply.STREAM_CODEC.decode(buffer))
                && !buffer.isReadable(), "consent identity or revision lost on wire");
            for (String stage : List.of("MISS", "ZERO_DAMAGE", "VANILLA_ACCEPTED", "VANILLA_REJECTED", "UNKNOWN")) {
                buffer.clear();
                var hit = new cc.sighs.dndturn.combat.CombatNetwork.HitNotice(18, 7, 18, 23, 12, 6,
                    2, 4, "ADVANTAGE", stage);
                var result = new cc.sighs.dndturn.combat.CombatNetwork.ResultNotice(id, id, 66,
                    UUID.randomUUID(), 1, stage, 0, 4, hit);
                cc.sighs.dndturn.combat.CombatNetwork.ResultNotice.STREAM_CODEC.encode(buffer, result);
                helper.assertTrue(result.equals(cc.sighs.dndturn.combat.CombatNetwork.ResultNotice.STREAM_CODEC.decode(buffer))
                        && !buffer.isReadable(), "structured hit evidence lost on wire: " + stage);
            }
            helper.succeed();
        } finally { buffer.release(); }
    }

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ARROW_CROSS_SESSION_PVP =
        TEST_FUNCTIONS.register("arrow_cross_session_pvp", () -> helper -> arrowCrossSession(helper, false, false, 2));

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CONSENT_MULTIPLAYER =
        TEST_FUNCTIONS.register("consent_multiplayer", () -> LocalTimeGameTests::consentMultiplayer);

    private static void consentMultiplayer(GameTestHelper helper) {
        helper.runAtTickTime(5, () -> {
            BlockPos anchor = helper.absolutePos(new BlockPos(16384, 201, 0));
            for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++) {
                    helper.getLevel().getChunk(x, z); helper.getLevel().setChunkForced(x, z, true);
                }
            ServerPlayer first = makeTargetablePlayer(helper);
            ServerPlayer second = makeTargetablePlayer(helper);
            ServerPlayer third = makeTargetablePlayer(helper);
            for (ServerPlayer player : List.of(first, second, third)) {
                player.connection.markClientLoaded(); player.setNoGravity(true);
                player.teleportTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            }
            Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(16386, 201, 0));
            target.setNoAi(true); target.setNoGravity(true);
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            UUID request = UUID.randomUUID();
            service.requestStart(first, request);
            var initial = service.consentView(request);
            helper.assertTrue(initial.members().containsAll(Set.of(first.getUUID(), second.getUUID(), third.getUUID()))
                    && service.encounterOf(first.getUUID()) == null && !service.isEntitySimulationPaused(first),
                "consent preflight paused world or lost required participants");
            service.respondToConsent(second, new cc.sighs.dndturn.combat.CombatNetwork.ConsentReply(service.generation(),
                request, UUID.randomUUID(), second.getUUID(), initial.revision(), true));
            second.teleportTo(anchor.getX() + 80, anchor.getY(), anchor.getZ() + 0.5);
            service.tickConsent();
            helper.assertFalse(service.consentView(request).approvals().contains(second.getUUID()), "departure kept consent");
            second.teleportTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            service.tickConsent();
            var reentered = service.consentView(request);
            helper.assertTrue(reentered.members().contains(second.getUUID()) && !reentered.approvals().contains(second.getUUID()),
                "reentry silently restored consent");
            UUID overlap = UUID.randomUUID();
            service.requestStart(third, overlap);
            var merged = service.consentView(overlap);
            helper.assertTrue(merged.id().equals(service.consentView(request).id())
                    && merged.deadline() == initial.deadline() && service.encounterOf(first.getUUID()) == null,
                "overlapping request extended deadline or started before all consented");
            boolean stale = false;
            try {
                service.respondToConsent(second, new cc.sighs.dndturn.combat.CombatNetwork.ConsentReply(service.generation(),
                    request, UUID.randomUUID(), second.getUUID(), initial.revision(), true));
            } catch (IllegalStateException expected) { stale = true; }
            helper.assertTrue(stale && service.encounterOf(first.getUUID()) == null, "stale consent created a session");
            var reply = new cc.sighs.dndturn.combat.CombatNetwork.ConsentReply(service.generation(), request,
                UUID.randomUUID(), second.getUUID(), merged.revision(), true);
            service.respondToConsent(second, reply);
            UUID encounter = service.encounterOf(first.getUUID());
            helper.assertTrue(encounter != null && encounter.equals(service.encounterOf(second.getUUID()))
                    && encounter.equals(service.encounterOf(third.getUUID())), "all consent did not create one shared session");
            long version = service.state(encounter).version();
            service.respondToConsent(second, reply);
            helper.assertTrue(service.state(encounter).version() == version, "duplicate consent changed the encounter");
            boolean conflict = false;
            try {
                service.respondToConsent(second, new cc.sighs.dndturn.combat.CombatNetwork.ConsentReply(service.generation(),
                    request, reply.operationId(), second.getUUID(), reply.revision(), false));
            } catch (IllegalStateException expected) { conflict = true; }
            helper.assertTrue(conflict && service.state(encounter).version() == version,
                "conflicting reply rewrote a committed request");
            UUID exit = UUID.randomUUID();
            long beforeExit = service.state(encounter).version();
            Vec3 exitStart = first.position();
            first.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            second.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            third.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            Difficulty exitDifficulty = helper.getLevel().getDifficulty();
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            target.setTarget(first);
            helper.assertTrue(target.getTarget() == first, "exit fixture requires a live target");
            boolean insideRejected = false;
            try { service.exitEncounter(first, encounter, exit, beforeExit); }
            catch (IllegalStateException expected) { insideRejected = true; }
            helper.assertTrue(insideRejected && encounter.equals(service.encounterOf(first.getUUID()))
                && service.state(encounter).version() == beforeExit, "inside EXIT changed membership or resources");
            // Another player remains in combat; the neutral requester exits in place.
            target.setTarget(second);
            service.exitEncounter(first, encounter, exit, beforeExit);
            helper.assertTrue(first.position().equals(exitStart) && !MinecraftCombatRuntime.isBodyPaused(first),
                "neutral inside exit teleported or retained body control");
            helper.assertTrue(service.encounterOf(first.getUUID()) == null
                    && encounter.equals(service.encounterOf(second.getUUID()))
                    && encounter.equals(service.encounterOf(third.getUUID())),
                "one participant's exit ended another participant's session");
            long afterExit = service.state(encounter).version();
            service.exitEncounter(first, encounter, exit, beforeExit);
            helper.assertTrue(service.state(encounter).version() == afterExit, "exit retry changed remaining members");
            service.exitEncounter(third, encounter, UUID.randomUUID(), afterExit);
            long lastVersion = service.state(encounter).version();
            second.setPos(exitStart.x + 128, exitStart.y, exitStart.z);
            boolean combatStopRejected = false;
            try { service.exitEncounter(second, encounter, UUID.randomUUID(), lastVersion); }
            catch (IllegalStateException expected) { combatStopRejected = true; }
            helper.assertTrue(combatStopRejected && service.state(encounter).version() == lastVersion,
                "last targeted player ended turn-based mode after crossing the boundary");
            target.setTarget(null);
            second.setPos(exitStart);
            service.exitEncounter(second, encounter, UUID.randomUUID(), lastVersion);
            helper.getLevel().getServer().setDifficulty(exitDifficulty, true);
            first.setPos(exitStart);
            UUID disconnect = UUID.randomUUID();
            service.requestStart(first, disconnect);
            service.consentDisconnected(second.getUUID());
            helper.assertTrue(service.consentView(disconnect).status() == cc.sighs.dndturn.combat.ConsentWindow.Status.DISCONNECTED
                    && service.encounterOf(first.getUUID()) == null, "disconnect did not cancel the entire request");
            UUID expiring = UUID.randomUUID();
            service.requestStart(first, expiring);
            helper.runAtTickTime(410, () -> {
                try {
                    helper.assertTrue(service.consentView(expiring).status() == cc.sighs.dndturn.combat.ConsentWindow.Status.EXPIRED,
                        "server did not expire consent after 400 actual ticks");
                    boolean expired = false;
                    try {
                        service.respondToConsent(second, new cc.sighs.dndturn.combat.CombatNetwork.ConsentReply(service.generation(),
                            expiring, UUID.randomUUID(), second.getUUID(), service.consentView(expiring).revision(), true));
                    } catch (IllegalStateException expected) { expired = true; }
                    helper.assertTrue(expired && service.encounterOf(first.getUUID()) == null, "late consent created a session");
                    helper.succeed();
                } finally {
                    for (ServerPlayer player : List.of(first, second, third)) {
                        UUID id = service.encounterOf(player.getUUID()); if (id != null) service.stop(id);
                    }
                    for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                        for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++)
                            helper.getLevel().setChunkForced(x, z, false);
                }
            });
        });
    }

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ZOMBIE_TARGET_BOUNDARY =
        TEST_FUNCTIONS.register("zombie_target_boundary", () -> helper -> zombieTargetBoundary(helper, false));
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ZOMBIE_TARGET_INVALIDATED =
        TEST_FUNCTIONS.register("zombie_target_invalidated", () -> helper -> zombieTargetBoundary(helper, true));

    private static void zombieTargetBoundary(GameTestHelper helper, boolean invalidate) {
        helper.runAtTickTime(5, () -> {
            int arenaX = invalidate ? 18944 : 18432;
            BlockPos anchor = helper.absolutePos(new BlockPos(arenaX, 241, 0));
            for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++) {
                    helper.getLevel().getChunk(x, z); helper.getLevel().setChunkForced(x, z, true);
                }
            helper.startSequence().thenWaitUntil(() -> {
                for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                    for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++) {
                        long chunk = new net.minecraft.world.level.ChunkPos(x, z).pack();
                        helper.assertTrue(helper.getLevel().shouldTickBlocksAt(chunk)
                            && helper.getLevel().areEntitiesLoaded(chunk), "boundary arena not ticking yet");
                    }
            }).thenExecute(() -> {
            ServerPlayer outside = helper.makeMockServerPlayerInLevel();
            ServerPlayer inside = helper.makeMockServerPlayerInLevel();
            for (ServerPlayer player : List.of(outside, inside)) {
                player.connection.markClientLoaded(); player.setNoGravity(true);
                player.teleportTo(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
            }
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(arenaX + 1, 241, 0));
            zombie.setNoAi(true); zombie.setNoGravity(true);
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            UUID request = UUID.randomUUID();
            service.requestStart(outside, request);
            service.respondToConsent(inside, new cc.sighs.dndturn.combat.CombatNetwork.ConsentReply(
                service.generation(), request, UUID.randomUUID(), inside.getUUID(),
                service.consentView(request).revision(), true));
            UUID encounter = service.encounterOf(outside.getUUID());
            try {
                var state = service.state(encounter);
                for (int i = 0; i < 3 && zombie.getUUID().equals(state.current()); i++) {
                    service.endCurrentTurn(encounter, UUID.randomUUID(), state.version());
                    state = service.state(encounter);
                }
                ServerPlayer opening = state.current().equals(outside.getUUID()) ? outside : inside;
                service.useAttackRandomForGameTest(encounter, new Random() {
                    @Override public int nextInt(int bound) { return 0; }
                });
                service.attack(opening, zombie.getUUID(), UUID.randomUUID(), state.version());
                state = service.state(encounter);
                for (int i = 0; i < 3 && !zombie.getUUID().equals(state.current()); i++) {
                    service.endCurrentTurn(encounter, UUID.randomUUID(), state.version());
                    state = service.state(encounter);
                }
                helper.assertTrue(state.phase() == EncounterPhase.ACTIVE && zombie.getUUID().equals(state.current()),
                    "boundary fixture did not reach a Zombie action");
                double edge = state.region().anchors().stream().mapToDouble(a -> a.center().z()).max().orElseThrow()
                    + state.region().radius();
                double x = zombie.getX(), y = zombie.getY();
                zombie.setPos(x, y, edge - .1);
                outside.teleportTo(x, y, edge + .1);
                inside.teleportTo(x, y, edge - 1.0);
                helper.assertTrue(!state.region().containsPoint(outside.getX(), outside.getBoundingBox().getCenter().y, outside.getZ())
                    && state.region().containsPoint(inside.getX(), inside.getBoundingBox().getCenter().y, inside.getZ())
                    && zombie.distanceToSqr(outside) < zombie.distanceToSqr(inside), "invalid boundary fixture");
                float outsideHealth = outside.getHealth(), insideHealth = inside.getHealth();
                int before = service.results(encounter, 0, 100).total();
                if (invalidate) {
                    inside.teleportTo(x, y, edge + 2);
                    helper.assertTrue(service.takeZombieTurn(encounter, inside.getUUID()) == null,
                        "invalidated selected target was attacked");
                } else service.advanceMobTurns();
                var attacks = service.results(encounter, before, 100).results().stream()
                    .filter(r -> r.snapshot().kind() == OperationRecord.Kind.ATTACK).toList();
                helper.assertTrue(invalidate ? attacks.isEmpty()
                    : attacks.size() == 1 && inside.getUUID().equals(attacks.getFirst().snapshot().target()),
                    "Zombie reselected an outside member: " + attacks);
                helper.assertTrue(outside.getHealth() == outsideHealth && inside.getHealth() == insideHealth,
                    "boundary rejection or forced miss changed health");
                helper.assertFalse(zombie.getUUID().equals(service.state(encounter).current()),
                    "completed/invalidated choice did not finish Zombie turn");
                helper.succeed();
            } finally {
                if (encounter != null) service.stop(encounter);
                for (int cx = (anchor.getX() - 32) >> 4; cx <= (anchor.getX() + 32) >> 4; cx++)
                    for (int cz = (anchor.getZ() - 32) >> 4; cz <= (anchor.getZ() + 32) >> 4; cz++)
                        helper.getLevel().setChunkForced(cx, cz, false);
            }
            });
        });
    }

    /** Normal consent fixture; no special scheduling set or execution permission. */
    private static ServerPlayer makeTargetablePlayer(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "exit-test-player");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
            profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static CombatEngine.StateView startEncounter(GameTestHelper helper, ServerPlayer player) {
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        UUID request = UUID.randomUUID();
        var created = service.requestStart(player, request);
        if (created.status() == cc.sighs.dndturn.combat.StartDisposition.STARTED) return created.state();
        if (created.status() != cc.sighs.dndturn.combat.StartDisposition.WAITING)
            throw new IllegalStateException(created.reason());
        var window = service.consentView(request);
        for (UUID id : window.members()) if (!window.approvals().contains(id)) {
            var member = helper.getLevel().getServer().getPlayerList().getPlayer(id);
            service.respondToConsent(member, new cc.sighs.dndturn.combat.CombatNetwork.ConsentReply(
                service.generation(), request, UUID.randomUUID(), id, service.consentView(request).revision(), true));
        }
        UUID encounter = service.encounterOf(player.getUUID());
        if (encounter == null) throw new IllegalStateException("normal consent did not create a session: " + service.consentView(request));
        return service.state(encounter);
    }

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> NEUTRAL_MOB_TURN =
        TEST_FUNCTIONS.register("neutral_mob_turn", () -> LocalTimeGameTests::neutralMobTurn);

    private static void neutralMobTurn(GameTestHelper helper) {
        helper.runAtTickTime(10, () -> {
            var level = helper.getLevel();
            var service = ServerCombatService.forServer(level.getServer());
            BlockPos anchor = helper.absolutePos(new BlockPos(30000, 121, 0));
            for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++) {
                    level.getChunk(x, z); level.setChunkForced(x, z, true);
                }
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++)
                level.setBlockAndUpdate(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            helper.runAfterDelay(20, () -> {
            var cow = helper.spawn(EntityType.COW, new BlockPos(30002, 121, 0));
            var sheep = helper.spawn(EntityType.SHEEP, new BlockPos(30002, 121, 4));
            sheep.setNoAi(true);
            sheep.setNoGravity(true);
            cow.setNoAi(true);
            boolean[] initialized = {false};
            helper.runAfterDelay(5, () -> {
            if (initialized[0]) return;
            initialized[0] = true;
            helper.assertTrue(level.getEntity(cow.getUUID()) == cow && level.getEntity(sheep.getUUID()) == sheep,
                "waiting for neutral fixture entity registration");
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.connection.markClientLoaded();
            player.setPos(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
            player.setNoGravity(true);
            cow.setNoAi(false);
            cow.setOnGround(true);
            helper.assertTrue(cow.getNavigation().moveTo(anchor.getX() + 9.5, anchor.getY(), anchor.getZ() + .5, 1.0),
                "neutral fixture has no vanilla path");
            helper.assertTrue(service.encounterOf(cow.getUUID()) == null && service.encounterOf(sheep.getUUID()) == null,
                "neutral fixture was already claimed");
            UUID id = startEncounter(helper, player).id();
            helper.assertTrue(id.equals(service.encounterOf(cow.getUUID())) && id.equals(service.encounterOf(sheep.getUUID())),
                "non-hostile mobs missing from turn roster");
            for (int n = 0; n < 3 && !cow.getUUID().equals(service.state(id).current()); n++)
                service.endCurrentTurn(id, UUID.randomUUID(), service.state(id).version());
            helper.assertTrue(cow.getUUID().equals(service.state(id).current()), "cow turn not reached");
            Vec3 before = cow.position();
            Vec3 sheepBefore = sheep.position();
            int budget = service.state(id).members().get(cow.getUUID()).movementTicks();
            boolean[] done = {false};
            for (int tick = 12; tick <= 70; tick++) {
                final int checkTick = tick;
                helper.runAfterDelay(tick, () -> {
                    if (done[0]) return;
                    var state = service.state(id);
                    var results = service.results(id, 0, 100).results();
                    int charged = results.stream().filter(r -> r.snapshot().owner().equals(cow.getUUID())
                        && r.snapshot().kind() == OperationRecord.Kind.MOVE).mapToInt(OperationRecord.Result::actualMovementTicks).sum();
                    boolean terminal = results.stream().anyMatch(r -> r.snapshot().owner().equals(cow.getUUID())
                        && r.snapshot().kind() == OperationRecord.Kind.MOVE && r.terminal());
                    if (!terminal && checkTick < 70) return;
                    done[0] = true;
                    try {
                        helper.assertTrue(cow.position().distanceToSqr(before) > .01 && charged > 0 && charged <= budget,
                            "neutral cow did not move with a bounded charge: " + results);
                        helper.assertTrue(terminal && !service.hasMobMoveLease(cow.getUUID()), "neutral movement lease did not terminate");
                        helper.assertTrue(sheep.position().equals(sheepBefore), "another neutral mob moved during the cow turn");
                        helper.assertTrue(state.phase() != EncounterPhase.ACTIVE && cow.getTarget() == null,
                            "neutral movement invented hostility");
                        helper.assertTrue(MinecraftCombatRuntime.isBodyPaused(player), "cow movement opened player body ticks");
                        helper.succeed();
                    } finally {
                        service.stop(id);
                        cow.discard(); sheep.discard();
                        for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                            for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++)
                                level.setChunkForced(x, z, false);
                    }
                });
            }
            });
            });
        });
    }

    private LocalTimeGameTests() {}

    private static void combatSaveValues(GameTestHelper helper) {
        CombatEngine engine = new CombatEngine(new Random(3), 28, 20);
        UUID member = UUID.randomUUID(), encounter = UUID.randomUUID();
        engine.beginCandidate(encounter,
            EncounterRegion.generate("minecraft:overworld", new EncounterRegion.Discovery(0, 0, 0, 4, 4, 4),
                List.of(new EncounterRegion.Anchor(member, new EncounterRegion.Point(2, 2, 2))), 2, 1), Set.of(member));
        UUID departing = UUID.randomUUID(), late = UUID.randomUUID(), emptyOrder = UUID.randomUUID();
        engine.beginCandidate(emptyOrder,
            EncounterRegion.generate("minecraft:overworld", new EncounterRegion.Discovery(20, 0, 0, 24, 4, 4),
                List.of(new EncounterRegion.Anchor(departing, new EncounterRegion.Point(22, 2, 2))), 2, 1), Set.of(departing));
        engine.join(emptyOrder, late);
        engine.leave(emptyOrder, departing);
        CombatSavedData saved = new CombatSavedData();
        saved.update(new CombatPersistenceEnvelope(CombatPersistenceEnvelope.CURRENT_SCHEMA,
            engine.exportSnapshot(), 24100, 1, java.util.Map.of(encounter, 1L),
            java.util.Map.of(encounter, new CombatPersistenceEnvelope.CapturedSettings(8, 16, 64, false)),
            java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.List.of(),
            java.util.Map.of(member, new CombatPersistenceEnvelope.StartReceipt(member, encounter)),
            java.util.Map.of(departing, new CombatPersistenceEnvelope.ExitReceipt(departing, emptyOrder, 2)),
            java.util.Map.of(), java.util.List.of()));
        var read = saved.envelope();
        var legacyJson = com.google.gson.JsonParser.parseString(saved.json()).getAsJsonObject();
        legacyJson.addProperty("schemaVersion", 2);
        legacyJson.add("prototypeStarts", legacyJson.remove("startReceipts"));
        legacyJson.add("prototypeExits", legacyJson.remove("exitReceipts"));
        var oldClassification = new com.google.gson.JsonArray();
        oldClassification.add(encounter.toString());
        legacyJson.add("prototypeEncounters", oldClassification);
        var legacyRules = legacyJson.getAsJsonObject("rules");
        legacyRules.addProperty("schemaVersion", 1);
        for (var entry : legacyRules.getAsJsonArray("encounters")) {
            entry.getAsJsonObject().remove("movementTicksPerTurn");
            entry.getAsJsonObject().remove("environmentTicks");
        }
        CombatSavedData migrated = CombatSavedData.TYPE.codecFactory().create(helper.getLevel())
            .parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(legacyJson.toString()))
            .getOrThrow();
        var migratedEngine = CombatEngine.restoreSnapshot(migrated.envelope().rules(), new Random(4));
        helper.assertTrue(migrated.envelope().schemaVersion() == CombatPersistenceEnvelope.CURRENT_SCHEMA
                && migratedEngine.movementTicksPerTurn(encounter) == 28
                && migratedEngine.environmentTicks(encounter) == 20
                && migratedEngine.view(emptyOrder).order().isEmpty()
                && migratedEngine.encounterIds().contains(encounter)
                && migrated.envelope().startReceipts().equals(read.startReceipts())
                && migrated.envelope().exitReceipts().equals(read.exitReceipts()),
            "legacy active schema did not preserve captured budgets and the legal empty queue");
        helper.assertTrue(read != null && read.cumulativeServerTicks() == 24100
                && read.capturedSettings().get(encounter).regionRadius() == 8
                && !read.capturedSettings().get(encounter).tacticalKnockbackEnabled(),
            "saved clock or captured encounter settings failed to decode");
        var restored = CombatEngine.restoreSnapshot(read.rules(), new Random(4));
        helper.assertTrue(restored.encounterOf(member).equals(encounter)
                && restored.stateView(encounter).members().get(member).movementTicks() == 28,
            "SavedData JSON lost membership or movement resources");
        var server = helper.getLevel().getServer();
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("dndturn-gametest-save");
        try (SavedDataStorage writer = new SavedDataStorage(folder,
            server.getFixerUpper(), server.registryAccess())) {
            writer.set(CombatSavedData.TYPE, saved);
            writer.saveAndJoin();
        }
        try (SavedDataStorage reader = new SavedDataStorage(folder,
            server.getFixerUpper(), server.registryAccess())) {
            CombatSavedData disk = reader.get(CombatSavedData.TYPE);
            helper.assertTrue(disk != null && disk.envelope() != null
                    && disk.envelope().cumulativeServerTicks() == 24100
                    && CombatEngine.restoreSnapshot(disk.envelope().rules(), new Random(5))
                        .encounterOf(member).equals(encounter),
                "SavedData disk round trip lost the captured combat values");
            var resumed = CombatEngine.restoreSnapshot(disk.envelope().rules(), new Random(6));
            helper.assertTrue(resumed.stateView(emptyOrder).phase() == EncounterPhase.ENVIRONMENT
                    && resumed.view(emptyOrder).order().isEmpty(),
                "legal environment empty order was not preserved on disk");
            for (int i = 0; i < 20; i++) {
                UUID step = UUID.randomUUID();
                resumed.authorizeEnvironmentStep(emptyOrder, step);
                resumed.commitEnvironmentStep(emptyOrder, step);
            }
            helper.assertTrue(late.equals(resumed.view(emptyOrder).current()),
                "restored late member did not become eligible next round");
        }
        helper.succeed();
    }

    private static void arrowCrossSession(GameTestHelper helper) {
        arrowCrossSession(helper, false);
    }

    private static void arrowCrossSession(GameTestHelper helper, boolean reverse) {
        arrowCrossSession(helper, reverse, false);
    }

    private static void arrowCrossSession(GameTestHelper helper, boolean reverse, boolean miss) {
        arrowCrossSession(helper, reverse, miss, 0);
    }

    private static void arrowCrossSession(GameTestHelper helper, boolean reverse, boolean miss, int special) {
        boolean removePending = special == 1;
        boolean pvp = special == 2;
        int arenaX = pvp ? 12288 : removePending ? 10240 : miss ? 8192 : reverse ? 6144 : 3072;
        int sourceX = reverse ? 24 : 0;
        int destinationX = reverse ? 0 : 24;
        BlockPos anchor = helper.absolutePos(new BlockPos(arenaX, 141, 1));
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        first.connection.markClientLoaded();
        second.connection.markClientLoaded();
        first.teleportTo(anchor.getX() + sourceX + 0.5, anchor.getY(), anchor.getZ() + 2.5);
        second.teleportTo(anchor.getX() + destinationX + 0.5, anchor.getY(), anchor.getZ() + 2.5);
        for (int x = (anchor.getX() - 24) >> 4; x <= (anchor.getX() + 48) >> 4; x++)
            for (int z = (anchor.getZ() - 24) >> 4; z <= (anchor.getZ() + 24) >> 4; z++) {
                helper.getLevel().getChunk(x, z);
                helper.getLevel().setChunkForced(x, z, true);
            }
        Zombie shooterNeighbor = helper.spawn(EntityType.ZOMBIE, new BlockPos(arenaX + sourceX + 1, 141, 4));
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(arenaX + destinationX + 1, 141, 1));
        shooterNeighbor.setNoAi(true);
        shooterNeighbor.setNoGravity(true);
        target.setNoAi(true);
        target.setNoGravity(true);
        Arrow[] fired = new Arrow[1];
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.ProjectileImpactEvent> removalProbe = event -> {
            if (!removePending || event.getProjectile() != fired[0]) return;
            service.persistIfChanged();
            var saved = helper.getLevel().getServer().getDataStorage().computeIfAbsent(CombatSavedData.TYPE).envelope();
            if (saved.pendingProjectileAttacks().containsKey(fired[0].getUUID())) {
                helper.assertFalse(service.encounterOf(first.getUUID()).equals(service.encounterOf(second.getUUID())),
                    "removal probe must execute before AB merge");
                fired[0].discard();
            }
        };
        if (removePending) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            net.neoforged.bus.api.EventPriority.LOWEST, true, removalProbe);
        // Register the whole driver before execution: mutating GameTestInfo's task hash map
        // with a large batch inside a running task can invalidate its iterator.
        for (int tick = 8; tick <= 195; tick += 3) helper.runAtTickTime(tick, () -> {
            UUID firstId = service.encounterOf(first.getUUID());
            UUID secondId = service.encounterOf(second.getUUID());
            if (firstId == null || secondId == null) return;
            if (pvp && fired[0] != null && fired[0].isRemoved()) {
                try {
                    helper.assertFalse(firstId.equals(secondId), "forbidden PvP merged the sessions");
                    helper.assertTrue(second.getHealth() == second.getMaxHealth(), "forbidden arrow harmed the player");
                    var rejected = service.results(firstId, 0, 200).results().stream()
                        .filter(r -> r.snapshot().source().equals(fired[0].getUUID())
                            && r.reason().contains("PvP arrow forbidden")).toList();
                    helper.assertTrue(rejected.size() == 1, "PvP did not publish one explicit rejection");
                    helper.succeed();
                } finally {
                    service.stop(firstId); service.stop(secondId);
                    for (int x = (anchor.getX() - 24) >> 4; x <= (anchor.getX() + 48) >> 4; x++)
                        for (int z = (anchor.getZ() - 24) >> 4; z <= (anchor.getZ() + 24) >> 4; z++)
                            helper.getLevel().setChunkForced(x, z, false);
                }
                return;
            }
            if (removePending && fired[0] != null && fired[0].isRemoved()) {
                try {
                    var unknown = service.results(secondId, 0, 200).results().stream()
                        .filter(r -> r.snapshot().source().equals(fired[0].getUUID())
                            && r.outcome() == OperationRecord.Outcome.UNKNOWN).toList();
                    helper.assertTrue(unknown.size() == 1 && unknown.getFirst().terminal(),
                        "pending AB removal lost its unique UNKNOWN result: " + unknown);
                    service.noteArrowLeave(fired[0]);
                    helper.assertTrue(service.results(secondId, 0, 200).results().stream()
                            .filter(r -> r.snapshot().source().equals(fired[0].getUUID())).count() == 1,
                        "duplicate removal published another collision result");
                    service.persistIfChanged();
                    var saved = helper.getLevel().getServer().getDataStorage().computeIfAbsent(CombatSavedData.TYPE).envelope();
                    helper.assertTrue(saved.projectileOrigins().containsKey(fired[0].getUUID())
                            && !saved.projectileDomains().containsKey(fired[0].getUUID())
                            && !saved.pendingProjectileAttacks().containsKey(fired[0].getUUID()),
                        "removal lost history or retained simulation/pending ownership");
                    helper.succeed();
                } finally {
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(removalProbe);
                    service.stop(firstId);
                    if (!firstId.equals(secondId)) service.stop(secondId);
                    for (int x = (anchor.getX() - 24) >> 4; x <= (anchor.getX() + 48) >> 4; x++)
                        for (int z = (anchor.getZ() - 24) >> 4; z <= (anchor.getZ() + 24) >> 4; z++)
                            helper.getLevel().setChunkForced(x, z, false);
                }
                return;
            }
            if (firstId.equals(secondId)) {
                var attacks = service.results(firstId, 0, 200).results().stream()
                    .filter(r -> r.snapshot().kind() == OperationRecord.Kind.ATTACK
                        && r.snapshot().owner().equals(first.getUUID())).toList();
                if (!attacks.isEmpty()) {
                    try {
                        helper.assertTrue(attacks.size() == 1 && attacks.getFirst().damageTrace() != null
                                && (miss ? !attacks.getFirst().damageTrace().hit() && attacks.getFirst().damageTrace().healthLoss() == 0
                                    : attacks.getFirst().damageTrace().healthLoss() > 0),
                            "AB arrow did not settle exactly once with observed damage: " + attacks);
                        helper.succeed();
                    } finally {
                        service.stop(firstId);
                        for (int x = (anchor.getX() - 24) >> 4; x <= (anchor.getX() + 48) >> 4; x++)
                            for (int z = (anchor.getZ() - 24) >> 4; z <= (anchor.getZ() + 24) >> 4; z++)
                                helper.getLevel().setChunkForced(x, z, false);
                    }
                    return;
                }
            }
            for (UUID id : new java.util.HashSet<>(List.of(firstId, secondId))) {
                var state = service.state(id);
                for (int turns = 0; turns < 5 && state.phase() != EncounterPhase.ENVIRONMENT; turns++) {
                    service.endCurrentTurn(id, UUID.randomUUID(), state.version());
                    state = service.state(id);
                }
            }
        });
        helper.runAtTickTime(5, () -> {
            first.teleportTo(anchor.getX() + sourceX + 0.5, anchor.getY(), anchor.getZ() + 2.5);
            second.teleportTo(anchor.getX() + destinationX + 0.5, anchor.getY(), anchor.getZ() + 2.5);
            var a = startEncounter(helper, first);
            CombatEngine.StateView b;
            try {
                b = startEncounter(helper, second);
            } catch (RuntimeException failure) {
                service.stop(a.id());
                throw failure;
            }
            helper.assertFalse(a.region().overlaps(b.region()), "AB fixture regions must be disjoint");
            Random hit = new Random() {
                @Override public int nextInt(int bound) { return miss ? 0 : bound - 1; }
            };
            service.useAttackRandomForGameTest(a.id(), hit);
            service.useAttackRandomForGameTest(b.id(), hit);
            Arrow arrow = new Arrow(helper.getLevel(), anchor.getX() + sourceX + 0.5, anchor.getY() + 1,
                anchor.getZ() + (pvp ? 2.5 : 0.5), new ItemStack(Items.ARROW), null);
            fired[0] = arrow;
            arrow.setOwner(first);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(reverse ? -26 : 26, 0, 0);
            helper.getLevel().addFreshEntity(arrow);
            float health = target.getHealth();
            for (UUID id : List.of(a.id(), b.id())) {
                for (int turns = 0; turns < 4 && service.state(id).phase() != EncounterPhase.ENVIRONMENT; turns++)
                    service.endCurrentTurn(id, UUID.randomUUID(), service.state(id).version());
            }
            helper.runAtTickTime(200, () -> {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(removalProbe);
                UUID primary = service.encounterOf(first.getUUID());
                try {
                    helper.assertFalse(removePending || pvp, "arrow never reached pending removal/PvP rejection probe");
                    helper.assertTrue(primary.equals(service.encounterOf(second.getUUID())),
                        "A arrow did not causally merge B: ticks=" + arrow.tickCount
                            + " removed=" + arrow.isRemoved() + " position=" + arrow.position()
                            + " stateA=" + service.state(primary)
                            + " A=" + service.results(primary, 0, 200).results()
                            + " B=" + service.results(service.encounterOf(second.getUUID()), 0, 200).results());
                    var attacks = service.results(primary, 0, 200).results().stream()
                        .filter(r -> r.snapshot().kind() == OperationRecord.Kind.ATTACK
                            && arrow.getUUID().equals(r.snapshot().source())).toList();
                    helper.assertTrue(attacks.size() == 1 && attacks.getFirst().damageTrace() != null
                            && (miss ? target.getHealth() == health : target.getHealth() < health),
                        "cross-session arrow did not settle exactly once with damage evidence: " + attacks);
                    helper.succeed();
                } finally {
                    service.stop(primary);
                    UUID remaining = service.encounterOf(second.getUUID());
                    if (remaining != null) service.stop(remaining);
                    for (int x = (anchor.getX() - 24) >> 4; x <= (anchor.getX() + 48) >> 4; x++)
                        for (int z = (anchor.getZ() - 24) >> 4; z <= (anchor.getZ() + 24) >> 4; z++)
                            helper.getLevel().setChunkForced(x, z, false);
                }
            });
        });
    }

    private static void arrowTacticalImpact(GameTestHelper helper) {
        arrowTacticalImpact(helper, 0);
    }

    private static void arrowTacticalImpact(GameTestHelper helper, int scenario) {
        helper.runAtTickTime(5, () -> {
            int arenaX = scenario == 0 ? 128 : 4096 + scenario * 128;
            int arenaY = 121;
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.connection.markClientLoaded();
            BlockPos anchor = helper.absolutePos(new BlockPos(arenaX + 1, arenaY, 1));
            player.teleportTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++) {
                    helper.getLevel().getChunk(x, z);
                    helper.getLevel().setChunkForced(x, z, true);
                }
            // Forced tickets become entity/block ticking asynchronously; elapsed test ticks
            // alone do not prove that an off-structure arena is ready for collision.
            helper.startSequence().thenWaitUntil(() -> {
                for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                    for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++) {
                        long chunk = new net.minecraft.world.level.ChunkPos(x, z).pack();
                        helper.assertTrue(helper.getLevel().shouldTickBlocksAt(chunk)
                            && helper.getLevel().areEntitiesLoaded(chunk), "arrow arena not ticking yet");
                    }
            }).thenExecute(() -> {
            for (int x = arenaX + 1; x <= arenaX + 2; x++)
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, arenaY - 1, 1)),
                    Blocks.STONE.defaultBlockState());
            Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(arenaX + 2, arenaY, 1));
            target.setNoGravity(true);
            target.setNoAi(true);
            if (scenario == 2) target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(64);
            if (scenario == 3) target.setInvulnerable(true);
            if (scenario == 4) {
                target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(10);
                target.setAbsorptionAmount(10);
            }
            player.teleportTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            CombatEngine.StateView created = startEncounter(helper, player);
            service.useAttackRandomForGameTest(created.id(), new Random() {
                @Override public int nextInt(int bound) { return scenario == 1 ? 0 : bound - 1; }
            });
            Arrow arrow = new Arrow(helper.getLevel(), anchor.getX() + 13, anchor.getY() + 1,
                anchor.getZ() + 0.5, new ItemStack(Items.ARROW), null);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(-14, 0, 0);
            helper.getLevel().addFreshEntity(arrow);
            for (int i = 0; i < 3 && service.state(created.id()).phase() != EncounterPhase.ENVIRONMENT; i++) {
                var state = service.state(created.id());
                service.endCurrentTurn(created.id(), UUID.randomUUID(), state.version());
            }
            helper.assertTrue(service.state(created.id()).phase() == EncounterPhase.ENVIRONMENT,
                "arrow test did not reach environment phase");
            float healthBefore = target.getHealth();
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                service.results(created.id(), 0, 100).results().stream().anyMatch(result ->
                    result.snapshot().kind() == OperationRecord.Kind.ATTACK
                        && result.snapshot().source().equals(arrow.getUUID())), "waiting for scheduled arrow collision"))
                .thenExecute(() -> {
                try {
                    var attacks = service.results(created.id(), 0, 100).results().stream()
                        .filter(result -> result.snapshot().kind() == OperationRecord.Kind.ATTACK
                            && result.snapshot().source().equals(arrow.getUUID())).toList();
                    helper.assertTrue(attacks.size() == 1 && attacks.getFirst().terminal(),
                        "arrow collision did not publish exactly one terminal causal attack: " + attacks
                            + "; arrow=" + arrow.position() + ", removed=" + arrow.isRemoved()
                            + ", target=" + target.getBoundingBox() + ", phase=" + service.state(created.id()).phase()
                            + ", results=" + service.results(created.id(), 0, 100).results());
                    helper.assertTrue(scenario == 0 ? target.getHealth() < healthBefore
                            : target.getHealth() == healthBefore,
                        "arrow health observation did not match the scenario " + scenario);
                    var trace = attacks.getFirst().damageTrace();
                    helper.assertTrue(trace != null && trace.evidence() != null
                            && trace.evidence().sourceId().equals(arrow.getUUID())
                            && trace.healthLoss() == healthBefore - target.getHealth(),
                        "arrow result lost structured source or observed health evidence");
                    var expectedStage = switch (scenario) {
                        case 1 -> cc.sighs.dndturn.combat.DamageTrace.Stage.MISS;
                        case 2 -> cc.sighs.dndturn.combat.DamageTrace.Stage.ZERO_DAMAGE;
                        case 3 -> cc.sighs.dndturn.combat.DamageTrace.Stage.VANILLA_REJECTED;
                        default -> cc.sighs.dndturn.combat.DamageTrace.Stage.VANILLA_ACCEPTED;
                    };
                    helper.assertTrue(trace.evidence().stage() == expectedStage,
                        "arrow trace stage mismatch: " + trace);
                    if (scenario == 4) helper.assertTrue(trace.absorptionLoss() > 0,
                        "accepted absorbed arrow lost its absorption evidence");
                    service.persistIfChanged();
                    var saved = helper.getLevel().getServer().getDataStorage()
                        .computeIfAbsent(CombatSavedData.TYPE).envelope();
                    var savedTrace = saved.rules().encounters().stream().filter(e -> e.id().equals(created.id()))
                        .flatMap(e -> e.history().stream())
                        .filter(r -> r.snapshot().operationId().equals(attacks.getFirst().snapshot().operationId()))
                        .findFirst().orElseThrow().damageTrace();
                    helper.assertTrue(trace.equals(savedTrace), "arrow trace did not survive SavedData encoding");
                    helper.succeed();
                } finally {
                    service.stop(created.id());
                    for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                        for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++)
                            helper.getLevel().setChunkForced(x, z, false);
                }
            });
            });
        });
    }

    private static void arrowBlockRejection(GameTestHelper helper) {
        helper.runAtTickTime(5, () -> {
            int arenaX = 256;
            int arenaY = 121;
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            BlockPos anchor = helper.absolutePos(new BlockPos(arenaX + 1, arenaY, 1));
            player.teleportTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++) {
                    helper.getLevel().getChunk(x, z);
                    helper.getLevel().setChunkForced(x, z, true);
                }
            for (int x = arenaX + 1; x <= arenaX + 2; x++)
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, arenaY - 1, 1)),
                    Blocks.STONE.defaultBlockState());
            Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(arenaX + 2, arenaY, 1));
            target.setNoGravity(true);
            target.setNoAi(true);
            helper.getLevel().setBlockAndUpdate(anchor.offset(6, 1, 0), Blocks.STONE.defaultBlockState());
            helper.runAtTickTime(6, () -> {
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            CombatEngine.StateView created = startEncounter(helper, player);
            Arrow arrow = new Arrow(helper.getLevel(), anchor.getX() + 13, anchor.getY() + 1,
                anchor.getZ() + 0.5, new ItemStack(Items.ARROW), null);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(-14, 0, 0);
            helper.getLevel().addFreshEntity(arrow);
            for (int i = 0; i < 3 && service.state(created.id()).phase() != EncounterPhase.ENVIRONMENT; i++) {
                var state = service.state(created.id());
                service.endCurrentTurn(created.id(), UUID.randomUUID(), state.version());
            }
            float healthBefore = target.getHealth();
            helper.runAtTickTime(12, () -> {
                try {
                    var rejected = service.results(created.id(), 0, 100).results().stream()
                        .filter(result -> result.snapshot().source().equals(arrow.getUUID())
                            && result.snapshot().kind() == OperationRecord.Kind.ENVIRONMENT
                            && result.outcome() == OperationRecord.Outcome.REJECTED).toList();
                    helper.assertTrue(rejected.size() == 1 && arrow.isRemoved()
                            && rejected.getFirst().reason().contains("traversal unsupported"),
                        "blocked arrow did not hit the pre-effect gate and terminate: " + rejected);
                    helper.assertTrue(target.getHealth() == healthBefore,
                        "blocked arrow damaged the tactical target");
                    service.persistIfChanged();
                    var saved = helper.getLevel().getServer().getDataStorage()
                        .computeIfAbsent(CombatSavedData.TYPE).envelope();
                    helper.assertTrue(saved != null
                            && !saved.projectileDomains().containsKey(arrow.getUUID())
                            && saved.projectileOrigins().containsKey(arrow.getUUID()),
                        "destroyed arrow retained a live scheduler domain or lost launch history");
                    helper.succeed();
                } finally {
                    service.stop(created.id());
                    for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                        for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++)
                            helper.getLevel().setChunkForced(x, z, false);
                }
            });
            });
        });
    }

    private static void arrowFirstIngress(GameTestHelper helper) {
        int arenaX = 0;
        int arenaY = 41;
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(arenaX, arenaY, 1));
        player.teleportTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
            for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++) {
                helper.getLevel().getChunk(x, z);
                helper.getLevel().setChunkForced(x, z, true);
            }
        for (int x = arenaX; x <= arenaX + 2; x++)
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, arenaY - 1, 1)),
                Blocks.STONE.defaultBlockState());
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(arenaX + 2, arenaY, 1));
        target.setNoGravity(true);
        target.setNoAi(true);
        helper.runAtTickTime(2, () -> {
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        CombatEngine.StateView state = startEncounter(helper, player);
        Arrow crossing = new Arrow(helper.getLevel(), anchor.getX() - 12, anchor.getY() + 1,
            anchor.getZ() + 0.5, new ItemStack(Items.ARROW), null);
        crossing.setNoGravity(true);
        crossing.setDeltaMovement(24, 0, 0);
        helper.getLevel().addFreshEntity(crossing);
        Arrow outside = new Arrow(helper.getLevel(), anchor.getX() - 12, anchor.getY() + 1,
            anchor.getZ() + 25.5, new ItemStack(Items.ARROW), null);
        outside.setNoGravity(true);
        outside.setDeltaMovement(1, 0, 0);
        helper.getLevel().addFreshEntity(outside);
        helper.runAtTickTime(8, () -> {
            try {
                helper.assertTrue(crossing.tickCount == 0,
                    "fast arrow crossed a paused encounter before first-entry classification");
                helper.assertTrue(outside.tickCount > 0,
                    "unrelated outside arrow was incorrectly paused");
                service.stop(state.id());
                var next = startEncounter(helper, player);
                helper.runAtTickTime(11, () -> {
                    try {
                        helper.assertTrue(crossing.tickCount == 0,
                            "arrow kept an ended domain and bypassed the new paused region");
                        service.persistIfChanged();
                        var saved = helper.getLevel().getServer().getDataStorage()
                            .computeIfAbsent(CombatSavedData.TYPE).envelope();
                        helper.assertTrue(next.id().equals(saved.projectileDomains().get(crossing.getUUID()))
                                && saved.projectileOrigins().containsKey(crossing.getUUID()),
                            "arrow did not rebind scheduling independently of launch history");
                        helper.succeed();
                    } finally {
                        service.stop(next.id());
                        for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                            for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++)
                                helper.getLevel().setChunkForced(x, z, false);
                    }
                });
            } catch (RuntimeException | Error failure) {
                service.stop(state.id());
                for (int x = (anchor.getX() - 32) >> 4; x <= (anchor.getX() + 32) >> 4; x++)
                    for (int z = (anchor.getZ() - 32) >> 4; z <= (anchor.getZ() + 32) >> 4; z++)
                        helper.getLevel().setChunkForced(x, z, false);
                throw failure;
            }
        });
        });
    }

    private static void mergeDiscoveryExpansion(GameTestHelper helper) {
        helper.runAtTickTime(5, () -> {
            BlockPos base = helper.absolutePos(BlockPos.ZERO);
            int x = Math.floorMod(-base.getX(), 16);
            int z = Math.floorMod(-base.getZ(), 16);
            BlockPos origin = helper.absolutePos(new BlockPos(x, 301, z));
            for (int floorX = x; floorX <= x + 36; floorX++)
                for (int floorZ = z; floorZ <= z + 2; floorZ++)
                    helper.getLevel().setBlockAndUpdate(
                        helper.absolutePos(new BlockPos(floorX, 300, floorZ)),
                        Blocks.STONE.defaultBlockState());
            int minChunkX = (origin.getX() - 24) >> 4;
            int maxChunkX = (origin.getX() + 34 + 24) >> 4;
            int minChunkZ = (origin.getZ() - 24) >> 4;
            int maxChunkZ = (origin.getZ() + 24) >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
                    helper.getLevel().setChunkForced(chunkX, chunkZ, true);
            ServerPlayer[] players = new ServerPlayer[3];
            Zombie[] mobs = new Zombie[3];
            int[] offsets = {0, 15, 34};
            for (int i = 0; i < players.length; i++) {
                players[i] = helper.makeMockServerPlayerInLevel();
                players[i].connection.markClientLoaded();
                players[i].setPos(origin.getX() + offsets[i] + 0.5,
                    origin.getY(), origin.getZ() + 0.5);
                mobs[i] = helper.spawn(EntityType.ZOMBIE,
                    new BlockPos(x + offsets[i] + 1, 301, z + 1));
            }
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            UUID[] ids = new UUID[3];
            Vec3[] positions = java.util.Arrays.stream(players).map(ServerPlayer::position).toArray(Vec3[]::new);
            for (ServerPlayer player : players) player.teleportTo(player.getX() + 128, player.getY(), player.getZ());
            for (int i = 0; i < ids.length; i++) {
                players[i].teleportTo(positions[i].x, positions[i].y, positions[i].z);
                ids[i] = startEncounter(helper, players[i]).id();
            }
            helper.assertFalse(service.state(ids[0]).region().overlaps(service.state(ids[2]).region()),
                "fixture started with the third session already overlapping the first");
            helper.assertFalse(service.state(ids[1]).region().overlaps(service.state(ids[2]).region()),
                "fixture started with the third session already overlapping the second");
            ArmorStand newAnchor = helper.spawn(EntityType.ARMOR_STAND,
                new BlockPos(x + 30, 301, z + 1));
            newAnchor.setNoGravity(true);
            for (UUID id : ids)
                for (int turn = 0; turn < 6
                    && service.state(id).phase() != EncounterPhase.ENVIRONMENT; turn++) {
                    var state = service.state(id);
                    service.endCurrentTurn(id, UUID.randomUUID(), state.version());
                }
            helper.runAtTickTime(10, () -> {
                try {
                    UUID canonical = service.encounterOf(players[0].getUUID());
                    helper.assertTrue(canonical != null
                        && canonical.equals(service.encounterOf(players[1].getUUID()))
                        && canonical.equals(service.encounterOf(players[2].getUUID()))
                        && canonical.equals(service.encounterOf(mobs[0].getUUID()))
                        && canonical.equals(service.encounterOf(mobs[1].getUUID()))
                        && canonical.equals(service.encounterOf(mobs[2].getUUID())),
                        "resampling did not expand and commit the three-session overlap");
                    helper.assertTrue(service.state(canonical).region().anchors().stream()
                        .anyMatch(anchor -> anchor.entityId().equals(newAnchor.getUUID())),
                        "merged region did not capture the newly arrived anchor");
                    helper.succeed();
                } finally {
                    Set<UUID> stopped = new java.util.HashSet<>();
                    for (ServerPlayer player : players) {
                        UUID active = service.encounterOf(player.getUUID());
                        if (active != null && stopped.add(active)) service.stop(active);
                    }
                    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
                        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
                            helper.getLevel().setChunkForced(chunkX, chunkZ, false);
                }
            });
        });
    }

    private static void mergeWorldBoundary(GameTestHelper helper) {
        helper.runAtTickTime(5, () -> {
            ServerPlayer firstPlayer = helper.makeMockServerPlayerInLevel();
            ServerPlayer secondPlayer = helper.makeMockServerPlayerInLevel();
            firstPlayer.connection.markClientLoaded();
            secondPlayer.connection.markClientLoaded();
            BlockPos origin = helper.absolutePos(new BlockPos(1, 221, 1));
            for (int x = 0; x <= 10; x++) for (int z = 0; z <= 3; z++)
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 220, z)),
                    Blocks.STONE.defaultBlockState());
            firstPlayer.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
            secondPlayer.setPos(origin.getX() + 6.5, origin.getY(), origin.getZ() + 0.5);
            for (int x = (origin.getX() - 24) >> 4; x <= (origin.getX() + 32) >> 4; x++)
                for (int z = (origin.getZ() - 24) >> 4; z <= (origin.getZ() + 24) >> 4; z++)
                    {
                        helper.getLevel().getChunk(x, z);
                        helper.getLevel().setChunkForced(x, z, true);
                    }
            helper.startSequence().thenWaitUntil(() -> {
                for (int x = (origin.getX() - 24) >> 4; x <= (origin.getX() + 32) >> 4; x++)
                    for (int z = (origin.getZ() - 24) >> 4; z <= (origin.getZ() + 24) >> 4; z++) {
                        long key = new net.minecraft.world.level.ChunkPos(x, z).pack();
                        helper.assertTrue(helper.getLevel().shouldTickBlocksAt(key)
                            && helper.getLevel().areEntitiesLoaded(key), "merge arena is not yet ticking");
                    }
            }).thenExecute(() -> {
            Zombie firstMob = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 221, 2));
            Zombie secondMob = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 221, 2));
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            Vec3 secondPosition = secondPlayer.position();
            secondPlayer.teleportTo(secondPosition.x + 128, secondPosition.y, secondPosition.z);
            UUID firstId = startEncounter(helper, firstPlayer).id();
            secondPlayer.teleportTo(secondPosition.x, secondPosition.y, secondPosition.z);
            UUID secondId = startEncounter(helper, secondPlayer).id();
            helper.assertFalse(firstId.equals(secondId), "merge fixture did not create two sessions");
            long firstProjection = service.sessionProjectionSequence(firstId);
            long secondProjection = service.sessionProjectionSequence(secondId);
            helper.assertTrue(firstProjection < secondProjection,
                "fixture did not create the source-order counterexample");
            BlockPos held = helper.absolutePos(new BlockPos(8, 221, 3));
            helper.getLevel().setBlockAndUpdate(held,
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            helper.getLevel().getBlockTicks().schedule(new ScheduledTick<>(Blocks.REDSTONE_LAMP,
                held, helper.getLevel().getGameTime() + 15, TickPriority.HIGH, 11));
            // Both possible primaries can reach their next environment entry through normal turns.
            UUID[] ids = {firstId, secondId};
            ServerPlayer[] players = {firstPlayer, secondPlayer};
            UUID[] oldOperations = new UUID[2];
            long[] oldVersions = new long[2];
            OperationRecord.Result[] oldResults = new OperationRecord.Result[2];
            for (int i = 0; i < ids.length; i++) {
                UUID id = ids[i];
                for (int turn = 0; turn < 6
                    && service.state(id).phase() != EncounterPhase.ENVIRONMENT; turn++) {
                    var state = service.state(id);
                    if (players[i].getUUID().equals(state.current()) && oldResults[i] == null) {
                        oldOperations[i] = UUID.randomUUID();
                        oldVersions[i] = state.version();
                        oldResults[i] = service.endTurn(id, players[i], oldOperations[i], oldVersions[i]);
                    } else service.endCurrentTurn(id, UUID.randomUUID(), state.version());
                }
                helper.assertTrue(oldResults[i] != null, "fixture did not record a player turn receipt");
            }
            helper.runAfterDelay(5, () -> {
                UUID canonical = service.encounterOf(firstPlayer.getUUID());
                helper.assertTrue(canonical != null && canonical.equals(service.encounterOf(secondPlayer.getUUID()))
                    && canonical.equals(service.encounterOf(firstMob.getUUID()))
                    && canonical.equals(service.encounterOf(secondMob.getUUID())),
                    "overlapping sessions did not merge at the real world tick boundary");
                helper.assertTrue(service.sessionProjectionSequence(canonical) > secondProjection,
                    "merged primary reused a projection sequence older than a source tombstone");
                helper.assertTrue(service.results(canonical, 0, 100).results().stream()
                    .anyMatch(result -> result.snapshot().kind() == OperationRecord.Kind.MERGE),
                    "merged domain has no immutable merge result");
                int sourceIndex = canonical.equals(firstId) ? 1 : 0;
                helper.assertTrue(oldResults[sourceIndex].equals(service.completedRetry(players[sourceIndex],
                    ids[sourceIndex], oldOperations[sourceIndex], OperationRecord.Kind.END_TURN,
                    oldVersions[sourceIndex], null, true)),
                    "a completed request under the source encounter ID was not replayable after merge");
                boolean conflict = false;
                try {
                    service.completedRetry(players[sourceIndex], ids[sourceIndex], oldOperations[sourceIndex],
                        OperationRecord.Kind.END_TURN, oldVersions[sourceIndex] + 1, null, true);
                } catch (IllegalStateException expected) { conflict = true; }
                helper.assertTrue(conflict, "same operation ID with a changed version was accepted");
                boolean disclosed = false;
                try {
                    service.completedRetry(players[1 - sourceIndex], ids[sourceIndex],
                        oldOperations[sourceIndex], OperationRecord.Kind.END_TURN,
                        oldVersions[sourceIndex], null, true);
                } catch (IllegalStateException expected) { disclosed = true; }
                helper.assertTrue(disclosed, "old encounter result was disclosed to a different participant");
                service.leave(players[sourceIndex].getUUID());
                long versionAfterLeave = service.state(canonical).version();
                int resultsAfterLeave = service.results(canonical, 0, 100).results().size();
                helper.assertTrue(service.encounterOf(players[sourceIndex].getUUID()) == null
                    && oldResults[sourceIndex].equals(service.completedRetry(players[sourceIndex],
                        ids[sourceIndex], oldOperations[sourceIndex], OperationRecord.Kind.END_TURN,
                        oldVersions[sourceIndex], null, true))
                    && service.state(canonical).version() == versionAfterLeave
                    && service.results(canonical, 0, 100).results().size() == resultsAfterLeave,
                    "departed source owner could not replay an immutable merged receipt");
                helper.assertTrue(helper.getLevel().getChunkAt(held)
                    .getTicksForSerialization(helper.getLevel().getGameTime()).blocks().stream()
                    .filter(tick -> tick.pos().equals(held) && tick.type() == Blocks.REDSTONE_LAMP
                        && tick.delay() == 15 - (20 - service.state(canonical).environmentRemaining())
                        && tick.priority() == TickPriority.HIGH).count() == 1,
                    "merge changed the sole held tick owner, delay, or priority");
                int remaining = service.state(canonical).environmentRemaining();
                helper.runAfterDelay(5, () -> {
                    try {
                        helper.assertTrue(service.state(canonical).environmentRemaining() == remaining - 5,
                            "merged encounter did not automatically execute exactly five environment steps");
                        helper.assertTrue(helper.getLevel().getChunkAt(held)
                            .getTicksForSerialization(helper.getLevel().getGameTime()).blocks().stream()
                            .filter(tick -> tick.pos().equals(held) && tick.type() == Blocks.REDSTONE_LAMP
                                && tick.delay() == 15 - (20 - remaining) - 5).count() == 1,
                            "held tick delay did not decrease by exactly five authorized environment steps");
                        helper.succeed();
                    } finally {
                        service.stop(canonical);
                        for (int x = (origin.getX() - 24) >> 4; x <= (origin.getX() + 32) >> 4; x++)
                            for (int z = (origin.getZ() - 24) >> 4; z <= (origin.getZ() + 24) >> 4; z++)
                                helper.getLevel().setChunkForced(x, z, false);
                    }
                });
            });
            });
        });
    }

    private static void candidateTargetReview(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "candidate-review-player");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
            profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(new BlockPos(2049, 261, 1));
        for (int x = 2048; x <= 2052; x++) for (int z = 0; z <= 4; z++)
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 260, z)),
                Blocks.STONE.defaultBlockState());
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        for (int chunkX = (playerPos.getX() - 16) >> 4; chunkX <= (playerPos.getX() + 16) >> 4; chunkX++)
            for (int chunkZ = (playerPos.getZ() - 16) >> 4; chunkZ <= (playerPos.getZ() + 16) >> 4; chunkZ++)
                helper.getLevel().getChunk(chunkX, chunkZ);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2050, 261, 1));
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        CombatEngine.StateView candidate = startEncounter(helper, player);
        try {
            if (!player.getUUID().equals(candidate.current())) {
                zombie.setTarget(null);
                service.endCurrentTurn(candidate.id(), UUID.randomUUID(), candidate.version());
                candidate = service.state(candidate.id());
            }
            helper.assertTrue(candidate.phase() == EncounterPhase.CANDIDATE
                && player.getUUID().equals(candidate.current()), "player candidate turn was not available");
            Difficulty priorDifficulty = helper.getLevel().getDifficulty();
            helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
            try {
                zombie.setTarget(player);
                helper.assertTrue(zombie.getTarget() == player,
                    "test target was rejected by vanilla Mob validation: mode=" + player.gameMode()
                        + " invulnerable=" + player.isInvulnerable() + " canAttack=" + zombie.canAttack(player)
                        + " difficulty=" + helper.getLevel().getDifficulty());
                service.endCurrentTurn(candidate.id(), UUID.randomUUID(), candidate.version());
                helper.assertTrue(service.state(candidate.id()).phase() == EncounterPhase.ACTIVE,
                    "player turn end did not review another member's directed target");
            } finally {
                helper.getLevel().getServer().setDifficulty(priorDifficulty, true);
            }
            helper.succeed();
        } finally {
            service.stop(candidate.id());
        }
    }

    private static void candidateAttackMiss(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerPos = helper.absolutePos(new BlockPos(1025, 261, 1));
        for (int x = 1024; x <= 1028; x++) for (int z = 0; z <= 4; z++)
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 260, z)),
                Blocks.STONE.defaultBlockState());
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        for (int chunkX = (playerPos.getX() - 16) >> 4; chunkX <= (playerPos.getX() + 16) >> 4; chunkX++)
            for (int chunkZ = (playerPos.getZ() - 16) >> 4; chunkZ <= (playerPos.getZ() + 16) >> 4; chunkZ++)
                helper.getLevel().getChunk(chunkX, chunkZ);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1026, 261, 1));
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        CombatEngine.StateView candidate = startEncounter(helper, player);
        try {
            if (!candidate.current().equals(player.getUUID())) {
                zombie.setTarget(null);
                service.endCurrentTurn(candidate.id(), UUID.randomUUID(), candidate.version());
                candidate = service.state(candidate.id());
            }
            helper.assertTrue(candidate.phase() == EncounterPhase.CANDIDATE
                && candidate.current().equals(player.getUUID()),
                "candidate player turn was not available for deterministic attack");
            boolean rejected = false;
            try {
                service.attack(player, UUID.randomUUID(), UUID.randomUUID(),
                    candidate.version());
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected && service.state(candidate.id()).phase() == EncounterPhase.CANDIDATE,
                "invalid attack activated candidate combat");
            service.useAttackRandomForGameTest(candidate.id(), new Random() {
                @Override public int nextInt(int bound) { return 0; }
            });
            zombie.setTarget(null);
            float playerHealth = player.getHealth();
            float zombieHealth = zombie.getHealth();
            OperationRecord.Result miss = service.attack(player, zombie.getUUID(),
                UUID.randomUUID(), service.state(candidate.id()).version());
            helper.assertTrue(miss != null && miss.outcome() == OperationRecord.Outcome.COMPLETED
                && miss.damageTrace() != null && !miss.damageTrace().hit()
                && miss.damageTrace().rollMode() == CombatRules.RollMode.ADVANTAGE
                && service.state(candidate.id()).phase() == EncounterPhase.ACTIVE
                && player.getHealth() == playerHealth && zombie.getHealth() == zombieHealth,
                "legal candidate miss did not spend the action and activate combat");
            helper.assertFalse(service.state(candidate.id()).members().get(miss.snapshot().owner()).action(),
                "candidate miss returned the initiating actor's action");
            helper.succeed();
        } finally {
            service.stop(candidate.id());
        }
    }

    private static void prototypeExitRetry(GameTestHelper helper) {
        ServerPlayer player = makeTargetablePlayer(helper);
        BlockPos playerPos = helper.absolutePos(new BlockPos(3073, 341, 1));
        for (int x = 3072; x <= 3076; x++) for (int z = 0; z <= 4; z++)
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 340, z)),
                Blocks.STONE.defaultBlockState());
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        for (int chunkX = (playerPos.getX() - 24) >> 4; chunkX <= (playerPos.getX() + 24) >> 4; chunkX++)
            for (int chunkZ = (playerPos.getZ() - 24) >> 4; chunkZ <= (playerPos.getZ() + 24) >> 4; chunkZ++)
                helper.getLevel().getChunk(chunkX, chunkZ);
        Zombie exitEnemy = helper.spawn(EntityType.ZOMBIE, new BlockPos(3074, 341, 1));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Difficulty exitDifficulty = helper.getLevel().getDifficulty();
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        exitEnemy.setTarget(player);
        helper.assertTrue(exitEnemy.getTarget() == player, "single exit fixture target missing");
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        var started = startEncounter(helper, player);
        UUID exitId = UUID.randomUUID();
        long version = started.version();
        boolean insideRejected = false;
        try { service.exitEncounter(player, started.id(), exitId, version); }
        catch (IllegalStateException expected) { insideRejected = true; }
        helper.assertTrue(insideRejected && started.id().equals(service.encounterOf(player.getUUID()))
            && service.state(started.id()).version() == version, "inside EXIT mutated the encounter");
        exitEnemy.setTarget(null);
        helper.getLevel().getServer().setDifficulty(exitDifficulty, true);
        service.exitEncounter(player, started.id(), exitId, version);
        helper.assertFalse(MinecraftCombatRuntime.isBodyPaused(player), "no-enemy inside EXIT retained body control");
        helper.assertTrue(service.encounterOf(player.getUUID()) == null
            && service.completedExitRetry(player, started.id(), exitId, version),
            "successful EXIT did not retain its same-generation receipt");
        service.exitEncounter(player, started.id(), exitId, version);
        boolean conflict = false;
        try {
            service.completedExitRetry(player, started.id(), exitId, version + 1);
        } catch (IllegalStateException expected) {
            conflict = true;
        }
        helper.assertTrue(conflict, "EXIT replay accepted a changed request version");
        ServerPlayer stranger = makeTargetablePlayer(helper);
        boolean disclosed = false;
        try {
            service.completedExitRetry(stranger, started.id(), exitId, version);
            disclosed = true;
        } catch (IllegalStateException expected) {
            // The receipt belongs to the original connection player.
        }
        helper.assertFalse(disclosed, "EXIT receipt was disclosed to another player");
        boolean reusedAsAnotherKind = false;
        try {
            service.rejectExitIdReuse(exitId);
        } catch (IllegalStateException expected) {
            reusedAsAnotherKind = true;
        }
        helper.assertTrue(reusedAsAnotherKind, "EXIT ID was available for another intent kind");
        helper.succeed();
    }

    private static void completedRetryAfterLeave(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.connection.markClientLoaded();
        BlockPos playerPos = helper.absolutePos(new BlockPos(4097, 201, 1));
        for (int x = 4096; x <= 4101; x++) for (int z = 0; z <= 4; z++)
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 200, z)),
                Blocks.STONE.defaultBlockState());
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        for (int chunkX = (playerPos.getX() - 24) >> 4; chunkX <= (playerPos.getX() + 24) >> 4; chunkX++)
            for (int chunkZ = (playerPos.getZ() - 24) >> 4; chunkZ <= (playerPos.getZ() + 24) >> 4; chunkZ++)
                helper.getLevel().getChunk(chunkX, chunkZ);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4098, 201, 1));
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        var state = startEncounter(helper, player);
        UUID encounterId = state.id();
        UUID nextId = null;
        try {
            if (!player.getUUID().equals(state.current())) {
                service.endCurrentTurn(encounterId, UUID.randomUUID(), state.version());
                state = service.state(encounterId);
            }
            UUID operationId = UUID.randomUUID();
            long version = state.version();
            OperationRecord.Result original = service.endTurn(encounterId, player, operationId, version);
            service.leave(player.getUUID());
            helper.assertTrue(service.encounterOf(player.getUUID()) == null
                && service.encounterOf(zombie.getUUID()).equals(encounterId),
                "fixture did not leave an active encounter after the owner departed");
            long unchangedVersion = service.state(encounterId).version();
            int unchangedResults = service.results(encounterId, 0, 100).results().size();
            int unchangedBudget = service.state(encounterId).members().get(zombie.getUUID()).movementTicks();
            float playerHealth = player.getHealth();
            float zombieHealth = zombie.getHealth();
            helper.assertTrue(original.equals(service.completedRetry(player, encounterId, operationId,
                OperationRecord.Kind.END_TURN, version, null, true)),
                "departed owner could not read a completed result from an active encounter");
            boolean foreign = false;
            try {
                service.completedRetry(other, encounterId, operationId,
                    OperationRecord.Kind.END_TURN, version, null, true);
            } catch (IllegalStateException expected) { foreign = true; }
            helper.assertTrue(foreign, "another connection read the departed owner's result");
            for (int change = 0; change < 3; change++) {
                boolean conflict = false;
                try {
                    service.completedRetry(player, encounterId, operationId,
                        change == 0 ? OperationRecord.Kind.ATTACK : OperationRecord.Kind.END_TURN,
                        change == 1 ? version + 1 : version,
                        change == 2 ? UUID.randomUUID() : null, true);
                } catch (IllegalStateException expected) { conflict = true; }
                helper.assertTrue(conflict, "changed completed request payload was accepted: " + change);
            }
            helper.assertTrue(service.completedRetry(player, encounterId, UUID.randomUUID(),
                OperationRecord.Kind.END_TURN, version, null, true) == null,
                "unknown operation ID acquired a historical result");
            helper.assertTrue(service.state(encounterId).version() == unchangedVersion
                && service.results(encounterId, 0, 100).results().size() == unchangedResults
                && service.state(encounterId).members().get(zombie.getUUID()).movementTicks() == unchangedBudget
                && player.getHealth() == playerHealth && zombie.getHealth() == zombieHealth,
                "old receipt lookup changed rules or world effects");
            service.leave(zombie.getUUID());
            helper.assertTrue(original.equals(service.completedRetry(player, encounterId, operationId,
                OperationRecord.Kind.END_TURN, version, null, true)),
                "departed owner could not query the closed encounter");
            var next = startEncounter(helper, player);
            nextId = next.id();
            helper.assertTrue(!nextId.equals(encounterId)
                && original.equals(service.completedRetry(player, encounterId, operationId,
                    OperationRecord.Kind.END_TURN, version, null, true)),
                "joining a new encounter obscured the owner's earlier result");
            helper.succeed();
        } finally {
            if (nextId != null) service.stop(nextId);
            if (service.encounterOf(zombie.getUUID()) != null) service.stop(encounterId);
        }
    }

    private static void prototypeFiveRounds(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.connection.markClientLoaded();
        BlockPos playerPos = helper.absolutePos(new BlockPos(513, 121, 1));
        for (int x = 512; x <= 516; x++) for (int z = 0; z <= 4; z++)
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 120, z)),
                Blocks.STONE.defaultBlockState());
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        for (int chunkX = (playerPos.getX() - 24) >> 4; chunkX <= (playerPos.getX() + 24) >> 4; chunkX++)
            for (int chunkZ = (playerPos.getZ() - 24) >> 4; chunkZ <= (playerPos.getZ() + 24) >> 4; chunkZ++)
                helper.getLevel().getChunk(chunkX, chunkZ);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(514, 121, 1));
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        zombie.setHealth(1000);
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        var started = startEncounter(helper, player);
        EncounterRegion region = started.region();
        int minX = Math.floorDiv((int) Math.floor(region.discovery().minX() - region.radius()), 16);
        int maxX = Math.floorDiv((int) Math.floor(region.discovery().maxX() + region.radius()), 16);
        int minZ = Math.floorDiv((int) Math.floor(region.discovery().minZ() - region.radius()), 16);
        int maxZ = Math.floorDiv((int) Math.floor(region.discovery().maxZ() + region.radius()), 16);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            helper.getLevel().setChunkForced(x, z, true);
        AtomicBoolean closed = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (!closed.compareAndSet(false, true)) return;
            service.stop(started.id());
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                helper.getLevel().setChunkForced(x, z, false);
        };
        AtomicBoolean restored = new AtomicBoolean();
        AtomicBoolean laterAttackChecked = new AtomicBoolean();
        AtomicBoolean dashChecked = new AtomicBoolean();
        AtomicBoolean dodgeChecked = new AtomicBoolean();
        AtomicBoolean disengageChecked = new AtomicBoolean();
        for (int tick = 1; tick <= 200; tick++) {
            final int observedTick = tick;
            helper.runAtTickTime(tick, () -> {
                if (closed.get()) return;
                try {
                    var state = service.state(started.id());
                    if (state.round() >= 5) {
                        long mobAttacks = service.results(started.id(), 0, 200).results().stream()
                            .filter(result -> result.snapshot().kind() == OperationRecord.Kind.ATTACK
                                && result.snapshot().owner().equals(zombie.getUUID()) && result.terminal())
                            .count();
                        helper.assertTrue(mobAttacks >= 5 && restored.get() && laterAttackChecked.get()
                                && dashChecked.get() && dodgeChecked.get() && disengageChecked.get(),
                            "five actual rounds did not retain attacks and action resources");
                        cleanup.run();
                        helper.assertFalse(service.isEntitySimulationPaused(player),
                            "normal prototype exit retained regional player control");
                        helper.succeed();
                        return;
                    }
                    if (player.getUUID().equals(state.current())) {
                        if (state.phase() == EncounterPhase.CANDIDATE)
                            service.attack(player, zombie.getUUID(), UUID.randomUUID(),
                                state.version());
                        else if (state.phase() == EncounterPhase.ACTIVE) {
                            if (state.round() > 0 && state.members().get(player.getUUID()).action())
                                restored.set(true);
                            if (restored.get() && !laterAttackChecked.get()) {
                                zombie.setTarget(null);
                                var laterAttack = service.attack(player, zombie.getUUID(),
                                    UUID.randomUUID(), state.version());
                                helper.assertTrue(laterAttack.damageTrace() != null
                                    && laterAttack.damageTrace().rollMode() == CombatRules.RollMode.NORMAL,
                                    "candidate first attack incorrectly reset in the active phase");
                                laterAttackChecked.set(true);
                            }
                            if (state.round() >= 2 && !dashChecked.get()) {
                                var beforeDash = service.state(started.id());
                                int movementBefore = beforeDash.members().get(player.getUUID()).movementTicks();
                                UUID dashId = UUID.randomUUID();
                                var dash = service.dash(player, dashId, beforeDash.version());
                                var afterDash = service.state(started.id());
                                helper.assertTrue(dash.snapshot().kind() == OperationRecord.Kind.DASH
                                        && afterDash.members().get(player.getUUID()).movementTicks()
                                            == movementBefore + 28
                                        && !afterDash.members().get(player.getUUID()).action(),
                                    "DASH did not consume action and grant one movement budget");
                                helper.assertTrue(service.dash(player, dashId, beforeDash.version()) == dash
                                        && service.state(started.id()).version() == afterDash.version(),
                                    "retrying DASH changed resources or result identity");
                                boolean conflict = false;
                                try {
                                    service.dash(player, dashId, afterDash.version());
                                } catch (IllegalStateException expected) {
                                    conflict = true;
                                }
                                helper.assertTrue(conflict, "DASH operation ID accepted a changed request version");
                                dashChecked.set(true);
                            }
                            if (state.round() >= 3 && !dodgeChecked.get()) {
                                var before = service.state(started.id());
                                UUID actionId = UUID.randomUUID();
                                var action = service.defensiveAction(player, actionId,
                                    before.version(), OperationRecord.Kind.DODGE);
                                var after = service.state(started.id());
                                helper.assertTrue(after.members().get(player.getUUID()).dodging()
                                        && !after.members().get(player.getUUID()).action(),
                                    "DODGE did not spend action and set defense");
                                helper.assertTrue(service.defensiveAction(player, actionId,
                                        before.version(), OperationRecord.Kind.DODGE) == action
                                        && service.state(started.id()).version() == after.version(),
                                    "DODGE retry changed resources or result");
                                dodgeChecked.set(true);
                            }
                            if (state.round() >= 4 && !disengageChecked.get()) {
                                var before = service.state(started.id());
                                UUID actionId = UUID.randomUUID();
                                service.defensiveAction(player, actionId, before.version(),
                                    OperationRecord.Kind.DISENGAGE);
                                helper.assertTrue(service.state(started.id()).members()
                                        .get(player.getUUID()).disengaged(),
                                    "DISENGAGE did not set this-turn protection");
                                disengageChecked.set(true);
                            }
                            service.endTurn(started.id(), player, UUID.randomUUID(),
                                service.state(started.id()).version());
                            if (disengageChecked.get())
                                helper.assertFalse(service.state(started.id()).members()
                                    .get(player.getUUID()).disengaged(),
                                    "DISENGAGE remained active after its turn ended");
                        }
                    }
                    if (observedTick == 200) {
                        String diagnosis = "round=" + state.round() + " phase=" + state.phase()
                            + " current=" + state.current() + " player=" + player.getUUID()
                            + " zombie=" + zombie.getUUID() + " env=" + state.environmentRemaining()
                            + " results=" + service.results(started.id(), 0, 200).results().stream()
                                .map(OperationRecord.Result::reason).toList();
                        cleanup.run();
                        helper.assertTrue(false, "prototype did not finish five world-driven rounds: " + diagnosis);
                    }
                } catch (RuntimeException | AssertionError error) {
                    cleanup.run();
                    throw error;
                }
            });
        }
    }

    private static void canceledDeath(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.connection.markClientLoaded();
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 81, 1));
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        for (int x = (playerPos.getX() - 16) >> 4; x <= (playerPos.getX() + 16) >> 4; x++)
            for (int z = (playerPos.getZ() - 16) >> 4; z <= (playerPos.getZ() + 16) >> 4; z++)
                helper.getLevel().getChunk(x, z);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 81, 2));
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        var state = startEncounter(helper, player);
        Consumer<LivingDeathEvent> cancel = event -> {
            if (event.getEntity() == zombie) {
                event.setCanceled(true);
                zombie.setHealth(1);
            }
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            zombie.setHealth(1);
            var source = helper.getLevel().damageSources().source(TacticalDamageContext.DAMAGE_TYPE, player);
            TacticalDamageContext.hurt(helper.getLevel(), zombie, source, 10);
            service.confirmPendingDeaths();
            helper.assertTrue(service.encounterOf(zombie.getUUID()) != null,
                "a canceled death event released a surviving member");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        try {
            var source = helper.getLevel().damageSources().source(TacticalDamageContext.DAMAGE_TYPE, player);
            TacticalDamageContext.hurt(helper.getLevel(), zombie, source, 10);
            helper.assertTrue(!zombie.isAlive(), "authorized second hit did not kill the target");
            service.confirmPendingDeaths();
            helper.assertTrue(service.encounterOf(zombie.getUUID()) == null,
                "confirmed death retained formal membership");
        } finally {
            service.stop(state.id());
        }
        helper.succeed();
    }

    private static void movementLeaseExit(GameTestHelper helper) {
        helper.runAtTickTime(50, () -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.connection.markClientLoaded();
            for (int teleportId = 0; teleportId < 4; teleportId++)
                player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleportId));
            BlockPos pos = helper.absolutePos(new BlockPos(22001, 121, 1));
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            player.connection.resetPosition();
            for (int x = (pos.getX() - 32) >> 4; x <= (pos.getX() + 32) >> 4; x++)
                for (int z = (pos.getZ() - 32) >> 4; z <= (pos.getZ() + 32) >> 4; z++)
                    helper.getLevel().getChunk(x, z);
            for (int x = 22000; x <= 22004; x++) for (int z = 0; z <= 4; z++)
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 120, z)), Blocks.STONE.defaultBlockState());
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(22002, 121, 2));
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            for (int scenario = 0; scenario < 3; scenario++) {
                boolean stop = scenario == 0;
                boolean special = scenario == 2;
                if (!zombie.isAlive() || zombie.isRemoved())
                    zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(22002, 121, 2));
                BlockPos zombiePos = helper.absolutePos(new BlockPos(22002, 121, 2));
                zombie.setPos(zombiePos.getX() + 0.5, zombiePos.getY(), zombiePos.getZ() + 0.5);
                zombie.setHealth(zombie.getMaxHealth());
                CombatEngine.StateView state = startEncounter(helper, player);
                if (!player.getUUID().equals(state.current())) {
                    service.endCurrentTurn(state.id(), UUID.randomUUID(), state.version());
                    state = service.state(state.id());
                }
                service.attack(player, zombie.getUUID(), UUID.randomUUID(), state.version());
                state = service.state(state.id());
                if (!player.getUUID().equals(state.current())) {
                    service.endCurrentTurn(state.id(), UUID.randomUUID(), state.version());
                    state = service.state(state.id());
                }
                if (special) {
                    player.getAbilities().flying = true;
                    boolean denied = false;
                    try {
                        service.beginPlayerMove(player, UUID.randomUUID(), state.version());
                    } catch (IllegalStateException expected) {
                        denied = true;
                    }
                    helper.assertTrue(denied, "flying player received an ordinary movement lease");
                    player.getAbilities().flying = false;
                }
                UUID moveId = UUID.randomUUID();
                service.beginPlayerMove(player, moveId, state.version());
                if (scenario == 1) player.applyPostImpulseGraceTime(40);
                player.connection.handlePlayerInput(new ServerboundPlayerInputPacket(
                    new Input(true, false, false, false, false, false, false)));
                Vec3 before = player.position();
                player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(
                    before.x + 0.2, before.y, before.z, true, false));
                helper.assertTrue(player.position().distanceToSqr(before) > 1.0E-10,
                    "lease exit test did not accept vanilla movement");
                if (special) {
                    player.getAbilities().flying = true;
                    Vec3 proposed = player.position();
                    helper.assertFalse(service.preparePlayerMovePacket(player,
                        new ServerboundMovePlayerPacket.Pos(proposed.x + 0.2,
                            proposed.y, proposed.z, true, false), false),
                        "special movement packet used an ordinary movement lease");
                    helper.assertFalse(service.hasPlayerMoveLease(player.getUUID()),
                        "special movement state did not release the ordinary movement lease");
                    player.getAbilities().flying = false;
                }
                if (stop) {
                    // A server position correction in the same tick makes the packet's
                    // active share unprovable, so closing the lease must not charge it.
                    Vec3 beforeCorrection = player.position();
                    player.setPos(beforeCorrection.x + 0.1, beforeCorrection.y, beforeCorrection.z);
                    service.observePlayerMovePacket(player, beforeCorrection, true,
                        player.onGround(), player.isInWater(), true);
                    Vec3 inside = player.position();
                    player.setPos(inside.x + 64, inside.y, inside.z);
                    service.reconcileMemberLocations();
                    helper.assertTrue(state.id().equals(service.encounterOf(player.getUUID())),
                        "crossing the fixed region removed encounter membership");
                    helper.assertFalse(service.hasPlayerMoveLease(player.getUUID()),
                        "movement lease survived leaving the fixed region");
                    player.setPos(inside.x, inside.y, inside.z);
                }
                if (stop) service.stop(state.id());
                else {
                    service.leave(zombie.getUUID());
                    service.leave(player.getUUID());
                }
                helper.assertFalse(service.hasPlayerMoveLease(player.getUUID()),
                    "lease survived stop or leave");
                int charged = service.results(state.id(), 0, 100).results().stream()
                    .filter(result -> result.snapshot().operationId().equals(moveId))
                    .mapToInt(OperationRecord.Result::actualMovementTicks).sum();
                helper.assertTrue(charged == (stop || special ? 0 : 1),
                    "mixed correction, special mode or ordinary move had the wrong charge in scenario "
                        + scenario + ": " + charged);
            }
            helper.succeed();
        });
    }

    private static void zombieMeleeReach(GameTestHelper helper) {
        helper.runAtTickTime(60, () -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.connection.markClientLoaded();
            BlockPos pos = helper.absolutePos(new BlockPos(22513, 121, 1));
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            for (int x = (pos.getX() - 32) >> 4; x <= (pos.getX() + 32) >> 4; x++)
                for (int z = (pos.getZ() - 32) >> 4; z <= (pos.getZ() + 32) >> 4; z++)
                    helper.getLevel().getChunk(x, z);
            for (int x = 22512; x <= 22516; x++) for (int z = 0; z <= 4; z++)
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 120, z)), Blocks.STONE.defaultBlockState());
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(22514, 121, 1));
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            CombatEngine.StateView state = startEncounter(helper, player);
            if (!player.getUUID().equals(state.current())) {
                service.endCurrentTurn(state.id(), UUID.randomUUID(), state.version());
                state = service.state(state.id());
            }
            service.attack(player, zombie.getUUID(), UUID.randomUUID(), state.version());
            state = service.state(state.id());
            if (player.getUUID().equals(state.current()))
                service.endCurrentTurn(state.id(), UUID.randomUUID(), state.version());
            zombie.setPos(player.getX() + 2, player.getY(), player.getZ());
            helper.assertTrue(service.takeZombieTurn(state.id()) == null
                && !zombie.getUUID().equals(service.state(state.id()).current()),
                "Zombie selected an out-of-reach target or failed to end its turn");
            service.stop(state.id());
            helper.succeed();
        });
    }

    private static void prototypeZombieNavigation(GameTestHelper helper) {
        class NavigationProbe {
            ServerPlayer player;
            BlockPos playerPos;
            Zombie zombie;
            ServerCombatService service;
            UUID encounterId;
            Vec3 initial;
            boolean completed;

            void release() {
                if (encounterId != null) service.stop(encounterId);
                if (playerPos != null)
                    for (int chunkX = (playerPos.getX() - 32) >> 4;
                         chunkX <= (playerPos.getX() + 32) >> 4; chunkX++)
                        for (int chunkZ = (playerPos.getZ() - 32) >> 4;
                             chunkZ <= (playerPos.getZ() + 32) >> 4; chunkZ++)
                            helper.getLevel().setChunkForced(chunkX, chunkZ, false);
            }
        }
        NavigationProbe probe = new NavigationProbe();
        int arenaX = 0;
        int arenaY = 141;
        helper.runAtTickTime(30, () -> {
            probe.player = helper.makeMockServerPlayerInLevel();
            probe.player.connection.markClientLoaded();
            // Give this concurrent GameTest its own arena, including the final region margin.
            probe.playerPos = helper.absolutePos(new BlockPos(arenaX + 1, arenaY, 1));
            probe.player.teleportTo(probe.playerPos.getX() + 0.5, probe.playerPos.getY(),
                probe.playerPos.getZ() + 0.5);
            for (int chunkX = (probe.playerPos.getX() - 32) >> 4;
                 chunkX <= (probe.playerPos.getX() + 32) >> 4; chunkX++)
                for (int chunkZ = (probe.playerPos.getZ() - 32) >> 4;
                     chunkZ <= (probe.playerPos.getZ() + 32) >> 4; chunkZ++) {
                    helper.getLevel().getChunk(chunkX, chunkZ);
                    helper.getLevel().setChunkForced(chunkX, chunkZ, true);
                }
            for (int x = arenaX + 1; x <= arenaX + 6; x++)
                for (int z = 1; z <= 3; z++) {
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, arenaY - 1, z)),
                        Blocks.STONE.defaultBlockState());
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, arenaY, z)),
                        Blocks.AIR.defaultBlockState());
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, arenaY + 1, z)),
                        Blocks.AIR.defaultBlockState());
                }
            probe.zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(arenaX + 2, arenaY, 2));
        });
        helper.runAtTickTime(32, () -> {
            probe.service = ServerCombatService.forServer(helper.getLevel().getServer());
            try {
                helper.assertTrue(!probe.zombie.isRemoved()
                        && helper.getLevel().getEntity(probe.zombie.getUUID()) == probe.zombie,
                    "navigation fixture Zombie disappeared before discovery: zombie="
                        + probe.zombie.position() + " player=" + probe.player.position()
                        + " removal=" + probe.zombie.getRemovalReason());
                var state = startEncounter(helper, probe.player);
                probe.encounterId = state.id();
                if (!probe.player.getUUID().equals(state.current())) {
                    probe.service.endCurrentTurn(state.id(), UUID.randomUUID(), state.version());
                    state = probe.service.state(state.id());
                }
                probe.service.attack(probe.player, probe.zombie.getUUID(),
                    UUID.randomUUID(), state.version());
                BlockPos distant = helper.absolutePos(new BlockPos(arenaX + 4, arenaY, 2));
                probe.zombie.setPos(distant.getX() + 0.5, distant.getY(), distant.getZ() + 0.5);
                probe.zombie.setOnGround(true);
                probe.zombie.setTarget(probe.player);
                var active = probe.service.state(state.id());
                helper.assertTrue(active.phase() == EncounterPhase.ACTIVE,
                    "legal candidate attack did not open an active encounter");
                if (probe.player.getUUID().equals(active.current()))
                    probe.service.endTurn(state.id(), probe.player, UUID.randomUUID(), active.version());
                probe.initial = probe.zombie.position();
            } catch (RuntimeException | Error failure) {
                probe.release();
                throw failure;
            }
        });
        for (int tick = 40; tick <= 75; tick++) {
            final int observedTick = tick;
            helper.runAtTickTime(tick, () -> {
                    if (probe.completed) return;
                    boolean moved = probe.initial.distanceToSqr(probe.zombie.position()) > 0.01;
                    boolean charged = probe.service.results(probe.encounterId, 0, 100).results().stream()
                        .anyMatch(result -> result.snapshot().kind() == OperationRecord.Kind.MOVE
                            && result.snapshot().owner().equals(probe.zombie.getUUID())
                            && result.actualMovementTicks() > 0);
                    if (!(moved && charged) && observedTick < 75) return;
                    probe.completed = true;
                    try {
                        helper.assertTrue(moved && charged,
                            "Zombie did not travel through vanilla navigation during its leased turn: initial="
                                + probe.initial + " current=" + probe.zombie.position() + " player=" + probe.player.position()
                                + " phase=" + probe.service.state(probe.encounterId).phase() + " currentActor="
                                + probe.service.state(probe.encounterId).current() + " navDone="
                                + probe.zombie.getNavigation().isDone() + " leased="
                                + probe.service.hasMobMoveLease(probe.zombie.getUUID()) + " results="
                                + probe.service.results(probe.encounterId, 0, 100).results().stream()
                                    .map(OperationRecord.Result::reason).toList());
                        helper.succeed();
                    } finally {
                        probe.release();
                    }
            });
        }
    }

    private static void mobUnknownRevokesLease(GameTestHelper helper) {
        class WaterProbe {
            BlockPos playerPos;
            ServerPlayer player;
            Zombie zombie;
            ServerCombatService service;
            UUID encounterId;
            int terminalTick = -1;
            int bodyTicks;
            int resultCount;
            Vec3 stoppedAt;
            boolean completed;

            void release() {
                if (encounterId != null) service.stop(encounterId);
                if (playerPos != null)
                    for (int chunkX = (playerPos.getX() - 32) >> 4;
                         chunkX <= (playerPos.getX() + 32) >> 4; chunkX++)
                        for (int chunkZ = (playerPos.getZ() - 32) >> 4;
                             chunkZ <= (playerPos.getZ() + 32) >> 4; chunkZ++)
                            helper.getLevel().setChunkForced(chunkX, chunkZ, false);
            }
        }
        WaterProbe probe = new WaterProbe();
        helper.runAtTickTime(50, () -> {
            int arenaX = 0;
            int arenaY = 181;
            probe.player = helper.makeMockServerPlayerInLevel();
            probe.player.connection.markClientLoaded();
            probe.playerPos = helper.absolutePos(new BlockPos(arenaX + 1, arenaY, 1));
            probe.player.teleportTo(probe.playerPos.getX() + 0.5, probe.playerPos.getY(),
                probe.playerPos.getZ() + 0.5);
            for (int chunkX = (probe.playerPos.getX() - 32) >> 4;
                 chunkX <= (probe.playerPos.getX() + 32) >> 4; chunkX++)
                for (int chunkZ = (probe.playerPos.getZ() - 32) >> 4;
                     chunkZ <= (probe.playerPos.getZ() + 32) >> 4; chunkZ++) {
                    helper.getLevel().getChunk(chunkX, chunkZ);
                    helper.getLevel().setChunkForced(chunkX, chunkZ, true);
                }
            try {
                for (int x = arenaX; x <= arenaX + 7; x++) for (int z = 0; z <= 4; z++) {
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, arenaY - 1, z)),
                        Blocks.STONE.defaultBlockState());
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, arenaY, z)),
                        Blocks.AIR.defaultBlockState());
                    helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, arenaY + 1, z)),
                        Blocks.AIR.defaultBlockState());
                }
                BlockPos water = helper.absolutePos(new BlockPos(arenaX + 4, arenaY, 2));
                helper.getLevel().setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
                probe.zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(arenaX + 2, arenaY, 2));
                probe.service = ServerCombatService.forServer(helper.getLevel().getServer());
                var state = startEncounter(helper, probe.player);
                probe.encounterId = state.id();
                if (!probe.player.getUUID().equals(state.current())) {
                    probe.service.endCurrentTurn(state.id(), UUID.randomUUID(), state.version());
                    state = probe.service.state(state.id());
                }
                probe.service.attack(probe.player, probe.zombie.getUUID(),
                    UUID.randomUUID(), state.version());
                probe.zombie.setPos(water.getX() + 0.5, water.getY(), water.getZ() + 0.5);
                probe.zombie.setOnGround(true);
                probe.zombie.setTarget(probe.player);
                var active = probe.service.state(state.id());
                helper.assertTrue(active.phase() == EncounterPhase.ACTIVE,
                    "legal candidate attack did not open the water-step encounter");
                if (probe.player.getUUID().equals(active.current()))
                    probe.service.endTurn(state.id(), probe.player, UUID.randomUUID(), active.version());
            } catch (RuntimeException | Error failure) {
                probe.release();
                throw failure;
            }
        });
        for (int tick = 53; tick <= 115; tick++) {
            final int observedTick = tick;
            helper.runAtTickTime(tick, () -> {
                if (probe.completed || probe.encounterId == null) return;
                try {
                    if (probe.terminalTick < 0) {
                        if (probe.service.hasMobMoveLease(probe.zombie.getUUID()))
                            probe.service.underreserveNextExpensiveMobStepForGameTest(probe.zombie.getUUID());
                        var results = probe.service.results(probe.encounterId, 0, 100).results();
                        var unknown = results.stream()
                            .filter(result -> result.snapshot().kind() == OperationRecord.Kind.MOVE
                                && result.snapshot().owner().equals(probe.zombie.getUUID())
                                && result.outcome() == OperationRecord.Outcome.UNKNOWN).toList();
                        if (unknown.isEmpty()) {
                            helper.assertTrue(observedTick < 110,
                                "real water step did not produce UNKNOWN: "
                                    + results.stream().map(OperationRecord.Result::reason).toList()
                                    + " phase=" + probe.service.state(probe.encounterId).phase()
                                    + " current=" + probe.service.state(probe.encounterId).current()
                                    + " water=" + probe.zombie.isInWater()
                                    + " leased=" + probe.service.hasMobMoveLease(probe.zombie.getUUID())
                                    + " bodyTicks=" + probe.zombie.tickCount);
                            return;
                        }
                        helper.assertTrue(unknown.size() == 1
                                && unknown.getFirst().reason().contains("reserved=1 observed=2")
                                && unknown.getFirst().reason().contains("delta=(")
                                && unknown.getFirst().reason().contains("remaining="
                                    + probe.service.state(probe.encounterId).members()
                                        .get(probe.zombie.getUUID()).movementTicks()
                                    + " pathOwned=true"),
                            "water step UNKNOWN lacked actual displacement evidence: " + unknown);
                        helper.assertFalse(probe.service.hasMobMoveLease(probe.zombie.getUUID()),
                            "terminal UNKNOWN retained the Mob movement lease");
                        helper.assertTrue(probe.service.isEntitySimulationPaused(probe.zombie)
                                && probe.service.isEntitySimulationPaused(probe.zombie),
                            "repeated body gate checks reopened a terminal Mob lease");
                        probe.service.noteMobTick(probe.zombie.getUUID());
                        probe.bodyTicks = probe.zombie.tickCount;
                        probe.stoppedAt = probe.zombie.position();
                        probe.resultCount = results.size();
                        probe.terminalTick = observedTick;
                    } else if (observedTick >= probe.terminalTick + 3) {
                        helper.assertTrue(probe.zombie.tickCount == probe.bodyTicks
                                && probe.zombie.position().equals(probe.stoppedAt),
                            "terminal lease still authorized a later vanilla body tick");
                        helper.assertTrue(probe.service.results(probe.encounterId, 0, 100)
                                .results().size() == probe.resultCount,
                            "terminal lease emitted a duplicate result after revocation");
                        probe.completed = true;
                        helper.succeed();
                        probe.release();
                    }
                } catch (RuntimeException | Error failure) {
                    probe.completed = true;
                    probe.release();
                    throw failure;
                }
            });
        }
    }

    private static void prototypeEnvironmentLoop(GameTestHelper helper) {
        helper.runAtTickTime(24, () -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.connection.markClientLoaded();
            BlockPos playerPos = helper.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
            for (int chunkX = (playerPos.getX() - 32) >> 4; chunkX <= (playerPos.getX() + 32) >> 4; chunkX++)
                for (int chunkZ = (playerPos.getZ() - 32) >> 4; chunkZ <= (playerPos.getZ() + 32) >> 4; chunkZ++)
                    helper.getLevel().getChunk(chunkX, chunkZ);
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            var created = startEncounter(helper, player);
            // The environment step requires every sampled region chunk to have a block-ticking ticket.
            EncounterRegion region = created.region();
            int minChunkX = Math.floorDiv((int) Math.floor(region.discovery().minX() - region.radius()), 16);
            int maxChunkX = Math.floorDiv((int) Math.floor(region.discovery().maxX() + region.radius()), 16);
            int minChunkZ = Math.floorDiv((int) Math.floor(region.discovery().minZ() - region.radius()), 16);
            int maxChunkZ = Math.floorDiv((int) Math.floor(region.discovery().maxZ() + region.radius()), 16);
            for (int x = minChunkX; x <= maxChunkX; x++)
                for (int z = minChunkZ; z <= maxChunkZ; z++)
                    helper.getLevel().setChunkForced(x, z, true);
            float healthBeforeExternal = zombie.getHealth();
            zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 3);
            helper.assertTrue(zombie.getHealth() == healthBeforeExternal,
                "unsupported external damage changed a prototype member");
            BlockPos lamp = helper.absolutePos(new BlockPos(2, 1, 3));
            helper.getLevel().setBlockAndUpdate(lamp,
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            helper.getLevel().getBlockTicks().schedule(new ScheduledTick<>(Blocks.REDSTONE_LAMP,
                lamp, helper.getLevel().getGameTime() + 1, TickPriority.NORMAL, 1));
            helper.runAtTickTime(26, () -> {
                helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
                    "prototype member phase did not hold a due scheduled block tick");
                for (int i = 0; i < 3 && service.state(created.id()).phase() != EncounterPhase.ENVIRONMENT; i++) {
                    var state = service.state(created.id());
                    service.endCurrentTurn(created.id(), UUID.randomUUID(), state.version());
                }
                var environment = service.state(created.id());
                helper.assertTrue(environment.phase() == EncounterPhase.ENVIRONMENT,
                    "member turns did not enter environment phase");
                int budget = environment.environmentRemaining();
                helper.runAtTickTime(29, () -> {
                    try {
                        var stepped = service.state(created.id());
                        helper.assertFalse(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT),
                            "authorized environment step did not run the held vanilla block tick; budget="
                                + stepped.environmentRemaining() + " expected=" + (budget - 1));
                        helper.assertTrue(stepped.environmentRemaining() < budget,
                            "environment budget was not charged after real world simulation");
                        helper.succeed();
                    } finally {
                        service.stop(created.id());
                        for (int x = minChunkX; x <= maxChunkX; x++)
                            for (int z = minChunkZ; z <= maxChunkZ; z++)
                                helper.getLevel().setChunkForced(x, z, false);
                    }
                });
            });
        });
    }

    private static void scheduledTickHold(GameTestHelper helper) {
        helper.runAtTickTime(24, () -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.connection.markClientLoaded();
            BlockPos playerPos = helper.absolutePos(new BlockPos(1, 1, 1));
            player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
            for (int chunkX = (playerPos.getX() - 16) >> 4; chunkX <= (playerPos.getX() + 16) >> 4; chunkX++)
                for (int chunkZ = (playerPos.getZ() - 16) >> 4; chunkZ <= (playerPos.getZ() + 16) >> 4; chunkZ++) {
                    helper.getLevel().getChunk(chunkX, chunkZ);
                    helper.getLevel().setChunkForced(chunkX, chunkZ, true);
                }
            helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
            BlockPos preexisting = helper.absolutePos(new BlockPos(2, 1, 4));
            helper.getLevel().setBlockAndUpdate(preexisting,
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            helper.getLevel().getBlockTicks().schedule(new ScheduledTick<>(Blocks.REDSTONE_LAMP,
                preexisting, helper.getLevel().getGameTime() + 1, TickPriority.NORMAL, 0));
            var preexistingChunk = helper.getLevel().getChunkAt(preexisting);
            preexistingChunk.tryMarkSaved();
            ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
            var state = startEncounter(helper, player);
            helper.assertTrue(preexistingChunk.isUnsaved(),
                "capturing an existing vanilla tick did not make its chunk eligible for saving");
            BlockPos inside = helper.absolutePos(new BlockPos(2, 1, 3));
            BlockPos outside = helper.absolutePos(new BlockPos(2, 25, 3));
            BlockPos fluid = helper.absolutePos(new BlockPos(5, 1, 5));
            helper.getLevel().setBlockAndUpdate(inside,
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            helper.getLevel().setBlockAndUpdate(outside,
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
            for (Direction side : Direction.Plane.HORIZONTAL)
                helper.getLevel().setBlockAndUpdate(fluid.relative(side), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(fluid.below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(fluid, Blocks.WATER.defaultBlockState());
            long due = helper.getLevel().getGameTime() + 1;
            var insideChunk = helper.getLevel().getChunkAt(inside);
            insideChunk.tryMarkSaved();
            helper.getLevel().getBlockTicks().schedule(new ScheduledTick<>(Blocks.REDSTONE_LAMP,
                inside, due, TickPriority.NORMAL, 1));
            helper.assertTrue(insideChunk.isUnsaved(),
                "moving a tick into held ownership did not make its chunk eligible for saving");
            helper.getLevel().getBlockTicks().schedule(new ScheduledTick<>(Blocks.REDSTONE_LAMP,
                outside, due, TickPriority.NORMAL, 2));
            helper.getLevel().getFluidTicks().schedule(new ScheduledTick<>(Fluids.WATER,
                fluid, due, TickPriority.HIGH, 3));
            helper.runAtTickTime(26, () -> {
                helper.assertTrue(helper.getLevel().getBlockState(preexisting).getValue(RedstoneLampBlock.LIT),
                    "vanilla tick captured at encounter creation ran during the paused phase: initial=" + state.phase()
                        + " current=" + service.state(state.id()).phase() + " domain=" + service.encounterAtBlock(helper.getLevel(), preexisting)
                        + " expected=" + state.id() + " held=" + helper.getLevel().getBlockTicks().hasScheduledTick(preexisting, Blocks.REDSTONE_LAMP));
                helper.assertTrue(helper.getLevel().getBlockState(inside).getValue(RedstoneLampBlock.LIT),
                    "regional scheduled block tick ran during paused member phase");
                helper.assertFalse(helper.getLevel().getBlockState(outside).getValue(RedstoneLampBlock.LIT),
                    "outside scheduled block tick was paused: inRegion="
                        + state.region().containsBlock(outside.getX(), outside.getY(), outside.getZ())
                        + " queued=" + helper.getLevel().getBlockTicks()
                            .hasScheduledTick(outside, Blocks.REDSTONE_LAMP)
                        + " blockTicking=" + helper.getLevel().shouldTickBlocksAt(outside)
                        + " gameTime=" + helper.getLevel().getGameTime());
                helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(inside, Blocks.REDSTONE_LAMP),
                    "held tick disappeared from hasScheduledTick query");
                helper.assertTrue(helper.getLevel().getFluidTicks().hasScheduledTick(fluid, Fluids.WATER),
                    "regional scheduled fluid tick was not held");
                int heldCount = helper.getLevel().getBlockTicks().count();
                helper.getLevel().getBlockTicks().schedule(new ScheduledTick<>(Blocks.REDSTONE_LAMP,
                    inside, helper.getLevel().getGameTime() + 40, TickPriority.LOW, 40));
                helper.assertTrue(helper.getLevel().getBlockTicks().count() == heldCount,
                    "duplicate position/type was accepted while the first tick was held");
                var area = new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    inside.getX(), inside.getY(), inside.getZ(),
                    inside.getX(), inside.getY(), inside.getZ());
                BlockPos copied = inside.east();
                helper.getLevel().getBlockTicks().copyArea(area, new net.minecraft.core.Vec3i(1, 0, 0));
                helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(copied, Blocks.REDSTONE_LAMP),
                    "copyArea omitted the held source tick");
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(copied.east(), Blocks.REDSTONE_LAMP),
                    "copyArea copied a tick it had just generated");
                helper.getLevel().getBlockTicks().clearArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    copied.getX(), copied.getY(), copied.getZ(),
                    copied.getX(), copied.getY(), copied.getZ()));
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(copied, Blocks.REDSTONE_LAMP),
                    "clearArea retained a copied held tick");
                helper.assertTrue(helper.getLevel().getChunkAt(inside)
                    .getTicksForSerialization(helper.getLevel().getGameTime()).blocks().stream()
                    .anyMatch(tick -> tick.pos().equals(inside) && tick.type() == Blocks.REDSTONE_LAMP
                        && tick.delay() == 1),
                    "chunk serialization omitted the held scheduled tick");
                helper.assertTrue(helper.getLevel().getChunkAt(inside)
                    .getTicksForSerialization(helper.getLevel().getGameTime()).blocks().stream()
                    .filter(tick -> tick.pos().equals(inside) && tick.type() == Blocks.REDSTONE_LAMP)
                    .count() == 1 && helper.getLevel().getBlockTicks().count() == heldCount,
                    "repeated serialization changed live held ownership");
                helper.assertTrue(helper.getLevel().getChunkAt(fluid)
                    .getTicksForSerialization(helper.getLevel().getGameTime()).fluids().stream()
                    .anyMatch(tick -> tick.pos().equals(fluid) && tick.type() == Fluids.WATER),
                    "chunk serialization omitted the held fluid tick");
                // Exercise the same fixed-version unregister/register seam used by actual chunk
                // unload and load. This remains an in-memory lifecycle check, not disk recovery.
                insideChunk.unregisterTickContainerFromLevel(helper.getLevel());
                helper.assertFalse(helper.getLevel().getBlockTicks().hasScheduledTick(inside, Blocks.REDSTONE_LAMP),
                    "unregistered chunk left a held tick in the level queue");
                helper.assertTrue(insideChunk.getTicksForSerialization(helper.getLevel().getGameTime())
                    .blocks().stream().filter(tick -> tick.pos().equals(inside)
                        && tick.type() == Blocks.REDSTONE_LAMP).count() == 1,
                    "unload transfer duplicated or lost the scheduled block tick");
                insideChunk.registerTickContainerInLevel(helper.getLevel());
                helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(inside, Blocks.REDSTONE_LAMP)
                    && helper.getLevel().getBlockTicks().count() == heldCount,
                    "reload changed the single owner of the scheduled tick");
            });
            helper.runAtTickTime(28, () -> {
                helper.assertTrue(helper.getLevel().getBlockState(inside).getValue(RedstoneLampBlock.LIT),
                    "reloaded scheduled tick ran before the paused encounter advanced");
                helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(inside, Blocks.REDSTONE_LAMP),
                    "reloaded scheduled tick was lost during paused recapture");
                service.stop(state.id());
                helper.assertTrue(helper.getLevel().getBlockTicks().hasScheduledTick(preexisting,
                    Blocks.REDSTONE_LAMP) && helper.getLevel().getBlockTicks().hasScheduledTick(inside,
                    Blocks.REDSTONE_LAMP), "encounter exit did not return held ticks to vanilla");
            });
            // GameTest callbacks may run before this world's scheduled queue in the same tick.
            helper.runAtTickTime(32, () -> {
                try {
                    helper.assertFalse(helper.getLevel().getBlockState(preexisting).getValue(RedstoneLampBlock.LIT),
                        "captured vanilla tick did not resume after the encounter ended");
                    helper.assertFalse(helper.getLevel().getBlockState(inside).getValue(RedstoneLampBlock.LIT),
                        "normal encounter exit did not return the held tick to vanilla");
                    helper.succeed();
                } finally {
                    for (int chunkX = (playerPos.getX() - 16) >> 4;
                         chunkX <= (playerPos.getX() + 16) >> 4; chunkX++)
                        for (int chunkZ = (playerPos.getZ() - 16) >> 4;
                             chunkZ <= (playerPos.getZ() + 16) >> 4; chunkZ++)
                            helper.getLevel().setChunkForced(chunkX, chunkZ, false);
                }
            });
        });
    }

    private static void developmentAttack(GameTestHelper helper) {
        helper.runAtTickTime(5, () -> developmentAttackIsolated(helper));
    }

    private static void tacticalShieldWear(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.connection.markClientLoaded();
        player.getAbilities().invulnerable = false;
        player.getAbilities().instabuild = false;
        ItemStack shield = new ItemStack(Items.SHIELD);
        BlocksAttacks originalBlocking = shield.get(DataComponents.BLOCKS_ATTACKS);
        helper.assertTrue(originalBlocking != null, "test shield has no blocking component");
        // Embedded mock players do not advance item-use time. Keep every vanilla blocking
        // component value except the delay so getItemBlockingWith can become true.
        shield.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(0.0F,
            originalBlocking.disableCooldownScale(), originalBlocking.damageReductions(),
            originalBlocking.itemDamage(), originalBlocking.bypassedBy(),
            originalBlocking.blockSound(), originalBlocking.disableSound()));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, shield);
        Zombie attacker = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        attacker.setNoAi(true); // The fixture must not interrupt the timed blocking setup.
        player.startUsingItem(InteractionHand.OFF_HAND);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(player.getItemBlockingWith() != null,
                "shield never reached the vanilla blocking state: using=" + player.isUsingItem()
                    + " remaining=" + player.getUseItemRemainingTicks()
                    + " useItem=" + player.getUseItem()
                    + " offhand=" + player.getOffhandItem());
            var source = helper.getLevel().damageSources().source(TacticalDamageContext.DAMAGE_TYPE, attacker);
            Vec3 toward = attacker.position().subtract(player.position());
            float facingAttacker = (float) (Math.atan2(toward.z, toward.x) * 180.0 / Math.PI) - 90.0F;
            player.setYHeadRot(facingAttacker);
            int before = player.getOffhandItem().getDamageValue();
            float healthBefore = player.getHealth();
            var front = TacticalDamageContext.hurtObserved(helper.getLevel(), player, source, 5,
                false, UUID.randomUUID());
            helper.assertTrue(front.accepted() && front.shieldWearCalled()
                && front.shieldBlockedContribution() > 0,
                "scoped tactical damage was rejected while blocking");
            helper.assertTrue(player.getOffhandItem().getDamageValue() > before,
                "accepted tactical damage skipped blocking shield durability");
            helper.assertTrue(Math.abs(healthBefore - player.getHealth() - 5) < 0.001F,
                "directional shield wear reduced tactical damage a second time");
            player.setYHeadRot(facingAttacker + 180.0F);
            int afterFrontHit = player.getOffhandItem().getDamageValue();
            var back = TacticalDamageContext.hurtObserved(helper.getLevel(), player, source, 5,
                false, UUID.randomUUID());
            helper.assertTrue(back.accepted() && !back.shieldWearCalled(),
                "back-facing tactical damage was rejected");
            helper.assertTrue(player.getOffhandItem().getDamageValue() == afterFrontHit,
                "back-facing tactical damage wore the raised shield");
            player.stopUsingItem();
            int afterBlocking = player.getOffhandItem().getDamageValue();
            helper.assertTrue(TacticalDamageContext.hurt(helper.getLevel(), player, source, 5),
                "follow-up tactical damage was rejected after shield use ended");
            helper.assertTrue(player.getOffhandItem().getDamageValue() == afterBlocking,
                "a held but inactive shield lost durability");
            helper.succeed();
        });
    }

    private static void developmentAttackIsolated(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.connection.markClientLoaded();
        // Acknowledge the mock connection's initial teleport before placing it in the structure.
        for (int teleportId = 0; teleportId < 4; teleportId++)
            player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleportId));
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 221, 1));
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++)
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 220, z)),
                Blocks.STONE.defaultBlockState());
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        for (int chunkX = (playerPos.getX() - 16) >> 4; chunkX <= (playerPos.getX() + 16) >> 4; chunkX++) {
            for (int chunkZ = (playerPos.getZ() - 16) >> 4; chunkZ <= (playerPos.getZ() + 16) >> 4; chunkZ++) {
                helper.getLevel().getChunk(chunkX, chunkZ);
            }
        }
        Zombie member = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 221, 2));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        player.getAbilities().instabuild = false;
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        CombatEngine.StateView state = startEncounter(helper, player);
        if (!player.getUUID().equals(state.current())) {
            service.endCurrentTurn(state.id(), UUID.randomUUID(), state.version());
            state = service.state(state.id());
        }
        helper.assertTrue(player.getUUID().equals(state.current()), "candidate attacker did not get a turn");
        RepairGameTests.inventorySwap(helper, player);
        service.useAttackRandomForGameTest(state.id(), new Random() {
            @Override public int nextInt(int bound) { return bound - 1; }
        });
        UUID attackId = UUID.randomUUID();
        long requestedVersion = state.version();
        float memberHealth = member.getHealth();
        OperationRecord.Result attack = service.attack(player, member.getUUID(),
            attackId, requestedVersion);
        helper.assertTrue(attack.outcome() == OperationRecord.Outcome.COMPLETED,
            "supported attack did not finish");
        helper.assertTrue(service.state(state.id()).phase() == EncounterPhase.ACTIVE,
            "candidate attack did not activate encounter");
        helper.assertFalse(service.state(state.id()).members().get(player.getUUID()).action(),
            "candidate attack did not spend its action");
        float afterFirstAttack = member.getHealth();
        helper.assertTrue(Math.abs(memberHealth - afterFirstAttack - attack.actualDamage()) < 0.001F,
            "reported health loss differs from observed health");
        helper.assertTrue(attack.damageTrace() != null
            && attack.damageTrace().targetId().equals(member.getUUID())
            && attack.damageTrace().healthLoss() == attack.actualDamage()
            && attack.damageTrace().selectedDie() >= 1
            && attack.damageTrace().selectedDie() <= 20,
            "attack result did not preserve the rule roll and observed health loss");
        var evidence = attack.damageTrace().evidence();
        helper.assertTrue(evidence != null && evidence.sourceId().equals(player.getUUID())
            && evidence.rulesRevision().equals(CombatRules.RULES_REVISION)
            && evidence.regionVersion() == state.region().version()
            && evidence.stage() == (attack.damageTrace().hit()
                ? attack.damageTrace().tacticalDamage() == 0 ? cc.sighs.dndturn.combat.DamageTrace.Stage.ZERO_DAMAGE
                    : attack.damageTrace().vanillaAccepted()
                        ? cc.sighs.dndturn.combat.DamageTrace.Stage.VANILLA_ACCEPTED
                        : cc.sighs.dndturn.combat.DamageTrace.Stage.VANILLA_REJECTED
                : cc.sighs.dndturn.combat.DamageTrace.Stage.MISS),
            "attack result omitted the captured rule/source/stage evidence");
        helper.assertTrue(evidence.equipmentChanges().stream()
            .anyMatch(change -> change.ownerId().equals(player.getUUID())
                && change.slot().equals("mainhand") && change.afterDamage() == 1)
            == attack.damageTrace().vanillaAccepted(),
            "weapon wear was not recorded in the immutable equipment delta");
        helper.assertTrue(player.getMainHandItem().getDamageValue()
            == (attack.damageTrace().vanillaAccepted() ? 1 : 0),
            "weapon durability did not follow the accepted vanilla hit: damage="
                + player.getMainHandItem().getDamageValue() + " accepted="
                + attack.damageTrace().vanillaAccepted() + " hit=" + attack.damageTrace().hit());
        OperationRecord.Result retried = service.attack(player, member.getUUID(),
            attackId, requestedVersion);
        helper.assertTrue(retried.equals(attack) && member.getHealth() == afterFirstAttack,
            "attack retry executed world damage again");

        Zombie pipelineTarget = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 245, 2));
        pipelineTarget.setNoGravity(true);
        pipelineTarget.getAttribute(Attributes.ARMOR).setBaseValue(20);
        pipelineTarget.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2);
        pipelineTarget.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4));
        pipelineTarget.setAbsorptionAmount(2);
        pipelineTarget.invulnerableTime = 20;
        var source = helper.getLevel().damageSources().source(TacticalDamageContext.DAMAGE_TYPE, player);
        helper.assertTrue(source.is(DamageTypeTags.BYPASSES_ARMOR)
            && source.is(DamageTypeTags.BYPASSES_RESISTANCE)
            && source.is(DamageTypeTags.BYPASSES_COOLDOWN), "tactical damage tags did not load");
        float healthBefore = pipelineTarget.getHealth();
        helper.assertTrue(TacticalDamageContext.hurt(helper.getLevel(), pipelineTarget, source, 5),
            "authorized tactical source was blocked by old invulnerability time");
        helper.assertTrue(Math.abs(healthBefore - pipelineTarget.getHealth() - 3) < 0.001F,
            "tactical damage was reduced twice or absorption ignored: before=" + healthBefore
                + ", after=" + pipelineTarget.getHealth() + ", absorption="
                + pipelineTarget.getAbsorptionAmount());
        helper.assertTrue(pipelineTarget.getAbsorptionAmount() == 0 && pipelineTarget.invulnerableTime == 0,
            "tactical hit left absorption or a new invulnerability frame");
        Vec3 beforeKnockback = pipelineTarget.position();
        helper.assertTrue(TacticalDamageContext.hurt(helper.getLevel(), pipelineTarget, source, 1, true)
            && pipelineTarget.position().distanceToSqr(beforeKnockback) > 1.0E-10,
            "enabled tactical knockback did not move the paused target through vanilla collision");
        player.getAbilities().invulnerable = false;
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
            new ItemStack(Items.IRON_CHESTPLATE));
        player.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(2);
        player.setAbsorptionAmount(2);
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4));
        player.invulnerableTime = 20;
        float playerHealthBefore = player.getHealth();
        var zombieSource = helper.getLevel().damageSources().source(TacticalDamageContext.DAMAGE_TYPE, member);
        helper.assertTrue(TacticalDamageContext.hurt(helper.getLevel(), player, zombieSource, 5),
            "ServerPlayer hurtServer override rejected scoped tactical damage");
        helper.assertTrue(Math.abs(playerHealthBefore - player.getHealth() - 3) < 0.001F
            && player.getAbsorptionAmount() == 0 && player.invulnerableTime == 0,
            "ServerPlayer override re-applied a tactical reduction or cooldown");
        helper.assertTrue(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getDamageValue() > 0,
            "bypassing armor reduction also skipped vanilla armor wear");
        boolean zombieActedFirst = member.getUUID().equals(service.state(state.id()).current());
        if (zombieActedFirst) {
            OperationRecord.Result zombieChoice = service.takeZombieTurn(state.id());
            helper.assertTrue(zombieChoice != null
                && zombieChoice.outcome() == OperationRecord.Outcome.COMPLETED
                && zombieChoice.damageTrace().targetId().equals(player.getUUID()),
                "higher-initiative Zombie did not complete its supported attack");
        }
        player.connection.resetPosition();
        var moveState = service.state(state.id());
        int moveBudget = moveState.members().get(player.getUUID()).movementTicks();
        UUID moveId = UUID.randomUUID();
        helper.assertTrue(service.beginPlayerMove(player, moveId, moveState.version()) == null,
            "player movement lease did not start");
        helper.assertTrue(service.beginPlayerMove(player, moveId, moveState.version()) == null,
            "identical pending movement retry did not return existing status");
        helper.assertTrue(service.isEntitySimulationPaused(player)
            && MinecraftCombatRuntime.isBodyPaused(player)
            && !MinecraftCombatRuntime.isPlayerMovementPaused(player),
            "movement lease did not separate movement input from body simulation");
        helper.assertTrue(MinecraftCombatRuntime.isGameplayInputPaused(player),
            "movement lease unexpectedly opened other gameplay inputs");
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        player.connection.handleUseItem(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, 1,
            player.getYRot(), player.getXRot()));
        helper.assertFalse(player.isUsingItem(),
            "movement lease let a shield-use packet bypass the gameplay input gate");
        helper.assertTrue(((cc.sighs.dndturn.mixin.PredictionAckAccessor) player.connection).dndturn$pendingAck() >= 1,
            "rejected use did not acknowledge the client prediction sequence");
        int selected = player.getInventory().getSelectedSlot();
        int alternate = (selected + 1) % 9;
        player.connection.handleSetCarriedItem(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(alternate));
        helper.assertTrue(player.getInventory().getSelectedSlot() == selected, "legacy hotbar packet bypassed confirmed selection");
        var selfSelection = new cc.sighs.dndturn.combat.TacticalIntent.Target(cc.sighs.dndturn.combat.TacticalIntent.TargetKind.SELF,
            player.level().dimension().identifier().toString(),null,null,-1,0,0,0);
        service.tacticalActions().discover(player,new cc.sighs.dndturn.combat.TacticalNetwork.Query(service.generation(),state.id(),UUID.randomUUID(),
            selfSelection,cc.sighs.dndturn.combat.TacticalIntent.Hand.MAIN_HAND,alternate,1,null));
        helper.assertTrue(player.getInventory().getSelectedSlot() == alternate,"confirmed own-turn selection failed");
        service.tacticalActions().discover(player,new cc.sighs.dndturn.combat.TacticalNetwork.Query(service.generation(),state.id(),UUID.randomUUID(),
            selfSelection,cc.sighs.dndturn.combat.TacticalIntent.Hand.MAIN_HAND,selected,2,null));
        player.connection.handleSetCarriedItem(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(selected));
        ItemStack beforeThrow = player.getMainHandItem().copy();
        player.connection.handleContainerClick(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(
            player.inventoryMenu.containerId, player.inventoryMenu.getStateId(), (short) (36 + selected), (byte) 1,
            net.minecraft.world.inventory.ContainerInput.THROW, it.unimi.dsi.fastutil.ints.Int2ObjectMaps.emptyMap(),
            net.minecraft.network.HashedStack.EMPTY));
        helper.assertTrue(ItemStack.matches(beforeThrow, player.getMainHandItem()), "container THROW bypassed drop authorization");
        helper.assertTrue(service.state(state.id()).members().get(player.getUUID()).movementTicks() == moveBudget,
            "inventory organization consumed movement budget");
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 100));
        int effectDuringMove = player.getEffect(MobEffects.SPEED).getDuration();
        player.startUsingItem(InteractionHand.OFF_HAND);
        player.connection.handlePlayerInput(new ServerboundPlayerInputPacket(Input.EMPTY));
        int useTicksDuringMove = player.getUseItemRemainingTicks();
        var beforeMove = player.position();
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(
            beforeMove.x + 0.2, beforeMove.y, beforeMove.z, true, false));
        helper.assertTrue(player.position().distanceToSqr(beforeMove) > 1.0E-10,
            "vanilla player movement was not accepted: before=" + beforeMove
                + " after=" + player.position() + " clientLoaded=" + player.connection.hasClientLoaded());
        UUID encounterId = state.id();
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(player.getEffect(MobEffects.SPEED).getDuration() == effectDuringMove
                && player.getUseItemRemainingTicks() == useTicksDuringMove,
                "movement lease advanced effects or an existing item use without charging movement");
            helper.assertTrue(service.state(encounterId).members().get(player.getUUID()).movementTicks() == moveBudget - 1,
                "accepted displacement did not cost exactly one movement tick at ServerTick.Post");
            helper.getLevel().setBlockAndUpdate(player.blockPosition().east(), Blocks.STONE.defaultBlockState());
            var blockedStart = player.position();
            player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(
                blockedStart.x + 0.5, blockedStart.y, blockedStart.z, true, false));
            helper.assertTrue(player.position().distanceToSqr(blockedStart) <= 1.0E-10,
                "collision test unexpectedly moved the player");
            helper.runAtTickTime(7, () -> {
                helper.assertTrue(service.state(encounterId).members().get(player.getUUID()).movementTicks() == moveBudget - 1,
                    "blocked vanilla movement consumed movement budget");
                player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(
                    player.getX(), player.getY(), player.getZ(), true, false));
                OperationRecord.Result moveResult = service.finishPlayerMove(player,
                    moveId, moveState.version());
                helper.assertTrue(service.finishPlayerMove(player, moveId,
                    moveState.version()).equals(moveResult),
                    "duplicate network movement finish did not return the original result");
                boolean endVersionConflict = false;
                try {
                    service.completedRetry(player, encounterId, moveId, OperationRecord.Kind.MOVE,
                        moveState.version() + 1, null, false);
                } catch (IllegalStateException expected) { endVersionConflict = true; }
                helper.assertTrue(endVersionConflict, "MOVE_END replay accepted a changed payload version");
                helper.assertTrue(service.beginPlayerMove(player, moveId, moveState.version())
                    .equals(moveResult), "completed movement retry did not return the existing result");
                helper.assertTrue(moveResult.outcome() == OperationRecord.Outcome.COMPLETED
                    && service.results(encounterId, 0, 100).results().stream()
                        .filter(result -> result.snapshot().operationId().equals(moveId))
                        .mapToInt(OperationRecord.Result::actualMovementTicks).sum() == 1,
                    "movement result did not retain its observed charged step");
                helper.assertTrue(service.isEntitySimulationPaused(player),
                    "closing the movement lease did not restore the regional body gate");
                helper.getLevel().setBlockAndUpdate(player.blockPosition().east(), Blocks.AIR.defaultBlockState());
                helper.runAtTickTime(8, () -> {
                // The body's east edge can enter water while its center block remains dry.
                BlockPos edgeWater = player.blockPosition().east();
                helper.getLevel().setBlockAndUpdate(edgeWater, Blocks.WATER.defaultBlockState());
                var wetMoveState = service.state(encounterId);
                UUID wetMoveId = UUID.randomUUID();
                service.beginPlayerMove(player, wetMoveId, wetMoveState.version());
                for (int teleportId = 0; teleportId < 16; teleportId++)
                    player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(teleportId));
                player.connection.resetPosition();
                Vec3 dryCenter = player.position();
                player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(
                    dryCenter.x + 0.1, dryCenter.y, dryCenter.z, true, false));
                helper.assertTrue(player.blockPosition().equals(BlockPos.containing(dryCenter)),
                    "water overlap test crossed into a different center block");
                OperationRecord.Result wetMove = service.finishPlayerMove(player);
                helper.assertTrue(service.finishPlayerMove(player, wetMoveId,
                    wetMoveState.version()).equals(wetMove),
                    "MOVE_END after automatic terminal movement did not bind the original result");
                helper.assertTrue(wetMove.outcome() == OperationRecord.Outcome.COMPLETED
                    && service.results(encounterId, 0, 100).results().stream()
                        .filter(result -> result.snapshot().operationId().equals(wetMoveId))
                        .mapToInt(OperationRecord.Result::actualMovementTicks).sum() == 2
                    && service.state(encounterId).members().get(player.getUUID()).movementTicks()
                        == moveBudget - 3,
                    "body-edge water overlap was not charged as one water movement tick: result="
                        + wetMove + " budget=" + service.state(encounterId).members().get(player.getUUID()).movementTicks()
                        + " before=" + dryCenter + " after=" + player.position()
                        + " edgeWater=" + edgeWater);
                helper.getLevel().setBlockAndUpdate(edgeWater, Blocks.AIR.defaultBlockState());
                service.endCurrentTurn(encounterId, UUID.randomUUID(),
                    service.state(encounterId).version());
                if (!zombieActedFirst) {
                    helper.assertTrue(member.getUUID().equals(service.state(encounterId).current()),
                        "Zombie did not receive its own turn");
                    OperationRecord.Result zombieChoice = service.takeZombieTurn(encounterId);
                    helper.assertTrue(zombieChoice != null
                        && zombieChoice.outcome() == OperationRecord.Outcome.COMPLETED
                        && zombieChoice.damageTrace().targetId().equals(player.getUUID()),
                        "supported Zombie did not choose an attack and end its turn");
                }
                helper.assertTrue(service.state(encounterId).phase() == EncounterPhase.ENVIRONMENT,
                    "both initiative turns did not reach the environment phase");
                service.stop(encounterId);
                helper.assertFalse(service.hasPlayerMoveLease(player.getUUID())
                    || service.isEntitySimulationPaused(player),
                    "stopping an encounter left player movement control behind");
                helper.succeed();
                });
            });
        });
    }

    private static void regionalEntityGate(GameTestHelper helper) {
        int arenaX = 12288;
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.connection.markClientLoaded();
        BlockPos playerPos = helper.absolutePos(new BlockPos(arenaX + 1, 1, 1));
        for (int x = arenaX; x <= arenaX + 6; x++) for (int z = 0; z <= 6; z++)
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)),
                Blocks.STONE.defaultBlockState());
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 100));
        int effectDuration = player.getEffect(MobEffects.SPEED).getDuration();
        for (int chunkX = (playerPos.getX() - 16) >> 4; chunkX <= (playerPos.getX() + 24) >> 4; chunkX++) {
            for (int chunkZ = (playerPos.getZ() - 16) >> 4; chunkZ <= (playerPos.getZ() + 16) >> 4; chunkZ++) {
                helper.getLevel().getChunk(chunkX, chunkZ);
            }
        }
        int arenaMinChunkX = (playerPos.getX() - 1) >> 4;
        int arenaMaxChunkX = (playerPos.getX() + 5) >> 4;
        int arenaMinChunkZ = (playerPos.getZ() - 1) >> 4;
        int arenaMaxChunkZ = (playerPos.getZ() + 5) >> 4;
        for (int chunkX = arenaMinChunkX; chunkX <= arenaMaxChunkX; chunkX++)
            for (int chunkZ = arenaMinChunkZ; chunkZ <= arenaMaxChunkZ; chunkZ++)
                helper.getLevel().setChunkForced(chunkX, chunkZ, true);
        helper.spawn(EntityType.ZOMBIE, new BlockPos(arenaX + 2, 1, 2));
        ArmorStand inside = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(arenaX + 3, 1, 2));
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        CombatEngine.StateView state = startEncounter(helper, player);
        ArmorStand outside = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(arenaX + 3, 25, 2));
        outside.setNoGravity(true);
        var outsideVehicle = helper.spawn(EntityType.PIG, new BlockPos(arenaX + 3, 25, 4));
        outsideVehicle.setNoGravity(true);
        ArmorStand insidePassenger = helper.spawn(EntityType.ARMOR_STAND,
            new BlockPos(arenaX + 3, 25, 4));
        helper.assertTrue(insidePassenger.startRiding(outsideVehicle, true, false),
            "passenger test could not establish riding relation");
        BlockPos passengerPos = helper.absolutePos(new BlockPos(arenaX + 3, 1, 4));
        insidePassenger.setPos(passengerPos.getX() + 0.5, passengerPos.getY(), passengerPos.getZ() + 0.5);
        TickCounter insideBlock = new TickCounter(helper.absolutePos(new BlockPos(arenaX + 2, 1, 4)));
        TickCounter outsideBlock = new TickCounter(helper.absolutePos(new BlockPos(arenaX + 2, 25, 4)));
        helper.getLevel().addBlockEntityTicker(insideBlock);
        helper.getLevel().addBlockEntityTicker(outsideBlock);
        BlockPos insidePiston = helper.absolutePos(new BlockPos(arenaX + 3, 1, 3));
        BlockPos outsidePiston = helper.absolutePos(new BlockPos(arenaX + 3, 25, 3));
        var piston = Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST);
        for (BlockPos pos : new BlockPos[] {insidePiston, outsidePiston}) {
            helper.getLevel().setBlock(pos, piston, 3);
            helper.getLevel().setBlock(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            helper.getLevel().blockEvent(pos, Blocks.PISTON, 0, Direction.WEST.get3DDataValue());
        }
        BlockPos insideOre = helper.absolutePos(new BlockPos(arenaX + 2, 1, 5));
        BlockPos outsideOre = helper.absolutePos(new BlockPos(arenaX + 2, 25, 5));
        var litOre = Blocks.REDSTONE_ORE.defaultBlockState().setValue(RedStoneOreBlock.LIT, true);
        helper.getLevel().setBlock(insideOre, litOre, 3);
        helper.getLevel().setBlock(outsideOre, litOre, 3);
        for (int attempt = 0; attempt < 3
            && helper.getLevel().getBlockState(outsideOre).getValue(RedStoneOreBlock.LIT); attempt++) {
            helper.getLevel().tickChunk(helper.getLevel().getChunk(outsideOre.getX() >> 4,
                outsideOre.getZ() >> 4), 65536);
        }
        helper.assertTrue(helper.getLevel().getBlockState(insideOre).getValue(RedStoneOreBlock.LIT),
            "inside random tick advanced during decision pause");
        helper.assertFalse(helper.getLevel().getBlockState(outsideOre).getValue(RedStoneOreBlock.LIT),
            "outside random tick did not advance");
        int insideTicks = inside.tickCount;
        int outsideTicks = outside.tickCount;
        int vehicleTicks = outsideVehicle.tickCount;
        int passengerTicks = insidePassenger.tickCount;
        helper.assertTrue(service.isEntitySimulationPaused(inside), "inside entity not classified as paused");
        helper.assertFalse(service.isEntitySimulationPaused(outside), "outside entity incorrectly classified as paused");
        helper.assertTrue(service.isEntitySimulationPaused(insidePassenger), "passenger not classified as paused");
        helper.assertTrue(service.isBlockSimulationPaused(helper.getLevel(), insideBlock.getPos()),
            "inside block not classified as paused");
        helper.assertFalse(service.isBlockSimulationPaused(helper.getLevel(), outsideBlock.getPos()),
            "outside block incorrectly classified as paused");
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(MinecraftCombatRuntime.isBodyPaused(player), "formal player body was not paused");
            player.connection.tick();
            helper.assertTrue(player.getEffect(MobEffects.SPEED).getDuration() == effectDuration,
                "formal player body effect advanced during pause");
            helper.assertTrue(inside.tickCount == insideTicks, "inside entity advanced during decision pause");
            helper.assertTrue(outside.tickCount > outsideTicks, "outside entity did not advance");
            helper.assertTrue(outsideVehicle.tickCount > vehicleTicks, "outside vehicle did not advance");
            helper.assertTrue(insidePassenger.tickCount == passengerTicks, "inside passenger advanced with outside vehicle");
            helper.assertTrue(insideBlock.ticks == 0, "inside block entity advanced during decision pause");
            helper.assertTrue(outsideBlock.ticks > 0, "outside block entity did not advance");
            helper.assertFalse(helper.getLevel().getBlockState(insidePiston).getValue(PistonBaseBlock.EXTENDED),
                "inside block event executed during decision pause");
            helper.assertTrue(helper.getLevel().getBlockState(outsidePiston).getValue(PistonBaseBlock.EXTENDED),
                "outside block event did not execute");
            service.stop(state.id());
            for (int attempt = 0; attempt < 3
                && helper.getLevel().getBlockState(insideOre).getValue(RedStoneOreBlock.LIT); attempt++) {
                helper.getLevel().tickChunk(helper.getLevel().getChunk(insideOre.getX() >> 4,
                    insideOre.getZ() >> 4), 65536);
            }
        });
        helper.runAtTickTime(25, () -> {
            try {
            player.connection.tick();
            helper.assertTrue(player.getEffect(MobEffects.SPEED).getDuration() < effectDuration,
                "formal player body did not resume after stop");
            helper.assertTrue(inside.tickCount > insideTicks, "inside entity did not resume after stop");
            helper.assertTrue(insideBlock.ticks > 0, "inside block entity did not resume after stop");
            helper.assertTrue(helper.getLevel().getBlockState(insidePiston).getValue(PistonBaseBlock.EXTENDED),
                "inside block event was lost instead of rescheduled: state="
                    + helper.getLevel().getBlockState(insidePiston) + " front="
                    + helper.getLevel().getBlockState(insidePiston.west()) + " east="
                    + helper.getLevel().getBlockState(insidePiston.east()) + " signal="
                    + helper.getLevel().hasNeighborSignal(insidePiston));
            helper.assertFalse(helper.getLevel().getBlockState(insideOre).getValue(RedStoneOreBlock.LIT),
                "inside random tick did not resume after stop");
            helper.succeed();
            } finally {
                for (int chunkX = arenaMinChunkX; chunkX <= arenaMaxChunkX; chunkX++)
                    for (int chunkZ = arenaMinChunkZ; chunkZ <= arenaMaxChunkZ; chunkZ++)
                        helper.getLevel().setChunkForced(chunkX, chunkZ, false);
            }
        });
    }

    private static final class TickCounter implements TickingBlockEntity {
        private final BlockPos pos;
        private int ticks;

        private TickCounter(BlockPos pos) { this.pos = pos; }
        @Override public void tick() { ticks++; }
        @Override public boolean isRemoved() { return false; }
        @Override public BlockPos getPos() { return pos; }
        @Override public String getType() { return "dndturn:test_counter"; }
    }

    private static void encounterService(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos playerPos = helper.absolutePos(new BlockPos(1537, 301, 1));
        for (int x = 1536; x <= 1540; x++) for (int z = 0; z <= 4; z++)
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 300, z)),
                Blocks.STONE.defaultBlockState());
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        for (int chunkX = (playerPos.getX() - 16) >> 4; chunkX <= (playerPos.getX() + 16) >> 4; chunkX++) {
            for (int chunkZ = (playerPos.getZ() - 16) >> 4; chunkZ <= (playerPos.getZ() + 16) >> 4; chunkZ++) {
                helper.getLevel().getChunk(chunkX, chunkZ);
            }
        }
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1538, 301, 2));
        ServerCombatService service = ServerCombatService.forServer(helper.getLevel().getServer());
        CombatEngine.StateView view = startEncounter(helper, player);
        helper.assertTrue(view.members().size() == 2, "Service did not create two rule members");
        helper.assertTrue(view.members().containsKey(zombie.getUUID()), "Service omitted supported Mob");
        helper.assertTrue(view.region().anchors().stream().anyMatch(anchor -> anchor.entityId().equals(zombie.getUUID())),
            "Service did not use world region sampling");
        var commands = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("dndturn");
        helper.assertTrue(commands != null && commands.getChild("local") == null
            && commands.getChild("tactical") != null, "obsolete local command is exposed or tactical is missing");
        UUID operationId = UUID.randomUUID();
        var result = service.endCurrentTurn(view.id(), operationId, view.version());
        long advancedVersion = service.state(view.id()).version();
        helper.assertTrue(advancedVersion == view.version() + 2, "END_TURN did not advance exactly once");
        helper.assertTrue(service.endCurrentTurn(view.id(), operationId, view.version()) == result,
            "Repeated operation did not return its original result");
        helper.assertTrue(service.state(view.id()).version() == advancedVersion,
            "Repeated operation advanced the session again");
        helper.assertTrue(service.stop(view.id()).size() == 2, "Service did not release both members");
        helper.assertTrue(service.stop(view.id()).isEmpty(), "Repeated stop was not idempotent");
        helper.assertTrue(service.encounterOf(player.getUUID()) == null, "Service retained player membership");
        helper.succeed();
    }

    private static void regionSampler(GameTestHelper helper) {
        ArmorStand first = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
        ArmorStand second = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(4, 1, 2));
        var firstCenter = first.getBoundingBox().getCenter();
        var secondCenter = second.getBoundingBox().getCenter();
        var discovery = new EncounterRegion.Discovery(firstCenter.x - 0.1, firstCenter.y - 0.1,
            firstCenter.z - 0.1, secondCenter.x + 0.1, secondCenter.y + 0.1, secondCenter.z + 0.1);
        var region = MinecraftRegionSampler.capture(helper.getLevel(), first, discovery, 1.0, 1, 4, 2);
        helper.assertTrue(region.anchors().size() == 2, "Sampler did not capture both entity centers");
        helper.assertTrue(region.anchors().stream().anyMatch(anchor -> anchor.entityId().equals(second.getUUID())),
            "Sampler omitted the second entity");
        helper.assertTrue(region.containsPoint((firstCenter.x + secondCenter.x) / 2,
            firstCenter.y, firstCenter.z), "Region did not fill the span between anchors");
        helper.assertTrue(region.minY() == firstCenter.y - 16.0 && region.maxY() == secondCenter.y + 16.0,
            "Sampler region has wrong vertical envelope");
        boolean rejected = false;
        try {
            MinecraftRegionSampler.capture(helper.getLevel(), first, discovery, 1.0, 1, 4, 1);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Sampler silently truncated an over-budget discovery");
        helper.succeed();
    }

    private static void cellProbe(GameTestHelper helper) {
        ArmorStand mover = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 2));
        var origin = mover.blockPosition();
        GridCell start = new GridCell(origin.getX(), origin.getY(), origin.getZ());
        GridCell goal = new GridCell(start.x() + 1, start.y(), start.z());
        var center = mover.getBoundingBox().getCenter();
        var discovery = new EncounterRegion.Discovery(center.x - 2, center.y - 1, center.z - 2,
            center.x + 2, center.y + 2, center.z + 2);
        var region = MinecraftRegionSampler.capture(helper.getLevel(), mover, discovery, 2, 1, 4, 64);
        MinecraftCellProbe probe = new MinecraftCellProbe(helper.getLevel(), mover, region);
        var proposal = TacticalPlanner.propose(start, goal, 2, 32, 1, probe);
        helper.assertTrue(proposal.cost() == 1, "Open floor has no one-step proposal");
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        helper.assertFalse(TacticalPlanner.revalidate(start, proposal, 1, probe),
            "Collision change did not invalidate proposal");
        helper.succeed();
    }

}
