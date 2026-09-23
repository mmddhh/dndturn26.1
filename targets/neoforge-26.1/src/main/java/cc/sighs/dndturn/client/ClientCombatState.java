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

/** Client prediction gate; the server remains authoritative. */
public final class ClientCombatState {
    private static long consentSequence = -1;
    private static boolean bodyPaused;
    private static boolean activeInputControlled;
    private static boolean movementAllowed;
    private static UUID bodyGeneration;
    private static long bodySequence = -1;
    private static CombatNetwork.EncounterState encounter;
    private static CombatNetwork.EncounterState lastEncounter;
    private static CombatNetwork.IntentStatus latestStatus;
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
            activeInputControlled = state.activeInputControlled();
            movementAllowed = state.movementAllowed();
        });
    }

    public static void receiveEncounter(CombatNetwork.EncounterState state, IPayloadContext context) {
        PacketOrigin origin = captureOrigin(context);
        context.enqueueWork(() -> {
            refreshSession();
            if (!acceptsOrigin(origin)) return;
            var decision = EncounterProjectionOrder.decide(
                lastEncounter == null ? null : stamp(lastEncounter), stamp(state));
            if (decision == EncounterProjectionOrder.Decision.IGNORE) return;
            if (decision == EncounterProjectionOrder.Decision.ACCEPT_RESET_RESULTS) {
                latestStatus = null;
                CombatControls.resetForNewEncounter();
                ClientTacticalPlan.reset();
            }
            lastEncounter = state;
            encounter = state.active() ? state : null;
            if (!state.active()) ClientTacticalPlan.reset();
            CombatControls.onEncounterState(state);
        });
    }

    public static CombatNetwork.EncounterState encounter() { return encounter; }
    public static CombatNetwork.IntentStatus latestStatus() { return latestStatus; }

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

    public static boolean movementAllowed() { return movementAllowed; }

    public static boolean holdBodySubsystemsDuringMovement() {
        return bodyPaused && movementAllowed;
    }

    public static boolean gameplayPaused() {
        return activeInputControlled || bodyPaused || encounter != null && encounter.active();
    }

    public static boolean bodySimulationPaused() { return bodyPaused && !movementAllowed; }

    public static void onClientTick(ClientTickEvent.Post event) {
        refreshSession();
        ClientControl.reconcile();
        ClientTacticalPlan.tick();
        TacticalOverlay.tick();
        ConsentOverlay.tick();
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
            activeInputControlled = false;
            ClientControl.reset();
            ClientEntitySimulation.clear();
            movementAllowed = false;
            bodyGeneration = null;
            bodySequence = -1;
            encounter = null;
            lastEncounter = null;
            latestStatus = null;
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
