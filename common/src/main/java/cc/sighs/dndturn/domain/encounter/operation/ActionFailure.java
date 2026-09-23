package cc.sighs.dndturn.domain.encounter.operation;

/** Machine-readable rejection; display wording is never a control protocol. */
public final class ActionFailure extends IllegalStateException {
    public enum Code { NONE, REJECTED, STALE_ENCOUNTER, SOURCE_INVALID, ABILITY_UNAVAILABLE, PAYLOAD_CONFLICT, EXPIRED_SESSION, UNKNOWN_EFFECT, CONTROL_FAULT, EVALUATION_DEFERRED }
    public enum Retry { NONE, REFRESH_AND_REPROPOSE, QUERY_OPERATION, RECOVERY_REVIEW }
    private final Code code;
    private final Retry retry;

    public ActionFailure(Code code, Retry retry, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
        this.retry = java.util.Objects.requireNonNull(retry);
    }
    public Code code() { return code; }
    public Retry retry() { return retry; }
    public record Details(Code code, Retry retry) {
        public Details { java.util.Objects.requireNonNull(code); java.util.Objects.requireNonNull(retry); }
        public static final Details NONE = new Details(Code.NONE, Retry.NONE);
        public static final Details REJECTED = new Details(Code.REJECTED, Retry.NONE);
        public static final Details UNKNOWN = new Details(Code.UNKNOWN_EFFECT, Retry.RECOVERY_REVIEW);
    }
    public Details details() { return new Details(code, retry); }
    public static Details classify(RuntimeException error) {
        return error instanceof ActionFailure failure ? failure.details() : Details.REJECTED;
    }
    public static ActionFailure source(String message) {
        return new ActionFailure(Code.SOURCE_INVALID, Retry.REFRESH_AND_REPROPOSE, message);
    }
    public static ActionFailure staleEncounter() {
        return new ActionFailure(Code.STALE_ENCOUNTER, Retry.REFRESH_AND_REPROPOSE, "stale encounter version");
    }
}
