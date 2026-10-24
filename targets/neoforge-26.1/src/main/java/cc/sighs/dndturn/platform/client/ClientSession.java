package cc.sighs.dndturn.platform.client;
import cc.sighs.dndturn.application.projection.EncounterProjectionOrder;
import cc.sighs.dndturn.platform.client.action.ClientTacticalPlan;
import cc.sighs.dndturn.platform.client.input.ClientControl;
import cc.sighs.dndturn.platform.client.input.CombatControls;
import cc.sighs.dndturn.platform.client.simulation.ClientEntitySimulation;
import cc.sighs.dndturn.platform.client.ui.ConsentOverlay;
import cc.sighs.dndturn.platform.client.ui.TacticalOverlay;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import cc.sighs.dndturn.platform.client.state.ClientCombatState;
/** Sole connection/world/player session reset and packet dispatch coordinator. */
public final class ClientSession {
 private ClientSession() {}
    private static ClientPacketListener connection;
    private static UUID playerId;
    private static ResourceKey<Level> dimension;
    private static Object playerInstance;
    private static Level levelInstance;

    private record PacketOrigin(Connection connection, Level level, Object player) {}

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
    public static void onClientTick(ClientTickEvent.Post event) {
        refreshSession();
        ClientControl.reconcile();
        ClientTacticalPlan.tick();
        TacticalOverlay.tick();
        ConsentOverlay.tick();
    }
    public static void refreshSession() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener currentConnection = minecraft.getConnection();
        UUID currentPlayer = minecraft.player == null ? null : minecraft.player.getUUID();
        ResourceKey<Level> currentDimension = minecraft.level == null ? null : minecraft.level.dimension();
        if (currentConnection == null || currentPlayer == null || currentDimension == null
            || connection != currentConnection || playerInstance != minecraft.player || levelInstance != minecraft.level
            || !Objects.equals(playerId, currentPlayer)
            || !Objects.equals(dimension, currentDimension)) {
            ClientCombatState.reset();
            ClientControl.reset();
            ClientTacticalPlan.reset(); cc.sighs.dndturn.platform.client.input.PlanSteering.reset();
            ClientEntitySimulation.clear();
            CombatControls.reset();
            TacticalOverlay.close();
            ConsentOverlay.close();
        }
        connection = currentConnection;
        playerInstance = minecraft.player;
        levelInstance = minecraft.level;
        playerId = currentPlayer;
        dimension = currentDimension;
    }
    public static void enqueue(IPayloadContext context, Runnable apply) {
        PacketOrigin origin = captureOrigin(context);
        context.enqueueWork(() -> { refreshSession(); if (acceptsOrigin(origin)) apply.run(); });
    }
    public static void receiveConsent(EncounterProtocol.ConsentState state, IPayloadContext context) {
        enqueue(context, () -> {
            if (!ClientCombatState.applyConsentState(state)) return;
            ConsentOverlay.accept(state);
            if (!state.active() && Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(state.reason()));
        });
    }
    public static void receive(EncounterProtocol.BodyState state, IPayloadContext context) {
        enqueue(context, () -> ClientCombatState.applyBodyState(state));
    }
    public static void receiveEncounter(EncounterProtocol.EncounterState state, IPayloadContext context) {
        enqueue(context, () -> {
            var decision = ClientCombatState.applyEncounterState(state);
            if (decision == EncounterProjectionOrder.Decision.IGNORE) return;
            if (decision == EncounterProjectionOrder.Decision.ACCEPT_RESET_RESULTS) {
                CombatControls.resetForNewEncounter(); ClientTacticalPlan.reset(); cc.sighs.dndturn.platform.client.input.PlanSteering.reset();
            }
            if (!state.active()) {
                ClientTacticalPlan.reset();
                cc.sighs.dndturn.platform.client.input.PlanSteering.reset();
            }
            CombatControls.onEncounterState(state);
        });
    }
    public static void receiveIntentStatus(EncounterProtocol.IntentStatus status, IPayloadContext context) {
        enqueue(context, () -> { if (ClientCombatState.applyIntentStatus(status)) CombatControls.onIntentStatus(status); });
    }

}
