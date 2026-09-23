package cc.sighs.dndturn.domain.fact;

/** Rule statistic identity, distinct from a resource balance. */
public record StatKey(String id) {
    public StatKey {
        FactKey.requireId(id);
        if (id.substring(id.indexOf(':') + 1).startsWith("resource/"))
            throw new IllegalArgumentException("resource balances are not modifiable statistics");
    }
    public FactKey<Double> fact() { return new FactKey<>(id, Double.class); }
}
