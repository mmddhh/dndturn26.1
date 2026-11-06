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
    private static final java.util.Random RANDOM = new java.util.Random();

    private SaveService() {}

    public record Result(int die, int bonus, int dc, int total, boolean success, String source) {}

    public static Result roll(LivingEntity actor, Ability ability, int dc, String source) {
        return roll(actor, ability, dc, source, RANDOM);
    }

    public static Result roll(LivingEntity actor, Ability ability, int dc, String source, java.util.Random random) {
        if (actor == null || ability == null || random == null) throw new IllegalArgumentException("save source");
        if (dc < 0 || dc > 100) throw new IllegalArgumentException("save dc");
        int bonus = bonus(actor, ability);
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

    public static int bonus(LivingEntity actor, Ability ability) {
        if (actor instanceof ServerPlayer player) {
            var service = cc.sighs.dndturn.platform.server.runtime.ServerRuntime.existingEncounter(
                player.level().getServer());
            if (service != null) {
                var state = service.actorStates().state(player.getUUID()).persistent().character();
                if (state.created()) {
                    var characterClass = CharacterContentService.current().classes().get(
                        state.progression().classId());
                    if (characterClass != null) return CharacterRules.savingThrowBonus(characterClass, state, ability);
                }
            }
            return 0;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(actor.getType()).toString();
        MobDefinition definition = CharacterContentService.current().mobOrFallback(id);
        Integer explicit = definition.saveBonuses().get(ability);
        return explicit != null ? explicit : definition.abilities().modifier(ability);
    }
}
