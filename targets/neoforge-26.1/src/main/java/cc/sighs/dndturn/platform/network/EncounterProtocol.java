package cc.sighs.dndturn.platform.network;

import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.encounter.StartDisposition;
import cc.sighs.dndturn.domain.encounter.TurnParticipant;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.platform.ModIdentity;
import cc.sighs.dndturn.platform.projection.PresentationState;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class EncounterProtocol {
    private EncounterProtocol() {}







    public record EntitySimulation(UUID generation, long sequence, UUID entityId, int runtimeId,
                                   UUID instance, String dimension, boolean tracked,
                                   PresentationState.Facts facts, PresentationState.Movement movement) implements CustomPacketPayload {
        public EntitySimulation {
            if (sequence < 0) throw new IllegalArgumentException("negative entity projection sequence");
            java.util.Objects.requireNonNull(instance);
            java.util.Objects.requireNonNull(facts);
        }
        public boolean paused() { return facts.paused(); }
        public static final Type<EntitySimulation> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "entity_simulation"));
        public static final StreamCodec<ByteBuf, EntitySimulation> STREAM_CODEC = StreamCodec.of((buffer, state) -> {
            writeUuid(buffer, state.generation()); buffer.writeLong(state.sequence());
            writeUuid(buffer, state.entityId()); buffer.writeInt(state.runtimeId()); writeUuid(buffer, state.instance());
            ByteBufCodecs.STRING_UTF8.encode(buffer, state.dimension()); buffer.writeBoolean(state.tracked());
            var facts = state.facts(); writeOptionalId(buffer, facts.member()); writeOptionalId(buffer, facts.controller());
            ByteBufCodecs.STRING_UTF8.encode(buffer, facts.phase()); buffer.writeBoolean(facts.paused());
            buffer.writeLong(facts.environmentTime());
            var use = facts.use(); writeOptionalId(buffer, use.identity()); buffer.writeBoolean(use.hand() == net.minecraft.world.InteractionHand.OFF_HAND);
            ByteBufCodecs.STRING_UTF8.encode(buffer, use.item()); buffer.writeInt(use.used()); buffer.writeInt(use.remaining());
            var movement = state.movement(); buffer.writeBoolean(movement != null);
            if (movement != null) {
                writeUuid(buffer, movement.operation()); buffer.writeInt(movement.step()); buffer.writeLong(movement.sequence());
                buffer.writeInt(movement.cause().ordinal()); buffer.writeDouble(movement.horizontal());
            }
        }, buffer -> {
            UUID generation = readUuid(buffer); long sequence = buffer.readLong(); UUID entity = readUuid(buffer);
            int runtimeId = buffer.readInt(); UUID instance = readUuid(buffer); String dimension = ByteBufCodecs.STRING_UTF8.decode(buffer);
            boolean tracked = buffer.readBoolean(); UUID member = readOptionalId(buffer), controller = readOptionalId(buffer);
            String phase = ByteBufCodecs.STRING_UTF8.decode(buffer); boolean paused = buffer.readBoolean();
            long environmentTime = buffer.readLong();
            var use = new PresentationState.Use(readOptionalId(buffer), buffer.readBoolean() ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND,
                ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readInt(), buffer.readInt());
            var movement = buffer.readBoolean() ? new PresentationState.Movement(readUuid(buffer), buffer.readInt(), buffer.readLong(),
                PresentationState.Motion.values()[buffer.readInt()], buffer.readDouble()) : null;
            return new EntitySimulation(generation, sequence, entity, runtimeId, instance, dimension, tracked,
                new PresentationState.Facts(member, controller, phase, paused, use, environmentTime), movement);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TacticalSwing(UUID generation, UUID encounter, UUID entity, int runtimeId, UUID instance,
                                UUID operation, long sequence, net.minecraft.world.InteractionHand hand,
                                net.minecraft.world.item.component.SwingAnimation animation) implements CustomPacketPayload {
        public TacticalSwing {
            if (sequence < 0 || animation.duration() <= 0) throw new IllegalArgumentException("invalid swing event");
        }
        public static final Type<TacticalSwing> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "tactical_swing"));
        public static final StreamCodec<ByteBuf, TacticalSwing> STREAM_CODEC = StreamCodec.of((b, s) -> {
            writeUuid(b, s.generation()); writeUuid(b, s.encounter()); writeUuid(b, s.entity()); b.writeInt(s.runtimeId());
            writeUuid(b, s.instance()); writeUuid(b, s.operation()); b.writeLong(s.sequence());
            b.writeBoolean(s.hand() == net.minecraft.world.InteractionHand.OFF_HAND);
            net.minecraft.world.item.component.SwingAnimation.STREAM_CODEC.encode(b, s.animation());
        }, b -> new TacticalSwing(readUuid(b), readUuid(b), readUuid(b), b.readInt(), readUuid(b), readUuid(b), b.readLong(),
            b.readBoolean() ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND,
            net.minecraft.world.item.component.SwingAnimation.STREAM_CODEC.decode(b)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeOptionalId(ByteBuf b, UUID id) { b.writeBoolean(id != null); if (id != null) writeUuid(b, id); }
    private static UUID readOptionalId(ByteBuf b) { return b.readBoolean() ? readUuid(b) : null; }







    private static void writeUuid(ByteBuf buffer, UUID id) {
        buffer.writeLong(id.getMostSignificantBits());
        buffer.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    public enum IntentKind {
        START(0), EXIT(1), END_TURN(2), DASH(3), DODGE(4), DISENGAGE(5);
        private final int code;
        IntentKind(int code) { this.code = code; }
        public int code() { return code; }
        public static IntentKind fromCode(int code) {
            for (IntentKind value : values()) if (value.code == code) return value;
            throw new IllegalArgumentException("unknown intent code");
        }
    }

    /** Client data is an untrusted intent; actor identity and target validation live on the server. */
    public record CombatIntent(UUID operationId, UUID generation, UUID encounterId,
                                  long expectedVersion, IntentKind kind, UUID targetId)
        implements CustomPacketPayload {
        public CombatIntent {
            if (expectedVersion < 0)
                throw new IllegalArgumentException("invalid intent version");
        }
        public static CombatIntent start(UUID operationId) {
            return new CombatIntent(operationId, null, null, 0, IntentKind.START, null);
        }
        public static final Type<CombatIntent> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "combat_intent"));
        public static final StreamCodec<ByteBuf, CombatIntent> STREAM_CODEC = StreamCodec.of(
            (buffer, intent) -> {
                writeUuid(buffer, intent.operationId());
                buffer.writeBoolean(intent.generation() != null);
                if (intent.generation() != null) writeUuid(buffer, intent.generation());
                buffer.writeBoolean(intent.encounterId() != null);
                if (intent.encounterId() != null) writeUuid(buffer, intent.encounterId());
                buffer.writeLong(intent.expectedVersion());
                buffer.writeByte(intent.kind().code());
                buffer.writeBoolean(intent.targetId() != null);
                if (intent.targetId() != null) writeUuid(buffer, intent.targetId());
            }, buffer -> {
                UUID operationId = readUuid(buffer);
                UUID generation = buffer.readBoolean() ? readUuid(buffer) : null;
                UUID encounterId = buffer.readBoolean() ? readUuid(buffer) : null;
                long version = buffer.readLong();
                int kindIndex = buffer.readUnsignedByte();
                UUID target = buffer.readBoolean() ? readUuid(buffer) : null;
                return new CombatIntent(operationId, generation, encounterId, version,
                    IntentKind.fromCode(kindIndex), target);
            });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record IntentStatus(UUID generation, UUID encounterId, UUID operationId,
                               IntentKind kind, boolean accepted, String reason,
                               boolean replay, int outcome, int actualMovementTicks,
                               float actualDamage, StartDisposition startState,
                               ActionFailure.Code code, ActionFailure.Retry retry)
        implements CustomPacketPayload {
        public IntentStatus(UUID generation, UUID encounterId, UUID operationId, IntentKind kind,
                            boolean accepted, String reason, boolean replay, int outcome,
                            int actualMovementTicks, float actualDamage, StartDisposition startState) {
            this(generation, encounterId, operationId, kind, accepted, reason, replay, outcome,
                actualMovementTicks, actualDamage, startState,
                accepted ? ActionFailure.Code.NONE : ActionFailure.Code.REJECTED, ActionFailure.Retry.NONE);
        }
        public static IntentStatus rejected(UUID generation, UUID encounterId, UUID operationId,
                                             IntentKind kind, RuntimeException failure) {
            return new IntentStatus(generation, encounterId, operationId, kind, false,
                failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                false, -1, 0, 0, kind == IntentKind.START ? StartDisposition.FAILED : StartDisposition.NONE,
                failure instanceof ActionFailure typed ? typed.code() : ActionFailure.Code.REJECTED,
                failure instanceof ActionFailure typed ? typed.retry() : ActionFailure.Retry.NONE);
        }
        public IntentStatus(UUID generation, UUID encounterId, UUID operationId, IntentKind kind,
                            boolean accepted, String reason, boolean replay, int outcome,
                            int actualMovementTicks, float actualDamage) {
            this(generation, encounterId, operationId, kind, accepted, reason, replay, outcome,
                actualMovementTicks, actualDamage, kind != IntentKind.START ? StartDisposition.NONE
                    : !accepted ? StartDisposition.FAILED
                    : encounterId == null ? StartDisposition.WAITING : StartDisposition.STARTED);
        }
        public IntentStatus(UUID generation, UUID encounterId, UUID operationId,
                            IntentKind kind, boolean accepted, String reason) {
            this(generation, encounterId, operationId, kind, accepted, reason, false, -1, 0, 0);
        }

        public IntentStatus {
            java.util.Objects.requireNonNull(code);
            java.util.Objects.requireNonNull(retry);
            if (replay) OperationRecord.Outcome.fromCode(outcome);
            if (replay && !accepted
                || actualMovementTicks < 0 || !Float.isFinite(actualDamage) || actualDamage < 0)
                throw new IllegalArgumentException("invalid intent result status");
        }

        public static final Type<IntentStatus> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "intent_status"));
        public static final StreamCodec<ByteBuf, IntentStatus> STREAM_CODEC = StreamCodec.of(
            (buffer, status) -> {
                writeUuid(buffer, status.generation());
                buffer.writeBoolean(status.encounterId() != null);
                if (status.encounterId() != null) writeUuid(buffer, status.encounterId());
                writeUuid(buffer, status.operationId());
                buffer.writeByte(status.kind().code());
                buffer.writeBoolean(status.accepted());
                ByteBufCodecs.STRING_UTF8.encode(buffer, status.reason());
                buffer.writeBoolean(status.replay());
                buffer.writeByte(status.outcome());
                buffer.writeInt(status.actualMovementTicks());
                buffer.writeFloat(status.actualDamage());
                buffer.writeByte(status.startState().code());
                ByteBufCodecs.stringUtf8(64).encode(buffer, status.code().name());
                ByteBufCodecs.stringUtf8(64).encode(buffer, status.retry().name());
            }, buffer -> {
                UUID generation = readUuid(buffer);
                UUID encounterId = buffer.readBoolean() ? readUuid(buffer) : null;
                UUID operationId = readUuid(buffer);
                int kind = buffer.readUnsignedByte();
                return new IntentStatus(generation, encounterId, operationId,
                    IntentKind.fromCode(kind), buffer.readBoolean(),
                    ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readBoolean(),
                    buffer.readByte(), buffer.readInt(), buffer.readFloat(), StartDisposition.fromCode(buffer.readUnsignedByte()),
                    ActionFailure.Code.valueOf(ByteBufCodecs.stringUtf8(64).decode(buffer)),
                    ActionFailure.Retry.valueOf(ByteBufCodecs.stringUtf8(64).decode(buffer)));
            });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Advisory only. -1 means no measured distance (no enemies or unknown facts, distinguished by code). */
    public record ExitNotice(ActionFailure.Code code, double nearestDistance, double requiredDistance) {
        public ExitNotice {
            java.util.Objects.requireNonNull(code);
            if (!Double.isFinite(nearestDistance) || nearestDistance < -1
                || !Double.isFinite(requiredDistance) || requiredDistance <= 0)
                throw new IllegalArgumentException("invalid exit projection");
        }
    }

    /** Display facts only; instant receipts never authorize another application. */
    public record EffectNotice(String id, int level, int ticks, long rounds, boolean instant, String support) {
        public EffectNotice {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 128
                || level < 1 || level > 256 || ticks < -1 || rounds < -1 || support == null || support.length() > 32)
                throw new IllegalArgumentException("invalid effect projection");
        }
    }

    /** Current participant state only; result history is never broadcast every tick. */
    public record EncounterState(UUID generation, UUID encounterId, long sessionSequence,
                                 boolean active, long version,
                                 EncounterPhase phase, long round, UUID current, long regionVersion,
                                 int movementTicks, boolean action, boolean reaction,
                                 int environmentRemaining, boolean moving, UUID movementOperationId, List<MemberNotice> members, int resultCount, boolean interactive,
                                 long projectionRevision, int movementTicksPerTurn, ExitNotice exit, List<EffectNotice> effects)
        implements CustomPacketPayload {
        public EncounterState(UUID generation, UUID encounterId, long sessionSequence,
                              boolean active, long version, EncounterPhase phase, long round, UUID current,
                              long regionVersion, int movementTicks, boolean action, boolean reaction,
                              int environmentRemaining, boolean moving, UUID movementOperationId,
                              List<MemberNotice> members, int resultCount, boolean interactive, long projectionRevision,
                              int movementTicksPerTurn) {
            this(generation, encounterId, sessionSequence, active, version, phase, round, current,
                regionVersion, movementTicks, action, reaction, environmentRemaining, moving,
                movementOperationId, members, resultCount, interactive, projectionRevision, movementTicksPerTurn, null, List.of());
        }
        public EncounterState(UUID generation, UUID encounterId, long sessionSequence,
                              boolean active, long version, EncounterPhase phase, long round, UUID current,
                              long regionVersion, int movementTicks, boolean action, boolean reaction,
                              int environmentRemaining, boolean moving, UUID movementOperationId,
                              List<MemberNotice> members, int resultCount, boolean interactive, long projectionRevision) {
            this(generation, encounterId, sessionSequence, active, version, phase, round, current,
                regionVersion, movementTicks, action, reaction, environmentRemaining, moving,
                movementOperationId, members, resultCount, interactive, projectionRevision, 0);
        }
        public EncounterState(UUID generation, UUID encounterId, long sessionSequence,
                              boolean active, long version, int phase, long round, UUID current,
                              long regionVersion, int movementTicks, boolean action, boolean reaction,
                              int environmentRemaining, boolean moving, UUID movementOperationId,
                              List<MemberNotice> members, int resultCount, boolean interactive) {
            this(generation, encounterId, sessionSequence, active, version, EncounterPhase.fromCode(phase), round, current,
                regionVersion, movementTicks, action, reaction, environmentRemaining, moving,
                movementOperationId, members, resultCount, interactive, 0);
        }
        public EncounterState {
            members = List.copyOf(members);
            effects = List.copyOf(effects);
            if (effects.size() > 256) throw new IllegalArgumentException("effect projection limit");
            if (resultCount < 0 || projectionRevision < 0 || movementTicksPerTurn < 0)
                throw new IllegalArgumentException("invalid projection count or movement budget");
        }
        public EncounterState(UUID generation, UUID encounterId, long sessionSequence,
                              boolean active, long version, int phase, long round, UUID current,
                              long regionVersion, int movementTicks, boolean action, boolean reaction,
                              int environmentRemaining, boolean moving, UUID movementOperationId,
                              List<MemberNotice> members) {
            this(generation, encounterId, sessionSequence, active, version, phase, round, current,
                regionVersion, movementTicks, action, reaction, environmentRemaining, moving,
                movementOperationId, members, 0, false);
        }
        public EncounterState(UUID generation, UUID encounterId, long sessionSequence,
                              boolean active, long version, int phase, long round, UUID current,
                              long regionVersion, int movementTicks, boolean action, boolean reaction,
                              int environmentRemaining, boolean moving, UUID movementOperationId) {
            this(generation, encounterId, sessionSequence, active, version, phase, round, current,
                regionVersion, movementTicks, action, reaction, environmentRemaining, moving,
                movementOperationId, List.of(), 0, false);
        }
        public EncounterState(UUID generation, UUID encounterId, long sessionSequence,
                              boolean active, long version, int phase, long round, UUID current,
                              long regionVersion, int movementTicks, boolean action, boolean reaction,
                              int environmentRemaining, boolean moving, UUID movementOperationId,
                              List<MemberNotice> members, int resultCount) {
            this(generation, encounterId, sessionSequence, active, version, phase, round, current,
                regionVersion, movementTicks, action, reaction, environmentRemaining, moving,
                movementOperationId, members, resultCount, false);
        }
        public static final Type<EncounterState> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "encounter_state"));
        public static final StreamCodec<ByteBuf, EncounterState> STREAM_CODEC = StreamCodec.of(
            (buffer, state) -> {
                writeUuid(buffer, state.generation());
                writeUuid(buffer, state.encounterId());
                buffer.writeLong(state.sessionSequence());
                buffer.writeLong(state.projectionRevision());
                buffer.writeBoolean(state.active());
                buffer.writeLong(state.version());
                buffer.writeByte(state.phase().code());
                buffer.writeLong(state.round());
                buffer.writeBoolean(state.current() != null);
                if (state.current() != null) writeUuid(buffer, state.current());
                buffer.writeLong(state.regionVersion());
                buffer.writeInt(state.movementTicks());
                buffer.writeInt(state.movementTicksPerTurn());
                buffer.writeBoolean(state.action());
                buffer.writeBoolean(state.reaction());
                buffer.writeInt(state.environmentRemaining());
                buffer.writeBoolean(state.moving());
                buffer.writeBoolean(state.movementOperationId() != null);
                if (state.movementOperationId() != null) writeUuid(buffer, state.movementOperationId());
                buffer.writeBoolean(state.interactive());
                buffer.writeBoolean(state.exit() != null);
                if (state.exit() != null) {
                    ByteBufCodecs.STRING_UTF8.encode(buffer, state.exit().code().name());
                    buffer.writeDouble(state.exit().nearestDistance());
                    buffer.writeDouble(state.exit().requiredDistance());
                }
                buffer.writeInt(state.effects().size());
                for (var effect : state.effects()) {
                    ByteBufCodecs.stringUtf8(128).encode(buffer, effect.id());
                    buffer.writeInt(effect.level()); buffer.writeInt(effect.ticks()); buffer.writeLong(effect.rounds());
                    buffer.writeBoolean(effect.instant()); ByteBufCodecs.stringUtf8(32).encode(buffer, effect.support());
                }
                buffer.writeInt(state.resultCount());
                buffer.writeInt(state.members().size());
                for (MemberNotice member : state.members()) {
                    writeUuid(buffer, member.id());
                    buffer.writeByte(member.kind().ordinal());
                    ByteBufCodecs.STRING_UTF8.encode(buffer, member.name());
                    buffer.writeInt(member.initiative());
                    buffer.writeLong(member.eligibleRound());
                    buffer.writeBoolean(member.dodging());
                    buffer.writeBoolean(member.disengaged());
                    buffer.writeInt(member.movementTicks());
                    buffer.writeBoolean(member.action());
                    buffer.writeBoolean(member.reaction());
                }
            }, buffer -> {
                UUID generation = readUuid(buffer);
                UUID encounterId = readUuid(buffer);
                long sessionSequence = buffer.readLong();
                long projectionRevision = buffer.readLong();
                boolean active = buffer.readBoolean();
                long version = buffer.readLong();
                EncounterPhase phase = EncounterPhase.fromCode(buffer.readUnsignedByte());
                long round = buffer.readLong();
                UUID current = buffer.readBoolean() ? readUuid(buffer) : null;
                long regionVersion = buffer.readLong();
                int movementTicks = buffer.readInt();
                int movementTicksPerTurn = buffer.readInt();
                boolean action = buffer.readBoolean();
                boolean reaction = buffer.readBoolean();
                int environmentRemaining = buffer.readInt();
                boolean moving = buffer.readBoolean();
                UUID movementOperationId = buffer.readBoolean() ? readUuid(buffer) : null;
                boolean interactive = buffer.readBoolean();
                ExitNotice exit = buffer.readBoolean() ? new ExitNotice(
                    ActionFailure.Code.valueOf(ByteBufCodecs.stringUtf8(64).decode(buffer)),
                    buffer.readDouble(), buffer.readDouble()) : null;
                int effectCount = buffer.readInt();
                if (effectCount < 0 || effectCount > 256) throw new IllegalArgumentException("effect projection limit");
                List<EffectNotice> effects = new ArrayList<>(effectCount);
                for (int i = 0; i < effectCount; i++) effects.add(new EffectNotice(
                    ByteBufCodecs.stringUtf8(128).decode(buffer), buffer.readInt(), buffer.readInt(), buffer.readLong(),
                    buffer.readBoolean(), ByteBufCodecs.stringUtf8(32).decode(buffer)));
                int resultCount = buffer.readInt();
                int size = buffer.readInt();
                if (size < 0 || size > 4096) throw new IllegalArgumentException("invalid roster size");
                List<MemberNotice> members = new ArrayList<>(size);
                for (int i = 0; i < size; i++) members.add(new MemberNotice(readUuid(buffer), participantKind(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readInt(), buffer.readLong(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readInt(),
                    buffer.readBoolean(), buffer.readBoolean()));
                return new EncounterState(generation, encounterId, sessionSequence, active, version,
                    phase, round, current, regionVersion, movementTicks, action, reaction,
                    environmentRemaining, moving, movementOperationId, members, resultCount, interactive, projectionRevision, movementTicksPerTurn, exit, effects);
            });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Ordered by authoritative initiative, including members eligible next round. */
    public record MemberNotice(UUID id, TurnParticipant.Kind kind, String name, int initiative, long eligibleRound,
                               boolean dodging, boolean disengaged, int movementTicks,
                               boolean action, boolean reaction) {
        public MemberNotice(UUID id, String name, int initiative, long eligibleRound, boolean dodging,
                            boolean disengaged, int movementTicks, boolean action, boolean reaction) {
            this(id, TurnParticipant.Kind.ENTITY, name, initiative, eligibleRound, dodging, disengaged, movementTicks, action, reaction);
        }
        public MemberNotice {
            java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(kind);
            if (kind == TurnParticipant.Kind.ENVIRONMENT && (initiative != -1 || action || reaction || dodging || disengaged))
                throw new IllegalArgumentException("invalid environment participant");
        }
    }
    private static TurnParticipant.Kind participantKind(ByteBuf buffer) {
        int value = buffer.readUnsignedByte();
        if (value >= TurnParticipant.Kind.values().length) throw new IllegalArgumentException("invalid participant kind");
        return TurnParticipant.Kind.values()[value];
    }

    public record ConsentMember(UUID id, String name, boolean agreed) {}

    public record ConsentState(UUID generation, long sequence, UUID requestId, long revision,
                               long deadline, long serverTick, boolean active,
                               List<ConsentMember> members, String reason) implements CustomPacketPayload {
        public ConsentState {
            members = List.copyOf(members);
            if (sequence < 0 || revision < 0 || deadline < 0 || serverTick < 0)
                throw new IllegalArgumentException("invalid consent clock");
        }
        public static final Type<ConsentState> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "consent_state"));
        public static final StreamCodec<ByteBuf, ConsentState> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
            writeUuid(buffer, value.generation()); buffer.writeLong(value.sequence());
            writeUuid(buffer, value.requestId()); buffer.writeLong(value.revision());
            buffer.writeLong(value.deadline()); buffer.writeLong(value.serverTick()); buffer.writeBoolean(value.active());
            buffer.writeInt(value.members().size());
            for (ConsentMember member : value.members()) {
                writeUuid(buffer, member.id()); ByteBufCodecs.STRING_UTF8.encode(buffer, member.name());
                buffer.writeBoolean(member.agreed());
            }
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.reason());
        }, buffer -> {
            UUID generation = readUuid(buffer); long sequence = buffer.readLong(); UUID id = readUuid(buffer);
            long revision = buffer.readLong(), deadline = buffer.readLong(), tick = buffer.readLong();
            boolean active = buffer.readBoolean(); int count = buffer.readInt();
            if (count < 0 || count > 4096) throw new IllegalArgumentException("invalid consent roster size");
            List<ConsentMember> members = new ArrayList<>(count);
            for (int i = 0; i < count; i++) members.add(new ConsentMember(readUuid(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readBoolean()));
            return new ConsentState(generation, sequence, id, revision, deadline, tick, active,
                members, ByteBufCodecs.STRING_UTF8.decode(buffer));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ConsentReply(UUID generation, UUID requestId, UUID operationId, UUID playerId,
                               long revision, boolean agree) implements CustomPacketPayload {
        public static final Type<ConsentReply> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "consent_reply"));
        public static final StreamCodec<ByteBuf, ConsentReply> STREAM_CODEC = StreamCodec.of((buffer, value) -> {
            writeUuid(buffer, value.generation()); writeUuid(buffer, value.requestId());
            writeUuid(buffer, value.operationId()); writeUuid(buffer, value.playerId());
            buffer.writeLong(value.revision()); buffer.writeBoolean(value.agree());
        }, buffer -> new ConsentReply(readUuid(buffer), readUuid(buffer), readUuid(buffer), readUuid(buffer),
            buffer.readLong(), buffer.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BodyState(UUID generation, long sequence, boolean bodyPaused,
                            boolean movementAllowed, boolean activeInputControlled) implements CustomPacketPayload {
        public BodyState(UUID generation, long sequence, boolean bodyPaused, boolean movementAllowed) {
            this(generation, sequence, bodyPaused, movementAllowed, bodyPaused);
        }
        public static final Type<BodyState> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "body_state"));
        public static final StreamCodec<ByteBuf, BodyState> STREAM_CODEC = StreamCodec.of(
            (buffer, state) -> {
                writeUuid(buffer, state.generation());
                buffer.writeLong(state.sequence());
                buffer.writeBoolean(state.bodyPaused());
                buffer.writeBoolean(state.movementAllowed());
                buffer.writeBoolean(state.activeInputControlled());
            }, buffer -> new BodyState(readUuid(buffer), buffer.readLong(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
