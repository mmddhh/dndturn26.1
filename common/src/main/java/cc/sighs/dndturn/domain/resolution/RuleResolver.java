package cc.sighs.dndturn.domain.resolution;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.RuleFacts;

/** Shared UI/AI/submission rule decisions. No native object, mutation, or random sampling. */
public final class RuleResolver {
    private RuleResolver() {}
    public enum Status { ALLOWED, REQUIRES_APPROACH, REJECTED, UNSUPPORTED, DEFERRED }
    public record Resolution(Status status, ActionCost cost, String reason) {
        public boolean accepted() { return status == Status.ALLOWED || status == Status.REQUIRES_APPROACH; }
    }
    public static Resolution resolve(ResolutionContext context) {
        var definition = context.invocation().binding().definition();
        var member = context.actor().participant();
        var intent = context.invocation().intent();
        if (definition.activation() != AbilityDefinition.Activation.MANUAL
                && !(definition.activation() == AbilityDefinition.Activation.TRIGGERED && context.trigger() != null))
            return new Resolution(Status.UNSUPPORTED, definition.cost(), "activation driver unavailable");
        if (!definition.targets().contains(intent.target().kind()))
            return new Resolution(Status.REJECTED, definition.cost(), "unsupported target kind");
        boolean reaction = context.trigger() != null && (definition.cost().requiresReaction()
                || definition.cost().equals(ActionCost.FREE));
        if (member == null || !reaction && !member.id().equals(context.encounter().current()))
            return new Resolution(Status.REJECTED, definition.cost(), "not current participant");
        if (context.stage() == ResolutionContext.Stage.PROPOSAL && definition.cost().requiresAction() && !member.action())
            return new Resolution(Status.REJECTED, definition.cost(), "action unavailable");
        if (context.stage() == ResolutionContext.Stage.PROPOSAL && definition.cost().requiresReaction() && !member.reaction())
            return new Resolution(Status.REJECTED, definition.cost(), "reaction unavailable");
        try {
            var facts = context.world(); var reads = definition.reads();
            if (!facts.read(reads, RuleFacts.DIMENSION_MATCH))
                return new Resolution(Status.REJECTED, definition.cost(), "target dimension changed");
            if (!facts.read(reads, RuleFacts.TARGET_LOADED))
                return new Resolution(Status.DEFERRED, definition.cost(), "target not loaded");
            if (!facts.read(reads, RuleFacts.TARGET_DOMAIN))
                return new Resolution(Status.REJECTED, definition.cost(), "target outside encounter");
            if (definition.targetPolicy() != AbilityDefinition.TargetPolicy.NATIVE_INTERACTION) {
                if (!facts.read(reads, RuleFacts.TARGET_LIVING)
                        || !context.encounter().members().containsKey(intent.target().entity()))
                    return new Resolution(Status.REJECTED, definition.cost(), "target not a living encounter member");
                boolean targetPlayer = facts.read(reads, RuleFacts.TARGET_PLAYER);
                if (definition.targetPolicy() == AbilityDefinition.TargetPolicy.NON_PLAYER_LIVING_MEMBER && targetPlayer
                        || definition.targetPolicy() == AbilityDefinition.TargetPolicy.OPPOSITE_PLAYER_LIVING_MEMBER
                        && targetPlayer == facts.read(reads, RuleFacts.ACTOR_PLAYER))
                    return new Resolution(Status.REJECTED, definition.cost(), "attack relationship unsupported");
            }
            if (!facts.read(reads, RuleFacts.SOURCE_VALID))
                return new Resolution(Status.REJECTED, definition.cost(), "ability grant revoked or replaced");
            String nativeRejection = facts.read(reads, RuleFacts.NATIVE_REJECTION);
            if (!nativeRejection.isEmpty()) return new Resolution(Status.REJECTED, definition.cost(), nativeRejection);
            if (!facts.read(reads, RuleFacts.IN_REACH)) {
                if (reaction) return new Resolution(Status.REJECTED, definition.cost(), "reaction requires current reach");
                if (member.movementTicks() <= 0)
                    return new Resolution(Status.REJECTED, definition.cost(), "movement budget exhausted");
                return new Resolution(Status.REQUIRES_APPROACH, definition.cost(), "");
            }
            return new Resolution(Status.ALLOWED, definition.cost(), "");
        } catch (FactSlice.MissingFact missing) {
            return new Resolution(missing.reason() == FactSlice.Missing.UNSUPPORTED ? Status.UNSUPPORTED : Status.DEFERRED,
                    definition.cost(), missing.getMessage());
        }
    }
    public static int armorClass(FactSlice defense) {
        return nonnegativeInteger(defense.read(RuleFacts.DEFENSE, RuleFacts.RESOLVED_AC));
    }
    public static int reduction(FactSlice defense) {
        return nonnegativeInteger(defense.read(RuleFacts.DEFENSE, RuleFacts.RESOLVED_REDUCTION));
    }
    private static int nonnegativeInteger(double value) {
        if (!Double.isFinite(value) || value > Integer.MAX_VALUE) throw new IllegalArgumentException("rule statistic overflow");
        return Math.max(0, (int) Math.floor(value));
    }
}
