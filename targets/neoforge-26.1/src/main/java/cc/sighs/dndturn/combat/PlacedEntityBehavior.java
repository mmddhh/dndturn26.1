package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Vanilla entity placement; generated identities are captured by the synchronous effect scope. */
final class PlacedEntityBehavior extends VanillaBehaviors.Interaction {
    PlacedEntityBehavior() { super("dndturn:spawn_item","放置实体 / 刷怪蛋",TacticalIntent.Capability.USE_ITEM,TacticalIntent.TargetKind.BLOCK); }
    public boolean supportsItem(ItemStack stack) {
        return Set.of(SpawnEggItem.class,ArmorStandItem.class,HangingEntityItem.class,ItemFrameItem.class,
            MinecartItem.class,BoatItem.class,EndCrystalItem.class).contains(stack.getItem().getClass());
    }
    public String unavailable(ServerPlayer p,TacticalIntent i,CombatEngine.StateView s) {
        var stack=stack(p,i);
        if (!supportsItem(stack)) return "entity placement adapter unavailable";
        // Default spawn-egg entity data selects the species; user-supplied entity NBT is separate.
        if (!Objects.equals(stack.get(DataComponents.ENTITY_DATA),stack.getItem().components().get(DataComponents.ENTITY_DATA)))
            return "custom entity data requires a placement adapter";
        if (stack.is(Items.END_CRYSTAL) && p.level().getDragonFight()!=null) return "dragon resurrection is outside item placement scope";
        if (stack.getItem() instanceof SpawnEggItem) {
            var type=SpawnEggItem.getType(stack);
            if (type==null || type==EntityType.ENDER_DRAGON || type==EntityType.WITHER)
                return "boss spawn effects require a dedicated adapter";
            if (p.level().getBlockEntity(TacticalActions.pos(i.target().cell())) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity)
                return "spawner modification requires a block-entity adapter";
        }
        return null;
    }
    protected ClipContext.Fluid fluids(ServerPlayer p,TacticalIntent i) { return stack(p,i).getItem() instanceof BoatItem ? ClipContext.Fluid.ANY : ClipContext.Fluid.SOURCE_ONLY; }
    public void prepare(TacticalActions a,ServerPlayer p,TacticalActions.Execution e) {
        super.prepare(a,p,e);
        var i=e.root.intent(); var clicked=TacticalActions.pos(i.target().cell()); var stack=stack(p,i);
        double width=2,height=3;
        if (stack.getItem() instanceof SpawnEggItem) {
            var size=SpawnEggItem.getType(stack).getDimensions(); width=Math.max(width,size.width()+1); height=Math.max(height,size.height()+1);
        }
        if (stack.is(Items.PAINTING)) { width=5; height=5; }
        if (width>8 || height>8) throw new IllegalStateException("placement dimensions exceed supported footprint");
        // This is a geometry/permission check, not a snapshot or hash of the surrounding world.
        var center=hit(i).getLocation();
        for (var pos:BlockPos.betweenClosed(BlockPos.containing(center.x-width,center.y-1,center.z-width),
            BlockPos.containing(center.x+width,center.y+height,center.z+width))) TacticalImpact.authorize(p,pos);
        if (!p.mayUseItemAt(clicked,hit(i).getDirection(),stack)) throw new IllegalStateException("placement permission denied");
        if (stack.getItem() instanceof BoatItem) {
            var actual=p.level().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getViewVector(1).scale(p.blockInteractionRange())),
                ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,p));
            if (actual.getType()!=HitResult.Type.BLOCK || !actual.getBlockPos().equals(clicked) || actual.getDirection()!=hit(i).getDirection())
                throw new IllegalStateException("boat ray changed");
        }
    }
    protected InteractionResult invoke(ServerPlayer p,TacticalIntent i) {
        return stack(p,i).getItem() instanceof BoatItem ? p.gameMode.useItem(p,p.level(),stack(p,i),hand(i))
            : stack(p,i).useOn(new UseOnContext(p,hand(i),hit(i)));
    }
    protected boolean accepted(ServerPlayer p,TacticalActions.Execution e,InteractionResult result) {
        if (result.consumesAction() && e.spawned.isEmpty())
            throw new IllegalStateException("placement accepted without confirmed insertion");
        return result.consumesAction();
    }
}
