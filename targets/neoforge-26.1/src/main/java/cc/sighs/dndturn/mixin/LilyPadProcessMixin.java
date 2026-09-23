package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.EnvironmentProcesses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LilyPadBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Re-evaluate live boat contact on vanilla body updates; never replay a historical collision. */
@Mixin(LilyPadBlock.class)
public abstract class LilyPadProcessMixin {
    @Redirect(method = "entityInside", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean dndturn$currentContact(Level level, BlockPos pos, boolean drops, Entity boat) {
        return (!(level instanceof ServerLevel serverLevel)
            || EnvironmentProcesses.allowed(serverLevel, pos,
                EnvironmentProcesses.policy(false, EnvironmentProcesses.Channel.LILY_PAD_CONTACT)))
            && level.destroyBlock(pos, drops, boat);
    }
}
