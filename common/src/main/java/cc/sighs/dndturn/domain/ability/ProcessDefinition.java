package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.fact.FactKey;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

/** Semantic execution contract. Clocks advance only at the named owner's authorized boundary. */
public record ProcessDefinition(String id, int version, Clock clock, Recovery recovery,
                                long maximumSteps, Set<String> controls, String provenance,
                                Map<String, Set<String>> transitions) {
    public ProcessDefinition(String id, int version, Clock clock, Recovery recovery,
                             long maximumSteps, Set<String> controls, String provenance) {
        this(id, version, clock, recovery, maximumSteps, controls, provenance, Map.of(
                "APPROACH", Set.of("PREPARE", "TERMINAL"), "PREPARE", Set.of("EXECUTE", "OBSERVE", "TERMINAL"),
                "EXECUTE", Set.of("OBSERVE", "TERMINAL"), "OBSERVE", Set.of("TERMINAL"), "TERMINAL", Set.of()));
    }
    public enum Clock { AUTHORIZED_EXECUTION_STEP, ACTOR_TURN_START, ACTOR_TURN_END, ENVIRONMENT_STEP }
    public enum Recovery { RESUME_SEMANTIC, RECONCILE_NATIVE, FAIL_UNKNOWN, UNSUPPORTED }
    public ProcessDefinition {
        FactKey.requireId(id); Objects.requireNonNull(clock); Objects.requireNonNull(recovery);
        controls = Set.copyOf(controls); Objects.requireNonNull(provenance);
        if (version < 1 || maximumSteps < 1 || maximumSteps > 1000000 || controls.size() > 8
                || provenance.isBlank() || provenance.length() > 512) throw new IllegalArgumentException("process definition bounds");
        controls.forEach(FactKey::requireId);
        var checked = new LinkedHashMap<String, Set<String>>();
        transitions.forEach((phase, next) -> checked.put(phase, Set.copyOf(next)));
        transitions = Map.copyOf(checked);
        if (transitions.isEmpty() || transitions.size() > 32 || transitions.keySet().stream().anyMatch(key -> key.isBlank() || key.length() > 64)
                || transitions.values().stream().anyMatch(next -> !checked.keySet().containsAll(next)))
            throw new IllegalArgumentException("process phase contract");
    }
}
