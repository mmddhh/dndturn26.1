package cc.sighs.dndturn.domain.action;

import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import java.util.*;

/** Pure request from an accepted resolution. It is NOT a world-effect permit. */
public record ExecutionRequest(UUID operation, UUID actor, UUID instance, UUID encounter, long encounterVersion,
                               String executor, int definitionVersion, String ruleset,
                               ActorSnapshot.Revision actorRevision, GrantEvidence evidence,
                               ActionIntent intent, ActionCost cost) {
    public ExecutionRequest {
        Objects.requireNonNull(operation); Objects.requireNonNull(actor); Objects.requireNonNull(instance);
        Objects.requireNonNull(encounter); Objects.requireNonNull(actorRevision); Objects.requireNonNull(evidence);
        Objects.requireNonNull(intent); Objects.requireNonNull(cost); FactKey.requireId(executor);
        if (!evidence.equals(intent.source()) || evidence.actor() != null
                && (!actor.equals(evidence.actor()) || !instance.equals(evidence.instance())))
            throw new IllegalArgumentException("execution request source mismatch");
        if (encounterVersion < 0 || definitionVersion != intent.behaviorVersion()
                || !intent.ruleset().equals(ruleset) || !actorRevision.ruleset().equals(ruleset))
            throw new IllegalArgumentException("execution request revision mismatch");
    }
    public static ExecutionRequest from(UUID operation, ResolutionContext context, RuleResolver.Resolution resolution) {
        if (!resolution.accepted() || !resolution.cost().equals(context.invocation().binding().definition().cost()))
            throw new IllegalArgumentException("accepted resolution required");
        var snapshot = context.actor().actor(); var binding = context.invocation().binding();
        return new ExecutionRequest(operation, snapshot.actor(), snapshot.instance(), context.encounter().id(),
                context.encounter().version(), binding.definition().executor(), binding.version(),
                snapshot.revision().ruleset(), snapshot.revision(), binding.source(), context.invocation().intent(), resolution.cost());
    }
}
