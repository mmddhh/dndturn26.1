package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Loaded/protected destination checks and bounded observations, independent of concrete classes. */
public final class TacticalImpact {
    private TacticalImpact() {}
    /** Bounded, immutable direct-placement evidence, held only for synchronous execution. */
    public record Prepared(BlockPos clicked, net.minecraft.core.Direction face,
                           net.minecraft.world.phys.Vec3 hit, float yaw, float pitch,
                           Set<BlockPos> writes, String placementState, Map<BlockPos, String> dependencies) {
        public Prepared { writes = Set.copyOf(writes); dependencies = Map.copyOf(dependencies); }
    }

    public static void authorize(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        var service = ServerRuntime.encounters(level.getServer());
        var id = service.encounterOf(player.getUUID());
        if (id == null || !level.hasChunkAt(pos) || !level.isInWorldBounds(pos)
            || !level.getWorldBorder().isWithinBounds(pos)
            || !service.state(id).region().containsBlock(pos.getX(), pos.getY(), pos.getZ())
            || !id.equals(service.encounterAtBlock(level, pos)))
            throw new IllegalStateException("effect outside loaded encounter");
        if (level.getServer().isUnderSpawnProtection(level, pos, player) || !level.mayInteract(player, pos))
            throw new IllegalStateException("protected effect destination");
    }

    public static Set<BlockPos> itemOnBlock(UseOnContext use) {
        return prepare(use).writes();
    }

    public static Prepared prepare(UseOnContext use) {
        if (!(use.getPlayer() instanceof ServerPlayer player)) throw new IllegalStateException("player impact port required");
        authorize(player, use.getClickedPos());
        Map<BlockPos, String> dependencies = new LinkedHashMap<>();
        dependencies.put(use.getClickedPos().immutable(), player.level().getBlockState(use.getClickedPos()).toString());
        var item = use.getItemInHand().getItem();
        if (item instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
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
            for (var pos : BlockPos.betweenClosed(destination.offset(-1,-1,-1), destination.offset(1,2,1))) {
                if (!player.level().hasChunkAt(pos)) throw new IllegalStateException("placement neighborhood unloaded");
                dependencies.put(pos.immutable(), player.level().getBlockState(pos).toString());
            }
            var state = block.getStateForPlacement(context);
            if (!context.canPlace() || state == null || !state.canSurvive(player.level(), destination))
                throw new IllegalStateException("placement unavailable");
            for (var pos : writes)
                if (!player.level().isUnobstructed(state, pos, CollisionContext.placementContext(player)))
                    throw new IllegalStateException("placement obstructed");
            // Native chest placement updates the partner via updateShape; authorize and
            // observe that write too, without testing collision against the existing chest.
            if (block instanceof ChestBlock && state.getValue(ChestBlock.TYPE)
                    != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                BlockPos partner = ChestBlock.getConnectedBlockPos(destination, state);
                authorize(player, partner);
                if (!player.mayUseItemAt(partner, use.getClickedFace(), use.getItemInHand()))
                    throw new IllegalStateException("chest partner permission denied");
                writes.add(partner);
            }
            return new Prepared(use.getClickedPos().immutable(), use.getClickedFace(), use.getClickLocation(),
                player.getYRot(), player.getXRot(), writes, state.toString(), dependencies);
        }
        Set<BlockPos> writes = new LinkedHashSet<>();
        BlockPos clicked = use.getClickedPos();
        var state = player.level().getBlockState(clicked);
        writes.add(clicked);
        if (item instanceof BoneMealItem && state.getBlock() instanceof BonemealableBlock growable) {
            if (!growable.isValidBonemealTarget(player.level(),clicked,state))
                throw new IllegalStateException("target cannot grow further");
        } else if (item instanceof FlintAndSteelItem || item instanceof FireChargeItem) {
            if (!CampfireBlock.canLight(state) && !CandleBlock.canLight(state) && !CandleCakeBlock.canLight(state)) {
                BlockPos fire = clicked.relative(use.getClickedFace());
                writes.add(fire);
                // Fire onPlace can construct a portal. That is a separate multi-block ability.
                for (BlockPos dependency : BlockPos.betweenClosed(fire.offset(-1,-1,-1),fire.offset(1,1,1))) {
                    if (!player.level().hasChunkAt(dependency)) throw new IllegalStateException("fire neighborhood unloaded");
                    var observed = player.level().getBlockState(dependency);
                    if (observed.is(Blocks.OBSIDIAN)) throw new IllegalStateException("portal ignition requires a separate impact adapter");
                    dependencies.put(dependency.immutable(),observed.toString());
                }
            }
        } else if (item instanceof HoneycombItem || item instanceof AxeItem) {
            if (state.getBlock() instanceof DoorBlock)
                writes.add(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? clicked.above() : clicked.below());
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE)
                != net.minecraft.world.level.block.state.properties.ChestType.SINGLE)
                writes.add(ChestBlock.getConnectedBlockPos(clicked,state));
        }
        for (BlockPos write : writes) {
            authorize(player,write);
            if (!player.mayUseItemAt(write,use.getClickedFace(),use.getItemInHand())) throw new IllegalStateException("item permission denied");
            dependencies.put(write.immutable(),player.level().getBlockState(write).toString());
        }
        return new Prepared(use.getClickedPos().immutable(), use.getClickedFace(), use.getClickLocation(),
            player.getYRot(), player.getXRot(), writes, "", dependencies);
    }
}
