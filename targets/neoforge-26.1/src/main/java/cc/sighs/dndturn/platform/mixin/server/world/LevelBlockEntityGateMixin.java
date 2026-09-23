package cc.sighs.dndturn.platform.mixin.server.world;

import cc.sighs.dndturn.platform.server.control.SimulationPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
import cc.sighs.dndturn.platform.server.world.EnvironmentProcesses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
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
            || SimulationPolicy.process(EnvironmentProcesses.tickerPolicy(serverLevel, ticker.getPos()), AuthorityProjection.block(serverLevel, ticker.getPos())).allowed()) ticker.tick();
    }
}
