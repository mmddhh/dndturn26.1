package cc.sighs.dndturn.domain.encounter;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** A turn identity is not necessarily an entity. Environment IDs never grant entity permissions. */
public record TurnParticipant(Kind kind, UUID id) {
    public enum Kind { ENTITY, ENVIRONMENT }
    public TurnParticipant { Objects.requireNonNull(kind); Objects.requireNonNull(id); }
    public static TurnParticipant environment(UUID encounter) {
        Objects.requireNonNull(encounter);
        return new TurnParticipant(Kind.ENVIRONMENT, UUID.nameUUIDFromBytes(
            ("dndturn:environment/" + encounter).getBytes(StandardCharsets.UTF_8)));
    }
    public static TurnParticipant entity(UUID id) { return new TurnParticipant(Kind.ENTITY, id); }
    public boolean environment() { return kind == Kind.ENVIRONMENT; }
}
