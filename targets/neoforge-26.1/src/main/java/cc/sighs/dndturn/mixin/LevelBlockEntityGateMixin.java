package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.EnvironmentProcesses;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep vanilla ticker registration, onLoad, and removed-ticker cleanup active. */
@Mixin(Level.class)
public abstract class LevelBlockEntityGateMixin {
    @Redirect(method = "tickBlockEntities()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void dndturn$processTick(TickingBlockEntity ticker) {
        Level level = (Level) (Object) this;
        if (!(level instanceof ServerLevel serverLevel)
            || EnvironmentProcesses.allowed(serverLevel, ticker.getPos(),
                EnvironmentProcesses.tickerPolicy(serverLevel, ticker.getPos()))) ticker.tick();
    }
}
