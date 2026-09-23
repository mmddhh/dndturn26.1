package cc.sighs.dndturn.domain.ability;

import java.util.Objects;

/** Cost and acceptance boundary are independent of operation kind. No new gameplay resource. */
public record ActionCost(Resource resource, CommitPoint commitPoint) {
    public enum Resource { FREE, ACTION, MOVEMENT, REACTION }
    public enum CommitPoint { LEGAL_ATTACK, ACCEPTED_EFFECT, MOVEMENT_OBSERVATION, FREE }
    public static final ActionCost ATTACK = new ActionCost(Resource.ACTION, CommitPoint.LEGAL_ATTACK);
    public static final ActionCost USE = new ActionCost(Resource.ACTION, CommitPoint.ACCEPTED_EFFECT);
    public static final ActionCost MOVE = new ActionCost(Resource.MOVEMENT, CommitPoint.MOVEMENT_OBSERVATION);
    public static final ActionCost FREE = new ActionCost(Resource.FREE, CommitPoint.FREE);
    public static final ActionCost REACTION = new ActionCost(Resource.REACTION, CommitPoint.LEGAL_ATTACK);
    public ActionCost {
        Objects.requireNonNull(resource); Objects.requireNonNull(commitPoint);
        if (resource == Resource.FREE ? commitPoint != CommitPoint.FREE
                : resource == Resource.MOVEMENT ? commitPoint != CommitPoint.MOVEMENT_OBSERVATION
                : commitPoint != CommitPoint.LEGAL_ATTACK && commitPoint != CommitPoint.ACCEPTED_EFFECT)
            throw new IllegalArgumentException("cost boundary mismatch");
    }
    public boolean requiresAction() { return resource == Resource.ACTION; }
    public boolean requiresReaction() { return resource == Resource.REACTION; }
}
