package cc.sighs.dndturn.client;

import cc.sighs.dndturn.combat.PresentationState;
import cc.sighs.dndturn.combat.TacticalItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** All render consumers use this matching snapshot; no general entity getter is intercepted. */
public final class PresentationUse {
    private PresentationUse() {}
    public static PresentationState.Use snapshot(LivingEntity entity) {
        if (entity == null || !PresentationAdapters.humanoid(entity)) return null;
        var projection = ClientEntitySimulation.projection(entity);
        if (projection == null || projection.facts().controller() == null) return null;
        var use = projection.facts().use();
        if (!use.active()) return use;
        return use.item().equals(TacticalItems.revision(entity, entity.getItemInHand(use.hand()))) ? use : null;
    }
    public static boolean using(LivingEntity entity) {
        var use = snapshot(entity); return use == null ? entity.isUsingItem() : use.active();
    }
    public static int remaining(LivingEntity entity) {
        var use = snapshot(entity); return use == null ? entity.getUseItemRemainingTicks() : use.remaining();
    }
    public static float used(LivingEntity entity, float partial) {
        var use = snapshot(entity); return use == null ? entity.getTicksUsingItem(partial) : use.used();
    }
    public static InteractionHand hand(LivingEntity entity) {
        var use = snapshot(entity); return use == null ? entity.getUsedItemHand() : use.hand();
    }
    public static ItemStack stack(LivingEntity entity) {
        var use = snapshot(entity); return use == null ? entity.getUseItem() : use.active() ? entity.getItemInHand(use.hand()) : ItemStack.EMPTY;
    }
    public static boolean matches(LivingEntity entity, ItemStack stack) {
        var use = snapshot(entity);
        return use != null && use.active() && use.item().equals(TacticalItems.revision(entity, stack));
    }
}
