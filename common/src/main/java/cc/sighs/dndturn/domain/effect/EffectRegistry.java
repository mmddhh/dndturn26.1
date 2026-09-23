package cc.sighs.dndturn.domain.effect;

import cc.sighs.dndturn.domain.ability.AbilityRegistry;
import java.util.*;

public final class EffectRegistry {
    private final Map<String, EffectDefinition> definitions = new LinkedHashMap<>();
    private boolean frozen;
    public void register(EffectDefinition definition) {
        Objects.requireNonNull(definition);
        if (frozen || definitions.size() >= 128 || definitions.containsKey(definition.id()))
            throw new IllegalStateException("effect registration closed or duplicate");
        definitions.put(definition.id(), definition);
    }
    public void freeze(AbilityRegistry abilities) {
        for (var definition : definitions.values()) for (var grant : definition.grants()) abilities.require(grant.ability(), grant.version());
        frozen = true;
    }
    public void require(EffectDefinition definition) {
        if (!definition.equals(definitions.get(definition.id()))) throw new IllegalStateException("effect definition unavailable or changed: " + definition.id());
    }
    public EffectDefinition require(String id, int version) {
        var definition = definitions.get(id);
        if (definition == null || definition.version() != version)
            throw new IllegalStateException("effect definition unavailable or changed: " + id + "@" + version);
        return definition;
    }
    public List<EffectDefinition> definitions() { return List.copyOf(definitions.values()); }
}
