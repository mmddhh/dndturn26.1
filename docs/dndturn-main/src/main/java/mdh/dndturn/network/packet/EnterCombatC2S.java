package mdh.dndturn.network.packet;

import mdh.dndturn.core.CombatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EnterCombatC2S {

    public static void encode(EnterCombatC2S packet, FriendlyByteBuf buf) {
    }

    public static EnterCombatC2S decode(FriendlyByteBuf buf) {
        return new EnterCombatC2S();
    }

    public static void handle(EnterCombatC2S packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                CombatManager.requestToggleCombat(player);
            }
        });
        context.setPacketHandled(true);
    }
}
