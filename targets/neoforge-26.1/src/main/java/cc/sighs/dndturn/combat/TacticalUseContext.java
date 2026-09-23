package cc.sighs.dndturn.combat;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/** Scoped capability selecting the block-only or item-only branch of the original use chain. */
public final class TacticalUseContext implements AutoCloseable {
    private static final ThreadLocal<TacticalUseContext> CURRENT = new ThreadLocal<>();
    private final TacticalUseContext previous;
    private final UUID player;
    private final BlockPos target;
    private final boolean free;
    public TacticalUseContext(Player player, BlockPos target, boolean free) {
        this.player = player.getUUID(); this.target = target.immutable(); this.free = free;
        previous = CURRENT.get(); CURRENT.set(this);
    }
    public static Boolean free(Player player, BlockPos target) {
        var context = CURRENT.get();
        return context != null && player != null && context.player.equals(player.getUUID())
            && context.target.equals(target) ? context.free : null;
    }
    @Override public void close() {
        if (CURRENT.get() != this) throw new IllegalStateException("unbalanced use scope");
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
