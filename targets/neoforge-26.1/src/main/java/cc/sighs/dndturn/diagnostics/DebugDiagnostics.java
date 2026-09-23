package cc.sighs.dndturn.diagnostics;

import java.util.Arrays;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in observations only. Never authorizes actions or retains game objects. */
public final class DebugDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("DNDTurn/diagnostics");
    private static final boolean ENABLED = Arrays.asList(
        FMLLoader.getCurrent().getProgramArgs().getArguments()).contains("-debug");
    private DebugDiagnostics() {}
    public static void startup() {
        LOGGER.info("DNDTurn diagnostics {} (program argument -debug)", ENABLED ? "enabled" : "disabled");
    }
    public static void log(String message, Object... values) {
        if (ENABLED) LOGGER.info("[DNDTurn/debug] " + message, values);
    }
}
