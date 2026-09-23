package cc.sighs.dndturn.platform.server.persistence;

import cc.sighs.dndturn.domain.encounter.EncounterStateSnapshot;
import cc.sighs.dndturn.platform.bootstrap.DNDTurnNeoForge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Fixed-version SavedData wrapper. The serialized body contains only common value records. */
public final class CombatSavedData extends SavedData {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int NBT_STRING_CHUNK = 30000;
    private static final Codec<CombatSavedData> CODEC = Codec.STRING.listOf().xmap(
        chunks -> new CombatSavedData(String.join("", chunks)), data -> split(data.json));
    public static final SavedDataType<CombatSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(DNDTurnNeoForge.MOD_ID, "combat_state"),
        CombatSavedData::new, CODEC);

    private String json;
    private EncounterStateSnapshot encodedRules;
    private String encodedRuleJson;
    private record EncodedState(long version, long structuralRevision, String json) {}
    private final java.util.Map<java.util.UUID, EncodedState> activeEncoding = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, EncodedState> closedEncoding = new java.util.HashMap<>();

    public CombatSavedData() { this(""); }

    CombatSavedData(String json) { this.json = java.util.Objects.requireNonNull(json); }

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
        requireFields(root, CombatPersistenceEnvelope.class);
        if (!root.get("rules").isJsonObject()) throw new IllegalArgumentException("invalid rules object");
        requireFields(root.getAsJsonObject("rules"), EncounterStateSnapshot.class);
        return GSON.fromJson(root, CombatPersistenceEnvelope.class);
    }

    private static void requireFields(com.google.gson.JsonObject value, Class<?> type) {
        for (var field : type.getRecordComponents())
            if (!value.has(field.getName()) || value.get(field.getName()).isJsonNull())
                throw new IllegalArgumentException("missing checkpoint field: " + field.getName());
    }

    public void update(CombatPersistenceEnvelope envelope) {
        // Clock-only saves reuse immutable rule/history encoding; metadata remains independently dirty.
        if (encodedRules != envelope.rules()) {
            encodedRuleJson = encodeRules(envelope.rules());
            encodedRules = envelope.rules();
        }
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
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
        metadata.put("exitAuthorizations", envelope.exitAuthorizations());
        metadata.put("moveEndReceipts", envelope.moveEndReceipts());
        metadata.put("leases", envelope.leases());
        metadata.put("abilities", envelope.abilities());
        metadata.put("processes", envelope.processes());
        metadata.put("mergeBindings", envelope.mergeBindings());
        metadata.put("projectileAbilities", envelope.projectileAbilities());
        metadata.put("recoveryAudits", envelope.recoveryAudits());
        metadata.put("participantEffects", envelope.participantEffects());
        metadata.put("turnSettlements", envelope.turnSettlements());
        metadata.put("quarantinedProjectiles", envelope.quarantinedProjectiles());
        String encoded = "{\"rules\":" + encodedRuleJson + "," + GSON.toJson(metadata).substring(1);
        if (!encoded.equals(json)) {
            json = encoded;
            setDirty();
        }
    }

    private String encodeRules(EncounterStateSnapshot rules) {
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
        return "{\"movementTicksPerTurn\":" + rules.movementTicksPerTurn()
            + ",\"environmentTicks\":" + rules.environmentTicks()
            + ",\"encounters\":[" + String.join(",", active)
            + "],\"closed\":[" + String.join(",", closed)
            + "],\"mergedInto\":" + GSON.toJson(rules.mergedInto())
            + ",\"mergeReceipts\":" + GSON.toJson(rules.mergeReceipts()) + "}";
    }

}
