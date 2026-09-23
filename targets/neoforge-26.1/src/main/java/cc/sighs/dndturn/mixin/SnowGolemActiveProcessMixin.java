package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ActiveBodyControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The .84 snow trail is outside customServerAiStep; melting and body maintenance still run. */
@Mixin(SnowGolem.class)
public abstract class SnowGolemActiveProcessMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/event/EventHooks;canEntityGrief(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean dndturn$snow(ServerLevel level, Entity entity) {
        return !ActiveBodyControl.controlled((SnowGolem) entity) && EventHooks.canEntityGrief(level, entity);
    }
}
