package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import java.util.*;

/** Common rule definition; registration grants neither ownership nor execution permission. */
public record AbilityDefinition(String id, int version, String label, ActionIntent.Capability kind,
                                Set<ActionIntent.TargetKind> targets, Activation activation,
                                ActionCost cost, ReadContract reads, String executor, TargetPolicy targetPolicy,
                                OperationRecord.Kind executionKind,
                                Map<String, Set<String>> parameters) {
    public AbilityDefinition(String id, int version, String label, ActionIntent.Capability kind,
            Set<ActionIntent.TargetKind> targets, Activation activation, ActionCost cost, ReadContract reads,
            String executor, TargetPolicy targetPolicy) {
        this(id, version, label, kind, targets, activation, cost, reads, executor, targetPolicy,
                defaultExecutionKind(kind), Map.of());
    }
    private static OperationRecord.Kind defaultExecutionKind(ActionIntent.Capability kind) {
        return switch (kind) {
            case MOVE -> OperationRecord.Kind.MOVE;
            case ATTACK -> OperationRecord.Kind.ATTACK;
            case PLACE -> OperationRecord.Kind.PLACE;
            case BREAK -> OperationRecord.Kind.BREAK;
            case USE_ITEM -> OperationRecord.Kind.USE_ITEM;
            case USE_BLOCK, EQUIP -> OperationRecord.Kind.USE_BLOCK;
        };
    }
    public enum Activation { MANUAL, TRIGGERED, CONTINUOUS, PERIODIC }
    public enum TargetPolicy { NATIVE_INTERACTION, NON_PLAYER_LIVING_MEMBER, OPPOSITE_PLAYER_LIVING_MEMBER }
    public AbilityDefinition {
        FactKey.requireId(id); FactKey.requireId(executor);
        Objects.requireNonNull(label); Objects.requireNonNull(kind); Objects.requireNonNull(activation);
        Objects.requireNonNull(cost); Objects.requireNonNull(reads);
        if (cost.requiresReaction() && activation != Activation.TRIGGERED)
            throw new IllegalArgumentException("reaction cost requires triggered admission");
        Objects.requireNonNull(targetPolicy);
        executionKind = executionKind == null ? defaultExecutionKind(kind) : executionKind;
        var bounded = new LinkedHashMap<String, Set<String>>();
        if (parameters != null) parameters.forEach((key, values) -> {
            if (key.isBlank() || key.length() > 64 || values.isEmpty() || values.size() > 32
                    || values.stream().anyMatch(v -> v.length() > 128)) throw new IllegalArgumentException("ability parameter contract");
            bounded.put(key, Set.copyOf(values));
        });
        parameters = Map.copyOf(bounded);
        if (parameters.size() > 16) throw new IllegalArgumentException("ability parameter bound");
        targets = Set.copyOf(targets);
        if (version < 1 || label.isBlank() || label.length() > 128 || targets.isEmpty())
            throw new IllegalArgumentException("ability definition");
        if (!reads.keys().contains(RuleFacts.SOURCE_VALID))
            throw new IllegalArgumentException("every ability must declare grant evidence");
    }
    public void validateParameters(Map<String, String> values) {
        if (values.entrySet().stream().anyMatch(e -> !parameters.getOrDefault(e.getKey(), Set.of()).contains(e.getValue())))
            throw new IllegalArgumentException("unsupported ability parameter");
    }
}
