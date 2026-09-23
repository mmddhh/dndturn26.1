package cc.sighs.dndturn.combat;

/** Stable start receipt states, independent of transport and nullable encounter snapshots. */
public enum StartDisposition {
    NONE(0), WAITING(1), STARTED(2), FAILED(3), CLOSED(4);
    private final int code;
    StartDisposition(int code) { this.code = code; }
    public int code() { return code; }
    public static StartDisposition fromCode(int code) {
        for (StartDisposition value : values()) if (value.code == code) return value;
        throw new IllegalArgumentException("unknown start disposition");
    }
}
