package cc.sighs.dndturn.domain.mob;

/** Damage keys used by the monster design document. */
public enum DamageType {
    BLUDGEONING("bludgeoning"),
    PIERCING("piercing"),
    SLASHING("slashing"),
    ACID("acid"),
    COLD("cold"),
    FIRE("fire"),
    FORCE("force"),
    LIGHTNING("lightning"),
    NECROTIC("necrotic"),
    POISON("poison"),
    PSYCHIC("psychic"),
    RADIANT("radiant"),
    THUNDER("thunder");

    private final String id;

    DamageType(String id) { this.id = id; }
    public String id() { return id; }

    public static DamageType byId(String id) {
        for (DamageType value : values()) if (value.id.equals(id)) return value;
        throw new IllegalArgumentException("unknown damage type: " + id);
    }
}
