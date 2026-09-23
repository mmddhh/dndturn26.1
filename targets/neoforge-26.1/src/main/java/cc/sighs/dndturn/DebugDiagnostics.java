package cc.sighs.dndturn;

import java.util.Arrays;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lazy process-local diagnostics; never sends chat, HUD or combat journal entries. */
public final class DebugDiagnostics {
    private static volatile boolean enabled;
    private static long window;
    private static int emitted, suppressed;
    private DebugDiagnostics() {}
    private static final class Output {
        static final Logger LOGGER = LoggerFactory.getLogger("dndturn.actions");
    }

    /** Consume only our exact program argument before vanilla parses arguments. */
    public static String[] launchArguments(String[] args) {
        if (Arrays.stream(args).noneMatch("-debug"::equals)) return args;
        enabled = true;
        log("DEBUG_ENABLED", () -> "action diagnostics enabled; journal remains empty");
        return Arrays.stream(args).filter(arg -> !arg.equals("-debug")).toArray(String[]::new);
    }

    public static boolean enabled() { return enabled; }

    /** Only call inside a lazy log supplier; omit item encodings and world objects. */
    public static String intent(cc.sighs.dndturn.combat.TacticalIntent intent) {
        if (intent == null) return "intent=cancel";
        return "ability=" + intent.behaviorId() + "@" + intent.behaviorVersion()
            + " source=" + intent.source().kind() + " hand=" + intent.hand()
            + " cost=" + intent.capability() + " target=" + intent.target()
            + " approach=" + intent.approach();
    }

    /** Breakpoint entry. Disabled calls never evaluate the supplier or initialize the logger. */
    public static void log(String stage, Supplier<String> details) {
        if (!enabled) return;
        try {
            synchronized (DebugDiagnostics.class) {
                long now = System.nanoTime();
                if (window == 0 || now - window >= 1_000_000_000L) {
                    if (suppressed > 0) Output.LOGGER.info("[DNDTurn/debug] RATE_LIMIT suppressed={}", suppressed);
                    window = now;
                    emitted = suppressed = 0;
                }
                if (emitted >= 300) { suppressed = Math.min(Integer.MAX_VALUE - 1, suppressed) + 1; return; }
                emitted++;
                String message = details.get();
                if (message.length() > 2048) message = message.substring(0, 2048) + " [truncated]";
                Output.LOGGER.info("[DNDTurn/debug] {} {}", stage, message.replace('\n', ' ').replace('\r', ' '));
            }
        } catch (RuntimeException ignored) {
            // Diagnostic failures do not change action outcomes.
        }
    }
}
