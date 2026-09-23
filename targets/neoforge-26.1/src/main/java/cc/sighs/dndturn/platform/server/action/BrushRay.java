package cc.sighs.dndturn.platform.server.action;

import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** .84's brush otherwise samples getViewVector(0), which still contains the previous tick's aim. */
public final class BrushRay {
    private BrushRay() {}
    public static HitResult current(Player p) {
        var from=p.getEyePosition();var delta=p.getViewVector(1).scale(p.blockInteractionRange());
        var block=p.level().clipIncludingBorder(new ClipContext(from,from.add(delta),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        var entity=ProjectileUtil.getEntityHitResult(p.level(),p,from,block.getLocation(),
            p.getBoundingBox().expandTowards(delta).inflate(1),EntitySelector.CAN_BE_PICKED,0);
        return entity==null?block:entity;
    }
}
