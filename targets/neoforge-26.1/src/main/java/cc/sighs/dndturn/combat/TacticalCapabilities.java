package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Complete server behaviors; no default attack fallback or predicate-only registration. */
public final class TacticalCapabilities {
    private static final Map<String, TacticalBehavior> ADAPTERS = new LinkedHashMap<>();
    static { VanillaBehaviors.register(); }
    private TacticalCapabilities() {}
    public static synchronized void register(TacticalBehavior adapter) {
        Objects.requireNonNull(adapter);
        if (ADAPTERS.size() >= 64) throw new IllegalStateException("behavior registry capacity exceeded");
        if (ADAPTERS.putIfAbsent(adapter.id(), adapter) != null) throw new IllegalArgumentException("duplicate behavior " + adapter.id());
    }
    public static synchronized TacticalBehavior resolve(TacticalIntent intent) {
        var adapter = ADAPTERS.get(intent.behaviorId());
        if (adapter == null) throw new IllegalStateException("behavior adapter missing: " + intent.behaviorId());
        if (adapter.version() != intent.behaviorVersion()) throw new IllegalStateException("behavior contract version changed");
        if (adapter.cost() != intent.capability()) throw new IllegalStateException("behavior cost classification conflict");
        return adapter;
    }
    public static String recoveryReason(TacticalIntent intent) {
        try { return resolve(intent).recoveryReason(); }
        catch (IllegalStateException unavailable) { return unavailable.getMessage() + "; not replayed"; }
    }
    public static synchronized List<TacticalBehavior> all() { return List.copyOf(ADAPTERS.values()); }
}
