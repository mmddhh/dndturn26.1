package cc.sighs.dndturn.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-level boundaries also cover targets that are not on common's Java 17 classpath. */
class PackageArchitectureTest {
    private static final Path ROOT = Path.of(System.getProperty("dndturn.repositoryRoot"));
    private static final String BASE = "cc.sighs.dndturn.";
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package ([\\w.]+);");
    private static final Pattern REFERENCE = Pattern.compile("cc\\.sighs\\.dndturn\\.(?:[a-z][\\w]*\\.)*[A-Z]\\w*");
    private static List<Path> javaFiles(Path root) throws IOException {
        try (var files = Files.walk(root)) { return files.filter(p -> p.toString().endsWith(".java")).toList(); }
    }
    private static String source(Path path) throws IOException { return Files.readString(path); }
    private static String code(Path path) throws IOException {
        return source(path).replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "");
    }
    private static String packageOf(Path path) throws IOException {
        var match = PACKAGE.matcher(source(path));
        assertTrue(match.find(), path + " has no package");
        return match.group(1);
    }
    private static Set<String> references(Path path) throws IOException {
        var result = new HashSet<String>();
        var matcher = REFERENCE.matcher(code(path));
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
    private static Path common() { return ROOT.resolve("common/src/main/java"); }
    private static Path platform() { return ROOT.resolve("targets/neoforge-26.1/src/main/java"); }

    @Test void commonHasOnlyDomainAndApplicationAndNoPlatformDependencies() throws Exception {
        for (var path : javaFiles(common())) {
            String pkg = packageOf(path), text = code(path);
            assertTrue(pkg.startsWith(BASE + "domain.") || pkg.startsWith(BASE + "application."), path.toString());
            for (String forbidden : List.of("net.minecraft.", "net.neoforged.", "net.minecraftforge.", "net.fabricmc.",
                    "org.spongepowered.asm.mixin", BASE + "platform."))
                assertFalse(text.contains(forbidden), path + " depends on " + forbidden);
            if (pkg.startsWith(BASE + "domain."))
                assertFalse(text.contains(BASE + "application."), path + " reverses domain/application dependency");
        }
    }

    @Test void packagesMatchPathsAndCommonDoesNotSharePackagesWithTargets() throws Exception {
        var shared = new HashSet<String>();
        for (var path : javaFiles(common())) shared.add(packageOf(path));
        try (var targets = Files.list(ROOT.resolve("targets"))) {
            for (var target : targets.filter(Files::isDirectory).toList()) {
                Path sources = target.resolve("src/main/java");
                if (!Files.isDirectory(sources)) continue;
                for (var path : javaFiles(sources)) {
                    var pkg = packageOf(path);
                    assertFalse(shared.contains(pkg), "split package: " + pkg + " in " + target);
                    assertEquals(pkg.replace('.', '/'), sources.relativize(path.getParent()).toString().replace('\\', '/'), path.toString());
                }
            }
        }
        for (var path : javaFiles(common()))
            assertEquals(packageOf(path).replace('.', '/'), common().relativize(path.getParent()).toString().replace('\\', '/'));
    }

    @Test void clientAndSharedPlatformCannotDependOnServerImplementation() throws Exception {
        for (var path : javaFiles(platform())) {
            var pkg = packageOf(path);
            if (pkg.startsWith(BASE + "platform.client.") || pkg.equals(BASE + "platform.observation")
                    || pkg.equals(BASE + "platform.spatial") || pkg.equals(BASE + "platform.projection")
                    || pkg.equals(BASE + "platform.network"))
                assertTrue(references(path).stream().noneMatch(ref -> ref.startsWith(BASE + "platform.server.")), path.toString());
        }
    }

    @Test void checkpointWriterDoesNotReachBackIntoLiveRuntimeOwners() throws Exception {
        Path writer = platform().resolve("cc/sighs/dndturn/platform/server/persistence/EncounterPersistence.java");
        for (var ref : references(writer)) {
            if (!ref.startsWith(BASE + "platform.")) continue;
            assertTrue(ref.startsWith(BASE + "platform.server.persistence.")
                || ref.equals(BASE + "platform.server.action.AbilityCheckpoint"),
                "checkpoint writer depends on live runtime: " + ref);
        }
        assertFalse(code(writer).contains("net.minecraft.world.entity."));
        assertFalse(code(writer).contains("net.minecraft.server.level."));
    }

    @Test void genericFrameworkDoesNotImportBuiltinSpecies() throws Exception {
        for (var path : javaFiles(platform())) {
            var pkg = packageOf(path);
            if (pkg.startsWith(BASE + "platform.server.") && !pkg.startsWith(BASE + "platform.server.builtin.")) {
                assertTrue(references(path).stream().noneMatch(ref -> ref.startsWith(BASE + "platform.server.builtin.")), path.toString());
                // Enemy is a generic Minecraft hostility marker, not a species implementation.
                assertFalse(code(path).replace("net.minecraft.world.entity.monster.Enemy", "").contains("net.minecraft.world.entity.monster."),
                        path + " embeds species knowledge");
            }
        }
    }

    // This list admits hook contracts, policies, projections and fixed-version access interfaces, never owners.
    private static final Set<String> MIXIN_SEAMS = Set.of(
        "diagnostics.DebugDiagnostics", "server.runtime.MinecraftCombatRuntime",
        "client.action.ClientTacticalPlan", "client.input.ClientControl", "client.input.MouseInputReset",
        "client.presentation.ClientPresentation", "client.presentation.PresentationUse", "client.simulation.ClientEntitySimulation",
        "client.state.ClientCombatState", "client.ui.OverlayUi", "client.ui.ParticipantPortraits", "client.ui.TacticalOverlay",
        "projection.PresentationIdentity", "projection.PresentationUseIdentity",
        "server.action.BrushRay", "server.action.ItemWorldMutationScope", "server.action.ProjectileImpactHooks", "server.action.TacticalUseContext",
        "server.actor.ActorLifecycleHooks", "server.actor.EquipmentRevisions",
        "server.builtin.creeper.CreeperClouds", "server.builtin.creeper.CreeperExplosion",
        "server.control.ActorControlPolicy", "server.control.InputPolicy", "server.control.MovementInputHooks",
        "server.control.SimulationPolicy", "server.control.VanillaInputPolicy", "server.damage.TacticalDamageContext",
        "server.effect.vanilla.EffectTimerAccess", "server.effect.vanilla.VanillaEffectRoundController",
        "server.encounter.AuthorityProjection", "server.presentation.ChestPresentation", "server.world.ConduitTimeAccess",
        "server.world.EnvironmentExplosion", "server.world.EnvironmentProcesses", "server.world.ScheduledTickHooks", "server.world.WorldSimulationHooks"
    );
    @Test void mixinsEnterOnlyApprovedPlatformSeams() throws Exception {
        for (var path : javaFiles(platform().resolve("cc/sighs/dndturn/platform/mixin")))
            for (var ref : references(path)) {
                if (ref.startsWith(BASE + "platform.mixin.")) continue;
                assertTrue(ref.startsWith(BASE + "platform.") && MIXIN_SEAMS.contains(ref.substring((BASE + "platform.").length())),
                        path + " enters unapproved seam " + ref);
            }
    }

    @Test void mixinConfigurationExactlyNamesRelocatedClasses() throws Exception {
        String json = Files.readString(ROOT.resolve("targets/neoforge-26.1/src/main/resources/dndturn.mixins.json"));
        assertTrue(json.contains("\"package\": \"" + BASE + "platform.mixin\""));
        var registered = new HashSet<String>();
        var matches = Pattern.compile("\"((?:server|client)\\.[\\w.]+)\"").matcher(json);
        while (matches.find()) assertTrue(registered.add(matches.group(1)), "duplicate mixin " + matches.group(1));
        var declared = new HashSet<String>();
        for (var path : javaFiles(platform().resolve("cc/sighs/dndturn/platform/mixin"))) {
            assertTrue(code(path).contains("@Mixin"), path.toString());
            declared.add(packageOf(path).substring((BASE + "platform.mixin.").length()) + "." + path.getFileName().toString().replace(".java", ""));
        }
        assertEquals(declared, registered);
    }
}
