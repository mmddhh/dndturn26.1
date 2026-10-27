package cc.sighs.dndturn.platform.server.actor;

import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.operation.ActionFailure;
import cc.sighs.dndturn.platform.mixin.server.lifecycle.ActorEquipmentAccess;
import cc.sighs.dndturn.platform.observation.ItemStackFingerprint;
import cc.sighs.dndturn.platform.server.entity.LiveActorContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Actor-relative equipment addresses. Mob hands are actual equipment, never a player inventory. */
public final class ActorEquipment {
    private ActorEquipment() {}
    public static ActionIntent.ItemReference reference(LiveActorContext actor, ActionIntent.Hand hand) {
        int address = address(actor, hand);
        return new ActionIntent.ItemReference(address, ItemStackFingerprint.revision(actor.body(), read(actor, address)));
    }
    public static long generation(LiveActorContext actor, ActionIntent.Hand hand) {
        actor.verifyCurrent();
        if (actor.body() instanceof ServerPlayer player && hand == ActionIntent.Hand.MAIN_HAND)
            return ((EquipmentRevisions) player.getInventory()).dndturn$equipmentRevision(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        return ((EquipmentRevisions)((ActorEquipmentAccess)actor.body()).dndturn$equipment())
                .dndturn$equipmentRevision(hand == ActionIntent.Hand.MAIN_HAND
                        ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
    }
    /** Identity evidence is scoped to this actor instance and audited equipment writes. */
    public static GrantEvidence bind(LiveActorContext actor, GrantEvidence source) {
        if (source == null || source.kind() != GrantEvidence.Kind.EQUIPMENT) return source;
        var dependencies = new java.util.HashMap<>(source.dependencies());
        dependencies.put("equipmentActor", actor.id().toString());
        dependencies.put("equipmentInstance", actor.instance().toString());
        dependencies.put("equipmentGeneration", Long.toString(generation(actor, source.hand())));
        return source.withDependencies(dependencies);
    }
    public static GrantEvidence continuing(GrantEvidence source, long generation) {
        var dependencies = new java.util.HashMap<>(source.dependencies());
        dependencies.put("equipmentGeneration", Long.toString(generation));
        return source.withDependencies(dependencies);
    }
    public static int address(LiveActorContext actor, ActionIntent.Hand hand) {
        if (hand == null) throw new IllegalArgumentException("equipment hand required");
        return actor.body() instanceof ServerPlayer player
                ? hand == ActionIntent.Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40
                : hand == ActionIntent.Hand.MAIN_HAND ? 0 : 1;
    }
    public static ItemStack read(LiveActorContext actor, int address) {
        actor.verifyCurrent();
        if (actor.body() instanceof ServerPlayer player) {
            if (address < 0 || address > 40) throw new IllegalArgumentException("inventory address");
            return player.getInventory().getItem(address);
        }
        return switch (address) {
            case 0 -> actor.body().getMainHandItem();
            case 1 -> actor.body().getOffhandItem();
            default -> throw new IllegalArgumentException("unsupported equipment address");
        };
    }
    public static InteractionHand hand(ActionIntent.Hand hand) {
        return hand == ActionIntent.Hand.MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
    public static void validate(LiveActorContext actor, GrantEvidence source, String revision) {
        actor.verifyCurrent();
        if (source.actor() != null && (!actor.id().equals(source.actor()) || !actor.instance().equals(source.instance())))
            throw ActionFailure.source("equipment actor instance replaced");
        if (!actor.id().toString().equals(source.dependencies().get("equipmentActor"))
                || !actor.instance().toString().equals(source.dependencies().get("equipmentInstance"))
                || !Long.toString(generation(actor, source.hand())).equals(source.dependencies().get("equipmentGeneration")))
            throw ActionFailure.source("equipment identity or generation changed");
        if (source.item().slot() != address(actor, source.hand())) throw ActionFailure.source("equip selected item first");
        if (!revision.equals(ItemStackFingerprint.revision(actor.body(), read(actor, source.item().slot()))))
            throw ActionFailure.source("item stack changed");
    }
}
