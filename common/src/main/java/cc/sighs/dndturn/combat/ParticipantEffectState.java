package cc.sighs.dndturn.combat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Value-only duration ownership and bounded settlement evidence; never a world execution permit. */
public final class ParticipantEffectState {
    private ParticipantEffectState() {}
    public static final String POLICY = "dndturn:participant_round_effects";
    public static final int VERSION = 1;
    public record Timer(UUID instance, int amplifier, int expectedNativeTicks, RoundDuration duration) {
        public Timer {
            Objects.requireNonNull(instance); Objects.requireNonNull(duration);
            if (amplifier < 0 || amplifier > 255 || expectedNativeTicks < -1
                || expectedNativeTicks != duration.nativeTicks())
                throw new IllegalArgumentException("invalid native timer evidence");
        }
    }
    public record Owner(Map<String, List<Timer>> effects, Timer fire) {
        public Owner {
            if (effects.size() > 128) throw new IllegalArgumentException("effect limit");
            var copy = new java.util.LinkedHashMap<String, List<Timer>>();
            effects.forEach((id, chain) -> {
                if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 128
                    || chain.isEmpty() || chain.size() > 16) throw new IllegalArgumentException("effect chain");
                copy.put(id, List.copyOf(chain));
            });
            effects = Map.copyOf(copy);
        }
    }
    public enum Phase { PREPARED, OBSERVED, UNKNOWN }
    public record Observation(String effect, int amplifier, RoundTime time, double scaledAmount,
                              float healthBefore, float healthAfter, float absorptionBefore, float absorptionAfter,
                              boolean nativeAccepted, Phase phase) {
        public Observation {
            Objects.requireNonNull(effect); Objects.requireNonNull(time); Objects.requireNonNull(phase);
            if (effect.isBlank() || effect.length() > 128 || amplifier < 0 || amplifier > 255
                || !Double.isFinite(scaledAmount) || scaledAmount < 0
                || !Float.isFinite(healthBefore) || !Float.isFinite(healthAfter)
                || !Float.isFinite(absorptionBefore) || !Float.isFinite(absorptionAfter))
                throw new IllegalArgumentException("effect observation");
        }
    }
    public record Settlement(UUID operation, UUID encounter, UUID owner, String policy, int version,
                             Phase phase, List<Observation> observations) {
        public Settlement {
            Objects.requireNonNull(operation); Objects.requireNonNull(encounter); Objects.requireNonNull(owner);
            Objects.requireNonNull(phase); observations = List.copyOf(observations);
            if (!POLICY.equals(policy) || version != VERSION || observations.size() > 129)
                throw new IllegalArgumentException("unsupported effect settlement policy");
        }
    }
}
