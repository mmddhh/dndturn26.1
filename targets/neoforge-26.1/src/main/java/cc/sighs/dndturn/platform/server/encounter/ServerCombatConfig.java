package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.encounter.time.RoundTime;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Per-world rule settings. These values are captured when a session starts. */
public record ServerCombatConfig(double discoveryHorizontal,
                                 double discoveryVertical, double regionRadius,
                                 int maxSampledChunks, int maxAnchors,
                                 int roundTicks, boolean tacticalKnockbackEnabled,
                                 long executionDeadlineTicks, long turnIdleSkipTicks,
                                 long historyRetentionTicks, double escapeDistance) {
    public ServerCombatConfig {
        new RoundTime(roundTicks);
        if (!Double.isFinite(discoveryHorizontal) || discoveryHorizontal <= 0
            || !Double.isFinite(discoveryVertical) || discoveryVertical <= 0
            || !Double.isFinite(regionRadius) || regionRadius <= 0
            || !Double.isFinite(escapeDistance) || escapeDistance <= 0
            || maxSampledChunks < 1 || maxSampledChunks > 4096
            || maxAnchors < 1 || maxAnchors > 4096
            || executionDeadlineTicks < 1 || turnIdleSkipTicks < 1 || historyRetentionTicks < 1) {
            throw new IllegalArgumentException("invalid combat server configuration");
        }
    }

    public RoundTime time() { return new RoundTime(roundTicks); }
    public static ServerCombatConfig load(MinecraftServer server) {
        return load(server.getWorldPath(LevelResource.ROOT).resolve("dndturn-server.properties"));
    }
    static ServerCombatConfig load(Path path) {
        Properties defaults = new Properties();
        defaults.setProperty("discoveryHorizontal", "16");
        defaults.setProperty("discoveryVertical", "8");
        defaults.setProperty("regionRadius", "35");
        defaults.setProperty("escapeDistance", "16");
        // Two independent player-centered fields merge only when their combined discovery is
        // sampled; 35-radius fields ~70+ apart exceed the old 16-chunk cap.
        defaults.setProperty("maxSampledChunks", "64");
        defaults.setProperty("maxAnchors", "64");
        defaults.setProperty("roundTicks", Integer.toString(RoundTime.DEFAULT_TICKS));
        defaults.setProperty("tacticalKnockbackEnabled", "false");
        // A hard liveness backstop, not a gameplay timer: no legitimate non-projectile operation
        // stays pending this long. Idle turns are skipped with the same horizon.
        defaults.setProperty("executionDeadlineTicks", "6000");
        defaults.setProperty("turnIdleSkipTicks", "6000");
        // Seven in-game days of actual server ticks; pause still counts and /time does not shorten.
        defaults.setProperty("historyRetentionTicks", "168000");
        Properties values = new Properties();
        try {
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) { values.load(in); }
            }
        } catch (IOException error) {
            throw new IllegalStateException("cannot load DNDTurn server configuration: " + path, error);
        }
        boolean write = defaults.keySet().stream().anyMatch(key -> !values.containsKey(key));
        defaults.forEach(values::putIfAbsent);
        ServerCombatConfig config = new ServerCombatConfig(
            Double.parseDouble(values.getProperty("discoveryHorizontal")),
            Double.parseDouble(values.getProperty("discoveryVertical")),
            Double.parseDouble(values.getProperty("regionRadius")),
            Integer.parseInt(values.getProperty("maxSampledChunks")),
            Integer.parseInt(values.getProperty("maxAnchors")),
            Integer.parseInt(values.getProperty("roundTicks")),
            parseBoolean(values.getProperty("tacticalKnockbackEnabled"), "tacticalKnockbackEnabled"),
            Long.parseLong(values.getProperty("executionDeadlineTicks")),
            Long.parseLong(values.getProperty("turnIdleSkipTicks")),
            Long.parseLong(values.getProperty("historyRetentionTicks")),
            Double.parseDouble(values.getProperty("escapeDistance")));
        // Validate before replacing the file; retain deprecated and unknown values verbatim as properties.
        if (write) {
            try {
                Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "dndturn-config-", ".tmp");
                try {
                    try (OutputStream out = Files.newOutputStream(temp)) {
                        values.store(out, "DNDTurn: roundTicks replaces movementTicks/environmentTicks. Deprecated values retained but ignored; active sessions retain captured time.");
                    }
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temp); }
            } catch (IOException error) {
                throw new IllegalStateException("cannot update DNDTurn server configuration: " + path, error);
            }
        }
        return config;
    }

    private static boolean parseBoolean(String value, String key) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
            throw new IllegalArgumentException(key + " must be true or false");
        return Boolean.parseBoolean(value);
    }
}
