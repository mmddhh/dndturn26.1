package cc.sighs.dndturn.gametest;

import cc.sighs.dndturn.compat.ArcheryExtension;
import cc.sighs.dndturn.platform.server.actor.EffectReactionChecks;
import cc.sighs.dndturn.platform.server.builtin.creeper.CreeperEffectChecks;
import cc.sighs.dndturn.platform.server.encounter.ActorHistoryChecks;
import cc.sighs.dndturn.platform.server.encounter.DefaultInteractionChecks;
import cc.sighs.dndturn.platform.server.encounter.GateMeleeChecks;
import cc.sighs.dndturn.platform.server.encounter.MeleeAdapterChecks;
import cc.sighs.dndturn.platform.server.encounter.MobRangedApproachChecks;
import cc.sighs.dndturn.platform.server.encounter.MobStandardizationChecks;
import cc.sighs.dndturn.platform.server.encounter.TacticalEffectChecks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Present only on the automated GameTest classpath, never in the published mod. */
@Mod("dndturn")
public final class GameTestBootstrap {
    public GameTestBootstrap(IEventBus bus) {
        SeededGameTests.register();
        ArcheryExtension.register();
        LocalTimeGameTests.TEST_FUNCTIONS.register("mob_standardization_automatic", () -> MobStandardizationChecks::automatic);
        LocalTimeGameTests.TEST_FUNCTIONS.register("mob_standardization_miss", () -> MobStandardizationChecks::miss);
        LocalTimeGameTests.TEST_FUNCTIONS.register("mob_standardization_approach", () -> MobRangedApproachChecks::run);
        LocalTimeGameTests.TEST_FUNCTIONS.register("mob_standardization_revoked", () -> MobRangedApproachChecks::revoked);
        LocalTimeGameTests.TEST_FUNCTIONS.register("mob_standardization_external", () -> MobStandardizationChecks::external);
        LocalTimeGameTests.TEST_FUNCTIONS.register("mob_standardization", () -> MobStandardizationChecks::run);
        LocalTimeGameTests.TEST_FUNCTIONS.register("mob_standardization_held", () -> MobStandardizationChecks::held);
        LocalTimeGameTests.TEST_FUNCTIONS.register("gate_melee", () -> GateMeleeChecks::run);
        LocalTimeGameTests.TEST_FUNCTIONS.register("gate_cow", () -> GateMeleeChecks::cow);
        LocalTimeGameTests.TEST_FUNCTIONS.register("default_interactions", () -> DefaultInteractionChecks::run);
        LocalTimeGameTests.TEST_FUNCTIONS.register("actor_history", () -> ActorHistoryChecks::run);
        LocalTimeGameTests.TEST_FUNCTIONS.register("gate_melee_approach", () -> GateMeleeChecks::approach);
        MeleeAdapterChecks.register();
        TacticalEffectChecks.register();
        EffectReactionChecks.register();
        cc.sighs.dndturn.compat.CombatStructureChecks.register();
        LocalTimeGameTests.TEST_FUNCTIONS.register("combat_structure", () -> cc.sighs.dndturn.compat.CombatStructureChecks::run);
        LocalTimeGameTests.TEST_FUNCTIONS.register("creeper_effect", () -> CreeperEffectChecks::run);
        LocalTimeGameTests.TEST_FUNCTIONS.register(bus);
    }
}
