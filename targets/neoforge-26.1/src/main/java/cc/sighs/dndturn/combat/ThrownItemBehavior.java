package cc.sighs.dndturn.combat;

import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Aimed use, including empty terrain. Launch acceptance costs one action; contact is a child effect. */
final class ThrownItemBehavior extends VanillaBehaviors.Interaction {
    private final Item item;
    ThrownItemBehavior(String id,String label,Item item) {
        super(id,label,TacticalIntent.Capability.USE_ITEM,Set.of(TacticalIntent.TargetKind.BLOCK,TacticalIntent.TargetKind.ENTITY)); this.item=item;
    }
    public boolean supportsItem(ItemStack stack) { return stack.is(item); }
    public String unavailable(ServerPlayer p,TacticalIntent i,CombatEngine.StateView s) {
        if (!stack(p,i).is(item)) return "selected throwable changed";
        if (item==Items.SPLASH_POTION) {
            var contents=stack(p,i).get(DataComponents.POTION_CONTENTS);
            if (contents==null || !contents.hasEffects()) return "water splash needs a separate block-effect adapter";
            for (var effect:contents.getAllEffects())
                if (!ParticipantEffects.supported(effect) || effect.getEffect().value().isInstantenous())
                    return "potion effect requires a dedicated impact adapter";
        }
        return null;
    }
    public boolean canExecute(ServerPlayer p,TacticalIntent i,Vec3 feet) {
        if (i.target().entity()!=null) {
            var target=p.level().getEntity(i.target().entity());
            return target!=null && feet.distanceToSqr(target.position())<=36;
        }
        return feet.distanceToSqr(hit(i).getLocation())<=36;
    }
    protected net.minecraft.world.InteractionResult invoke(ServerPlayer p,TacticalIntent i) { throw new IllegalStateException("bound launch required"); }
    protected net.minecraft.world.InteractionResult invoke(ServerPlayer p,TacticalActions.Execution e) {
        var r=e.root;
        try (var launch=new TacticalLaunchContext(p.getUUID(),r.encounterId(),e.action,null,null,r.operationId(),r.intent())) {
            return p.gameMode.useItem(p,p.level(),stack(p,r.intent()),hand(r.intent()));
        }
    }
}
