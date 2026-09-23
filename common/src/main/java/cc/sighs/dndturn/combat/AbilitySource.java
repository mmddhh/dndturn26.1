package cc.sighs.dndturn.combat;

import java.util.Objects;
import java.util.UUID;

/** Pure source evidence, never execution permission. Equipment proves content/slot equivalence only. */
public record AbilitySource(Kind kind, TacticalIntent.Hand hand, TacticalIntent.ItemReference item,
                            String provider, int version, UUID actor, UUID instance, UUID grant) {
    public enum Kind { EQUIPMENT, BASIC, INTRINSIC, STATUS }
    public AbilitySource(Kind kind, TacticalIntent.Hand hand, TacticalIntent.ItemReference item,
                         String provider, int version, UUID actor, UUID instance) {
        this(kind, hand, item, provider, version, actor, instance, null);
    }
    public AbilitySource {
        Objects.requireNonNull(kind);
        if ((kind == Kind.STATUS) != (grant != null)) throw new IllegalArgumentException("status grant evidence");
        if (kind == Kind.INTRINSIC || kind == Kind.STATUS) {
            if (hand != null || item != null || provider == null || provider.length() > 128
                || !provider.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || version < 1
                || actor == null || instance == null) throw new IllegalArgumentException("intrinsic source evidence");
        } else {
            if (kind == Kind.EQUIPMENT) Objects.requireNonNull(hand);
            if ((kind == Kind.EQUIPMENT) != (item != null) || provider != null || version != 0
                || actor != null || instance != null) throw new IllegalArgumentException("equipment/basic source evidence");
        }
    }
    public static AbilitySource equipment(TacticalIntent.Hand hand, TacticalIntent.ItemReference item) {
        return new AbilitySource(Kind.EQUIPMENT, hand, Objects.requireNonNull(item), null, 0, null, null);
    }
    public static AbilitySource basic(TacticalIntent.Hand hand) {
        return new AbilitySource(Kind.BASIC, hand, null, null, 0, null, null);
    }
    public static AbilitySource basic() { return basic(null); }
    public static AbilitySource intrinsic(String provider, int version, UUID actor, UUID instance) {
        return new AbilitySource(Kind.INTRINSIC, null, null, provider, version, actor, instance);
    }
    /** The provider owns grant lifetime; restoring this value does not restore a live grant. */
    public static AbilitySource status(String provider, int version, UUID actor, UUID instance, UUID grant) {
        return new AbilitySource(Kind.STATUS, null, null, provider, version, actor, instance, grant);
    }
}
