package cc.sighs.dndturn.platform.server.builtin.skeleton;

import cc.sighs.dndturn.domain.actor.ActorDefinition;
import cc.sighs.dndturn.domain.ai.AiDefinition;
import cc.sighs.dndturn.domain.effect.EffectDefinition;
import cc.sighs.dndturn.domain.effect.EquipmentEffects;
import cc.sighs.dndturn.domain.fact.ActorFacts;
import cc.sighs.dndturn.domain.fact.FactSlice;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.action.LiveActorContext;
import cc.sighs.dndturn.platform.server.actor.ActorDefinitions;
import cc.sighs.dndturn.platform.server.ai.AiDefinitions;
import cc.sighs.dndturn.platform.server.damage.RangedAdapters;
import cc.sighs.dndturn.platform.server.effect.GroupEffects;
import java.util.*;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

/** Fixed .84 native family registration. All entity-specific knowledge stays in this adapter. */
public final class SkeletonFamily {
    static final String GROUP = "dndturn:skeleton_archer";
    static final EffectDefinition EFFECT = new EffectDefinition("dndturn:native_archery", 1,
            EffectDefinition.Clock.EXPLICIT, EffectDefinition.Stacking.REJECT, 1, false, true,
            List.of(), List.of(), Set.of(), EffectDefinition.InstancePolicy.SINGLE_PER_TARGET,
            EffectDefinition.Overflow.REJECT, EffectDefinition.Refresh.KEEP);
    private SkeletonFamily() {}
    public static void register() {
        AbilityAdapterRegistry.effects().register(EFFECT);
        AbilityAdapterRegistry.equipmentEffects().register(EFFECT, new EquipmentEffects.Policy("dndturn:native_archery", 1,
                EquipmentEffects.Ammunition.HELD_OR_NATIVE_ORDINARY_ARROW, EquipmentEffects.Consumption.NATIVE_NO_ITEM_COST));
        GroupEffects.register(GROUP, EFFECT);
        ActorDefinitions.register(new ActorDefinitions.Provider() {
            public String id() { return GROUP; }
            public int priority() { return 0; }
            public boolean matches(LiveActorContext actor) { return actor.body() instanceof AbstractSkeleton; }
            public ActorDefinition definition(LiveActorContext actor) {
                return new ActorDefinition(GROUP, 1, Set.of(GROUP), new FactSlice(Map.of(), Map.of()));
            }
        });
        RangedAdapters.register(new RangedAdapters.Adapter("dndturn:skeleton_bow", 1,
                actor -> actor.body() instanceof AbstractSkeleton,
                (actor, target) -> ((AbstractSkeleton)actor.body()).performRangedAttack(target, 1.0F)));
        AiDefinitions.register(GROUP, 1, new AiDefinition(GROUP, 1, "dndturn:shared_ground", Set.of("BOUNDED_PROPOSALS"),
                AiDefinition.TargetPreference.VISIBLE_PLAYERS, AiDefinition.Positioning.GROUND_OPPORTUNITY,
                AiDefinition.ResourcePreference.CONSERVATIVE, AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY,
                ActorFacts.DECISION, new AiDefinition.Provenance(AiDefinition.Source.EXPLICIT_PROVIDER,
                GROUP, 1, "Minecraft 26.1 / NeoForge 26.1.2.84 skeleton family native bow")));
    }
}
