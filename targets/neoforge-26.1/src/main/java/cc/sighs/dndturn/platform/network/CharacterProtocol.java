package cc.sighs.dndturn.platform.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded creation-screen wire schema. The server validates every submitted value again. */
public final class CharacterProtocol {
    private CharacterProtocol() {}

    public record ClassOption(String id, String name, int hitDie) {}
    public record RaceOption(String id, String name) {}

    /** Empty request that opens the client's linked class-creation screen. */
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
            (buffer, value) -> {}, buffer -> new Open());
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

    public record Options(List<ClassOption> classes, List<RaceOption> races) implements CustomPacketPayload {
        public Options {
            classes = List.copyOf(classes); races = List.copyOf(races);
            if (classes.size() > 128 || races.size() > 128) throw new IllegalArgumentException("character option bound");
        }
        public static final Type<Options> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_options"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Options> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeVarInt(value.classes.size());
            for (var option : value.classes) {
                buffer.writeUtf(option.id(), 128); buffer.writeUtf(option.name(), 128); buffer.writeVarInt(option.hitDie());
            }
            buffer.writeVarInt(value.races.size());
            for (var option : value.races) {
                buffer.writeUtf(option.id(), 128); buffer.writeUtf(option.name(), 128);
            }
        }, buffer -> {
            int classCount = buffer.readVarInt();
            if (classCount < 0 || classCount > 128) throw new IllegalArgumentException("character class option bound");
            var classes = new ArrayList<ClassOption>(classCount);
            for (int i = 0; i < classCount; i++)
                classes.add(new ClassOption(buffer.readUtf(128), buffer.readUtf(128), buffer.readVarInt()));
            int raceCount = buffer.readVarInt();
            if (raceCount < 0 || raceCount > 128) throw new IllegalArgumentException("character race option bound");
            var races = new ArrayList<RaceOption>(raceCount);
            for (int i = 0; i < raceCount; i++) races.add(new RaceOption(buffer.readUtf(128), buffer.readUtf(128)));
            return new Options(classes, races);
        });
        public Type<Options> type() { return TYPE; }
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

    public record Create(String classId, String raceId, int strength, int dexterity, int constitution,                         int intelligence, int wisdom, int charisma) implements CustomPacketPayload {
        public Create {
            Objects.requireNonNull(classId); Objects.requireNonNull(raceId);
            if (classId.isBlank() || classId.length() > 128 || raceId.isBlank() || raceId.length() > 128)
                throw new IllegalArgumentException("character ids");
            for (int value : new int[] {strength, dexterity, constitution, intelligence, wisdom, charisma})
                if (value < 1 || value > 30) throw new IllegalArgumentException("character ability range");
        }
        public static final Type<Create> TYPE = new Type<>(Identifier.fromNamespaceAndPath("dndturn", "character_create"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Create> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeUtf(value.classId(), 128); buffer.writeUtf(value.raceId(), 128);
            buffer.writeByte(value.strength()); buffer.writeByte(value.dexterity()); buffer.writeByte(value.constitution());
            buffer.writeByte(value.intelligence()); buffer.writeByte(value.wisdom()); buffer.writeByte(value.charisma());
        }, buffer -> new Create(buffer.readUtf(128), buffer.readUtf(128), buffer.readByte(), buffer.readByte(),
            buffer.readByte(), buffer.readByte(), buffer.readByte(), buffer.readByte()));
        public Type<Create> type() { return TYPE; }
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
