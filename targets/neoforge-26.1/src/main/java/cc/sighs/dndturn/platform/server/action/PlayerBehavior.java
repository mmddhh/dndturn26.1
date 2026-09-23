package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.BuiltinAbilities;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.spatial.GridCell;
import cc.sighs.dndturn.platform.projection.PresentationUseIdentity;
import cc.sighs.dndturn.platform.server.ability.MinecraftAbilityAdapter;
import cc.sighs.dndturn.platform.server.actor.ActorEquipment;
import cc.sighs.dndturn.platform.server.damage.DamageReceivers;
import cc.sighs.dndturn.platform.spatial.AimGeometry.Aim;
import cc.sighs.dndturn.platform.spatial.AimGeometry;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import static cc.sighs.dndturn.platform.spatial.AimGeometry.plannedAim;

/** Restricted player inventory/game-mode port for item and block behaviors.
 * Hooks run on the server thread. Discovery/validation/reach must have no world side effects.
 * prepare runs after approach/revalidation; start/tick observe vanilla effects before publishing.
 * release must be idempotent, releasing only control acquired by this execution, without firing it.
 */
public abstract class PlayerBehavior extends MinecraftAbilityAdapter {
    protected PlayerBehavior(String id) {
        super(BuiltinAbilities.require(id));
    }
    protected PlayerBehavior(AbilityDefinition definition) { super(definition); }
    @Override public GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) {
        if (!(actor.body() instanceof ServerPlayer player) || hand == null) return null;
        if (kind() == ActionIntent.Capability.USE_BLOCK) return GrantEvidence.basic(hand);
        var stack = player.getItemInHand(InteractionHand.valueOf(hand.name()));
        return supportsItem(stack) ? GrantEvidence.equipment(hand, ActorEquipment.reference(actor, hand)) : null;
    }
    @Override public final String unavailable(LiveActorContext actor, ActionIntent intent, EncounterAuthority.StateView state) {
        return unavailable(actor.requirePlayer(), intent, state);
    }
    @Override public final boolean canExecute(LiveActorContext actor, ActionIntent intent, Vec3 feet) { return canExecute(actor.requirePlayer(), intent, feet); }
    @Override public final void prepare(ActionExecutionCoordinator a, LiveActorContext actor, ActionExecutionCoordinator.Execution e) { prepare(a, actor.requirePlayer(), e); }
    @Override public final void start(ActionExecutionCoordinator a, LiveActorContext actor, ActionExecutionCoordinator.Execution e) { start(a, actor.requirePlayer(), e); }
    @Override public final void tick(ActionExecutionCoordinator a, LiveActorContext actor, ActionExecutionCoordinator.Execution e) { tick(a, actor.requirePlayer(), e); }
    @Override public final void requestCancel(ActionExecutionCoordinator a, LiveActorContext actor, ActionExecutionCoordinator.Execution e) { requestCancel(a, actor.requirePlayer(), e); }
    @Override public final void release(ActionExecutionCoordinator a, LiveActorContext actor, ActionExecutionCoordinator.Execution e) { release(a, actor.requirePlayer(), e); }
    public boolean supportsItem(ItemStack stack) { return true; }
    public int approachRadius() { return 4; }
    public abstract String unavailable(ServerPlayer player, ActionIntent intent, EncounterAuthority.StateView state);
    public void prepare(ActionExecutionCoordinator actions, ServerPlayer player, ActionExecutionCoordinator.Execution execution) {
        var intent = execution.root.intent();
        if (intent.target().cell() != null) {
            BlockPos pos = MinecraftCoordinates.pos(intent.target().cell());
            if (player.level().getServer().isUnderSpawnProtection(player.level(), pos, player) || !player.level().mayInteract(player, pos))
                throw new IllegalStateException("protected block");
        }
    }
    public abstract void start(ActionExecutionCoordinator actions, ServerPlayer player, ActionExecutionCoordinator.Execution execution);
    public abstract void tick(ActionExecutionCoordinator actions, ServerPlayer player, ActionExecutionCoordinator.Execution execution);
    public void requestCancel(ActionExecutionCoordinator actions, ServerPlayer player, ActionExecutionCoordinator.Execution execution) {}
    public void release(ActionExecutionCoordinator actions, ServerPlayer player, ActionExecutionCoordinator.Execution execution) {
        if (execution.useIdentity != null && player.isUsingItem()
            && execution.useIdentity.equals(((PresentationUseIdentity)player).dndturn$useIdentity())) player.stopUsingItem();
    }
    /** No replay: persisted evidence cannot prove that Minecraft side effects did not happen. */
    public String recoveryReason() { return "behavior execution evidence uncertain; not replayed"; }
    protected double entityRange() { return 3; }
    protected ClipContext.Fluid fluids(ServerPlayer player, ActionIntent intent) { return ClipContext.Fluid.NONE; }
    public boolean canExecute(ServerPlayer player, ActionIntent intent, Vec3 feet) {
        if (intent.target().kind() == ActionIntent.TargetKind.SELF) return true;
        GridCell target = ActionExecutionCoordinator.targetCell(player, intent);
        Vec3 eye = feet.add(0, player.getEyeHeight(), 0), destination;
        if (intent.target().kind() == ActionIntent.TargetKind.ENTITY) {
            var entity = player.level().getEntity(intent.target().entity());
            if (entity == null || feet.distanceToSqr(entity.position()) > entityRange() * entityRange()) return false;
            destination = entity.getEyePosition();
        } else {
            var t = intent.target();
            destination = new Vec3(target.x() + t.x(), target.y() + t.y(), target.z() + t.z());
            if (eye.distanceToSqr(destination) > Math.pow(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE), 2)) return false;
        }
        var hit = player.level().clip(new ClipContext(eye, destination, ClipContext.Block.COLLIDER, fluids(player, intent), player));
        return hit.getType() == HitResult.Type.MISS || intent.target().kind() == ActionIntent.TargetKind.BLOCK
            && hit.getBlockPos().equals(MinecraftCoordinates.pos(target));
    }
    public static InteractionHand hand(ActionIntent intent) { return InteractionHand.valueOf(intent.hand().name()); }
    public static ItemStack stack(ServerPlayer player, ActionIntent intent) { return player.getItemInHand(hand(intent)); }
    protected static BlockHitResult hit(ActionIntent intent) {
        var t = intent.target(); var pos = MinecraftCoordinates.pos(t.cell());
        return new BlockHitResult(new Vec3(pos.getX() + t.x(), pos.getY() + t.y(), pos.getZ() + t.z()), Direction.values()[t.face()], pos, false);
    }
    /** Pure orientation proposal; applying it is a separate, audited pose-only step. */
    
    public static Aim plannedAim(ServerPlayer player, ActionIntent intent) {
        Vec3 destination;
        if (intent.target().kind() == ActionIntent.TargetKind.ENTITY) destination = player.level().getEntity(intent.target().entity()).getBoundingBox().getCenter();
        else if (intent.target().kind() == ActionIntent.TargetKind.BLOCK) destination = hit(intent).getLocation();
        else return new Aim(player.getYRot(), player.getXRot());
        return AimGeometry.plannedAim(player.getEyePosition(), destination);
    }
    public static void applyAim(Aim aim, ServerPlayer player) {
        player.setYRot(aim.yaw()); player.setXRot(aim.pitch()); player.setYHeadRot(aim.yaw());
    }
    
    protected static String attackTarget(ServerPlayer player, ActionIntent intent, EncounterAuthority.StateView state) {
        var target = player.level().getEntity(intent.target().entity());
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) return null;
        return DamageReceivers.server().find(living) != null ? null : "target combat adapter unavailable";
    }
}
