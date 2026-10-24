package cc.sighs.dndturn.platform.server.entity;
import net.minecraft.world.entity.LivingEntity;
/** Fixed-version committed-death observation. */
public final class MinecraftDeaths {
    private MinecraftDeaths() {}

    public static boolean deathCommitted(LivingEntity actor) {
        return ((cc.sighs.dndturn.platform.mixin.server.lifecycle.LivingEntityDeathAccessor) actor).dndturn$isDead();
    }
}
