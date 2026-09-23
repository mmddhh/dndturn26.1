package cc.sighs.dndturn.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Cast and retrieve are distinct commands. Waiting never guarantees a catch. */
final class FishingBehavior extends VanillaBehaviors.Interaction {
    private final boolean retrieve;
    FishingBehavior(boolean retrieve) {
        super(retrieve?"dndturn:reel":"dndturn:fishing_rod",retrieve?"收竿":"瞄准抛竿",
            TacticalIntent.Capability.USE_ITEM,retrieve?TacticalIntent.TargetKind.ENTITY:TacticalIntent.TargetKind.BLOCK);
        this.retrieve=retrieve;
    }
    public boolean supportsItem(ItemStack stack) { return stack.is(Items.FISHING_ROD); }
    public AbilitySource source(TacticalActor actor,TacticalIntent.Hand hand) {
        if (!(actor.body() instanceof ServerPlayer p) || (p.fishing!=null)!=retrieve) return null;
        return super.source(actor,hand);
    }
    public String unavailable(ServerPlayer p,TacticalIntent i,CombatEngine.StateView s) {
        if (!retrieve) return p.fishing==null?null:"retrieve existing hook before casting";
        if (p.fishing==null || !p.fishing.getUUID().equals(i.target().entity()) || p.fishing.getOwner()!=p)
            return "selected fishing hook replaced";
        var service=ServerCombatService.forServer(p.level().getServer());
        if (!service.ownsItemProjectile(p,p.fishing)) return "fishing hook has no verified cast";
        var caught=p.fishing.getHookedIn();
        if (caught!=null && (caught instanceof net.minecraft.world.entity.player.Player
            || !s.id().equals(service.encounterAtBlock(p.level(),caught.blockPosition())))) return "hooked entity outside retrieval permission";
        return null;
    }
    public boolean canExecute(ServerPlayer p,TacticalIntent i,Vec3 feet) {
        return retrieve ? p.fishing!=null && p.fishing.distanceToSqr(p)<=1024 : feet.distanceToSqr(hit(i).getLocation())<=36;
    }
    public void prepare(TacticalActions a,ServerPlayer p,TacticalActions.Execution e) {
        super.prepare(a,p,e);
        if (retrieve) {
            for(var pos:net.minecraft.core.BlockPos.betweenClosed(p.fishing.getBoundingBox().inflate(1))) TacticalImpact.authorize(p,pos);
        }
    }
    protected InteractionResult invoke(ServerPlayer p,TacticalIntent i) { throw new IllegalStateException("bound fishing action required"); }
    protected InteractionResult invoke(ServerPlayer p,TacticalActions.Execution e) {
        var root=e.root;
        try(var launch=new TacticalLaunchContext(p.getUUID(),root.encounterId(),e.action,null,null,root.operationId(),root.intent())) {
            return p.gameMode.useItem(p,p.level(),stack(p,root.intent()),hand(root.intent()));
        }
    }
}
