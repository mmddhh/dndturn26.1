package cc.sighs.dndturn.combat;

import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;

/** Current-only format policy; rejection must not rewrite the original value body. */
public final class PersistencePolicyChecks {
    private PersistencePolicyChecks() {}
    public static void verify(GameTestHelper helper, CombatSavedData current) {
        String original = current.json();
        helper.assertTrue(new CombatSavedData(original).envelope().equals(current.envelope()), "current format changed on read");
        for (int[] version : new int[][] {{6, 7}, {8, 8}, {9, 9}, {9, 999}, {999, 10}}) {
            var root = JsonParser.parseString(original).getAsJsonObject();
            root.addProperty("schemaVersion", version[0]);
            root.getAsJsonObject("rules").addProperty("schemaVersion", version[1]);
            String unsupported = root.toString();
            var data = new CombatSavedData(unsupported);
            boolean rejected = false;
            try { data.envelope(); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && unsupported.equals(data.json()), "unsupported format accepted or raw evidence rewritten");
        }
    }
}
