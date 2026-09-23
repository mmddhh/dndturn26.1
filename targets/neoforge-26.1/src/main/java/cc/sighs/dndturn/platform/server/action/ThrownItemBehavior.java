package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Aimed use, including empty terrain. Launch acceptance costs one action; contact is a child effect. */
final class ThrownItemBehavior extends VanillaBehaviors.Interaction {
    private final Item item;
    ThrownItemBehavior(String id,Item item) {
        super(id); this.item=item;
    }
    public boolean supportsItem(ItemStack stack) { return item == Items.EGG ? stack.getItem() instanceof EggItem
            : item == Items.EXPERIENCE_BOTTLE ? stack.getItem() instanceof ExperienceBottleItem
            : item == Items.SPLASH_POTION && stack.getItem() instanceof SplashPotionItem; }
    public String unavailable(ServerPlayer p,ActionIntent i,EncounterAuthority.StateView s) {
        if (!supportsItem(stack(p,i))) return "selected throwable changed";
        if (item==Items.SPLASH_POTION) {
            var contents=stack(p,i).get(DataComponents.POTION_CONTENTS);
            if (contents==null || !contents.hasEffects()) return "water splash needs a separate block-effect adapter";
            for (var effect:contents.getAllEffects())
                if (effect.getEffect().value().isInstantenous())
                    return "potion effect requires a dedicated impact adapter";
        }
        return null;
    }
    public boolean canExecute(ServerPlayer p,ActionIntent i,Vec3 feet) {
        if (i.target().entity()!=null) {
            var target=p.level().getEntity(i.target().entity());
            return target!=null && feet.distanceToSqr(target.position())<=36;
        }
        return feet.distanceToSqr(hit(i).getLocation())<=36;
    }
    protected net.minecraft.world.InteractionResult invoke(ServerPlayer p,ActionIntent i) { throw new IllegalStateException("bound launch required"); }
    protected net.minecraft.world.InteractionResult invoke(ServerPlayer p,ActionExecutionCoordinator.Execution e) {
        var r=e.root;
        try (var launch=new TacticalLaunchContext(p.getUUID(),r.encounterId(),e.action,null,null,r.operationId(),r.intent())) {
            return p.gameMode.useItem(p,p.level(),stack(p,r.intent()),hand(r.intent()));
        }
    }
}
