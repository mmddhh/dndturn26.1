package cc.sighs.dndturn.combat;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Ordinary ammunition only; launch ownership outlives this adapter's execution. */
final class RangedBehavior extends TacticalBehavior {
    private final Item weapon;
    RangedBehavior(String id, Item weapon) {
        super(id, "使用 " + weapon.toString(), TacticalIntent.Capability.ATTACK, Set.of(TacticalIntent.TargetKind.ENTITY));
        this.weapon = weapon;
    }
    @Override public boolean supportsItem(ItemStack stack) { return stack.is(weapon); }
    @Override public String unavailable(ServerPlayer player, TacticalIntent intent, CombatEngine.StateView state) {
        if (!stack(player, intent).is(weapon)) return "selected ranged weapon required";
        try { validateAmmo(player, intent); } catch (IllegalStateException e) { return e.getMessage(); }
        return attackTarget(player, intent, state);
    }
    private void validateAmmo(ServerPlayer player, TacticalIntent intent) {
            ItemStack weapon = stack(player, intent);
            if (!weapon.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()) throw new IllegalStateException("ranged enchantments unsupported");
            if (!weapon.is(net.minecraft.world.item.Items.SNOWBALL)) {
                var charged = weapon.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
                var ammo = charged != null && !charged.isEmpty() ? charged.itemCopies() : List.of(player.getProjectile(weapon));
                if (ammo.size() != 1 || !ammo.getFirst().is(net.minecraft.world.item.Items.ARROW)
                    || ammo.getFirst().has(net.minecraft.core.component.DataComponents.POTION_CONTENTS))
                    throw new IllegalStateException("ordinary arrows required");
            }
    }
    @Override protected double entityRange() { return 6; }
    public void start(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution) {
        var service = actions.service; var engine = actions.engine; UUID child = UUID.randomUUID();
        var root = execution.root;
        var snapshot = new OperationRecord.Snapshot(child, root.operationId(), root.encounterId(), player.getUUID(), player.getUUID(),
            root.target(), service.planClock(), engine.stateView(root.encounterId()).version(), TacticalActions.cell(player.blockPosition()), execution.targetCell,
            OperationRecord.Kind.ATTACK, service.generation());
        boolean opening = !engine.hasAttemptedAttack(root.encounterId(), player.getUUID())
            && player.level().getEntity(root.target()) instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != player;
        if (!engine.beginPlanStep(snapshot)) throw new IllegalStateException("ranged attack rejected");
        execution.launchTrace = service.rangedTrace(player, root.target(), child, opening);
        execution.action = child;
        aim(player, root.target());
        try (var launch = new TacticalLaunchContext(player.getUUID(), root.encounterId(), child, root.target(), execution.launchTrace)) {
            InteractionResult result = player.gameMode.useItem(player, player.level(), stack(player, execution.root.intent()), hand(execution.root.intent()));
            execution.runningItemRevision = TacticalItems.revision(player, stack(player, execution.root.intent()));
            if (player.isUsingItem()) {
                engine.publish(root.encounterId(), child, execution.actionStep++, OperationRecord.Outcome.ACCEPTED, "ranged attack charging", 0, 0, false);
                actions.send(player, root, null, true, "charging ranged attack");
            } else actions.finishAction(player, execution, result.consumesAction() ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.REJECTED,
                result.consumesAction() ? "projectile launch accepted" : "projectile launch rejected");
        } catch (RuntimeException failure) {
            actions.finishAction(player, execution, OperationRecord.Outcome.UNKNOWN, "projectile launch outcome unknown");
        }
    }
    public void tick(TacticalActions actions, ServerPlayer player, TacticalActions.Execution execution) {
        if (!canExecute(player, execution.root.intent(), player.position())) throw new IllegalStateException("ranged target out of reach");
        if (!player.isUsingItem()) throw new IllegalStateException("ranged charge interrupted");
        aim(player, execution.root.target());
        try (var launch = new TacticalLaunchContext(player.getUUID(), execution.root.encounterId(), execution.action, execution.root.target(), execution.launchTrace)) {
            ((cc.sighs.dndturn.mixin.TacticalUseTickAccessor) player).dndturn$tickUsingItem();
            ItemStack weapon = stack(player, execution.root.intent());
            execution.runningItemRevision = TacticalItems.revision(player, weapon);
            if (weapon.is(net.minecraft.world.item.Items.BOW) && player.getTicksUsingItem() >= 20) {
                player.releaseUsingItem();
                actions.finishAction(player, execution, OperationRecord.Outcome.COMPLETED, "bow released");
            } else if (weapon.is(net.minecraft.world.item.Items.CROSSBOW) && net.minecraft.world.item.CrossbowItem.isCharged(weapon)) {
                player.releaseUsingItem();
                InteractionResult result = player.gameMode.useItem(player, player.level(), weapon, hand(execution.root.intent()));
                actions.finishAction(player, execution, result.consumesAction() ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.REJECTED,
                    "crossbow release " + (result.consumesAction() ? "accepted" : "rejected"));
            }
        } catch (RuntimeException failure) {
            actions.finishAction(player, execution, OperationRecord.Outcome.UNKNOWN, "ranged execution outcome unknown");
        }
    }
    private static void aim(ServerPlayer player, UUID target) {
        var entity = player.level().getEntity(target);
        if (entity == null) throw new IllegalStateException("target unavailable");
        Vec3 delta = entity.getBoundingBox().getCenter().subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
        player.setYHeadRot(player.getYRot());
    }
}
