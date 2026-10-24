package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.platform.server.ability.MinecraftAbilityFacts;
import cc.sighs.dndturn.platform.server.action.AbilityExecutor;
import cc.sighs.dndturn.domain.ability.AbilityDefinition;

/** Convenience for audited built-ins sharing capture and execution implementation.
 * The registry stores the two ports separately; extensions may supply independent objects.
 */
public abstract class MinecraftAbilityAdapter extends AbilityExecutor implements MinecraftAbilityFacts {
    protected MinecraftAbilityAdapter(AbilityDefinition definition) { super(definition); }
}
