package mdh.dndturn.network.packet;

import mdh.dndturn.core.CombatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EndTurnC2S {

    public static void encode(EndTurnC2S packet, FriendlyByteBuf buf) {
    }

    public static EndTurnC2S decode(FriendlyByteBuf buf) {
        return new EndTurnC2S();
    }

    public static void handle(EndTurnC2S packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                CombatManager.requestEndTurn(player);
            }
        });
        context.setPacketHandled(true);
    }
}
