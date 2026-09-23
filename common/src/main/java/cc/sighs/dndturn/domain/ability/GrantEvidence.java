package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.action.ActionIntent;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pure source evidence, never execution permission. Equipment can additionally bind a live effect and audited write generations. */
public record GrantEvidence(Kind kind, ActionIntent.Hand hand, ActionIntent.ItemReference item,
                            String provider, int version, UUID actor, UUID instance, UUID grant,
                            long revision, UUID domain, Map<String, String> dependencies) {
    public GrantEvidence(Kind kind, ActionIntent.Hand hand, ActionIntent.ItemReference item,
                         String provider, int version, UUID actor, UUID instance, UUID grant, long revision, UUID domain) {
        this(kind, hand, item, provider, version, actor, instance, grant, revision, domain, Map.of());
    }
    public enum Kind { EQUIPMENT, BASIC, INTRINSIC, STATUS, PERSISTENT, EFFECT, ENCOUNTER }
    public GrantEvidence(Kind kind, ActionIntent.Hand hand, ActionIntent.ItemReference item,
                         String provider, int version, UUID actor, UUID instance, UUID grant) {
        this(kind, hand, item, provider, version, actor, instance, grant, 0, null);
    }
    public GrantEvidence(Kind kind, ActionIntent.Hand hand, ActionIntent.ItemReference item,
                         String provider, int version, UUID actor, UUID instance) {
        this(kind, hand, item, provider, version, actor, instance, null);
    }
    public GrantEvidence {
        Objects.requireNonNull(kind);
        dependencies = dependencies == null ? Map.of() : Map.copyOf(dependencies);
        if (dependencies.size() > 16 || dependencies.entrySet().stream().anyMatch(e ->
                e.getKey().isBlank() || e.getKey().length() > 64 || e.getValue().length() > 256))
            throw new IllegalArgumentException("source dependency bounds");
        boolean owned = kind == Kind.PERSISTENT || kind == Kind.EFFECT || kind == Kind.ENCOUNTER;
        boolean effectEquipment = kind == Kind.EQUIPMENT && provider != null;
        if ((kind == Kind.STATUS || owned || effectEquipment) != (grant != null)) throw new IllegalArgumentException("grant evidence");
        if (owned || effectEquipment ? revision < 1 : revision != 0) throw new IllegalArgumentException("grant revision");
        if ((kind == Kind.ENCOUNTER) != (domain != null)) throw new IllegalArgumentException("grant domain");
        if (effectEquipment) {
            Objects.requireNonNull(hand); Objects.requireNonNull(item);
            if (!provider.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || provider.length() > 128
                    || version < 1 || actor == null || instance == null) throw new IllegalArgumentException("equipment effect evidence");
        } else if (kind == Kind.INTRINSIC || kind == Kind.STATUS || owned) {
            if (hand != null || item != null || provider == null || provider.length() > 128
                || !provider.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || version < 1
                || actor == null || instance == null) throw new IllegalArgumentException("intrinsic source evidence");
        } else {
            if (kind == Kind.EQUIPMENT) Objects.requireNonNull(hand);
            if ((kind == Kind.EQUIPMENT) != (item != null) || provider != null || version != 0
                || actor != null || instance != null) throw new IllegalArgumentException("equipment/basic source evidence");
        }
    }
    public static GrantEvidence equipment(ActionIntent.Hand hand, ActionIntent.ItemReference item) {
        return new GrantEvidence(Kind.EQUIPMENT, hand, Objects.requireNonNull(item), null, 0, null, null);
    }
    public static GrantEvidence equipment(ActionIntent.Hand hand, ActionIntent.ItemReference item,
            String policy, int version, UUID actor, UUID instance, UUID effect, long revision) {
        return new GrantEvidence(Kind.EQUIPMENT, hand, item, policy, version, actor, instance, effect, revision, null);
    }
    public GrantEvidence withItemRevision(String value) {
        if (kind != Kind.EQUIPMENT) throw new IllegalStateException("not equipment evidence");
        return new GrantEvidence(kind, hand, new ActionIntent.ItemReference(item.slot(), value), provider, version,
                actor, instance, grant, revision, domain, dependencies);
    }
    public GrantEvidence withDependencies(Map<String, String> values) {
        return new GrantEvidence(kind, hand, item, provider, version, actor, instance, grant, revision, domain, values);
    }
    public static GrantEvidence basic(ActionIntent.Hand hand) {
        return new GrantEvidence(Kind.BASIC, hand, null, null, 0, null, null);
    }
    public static GrantEvidence basic() { return basic(null); }
    public static GrantEvidence intrinsic(String provider, int version, UUID actor, UUID instance) {
        return new GrantEvidence(Kind.INTRINSIC, null, null, provider, version, actor, instance);
    }
    /** The provider owns grant lifetime; restoring this value does not restore a live grant. */
    public static GrantEvidence status(String provider, int version, UUID actor, UUID instance, UUID grant) {
        return new GrantEvidence(Kind.STATUS, null, null, provider, version, actor, instance, grant);
    }
    public static GrantEvidence owned(Kind kind, String provider, int version, UUID actor, UUID instance,
                                      UUID grant, long revision, UUID domain) {
        if (kind != Kind.PERSISTENT && kind != Kind.EFFECT && kind != Kind.ENCOUNTER)
            throw new IllegalArgumentException("not an owned grant");
        return new GrantEvidence(kind, null, null, provider, version, actor, instance, grant, revision, domain);
    }
}
