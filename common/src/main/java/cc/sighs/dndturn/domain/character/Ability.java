package cc.sighs.dndturn.domain.character;

/** The six D&amp;D ability scores. The id is a stable rule key, not a display name. */
public enum Ability {
    STR("str"),
    DEX("dex"),
    CON("con"),
    INT("int"),
    WIS("wis"),
    CHA("cha");

    private final String id;

    Ability(String id) { this.id = id; }

    public String id() { return id; }

    public static Ability byId(String id) {
        if (id == null) throw new IllegalArgumentException("ability id");
        for (Ability value : values()) if (value.id.equals(id)) return value;
        throw new IllegalArgumentException("unknown ability: " + id);
    }
}
