package cc.sighs.dndturn.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-thread-owned debug-local session; it never authorizes tactical actions. */
public final class CombatTimeController {
    private final Map<UUID, Encounter> encounters = new HashMap<>();
    private final Map<UUID, UUID> membership = new HashMap<>();

    public UUID begin(Set<UUID> members) {
        Objects.requireNonNull(members, "members");
        if (members.size() != 1) {
            throw new IllegalArgumentException("A debug-local session needs exactly one member");
        }
        for (UUID member : members) {
            Objects.requireNonNull(member, "member");
            if (membership.containsKey(member)) {
                throw new IllegalStateException("Member already belongs to an encounter: " + member);
            }
        }

        UUID id = UUID.randomUUID();
        Encounter encounter = new Encounter();
        for (UUID member : members) {
            encounter.controllers.put(member, new VirtualController());
            membership.put(member, id);
        }
        encounters.put(id, encounter);
        return id;
    }

    public Set<UUID> end(UUID encounterId) {
        Encounter encounter = encounters.remove(encounterId);
        if (encounter == null) {
            return Set.of();
        }
        Set<UUID> members = Set.copyOf(encounter.controllers.keySet());
        members.forEach(membership::remove);
        return members;
    }

    public Set<UUID> endFor(UUID member) {
        UUID encounterId = membership.get(member);
        return encounterId == null ? Set.of() : end(encounterId);
    }

    public boolean isMember(UUID member) {
        return membership.containsKey(member);
    }

    public boolean isBodyPaused(UUID member) {
        VirtualController controller = controller(member);
        return controller != null && controller.actionTicks == 0;
    }

    public long controllerTicks(UUID member) {
        VirtualController controller = controller(member);
        return controller == null ? 0 : controller.ticks;
    }

    public int actionTicks(UUID member) {
        VirtualController controller = controller(member);
        return controller == null ? 0 : controller.actionTicks;
    }

    /** Grants a bounded window in which the real body uses its normal Minecraft tick path. */
    public void authorizeActionTicks(UUID member, int ticks) {
        VirtualController controller = controller(member);
        if (controller == null) {
            throw new IllegalStateException("Member has no active encounter: " + member);
        }
        if (ticks < 1 || ticks > 200) {
            throw new IllegalArgumentException("Action window must be 1..200 ticks");
        }
        controller.actionTicks = ticks;
        controller.newWindow = true;
    }

    /** Called after each server tick; returns bodies whose action window just expired. */
    public List<UUID> finishServerTick() {
        List<UUID> newlyPaused = new ArrayList<>();
        for (Encounter encounter : encounters.values()) {
            for (Map.Entry<UUID, VirtualController> entry : encounter.controllers.entrySet()) {
                VirtualController controller = entry.getValue();
                controller.ticks++;
                if (controller.newWindow) {
                    controller.newWindow = false;
                } else if (controller.actionTicks > 0 && --controller.actionTicks == 0) {
                    newlyPaused.add(entry.getKey());
                }
            }
        }
        return newlyPaused;
    }

    private VirtualController controller(UUID member) {
        UUID encounterId = membership.get(member);
        Encounter encounter = encounterId == null ? null : encounters.get(encounterId);
        return encounter == null ? null : encounter.controllers.get(member);
    }

    private static final class Encounter {
        private final Map<UUID, VirtualController> controllers = new LinkedHashMap<>();
    }

    private static final class VirtualController {
        private long ticks;
        private int actionTicks;
        private boolean newWindow;
    }
}
