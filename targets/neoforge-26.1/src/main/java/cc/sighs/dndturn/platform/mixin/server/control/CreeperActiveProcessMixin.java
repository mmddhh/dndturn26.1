package cc.sighs.dndturn.platform.mixin.server.control;

import cc.sighs.dndturn.platform.server.control.ActorControlPolicy;
import cc.sighs.dndturn.platform.server.encounter.AuthorityProjection;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The outer tick fuse is an active skill and is not covered by Goal/Brain suppression. */
@Mixin(Creeper.class)
public abstract class CreeperActiveProcessMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/monster/Creeper;isAlive()Z"))
    private boolean dndturn$skill(Creeper entity) { return entity.isAlive() && ActorControlPolicy.autonomousDecision(AuthorityProjection.actor(entity)).allowed(); }
}
