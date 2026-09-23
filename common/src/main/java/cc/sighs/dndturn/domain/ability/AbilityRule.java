package cc.sighs.dndturn.domain.ability;

import cc.sighs.dndturn.domain.resolution.ResolutionContext;
import cc.sighs.dndturn.domain.resolution.RuleResolver;

/** Pure registered rule implementation. The definition version pins its semantics. */
@FunctionalInterface
public interface AbilityRule {
    RuleResolver.Resolution resolve(ResolutionContext context);
}
