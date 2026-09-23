package cc.sighs.dndturn.combat;

import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Player commands route through the same authority and receipt handling as client intents. */
public final class TacticalCommands {
    private TacticalCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dndturn")
            .then(Commands.literal("tactical").executes(context -> intent(context.getSource().getPlayerOrException(), CombatNetwork.IntentKind.START))
                .then(Commands.literal("start").executes(context -> intent(context.getSource().getPlayerOrException(), CombatNetwork.IntentKind.START)))
                .then(Commands.literal("end").executes(context -> intent(context.getSource().getPlayerOrException(), CombatNetwork.IntentKind.END_TURN)))
                .then(Commands.literal("exit").executes(context -> intent(context.getSource().getPlayerOrException(), CombatNetwork.IntentKind.EXIT)))));
    }
    private static int intent(ServerPlayer player, CombatNetwork.IntentKind kind) {
        ServerCombatService service = ServerCombatService.forServer(player.level().getServer());
        UUID encounter = service.encounterOf(player.getUUID());
        long version = encounter == null ? 0 : service.state(encounter).version();
        CombatIntentHandler.handleIntent(player, new CombatNetwork.CombatIntent(UUID.randomUUID(), service.generation(),
            encounter, version, kind, null));
        return 1;
    }
}
