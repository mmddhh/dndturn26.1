package cc.sighs.dndturn.combat;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The audited natural melee driver. No inventory access or held-item callbacks. */
final class IntrinsicMelee extends TacticalAdapter {
    IntrinsicMelee() { super("dndturn:intrinsic_melee", 1, "空手 / 天生攻击", TacticalIntent.Capability.ATTACK, Set.of(TacticalIntent.TargetKind.ENTITY)); }
    public MeleeEffects meleeEffects(TacticalActor actor, boolean configuredKnockback) {
        var provider = MeleeAdapters.server().attacker(actor.body());
        if (provider == null) throw new IllegalStateException("intrinsic melee provider unavailable");
        boolean punch = actor.body() instanceof ServerPlayer;
        return new MeleeEffects(provider.intrinsicDamage().applyAsDouble(actor.body()),
            punch || configuredKnockback, punch, false);
    }
    public AbilitySource source(TacticalActor actor, TacticalIntent.Hand hand) {
        var provider = MeleeAdapters.server().attacker(actor.body());
        return provider == null ? null : AbilitySource.intrinsic(provider.id(), provider.version(), actor.id(), actor.instance());
    }
    public String unavailable(TacticalActor actor, TacticalIntent intent, CombatEngine.StateView state) {
        var found = actor.level().getEntity(intent.target().entity());
        if (!(found instanceof LivingEntity target) || !target.isAlive() || !state.members().containsKey(target.getUUID())) return "target not a living member";
        if ((actor.body() instanceof ServerPlayer) == (target instanceof ServerPlayer)) return "melee relationship unsupported";
        return MeleeAdapters.server().receiver(target) == null ? "target receiver unavailable" : null;
    }
    public boolean canExecute(TacticalActor actor, TacticalIntent intent, Vec3 feet) {
        var target = actor.level().getEntity(intent.target().entity());
        if (target == null || TacticalActions.cell(BlockPos.containing(feet)).chebyshev(TacticalActions.cell(target.blockPosition())) > 1) return false;
        var eye = feet.add(0, actor.body().getEyeHeight(), 0);
        return actor.level().clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, actor.body())).getType() == HitResult.Type.MISS;
    }
    public void start(TacticalActor actor, TacticalExecution execution) {
        execution.melee();
    }
    public void tick(TacticalActor actor, TacticalExecution execution) {
        throw new IllegalStateException("synchronous melee already terminal");
    }
}
