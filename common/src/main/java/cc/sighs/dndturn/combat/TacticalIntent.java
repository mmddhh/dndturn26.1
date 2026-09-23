package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.UUID;

/** Untrusted value intent. Target adapters must resolve and validate every world value. */
public record TacticalIntent(String behaviorId, int behaviorVersion, Hand hand,
                             Capability capability, Target target, ItemReference item) {
    public enum Hand { MAIN_HAND, OFF_HAND }
    /** Source compatibility for callers selecting an explicit built-in family; never detects items. */
    public TacticalIntent(Capability capability, Target target, ItemReference item) {
        this("dndturn:" + switch (capability) {
            case MOVE -> "move"; case ATTACK -> "melee"; case PLACE -> "place";
            case BREAK -> "break"; case USE_BLOCK -> "block"; case USE_ITEM -> "consume";
        }, 1, Hand.MAIN_HAND, capability, target, item);
    }
    public enum Capability { MOVE, ATTACK, PLACE, BREAK, USE_ITEM, USE_BLOCK }
    public enum TargetKind { ENTITY, BLOCK, GROUND, SELF }
    public record Target(TargetKind kind, String dimension, UUID entity, GridCell cell,
                         int face, double x, double y, double z) {
        public Target {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(dimension);
            if (dimension.isBlank() || dimension.length() > 256 || !Double.isFinite(x)
                || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("target values");
            if ((kind == TargetKind.ENTITY) != (entity != null)
                || ((kind == TargetKind.BLOCK || kind == TargetKind.GROUND) != (cell != null))
                || (kind == TargetKind.BLOCK ? face < 0 || face > 5 : face != -1))
                throw new IllegalArgumentException("target shape");
            if (kind == TargetKind.BLOCK && (x < 0 || x > 1 || y < 0 || y > 1 || z < 0 || z > 1))
                throw new IllegalArgumentException("block hit outside face");
        }
    }
    /** Fingerprint verified against the authoritative revision of the complete stack, not just its item type. */
    public record ItemReference(int slot, String revision) {
        public ItemReference {
            Objects.requireNonNull(revision);
            if (slot < 0 || slot > 40 || revision.isBlank() || revision.length() > 128)
                throw new IllegalArgumentException("item reference");
        }
    }
    public TacticalIntent {
        Objects.requireNonNull(behaviorId);
        Objects.requireNonNull(hand);
        if (!behaviorId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || behaviorId.length() > 128 || behaviorVersion < 1)
            throw new IllegalArgumentException("behavior contract");
        Objects.requireNonNull(capability);
        Objects.requireNonNull(target);
        boolean valid = switch (capability) {
            case MOVE -> target.kind() == TargetKind.GROUND;
            case ATTACK -> target.kind() == TargetKind.ENTITY;
            case PLACE, BREAK, USE_BLOCK -> target.kind() == TargetKind.BLOCK;
            case USE_ITEM -> true;
        };
        if (!valid || (capability != Capability.MOVE && capability != Capability.USE_BLOCK && item == null))
            throw new IllegalArgumentException("capability target or item mismatch");
    }
    public boolean requiresAction() {
        return switch (capability) {
            case MOVE, USE_BLOCK -> false;
            case ATTACK, PLACE, BREAK, USE_ITEM -> true;
        };
    }
    public OperationRecord.Kind executionKind() {
        return switch (capability) {
            case MOVE -> OperationRecord.Kind.MOVE;
            case ATTACK -> OperationRecord.Kind.ATTACK;
            case PLACE -> OperationRecord.Kind.PLACE;
            case BREAK -> OperationRecord.Kind.BREAK;
            case USE_ITEM -> OperationRecord.Kind.USE_ITEM;
            case USE_BLOCK -> OperationRecord.Kind.USE_BLOCK;
        };
    }
}
