package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.ServerCombatService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkHeldTickUnloadMixin {
    @Inject(method = "getTicksForSerialization(J)Lnet/minecraft/world/level/chunk/ChunkAccess$PackedTicks;",
        at = @At("RETURN"), cancellable = true)
    private void dndturn$saveHeldWithoutReleasing(long currentTick,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.level.chunk.ChunkAccess.PackedTicks> callback) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (!(chunk.getLevel() instanceof ServerLevel level)) return;
        ServerCombatService service = ServerCombatService.existing(level.getServer());
        if (service != null) callback.setReturnValue(service.savedTicksForChunk(level, chunk.getPos(), callback.getReturnValue()));
    }

    @Inject(method = "unregisterTickContainerFromLevel(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("HEAD"))
    private void dndturn$releaseBeforeUnregister(ServerLevel level, CallbackInfo callback) {
        ServerCombatService service = ServerCombatService.existing(level.getServer());
        if (service != null) service.beforeChunkUnload(level, ((LevelChunk) (Object) this).getPos());
    }
}
