package cc.sighs.dndturn.platform.server.action;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Injected effects capability for accepted action execution. */
public interface ActionEffects {
    void drainImmediateTriggers(LiveActorContext actor, UUID encounter, UUID parent);
    void joinGeneratedMob(ServerPlayer owner, Mob mob, UUID encounter);
    void swing(LivingEntity actor, UUID encounter, UUID operation, net.minecraft.world.InteractionHand hand);
}
