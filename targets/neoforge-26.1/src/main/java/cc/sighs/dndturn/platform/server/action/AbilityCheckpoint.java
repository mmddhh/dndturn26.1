package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.encounter.operation.NativeObservation;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.action.ExecutionRequest;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.server.ability.AbilityExecutor;
import java.util.*;

/** Immutable target envelope evidence, never a resumable driver or a second resource ledger. */
public record AbilityCheckpoint(UUID operation, UUID encounter, UUID owner, UUID ownerInstance,
                                ActionIntent invocation, Phase phase, AbilityExecutor.ExecutionClock clock, long tick, long executionSteps,
                                UUID movement, UUID action, Sample before, Sample observed,
                                SourceChange sourceChange, String observationFailure,
                                Release release, String releaseReason,
                                ExecutionRequest ruleRequest,
                                List<NativeObservation> details) {
    public AbilityCheckpoint(UUID operation, UUID encounter, UUID owner, UUID ownerInstance,
            ActionIntent invocation, Phase phase, AbilityExecutor.ExecutionClock clock, long tick, long executionSteps,
            UUID movement, UUID action, Sample before, Sample observed, SourceChange sourceChange, String observationFailure,
            Release release, String releaseReason, ExecutionRequest ruleRequest) {
        this(operation, encounter, owner, ownerInstance, invocation, phase, clock, tick, executionSteps, movement, action,
                before, observed, sourceChange, observationFailure, release, releaseReason, ruleRequest, List.of());
    }
    public static final int MAX_OBSERVED_BLOCKS = 256;
    public enum Release { HELD, RELEASED, FAILED }
    public enum Phase { APPROACH, PREPARE, EXECUTE, OBSERVE, TERMINAL }
    public record SourceChange(long step, String before, String after) {
        public SourceChange {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            if (step < 0) throw new IllegalArgumentException("source change step");
        }
    }
    public record Item(int slot, String id, int count, int damage, String revision) {
        public Item {
            Objects.requireNonNull(id); Objects.requireNonNull(revision);
            if (slot < 0 || slot > 40 || count < 0 || damage < 0) throw new IllegalArgumentException("item evidence");
        }
    }
    public record Block(GridCell cell, String state) {
        public Block { Objects.requireNonNull(cell); Objects.requireNonNull(state); }
    }
    public record Body(UUID entity, UUID instance, double x, double y, double z, float health, float absorption) {
        public Body {
            Objects.requireNonNull(entity); Objects.requireNonNull(instance);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(health) || !Float.isFinite(absorption)) throw new IllegalArgumentException("body evidence");
        }
    }
    /** These are observed fields only, not a claim that every world effect is known. */
    public record Spawn(UUID entity, UUID instance, String type, double x, double y, double z) {
        public Spawn {
            Objects.requireNonNull(entity); Objects.requireNonNull(instance); Objects.requireNonNull(type);
            if (type.isEmpty() || type.length()>256 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("spawn evidence");
        }
    }
    public record Sample(List<Item> items, List<Block> blocks, List<Body> bodies, List<Spawn> spawned) {
        public Sample(List<Item> items, List<Block> blocks, List<Body> bodies) { this(items,blocks,bodies,List.of()); }
        public Sample {
            items = List.copyOf(items); blocks = List.copyOf(blocks); bodies = List.copyOf(bodies);
            spawned = List.copyOf(spawned);
            if (spawned.size() > 32) throw new IllegalArgumentException("spawn evidence bounds");
            if (items.size() > 41 || blocks.size() > MAX_OBSERVED_BLOCKS || bodies.size() > 2) throw new IllegalArgumentException("observation bounds");
        }
    }
    public AbilityCheckpoint {
        details = List.copyOf(details);
        if (details.size() > 128 || details.stream().anyMatch(value -> !value.operation().equals(operation)))
            throw new IllegalArgumentException("typed checkpoint observation identity");
        Objects.requireNonNull(operation); Objects.requireNonNull(encounter); Objects.requireNonNull(owner);
        Objects.requireNonNull(ownerInstance); Objects.requireNonNull(invocation); Objects.requireNonNull(phase); Objects.requireNonNull(clock);
        Objects.requireNonNull(release); Objects.requireNonNull(releaseReason); Objects.requireNonNull(observationFailure);
        if (observationFailure.length() > 256 || releaseReason.length() > 512) throw new IllegalArgumentException("evidence reason bounds");
        if (tick < 0 || executionSteps < 0) throw new IllegalArgumentException("checkpoint clock");
        if (ruleRequest != null && (!operation.equals(ruleRequest.operation()) || !owner.equals(ruleRequest.actor())
                || !ownerInstance.equals(ruleRequest.instance()) || !encounter.equals(ruleRequest.encounter())
                || !invocation.equals(ruleRequest.intent()))) throw new IllegalArgumentException("checkpoint rule request mismatch");
    }
}
