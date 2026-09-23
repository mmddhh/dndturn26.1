package cc.sighs.dndturn.gametest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
/** Present only on the automated GameTest classpath, never in the published mod. */
@Mod("dndturn")
public final class GameTestBootstrap {
    public GameTestBootstrap(IEventBus bus) { LocalTimeGameTests.TEST_FUNCTIONS.register(bus); }
}
