package cc.sighs.dndturn.domain.mob;

import cc.sighs.dndturn.domain.character.Ability;
import java.util.*;

/** Only descriptors with an implemented tactical executor are admitted. */
public sealed interface MobAction permits MobAction.Attack, MobAction.SaveArea {
    String id();
    List<DamageComponent> damage();
    record Attack(String id, int attackBonus, int reachTicks, int rangeTicks, List<DamageComponent> damage) implements MobAction {
        public Attack {
            validate(id, damage); damage = List.copyOf(damage);
            if (attackBonus < -20 || attackBonus > 50 || reachTicks < 0 || reachTicks > 64 || rangeTicks < 0 || rangeTicks > 512)
                throw new IllegalArgumentException("attack descriptor");
        }
    }
    record SaveArea(String id, Ability saveAbility, int saveDc, boolean halfOnSave, int radiusTicks,
                    int rangeTicks, List<DamageComponent> damage) implements MobAction {
        public SaveArea {
            validate(id, damage); damage = List.copyOf(damage); Objects.requireNonNull(saveAbility);
            if (saveDc < 0 || saveDc > 40 || radiusTicks < 0 || radiusTicks > 64 || rangeTicks < 0 || rangeTicks > 512)
                throw new IllegalArgumentException("save area descriptor");
        }
    }
    private static void validate(String id, List<DamageComponent> damage) {
        Objects.requireNonNull(id); Objects.requireNonNull(damage);
        if (id.isBlank() || id.length() > 128 || damage.isEmpty() || damage.size() > 8)
            throw new IllegalArgumentException("mob action descriptor");
    }
}
