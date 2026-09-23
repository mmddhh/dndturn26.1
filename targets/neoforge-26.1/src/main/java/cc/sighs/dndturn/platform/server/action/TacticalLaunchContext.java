package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.DamageTrace;
import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import java.util.UUID;

/** Short synchronous scope consumed at projectile insertion, before it can tick. */
public final class TacticalLaunchContext implements AutoCloseable {
    private static final ThreadLocal<TacticalLaunchContext> CURRENT = new ThreadLocal<>();
    public record Evidence(UUID owner, UUID encounter, UUID operation, UUID target, DamageTrace trace, UUID root, ActionIntent invocation) {}
    private final TacticalLaunchContext previous;
    private final Evidence evidence;
    private final java.util.List<AbilityCheckpoint.Spawn> spawned = new java.util.ArrayList<>();
    public java.util.List<AbilityCheckpoint.Spawn> spawned() { return java.util.List.copyOf(spawned); }
    public static void observe(net.minecraft.world.entity.Entity entity) {
        var current = CURRENT.get();
        if (current == null) return;
        if (current.spawned.size() >= 32) throw new IllegalStateException("launch observation bound");
        current.spawned.add(new AbilityCheckpoint.Spawn(entity.getUUID(), ((PresentationIdentity)entity).dndturn$presentationInstance(),
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.getX(), entity.getY(), entity.getZ()));
    }
    public TacticalLaunchContext(UUID owner, UUID encounter, UUID operation, UUID target, DamageTrace trace) {
        this(owner, encounter, operation, target, trace, null, null);
    }
    public TacticalLaunchContext(UUID owner, UUID encounter, UUID operation, UUID target, DamageTrace trace,
                                 UUID root, ActionIntent invocation) {
        evidence = new Evidence(owner, encounter, operation, target, trace, root, invocation); previous = CURRENT.get(); CURRENT.set(this);
    }
    public static Evidence current() { var context = CURRENT.get(); return context == null ? null : context.evidence; }
    @Override public void close() {
        if (CURRENT.get() != this) throw new IllegalStateException("unbalanced launch scope");
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
