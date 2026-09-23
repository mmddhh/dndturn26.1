package cc.sighs.dndturn.combat;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Fixed .84 direct-write contracts. Unknown overrides never inherit placement permission. */
public final class TacticalImpact {
    private TacticalImpact() {}

    public static void authorize(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        var service = ServerCombatService.forServer(level.getServer());
        var id = service.encounterOf(player.getUUID());
        if (id == null || !level.hasChunkAt(pos) || !level.isInWorldBounds(pos)
            || !level.getWorldBorder().isWithinBounds(pos)
            || !service.state(id).region().containsBlock(pos.getX(), pos.getY(), pos.getZ())
            || !id.equals(service.encounterAtBlock(level, pos)))
            throw new IllegalStateException("effect outside loaded encounter");
        if (level.getServer().isUnderSpawnProtection(level, pos, player) || !level.mayInteract(player, pos))
            throw new IllegalStateException("protected effect destination");
    }

    public static void itemOnBlock(UseOnContext use) {
        if (!(use.getPlayer() instanceof ServerPlayer player)) return;
        authorize(player, use.getClickedPos());
        var item = use.getItemInHand().getItem();
        if (item instanceof BlockItem blockItem) {
            if (use.getItemInHand().has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
                || !use.getItemInHand().getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_STATE,
                    net.minecraft.world.item.component.BlockItemStateProperties.EMPTY).isEmpty())
                throw new IllegalStateException("custom placement state requires an impact adapter");
            if (item.getClass() != BlockItem.class && item.getClass() != DoubleHighBlockItem.class && item.getClass() != BedItem.class)
                throw new IllegalStateException("unsupported placement override");
            var block = blockItem.getBlock();
            Set<Class<?>> supported = Set.of(Block.class, RotatedPillarBlock.class, SlabBlock.class,
                StairBlock.class, DoorBlock.class, BedBlock.class, DoublePlantBlock.class);
            if (!supported.contains(block.getClass())) throw new IllegalStateException("placement impact adapter unavailable");
            var context = new BlockPlaceContext(use);
            BlockPos destination = context.getClickedPos();
            Set<BlockPos> writes = new LinkedHashSet<>();
            writes.add(destination);
            if (item instanceof DoubleHighBlockItem || block instanceof DoorBlock || block instanceof DoublePlantBlock)
                writes.add(destination.above());
            if (block instanceof BedBlock) writes.add(destination.relative(context.getHorizontalDirection()));
            for (var pos : writes) {
                authorize(player, pos);
                if (!player.mayUseItemAt(pos,use.getClickedFace(),use.getItemInHand())) throw new IllegalStateException("placement permission denied");
            }
            // Placement state queries inspect neighboring blocks; do not let these load chunks.
            for (var pos : BlockPos.betweenClosed(destination.offset(-1,-1,-1), destination.offset(1,2,1)))
                if (!player.level().hasChunkAt(pos)) throw new IllegalStateException("placement neighborhood unloaded");
            var state = block.getStateForPlacement(context);
            if (!context.canPlace() || state == null || !state.canSurvive(player.level(), destination))
                throw new IllegalStateException("placement unavailable");
            for (var pos : writes)
                if (!player.level().isUnobstructed(state, pos, CollisionContext.placementContext(player)))
                    throw new IllegalStateException("placement obstructed");
        } else if (item.getClass() != AxeItem.class && item.getClass() != HoeItem.class && item.getClass() != ShovelItem.class) {
            throw new IllegalStateException("item impact adapter unavailable");
        }
    }
}
