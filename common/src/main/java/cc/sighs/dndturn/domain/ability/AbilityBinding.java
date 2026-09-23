package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.action.ActionIntent;
import java.util.*;

/** One definition through one grant. UI grouping must not erase this provenance. */
public record AbilityBinding(AbilityDefinition definition, AbilityGrant grant) {
    public AbilityBinding { Objects.requireNonNull(definition); Objects.requireNonNull(grant); }
    public String id() { return definition.id(); }
    public int version() { return definition.version(); }
    public ActionIntent.Capability kind() { return definition.kind(); }
    public Set<ActionIntent.TargetKind> targets() { return definition.targets(); }
    public GrantEvidence source() { return grant.evidence(); }
    public ActionIntent invocation(ActionIntent.Target target) {
        return new ActionIntent(id(), version(), kind(), target, source());
    }
}
