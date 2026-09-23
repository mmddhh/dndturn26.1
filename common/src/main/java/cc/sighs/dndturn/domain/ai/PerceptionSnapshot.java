package cc.sighs.dndturn.domain.ai;

import java.util.*;

/** Current sensory evidence. Commitment is not hostility, legality, or permission. */
public record PerceptionSnapshot(List<ObservedActor> actors) {
    public enum Relation { UNKNOWN, HOSTILE, ALLY, OWNER, PROTECTED, RETALIATION }
    public record Evidence(cc.sighs.dndturn.domain.action.ActionIntent.Point lastKnownPosition,
                           long age, String sensor, double confidence, Set<Relation> relations) {
        public Evidence {
            cc.sighs.dndturn.domain.fact.FactKey.requireId(sensor); relations = Set.copyOf(relations);
            if (age < 0 || !Double.isFinite(confidence) || confidence < 0 || confidence > 1)
                throw new IllegalArgumentException("perception evidence");
        }
    }
    public record ObservedActor(UUID id, boolean visible, boolean currentTarget, boolean player,
                                boolean nativeAttackable, double distanceSquared, Evidence evidence) {
        public ObservedActor(UUID id, boolean visible, boolean currentTarget, boolean player,
                             boolean nativeAttackable, double distanceSquared) {
            this(id, visible, currentTarget, player, nativeAttackable, distanceSquared,
                    new Evidence(null, 0, "dndturn:legacy_observation", visible ? 1 : 0, Set.of()));
        }
        public ObservedActor { Objects.requireNonNull(id);
            Objects.requireNonNull(evidence);
            if (!Double.isFinite(distanceSquared) || distanceSquared < 0) throw new IllegalArgumentException("distance"); }
    }
    public PerceptionSnapshot { actors = List.copyOf(actors);
        if (actors.size() > 4096) throw new IllegalArgumentException("perception bound"); }
}
