package mdh.dndturn.network.packet;

import mdh.dndturn.combat.CombatAction;
import mdh.dndturn.core.CombatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CombatActionC2S {

    public final CombatAction action;

    public CombatActionC2S(CombatAction action) {
        this.action = action;
    }

    public static void encode(CombatActionC2S packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.action.ordinal());
    }

    public static CombatActionC2S decode(FriendlyByteBuf buf) {
        CombatAction[] values = CombatAction.values();
        int ordinal = buf.readVarInt();
        return new CombatActionC2S(ordinal >= 0 && ordinal < values.length ? values[ordinal] : CombatAction.END_TURN);
    }

    public static void handle(CombatActionC2S packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                CombatManager.requestAction(player, packet.action);
            }
        });
        context.setPacketHandled(true);
    }
}
