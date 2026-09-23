package cc.sighs.dndturn.combat;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;

/** Bounded samples on the server thread; unavailable targets are not synthesized as unchanged. */
final class AbilityObservations {
    private AbilityObservations() {}
    static AbilityCheckpoint.Sample capture(TacticalActor actor, TacticalIntent intent, List<GridCell> footprint, Set<Integer> slots) {
        actor.verifyCurrent();
        var items = new ArrayList<AbilityCheckpoint.Item>();
        if (!slots.isEmpty()) {
            ServerPlayer player = actor.requirePlayer();
            for (int slot : new TreeSet<>(slots)) {
                var stack = player.getInventory().getItem(slot);
                items.add(new AbilityCheckpoint.Item(slot, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.getCount(), stack.getDamageValue(), TacticalItems.revision(player, stack)));
            }
        }
        var blocks = new ArrayList<AbilityCheckpoint.Block>();
        for (var cell : footprint) {
            var pos = TacticalActions.pos(cell);
            if (!actor.level().hasChunkAt(pos)) throw new IllegalStateException("observation footprint unloaded");
            blocks.add(new AbilityCheckpoint.Block(cell, actor.level().getBlockState(pos).toString()));
        }
        var bodies = new ArrayList<AbilityCheckpoint.Body>();
        bodies.add(body(actor.body()));
        if (intent.target().entity() != null && !intent.target().entity().equals(actor.body().getUUID())) {
            var entity=actor.level().getEntity(intent.target().entity());
            if (entity instanceof LivingEntity target) bodies.add(body(target));
            else if (!intent.behaviorId().equals("dndturn:reel"))
                throw new IllegalStateException("observation target unavailable");
        }
        return new AbilityCheckpoint.Sample(items, blocks, bodies);
    }
    private static AbilityCheckpoint.Body body(LivingEntity entity) {
        return new AbilityCheckpoint.Body(entity.getUUID(), ((PresentationIdentity)entity).dndturn$presentationInstance(),
            entity.getX(), entity.getY(), entity.getZ(), entity.getHealth(), entity.getAbsorptionAmount());
    }
}
