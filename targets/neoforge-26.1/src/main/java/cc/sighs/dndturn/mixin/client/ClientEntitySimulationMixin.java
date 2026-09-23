package cc.sighs.dndturn.mixin.client;

import cc.sighs.dndturn.client.ClientEntitySimulation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientEntitySimulationMixin {
    @Shadow private void tickPassenger(Entity vehicle, Entity passenger) {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void dndturn$entity(Entity entity, CallbackInfo ci) {
        if (entity != Minecraft.getInstance().player && !entity.isRemoved() && ClientEntitySimulation.paused(entity)) {
            dndturn$pausedProjection(entity);
            ci.cancel();
        }
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void dndturn$passenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        if (passenger != Minecraft.getInstance().player && !passenger.isRemoved() && passenger.getVehicle() == vehicle
            && ClientEntitySimulation.paused(passenger)) {
            dndturn$pausedProjection(passenger);
            ci.cancel();
        }
    }

    @Unique private void dndturn$pausedProjection(Entity entity) {
        entity.setOldPosAndRot();
        // Keep vanilla convergence to received positions, without gravity, travel or body timers.
        if (entity != Minecraft.getInstance().player && entity.isInterpolating())
            entity.getInterpolation().interpolate();
        // A paused vehicle does not own the simulation or lifecycle of every passenger.
        for (Entity passenger : entity.getPassengers()) tickPassenger(entity, passenger);
    }
}
