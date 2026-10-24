package cc.sighs.dndturn.application.action;
import cc.sighs.dndturn.domain.action.ExecutionRequest;
import cc.sighs.dndturn.domain.resolution.*;
import java.util.UUID;
/** Assembles the accepted pure request; does not issue a world permit. */
public final class ExecutionRequests {
 private ExecutionRequests() {}
    public static ExecutionRequest from(UUID operation, ResolutionContext context, RuleResolver.Resolution resolution) {
        if (!resolution.accepted() || !resolution.cost().equals(context.invocation().binding().definition().cost()))
            throw new IllegalArgumentException("accepted resolution required");
        var snapshot = context.actor().actor(); var binding = context.invocation().binding();
        return new ExecutionRequest(operation, snapshot.actor(), snapshot.instance(), context.encounter().id(),
                context.encounter().version(), binding.definition().executor(), binding.version(),
                snapshot.revision().ruleset(), snapshot.revision(), binding.source(), context.invocation().intent(), resolution.cost());
    }
}
