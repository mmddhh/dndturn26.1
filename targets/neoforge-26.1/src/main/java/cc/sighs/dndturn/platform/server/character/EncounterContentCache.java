package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.actor.MobAttackProfiles;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

/** Server-thread owner of per-encounter participant content. Never re-reads current JSON during a fight. */
public final class EncounterContentCache {
    private static final EncounterContentCache SERVER = new EncounterContentCache();
    private final Map<UUID, EncounterContent> byEncounter = new HashMap<>();

    private EncounterContentCache() {}

    public static EncounterContentCache server() { return SERVER; }

    public void capture(UUID encounterId, Entity entity, ActorStateAuthority actors) {
        Objects.requireNonNull(encounterId);
        Objects.requireNonNull(entity);
        Objects.requireNonNull(actors);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living))
            throw new IllegalArgumentException("encounter content requires a living participant");
        EncounterContent current = byEncounter.get(encounterId);
        if (current == null) {
            current = new EncounterContent(encounterId.toString(), CharacterContentService.current(), Map.of());
            byEncounter.put(encounterId, current);
        }
        UUID actor = entity.getUUID();
        if (current.participants().containsKey(actor)) return;
        var state = actors.state(actor);
        var character = state.persistent().character();
        var definition = ActorDefinitions.capture(new LiveActorContext(living));
        var content = current.content();
        var characterClass = character.created() ? content.classes().get(character.progression().classId()) : null;
        var characterRace = character.created() ? content.races().get(character.progression().raceId()) : null;
        if (character.created() && (characterClass == null || characterRace == null))
            throw new IllegalStateException("character content is not present in the encounter snapshot");
        var participant = new ParticipantContent(actor, definition.id(), definition.version(), character,
                characterClass, characterRace, MobAttackProfiles.of(entity));
        var participants = new LinkedHashMap<>(current.participants());
        participants.put(actor, participant);
        byEncounter.put(encounterId, new EncounterContent(encounterId.toString(), content, participants));
    }

    public EncounterContent find(UUID encounterId) { return byEncounter.get(encounterId); }

    public cc.sighs.dndturn.domain.resolution.AttackProfile attackProfile(UUID encounterId, UUID actor) {
        EncounterContent content = byEncounter.get(encounterId);
        if (content == null) return null;
        ParticipantContent participant = content.participants().get(actor);
        return participant == null ? null : participant.attackProfile();
    }

    public Map<UUID, EncounterContent> snapshot() { return Map.copyOf(byEncounter); }

    public void restore(Map<UUID, EncounterContent> values) {
        byEncounter.clear();
        if (values != null) byEncounter.putAll(values);
    }

    public void retain(Set<UUID> encounters) {
        byEncounter.keySet().retainAll(encounters);
    }

    public void clear() { byEncounter.clear(); }
}
