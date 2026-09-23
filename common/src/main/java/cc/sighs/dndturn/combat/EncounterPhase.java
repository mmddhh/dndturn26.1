package cc.sighs.dndturn.combat;

/** Stable domain phase shared by rules, immutable snapshots and client projections. */
public enum EncounterPhase {
    CANDIDATE(0), ACTIVE(1), ENVIRONMENT(2), ENDED(3);
    private final int code;
    EncounterPhase(int code) { this.code = code; }
    public int code() { return code; }
    public static EncounterPhase fromCode(int code) {
        for (EncounterPhase value : values()) if (value.code == code) return value;
        throw new IllegalArgumentException("unknown encounter phase");
    }
}
