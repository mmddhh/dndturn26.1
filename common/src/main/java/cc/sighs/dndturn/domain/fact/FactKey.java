package cc.sighs.dndturn.domain.fact;

import java.util.Objects;
import java.util.UUID;

/** Canonical immutable scalar; native objects and mutable collections cannot cross this boundary. */
public record FactKey<T>(String id, Class<T> type) {
    public FactKey {
        Objects.requireNonNull(type);
        requireId(id);
        if (type != Double.class && type != Long.class && type != Boolean.class
                && type != String.class && type != UUID.class)
            throw new IllegalArgumentException("unsupported fact value type");
    }
    public static String requireId(String id) {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("namespaced identifier required");
        return id;
    }
}
