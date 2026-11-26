package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.CharacterClass;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.character.CharacterState;
import cc.sighs.dndturn.platform.network.CharacterProtocol;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Subclass metadata copied from classes; only stores the choice until features are migrated. */
public final class CharacterSubclass {
    private CharacterSubclass() {}

    public static void open(ServerPlayer player) {
        CharacterState character = character(player);
        if (!available(ServerRuntime.content(player.level().getServer()).current().requireClass(character.progression().classId()), character)) throw new IllegalStateException("subclass is not selectable");
        var progression = character.progression();
        var entry = ServerRuntime.content(player.level().getServer()).current().requireClass(progression.classId());
        JsonArray options = new JsonArray();
        for (String id : entry.subclasses()) {
            JsonObject option = new JsonObject();
            option.addProperty("id", id);
            option.addProperty("name", "subclass.dndclasses." + id.substring(id.indexOf(':') + 1) + ".name");
            options.add(option);
        }
        JsonObject root = new JsonObject();
        root.add("options", options);
        PacketDistributor.sendToPlayer(player, new CharacterProtocol.SubclassOpen(root.toString()));
    }

    public static void apply(ServerPlayer player, String choice) {
        CharacterState character = character(player);
        var progression = character.progression();
        var entry = ServerRuntime.content(player.level().getServer()).current().requireClass(progression.classId());
        if (progression.level() < entry.subclassLevel()) throw new IllegalStateException("subclass level not reached");
        if (progression.subclassId() != null) throw new IllegalStateException("subclass already selected");
        String id = choice;
        if (!entry.subclasses().contains(id)) throw new IllegalArgumentException("invalid subclass");
        var next = character.withProgression(progression.withSubclass(id));
        var service = ServerRuntime.encounters(player.level().getServer());
        var current = service.actorStates().state(player.getUUID());
        service.actorStates().command(new LiveActorContext(player),
            new ActorStates.Command(UUID.randomUUID(), player.getUUID(), current.revision(),
                List.of(new ActorStates.Character(next)), null));
        PacketDistributor.sendToPlayer(player, new CharacterProtocol.Reply(true, "Subclass selected"));
    }

    private static CharacterState character(ServerPlayer player) {
        var service = ServerRuntime.encounters(player.level().getServer());
        if (service.encounterOf(player.getUUID()) != null) throw new IllegalStateException("subclass cannot change during an encounter");
        CharacterState character = service.actorStates().state(player.getUUID()).persistent().character();
        if (!character.created()) throw new IllegalStateException("character has not been created");
        return character;
    }

    public static boolean available(CharacterClass entry, CharacterState character) {
        if (character == null || !character.created() || character.progression().subclassId() != null) return false;
        var progression = character.progression();
        return !entry.subclasses().isEmpty() && progression.level() >= entry.subclassLevel();
    }

}
