package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.CharacterClass;
import cc.sighs.dndturn.domain.character.CharacterRace;
import cc.sighs.dndturn.domain.character.CharacterState;
import cc.sighs.dndturn.domain.character.RulesCatalog;
import cc.sighs.dndturn.domain.resolution.AttackProfile;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One participant's immutable content evidence, captured when the encounter starts. */
public record ParticipantContent(UUID actor, String definitionId, int definitionVersion,
                                 CharacterState character, CharacterClass characterClass,
                                 CharacterRace characterRace, AttackProfile attackProfile) {
    public ParticipantContent {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(definitionId);
        Objects.requireNonNull(character);
        if (definitionId.isBlank() || definitionVersion < 1)
            throw new IllegalArgumentException("participant definition evidence");
        if (character.created() != (characterClass != null) || character.created() != (characterRace != null))
            throw new IllegalArgumentException("participant character content mismatch");
    }
}
