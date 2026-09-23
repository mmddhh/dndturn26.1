package cc.sighs.dndturn.application.actor;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactKey;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import java.util.*;

/** Pure compilation. Native effective values already include native equipment/effect modifiers. */
public final class SnapshotCompiler {
    private SnapshotCompiler() {}
    public static ActorSnapshot compile(UUID actor, UUID instance, ActorDefinition definition,
                                        ActorSnapshot.Revision revision, FactSlice nativeFacts, List<AbilityBinding> bindings) {
        var derived = new HashMap<FactKey<?>, Object>();
        var missing = new HashMap<FactKey<?>, FactSlice.Missing>();
        try {
            derived.put(ActorFacts.ARMOR_CLASS, (double) CombatRules.armorClass(
                    nativeFacts.read(ActorFacts.DECISION, ActorFacts.ARMOR_EFFECTIVE),
                    nativeFacts.read(ActorFacts.DECISION, ActorFacts.HOLDING_SHIELD)));
        } catch (FactSlice.MissingFact unavailable) { missing.put(ActorFacts.ARMOR_CLASS, unavailable.reason()); }
        try {
            derived.put(ActorFacts.DAMAGE_REDUCTION, (double) CombatRules.damageReduction(
                    nativeFacts.read(ActorFacts.DECISION, ActorFacts.TOUGHNESS_EFFECTIVE)));
        } catch (FactSlice.MissingFact unavailable) { missing.put(ActorFacts.DAMAGE_REDUCTION, unavailable.reason()); }
        var facts = definition.defaults().merge(nativeFacts).merge(new FactSlice(derived, missing));
        return new ActorSnapshot(actor, instance, definition, revision, facts, bindings);
    }
}
