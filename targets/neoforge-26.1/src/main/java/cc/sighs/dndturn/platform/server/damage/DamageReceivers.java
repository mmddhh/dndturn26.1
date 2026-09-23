package cc.sighs.dndturn.platform.server.damage;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;

/** Sole receiver-support registry. Receiving damage does not grant an attack or autonomous AI. */
public final class DamageReceivers {
    public interface Receiver {
        String id();
        int version();
        boolean matches(LivingEntity entity);
        /** Audited perception fact used by the existing opening-advantage rule. */
        boolean unawareOf(LivingEntity receiver, LivingEntity attacker);
        default boolean supportsFacet(BodyTargets.Hit target) { return target.facet() == null; }
        default int priority() { return 0; }
        default Set<String> overrides() { return Set.of(); }
    }
    private final Map<String, Receiver> receivers = new LinkedHashMap<>();
    private boolean frozen;
    private Receiver fallback;
    private static final DamageReceivers BUILT_INS = new DamageReceivers();
    public static DamageReceivers server() { return BUILT_INS; }
    
    public synchronized void register(Receiver receiver) {
        Objects.requireNonNull(receiver);
        FactKey.requireId(receiver.id());
        if (receiver.version() < 1 || receiver.overrides().size() > 64 || receiver.overrides().contains(receiver.id()))
            throw new IllegalArgumentException("receiver metadata");
        if (frozen || receivers.size() >= 64 || receivers.containsKey(receiver.id()))
            throw new IllegalStateException("receiver registration closed, full or duplicate");
        receivers.put(receiver.id(), receiver);
    }
    /** Explicit fallback registration; fresh registries remain unsupported until installed. */
    public synchronized void registerFallback(Receiver value) {
        Objects.requireNonNull(value);
        if (frozen || fallback != null) throw new IllegalStateException("fallback already set or registry frozen");
        fallback = value;
    }
    public synchronized void freeze() {
        for (var receiver : receivers.values()) for (var id : receiver.overrides()) {
            var base = receivers.get(id);
            if (base == null || base.priority() >= receiver.priority()) throw new IllegalArgumentException("invalid receiver override: " + id);
        }
        frozen = true;
    }
    public synchronized Receiver find(LivingEntity target) {
        var matches = receivers.values().stream().filter(r -> r.matches(target)).toList();
        var found = matches.stream().max(Comparator.comparingInt(Receiver::priority)).orElse(null);
        if (found != null) for (var other : matches) if (other != found
            && (other.priority() == found.priority() || !found.overrides().contains(other.id())))
            throw new IllegalStateException("ambiguous damage receivers: " + found.id() + ", " + other.id());
        return found == null ? fallback : found;
    }
    public Receiver find(BodyTargets.Hit target) {
        var receiver = find(target.actor());
        return receiver != null && receiver.supportsFacet(target) ? receiver : null;
    }
}
