package cc.sighs.dndturn.platform.server.action;

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
    private final TacticalImpact.Prepared prepared;
    public TacticalUseContext(Player player, BlockPos target, boolean free) {
        this(player, target, free, null);
    }
    public TacticalUseContext(Player player, BlockPos target, boolean free, TacticalImpact.Prepared prepared) {
        this.player = player.getUUID(); this.target = target.immutable(); this.free = free;
        this.prepared = prepared;
        previous = CURRENT.get(); CURRENT.set(this);
    }
    public static void verifyItem(net.minecraft.world.item.context.UseOnContext use) {
        var actual = TacticalImpact.prepare(use);
        var scope = CURRENT.get();
        if (scope == null || scope.prepared == null || !scope.prepared.equals(actual))
            throw new IllegalStateException("prepared item impact changed before write");
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
