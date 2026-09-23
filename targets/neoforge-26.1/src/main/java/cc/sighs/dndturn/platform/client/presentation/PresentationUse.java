package cc.sighs.dndturn.platform.client.presentation;

import cc.sighs.dndturn.platform.client.simulation.ClientEntitySimulation;
import cc.sighs.dndturn.platform.observation.ItemStackFingerprint;
import cc.sighs.dndturn.platform.projection.PresentationState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** All render consumers use this matching snapshot; no general entity getter is intercepted. */
public final class PresentationUse {
    private PresentationUse() {}
    private static final java.util.Map<LivingEntity, java.util.IdentityHashMap<ItemStack, Cached>> revisions = new java.util.IdentityHashMap<>();
    private record Cached(ItemStack copy, String revision) {}
    public static void clear() { revisions.clear(); }
    static void beginUpdate() { clear(); }
    private static String revision(LivingEntity entity, ItemStack stack) {
        var values = revisions.computeIfAbsent(entity, ignored -> new java.util.IdentityHashMap<>());
        var cached = values.get(stack);
        if (cached == null || !ItemStack.matches(cached.copy(), stack)) {
            cached = new Cached(stack.copy(), ItemStackFingerprint.revision(entity, stack)); values.put(stack, cached);
        }
        return cached.revision();
    }
    public static PresentationState.Use snapshot(LivingEntity entity) {
        if (entity == null || !PresentationAdapters.humanoid(entity)) return null;
        var projection = ClientEntitySimulation.projection(entity);
        if (projection == null || projection.facts().controller() == null) return null;
        var use = projection.facts().use();
        if (!use.active()) return use;
        return use.item().equals(revision(entity, entity.getItemInHand(use.hand()))) ? use : null;
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
        return use != null && use.active() && use.item().equals(revision(entity, stack));
    }
}
