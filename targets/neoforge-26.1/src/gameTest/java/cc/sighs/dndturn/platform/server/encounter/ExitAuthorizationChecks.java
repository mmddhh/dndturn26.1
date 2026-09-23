package cc.sighs.dndturn.platform.server.encounter;

import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.EncounterRegion;
import cc.sighs.dndturn.platform.server.control.ExitAuthorizations;
import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Independent CAP-T29 fixture: current authorization has no dependency on a receipt collection. */
public final class ExitAuthorizationChecks {
    private ExitAuthorizationChecks() {}
    public static void verify(GameTestHelper helper, ServerPlayer player) {
        var engine = new EncounterAuthority(new Random(8), 28, 20);
        UUID domain = UUID.randomUUID(), remaining = UUID.randomUUID();
        var region = EncounterRegion.generate(player.level().dimension().identifier().toString(),
            new EncounterRegion.Discovery(0, 0, 0, 4, 4, 4),
            List.of(new EncounterRegion.Anchor(remaining, new EncounterRegion.Point(2, 2, 2))), 2, 1);
        engine.beginCandidate(domain, region, Set.of(remaining));
        var owner = new ExitAuthorizations();
        UUID operation = UUID.randomUUID();
        owner.grant(player, domain, operation, 2);
        owner.reconcile(engine, id -> id.equals(player.getUUID()) ? player : null);
        helper.assertTrue(owner.permits(player, domain), "grant depended on an archived receipt");
        var restored = new ExitAuthorizations();
        restored.restore(owner.snapshot());
        restored.reconcile(engine, id -> id.equals(player.getUUID()) ? player : null);
        helper.assertTrue(restored.permits(player, domain), "explicit saved grant did not rebind");
        engine.join(domain, player.getUUID());
        restored.reconcile(engine, id -> id.equals(player.getUUID()) ? player : null);
        engine.leave(domain, player.getUUID());
        restored.reconcile(engine, id -> id.equals(player.getUUID()) ? player : null);
        helper.assertTrue(!restored.permits(player, domain) && restored.snapshot().isEmpty(),
            "old exit grant revived after rejoining and leaving");
        engine.end(domain);
        owner.reconcile(engine, id -> player);
        helper.assertTrue(owner.snapshot().isEmpty(), "closed domain retained live authorization");
    }
}
