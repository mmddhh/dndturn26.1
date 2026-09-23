package cc.sighs.dndturn.combat;

import cc.sighs.dndturn.DNDTurnNeoForge261;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Fixed-version SavedData wrapper. The serialized body contains only common value records. */
public final class CombatSavedData extends SavedData {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int NBT_STRING_CHUNK = 30000;
    private static final Codec<CombatSavedData> CODEC = Codec.either(Codec.STRING,
        Codec.STRING.listOf()).xmap(value -> new CombatSavedData(value.map(
            text -> text, chunks -> String.join("", chunks))),
            data -> Either.right(split(data.json)));
    public static final SavedDataType<CombatSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(DNDTurnNeoForge261.MOD_ID, "combat_state"),
        CombatSavedData::new, CODEC);

    private String json;
    private CombatStateSnapshot encodedRules;
    private String encodedRuleJson;
    private record EncodedState(long version, long structuralRevision, String json) {}
    private final java.util.Map<java.util.UUID, EncodedState> activeEncoding = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, EncodedState> closedEncoding = new java.util.HashMap<>();

    public CombatSavedData() { this(""); }

    private CombatSavedData(String json) { this.json = json; }

    private static List<String> split(String value) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < value.length(); start += NBT_STRING_CHUNK)
            chunks.add(value.substring(start, Math.min(value.length(), start + NBT_STRING_CHUNK)));
        if (chunks.isEmpty()) chunks.add("");
        return chunks;
    }

    public String json() { return json; }

    public CombatPersistenceEnvelope envelope() {
        if (json.isBlank()) return null;
        var root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("rules")) {
            migrateRuleBudgets(root.getAsJsonObject("rules"));
            if (root.get("schemaVersion").getAsInt() == 1) {
                CombatStateSnapshot oldRules = GSON.fromJson(root.get("rules"), CombatStateSnapshot.class);
                if (oldRules == null || !oldRules.encounters().isEmpty())
                    throw new IllegalArgumentException("legacy active combat save lacks captured settings");
                root.addProperty("schemaVersion", 3);
                root.add("capturedSettings", new JsonObject());
            }
            if (root.get("schemaVersion").getAsInt() == 2)
                root.addProperty("schemaVersion", 3);
            if (root.get("schemaVersion").getAsInt() == 3) {
                // Every saved encounter now uses the same scheduler and evidence audit.
                // The old classification grants no rights; preserve all rule state and receipts.
                root.add("startReceipts", root.remove("prototypeStarts"));
                root.add("exitReceipts", root.remove("prototypeExits"));
                root.remove("prototypeEncounters");
                root.addProperty("schemaVersion", 4);
            }
            if (root.get("schemaVersion").getAsInt() == 4) {
                root.add("quarantinedProjectiles", new JsonObject());
                root.addProperty("schemaVersion", CombatPersistenceEnvelope.CURRENT_SCHEMA);
            }
            if (root.get("schemaVersion").getAsInt() == 5)
                root.addProperty("schemaVersion", CombatPersistenceEnvelope.CURRENT_SCHEMA);
            CombatPersistenceEnvelope saved = GSON.fromJson(root, CombatPersistenceEnvelope.class);
            if (saved == null || saved.schemaVersion() != CombatPersistenceEnvelope.CURRENT_SCHEMA
                || saved.rules() == null
                || saved.rules().schemaVersion() != CombatStateSnapshot.CURRENT_SCHEMA)
                throw new IllegalArgumentException("unsupported combat execution save schema");
            return saved;
        }
        // The earlier value-only format cannot restore an active platform projection safely.
        migrateRuleBudgets(root);
        CombatStateSnapshot legacy = GSON.fromJson(root, CombatStateSnapshot.class);
        if (legacy == null || legacy.schemaVersion() != CombatStateSnapshot.CURRENT_SCHEMA
            || !legacy.encounters().isEmpty())
            throw new IllegalArgumentException("legacy active combat save lacks execution evidence");
        return new CombatPersistenceEnvelope(CombatPersistenceEnvelope.CURRENT_SCHEMA, legacy,
            0, 0, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
            java.util.List.of(), java.util.Map.of(), java.util.Map.of(),
            java.util.Map.of(), java.util.List.of());
    }

    public void update(CombatPersistenceEnvelope envelope) {
        // Clock-only saves reuse immutable rule/history encoding; metadata remains independently dirty.
        if (encodedRules != envelope.rules()) {
            encodedRuleJson = encodeRules(envelope.rules());
            encodedRules = envelope.rules();
        }
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("schemaVersion", envelope.schemaVersion());
        metadata.put("cumulativeServerTicks", envelope.cumulativeServerTicks());
        metadata.put("nextSessionSequence", envelope.nextSessionSequence());
        metadata.put("sessionSequences", envelope.sessionSequences());
        metadata.put("capturedSettings", envelope.capturedSettings());
        metadata.put("projectileOrigins", envelope.projectileOrigins());
        metadata.put("projectileDomains", envelope.projectileDomains());
        metadata.put("pendingProjectileAttacks", envelope.pendingProjectileAttacks());
        metadata.put("pendingMerges", envelope.pendingMerges());
        metadata.put("startReceipts", envelope.startReceipts());
        metadata.put("exitReceipts", envelope.exitReceipts());
        metadata.put("moveEndReceipts", envelope.moveEndReceipts());
        metadata.put("leases", envelope.leases());
        metadata.put("quarantinedProjectiles", envelope.quarantinedProjectiles());
        String encoded = "{\"rules\":" + encodedRuleJson + "," + GSON.toJson(metadata).substring(1);
        if (!encoded.equals(json)) {
            json = encoded;
            setDirty();
        }
    }

    private String encodeRules(CombatStateSnapshot rules) {
        java.util.Set<java.util.UUID> activeIds = new java.util.HashSet<>();
        List<String> active = new ArrayList<>();
        for (var encounter : rules.encounters()) {
            activeIds.add(encounter.id());
            EncodedState cached = activeEncoding.get(encounter.id());
            if (cached == null || cached.version() != encounter.version()
                || cached.structuralRevision() != encounter.structuralRevision()) {
                cached = new EncodedState(encounter.version(), encounter.structuralRevision(), GSON.toJson(encounter));
                activeEncoding.put(encounter.id(), cached);
            }
            active.add(cached.json());
        }
        activeEncoding.keySet().retainAll(activeIds);
        java.util.Set<java.util.UUID> closedIds = new java.util.HashSet<>();
        List<String> closed = new ArrayList<>();
        for (var encounter : rules.closed()) {
            closedIds.add(encounter.id());
            EncodedState cached = closedEncoding.get(encounter.id());
            if (cached == null || cached.version() != encounter.version()) {
                cached = new EncodedState(encounter.version(), 0, GSON.toJson(encounter));
                closedEncoding.put(encounter.id(), cached);
            }
            closed.add(cached.json());
        }
        closedEncoding.keySet().retainAll(closedIds);
        return "{\"schemaVersion\":" + rules.schemaVersion()
            + ",\"movementTicksPerTurn\":" + rules.movementTicksPerTurn()
            + ",\"environmentTicks\":" + rules.environmentTicks()
            + ",\"encounters\":[" + String.join(",", active)
            + "],\"closed\":[" + String.join(",", closed)
            + "],\"mergedInto\":" + GSON.toJson(rules.mergedInto())
            + ",\"mergeReceipts\":" + GSON.toJson(rules.mergeReceipts()) + "}";
    }

    /** Schema 1 had global budgets; copy their captured values without consulting live config. */
    private static void migrateRuleBudgets(JsonObject rules) {
        int schema = rules.get("schemaVersion").getAsInt();
        if (schema <= 4) migrateBehaviorIntents(rules);
        if (schema == 4) { rules.addProperty("schemaVersion", CombatStateSnapshot.CURRENT_SCHEMA); return; }
        if (rules.get("schemaVersion").getAsInt() == 2 || rules.get("schemaVersion").getAsInt() == 3) {
            // Missing observationEpoch remains null: historical clock provenance is unknown.
            rules.addProperty("schemaVersion", CombatStateSnapshot.CURRENT_SCHEMA);
            return;
        }
        if (rules.get("schemaVersion").getAsInt() != 1) return;
        int movement = rules.get("movementTicksPerTurn").getAsInt();
        int environment = rules.get("environmentTicks").getAsInt();
        for (var value : rules.getAsJsonArray("encounters")) {
            JsonObject encounter = value.getAsJsonObject();
            encounter.addProperty("movementTicksPerTurn", movement);
            encounter.addProperty("environmentTicks", environment);
        }
        rules.addProperty("schemaVersion", CombatStateSnapshot.CURRENT_SCHEMA);
    }
    private static void migrateBehaviorIntents(com.google.gson.JsonElement value) {
        if (value.isJsonArray()) { for (var child : value.getAsJsonArray()) migrateBehaviorIntents(child); }
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("capability") && object.has("target") && !object.has("behaviorId")) {
                object.addProperty("behaviorId", "dndturn:legacy_unresolved");
                object.addProperty("behaviorVersion", 1);
                object.addProperty("hand", "MAIN_HAND");
            }
            for (var entry : object.entrySet()) migrateBehaviorIntents(entry.getValue());
        }
    }

}
