package cc.sighs.dndturn.platform.server.world;

import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

/** Causal evidence providers; matching does not grant a simulation or damage permit. */
public final class CloudOrigins {
    public interface Provider {
        boolean tagged(Entity entity);
        UUID domain(Entity entity);
        boolean confirmed(Entity entity, EncounterRuntime encounter);
    }
    private static final Map<String, Provider> providers = new LinkedHashMap<>();
    private static boolean frozen;
    private CloudOrigins() {}
    public static synchronized void register(String id, Provider provider) {
        FactKey.requireId(id);
        Objects.requireNonNull(provider);
        if (frozen || providers.size() >= 128 || providers.putIfAbsent(id, provider) != null)
            throw new IllegalStateException("cloud origin registration closed or duplicate");
    }
    public static synchronized void freeze() { frozen = true; }
    private static Provider find(Entity entity) {
        Provider result = null;
        for (var provider : providers.values()) if (provider.tagged(entity)) {
            if (result != null) throw new IllegalStateException("conflicting cloud origin providers");
            result = provider;
        }
        return result;
    }
    public static boolean tagged(Entity entity) { return find(entity) != null; }
    public static UUID domain(Entity entity) { return Objects.requireNonNull(find(entity), "missing cloud origin").domain(entity); }
    public static boolean confirmed(Entity entity, EncounterRuntime encounter) {
        var provider = find(entity);
        return provider != null && provider.confirmed(entity, encounter);
    }
}
