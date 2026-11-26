package cc.sighs.dndturn.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Embedded fixture, not a real client. Unlike the vanilla helper, gameMode is not hardcoded creative. */
public final class TestPlayers {
    private TestPlayers() {}
    public static ServerPlayer creative(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        NetworkRegistry.configureMockConnection(player.connection.getConnection());
        return player;
    }

    public static ServerPlayer survival(GameTestHelper helper) {
        return survival(helper, UUID.randomUUID());
    }
    public static ServerPlayer survival(GameTestHelper helper, UUID actor) {
        var level = helper.getLevel();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(actor, "tactical-test"), false);
        // NeoForge recognizes GameTest players by their subclass during configuration sync.
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {};
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        return player;
    }
}
