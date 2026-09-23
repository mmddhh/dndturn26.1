package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.action.ActionIntent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Default native entity interaction. Discovery never invokes an item or entity callback. */
final class MinecraftEntityUse {
    static boolean supports(ItemStack stack) { return !stack.isEmpty(); }
    static String unavailable(ServerPlayer player, ActionIntent intent) {
        var target = player.level().getEntity(intent.target().entity());
        return target instanceof LivingEntity living && living.isAlive()
                && !(target instanceof net.minecraft.world.entity.player.Player)
                ? null : "living non-player interaction target required";
    }
    static InteractionResult invoke(ServerPlayer player, ActionIntent intent) {
        var target = (LivingEntity)player.level().getEntity(intent.target().entity());
        // The plan binds the entity, without fabricating a precise client hit on a body part.
        return player.interactOn(target, PlayerBehavior.hand(intent), target.getBoundingBox().getCenter().subtract(target.position()));
    }
}
