package cc.sighs.dndturn.platform.server.damage;
import cc.sighs.dndturn.domain.encounter.StateView;
import cc.sighs.dndturn.domain.encounter.operation.DamageTrace;
import cc.sighs.dndturn.domain.encounter.operation.ProjectileOrigin;
import cc.sighs.dndturn.domain.resolution.AttackResolution;
import cc.sighs.dndturn.domain.resolution.CombatRules;
import cc.sighs.dndturn.platform.diagnostics.DebugDiagnostics;
import cc.sighs.dndturn.platform.server.damage.TacticalDamageContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Shared attack dice and immutable damage observation evidence. */
public final class DamageResolution {
    private final Random attackRandom = new Random();
    private final Map<UUID, Random> gameTestAttackRandom = new HashMap<>();

    public AttackResolution.Plan resolveAttack(UUID encounter, int ac, int reduction,
            double damage, CombatRules.RollMode mode, DamageTrace captured) {
        var random = gameTestAttackRandom.getOrDefault(encounter, attackRandom);
        int first = captured == null ? 1 + random.nextInt(20) : captured.firstDie();
        int second = captured == null ? mode == CombatRules.RollMode.NORMAL ? first : 1 + random.nextInt(20) : captured.secondDie();
        return AttackResolution.resolve(new AttackResolution.Input(
                CombatRules.RULES_REVISION, new AttackResolution.Defense(ac, reduction), damage, mode,
                new AttackResolution.Dice(first, second)));
    }

    public static DamageTrace attackTrace(UUID operationId, UUID targetId,
                                           CombatRules.RollMode mode, CombatRules.AttackRoll roll,
                                           int armorClass, double weaponDamage, int reduction,
                                           int tacticalDamage, boolean accepted,
                                           float absorptionLoss, float healthLoss,
                                           DamageTrace.DamageEvidence evidence) {
        DebugDiagnostics.log("ATTACK_ROLL_OBSERVATION", () -> "op=" + operationId + " target=" + targetId
            + " mode=" + mode + " first=" + roll.first() + " second=" + roll.second()
            + " selected=" + roll.die() + " total=" + roll.total() + " ac=" + armorClass
            + " hit=" + roll.hit() + " critical=" + roll.critical() + " base=" + weaponDamage
            + " reduction=" + reduction + " tacticalDamage=" + tacticalDamage
            + " accepted=" + accepted + " absorptionLoss=" + absorptionLoss + " healthLoss=" + healthLoss
            + " stage=" + (evidence == null ? "PREPARED" : evidence.stage()));
        return new DamageTrace(operationId, targetId, mode, roll.first(), roll.second(),
            roll.die(), roll.total(), armorClass, roll.hit(), roll.critical(), weaponDamage,
            reduction, tacticalDamage, accepted, absorptionLoss, healthLoss, evidence);
    }

    public static DamageTrace.DamageEvidence damageEvidence(Entity actor, StateView state,
                                                     DamageTrace.Stage stage,
                                                     TacticalDamageContext.HurtObservation observed,
                                                     List<DamageTrace.EquipmentChange> equipmentChanges, ProjectileOrigin origin, boolean knockback) {
        return new DamageTrace.DamageEvidence(actor.getUUID(), CombatRules.RULES_REVISION,
            state.region().version(), observed == null ? knockback : observed.knockbackEnabled(), stage,
            observed == null ? 0 : observed.shieldBlockedContribution(),
            observed != null && observed.shieldWearCalled(),
            observed != null && observed.knockbackEventObserved(),
            observed != null && observed.knockbackMoved(), equipmentChanges,
            origin == null ? actor.getUUID() : origin.ownerId(),
            origin == null ? state.id() : origin.sourceEncounterId(),
            origin == null ? null : origin.rootOperationId(), observed == null ? null : observed.displacement());
    }

    public record EquipmentKey(UUID ownerId, String slot) {}
    public record EquipmentValue(String item, int count, int damage) {}

    public static Map<EquipmentKey, EquipmentValue> equipmentSnapshot(LivingEntity actor,
                                                                        LivingEntity target) {
        Map<EquipmentKey, EquipmentValue> values = new LinkedHashMap<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND))
            values.put(new EquipmentKey(target.getUUID(), slot.getName()),
                equipmentValue(target.getItemBySlot(slot)));
        if (actor != null) values.put(new EquipmentKey(actor.getUUID(), EquipmentSlot.MAINHAND.getName()),
            equipmentValue(actor.getMainHandItem()));
        return values;
    }

    private static EquipmentValue equipmentValue(ItemStack stack) {
        return new EquipmentValue(stack.isEmpty() ? "minecraft:air"
            : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
            stack.getCount(), stack.getDamageValue());
    }

    public static List<DamageTrace.EquipmentChange> equipmentChanges(
        Map<EquipmentKey, EquipmentValue> before, Map<EquipmentKey, EquipmentValue> after) {
        List<DamageTrace.EquipmentChange> changes = new ArrayList<>();
        for (var entry : before.entrySet()) {
            EquipmentValue oldValue = entry.getValue();
            EquipmentValue newValue = after.get(entry.getKey());
            if (newValue == null) throw new IllegalStateException("equipment slot disappeared");
            if (!oldValue.equals(newValue)) changes.add(new DamageTrace.EquipmentChange(
                entry.getKey().ownerId(), entry.getKey().slot(), oldValue.item(), newValue.item(),
                oldValue.count(), newValue.count(), oldValue.damage(), newValue.damage()));
        }
        return List.copyOf(changes);
    }
    public void seed(UUID encounter, Random random) { gameTestAttackRandom.put(encounter, random); }
    public void forget(UUID encounter) { gameTestAttackRandom.remove(encounter); }

}
