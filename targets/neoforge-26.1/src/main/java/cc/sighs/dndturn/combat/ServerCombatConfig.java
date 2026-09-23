package cc.sighs.dndturn.combat;

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
                                 int roundTicks, boolean tacticalKnockbackEnabled) {
    public ServerCombatConfig {
        new RoundTime(roundTicks);
        if (!Double.isFinite(discoveryHorizontal) || discoveryHorizontal <= 0
            || !Double.isFinite(discoveryVertical) || discoveryVertical <= 0
            || !Double.isFinite(regionRadius) || regionRadius <= 0
            || maxSampledChunks < 1 || maxSampledChunks > 4096
            || maxAnchors < 1 || maxAnchors > 4096) {
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
        defaults.setProperty("regionRadius", "8");
        defaults.setProperty("maxSampledChunks", "16");
        defaults.setProperty("maxAnchors", "64");
        defaults.setProperty("roundTicks", Integer.toString(RoundTime.DEFAULT_TICKS));
        defaults.setProperty("tacticalKnockbackEnabled", "false");
        Properties values = new Properties();
        try {
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) { values.load(in); }
            }
        } catch (IOException error) {
            throw new IllegalStateException("cannot load DNDTurn server configuration: " + path, error);
        }
        boolean write = !values.containsKey("roundTicks");
        defaults.forEach(values::putIfAbsent);
        ServerCombatConfig config = new ServerCombatConfig(
            Double.parseDouble(values.getProperty("discoveryHorizontal")),
            Double.parseDouble(values.getProperty("discoveryVertical")),
            Double.parseDouble(values.getProperty("regionRadius")),
            Integer.parseInt(values.getProperty("maxSampledChunks")),
            Integer.parseInt(values.getProperty("maxAnchors")),
            Integer.parseInt(values.getProperty("roundTicks")),
            parseBoolean(values.getProperty("tacticalKnockbackEnabled"), "tacticalKnockbackEnabled"));
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
