package cc.sighs.dndturn.platform.server.damage;

import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;

/** Audited native drivers; only the bound execution port invokes a driver. */
public final class RangedAdapters {
    @FunctionalInterface public interface Driver { void shoot(LiveActorContext actor, LivingEntity target); }
    public record Adapter(String id, int version, Predicate<LiveActorContext> matches, Driver driver) {
        public Adapter { FactKey.requireId(id); Objects.requireNonNull(matches); Objects.requireNonNull(driver);
            if (version < 1) throw new IllegalArgumentException("ranged adapter version"); }
    }
    private static final Map<String, Adapter> entries = new LinkedHashMap<>();
    private static boolean frozen;
    private RangedAdapters() {}
    public static void register(Adapter adapter) {
        if (frozen || entries.size() >= 128 || entries.putIfAbsent(adapter.id(), adapter) != null)
            throw new IllegalStateException("ranged adapter registration closed or duplicate");
    }
    public static void freeze() { frozen = true; }
    public static Adapter find(LiveActorContext actor) {
        Adapter found = null;
        for (var entry : entries.values()) if (entry.matches().test(actor)) {
            if (found != null) throw new IllegalStateException("ambiguous ranged adapters");
            found = entry;
        }
        return found;
    }
}
