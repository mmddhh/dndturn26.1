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
        TacticalCapabilities.register(new IntrinsicMelee());
        TacticalCapabilities.register(new BlockUse());
        TacticalCapabilities.register(new ItemOnBlock("dndturn:place", "放置", TacticalIntent.Capability.PLACE, s -> s.getItem() instanceof BlockItem));
        TacticalCapabilities.register(new ItemOnBlock("dndturn:tool", "物品对方块使用", TacticalIntent.Capability.USE_ITEM,
            s -> s.getItem() instanceof AxeItem || s.getItem() instanceof HoeItem || s.getItem() instanceof ShovelItem
                || s.getItem() instanceof FlintAndSteelItem || s.getItem() instanceof FireChargeItem
                || s.getItem() instanceof HoneycombItem || s.getItem() instanceof ShearsItem
                || s.getItem() instanceof BoneMealItem || s.getItem() instanceof CompassItem || s.getItem() instanceof PotionItem));
        TacticalCapabilities.register(new Brush());
        TacticalCapabilities.register(new PlacedEntityBehavior());
        TacticalCapabilities.register(new FishingBehavior(false));
        TacticalCapabilities.register(new FishingBehavior(true));
        TacticalCapabilities.register(new Break());
        TacticalCapabilities.register(new Consume());
        TacticalCapabilities.register(new Equip());
        TacticalCapabilities.register(new Bucket());
        TacticalCapabilities.register(new Bottle());
        TacticalCapabilities.register(new EntityItem());
        TacticalCapabilities.register(new RangedBehavior("dndturn:bow", Items.BOW));
        TacticalCapabilities.register(new RangedBehavior("dndturn:crossbow", Items.CROSSBOW));
        TacticalCapabilities.register(new RangedBehavior("dndturn:snowball", Items.SNOWBALL));
        TacticalCapabilities.register(new ThrownItemBehavior("dndturn:egg","瞄准投掷鸡蛋",Items.EGG));
        TacticalCapabilities.register(new ThrownItemBehavior("dndturn:experience_bottle","瞄准投掷经验瓶",Items.EXPERIENCE_BOTTLE));
        TacticalCapabilities.register(new ThrownItemBehavior("dndturn:splash_potion","瞄准投掷药水",Items.SPLASH_POTION));
    }
    static final class Move extends TacticalBehavior {
        Move() { super("dndturn:move", "移动", TacticalIntent.Capability.MOVE, Set.of(TacticalIntent.TargetKind.GROUND)); }
        public AbilitySource source(TacticalActor actor, TacticalIntent.Hand hand) { return AbilitySource.basic(); }
        public String unavailable(TacticalActor actor, TacticalIntent i, CombatEngine.StateView s) { return null; }
        public int approachRadius() { return 0; }
        public boolean canExecute(TacticalActor actor, TacticalIntent i, Vec3 feet) {
            return feet.distanceToSqr(i.approach() == null ? point(i.target().cell()) : PreviewPathfinder.vector(i.approach().feet())) < .16;
        }
        public void start(TacticalActions a, TacticalActor actor, Execution e) { a.finish(actor.body(), e, OperationRecord.Outcome.COMPLETED, "arrived"); }
        public void tick(TacticalActions a, TacticalActor actor, Execution e) { throw new IllegalStateException("movement is driven by its lease"); }
    }
    static final class Melee extends PlayerBehavior {
        Melee() { super("dndturn:melee", "近战攻击", TacticalIntent.Capability.ATTACK, Set.of(TacticalIntent.TargetKind.ENTITY)); }
        public MeleeEffects meleeEffects(TacticalActor actor, boolean configuredKnockback) {
            double damage = actor.requirePlayer().getMainHandItem().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).compute(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 0.0, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            return new MeleeEffects(Math.max(0, damage), configuredKnockback, false, true);
        }
        public boolean supportsItem(ItemStack stack) {
            return !stack.isEmpty();
        }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
            if (i.hand() != TacticalIntent.Hand.MAIN_HAND) return "melee requires main hand";
            if (stack(p, i).isEmpty()) return "selected item is empty";
            return attackTarget(p, i, s);
        }
        public boolean canExecute(ServerPlayer p, TacticalIntent i, Vec3 feet) {
            return cell(BlockPos.containing(feet)).chebyshev(targetCell(p, i)) <= 1 && super.canExecute(p, i, feet);
        }
        public void start(TacticalActions a, ServerPlayer p, Execution e) {
            a.service.attackPlan(new TacticalActor(p), e.root.target(), UUID.randomUUID(), e.root.operationId(),
                result -> a.finish(p, e, result.outcome(), result.reason()));
        }
        public void tick(TacticalActions a, ServerPlayer p, Execution e) { throw new IllegalStateException("melee already terminal"); }
    }
    /** General original interaction lifecycle; adapters supply only the verified entry point. */
    abstract static class Interaction extends PlayerBehavior {
        Interaction(String id, String label, TacticalIntent.Capability cost, TacticalIntent.TargetKind kind) { super(id, label, cost, Set.of(kind)); }
        Interaction(String id, String label, TacticalIntent.Capability cost, Set<TacticalIntent.TargetKind> kinds) { super(id, label, cost, kinds); }
        protected abstract InteractionResult invoke(ServerPlayer p, TacticalIntent intent);
        protected InteractionResult invoke(ServerPlayer p, Execution execution) { return invoke(p, execution.root.intent()); }
        protected boolean accepted(ServerPlayer p, Execution e, InteractionResult result) { return result.consumesAction(); }
        public Set<Integer> observationSlots(TacticalActor actor,TacticalIntent intent) {
            // Native filled results and split compasses may insert into any inventory slot.
            var slots=new HashSet<Integer>(); for(int n=0;n<=40;n++) slots.add(n); return Set.copyOf(slots);
        }
        public void start(TacticalActions a, ServerPlayer p, Execution e) {
            a.beginStep(p, e);
            try {
                InteractionResult result;
                try (var effects = new ItemUseEffects(p,e)) { result = invoke(p, e); }
                // Tactical submission has no client-side use prediction. Bridge either native
                // swing source once through the existing confirmed presentation event channel.
                if (result instanceof InteractionResult.Success success && success.swingSource() != InteractionResult.SwingSource.NONE)
                    a.service.entityProjections().swing(p, e.root.encounterId(), e.action, hand(e.root.intent()));
                a.confirmSourceChange(p, e);
                if (result.consumesAction() && p.isUsingItem()) {
                    e.useIdentity = ((PresentationUseIdentity)p).dndturn$useIdentity();
                    a.confirmSourceChange(p, e);
                    a.accept(p, e, "item use started");
                    return;
                }
                if (cost() == TacticalIntent.Capability.USE_BLOCK && result.consumesAction()) a.observeMenu(p, e);
                boolean accepted=accepted(p,e,result);
                a.finishAction(p, e, accepted ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.REJECTED,
                    accepted ? "world interaction accepted" : "world interaction not handled");
            } catch (RuntimeException failure) { a.finishAction(p, e, OperationRecord.Outcome.UNKNOWN, "world interaction outcome unknown"); }
        }
        public void tick(TacticalActions a, ServerPlayer p, Execution e) {
            if (!p.isUsingItem() || p.getUsedItemHand() != hand(e.root.intent())) throw new IllegalStateException("item use interrupted");
            try (var effects = new ItemUseEffects(p,e)) { ActiveBodyControl.tickUse(p); }
            a.confirmSourceChange(p, e);
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
        public List<GridCell> observationFootprint(TacticalActor actor, TacticalIntent intent) {
            return TacticalImpact.itemOnBlock(new net.minecraft.world.item.context.UseOnContext(actor.requirePlayer(), hand(intent), hit(intent)))
                .stream().map(TacticalActions::cell).toList();
        }
        public boolean supportsItem(ItemStack stack) { return supports.test(stack); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) { return supports.test(stack(p, i)) ? null : "item-on-block contract unavailable"; }
        public void prepare(TacticalActions a, ServerPlayer p, Execution e) {
            super.prepare(a,p,e);
            if (!e.preparedImpact.equals(TacticalImpact.prepare(new net.minecraft.world.item.context.UseOnContext(p,hand(e.root.intent()),hit(e.root.intent())))))
                throw new IllegalStateException("prepared impact invalidated");
        }
        public List<GridCell> prepareObservation(TacticalActor actor, Execution e) {
            var intent = e.snapshot().intent();
            e.preparedImpact = TacticalImpact.prepare(new net.minecraft.world.item.context.UseOnContext(
                actor.requirePlayer(), hand(intent), hit(intent)));
            return e.preparedImpact.writes().stream().map(TacticalActions::cell).toList();
        }
        protected InteractionResult invoke(ServerPlayer p, Execution e) {
            var i = e.root.intent();
            try (var context = new TacticalUseContext(p, pos(i.target().cell()), false, e.preparedImpact)) {
                return p.gameMode.useItemOn(p, p.level(), stack(p, i), hand(i), hit(i));
            }
        }
        protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
            throw new IllegalStateException("prepared execution required");
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
    static final class Equip extends Interaction {
        Equip() { super("dndturn:equip","穿戴 / 免费整理",TacticalIntent.Capability.EQUIP,TacticalIntent.TargetKind.SELF); }
        public boolean supportsItem(ItemStack stack) {
            var value=stack.get(DataComponents.EQUIPPABLE);
            return stack.getItem().getClass()==Item.class && value!=null && value.swappable() && !stack.has(DataComponents.CONSUMABLE);
        }
        public String unavailable(ServerPlayer p,TacticalIntent i,CombatEngine.StateView s) {
            var stack=stack(p,i);var equip=stack.get(DataComponents.EQUIPPABLE);
            if(equip==null || !equip.swappable() || !equip.canBeEquippedBy(p.typeHolder()) || !p.canUseSlot(equip.slot())) return "equipment slot unavailable";
            if(stack.getCount()>1 && !p.getItemBySlot(equip.slot()).isEmpty() && p.getInventory().getFreeSlot()<0)
                return "no inventory space for displaced equipment";
            return null;
        }
        protected InteractionResult invoke(ServerPlayer p,TacticalIntent i) { return p.gameMode.useItem(p,p.level(),stack(p,i),hand(i)); }
    }
    /** One complete brushing action; the player's turn stays active until explicitly ended. */
    static final class Brush extends Interaction {
        Brush() { super("dndturn:brush", "刷取 / 完整使用", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.BLOCK); }
        public boolean supportsItem(ItemStack stack) { return stack.is(Items.BRUSH) && stack.getItem().getClass() == BrushItem.class; }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
            return p.level().getBlockEntity(pos(i.target().cell())) instanceof net.minecraft.world.level.block.entity.BrushableBlockEntity
                ? null : "target is not brushable";
        }
        public List<GridCell> observationFootprint(TacticalActor actor, TacticalIntent i) {
            return List.of(i.target().cell(), cell(pos(i.target().cell()).relative(Direction.values()[i.target().face()])));
        }
        public void prepare(TacticalActions a, ServerPlayer p, Execution e) {
            super.prepare(a,p,e);
            for (var c : observationFootprint(new TacticalActor(p),e.root.intent())) TacticalImpact.authorize(p,pos(c));
            verifyRay(p,e.root.intent());
        }
        private void verifyRay(ServerPlayer p, TacticalIntent i) {
            var result = BrushRay.current(p);
            if (!(result instanceof BlockHitResult b) || result.getType() != HitResult.Type.BLOCK
                || !b.getBlockPos().equals(pos(i.target().cell())) || b.getDirection().ordinal() != i.target().face())
                throw new IllegalStateException("brush ray no longer matches selected target");
        }
        protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
            return stack(p,i).useOn(new net.minecraft.world.item.context.UseOnContext(p,hand(i),hit(i)));
        }
        public void tick(TacticalActions a, ServerPlayer p, Execution e) {
            if (!(p.level().getBlockEntity(pos(e.targetCell)) instanceof net.minecraft.world.level.block.entity.BrushableBlockEntity)) {
                a.finishAction(p,e,OperationRecord.Outcome.COMPLETED,"brushing completed"); return;
            }
            verifyRay(p,e.root.intent());
            super.tick(a,p,e);
        }
    }
    static final class Bucket extends Interaction {
        public List<GridCell> observationFootprint(TacticalActor actor, TacticalIntent intent) {
            var clicked = pos(intent.target().cell());
            return List.of(cell(clicked), cell(clicked.relative(Direction.values()[intent.target().face()])));
        }
        public boolean supportsItem(ItemStack stack) { return stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET)
            || stack.getItem().getClass()==MobBucketItem.class; }
        Bucket() { super("dndturn:bucket", "桶 / 流体", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.BLOCK); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
            var stack = stack(p, i);
            if (!supportsItem(stack)) return "fluid or vanilla entity bucket required";
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
    static final class Bottle extends Interaction {
        Bottle() { super("dndturn:bottle","装水",TacticalIntent.Capability.USE_ITEM,TacticalIntent.TargetKind.BLOCK); }
        public boolean supportsItem(ItemStack stack) { return stack.is(Items.GLASS_BOTTLE); }
        protected ClipContext.Fluid fluids(ServerPlayer p,TacticalIntent i) { return ClipContext.Fluid.SOURCE_ONLY; }
        public String unavailable(ServerPlayer p,TacticalIntent i,CombatEngine.StateView s) {
            return p.level().getFluidState(pos(i.target().cell())).is(net.minecraft.tags.FluidTags.WATER) ? null : "water source required";
        }
        public void prepare(TacticalActions a,ServerPlayer p,Execution e) {
            super.prepare(a,p,e); var i=e.root.intent(); TacticalImpact.authorize(p,pos(i.target().cell()));
            // BottleItem prioritizes nearby dragon breath over its water ray. Never redirect the selected action.
            if (!p.level().getEntitiesOfClass(net.minecraft.world.entity.AreaEffectCloud.class,p.getBoundingBox().inflate(2),
                cloud -> cloud.isAlive() && cloud.getOwner() instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon).isEmpty())
                throw new IllegalStateException("dragon breath conflicts with selected water collection");
            var actual=p.level().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getViewVector(1).scale(p.blockInteractionRange())),
                ClipContext.Block.OUTLINE,ClipContext.Fluid.SOURCE_ONLY,p));
            if (actual.getType()!=HitResult.Type.BLOCK || !actual.getBlockPos().equals(pos(i.target().cell())))
                throw new IllegalStateException("bottle ray changed");
        }
        public Set<Integer> observationSlots(TacticalActor actor,TacticalIntent i) {
            var slots=new HashSet<Integer>(); for(int n=0;n<=40;n++) slots.add(n); return Set.copyOf(slots);
        }
        protected InteractionResult invoke(ServerPlayer p,TacticalIntent i) { return p.gameMode.useItem(p,p.level(),stack(p,i),hand(i)); }
    }
    static final class EntityItem extends Interaction {
        public boolean supportsItem(ItemStack stack) { return NativeEntityUse.supports(stack); }
        EntityItem() { super("dndturn:entity_item", "物品对实体使用", TacticalIntent.Capability.USE_ITEM, TacticalIntent.TargetKind.ENTITY); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) {
            return NativeEntityUse.unavailable(p,i);
        }
        public void prepare(TacticalActions a, ServerPlayer p, Execution e) {
            super.prepare(a,p,e);
            var target=p.level().getEntity(e.root.target());
            for (var pos:BlockPos.betweenClosed(target.getBoundingBox().inflate(1))) TacticalImpact.authorize(p,pos);
        }
        protected InteractionResult invoke(ServerPlayer p, TacticalIntent i) {
            return NativeEntityUse.invoke(p,i);
        }
    }
    static final class Break extends PlayerBehavior {
        Break() { super("dndturn:break", "持续破坏", TacticalIntent.Capability.BREAK, Set.of(TacticalIntent.TargetKind.BLOCK)); }
        public String unavailable(ServerPlayer p, TacticalIntent i, CombatEngine.StateView s) { return i.hand() == TacticalIntent.Hand.MAIN_HAND ? null : "breaking requires main hand"; }
        public void start(TacticalActions a, ServerPlayer p, Execution e) {
            a.beginStep(p, e);
            BlockPos target = pos(e.targetCell); var before = p.level().getBlockState(target);
            p.gameMode.handleBlockBreakAction(target, net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.values()[e.root.intent().target().face()], p.level().getMaxY(), 0);
            var progress = (cc.sighs.dndturn.mixin.TacticalDestroyAccessor)p.gameMode;
            if (progress.dndturn$destroying() && target.equals(progress.dndturn$destroyPos())) { e.destroyStart = progress.dndturn$destroyStart(); a.accept(p, e, "breaking started"); return; }
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
        public void release(TacticalActions a, ServerPlayer p, Execution e) {
            if (e.releaseAction == null) return;
            var progress = (cc.sighs.dndturn.mixin.TacticalDestroyAccessor)p.gameMode;
            if (e.destroyStart == null || e.destroyStart != progress.dndturn$destroyStart()
                || !pos(e.targetCell).equals(progress.dndturn$destroyPos())) return;
            progress.dndturn$clearDelayed(false);
            p.gameMode.handleBlockBreakAction(pos(e.targetCell), net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                Direction.values()[e.root.intent().target().face()], p.level().getMaxY(), 0);
        }
    }
}
