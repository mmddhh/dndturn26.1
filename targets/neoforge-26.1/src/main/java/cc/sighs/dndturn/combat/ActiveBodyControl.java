package cc.sighs.dndturn.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Separates active input/use from ordinary body maintenance. Scope never survives a call. */
public final class ActiveBodyControl {
    private static final ThreadLocal<LivingEntity> USE_STEP = new ThreadLocal<>();
    private ActiveBodyControl() {}
    public static boolean controlled(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        var service = ServerCombatService.existing(level.getServer());
        return service != null && service.isEntityInsidePausedRegion(entity);
    }
    public static boolean movementAllowed(LivingEntity entity) {
        if (!controlled(entity)) return true;
        var service = ServerCombatService.existing(((ServerLevel)entity.level()).getServer());
        return entity instanceof ServerPlayer ? service.hasPlayerMoveLease(entity.getUUID()) : service.hasMobMoveLease(entity.getUUID());
    }
    public static boolean useAllowed(LivingEntity entity) { return !controlled(entity) || USE_STEP.get() == entity; }
    static void tickUse(ServerPlayer player) {
        var previous = USE_STEP.get();
        if (previous != null) throw new IllegalStateException("nested item use step");
        USE_STEP.set(player);
        try { ((cc.sighs.dndturn.mixin.TacticalUseTickAccessor)player).dndturn$tickUsingItem(); }
        finally { USE_STEP.remove(); }
    }
}
