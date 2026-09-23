package cc.sighs.dndturn.platform.network;

import cc.sighs.dndturn.application.inspection.ActorViews;
import cc.sighs.dndturn.application.inspection.InspectionPolicy;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Fixed bounded wire schema. Snapshots and caller-supplied read contracts never cross the wire. */
public final class InspectionProtocol {
    private InspectionProtocol() {}
    public enum Status { OK, UNAVAILABLE, STALE_SESSION, RATE_LIMITED }
    public record Query(UUID generation, UUID encounter, UUID request, UUID target) implements CustomPacketPayload {
        public Query { Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(request); Objects.requireNonNull(target); }
        public static final Type<Query> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "inspection_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Query> CODEC = StreamCodec.of(
            (b, q) -> { b.writeUUID(q.generation); b.writeUUID(q.encounter); b.writeUUID(q.request); b.writeUUID(q.target); },
            b -> new Query(b.readUUID(), b.readUUID(), b.readUUID(), b.readUUID()));
        public Type<Query> type() { return TYPE; }
    }
    public record Reply(Query query, Status status, ActorViews.InspectionView view) implements CustomPacketPayload {
        public Reply {
            Objects.requireNonNull(query); Objects.requireNonNull(status);
            if ((status == Status.OK) != (view != null) || view != null && !view.actor().equals(query.target()))
                throw new IllegalArgumentException("inspection response");
            if (view != null) {
                int bytes = 128;
                for (var field : view.fields()) bytes += 8 + field.id().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + field.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (bytes > 16384) throw new IllegalArgumentException("inspection byte budget");
            }
        }
        public static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "inspection_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of((b, r) -> {
            Query.CODEC.encode(b, r.query); b.writeEnum(r.status);
            if (r.view != null) {
                b.writeUUID(r.view.instance()); b.writeUUID(r.view.capture()); b.writeVarInt(r.view.fields().size());
                for (var f : r.view.fields()) { b.writeUtf(f.id(), 128); b.writeUtf(f.value(), 256); }
            }
        }, b -> {
            if (b.readableBytes() > 16384) throw new IllegalArgumentException("inspection byte budget");
            var query = Query.CODEC.decode(b); var status = b.readEnum(Status.class);
            ActorViews.InspectionView view = null;
            if (status == Status.OK) {
                var instance = b.readUUID(); var capture = b.readUUID(); int count = b.readVarInt();
                if (count < 0 || count > 32) throw new IllegalArgumentException("inspection field budget");
                var fields = new ArrayList<InspectionPolicy.Field>(count);
                for (int i = 0; i < count; i++) fields.add(new InspectionPolicy.Field(b.readUtf(128), b.readUtf(256)));
                view = new ActorViews.InspectionView(query.target(), instance, capture, fields);
            }
            return new Reply(query, status, view);
        });
        public Type<Reply> type() { return TYPE; }
    }
}
