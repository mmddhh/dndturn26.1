package cc.sighs.dndturn.platform.server.character;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.domain.character.CharacterCreation;
import cc.sighs.dndturn.domain.character.CharacterState;
import cc.sighs.dndturn.domain.character.DndLeveling;
import cc.sighs.dndturn.platform.server.actor.ActorStateAuthority;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Server-side player character creation. UI/network callers must still validate through this path. */
public final class PlayerCharacterService {
    private PlayerCharacterService() {}

    public static CharacterState create(ServerPlayer player, String classId, String raceId,
                                        AbilityScores scores) {
        var runtime = ServerRuntime.encounters(player.level().getServer());
        var catalogs = ServerRuntime.content(player.level().getServer());
        var selection = new ActorStates.Creation(catalogs.current().revision(), namespaced(classId),
            namespaced(raceId), null, null, scores, List.of(), null);
        if (submit(player, UUID.randomUUID(), selection).unknown())
            throw new IllegalStateException("character created, native health update needs reconciliation");
        return runtime.actorStates().state(player.getUUID()).persistent().character();
    }

    public static ActorStates.CreationOutcome submit(ServerPlayer player, UUID operation, ActorStates.Creation selection) {
        var actors = ServerRuntime.encounters(player.level().getServer()).actorStates();
        var prior = actors.receipt(operation);
        if (prior != null) {
            if (!prior.command().actor().equals(player.getUUID()) || prior.command().changes().size() != 1
                || !(prior.command().changes().getFirst() instanceof ActorStates.Character change)
                || !selection.equals(change.creation())) throw new IllegalStateException("operation payload mismatch");
            var outcome = actors.creation(operation);
            return outcome == null ? new ActorStates.CreationOutcome(null, List.of(), true) : outcome;
        }
        if (player.isDeadOrDying() || inEncounter(player)) throw new IllegalStateException("player unavailable");
        var catalog = ServerRuntime.content(player.level().getServer()).current();
        if (!catalog.revision().equals(selection.revision())) throw new IllegalStateException("catalog changed");
        var definition = catalog.requireClass(selection.characterClass());
        var race = catalog.requireRace(selection.race());
        var current = actors.state(player.getUUID());
        if (current.persistent().character().created()) throw new IllegalStateException("character already created");
        boolean defaults = selection.kit() == null;
        if (defaults && (selection.subrace() != null || selection.subclass() != null || !selection.targets().isEmpty()))
            throw new IllegalArgumentException("invalid default allocation");
        var state = defaults ? CharacterCreation.create(definition, race, selection.bought(), null)
            : CharacterCreation.createFromScreen(definition, race, selection.bought(), selection.targets(),
                selection.subrace(), selection.subclass());
        var stacks = defaults ? List.<ItemStack>of() : CharacterEquipment.prepare(definition, selection.kit());
        var hp = CharacterCreation.maxHp(definition, state);
        if (player.getAttribute(Attributes.MAX_HEALTH) == null) throw new IllegalStateException("missing health attribute");
        var observations = new ArrayList<ActorStates.GrantedStack>();
        Double maxHealth = null;
        boolean unknown = false;
        try {
            actors.command(new LiveActorContext(player), new ActorStates.Command(operation, player.getUUID(),
                current.revision(), List.of(new ActorStates.Character(state, selection))));
            applyMaxHealth(player, hp);
            maxHealth = player.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
            CharacterEquipment.grant(player, stacks, observations);
        } catch (RuntimeException failure) {
            if (actors.receipt(operation) == null) throw failure;
            unknown = true;
        }
        var outcome = new ActorStates.CreationOutcome(maxHealth, observations, unknown);
        actors.finishCreation(operation, outcome);
        return outcome;
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
        var characterClass = ServerRuntime.content(player.level().getServer()).current().requireClass(character.progression().classId());
        var progression = character.progression();
        int oldLevel = progression.level();
        int experience = DndLeveling.addExperience(progression.experience(), amount);
        int newLevel = DndLeveling.levelForExperience(experience);
        progression = progression.withExperience(experience).withLevel(newLevel);
        for (int level = oldLevel + 1; level <= newLevel; level++)
            if (characterClass.asiLevels().contains(level)) progression = progression.grantAsiLevel(level);
        var next = new CharacterState(progression, character.abilities());
        var command = new ActorStates.Command(UUID.randomUUID(), player.getUUID(), current.revision(),
            List.of(new ActorStates.Character(next)), null);
        actors.command(new LiveActorContext(player), command);
        applyMaxHealth(player, CharacterCreation.maxHp(characterClass, next));
        return next;
    }

    public static void refreshMaxHealth(ServerPlayer player, CharacterState character) {
        var characterClass = ServerRuntime.content(player.level().getServer()).current().requireClass(character.progression().classId());
        applyMaxHealth(player, CharacterCreation.maxHp(characterClass, character));
    }

    private static boolean inEncounter(ServerPlayer player) {
        return ServerRuntime.encounters(player.level().getServer()).encounterOf(player.getUUID()) != null;
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

}
