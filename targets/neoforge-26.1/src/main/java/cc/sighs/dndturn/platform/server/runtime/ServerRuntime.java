package cc.sighs.dndturn.platform.server.runtime;

import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

/** Server-instance composition and lifecycle owner; contains no encounter rule or execution ledger. */
public final class ServerRuntime {
    private static final Map<MinecraftServer, ServerRuntime> SERVERS = new IdentityHashMap<>();
    private static final Set<MinecraftServer> CLOSED = Collections.newSetFromMap(new WeakHashMap<>());
    private final ActorStateAuthority actors;
    private final EncounterRuntime encounters;
    private ServerRuntime(MinecraftServer server) {
        actors = new ActorStateAuthority(server);
        try { encounters = new EncounterRuntime(server, actors, UUID.randomUUID()); }
        catch (RuntimeException failure) {
            try { actors.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    public static EncounterRuntime encounters(MinecraftServer server) {
        requireThread(server);
        if (CLOSED.contains(server)) throw new IllegalStateException("server runtime is closed");
        return SERVERS.computeIfAbsent(server, ServerRuntime::new).encounters;
    }
    public static EncounterRuntime existingEncounter(MinecraftServer server) {
        var runtime = SERVERS.get(server);
        return runtime == null ? null : runtime.encounters;
    }
    public static void release(MinecraftServer server) {
        requireThread(server);
        var runtime = SERVERS.get(server);
        if (!CLOSED.add(server) || runtime == null) return;
        try { runtime.encounters.close(); }
        finally {
            try { runtime.actors.close(); }
            finally { SERVERS.remove(server); }
        }
    }
    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("server lifecycle requires server thread");
    }
}
