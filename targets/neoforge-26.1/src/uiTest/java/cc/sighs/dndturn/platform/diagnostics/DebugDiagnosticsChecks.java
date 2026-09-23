package cc.sighs.dndturn.platform.diagnostics;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Separate JVM verifies default no-op before enabling; no production probe. */
public final class DebugDiagnosticsChecks {
    public static void main(String[] args) {
        if (DebugDiagnostics.enabled()) throw new AssertionError("default must be disabled");
        var calls = new AtomicInteger();
        DebugDiagnostics.log("DISABLED", () -> { calls.incrementAndGet(); throw new AssertionError("evaluated while disabled"); });
        String[] untouched = {"-debug", "--world", "example"};
        if (DebugDiagnostics.launchArguments(untouched) != untouched || DebugDiagnostics.enabled())
            throw new AssertionError("only exact --debug should enable diagnostics");
        String[] filtered = DebugDiagnostics.launchArguments(new String[] {"--debug", "--world", "example", "--debug"});
        if (!DebugDiagnostics.enabled() || !Arrays.equals(filtered, new String[] {"--world", "example"}))
            throw new AssertionError("argument consumption changed unrelated arguments");
        DebugDiagnostics.log("ENABLED_CHECK", () -> { calls.incrementAndGet(); return "enabled forwarding"; });
        DebugDiagnostics.log("FORMAT_FAILURE", () -> { throw new IllegalStateException("diagnostics must not propagate"); });
        if (calls.get() != 1) throw new AssertionError("unexpected supplier evaluation");
        for (int i = 0; i < 350; i++) DebugDiagnostics.log("BURST_CHECK", () -> { calls.incrementAndGet(); return "query trace"; });
        if (calls.get() != 351) throw new AssertionError("diagnostic burst was suppressed");
        System.out.println("Debug diagnostics checks passed: default no-op, exact argument, lazy forwarding, formatter failure isolation");
    }
}
