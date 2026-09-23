package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.MinecraftCombatRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the vanilla entity container maintenance while skipping paused simulation before despawn and tickCount. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEntityGateMixin {
    @Redirect(method = "lambda$tick$0(Lnet/minecraft/world/TickRateManager;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/TickRateManager;isEntityFrozen(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean dndturn$regionalEntityFreeze(TickRateManager manager, Entity entity) {
        return manager.isEntityFrozen(entity) || MinecraftCombatRuntime.prepareFormalEntitySimulation(entity);
    }

    @Inject(method = "tickPassenger(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)V",
        at = @At("HEAD"), cancellable = true)
    private void dndturn$regionalPassengerFreeze(Entity vehicle, Entity passenger, CallbackInfo callback) {
        // Let vanilla repair an invalid riding relation before considering a simulation pause.
        if (!passenger.isRemoved() && passenger.getVehicle() == vehicle
            && MinecraftCombatRuntime.prepareFormalEntitySimulation(passenger)) callback.cancel();
    }
}
