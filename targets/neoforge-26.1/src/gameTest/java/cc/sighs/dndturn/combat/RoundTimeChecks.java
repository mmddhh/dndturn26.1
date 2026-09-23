package cc.sighs.dndturn.combat;

import java.nio.file.Files;
import java.util.Properties;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;

public final class RoundTimeChecks {
    public static void run(GameTestHelper helper) {
        try {
            var directory = Files.createTempDirectory("dndturn-round-config-");
            var file = directory.resolve("world.properties");
            try {
                Files.writeString(file, "movementTicks=28\nenvironmentTicks=20\nregionRadius=7\ncustom=value\n");
                var first = ServerCombatConfig.load(file);
                helper.assertTrue(first.roundTicks() == 30 && first.regionRadius() == 7, "old config did not adopt unified default");
                var values = new Properties();
                try (var input = Files.newInputStream(file)) { values.load(input); }
                helper.assertTrue(values.getProperty("movementTicks").equals("28")
                    && values.getProperty("environmentTicks").equals("20")
                    && values.getProperty("custom").equals("value") && values.getProperty("roundTicks").equals("30"),
                    "config conversion lost old values or unrelated settings");
                Files.writeString(file, Files.readString(file).replace("roundTicks=30", "roundTicks=17"));
                helper.assertTrue(ServerCombatConfig.load(file).roundTicks() == 17, "nondefault time ignored");
                Files.writeString(file, "roundTicks=0\ncustom=unchanged\n");
                var before = Files.readString(file);
                boolean rejected = false;
                try { ServerCombatConfig.load(file); } catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected && Files.readString(file).equals(before), "invalid config was overwritten");
            } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
            var registry = new MeleeAdapters();
            var zombie = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
            var base = new MeleeAdapters.Attacker("test:base", 1, e -> true);
            var specific = new MeleeAdapters.Attacker("test:specific", 1, e -> true, e -> 7, 10, Set.of("test:base"));
            registry.register(specific); registry.register(base); registry.freeze();
            helper.assertTrue(registry.attacker(zombie) == specific, "explicit coverage depends on registration order");
            var ambiguous = new MeleeAdapters();
            ambiguous.register(base); ambiguous.register(new MeleeAdapters.Attacker("test:other", 1, e -> true));
            boolean rejected = false;
            try { ambiguous.attacker(zombie); } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "equal priority ambiguity accepted");
            var facts = new PresentationState.Facts(null, java.util.UUID.randomUUID(), "CANDIDATE", true,
                PresentationState.Use.STOPPED, 30);
            var packet = new CombatNetwork.EntitySimulation(java.util.UUID.randomUUID(), 1, java.util.UUID.randomUUID(), 1,
                java.util.UUID.randomUUID(), "minecraft:overworld", true, facts, null);
            var buffer = io.netty.buffer.Unpooled.buffer();
            try {
                CombatNetwork.EntitySimulation.STREAM_CODEC.encode(buffer, packet);
                helper.assertTrue(CombatNetwork.EntitySimulation.STREAM_CODEC.decode(buffer).equals(packet), "committed phase evidence wire mismatch");
            } finally { buffer.release(); }
            helper.succeed();
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
}
