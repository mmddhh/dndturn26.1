package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.encounter.operation.OperationRecord;
import cc.sighs.dndturn.platform.projection.PresentationUseIdentity;
import cc.sighs.dndturn.platform.server.control.ActiveBodyControl;
import cc.sighs.dndturn.platform.spatial.AimGeometry;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;

/** Ordinary ammunition only; launch ownership outlives this adapter's execution. */
final class RangedBehavior extends PlayerBehavior {
    private final Item weapon;
    RangedBehavior(String id, Item weapon) {
        super(id);
        this.weapon = weapon;
    }
    @Override public boolean supportsItem(ItemStack stack) { return weapon instanceof BowItem ? stack.getItem() instanceof BowItem
            : weapon instanceof CrossbowItem ? stack.getItem() instanceof CrossbowItem
            : weapon instanceof SnowballItem ? stack.getItem() instanceof SnowballItem : stack.is(weapon); }
    @Override public String unavailable(ServerPlayer player, ActionIntent intent, EncounterAuthority.StateView state) {
        if (!supportsItem(stack(player, intent))) return "selected ranged weapon required";
        try { validateAmmo(player, intent); } catch (IllegalStateException e) { return e.getMessage(); }
        return attackTarget(player, intent, state);
    }
    private void validateAmmo(ServerPlayer player, ActionIntent intent) {
            ItemStack weapon = stack(player, intent);
            if (!weapon.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()) throw new IllegalStateException("ranged enchantments unsupported");
            if (!(weapon.getItem() instanceof SnowballItem)) {
                var charged = weapon.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
                var ammo = charged != null && !charged.isEmpty() ? charged.itemCopies() : List.of(player.getProjectile(weapon));
                if (ammo.size() != 1 || !ammo.getFirst().is(net.minecraft.world.item.Items.ARROW)
                    || ammo.getFirst().has(net.minecraft.core.component.DataComponents.POTION_CONTENTS))
                    throw new IllegalStateException("ordinary arrows required");
            }
    }
    @Override protected double entityRange() { return 6; }
    @Override public Set<Integer> observationSlots(LiveActorContext actor, ActionIntent intent) {
        actor.requirePlayer();
        if (intent.behaviorId().equals("dndturn:snowball")) return super.observationSlots(actor, intent);
        // Bow/crossbow ammunition may be selected from any player inventory slot.
        var slots = new HashSet<Integer>();
        for (int slot = 0; slot <= 40; slot++) slots.add(slot);
        return Set.copyOf(slots);
    }
    public void start(ActionExecutionCoordinator actions, ServerPlayer player, ActionExecutionCoordinator.Execution execution) {
        var service = actions.service; var engine = actions.engine; UUID child = UUID.randomUUID();
        var root = execution.root;
        var snapshot = new OperationRecord.Snapshot(child, root.operationId(), root.encounterId(), player.getUUID(), player.getUUID(),
            root.target(), service.actionHost().planClock(), engine.stateView(root.encounterId()).version(), MinecraftCoordinates.cell(player.blockPosition()), execution.targetCell,
            OperationRecord.Kind.ATTACK, service.generation());
        boolean opening = !engine.hasAttemptedAttack(root.encounterId(), player.getUUID())
            && player.level().getEntity(root.target()) instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != player;
        if (!engine.beginPlanStep(snapshot)) throw new IllegalStateException("ranged attack rejected");
        execution.launchTrace = service.actionHost().rangedTrace(player, root.target(), child, opening);
        execution.action = child;
        aim(player, root.target());
        try (var launch = new TacticalLaunchContext(player.getUUID(), root.encounterId(), child, root.target(), execution.launchTrace, root.operationId(), root.intent())) {
            InteractionResult result = player.gameMode.useItem(player, player.level(), stack(player, execution.root.intent()), hand(execution.root.intent()));
            actions.confirmSourceChange(player, execution);
            if (player.isUsingItem()) {
                execution.useIdentity = ((PresentationUseIdentity)player).dndturn$useIdentity();
                engine.publish(root.encounterId(), child, execution.actionStep++, OperationRecord.Outcome.ACCEPTED, "ranged attack charging", 0, 0, false);
                actions.send(player, root, null, true, "charging ranged attack");
            } else actions.finishAction(player, execution, result.consumesAction() ? OperationRecord.Outcome.COMPLETED : OperationRecord.Outcome.REJECTED,
                result.consumesAction() ? "projectile launch accepted" : "projectile launch rejected");
        } catch (RuntimeException failure) {
            actions.finishAction(player, execution, OperationRecord.Outcome.UNKNOWN, "projectile launch outcome unknown");
        }
    }
    public void tick(ActionExecutionCoordinator actions, ServerPlayer player, ActionExecutionCoordinator.Execution execution) {
        if (!canExecute(player, execution.root.intent(), player.position())) throw new IllegalStateException("ranged target out of reach");
        if (!player.isUsingItem()) throw new IllegalStateException("ranged charge interrupted");
        aim(player, execution.root.target());
        try (var launch = new TacticalLaunchContext(player.getUUID(), execution.root.encounterId(), execution.action, execution.root.target(), execution.launchTrace, execution.root.operationId(), execution.root.intent())) {
            ActiveBodyControl.tickUse(player);
            ItemStack weapon = stack(player, execution.root.intent());
            actions.confirmSourceChange(player, execution);
            if (weapon.getItem() instanceof BowItem && player.getTicksUsingItem() >= 20) {
                player.releaseUsingItem();
                actions.confirmSourceChange(player, execution);
                actions.finishAction(player, execution, OperationRecord.Outcome.COMPLETED, "bow released");
            } else if (weapon.getItem() instanceof CrossbowItem && net.minecraft.world.item.CrossbowItem.isCharged(weapon)) {
                player.releaseUsingItem();
                InteractionResult result = player.gameMode.useItem(player, player.level(), weapon, hand(execution.root.intent()));
                actions.confirmSourceChange(player, execution);
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
        PlayerBehavior.applyAim(AimGeometry.plannedAim(player.getEyePosition(), entity.getBoundingBox().getCenter()), player);
    }
}
