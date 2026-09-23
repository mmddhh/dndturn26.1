package cc.sighs.dndturn.gametest;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.platform.server.encounter.EncounterRuntime;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.Random;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Test-classpath-only RNG control. No production flags, authorization changes, or alternate sessions. */
final class SeededGameTests {
    private SeededGameTests() {}
    static void register() {
        String configured = System.getenv("DNDTURN_TEST_SEED");
        if (configured == null) return;
        long seed = Long.parseLong(configured);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            var server = event.getServer();
            if (!(server instanceof net.minecraft.gametest.framework.GameTestServer))
                throw new IllegalStateException("seed fixture requires GameTestServer");
            var service = ServerRuntime.encounters(server);
            // Access exact fixed-version fields only in this fixture, instead of adding production test setters.
            var engine = field(service, EncounterRuntime.class, "engine", EncounterAuthority.class);
            field(engine, EncounterAuthority.class, "random", Random.class).setSeed(seed);
            field(service, EncounterRuntime.class, "attackRandom", Random.class).setSeed(seed ^ 0x5deece66dL);
            for (var level : server.getAllLevels()) level.getRandom().setSeed(seed);
            System.out.println("DNDTURN_TEST_SEED=" + seed + " (world, initiative, attack, living entity RNGs; UUIDs remain unique)");
        });
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living)
                living.getRandom().setSeed(seed ^ Integer.toUnsignedLong(living.getId()));
        });
    }
    private static <T> T field(Object owner, Class<?> type, String name, Class<T> valueType) {
        try {
            var field = type.getDeclaredField(name);
            if (field.getType() != valueType) throw new IllegalStateException("seed fixture field type changed: " + name);
            field.setAccessible(true);
            return valueType.cast(field.get(owner));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("seed fixture unavailable", failure); }
    }
}
