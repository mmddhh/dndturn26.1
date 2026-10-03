package cc.sighs.dndturn.platform.network;

import cc.sighs.dndturn.domain.encounter.EncounterRegion;
import cc.sighs.dndturn.platform.bootstrap.DNDTurnNeoForge;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * DNDTURN-TEMP-BOUNDARY-VIZ: TEMPORARY developer aid that ships the encounter field geometry to the
 * client so it can be drawn. Not part of the gameplay protocol; remove once the region is exposed
 * through a reviewed projection. Remove together with the other DNDTURN-TEMP-BOUNDARY-VIZ sites.
 */
public final class RegionBoundaryProtocol {
    private RegionBoundaryProtocol() {}

    public record Point(double x, double y, double z) {}

    public record Boundary(List<Point> anchors, double radius, double minY, double maxY,
                           long version, String dimension) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Boundary> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(DNDTurnNeoForge.MOD_ID, "region_boundary"));
        public static final StreamCodec<ByteBuf, Boundary> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeInt(value.anchors().size());
                for (Point point : value.anchors()) {
                    buffer.writeDouble(point.x());
                    buffer.writeDouble(point.y());
                    buffer.writeDouble(point.z());
                }
                buffer.writeDouble(value.radius());
                buffer.writeDouble(value.minY());
                buffer.writeDouble(value.maxY());
                buffer.writeLong(value.version());
                ByteBufCodecs.STRING_UTF8.encode(buffer, value.dimension());
            },
            buffer -> {
                int count = buffer.readInt();
                if (count < 0 || count > 4096) throw new IllegalArgumentException("invalid anchor count");
                List<Point> anchors = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    anchors.add(new Point(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
                }
                double radius = buffer.readDouble();
                double minY = buffer.readDouble();
                double maxY = buffer.readDouble();
                long version = buffer.readLong();
                return new Boundary(List.copyOf(anchors), radius, minY, maxY, version,
                    ByteBufCodecs.STRING_UTF8.decode(buffer));
            });

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void send(ServerPlayer player, EncounterRegion region) {
        if (region == null) {
            clear(player);
            return;
        }
        if (!NetworkRegistry.hasChannel(player.connection, Boundary.TYPE.id())) return;
        List<Point> anchors = new ArrayList<>(region.anchors().size());
        for (EncounterRegion.Anchor anchor : region.anchors()) {
            EncounterRegion.Point center = anchor.center();
            anchors.add(new Point(center.x(), center.y(), center.z()));
        }
        PacketDistributor.sendToPlayer(player, new Boundary(List.copyOf(anchors),
            region.radius(), region.minY(), region.maxY(), region.version(), region.dimension()));
    }

    public static void clear(ServerPlayer player) {
        if (!NetworkRegistry.hasChannel(player.connection, Boundary.TYPE.id())) return;
        PacketDistributor.sendToPlayer(player, new Boundary(List.of(), 0, 0, 0, 0, ""));
    }
}
