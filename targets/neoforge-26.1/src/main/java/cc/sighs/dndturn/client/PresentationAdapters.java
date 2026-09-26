package cc.sighs.dndturn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/** Exact audited renderer/model pairs. Subclasses and replacement renderers retain vanilla fields. */
public final class PresentationAdapters {
    private PresentationAdapters() {}
    public static boolean humanoid(Entity entity) {
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        return entity instanceof Player && renderer.getClass() == AvatarRenderer.class
            && ((LivingEntityRenderer<?, ?, ?>)renderer).getModel().getClass() == net.minecraft.client.model.player.PlayerModel.class
            || entity.getClass() == Zombie.class && renderer.getClass() == ZombieRenderer.class
            && ((LivingEntityRenderer<?, ?, ?>)renderer).getModel().getClass() == net.minecraft.client.model.monster.zombie.ZombieModel.class;
    }
    public static boolean supported(Entity entity) {
        if (humanoid(entity)) return true;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        return entity.getClass() == Bat.class && renderer.getClass() == BatRenderer.class
            && ((LivingEntityRenderer<?, ?, ?>)renderer).getModel().getClass() == net.minecraft.client.model.ambient.BatModel.class
            || entity.getClass() == ItemEntity.class && renderer.getClass() == ItemEntityRenderer.class;
    }
}
