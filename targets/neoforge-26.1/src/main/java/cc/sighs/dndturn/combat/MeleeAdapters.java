package cc.sighs.dndturn.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

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
    public interface Receiver {
        String id();
        int version();
        boolean matches(LivingEntity entity);
        /** Audited perception fact used by the existing opening-advantage rule. */
        boolean unawareOf(LivingEntity receiver, LivingEntity attacker);
        default int priority() { return 0; }
        default Set<String> overrides() { return Set.of(); }
    }
    private final Map<String, Attacker> attackers = new LinkedHashMap<>();
    private final Map<String, Receiver> receivers = new LinkedHashMap<>();
    private boolean frozen;
    private static final MeleeAdapters BUILT_INS = builtIns();

    public static MeleeAdapters server() { return BUILT_INS; }
    private static MeleeAdapters builtIns() {
        var registry = new MeleeAdapters();
        registry.register(new Attacker("dndturn:player_melee", 1, e -> e instanceof ServerPlayer));
        // Preserve the previously audited attacker range; receiver coverage remains exact-type.
        registry.register(new Attacker("dndturn:zombie_melee", 1, e -> e instanceof Zombie));
        registry.register(new Attacker("dndturn:enderman_melee", 1,
            e -> e.getClass() == net.minecraft.world.entity.monster.EnderMan.class));
        registry.register(new Receiver() {
            public String id() { return "dndturn:player_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity entity) { return entity instanceof ServerPlayer; }
            public boolean unawareOf(LivingEntity receiver, LivingEntity attacker) { return false; }
        });
        registry.register(new Receiver() {
            public String id() { return "dndturn:enderman_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity entity) { return entity.getClass() == net.minecraft.world.entity.monster.EnderMan.class; }
            public boolean unawareOf(LivingEntity receiver, LivingEntity attacker) {
                return ((net.minecraft.world.entity.monster.EnderMan)receiver).getTarget() != attacker;
            }
        });
        registry.register(new Receiver() {
            public String id() { return "dndturn:zombie_receiver"; }
            public int version() { return 1; }
            public boolean matches(LivingEntity entity) { return entity.getType() == EntityType.ZOMBIE; }
            public boolean unawareOf(LivingEntity receiver, LivingEntity attacker) {
                return ((Zombie)receiver).getTarget() != attacker;
            }
        });
        return registry;
    }
    public synchronized void register(Attacker adapter) {
        Objects.requireNonNull(adapter);
        writable(attackers.size());
        if (attackers.putIfAbsent(adapter.id(), adapter) != null)
            throw new IllegalArgumentException("duplicate melee attacker " + adapter.id());
    }
    public synchronized void register(Receiver adapter) {
        Objects.requireNonNull(adapter);
        metadata(adapter.id(), adapter.version());
        writable(receivers.size());
        if (receivers.putIfAbsent(adapter.id(), adapter) != null)
            throw new IllegalArgumentException("duplicate melee receiver " + adapter.id());
    }
    public synchronized void freeze() {
        for (var adapter : attackers.values()) for (var id : adapter.overrides()) {
            var base = attackers.get(id);
            if (base == null || base.priority() >= adapter.priority()) throw new IllegalArgumentException("invalid attacker override: " + id);
        }
        for (var adapter : receivers.values()) for (var id : adapter.overrides()) {
            var base = receivers.get(id);
            if (base == null || base.priority() >= adapter.priority()) throw new IllegalArgumentException("invalid receiver override: " + id);
        }
        frozen = true;
    }
    public synchronized Attacker attacker(LivingEntity actor) {
        var matches = attackers.values().stream().filter(a -> a.matches().test(actor)).toList();
        var found = matches.stream().max(java.util.Comparator.comparingInt(Attacker::priority)).orElse(null);
        if (found != null) for (var other : matches) if (other != found
            && (other.priority() == found.priority() || !found.overrides().contains(other.id())))
            throw new IllegalStateException("ambiguous melee attackers: " + found.id() + ", " + other.id());
        return found;
    }
    public synchronized Receiver receiver(LivingEntity target) {
        var matches = receivers.values().stream().filter(a -> a.matches(target)).toList();
        var found = matches.stream().max(java.util.Comparator.comparingInt(Receiver::priority)).orElse(null);
        if (found != null) for (var other : matches) if (other != found
            && (other.priority() == found.priority() || !found.overrides().contains(other.id())))
            throw new IllegalStateException("ambiguous melee receivers: " + found.id() + ", " + other.id());
        return found;
    }
    private void writable(int size) {
        if (frozen) throw new IllegalStateException("melee registry is frozen");
        if (size >= 64) throw new IllegalStateException("melee registry capacity exceeded");
    }
    private static void metadata(String id, int version) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || version < 1)
            throw new IllegalArgumentException("melee adapter metadata");
    }
}
