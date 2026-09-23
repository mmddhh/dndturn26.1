package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.CombatNetwork;
import cc.sighs.dndturn.combat.EncounterProjectionOrder;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client prediction gate; the server remains authoritative. */
public final class ClientCombatState {
    private static long consentSequence = -1;
    private static boolean bodyPaused;
    private static boolean movementAllowed;
    private static UUID bodyGeneration;
    private static long bodySequence = -1;
    private static CombatNetwork.EncounterState encounter;
    private static CombatNetwork.EncounterState lastEncounter;
    private static CombatNetwork.ResultNotice latestResult;
    private static CombatNetwork.IntentStatus latestStatus;
    private static final java.util.ArrayDeque<CombatNetwork.ResultNotice> results = new java.util.ArrayDeque<>();
    private static int nextResultIndex;
    private static int expectedResultCount;
    private static boolean resyncPending;
    private static ClientPacketListener connection;
    private static UUID playerId;
    private static ResourceKey<Level> dimension;
    private static Object playerInstance;
    private static Level levelInstance;

    private record PacketOrigin(Connection connection, Level level, Object player) {}

    private ClientCombatState() {}

    private static PacketOrigin captureOrigin(IPayloadContext context) {
        Level level = Minecraft.getInstance().level;
        return new PacketOrigin(context.connection(), level, Minecraft.getInstance().player);
    }

    private static boolean acceptsOrigin(PacketOrigin origin) {
        ClientPacketListener current = Minecraft.getInstance().getConnection();
        return current != null && current.getConnection() == origin.connection()
            && origin.level() != null && origin.level() == levelInstance
            && origin.player() != null && origin.player() == playerInstance;
    }

    private static EncounterProjectionOrder.Stamp stamp(CombatNetwork.EncounterState state) {
        return new EncounterProjectionOrder.Stamp(state.generation(), state.encounterId(),
            state.sessionSequence(), state.version(), state.active(), state.projectionRevision());
    }

    public static void receiveConsent(CombatNetwork.ConsentState state, IPayloadContext context) {
        PacketOrigin origin = captureOrigin(context);
        context.enqueueWork(() -> {
            refreshSession();
            if (!acceptsOrigin(origin) || state.sequence() <= consentSequence
                || bodyGeneration != null && !bodyGeneration.equals(state.generation())
                || encounter != null && !encounter.generation().equals(state.generation())) return;
            consentSequence = state.sequence();
            ConsentOverlay.accept(state);
            if (!state.active() && Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(state.reason()));
        });
    }

    public static void receive(CombatNetwork.BodyState state, IPayloadContext context) {
        PacketOrigin origin = captureOrigin(context);
        context.enqueueWork(() -> {
            refreshSession();
            if (!acceptsOrigin(origin)) return;
            if (bodyGeneration != null && bodyGeneration.equals(state.generation())
                && state.sequence() <= bodySequence) return;
            if (encounter != null && !encounter.generation().equals(state.generation())) return;
            bodyGeneration = state.generation();
            bodySequence = state.sequence();
            bodyPaused = state.bodyPaused();
            movementAllowed = state.movementAllowed();
        });
    }

    public static void receiveEncounter(CombatNetwork.EncounterState state, IPayloadContext context) {
        PacketOrigin origin = captureOrigin(context);
        context.enqueueWork(() -> {
            refreshSession();
            if (!acceptsOrigin(origin)) return;
            var update = EncounterProjectionOrder.advance(
                lastEncounter == null ? null : stamp(lastEncounter),
                nextResultIndex, resyncPending, stamp(state));
            var decision = update.decision();
            if (decision == EncounterProjectionOrder.Decision.IGNORE) return;
            nextResultIndex = update.nextResultIndex();
            resyncPending = update.resyncPending();
            if (decision == EncounterProjectionOrder.Decision.ACCEPT_RESET_RESULTS) {
                latestResult = null;
                results.clear();
                latestStatus = null;
                CombatControls.resetForNewEncounter();
                ClientTacticalPlan.reset();
            }
            expectedResultCount = state.resultCount();
            lastEncounter = state;
            encounter = state.active() ? state : null;
            if (!state.active()) ClientTacticalPlan.reset();
            CombatControls.onEncounterState(state);
        });
    }

    public static CombatNetwork.EncounterState encounter() { return encounter; }
    public static CombatNetwork.ResultNotice latestResult() { return latestResult; }
    public static CombatNetwork.IntentStatus latestStatus() { return latestStatus; }
    public static java.util.List<CombatNetwork.ResultNotice> results() { return java.util.List.copyOf(results); }

