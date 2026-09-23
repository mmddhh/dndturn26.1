package cc.sighs.dndturn.platform.mixin.server.world;

import cc.sighs.dndturn.platform.server.world.ConduitTimeAccess;
import cc.sighs.dndturn.platform.server.world.EnvironmentProcesses;
import cc.sighs.dndturn.platform.server.world.WorldSimulationHooks;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ConduitBlockEntity.class)
public abstract class ConduitEnvironmentTimeMixin implements ConduitTimeAccess {
    @Shadow private long nextAmbientSoundActivation;
    @Unique private UUID dndturn$domain;
    @Unique private long dndturn$lastTime;

    @Override public void dndturn$rebaseAmbient(long worldTime, long projectedTime, UUID domain) {
        if (!Objects.equals(domain, dndturn$domain)) {
            long priorTime = dndturn$domain == null ? worldTime : dndturn$lastTime;
            nextAmbientSoundActivation = projectedTime + Math.max(0, nextAmbientSoundActivation - priorTime);
        }
        dndturn$domain = domain;
        dndturn$lastTime = projectedTime;
    }
    @Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getGameTime()J"))
    private static long dndturn$period(Level receiver, Level level, BlockPos pos, BlockState state, ConduitBlockEntity conduit) {
        long projected = EnvironmentProcesses.time(level, pos);
        UUID domain = WorldSimulationHooks.domainAt(level, pos);
        ((ConduitTimeAccess) conduit).dndturn$rebaseAmbient(level.getGameTime(), projected, domain);
        return projected;
    }
}
