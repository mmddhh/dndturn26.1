package cc.sighs.dndturn.domain.fact;

import java.util.*;

/** Captured evidence. Absence never means false, zero, or permission. */
public final class FactSlice {
    public enum Missing { UNSUPPORTED, UNRESOLVED }
    private final Map<FactKey<?>, Object> values;
    private final Map<FactKey<?>, Missing> missing;
    public FactSlice(Map<FactKey<?>, ?> values, Map<FactKey<?>, Missing> missing) {
        if (values.size() + missing.size() > 128) throw new IllegalArgumentException("fact budget exceeded");
        var checked = new HashMap<FactKey<?>, Object>();
        values.forEach((key, value) -> {
            if (!key.type().isInstance(value) || value instanceof Double n && !Double.isFinite(n)
                    || value instanceof String s && s.length() > 512)
                throw new IllegalArgumentException("invalid fact: " + key.id());
            checked.put(key, value);
        });
        this.values = Map.copyOf(checked);
        this.missing = Map.copyOf(missing);
        var keys = new HashSet<>(values.keySet()); keys.addAll(missing.keySet());
        if (keys.size() != values.size() + missing.size()
                || keys.stream().map(FactKey::id).distinct().count() != keys.size())
            throw new IllegalArgumentException("conflicting fact evidence");
    }
    public <T> T read(ReadContract contract, FactKey<T> key) {
        if (!contract.keys().contains(key)) throw new IllegalArgumentException("undeclared fact: " + key.id());
        if (!values.containsKey(key)) throw new MissingFact(key, missing.getOrDefault(key, Missing.UNRESOLVED));
        return key.type().cast(values.get(key));
    }
    public Set<FactKey<?>> keys() {
        var keys = new HashSet<>(values.keySet()); keys.addAll(missing.keySet()); return Set.copyOf(keys);
    }
    public FactSlice restrict(ReadContract contract) {
        var selected = new HashMap<FactKey<?>, Object>();
        var absent = new HashMap<FactKey<?>, Missing>();
        for (var key : contract.keys()) {
            if (values.containsKey(key)) selected.put(key, values.get(key));
            else absent.put(key, missing.getOrDefault(key, Missing.UNRESOLVED));
        }
        return new FactSlice(selected, absent);
    }
    /** Compiler-only derived overrides. Every modified key must already be an observed numeric fact. */
    public FactSlice derive(Map<FactKey<?>, ?> derived) {
        var result = new HashMap<FactKey<?>, Object>(values);
        for (var entry : derived.entrySet()) {
            if (!values.containsKey(entry.getKey()) || entry.getKey().type() != Double.class)
                throw new IllegalArgumentException("modifier needs an owned base fact");
            result.put(entry.getKey(), entry.getValue());
        }
        return new FactSlice(result, missing);
    }
    /** Combine independently owned facts. Overlapping providers are an error, never last-writer-wins. */
    public FactSlice merge(FactSlice other) {
        var combined = new HashMap<FactKey<?>, Object>(values);
        var absent = new HashMap<FactKey<?>, Missing>(missing);
        var existing = new HashSet<>(values.keySet()); existing.addAll(missing.keySet());
        if (other.values.keySet().stream().anyMatch(existing::contains)
                || other.missing.keySet().stream().anyMatch(existing::contains))
            throw new IllegalArgumentException("multiple owners for canonical fact");
        combined.putAll(other.values); absent.putAll(other.missing);
        return new FactSlice(combined, absent);
    }
    public static final class MissingFact extends IllegalStateException {
        private final Missing reason;
        public MissingFact(FactKey<?> key, Missing reason) { super(key.id() + ": " + reason); this.reason = reason; }
        public Missing reason() { return reason; }
    }
    @Override public boolean equals(Object other) {
        return other instanceof FactSlice that && values.equals(that.values) && missing.equals(that.missing);
    }
    @Override public int hashCode() { return Objects.hash(values, missing); }
}
