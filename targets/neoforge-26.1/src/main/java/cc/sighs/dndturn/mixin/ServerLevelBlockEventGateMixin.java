package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.EnvironmentProcesses;
import cc.sighs.dndturn.combat.ChestPresentation;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve vanilla queue ownership while classifying the concrete event before execution. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockEventGateMixin {
    @Shadow @Final private ObjectLinkedOpenHashSet<BlockEventData> blockEvents;
    @Shadow @Final private List<BlockEventData> blockEventsToReschedule;
    @Shadow private boolean doBlockEvent(BlockEventData event) { throw new AssertionError("Mixin shadow"); }

    @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true)
    private void dndturn$lid(BlockPos pos, Block block, int event, int count, CallbackInfo ci) {
        ServerLevel level = (ServerLevel)(Object)this;
        if (!ChestPresentation.accepts(level, pos, block, event)) return;
        // This display channel now has one owner; old queued targets must never replay later.
        blockEvents.removeIf(e -> e.pos().equals(pos) && e.block() == block && e.paramA() == 1);
        blockEventsToReschedule.removeIf(e -> e.pos().equals(pos) && e.block() == block && e.paramA() == 1);
        ChestPresentation.send(level, pos, block, count);
        ci.cancel();
    }
    @Redirect(method = "runBlockEvents()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;doBlockEvent(Lnet/minecraft/world/level/BlockEventData;)Z"))
    private boolean dndturn$processEvent(ServerLevel level, BlockEventData event) {
        if (!EnvironmentProcesses.allowed(level, event.pos(), EnvironmentProcesses.eventPolicy(level, event))) {
            blockEventsToReschedule.add(event);
            return false;
        }
        return doBlockEvent(event);
    }
}
