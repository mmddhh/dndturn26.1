package cc.sighs.dndturn.domain.ai;

import java.util.*;

/** Definition-owned, version keyed, frozen registry; unknown semantics grant nothing. */
public final class AiAbilitySemantics {
    public record Key(String id, int version) {
        public Key { Objects.requireNonNull(id); if (version < 1) throw new IllegalArgumentException("version"); }
    }
    private final Map<Key, Set<AiAffordance>> values = new LinkedHashMap<>();
    private boolean frozen;
    public synchronized void register(Key key, Set<AiAffordance> semantics) {
        if (frozen || values.size() >= 1024) throw new IllegalStateException("AI semantics registration closed");
        if (values.putIfAbsent(key, Set.copyOf(semantics)) != null) throw new IllegalArgumentException("duplicate AI semantics");
    }
    public synchronized void freeze() { frozen = true; }
    public synchronized Set<AiAffordance> get(String id, int version) { return values.getOrDefault(new Key(id, version), Set.of()); }
}
