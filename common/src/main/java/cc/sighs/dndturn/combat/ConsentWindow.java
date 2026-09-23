package cc.sighs.dndturn.combat;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pre-encounter consent only: no membership, body permission, or action resource is granted here. */
public final class ConsentWindow {
    public enum Status { WAITING, ACCEPTED, DECLINED, EXPIRED, DISCONNECTED, CANCELED }
    public record View(UUID id, long revision, long deadline, Set<UUID> members,
                       Set<UUID> approvals, Status status) {
        public View { members = Set.copyOf(members); approvals = Set.copyOf(approvals); }
    }

    private final UUID id;
    private long deadline;
    private long revision;
    private Set<UUID> members;
    private final Set<UUID> approvals = new HashSet<>();
    private Status status = Status.WAITING;

    public ConsentWindow(UUID id, long now, long duration, Set<UUID> members) {
        this.id = Objects.requireNonNull(id);
        if (now < 0 || duration <= 0) throw new IllegalArgumentException("consent clock");
        this.deadline = Math.addExact(now, duration);
        this.members = Set.copyOf(members);
    }

    public View view() { return new View(id, revision, deadline, members, approvals, status); }

    public void refresh(Set<UUID> current, long now) {
        Set<UUID> checked = Set.copyOf(current);
        expire(now);
        if (status != Status.WAITING || checked.equals(members)) return;
        approvals.retainAll(checked);
        members = checked;
        revision++;
    }

    public void respond(UUID member, long expectedRevision, boolean agree, long now) {
        Objects.requireNonNull(member);
        expire(now);
        if (status != Status.WAITING) throw new IllegalStateException("consent window is closed");
        if (expectedRevision != revision) throw new IllegalStateException("consent roster changed");
        if (!members.contains(member)) throw new IllegalStateException("not in consent roster");
        if (!agree) { status = Status.DECLINED; revision++; }
        else if (approvals.add(member)) revision++;
    }

    /** Platform must resample/revalidate the proposal before calling commit. */
    public boolean ready(long now) {
        expire(now);
        return status == Status.WAITING && !members.isEmpty() && approvals.containsAll(members);
    }

    public void commit(long now) {
        if (!ready(now)) throw new IllegalStateException("consent is incomplete or expired");
        status = Status.ACCEPTED;
        revision++;
    }

    public void disconnected(UUID member) {
        if (status == Status.WAITING && members.contains(member)) {
            status = Status.DISCONNECTED;
            revision++;
        }
    }

    public void cancel() {
        if (status == Status.WAITING) { status = Status.CANCELED; revision++; }
    }

    public void expire(long now) {
        if (now < 0) throw new IllegalArgumentException("consent clock");
        if (status == Status.WAITING && now >= deadline) { status = Status.EXPIRED; revision++; }
    }

    /** Caller chooses the stable request identity; merged requests never extend the earliest deadline. */
    public void absorb(ConsentWindow other, Set<UUID> current, long now) {
        Objects.requireNonNull(other);
        Set<UUID> checked = Set.copyOf(current);
        expire(now); other.expire(now);
        if (other == this || status != Status.WAITING || other.status != Status.WAITING)
            throw new IllegalStateException("cannot merge closed consent windows");
        deadline = Math.min(deadline, other.deadline);
        approvals.addAll(other.approvals);
        approvals.retainAll(checked);
        members = checked;
        revision++;
        other.cancel();
    }
}
