package cc.sighs.dndturn.platform.bootstrap;

import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.server.runtime.EncounterRuntime;
import cc.sighs.dndturn.platform.server.network.CombatIntentHandler;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.UUID;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Player commands route through the same authority and receipt handling as client intents. */
public final class TacticalCommands {
    private TacticalCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dndturn")
            .then(Commands.literal("encounter")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("stop").then(Commands.argument("uuid", UuidArgument.uuid())
                    .executes(context -> {
                        UUID id = UuidArgument.getUuid(context, "uuid");
                        EncounterRuntime service = ServerRuntime.encounters(context.getSource().getServer());
                        try {
                            service.lifecycle().stop(id);
                            context.getSource().sendSuccess(() -> Component.literal("Encounter " + id + " stopped (or already absent)."), true);
                            return 1;
                        } catch (RuntimeException failure) {
                            context.getSource().sendFailure(Component.literal("Encounter " + id + " cleanup incomplete; retry pending: " + failure.getMessage()));
                            return 0;
                        }
                    }))))
            .then(Commands.literal("tactical").executes(context -> intent(context.getSource().getPlayerOrException(), EncounterProtocol.IntentKind.START))
                .then(Commands.literal("start").executes(context -> intent(context.getSource().getPlayerOrException(), EncounterProtocol.IntentKind.START)))
                .then(Commands.literal("end").executes(context -> intent(context.getSource().getPlayerOrException(), EncounterProtocol.IntentKind.END_TURN)))
                .then(Commands.literal("exit").executes(context -> intent(context.getSource().getPlayerOrException(), EncounterProtocol.IntentKind.EXIT)))));
    }
    private static int intent(ServerPlayer player, EncounterProtocol.IntentKind kind) {
        EncounterRuntime service = ServerRuntime.encounters(player.level().getServer());
        UUID encounter = service.encounterOf(player.getUUID());
        long version = encounter == null ? 0 : service.state(encounter).version();
        CombatIntentHandler.handleIntent(player, new EncounterProtocol.CombatIntent(UUID.randomUUID(), service.generation(),
            encounter, version, kind, null));
        return 1;
    }
}
