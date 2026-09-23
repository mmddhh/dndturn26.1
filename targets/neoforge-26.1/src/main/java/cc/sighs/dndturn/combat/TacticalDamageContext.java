package cc.sighs.dndturn.combat;

import cc.sighs.dndturn.DNDTurnNeoForge261;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import java.util.UUID;

/** One server-thread vanilla damage call, scoped to its exact source and target. */
public final class TacticalDamageContext {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
        Identifier.fromNamespaceAndPath(DNDTurnNeoForge261.MOD_ID, "tactical"));
    private static final class Frame {
        final LivingEntity target;
        final DamageSource source;
        final boolean knockback;
        final UUID operationId;
        boolean sawKnockback;
        ItemStack blockingItem;
        InteractionHand blockingHand;
        BlocksAttacks blocking;
        float blockedContribution;
        int shieldDamage;
        boolean shieldWearCalled;
        boolean knockbackMoved;

        Frame(LivingEntity target, DamageSource source, boolean knockback, UUID operationId) {
            this.target = target;
            this.source = source;
            this.knockback = knockback;
            this.operationId = operationId;
        }
    }
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    public record HurtObservation(boolean accepted, float shieldBlockedContribution,
                                  boolean shieldWearCalled, boolean knockbackEventObserved,
                                  boolean knockbackMoved) {}

    private TacticalDamageContext() {}

    public static boolean restoresArmorDurability(LivingEntity target, DamageSource source) {
        Frame frame = CURRENT.get();
        return frame != null && frame.target == target && frame.source == source
            && source.is(DamageTypeTags.BYPASSES_ARMOR);
    }

    public static boolean applies(LivingEntity target, DamageSource source) {
        Frame frame = CURRENT.get();
        return frame != null && frame.target == target && frame.source == source;
    }

    public static UUID currentOperationId(LivingEntity target) {
        Frame frame = CURRENT.get();
        return frame != null && frame.target == target ? frame.operationId : null;
    }

    /** Called only at LivingEntity.applyItemBlocking, after Incoming and before world damage. */
    public static boolean captureShieldAttempt(LivingEntity target, DamageSource source,
                                               float amount, DamageContainer container) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.target != target || frame.source != source) return false;
        // Tactical damage has already used AC to resolve the attack. The vanilla method must
        // neither reduce it again nor apply its immediate block side effects.
        if (amount <= 0 || source.getDirectEntity() instanceof AbstractArrow arrow
            && arrow.getPierceLevel() > 0) return true;
        ItemStack item = target.getItemBlockingWith();
        if (item == null || !item.is(Items.SHIELD)) return true;
        BlocksAttacks blocking = item.get(DataComponents.BLOCKS_ATTACKS);
        if (blocking == null) return true;
        Vec3 sourcePosition = source.getSourcePosition();
        double angle = Math.PI;
        if (sourcePosition != null) {
            Vec3 facing = target.calculateViewVector(0.0F, target.getYHeadRot());
            Vec3 towardSource = sourcePosition.subtract(target.position());
            towardSource = new Vec3(towardSource.x, 0.0, towardSource.z).normalize();
            angle = Math.acos(Math.max(-1.0, Math.min(1.0, towardSource.dot(facing))));
        }
        float eligibleDamage = blocking.resolveBlockedDamage(source, amount, angle);
        // The tactical bypass tag deliberately skips vanilla shield reduction. For equipment
        // wear, expose the directional block to the same NeoForge cancellation/override hook.
        var event = CommonHooks.onDamageBlock(target, container, eligibleDamage, eligibleDamage > 0);
        if (eligibleDamage > 0 && event.getBlocked() && event.getBlockedDamage() > 0) {
            frame.blockingItem = item;
            frame.blockingHand = target.getUsedItemHand();
            frame.blocking = blocking;
            frame.blockedContribution = event.getBlockedDamage();
            frame.shieldDamage = event.shieldDamage();
        }
        return true;
    }

    public static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
        return hurt(level, target, source, amount, false);
    }

    public static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, float amount,
                               boolean knockback) {
        return hurt(level, target, source, amount, knockback, null);
    }

    public static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, float amount,
                               boolean knockback, UUID operationId) {
        return hurtObserved(level, target, source, amount, knockback, operationId).accepted();
    }

    public static HurtObservation hurtObserved(ServerLevel level, LivingEntity target, DamageSource source,
                                               float amount, boolean knockback, UUID operationId) {
        if (!level.getServer().isSameThread() || target.level() != level
            || !source.is(DAMAGE_TYPE) || !Float.isFinite(amount) || amount < 0)
            throw new IllegalArgumentException("invalid tactical damage call");
        if (CURRENT.get() != null) throw new IllegalStateException("nested tactical damage needs a child operation");
        Frame frame = new Frame(target, source, knockback, operationId);
        CURRENT.set(frame);
        try {
            Vec3 velocityBefore = target.getDeltaMovement();
            boolean accepted = target.hurtServer(level, source, amount);
            if (accepted && amount > 0 && frame.blockingItem != null
                && target.getItemInHand(frame.blockingHand) == frame.blockingItem) {
                frame.blocking.hurtBlockingItem(level, frame.blockingItem, target,
                    frame.blockingHand, frame.blockedContribution, frame.shieldDamage);
                frame.shieldWearCalled = true;
            }
            if (accepted && frame.sawKnockback && !target.getDeltaMovement().equals(velocityBefore)) {
                // The paused victim cannot wait for a body tick. Use vanilla collision for one forced response.
                Vec3 positionBefore = target.position();
                target.move(MoverType.SELF, target.getDeltaMovement());
                frame.knockbackMoved = !target.position().equals(positionBefore);
                target.setDeltaMovement(velocityBefore);
            }
            if (accepted) target.invulnerableTime = 0;
            return new HurtObservation(accepted, frame.blockedContribution, frame.shieldWearCalled,
                frame.sawKnockback, frame.knockbackMoved);
        } finally {
            CURRENT.remove();
        }
    }

    public static void onIncoming(LivingIncomingDamageEvent event) {
        Frame frame = CURRENT.get();
        if (frame != null && event.getEntity() == frame.target && event.getSource() == frame.source) {
            // 26.1.2.84 rejects zero here despite its error text. Clear the one tick after hurtServer returns.
            event.setInvulnerabilityTicks(1);
        } else if (event.getEntity().level() instanceof ServerLevel level) {
            ServerCombatService service = ServerCombatService.existing(level.getServer());
            if (service != null && service.isMember(event.getEntity().getUUID())) {
                event.setCanceled(true);
                if (event.getEntity() instanceof ServerPlayer player)
                    player.sendOverlayMessage(Component.literal("DNDTurn: unsupported external damage rejected"));
            }
        }
    }

    public static void onKnockback(LivingKnockBackEvent event) {
        Frame frame = CURRENT.get();
        if (frame != null && event.getEntity() == frame.target) {
            if (frame.knockback) frame.sawKnockback = true;
            else event.setCanceled(true);
        }
    }
}
