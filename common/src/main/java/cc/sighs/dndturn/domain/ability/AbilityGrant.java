package cc.sighs.dndturn.domain.ability;

import java.util.Objects;
import java.util.UUID;

/** Provenance, lifetime ownership, and present evidence are deliberately different fields. */
public record AbilityGrant(UUID actor, Origin origin, Owner owner, GrantEvidence evidence) {
    public enum Origin { INTRINSIC, LEARNED, EQUIPMENT, EFFECT, ENCOUNTER, EXTERNAL }
    public enum Owner { DEFINITION, ACTOR_PERSISTENT, EQUIPMENT_PROVIDER, EFFECT_INSTANCE, ENCOUNTER, EXTERNAL_PROVIDER }
    public AbilityGrant {
        Objects.requireNonNull(actor); Objects.requireNonNull(origin);
        Objects.requireNonNull(owner); Objects.requireNonNull(evidence);
        if (evidence.actor() != null && !actor.equals(evidence.actor()))
            throw new IllegalArgumentException("grant actor mismatch");
    }
    public static AbilityGrant nativeGrant(UUID actor, GrantEvidence evidence) {
        return switch (evidence.kind()) {
            case EQUIPMENT -> new AbilityGrant(actor, Origin.EQUIPMENT, Owner.EQUIPMENT_PROVIDER, evidence);
            case BASIC, INTRINSIC -> new AbilityGrant(actor, Origin.INTRINSIC, Owner.DEFINITION, evidence);
            // Existing status registrations are external providers, not necessarily Minecraft effects.
            case STATUS -> new AbilityGrant(actor, Origin.EXTERNAL, Owner.EXTERNAL_PROVIDER, evidence);
            case PERSISTENT -> new AbilityGrant(actor, Origin.LEARNED, Owner.ACTOR_PERSISTENT, evidence);
            case EFFECT -> new AbilityGrant(actor, Origin.EFFECT, Owner.EFFECT_INSTANCE, evidence);
            case ENCOUNTER -> new AbilityGrant(actor, Origin.ENCOUNTER, Owner.ENCOUNTER, evidence);
        };
    }
}
