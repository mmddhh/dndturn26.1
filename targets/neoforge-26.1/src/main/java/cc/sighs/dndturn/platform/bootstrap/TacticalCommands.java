package cc.sighs.dndturn.platform.bootstrap;

import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.platform.server.character.PlayerCharacterService;
import cc.sighs.dndturn.platform.network.EncounterProtocol;
import cc.sighs.dndturn.platform.server.runtime.EncounterRuntime;
import cc.sighs.dndturn.platform.server.network.CombatIntentHandler;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Player commands route through the same authority and receipt handling as client intents. */
public final class TacticalCommands {
    private TacticalCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dndturn")
            .then(ActorAbilityCommands.tree())
            .then(Commands.literal("content")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("reload").executes(context -> {
                    try {
                        var snapshot = cc.sighs.dndturn.platform.server.character.CharacterContentService.reload();
                        context.getSource().sendSuccess(() -> Component.literal("Character content reloaded: "
                            + snapshot.classes().size() + " classes, " + snapshot.races().size()
                            + " races, revision " + snapshot.revision()), true);
                        return 1;
                    } catch (RuntimeException failure) {
                        context.getSource().sendFailure(Component.literal("Character content rejected: " + failure.getMessage()));
                        return 0;
                    }
                })))
            .then(Commands.literal("character")
                .then(Commands.literal("create")
                    .then(Commands.argument("class", StringArgumentType.word())
                        .then(Commands.argument("race", StringArgumentType.word())
                            .then(Commands.argument("str", IntegerArgumentType.integer(8, 15))
                                .then(Commands.argument("dex", IntegerArgumentType.integer(8, 15))
                                    .then(Commands.argument("con", IntegerArgumentType.integer(8, 15))
                                        .then(Commands.argument("int", IntegerArgumentType.integer(8, 15))
                                            .then(Commands.argument("wis", IntegerArgumentType.integer(8, 15))
                                                .then(Commands.argument("cha", IntegerArgumentType.integer(8, 15))
                                                    .executes(TacticalCommands::createCharacter))))))))))
                .then(Commands.literal("xp")
                    .then(Commands.literal("add")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                            .executes(TacticalCommands::addExperience))))
                .then(Commands.literal("asi").executes(context -> {
                    cc.sighs.dndturn.platform.server.character.CharacterAsi.open(
                        context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("subclass").executes(context -> {
                    cc.sighs.dndturn.platform.server.character.CharacterSubclass.open(
                        context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("sheet").executes(context -> {
                    cc.sighs.dndturn.platform.server.character.CharacterSheet.send(
                        context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("show").executes(TacticalCommands::showCharacter))
                .then(Commands.literal("ui").executes(context -> {
                    cc.sighs.dndturn.platform.server.character.CharacterOptions.send(
                        context.getSource().getPlayerOrException());
                    return 1;
                })))
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
    private static int createCharacter(CommandContext<CommandSourceStack> context) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var scores = new AbilityScores(
            IntegerArgumentType.getInteger(context, "str"),
            IntegerArgumentType.getInteger(context, "dex"),
            IntegerArgumentType.getInteger(context, "con"),
            IntegerArgumentType.getInteger(context, "int"),
            IntegerArgumentType.getInteger(context, "wis"),
            IntegerArgumentType.getInteger(context, "cha"));
        try {
            var state = PlayerCharacterService.create(player,
                StringArgumentType.getString(context, "class"),
                StringArgumentType.getString(context, "race"), scores);
            context.getSource().sendSuccess(() -> Component.literal("Character created: "
                + state.progression().classId() + " / " + state.progression().raceId()
                + " level " + state.progression().level()), true);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Character creation rejected: " + failure.getMessage()));
            return 0;
        }
    }

    private static int addExperience(CommandContext<CommandSourceStack> context) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(context, "amount");
        try {
            var state = PlayerCharacterService.addExperience(player, amount);
            context.getSource().sendSuccess(() -> Component.literal("Experience added: "
                + state.progression().experience() + " XP, level " + state.progression().level()
                + " (pending ASI " + state.progression().pendingAsi() + ")"), false);
            return 1;
        } catch (RuntimeException failure) {
            context.getSource().sendFailure(Component.literal("Experience rejected: " + failure.getMessage()));
            return 0;
        }
    }

    private static int showCharacter(CommandContext<CommandSourceStack> context) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var character = ServerRuntime.encounters(player.level().getServer())
            .actorStates().state(player.getUUID()).persistent().character();
        if (!character.created()) {
            context.getSource().sendFailure(Component.literal("No character; use /dndturn character create"));
            return 0;
        }
        var p = character.progression();
        var a = character.abilities();
        int ac = cc.sighs.dndturn.platform.server.character.PlayerArmorRules.armorClass(player, character);
        context.getSource().sendSuccess(() -> Component.literal(
            p.classId() + (p.subclassId() == null ? "" : " (" + p.subclassId() + ")") + " / " + p.raceId()
                + (p.subraceId() == null ? "" : " (" + p.subraceId() + ")")
                + " | level " + p.level() + " | XP " + p.experience()
                + " | prof +" + p.proficiencyBonus() + " | HP " + player.getHealth() + "/" + player.getMaxHealth()
                + " | AC " + ac
                + " | STR " + a.strength() + " (" + a.modifier(Ability.STR) + ") DEX " + a.dexterity()
                + " (" + a.modifier(Ability.DEX) + ") CON " + a.constitution() + " (" + a.modifier(Ability.CON)
                + ") INT " + a.intelligence() + " (" + a.modifier(Ability.INT) + ") WIS " + a.wisdom()
                + " (" + a.modifier(Ability.WIS) + ") CHA " + a.charisma() + " (" + a.modifier(Ability.CHA) + ")"), false);
        return 1;
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
