package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.Ability;
import cc.sighs.dndturn.domain.character.CharacterRules;
import cc.sighs.dndturn.domain.mob.MobDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

/** Backend d20 save for any participant; no client QTE and no automatic retry. */
public final class SaveService {
    private SaveService() {}

    public record Result(int die, int bonus, int dc, int total, boolean success, String source) {}

    public static Result roll(LivingEntity actor, Ability ability, int dc, String source,
                              EncounterContent content, java.util.Random random) {
        if (actor == null || ability == null || random == null) throw new IllegalArgumentException("save source");
        if (dc < 0 || dc > 100) throw new IllegalArgumentException("save dc");
        int bonus = bonus(actor, ability, content);
        int die = 1 + random.nextInt(20);
        SaveRollEvent event = new SaveRollEvent(actor.getUUID(), ability, die, bonus, dc, source);
        NeoForge.EVENT_BUS.post(event);
        int total = event.total();
        boolean success = event.success();
        if (actor instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable(
                success ? "save.dndturn.success" : "save.dndturn.failure",
                total, event.dc()));
        }
        return new Result(event.die(), event.bonus(), event.dc(), total, success, source);
    }

    public static int bonus(LivingEntity actor, Ability ability, EncounterContent content) {
        java.util.Objects.requireNonNull(content, "saving throw content");
        var participant = content.participants().get(actor.getUUID());
        if (participant != null && participant.character().created()) {
            return CharacterRules.savingThrowBonus(participant.characterClass(), participant.character(), ability);
        }
        if (actor instanceof ServerPlayer player) {
            var service = cc.sighs.dndturn.platform.server.runtime.ServerRuntime.existingEncounter(
                player.level().getServer());
            if (service == null) throw new IllegalStateException("saving throw actor owner unavailable");
            var character = service.actorStates().state(player.getUUID()).persistent().character();
            return character.created() ? CharacterRules.savingThrowBonus(
                content.content().requireClass(character.progression().classId()), character, ability) : 0;
        }
        String id = participant == null ? BuiltInRegistries.ENTITY_TYPE.getKey(actor.getType()).toString() : participant.definitionId();
        MobDefinition definition = content.contentFor(actor.getUUID()).mobOrFallback(id);
        Integer explicit = definition.saveBonuses().get(ability);
        return explicit != null ? explicit : definition.abilities().modifier(ability);
    }
}
