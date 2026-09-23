package cc.sighs.dndturn.domain.fact;

public record ResourceKey(String id) {
    public ResourceKey { FactKey.requireId(id); }
    public FactKey<Double> fact() {
        int separator = id.indexOf(':');
        return new FactKey<>(id.substring(0, separator + 1) + "resource/" + id.substring(separator + 1), Double.class);
    }
}
