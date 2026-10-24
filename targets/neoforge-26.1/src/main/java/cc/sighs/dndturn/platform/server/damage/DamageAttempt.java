package cc.sighs.dndturn.platform.server.damage;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
/** The admitted operation's reaction and hit-body capabilities, supplied by its execution owner. */
public interface DamageAttempt {
    boolean cancelDamageAttempt(LivingEntity target, DamageSource source, float amount, UUID operation);
    Entity damageBody(LivingEntity target, UUID operation);
}
