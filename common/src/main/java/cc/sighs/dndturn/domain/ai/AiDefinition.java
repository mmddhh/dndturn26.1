package cc.sighs.dndturn.domain.ai;

import cc.sighs.dndturn.domain.fact.ReadContract;
import java.util.*;

/** Cold decision semantics; neither ability ownership nor execution authority. */
public record AiDefinition(String id, int version, String planner, Set<String> traits,
        TargetPreference targetPreference, Positioning positioning, ResourcePreference resources, RiskPreference risk,
        ReadContract requiredFacts, Provenance provenance) {
    public enum TargetPreference { NONE, VISIBLE_PLAYERS, COMMITTED_OR_HOSTILE_PLAYERS, COMMITTED_OR_HOSTILE_ACTORS }
    public enum Positioning { GROUND_OPPORTUNITY, STATIONARY, MOVEMENT_OPPORTUNITY }
    public enum ResourcePreference { CONSERVATIVE }
    public enum RiskPreference { AUDITED_OPPORTUNITIES_ONLY }
    public enum Source { EXPLICIT_PROVIDER, AUDITED_NATIVE_FAMILY, STRUCTURAL_CANDIDATE, GENERIC_FALLBACK, UNSUPPORTED }
    public record Provenance(Source source, String provider, int version, String evidence) {
        public Provenance { Objects.requireNonNull(source); Objects.requireNonNull(provider); Objects.requireNonNull(evidence);
            if (version < 1 || provider.length() > 128 || evidence.length() > 1024) throw new IllegalArgumentException("AI provenance"); }
    }
    public AiDefinition {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || version < 1)
            throw new IllegalArgumentException("AI definition identity");
        Objects.requireNonNull(planner); traits = Set.copyOf(traits); Objects.requireNonNull(targetPreference);
        Objects.requireNonNull(positioning); Objects.requireNonNull(resources); Objects.requireNonNull(risk); Objects.requireNonNull(requiredFacts); Objects.requireNonNull(provenance);
        if (traits.size() > 32 || traits.stream().anyMatch(t -> t.length() > 64)) throw new IllegalArgumentException("AI traits");
    }
    public boolean supported() { return provenance.source() != Source.UNSUPPORTED && provenance.source() != Source.STRUCTURAL_CANDIDATE; }
}
