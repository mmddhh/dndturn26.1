package cc.sighs.dndturn.platform.server.network;

import cc.sighs.dndturn.domain.encounter.StartDisposition;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import cc.sighs.dndturn.platform.network.ActionProtocol;
import cc.sighs.dndturn.platform.network.CharacterProtocol;
import cc.sighs.dndturn.platform.network.InspectionProtocol;
import cc.sighs.dndturn.platform.server.runtime.EncounterRuntime;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import static cc.sighs.dndturn.platform.network.EncounterProtocol.*;
import static cc.sighs.dndturn.platform.server.network.EncounterSender.*;

/** Server-side intent authorization; protocol values and outbound sends never create services. */
public final class CombatIntentHandler {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("dndturn-character");
    private CombatIntentHandler() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("dndturn");
        registrar.playToServer(InspectionProtocol.Query.TYPE, InspectionProtocol.Query.CODEC, (payload, context) ->
            enqueue(context, player -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                ServerRuntime.encounters(player.level().getServer()).inspections().query(player, payload))));
        registrar.playToServer(CharacterProtocol.RequestUi.TYPE, CharacterProtocol.RequestUi.CODEC, (payload, context) ->
            enqueueInput(context, player -> {
                var service = ServerRuntime.encounters(player.level().getServer());
                if (service.actorStates().state(player.getUUID()).persistent().character().created()) {
                    cc.sighs.dndturn.platform.server.character.CharacterSheet.send(player);
                } else {
                    cc.sighs.dndturn.platform.server.character.CharacterOptions.send(player);
                }
            }));
        registrar.playToServer(CharacterProtocol.CreateJson.TYPE, CharacterProtocol.CreateJson.CODEC, (payload, context) ->
            enqueueInput(context, player -> {
                try {
                    var root = com.google.gson.JsonParser.parseString(payload.json()).getAsJsonObject();
                    String classId = contentId(root.get("class").getAsString());
                    String raceId = contentId(root.get("race").getAsString());
                    String subraceId = optionalContentId(root, "subrace");
                    String subclassId = optionalContentId(root, "subclass");
                    var bonuses = new java.util.EnumMap<cc.sighs.dndturn.domain.character.Ability, Integer>(
                        cc.sighs.dndturn.domain.character.Ability.class);
                    if (root.has("raceBonuses") && root.get("raceBonuses").isJsonObject()) {
                        for (var entry : root.getAsJsonObject("raceBonuses").entrySet()) {
                            bonuses.put(cc.sighs.dndturn.domain.character.Ability.byId(entry.getKey()),
                                entry.getValue().getAsInt());
                        }
                    }
                    var abilities = root.getAsJsonObject("abilities");
                    var state = cc.sighs.dndturn.platform.server.character.PlayerCharacterService.createFromScreen(player,
                        classId, raceId, subraceId, subclassId,
                        new AbilityScores(abilities.get("str").getAsInt(), abilities.get("dex").getAsInt(),
                            abilities.get("con").getAsInt(), abilities.get("int").getAsInt(),
                            abilities.get("wis").getAsInt(), abilities.get("cha").getAsInt()), bonuses);
                    cc.sighs.dndturn.platform.server.character.CharacterEquipment.grant(player, root);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new CharacterProtocol.Reply(true, "Character created: " + state.progression().classId()));
                    LOGGER.info("character created player={} class={} race={}", player.getUUID(),
                        state.progression().classId(), state.progression().raceId());
                } catch (RuntimeException failure) {
                    LOGGER.warn("character creation rejected player={}: {}", player.getUUID(), failure.getMessage());
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new CharacterProtocol.Reply(false, failure.getMessage()));
                }
            }));
        registrar.playToServer(CharacterProtocol.AsiChoice.TYPE, CharacterProtocol.AsiChoice.CODEC, (payload, context) ->
            enqueueInput(context, player -> {
                try {
                    cc.sighs.dndturn.platform.server.character.CharacterAsi.apply(player, payload.json());
                } catch (RuntimeException failure) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new CharacterProtocol.Reply(false, failure.getMessage()));
                }
            }));
        registrar.playToServer(CharacterProtocol.SheetAction.TYPE, CharacterProtocol.SheetAction.CODEC, (payload, context) ->
            enqueueInput(context, player -> {
                try {
                    if ("asi".equals(payload.action())) {
                        cc.sighs.dndturn.platform.server.character.CharacterAsi.open(player);
                    } else {
                        cc.sighs.dndturn.platform.server.character.CharacterSubclass.open(player);
                    }
                } catch (RuntimeException failure) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new CharacterProtocol.Reply(false, failure.getMessage()));
                }
            }));
        registrar.playToServer(CharacterProtocol.SubclassChoice.TYPE, CharacterProtocol.SubclassChoice.CODEC, (payload, context) ->
            enqueueInput(context, player -> {
                try {
                    cc.sighs.dndturn.platform.server.character.CharacterSubclass.apply(player, payload.id());
                } catch (RuntimeException failure) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new CharacterProtocol.Reply(false, failure.getMessage()));
                }
            }));
        registrar.playToServer(CharacterProtocol.Create.TYPE, CharacterProtocol.Create.CODEC, (payload, context) ->
            enqueueInput(context, player -> {
                try {
                    var state = cc.sighs.dndturn.platform.server.character.PlayerCharacterService.create(player,
                        payload.classId(), payload.raceId(), new AbilityScores(payload.strength(), payload.dexterity(),
                            payload.constitution(), payload.intelligence(), payload.wisdom(), payload.charisma()));
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new CharacterProtocol.Reply(true, "Character created: " + state.progression().classId()));
                } catch (RuntimeException failure) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new CharacterProtocol.Reply(false, failure.getMessage()));
                }
            }));
        registrar.playToServer(ActionProtocol.Query.TYPE, ActionProtocol.Query.CODEC, (payload, context) ->
            enqueue(context, player -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                ServerRuntime.encounters(player.level().getServer()).actions().selectAndDiscover(player, payload))));
        registrar.playToServer(ActionProtocol.Request.TYPE, ActionProtocol.Request.CODEC, (payload, context) ->
            enqueueInput(context, player -> ServerRuntime.encounters(player.level().getServer()).actions().request(player, payload)));
        registrar.playToServer(ConsentReply.TYPE, ConsentReply.STREAM_CODEC, (payload, context) -> enqueueInput(context, player -> {
            EncounterRuntime service = ServerRuntime.encounters(player.level().getServer());
            try { service.lifecycle().respondToConsent(player, payload); }
            catch (RuntimeException failure) {
                service.lifecycle().resyncConsent(player, payload.requestId());
                player.sendOverlayMessage(Component.literal("DNDTurn: " + failure.getMessage()));
            }
        }));
        registrar.playToServer(CombatIntent.TYPE, CombatIntent.STREAM_CODEC,
            (payload, context) -> enqueueInput(context, player -> handleIntent(player, payload)));
    }

    private static void enqueueInput(net.neoforged.neoforge.network.handling.IPayloadContext context,
                                     java.util.function.Consumer<ServerPlayer> command) {
        enqueue(context, player -> {
            player.resetLastActionTime();
            command.accept(player);
        });
    }

    private static void enqueue(net.neoforged.neoforge.network.handling.IPayloadContext context,
                                java.util.function.Consumer<ServerPlayer> command) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var connection = context.connection();
        if (player.connection.getConnection() != connection) return;
        context.enqueueWork(() -> {
            if (context.player() != player || player.isRemoved() || !connection.isConnected()
                || player.connection.getConnection() != connection
                || player.level().getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
            command.accept(player);
        });
    }

    private static String contentId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("character id is empty");
        if (value.contains(":")) return value.replace("dndclasses:", "dndturn:");
        return "dndturn:" + value;
    }

    private static String optionalContentId(com.google.gson.JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) return null;
        String value = root.get(key).getAsString().trim();
        return value.isEmpty() ? null : contentId(value);
    }

    public static void handleIntent(ServerPlayer player, CombatIntent intent) {        DebugDiagnostics.log("CONTROL_REQUEST", () -> "actor=" + player.getUUID() + " request=" + intent);
        EncounterRuntime service = ServerRuntime.encounters(player.level().getServer());
        try {
            if (intent.kind() == IntentKind.START) {
                if (intent.targetId() != null || intent.encounterId() != null || intent.expectedVersion() != 0
                    || intent.generation() != null && !service.matchesGeneration(intent.generation()))
                    throw new IllegalStateException("invalid START payload or epoch");
                var start = service.lifecycle().requestStart(player, intent.operationId());
                if (start.status() != StartDisposition.STARTED) {
                    sendIntentStatus(player, new IntentStatus(service.generation(), null,
                        intent.operationId(), intent.kind(), start.status() == StartDisposition.WAITING,
                        start.reason(), false, -1, 0, 0, start.status()));
                    return;
                }
                var state = start.state();
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
                if (service.lifecycle().completedExitRetry(player, intent.encounterId(),
                    intent.operationId(), intent.expectedVersion())) {
                    sendIntentStatus(player, new IntentStatus(service.generation(), intent.encounterId(),
                        intent.operationId(), intent.kind(), true, "combat exited", true,
                        OperationRecord.Outcome.COMPLETED.code(), 0, 0));
                    return;
                }
            }
            service.lifecycle().rejectExitIdReuse(intent.operationId());
            OperationRecord.Kind retryKind = switch (intent.kind()) {
                case END_TURN -> OperationRecord.Kind.END_TURN;
                case DASH -> OperationRecord.Kind.DASH;
                case DODGE -> OperationRecord.Kind.DODGE;
                case DISENGAGE -> OperationRecord.Kind.DISENGAGE;
                default -> null;
            };
            if (retryKind != null) {
                var completed = service.lifecycle().completedRetry(player, intent.encounterId(), intent.operationId(),
                    retryKind, intent.expectedVersion(), intent.targetId());
                if (completed != null) {
                    service.lifecycle().resyncMember(player, intent.encounterId());
                    sendIntentStatus(player, new IntentStatus(service.generation(), intent.encounterId(),
                        intent.operationId(), intent.kind(), true,
                        completed.reason(), true, completed.outcome().code(),
                        completed.actualMovementTicks(), completed.actualDamage()));
                    return;
                }
            }
            if (!intent.encounterId().equals(service.encounterOf(player.getUUID())))
                throw new IllegalStateException("combat session is no longer active");
            switch (intent.kind()) {
                case EXIT -> {
                    service.lifecycle().exitEncounter(player, intent.encounterId(), intent.operationId(),
                        intent.expectedVersion());
                }
                case END_TURN -> service.combat().endTurn(intent.encounterId(), player, intent.operationId(),
                    intent.expectedVersion());
                case DASH -> {
                    if (intent.targetId() != null) throw new IllegalStateException("DASH does not accept a target");
                    service.combat().dash(player, intent.operationId(), intent.expectedVersion());
                }
                case DODGE, DISENGAGE -> {
                    if (intent.targetId() != null) throw new IllegalStateException(intent.kind() + " does not accept a target");
                    OperationRecord.Kind kind = intent.kind() == IntentKind.DODGE
                        ? OperationRecord.Kind.DODGE : OperationRecord.Kind.DISENGAGE;
                    service.combat().defensiveAction(player, intent.operationId(),
                        intent.expectedVersion(), kind);
                }
                default -> throw new IllegalStateException("unsupported combat intent");
            }
            sendIntentStatus(player, new IntentStatus(service.generation(), intent.encounterId(),
                intent.operationId(), intent.kind(), true, "accepted"));
        } catch (RuntimeException error) {
            service.lifecycle().resyncMember(player, intent.encounterId());
            sendIntentStatus(player, IntentStatus.rejected(service.generation(), intent.encounterId(),
                intent.operationId(), intent.kind(), error));
            player.sendOverlayMessage(Component.literal("DNDTurn: " + error.getMessage()));
        }
    }

}
