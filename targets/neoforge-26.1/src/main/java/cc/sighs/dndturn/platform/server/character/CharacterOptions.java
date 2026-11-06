package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.platform.network.CharacterProtocol;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends the bounded creation catalogue; the server still validates the submitted choice. */
public final class CharacterOptions {
    private static final Set<UUID> offered = new HashSet<>();

    private CharacterOptions() {}

    public static CharacterProtocol.Options current() {
        var content = CharacterContentService.current();
        var classes = content.classes().values().stream()
            .sorted(Comparator.comparing(value -> value.id()))
            .map(value -> new CharacterProtocol.ClassOption(value.id(), value.name(), value.hitDie())).toList();
        var races = content.races().values().stream()
            .sorted(Comparator.comparing(value -> value.id()))
            .map(value -> new CharacterProtocol.RaceOption(value.id(), value.name())).toList();
        return new CharacterProtocol.Options(classes, races);
    }

    public static void send(ServerPlayer player) {
        offered.add(player.getUUID());
        PacketDistributor.sendToPlayer(player, new CharacterProtocol.Open());
    }

    /** New players get the creation screen once; players who already created keep their existing sheet. */
    public static void offer(MinecraftServer server) {
        if (server.getClass().getName().startsWith("net.minecraft.gametest")) return;
        var service = cc.sighs.dndturn.platform.server.runtime.ServerRuntime.existingEncounter(server);
        if (service == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (service.actorStates().state(player.getUUID()).persistent().character().created()) continue;
            if (offered.add(player.getUUID())) send(player);
        }
    }

    public static void forget(UUID player) {
        offered.remove(player);
    }

    public static void clear() { offered.clear(); }

    /** Explicit command resend; it does not change the one-time auto-open receipt. */
    public static void resend(ServerPlayer player) {
        send(player);
    }

    /** Kept for callers that want an immediate login attempt; the server tick is authoritative. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        offer(player.level().getServer());
    }
}
