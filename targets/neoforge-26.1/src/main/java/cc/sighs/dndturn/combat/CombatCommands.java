package cc.sighs.dndturn.combat;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Small in-game entry point for the first local-time vertical slice. */
public final class CombatCommands {
    private CombatCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dndturn")
            .then(Commands.literal("local").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ServerCombatService formal = ServerCombatService.existing(player.level().getServer());
                    if (MinecraftCombatRuntime.isLocalMember(player.level().getServer(), player.getUUID())
                        || formal != null && formal.isMember(player.getUUID())) {
                        context.getSource().sendFailure(Component.literal("Already in an encounter"));
                        return 0;
                    }
                    UUID id = MinecraftCombatRuntime.beginLocal(player.level().getServer(), Set.of(player.getUUID()));
                    context.getSource().sendSuccess(() -> Component.literal("Local encounter " + id), false);
                    return 1;
                }))
                .then(Commands.literal("stop").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    MinecraftCombatRuntime.endLocal(player.level().getServer(), player.getUUID());
                    context.getSource().sendSuccess(() -> Component.literal("Local encounter ended"), false);
                    return 1;
                }))
                .then(Commands.literal("step")
                    .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200)).executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        if (!MinecraftCombatRuntime.isLocalMember(player.level().getServer(), player.getUUID())) {
                            context.getSource().sendFailure(Component.literal("No active local encounter"));
                            return 0;
                        }
                        int ticks = IntegerArgumentType.getInteger(context, "ticks");
                        MinecraftCombatRuntime.authorizeLocalBodyTicks(player.level().getServer(), player.getUUID(), ticks);
                        context.getSource().sendSuccess(() -> Component.literal("Body authorized for " + ticks + " ticks"), false);
                        return 1;
                    })))));
        TacticalCommands.register(event);
    }
}
