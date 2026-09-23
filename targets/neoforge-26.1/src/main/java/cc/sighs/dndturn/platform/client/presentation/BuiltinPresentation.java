package cc.sighs.dndturn.platform.client.presentation;

import java.util.Set;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import static cc.sighs.dndturn.platform.client.presentation.PresentationAdapters.*;

/** Built-ins use the same extension contract as external client adapters. */
final class BuiltinPresentation {
    private BuiltinPresentation() {}
    static void register() {
        PresentationAdapters.register(new Adapter("dndturn:humanoid", 1,
            Set.of(Channel.AGE, Channel.GAIT, Channel.ATTACK, Channel.USE, Channel.HURT),
            Set.of(Port.HUMANOID_HANDS),
            (entity, renderer) -> entity instanceof Player && renderer.getClass() == AvatarRenderer.class
                && ((LivingEntityRenderer<?, ?, ?>)renderer).getModel().getClass() == net.minecraft.client.model.player.PlayerModel.class
                || entity.getClass() == Zombie.class && renderer.getClass() == ZombieRenderer.class
                && ((LivingEntityRenderer<?, ?, ?>)renderer).getModel().getClass() == net.minecraft.client.model.monster.zombie.ZombieModel.class,
            entity -> new Humanoid()));
        PresentationAdapters.register(new Adapter("dndturn:bat", 1, Set.of(Channel.AGE, Channel.SPECIAL, Channel.HURT),
            (entity, renderer) -> entity.getClass() == Bat.class && renderer.getClass() == BatRenderer.class
                && ((LivingEntityRenderer<?, ?, ?>)renderer).getModel().getClass() == net.minecraft.client.model.ambient.BatModel.class,
            entity -> new BatState((Bat)entity)));
        PresentationAdapters.register(new Adapter("dndturn:item", 1, Set.of(Channel.AGE),
            (entity, renderer) -> entity.getClass() == ItemEntity.class && renderer.getClass() == ItemEntityRenderer.class,
            entity -> (current, input) -> (target, weight, partial) -> {}));
    }
    private static final class Humanoid implements State {
        private final WalkAnimationState walk = new WalkAnimationState();
        public Frame update(Entity entity, Input input) {
            var living = (LivingEntity)entity;
            walk.update(input.controlled() && living.isAlive() && !living.isPassenger()
                ? (float)Math.min(1, input.horizontal() * 4) : 0, .4F, living.isBaby() ? 3 : 1);
            float oldPos = walk.position(0), pos = walk.position(1), oldSpeed = walk.speed(0), speed = walk.speed(1);
            return (target, weight, partial) -> {
                if (target instanceof ArmedEntityRenderState armed && input.attack() != null) {
                    var attack = input.attack();
                    armed.attackTime = Math.min(1, (attack.elapsed() + partial) / attack.animation().duration());
                    armed.attackArm = attack.hand() == net.minecraft.world.InteractionHand.MAIN_HAND ? armed.mainArm : armed.mainArm.getOpposite();
                    armed.swingAnimationType = attack.animation().type();
                }
                if (target instanceof LivingEntityRenderState state && weight > 0) {
                    state.walkAnimationPos = Mth.lerp(weight, state.walkAnimationPos, Mth.lerp(partial, oldPos, pos));
                    state.walkAnimationSpeed = Mth.lerp(weight, state.walkAnimationSpeed, Mth.lerp(partial, oldSpeed, speed));
                    state.wornHeadAnimationPos = state.walkAnimationPos;
                }
            };
        }
    }
    private static final class BatState implements State {
        private final AnimationState flying = new AnimationState(), resting = new AnimationState();
        BatState(Bat bat) { flying.copyFrom(bat.flyAnimationState); resting.copyFrom(bat.restAnimationState); }
        public Frame update(Entity entity, Input input) {
            if (((Bat)entity).isResting()) { flying.stop(); resting.startIfStopped((int)input.age()); }
            else { resting.stop(); flying.startIfStopped((int)input.age()); }
            var fly = new AnimationState(); fly.copyFrom(flying);
            var rest = new AnimationState(); rest.copyFrom(resting);
            return (target, weight, partial) -> {
                if (weight > 0 && target instanceof BatRenderState state) { state.flyAnimationState.copyFrom(fly); state.restAnimationState.copyFrom(rest); }
            };
        }
    }
}
