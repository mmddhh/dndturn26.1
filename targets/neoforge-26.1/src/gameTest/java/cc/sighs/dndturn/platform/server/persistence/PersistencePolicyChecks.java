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
    }
}
