package mdh.dndturn.network.packet;

import mdh.dndturn.core.CombatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MoveToCellC2S {

    public final BlockPos target;

    public MoveToCellC2S(BlockPos target) {
        this.target = target;
    }

    public static void encode(MoveToCellC2S packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.target);
    }

    public static MoveToCellC2S decode(FriendlyByteBuf buf) {
        return new MoveToCellC2S(buf.readBlockPos());
    }

    public static void handle(MoveToCellC2S packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                CombatManager.requestMove(player, packet.target);
            }
        });
        context.setPacketHandled(true);
    }
}
