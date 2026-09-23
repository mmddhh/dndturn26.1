package cc.sighs.dndturn.platform.mixin.server.action;

import cc.sighs.dndturn.platform.server.action.BrushRay;
import cc.sighs.dndturn.platform.server.action.ItemWorldMutationScope;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrushItem.class)
public abstract class TacticalBrushRayMixin {
    @Inject(method="calculateHitResult",at=@At("HEAD"),cancellable=true)
    private void dndturn$currentSelectedAim(Player player,CallbackInfoReturnable<HitResult> cir) {
        if(ItemWorldMutationScope.brushing(player))cir.setReturnValue(BrushRay.current(player));
    }
}
