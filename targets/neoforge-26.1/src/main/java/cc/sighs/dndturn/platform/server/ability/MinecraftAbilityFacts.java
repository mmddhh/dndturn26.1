package cc.sighs.dndturn.platform.server.ability;

import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.ReadContract;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.MinecraftFactProviders;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Read-only native compatibility capture, independent of executor registration and rule definitions. */
public interface MinecraftAbilityFacts {
    /** Explicit opt-in: the adapter must evaluate facet geometry, target facts and reach. */
    default boolean supportsBodyFacet() { return false; }
    GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand);
    default List<GrantEvidence> sources(LiveActorContext actor) {
        var values = new LinkedHashSet<GrantEvidence>();
        var natural = source(actor, null);
        if (natural != null) values.add(natural);
        for (var hand : ActionIntent.Hand.values()) {
            var value = source(actor, hand);
            if (value != null) values.add(value);
        }
        return List.copyOf(values);
    }
    default List<GrantEvidence> checkedSources(LiveActorContext actor) {
        actor.verifyCurrent();
        var values = List.copyOf(sources(actor));
        if (values.size() > 16 || new HashSet<>(values).size() != values.size())
            throw new IllegalStateException("grant enumeration exceeds contract");
        return values;
    }
    String unavailable(LiveActorContext actor, ActionIntent intent, EncounterAuthority.StateView state);
    boolean canExecute(LiveActorContext actor, ActionIntent intent, Vec3 feet);
    /** Planning may choose an interior position to accommodate native arrival tolerance; execution keeps the rule range. */
    default boolean canPlanExecutionFrom(LiveActorContext actor, ActionIntent intent, Vec3 feet) { return canExecute(actor, intent, feet); }
    default int approachRadius() { return 4; }
    /** Additional declared native/world reads; uninstalled providers remain explicitly unsupported. */
    default FactSlice captureAdditional(LiveActorContext actor, ActionIntent intent,
            EncounterAuthority.StateView state, ReadContract reads) {
        return MinecraftFactProviders.capture(actor, reads);
    }
}
