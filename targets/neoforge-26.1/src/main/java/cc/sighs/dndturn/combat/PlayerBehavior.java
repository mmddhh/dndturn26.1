package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Restricted player inventory/game-mode port for item and block behaviors.
 * Hooks run on the server thread. Discovery/validation/reach must have no world side effects.
 * prepare runs after approach/revalidation; start/tick observe vanilla effects before publishing.
 * release must be idempotent, releasing only control acquired by this execution, without firing it.
 */
public abstract class PlayerBehavior extends TacticalBehavior {
    protected PlayerBehavior(String id, String label, TacticalIntent.Capability cost, Set<TacticalIntent.TargetKind> targets) {
        super(id, label, cost, targets);
    }
    @Override public AbilitySource source(TacticalActor actor, TacticalIntent.Hand hand) {
        if (!(actor.body() instanceof ServerPlayer player) || hand == null) return null;
        if (cost() == TacticalIntent.Capability.USE_BLOCK) return AbilitySource.basic(hand);
        var stack = player.getItemInHand(InteractionHand.valueOf(hand.name()));
        return supportsItem(stack) ? AbilitySource.equipment(hand,
            new TacticalIntent.ItemReference(hand == TacticalIntent.Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40,
                TacticalItems.revision(player, stack))) : null;
    }
    @Override public final String unavailable(TacticalActor actor, TacticalIntent intent, CombatEngine.StateView state) {
        return unavailable(actor.requirePlayer(), intent, state);
    }
    @Override public final boolean canExecute(TacticalActor actor, TacticalIntent intent, Vec3 feet) { return canExecute(actor.requirePlayer(), intent, feet); }
    @Override public final void prepare(TacticalActions a, TacticalActor actor, TacticalActions.Execution e) { prepare(a, actor.requirePlayer(), e); }
    @Override public final void start(TacticalActions a, TacticalActor actor, TacticalActions.Execution e) { start(a, actor.requirePlayer(), e); }
    @Override public final void tick(TacticalActions a, TacticalActor actor, TacticalActions.Execution e) { tick(a, actor.requirePlayer(), e); }
    @Override public final void requestCancel(TacticalActions a, TacticalActor actor, TacticalActions.Execution e) { requestCancel(a, actor.requirePlayer(), e); }
    @Override public final void release(TacticalActions a, TacticalActor actor, TacticalActions.Execution e) { release(a, actor.requirePlayer(), e); }
    public boolean supportsItem(ItemStack stack) { return true; }
    public int approachRadius() { return 4; }
    public abstract String unavailable(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state);
    public void prepare(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution) {
        var intent = execution.root.intent();
        if (intent.target().cell() != null) {
            BlockPos pos = TacticalActions.pos(intent.target().cell());
            if (player.level().getServer().isUnderSpawnProtection(player.level(), pos, player) || !player.level().mayInteract(player, pos))
                throw new IllegalStateException("protected block");
        }
    }
    public abstract void start(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution);
    public abstract void tick(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution);
    public void requestCancel(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution) {}
    public void release(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution) {
        if (execution.useIdentity != null && player.isUsingItem()
            && execution.useIdentity.equals(((PresentationUseIdentity)player).dndturn$useIdentity())) player.stopUsingItem();
    }
    /** No replay: persisted evidence cannot prove that Minecraft side effects did not happen. */
    public String recoveryReason() { return "behavior execution evidence uncertain; not replayed"; }
    protected double entityRange() { return 3; }
    protected ClipContext.Fluid fluids(ServerPlayer player, TacticalIntent intent) { return ClipContext.Fluid.NONE; }
    public boolean canExecute(ServerPlayer player, TacticalIntent intent, Vec3 feet) {
        if (intent.target().kind() == TacticalIntent.TargetKind.SELF) return true;
        GridCell target = TacticalActions.targetCell(player, intent);
        Vec3 eye = feet.add(0, player.getEyeHeight(), 0), destination;
        if (intent.target().kind() == TacticalIntent.TargetKind.ENTITY) {
            var entity = player.level().getEntity(intent.target().entity());
            if (entity == null || feet.distanceToSqr(entity.position()) > entityRange() * entityRange()) return false;
            destination = entity.getEyePosition();
        } else {
            var t = intent.target();
            destination = new Vec3(target.x() + t.x(), target.y() + t.y(), target.z() + t.z());
            if (eye.distanceToSqr(destination) > Math.pow(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE), 2)) return false;
        }
        var hit = player.level().clip(new ClipContext(eye, destination, ClipContext.Block.COLLIDER, fluids(player, intent), player));
        return hit.getType() == HitResult.Type.MISS || intent.target().kind() == TacticalIntent.TargetKind.BLOCK
            && hit.getBlockPos().equals(TacticalActions.pos(target));
    }
    public static InteractionHand hand(TacticalIntent intent) { return InteractionHand.valueOf(intent.hand().name()); }
    public static ItemStack stack(ServerPlayer player, TacticalIntent intent) { return player.getItemInHand(hand(intent)); }
    protected static BlockHitResult hit(TacticalIntent intent) {
        var t = intent.target(); var pos = TacticalActions.pos(t.cell());
        return new BlockHitResult(new Vec3(pos.getX() + t.x(), pos.getY() + t.y(), pos.getZ() + t.z()), Direction.values()[t.face()], pos, false);
    }
    /** Pure orientation proposal; applying it is a separate, audited pose-only step. */
    public record Aim(float yaw, float pitch) {
        public void apply(ServerPlayer player) {
            player.setYRot(yaw); player.setXRot(pitch); player.setYHeadRot(yaw);
        }
    }
    public static Aim plannedAim(ServerPlayer player, TacticalIntent intent) {
        Vec3 destination;
        if (intent.target().kind() == TacticalIntent.TargetKind.ENTITY) destination = player.level().getEntity(intent.target().entity()).getBoundingBox().getCenter();
        else if (intent.target().kind() == TacticalIntent.TargetKind.BLOCK) destination = hit(intent).getLocation();
        else return new Aim(player.getYRot(), player.getXRot());
        return plannedAim(player.getEyePosition(), destination);
    }
    public static Aim plannedAim(Vec3 eye, Vec3 destination) {
        Vec3 delta = destination.subtract(eye);
        return new Aim((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)),
            (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }
    protected static String attackTarget(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
        var target = player.level().getEntity(intent.target().entity());
        if (target instanceof net.minecraft.world.entity.player.Player) return "PvP unsupported";
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive() || !state.members().containsKey(target.getUUID())) return "target not a living encounter member: present=" + (target != null) + " alive=" + (target != null && target.isAlive()) + " member=" + state.members().containsKey(intent.target().entity());
        return MeleeAdapters.server().receiver(living) != null ? null : "target combat adapter unavailable";
    }
}
