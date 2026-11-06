package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.ContentSnapshot;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Content revision and participant evidence pinned to one encounter. */
public record EncounterContent(String encounterId, ContentSnapshot content,
                               Map<UUID, ParticipantContent> participants) {
    public EncounterContent {
        Objects.requireNonNull(encounterId);
        Objects.requireNonNull(content);
        participants = Map.copyOf(participants);
        participants.forEach((id, value) -> {
            if (!id.equals(value.actor())) throw new IllegalArgumentException("participant key mismatch");
        });
        if (participants.size() > 512) throw new IllegalArgumentException("encounter content bound");
    }

    public ParticipantContent require(UUID actor) {
        ParticipantContent value = participants.get(actor);
        if (value == null) throw new IllegalStateException("participant content missing: " + actor);
        return value;
    }
}
