package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.ability.AbilityDefinition;
import cc.sighs.dndturn.domain.ability.ActionCost;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.effect.EquipmentEffects;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.domain.fact.RuleFacts;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.platform.observation.ItemStackFingerprint;
import cc.sighs.dndturn.platform.server.ability.AbilityAdapterRegistry;
import cc.sighs.dndturn.platform.server.actor.ActorEquipment;
import cc.sighs.dndturn.platform.server.damage.DamageReceivers;
import cc.sighs.dndturn.platform.server.damage.RangedAdapters;
import cc.sighs.dndturn.platform.server.runtime.ServerRuntime;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Standard equipment ability. Species, native AI and native ammunition hooks are not queried here. */
public final class EquippedRanged extends AbilityAdapter {
    public static final String ID = "dndturn:equipment_bow";
    public EquippedRanged() {
        super(new AbilityDefinition(ID, 1, "Bow", ActionIntent.Capability.ATTACK,
                Set.of(ActionIntent.TargetKind.ENTITY), AbilityDefinition.Activation.MANUAL,
                ActionCost.ATTACK, RuleFacts.AVAILABILITY, ID, AbilityDefinition.TargetPolicy.OPPOSITE_PLAYER_LIVING_MEMBER));
    }
    static EquipmentEffects.Contribution contribution(LiveActorContext actor) {
        return AbilityAdapterRegistry.equipmentEffects().resolve(ServerRuntime.encounters(actor.level().getServer())
                .actorStates().state(actor.id()).runtime().effects().values()).orElse(null);
    }
    private static boolean ordinary(ItemStack stack, Item item) {
        return stack.is(item) && stack.getComponentsPatch().isEmpty();
    }
    static String ammunition(LiveActorContext actor) {
        // A held arrow is a real source even though the audited policy does not consume it.
        for (var hand : new ActionIntent.Hand[] {ActionIntent.Hand.OFF_HAND, ActionIntent.Hand.MAIN_HAND}) {
            var stack = ActorEquipment.read(actor, ActorEquipment.address(actor, hand));
            if (stack.getItem() instanceof ArrowItem) {
                return hand.name() + ":" + ItemStackFingerprint.revision(actor.body(), stack);
            }
        }
        return "native:minecraft:arrow";
    }
    public GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) {
        if (hand == null) return null;
        var driver = RangedAdapters.find(actor);
        var effect = contribution(actor);
        if (driver == null || effect == null) return null;
        var stack = ActorEquipment.read(actor, ActorEquipment.address(actor, hand));
        if (!stack.is(Items.BOW)) return null;
        // Native weapon selection prefers the main hand. Never expose a second ambiguous binding.
        if (hand == ActionIntent.Hand.OFF_HAND && actor.body().getMainHandItem().is(Items.BOW)) return null;
        String ammunition = switch (effect.policy().ammunition()) {
            case HELD_OR_NATIVE_ORDINARY_ARROW -> ammunition(actor);
        };
        return GrantEvidence.equipment(hand, ActorEquipment.reference(actor, hand), effect.policy().id(), effect.policy().version(),
                actor.id(), actor.instance(), effect.effect(), effect.grantRevision())
                .withDependencies(Map.of("ammunition", ammunition, "driver", driver.id() + "@" + driver.version(),
                        "mainGeneration", Long.toString(ActorEquipment.generation(actor, ActionIntent.Hand.MAIN_HAND)),
                        "offGeneration", Long.toString(ActorEquipment.generation(actor, ActionIntent.Hand.OFF_HAND))));
    }
    public String unavailable(LiveActorContext actor, ActionIntent intent, EncounterAuthority.StateView state) {
        var source = source(actor, intent.hand());
        if (source == null || !source.equals(intent.source())) return "equipment effect or ammunition source changed";
        var bow = ActorEquipment.read(actor, source.item().slot());
        for (var hand : ActionIntent.Hand.values()) {
            var held = ActorEquipment.read(actor, ActorEquipment.address(actor, hand));
            if (held.getItem() instanceof ArrowItem && !ordinary(held, Items.ARROW)) return "unsupported ammunition components";
        }
        // Damage alone is harmless; all other non-default components require their own adapter audit.
        if (!bow.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()
                || bow.getComponentsPatch().entrySet().stream().anyMatch(e -> e.getKey() != DataComponents.DAMAGE))
            return "unsupported bow components";
        var target = actor.level().getEntity(intent.target().entity());
        if (!(target instanceof LivingEntity living) || !living.isAlive()) return "ranged target unavailable";
        if (DamageReceivers.server().find(living) == null) return "ranged receiver unsupported";
        return null;
    }
    public boolean canExecute(LiveActorContext actor, ActionIntent intent, Vec3 feet) {
        var target = actor.level().getEntity(intent.target().entity());
        if (!(target instanceof LivingEntity living)) return false;
        Vec3 eye = feet.add(0, actor.body().getEyeHeight(), 0);
        return feet.distanceToSqr(target.position()) <= CombatRules.RANGED_RANGE * CombatRules.RANGED_RANGE && actor.level().clip(new net.minecraft.world.level.ClipContext(
                eye, living.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, actor.body())).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }
    public int approachRadius() { return CombatRules.RANGED_RANGE; }
    public boolean canPlanExecutionFrom(LiveActorContext actor, ActionIntent intent, Vec3 feet) {
        var target = actor.level().getEntity(intent.target().entity());
        double interiorRange = CombatRules.RANGED_RANGE - .5;
        return target != null && feet.distanceToSqr(target.position()) <= interiorRange * interiorRange && canExecute(actor, intent, feet);
    }
    public Set<Integer> observationSlots(LiveActorContext actor, ActionIntent intent) {
        return Set.of(ActorEquipment.address(actor, ActionIntent.Hand.MAIN_HAND), ActorEquipment.address(actor, ActionIntent.Hand.OFF_HAND));
    }
    public void start(LiveActorContext actor, AbilityExecutionContext execution) { execution.ranged(); }
    public void tick(LiveActorContext actor, AbilityExecutionContext execution) { throw new IllegalStateException("synchronous ranged driver"); }
}
