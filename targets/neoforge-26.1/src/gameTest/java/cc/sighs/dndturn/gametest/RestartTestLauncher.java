package cc.sighs.dndturn.gametest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.startup.Entrypoint;

/** Same fixed-loader startup as GameTestServer, selecting a non-destructive test-world entrypoint. */
public final class RestartTestLauncher extends Entrypoint {
    public static void main(String[] args) throws Throwable {
        try (var startup = startup(args, true, Dist.DEDICATED_SERVER, false)) {
            var main = createMainMethodCallable(startup, "cc.sighs.dndturn.gametest.PersistentTestMain");
            main.invokeExact(startup.loader().getProgramArgs().getArguments());
            var server = findThread("Server thread");
            if (server == null) throw new IllegalStateException("persistent test server did not start");
            server.join();
        }
    }
}
