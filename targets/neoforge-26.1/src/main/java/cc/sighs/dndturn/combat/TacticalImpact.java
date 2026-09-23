package cc.sighs.dndturn.combat;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
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
    /** Bounded, immutable direct-placement evidence, held only for synchronous execution. */
    public record Prepared(BlockPos clicked, net.minecraft.core.Direction face,
                           net.minecraft.world.phys.Vec3 hit, float yaw, float pitch,
                           Set<BlockPos> writes, String placementState, Map<BlockPos, String> dependencies) {
        public Prepared { writes = Set.copyOf(writes); dependencies = Map.copyOf(dependencies); }
    }

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
            if (use.getItemInHand().has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
                || !use.getItemInHand().getOrDefault(net.minecraft.core.component.DataComponents.BLOCK_STATE,
                    net.minecraft.world.item.component.BlockItemStateProperties.EMPTY).isEmpty())
                throw new IllegalStateException("custom placement state requires an impact adapter");
            if (item.getClass() != BlockItem.class && item.getClass() != DoubleHighBlockItem.class && item.getClass() != BedItem.class)
                throw new IllegalStateException("unsupported placement override");
            var block = blockItem.getBlock();
            Set<Class<?>> supported = Set.of(Block.class, RotatedPillarBlock.class, SlabBlock.class,
                StairBlock.class, DoorBlock.class, BedBlock.class, DoublePlantBlock.class,
                ChestBlock.class, TrappedChestBlock.class);
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
        if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft"))
            throw new IllegalStateException("custom block effects require an impact adapter");
        if (item.getClass() == BoneMealItem.class) {
            // These exact growth implementations only update their own crop (no feature generation).
            if (!Set.of(CropBlock.class, CarrotBlock.class, PotatoBlock.class, BeetrootBlock.class,
                CocoaBlock.class, SweetBerryBushBlock.class).contains(state.getBlock().getClass()))
                throw new IllegalStateException("bonemeal feature generation requires a bounded growth adapter");
            if (!((BonemealableBlock)state.getBlock()).isValidBonemealTarget(player.level(),clicked,state))
                throw new IllegalStateException("crop cannot grow further");
        } else if (item.getClass() == FlintAndSteelItem.class || item.getClass() == FireChargeItem.class) {
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
        } else if (item.getClass() == HoneycombItem.class || item.getClass() == AxeItem.class) {
            if (state.getBlock() instanceof DoorBlock)
                writes.add(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? clicked.above() : clicked.below());
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE)
                != net.minecraft.world.level.block.state.properties.ChestType.SINGLE)
                writes.add(ChestBlock.getConnectedBlockPos(clicked,state));
        } else if (item.getClass() != HoeItem.class && item.getClass() != ShovelItem.class
            && item.getClass() != ShearsItem.class && item.getClass() != CompassItem.class && item.getClass() != PotionItem.class) {
            throw new IllegalStateException("item impact adapter unavailable");
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
