package cc.sighs.dndturn.platform.server.persistence;

import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;

/** Current-only format policy; rejection must not rewrite the original value body. */
public final class PersistencePolicyChecks {
    private PersistencePolicyChecks() {}
    public static void verify(GameTestHelper helper, CombatSavedData current) {
        String original = current.json();
        helper.assertTrue(PersistenceTestAccess.saved(original).envelope().equals(current.envelope()), "current format changed on read");
        var decorated = JsonParser.parseString(original).getAsJsonObject();
        helper.assertTrue(!decorated.has("schemaVersion") && !decorated.getAsJsonObject("rules").has("schemaVersion"),
            "format version still written");
        decorated.addProperty("schemaVersion", 999);
        decorated.getAsJsonObject("rules").addProperty("schemaVersion", -1);
        helper.assertTrue(PersistenceTestAccess.saved(decorated.toString()).envelope().equals(current.envelope()),
            "obsolete format metadata influences restoration");
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
}
