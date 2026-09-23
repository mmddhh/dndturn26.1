package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.domain.ability.BuiltinAbilities;
import cc.sighs.dndturn.domain.ability.GrantEvidence;
import cc.sighs.dndturn.domain.action.ActionIntent;
import cc.sighs.dndturn.domain.encounter.EncounterAuthority;
import cc.sighs.dndturn.platform.server.damage.DamageReceivers;
import cc.sighs.dndturn.platform.server.damage.MeleeAdapters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The audited natural melee driver. No inventory access or held-item callbacks. */
final class IntrinsicMelee extends AbilityAdapter {
    IntrinsicMelee() { super(BuiltinAbilities.require("dndturn:intrinsic_melee")); }
    public MeleeEffects meleeEffects(LiveActorContext actor, boolean configuredKnockback) {
        var provider = MeleeAdapters.server().attacker(actor.body());
        if (provider == null) throw new IllegalStateException("intrinsic melee provider unavailable");
        boolean punch = actor.body() instanceof ServerPlayer;
        return new MeleeEffects(provider.intrinsicDamage().applyAsDouble(actor.body()),
            punch || configuredKnockback, punch, false);
    }
    public GrantEvidence source(LiveActorContext actor, ActionIntent.Hand hand) {
        var provider = MeleeAdapters.server().attacker(actor.body());
        return provider == null ? null : GrantEvidence.intrinsic(provider.id(), provider.version(), actor.id(), actor.instance());
    }
    public String unavailable(LiveActorContext actor, ActionIntent intent, EncounterAuthority.StateView state) {
        var found = actor.level().getEntity(intent.target().entity());
        if (!(found instanceof LivingEntity target)) return null; // Common target contract rejects non-living targets.
        return DamageReceivers.server().find(target) == null ? "target receiver unavailable" : null;
    }
    public boolean canExecute(LiveActorContext actor, ActionIntent intent, Vec3 feet) {
        var target = actor.level().getEntity(intent.target().entity());
        if (target == null || MinecraftCoordinates.cell(BlockPos.containing(feet)).chebyshev(MinecraftCoordinates.cell(target.blockPosition())) > 1) return false;
        var eye = feet.add(0, actor.body().getEyeHeight(), 0);
        return actor.level().clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, actor.body())).getType() == HitResult.Type.MISS;
    }
    public void start(LiveActorContext actor, AbilityExecutionContext execution) {
        execution.melee();
    }
    public void tick(LiveActorContext actor, AbilityExecutionContext execution) {
        throw new IllegalStateException("synchronous melee already terminal");
    }
}
