package cc.sighs.dndturn.platform.network;

import java.util.UUID;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded creation-screen wire schema. The server validates every submitted value again. */
public final class CharacterProtocol {
    private CharacterProtocol() {}

    public record ClassOption(String id, String name, int hitDie) {}

    public record DetailRequest(UUID page, String revision, String classId) implements CustomPacketPayload {
        public static final Type<DetailRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_detail_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DetailRequest> CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeUUID(value.page()); buffer.writeUtf(value.revision(), 128); buffer.writeUtf(value.classId(), 128); },
            buffer -> new DetailRequest(buffer.readUUID(), buffer.readUtf(128), buffer.readUtf(128)));
        public Type<DetailRequest> type() { return TYPE; }
    }

    public record Detail(String json) implements CustomPacketPayload {
        public Detail { if (json.isEmpty() || json.length() > 32767) throw new IllegalArgumentException("character projection bound"); }
        public static final Type<Detail> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_detail"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Detail> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.json(), 32767), buffer -> new Detail(buffer.readUtf(32767)));
        public Type<Detail> type() { return TYPE; }
    }

    public record CreationResult(String json) implements CustomPacketPayload {
        public CreationResult { if (json.isEmpty() || json.length() > 32767) throw new IllegalArgumentException("character projection bound"); }
        public static final Type<CreationResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_creationresult"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CreationResult> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.json(), 32767), buffer -> new CreationResult(buffer.readUtf(32767)));
        public Type<CreationResult> type() { return TYPE; }
    }

    public record Open(String json) implements CustomPacketPayload {
        public Open { if (json.isEmpty() || json.length() > 32767) throw new IllegalArgumentException("character projection bound"); }
        public static final Type<Open> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.json(), 32767), buffer -> new Open(buffer.readUtf(32767)));
        public Type<Open> type() { return TYPE; }
    }

    /** Client request for whichever character page is appropriate. */
    public record RequestUi() implements CustomPacketPayload {
        public static final Type<RequestUi> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_request_ui"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestUi> CODEC = StreamCodec.of(
            (buffer, value) -> {}, buffer -> new RequestUi());
        public Type<RequestUi> type() { return TYPE; }
    }

    /** Bound JSON emitted by the linked classes creation page. */
    public record CreateJson(String json) implements CustomPacketPayload {
        public CreateJson {
            Objects.requireNonNull(json);
            if (json.isEmpty() || json.length() > 32767) throw new IllegalArgumentException("character payload bound");
        }
        public static final Type<CreateJson> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_create_json"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateJson> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.json(), 32767), buffer -> new CreateJson(buffer.readUtf(32767)));
        public Type<CreateJson> type() { return TYPE; }
    }

    /** Server-built sheet payload consumed by the classes page's sheet mode. */
    public record Sheet(String json) implements CustomPacketPayload {
        public Sheet {
            Objects.requireNonNull(json);
            if (json.isEmpty() || json.length() > 32767) throw new IllegalArgumentException("character sheet bound");
        }
        public static final Type<Sheet> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_sheet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sheet> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.json(), 32767), buffer -> new Sheet(buffer.readUtf(32767)));
        public Type<Sheet> type() { return TYPE; }
    }

    /** Action button selected on the sheet page. */
    public record SheetAction(String action) implements CustomPacketPayload {
        public SheetAction {
            Objects.requireNonNull(action);
            if (!action.equals("asi") && !action.equals("subclass")) throw new IllegalArgumentException("sheet action");
        }
        public static final Type<SheetAction> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_sheet_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SheetAction> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.action(), 16), buffer -> new SheetAction(buffer.readUtf(16)));
        public Type<SheetAction> type() { return TYPE; }
    }

    /** Server-opened ASI page data. */
    public record AsiOpen(String json) implements CustomPacketPayload {
        public AsiOpen {
            Objects.requireNonNull(json);
            if (json.isEmpty() || json.length() > 16384) throw new IllegalArgumentException("ASI page bound");
        }
        public static final Type<AsiOpen> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_asi_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AsiOpen> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.json(), 16384), buffer -> new AsiOpen(buffer.readUtf(16384)));
        public Type<AsiOpen> type() { return TYPE; }
    }

    /** ASI page selection payload. */
    public record AsiChoice(String json) implements CustomPacketPayload {
        public AsiChoice {
            Objects.requireNonNull(json);
            if (json.isEmpty() || json.length() > 4096) throw new IllegalArgumentException("ASI choice bound");
        }
        public static final Type<AsiChoice> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_asi_choice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AsiChoice> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.json(), 4096), buffer -> new AsiChoice(buffer.readUtf(4096)));
        public Type<AsiChoice> type() { return TYPE; }
    }

    /** Server-opened subclass choice data. */
    public record SubclassOpen(String json) implements CustomPacketPayload {
        public SubclassOpen {
            Objects.requireNonNull(json);
            if (json.isEmpty() || json.length() > 16384) throw new IllegalArgumentException("subclass page bound");
        }
        public static final Type<SubclassOpen> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_subclass_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SubclassOpen> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.json(), 16384), buffer -> new SubclassOpen(buffer.readUtf(16384)));
        public Type<SubclassOpen> type() { return TYPE; }
    }

    public record SubclassChoice(String id) implements CustomPacketPayload {
        public SubclassChoice {
            Objects.requireNonNull(id);
            if (id.isBlank() || id.length() > 128) throw new IllegalArgumentException("subclass id");
        }
        public static final Type<SubclassChoice> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_subclass_choice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SubclassChoice> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUtf(value.id(), 128), buffer -> new SubclassChoice(buffer.readUtf(128)));
        public Type<SubclassChoice> type() { return TYPE; }
    }

    public record Reply(boolean success, String message) implements CustomPacketPayload {
        public Reply {
            Objects.requireNonNull(message);
            if (message.length() > 256) message = message.substring(0, 256);
        }
        public static final Type<Reply> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeBoolean(value.success()); buffer.writeUtf(value.message(), 256); },
            buffer -> new Reply(buffer.readBoolean(), buffer.readUtf(256)));
        public Type<Reply> type() { return TYPE; }
    }
}
