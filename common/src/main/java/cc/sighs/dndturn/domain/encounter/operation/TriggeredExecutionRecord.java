package cc.sighs.dndturn.domain.encounter.operation;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.actor.ActorActivations;
import cc.sighs.dndturn.domain.actor.TriggeredAbilityInvocation;
import java.util.Objects;
import java.util.UUID;

/** Durable emission evidence. STARTED without a confirmed outcome is never replayed. */
public record TriggeredExecutionRecord(UUID operation, UUID root, ActorActivations.Event event, AbilityBinding source,
                               TriggeredAbilityInvocation invocation, Status status, String reason, WorldOutcomeObservation observation, UUID encounter,
                               Intervention intervention) {
    public TriggeredExecutionRecord(UUID operation, UUID root, ActorActivations.Event event, AbilityBinding source,
            TriggeredAbilityInvocation invocation, Status status, String reason, WorldOutcomeObservation observation, UUID encounter) {
        this(operation, root, event, source, invocation, status, reason, observation, encounter, null);
    }
    public TriggeredExecutionRecord(UUID operation, UUID root, ActorActivations.Event event, AbilityBinding source,
                            TriggeredAbilityInvocation invocation, Status status, String reason) {
        this(operation, root, event, source, invocation, status, reason, null, null);
    }
    public enum Status { PENDING, STARTED, COMPLETED, REJECTED, UNKNOWN }
    public TriggeredExecutionRecord {
        Objects.requireNonNull(operation); Objects.requireNonNull(root); Objects.requireNonNull(event);
        Objects.requireNonNull(source); Objects.requireNonNull(invocation); Objects.requireNonNull(status);
        Objects.requireNonNull(reason);
        if (!source.grant().actor().equals(event.actor()) || reason.length() > 256
                || status == Status.PENDING && observation != null)
            throw new IllegalArgumentException("effect invocation evidence");
        if (intervention != null && (status == Status.PENDING || event.causal() == null
                || event.causal().phase() != CausalEvent.Phase.ATTEMPT || !event.id().equals(intervention.event())))
            throw new IllegalArgumentException("intervention evidence");
    }
    public TriggeredExecutionRecord withStatus(Status next, String reason) {
        if (status == next && this.reason.equals(reason)) return this;
        if (status != Status.PENDING && status != Status.STARTED || next == Status.PENDING)
            throw new IllegalStateException("effect invocation terminal transition");
        return new TriggeredExecutionRecord(operation, root, event, source, invocation, next, reason, observation, encounter, intervention);
    }
    public TriggeredExecutionRecord observe(WorldOutcomeObservation value) {
        if (status != Status.STARTED) throw new IllegalStateException("observation outside execution");
        Objects.requireNonNull(value);
        return new TriggeredExecutionRecord(operation, root, event, source, invocation, status, reason, value, encounter, intervention);
    }
    public TriggeredExecutionRecord intervene(Intervention decision) {
        if (status != Status.STARTED || intervention != null) throw new IllegalStateException("intervention already settled");
        return new TriggeredExecutionRecord(operation, root, event, source, invocation, status, reason, observation, encounter,
                Objects.requireNonNull(decision));
    }
}
