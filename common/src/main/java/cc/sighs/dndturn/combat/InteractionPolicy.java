package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.UUID;

/** Domain compatibility only; an execution lease is still required before any world effect. */
public final class InteractionPolicy {
    public enum Effect { DAMAGE, KNOCKBACK, TARGETING, PROJECTILE_COLLISION, EXPLOSION, STATUS_EFFECT, ENVIRONMENT_DAMAGE }
    public enum Disposition { ALLOW, REJECT, REQUIRES_CLASSIFICATION }
    public record Decision(Disposition disposition, String reason) {
        public Decision {
            Objects.requireNonNull(disposition);
            Objects.requireNonNull(reason);
        }
        /** Domain compatibility, not an execution permit. */
        public boolean allowed() { return disposition == Disposition.ALLOW; }
    }

    private InteractionPolicy() {}

    public static Decision decide(CombatEngine.Domain source, CombatEngine.Domain target,
                                  Effect effect, UUID causalOwner) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        Objects.requireNonNull(effect);
        if (source.kind() == CombatEngine.DomainKind.WORLD && target.kind() == CombatEngine.DomainKind.WORLD)
            return new Decision(Disposition.ALLOW, "world interaction");
        if (source.kind() == CombatEngine.DomainKind.ENCOUNTER && source.equals(target) && causalOwner != null) {
            if (effect == Effect.ENVIRONMENT_DAMAGE)
                return new Decision(Disposition.REQUIRES_CLASSIFICATION, "environment damage policy pending");
            return new Decision(Disposition.ALLOW, "same encounter with causal owner");
        }
        if (source.equals(target))
            return new Decision(Disposition.REJECT, "causal owner required");
        return new Decision(Disposition.REQUIRES_CLASSIFICATION, "cross-domain effect needs entry classification");
    }
}
