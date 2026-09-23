package cc.sighs.dndturn.platform.server.action;

import cc.sighs.dndturn.platform.projection.PresentationIdentity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Stack-scoped current instance. This context grants no execution permission and is never saved. */
public record LiveActorContext(LivingEntity body) {
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
