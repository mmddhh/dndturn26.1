package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.CharacterState;
import cc.sighs.dndturn.domain.character.DndLeveling;
import cc.sighs.dndturn.platform.network.CharacterProtocol;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Builds the bounded sheet JSON consumed by the linked classes page. */
public final class CharacterSheet {
    private CharacterSheet() {}

    public static void send(ServerPlayer player) {
        var service = ServerRuntime.existingEncounter(player.level().getServer());
        if (service == null) throw new IllegalStateException("server runtime is not active");
        CharacterState character = service.actorStates().state(player.getUUID()).persistent().character();
        if (!character.created()) throw new IllegalStateException("character has not been created");
        PacketDistributor.sendToPlayer(player, new CharacterProtocol.Sheet(json(player, character)));
    }

    public static String json(ServerPlayer player, CharacterState character) {
        var progression = character.progression();
        var abilities = character.abilities();
        JsonObject root = new JsonObject();
        root.addProperty("mode", "sheet");
        root.addProperty("class", bare(progression.classId()));
        root.addProperty("subclass", bare(progression.subclassId()));
        root.addProperty("level", progression.level());
        root.addProperty("exp", progression.experience());
        root.addProperty("expLevelStart", DndLeveling.experienceForLevel(progression.level()));
        root.addProperty("expToNext", progression.level() >= DndLeveling.MAX_LEVEL ? 0
            : DndLeveling.experienceForLevel(progression.level() + 1));
        root.addProperty("proficiency", progression.proficiencyBonus());
        root.addProperty("created", true);
        root.addProperty("pendingAsi", progression.pendingAsi());
        root.addProperty("subclassAvailable", CharacterSubclass.available(character));

        JsonObject scores = new JsonObject();
        scores.addProperty("str", abilities.strength());
        scores.addProperty("dex", abilities.dexterity());
        scores.addProperty("con", abilities.constitution());
        scores.addProperty("int", abilities.intelligence());
        scores.addProperty("wis", abilities.wisdom());
        scores.addProperty("cha", abilities.charisma());
        root.add("abilities", scores);
        root.addProperty("weightCapacity", abilities.strength() * 15);
        root.addProperty("ac", PlayerArmorRules.armorClass(player, character));
        root.addProperty("hp", player.getHealth());
        root.addProperty("hpMax", player.getMaxHealth());
        return root.toString();
    }

    private static String bare(String id) {
        if (id == null || id.isBlank()) return "";
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
