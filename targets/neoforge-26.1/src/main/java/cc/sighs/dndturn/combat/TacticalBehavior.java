package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Server extension contract. Instances are immutable; execution state contains values only.
 * Hooks run on the server thread. Discovery/validation/reach must have no world side effects.
 * prepare runs after approach/revalidation; start/tick observe vanilla effects before publishing.
 * cancel must be idempotent, releasing only control acquired by this execution, without firing it.
 */
public abstract class TacticalBehavior {
    public enum CommitPoint { LEGAL_ATTACK, ACCEPTED_EFFECT, MOVEMENT_OBSERVATION, FREE }
    private final String id, label;
    private final int version;
    private final TacticalIntent.Capability cost;
    private final Set<TacticalIntent.TargetKind> targets;
    protected TacticalBehavior(String id, String label, TacticalIntent.Capability cost, Set<TacticalIntent.TargetKind> targets) {
        this(id, 1, label, cost, targets);
    }
    protected TacticalBehavior(String id, int version, String label, TacticalIntent.Capability cost, Set<TacticalIntent.TargetKind> targets) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 128 || version < 1 || label.isBlank() || label.length() > 128)
            throw new IllegalArgumentException("behavior metadata");
        this.id = id; this.version = version; this.label = label; this.cost = Objects.requireNonNull(cost);
        this.targets = Set.copyOf(targets);
        if (this.targets.isEmpty()) throw new IllegalArgumentException("target kinds required");
    }
    public final String id() { return id; }
    public final int version() { return version; }
    public final String label() { return label; }
    public final TacticalIntent.Capability cost() { return cost; }
    public final Set<TacticalIntent.TargetKind> targets() { return targets; }
    public final CommitPoint commitPoint() {
        return switch (cost) {
            case ATTACK -> CommitPoint.LEGAL_ATTACK; case MOVE -> CommitPoint.MOVEMENT_OBSERVATION;
            case USE_BLOCK -> CommitPoint.FREE; default -> CommitPoint.ACCEPTED_EFFECT;
        };
    }
    public boolean supportsItem(ItemStack stack) { return true; }
    public int approachRadius() { return 4; }
    public abstract String unavailable(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state);
    public final void validate(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
        if (!targets.contains(intent.target().kind())) throw new IllegalStateException("unsupported target kind");
        String reason = unavailable(player, intent, state);
        if (reason != null) throw new IllegalStateException(reason);
    }
    public void prepare(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution) {
        var intent = execution.root.intent();
        if (intent.target().cell() != null) {
            BlockPos pos = TacticalActions.pos(intent.target().cell());
            if (player.level().getServer().isUnderSpawnProtection(player.level(), pos, player) || !player.level().mayInteract(player, pos))
                throw new IllegalStateException("protected block");
        }
        aim(player, intent);
    }
    public abstract void start(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution);
    public abstract void tick(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution);
    public void cancel(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution) {
        if (execution.action != null && player.isUsingItem() && player.getUsedItemHand() == hand(execution.root.intent())) player.stopUsingItem();
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
    protected static void aim(ServerPlayer player, TacticalIntent intent) {
        Vec3 destination;
        if (intent.target().kind() == TacticalIntent.TargetKind.ENTITY) destination = player.level().getEntity(intent.target().entity()).getBoundingBox().getCenter();
        else if (intent.target().kind() == TacticalIntent.TargetKind.BLOCK) destination = hit(intent).getLocation();
        else return;
        Vec3 delta = destination.subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
        player.setYHeadRot(player.getYRot());
    }
    protected static String attackTarget(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
        var target = player.level().getEntity(intent.target().entity());
        if (target instanceof net.minecraft.world.entity.player.Player) return "PvP unsupported";
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive() || !state.members().containsKey(target.getUUID())) return "target not a living encounter member: present=" + (target != null) + " alive=" + (target != null && target.isAlive()) + " member=" + state.members().containsKey(intent.target().entity());
        return target.getType() == net.minecraft.world.entity.EntityType.ZOMBIE ? null : "target combat adapter unavailable";
    }
}
