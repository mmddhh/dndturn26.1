package cc.sighs.dndturn.platform.server.actor;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Per-entity movement budget in ticks, keyed by native entity id. */
public final class MobMovementBudgets {
    private static final int DEFAULT = 30;
    private static final Map<String, Integer> BUDGETS = Map.ofEntries(
        Map.entry("minecraft:creeper", 25),
        Map.entry("minecraft:drowned", 20),
        Map.entry("minecraft:husk", 20),
        Map.entry("minecraft:phantom", 60),
        Map.entry("minecraft:silverfish", 30),
        Map.entry("minecraft:enderman", 30),
        Map.entry("minecraft:spider", 40),
        Map.entry("minecraft:cave_spider", 40),
        Map.entry("minecraft:stray", 30),
        Map.entry("minecraft:skeleton", 30),
        Map.entry("minecraft:vex", 50),
        Map.entry("minecraft:vindicator", 35),
        Map.entry("minecraft:witch", 30),
        Map.entry("minecraft:zombie", 20),
        Map.entry("minecraft:zombie_villager", 20),
        Map.entry("minecraft:bogged", 30),
        Map.entry("minecraft:parched", 30),
        Map.entry("minecraft:creaking", 35),
        Map.entry("minecraft:pillager", 30),
        Map.entry("minecraft:ravager", 35),
        Map.entry("minecraft:warden", 30),
        Map.entry("minecraft:blaze", 30),
        Map.entry("minecraft:ghast", 25),
        Map.entry("minecraft:piglin_brute", 35),
        Map.entry("minecraft:wither_skeleton", 30),
        Map.entry("minecraft:piglin", 30),
        Map.entry("minecraft:hoglin", 30),
        Map.entry("minecraft:zoglin", 30),
        Map.entry("minecraft:endermite", 25),
        Map.entry("minecraft:ender_dragon", 60),
        Map.entry("minecraft:wither", 60),
        Map.entry("minecraft:elder_guardian", 35),
        Map.entry("minecraft:guardian", 35),
        Map.entry("minecraft:evoker", 30)
    );

    private MobMovementBudgets() {}

    public static int of(Entity entity) {
        if (entity == null) return DEFAULT;
        return of(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    public static int of(String entityId) {
        int value = BUDGETS.getOrDefault(entityId, DEFAULT);
        return Math.clamp(value <= 0 ? DEFAULT : value, 1, 120);
    }
}
