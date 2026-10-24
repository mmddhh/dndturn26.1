package cc.sighs.dndturn.platform.server.actor;
import java.util.List;
import java.util.UUID;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
/** Bounded state handoff commands; no access to the actor authority or other owners. */
public interface ActorLifecycleHost {
    ActorStates.State state(UUID actor);
    ActorStates.State command(LiveActorContext actor, ActorStates.Command command);
    List<TriggeredExecutionRecord> invocations(UUID actor);
    void invocationStatus(UUID operation, TriggeredExecutionRecord.Status status, String reason);
    UUID encounterOf(UUID actor);
}
