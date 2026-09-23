package cc.sighs.dndturn.domain.encounter.operation;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;

/** Startup-frozen schemas. A new observation requires no switch in a gameplay owner or persistence writer. */
public final class ObservationRegistry {
    public record Key(String id, int version) {
        public Key { FactKey.requireId(id); if (version < 1) throw new IllegalArgumentException("observation version"); }
    }
    public record Definition(Key key, Map<String, NativeObservation.Type> fields, Set<String> required) {
        public Definition {
            Objects.requireNonNull(key); fields = Map.copyOf(fields); required = Set.copyOf(required);
            if (fields.size() > 32 || !fields.keySet().containsAll(required)
                    || fields.keySet().stream().anyMatch(k -> k.isBlank() || k.length() > 64))
                throw new IllegalArgumentException("observation schema");
        }
    }
    private final Map<Key, Definition> definitions = new LinkedHashMap<>();
    private boolean frozen;
    public void register(Definition definition) {
        if (frozen || definitions.size() >= 128 || definitions.putIfAbsent(definition.key(), definition) != null)
            throw new IllegalStateException("observation registry closed or duplicate");
    }
    public void freeze() { frozen = true; }
    public void validate(NativeObservation observation) {
        var definition = definitions.get(new Key(observation.type(), observation.version()));
        if (definition == null) throw new IllegalArgumentException("OBSERVATION_TYPE_UNSUPPORTED");
        if (observation.fields().entrySet().stream().anyMatch(e -> definition.fields().get(e.getKey()) != e.getValue().type())
                || observation.certainty() == NativeObservation.Certainty.KNOWN && !observation.fields().keySet().containsAll(definition.required()))
            throw new IllegalArgumentException("observation schema mismatch");
    }
    public List<NativeObservation> validate(List<NativeObservation> observations, Set<Key> required, UUID operation) {
        observations = List.copyOf(observations);
        if (observations.size() > 128) throw new IllegalArgumentException("observation batch bound");
        var confirmed = new HashSet<Key>();
        for (var observation : observations) {
            validate(observation);
            if (!observation.operation().equals(operation)) throw new IllegalArgumentException("observation operation mismatch");
            if (observation.certainty() == NativeObservation.Certainty.KNOWN) confirmed.add(new Key(observation.type(), observation.version()));
        }
        if (!confirmed.containsAll(required)) throw new IllegalStateException("REQUIRED_OBSERVATION_UNKNOWN");
        return observations;
    }
}
