package cc.sighs.dndturn.domain.actor;

/** Detached rule output; neither branch is a native execution permit. */
public sealed interface RuleEmission permits ActorStates.Change, TriggeredAbilityInvocation {}
