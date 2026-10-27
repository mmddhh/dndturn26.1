package cc.sighs.dndturn.platform.bootstrap;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.actor.ActorPersistentState;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.IdentifierArgument;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Administrative input only; ActorStateAuthority remains the sole writer. */
public final class ActorAbilityCommands {
    private static final String ORIGIN = "dndturn:administrative_grant";
    private ActorAbilityCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var root = Commands.literal("actor").requires(Commands.hasPermission(Commands.LEVEL_ADMINS));
        var abilities = Commands.literal("ability");
        abilities.then(Commands.literal("list").then(Commands.argument("target", EntityArgument.entity())
                .executes(c -> execute(c, "list"))));
        abilities.then(Commands.literal("grant").then(Commands.argument("target", EntityArgument.entity())
                .then(Commands.argument("ability", IdentifierArgument.id())
                    .suggests((c, b) -> { AbilityAdapterRegistry.definitions().all().stream()
                        .filter(d -> d.learning() != AbilityDefinition.Learning.UNAVAILABLE)
                        .map(AbilityDefinition::id).filter(id -> id.startsWith(b.getRemaining())).forEach(b::suggest);
                        return b.buildFuture(); })
                    .then(Commands.argument("semanticVersion", IntegerArgumentType.integer(1))
                        .executes(c -> execute(c, "grant"))))));
        for (String operation : List.of("revoke", "prepare", "unprepare"))
            abilities.then(Commands.literal(operation).then(Commands.argument("target", EntityArgument.entity())
                    .then(Commands.argument("grantId", UuidArgument.uuid()).executes(c -> execute(c, operation)))));
        return root.then(abilities);
    }
    private static int execute(CommandContext<CommandSourceStack> context, String operation)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var target = EntityArgument.getEntity(context, "target");
        var output = context.getSource();
        if (!(target instanceof ServerPlayer || target instanceof Mob) || !target.isAlive() || target.isRemoved()) {
            output.sendFailure(Component.literal("TARGET_UNAVAILABLE: expected one loaded living player or Mob")); return 0;
        }
        var authority = ServerRuntime.encounters(output.getServer()).actorStates();
        var actor = authority.context((LivingEntity) target);
        try {
            actor.verifyCurrent();
            var state = authority.state(actor.id());
            var grants = state.persistent().grants();
            if (operation.equals("list")) {
                output.sendSuccess(() -> Component.literal("Actor " + actor.id() + ": " + grants.size() + " learned grants"), false);
                grants.values().stream().sorted(Comparator.comparing(ActorPersistentState.Learned::id)).forEach(g -> {
                    var definition = AbilityAdapterRegistry.definitions().find(g.ability(), g.version());
                    String status = definition == null ? "DEFINITION_UNAVAILABLE" : g.requiresPreparation()
                            && !state.persistent().prepared().contains(g.id()) ? "UNPREPARED" : "BOUND";
                    output.sendSuccess(() -> Component.literal(g.id() + " " + g.ability() + "@" + g.version()
                            + " requiresPreparation=" + g.requiresPreparation() + " " + status), false);
                });
                return 1;
            }
            ActorStates.Change change;
            UUID grantId;
            if (operation.equals("grant")) {
                String id = IdentifierArgument.getId(context, "ability").toString();
                int version = IntegerArgumentType.getInteger(context, "semanticVersion");
                var definition = AbilityAdapterRegistry.definitions().find(id, version);
                if (definition == null) throw new IllegalArgumentException("DEFINITION_UNAVAILABLE");
                if (definition.learning() == AbilityDefinition.Learning.UNAVAILABLE) throw new IllegalArgumentException("NOT_LEARNABLE");
                var existing = grants.values().stream().filter(g -> g.origin().equals(ORIGIN)
                        && g.ability().equals(id) && g.version() == version).findFirst();
                if (existing.isPresent()) {
                    output.sendSuccess(() -> Component.literal("EXISTING_GRANT " + existing.get().id()), false); return 1;
                }
                grantId = UUID.randomUUID();
                change = new ActorStates.Learn(new ActorPersistentState.Learned(grantId, id, version, ORIGIN,
                        definition.learning() == AbilityDefinition.Learning.PREPARED, Math.addExact(state.revision(), 1)));
            } else {
                grantId = UuidArgument.getUuid(context, "grantId");
                var grant = grants.get(grantId);
                if (grant == null) {
                    if (operation.equals("revoke")) {
                        output.sendSuccess(() -> Component.literal("ALREADY_ABSENT " + grantId), false); return 1;
                    }
                    throw new IllegalArgumentException("GRANT_UNAVAILABLE");
                }
                if (operation.equals("revoke")) change = new ActorStates.Forget(grantId);
                else {
                    if (!grant.requiresPreparation()) throw new IllegalArgumentException("PREPARATION_NOT_REQUIRED");
                    var prepared = new HashSet<>(state.persistent().prepared());
                    boolean changed = operation.equals("prepare") ? prepared.add(grantId) : prepared.remove(grantId);
                    if (!changed) {
                        output.sendSuccess(() -> Component.literal("UNCHANGED " + grantId), false); return 1;
                    }
                    change = new ActorStates.Prepare(prepared);
                }
            }
            authority.command(actor, new ActorStates.Command(UUID.randomUUID(), actor.id(), state.revision(), List.of(change)));
            output.sendSuccess(() -> Component.literal(operation + " " + grantId), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            output.sendFailure(Component.literal("ACTOR_ABILITY_REJECTED: " + failure.getMessage())); return 0;
        }
    }
}
