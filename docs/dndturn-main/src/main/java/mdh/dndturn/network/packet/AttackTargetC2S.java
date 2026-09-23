package mdh.dndturn.network.packet;

import mdh.dndturn.core.CombatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AttackTargetC2S {

    public final int networkId;

    public AttackTargetC2S(int networkId) {
        this.networkId = networkId;
    }

    public static void encode(AttackTargetC2S packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.networkId);
    }

    public static AttackTargetC2S decode(FriendlyByteBuf buf) {
        return new AttackTargetC2S(buf.readVarInt());
    }

    public static void handle(AttackTargetC2S packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                CombatManager.requestAttack(player, packet.networkId);
            }
        });
        context.setPacketHandled(true);
    }
}
