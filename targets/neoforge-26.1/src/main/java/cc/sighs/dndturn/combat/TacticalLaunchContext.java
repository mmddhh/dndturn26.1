package cc.sighs.dndturn.combat;

import java.util.UUID;

/** Short synchronous scope consumed at projectile insertion, before it can tick. */
public final class TacticalLaunchContext implements AutoCloseable {
    private static final ThreadLocal<TacticalLaunchContext> CURRENT = new ThreadLocal<>();
    public record Evidence(UUID owner, UUID encounter, UUID operation, UUID target, DamageTrace trace) {}
    private final TacticalLaunchContext previous;
    private final Evidence evidence;
    public TacticalLaunchContext(UUID owner, UUID encounter, UUID operation, UUID target, DamageTrace trace) {
        evidence = new Evidence(owner, encounter, operation, target, trace); previous = CURRENT.get(); CURRENT.set(this);
    }
    public static Evidence current() { var context = CURRENT.get(); return context == null ? null : context.evidence; }
    @Override public void close() {
        if (CURRENT.get() != this) throw new IllegalStateException("unbalanced launch scope");
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
