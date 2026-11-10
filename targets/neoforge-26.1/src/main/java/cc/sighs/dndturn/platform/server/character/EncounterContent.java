package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.RulesCatalog;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Content revision and participant evidence pinned to one encounter. */
public record EncounterContent(String encounterId, RulesCatalog content,
                               Map<UUID, ParticipantContent> participants, Map<String, RulesCatalog> catalogs) {
    public EncounterContent {
        Objects.requireNonNull(encounterId);
        Objects.requireNonNull(content);
        participants = Map.copyOf(participants);
        catalogs = Map.copyOf(catalogs);
        if (catalogs.size() > 64 || !content.equals(catalogs.get(content.revision())))
            throw new IllegalArgumentException("encounter catalog bindings");
        for (var entry : catalogs.entrySet()) if (!entry.getKey().equals(entry.getValue().revision()))
            throw new IllegalArgumentException("catalog semantic identity mismatch");
        for (var participant : participants.values()) if (!catalogs.containsKey(participant.catalogRevision()))
            throw new IllegalArgumentException("participant catalog missing");
        for (var entry : participants.entrySet()) {
            var id = entry.getKey();
            var value = entry.getValue();
            if (!id.equals(value.actor())) throw new IllegalArgumentException("participant key mismatch");
            var catalog = catalogs.get(value.catalogRevision());
            if (value.character().created()
                    && (!Objects.equals(value.characterClass(), catalog.classes().get(value.character().progression().classId()))
                    || !Objects.equals(value.characterRace(), catalog.races().get(value.character().progression().raceId()))))
                throw new IllegalArgumentException("participant character binding differs from its catalog");
        }
        if (participants.size() > 512) throw new IllegalArgumentException("encounter content bound");
    }

    public EncounterContent(String encounterId, RulesCatalog content, Map<UUID,ParticipantContent> participants) {
        this(encounterId, content, participants, Map.of(content.revision(),content));
    }
    public RulesCatalog contentFor(UUID actor) {
        var participant = participants.get(actor);
        return participant == null ? content : catalogs.get(participant.catalogRevision());
    }

    public ParticipantContent require(UUID actor) {
        ParticipantContent value = participants.get(actor);
        if (value == null) throw new IllegalStateException("participant content missing: " + actor);
        return value;
    }
}
