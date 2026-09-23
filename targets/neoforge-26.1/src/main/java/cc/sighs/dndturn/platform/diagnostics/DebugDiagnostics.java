package cc.sighs.dndturn.platform.diagnostics;

import cc.sighs.dndturn.domain.action.ActionIntent;
import java.util.Arrays;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lazy process-local diagnostics; never sends chat, HUD or combat journal entries. */
public final class DebugDiagnostics {
    private static volatile boolean enabled;
    private DebugDiagnostics() {}
    private static final class Output {
        static final Logger LOGGER = LoggerFactory.getLogger("dndturn.actions");
    }

    /** Consume only our exact program argument before vanilla parses arguments. */
    public static String[] launchArguments(String[] args) {
        if (Arrays.stream(args).noneMatch("--debug"::equals)) return args;
        enabled = true;
        log("DEBUG_ENABLED", () -> "action diagnostics enabled; journal remains empty");
        return Arrays.stream(args).filter(arg -> !arg.equals("--debug")).toArray(String[]::new);
    }

    public static boolean enabled() { return enabled; }

    /** Bounded exception provenance for query failures; call only inside a lazy supplier. */
    public static String failure(Throwable failure) {
        var text = new StringBuilder();
        for (int cause = 0; failure != null && cause < 3; cause++, failure = failure.getCause()) {
            if (cause > 0) text.append(" causedBy=");
            text.append(failure.getClass().getName()).append(": ").append(failure.getMessage());
            var frames = failure.getStackTrace();
            for (int i = 0; i < Math.min(4, frames.length); i++) text.append(" at ").append(frames[i]);
        }
        return text.toString();
    }

    /** Only call inside a lazy log supplier; omit item encodings and world objects. */
    public static String intent(ActionIntent intent) {
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
            String message = details.get();
            if (message.length() > 2048) message = message.substring(0, 2048) + " [truncated]";
            Output.LOGGER.info("[DNDTurn/debug] {} {}", stage, message.replace('\n', ' ').replace('\r', ' '));
        } catch (RuntimeException ignored) {
            // Diagnostic failures do not change action outcomes.
        }
    }
}
