package cc.sighs.dndturn.compat;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;

/** Independent package using only public registration contracts, with a non-skeleton native body. */
public final class ArcheryExtension {
    public static final String GROUP="test:archery_extension";
    public static final EffectDefinition EFFECT=new EffectDefinition("test:archery_effect",1,
            EffectDefinition.Clock.EXPLICIT,EffectDefinition.Stacking.REJECT,1,false,true,List.of(),List.of());
    public static void register() {
        AbilityAdapterRegistry.effects().register(EFFECT);
        AbilityAdapterRegistry.equipmentEffects().register(EFFECT,new EquipmentEffects.Policy("test:archery_policy",1,
                EquipmentEffects.Ammunition.HELD_OR_NATIVE_ORDINARY_ARROW,EquipmentEffects.Consumption.NATIVE_NO_ITEM_COST));
        GroupEffects.register(GROUP,EFFECT);
        ActorDefinitions.register(new ActorDefinitions.Provider() {
            public String id(){return GROUP;}
            public int priority(){return 10;}
            public boolean matches(LiveActorContext actor){return actor.body().getType()==EntityType.COW && actor.body().entityTags().contains(GROUP);}
            public ActorDefinition definition(LiveActorContext actor){return new ActorDefinition(GROUP,1,Set.of(GROUP),new FactSlice(Map.of(),Map.of()));}
        });
        RangedAdapters.register(new RangedAdapters.Adapter("test:cow_native_arrow",1,
                actor->actor.body().getType()==EntityType.COW && actor.body().entityTags().contains(GROUP),
                (actor,target)->{
                    var ammo=new ItemStack(Items.ARROW);
                    var arrow=ProjectileUtil.getMobArrow(actor.body(),ammo,1,actor.body().getMainHandItem());
                    var direction=target.getEyePosition().subtract(arrow.position());
                    Projectile.spawnProjectileUsingShoot(arrow,actor.level(),ammo,direction.x,direction.y,direction.z,1.6F,0);
                }));
        AiDefinitions.register(GROUP,1,new AiDefinition(GROUP,1,"dndturn:shared_ground",Set.of(),
                AiDefinition.TargetPreference.VISIBLE_PLAYERS,AiDefinition.Positioning.GROUND_OPPORTUNITY,
                AiDefinition.ResourcePreference.CONSERVATIVE,AiDefinition.RiskPreference.AUDITED_OPPORTUNITIES_ONLY,
                ActorFacts.DECISION,new AiDefinition.Provenance(AiDefinition.Source.EXPLICIT_PROVIDER,GROUP,1,"independent non-skeleton test extension")));
    }
}
