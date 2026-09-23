package cc.sighs.dndturn.platform.server.builtin.ai;

import cc.sighs.dndturn.domain.ai.AiDefinition;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.platform.server.ai.AiDefinitions;
import java.util.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** Native type evidence is confined to startup registration, never the standard planner. */
public final class VanillaAiFamilies {
    private VanillaAiFamilies() {}
    public static void register() {
        register("dndturn:zombie_combat", mob -> ground(mob) && mob instanceof Zombie,
                AiDefinition.TargetPreference.VISIBLE_PLAYERS);
        register("dndturn:enderman_combat", mob -> ground(mob) && mob instanceof EnderMan,
                AiDefinition.TargetPreference.COMMITTED_OR_HOSTILE_PLAYERS);
    }
    private static boolean ground(Mob mob) { return mob instanceof PathfinderMob && mob.getNavigation() instanceof GroundPathNavigation; }
    private static void register(String id, java.util.function.Predicate<Mob> matches, AiDefinition.TargetPreference preference) {
        AiDefinitions.registerFamily(new AiDefinitions.Family(id, 1, 0, matches,
                new AiDefinition(id, 1, "dndturn:shared_ground", Set.of("BOUNDED_PROPOSALS"), preference,
                        AiDefinition.Positioning.GROUND_OPPORTUNITY, AiDefinition.ResourcePreference.CONSERVATIVE,
                        AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY, ActorFacts.DECISION,
                        new AiDefinition.Provenance(AiDefinition.Source.AUDITED_NATIVE_FAMILY, id, 1,
                                "Minecraft 26.1 / NeoForge 26.1.2.84; capability authorization remains independent"))));
    }
}
