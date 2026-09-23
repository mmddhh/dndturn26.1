package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.UUID;

/** Untrusted value intent. Target adapters must resolve and validate every world value. */
public record TacticalIntent(String behaviorId, int behaviorVersion, Hand hand,
                             Capability capability, Target target, ItemReference item, AbilitySource source, Approach approach) {
    public TacticalIntent(String behaviorId, int behaviorVersion, Hand hand, Capability capability, Target target, ItemReference item, AbilitySource source) {
        this(behaviorId, behaviorVersion, hand, capability, target, item, source, null);
    }
    /** Selected preview position participates in deduplication; it is never an authorization. */
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 20_000_000)
                throw new IllegalArgumentException("invalid preview position");
        }
    }
    public record Approach(UUID proposal, int candidate, Point feet) {
        public Approach {
            Objects.requireNonNull(proposal); Objects.requireNonNull(feet);
            if (candidate < 0 || candidate >= 256) throw new IllegalArgumentException("candidate index");
        }
    }
    public TacticalIntent withApproach(Approach value) {
        return new TacticalIntent(behaviorId, behaviorVersion, hand, capability, target, item, source, value);
    }
    public enum Hand { MAIN_HAND, OFF_HAND }
    public TacticalIntent(String behaviorId, int behaviorVersion, Capability capability, Target target, AbilitySource source) {
        this(behaviorId, behaviorVersion, source.hand(), capability, target, source.item(), source);
    }
    public enum Capability { MOVE, ATTACK, PLACE, BREAK, USE_ITEM, USE_BLOCK, EQUIP }
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
        Objects.requireNonNull(source);
        if (hand != source.hand() || !Objects.equals(item, source.item())) throw new IllegalArgumentException("source payload conflict");
        if (!behaviorId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || behaviorId.length() > 128 || behaviorVersion < 1)
            throw new IllegalArgumentException("behavior contract");
        Objects.requireNonNull(capability);
        Objects.requireNonNull(target);
        if (approach != null && target.kind() == TargetKind.SELF) throw new IllegalArgumentException("self action cannot select an approach");
        boolean valid = switch (capability) {
            case MOVE -> target.kind() == TargetKind.GROUND;
            case ATTACK -> target.kind() == TargetKind.ENTITY;
            case PLACE, BREAK, USE_BLOCK -> target.kind() == TargetKind.BLOCK;
            case USE_ITEM -> true;
            case EQUIP -> target.kind() == TargetKind.SELF;
        };
        if (!valid || (capability != Capability.MOVE && capability != Capability.USE_BLOCK
            && source.kind() == AbilitySource.Kind.BASIC))
            throw new IllegalArgumentException("capability target or item mismatch");
    }
    public boolean requiresAction() {
        return switch (capability) {
            case MOVE, USE_BLOCK, EQUIP -> false;
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
            case USE_BLOCK, EQUIP -> OperationRecord.Kind.USE_BLOCK;
        };
    }
}
