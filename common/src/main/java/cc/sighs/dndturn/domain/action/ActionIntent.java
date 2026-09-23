package cc.sighs.dndturn.domain.action;

import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.domain.spatial.GridCell;
import java.util.Objects;
import java.util.UUID;

/** Untrusted value intent. Target adapters must resolve and validate every world value. */
public record ActionIntent(String behaviorId, int behaviorVersion, Hand hand,
                             Capability capability, Target target, ItemReference item, GrantEvidence source, Approach approach, String ruleset,
                             java.util.Map<String, String> parameters) {
    public ActionIntent(String behaviorId, int behaviorVersion, Hand hand, Capability capability, Target target,
            ItemReference item, GrantEvidence source, Approach approach, String ruleset) {
        this(behaviorId, behaviorVersion, hand, capability, target, item, source, approach, ruleset, java.util.Map.of());
    }
    public ActionIntent(String behaviorId, int behaviorVersion, Hand hand, Capability capability, Target target,
                          ItemReference item, GrantEvidence source, Approach approach) {
        this(behaviorId, behaviorVersion, hand, capability, target, item, source, approach, CombatRules.RULES_REVISION);
    }
    public ActionIntent(String behaviorId, int behaviorVersion, Hand hand, Capability capability, Target target, ItemReference item, GrantEvidence source) {
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
    public ActionIntent withApproach(Approach value) {
        return new ActionIntent(behaviorId, behaviorVersion, hand, capability, target, item, source, value, ruleset, parameters);
    }
    public enum Hand { MAIN_HAND, OFF_HAND }
    public ActionIntent(String behaviorId, int behaviorVersion, Capability capability, Target target, GrantEvidence source) {
        this(behaviorId, behaviorVersion, source.hand(), capability, target, source.item(), source);
    }
    public enum Capability { MOVE, ATTACK, PLACE, BREAK, USE_ITEM, USE_BLOCK, EQUIP }
    public enum TargetKind { ENTITY, BLOCK, GROUND, SELF }
    /** Hit identity beneath an Actor; the enclosing target UUID remains the sole resource owner. */
    public record BodyFacet(String adapter, int version, String part, UUID instance, UUID bodyInstance) {
        public BodyFacet {
            cc.sighs.dndturn.domain.fact.FactKey.requireId(adapter); Objects.requireNonNull(part); Objects.requireNonNull(instance);
            Objects.requireNonNull(bodyInstance);
            if (version < 1 || part.isBlank() || part.length() > 128) throw new IllegalArgumentException("body facet identity");
        }
    }
    public record Target(TargetKind kind, String dimension, UUID entity, GridCell cell,
                         int face, double x, double y, double z, BodyFacet facet) {
        public Target(TargetKind kind, String dimension, UUID entity, GridCell cell, int face, double x, double y, double z) {
            this(kind, dimension, entity, cell, face, x, y, z, null);
        }
        public Target {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(dimension);
            if (facet != null && kind != TargetKind.ENTITY) throw new IllegalArgumentException("only actors have body facets");
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
    public ActionIntent {
        parameters = parameters == null ? java.util.Map.of() : java.util.Map.copyOf(parameters);
        if (parameters.size() > 16 || parameters.entrySet().stream().anyMatch(e -> e.getKey().isBlank()
                || e.getKey().length() > 64 || e.getValue().length() > 128)) throw new IllegalArgumentException("ability parameter bounds");
        Objects.requireNonNull(ruleset);
        if (ruleset.isBlank() || ruleset.length() > 128) throw new IllegalArgumentException("ruleset version");
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
            case ATTACK -> true; // The registered ability owns its target shape, independently of cost and execution kind.
            case PLACE, BREAK, USE_BLOCK -> target.kind() == TargetKind.BLOCK;
            case USE_ITEM -> true;
            case EQUIP -> target.kind() == TargetKind.SELF;
        };
        if (!valid || (capability != Capability.MOVE && capability != Capability.USE_BLOCK
            && source.kind() == GrantEvidence.Kind.BASIC))
            throw new IllegalArgumentException("capability target or item mismatch");
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
