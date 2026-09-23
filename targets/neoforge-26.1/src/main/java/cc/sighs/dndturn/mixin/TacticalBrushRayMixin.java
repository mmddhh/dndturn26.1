package cc.sighs.dndturn.mixin;

import cc.sighs.dndturn.combat.BrushRay;
import cc.sighs.dndturn.combat.ItemUseEffects;
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
        if(ItemUseEffects.brushing(player))cir.setReturnValue(BrushRay.current(player));
    }
}
