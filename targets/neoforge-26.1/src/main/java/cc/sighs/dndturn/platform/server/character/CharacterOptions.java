package cc.sighs.dndturn.platform.server.character;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import cc.sighs.dndturn.domain.character.RulesCatalog;
import cc.sighs.dndturn.domain.character.CharacterClass;
import cc.sighs.dndturn.platform.network.CharacterProtocol;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends the bounded creation catalogue; the server still validates the submitted choice. */
public final class CharacterOptions {
    private static final Gson GSON = new Gson();

    private CharacterOptions() {}

    static CharacterProtocol.Open project(RulesCatalog catalog, UUID pageId, UUID replaces) {
        var page = new JsonObject();
        page.addProperty("page", pageId.toString());
        if (replaces != null) page.addProperty("replaces", replaces.toString());
        page.addProperty("revision", catalog.revision());
        page.addProperty("mode", "create");
        var classes = catalog.classes().values().stream().sorted(Comparator.comparing(value -> value.id()))
            .map(value -> new CharacterProtocol.ClassOption(value.id(), value.name(), value.hitDie())).toList();
        page.add("classes", GSON.toJsonTree(classes));
        page.add("races", GSON.toJsonTree(catalog.races().values().stream()
            .sorted(Comparator.comparing(value -> value.id())).toList()));
        return new CharacterProtocol.Open(page.toString());
    }

    public static CharacterProtocol.Detail detail(CharacterClass definition, UUID page) {
        var value = GSON.toJsonTree(definition).getAsJsonObject();
        value.addProperty("page", page.toString());
        return new CharacterProtocol.Detail(value.toString());
    }

    public static void send(ServerPlayer player) {
        var content = ServerRuntime.content(player.level().getServer());
        content.markOffered(player.getUUID());
        PacketDistributor.sendToPlayer(player, project(content.current(), UUID.randomUUID(), null));
    }

    public static void refresh(ServerPlayer player, UUID replaces) {
        PacketDistributor.sendToPlayer(player, project(ServerRuntime.content(player.level().getServer()).current(), UUID.randomUUID(), replaces));
    }

    /** New players get the creation screen once; players who already created keep their existing sheet. */
    public static void offer(MinecraftServer server) {
        if (server.getClass().getName().startsWith("net.minecraft.gametest")) return;
        var service = cc.sighs.dndturn.platform.server.runtime.ServerRuntime.existingEncounter(server);
        if (service == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (service.actorStates().state(player.getUUID()).persistent().character().created()) continue;
            if (ServerRuntime.content(server).markOffered(player.getUUID())) send(player);
        }
    }

    /** Kept for callers that want an immediate login attempt; the server tick is authoritative. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        offer(player.level().getServer());
    }
}
