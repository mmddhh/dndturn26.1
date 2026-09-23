package cc.sighs.dndturn.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exact audited feeding hooks, without Entity.interact's unrelated leash/ride fallbacks. */
@Mixin(Mob.class)
public interface MobItemInteractInvoker {
    @Invoker("mobInteract") InteractionResult dndturn$itemInteract(Player player, InteractionHand hand);
}
