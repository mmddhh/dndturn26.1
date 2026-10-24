package cc.sighs.dndturn.platform.server.persistence;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.actor.ActorRuntimeState;
import cc.sighs.dndturn.domain.actor.ActorStates;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EffectInstance;
import cc.sighs.dndturn.domain.encounter.operation.TriggeredExecutionRecord;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Actor checkpoint validated against current value contracts. SavedData dirty is not a world transaction. */
public final class ActorSavedData extends SavedData {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Map<String, Class<? extends ActorStates.Change>> CHANGES = Map.ofEntries(
            Map.entry("learn", ActorStates.Learn.class), Map.entry("forget", ActorStates.Forget.class), Map.entry("prepare", ActorStates.Prepare.class),
            Map.entry("resource", ActorStates.Resource.class), Map.entry("apply", ActorStates.Apply.class), Map.entry("remove", ActorStates.Remove.class),
            Map.entry("advance", ActorStates.Advance.class), Map.entry("death", ActorStates.Death.class),
            Map.entry("add_stacks", ActorStates.AddStacks.class), Map.entry("consume_stacks", ActorStates.ConsumeStacks.class),
            Map.entry("refresh_duration", ActorStates.RefreshDuration.class));
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(ActorStates.Change.class, new ChangeCodec())
            .registerTypeAdapter(EffectDefinition.class, new DefinitionCodec())
            .registerTypeAdapter(AbilityDefinition.class, new AbilityDefinitionCodec()).create();
    private static final class AbilityDefinitionCodec implements JsonSerializer<AbilityDefinition>, JsonDeserializer<AbilityDefinition> {
        public JsonElement serialize(AbilityDefinition value, java.lang.reflect.Type type, JsonSerializationContext context) {
            var result = new JsonObject(); result.addProperty("id", value.id()); result.addProperty("version", value.version()); return result;
        }
        public AbilityDefinition deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext context) {
            var value = json.getAsJsonObject();
            return AbilityAdapterRegistry.definitions().require(value.get("id").getAsString(), value.get("version").getAsInt());
        }
    }
    /** Persistent references resolve only against the startup-frozen definitions; no embedded executable semantics. */
    private static final class DefinitionCodec implements JsonSerializer<EffectDefinition>, JsonDeserializer<EffectDefinition> {
        public JsonElement serialize(EffectDefinition value, java.lang.reflect.Type type, JsonSerializationContext context) {
            var result = new JsonObject(); result.addProperty("id", value.id()); result.addProperty("version", value.version()); return result;
        }
        public EffectDefinition deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext context) {
            var value = json.getAsJsonObject();
            return AbilityAdapterRegistry.effects().require(value.get("id").getAsString(), value.get("version").getAsInt());
        }
    }
    private static final class ChangeCodec implements JsonSerializer<ActorStates.Change>, JsonDeserializer<ActorStates.Change> {
        public JsonElement serialize(ActorStates.Change value, java.lang.reflect.Type type, JsonSerializationContext context) {
            var result = new JsonObject();
            String kind = CHANGES.entrySet().stream().filter(e -> e.getValue() == value.getClass()).findFirst().orElseThrow().getKey();
            result.addProperty("kind", kind); result.add("value", context.serialize(value, value.getClass())); return result;
        }
        public ActorStates.Change deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext context) {
            var root = json.getAsJsonObject(); var target = CHANGES.get(root.get("kind").getAsString());
            if (target == null) throw new JsonParseException("unknown actor command kind");
            return context.deserialize(root.get("value"), target);
        }
    }
    private record Checkpoint(Map<UUID, ActorStates.State> actors, List<ActorStates.Receipt> receipts,
                              Map<UUID, String> faults, List<ActorStates.ReactionDelivery> deliveries, List<TriggeredExecutionRecord> invocations,
                              Map<UUID, List<ActorStates.SimulationRange>> simulationArchive) {
        Checkpoint {
            actors = Map.copyOf(actors); receipts = List.copyOf(receipts);
            faults = Map.copyOf(faults);
            deliveries = List.copyOf(deliveries);
            invocations = List.copyOf(invocations);
            simulationArchive = Map.copyOf(simulationArchive);
        }
    }
    private static final Codec<ActorSavedData> CODEC = Codec.STRING.listOf().xmap(
            chunks -> new ActorSavedData(join(chunks)), data -> split(data.json));
    public static final SavedDataType<ActorSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("dndturn", "actor_state"), ActorSavedData::new, CODEC);
    private String json;
    public ActorSavedData() { this(""); }
    ActorSavedData(String json) { this.json = json; }
    private static String join(List<String> chunks) {
        long length = chunks.stream().mapToLong(s -> s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
        if (length > MAX_BYTES || chunks.size() > 1024) throw new IllegalArgumentException("actor checkpoint size");
        return String.join("", chunks);
    }
    private static List<String> split(String json) {
        var chunks = new ArrayList<String>();
        for (int start = 0; start < json.length(); start += 30000) chunks.add(json.substring(start, Math.min(start + 30000, json.length())));
        return List.copyOf(chunks);
    }
    String json() { return json; }
    public ActorStates restore() {
        var owner = new ActorStates();
        if (json.isBlank()) return owner;
        int depth = 0; boolean quoted = false, escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char ch = json.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
            } else if (ch == '"') quoted = true;
            else if (ch == '{' || ch == '[') {
                if (++depth > 64) throw new IllegalArgumentException("actor checkpoint nesting");
            } else if (ch == '}' || ch == ']') {
                if (--depth < 0) throw new IllegalArgumentException("actor checkpoint structure");
            }
        }
        if (depth != 0 || quoted) throw new IllegalArgumentException("actor checkpoint structure");
        var document = JsonParser.parseString(json).getAsJsonObject();
        if (document.size() != Checkpoint.class.getRecordComponents().length)
            throw new IllegalArgumentException("unexpected actor checkpoint fields");
        for (String field : List.of("actors", "receipts", "faults", "deliveries", "invocations", "simulationArchive"))
            if (!document.has(field) || document.get(field).isJsonNull())
                throw new IllegalArgumentException("missing actor checkpoint field: " + field);
        var saved = GSON.fromJson(document, Checkpoint.class);
        var states = new LinkedHashMap<UUID, ActorStates.State>();
        saved.actors().forEach((id, state) -> {
            var effects = new LinkedHashMap<UUID, EffectInstance>();
            state.runtime().effects().forEach((key, value) -> { if (value.definition().persist()) effects.put(key, value); });
            long revision = effects.size() == state.runtime().effects().size() ? state.revision() : Math.addExact(state.revision(), 1);
            states.put(id, new ActorStates.State(revision, state.persistent(), new ActorRuntimeState(effects), state.clocks()));
        });
        owner.restore(states, saved.receipts()); owner.restoreFaults(saved.faults()); owner.restoreDeliveries(saved.deliveries());
        owner.restoreInvocations(saved.invocations());
        owner.restoreSimulationArchive(saved.simulationArchive());
        return owner;
    }
    public void update(ActorStates owner) {
        String encoded = GSON.toJson(new Checkpoint(owner.snapshot(), owner.receipts(), owner.faults(), owner.deliveries(), owner.invocations(),
                owner.simulationArchive()));
        if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalStateException("actor checkpoint capacity");
        if (!encoded.equals(json)) { json = encoded; setDirty(); }
    }
}
