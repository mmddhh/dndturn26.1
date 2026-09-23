package cc.sighs.dndturn.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.server.loading.ServerModLoader;

/** Test-only startup retaining chunk and SavedData files between JVMs. Never resets a directory.
 * GameTestServer still creates test level settings; this is not dedicated-server level.dat recovery. */
public final class PersistentTestMain {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Util.startTimerHackThread();
        ServerModLoader.load(true);
        Path universe = Path.of("persistent-test-universe").toAbsolutePath().normalize();
        Files.createDirectories(universe);
        var storage = LevelStorageSource.createDefault(universe).createAccess("gametestworld");
        var packs = ServerPacksSource.createPackRepository(storage);
        MinecraftServer.spin(thread -> GameTestServer.create(thread, storage, packs,
            Optional.of("dndturn:restart_history"), false, 1));
    }
}