    public static void receiveIntentStatus(CombatNetwork.IntentStatus status, IPayloadContext context) {
        PacketOrigin origin = captureOrigin(context);
        context.enqueueWork(() -> {
            refreshSession();
            if (!acceptsOrigin(origin)) return;
            if (status.replay()) {
                if (bodyGeneration != null && !bodyGeneration.equals(status.generation())
                    || encounter != null && !encounter.generation().equals(status.generation())
                    || encounter == null && lastEncounter != null
                        && !lastEncounter.generation().equals(status.generation())) return;
                latestStatus = status;
                CombatControls.onIntentStatus(status);
                return;
            }
            if (encounter != null && !encounter.generation().equals(status.generation())) return;
            if (status.encounterId() != null && encounter != null
                && !encounter.encounterId().equals(status.encounterId())) return;
            if (status.encounterId() != null && encounter == null && lastEncounter != null
                && (!lastEncounter.generation().equals(status.generation())
                    || !lastEncounter.encounterId().equals(status.encounterId()))) return;
            latestStatus = status;
            CombatControls.onIntentStatus(status);
        });
    }

    public static void receiveResult(CombatNetwork.ResultNotice result, IPayloadContext context) {
        PacketOrigin origin = captureOrigin(context);
        context.enqueueWork(() -> {
            refreshSession();
            if (!acceptsOrigin(origin)) return;
            if (encounter == null || !encounter.generation().equals(result.generation())
                || !encounter.encounterId().equals(result.encounterId())
                || result.index() < nextResultIndex) return;
            if (result.index() > nextResultIndex) {
                latestResult = new CombatNetwork.ResultNotice(result.generation(), result.encounterId(),
                    result.index(), result.operationId(), result.outcome(),
                    "result sequence gap; resync requested", 0, 0);
                if (!resyncPending) {
                    resyncPending = true;
                    ClientPacketDistributor.sendToServer(CombatNetwork.CombatIntent.resultSync(UUID.randomUUID(),
                        result.generation(), result.encounterId(), encounter.version(), nextResultIndex));
                }
                return;
            }
            latestResult = result;
            results.addLast(result);
            while (results.size() > 128) results.removeFirst();
            nextResultIndex++;
            resyncPending = false;
        });
    }

    public static boolean movementAllowed() { return movementAllowed; }

    public static boolean holdBodySubsystemsDuringMovement() {
        return bodyPaused && movementAllowed;
    }

    public static boolean gameplayPaused() {
        return bodyPaused || encounter != null && encounter.active();
    }

    public static boolean bodySimulationPaused() { return bodyPaused && !movementAllowed; }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (Boolean.getBoolean("dndturn.uiSmoke")) {
            TacticalUiSmokeTest.tick();
            return;
        }
        refreshSession();
        ClientControl.reconcile();
        ClientTacticalPlan.tick();
        if (encounter != null && nextResultIndex < expectedResultCount && !resyncPending) {
            resyncPending = true;
            ClientPacketDistributor.sendToServer(CombatNetwork.CombatIntent.resultSync(UUID.randomUUID(),
                encounter.generation(), encounter.encounterId(), encounter.version(), nextResultIndex));
        }
        TacticalOverlay.tick();
        ConsentOverlay.tick();
        DedicatedClientProbe.tick();
        ClientControlRegression.tick();
    }

    static void refreshSession() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener currentConnection = minecraft.getConnection();
        UUID currentPlayer = minecraft.player == null ? null : minecraft.player.getUUID();
        ResourceKey<Level> currentDimension = minecraft.level == null ? null : minecraft.level.dimension();
        if (currentConnection == null || currentPlayer == null || currentDimension == null
            || connection != currentConnection || playerInstance != minecraft.player || levelInstance != minecraft.level
            || !Objects.equals(playerId, currentPlayer)
            || !Objects.equals(dimension, currentDimension)) {
            bodyPaused = false;
            ClientControl.reset();
            ClientEntitySimulation.clear();
            movementAllowed = false;
            bodyGeneration = null;
            bodySequence = -1;
            encounter = null;
            lastEncounter = null;
            latestResult = null;
            results.clear();
            latestStatus = null;
            nextResultIndex = 0;
            expectedResultCount = 0;
            resyncPending = false;
            CombatControls.reset();
            TacticalOverlay.close();
            ConsentOverlay.close();
            consentSequence = -1;
        }
        connection = currentConnection;
        playerInstance = minecraft.player;
        levelInstance = minecraft.level;
        playerId = currentPlayer;
        dimension = currentDimension;
    }
}
