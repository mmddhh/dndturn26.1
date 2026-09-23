package cc.sighs.dndturn.combat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Per-world rule settings. These values are captured when a session starts. */
public record ServerCombatConfig(double discoveryHorizontal,
                                 double discoveryVertical, double regionRadius,
                                 int maxSampledChunks, int maxAnchors,
                                 int movementTicks, int environmentTicks, boolean tacticalKnockbackEnabled) {
    public ServerCombatConfig {
        if (!Double.isFinite(discoveryHorizontal) || discoveryHorizontal <= 0
            || !Double.isFinite(discoveryVertical) || discoveryVertical <= 0
            || !Double.isFinite(regionRadius) || regionRadius <= 0
            || maxSampledChunks < 1 || maxSampledChunks > 4096
            || maxAnchors < 1 || maxAnchors > 4096
            || movementTicks < 1 || environmentTicks < 1) {
            throw new IllegalArgumentException("invalid combat server configuration");
        }
    }

    public static ServerCombatConfig load(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("dndturn-server.properties");
        Properties defaults = new Properties();
        defaults.setProperty("discoveryHorizontal", "16");
        defaults.setProperty("discoveryVertical", "8");
        defaults.setProperty("regionRadius", "8");
        defaults.setProperty("maxSampledChunks", "16");
        defaults.setProperty("maxAnchors", "64");
        defaults.setProperty("movementTicks", "28");
        defaults.setProperty("environmentTicks", "20");
        defaults.setProperty("tacticalKnockbackEnabled", "false");
        Properties values = new Properties(defaults);
        try {
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) { values.load(in); }
            } else {
                try (OutputStream out = Files.newOutputStream(path)) {
                    defaults.store(out, "DNDTurn per-world combat configuration");
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("cannot load DNDTurn server configuration: " + path, error);
        }
        return new ServerCombatConfig(
            Double.parseDouble(values.getProperty("discoveryHorizontal")),
            Double.parseDouble(values.getProperty("discoveryVertical")),
            Double.parseDouble(values.getProperty("regionRadius")),
            Integer.parseInt(values.getProperty("maxSampledChunks")),
            Integer.parseInt(values.getProperty("maxAnchors")),
            Integer.parseInt(values.getProperty("movementTicks")),
            Integer.parseInt(values.getProperty("environmentTicks")),
            parseBoolean(values.getProperty("tacticalKnockbackEnabled"), "tacticalKnockbackEnabled"));
    }

    private static boolean parseBoolean(String value, String key) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
            throw new IllegalArgumentException(key + " must be true or false");
        return Boolean.parseBoolean(value);
    }
}
