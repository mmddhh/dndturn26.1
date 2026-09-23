package cc.sighs.dndturn.platform.server.world;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Registered observers bridge audited synchronous outcome scopes to platform events. */
public final class WorldOutcomeHooks {
    public interface Observer {
        boolean admitSpawn(Entity entity);
        void inserted(Entity entity);
        boolean authorizesDamage(LivingEntity target, DamageSource source);
    }
    private static final Map<String, Observer> observers = new LinkedHashMap<>();
    private static boolean frozen;
    private WorldOutcomeHooks() {}
    public static synchronized void register(String id, Observer observer) {
        FactKey.requireId(id);
        Objects.requireNonNull(observer);
        if (frozen || observers.size() >= 128 || observers.putIfAbsent(id, observer) != null)
            throw new IllegalStateException("outcome observer registration closed or duplicate");
    }
    public static synchronized void freeze() { frozen = true; }
    public static boolean admitSpawn(Entity entity) {
        for (var observer : observers.values()) if (!observer.admitSpawn(entity)) return false;
        return true;
    }
    public static void inserted(Entity entity) { observers.values().forEach(observer -> observer.inserted(entity)); }
    public static boolean authorizesDamage(LivingEntity target, DamageSource source) {
        for (var observer : observers.values()) if (observer.authorizesDamage(target, source)) return true;
        return false;
    }
}
