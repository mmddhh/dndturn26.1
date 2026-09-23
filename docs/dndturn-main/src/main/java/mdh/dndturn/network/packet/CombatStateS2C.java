package mdh.dndturn.network.packet;

import mdh.dndturn.client.ClientCombatState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class CombatStateS2C {

    public final UUID encounterId;
    public final int round;
    public final UUID currentId;
    public final List<Entry> entries;
    public final int gridMinX;
    public final int gridMaxX;
    public final int gridMinZ;
    public final int gridMaxZ;
    public final int floorY;
    public final List<BlockPos> reachable;
    public final boolean myTurn;
    public final int myMovementRemaining;
    public final boolean myAction;
    public final boolean myBonus;

    public CombatStateS2C(UUID encounterId, int round, UUID currentId, List<Entry> entries,
                          int gridMinX, int gridMaxX, int gridMinZ, int gridMaxZ, int floorY,
                          List<BlockPos> reachable, boolean myTurn, int myMovementRemaining,
                          boolean myAction, boolean myBonus) {
        this.encounterId = encounterId;
        this.round = round;
        this.currentId = currentId;
        this.entries = entries;
        this.gridMinX = gridMinX;
        this.gridMaxX = gridMaxX;
        this.gridMinZ = gridMinZ;
        this.gridMaxZ = gridMaxZ;
        this.floorY = floorY;
        this.reachable = reachable;
        this.myTurn = myTurn;
        this.myMovementRemaining = myMovementRemaining;
        this.myAction = myAction;
        this.myBonus = myBonus;
    }

    public record Entry(UUID id, int networkId, String name, int initiative, String side,
                        int movementRemaining, boolean hasAction, boolean isCurrent) {
    }

    public static void encode(CombatStateS2C packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.encounterId);
        buf.writeVarInt(packet.round);
        buf.writeBoolean(packet.currentId != null);
        if (packet.currentId != null) {
            buf.writeUUID(packet.currentId);
        }
        buf.writeVarInt(packet.gridMinX);
        buf.writeVarInt(packet.gridMaxX);
        buf.writeVarInt(packet.gridMinZ);
        buf.writeVarInt(packet.gridMaxZ);
        buf.writeVarInt(packet.floorY);
        buf.writeBoolean(packet.myTurn);
        buf.writeVarInt(packet.myMovementRemaining);
        buf.writeBoolean(packet.myAction);
        buf.writeBoolean(packet.myBonus);
        buf.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buf.writeUUID(entry.id());
            buf.writeVarInt(entry.networkId());
            buf.writeUtf(entry.name() == null ? "" : entry.name(), 64);
            buf.writeVarInt(entry.initiative());
            buf.writeUtf(entry.side() == null ? "" : entry.side(), 16);
            buf.writeVarInt(entry.movementRemaining());
            buf.writeBoolean(entry.hasAction());
            buf.writeBoolean(entry.isCurrent());
        }
        buf.writeVarInt(packet.reachable.size());
        for (BlockPos pos : packet.reachable) {
            buf.writeBlockPos(pos);
        }
    }

    public static CombatStateS2C decode(FriendlyByteBuf buf) {
        UUID encounterId = buf.readUUID();
        int round = buf.readVarInt();
        UUID currentId = buf.readBoolean() ? buf.readUUID() : null;
        int gridMinX = buf.readVarInt();
        int gridMaxX = buf.readVarInt();
        int gridMinZ = buf.readVarInt();
        int gridMaxZ = buf.readVarInt();
        int floorY = buf.readVarInt();
        boolean myTurn = buf.readBoolean();
        int myMovementRemaining = buf.readVarInt();
        boolean myAction = buf.readBoolean();
        boolean myBonus = buf.readBoolean();
        int entryCount = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            UUID id = buf.readUUID();
            int networkId = buf.readVarInt();
            String name = buf.readUtf(64);
            int initiative = buf.readVarInt();
            String side = buf.readUtf(16);
            int movementRemaining = buf.readVarInt();
            boolean hasAction = buf.readBoolean();
            boolean isCurrent = buf.readBoolean();
            entries.add(new Entry(id, networkId, name, initiative, side, movementRemaining, hasAction, isCurrent));
        }
        int reachableCount = buf.readVarInt();
        List<BlockPos> reachable = new ArrayList<>(reachableCount);
        for (int i = 0; i < reachableCount; i++) {
            reachable.add(buf.readBlockPos());
        }
        return new CombatStateS2C(encounterId, round, currentId, entries,
                gridMinX, gridMaxX, gridMinZ, gridMaxZ, floorY,
                reachable, myTurn, myMovementRemaining, myAction, myBonus);
    }

    public static void handle(CombatStateS2C packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCombatState.apply(packet)));
        context.setPacketHandled(true);
    }
}
