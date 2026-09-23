package cc.sighs.dndturn.domain.effect;

import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.StatKey;
import java.util.*;

/** Tactical effect definition (legacy source name). Native effects remain native-owned observations. */
public record EffectDefinition(String id, int version, Clock clock, Stacking stacking,
                                  int maxStacks, boolean surviveDeath, boolean persist,
                                  List<Modifier> modifiers, List<Grant> grants, Set<String> tags,
                                  InstancePolicy instancePolicy, Overflow overflow, Refresh refresh) {
    public enum InstancePolicy { SINGLE_PER_TARGET, PER_SOURCE_ACTOR, PER_SOURCE_ABILITY, UNIQUE_APPLICATION }
    public enum Overflow { REJECT, CLAMP }
    public enum Refresh { KEEP, REPLACE, MAXIMUM }
    public enum Scaling { CONSTANT, PER_STACK }
    public EffectDefinition(String id, int version, Clock clock, Stacking stacking, int maxStacks,
                               boolean surviveDeath, boolean persist, List<Modifier> modifiers, List<Grant> grants) {
        this(id, version, clock, stacking, maxStacks, surviveDeath, persist, modifiers, grants,
                Set.of(), InstancePolicy.UNIQUE_APPLICATION, Overflow.REJECT,
                stacking == Stacking.REFRESH ? Refresh.MAXIMUM : Refresh.REPLACE);
    }
    public enum Clock { SIMULATION_STEP, TURN_START, TURN_END, EXPLICIT }
    public enum Stacking { REJECT, REPLACE, REFRESH, STACK }
    public enum Operator { ADD, MULTIPLY }
    public record Modifier(StatKey stat, Operator operator, double amount, Scaling scaling) {
        public Modifier(StatKey stat, Operator operator, double amount) { this(stat, operator, amount, Scaling.PER_STACK); }
        public Modifier(String stat, Operator operator, double amount) { this(new StatKey(stat), operator, amount); }
        public Modifier {
            Objects.requireNonNull(stat); Objects.requireNonNull(operator); Objects.requireNonNull(scaling);
            if (!Double.isFinite(amount)) throw new IllegalArgumentException("modifier amount");
        }
    }
    public record Grant(String ability, int version, int minimumStacks, int minimumRank) {
        public Grant(String ability, int version) { this(ability, version, 1, 1); }
        public Grant {
            FactKey.requireId(ability);
            if (version < 1 || minimumStacks < 1 || minimumStacks > 64 || minimumRank < 1)
                throw new IllegalArgumentException("effect grant requirement");
        }
    }
    public EffectDefinition {
        FactKey.requireId(id); Objects.requireNonNull(clock); Objects.requireNonNull(stacking);
        modifiers = List.copyOf(modifiers); grants = List.copyOf(grants);
        tags = Set.copyOf(tags); tags.forEach(FactKey::requireId);
        Objects.requireNonNull(instancePolicy); Objects.requireNonNull(overflow); Objects.requireNonNull(refresh);
        if (version < 1 || maxStacks < 1 || maxStacks > 64 || modifiers.size() > 64 || grants.size() > 64
                || tags.size() > 64 || stacking != Stacking.STACK && maxStacks != 1
                || grants.stream().map(grant -> grant.ability() + "@" + grant.version()).distinct().count() != grants.size())
            throw new IllegalArgumentException("effect contract");
        if (grants.stream().anyMatch(grant -> grant.minimumStacks() > maxStacks))
            throw new IllegalArgumentException("unreachable effect grant");
    }
}
