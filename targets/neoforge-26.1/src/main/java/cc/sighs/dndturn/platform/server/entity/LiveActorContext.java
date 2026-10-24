package cc.sighs.dndturn.platform.server.entity;

import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Stack-scoped current instance. This context grants no execution permission and is never saved. */
public record LiveActorContext(LivingEntity body, Facts facts) {
    /** Explicit read-only ports. These never expose another runtime owner. */
    public interface Facts {
        cc.sighs.dndturn.domain.actor.ActorStates.State state(UUID actor);
        UUID encounterAtBlock(ServerLevel level, net.minecraft.core.BlockPos pos);
        boolean ownsProjectile(ServerPlayer owner, net.minecraft.world.entity.projectile.Projectile projectile);
        String fault(UUID actor);
        boolean hostile(UUID encounter, UUID actor, UUID target);
        void requireAbilityWork(UUID actor, UUID encounter, cc.sighs.dndturn.platform.server.budget.AbilityWorkBudget.Work work);
    }
    public LiveActorContext(LivingEntity body) { this(body, null); }
    public Facts requireFacts() { return Objects.requireNonNull(facts, "actor query facts were not supplied"); }
    public cc.sighs.dndturn.domain.actor.ActorStates.State state() { return requireFacts().state(id()); }

    public LiveActorContext {
        Objects.requireNonNull(body);
        if (!(body.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
            || level.getEntity(body.getUUID()) != body)
            throw new IllegalStateException("actor is not the current loaded server instance");
    }
    public UUID id() { return body.getUUID(); }
    public UUID instance() { return ((PresentationIdentity)body).dndturn$presentationInstance(); }
    public ServerLevel level() { return (ServerLevel)body.level(); }
    /** Recheck at the effect boundary; a captured context is not proof of continued ownership. */
    public void verifyCurrent() {
        if (!level().getServer().isSameThread() || level().getEntity(id()) != body || !body.isAlive())
            throw new IllegalStateException("actor instance invalidated");
    }
    /** A missing player inventory/input port is unsupported, never emulated for a Mob. */
    public ServerPlayer requirePlayer() {
        if (body instanceof ServerPlayer player) return player;
        throw new IllegalStateException("ability requires player inventory/input port");
    }
}
