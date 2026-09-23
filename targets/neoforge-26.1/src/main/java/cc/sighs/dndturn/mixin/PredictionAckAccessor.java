package cc.sighs.dndturn.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Observation of the fixed-version ACK queue for protocol regression tests. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface PredictionAckAccessor {
    @Accessor("ackBlockChangesUpTo") int dndturn$pendingAck();
}
