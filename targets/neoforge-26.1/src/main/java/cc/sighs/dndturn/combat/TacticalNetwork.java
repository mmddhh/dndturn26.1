package cc.sighs.dndturn.combat;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded value messages; no world objects, costs, damage or trusted actor from C2S. */
public final class TacticalNetwork {
    private static final Gson JSON = new Gson();
    private TacticalNetwork() {}
    private static <T> StreamCodec<ByteBuf, T> codec(Class<T> type) {
        var text = ByteBufCodecs.stringUtf8(32768);
        return StreamCodec.of((buffer, value) -> text.encode(buffer, JSON.toJson(value)),
            buffer -> Objects.requireNonNull(JSON.fromJson(text.decode(buffer), type)));
    }
    public record Query(UUID generation, UUID encounter, UUID query, TacticalIntent.Target target,
                        TacticalIntent.Hand hand, int slot, long revision, TacticalIntent.ItemReference expectedItem) implements CustomPacketPayload {
        public Query(UUID generation, UUID encounter, UUID query, TacticalIntent.Target target, TacticalIntent.Hand hand) {
            this(generation, encounter, query, target, hand, hand == TacticalIntent.Hand.MAIN_HAND ? 0 : 40, System.nanoTime(), null);
        }
        public Query { Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(query); Objects.requireNonNull(target); Objects.requireNonNull(hand); }
        public static final Type<Query> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "behavior_query"));
        public static final StreamCodec<ByteBuf, Query> CODEC = codec(Query.class);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Ability(String id, String label, TacticalIntent.Capability cost, java.util.Set<TacticalIntent.TargetKind> targets) {}
    public record Offer(String label, TacticalIntent intent, String reason, boolean approach) {
        public Offer { Objects.requireNonNull(label); Objects.requireNonNull(intent); Objects.requireNonNull(reason); }
    }
    public record Options(UUID generation, UUID encounter, UUID query, long version, java.util.List<Offer> offers,
                          String reason, long revision, TacticalIntent.ItemReference item, String defaultBehavior, java.util.List<Ability> abilities, String itemName) implements CustomPacketPayload {
        public Options { abilities = java.util.List.copyOf(abilities); offers = java.util.List.copyOf(offers); Objects.requireNonNull(reason); }
        public static final Type<Options> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "behavior_options"));
        public static final StreamCodec<ByteBuf, Options> CODEC = codec(Options.class);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Request(UUID generation, UUID encounter, UUID operation, long version,
                          TacticalIntent intent, boolean cancel) implements CustomPacketPayload {
        public Request {
            Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(operation);
            if (version < 0 || !cancel && intent == null || cancel && intent != null)
                throw new IllegalArgumentException("invalid plan request");
        }
        public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "plan_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = codec(Request.class);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Projection(UUID generation, UUID encounter, UUID operation, long sequence,
                             GridCell waypoint, boolean running, String reason, OperationRecord.Outcome outcome, long version) implements CustomPacketPayload {
        public static Projection terminal(UUID generation, long sequence, OperationRecord.Result result) {
            if (!result.terminal() || result.snapshot().kind() != OperationRecord.Kind.PLAN) throw new IllegalArgumentException("terminal plan required");
            return new Projection(generation,result.snapshot().encounterId(),result.snapshot().operationId(),sequence,null,false,result.reason(),result.outcome(),result.publishedVersion());
        }
        public Projection {
            Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(operation);
            Objects.requireNonNull(reason);
            if (sequence < 0 || version < 0 || !running && waypoint != null || running && outcome != null || !running && outcome == null) throw new IllegalArgumentException("plan projection");
        }
        public static final Type<Projection> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "plan_state"));
        public static final StreamCodec<ByteBuf, Projection> CODEC = codec(Projection.class);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
