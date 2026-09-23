package mdh.dndturn.network.packet;

import mdh.dndturn.client.ClientCombatState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CombatLogS2C {

    public final String message;

    public CombatLogS2C(String message) {
        this.message = message;
    }

    public static void encode(CombatLogS2C packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.message == null ? "" : packet.message, 512);
    }

    public static CombatLogS2C decode(FriendlyByteBuf buf) {
        return new CombatLogS2C(buf.readUtf(512));
    }

    public static void handle(CombatLogS2C packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCombatState.addLog(packet.message)));
        context.setPacketHandled(true);
    }
}
