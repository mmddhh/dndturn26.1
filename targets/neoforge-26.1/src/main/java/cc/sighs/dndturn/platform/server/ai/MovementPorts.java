package cc.sighs.dndturn.platform.server.ai;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import java.util.*;
import net.minecraft.world.entity.Mob;

/** Independent movement compatibility axis. Queries never acquire the execution driver. */
public final class MovementPorts {
    public interface Port {
        boolean matches(Mob actor);
        ActionIntent opportunity(LiveActorContext actor, EncounterAuthority.StateView encounter, OperationRecord.Result previous);
        List<GridCell> plan(LiveActorContext actor, EncounterAuthority.StateView encounter, ActionIntent intent,
                            GridCell target, Runnable probeBudget);
        Driver start(Mob actor, GridCell destination);
    }
    /** Bounded native control ownership; no tick/move/travel loop or rule-resource writer. */
    public interface Driver {
        boolean owns(Mob actor);
        boolean done(Mob actor);
        int nextStepBudget(Mob actor);
        void release(Mob actor);
    }
    public record Registration(String id, int version, int priority, Port port) {
        public Registration {
            FactKey.requireId(id); Objects.requireNonNull(port);
            if (version < 1) throw new IllegalArgumentException("movement port version");
        }
    }
    private static final Map<String, Registration> registrations = new LinkedHashMap<>();
    private static boolean frozen;
    private MovementPorts() {}
    public static synchronized void register(Registration value) {
        if (frozen || registrations.size() >= 128 || registrations.putIfAbsent(value.id(), value) != null)
            throw new IllegalStateException("movement registration closed or duplicate");
    }
    public static synchronized void freeze() { frozen = true; }
    public static synchronized Registration find(Mob actor) {
        Registration best = null; boolean conflict = false;
        for (var value : registrations.values()) if (value.port().matches(actor)) {
            if (best == null || value.priority() > best.priority()) { best = value; conflict = false; }
            else if (value.priority() == best.priority()) conflict = true;
        }
        if (conflict) throw new IllegalStateException("MOVEMENT_PORT_CONFLICT");
        return best;
    }
    public static Registration require(Mob actor) {
        return Objects.requireNonNull(find(actor), "MOVEMENT_PORT_UNSUPPORTED");
    }
}
