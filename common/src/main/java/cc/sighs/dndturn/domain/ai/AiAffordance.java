package cc.sighs.dndturn.domain.ai;

/** Tactical meaning only. No tag grants ownership, legality, or a native permit. */
public enum AiAffordance {
    DAMAGE, HEAL, CONTROL, BUFF, DEBUFF, MELEE, RANGED, AREA, SINGLE_TARGET,
    REPOSITION_SELF, REPOSITION_TARGET, ESCAPE, PROTECT_ALLY, RESOURCE_CHEAP, RESOURCE_LIMITED, HIGH_RISK
}
