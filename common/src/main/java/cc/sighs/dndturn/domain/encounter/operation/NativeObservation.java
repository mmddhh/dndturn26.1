package cc.sighs.dndturn.domain.encounter.operation;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;

/** Typed extension evidence. Unknown/unsupported are facts about observation, never invented success. */
public record NativeObservation(String type, int version, UUID operation, UUID subject,
                                Certainty certainty, Map<String, Value> fields) {
    public enum Certainty { KNOWN, UNKNOWN, UNSUPPORTED }
    public enum Type { TEXT, BOOLEAN, INTEGER, REAL, IDENTITY }
    public record Value(Type type, String encoded) {
        public Value {
            Objects.requireNonNull(type); Objects.requireNonNull(encoded);
            if (encoded.length() > 1024) throw new IllegalArgumentException("observation value bound");
            switch (type) {
                case BOOLEAN -> { if (!encoded.equals("true") && !encoded.equals("false")) throw new IllegalArgumentException("boolean evidence"); }
                case INTEGER -> Long.parseLong(encoded);
                case REAL -> { if (!Double.isFinite(Double.parseDouble(encoded))) throw new IllegalArgumentException("nonfinite observation"); }
                case IDENTITY -> UUID.fromString(encoded);
                case TEXT -> { }
            }
        }
        public double number() {
            if (type != Type.REAL && type != Type.INTEGER) throw new IllegalStateException("not numerical evidence");
            return Double.parseDouble(encoded);
        }
        public UUID identity() {
            if (type != Type.IDENTITY) throw new IllegalStateException("not identity evidence");
            return UUID.fromString(encoded);
        }
    }
    public NativeObservation {
        FactKey.requireId(type); Objects.requireNonNull(operation); Objects.requireNonNull(subject); Objects.requireNonNull(certainty);
        fields = Map.copyOf(fields);
        if (version < 1 || fields.size() > 32 || fields.keySet().stream().anyMatch(key -> key.isBlank() || key.length() > 64))
            throw new IllegalArgumentException("observation envelope bounds");
    }
}
