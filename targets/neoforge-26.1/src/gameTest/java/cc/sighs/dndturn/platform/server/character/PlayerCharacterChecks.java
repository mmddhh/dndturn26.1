package cc.sighs.dndturn.platform.server.character;

import cc.sighs.dndturn.domain.character.AbilityScores;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Platform seam: creator content, actor persistence and native max-health binding agree. */
public final class PlayerCharacterChecks {
    private PlayerCharacterChecks() {}

    public static void run(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var content = CharacterContentService.current();
        helper.assertTrue(content.classes().containsKey("dndturn:fighter")
            && content.races().containsKey("dndturn:human"), "default character content missing");
        var state = PlayerCharacterService.create(player, "fighter", "human",
            new AbilityScores(15, 15, 14, 10, 8, 8));
        helper.assertTrue(state.created() && state.progression().level() == 1, "level-1 character not created");
        helper.assertTrue(state.abilities().strength() == 16 && state.abilities().intelligence() == 11,
            "racial ability boosts were not applied");
        helper.assertTrue(Math.abs(player.getMaxHealth() - 13.0F) < 0.001F, "class hit-die max health not applied");
        var persisted = ServerRuntime.encounters(player.level().getServer()).actorStates()
            .state(player.getUUID()).persistent().character();
        helper.assertTrue(persisted.equals(state), "character state did not reach the actor owner");
        var levelled = PlayerCharacterService.addExperience(player, 300);
        helper.assertTrue(levelled.progression().level() == 2, "XP threshold did not raise the level");
        helper.assertTrue(Math.abs(player.getMaxHealth() - 22.0F) < 0.001F, "level-up max health expectation mismatch");
    }
}
