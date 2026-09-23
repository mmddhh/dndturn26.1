package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.TacticalUseContext;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep .84 protection/event handling while restricting the selected capability's branches. */
@Mixin(ServerPlayerGameMode.class)
public abstract class TacticalBlockUseMixin {
    @Redirect(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onItemUseFirst(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult dndturn$first(ItemStack stack, UseOnContext context) {
        return TacticalUseContext.free(context.getPlayer(), context.getClickedPos()) != null
            ? InteractionResult.PASS : stack.onItemUseFirst(context);
    }
    @Redirect(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult dndturn$block(BlockState state, ItemStack stack, Level level, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        Boolean free = TacticalUseContext.free(player, hit.getBlockPos());
        if (free != null) return free ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        return state.useItemOn(stack, level, player, hand, hit);
    }
    @Redirect(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult dndturn$item(ItemStack stack, UseOnContext context) {
        Boolean free = TacticalUseContext.free(context.getPlayer(), context.getClickedPos());
        if (Boolean.TRUE.equals(free)) return InteractionResult.PASS;
        if (Boolean.FALSE.equals(free)) cc.sighs.dndturn.combat.TacticalImpact.itemOnBlock(context);
        return stack.useOn(context);
    }
}
