package mdh.dndturn.network.packet;

import mdh.dndturn.client.ClientCombatState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MovePlanS2C {

    public final List<BlockPos> path;

    public MovePlanS2C(List<BlockPos> path) {
        this.path = path == null ? List.of() : path;
    }

    public static void encode(MovePlanS2C packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.path.size());
        for (BlockPos pos : packet.path) {
            buf.writeBlockPos(pos);
        }
    }

    public static MovePlanS2C decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<BlockPos> path = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            path.add(buf.readBlockPos());
        }
        return new MovePlanS2C(path);
    }

    public static void handle(MovePlanS2C packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCombatState.setPath(packet.path)));
        context.setPacketHandled(true);
    }
}
