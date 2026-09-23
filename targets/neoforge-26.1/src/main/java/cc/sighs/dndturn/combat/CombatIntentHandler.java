package cc.sighs.dndturn.combat;

import static cc.sighs.dndturn.combat.CombatNetwork.*;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-side intent authorization; protocol values and outbound sends never create services. */
public final class CombatIntentHandler {
    private CombatIntentHandler() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("15");
        registrar.playToServer(TacticalNetwork.Query.TYPE, TacticalNetwork.Query.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    ServerCombatService.forServer(player.level().getServer()).tacticalActions().discover(player, payload));
        }));
        registrar.playToServer(TacticalNetwork.Request.TYPE, TacticalNetwork.Request.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                ServerCombatService.forServer(player.level().getServer()).tacticalActions().request(player, payload);
        }));
        registrar.playToServer(ConsentReply.TYPE, ConsentReply.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerCombatService service = ServerCombatService.forServer(player.level().getServer());
            try { service.respondToConsent(player, payload); }
            catch (RuntimeException failure) {
                service.resyncConsent(player, payload.requestId());
                player.sendOverlayMessage(Component.literal("DNDTurn: " + failure.getMessage()));
            }
        }));
        registrar.playToServer(CombatIntent.TYPE, CombatIntent.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) handleIntent(player, payload);
            }));

    }
    public static void handleIntent(ServerPlayer player, CombatIntent intent) {
        ServerCombatService service = ServerCombatService.forServer(player.level().getServer());
        try {
            if (intent.kind() == IntentKind.START) {
                if (intent.targetId() != null || intent.encounterId() != null || intent.expectedVersion() != 0
                    || intent.generation() != null && !service.matchesGeneration(intent.generation()))
                    throw new IllegalStateException("invalid START payload or epoch");
                var start = service.requestStart(player, intent.operationId());
                if (start.status() != cc.sighs.dndturn.combat.StartDisposition.STARTED) {
                    sendIntentStatus(player, new IntentStatus(service.generation(), null,
                        intent.operationId(), intent.kind(), start.status() == cc.sighs.dndturn.combat.StartDisposition.WAITING,
                        start.reason(), false, -1, 0, 0, start.status()));
                    return;
                }
                var state = start.state();
                player.sendOverlayMessage(Component.literal("DNDTurn combat started: " + state.phase()));
                sendIntentStatus(player, new IntentStatus(service.generation(), state.id(),
                    intent.operationId(), intent.kind(), true, "started"));
                return;
            }
            if (intent.generation() == null || !service.matchesGeneration(intent.generation())
                || intent.encounterId() == null)
                throw new IllegalStateException("combat session is no longer active");
            if (intent.kind() == IntentKind.EXIT) {
                if (intent.targetId() != null)
                    throw new IllegalStateException("EXIT does not accept a target");
                if (service.completedExitRetry(player, intent.encounterId(),
                    intent.operationId(), intent.expectedVersion())) {
                    sendIntentStatus(player, new IntentStatus(service.generation(), intent.encounterId(),
                        intent.operationId(), intent.kind(), true, "combat exited", true,
                        OperationRecord.Outcome.COMPLETED.code(), 0, 0));
                    player.sendOverlayMessage(Component.literal("DNDTurn: combat already exited"));
                    return;
                }
            }
            service.rejectExitIdReuse(intent.operationId());
            OperationRecord.Kind retryKind = switch (intent.kind()) {
                case MOVE_BEGIN, MOVE_END -> OperationRecord.Kind.MOVE;
                case ATTACK -> OperationRecord.Kind.ATTACK;
                case END_TURN -> OperationRecord.Kind.END_TURN;
                case DASH -> OperationRecord.Kind.DASH;
                case DODGE -> OperationRecord.Kind.DODGE;
                case DISENGAGE -> OperationRecord.Kind.DISENGAGE;
                default -> null;
            };
            if (retryKind != null) {
                var completed = service.completedRetry(player, intent.encounterId(), intent.operationId(),
                    retryKind, intent.expectedVersion(), intent.targetId(),
                    intent.kind() != IntentKind.MOVE_END);
                if (completed != null) {
                    service.resyncMember(player, intent.encounterId());
                    sendIntentStatus(player, new IntentStatus(service.generation(), intent.encounterId(),
                        intent.operationId(), intent.kind(), true,
                        completed.reason(), true, completed.outcome().code(),
                        completed.actualMovementTicks(), completed.actualDamage()));
                    player.sendOverlayMessage(Component.literal("DNDTurn retry: " + completed.reason()));
                    return;
                }
            }
            if (!intent.encounterId().equals(service.encounterOf(player.getUUID())))
                throw new IllegalStateException("combat session is no longer active");
            switch (intent.kind()) {
                case EXIT -> {
                    service.exitEncounter(player, intent.encounterId(), intent.operationId(),
                        intent.expectedVersion());
                }
                case MOVE_BEGIN, MOVE_END, ATTACK -> throw new IllegalStateException("legacy world action disabled; submit a tactical plan");
                case END_TURN -> service.endTurn(intent.encounterId(), player, intent.operationId(),
                    intent.expectedVersion());
                case DASH -> {
                    if (intent.targetId() != null) throw new IllegalStateException("DASH does not accept a target");
                    var result = service.dash(player, intent.operationId(), intent.expectedVersion());
                    player.sendOverlayMessage(Component.literal("DNDTurn: " + result.reason()));
                }
                case DODGE, DISENGAGE -> {
                    if (intent.targetId() != null) throw new IllegalStateException(intent.kind() + " does not accept a target");
                    OperationRecord.Kind kind = intent.kind() == IntentKind.DODGE
                        ? OperationRecord.Kind.DODGE : OperationRecord.Kind.DISENGAGE;
                    var result = service.defensiveAction(player, intent.operationId(),
                        intent.expectedVersion(), kind);
                    player.sendOverlayMessage(Component.literal("DNDTurn: " + result.reason()));
                }
                case RESULT_SYNC -> {
                    if (intent.targetId() != null)
                        throw new IllegalStateException("result resync cannot specify a target");
                    service.resendResults(player, intent.encounterId(),
                        intent.fromIndex());
                }
                default -> throw new IllegalStateException("unsupported combat intent");
            }
            sendIntentStatus(player, new IntentStatus(service.generation(), intent.encounterId(),
                intent.operationId(), intent.kind(), true, "accepted"));
        } catch (RuntimeException error) {
            service.resyncMember(player, intent.encounterId());
            sendIntentStatus(player, new IntentStatus(service.generation(), intent.encounterId(),
                intent.operationId(), intent.kind(), false,
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            player.sendOverlayMessage(Component.literal("DNDTurn: " + error.getMessage()));
        }
    }

}
