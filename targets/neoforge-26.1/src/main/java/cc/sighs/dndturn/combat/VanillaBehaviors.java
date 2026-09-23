package cc.sighs.dndturn.combat;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import static cc.sighs.dndturn.combat.TacticalActions.*;

final class VanillaBehaviors {
    private VanillaBehaviors() {}
    static void register() {
        TacticalCapabilities.register(new Move());
        TacticalCapabilities.register(new Melee());
        TacticalCapabilities.register(new BlockUse());
        TacticalCapabilities.register(new ItemOnBlock("dndturn:place", "放置", TacticalIntent.Capability.PLACE, s -> s.getItem() instanceof BlockItem));
        TacticalCapabilities.register(new ItemOnBlock("dndturn:tool", "物品对方块使用", TacticalIntent.Capability.USE_ITEM,
            s -> s.getItem() instanceof AxeItem || s.getItem() instanceof HoeItem || s.getItem() instanceof ShovelItem || s.getItem() instanceof FlintAndSteelItem));
        TacticalCapabilities.register(new Break());
        TacticalCapabilities.register(new Consume());
        TacticalCapabilities.register(new Bucket());
        TacticalCapabilities.register(new EntityItem());
        TacticalCapabilities.register(new RangedBehavior("dndturn:bow", Items.BOW));
        TacticalCapabilities.register(new RangedBehavior("dndturn:crossbow", Items.CROSSBOW));
        TacticalCapabilities.register(new RangedBehavior("dndturn:snowball", Items.SNOWBALL));
    }
    static final class Move extends TacticalBehavior {
        Move() { super("dndturn:move", "移动", TacticalIntent.Capability.MOVE, Set.of(TacticalIntent.TargetKind.GROUND)); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) { return null; }
        public int approachRadius() { return 0; }
        public boolean canExecute(ServerPlayer p, TacticalIntent i, Vec3 feet) { return feet.distanceToSqr(point(i.target().cell())) < .16; }
        public void prepare(TacticalActions a, ServerPlayer p, Execution e) {}
        public void start(TacticalActions a, ServerPlayer p, Execution e) { a.finish(p, e, OperationRecord.Outcome.COMPLETED, "arrived"); }
        public void tick(TacticalActions a, ServerPlayer p, Execution e) { throw new IllegalStateException("movement is driven by its lease"); }
    }
    static final class Melee extends TacticalBehavior {
        Melee() { super("dndturn:melee", "近战攻击", TacticalIntent.Capability.ATTACK, Set.of(TacticalIntent.TargetKind.ENTITY)); }
        public boolean supportsItem(ItemStack stack) {
            return !(stack.getItem() instanceof ProjectileWeaponItem) && !stack.is(Items.SNOWBALL)
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft");
        }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
            if (i.hand() != TacticalIntent.Hand.MAIN_HAND) return "melee requires main hand";
            var stack = stack(p, i);
            if (stack.getItem() instanceof ProjectileWeaponItem || stack.is(Items.SNOWBALL)) return "item has a ranged adapter; no melee fallback";
            // Generic attack contract permits ordinary equipment; custom side effects need explicit registration.
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")) return "custom attack effects require an adapter";
            return attackTarget(p, i, s);
        }
        public boolean canExecute(ServerPlayer p, TacticalIntent i, Vec3 feet) {
            return cell(BlockPos.containing(feet)).chebyshev(targetCell(p, i)) <= 1 && super.canExecute(p, i, feet);
        }
        public void start(TacticalActions a, ServerPlayer p, Execution e) {
            var result = a.service.attackPlan(p, e.root.target(), UUID.randomUUID(), e.root.operationId());
            a.finish(p, e, result.outcome(), result.reason());
        }
        public void tick(TacticalActions a, ServerPlayer p, Execution e) { throw new IllegalStateException("melee already terminal"); }
    }
    /** General original interaction lifecycle; adapters supply only the verified entry point. */
    abstract static class Interaction extends TacticalBehavior {
        Interaction(String id, String label, TacticalIntent.Capability cost, TacticalIntent.TargetKind kind) { super(id, label, cost, Set.of(kind)); }
        protected abstract InteractionResult invoke(ServerPlayer p, TacticalIntent intent);
        public void start(TacticalActions a, ServerPlayer p, Execution e) {
            a.beginStep(p, e);
            try {
                InteractionResult result = invoke(p, e.root.intent());
                if (result.consumesAction() && p.isUsingItem()) {
                    e.runningItemRevision = TacticalItems.revision(p, stack(p, e.root.intent()));
                    a.accept(p, e, "item use started");
                    return;
                }
                if (cost() == TacticalIntent.Capability.USE_BLOCK && result.consumesAction()) a.observeMenu(p, e);
                a.finishAction(p, e, result.consumesAction() ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.REJECTED,
                    result.consumesAction() ? "world interaction accepted" : "world interaction not handled");
            } catch (RuntimeException failure) { a.finishAction(p, e, OperationRecord.Outcome.UNKNOWN, "world interaction outcome unknown"); }
        }
        public void tick(TacticalActions a, ServerPlayer p, Execution e) {
            if (!p.isUsingItem() || p.getUsedItemHand() != hand(e.root.intent())) throw new IllegalStateException("item use interrupted");
            ((cc.sighs.dndturn.mixin.TacticalUseTickAccessor)p).dndturn$tickUsingItem();
            e.runningItemRevision = TacticalItems.revision(p, stack(p, e.root.intent()));
            if (!p.isUsingItem()) a.finishAction(p, e, OperationRecord.Outcome.COMPLETED, "item use finished");
        }
    }
    static final class BlockUse extends Interaction {
        BlockUse() { super("dndturn:block", "方块自身交互", TacticalIntent.Capability.USE_BLOCK, TacticalIntent.TargetKind.BLOCK); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
            return i.hand() == TacticalIntent.Hand.MAIN_HAND ? null : "block-only vanilla branch requires main hand";
        }
        protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
            try (var context = new TacticalUseContext(p, pos(i.target().cell()), true)) {
                return p.gameMode.useItemOn(p, p.level(), stack(p, i), hand(i), hit(i));
            }
        }
    }
    static final class ItemOnBlock extends Interaction {
        private final Predicate<ItemStack> supports;
        ItemOnBlock(String id, String label, TacticalIntent.Capability cost, Predicate<ItemStack> supports) {
            super(id, label, cost, TacticalIntent.TargetKind.BLOCK); this.supports = supports;
        }
        public boolean supportsItem(ItemStack stack) { return supports.test(stack); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) { return supports.test(stack(p, i)) ? null : "item-on-block contract unavailable"; }
        public void prepare(TacticalActions a, ServerPlayer p, Execution e) {
            super.prepare(a,p,e);
            TacticalImpact.itemOnBlock(new net.minecraft.world.item.context.UseOnContext(p,hand(e.root.intent()),hit(e.root.intent())));
        }
        protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
            try (var context = new TacticalUseContext(p, pos(i.target().cell()), false)) {
                return p.gameMode.useItemOn(p, p.level(), stack(p, i), hand(i), hit(i));
            }
        }
    }
    static final class Consume extends Interaction {
        public boolean supportsItem(ItemStack stack) { return stack.has(DataComponents.CONSUMABLE); }
        Consume() { super("dndturn:consume", "自身使用 / 食饮", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.SELF); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
            return stack(p, i).has(DataComponents.CONSUMABLE) ? null : "consumable component required";
        }
        protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) { return p.gameMode.useItem(p, p.level(), stack(p, i), hand(i)); }
    }
    static final class Bucket extends Interaction {
        public boolean supportsItem(ItemStack stack) { return stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET); }
        Bucket() { super("dndturn:bucket", "桶 / 流体", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.BLOCK); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
            var stack = stack(p, i);
            if (!stack.is(Items.BUCKET) && !stack.is(Items.WATER_BUCKET) && !stack.is(Items.LAVA_BUCKET)) return "ordinary fluid bucket required; entity buckets need an adapter";
            BlockPos adjacent = pos(i.target().cell()).relative(Direction.values()[i.target().face()]);
            if (!p.level().hasChunkAt(adjacent) || !s.region().containsPoint(adjacent.getX()+.5, adjacent.getY()+.5, adjacent.getZ()+.5)) return "bucket effect outside loaded encounter";
            if (p.level().getServer().isUnderSpawnProtection(p.level(), adjacent, p) || !p.level().mayInteract(p, adjacent)) return "protected fluid destination";
            return null;
        }
        protected ClipContext.Fluid fluids(ServerPlayer p, TacticalIntent i) { return stack(p, i).is(Items.BUCKET) ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE; }
        public void prepare(TacticalActions a, ServerPlayer p, Execution e) {
            super.prepare(a, p, e);
            TacticalImpact.authorize(p, pos(e.root.intent().target().cell()));
            TacticalImpact.authorize(p, pos(e.root.intent().target().cell()).relative(Direction.values()[e.root.intent().target().face()]));
            var i = e.root.intent();
            var clicked = p.level().getBlockState(pos(i.target().cell()));
            var adjacent = p.level().getBlockState(pos(i.target().cell()).relative(Direction.values()[i.target().face()]));
            if (stack(p,i).is(Items.BUCKET)) {
                if (clicked.getBlock().getClass() != net.minecraft.world.level.block.LiquidBlock.class)
                    throw new IllegalStateException("bucket pickup impact adapter unavailable");
            } else if (clicked.getBlock() instanceof net.minecraft.world.level.block.LiquidBlockContainer
                || !adjacent.isAir() && adjacent.getBlock().getClass() != net.minecraft.world.level.block.LiquidBlock.class)
                throw new IllegalStateException("bucket replacement impact adapter unavailable");
            var eye = p.getEyePosition();
            var actual = p.level().clip(new ClipContext(eye, eye.add(p.calculateViewVector(p.getXRot(), p.getYRot()).scale(p.blockInteractionRange())), ClipContext.Block.OUTLINE, fluids(p, i), p));
            if (actual.getType() != HitResult.Type.BLOCK || !actual.getBlockPos().equals(pos(i.target().cell())) || actual.getDirection().ordinal() != i.target().face())
                throw new IllegalStateException("bucket vanilla ray no longer matches target and face: " + actual.getType() + " " + actual.getBlockPos() + " " + actual.getDirection() + " eye=" + eye + " view=" + p.getViewVector(1));
        }
        protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) { return p.gameMode.useItem(p, p.level(), stack(p, i), hand(i)); }
    }
    static final class EntityItem extends Interaction {
        public boolean supportsItem(ItemStack stack) { return stack.is(Items.NAME_TAG); }
        EntityItem() { super("dndturn:entity_item", "物品对实体使用", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.ENTITY); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
            if (!stack(p, i).is(Items.NAME_TAG)) return "entity item effects require a registered contract";
            return attackTarget(p, i, s);
        }
        protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
            var target = p.level().getEntity(i.target().entity());
            return p.interactOn(target, hand(i), target.getBoundingBox().getCenter().subtract(target.position()));
        }
    }
    static final class Break extends TacticalBehavior {
        Break() { super("dndturn:break", "持续破坏", TacticalIntent.Capability.BREAK, Set.of(TacticalIntent.TargetKind.BLOCK)); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) { return i.hand() == TacticalIntent.Hand.MAIN_HAND ? null : "breaking requires main hand"; }
        public void start(TacticalActions a, ServerPlayer p, Execution e) {
            a.beginStep(p, e);
            BlockPos target = pos(e.targetCell); var before = p.level().getBlockState(target);
            p.gameMode.handleBlockBreakAction(target, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.values()[e.root.intent().target().face()], p.level().getMaxY(), 0);
            var progress = (cc.sighs.dndturn.mixin.TacticalDestroyAccessor)p.gameMode;
            if (progress.dndturn$destroying() && target.equals(progress.dndturn$destroyPos())) { a.accept(p, e, "breaking started"); return; }
            boolean changed = !before.equals(p.level().getBlockState(target));
            a.finishAction(p, e, changed ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.REJECTED,
                changed ? "block destroyed" : "block breaking rejected");
        }
        public void tick(TacticalActions a, ServerPlayer p, Execution e) {
            BlockPos target = pos(e.targetCell); var state = p.level().getBlockState(target);
            if (!state.toString().equals(e.blockState)) throw new IllegalStateException("breaking target changed");
            var progress = (cc.sighs.dndturn.mixin.TacticalDestroyAccessor)p.gameMode;
            if (!progress.dndturn$destroying() || !target.equals(progress.dndturn$destroyPos())) throw new IllegalStateException("breaking interrupted");
            p.gameMode.tick();
            float amount = state.getDestroyProgress(p, p.level(), target) * (progress.dndturn$gameTicks() - progress.dndturn$destroyStart() + 1);
            if (amount < 1) return;
            p.gameMode.handleBlockBreakAction(target, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                Direction.values()[e.root.intent().target().face()], p.level().getMaxY(), 0);
            boolean changed = !state.equals(p.level().getBlockState(target));
            a.finishAction(p, e, changed ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.REJECTED,
                changed ? "block destroyed" : "block destruction rejected");
        }
        public void cancel(TacticalActions a, ServerPlayer p, Execution e) {
            if (e.action == null) return;
            var progress = (cc.sighs.dndturn.mixin.TacticalDestroyAccessor)p.gameMode;
            if (!pos(e.targetCell).equals(progress.dndturn$destroyPos())) return;
            progress.dndturn$clearDelayed(false);
            p.gameMode.handleBlockBreakAction(pos(e.targetCell), net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                Direction.values()[e.root.intent().target().face()], p.level().getMaxY(), 0);
        }
    }
}
