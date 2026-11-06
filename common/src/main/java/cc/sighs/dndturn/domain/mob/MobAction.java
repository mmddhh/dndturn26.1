package cc.sighs.dndturn.domain.mob;

import cc.sighs.dndturn.domain.character.Ability;
import java.util.List;
import java.util.Objects;

/** Structured approximation of one action from the monster design document. */
public record MobAction(String id, Kind kind, int attackBonus, int reachTicks, int rangeTicks,
                        List<DamageComponent> damage, Ability saveAbility, int saveDc,
                        boolean halfOnSave, String condition, int durationRounds,
                        int radiusTicks, int rechargeMin, String summonId, int summonCount) {
    public enum Kind { ATTACK, SAVE_AREA, SUMMON, CHANNEL, SPECIAL }

    public MobAction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
        damage = List.copyOf(damage);
        if (id.isBlank() || id.length() > 128) throw new IllegalArgumentException("mob action id");
        if (attackBonus < -20 || attackBonus > 50 || reachTicks < 0 || reachTicks > 64
            || rangeTicks < 0 || rangeTicks > 512 || saveDc < 0 || saveDc > 40
            || durationRounds < 0 || durationRounds > 10_000 || radiusTicks < 0 || radiusTicks > 64
            || rechargeMin < 0 || rechargeMin > 6 || summonCount < 0 || summonCount > 64)
            throw new IllegalArgumentException("mob action bounds");
        if (damage.size() > 8) throw new IllegalArgumentException("mob action damage components");
        if (condition != null && (condition.isBlank() || condition.length() > 128))
            throw new IllegalArgumentException("mob action condition");
        if (summonId != null && (summonId.isBlank() || summonId.length() > 128))
            throw new IllegalArgumentException("mob action summon");
    }

    public static MobAction attack(String id, int attackBonus, int reachTicks, int rangeTicks,
                                   List<DamageComponent> damage) {
        return new MobAction(id, Kind.ATTACK, attackBonus, reachTicks, rangeTicks, damage,
            null, 0, false, null, 0, 0, 0, null, 0);
    }

    public static MobAction saveArea(String id, Ability saveAbility, int saveDc, boolean halfOnSave,
                                     int radiusTicks, List<DamageComponent> damage) {
        return new MobAction(id, Kind.SAVE_AREA, 0, 0, 0, damage, saveAbility, saveDc,
            halfOnSave, null, 0, radiusTicks, 0, null, 0);
    }
}
