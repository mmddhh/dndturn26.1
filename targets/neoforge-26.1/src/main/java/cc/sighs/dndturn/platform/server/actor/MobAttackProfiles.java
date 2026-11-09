package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.domain.character.RulesCatalog;
import cc.sighs.dndturn.domain.resolution.AttackProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Native registry identity is translated once into the captured rules catalog. */
public final class MobAttackProfiles {
    private MobAttackProfiles() {}
    public static AttackProfile of(Entity entity, RulesCatalog catalog) {
        return entity == null ? null : catalog.attackProfile(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }
}
