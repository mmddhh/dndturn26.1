package cc.sighs.dndturn.domain.ai;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterPhase;
import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.*;

/** Shared deterministic planner. Its result must enter the same RuleResolver/PLAN as player input. */
public final class AiPlanner {
    private AiPlanner() {}
    private static final Map<String, java.util.function.Function<AiDecisionContext, Decision>> providers = new LinkedHashMap<>();
    private static boolean frozen;
    public static synchronized void register(String id, java.util.function.Function<AiDecisionContext, Decision> provider) {
        FactKey.requireId(id); Objects.requireNonNull(provider);
        if (frozen || id.equals("dndturn:shared_ground") || providers.size() >= 128 || providers.putIfAbsent(id, provider) != null)
            throw new IllegalStateException("AI planner registration closed or duplicate");
    }
    public static synchronized void freeze() { frozen = true; }
    public sealed interface Decision permits Propose, EndTurn, Wait {}
    public record Propose(ActionIntent intent) implements Decision { public Propose { Objects.requireNonNull(intent); } }
    public record EndTurn(String reason) implements Decision {}
    public record Wait(int ticks) implements Decision {
        public Wait { if (ticks < 1 || ticks > 20) throw new IllegalArgumentException("wait bound"); }
    }
    public static Decision decide(AiDecisionContext c) {
        var d = c.definition();
        if (d.supported() && providers.containsKey(d.planner())) return bound(c, Objects.requireNonNull(providers.get(d.planner()).apply(c)));
        if (!d.supported() || !d.planner().equals("dndturn:shared_ground")) return new EndTurn("AI_UNSUPPORTED");
        boolean combat = d.targetPreference() != AiDefinition.TargetPreference.NONE
            && (c.phase() == EncounterPhase.ACTIVE || c.phase() == EncounterPhase.CANDIDATE
                && d.targetPreference() == AiDefinition.TargetPreference.VISIBLE_PLAYERS);
        if (combat) {
            var target = c.perception().actors().stream()
                .filter(t -> (t.player() || d.targetPreference() == AiDefinition.TargetPreference.COMMITTED_OR_HOSTILE_ACTORS)
                        && t.nativeAttackable() && (t.visible() || t.currentTarget()))
                .filter(t -> d.targetPreference() == AiDefinition.TargetPreference.VISIBLE_PLAYERS || t.currentTarget() || c.hostile().contains(t.id()))
                .filter(t -> c.options().stream().anyMatch(o -> o.target().equals(t.id()) && usable(o)))
                .min(Comparator.<PerceptionSnapshot.ObservedActor>comparingInt(t -> t.currentTarget() ? 0 : 1)
                    .thenComparingDouble(PerceptionSnapshot.ObservedActor::distanceSquared).thenComparing(PerceptionSnapshot.ObservedActor::id)).orElse(null);
            if (target != null && c.actionAvailable()) {
                var aim = new ActionIntent.Target(ActionIntent.TargetKind.ENTITY, c.dimension(), target.id(), null, -1, 0, 0, 0);
                var option = c.options().stream().filter(o -> o.target().equals(target.id()) && usable(o)).findFirst().orElseThrow();
                return new Propose(option.binding().invocation(aim));
            }
        }
        if (c.actionAvailable()) {
            var support = c.options().stream().filter(o -> o.available() && c.perception().actors().stream()
                    .anyMatch(t -> t.id().equals(o.target()) && t.visible()
                        && (t.evidence().relations().contains(PerceptionSnapshot.Relation.ALLY)
                            || t.evidence().relations().contains(PerceptionSnapshot.Relation.PROTECTED))))
                .filter(o -> o.semantics().contains(AiAffordance.HEAL) || o.semantics().contains(AiAffordance.BUFF)
                        || o.semantics().contains(AiAffordance.PROTECT_ALLY)).findFirst().orElse(null);
            if (support != null) return new Propose(support.binding().invocation(new ActionIntent.Target(
                    ActionIntent.TargetKind.ENTITY, c.dimension(), support.target(), null, -1, 0, 0, 0)));
        }
        return d.positioning() != AiDefinition.Positioning.STATIONARY && c.movementOpportunity() != null
            ? new Propose(c.movementOpportunity()) : new EndTurn("NO_PROPOSAL");
    }
    private static boolean usable(AiDecisionContext.Option o) {
        return o.available() && (o.semantics().contains(AiAffordance.CONTROL) || o.semantics().contains(AiAffordance.DEBUFF)
            || o.semantics().contains(AiAffordance.DAMAGE)
                && (o.semantics().contains(AiAffordance.MELEE) || o.semantics().contains(AiAffordance.RANGED)
                    || o.semantics().contains(AiAffordance.AREA)));
    }
    private static Decision bound(AiDecisionContext context, Decision decision) {
        if (!(decision instanceof Propose proposal)) return decision;
        var intent = proposal.intent();
        if (intent.equals(context.movementOpportunity())) return decision;
        var binding = context.actor().abilities().stream().filter(b -> b.id().equals(intent.behaviorId())
                && b.version() == intent.behaviorVersion() && b.source().equals(intent.source())
                && b.kind() == intent.capability() && b.targets().contains(intent.target().kind())).findFirst().orElse(null);
        if (binding == null) return new EndTurn("AI_UNBOUND_ABILITY");
        binding.definition().validateParameters(intent.parameters());
        return decision;
    }
}
