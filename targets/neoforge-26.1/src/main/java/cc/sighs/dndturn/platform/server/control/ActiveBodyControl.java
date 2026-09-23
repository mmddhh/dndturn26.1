package cc.sighs.dndturn.platform.server.control;

import cc.sighs.dndturn.platform.mixin.server.action.TacticalUseTickAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Separates active input/use from ordinary body maintenance. Scope never survives a call. */
public final class ActiveBodyControl {
    private static final ThreadLocal<LivingEntity> USE_STEP = new ThreadLocal<>();
    private ActiveBodyControl() {}
    public static boolean inUseStep(LivingEntity entity) { return USE_STEP.get() == entity; }
    public static void tickUse(ServerPlayer player) {
        var previous = USE_STEP.get();
        if (previous != null) throw new IllegalStateException("nested item use step");
        USE_STEP.set(player);
        try { ((TacticalUseTickAccessor)player).dndturn$tickUsingItem(); }
        finally { USE_STEP.remove(); }
    }
}
