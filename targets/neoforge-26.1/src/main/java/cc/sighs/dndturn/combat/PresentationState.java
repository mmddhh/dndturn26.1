package cc.sighs.dndturn.combat;

import java.util.UUID;
import net.minecraft.world.InteractionHand;

/** Immutable display facts, not membership authority or an execution permit. */
public final class PresentationState {
    private PresentationState() {}
    public enum Motion { ACTIVE, FORCED, PASSIVE, MIXED, CORRECTION }
    public record Movement(UUID operation, int step, long sequence, Motion cause, double horizontal) {
        public Movement {
            if (operation == null || step < 0 || sequence < 0 || cause == null
                || !Double.isFinite(horizontal) || horizontal < 0) throw new IllegalArgumentException("invalid movement evidence");
        }
    }
    public record Use(UUID identity, InteractionHand hand, String item, int used, int remaining) {
        public static final Use STOPPED = new Use(null, InteractionHand.MAIN_HAND, "empty", 0, 0);
        public Use {
            if (hand == null || item == null || used < 0 || remaining < 0
                || identity == null && (used != 0 || remaining != 0)) throw new IllegalArgumentException("invalid use snapshot");
        }
        public boolean active() { return identity != null && remaining > 0; }
    }
    public record Facts(UUID member, UUID controller, String phase, boolean paused, Use use) {
        public Facts { java.util.Objects.requireNonNull(phase); java.util.Objects.requireNonNull(use); }
    }
}
