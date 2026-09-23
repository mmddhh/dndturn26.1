package cc.sighs.dndturn.platform.server.damage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Independent, read-only attacker and receiver support contracts for the audited melee bridge.
 * Registration does not grant membership, a turn, resources, or an effect permit.
 * Matchers run on the server thread and must not mutate the world. Ambiguity fails closed.
 * Register during construction, before common setup; no hot replacement of running contracts.
 */
public final class MeleeAdapters {
    public record Attacker(String id, int version, Predicate<LivingEntity> matches,
                           java.util.function.ToDoubleFunction<LivingEntity> intrinsicDamage,
                           int priority, Set<String> overrides) {
        public Attacker {
            metadata(id, version); Objects.requireNonNull(matches); Objects.requireNonNull(intrinsicDamage);
            overrides = Set.copyOf(overrides);
            if (overrides.contains(id) || overrides.size() > 64) throw new IllegalArgumentException("invalid attacker overrides");
        }
        public Attacker(String id, int version, Predicate<LivingEntity> matches,
                        java.util.function.ToDoubleFunction<LivingEntity> intrinsicDamage) {
            this(id, version, matches, intrinsicDamage, 0, Set.of());
        }
        public Attacker(String id, int version, Predicate<LivingEntity> matches) {
            this(id, version, matches, e -> e instanceof ServerPlayer ? 1.0 : e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        }
    }
    /** Compatibility registration alias; DamageReceivers owns all receiver state. */
    @Deprecated public interface Receiver extends DamageReceivers.Receiver {}
    private final Map<String, Attacker> attackers = new LinkedHashMap<>();
    private final DamageReceivers receivers;
    public MeleeAdapters() { this(new DamageReceivers()); }
    private MeleeAdapters(DamageReceivers receivers) { this.receivers = receivers; }
    private boolean frozen;
    private Attacker fallback;
    private static final MeleeAdapters BUILT_INS = new MeleeAdapters (DamageReceivers.server());

    public static MeleeAdapters server() { return BUILT_INS; }
    
    public synchronized void register(Attacker adapter) {
        Objects.requireNonNull(adapter);
        writable(attackers.size());
        if (attackers.putIfAbsent(adapter.id(), adapter) != null)
            throw new IllegalArgumentException("duplicate melee attacker " + adapter.id());
    }
    @Deprecated public void register(Receiver adapter) { receivers.register(adapter); }
    /** Explicit fallback registration; fresh registries remain unsupported until installed. */
    public synchronized void registerFallback(Attacker value) {
        Objects.requireNonNull(value);
        if (frozen || fallback != null) throw new IllegalStateException("fallback already set or registry frozen");
        fallback = value;
    }
    public synchronized void freeze() {
        for (var adapter : attackers.values()) for (var id : adapter.overrides()) {
            var base = attackers.get(id);
            if (base == null || base.priority() >= adapter.priority()) throw new IllegalArgumentException("invalid attacker override: " + id);
        }
        receivers.freeze();
        frozen = true;
    }
    public synchronized Attacker attacker(LivingEntity actor) {
        var matches = attackers.values().stream().filter(a -> a.matches().test(actor)).toList();
        var found = matches.stream().max(java.util.Comparator.comparingInt(Attacker::priority)).orElse(null);
        if (found != null) for (var other : matches) if (other != found
            && (other.priority() == found.priority() || !found.overrides().contains(other.id())))
            throw new IllegalStateException("ambiguous melee attackers: " + found.id() + ", " + other.id());
        return found == null && fallback != null && fallback.matches().test(actor) ? fallback : found;
    }
    @Deprecated public DamageReceivers.Receiver receiver(LivingEntity target) { return receivers.find(target); }
    private void writable(int size) {
        if (frozen) throw new IllegalStateException("melee registry is frozen");
        if (size >= 64) throw new IllegalStateException("melee registry capacity exceeded");
    }
    private static void metadata(String id, int version) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || version < 1)
            throw new IllegalArgumentException("melee adapter metadata");
    }
}
