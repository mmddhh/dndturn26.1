package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import net.minecraft.world.entity.LivingEntity;

/** GameTest-only friend access. Never included in the production source set. */
public final class ActionTestAccess {
    private ActionTestAccess() {}
    public static EncounterAuthority authority(ActionExecutionCoordinator actions) { return actions.engine; }
    public static void validate(ActionExecutionCoordinator actions, LivingEntity actor, ActionIntent intent, EncounterAuthority.StateView state) {
        actions.validate(actor, intent, state);
    }
}
