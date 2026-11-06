package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.domain.character.CharacterState;
import cc.sighs.dndturn.platform.network.CharacterProtocol;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned ASI page data and the +2 / +1+1 choice application. */
public final class CharacterAsi {
    private CharacterAsi() {}

    public static void open(ServerPlayer player) {
        var service = ServerRuntime.encounters(player.level().getServer());
        var current = service.actorStates().state(player.getUUID());
        CharacterState character = current.persistent().character();
        if (!character.created()) throw new IllegalStateException("character has not been created");
        if (character.progression().pendingAsi() <= 0) throw new IllegalStateException("no pending ASI");
        JsonObject root = new JsonObject();
        JsonObject abilities = new JsonObject();
        for (Ability ability : Ability.values()) abilities.addProperty(key(ability), character.abilities().score(ability));
        root.add("abilities", abilities);
        root.addProperty("pending", character.progression().pendingAsi());
        PacketDistributor.sendToPlayer(player, new CharacterProtocol.AsiOpen(root.toString()));
    }

    public static void apply(ServerPlayer player, String json) {
        var service = ServerRuntime.encounters(player.level().getServer());
        var current = service.actorStates().state(player.getUUID());
        CharacterState character = current.persistent().character();
        if (!character.created()) throw new IllegalStateException("character has not been created");
        if (character.progression().pendingAsi() <= 0) throw new IllegalStateException("no pending ASI");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String mode = root.get("mode").getAsString();
        var array = root.getAsJsonArray("abilities");
        Set<Ability> picked = new HashSet<>();
        for (var element : array) {
            Ability ability = Ability.byId(element.getAsString());
            if (ability == null || !picked.add(ability)) throw new IllegalArgumentException("invalid ASI ability");
        }
        AbilityScores scores = character.abilities();
        if ("plus2".equals(mode)) {
            if (picked.size() != 1) throw new IllegalArgumentException("+2 requires one ability");
            Ability ability = picked.iterator().next();
            if (scores.score(ability) + 2 > 20) throw new IllegalArgumentException("ability cap");
            scores = scores.plus(ability, 2);
        } else if ("plus1plus1".equals(mode)) {
            if (picked.size() != 2) throw new IllegalArgumentException("+1/+1 requires two abilities");
            for (Ability ability : picked) {
                if (scores.score(ability) + 1 > 20) throw new IllegalArgumentException("ability cap");
                scores = scores.plus(ability, 1);
            }
        } else {
            throw new IllegalArgumentException("invalid ASI mode");
        }
        var progression = character.progression().spendAsi(1);
        var next = new CharacterState(progression, scores);
        var command = new ActorStates.Command(UUID.randomUUID(), player.getUUID(), current.revision(),
            List.of(new ActorStates.Character(next)), null);
        service.actorStates().command(new LiveActorContext(player), command);
        PlayerCharacterService.refreshMaxHealth(player, next);
        PacketDistributor.sendToPlayer(player, new CharacterProtocol.Reply(true, "Ability score improved"));
    }

    private static String key(Ability ability) {
        return ability.name().toLowerCase(java.util.Locale.ROOT);
    }
}
