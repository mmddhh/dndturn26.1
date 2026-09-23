package cc.sighs.dndturn.combat;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Detached input evidence. Effective attributes already include installed equipment modifiers. */
public record NativeActorFacts(UUID actor, UUID instance, Map<String, Attribute> attributes,
                               double width, double height, String pose, String policy, int policyVersion) {
    public record Attribute(double base, double effective) {
        public Attribute {
            if (!Double.isFinite(base) || !Double.isFinite(effective)) throw new IllegalArgumentException("attribute value");
        }
    }
    public NativeActorFacts {
        Objects.requireNonNull(actor); Objects.requireNonNull(instance); Objects.requireNonNull(pose);
        Objects.requireNonNull(policy);
        attributes = Map.copyOf(attributes);
        if (attributes.size() > 32 || !Double.isFinite(width) || !Double.isFinite(height)
            || width <= 0 || height <= 0 || policyVersion < 1) throw new IllegalArgumentException("native facts");
    }
}
