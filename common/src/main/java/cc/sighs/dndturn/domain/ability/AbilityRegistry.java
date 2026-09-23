package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.domain.resolution.RuleResolver;
import java.util.*;

/** Register at setup; freeze before use. Definitions never change under running operations. */
public final class AbilityRegistry {
    private final Map<String, AbilityDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, AbilityRule> rules = new LinkedHashMap<>();
    private boolean frozen;
    public synchronized void register(AbilityDefinition definition) {
        register(definition, RuleResolver::resolve);
    }
    public synchronized void register(AbilityDefinition definition, AbilityRule rule) {
        Objects.requireNonNull(definition);
        Objects.requireNonNull(rule);
        if (frozen) throw new IllegalStateException("definition registry frozen");
        if (definitions.size() >= 64 || definitions.containsKey(definition.id()))
            throw new IllegalArgumentException("duplicate definition or capacity exceeded: " + definition.id());
        definitions.put(definition.id(), definition);
        rules.put(definition.id(), rule);
    }
    public synchronized void freeze() { frozen = true; }
    public synchronized AbilityDefinition require(String id, int version) {
        var definition = definitions.get(id);
        if (definition == null || definition.version() != version)
            throw new IllegalStateException("ability definition missing or version changed: " + id);
        return definition;
    }
    public AbilityDefinition require(ActionIntent intent) {
        if (!CombatRules.RULES_REVISION.equals(intent.ruleset()))
            throw new IllegalStateException("captured ruleset unavailable; no semantic substitution");
        var definition = require(intent.behaviorId(), intent.behaviorVersion());
        definition.validateParameters(intent.parameters());
        if (definition.kind() != intent.capability() || !definition.targets().contains(intent.target().kind()))
            throw new IllegalArgumentException("invocation contradicts definition");
        return definition;
    }
    public synchronized List<AbilityDefinition> all() { return List.copyOf(definitions.values()); }
    public synchronized AbilityDefinition find(String id, int version) {
        var value = definitions.get(id);
        return value != null && value.version() == version ? value : null;
    }
    public RuleResolver.Resolution resolve(ResolutionContext context) {
        var definition = require(context.invocation().intent());
        if (!definition.equals(context.invocation().binding().definition()))
            throw new IllegalArgumentException("unregistered definition evidence");
        try {
            if (!context.world().read(definition.reads(), RuleFacts.SOURCE_VALID))
                return new RuleResolver.Resolution(RuleResolver.Status.REJECTED, definition.cost(), "ability grant revoked or replaced");
            for (var key : definition.reads().keys()) context.world().read(definition.reads(), key);
            for (var key : definition.reads().actorKeys()) context.actor().actor().facts().read(definition.reads().actor(), key);
            for (var key : definition.reads().targetKeys()) context.targetFacts().read(definition.reads().target(), key);
        } catch (FactSlice.MissingFact missing) {
            return new RuleResolver.Resolution(missing.reason() == FactSlice.Missing.UNSUPPORTED
                    ? RuleResolver.Status.UNSUPPORTED : RuleResolver.Status.DEFERRED, definition.cost(), missing.getMessage());
        }
        var resolution = Objects.requireNonNull(rules.get(definition.id()).resolve(context));
        if (!definition.cost().equals(resolution.cost())) throw new IllegalArgumentException("resolver changed registered cost");
        return resolution;
    }
}
