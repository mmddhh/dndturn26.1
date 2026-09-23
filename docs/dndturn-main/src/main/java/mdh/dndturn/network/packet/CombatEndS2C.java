package mdh.dndturn.network.packet;

import mdh.dndturn.client.ClientCombatState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CombatEndS2C {

    public static void encode(CombatEndS2C packet, FriendlyByteBuf buf) {
    }

    public static CombatEndS2C decode(FriendlyByteBuf buf) {
        return new CombatEndS2C();
    }

    public static void handle(CombatEndS2C packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCombatState.clear()));
        context.setPacketHandled(true);
    }
}
