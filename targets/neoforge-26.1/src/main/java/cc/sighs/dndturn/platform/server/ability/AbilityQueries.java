package cc.sighs.dndturn.platform.server.ability;
import cc.sighs.dndturn.domain.encounter.StateView;

import cc.sighs.dndturn.domain.ability.AbilityBinding;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.actor.ActorSnapshot;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.MinecraftSnapshotCapture;
import java.util.*;

/** Bounded discovery and rule evaluation; never grants an execution permit. */
public final class AbilityQueries {
    private final MinecraftSnapshotCapture snapshots;
    public AbilityQueries(MinecraftSnapshotCapture snapshots) { this.snapshots = Objects.requireNonNull(snapshots); }
    public static AbilityQueries forActor(LiveActorContext actor) { return new AbilityQueries(MinecraftSnapshotCapture.forActor(actor)); }
    public enum Availability { EXECUTABLE, APPROACH_REQUIRED, TARGET_REQUIRED, RULE_BLOCKED, ADAPTER_MISSING, EVALUATION_DEFERRED }
    public record Discovered(AbilityBinding binding, Availability availability, ActionFailure.Details failure) {}
    public RuleResolver.Resolution evaluate(LiveActorContext actor, ActionIntent intent,
            StateView state, GrantEvidence expected, ResolutionContext.Stage stage) {
        var snapshot = snapshots.capture(actor, AbilityAdapterRegistry.definitions().require(intent));
        return AbilityAdapterRegistry.definitions().resolve(snapshots.capture(actor, snapshot, intent, state, expected, stage));
    }
    public static void requireAccepted(RuleResolver.Resolution resolution) {
        if (resolution.accepted()) return;
        throw new ActionFailure(resolution.status() == RuleResolver.Status.DEFERRED
                ? ActionFailure.Code.EVALUATION_DEFERRED : ActionFailure.Code.ABILITY_UNAVAILABLE,
                resolution.status() == RuleResolver.Status.DEFERRED ? ActionFailure.Retry.REFRESH_AND_REPROPOSE : ActionFailure.Retry.NONE,
                resolution.reason());
    }
    public List<Discovered> discover(LiveActorContext actor, StateView state, ActionIntent.Target target) {
        var snapshot = snapshots.capture(actor, state);
        return discover(actor, snapshot, state, target);
    }
    /** Reuse one decision-boundary source capture across bounded target evaluation. Not an authority cache. */
    public List<Discovered> discover(LiveActorContext actor, ActorSnapshot snapshot, StateView state, ActionIntent.Target target) {
        var found = new ArrayList<Discovered>();
        try (var query = new cc.sighs.dndturn.platform.server.query.AbilityQueryService(snapshots)) {
            for (var binding : snapshot.abilities()) {
                if (binding.definition().activation() != AbilityDefinition.Activation.MANUAL) continue;
                if (target == null || !binding.targets().contains(target.kind())) {
                    found.add(new Discovered(binding, Availability.TARGET_REQUIRED, ActionFailure.Details.NONE)); continue;
                }
                try {
                    var intent = binding.invocation(target);
                    var result = query.evaluate(actor, snapshot, state, intent);
                    Availability availability = switch (result.status()) {
                        case ALLOWED -> Availability.EXECUTABLE;
                        case REQUIRES_APPROACH -> Availability.APPROACH_REQUIRED;
                        case REJECTED -> Availability.RULE_BLOCKED;
                        case UNSUPPORTED -> Availability.ADAPTER_MISSING;
                        case DEFERRED -> Availability.EVALUATION_DEFERRED;
                    };
                    found.add(new Discovered(binding, availability, result.accepted() ? ActionFailure.Details.NONE : ActionFailure.Details.REJECTED));
                } catch (RuntimeException rejected) {
                    var failure = ActionFailure.classify(rejected);
                    found.add(new Discovered(binding, failure.code() == ActionFailure.Code.EVALUATION_DEFERRED
                            ? Availability.EVALUATION_DEFERRED : Availability.RULE_BLOCKED, failure));
                }
            }
        }
        return List.copyOf(found);
    }
}
