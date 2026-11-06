package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.domain.character.CharacterCreation;
import cc.sighs.dndturn.domain.character.CharacterState;
import cc.sighs.dndturn.domain.character.DndLeveling;
import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Server-side player character creation. UI/network callers must still validate through this path. */
public final class PlayerCharacterService {
    private PlayerCharacterService() {}

    public static CharacterState create(ServerPlayer player, String classId, String raceId,
                                        AbilityScores scores) {
        if (player == null || player.isDeadOrDying()) throw new IllegalArgumentException("player unavailable");
        if (inEncounter(player)) throw new IllegalStateException("character cannot be created during an encounter");
        if (CharacterContentService.lastFailure() != null && !CharacterContentService.lastFailure().isBlank())
            throw new IllegalStateException("character content unavailable: " + CharacterContentService.lastFailure());
        var content = CharacterContentService.current();
        var characterClass = content.requireClass(namespaced(classId));
        var race = content.requireRace(namespaced(raceId));
        var state = CharacterCreation.create(characterClass, race, scores, null);
        ActorStateAuthority actors = ServerRuntime.encounters(player.level().getServer()).actorStates();
        var current = actors.state(player.getUUID());
        if (current.persistent().character().created())
            throw new IllegalStateException("character already created");
        var command = new ActorStates.Command(UUID.randomUUID(), player.getUUID(), current.revision(),
            List.of(new ActorStates.Character(state)), null);
        actors.command(new LiveActorContext(player), command);
        applyMaxHealth(player, CharacterCreation.maxHp(characterClass, state));
        return state;
    }

    public static CharacterState createFromScreen(ServerPlayer player, String classId, String raceId,
                                                  String subraceId, String subclassId, AbilityScores bought,
                                                  java.util.Map<Ability, Integer> racialBonuses) {
        if (player == null || player.isDeadOrDying()) throw new IllegalArgumentException("player unavailable");
        if (inEncounter(player)) throw new IllegalStateException("character cannot be created during an encounter");
        if (CharacterContentService.lastFailure() != null && !CharacterContentService.lastFailure().isBlank())
            throw new IllegalStateException("character content unavailable: " + CharacterContentService.lastFailure());
        var content = CharacterContentService.current();
        var characterClass = content.requireClass(namespaced(classId));
        var race = content.requireRace(namespaced(raceId));
        var state = CharacterCreation.createFromScreen(characterClass, race, bought, racialBonuses,
            subraceId == null || subraceId.isBlank() ? null : namespacedContent(subraceId),
            subclassId == null || subclassId.isBlank() ? null : namespacedContent(subclassId));
        ActorStateAuthority actors = ServerRuntime.encounters(player.level().getServer()).actorStates();
        var current = actors.state(player.getUUID());
        if (current.persistent().character().created())
            throw new IllegalStateException("character already created");
        var command = new ActorStates.Command(UUID.randomUUID(), player.getUUID(), current.revision(),
            List.of(new ActorStates.Character(state)), null);
        actors.command(new LiveActorContext(player), command);
        applyMaxHealth(player, CharacterCreation.maxHp(characterClass, state));
        return state;
    }

    /** Adds XP and applies all reached levels/ASI slots. Refuses inside an encounter so content stays pinned. */
    public static CharacterState addExperience(ServerPlayer player, int amount) {
        if (player == null || player.isDeadOrDying()) throw new IllegalArgumentException("player unavailable");
        if (amount <= 0) throw new IllegalArgumentException("experience amount");
        if (inEncounter(player)) throw new IllegalStateException("experience cannot change during an encounter");
        ActorStateAuthority actors = ServerRuntime.encounters(player.level().getServer()).actorStates();
        var current = actors.state(player.getUUID());
        var character = current.persistent().character();
        if (!character.created()) throw new IllegalStateException("character has not been created");
        var characterClass = CharacterContentService.current().requireClass(character.progression().classId());
        var progression = character.progression();
        int oldLevel = progression.level();
        int experience = DndLeveling.addExperience(progression.experience(), amount);
        int newLevel = DndLeveling.levelForExperience(experience);
        progression = progression.withExperience(experience).withLevel(newLevel);
        for (int level = oldLevel + 1; level <= newLevel; level++)
            if (asiLevels(progression.classId()).contains(level)) progression = progression.grantAsiLevel(level);
        var next = new CharacterState(progression, character.abilities());
        var command = new ActorStates.Command(UUID.randomUUID(), player.getUUID(), current.revision(),
            List.of(new ActorStates.Character(next)), null);
        actors.command(new LiveActorContext(player), command);
        applyMaxHealth(player, CharacterCreation.maxHp(characterClass, next));
        return next;
    }

    public static void refreshMaxHealth(ServerPlayer player, CharacterState character) {
        var characterClass = CharacterContentService.current().requireClass(character.progression().classId());
        applyMaxHealth(player, CharacterCreation.maxHp(characterClass, character));
    }

    private static boolean inEncounter(ServerPlayer player) {
        return ServerRuntime.encounters(player.level().getServer()).encounterOf(player.getUUID()) != null;
    }

    private static Set<Integer> asiLevels(String classId) {
        return switch (classId) {
            case "dndturn:fighter" -> Set.of(4, 6, 8, 12, 14, 16, 19);
            case "dndturn:rogue" -> Set.of(4, 8, 10, 12, 16, 19);
            default -> Set.of(4, 8, 12, 16, 19);
        };
    }

    private static void applyMaxHealth(ServerPlayer player, int maxHp) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) throw new IllegalStateException("player lacks max health attribute");
        attribute.setBaseValue(maxHp);
        player.setHealth(Math.min(maxHp, player.getMaxHealth()));
    }

    private static String namespaced(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("content id");
        return value.indexOf(':') >= 0 ? value : "dndturn:" + value;
    }

    private static String namespacedContent(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("content id");
        return value.indexOf(':') >= 0 ? value.replace("dndclasses:", "dndturn:") : "dndturn:" + value;
    }
}
