package mdh.dndturn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mdh.dndturn.core.CombatManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DndTurnCommands {

    private DndTurnCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dndturn")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(context -> start(context.getSource(), 24))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 128))
                                .executes(context -> start(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("end")
                        .executes(context -> end(context.getSource())))
                .then(Commands.literal("next")
                        .executes(context -> next(context.getSource())))
        );
    }

    private static int start(CommandSourceStack source, int radius) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CombatManager.requestEnterCombat(player, radius);
        return 1;
    }

    private static int end(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CombatManager manager = CombatManager.getIfPresent(player.getServer());
        if (manager != null) {
            manager.forceEnd(player);
        }
        source.sendSuccess(() -> Component.literal("已结束战斗"), false);
        return 1;
    }

    private static int next(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CombatManager manager = CombatManager.getIfPresent(player.getServer());
        if (manager != null) {
            manager.forceNext(player);
        }
        return 1;
    }
}
