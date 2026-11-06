package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.domain.resolution.AttackProfile;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Per-entity attack profiles from the 5e bestiary input; players keep the global +5/native weapon path. */
public final class MobAttackProfiles {
    private static final Map<String, AttackProfile> PROFILES = Map.ofEntries(
        Map.entry("minecraft:zombie", profile(3, 1, 6, 1, "dndturn:bludgeoning")),
        Map.entry("minecraft:husk", profile(3, 1, 6, 1, "dndturn:bludgeoning")),
        Map.entry("minecraft:drowned", profile(3, 1, 6, 1, "dndturn:bludgeoning")),
        Map.entry("minecraft:zombie_villager", profile(3, 1, 6, 1, "dndturn:bludgeoning")),
        Map.entry("minecraft:skeleton", profile(4, 1, 6, 2, "dndturn:piercing")),
        Map.entry("minecraft:stray", profile(4, 1, 6, 2, "dndturn:piercing")),
        Map.entry("minecraft:bogged", profile(4, 1, 6, 2, "dndturn:piercing")),
        Map.entry("minecraft:parched", profile(4, 1, 6, 2, "dndturn:piercing")),
        Map.entry("minecraft:wither_skeleton", profile(5, 1, 12, 4, "dndturn:slashing")),
        Map.entry("minecraft:enderman", profile(4, 1, 8, 2, "dndturn:bludgeoning")),
        Map.entry("minecraft:spider", profile(3, 1, 6, 1, "dndturn:piercing")),
        Map.entry("minecraft:cave_spider", profile(3, 1, 6, 1, "dndturn:piercing")),
        Map.entry("minecraft:silverfish", profile(4, 1, 4, 2, "dndturn:piercing")),
        Map.entry("minecraft:vex", profile(4, 3, 4, 0, "dndturn:necrotic")),
        Map.entry("minecraft:vindicator", profile(5, 1, 12, 4, "dndturn:slashing")),
        Map.entry("minecraft:pillager", profile(4, 1, 6, 3, "dndturn:piercing")),
        Map.entry("minecraft:ravager", profile(6, 0, 0, 12, "dndturn:bludgeoning")),
        Map.entry("minecraft:warden", profile(8, 0, 0, 15, "dndturn:bludgeoning")),
        Map.entry("minecraft:blaze", profile(3, 1, 4, 2, "dndturn:fire")),
        Map.entry("minecraft:piglin_brute", profile(5, 0, 0, 12, "dndturn:slashing")),
        Map.entry("minecraft:piglin", profile(5, 0, 0, 6, "dndturn:slashing")),
        Map.entry("minecraft:hoglin", profile(6, 0, 0, 10, "dndturn:piercing")),
        Map.entry("minecraft:zoglin", profile(6, 0, 0, 10, "dndturn:piercing")),
        Map.entry("minecraft:endermite", profile(2, 0, 0, 2, "dndturn:piercing")),
        Map.entry("minecraft:phantom", profile(4, 1, 4, 2, "dndturn:slashing")),
        Map.entry("minecraft:creaking", profile(4, 1, 6, 2, "dndturn:bludgeoning")),
        Map.entry("minecraft:slime", profile(4, 2, 6, 2, "dndturn:bludgeoning")),
        Map.entry("minecraft:magma_cube", profile(4, 2, 6, 2, "dndturn:bludgeoning"))
    );

    private MobAttackProfiles() {}

    /** Unknown third-party mob: +5 to hit, 10 bludgeoning, as the documented fallback template. */
    public static final AttackProfile DEFAULT =
        new AttackProfile(5, 0, 0, 10, "dndturn:bludgeoning");

    public static AttackProfile of(Entity entity) {
        if (entity == null) return null;
        return PROFILES.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), DEFAULT);
    }

    private static AttackProfile profile(int bonus, int count, int sides, int flat, String type) {
        return new AttackProfile(bonus, count, sides, flat, type);
    }
}
