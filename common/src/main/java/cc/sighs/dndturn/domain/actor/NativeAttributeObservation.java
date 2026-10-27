package cc.sighs.dndturn.domain.actor;

import java.util.List;
import java.util.Objects;

/** Explanation of an observed native value, never a second attribute calculator or write owner. */
public record NativeAttributeObservation(double base, double effective, List<Modifier> modifiers,
                                         boolean complete) {
    public enum Operation { ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL }
    /** A unique current declaration match is evidence, not proof of the modifier's historical writer. */
    public record EquipmentMatch(String slot, String item) {
        public EquipmentMatch { bounded(slot); bounded(item); }
    }
    public record Modifier(String id, Operation operation, double amount, EquipmentMatch equipment, String nativeEffect) {
        public Modifier {
            bounded(id); Objects.requireNonNull(operation);
            if (nativeEffect != null) bounded(nativeEffect);
            if (equipment != null && nativeEffect != null) throw new IllegalArgumentException("ambiguous source");
            if (!Double.isFinite(amount)) throw new IllegalArgumentException("native modifier amount");
        }
    }
    public NativeAttributeObservation {
        if (!Double.isFinite(base) || !Double.isFinite(effective)) throw new IllegalArgumentException("native attribute value");
        modifiers = List.copyOf(modifiers);
        if (modifiers.size() > 64 || modifiers.stream().map(Modifier::id).distinct().count() != modifiers.size())
            throw new IllegalArgumentException("native modifier bounds or duplicate identity");
    }
    private static void bounded(String value) {
        Objects.requireNonNull(value);
        if (value.isBlank() || value.length() > 128) throw new IllegalArgumentException("native source identity");
    }
}
