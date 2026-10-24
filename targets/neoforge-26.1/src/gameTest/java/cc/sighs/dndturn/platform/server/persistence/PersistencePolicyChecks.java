package cc.sighs.dndturn.platform.server.persistence;

import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;

/** Current-only format policy; rejection must not rewrite the original value body. */
public final class PersistencePolicyChecks {
    private PersistencePolicyChecks() {}
    public static void verify(GameTestHelper helper, CombatSavedData current) {
        verifyActor(helper);
        String original = current.json();
        helper.assertTrue(PersistenceTestAccess.saved(original).envelope().equals(current.envelope()), "current format changed on read");
        var decorated = JsonParser.parseString(original).getAsJsonObject();
        helper.assertTrue(!decorated.has("schemaVersion") && !decorated.getAsJsonObject("rules").has("schemaVersion"),
            "format version still written");
        decorated.addProperty("schemaVersion", 999);
        decorated.getAsJsonObject("rules").addProperty("schemaVersion", -1);
        var obsolete = PersistenceTestAccess.saved(decorated.toString());
        boolean obsoleteRejected = false;
        try { obsolete.envelope(); } catch (IllegalArgumentException expected) { obsoleteRejected = true; }
        helper.assertTrue(obsoleteRejected && obsolete.json().equals(decorated.toString()),
            "unexpected checkpoint fields accepted or original rewritten");
        for (String field : new String[] {"rules", "cumulativeServerTicks", "abilities"}) {
            var root = JsonParser.parseString(original).getAsJsonObject();
            root.remove(field);
            String unsupported = root.toString();
            var data = PersistenceTestAccess.saved(unsupported);
            boolean rejected = false;
            try { data.envelope(); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && unsupported.equals(data.json()), "missing field accepted or raw evidence rewritten");
        }
        for (String field : new String[] {"discoveryHorizontal", "discoveryVertical", "escapeDistance", "tacticalKnockbackEnabled"}) {
            var root = JsonParser.parseString(original).getAsJsonObject();
            var settings = JsonParser.parseString("{\"discoveryHorizontal\":16,\"discoveryVertical\":8,"
                + "\"regionRadius\":35,\"maxSampledChunks\":64,\"maxAnchors\":64,"
                + "\"tacticalKnockbackEnabled\":false,\"escapeDistance\":16}").getAsJsonObject();
            settings.remove(field);
            root.getAsJsonObject("capturedSettings").add("00000000-0000-0000-0000-000000000001", settings);
            String unsupported = root.toString();
            var data = PersistenceTestAccess.saved(unsupported);
            boolean rejected = false;
            try { data.envelope(); }
            catch (IllegalArgumentException expected) {
                rejected = expected.getMessage().contains(field);
            }
            helper.assertTrue(rejected && unsupported.equals(data.json()),
                "missing captured setting silently defaulted or raw evidence rewritten: " + field);
        }
    }
    private static void verifyActor(GameTestHelper helper) {
        var current = new ActorSavedData();
        current.update(new cc.sighs.dndturn.domain.actor.ActorStates());
        helper.assertTrue(new ActorSavedData(current.json()).restore().snapshot().isEmpty(),
            "current actor checkpoint failed roundtrip");
        for (String field : new String[] {"actors", "receipts", "faults", "deliveries", "invocations", "simulationArchive"}) {
            var root = JsonParser.parseString(current.json()).getAsJsonObject();
            root.remove(field);
            String raw = root.toString();
            var missing = new ActorSavedData(raw);
            boolean rejected = false;
            try { missing.restore(); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && missing.json().equals(raw), "actor field defaulted or original rewritten: " + field);
        }
        var root = JsonParser.parseString(current.json()).getAsJsonObject();
        root.add("capacityRecoveries", new com.google.gson.JsonObject());
        var obsolete = new ActorSavedData(root.toString());
        boolean rejected = false;
        try { obsolete.restore(); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && obsolete.json().equals(root.toString()), "obsolete actor repair metadata accepted");
    }

}
