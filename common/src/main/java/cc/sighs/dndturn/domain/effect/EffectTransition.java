package cc.sighs.dndturn.domain.effect;

import cc.sighs.dndturn.domain.ability.ActivationSpec;
import cc.sighs.dndturn.domain.actor.ActorStates;
import java.util.*;

/** Committed before/after facts. Thresholds compare edges, never the current level alone. */
public record EffectTransition(UUID instance, long beforeRevision, long afterRevision,
                               EffectInstance before, EffectInstance after,
                               Set<ActivationSpec.Event> events) {
    public enum Edge { CROSS_UP, CROSS_DOWN, BECAME_ZERO, BECAME_NONZERO }
    public EffectTransition {
        Objects.requireNonNull(instance); events = Set.copyOf(events);
        if (before == null && after == null || beforeRevision < 0 || afterRevision <= beforeRevision
                || before != null && (!instance.equals(before.id()) || before.revision() != beforeRevision)
                || after != null && (!instance.equals(after.id()) || after.revision() != afterRevision))
            throw new IllegalArgumentException("effect transition revisions");
        var expected = EnumSet.noneOf(ActivationSpec.Event.class);
        if (before == null) expected.add(ActivationSpec.Event.APPLIED);
        if (after == null) expected.add(ActivationSpec.Event.REMOVED);
        if ((before == null ? 0 : before.stacks()) != (after == null ? 0 : after.stacks()))
            expected.add(ActivationSpec.Event.STACK_CHANGED);
        if (events.contains(ActivationSpec.Event.EXPIRED) && after == null) expected.add(ActivationSpec.Event.EXPIRED);
        if (!events.equals(expected)) throw new IllegalArgumentException("effect transition event mismatch");
    }
    public boolean crosses(Edge edge, int threshold) {
        Objects.requireNonNull(edge);
        if (threshold < 1 || threshold > 64) throw new IllegalArgumentException("effect threshold");
        int old = before == null ? 0 : before.stacks(), next = after == null ? 0 : after.stacks();
        return switch (edge) {
            case CROSS_UP -> old < threshold && next >= threshold;
            case CROSS_DOWN -> old >= threshold && next < threshold;
            case BECAME_ZERO -> old != 0 && next == 0;
            case BECAME_NONZERO -> old == 0 && next != 0;
        };
    }
    public static List<EffectTransition> between(ActorStates.State before, ActorStates.State after, Set<UUID> expired) {
        var ids = new TreeSet<UUID>(before.runtime().effects().keySet());
        ids.addAll(after.runtime().effects().keySet());
        var result = new ArrayList<EffectTransition>();
        for (var id : ids) {
            var old = before.runtime().effects().get(id);
            var next = after.runtime().effects().get(id);
            if (Objects.equals(old, next)) continue;
            var events = EnumSet.noneOf(ActivationSpec.Event.class);
            if (old == null) events.add(ActivationSpec.Event.APPLIED);
            if (next == null) {
                events.add(ActivationSpec.Event.REMOVED);
                if (expired.contains(id)) events.add(ActivationSpec.Event.EXPIRED);
            }
            if ((old == null ? 0 : old.stacks()) != (next == null ? 0 : next.stacks()))
                events.add(ActivationSpec.Event.STACK_CHANGED);
            result.add(new EffectTransition(id, old == null ? 0 : old.revision(),
                    next == null ? after.revision() : next.revision(), old, next, events));
        }
        return List.copyOf(result);
    }
}
