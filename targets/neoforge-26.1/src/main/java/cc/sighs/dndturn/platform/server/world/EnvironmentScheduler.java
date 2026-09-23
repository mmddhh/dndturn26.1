package cc.sighs.dndturn.platform.server.world;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Owns the active world-step bindings and fair scheduling cursor. Rule budgets remain in EncounterAuthority. */
public final class EnvironmentScheduler {
    private record Step(UUID encounter, UUID operation) {}
    private final EncounterAuthority authority;
    private final Map<ServerLevel, Step> active = new IdentityHashMap<>();
    private final Map<ServerLevel, UUID> last = new IdentityHashMap<>();
    public EnvironmentScheduler(EncounterAuthority authority) { this.authority = authority; }
    public UUID step(Level level) { var value = active.get(level); return value == null ? null : value.operation(); }
    public UUID domain(Level level) { var value = active.get(level); return value == null ? null : value.encounter(); }
    public void begin(ServerLevel level, List<UUID> eligible, Predicate<UUID> loaded) {
        if (active.containsKey(level)) throw new IllegalStateException("world already has an active environment step");
        var candidates = new ArrayList<>(eligible);
        candidates.sort(UUID::compareTo);
        UUID previous = last.get(level);
        int offset = previous == null ? 0 : (candidates.indexOf(previous) + 1) % Math.max(1, candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            UUID encounter = candidates.get((offset + i) % candidates.size());
            if (!loaded.test(encounter)) continue;
            UUID operation = UUID.randomUUID();
            authority.authorizeEnvironmentStep(encounter, operation);
            active.put(level, new Step(encounter, operation));
            last.put(level, encounter);
            return;
        }
    }
    /** Remove the binding only after the rule commit, retaining evidence if commit throws. */
    public UUID complete(ServerLevel level) {
        var value = active.get(level);
        if (value == null) return null;
        if (!authority.encounterIds().contains(value.encounter())) { active.remove(level); return null; }
        authority.commitEnvironmentStep(value.encounter(), value.operation());
        active.remove(level);
        return value.encounter();
    }
    public UUID abort(ServerLevel level, long clock, Throwable failure) {
        var value = active.remove(level);
        if (value == null || !authority.encounterIds().contains(value.encounter())) return null;
        com.mojang.logging.LogUtils.getLogger().error("Environment step {} in encounter {} has an unknown partial world outcome; releasing its control instead of retrying",
                value.operation(), value.encounter(), failure);
        authority.failEnvironmentStep(value.encounter(), value.operation(), clock,
                "world tick aborted after partial execution: " + failure.getClass().getSimpleName());
        return value.encounter();
    }
    public void close() { active.clear(); last.clear(); }
}
