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
    public record PreviewStep(TacticalIntent.Point feet, String kind) {
        public PreviewStep { Objects.requireNonNull(feet); if (!java.util.Set.of("WALK", "STEP", "JUMP", "DROP", "SWIM").contains(kind)) throw new IllegalArgumentException("step kind"); }
    }
    public record Candidate(int id, TacticalIntent.Point feet) {
        public Candidate { Objects.requireNonNull(feet); if (id < 0 || id >= 256) throw new IllegalArgumentException("candidate"); }
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
    public record Ability(String id, String label, TacticalIntent.Capability cost, java.util.Set<TacticalIntent.TargetKind> targets, int version, AbilitySource source) {}
    public record Offer(String label, TacticalIntent intent, String reason, boolean approach, ActionFailure.Details failure) {
        public Offer(String label, TacticalIntent intent, String reason, boolean approach) {
            this(label, intent, reason, approach, reason.isEmpty() ? ActionFailure.Details.NONE : ActionFailure.Details.REJECTED);
        }
        public Offer { Objects.requireNonNull(failure); Objects.requireNonNull(label); Objects.requireNonNull(intent); Objects.requireNonNull(reason); }
    }
    public record Options(UUID generation, UUID encounter, UUID query, long version, java.util.List<Offer> offers,
                          String reason, long revision, TacticalIntent.ItemReference item, String defaultBehavior, java.util.List<Ability> abilities, String itemName,
                          ActionFailure.Details failure) implements CustomPacketPayload {
        public Options(UUID generation, UUID encounter, UUID query, long version, java.util.List<Offer> offers,
                       String reason, long revision, TacticalIntent.ItemReference item, String defaultBehavior, java.util.List<Ability> abilities, String itemName) {
            this(generation, encounter, query, version, offers, reason, revision, item, defaultBehavior, abilities, itemName,
                reason.isEmpty() ? ActionFailure.Details.NONE : ActionFailure.Details.REJECTED);
        }
        public Options { Objects.requireNonNull(failure); abilities = java.util.List.copyOf(abilities); offers = java.util.List.copyOf(offers); Objects.requireNonNull(reason); }
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
                             GridCell waypoint, boolean running, String reason, OperationRecord.Outcome outcome, long version,
                             ExecutionConclusion conclusion, ActionFailure.Details failure, java.util.List<PreviewStep> route, int cursor) implements CustomPacketPayload {
        public Projection(UUID generation, UUID encounter, UUID operation, long sequence,
                          GridCell waypoint, boolean running, String reason, OperationRecord.Outcome outcome, long version,
                          ExecutionConclusion conclusion, ActionFailure.Details failure) {
            this(generation, encounter, operation, sequence, waypoint, running, reason, outcome, version, conclusion, failure, java.util.List.of(), 0);
        }
        public Projection(UUID generation, UUID encounter, UUID operation, long sequence,
                          GridCell waypoint, boolean running, String reason, OperationRecord.Outcome outcome, long version,
                          ExecutionConclusion conclusion) {
            this(generation, encounter, operation, sequence, waypoint, running, reason, outcome, version, conclusion,
                outcome == OperationRecord.Outcome.UNKNOWN ? ActionFailure.Details.UNKNOWN
                    : outcome == OperationRecord.Outcome.REJECTED ? ActionFailure.Details.REJECTED : ActionFailure.Details.NONE);
        }
        public Projection(UUID generation, UUID encounter, UUID operation, long sequence,
                          GridCell waypoint, boolean running, String reason, OperationRecord.Outcome outcome, long version) {
            this(generation, encounter, operation, sequence, waypoint, running, reason, outcome, version, null);
        }
        public static Projection terminal(UUID generation, long sequence, OperationRecord.Result result) {
            if (!result.terminal() || result.snapshot().kind() != OperationRecord.Kind.PLAN) throw new IllegalArgumentException("terminal plan required");
            return new Projection(generation,result.snapshot().encounterId(),result.snapshot().operationId(),sequence,null,false,result.reason(),result.outcome(),result.publishedVersion(),result.conclusion(),result.failure());
        }
        public Projection {
            route = java.util.List.copyOf(route);
            if (route.size() > 128 || cursor < 0 || cursor > route.size()) throw new IllegalArgumentException("route bounds");
            Objects.requireNonNull(failure);
            Objects.requireNonNull(generation); Objects.requireNonNull(encounter); Objects.requireNonNull(operation);
            Objects.requireNonNull(reason);
            if (sequence < 0 || version < 0 || !running && waypoint != null || running && outcome != null || !running && outcome == null) throw new IllegalArgumentException("plan projection");
            if (conclusion != null && (running || conclusion.outcome() != outcome)) throw new IllegalArgumentException("projection conclusion");
        }
        public static final Type<Projection> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "plan_state"));
        // Bounded Gson record codec includes conclusion and invokes its validating canonical constructor.
        public static final StreamCodec<ByteBuf, Projection> CODEC = codec(Projection.class);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
