package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;

/** Explicit native/state handoff ports, separate from pure actor definition discovery. */
public final class ActorLifecycleAdapters {
    public interface Adapter {
        void capture(EncounterRuntime service, LivingEntity actor);
        void release(EncounterRuntime service, LivingEntity actor);
    }
    private static final Map<Class<? extends LivingEntity>, Adapter> adapters = new HashMap<>();
    private static boolean frozen;
    private ActorLifecycleAdapters() {}
    public static void register(Class<? extends LivingEntity> type, Adapter adapter) {
        Objects.requireNonNull(type); Objects.requireNonNull(adapter);
        if (frozen || adapters.size() >= 128 || adapters.putIfAbsent(type, adapter) != null)
            throw new IllegalStateException("lifecycle registration closed or duplicate");
    }
    public static void freeze() { frozen = true; }
    private static Adapter find(LivingEntity actor) {
        Class<?> selected = null;
        for (var type : adapters.keySet()) if (type.isInstance(actor)
                && (selected == null || selected.isAssignableFrom(type))) selected = type;
        return adapters.get(selected);
    }
    public static void capture(EncounterRuntime service, LivingEntity actor) {
        var adapter = find(actor); if (adapter != null) adapter.capture(service, actor);
    }
    public static void release(EncounterRuntime service, LivingEntity actor) {
        var adapter = find(actor); if (adapter != null) adapter.release(service, actor);
    }
}
