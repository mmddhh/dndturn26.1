package cc.sighs.dndturn.platform.server.character;

import java.util.Collection;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.character.RulesCatalog;
import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.actor.MobAttackProfiles;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

/** Server-thread owner of per-encounter participant content. Never re-reads current JSON during a fight. */
public final class EncounterContentCache {
    private final CharacterContentService catalogs;
    private final Map<UUID, EncounterContent> byEncounter = new HashMap<>();

    public EncounterContentCache(CharacterContentService catalogs) { this.catalogs = Objects.requireNonNull(catalogs); }

    public EncounterContent prepareStart(UUID encounter, Collection<? extends Entity> entities, ActorStateAuthority actors) {
        var catalog = catalogs.current();
        var participants = new LinkedHashMap<UUID,ParticipantContent>();
        for (Entity entity : entities) {
            var value = participant(entity, actors, catalog);
            if (participants.putIfAbsent(value.actor(), value) != null)
                throw new IllegalArgumentException("duplicate starting member");
        }
        return new EncounterContent(encounter.toString(), catalog, participants);
    }

    private ParticipantContent participant(Entity entity, ActorStateAuthority actors,
                                          RulesCatalog content) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living))
            throw new IllegalArgumentException("encounter content requires a living participant");
        UUID actor = entity.getUUID();
        var state = actors.state(actor);
        var character = state.persistent().character();
        var definition = ActorDefinitions.capture(new LiveActorContext(living));
        var characterClass = character.created() ? content.classes().get(character.progression().classId()) : null;
        var characterRace = character.created() ? content.races().get(character.progression().raceId()) : null;
        if (character.created() && (characterClass == null || characterRace == null))
            throw new IllegalStateException("character content is not present in the encounter snapshot");
        return new ParticipantContent(actor, definition.id(), definition.version(), character,
                characterClass, characterRace, MobAttackProfiles.of(entity, content), content.revision());
    }

    public boolean join(EncounterAuthority engine, UUID encounter,
                        Entity entity, int budget, ActorStateAuthority actors) {
        if (engine.encounterOf(entity.getUUID()) != null) return false;
        var current = Objects.requireNonNull(find(encounter));
        var participants = new LinkedHashMap<>(current.participants());
        var value = participant(entity, actors, current.content());
        participants.put(value.actor(), value);
        var next = new EncounterContent(current.encounterId(), current.content(), participants, current.catalogs());
        if (!engine.join(encounter, entity.getUUID(), budget)) return false;
        install(next);
        return true;
    }

    /** Prepare bounded, immutable owner transfer before the Encounter commits its merge. */
    public EncounterContent prepareMerge(EncounterAuthority engine, UUID primary, Collection<UUID> sources) {
        var base = Objects.requireNonNull(byEncounter.get(primary), "primary content");
        var participants = new LinkedHashMap<>(base.participants());
        var catalogs = new LinkedHashMap<>(base.catalogs());
        for (UUID source : sources) {
            var captured = Objects.requireNonNull(byEncounter.get(source), "merge source content");
            if (!captured.participants().keySet().equals(engine.stateView(source).members().keySet()))
                throw new IllegalStateException("merge participant content differs from membership");
            captured.catalogs().forEach((revision, value) -> {
                var prior = catalogs.putIfAbsent(revision, value);
                if (prior != null && !prior.equals(value)) throw new IllegalStateException("conflicting catalog identity");
            });
            captured.participants().forEach((actor, value) -> {
                var prior = participants.putIfAbsent(actor, value);
                if (prior != null && !prior.equals(value)) throw new IllegalStateException("conflicting participant content");
            });
        }
        return new EncounterContent(primary.toString(), base.content(), participants, catalogs);
    }
    public void install(EncounterContent prepared) {
        byEncounter.put(UUID.fromString(prepared.encounterId()), prepared);
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

    public void remove(UUID encounter) { byEncounter.remove(encounter); }

    public void leave(UUID encounter, UUID actor) {
        var current = Objects.requireNonNull(find(encounter));
        var participants = new LinkedHashMap<>(current.participants());
        participants.remove(actor);
        install(new EncounterContent(current.encounterId(), current.content(), participants, current.catalogs()));
    }

}
