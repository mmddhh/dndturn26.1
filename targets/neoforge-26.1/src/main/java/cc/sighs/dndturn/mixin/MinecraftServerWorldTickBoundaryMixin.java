package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ServerCombatService;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep a failed world tick from leaving an authorized environment step reusable. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerWorldTickBoundaryMixin {
    @Redirect(method = "tickChildren", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void dndturn$runLevelWithAbort(ServerLevel level, BooleanSupplier haveTime) {
        try {
            level.tick(haveTime);
        } catch (RuntimeException | Error failure) {
            ServerCombatService service = ServerCombatService.existing((MinecraftServer) (Object) this);
            if (service != null) {
                try {
                    service.abortLevelTick(level, failure);
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }
}
